"""
yfinance 벌크 데이터 수집.
② Quote & Metrics — 가격 벌크 + Ticker.info (우선 티커 리스트)
"""

import logging
import time
from datetime import date, timedelta

import pandas as pd
import yfinance as yf

from pipeline.config import (
    YFINANCE_CHUNK_SIZE,
    YFINANCE_INFO_SLEEP,
    YFINANCE_MAX_RETRIES,
    YFINANCE_RETRY_BACKOFF,
    YFINANCE_SLEEP_BETWEEN,
)

logger = logging.getLogger(__name__)

# Ticker.info → DB 컬럼 매핑
INFO_FIELD_MAP = {
    "currentPrice": "price",
    "regularMarketPrice": "price",
    "marketCap": "market_cap",
    "trailingPE": "pe_ttm",
    "forwardPE": "pe_forward",
    "priceToBook": "pb_ratio",
    "trailingEps": "eps_ttm",
    "dividendYield": "dividend_yield",
    "dividendRate": "dividend_rate",
    "fiftyTwoWeekHigh": "week52_high",
    "fiftyTwoWeekLow": "week52_low",
    "fiftyDayAverage": "ma_50",
    "twoHundredDayAverage": "ma_200",
    "averageVolume": "avg_volume_10d",
    "averageDailyVolume10Day": "avg_volume_10d",
    "beta": "beta",
    "volume": "volume",
    "previousClose": "prev_close",
    "open": "open_price",
    "dayHigh": "day_high",
    "dayLow": "day_low",
    "returnOnEquity": "roe",
    "returnOnAssets": "roa",
    "debtToEquity": "debt_to_equity",
    "currentRatio": "current_ratio",
    "operatingMargins": "operating_margin",
    "profitMargins": "profit_margin",
    "grossMargins": "gross_margin",
    "revenueGrowth": "revenue_growth",
    "earningsGrowth": "earnings_growth",
    "pegRatio": "peg_ratio",
    "priceToSalesTrailing12Months": "ps_ratio",
    "priceToFreeCashflows": "pfcf_ratio",
    "enterpriseValue": "ev",
    "enterpriseToEbitda": "ev_ebitda",
    "enterpriseToRevenue": "ev_revenue",
    "totalRevenue": "total_revenue",
    "ebitda": "ebitda",
    "freeCashflow": "free_cashflow",
    "totalCash": "total_cash",
    "totalDebt": "total_debt",
    "bookValue": "book_value",
    "revenuePerShare": "revenue_per_share",
    "quickRatio": "quick_ratio",
    "payoutRatio": "payout_ratio",
    "sharesOutstanding": "shares_outstanding",
    "floatShares": "float_shares",
    "sharesShort": "shares_short",
    "shortRatio": "short_ratio",
    "shortPercentOfFloat": "short_pct_float",
    "heldPercentInsiders": "insider_pct",
    "heldPercentInstitutions": "inst_pct",
    "recommendationKey": "analyst_rating",
    "recommendationMean": "analyst_rating_score",
    "targetMeanPrice": "target_mean",
    "targetHighPrice": "target_high",
    "targetLowPrice": "target_low",
    "numberOfAnalystOpinions": "analyst_count",
    "sector": "sector",
    "industry": "industry",
    "country": "country",
}


def fetch_bulk_prices(symbols: list[str]) -> dict[str, dict]:
    """
    yf.download()로 전종목 가격 벌크 취득.
    CHUNK_SIZE(80)개씩 분할 → 전종목 ~20분.
    Returns: {symbol: {"price": ..., "volume": ..., ...}}
    """
    results = {}
    chunks = [
        symbols[i : i + YFINANCE_CHUNK_SIZE]
        for i in range(0, len(symbols), YFINANCE_CHUNK_SIZE)
    ]

    for i, chunk in enumerate(chunks):
        for attempt in range(YFINANCE_MAX_RETRIES):
            try:
                df = yf.download(
                    chunk,
                    period="1d",
                    threads=True,
                    progress=False,
                    ignore_tz=True,
                )
                if df.empty:
                    break

                # 실제 거래일 추출 (yfinance index)
                trade_date = None
                if not df.empty:
                    idx = df.index[-1]
                    if hasattr(idx, "date"):
                        trade_date = idx.date().isoformat()
                    else:
                        trade_date = str(idx)[:10]

                # MultiIndex인 경우 (여러 종목)
                if isinstance(df.columns, pd.MultiIndex):
                    for sym in chunk:
                        try:
                            if sym in df.columns.get_level_values(1):
                                row = df.xs(sym, level=1, axis=1)
                                if not row.empty:
                                    last = row.iloc[-1]
                                    results[sym] = {
                                        "price": _safe_float(last.get("Close")),
                                        "open_price": _safe_float(last.get("Open")),
                                        "day_high": _safe_float(last.get("High")),
                                        "day_low": _safe_float(last.get("Low")),
                                        "volume": _safe_int(last.get("Volume")),
                                        "data_date": trade_date,
                                    }
                        except Exception:
                            pass
                else:
                    # 단일 종목
                    if len(chunk) == 1 and not df.empty:
                        last = df.iloc[-1]
                        results[chunk[0]] = {
                            "price": _safe_float(last.get("Close")),
                            "open_price": _safe_float(last.get("Open")),
                            "day_high": _safe_float(last.get("High")),
                            "day_low": _safe_float(last.get("Low")),
                            "volume": _safe_int(last.get("Volume")),
                            "data_date": trade_date,
                        }
                break  # 성공 시 루프 탈출
            except Exception as e:
                logger.warning(
                    f"[yfinance] 가격 chunk {i + 1} 실패 "
                    f"(attempt {attempt + 1}): {e}"
                )
                if attempt < YFINANCE_MAX_RETRIES - 1:
                    time.sleep(YFINANCE_RETRY_BACKOFF)

        if (i + 1) % 20 == 0 or i == len(chunks) - 1:
            logger.info(
                f"[yfinance] 가격 벌크: {i + 1}/{len(chunks)} 청크 완료 "
                f"({len(results)}건)"
            )
        time.sleep(YFINANCE_SLEEP_BETWEEN)

    return results


