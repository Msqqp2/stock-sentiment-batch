package com.musiqq.stockscreener.data.remote

import com.musiqq.stockscreener.data.remote.dto.EquityDto
import com.musiqq.stockscreener.data.remote.dto.EtfCountryExposureDto
import com.musiqq.stockscreener.data.remote.dto.EtfHoldingDto
import com.musiqq.stockscreener.data.remote.dto.EtfSectorExposureDto
import com.musiqq.stockscreener.data.remote.dto.InsiderTradeDto
import com.google.gson.annotations.SerializedName
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.QueryMap

data class RecommendationHistoryDto(
    val symbol: String? = null,
    val period: String? = null,
    @SerializedName("strong_buy") val strongBuy: Int? = null,
    val buy: Int? = null,
    val hold: Int? = null,
    val sell: Int? = null,
    @SerializedName("strong_sell") val strongSell: Int? = null,
)

data class AnalystRatingDto(
    val symbol: String? = null,
    val date: String? = null,
    val action: String? = null,
    val analyst: String? = null,
    val rating: String? = null,
    @SerializedName("price_target") val priceTarget: String? = null,
)

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

    @GET("rest/v1/etf_holdings")
    suspend fun getEtfHoldings(
        @Query("etf_symbol") etfSymbol: String,
        @Query("select") select: String = "etf_symbol,holding_symbol,holding_name,weight,shares,market_value",
        @Query("order") order: String = "weight.desc.nullslast",
        @Query("limit") limit: Int = 50,
    ): List<EtfHoldingDto>

    @GET("rest/v1/etf_sector_exposure")
    suspend fun getEtfSectorExposure(
        @Query("etf_symbol") etfSymbol: String,
        @Query("select") select: String = "etf_symbol,sector,weight",
        @Query("order") order: String = "weight.desc.nullslast",
    ): List<EtfSectorExposureDto>

    @GET("rest/v1/etf_country_exposure")
    suspend fun getEtfCountryExposure(
        @Query("etf_symbol") etfSymbol: String,
        @Query("select") select: String = "etf_symbol,country,weight",
        @Query("order") order: String = "weight.desc.nullslast",
    ): List<EtfCountryExposureDto>

    @GET("rest/v1/fh_recommendation_history")
    suspend fun getRecommendationHistory(
        @Query("symbol") symbol: String,
        @Query("order") order: String = "period.desc",
        @Query("limit") limit: Int = 4,
    ): List<RecommendationHistoryDto>

    @GET("rest/v1/analyst_ratings_history")
    suspend fun getAnalystRatingsHistory(
        @Query("symbol") symbol: String,
        @Query("order") order: String = "date.desc",
        @Query("limit") limit: Int = 20,
    ): List<AnalystRatingDto>

    @GET("rest/v1/latest_equities")
    suspend fun getHeatmapData(
        @QueryMap filters: Map<String, String> = emptyMap(),
        @Query("select") select: String = "symbol,name,sector,market_cap,change_pct,price",
        @Query("is_delisted") isDelisted: String = "eq.false",
        @Query("order") order: String = "market_cap.desc.nullslast",
        @Query("limit") limit: Int = 200,
    ): List<EquityDto>
}
