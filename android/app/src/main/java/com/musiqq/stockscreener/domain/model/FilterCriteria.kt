package com.musiqq.stockscreener.domain.model

data class FilterCriteria(
    val assetType: String = "stock",
    // (1) 기본 정보
    val exchange: String? = null,
    val sectors: List<String> = emptyList(),
    val industry: String? = null,
    val country: String? = null,
    val indexMembership: String? = null,
    val marketCapMin: Long? = null,
    val marketCapMax: Long? = null,
    val priceMin: Double? = null,
    val priceMax: Double? = null,
    val turnoverMin: Double? = null,
    val turnoverMax: Double? = null,
    val volumeMin: Long? = null,
    val volumeMax: Long? = null,
    // (2) 밸류에이션
    val peMin: Double? = null,
    val peMax: Double? = null,
    val peForwardMin: Double? = null,
    val peForwardMax: Double? = null,
    val pegMin: Double? = null,
    val pegMax: Double? = null,
    val pbMin: Double? = null,
    val pbMax: Double? = null,
    val psMin: Double? = null,
    val psMax: Double? = null,
    val pfcfMin: Double? = null,
    val pfcfMax: Double? = null,
    val pcashMin: Double? = null,
    val pcashMax: Double? = null,
    val evEbitdaMin: Double? = null,
    val evEbitdaMax: Double? = null,
    val evRevenueMin: Double? = null,
    val evRevenueMax: Double? = null,
    val dcfUpsideMin: Double? = null,
    val dcfUpsideMax: Double? = null,
    val grahamUpsideMin: Double? = null,
    val grahamUpsideMax: Double? = null,
    // (3) 수익성/품질
    val roeMin: Double? = null,
    val roeMax: Double? = null,
    val roaMin: Double? = null,
    val roaMax: Double? = null,
    val roicMin: Double? = null,
    val roicMax: Double? = null,
    val operatingMarginMin: Double? = null,
    val operatingMarginMax: Double? = null,
    val profitMarginMin: Double? = null,
    val profitMarginMax: Double? = null,
    val grossMarginMin: Double? = null,
    val grossMarginMax: Double? = null,
    val piotroskiMin: Int? = null,
    val piotroskiMax: Int? = null,
    val altmanZMin: Double? = null,
    val altmanZMax: Double? = null,
    val earningsQualityMin: Double? = null,
    val earningsQualityMax: Double? = null,
    // (4) 성장
    val revenueGrowthMin: Double? = null,
    val revenueGrowthMax: Double? = null,
    val earningsGrowthMin: Double? = null,
    val earningsGrowthMax: Double? = null,
    val epsGrowthThisYrMin: Double? = null,
    val epsGrowthThisYrMax: Double? = null,
    val epsGrowthPast5yMin: Double? = null,
    val epsGrowthPast5yMax: Double? = null,
    val salesGrowthPast5yMin: Double? = null,
    val salesGrowthPast5yMax: Double? = null,
    val opIncomeGrowthMin: Double? = null,
    val opIncomeGrowthMax: Double? = null,
    // (5) 재무 건전성
    val debtToEquityMin: Double? = null,
    val debtToEquityMax: Double? = null,
    val ltDebtEquityMin: Double? = null,
    val ltDebtEquityMax: Double? = null,
    val currentRatioMin: Double? = null,
    val currentRatioMax: Double? = null,
    val quickRatioMin: Double? = null,
    val quickRatioMax: Double? = null,
    val interestCoverageMin: Double? = null,
    val interestCoverageMax: Double? = null,
    val debtGrowthMin: Double? = null,
    val debtGrowthMax: Double? = null,
    // (6) 배당
    val dividendYieldMin: Double? = null,
    val dividendYieldMax: Double? = null,
    val payoutRatioMin: Double? = null,
    val payoutRatioMax: Double? = null,
    val avgDividendYield5yMin: Double? = null,
    val avgDividendYield5yMax: Double? = null,
    val shareholderYieldMin: Double? = null,
    val shareholderYieldMax: Double? = null,
    val buybackYieldMin: Double? = null,
    val buybackYieldMax: Double? = null,
    // (7) 기술적 지표
    val rsiMin: Double? = null,
    val rsiMax: Double? = null,
    val sma20PctMin: Double? = null,
    val sma20PctMax: Double? = null,
    val sma50PctMin: Double? = null,
    val sma50PctMax: Double? = null,
    val sma200PctMin: Double? = null,
    val sma200PctMax: Double? = null,
    val pctFrom52hMin: Double? = null,
    val pctFrom52hMax: Double? = null,
    val pctFrom52lMin: Double? = null,
    val pctFrom52lMax: Double? = null,
    val betaMin: Double? = null,
    val betaMax: Double? = null,
    val volatilityWMin: Double? = null,
    val volatilityWMax: Double? = null,
    val volatilityMMin: Double? = null,
    val volatilityMMax: Double? = null,
    val relativeVolumeMin: Double? = null,
    val relativeVolumeMax: Double? = null,
    val gapPctMin: Double? = null,
    val gapPctMax: Double? = null,
    // (8) 퍼포먼스
    val changePctMin: Double? = null,
    val changePctMax: Double? = null,
    val perf1wMin: Double? = null,
    val perf1wMax: Double? = null,
    val perf1mMin: Double? = null,
    val perf1mMax: Double? = null,
    val perf3mMin: Double? = null,
    val perf3mMax: Double? = null,
    val perf6mMin: Double? = null,
    val perf6mMax: Double? = null,
    val perf1yMin: Double? = null,
    val perf1yMax: Double? = null,
    val perfYtdMin: Double? = null,
    val perfYtdMax: Double? = null,
    // (9) 공매도/기관
    val shortPctFloatMin: Double? = null,
    val shortPctFloatMax: Double? = null,
    val shortRatioMin: Double? = null,
    val shortRatioMax: Double? = null,
    val instPctMin: Double? = null,
    val instPctMax: Double? = null,
    val insiderPctMin: Double? = null,
    val insiderPctMax: Double? = null,
    // (10) 애널리스트
    val analystRatings: List<String> = emptyList(),
    val targetUpsideMin: Double? = null,
    val targetUpsideMax: Double? = null,
    val analystCountMin: Int? = null,
    val analystCountMax: Int? = null,
    // (11) 종합 점수
    val scoreTotalMin: Int? = null,
    val scoreTotalMax: Int? = null,
    val scoreValueMin: Int? = null,
    val scoreValueMax: Int? = null,
    val scoreQualityMin: Int? = null,
    val scoreQualityMax: Int? = null,
    val scoreMomentumMin: Int? = null,
    val scoreMomentumMax: Int? = null,
    val scoreGrowthMin: Int? = null,
    val scoreGrowthMax: Int? = null,
    // Legacy
    val insiderBuy3mMin: Int? = null,
    // ETF
    val expenseRatioMax: Double? = null,
    val aumMin: Long? = null,
    val assetClass: String? = null,
    val holdingSymbol: String? = null,
    val holdingMinWeight: Double? = null,
    // 정렬
    val orderBy: String = "market_cap",
    val orderDesc: Boolean = true,
    // 페이지
    val limit: Int = 50,
    val offset: Int = 0,
) {
    fun toQueryMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val rangeConditions = mutableListOf<String>()

        map["asset_type"] = "eq.$assetType"

        // Helper for range filters
        fun addRange(col: String, min: Any?, max: Any?) {
            if (min != null && max != null) {
                rangeConditions.add("$col.gte.$min")
                rangeConditions.add("$col.lte.$max")
            } else {
                min?.let { map[col] = "gte.$it" }
                max?.let { map[col] = "lte.$it" }
            }
        }

        fun addEq(col: String, value: String?) {
            value?.let { map[col] = "eq.$it" }
        }

        // (1) 기본 정보
        addEq("exchange", exchange)
        addEq("industry", industry)
        addEq("country", country)
        addEq("index_membership", indexMembership)
        addRange("market_cap", marketCapMin, marketCapMax)
        addRange("price", priceMin, priceMax)
        addRange("turnover", turnoverMin, turnoverMax)
        addRange("volume", volumeMin, volumeMax)
        // (2) 밸류에이션
        addRange("pe_ttm", peMin, peMax)
        addRange("pe_forward", peForwardMin, peForwardMax)
        addRange("peg_ratio", pegMin, pegMax)
        addRange("pb_ratio", pbMin, pbMax)
        addRange("ps_ratio", psMin, psMax)
        addRange("pfcf_ratio", pfcfMin, pfcfMax)
        addRange("pcash_ratio", pcashMin, pcashMax)
        addRange("ev_ebitda", evEbitdaMin, evEbitdaMax)
        addRange("ev_revenue", evRevenueMin, evRevenueMax)
        addRange("dcf_upside_pct", dcfUpsideMin, dcfUpsideMax)
        addRange("graham_upside_pct", grahamUpsideMin, grahamUpsideMax)
        // (3) 수익성
        addRange("roe", roeMin, roeMax)
        addRange("roa", roaMin, roaMax)
        addRange("roic", roicMin, roicMax)
        addRange("operating_margin", operatingMarginMin, operatingMarginMax)
        addRange("profit_margin", profitMarginMin, profitMarginMax)
        addRange("gross_margin", grossMarginMin, grossMarginMax)
        addRange("piotroski_score", piotroskiMin, piotroskiMax)
        addRange("altman_z_score", altmanZMin, altmanZMax)
        addRange("earnings_quality_score", earningsQualityMin, earningsQualityMax)
        // (4) 성장
        addRange("revenue_growth", revenueGrowthMin, revenueGrowthMax)
        addRange("earnings_growth", earningsGrowthMin, earningsGrowthMax)
        addRange("eps_growth_this_yr", epsGrowthThisYrMin, epsGrowthThisYrMax)
        addRange("eps_growth_past_5y", epsGrowthPast5yMin, epsGrowthPast5yMax)
        addRange("sales_growth_past_5y", salesGrowthPast5yMin, salesGrowthPast5yMax)
        addRange("op_income_growth", opIncomeGrowthMin, opIncomeGrowthMax)
        // (5) 재무 건전성
        addRange("debt_to_equity", debtToEquityMin, debtToEquityMax)
        addRange("lt_debt_equity", ltDebtEquityMin, ltDebtEquityMax)
        addRange("current_ratio", currentRatioMin, currentRatioMax)
        addRange("quick_ratio", quickRatioMin, quickRatioMax)
        addRange("interest_coverage", interestCoverageMin, interestCoverageMax)
        addRange("debt_growth", debtGrowthMin, debtGrowthMax)
        // (6) 배당
        addRange("dividend_yield", dividendYieldMin, dividendYieldMax)
        addRange("payout_ratio", payoutRatioMin, payoutRatioMax)
        addRange("avg_dividend_yield_5y", avgDividendYield5yMin, avgDividendYield5yMax)
        addRange("shareholder_yield", shareholderYieldMin, shareholderYieldMax)
        addRange("buyback_yield", buybackYieldMin, buybackYieldMax)
        // (7) 기술적
        addRange("rsi_14", rsiMin, rsiMax)
        addRange("sma20_pct", sma20PctMin, sma20PctMax)
        addRange("sma50_pct", sma50PctMin, sma50PctMax)
        addRange("sma200_pct", sma200PctMin, sma200PctMax)
        addRange("pct_from_52h", pctFrom52hMin, pctFrom52hMax)
        addRange("pct_from_52l", pctFrom52lMin, pctFrom52lMax)
        addRange("beta", betaMin, betaMax)
        addRange("volatility_w", volatilityWMin, volatilityWMax)
        addRange("volatility_m", volatilityMMin, volatilityMMax)
        addRange("relative_volume", relativeVolumeMin, relativeVolumeMax)
        addRange("gap_pct", gapPctMin, gapPctMax)
        // (8) 퍼포먼스
        addRange("change_pct", changePctMin, changePctMax)
        addRange("perf_1w", perf1wMin, perf1wMax)
        addRange("perf_1m", perf1mMin, perf1mMax)
        addRange("perf_3m", perf3mMin, perf3mMax)
        addRange("perf_6m", perf6mMin, perf6mMax)
        addRange("perf_1y", perf1yMin, perf1yMax)
        addRange("perf_ytd", perfYtdMin, perfYtdMax)
        // (9) 공매도/기관
        addRange("short_pct_float", shortPctFloatMin, shortPctFloatMax)
        addRange("short_ratio", shortRatioMin, shortRatioMax)
        addRange("inst_pct", instPctMin, instPctMax)
        addRange("insider_pct", insiderPctMin, insiderPctMax)
        // (10) 애널리스트
        addRange("target_upside_pct", targetUpsideMin, targetUpsideMax)
        addRange("analyst_count", analystCountMin, analystCountMax)
        // (11) 점수
        addRange("score_total", scoreTotalMin, scoreTotalMax)
        addRange("score_value", scoreValueMin, scoreValueMax)
        addRange("score_quality", scoreQualityMin, scoreQualityMax)
        addRange("score_momentum", scoreMomentumMin, scoreMomentumMax)
        addRange("score_growth", scoreGrowthMin, scoreGrowthMax)
        // Legacy
        insiderBuy3mMin?.let { map["insider_buy_3m"] = "gte.$it" }
        // ETF
        expenseRatioMax?.let { map["expense_ratio"] = "lte.$it" }
        aumMin?.let { map["aum"] = "gte.$it" }
        addEq("asset_class", assetClass)
        // Multi-select
        if (sectors.isNotEmpty()) {
            map["sector"] = "in.(${sectors.joinToString(",")})"
        }
        if (analystRatings.isNotEmpty()) {
            map["analyst_rating"] = "in.(${analystRatings.joinToString(",")})"
        }

        // Combine range conditions into "and"
        if (rangeConditions.isNotEmpty()) {
            map["and"] = "(${rangeConditions.joinToString(",")})"
        }

        return map
    }

    fun toOrderString(): String {
        val dir = if (orderDesc) "desc" else "asc"
        return "$orderBy.$dir.nullslast"
    }
}
