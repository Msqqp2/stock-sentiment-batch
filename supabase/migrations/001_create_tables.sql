-- =============================================================
-- US Stock Screener — DDL v2.1
-- Supabase PostgreSQL 15+ (서울 리전)
-- =============================================================

-- 확장 모듈 활성화
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- =============================================================
-- 1. 단일 스냅샷 테이블: latest_equities
-- =============================================================
CREATE TABLE IF NOT EXISTS latest_equities (

    -- ── 식별자 ──
    symbol          TEXT        PRIMARY KEY,
    name            TEXT        NOT NULL,

    -- ── 분류 ──
    asset_type      TEXT        NOT NULL DEFAULT 'stock',
    exchange        TEXT,
    sector          TEXT,
    industry        TEXT,

    -- ── 가격 ──
    price           NUMERIC(12,4),
    open_price      NUMERIC(12,4),
    day_high        NUMERIC(12,4),
    day_low         NUMERIC(12,4),
    prev_close      NUMERIC(12,4),
    change_pct      NUMERIC(8,4),

    -- ── 거래량 ──
    volume          BIGINT,
    avg_volume_10d  BIGINT,
    turnover        NUMERIC(18,2),

    -- ── 밸류에이션 ──
    market_cap      BIGINT,
    pe_ttm          NUMERIC(10,2),
    pe_forward      NUMERIC(10,2),
    pb_ratio        NUMERIC(10,2),
    eps_ttm         NUMERIC(10,2),

    -- ── 배당 ──
    dividend_yield  NUMERIC(8,4),
    dividend_rate   NUMERIC(10,4),
    ex_dividend_date DATE,
    avg_dividend_yield_5y NUMERIC(8,4),

    -- ── 기술적 지표 (벤더 산출) ──
    week52_high     NUMERIC(12,4),
    week52_low      NUMERIC(12,4),
    pct_from_52h    NUMERIC(8,4),
    pct_from_52l    NUMERIC(8,4),
    ma_50           NUMERIC(12,4),
    ma_200          NUMERIC(12,4),
    beta            NUMERIC(8,4),

    -- ── 재무 건전성 ──
    roe             NUMERIC(10,4),
    roa             NUMERIC(10,4),
    debt_to_equity  NUMERIC(10,2),
    current_ratio   NUMERIC(10,2),
    operating_margin NUMERIC(10,4),
    profit_margin   NUMERIC(10,4),
    gross_margin    NUMERIC(10,4),
    revenue_growth  NUMERIC(10,4),
    earnings_growth NUMERIC(10,4),
    peg_ratio       NUMERIC(10,2),
    ps_ratio        NUMERIC(10,2),
    pfcf_ratio      NUMERIC(10,2),
    pcash_ratio     NUMERIC(10,2),
    ev              BIGINT,
    ev_ebitda       NUMERIC(10,2),
    ev_revenue      NUMERIC(10,2),
    fcf_yield       NUMERIC(10,4),

    -- ── 실적 절대값 ──
    total_revenue   BIGINT,
    ebitda          BIGINT,
    free_cashflow   BIGINT,
    total_cash      BIGINT,
    total_debt      BIGINT,
    book_value      NUMERIC(12,4),
    revenue_per_share NUMERIC(12,4),

    -- ── 심화 재무 (상위 500 배치 + 나머지 온디맨드) ──
    op_income_growth NUMERIC(10,4),
    debt_growth      NUMERIC(10,4),
    interest_coverage NUMERIC(10,2),
    roic            NUMERIC(10,4),
    quick_ratio     NUMERIC(10,2),
    lt_debt_equity  NUMERIC(10,2),
    payout_ratio    NUMERIC(10,4),
    eps_growth_this_yr NUMERIC(10,4),
    eps_growth_past_5y NUMERIC(10,4),
    sales_growth_past_5y NUMERIC(10,4),
    inst_transactions_pct NUMERIC(8,4),
    earnings_surprise_pct NUMERIC(8,4),
    piotroski_score  SMALLINT,
    altman_z_score   NUMERIC(8,2),
    asof_deep_financial DATE,

    -- ── 기술적 지표 확장 ──
    rsi_14          NUMERIC(8,2),
    atr_14          NUMERIC(12,4),
    volatility_w    NUMERIC(8,4),
    volatility_m    NUMERIC(8,4),
    ma_20           NUMERIC(12,4),
    sma20_pct       NUMERIC(8,4),
    sma50_pct       NUMERIC(8,4),
    sma200_pct      NUMERIC(8,4),
    gap_pct         NUMERIC(8,4),
    change_from_open NUMERIC(8,4),
    relative_volume NUMERIC(8,2),

    -- ── 모멘텀/추세 ──
    macd            NUMERIC(10,4),
    macd_signal     NUMERIC(10,4),
    macd_hist       NUMERIC(10,4),
    stoch_k         NUMERIC(8,2),
    stoch_d         NUMERIC(8,2),
    adx_14          NUMERIC(8,2),
    cci_20          NUMERIC(10,2),
    williams_r      NUMERIC(8,2),

    -- ── 밸류에이션 모델 ──
    dcf_value       NUMERIC(12,4),
    dcf_upside_pct  NUMERIC(8,4),
    graham_number   NUMERIC(12,4),
    graham_upside_pct NUMERIC(8,4),

    -- ── 수익 품질 (상위 500 배치) ──
    accruals_ratio  NUMERIC(8,4),
    fcf_to_ni       NUMERIC(8,2),
    earnings_quality_score SMALLINT,

    -- ── 자본 효율성 (상위 500 배치) ──
    buyback_yield   NUMERIC(8,4),
    shareholder_yield NUMERIC(8,4),
    capex_to_revenue NUMERIC(8,4),

    -- ── 종합 점수 ──
    score_value     SMALLINT,
    score_quality   SMALLINT,
    score_momentum  SMALLINT,
    score_growth    SMALLINT,
    score_total     SMALLINT,

    -- ── 성과 (Performance) ──
    perf_1w         NUMERIC(8,4),
    perf_1m         NUMERIC(8,4),
    perf_3m         NUMERIC(8,4),
    perf_6m         NUMERIC(8,4),
    perf_1y         NUMERIC(8,4),
    perf_ytd        NUMERIC(8,4),

    -- ── 기타 ──
    country         TEXT,
    earnings_date   DATE,
    has_options     BOOLEAN,
    index_membership TEXT,

    -- ── 수급 지표 ──
    shares_outstanding BIGINT,
    float_shares    BIGINT,
    shares_short    BIGINT,
    short_ratio     NUMERIC(8,2),
    short_pct_float NUMERIC(8,4),
    insider_pct     NUMERIC(8,4),
    inst_pct        NUMERIC(8,4),

    -- ── ETF 전용 ──
    expense_ratio   NUMERIC(8,4),
    aum             BIGINT,
    nav             NUMERIC(12,4),
    holdings_count  INTEGER,
    index_tracked   TEXT,
    asset_class     TEXT,
    is_active       BOOLEAN     DEFAULT false,
    inception_date  DATE,

    -- ── ETF Holdings (JSONB) ──
    holdings        JSONB,

    -- ── 애널리스트 컨센서스 ──
    analyst_rating       TEXT,
    analyst_rating_score NUMERIC(4,2),
    target_mean          NUMERIC(12,4),
    target_high          NUMERIC(12,4),
    target_low           NUMERIC(12,4),
    analyst_count        INTEGER,
    target_upside_pct    NUMERIC(8,4),

    -- ── SEC EDGAR 집계 ──
    insider_buy_3m       SMALLINT    DEFAULT 0,
    insider_sell_3m      SMALLINT    DEFAULT 0,
    insider_net_shares_3m BIGINT     DEFAULT 0,
    insider_latest_date  DATE,
    insider_latest_type  TEXT,
    inst_holders_13f     INTEGER,
    edgar_updated_at     TIMESTAMPTZ,

    -- ── 소셜 센티먼트 ──
    social_score         SMALLINT,
    social_bullish_pct   NUMERIC(6,4),
    social_mentions_24h  INTEGER,
    social_reddit_validated BOOLEAN,
    asof_social          DATE,

    -- ── 데이터 기준일 ──
    asof_price       DATE,
    asof_financial   DATE,
    asof_valuation   DATE,
    asof_dividend    DATE,
    asof_technical   DATE,
    asof_short       DATE,
    asof_insider     DATE,
    asof_inst_13f    DATE,
    asof_analyst     DATE,
    asof_etf         DATE,

    -- ── 메타 ──
    is_delisted     BOOLEAN     DEFAULT false,
    data_date       DATE,
    updated_at      TIMESTAMPTZ DEFAULT now()
);

