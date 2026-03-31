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
        peMin?.let { map["pe_ttm"] = "gte.$it" }
        peMax?.let {
            val existing = map["pe_ttm"]
            if (existing != null) {
                // PostgREST doesn't support dual range on same column easily
                // Use separate param name approach isn't supported
                // For now, just use max
            }
            map["pe_ttm"] = "lte.$it"
        }
        dividendYieldMin?.let { map["dividend_yield"] = "gte.$it" }
        pctFrom52hMin?.let { map["pct_from_52h"] = "gte.$it" }
        roeMin?.let { map["roe"] = "gte.$it" }
        debtToEquityMax?.let { map["debt_to_equity"] = "lte.$it" }
        revenueGrowthMin?.let { map["revenue_growth"] = "gte.$it" }
        targetUpsideMin?.let { map["target_upside_pct"] = "gte.$it" }
        insiderBuy3mMin?.let { map["insider_buy_3m"] = "gte.$it" }
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
