"""
산출 필드 계산.
벤더 값을 기반으로 파생 지표를 Python에서 미리 산출.
"""

import math


def compute_derived_fields(row: dict) -> dict:
    """RDBMS 연산 부하 0을 위해 Python에서 미리 계산."""
    price = row.get("price")
    prev = row.get("prev_close")
    h52 = row.get("week52_high")
    l52 = row.get("week52_low")
    vol = row.get("volume")
    target = row.get("target_mean")
    open_p = row.get("open_price")
    avg_vol = row.get("avg_volume_10d")
    fcf = row.get("free_cashflow")
    mcap = row.get("market_cap")

    # 등락률
    if price and prev and prev > 0:
        row["change_pct"] = round((price - prev) / prev * 100, 4)

    # 52주 고점 대비 (%)
    if price and h52 and h52 > 0:
        row["pct_from_52h"] = round((price - h52) / h52 * 100, 4)

    # 52주 저점 대비 (%)
    if price and l52 and l52 > 0:
        row["pct_from_52l"] = round((price - l52) / l52 * 100, 4)

    # 거래대금
    if price and vol:
        row["turnover"] = round(price * vol, 2)

    # 목표가 괴리율
    if price and target and price > 0:
        row["target_upside_pct"] = round(
            (target - price) / price * 100, 4
        )

    # FCF 수익률
    if fcf and mcap and mcap > 0:
        row["fcf_yield"] = round(fcf / mcap, 4)

    # 상대거래량
    if vol and avg_vol and avg_vol > 0:
        row["relative_volume"] = round(vol / avg_vol, 2)

    # 갭 (%)
    if open_p and prev and prev > 0:
        row["gap_pct"] = round((open_p - prev) / prev * 100, 4)

    # 시가대비 등락
    if price and open_p and open_p > 0:
        row["change_from_open"] = round(
            (price - open_p) / open_p * 100, 4
        )

    # SMA 괴리율
    for ma_key, pct_key in [
        ("ma_20", "sma20_pct"),
        ("ma_50", "sma50_pct"),
        ("ma_200", "sma200_pct"),
    ]:
        ma_val = row.get(ma_key)
        if price and ma_val and ma_val > 0:
            row[pct_key] = round((price - ma_val) / ma_val * 100, 4)

    # Graham Number
    eps = row.get("eps_ttm")
    bvps = row.get("book_value")
    if eps and bvps and eps > 0 and bvps > 0:
        row["graham_number"] = round(math.sqrt(22.5 * eps * bvps), 4)
        if price and price > 0:
            row["graham_upside_pct"] = round(
                (row["graham_number"] - price) / price * 100, 4
            )

    # PEG (yfinance 미제공 시 직접 계산)
    pe = row.get("pe_ttm")
    eg = row.get("earnings_growth")
    if not row.get("peg_ratio") and pe and eg and pe > 0 and eg > 0:
        eg_pct = eg * 100  # 0.15 → 15
        row["peg_ratio"] = round(pe / eg_pct, 4)

    # P/FCF (yfinance 미제공 시 직접 계산)
    if not row.get("pfcf_ratio") and mcap and fcf and fcf > 0:
        row["pfcf_ratio"] = round(mcap / fcf, 4)

    # DCF — 간이 10년 할인현금흐름 모델
    shares = row.get("shares_outstanding")
    if not row.get("dcf_value") and fcf and shares and fcf > 0 and shares > 0:
        g = eg or row.get("revenue_growth")
        if g is not None and g > 0:
            g = min(g, 0.25)  # 성장률 상한 25%
            r = 0.10           # 할인율 10%
            terminal_g = 0.03  # 영구 성장률 3%

            pv_sum = 0.0
            projected_fcf = float(fcf)
            for t in range(1, 11):
                projected_fcf *= (1 + g)
                pv_sum += projected_fcf / ((1 + r) ** t)

            terminal_value = projected_fcf * (1 + terminal_g) / (r - terminal_g)
            pv_sum += terminal_value / ((1 + r) ** 10)

            dcf_per_share = pv_sum / shares
            row["dcf_value"] = round(dcf_per_share, 4)

            if price and price > 0:
                row["dcf_upside_pct"] = round(
                    (dcf_per_share - price) / price * 100, 4
                )

    return row
