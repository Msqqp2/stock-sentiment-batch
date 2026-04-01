package com.musiqq.stockscreener.data.remote.dto

import com.google.gson.annotations.SerializedName

data class EtfHoldingDto(
    @SerializedName("etf_symbol") val etfSymbol: String,
    @SerializedName("holding_symbol") val holdingSymbol: String? = null,
    @SerializedName("holding_name") val holdingName: String? = null,
    val weight: Double? = null,
    val shares: Int? = null,
    @SerializedName("market_value") val marketValue: Double? = null,
)

data class EtfSectorExposureDto(
    @SerializedName("etf_symbol") val etfSymbol: String,
    val sector: String,
    val weight: Double? = null,
)

data class EtfCountryExposureDto(
    @SerializedName("etf_symbol") val etfSymbol: String,
    val country: String,
    val weight: Double? = null,
)
