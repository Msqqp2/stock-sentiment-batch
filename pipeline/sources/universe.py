"""
Universe Sync — NASDAQ Trader 공식 파일 기반.
nasdaqtraded.txt: NYSE/NASDAQ/AMEX 전종목 + ETF 플래그 제공.
API 키 불필요, 일 1회 HTTP GET.
"""

import logging
import urllib.request

import pandas as pd

from pipeline.config import TARGET_EXCHANGES

logger = logging.getLogger(__name__)

NASDAQ_TRADED_URL = "https://www.nasdaqtrader.com/dynamic/SymDir/nasdaqtraded.txt"

# nasdaqtraded.txt Listing Exchange 코드 → 표준 거래소명
EXCHANGE_MAP = {
    "Q": "NASDAQ",
    "N": "NYSE",
    "A": "AMEX",
    "P": "NYSE",   # NYSE ARCA → NYSE로 통합
    "Z": "BATS",
}


def fetch_universe() -> list[dict]:
    """
    NASDAQ Trader nasdaqtraded.txt에서 전종목 리스트 취득.
    Returns: [{"symbol": ..., "name": ..., "exchange": ..., "asset_type": ...}, ...]
    """
    req = urllib.request.Request(
        NASDAQ_TRADED_URL,
        headers={"User-Agent": "StockScreener/1.0"},
    )
    data = urllib.request.urlopen(req, timeout=30).read().decode("utf-8")
    lines = data.strip().split("\n")

    header = [h.strip() for h in lines[0].split("|")]
    rows = [
        [f.strip() for f in line.split("|")]
        for line in lines[1:]
        if not line.startswith("File Creation")
    ]

    df = pd.DataFrame(rows, columns=header)
    df = df[(df["Nasdaq Traded"] == "Y") & (df["Test Issue"] == "N")]

    results = []
    for _, row in df.iterrows():
        exchange_code = row.get("Listing Exchange", "")
        exchange = EXCHANGE_MAP.get(exchange_code, exchange_code)
        if exchange not in TARGET_EXCHANGES:
            continue

        symbol = row.get("Symbol", "").strip()
        if not symbol or "$" in symbol or "." in symbol:
            continue  # 우선주, 워런트 등 제외

        is_etf = row.get("ETF", "N") == "Y"
        results.append({
            "symbol": symbol,
            "name": row.get("Security Name", ""),
            "exchange": exchange,
            "asset_type": "etf" if is_etf else "stock",
        })

    logger.info(
        f"[Universe] NASDAQ Trader: {len(df)}건 수신 → {len(results)}건 필터 "
        f"(NYSE/NASDAQ/AMEX)"
    )
    return results
