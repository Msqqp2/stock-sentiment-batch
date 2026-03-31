package com.musiqq.stockscreener.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, DARK, LIGHT }

class ThemePreferences(private val context: Context) {

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        try {
            ThemeMode.valueOf(prefs[THEME_KEY] ?: ThemeMode.SYSTEM.name)
        } catch (_: Exception) {
            ThemeMode.SYSTEM
        }
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.dataStore.edit { it[THEME_KEY] = mode.name }
    }

    companion object {
        val THEME_KEY = stringPreferencesKey("theme_mode")
    }
}
