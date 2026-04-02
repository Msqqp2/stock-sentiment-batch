"""
메인 오케스트레이터 — 매일 배치 파이프라인 (repo-A).
① Universe Sync → ② 가격 벌크 →
⑧ 심화재무 → ⑨ 기술적 → ⑩ Performance →
Scoring → UPSERT → ETF Profile 복사 → Sanity Check

yfinance Info는 weekly_info로 분리됨 (pipeline/info_sync.py).
"""

import logging
import sys
import time
from datetime import date
from pathlib import Path

from dotenv import load_dotenv

# .env 파일 로드 (프로젝트 루트 기준)
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from pipeline.config import DEEP_FINANCIAL_TOP_N, TECHNICALS_TOP_N
from pipeline.loaders.supabase_upsert import (
    cleanup_fmp_cache,
    get_supabase_client,
    mark_delisted,
    upsert_equities,
    upsert_etf_profile,
)
from pipeline.sources.fmp_client import FMPClient
from pipeline.sources.priority_builder import build_daily_priority_list
from pipeline.sources.universe import fetch_universe
from pipeline.sources.yfinance_bulk import (
    fetch_bulk_prices,
    fetch_price_history,
)
from pipeline.transforms.compute import compute_derived_fields
from pipeline.transforms.deep_financials import batch_deep_financials
from pipeline.transforms.freshness import compute_freshness_dates
from pipeline.transforms.merge import merge_universe_with_prices
from pipeline.transforms.scoring import compute_scores
from pipeline.transforms.technicals import compute_performance, compute_technicals
from pipeline.utils.alert import send_failure_alert
from pipeline.utils.batch_summary import build_batch_summary, write_github_summary
from pipeline.utils.sanity_check import run_sanity_checks

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def main():
    start_time = time.time()
    step_timings = {}

    logger.info("=" * 60)
    logger.info(f"Daily Sync 시작 — {date.today()}")
    logger.info("=" * 60)

    today = date.today()
    supabase = get_supabase_client()
    fmp = FMPClient()

    # ── ① Universe Sync (NASDAQ Trader) ──
    t0 = time.time()
    universe = fetch_universe()
    active_symbols = {item["symbol"] for item in universe}
    all_symbols = list(active_symbols)
    step_timings["① Universe Sync"] = time.time() - t0

    # ── ② yfinance 가격 벌크 ──
    t0 = time.time()
    price_data = fetch_bulk_prices(all_symbols)
    step_timings["② yfinance 가격"] = time.time() - t0

    # ── 우선 티커 리스트 빌드 ──
    t0 = time.time()
    try:
        priority_list = build_daily_priority_list(supabase)
    except Exception:
        # 첫 실행 시 DB가 비어있을 수 있음
        logger.info("[Priority] DB 비어있음 — 시총 상위 2000개로 대체")
        # 가격 데이터에서 시총 추정
        priority_list = list(price_data.keys())[:2000]
    step_timings["② Priority 빌드"] = time.time() - t0

    # yfinance Info는 weekly_info (info_sync.py)로 분리됨
    info_data = {}

    # ── 데이터 병합 ──
    t0 = time.time()
    records = merge_universe_with_prices(universe, price_data, info_data)

    # 산출 필드 계산
    for rec in records:
        compute_derived_fields(rec)
        compute_freshness_dates(rec, "yfinance")

    step_timings["병합+산출"] = time.time() - t0

    # ── ⑨ 기술적 지표 + ⑩ Performance (거래대금 Top 5,000 보통주) ──
    t0 = time.time()
    stocks_for_tech = [
        r for r in records
        if r.get("asset_type") == "stock" and r.get("price") and r.get("volume")
    ]
    stocks_for_tech.sort(
        key=lambda x: (x.get("price", 0) or 0) * (x.get("volume", 0) or 0),
        reverse=True,
    )
    tech_symbols = [r["symbol"] for r in stocks_for_tech[:TECHNICALS_TOP_N]]
    history_data = fetch_price_history(tech_symbols)

    symbol_to_record = {r["symbol"]: r for r in records}
    for sym, hist in history_data.items():
        rec = symbol_to_record.get(sym)
        if not rec:
            continue

        tech = compute_technicals(hist)
        if tech:
            rec.update(tech)

        perf = compute_performance(hist)
        if perf:
            rec.update(perf)

    step_timings["⑨⑩ 기술적+Performance"] = time.time() - t0

    # ── ⑧ 심화 재무 (상위 500) ──
    t0 = time.time()
    # 시총 기준 상위 500 추출
    stocks_with_mcap = [
        r for r in records
        if r.get("asset_type") == "stock" and r.get("market_cap")
    ]
    stocks_with_mcap.sort(key=lambda x: x["market_cap"], reverse=True)
    top_500 = stocks_with_mcap[:DEEP_FINANCIAL_TOP_N]
    top_500_symbols = [r["symbol"] for r in top_500]
    mcap_map = {r["symbol"]: r["market_cap"] for r in top_500}

    deep_data = batch_deep_financials(top_500_symbols, mcap_map)
    for d in deep_data:
        rec = symbol_to_record.get(d["symbol"])
        if rec:
            rec.update(d)

    step_timings["⑧ 심화재무"] = time.time() - t0

    # ── 종합 점수 산출 ──
    t0 = time.time()
    records = compute_scores(records)
    step_timings["종합 점수"] = time.time() - t0

    # ── UPSERT ──
    t0 = time.time()
    # UPSERT 전 기존 건수 조회 (sanity check용)
    try:
        prev_total = (
            supabase.table("latest_equities")
            .select("symbol", count="exact")
            .eq("is_delisted", False)
            .execute()
        )
        prev_count = prev_total.count or 0
    except Exception:
        prev_count = None

    cleanup_fmp_cache(supabase)
    upsert_count = upsert_equities(supabase, records)
    mark_delisted(supabase, active_symbols)
    step_timings["UPSERT"] = time.time() - t0

    logger.info(f"[FMP] 총 요청 수: {fmp.get_request_count()}")

    # ── ETF Profile 복사 (latest_equities → etf_profile) ──
    t0 = time.time()
    etf_records = [r for r in records if r.get("asset_type") == "etf"]
    if etf_records:
        upsert_etf_profile(supabase, etf_records)
    step_timings["ETF Profile 복사"] = time.time() - t0

    # ── Sanity Check ──
    t0 = time.time()
    check_result = run_sanity_checks(supabase, prev_count=prev_count)
    step_timings["Sanity Check"] = time.time() - t0

    if not check_result.passed:
        logger.error(
            "Sanity check CRITICAL — 문제 데이터 존재, 수동 확인 필요"
        )
        send_failure_alert(
            subject=f"Sanity Check 실패 ({date.today()})",
            body=check_result.summary(),
        )

    # ── Summary ──
    elapsed = time.time() - start_time
    summary = build_batch_summary(elapsed, check_result, step_timings)
    logger.info("\n" + summary)
    write_github_summary(summary)

    logger.info("=" * 60)
    logger.info(f"Daily Sync 완료 — {elapsed / 60:.1f}분 소요")
    logger.info("=" * 60)

    if not check_result.passed:
        sys.exit(1)


if __name__ == "__main__":
    main()
