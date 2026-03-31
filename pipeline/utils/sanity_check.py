"""
데이터 무결성 자동 검증 (Sanity Check).
UPSERT 완료 후 실행, 이상 데이터 감지.
"""

import logging
from datetime import date

logger = logging.getLogger(__name__)


class SanityCheckResult:
    def __init__(self):
        self.warnings: list[str] = []
        self.criticals: list[str] = []

    @property
    def passed(self) -> bool:
        return len(self.criticals) == 0

    def summary(self) -> str:
        lines = [f"=== Sanity Check Summary ({date.today()}) ==="]
        lines.append(
            f"CRITICAL: {len(self.criticals)}건, "
            f"WARNING: {len(self.warnings)}건"
        )
        for c in self.criticals:
            lines.append(f"  CRITICAL: {c}")
        for w in self.warnings:
            lines.append(f"  WARNING: {w}")
        return "\n".join(lines)


def run_sanity_checks(
    supabase, prev_count: int | None = None
) -> SanityCheckResult:
    """UPSERT 직후 실행. DB 검증 쿼리로 이상 여부 확인."""
    result = SanityCheckResult()

    # 1) 핵심 종목 가격 NULL 체크
    sentinel_symbols = ["AAPL", "MSFT", "NVDA", "GOOGL", "AMZN"]
    try:
        sentinel_check = (
            supabase.table("latest_equities")
            .select("symbol, price")
            .in_("symbol", sentinel_symbols)
            .is_("price", "null")
            .execute()
        )
        if sentinel_check.data:
            missing = [r["symbol"] for r in sentinel_check.data]
            result.criticals.append(
                f"핵심 종목 가격 NULL: {missing} — yfinance 전면 장애 의심"
            )
    except Exception as e:
        result.warnings.append(f"Sentinel 체크 실패: {e}")

    # 2) 전체 건수 급감 체크
    try:
        total = (
            supabase.table("latest_equities")
            .select("symbol", count="exact")
            .eq("is_delisted", False)
            .execute()
        )
        current_count = total.count or 0

        if prev_count and current_count < prev_count * 0.5:
            result.criticals.append(
                f"UPSERT 건수 급감: {prev_count} -> {current_count} "
                f"({current_count / prev_count * 100:.0f}%)"
            )
        elif prev_count and current_count < prev_count * 0.8:
            result.warnings.append(
                f"UPSERT 건수 감소: {prev_count} -> {current_count} "
                f"({current_count / prev_count * 100:.0f}%)"
            )
    except Exception as e:
        result.warnings.append(f"건수 체크 실패: {e}")

    # 3) 가격 0 이하 종목
    try:
        bad_price = (
            supabase.table("latest_equities")
            .select("symbol", count="exact")
            .eq("is_delisted", False)
            .lte("price", 0)
            .not_.is_("price", "null")
            .execute()
        )
        if (bad_price.count or 0) > 10:
            result.warnings.append(
                f"가격 <= 0 종목: {bad_price.count}건"
            )
    except Exception as e:
        result.warnings.append(f"가격 체크 실패: {e}")

    # 결과 출력
    summary = result.summary()
    if result.criticals:
        logger.error(summary)
        for c in result.criticals:
            print(f"::error::{c}")
    elif result.warnings:
        logger.warning(summary)
        for w in result.warnings:
            print(f"::warning::{w}")
    else:
        logger.info(summary)

    return result
