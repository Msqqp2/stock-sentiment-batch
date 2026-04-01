"""
Finnhub Sentiment + Insider + Recommendation 수집 (repo-B daily).
500종목 × 4 엔드포인트 = 2,000건/일, ~33분.
"""

import logging
import sys
import time
from datetime import date, datetime, timezone
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

import requests

from pipeline.common import (
    get_sentiment_symbols,
    is_us_trading_day,
    send_batch_email,
)
from pipeline.config import (
    FINNHUB_API_KEY,
    FINNHUB_BASE_URL,
    FINNHUB_RATE_LIMIT_SLEEP,
    FINNHUB_SENTIMENT_TOP_N,
)
from pipeline.loaders.supabase_upsert import get_supabase_client

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def _fh_get(endpoint: str, params: dict | None = None) -> dict | list | None:
    """Finnhub API GET 요청."""
    url = f"{FINNHUB_BASE_URL}{endpoint}"
    headers = {"X-Finnhub-Token": FINNHUB_API_KEY}
    try:
        resp = requests.get(url, headers=headers, params=params, timeout=15)
        if resp.status_code == 429:
            logger.warning("[Finnhub] Rate limit 도달 — 60초 대기")
            time.sleep(60)
            resp = requests.get(url, headers=headers, params=params, timeout=15)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.debug(f"[Finnhub] {endpoint} 실패: {e}")
        return None


def fetch_social_sentiment(symbol: str) -> dict:
    """소셜 센티먼트 (Reddit + Twitter)."""
    today = date.today()
    data = _fh_get(
        "/stock/social-sentiment",
        {"symbol": symbol, "from": today.isoformat(), "to": today.isoformat()},
    )
    if not data:
        return {}

    reddit = data.get("reddit", [])
    twitter = data.get("twitter", [])

    total_mention = sum(r.get("mention", 0) for r in reddit + twitter)
    pos_score = sum(r.get("positiveMention", 0) for r in reddit + twitter)
    neg_score = sum(r.get("negativeMention", 0) for r in reddit + twitter)

    total = pos_score + neg_score
    sentiment = (pos_score - neg_score) / total if total > 0 else 0

    return {
        "fh_social_sentiment": round(sentiment, 4),
        "fh_social_positive": pos_score,
        "fh_social_negative": neg_score,
    }


def fetch_insider_sentiment(symbol: str) -> dict:
    """Insider Sentiment (MSPR)."""
    today = date.today()
    year_start = f"{today.year}-01-01"
    data = _fh_get(
        "/stock/insider-sentiment",
        {"symbol": symbol, "from": year_start, "to": today.isoformat()},
    )
    if not data or not data.get("data"):
        return {}

    latest = data["data"][-1]
    return {
        "fh_insider_mspr": latest.get("mspr"),
        "fh_insider_change": latest.get("change"),
    }


def fetch_insider_transactions(symbol: str) -> dict:
    """내부자 거래 내역 (Form 3, 4, 5 기반)."""
    data = _fh_get("/stock/insider-transactions", {"symbol": symbol})
    if not data or not data.get("data"):
        return {}

    txns = data["data"]
    buys = sum(1 for t in txns if t.get("change", 0) > 0)
    sells = sum(1 for t in txns if t.get("change", 0) < 0)

    return {
        "fh_insider_buy_count": buys,
        "fh_insider_sell_count": sells,
    }


def fetch_recommendation(symbol: str) -> dict:
    """애널리스트 투자의견 (Finnhub)."""
    data = _fh_get("/stock/recommendation", {"symbol": symbol})
    if not data or not isinstance(data, list) or len(data) == 0:
        return {}

    latest = data[0]
    return {
        "fh_rec_buy": latest.get("buy"),
        "fh_rec_hold": latest.get("hold"),
        "fh_rec_sell": latest.get("sell"),
        "fh_rec_strong_buy": latest.get("strongBuy"),
        "fh_rec_strong_sell": latest.get("strongSell"),
        "fh_rec_period": latest.get("period"),
    }


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"Finnhub Sentiment Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    if not FINNHUB_API_KEY:
        logger.info("[Finnhub] API 키 미설정 — 스킵")
        return

    if not is_us_trading_day():
        logger.info("[Finnhub] 미국 비영업일 — 스킵")
        return

    supabase = get_supabase_client()
    symbols = get_sentiment_symbols(supabase, FINNHUB_SENTIMENT_TOP_N)

    if not symbols:
        logger.error("[Finnhub] 대상 종목 없음 — 종료")
        sys.exit(1)

    now_ts = datetime.now(timezone.utc).isoformat()
    updated = 0
    failed = 0
    api_calls = 0

    for i, sym in enumerate(symbols):
        row = {"symbol": sym, "fh_asof": now_ts}

        # 3개 엔드포인트 호출 (social sentiment 제거 — Finnhub 무료플랜 데이터 미제공)
        mspr = fetch_insider_sentiment(sym)
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        txns = fetch_insider_transactions(sym)
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        rec = fetch_recommendation(sym)
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        row.update(mspr)
        row.update(txns)
        row.update(rec)

        has_data = any(
            row.get(k) is not None
            for k in ("fh_insider_mspr", "fh_rec_buy")
        )

        if has_data:
            try:
                supabase.table("latest_equities").update(
                    {k: v for k, v in row.items() if k != "symbol"}
                ).eq("symbol", sym).execute()
                updated += 1
            except Exception as e:
                logger.debug(f"[Finnhub] {sym} upsert 실패: {e}")
                failed += 1
        else:
            failed += 1

        if (i + 1) % 50 == 0:
            logger.info(
                f"[Finnhub] {i + 1}/{len(symbols)} 완료 "
                f"({updated}건 성공, API {api_calls}건)"
            )

    elapsed = time.time() - start_time

    summary = (
        f"Finnhub Sentiment Sync 완료\n"
        f"대상: {len(symbols)}종목\n"
        f"성공: {updated}건, 실패: {failed}건\n"
        f"API 호출: {api_calls}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] Finnhub Sync 완료 ({date.today()})",
        summary,
    )

    logger.info("=" * 60)
    logger.info(f"Finnhub Sentiment Sync 완료 — {elapsed / 60:.1f}분")
    logger.info("=" * 60)


if __name__ == "__main__":
    main()
