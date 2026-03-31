"""
yfinance 데이터 병합.
Universe 기본 정보 + yfinance 가격 + yfinance Ticker.info 통합.
"""

import logging
from datetime import date

logger = logging.getLogger(__name__)


def merge_universe_with_prices(
    universe: list[dict],
    price_data: dict[str, dict],
    info_data: dict[str, dict],
) -> list[dict]:
    """
    Universe 기본 정보 + yfinance 가격 + yfinance info 병합.
    우선순위: yfinance info > yfinance price > Universe 기본.
    """
    records = []
    today = date.today().isoformat()

    for item in universe:
        sym = item["symbol"]
        # yfinance 가격 데이터에서 실제 거래일 추출 (없으면 오늘 날짜)
        yf_date = price_data.get(sym, {}).get("data_date")
        record = {
            "symbol": sym,
            "name": item.get("name", ""),
            "asset_type": item.get("asset_type", "stock"),
            "exchange": item.get("exchange", ""),
            "is_delisted": False,
            "data_date": yf_date or today,
        }

        # yfinance 가격 데이터 (하위 우선순위)
        yf_price = price_data.get(sym, {})
        if yf_price:
            for key, val in yf_price.items():
                if val is not None:
                    record[key] = val

        # yfinance Ticker.info (최상위 우선순위)
        yf_info = info_data.get(sym, {})
        if yf_info:
            for key, val in yf_info.items():
                if val is not None and key != "symbol":
                    record[key] = val

        records.append(record)

    logger.info(
        f"[Merge] {len(records)}건 병합 완료 "
        f"(price: {len(price_data)}, info: {len(info_data)})"
    )
    return records
