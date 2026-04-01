"""
StockGeist 센티먼트 수집 (repo-B 수요일 주1회).
2,000종목 × 1 호출 = 2,000크레딧/주, 월 ~10,000 (한도 이내).
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
from pipeline.config import STOCKGEIST_BASE_URL, STOCKGEIST_TOKEN, STOCKGEIST_TOP_N
from pipeline.loaders.supabase_upsert import get_supabase_client

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def fetch_stockgeist_sentiment(symbol: str) -> dict | None:
    """개별 종목 StockGeist 센티먼트 조회."""
    headers = {"Authorization": f"Bearer {STOCKGEIST_TOKEN}"}
    try:
        resp = requests.get(
            f"{STOCKGEIST_BASE_URL}/stock/{symbol}/sentiment",
            headers=headers,
            timeout=15,
        )
        if resp.status_code == 429:
            logger.warning("[StockGeist] Rate limit — 5초 대기")
            time.sleep(5)
            resp = requests.get(
                f"{STOCKGEIST_BASE_URL}/stock/{symbol}/sentiment",
                headers=headers,
                timeout=15,
            )
        if resp.status_code != 200:
            return None
        return resp.json()
    except Exception as e:
        logger.debug(f"[StockGeist] {symbol} 실패: {e}")
        return None


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"StockGeist Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    if not STOCKGEIST_TOKEN:
        logger.info("[StockGeist] API 토큰 미설정 — 스킵")
        return

    supabase = get_supabase_client()
    symbols = get_sentiment_symbols(supabase, STOCKGEIST_TOP_N)

    if not symbols:
        logger.error("[StockGeist] 대상 종목 없음 — 종료")
        sys.exit(1)

    now_ts = datetime.now(timezone.utc).isoformat()
    updated = 0
    failed = 0

    for i, sym in enumerate(symbols):
        data = fetch_stockgeist_sentiment(sym)

        if data:
            sentiment = data.get("sentiment", {})
            row = {
                "sg_sentiment_pos": sentiment.get("positive"),
                "sg_sentiment_neg": sentiment.get("negative"),
                "sg_sentiment_neu": sentiment.get("neutral"),
                "sg_emotionality": data.get("emotionality"),
                "sg_mention_count": data.get("mention_count"),
                "sg_asof": now_ts,
            }
            # None 값 제거
            row = {k: v for k, v in row.items() if v is not None}

            if row:
                try:
                    supabase.table("latest_equities").update(row).eq(
                        "symbol", sym
                    ).execute()
                    updated += 1
                except Exception as e:
                    logger.debug(f"[StockGeist] {sym} upsert 실패: {e}")
                    failed += 1
            else:
                failed += 1
        else:
            failed += 1

        if (i + 1) % 200 == 0:
            logger.info(
                f"[StockGeist] {i + 1}/{len(symbols)} 완료 ({updated}건 성공)"
            )

        time.sleep(0.5)

    elapsed = time.time() - start_time

    summary = (
        f"StockGeist Sync 완료\n"
        f"대상: {len(symbols)}종목\n"
        f"성공: {updated}건, 실패: {failed}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] StockGeist Sync 완료 ({date.today()})",
        summary,
    )


if __name__ == "__main__":
    main()
