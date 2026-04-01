"""
Finnhub ETF Holdings/Sector/Country 수집 (repo-B 목요일 주1회).
200 ETF × 3 엔드포인트 = 600건, ~10분.
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
    get_etf_symbols,
    get_finnhub_etf_list_cache,
    send_batch_email,
)
from pipeline.config import (
    FINNHUB_API_KEY,
    FINNHUB_BASE_URL,
    FINNHUB_ETF_TOP_N,
    FINNHUB_RATE_LIMIT_SLEEP,
)
from pipeline.loaders.supabase_upsert import (
    get_supabase_client,
    upsert_etf_country_exposure,
    upsert_etf_holdings,
    upsert_etf_list_cache,
    upsert_etf_sector_exposure,
)

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
            logger.warning("[Finnhub ETF] Rate limit — 60초 대기")
            time.sleep(60)
            resp = requests.get(url, headers=headers, params=params, timeout=15)
        resp.raise_for_status()
        return resp.json()
    except Exception as e:
        logger.debug(f"[Finnhub ETF] {endpoint} 실패: {e}")
        return None


def refresh_etf_list_cache(supabase):
    """Finnhub /etf/list 호출 → etf_list_cache 테이블 갱신 (월 1회)."""
    # 캐시 최신 여부 확인
    try:
        resp = (
            supabase.table("etf_list_cache")
            .select("cached_at")
            .order("cached_at", desc=True)
            .limit(1)
            .execute()
        )
        if resp.data:
            cached_at = resp.data[0].get("cached_at", "")
            if cached_at:
                from datetime import datetime as dt

                cached_date = dt.fromisoformat(cached_at.replace("Z", "+00:00"))
                days_old = (datetime.now(timezone.utc) - cached_date).days
                if days_old < 30:
                    logger.info(
                        f"[Finnhub ETF] 캐시 유효 ({days_old}일 경과, 30일 미만)"
                    )
                    return
    except Exception:
        pass

    logger.info("[Finnhub ETF] /etf/list 캐시 갱신 중...")
    data = _fh_get("/etf/list")
    if data and isinstance(data, list):
        upsert_etf_list_cache(supabase, data)
    else:
        logger.warning("[Finnhub ETF] /etf/list 응답 없음")


def main():
    start_time = time.time()

    logger.info("=" * 60)
    logger.info(f"Finnhub ETF Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    if not FINNHUB_API_KEY:
        logger.info("[Finnhub ETF] API 키 미설정 — 스킵")
        return

    supabase = get_supabase_client()

    # 1. Finnhub 지원 ETF 목록 캐시 갱신 (월 1회)
    refresh_etf_list_cache(supabase)
    time.sleep(FINNHUB_RATE_LIMIT_SLEEP)

    # 2. 거래대금 Top 200 ETF 추출
    candidate_symbols = get_etf_symbols(supabase, FINNHUB_ETF_TOP_N)

    # 3. Finnhub 지원 목록과 교집합
    finnhub_supported = get_finnhub_etf_list_cache(supabase)
    if finnhub_supported:
        symbols = [s for s in candidate_symbols if s in finnhub_supported]
        logger.info(
            f"[Finnhub ETF] 교집합: {len(symbols)}개 "
            f"(후보 {len(candidate_symbols)}, Finnhub {len(finnhub_supported)})"
        )
    else:
        symbols = candidate_symbols
        logger.warning("[Finnhub ETF] 캐시 없음 — 전체 후보로 진행")

    if not symbols:
        logger.error("[Finnhub ETF] 대상 ETF 없음 — 종료")
        sys.exit(1)

    updated = 0
    failed = 0
    api_calls = 0

    for i, sym in enumerate(symbols):
        # Holdings
        holdings = _fh_get("/etf/holdings", {"symbol": sym})
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        if holdings and isinstance(holdings, dict):
            holding_list = holdings.get("holdings", [])
            upsert_etf_holdings(supabase, sym, holding_list)
        elif holdings and isinstance(holdings, list):
            upsert_etf_holdings(supabase, sym, holdings)

        # Sector Exposure
        sectors = _fh_get("/etf/sector", {"symbol": sym})
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        if sectors and isinstance(sectors, dict):
            sector_list = sectors.get("sectorExposure", [])
            upsert_etf_sector_exposure(supabase, sym, sector_list)
        elif sectors and isinstance(sectors, list):
            upsert_etf_sector_exposure(supabase, sym, sectors)

        # Country Exposure
        countries = _fh_get("/etf/country", {"symbol": sym})
        time.sleep(FINNHUB_RATE_LIMIT_SLEEP)
        api_calls += 1

        if countries and isinstance(countries, dict):
            country_list = countries.get("countryExposure", [])
            upsert_etf_country_exposure(supabase, sym, country_list)
        elif countries and isinstance(countries, list):
            upsert_etf_country_exposure(supabase, sym, countries)

        has_any = (
            (holdings and (isinstance(holdings, list) or holdings.get("holdings")))
            or (sectors and (isinstance(sectors, list) or sectors.get("sectorExposure")))
            or (countries and (isinstance(countries, list) or countries.get("countryExposure")))
        )
        if has_any:
            updated += 1
        else:
            failed += 1

        if (i + 1) % 20 == 0:
            logger.info(
                f"[Finnhub ETF] {i + 1}/{len(symbols)} 완료 "
                f"({updated}건 성공, API {api_calls}건)"
            )

    elapsed = time.time() - start_time

    summary = (
        f"Finnhub ETF Sync 완료\n"
        f"대상: {len(symbols)} ETF\n"
        f"성공: {updated}건, 실패: {failed}건\n"
        f"API 호출: {api_calls}건\n"
        f"소요 시간: {elapsed / 60:.1f}분"
    )
    logger.info(summary)

    send_batch_email(
        f"[StockScreener] ETF Sync 완료 ({date.today()})",
        summary,
    )


if __name__ == "__main__":
    main()
