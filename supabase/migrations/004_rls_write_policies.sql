-- ============================================================
-- 004_rls_write_policies.sql
-- INSERT/UPDATE/DELETE를 service_role에만 허용하는 RLS 정책.
-- 기존 SELECT "Public read access" 정책은 유지.
-- ============================================================

-- ── latest_equities ──
CREATE POLICY "Service role write access" ON latest_equities
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── insider_trades ──
CREATE POLICY "Service role write access" ON insider_trades
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── fmp_cache ──
CREATE POLICY "Service role write access" ON fmp_cache
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── etf_profile ──
CREATE POLICY "Service role write access" ON etf_profile
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── etf_holdings ──
CREATE POLICY "Service role write access" ON etf_holdings
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── etf_sector_exposure ──
CREATE POLICY "Service role write access" ON etf_sector_exposure
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── etf_country_exposure ──
CREATE POLICY "Service role write access" ON etf_country_exposure
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');

-- ── etf_list_cache ──
CREATE POLICY "Service role write access" ON etf_list_cache
    FOR ALL USING (auth.role() = 'service_role')
    WITH CHECK (auth.role() = 'service_role');