def fetch_ticker_info(symbols: list[str]) -> dict[str, dict]:
    """
    우선 티커 리스트에 대해 Ticker.info 호출.
    종목당 ~2초, ~1,900개 → ~63분.
    Returns: {symbol: {db_column: value, ...}}
    """
    results = {}

    for i, sym in enumerate(symbols):
        try:
            ticker = yf.Ticker(sym)
            info = ticker.info
            if not info or info.get("regularMarketPrice") is None:
                continue

            row = {"symbol": sym}
            for yf_key, db_col in INFO_FIELD_MAP.items():
                val = info.get(yf_key)
                if val is not None:
                    # price는 currentPrice 우선, 없으면 regularMarketPrice
                    if db_col == "price" and "price" in row and row["price"]:
                        continue
                    row[db_col] = val

            # exDividendDate (timestamp → date)
            ex_div = info.get("exDividendDate")
            if ex_div:
                try:
                    row["ex_dividend_date"] = pd.Timestamp(
                        ex_div, unit="s"
                    ).strftime("%Y-%m-%d")
                except Exception:
                    pass

            # 5년 평균 배당률
            five_yr = info.get("fiveYearAvgDividendYield")
            if five_yr is not None:
                row["avg_dividend_yield_5y"] = five_yr / 100.0  # %→소수 변환

            results[sym] = row

        except Exception as e:
            logger.debug(f"[yfinance] Ticker.info 실패 ({sym}): {e}")

        if (i + 1) % 100 == 0:
            logger.info(
                f"[yfinance] Ticker.info: {i + 1}/{len(symbols)} 완료 "
                f"({len(results)}건)"
            )
        time.sleep(YFINANCE_INFO_SLEEP)

    logger.info(f"[yfinance] Ticker.info 완료: {len(results)}/{len(symbols)}건")
    return results


def fetch_price_history(
    symbols: list[str], period: str = "200d"
) -> dict[str, pd.DataFrame]:
    """
    가격 히스토리 다운로드 (기술적 지표 산출용).
    Returns: {symbol: DataFrame(Date, Open, High, Low, Close, Volume)}
    """
    results = {}
    chunks = [
        symbols[i : i + YFINANCE_CHUNK_SIZE]
        for i in range(0, len(symbols), YFINANCE_CHUNK_SIZE)
    ]

    for i, chunk in enumerate(chunks):
        for attempt in range(YFINANCE_MAX_RETRIES):
            try:
                df = yf.download(
                    chunk, period=period, threads=True, progress=False, ignore_tz=True
                )
                if df.empty:
                    break

                if isinstance(df.columns, pd.MultiIndex):
                    for sym in chunk:
                        try:
                            if sym in df.columns.get_level_values(1):
                                sym_df = df.xs(sym, level=1, axis=1).dropna()
                                if len(sym_df) >= 20:
                                    results[sym] = sym_df
                        except Exception:
                            pass
                else:
                    if len(chunk) == 1 and len(df) >= 20:
                        results[chunk[0]] = df.dropna()
                break  # 성공 시 루프 탈출
            except Exception as e:
                logger.warning(
                    f"[yfinance] 히스토리 chunk {i + 1} 실패 "
                    f"(attempt {attempt + 1}): {e}"
                )
                if attempt < YFINANCE_MAX_RETRIES - 1:
                    time.sleep(YFINANCE_RETRY_BACKOFF)

        if (i + 1) % 10 == 0 or i == len(chunks) - 1:
            logger.info(
                f"[yfinance] 히스토리: {i + 1}/{len(chunks)} 청크 ({len(results)}건)"
            )
        time.sleep(YFINANCE_SLEEP_BETWEEN)

    return results


def _safe_float(val) -> float | None:
    try:
        if val is None or pd.isna(val):
            return None
        return round(float(val), 4)
    except (ValueError, TypeError):
        return None


def _safe_int(val) -> int | None:
    try:
        if val is None or pd.isna(val):
            return None
        return int(val)
    except (ValueError, TypeError):
        return None
