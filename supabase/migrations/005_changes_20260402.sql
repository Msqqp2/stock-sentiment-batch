-- =============================================
-- 005: 2026-04-02 배치 변경 (CHANGES_20260402.md)
-- =============================================

-- ── 1. Finnhub Recommendation History (신규 테이블) ──
CREATE TABLE IF NOT EXISTS fh_recommendation_history (
  id BIGSERIAL PRIMARY KEY,
  symbol TEXT NOT NULL,
  period DATE NOT NULL,
  strong_buy INT DEFAULT 0,
  buy INT DEFAULT 0,
  hold INT DEFAULT 0,
  sell INT DEFAULT 0,
  strong_sell INT DEFAULT 0,
  asof TIMESTAMPTZ,
  UNIQUE(symbol, period)
);
CREATE INDEX IF NOT EXISTS idx_fh_rec_symbol ON fh_recommendation_history(symbol);

-- ── 2. StockGeist 컬럼 삭제 ──
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_sentiment_pos;
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_sentiment_neg;
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_sentiment_neu;
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_emotionality;
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_mention_count;
ALTER TABLE latest_equities DROP COLUMN IF EXISTS sg_asof;

-- ── 3. ETF Profile — etfpy 전용 컬럼 추가 ──
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS issuer TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS brand TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS structure TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS index_tracked TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS asset_class TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS asset_class_size TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS asset_class_style TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS region TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS focus TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS niche TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS strategy TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS weighting_scheme TEXT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS pe_ratio FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS shares_outstanding FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS ma_20d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS ma_60d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS rsi_10d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS rsi_20d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS rsi_30d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS macd_15 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS macd_100 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS bollinger_lower_10 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS bollinger_lower_20 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS bollinger_lower_30 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS stoch_k_1d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS stoch_k_5d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS stoch_d_1d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS stoch_d_5d FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS resistance_1 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS resistance_2 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS support_1 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS support_2 FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS tracking_diff_up FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS tracking_diff_down FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS max_premium_discount FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS median_premium_discount FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS avg_spread_dollar FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS avg_spread_pct FLOAT;
ALTER TABLE etf_profile ADD COLUMN IF NOT EXISTS etfpy_asof TIMESTAMPTZ;

-- ── 6. Finviz — analyst_ratings_history 테이블 + latest_equities 컬럼 ──
CREATE TABLE IF NOT EXISTS analyst_ratings_history (
  id BIGSERIAL PRIMARY KEY,
  symbol TEXT NOT NULL,
  date DATE,
  action TEXT,
  analyst TEXT,
  rating TEXT,
  price_target TEXT,
  asof TIMESTAMPTZ,
  UNIQUE(symbol, date, analyst, action)
);
CREATE INDEX IF NOT EXISTS idx_ratings_symbol ON analyst_ratings_history(symbol);

ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS peers JSONB;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS etf_holders JSONB;
ALTER TABLE latest_equities ADD COLUMN IF NOT EXISTS asof_finviz TIMESTAMPTZ;

-- ── 9. 성능 인덱스 ──
CREATE INDEX IF NOT EXISTS idx_equities_score_total ON latest_equities(score_total DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_equities_market_cap ON latest_equities(market_cap DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_equities_volume ON latest_equities(volume DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_equities_change_pct ON latest_equities(change_pct DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_equities_sector ON latest_equities(sector);
CREATE INDEX IF NOT EXISTS idx_equities_industry ON latest_equities(industry);
CREATE INDEX IF NOT EXISTS idx_equities_asset_type ON latest_equities(asset_type);
CREATE INDEX IF NOT EXISTS idx_equities_is_delisted ON latest_equities(is_delisted);
CREATE INDEX IF NOT EXISTS idx_equities_active_score
  ON latest_equities(is_delisted, asset_type, score_total DESC NULLS LAST);
CREATE INDEX IF NOT EXISTS idx_holdings_etf ON etf_holdings(etf_symbol);
CREATE INDEX IF NOT EXISTS idx_sector_etf ON etf_sector_exposure(etf_symbol);
CREATE INDEX IF NOT EXISTS idx_country_etf ON etf_country_exposure(etf_symbol);
CREATE INDEX IF NOT EXISTS idx_insider_symbol ON insider_trades(symbol);

-- ── 10. etf_list_cache 삭제 ──
DROP TABLE IF EXISTS etf_list_cache;
