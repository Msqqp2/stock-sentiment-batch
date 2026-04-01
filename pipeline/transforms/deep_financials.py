"""
심화 재무 지표 — 영업이익 증가율, 차입금 증가율, 이자보상배율,
Piotroski F-Score, Altman Z-Score.
yfinance 재무제표 직접 파싱.
"""

import logging
import math
import time

import yfinance as yf

logger = logging.getLogger(__name__)


def _safe(val):
    """NaN/Inf를 None으로 변환."""
    if val is None:
        return None
    try:
        f = float(val)
        if math.isnan(f) or math.isinf(f):
            return None
        return f
    except (ValueError, TypeError):
        return None


def _get(df, label, col=0):
    """재무제표 DataFrame에서 안전하게 값 추출."""
    if df is None or df.empty:
        return None
    if label not in df.index:
        return None
    if df.shape[1] <= col:
        return None
    return _safe(df.loc[label].iloc[col])


def _compute_piotroski(annual_inc, annual_bs, cf) -> int | None:
    """
    Piotroski F-Score (0~9).
    연간 재무제표에서 당기/전기 비교.
    """
    if annual_inc is None or annual_inc.empty:
        return None
    if annual_bs is None or annual_bs.empty:
        return None
    if annual_inc.shape[1] < 2 or annual_bs.shape[1] < 2:
        return None

    score = 0

    ni_cur = _get(annual_inc, "Net Income")
    ta_cur = _get(annual_bs, "Total Assets")
    ta_prev = _get(annual_bs, "Total Assets", 1)

    # ── 수익성 (4점) ──
    # 1. ROA > 0
    if ni_cur is not None and ta_cur and ta_cur > 0:
        if ni_cur / ta_cur > 0:
            score += 1

    # 2. 영업현금흐름 > 0
    cfo_cur = _get(cf, "Operating Cash Flow")
    if cfo_cur is None:
        cfo_cur = _get(cf, "Cash Flow From Continuing Operating Activities")
    if cfo_cur is not None and cfo_cur > 0:
        score += 1

    # 3. ROA 증가 (당기 > 전기)
    ni_prev = _get(annual_inc, "Net Income", 1)
    if (ni_cur is not None and ni_prev is not None
            and ta_cur and ta_prev and ta_cur > 0 and ta_prev > 0):
        if ni_cur / ta_cur > ni_prev / ta_prev:
            score += 1

    # 4. CFO > Net Income (발생주의 품질)
    if cfo_cur is not None and ni_cur is not None:
        if cfo_cur > ni_cur:
            score += 1

    # ── 재무건전성 (3점) ──
    # 5. 장기부채비율 감소
    ltd_cur = _get(annual_bs, "Long Term Debt")
    ltd_prev = _get(annual_bs, "Long Term Debt", 1)
    if ltd_cur is None:
        ltd_cur = _get(annual_bs, "Total Debt")
    if ltd_prev is None:
        ltd_prev = _get(annual_bs, "Total Debt", 1)
    if ta_cur and ta_prev and ta_cur > 0 and ta_prev > 0:
        ratio_cur = (ltd_cur or 0) / ta_cur
        ratio_prev = (ltd_prev or 0) / ta_prev
        if ratio_cur <= ratio_prev:
            score += 1

    # 6. 유동비율 증가
    ca_cur = _get(annual_bs, "Current Assets")
    cl_cur = _get(annual_bs, "Current Liabilities")
    ca_prev = _get(annual_bs, "Current Assets", 1)
    cl_prev = _get(annual_bs, "Current Liabilities", 1)
    if ca_cur and cl_cur and ca_prev and cl_prev and cl_cur > 0 and cl_prev > 0:
        if ca_cur / cl_cur >= ca_prev / cl_prev:
            score += 1

    # 7. 신주 미발행 (주식수 비증가)
    so_cur = _get(annual_bs, "Share Issued") or _get(annual_bs, "Ordinary Shares Number")
    so_prev = (_get(annual_bs, "Share Issued", 1)
               or _get(annual_bs, "Ordinary Shares Number", 1))
    if so_cur is not None and so_prev is not None:
        if so_cur <= so_prev:
            score += 1

    # ── 효율성 (2점) ──
    # 8. 매출총이익률 증가
    gp_cur = _get(annual_inc, "Gross Profit")
    rev_cur = _get(annual_inc, "Total Revenue")
    gp_prev = _get(annual_inc, "Gross Profit", 1)
    rev_prev = _get(annual_inc, "Total Revenue", 1)
    if gp_cur and rev_cur and gp_prev and rev_prev and rev_cur > 0 and rev_prev > 0:
        if gp_cur / rev_cur >= gp_prev / rev_prev:
            score += 1

    # 9. 자산회전율 증가
    if rev_cur and rev_prev and ta_cur and ta_prev and ta_cur > 0 and ta_prev > 0:
        if rev_cur / ta_cur >= rev_prev / ta_prev:
            score += 1

    return score


