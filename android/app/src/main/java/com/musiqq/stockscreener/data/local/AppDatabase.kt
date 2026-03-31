package com.musiqq.stockscreener.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.musiqq.stockscreener.data.local.dao.WatchlistDao
import com.musiqq.stockscreener.data.local.entity.WatchlistItem

@Database(entities = [WatchlistItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun watchlistDao(): WatchlistDao
}
