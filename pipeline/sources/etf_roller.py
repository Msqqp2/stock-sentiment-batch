"""
ETF 롤링 배치 스케줄러.
FMP 250 req/day 예산 내에서 ETF 데이터를 우선순위 기반 순환 갱신.
"""

import logging
from datetime import date

from pipeline.config import ETF_TIER1_COUNT, ETF_TIER2_COUNT

logger = logging.getLogger(__name__)


def classify_etf_tiers(supabase) -> dict[str, list[str]]:
    """
    ETF를 AUM 기준으로 Tier 분류.
    Tier 1: 상위 300 (매일)
    Tier 2: 301~1000 (주 2회)
    Tier 3: 나머지 (주 1회)
    """
    all_etfs = []
    offset = 0
    while True:
        batch = (
            supabase.table("latest_equities")
            .select("symbol, aum")
            .eq("is_delisted", False)
            .eq("asset_type", "etf")
            .order("aum", desc=True)
            .range(offset, offset + 999)
            .execute()
            .data
        )
        all_etfs.extend(batch)
        if len(batch) < 1000:
            break
        offset += 1000

    symbols = [r["symbol"] for r in all_etfs]
    tiers = {
        "tier1": symbols[:ETF_TIER1_COUNT],
        "tier2": symbols[ETF_TIER1_COUNT : ETF_TIER1_COUNT + ETF_TIER2_COUNT],
        "tier3": symbols[ETF_TIER1_COUNT + ETF_TIER2_COUNT :],
    }

    logger.info(
        f"[ETF Tier] T1={len(tiers['tier1'])}, "
        f"T2={len(tiers['tier2'])}, T3={len(tiers['tier3'])}"
    )
    return tiers


def get_todays_etf_batch(tiers: dict[str, list[str]]) -> list[str]:
    """
    오늘 갱신할 ETF 목록 산출.
    Tier 1: 매일 전체
    Tier 2: 월·목 (주 2회, 절반씩 교대)
    Tier 3: 수요일 (7등분 순환)
    """
    today = date.today()
    weekday = today.weekday()  # 0=월 ~ 4=금
    batch = list(tiers["tier1"])  # Tier 1은 항상 포함

    # Tier 2: 월(0), 목(3)
    t2 = tiers["tier2"]
    if weekday in (0, 3) and t2:
        half = len(t2) // 2
        if weekday == 0:
            batch.extend(t2[:half])
        else:
            batch.extend(t2[half:])

    # Tier 3: 수(2), 7등분 순환
    t3 = tiers["tier3"]
    if weekday == 2 and t3:
        group = (today.timetuple().tm_yday // 7) % 7
        chunk_size = max(1, len(t3) // 7)
        start = group * chunk_size
        batch.extend(t3[start : start + chunk_size])

    logger.info(
        f"[ETF Roller] 오늘 배치: {len(batch)}개 "
        f"(T1={len(tiers['tier1'])}, 추가={len(batch) - len(tiers['tier1'])})"
    )
    return batch
