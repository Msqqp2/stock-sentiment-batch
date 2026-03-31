package com.musiqq.stockscreener.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.musiqq.stockscreener.data.local.entity.WatchlistItem
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {
    @Query("SELECT * FROM watchlist ORDER BY addedAt DESC")
    fun getAll(): Flow<List<WatchlistItem>>

    @Query("SELECT symbol FROM watchlist")
    suspend fun getAllSymbols(): List<String>

    @Query("SELECT EXISTS(SELECT 1 FROM watchlist WHERE symbol = :symbol)")
    fun isWatched(symbol: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: WatchlistItem)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun removeBySymbol(symbol: String)

    @Query("UPDATE watchlist SET memo = :memo WHERE symbol = :symbol")
    suspend fun updateMemo(symbol: String, memo: String?)

    @Query("SELECT COUNT(*) FROM watchlist")
    fun count(): Flow<Int>
}
