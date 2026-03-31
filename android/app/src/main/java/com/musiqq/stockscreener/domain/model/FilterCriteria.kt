package com.musiqq.stockscreener.domain.model

data class FilterCriteria(
    val assetType: String = "stock",
    val marketCapMin: Long? = null,
    val marketCapMax: Long? = null,
    val peMin: Double? = null,
    val peMax: Double? = null,
    val dividendYieldMin: Double? = null,
    val pctFrom52hMin: Double? = null,
    val pctFrom52hMax: Double? = null,
    val sectors: List<String> = emptyList(),
    val roeMin: Double? = null,
    val debtToEquityMax: Double? = null,
    val revenueGrowthMin: Double? = null,
    val analystRatings: List<String> = emptyList(),
    val targetUpsideMin: Double? = null,
    val insiderBuy3mMin: Int? = null,
    val shortPctFloatMin: Double? = null,
    val shortPctFloatMax: Double? = null,
    // ETF
    val expenseRatioMax: Double? = null,
    val aumMin: Long? = null,
    val assetClass: String? = null,
    // Holdings 역산출
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

        map["asset_type"] = "eq.$assetType"

        marketCapMin?.let { map["market_cap"] = "gte.$it" }
        marketCapMax?.let {
            val existing = map["market_cap"]
            if (existing != null) {
                // 범위 필터: and 조건으로 결합
                map.remove("market_cap")
                map["and"] = "(market_cap.gte.${marketCapMin},market_cap.lte.$it)"
            } else {
                map["market_cap"] = "lte.$it"
            }
        }
        // PE 범위 필터
        if (peMin != null && peMax != null) {
            val conditions = mutableListOf<String>()
            conditions.add("pe_ttm.gte.$peMin")
            conditions.add("pe_ttm.lte.$peMax")
            val existing = map["and"]
            if (existing != null) {
                map["and"] = existing.dropLast(1) + ",${conditions.joinToString(",")})"
            } else {
                map["and"] = "(${conditions.joinToString(",")})"
            }
        } else {
            peMin?.let { map["pe_ttm"] = "gte.$it" }
            peMax?.let { map["pe_ttm"] = "lte.$it" }
        }
        dividendYieldMin?.let { map["dividend_yield"] = "gte.$it" }
        pctFrom52hMin?.let { map["pct_from_52h"] = "gte.$it" }
        pctFrom52hMax?.let {
            val existing = map["pct_from_52h"]
            if (existing != null) {
                map.remove("pct_from_52h")
                val andVal = map["and"]
                val cond = "pct_from_52h.gte.${pctFrom52hMin},pct_from_52h.lte.$it"
                map["and"] = if (andVal != null) andVal.dropLast(1) + ",$cond)" else "($cond)"
            } else {
                map["pct_from_52h"] = "lte.$it"
            }
        }
        roeMin?.let { map["roe"] = "gte.$it" }
        debtToEquityMax?.let { map["debt_to_equity"] = "lte.$it" }
        revenueGrowthMin?.let { map["revenue_growth"] = "gte.$it" }
        targetUpsideMin?.let { map["target_upside_pct"] = "gte.$it" }
        insiderBuy3mMin?.let { map["insider_buy_3m"] = "gte.$it" }
        shortPctFloatMin?.let { map["short_pct_float"] = "gte.$it" }
        shortPctFloatMax?.let {
            val existing = map["short_pct_float"]
            if (existing != null) {
                map.remove("short_pct_float")
                val andVal = map["and"]
                val cond = "short_pct_float.gte.${shortPctFloatMin},short_pct_float.lte.$it"
                map["and"] = if (andVal != null) andVal.dropLast(1) + ",$cond)" else "($cond)"
            } else {
                map["short_pct_float"] = "lte.$it"
            }
        }
        expenseRatioMax?.let { map["expense_ratio"] = "lte.$it" }
        aumMin?.let { map["aum"] = "gte.$it" }
        assetClass?.let { map["asset_class"] = "eq.$it" }
        if (sectors.isNotEmpty()) {
            map["sector"] = "in.(${sectors.joinToString(",")})"
        }
        if (analystRatings.isNotEmpty()) {
            map["analyst_rating"] = "in.(${analystRatings.joinToString(",")})"
        }

        return map
    }

    fun toOrderString(): String {
        val dir = if (orderDesc) "desc" else "asc"
        return "$orderBy.$dir.nullslast"
    }
}
