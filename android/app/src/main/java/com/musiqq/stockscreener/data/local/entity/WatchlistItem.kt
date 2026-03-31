package com.musiqq.stockscreener.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist")
data class WatchlistItem(
    @PrimaryKey
    val symbol: String,
    val name: String,
    val assetType: String = "stock",
    val addedAt: Long = System.currentTimeMillis(),
    val memo: String? = null,
)
