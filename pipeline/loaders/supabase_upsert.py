"""
Supabase PostgreSQL UPSERT 실행.
INSERT ... ON CONFLICT (symbol) DO UPDATE.
"""

import logging
import math
from datetime import datetime, timedelta, timezone

from supabase import create_client

from pipeline.config import (
    SUPABASE_SERVICE_KEY,
    SUPABASE_URL,
    UPSERT_CHUNK_SIZE,
)

logger = logging.getLogger(__name__)

# DDL NUMERIC(precision, scale) → 최대 절대값 = 10^(precision-scale)
# 컬럼별 overflow 방지용
_COL_MAX = {}
_NUMERIC_8_4 = 9999.9999  # NUMERIC(8,4)
for _col in (
    "change_pct", "dividend_yield", "pct_from_52h", "pct_from_52l", "beta",
    "volatility_w", "volatility_m", "sma20_pct", "sma50_pct", "sma200_pct",
    "gap_pct", "change_from_open", "dcf_upside_pct", "graham_upside_pct",
    "accruals_ratio", "buyback_yield", "shareholder_yield", "capex_to_revenue",
    "perf_1w", "perf_1m", "perf_3m", "perf_6m", "perf_1y", "perf_ytd",
    "short_pct_float", "insider_pct", "inst_pct", "expense_ratio",
    "inst_transactions_pct", "earnings_surprise_pct", "avg_dividend_yield_5y",
    "fcf_yield", "target_upside_pct",
):
    _COL_MAX[_col] = _NUMERIC_8_4

for _col in (
    "roe", "roa", "operating_margin", "profit_margin", "gross_margin",
    "revenue_growth", "earnings_growth", "op_income_growth", "debt_growth",
    "roic", "payout_ratio", "eps_growth_this_yr", "eps_growth_past_5y",
    "sales_growth_past_5y", "macd", "macd_signal", "macd_hist",
    "dividend_rate",
):
    _COL_MAX[_col] = 999999.9999  # NUMERIC(10,4)

# NUMERIC(4,2)
_COL_MAX["analyst_rating_score"] = 99.99
# NUMERIC(6,4)
_COL_MAX["social_bullish_pct"] = 99.9999
# NUMERIC(8,2)
for _col in ("rsi_14", "relative_volume", "stoch_k", "stoch_d",
             "adx_14", "williams_r", "cci_20", "short_ratio",
             "altman_z_score", "fcf_to_ni"):
    _COL_MAX[_col] = 999999.99
# NUMERIC(10,2)
for _col in ("pe_ttm", "pe_forward", "pb_ratio", "peg_ratio", "ps_ratio",
             "pfcf_ratio", "pcash_ratio", "ev_ebitda", "ev_revenue",
             "debt_to_equity", "current_ratio", "interest_coverage",
             "quick_ratio", "lt_debt_equity", "eps_ttm"):
    _COL_MAX[_col] = 99999999.99
# NUMERIC(12,4)
for _col in ("price", "open_price", "day_high", "day_low", "prev_close",
             "week52_high", "week52_low", "ma_50", "ma_200", "ma_20",
             "book_value", "revenue_per_share", "atr_14",
             "dcf_value", "graham_number", "nav",
             "target_mean", "target_high", "target_low"):
    _COL_MAX[_col] = 99999999.9999
# NUMERIC(18,2)
_COL_MAX["turnover"] = 9999999999999999.99


def _clamp_numeric(rec: dict) -> dict:
    """DB 컬럼 precision 초과 값 제거 + 모든 숫자를 Python native로 변환."""
    to_delete = []
    for k, v in rec.items():
        if v is None:
            continue
        # numpy/기타 숫자 타입 → Python native 변환
        try:
            if hasattr(v, 'item'):  # numpy scalar
                v = v.item()
                rec[k] = v
            if isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                to_delete.append(k)
                continue
        except (ValueError, TypeError):
            pass
        # 컬럼별 overflow 체크
        max_val = _COL_MAX.get(k)
        if max_val is not None:
            try:
                if abs(float(v)) > max_val:
                    to_delete.append(k)
            except (ValueError, TypeError):
                pass
    for k in to_delete:
        rec.pop(k, None)
    return rec


def get_supabase_client():
    """Supabase 클라이언트 생성 (service_role 키)."""
    return create_client(SUPABASE_URL, SUPABASE_SERVICE_KEY)


def upsert_equities(supabase, records: list[dict]):
    """
    latest_equities 테이블에 UPSERT.
    500건씩 청크 분할.
    """
    total = 0
    chunks = [
        records[i : i + UPSERT_CHUNK_SIZE]
        for i in range(0, len(records), UPSERT_CHUNK_SIZE)
    ]

    for i, chunk in enumerate(chunks):
        # None 값 필터링 — Supabase SDK는 None을 null로 전송
        cleaned = []
        for rec in chunk:
            # symbol과 name은 필수
            if not rec.get("symbol") or not rec.get("name"):
                continue
            # JSON 직렬화 불가능한 값 제거 (NaN/Inf 포함)
            clean_rec = {}
            for k, v in rec.items():
                if v is None:
                    if k in ("is_delisted",):
                        clean_rec[k] = v
                elif isinstance(v, float) and (math.isnan(v) or math.isinf(v)):
                    continue  # NaN/Inf는 JSON 불가 → 스킵
                else:
                    clean_rec[k] = v
            cleaned.append(_clamp_numeric(clean_rec))

        if not cleaned:
            continue

        try:
            supabase.table("latest_equities").upsert(
                cleaned, on_conflict="symbol"
            ).execute()
            total += len(cleaned)
        except Exception as e:
            logger.warning(
                f"[UPSERT] 청크 {i + 1}/{len(chunks)} 실패, 개별 레코드 폴백: {e}"
            )
            # 개별 레코드로 폴백
            for rec in cleaned:
                try:
                    supabase.table("latest_equities").upsert(
                        [rec], on_conflict="symbol"
                    ).execute()
                    total += 1
                except Exception as e2:
                    logger.error(
                        f"[UPSERT] {rec.get('symbol')} 실패: {e2}"
                    )

    logger.info(f"[UPSERT] latest_equities: {total}건 완료")
    return total


