"""
ETF Holdings → 축약 JSONB 변환.
상위 50개, 용량 절약 축약키 사용.
"""

import logging

logger = logging.getLogger(__name__)


def transform_holdings(raw_holdings: list[dict]) -> list[dict]:
    """
    FMP ETF Holdings 응답 → 축약 JSONB 배열.
    [{"s":"AAPL","w":7.15,"v":31063844227,"n":"Apple Inc."}]
    """
    result = []
    for h in raw_holdings[:50]:
        entry = {}
        sym = h.get("asset", "")
        if sym:
            entry["s"] = sym
        weight = h.get("weightPercentage")
        if weight is not None:
            entry["w"] = round(float(weight), 2)
        value = h.get("marketValue")
        if value is not None:
            entry["v"] = int(value)
        name = h.get("name", "")
        if name:
            entry["n"] = name

        if entry.get("s"):
            result.append(entry)

    return result


def build_etf_update(
    symbol: str,
    etf_info: dict | None,
    raw_holdings: list[dict],
) -> dict:
    """ETF 메타 + Holdings를 하나의 업데이트 레코드로."""
    record = {"symbol": symbol}

    if etf_info:
        record["expense_ratio"] = etf_info.get("expenseRatio")
        record["nav"] = etf_info.get("navPrice")
        record["asset_class"] = etf_info.get("assetClass")
        record["inception_date"] = etf_info.get("inceptionDate")
        record["aum"] = etf_info.get("aum")
        record["holdings_count"] = etf_info.get("holdingsCount")
        record["index_tracked"] = etf_info.get("indexTracked")
        record["is_active"] = etf_info.get("isActivelyManaged", False)

    if raw_holdings:
        record["holdings"] = transform_holdings(raw_holdings)

    return record
