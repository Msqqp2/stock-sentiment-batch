"""
Ticker.info 보강 전용 스크립트.
첫 실행 후 market_cap/sector/PE 등이 비어있을 때 사용.
가격 벌크를 건너뛰고 Ticker.info + 기술적 지표 + 심화재무만 실행.
"""

import logging
import time
from datetime import date
from pathlib import Path

from dotenv import load_dotenv

load_dotenv(Path(__file__).resolve().parent.parent / ".env")

from pipeline.config import DEEP_FINANCIAL_TOP_N
from pipeline.loaders.supabase_upsert import get_supabase_client, upsert_equities
from pipeline.sources.yfinance_bulk import fetch_price_history, fetch_ticker_info
from pipeline.transforms.compute import compute_derived_fields
from pipeline.transforms.deep_financials import batch_deep_financials
from pipeline.transforms.freshness import compute_freshness_dates
from pipeline.transforms.scoring import compute_scores
from pipeline.transforms.technicals import compute_performance, compute_technicals

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)


def main():
    start = time.time()
    supabase = get_supabase_client()

    # DB에서 거래량 상위 500 종목 가져오기
    top = (
        supabase.table("latest_equities")
        .select("symbol, name, asset_type, exchange, price, volume")
        .eq("is_delisted", False)
        .not_.is_("volume", "null")
        .order("volume", desc=True)
        .limit(500)
        .execute()
        .data
    )
    symbols = [r["symbol"] for r in top]
    logger.info(f"[Enrich] 대상: {len(symbols)}종목 (거래량 Top 500)")

    # Ticker.info 수집
    t0 = time.time()
    info_data = fetch_ticker_info(symbols)
    logger.info(f"[Enrich] Ticker.info: {len(info_data)}건 ({time.time() - t0:.0f}초)")

    # 기존 DB 레코드에 info 병합
    records = []
    for row in top:
        sym = row["symbol"]
        rec = dict(row)
        rec["data_date"] = date.today().isoformat()
        rec["is_delisted"] = False

        info = info_data.get(sym, {})
        if info:
            for k, v in info.items():
                if v is not None and k != "symbol":
                    rec[k] = v

        compute_derived_fields(rec)
        compute_freshness_dates(rec, "yfinance")
        records.append(rec)

    # 기술적 지표
    t0 = time.time()
    history = fetch_price_history(symbols)
    sym_map = {r["symbol"]: r for r in records}
    for sym, hist in history.items():
        rec = sym_map.get(sym)
        if not rec:
            continue
        tech = compute_technicals(hist)
        if tech:
            rec.update(tech)
        perf = compute_performance(hist)
        if perf:
            rec.update(perf)
    logger.info(f"[Enrich] 기술적 지표: {len(history)}건 ({time.time() - t0:.0f}초)")

    # 심화 재무 (시총 상위 500 중 stock만)
    t0 = time.time()
    stocks = [r for r in records if r.get("asset_type") == "stock" and r.get("market_cap")]
    stocks.sort(key=lambda x: x["market_cap"], reverse=True)
    deep_symbols = [r["symbol"] for r in stocks[:DEEP_FINANCIAL_TOP_N]]
    if deep_symbols:
        deep = batch_deep_financials(deep_symbols)
        for d in deep:
            rec = sym_map.get(d["symbol"])
            if rec:
                rec.update(d)
        logger.info(f"[Enrich] 심화재무: {len(deep)}건 ({time.time() - t0:.0f}초)")

    # 점수 산출
    records = compute_scores(records)

    # UPSERT
    upsert_equities(supabase, records)

    elapsed = time.time() - start
    with_mcap = sum(1 for r in records if r.get("market_cap"))
    logger.info(f"[Enrich] 완료: {len(records)}건 UPSERT, market_cap: {with_mcap}건, {elapsed / 60:.1f}분")


if __name__ == "__main__":
    main()