def upsert_insider_trades(supabase, trades: list[dict]):
    """insider_trades 테이블에 INSERT."""
    if not trades:
        return 0

    records = []
    for t in trades:
        rec = {
            "symbol": t.get("symbol"),
            "insider_name": t.get("insider_name", "Unknown"),
            "insider_title": t.get("insider_title"),
            "txn_type": t.get("transaction_type", "Unknown"),
            "txn_date": t.get("transaction_date"),
            "shares": t.get("shares", 0),
            "price": t.get("price_per_share"),
            "shares_after": t.get("shares_owned_after"),
            "filing_date": t.get("filing_date"),
            "accession_no": t.get("accession_no"),
        }
        # total_value 산출
        if rec["price"] and rec["shares"]:
            rec["total_value"] = round(rec["price"] * rec["shares"], 2)

        if rec["symbol"] and rec["txn_date"]:
            records.append(rec)

    if not records:
        return 0

    total = 0
    for i in range(0, len(records), UPSERT_CHUNK_SIZE):
        chunk = records[i : i + UPSERT_CHUNK_SIZE]
        try:
            supabase.table("insider_trades").upsert(
                chunk,
                on_conflict="symbol,txn_date,insider_name,accession_no",
            ).execute()
            total += len(chunk)
        except Exception as e:
            logger.warning(f"[UPSERT] insider_trades 실패: {e}")

    logger.info(f"[UPSERT] insider_trades: {total}건 완료")
    return total


def update_insider_aggregates(supabase, agg_data: dict[str, dict]):
    """내부자 거래 집계를 latest_equities에 직접 UPDATE (name 불필요)."""
    if not agg_data:
        return

    updated = 0
    now_ts = datetime.now(timezone.utc).isoformat()

    for sym, agg in agg_data.items():
        payload = dict(agg)
        payload["edgar_updated_at"] = now_ts
        try:
            supabase.table("latest_equities").update(
                payload
            ).eq("symbol", sym).execute()
            updated += 1
        except Exception as e:
            logger.debug(f"[UPSERT] 내부자 집계 {sym} 실패: {e}")

    logger.info(f"[UPSERT] 내부자 집계: {updated}/{len(agg_data)}종목 업데이트")


def update_etf_data(supabase, etf_records: list[dict]):
    """ETF 메타 + Holdings 업데이트."""
    if etf_records:
        upsert_equities(supabase, etf_records)
        logger.info(f"[UPSERT] ETF 데이터: {len(etf_records)}건 업데이트")


def cleanup_fmp_cache(supabase):
    """30분 이상 경과한 FMP 온디맨드 캐시 삭제."""
    cutoff = (datetime.now(timezone.utc) - timedelta(minutes=30)).isoformat()
    try:
        supabase.table("fmp_cache").delete().lt(
            "fetched_at", cutoff
        ).execute()
        logger.info("[UPSERT] fmp_cache TTL 정리 완료")
    except Exception as e:
        logger.warning(f"[UPSERT] fmp_cache 정리 실패: {e}")


def mark_delisted(supabase, active_symbols: set[str]):
    """Universe에 없는 종목을 상장폐지 마킹. 안전장치 포함."""
    try:
        existing = (
            supabase.table("latest_equities")
            .select("symbol")
            .eq("is_delisted", False)
            .execute()
            .data
        )
        existing_symbols = {r["symbol"] for r in existing}
        to_delist = existing_symbols - active_symbols

        # 안전장치: universe가 50% 미만이면 스킵 (부분 실패 방지)
        if len(active_symbols) < len(existing_symbols) * 0.5:
            logger.warning(
                f"[UPSERT] 상장폐지 스킵 — universe({len(active_symbols)})가 "
                f"기존({len(existing_symbols)})의 50% 미만. 데이터 소스 장애 의심"
            )
            return

        if to_delist:
            # 배치 UPDATE (N+1 방지)
            delist_list = list(to_delist)
            for i in range(0, len(delist_list), UPSERT_CHUNK_SIZE):
                chunk = delist_list[i : i + UPSERT_CHUNK_SIZE]
                supabase.table("latest_equities").update(
                    {"is_delisted": True}
                ).in_("symbol", chunk).execute()
            logger.info(f"[UPSERT] 상장폐지 마킹: {len(to_delist)}건")
    except Exception as e:
        logger.warning(f"[UPSERT] 상장폐지 마킹 실패: {e}")
