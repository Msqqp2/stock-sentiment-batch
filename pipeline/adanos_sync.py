"""
Adanos Polymarket 센티먼트 수집 (repo-B 화요일 주1회).
500종목 ÷ 10 = 50회 호출, 월 ~200건 (한도 250건 이내).
"""

import logging
import sys
import time
from datetime import date, datetime, timezone
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

import requests

from pipeline.common import get_sentiment_symbols, send_batch_email
from pipeline.config import (
    ADANOS_API_KEY,
    ADANOS_BASE_URL,
    ADANOS_BATCH_SIZE,
    ADANOS_TOP_N,
)
from pipeline.loaders.supabase_upsert import get_supabase_client

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def fetch_compare(tickers: list[str], days: int = 7) -> list[dict]:
    """Adanos /compare 엔드포인트 (최대 10종목)."""
    headers = {"X-API-Key": ADANOS_API_KEY}
    params = {"tickers": ",".join(tickers), "days": days}
    try:
        resp = requests.get(
            f"{ADANOS_BASE_URL}/compare",
            headers=headers,
            params=params,
            timeout=30,
        )
        if resp.status_code == 429:
            logger.warning("[Adanos] Rate limit — 60초 대기")
            time.sleep(60)
            resp = requests.get(
                f"{ADANOS_BASE_URL}/compare",
                headers=headers,
                params=params,
                timeout=30,
            )
        resp.raise_for_status()
        data = resp.json()
        return data.get("stocks", [])
    except Exception as e:
        logger.debug(f"[Adanos] compare 실패: {e}")
        return []


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"Adanos Polymarket Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    if not ADANOS_API_KEY:
        logger.info("[Adanos] API 키 미설정 — 스킵")
        return

    supabase = get_supabase_client()
    symbols = get_sentiment_symbols(supabase, ADANOS_TOP_N)

    if not symbols:
        logger.error("[Adanos] 대상 종목 없음 — 종료")
        sys.exit(1)

    now_ts = datetime.now(timezone.utc).isoformat()
    updated = 0
    failed = 0
    api_calls = 0

    # 10종목씩 배치 호출
    chunks = [
        symbols[i : i + ADANOS_BATCH_SIZE]
        for i in range(0, len(symbols), ADANOS_BATCH_SIZE)
    ]

    for i, chunk in enumerate(chunks):
        stocks = fetch_compare(chunk)
        api_calls += 1

        for stock in stocks:
            ticker = stock.get("ticker")
            if not ticker:
                continue

            row = {
                "pm_buzz_score": stock.get("buzz_score"),
                "pm_trend": stock.get("trend"),
                "pm_sentiment_score": stock.get("sentiment_score"),
                "pm_bullish_pct": stock.get("bullish_pct"),
                "pm_bearish_pct": stock.get("bearish_pct"),
                "pm_trade_count": stock.get("trade_count"),
                "pm_market_count": stock.get("market_count"),
                "pm_unique_traders": stock.get("unique_traders"),
                "pm_total_liquidity": stock.get("total_liquidity"),
                "pm_asof": now_ts,
            }
            row = {k: v for k, v in row.items() if v is not None}

            if row:
                try:
                    supabase.table("latest_equities").update(row).eq(
                        "symbol", ticker
                    ).execute()
                    updated += 1
                except Exception as e:
                    logger.debug(f"[Adanos] {ticker} upsert 실패: {e}")
                    failed += 1

        if not stocks:
            failed += len(chunk)

        if (i + 1) % 10 == 0:
            logger.info(
                f"[Adanos] {i + 1}/{len(chunks)} 배치 완료 "
                f"({updated}건 성공, API {api_calls}건)"
            )

        time.sleep(0.6)  # 분당 100건 제한

    elapsed = time.time() - start_time

    summary = (
        f"Adanos Polymarket Sync 완료\n"
        f"대상: {len(symbols)}종목\n"
        f"성공: {updated}건, 실패: {failed}건\n"
        f"API 호출: {api_calls}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] Adanos Sync 완료 ({date.today()})",
        summary,
    )


if __name__ == "__main__":
    main()