-- =============================================================
-- 2. 인덱스
-- =============================================================

-- Trigram: 자동완성
CREATE INDEX IF NOT EXISTS idx_trgm_symbol
    ON latest_equities USING gin (symbol gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_trgm_name
    ON latest_equities USING gin (name gin_trgm_ops);

-- B-Tree 복합: 필터 + 정렬
CREATE INDEX IF NOT EXISTS idx_filter_stock
    ON latest_equities (asset_type, market_cap DESC NULLS LAST)
    WHERE is_delisted = false;

CREATE INDEX IF NOT EXISTS idx_pe_dividend
    ON latest_equities (pe_ttm, dividend_yield DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

CREATE INDEX IF NOT EXISTS idx_etf_expense
    ON latest_equities (expense_ratio ASC NULLS LAST, aum DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'etf';

CREATE INDEX IF NOT EXISTS idx_volume_turnover
    ON latest_equities (turnover DESC NULLS LAST)
    WHERE is_delisted = false;

CREATE INDEX IF NOT EXISTS idx_52week
    ON latest_equities (pct_from_52h ASC NULLS LAST)
    WHERE is_delisted = false;

-- GIN: JSONB Holdings 역산출
CREATE INDEX IF NOT EXISTS idx_holdings_gin
    ON latest_equities USING gin (holdings jsonb_path_ops);

-- 애널리스트 필터
CREATE INDEX IF NOT EXISTS idx_analyst
    ON latest_equities (analyst_rating, target_upside_pct DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 재무건전성 + 성장성
CREATE INDEX IF NOT EXISTS idx_financial_health
    ON latest_equities (roe DESC NULLS LAST, debt_to_equity ASC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

CREATE INDEX IF NOT EXISTS idx_growth
    ON latest_equities (revenue_growth DESC NULLS LAST, earnings_growth DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- 공매도
CREATE INDEX IF NOT EXISTS idx_short_interest
    ON latest_equities (short_pct_float DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- EDGAR 내부자 거래
CREATE INDEX IF NOT EXISTS idx_insider_activity
    ON latest_equities (insider_buy_3m DESC NULLS LAST, insider_sell_3m DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- =============================================================
-- 3. RLS
-- =============================================================
ALTER TABLE latest_equities ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Public read access"
    ON latest_equities FOR SELECT
    USING (true);

-- =============================================================
-- 4. insider_trades 보조 테이블
-- =============================================================
CREATE TABLE IF NOT EXISTS insider_trades (
    id              BIGSERIAL   PRIMARY KEY,
    symbol          TEXT        NOT NULL REFERENCES latest_equities(symbol),
    insider_name    TEXT        NOT NULL,
    insider_title   TEXT,
    txn_type        TEXT        NOT NULL,
    txn_date        DATE        NOT NULL,
    shares          BIGINT      NOT NULL,
    price           NUMERIC(12,4),
    total_value     NUMERIC(18,2),
    shares_after    BIGINT,
    filing_date     DATE        NOT NULL,
    accession_no    TEXT,
    created_at      TIMESTAMPTZ DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_insider_symbol_date
    ON insider_trades (symbol, txn_date DESC);

CREATE INDEX IF NOT EXISTS idx_insider_type_date
    ON insider_trades (txn_type, txn_date DESC);

ALTER TABLE insider_trades ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON insider_trades FOR SELECT USING (true);

-- =============================================================
-- 5. fmp_cache 온디맨드 캐시 테이블
-- =============================================================
CREATE TABLE IF NOT EXISTS fmp_cache (
    symbol          TEXT        NOT NULL,
    endpoint        TEXT        NOT NULL,
    response_json   JSONB       NOT NULL,
    fetched_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (symbol, endpoint)
);

CREATE INDEX IF NOT EXISTS idx_fmp_cache_ttl
    ON fmp_cache (fetched_at);

ALTER TABLE fmp_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON fmp_cache FOR SELECT USING (true);

-- =============================================================
-- 6. DB Function: ETF Holdings 역산출
-- =============================================================
CREATE OR REPLACE FUNCTION find_etfs_by_holding(
    p_symbol TEXT,
    p_min_weight NUMERIC DEFAULT 0,
    p_max_expense NUMERIC DEFAULT 1,
    p_min_pct_52h NUMERIC DEFAULT -100
)
RETURNS TABLE (
    symbol TEXT,
    name TEXT,
    expense_ratio NUMERIC,
    aum BIGINT,
    pct_from_52h NUMERIC,
    holding_weight NUMERIC
)
LANGUAGE sql STABLE AS $$
    SELECT e.symbol, e.name, e.expense_ratio, e.aum, e.pct_from_52h,
           (h->>'w')::numeric AS holding_weight
    FROM latest_equities e,
         jsonb_array_elements(e.holdings) AS h
    WHERE e.is_delisted = false
      AND e.asset_type = 'etf'
      AND e.expense_ratio <= p_max_expense
      AND (e.pct_from_52h >= p_min_pct_52h OR e.pct_from_52h IS NULL)
      AND h->>'s' = p_symbol
      AND (h->>'w')::numeric >= p_min_weight
    ORDER BY (h->>'w')::numeric DESC;
$$;
