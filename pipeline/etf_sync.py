"""
ETF Holdings/Sector/Country/Profile 수집 (etfpy, repo-B 목요일 주1회).
etfpy: ETFdb.com 스크래핑, 무료, API 키 불필요.
250~300 ETF × ~7초 = 25~35분.
"""

import logging
import re
import sys
import time
from datetime import date, datetime, timezone
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from etfpy import ETF

from pipeline.common import send_batch_email
from pipeline.loaders.supabase_upsert import (
    get_supabase_client,
    upsert_etf_country_exposure,
    upsert_etf_holdings,
    upsert_etf_profile_etfpy,
    upsert_etf_sector_exposure,
)

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def _parse_number(val) -> float | None:
    """문자열에서 숫자 파싱. '$402,034.0 M' → 402034000000.0, '0.09%' → 0.09 등."""
    if val is None:
        return None
    if isinstance(val, (int, float)):
        return float(val)
    s = str(val).strip()
    if not s or s == "N/A" or s == "--":
        return None
    try:
        # % 제거
        s = s.replace("%", "").replace(",", "")
        # $ 제거
        s = s.replace("$", "")
        # M/B 단위 처리
        multiplier = 1.0
        if s.endswith(" M"):
            multiplier = 1_000_000
            s = s[:-2]
        elif s.endswith(" B"):
            multiplier = 1_000_000_000
            s = s[:-2]
        elif s.endswith("M"):
            multiplier = 1_000_000
            s = s[:-1]
        elif s.endswith("B"):
            multiplier = 1_000_000_000
            s = s[:-1]
        return float(s.strip()) * multiplier
    except (ValueError, TypeError):
        return None


def _safe_get(d: dict, key: str, default=None):
    """딕셔너리에서 안전하게 값 추출."""
    if not isinstance(d, dict):
        return default
    return d.get(key, default)


def _get_etf_symbols(supabase) -> list[str]:
    """
    ETF 선정: 시총 Top 200 ∪ 거래대금 Top 200 → 합집합 (250~300개).
    """
    from pipeline.common import _paginated_select

    rows = _paginated_select(
        supabase, "latest_equities",
        "symbol, price, volume, total_assets, market_cap",
        {"is_delisted": False, "asset_type": "etf"},
    )

    # 거래대금 Top 200
    for r in rows:
        price = r.get("price") or 0
        volume = r.get("volume") or 0
        r["_turnover"] = price * volume

    by_turnover = sorted(rows, key=lambda x: x["_turnover"], reverse=True)
    turnover_set = {r["symbol"] for r in by_turnover[:200]}

    # 시총(total_assets 또는 market_cap) Top 200
    for r in rows:
        r["_aum"] = r.get("total_assets") or r.get("market_cap") or 0

    by_aum = sorted(rows, key=lambda x: x["_aum"], reverse=True)
    aum_set = {r["symbol"] for r in by_aum[:200]}

    # 합집합
    combined = turnover_set | aum_set
    symbols = [r["symbol"] for r in by_turnover if r["symbol"] in combined]
    # turnover 순 정렬 유지, aum에만 있는 것 추가
    aum_only = aum_set - turnover_set
    symbols.extend(r["symbol"] for r in by_aum if r["symbol"] in aum_only)

    logger.info(
        f"[ETF] 대상: {len(symbols)}개 "
        f"(거래대금 {len(turnover_set)} ∪ 시총 {len(aum_set)})"
    )
    return symbols


