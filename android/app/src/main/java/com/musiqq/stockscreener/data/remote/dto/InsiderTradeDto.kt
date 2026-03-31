package com.musiqq.stockscreener.data.remote.dto

import com.google.gson.annotations.SerializedName

data class InsiderTradeDto(
    val id: Long,
    val symbol: String,
    @SerializedName("insider_name") val insiderName: String,
    @SerializedName("insider_title") val insiderTitle: String?,
    @SerializedName("txn_type") val txnType: String,
    @SerializedName("txn_date") val txnDate: String,
    val shares: Long,
    val price: Double?,
    @SerializedName("total_value") val totalValue: Double?,
    @SerializedName("shares_after") val sharesAfter: Long?,
    @SerializedName("filing_date") val filingDate: String,
)
