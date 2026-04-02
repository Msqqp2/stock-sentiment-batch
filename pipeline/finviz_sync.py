"""
Finviz 유니크 데이터 수집 (repo-B, 금요일 주1회).
finvizfinance: FinViz 웹 스크래핑, API 키 불필요, 무료.
300종목 × ~8초 = ~40분.

수집 항목:
1) 애널리스트 업/다운그레이드 히스토리
2) 회사 설명 텍스트
3) 동종업계 유사 종목
4) 해당 종목을 보유한 ETF 목록
"""

import json
import logging
import sys
import time
from datetime import date, datetime, timezone
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from finvizfinance.quote import finvizfinance

from pipeline.common import get_sentiment_symbols, send_batch_email
from pipeline.loaders.supabase_upsert import get_supabase_client

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)

FINVIZ_TOP_N = 300
FINVIZ_SLEEP = 3  # IP 차단 방지


def _upsert_ratings_history(supabase, symbol: str, ratings_df, asof: str):
    """analyst_ratings_history 테이블에 DELETE → INSERT."""
    try:
        supabase.table("analyst_ratings_history").delete().eq(
            "symbol", symbol
        ).execute()
    except Exception:
        pass

    if ratings_df is None or ratings_df.empty:
        return

    rows = []
    for _, row in ratings_df.iterrows():
        rows.append({
            "symbol": symbol,
            "date": str(row.get("Date", "")),
            "action": str(row.get("Action", "")),
            "analyst": str(row.get("Analyst", "")),
            "rating": str(row.get("Rating", "")),
            "price_target": str(row.get("Price Target", "")),
            "asof": asof,
        })

    if rows:
        try:
            supabase.table("analyst_ratings_history").upsert(
                rows, on_conflict="symbol,date,analyst,action"
            ).execute()
        except Exception as e:
            logger.warning(f"[Finviz] {symbol} ratings upsert 실패: {e}")


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"Finviz Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    supabase = get_supabase_client()

    # 거래대금 Top 300 보통주
    symbols = get_sentiment_symbols(supabase, FINVIZ_TOP_N)
    if not symbols:
        logger.error("[Finviz] 대상 종목 없음 — 종료")
        sys.exit(1)

    now_ts = datetime.now(timezone.utc).isoformat()
    success_count = 0
    fail_count = 0

    for i, sym in enumerate(symbols):
        try:
            stock = finvizfinance(sym)

            # 1) 애널리스트 업/다운그레이드 히스토리
            try:
                ratings = stock.ticker_outer_ratings()
            except Exception:
                ratings = None

            _upsert_ratings_history(supabase, sym, ratings, now_ts)

            # 2) 회사 설명
            try:
                description = stock.ticker_description()
            except Exception:
                description = None

            # 3) 동종업계 유사 종목
            try:
                peers = stock.ticker_peer()
            except Exception:
                peers = None

            # 4) 해당 종목을 보유한 ETF 목록
            try:
                etf_holders = stock.ticker_etf_holders()
            except Exception:
                etf_holders = None

            # latest_equities 업데이트
            update_row = {"asof_finviz": now_ts}
            if description:
                update_row["description"] = description
            if peers and isinstance(peers, list):
                update_row["peers"] = json.dumps(peers)
            if etf_holders and isinstance(etf_holders, list):
                update_row["etf_holders"] = json.dumps(etf_holders)

            if len(update_row) > 1:  # asof 외 데이터가 있을 때만
                try:
                    supabase.table("latest_equities").update(
                        update_row
                    ).eq("symbol", sym).execute()
                except Exception as e:
                    logger.debug(f"[Finviz] {sym} update 실패: {e}")

            success_count += 1
        except Exception as e:
            logger.warning(f"[Finviz] {sym} 실패: {e}")
            fail_count += 1
            continue

        if (i + 1) % 50 == 0:
            logger.info(
                f"[Finviz] {i + 1}/{len(symbols)} 완료 ({success_count}건 성공)"
            )

        time.sleep(FINVIZ_SLEEP)

    elapsed = time.time() - start_time

    summary = (
        f"Finviz Sync 완료\n"
        f"대상: {len(symbols)}종목\n"
        f"성공: {success_count}건, 실패: {fail_count}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] Finviz Sync 완료 ({date.today()})",
        summary,
    )

    logger.info("=" * 60)
    logger.info(f"Finviz Sync 완료 — {elapsed / 60:.1f}분")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
