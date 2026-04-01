"""
우선 티커 리스트 빌더.
매일 yfinance Ticker.info 호출 대상 선정:
시총 Top1000 + 거래량 Top1000 + 인기산업 + 필수티커 + 신규미수집.
"""

import logging

from pipeline.config import PRIORITY_INDUSTRIES, PRIORITY_TICKERS

logger = logging.getLogger(__name__)


def build_daily_priority_list(supabase) -> list[str]:
    """
    매일 yfinance Ticker.info 호출 대상 선정.
    예상 ~1,600~1,900개 (중복 제거 후).
    """
    # 1) 시총 상위 1,000개
    top_by_cap = (
        supabase.table("latest_equities")
        .select("symbol")
        .eq("is_delisted", False)
        .not_.is_("market_cap", "null")
        .order("market_cap", desc=True)
        .limit(1000)
        .execute()
        .data
    )

    # 2) 거래량 상위 1,000개
    top_by_volume = (
        supabase.table("latest_equities")
        .select("symbol")
        .eq("is_delisted", False)
        .not_.is_("volume", "null")
        .order("volume", desc=True)
        .limit(1000)
        .execute()
        .data
    )

    # 3) 인기 산업 전종목 (1000행 초과 가능 → 페이지네이션)
    by_industry = []
    offset = 0
    while True:
        batch = (
            supabase.table("latest_equities")
            .select("symbol")
            .eq("is_delisted", False)
            .in_("industry", PRIORITY_INDUSTRIES)
            .range(offset, offset + 999)
            .execute()
            .data
        )
        by_industry.extend(batch)
        if len(batch) < 1000:
            break
        offset += 1000

    # 4) 하드코딩 필수 티커
    forced = PRIORITY_TICKERS

    # 5) 신규 상장: sector가 아직 NULL
    new_stocks = (
        supabase.table("latest_equities")
        .select("symbol")
        .eq("is_delisted", False)
        .is_("sector", "null")
        .limit(200)
        .execute()
        .data
    )

    all_symbols = set(
        [r["symbol"] for r in top_by_cap]
        + [r["symbol"] for r in top_by_volume]
        + [r["symbol"] for r in by_industry]
        + forced
        + [r["symbol"] for r in new_stocks]
    )

    logger.info(
        f"[Priority] 시총Top1000: {len(top_by_cap)}, "
        f"거래량Top1000: {len(top_by_volume)}, "
        f"인기산업: {len(by_industry)}, "
        f"필수티커: {len(forced)}, "
        f"신규: {len(new_stocks)}, "
        f"합계(중복제거): {len(all_symbols)}"
    )

    return list(all_symbols)
