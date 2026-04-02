"""
yfinance Ticker.info 주간 수집 (repo-A, 월요일).
Priority 종목 3,000건의 Info 데이터를 수집하여 Supabase에 UPSERT.
daily_sync.py에서 분리됨.
"""

import logging
import sys
import time
from datetime import date
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from pipeline.common import send_batch_email
from pipeline.config import YFINANCE_INFO_TOP_N
from pipeline.loaders.supabase_upsert import get_supabase_client, upsert_equities
from pipeline.sources.priority_builder import build_daily_priority_list
from pipeline.sources.yfinance_bulk import fetch_ticker_info

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"Weekly Info Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    supabase = get_supabase_client()

    # 1. Priority 종목 리스트 조회
    try:
        priority_list = build_daily_priority_list(supabase)
    except Exception:
        logger.error("[Info] Priority 빌드 실패 — 종료")
        sys.exit(1)

    target_symbols = priority_list[:YFINANCE_INFO_TOP_N]
    logger.info(f"[Info] 대상: {len(target_symbols)}종목")

    # 2. yfinance Ticker.info 호출
    info_data = fetch_ticker_info(target_symbols)
    logger.info(f"[Info] 수집 완료: {len(info_data)}건")

    if not info_data:
        logger.warning("[Info] 수집 데이터 없음 — 종료")
        return

    # 3. Supabase UPSERT (Info 컬럼만)
    records = []
    for sym, info in info_data.items():
        if isinstance(info, dict) and info:
            info["symbol"] = sym
            records.append(info)

    if records:
        upsert_count = upsert_equities(supabase, records)
        logger.info(f"[Info] UPSERT 완료: {upsert_count}건")

    elapsed = time.time() - start_time

    summary = (
        f"Weekly Info Sync 완료\n"
        f"대상: {len(target_symbols)}종목\n"
        f"수집: {len(info_data)}건\n"
        f"UPSERT: {len(records)}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] Weekly Info 완료 ({date.today()})",
        summary,
    )

    logger.info("=" * 60)
    logger.info(f"Weekly Info Sync 완료 — {elapsed / 60:.1f}분")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
