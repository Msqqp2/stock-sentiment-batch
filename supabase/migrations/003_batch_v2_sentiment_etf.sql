-- =============================================================
-- US Stock Screener — Migration v3: Sentiment + ETF 배치 확장
-- repo-B 데이터 소스 (Finnhub, StockGeist, Adanos, Finnhub ETF)
-- =============================================================

-- ── 1. latest_equities: Finnhub Sentiment 컬럼 추가 ──
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_sentiment FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_positive INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_social_negative INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_mspr FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_change FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_buy INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_hold INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_sell INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_strong_buy INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_strong_sell INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_rec_period TEXT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_buy_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_insider_sell_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS fh_asof TIMESTAMPTZ;

-- ── 2. latest_equities: StockGeist 컬럼 추가 ──
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_pos FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_neg FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_sentiment_neu FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_emotionality FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_mention_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS sg_asof TIMESTAMPTZ;

-- ── 3. latest_equities: Adanos Polymarket 컬럼 추가 ──
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_buzz_score FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_trend TEXT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_sentiment_score FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_bullish_pct INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_bearish_pct INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_trade_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_market_count INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_unique_traders INT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_total_liquidity FLOAT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS pm_asof TIMESTAMPTZ;

-- ── 4. etf_profile 테이블 ──
CREATE TABLE IF NOT EXISTS etf_profile (
    symbol          TEXT PRIMARY KEY,
    name            TEXT,
    fund_family     TEXT,
    category        TEXT,
    expense_ratio   FLOAT,
    total_assets    FLOAT,
    yield           FLOAT,
    beta_3y         FLOAT,
    price           FLOAT,
    prev_close      FLOAT,
    change_pct      FLOAT,
    volume          BIGINT,
    avg_volume_10d  BIGINT,
    week52_high     FLOAT,
    week52_low      FLOAT,
    inception_date  TEXT,
    asof            TIMESTAMPTZ
);

ALTER TABLE etf_profile ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON etf_profile FOR SELECT USING (true);

-- ── 5. etf_holdings 테이블 ──
CREATE TABLE IF NOT EXISTS etf_holdings (
    id              BIGSERIAL PRIMARY KEY,
    etf_symbol      TEXT NOT NULL,
    holding_symbol  TEXT,
    holding_name    TEXT,
    weight          FLOAT,
    shares          INT,
    market_value    FLOAT,
    asof            TIMESTAMPTZ,
    UNIQUE(etf_symbol, holding_symbol)
);

CREATE INDEX IF NOT EXISTS idx_etf_holdings_symbol
    ON etf_holdings (etf_symbol);

CREATE INDEX IF NOT EXISTS idx_etf_holdings_holding
    ON etf_holdings (holding_symbol);

ALTER TABLE etf_holdings ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON etf_holdings FOR SELECT USING (true);

-- ── 6. etf_sector_exposure 테이블 ──
CREATE TABLE IF NOT EXISTS etf_sector_exposure (
    id              BIGSERIAL PRIMARY KEY,
    etf_symbol      TEXT NOT NULL,
    sector          TEXT NOT NULL,
    weight          FLOAT,
    asof            TIMESTAMPTZ,
    UNIQUE(etf_symbol, sector)
);

CREATE INDEX IF NOT EXISTS idx_etf_sector_symbol
    ON etf_sector_exposure (etf_symbol);

ALTER TABLE etf_sector_exposure ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON etf_sector_exposure FOR SELECT USING (true);

-- ── 7. etf_country_exposure 테이블 ──
CREATE TABLE IF NOT EXISTS etf_country_exposure (
    id              BIGSERIAL PRIMARY KEY,
    etf_symbol      TEXT NOT NULL,
    country         TEXT NOT NULL,
    weight          FLOAT,
    asof            TIMESTAMPTZ,
    UNIQUE(etf_symbol, country)
);

CREATE INDEX IF NOT EXISTS idx_etf_country_symbol
    ON etf_country_exposure (etf_symbol);

ALTER TABLE etf_country_exposure ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON etf_country_exposure FOR SELECT USING (true);

-- ── 8. etf_list_cache 테이블 (Finnhub 지원 ETF 캐싱) ──
CREATE TABLE IF NOT EXISTS etf_list_cache (
    symbol          TEXT PRIMARY KEY,
    name            TEXT,
    cached_at       TIMESTAMPTZ
);

ALTER TABLE etf_list_cache ENABLE ROW LEVEL SECURITY;
CREATE POLICY "Public read access" ON etf_list_cache FOR SELECT USING (true);

-- ── 9. Finnhub Sentiment 인덱스 ──
CREATE INDEX IF NOT EXISTS idx_fh_insider_mspr
    ON latest_equities (fh_insider_mspr DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

CREATE INDEX IF NOT EXISTS idx_fh_social_sentiment
    ON latest_equities (fh_social_sentiment DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';

-- ── 10. Polymarket Sentiment 인덱스 ──
CREATE INDEX IF NOT EXISTS idx_pm_buzz_score
    ON latest_equities (pm_buzz_score DESC NULLS LAST)
    WHERE is_delisted = false AND asset_type = 'stock';
