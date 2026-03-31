"""
X Stock Sentiment API 소셜 센티먼트 수집.
Nice-to-Have 등급 — 장애 시 핵심 기능 무영향.
"""

import logging
from datetime import date, datetime

import requests

from pipeline.config import (
    X_SENTIMENT_BASE,
    X_SENTIMENT_HEALTH_TIMEOUT,
    X_SENTIMENT_KEY,
    X_SENTIMENT_MAX_CONSECUTIVE_FAILURES,
)

logger = logging.getLogger(__name__)

HEADERS = {"X-API-Key": X_SENTIMENT_KEY}


def check_api_health() -> bool:
    """X Sentiment API 가용성 사전 확인."""
    if not X_SENTIMENT_KEY:
        logger.info("[XSentiment] API Key 미설정 — 스킵")
        return False

    try:
        resp = requests.get(
            f"{X_SENTIMENT_BASE}/trending",
            headers=HEADERS,
            timeout=X_SENTIMENT_HEALTH_TIMEOUT,
        )
        if resp.status_code == 200:
            logger.info("[XSentiment] API 정상 (200 OK)")
            return True
        logger.warning(
            f"[XSentiment] API 비정상 응답: HTTP {resp.status_code}"
        )
        return False
    except requests.exceptions.ConnectionError:
        logger.warning("[XSentiment] API 연결 불가")
        return False
    except requests.exceptions.Timeout:
        logger.warning(
            f"[XSentiment] API 타임아웃 ({X_SENTIMENT_HEALTH_TIMEOUT}초)"
        )
        return False
    except Exception as e:
        logger.warning(f"[XSentiment] API 헬스체크 예외: {e}")
        return False


def get_stock_sentiment(ticker: str) -> dict | None:
    """개별 종목 센티먼트 상세 (1 req)."""
    try:
        resp = requests.get(
            f"{X_SENTIMENT_BASE}/stock/{ticker}",
            headers=HEADERS,
            timeout=10,
        )
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.debug(f"[XSentiment] {ticker} 수집 실패: {e}")
        return None


def batch_scrape_weekly(
    top_symbols: list[str], supabase
) -> dict:
    """
    주간 배치: 상위 250종목 센티먼트 수집.
    Returns: {"status": ..., "collected": int, "failed": int, "reason": str}
    """
    # 헬스체크
    if not check_api_health():
        consecutive = _get_consecutive_failures(supabase)
        _set_consecutive_failures(supabase, consecutive + 1)

        msg = (
            f"[XSentiment] API 불가 — 소셜 센티먼트 수집 스킵 "
            f"(연속 {consecutive + 1}회 실패)"
        )

        if consecutive + 1 >= X_SENTIMENT_MAX_CONSECUTIVE_FAILURES:
            print(f"::warning::{msg} — API 서비스 중단 가능성, 수동 확인 필요")
        else:
            logger.warning(msg)

        return {
            "status": "skipped",
            "collected": 0,
            "failed": 0,
            "reason": msg,
        }

    # 연속 실패 카운터 리셋
    _set_consecutive_failures(supabase, 0)

    # 데이터 수집
    results = []
    failed = 0
    for sym in top_symbols[:250]:
        data = get_stock_sentiment(sym)
        if data:
            results.append(
                {
                    "symbol": sym,
                    "social_score": data.get("sentiment_score"),
                    "social_bullish_pct": data.get("bullish_pct"),
                    "social_mentions_24h": data.get("mention_count_24h"),
                    "social_reddit_validated": data.get("is_validated"),
                    "asof_social": date.today().isoformat(),
                }
            )
        else:
            failed += 1

    logger.info(
        f"[XSentiment] 수집 완료: {len(results)}건 성공, {failed}건 실패"
    )
    return {
        "status": "success" if failed == 0 else "partial",
        "collected": len(results),
        "failed": failed,
        "reason": "",
        "data": results,
    }


def _get_consecutive_failures(supabase) -> int:
    try:
        r = (
            supabase.table("fmp_cache")
            .select("response_json")
            .eq("symbol", "_META_")
            .eq("endpoint", "x_sentiment_health")
            .single()
            .execute()
        )
        return r.data["response_json"].get("consecutive_failures", 0)
    except Exception:
        return 0


def _set_consecutive_failures(supabase, count: int):
    supabase.table("fmp_cache").upsert(
        {
            "symbol": "_META_",
            "endpoint": "x_sentiment_health",
            "response_json": {"consecutive_failures": count},
            "fetched_at": datetime.utcnow().isoformat(),
        },
        on_conflict="symbol,endpoint",
    ).execute()