def _build_profile_row(symbol: str, info: dict, technicals: dict) -> dict:
    """etfpy info + technicals → etf_profile 행 구성."""
    now_ts = datetime.now(timezone.utc).isoformat()

    row = {"symbol": symbol, "etfpy_asof": now_ts}

    # info 매핑
    row["issuer"] = _safe_get(info, "Issuer")
    row["brand"] = _safe_get(info, "Brand")
    row["structure"] = _safe_get(info, "Structure")
    row["index_tracked"] = _safe_get(info, "Index Tracked")
    row["asset_class"] = _safe_get(info, "Asset Class")
    row["asset_class_size"] = _safe_get(info, "Asset Class Size")
    row["asset_class_style"] = _safe_get(info, "Asset Class Style")
    row["region"] = _safe_get(info, "Region (General)")
    row["focus"] = _safe_get(info, "Focus")
    row["niche"] = _safe_get(info, "Niche")
    row["strategy"] = _safe_get(info, "Strategy")
    row["weighting_scheme"] = _safe_get(info, "Weighting Scheme")

    # 기존 컬럼 업데이트
    row["expense_ratio"] = _parse_number(_safe_get(info, "Expense Ratio"))
    row["category"] = _safe_get(info, "Category:")
    row["fund_family"] = _safe_get(info, "Brand")
    row["total_assets"] = _parse_number(_safe_get(info, "AUM"))
    row["inception_date"] = _safe_get(info, "Inception")

    # P/E Ratio (딕셔너리일 수 있음)
    pe_raw = _safe_get(info, "P/E Ratio")
    if isinstance(pe_raw, dict):
        row["pe_ratio"] = _parse_number(pe_raw.get(symbol))
    else:
        row["pe_ratio"] = _parse_number(pe_raw)

    row["week52_high"] = _parse_number(_safe_get(info, "52 Week Hi"))
    row["week52_low"] = _parse_number(_safe_get(info, "52 Week Lo"))
    row["shares_outstanding"] = _parse_number(_safe_get(info, "Shares"))

    # technicals 매핑
    tech_map = {
        "ma_20d": "20 Day MA",
        "ma_60d": "60 Day MA",
        "rsi_10d": "RSI 10 Day",
        "rsi_20d": "RSI 20 Day",
        "rsi_30d": "RSI 30 Day",
        "macd_15": "MACD 15 Period",
        "macd_100": "MACD 100 Period",
        "bollinger_lower_10": "Lower Bollinger (10 Day)",
        "bollinger_lower_20": "Lower Bollinger (20 Day)",
        "bollinger_lower_30": "Lower Bollinger (30 Day)",
        "stoch_k_1d": "Stochastic Oscillator %K (1 Day)",
        "stoch_k_5d": "Stochastic Oscillator %K (5 Day)",
        "stoch_d_1d": "Stochastic Oscillator %D (1 Day)",
        "stoch_d_5d": "Stochastic Oscillator %D (5 Day)",
        "resistance_1": "Resistance Level 1",
        "resistance_2": "Resistance Level 2",
        "support_1": "Support Level 1",
        "support_2": "Support Level 2",
        "tracking_diff_up": "Tracking Difference Max Upside (%)",
        "tracking_diff_down": "Tracking Difference Max Downside (%)",
        "max_premium_discount": "Maximum Premium Discount (%)",
        "median_premium_discount": "Median Premium Discount (%)",
        "avg_spread_dollar": "Average Spread ($)",
        "avg_spread_pct": "Average Spread (%)",
    }
    for col, key in tech_map.items():
        row[col] = _parse_number(_safe_get(technicals, key))

    # None 값 제거
    return {k: v for k, v in row.items() if v is not None}


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"ETF Sync (etfpy) 시작 — {date.today()}")
    logger.info("=" * 60)

    supabase = get_supabase_client()
    symbols = _get_etf_symbols(supabase)

    if not symbols:
        logger.error("[ETF] 대상 ETF 없음 — 종료")
        sys.exit(1)

    now_ts = datetime.now(timezone.utc).isoformat()
    success_count = 0
    fail_count = 0

    for i, sym in enumerate(symbols):
        try:
            etf = ETF(sym)
            info = etf.info or {}
            technicals = etf.technicals or {}
            holdings_data = etf.holdings or []
            exposure = etf.exposure or {}

            # 1. etf_profile UPSERT
            profile_row = _build_profile_row(sym, info, technicals)
            upsert_etf_profile_etfpy(supabase, profile_row)

            # 2. etf_holdings: DELETE → INSERT
            if holdings_data:
                holding_rows = []
                for h in holdings_data:
                    weight = _parse_number(h.get("Share") or h.get("% Of Portfolio"))
                    holding_rows.append({
                        "symbol": h.get("Symbol"),
                        "name": h.get("Holding"),
                        "percent": weight,
                    })
                upsert_etf_holdings(supabase, sym, holding_rows)

            # 3. etf_sector_exposure: DELETE → INSERT
            sector_breakdown = _safe_get(exposure, "Sector Breakdown") or {}
            if sector_breakdown and isinstance(sector_breakdown, dict):
                sector_rows = [
                    {"sector": k, "weight": _parse_number(v)}
                    for k, v in sector_breakdown.items()
                ]
                upsert_etf_sector_exposure(supabase, sym, sector_rows)

            # 4. etf_country_exposure: DELETE → INSERT
            country_breakdown = _safe_get(exposure, "Country Breakdown") or {}
            if country_breakdown and isinstance(country_breakdown, dict):
                country_rows = [
                    {"country": k, "percentage": _parse_number(v)}
                    for k, v in country_breakdown.items()
                ]
                upsert_etf_country_exposure(supabase, sym, country_rows)

            success_count += 1
        except Exception as e:
            logger.warning(f"[ETF] {sym} 실패: {e}")
            fail_count += 1
            continue

        if (i + 1) % 20 == 0:
            logger.info(f"[ETF] {i + 1}/{len(symbols)} 완료 ({success_count}건 성공)")

        time.sleep(1.5)  # ETFdb.com 스크래핑 속도 조절

    elapsed = time.time() - start_time

    summary = (
        f"ETF Sync (etfpy) 완료\n"
        f"대상: {len(symbols)} ETF\n"
        f"성공: {success_count}건, 실패: {fail_count}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] ETF Sync 완료 ({date.today()})",
        summary,
    )

    logger.info("=" * 60)
    logger.info(f"ETF Sync 완료 — {elapsed / 60:.1f}분")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
