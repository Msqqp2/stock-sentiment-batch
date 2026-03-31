package com.musiqq.stockscreener.data.repository

import com.musiqq.stockscreener.data.local.dao.WatchlistDao
import com.musiqq.stockscreener.data.local.entity.WatchlistItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepository @Inject constructor(
    private val dao: WatchlistDao,
) {
    fun getAll(): Flow<List<WatchlistItem>> = dao.getAll()

    fun getSymbols(): Flow<List<String>> = getAll().map { list -> list.map { it.symbol } }

    fun isWatchlisted(symbol: String): Flow<Boolean> = dao.isWatched(symbol)

    suspend fun add(symbol: String, name: String, assetType: String = "stock") {
        dao.add(
            WatchlistItem(
                symbol = symbol,
                name = name,
                assetType = assetType,
            )
        )
    }

    suspend fun remove(symbol: String) = dao.removeBySymbol(symbol)

    suspend fun updateMemo(symbol: String, memo: String) = dao.updateMemo(symbol, memo)
}
