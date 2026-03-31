package com.musiqq.stockscreener.data.remote

import com.musiqq.stockscreener.data.remote.dto.EquityDto
import com.musiqq.stockscreener.data.remote.dto.InsiderTradeDto
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface SupabaseApi {

    @GET("rest/v1/latest_equities")
    suspend fun getEquities(
        @QueryMap filters: Map<String, String> = emptyMap(),
        @Query("select") select: String = "*",
        @Query("is_delisted") isDelisted: String = "eq.false",
        @Query("order") order: String = "market_cap.desc.nullslast",
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
    ): List<EquityDto>

    @GET("rest/v1/latest_equities")
    suspend fun searchEquities(
        @Query("or") query: String,
        @Query("select") select: String = "symbol,name,asset_type,price,market_cap,change_pct,score_total,sector,volume",
        @Query("is_delisted") isDelisted: String = "eq.false",
        @Query("order") order: String = "market_cap.desc.nullslast",
        @Query("limit") limit: Int = 10,
    ): List<EquityDto>

    @GET("rest/v1/latest_equities")
    suspend fun getEquityBySymbol(
        @Query("symbol") symbol: String,
        @Query("select") select: String = "*",
    ): List<EquityDto>

    @GET("rest/v1/latest_equities")
    suspend fun getEquitiesBySymbols(
        @Query("symbol") symbols: String,
        @Query("select") select: String = "*",
    ): List<EquityDto>

    @GET("rest/v1/insider_trades")
    suspend fun getInsiderTrades(
        @Query("symbol") symbol: String,
        @Query("order") order: String = "txn_date.desc",
        @Query("limit") limit: Int = 10,
    ): List<InsiderTradeDto>

    @GET("rest/v1/latest_equities")
    suspend fun getHeatmapData(
        @QueryMap filters: Map<String, String> = emptyMap(),
        @Query("select") select: String = "symbol,name,sector,market_cap,change_pct,price",
        @Query("is_delisted") isDelisted: String = "eq.false",
        @Query("order") order: String = "market_cap.desc.nullslast",
        @Query("limit") limit: Int = 200,
    ): List<EquityDto>
}
