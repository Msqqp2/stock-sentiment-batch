package com.musiqq.stockscreener.data.repository

import com.musiqq.stockscreener.data.remote.SupabaseApi
import com.musiqq.stockscreener.data.remote.dto.EquityDto
import com.musiqq.stockscreener.data.remote.dto.InsiderTradeDto
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.domain.model.FilterCriteria
import com.musiqq.stockscreener.domain.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EquityRepository @Inject constructor(
    private val api: SupabaseApi,
) {
    suspend fun getEquities(criteria: FilterCriteria): List<Equity> {
        val queryMap = criteria.toQueryMap()
        val order = criteria.toOrderString()
        val dtos = api.getEquities(
            filters = queryMap,
            order = order,
            limit = criteria.limit,
            offset = criteria.offset,
        )
        return dtos.map { it.toDomain() }
    }

    suspend fun search(query: String, limit: Int = 20): List<Equity> {
        val dtos = api.searchEquities(query = "%$query%", limit = limit)
        return dtos.map { it.toDomain() }
    }

    suspend fun getBySymbol(symbol: String): Equity? {
        return api.getEquityBySymbol(symbol = "eq.$symbol")
            .firstOrNull()?.toDomain()
    }

    suspend fun getBySymbols(symbols: List<String>): List<Equity> {
        if (symbols.isEmpty()) return emptyList()
        val param = "in.(${symbols.joinToString(",")})"
        return api.getEquitiesBySymbols(symbols = param).map { it.toDomain() }
    }

    suspend fun getInsiderTrades(symbol: String): List<InsiderTradeDto> {
        return api.getInsiderTrades(
            symbol = "eq.$symbol",
            order = "filing_date.desc.nullslast",
            limit = 50,
        )
    }

    suspend fun getHeatmapData(sector: String? = null): List<EquityDto> {
        val filters = mutableMapOf<String, String>()
        filters["asset_type"] = "eq.stock"
        filters["market_cap"] = "gte.2000000000" // $2B+
        sector?.let { filters["sector"] = "eq.$it" }
        return api.getHeatmapData(
            filters = filters,
            select = "symbol,name,sector,market_cap,change_pct",
            order = "market_cap.desc.nullslast",
            limit = 500,
        )
    }
}
