-- =============================================================
-- US Stock Screener — Migration v2: Improvements
-- =============================================================

-- ── 1. 누락된 인덱스 추가 ──

-- 종합 점수 (스크리너 기본 정렬)
CREATE INDEX IF NOT EXISTS idx_score_total
    ON latest_equities (score_total DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 등락률 (일간 Movers)
CREATE INDEX IF NOT EXISTS idx_change_pct
    ON latest_equities (change_pct DESC NULLS LAST)
    WHERE is_delisted = false;

-- 섹터 필터
CREATE INDEX IF NOT EXISTS idx_sector
    ON latest_equities (sector)
    WHERE is_delisted = false;

-- 데이터 기준일 (freshness)
CREATE INDEX IF NOT EXISTS idx_data_date
    ON latest_equities (data_date DESC NULLS LAST)
    WHERE is_delisted = false;

-- is_delisted 단독 (mark_delisted 쿼리용)
CREATE INDEX IF NOT EXISTS idx_is_delisted
    ON latest_equities (is_delisted)
    WHERE is_delisted = false;

-- ── 2. insider_trades 중복 방지 ──
-- 같은 filing의 같은 거래는 중복 INSERT 방지
CREATE UNIQUE INDEX IF NOT EXISTS idx_insider_unique
    ON insider_trades (symbol, txn_date, insider_name, accession_no)
    WHERE accession_no IS NOT NULL;

-- ── 3. updated_at 자동 갱신 트리거 ──
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_updated_at ON latest_equities;
CREATE TRIGGER trigger_updated_at
    BEFORE UPDATE ON latest_equities
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
