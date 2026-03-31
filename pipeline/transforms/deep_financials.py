"""
심화 재무 지표 — 영업이익 증가율, 차입금 증가율, 이자보상배율.
yfinance 재무제표 직접 파싱.
"""

import logging
import time

import yfinance as yf

logger = logging.getLogger(__name__)


def compute_deep_financials(symbol: str) -> dict | None:
    """
    yfinance 재무제표에서 심화 재무 지표 산출.
    종목당 ~2초 소요.
    """
    try:
        ticker = yf.Ticker(symbol)

        # 분기 손익계산서
        qi = ticker.quarterly_income_stmt
        if qi is None or qi.empty:
            return None

        qb = ticker.quarterly_balance_sheet

        result = {"symbol": symbol}

        # 영업이익 증가율 (YoY)
        if "Operating Income" in qi.index and qi.shape[1] >= 5:
            current = qi.loc["Operating Income"].iloc[0]
            yoy = qi.loc["Operating Income"].iloc[4]
            if yoy and abs(yoy) > 0:
                result["op_income_growth"] = round(
                    (current - yoy) / abs(yoy), 4
                )

        # 차입금 증가율 (YoY)
        if (
            qb is not None
            and "Total Debt" in qb.index
            and qb.shape[1] >= 5
        ):
            current_debt = qb.loc["Total Debt"].iloc[0]
            yoy_debt = qb.loc["Total Debt"].iloc[4]
            if yoy_debt and abs(yoy_debt) > 0:
                result["debt_growth"] = round(
                    (current_debt - yoy_debt) / abs(yoy_debt), 4
                )

        # 이자보상배율 (TTM)
        annual = ticker.income_stmt
        if annual is not None and not annual.empty:
            has_oi = "Operating Income" in annual.index
            has_ie = "Interest Expense" in annual.index
            if has_oi and has_ie:
                op_ttm = annual.loc["Operating Income"].iloc[0]
                int_exp = annual.loc["Interest Expense"].iloc[0]
                if int_exp and abs(int_exp) > 0:
                    result["interest_coverage"] = round(
                        op_ttm / abs(int_exp), 2
                    )

        # ROIC (NOPAT / 투하자본)
        if annual is not None and qb is not None:
            try:
                if "Operating Income" in annual.index:
                    oi = annual.loc["Operating Income"].iloc[0]
                    tax_rate = 0.21  # 미국 법인세율 근사
                    nopat = oi * (1 - tax_rate)

                    total_equity = None
                    total_debt_val = None
                    cash = None

                    if "Stockholders Equity" in qb.index:
                        total_equity = qb.loc["Stockholders Equity"].iloc[0]
                    if "Total Debt" in qb.index:
                        total_debt_val = qb.loc["Total Debt"].iloc[0]
                    if "Cash And Cash Equivalents" in qb.index:
                        cash = qb.loc["Cash And Cash Equivalents"].iloc[0]

                    if total_equity and total_debt_val:
                        invested_capital = (
                            total_equity
                            + total_debt_val
                            - (cash or 0)
                        )
                        if invested_capital > 0:
                            result["roic"] = round(
                                nopat / invested_capital, 4
                            )
            except Exception:
                pass

        # 기준일
        if qi.shape[1] > 0 and hasattr(qi.columns[0], "strftime"):
            result["asof_deep_financial"] = qi.columns[0].strftime(
                "%Y-%m-%d"
            )

        return result

    except Exception as e:
        logger.debug(f"[DeepFinancial] {symbol} 실패: {e}")
        return None


def batch_deep_financials(top_symbols: list[str]) -> list[dict]:
    """
    상위 500종목 심화 재무 배치.
    500 x ~2초 = ~17분.
    """
    results = []
    total = min(len(top_symbols), 500)

    for i, sym in enumerate(top_symbols[:500]):
        data = compute_deep_financials(sym)
        if data:
            results.append(data)
        time.sleep(1)

        if (i + 1) % 100 == 0 or i == total - 1:
            logger.info(
                f"[DeepFinancial] {i + 1}/{total} 완료 ({len(results)}건)"
            )

    return results
