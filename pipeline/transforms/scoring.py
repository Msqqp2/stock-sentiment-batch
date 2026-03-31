"""
종합 점수 산출 — 밸류/퀄리티/모멘텀/성장/종합 (0~100 백분위).
전종목 대상으로 배치에서 산출.
"""

import logging

import numpy as np

logger = logging.getLogger(__name__)


def _num(val) -> float | None:
    """안전한 숫자 변환. 문자열/None 등 처리."""
    if val is None:
        return None
    try:
        return float(val)
    except (ValueError, TypeError):
        return None


def compute_scores(records: list[dict]) -> list[dict]:
    """
    전종목 레코드에 대해 4개 하위 점수 + 종합 점수 산출.
    백분위 기반: 해당 지표가 전체 중 상위 몇 %인지.
    """
    stocks = [r for r in records if r.get("asset_type") == "stock"]
    if not stocks:
        return records

    # 각 지표별 값 추출 (None 제외)
    value_scores = _percentile_rank(stocks, _value_composite)
    quality_scores = _percentile_rank(stocks, _quality_composite)
    momentum_scores = _percentile_rank(stocks, _momentum_composite)
    growth_scores = _percentile_rank(stocks, _growth_composite)

    # 점수 할당
    symbol_to_record = {r["symbol"]: r for r in records}

    for i, stock in enumerate(stocks):
        sym = stock["symbol"]
        rec = symbol_to_record.get(sym)
        if not rec:
            continue

        sv = value_scores[i]
        sq = quality_scores[i]
        sm = momentum_scores[i]
        sg = growth_scores[i]

        rec["score_value"] = sv
        rec["score_quality"] = sq
        rec["score_momentum"] = sm
        rec["score_growth"] = sg

        # 종합: 균등 가중 (25% 씩)
        parts = [x for x in [sv, sq, sm, sg] if x is not None]
        if parts:
            rec["score_total"] = round(sum(parts) / len(parts))

    scored = sum(
        1 for r in records if r.get("score_total") is not None
    )
    logger.info(f"[Scoring] {scored}/{len(stocks)} 종목 점수 산출 완료")
    return records


def _value_composite(row: dict) -> float | None:
    """밸류 종합 (낮을수록 좋음 → 역순위)."""
    pe = _num(row.get("pe_ttm"))
    pb = _num(row.get("pb_ratio"))
    ps = _num(row.get("ps_ratio"))
    ev_ebitda = _num(row.get("ev_ebitda"))

    vals = []
    if pe and 0 < pe < 500:
        vals.append(-pe)
    if pb and 0 < pb < 100:
        vals.append(-pb)
    if ps and 0 < ps < 100:
        vals.append(-ps)
    if ev_ebitda and 0 < ev_ebitda < 200:
        vals.append(-ev_ebitda)

    if not vals:
        return None
    return sum(vals) / len(vals)


def _quality_composite(row: dict) -> float | None:
    """퀄리티 종합 (높을수록 좋음). 모든 지표를 0~1 스케일로 정규화."""
    roe = _num(row.get("roe"))
    roa = _num(row.get("roa"))
    margin = _num(row.get("operating_margin"))
    current = _num(row.get("current_ratio"))

    vals = []
    # ROE: 일반적 범위 -0.5~0.5 → 0~1로 정규화
    if roe is not None:
        vals.append(max(0, min((roe + 0.5) / 1.0, 1.0)))
    # ROA: 일반적 범위 -0.2~0.2 → 0~1
    if roa is not None:
        vals.append(max(0, min((roa + 0.2) / 0.4, 1.0)))
    # Operating margin: -0.3~0.5 → 0~1
    if margin is not None:
        vals.append(max(0, min((margin + 0.3) / 0.8, 1.0)))
    # Current ratio: 0~3 → 0~1
    if current is not None and 0 < current < 20:
        vals.append(min(current / 3, 1.0))

    if not vals:
        return None
    return sum(vals) / len(vals)


def _momentum_composite(row: dict) -> float | None:
    """모멘텀 종합 (높을수록 좋음)."""
    change = _num(row.get("change_pct"))
    pct52h = _num(row.get("pct_from_52h"))
    rel_vol = _num(row.get("relative_volume"))
    rsi = _num(row.get("rsi_14"))

    vals = []
    if change is not None:
        vals.append(change / 10)
    if pct52h is not None:
        vals.append(pct52h / 50 + 1)  # -50%→0, 0%→1
    if rel_vol is not None and rel_vol > 0:
        vals.append(min(rel_vol / 3, 1.0))
    if rsi is not None:
        vals.append(rsi / 100)

    if not vals:
        return None
    return sum(vals) / len(vals)


def _growth_composite(row: dict) -> float | None:
    """성장 종합 (높을수록 좋음)."""
    rev_g = _num(row.get("revenue_growth"))
    earn_g = _num(row.get("earnings_growth"))
    op_g = _num(row.get("op_income_growth"))

    vals = []
    if rev_g is not None:
        vals.append(rev_g)
    if earn_g is not None:
        vals.append(earn_g)
    if op_g is not None:
        vals.append(op_g)

    if not vals:
        return None
    return sum(vals) / len(vals)


def _percentile_rank(
    rows: list[dict], composite_fn
) -> list[int | None]:
    """복합 점수 → 백분위 순위 (0~100)."""
    values = [composite_fn(r) for r in rows]

    valid_values = [(i, v) for i, v in enumerate(values) if v is not None]
    if not valid_values:
        return [None] * len(rows)

    sorted_vals = sorted(valid_values, key=lambda x: x[1])
    n = len(sorted_vals)

    result = [None] * len(rows)
    for rank, (idx, _) in enumerate(sorted_vals):
        result[idx] = round(rank / max(n - 1, 1) * 100)

    return result
