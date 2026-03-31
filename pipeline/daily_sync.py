"""
메인 오케스트레이터 — 매일 배치 파이프라인.
① Universe Sync (NASDAQ Trader) → ② yfinance Bulk → ⑤ 애널리스트 →
⑥ EDGAR → ⑧ 심화재무 → ⑨ 기술적 → ⑩ Performance
→ Scoring → UPSERT → Sanity Check
"""

import logging
import sys
import time
from datetime import date, datetime, timedelta
from pathlib import Path

from dotenv import load_dotenv

# .env 파일 로드 (프로젝트 루트 기준)
load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from pipeline.config import DEEP_FINANCIAL_TOP_N
from pipeline.loaders.supabase_upsert import (
    cleanup_fmp_cache,
    get_supabase_client,
    mark_delisted,
    update_insider_aggregates,
    upsert_equities,
    upsert_insider_trades,
)
from pipeline.sources.edgar_13f import check_new_13f_filings, is_13f_season
from pipeline.sources.edgar_insider import (
    batch_collect_insider_trades,
    load_cik_mapping,
)
from pipeline.sources.fmp_client import FMPClient
from pipeline.sources.priority_builder import build_daily_priority_list
from pipeline.sources.universe import fetch_universe
from pipeline.sources.x_sentiment import batch_scrape_weekly
from pipeline.sources.yfinance_bulk import (
    fetch_bulk_prices,
    fetch_price_history,
    fetch_ticker_info,
)
from pipeline.transforms.compute import compute_derived_fields
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

    # ② yfinance Ticker.info (우선 티커만)
    info_data = fetch_ticker_info(priority_list)
    step_timings["② yfinance Info"] = time.time() - t0

    # ── 데이터 병합 ──
    t0 = time.time()
    records = merge_universe_with_prices(universe, price_data, info_data)

    # 산출 필드 계산
    for rec in records:
        compute_derived_fields(rec)
        compute_freshness_dates(rec, "yfinance")

    step_timings["병합+산출"] = time.time() - t0

    # ── ⑨ 기술적 지표 + ⑩ Performance ──
    t0 = time.time()
    # 우선 티커만 히스토리 다운로드
    history_data = fetch_price_history(priority_list[:1000])

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
    top_500_symbols = [
        r["symbol"] for r in stocks_with_mcap[:DEEP_FINANCIAL_TOP_N]
    ]

    from pipeline.transforms.deep_financials import batch_deep_financials
    deep_data = batch_deep_financials(top_500_symbols)
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
    cleanup_fmp_cache(supabase)
    upsert_count = upsert_equities(supabase, records)
    mark_delisted(supabase, active_symbols)
    step_timings["UPSERT"] = time.time() - t0

    # ── ④ ETF 롤링 배치 (FMP Free 제약으로 스킵) ──
    # ETF Holdings/Info는 FMP 유료 전용. 추후 대안 소스 확보 시 활성화.
    logger.info("[ETF] FMP Free 제약으로 ETF Holdings 배치 스킵")

    logger.info(f"[FMP] 총 요청 수: {fmp.get_request_count()}")

    # ── ⑥ EDGAR 내부자 거래 ──
    t0 = time.time()
    try:
        cik_map = load_cik_mapping()
        # 상위 1000 종목만
        top_1000 = [r["symbol"] for r in stocks_with_mcap[:1000]]
        symbols_with_cik = {
            sym: cik_map[sym]
            for sym in top_1000
            if sym in cik_map
        }

        trades = batch_collect_insider_trades(symbols_with_cik)
        upsert_insider_trades(supabase, trades)

        # 집계
        from pipeline.transforms.insider_agg import aggregate_insider_trades
        agg = aggregate_insider_trades(trades)
        update_insider_aggregates(supabase, agg)

    except Exception as e:
        logger.warning(f"[EDGAR] 내부자 거래 수집 실패: {e}")

    # 13F 시즌 체크 (2/5/8/11월)
    if is_13f_season():
        try:
            date_from = (today - timedelta(days=7)).isoformat()
            new_13f = check_new_13f_filings(date_from)
            if new_13f:
                logger.info(f"[EDGAR 13F] 신규 {len(new_13f)}건 감지")
        except Exception as e:
            logger.warning(f"[EDGAR 13F] 확인 실패 (non-critical): {e}")

    step_timings["⑥ EDGAR"] = time.time() - t0

    # ── ⑦ X Sentiment (주간, 일요일만 실행) ──
    t0 = time.time()
    if today.weekday() == 6:  # Sunday
        try:
            top_250 = [r["symbol"] for r in stocks_with_mcap[:250]]
            sentiment_result = batch_scrape_weekly(top_250, supabase)

            if sentiment_result.get("data"):
                for s_data in sentiment_result["data"]:
                    rec = symbol_to_record.get(s_data["symbol"])
                    if rec:
                        rec.update(s_data)
                # 센티먼트 업데이트된 레코드 재upsert
                sentiment_records = [
                    symbol_to_record[s["symbol"]]
                    for s in sentiment_result["data"]
                    if s["symbol"] in symbol_to_record
                ]
                if sentiment_records:
                    upsert_equities(supabase, sentiment_records)

            logger.info(
                f"[XSentiment] {sentiment_result.get('collected', 0)}건 수집, "
                f"{sentiment_result.get('failed', 0)}건 실패"
            )
        except Exception as e:
            logger.warning(f"[XSentiment] 수집 실패 (non-critical): {e}")
    else:
        logger.info("[XSentiment] 주간 배치 — 일요일만 실행 (스킵)")

    step_timings["⑦ X Sentiment"] = time.time() - t0

    # ── Sanity Check ──
    t0 = time.time()
    check_result = run_sanity_checks(supabase, prev_count=None)
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