def _compute_altman_z(annual_inc, annual_bs, market_cap) -> float | None:
    """
    Altman Z-Score.
    Z = 1.2·A + 1.4·B + 3.3·C + 0.6·D + 1.0·E
    A = 운전자본/총자산, B = 이익잉여금/총자산, C = EBIT/총자산,
    D = 시가총액/총부채, E = 매출/총자산
    """
    ta = _get(annual_bs, "Total Assets")
    if not ta or ta <= 0:
        return None

    ca = _get(annual_bs, "Current Assets")
    cl = _get(annual_bs, "Current Liabilities")
    re = _get(annual_bs, "Retained Earnings")
    tl = (_get(annual_bs, "Total Liabilities Net Minority Interest")
          or _get(annual_bs, "Total Liabilities"))
    ebit = (_get(annual_inc, "EBIT")
            or _get(annual_inc, "Operating Income"))
    rev = _get(annual_inc, "Total Revenue")

    if ca is None or cl is None:
        return None

    a = (ca - cl) / ta                                     # 운전자본/총자산
    b = (re / ta) if re is not None else 0.0                # 이익잉여금/총자산
    c = (ebit / ta) if ebit is not None else 0.0            # EBIT/총자산
    d = (market_cap / tl) if market_cap and tl and tl > 0 else 0.0
    e = (rev / ta) if rev is not None else 0.0              # 매출/총자산

    z = 1.2 * a + 1.4 * b + 3.3 * c + 0.6 * d + 1.0 * e
    return round(z, 2)


def compute_deep_financials(
    symbol: str, market_cap: float | None = None
) -> dict | None:
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
            current = _safe(qi.loc["Operating Income"].iloc[0])
            yoy = _safe(qi.loc["Operating Income"].iloc[4])
            if current is not None and yoy is not None and abs(yoy) > 0:
                result["op_income_growth"] = round(
                    (current - yoy) / abs(yoy), 4
                )

        # 차입금 증가율 (YoY)
        if (
            qb is not None
            and "Total Debt" in qb.index
            and qb.shape[1] >= 5
        ):
            current_debt = _safe(qb.loc["Total Debt"].iloc[0])
            yoy_debt = _safe(qb.loc["Total Debt"].iloc[4])
            if current_debt is not None and yoy_debt is not None and abs(yoy_debt) > 0:
                result["debt_growth"] = round(
                    (current_debt - yoy_debt) / abs(yoy_debt), 4
                )

        # 이자보상배율 (TTM)
        annual = ticker.income_stmt
        if annual is not None and not annual.empty:
            has_oi = "Operating Income" in annual.index
            has_ie = "Interest Expense" in annual.index
            if has_oi and has_ie:
                op_ttm = _safe(annual.loc["Operating Income"].iloc[0])
                int_exp = _safe(annual.loc["Interest Expense"].iloc[0])
                if op_ttm is not None and int_exp is not None and abs(int_exp) > 0:
                    result["interest_coverage"] = round(
                        op_ttm / abs(int_exp), 2
                    )

        # ROIC (NOPAT / 투하자본)
        if annual is not None and qb is not None:
            try:
                if "Operating Income" in annual.index:
                    oi = _safe(annual.loc["Operating Income"].iloc[0])
                    if oi is not None:
                        tax_rate = 0.21  # 미국 법인세율 근사
                        nopat = oi * (1 - tax_rate)
                    else:
                        nopat = None

                    total_equity = None
                    total_debt_val = None
                    cash = None

                    if "Stockholders Equity" in qb.index:
                        total_equity = _safe(qb.loc["Stockholders Equity"].iloc[0])
                    if "Total Debt" in qb.index:
                        total_debt_val = _safe(qb.loc["Total Debt"].iloc[0])
                    if "Cash And Cash Equivalents" in qb.index:
                        cash = _safe(qb.loc["Cash And Cash Equivalents"].iloc[0])

                    if nopat is not None and total_equity and total_debt_val:
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

        # ── Piotroski F-Score & Altman Z-Score ──
        annual_bs = ticker.balance_sheet
        cf = ticker.cashflow

        fscore = _compute_piotroski(annual, annual_bs, cf)
        if fscore is not None:
            result["piotroski_score"] = fscore

        zscore = _compute_altman_z(annual, annual_bs, market_cap)
        if zscore is not None:
            result["altman_z_score"] = zscore

        # 기준일
        if qi.shape[1] > 0 and hasattr(qi.columns[0], "strftime"):
            result["asof_deep_financial"] = qi.columns[0].strftime(
                "%Y-%m-%d"
            )

        return result

    except Exception as e:
        logger.debug(f"[DeepFinancial] {symbol} 실패: {e}")
        return None


def batch_deep_financials(
    top_symbols: list[str],
    mcap_map: dict[str, float] | None = None,
) -> list[dict]:
    """
    상위 500종목 심화 재무 배치.
    500 x ~2초 = ~17분.
    mcap_map: {symbol: market_cap} — Altman Z-Score 산출용.
    """
    if mcap_map is None:
        mcap_map = {}
    results = []
    total = min(len(top_symbols), 500)

    for i, sym in enumerate(top_symbols[:500]):
        data = compute_deep_financials(sym, mcap_map.get(sym))
        if data:
            results.append(data)
        time.sleep(1)

        if (i + 1) % 100 == 0 or i == total - 1:
            logger.info(
                f"[DeepFinancial] {i + 1}/{total} 완료 ({len(results)}건)"
            )

    return results
