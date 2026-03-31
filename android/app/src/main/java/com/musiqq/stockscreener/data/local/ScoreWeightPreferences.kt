package com.musiqq.stockscreener.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import com.musiqq.stockscreener.domain.model.ScoreWeights
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ScoreWeightPreferences(private val context: Context) {

    val weights: Flow<ScoreWeights> = context.dataStore.data.map { prefs ->
        ScoreWeights(
            value = prefs[KEY_VALUE] ?: 0.25f,
            quality = prefs[KEY_QUALITY] ?: 0.25f,
            momentum = prefs[KEY_MOMENTUM] ?: 0.25f,
            growth = prefs[KEY_GROWTH] ?: 0.25f,
        )
    }

    suspend fun setWeights(weights: ScoreWeights) {
        context.dataStore.edit {
            it[KEY_VALUE] = weights.value
            it[KEY_QUALITY] = weights.quality
            it[KEY_MOMENTUM] = weights.momentum
            it[KEY_GROWTH] = weights.growth
        }
    }

    suspend fun resetToDefault() = setWeights(ScoreWeights())

    companion object {
        val KEY_VALUE = floatPreferencesKey("score_weight_value")
        val KEY_QUALITY = floatPreferencesKey("score_weight_quality")
        val KEY_MOMENTUM = floatPreferencesKey("score_weight_momentum")
        val KEY_GROWTH = floatPreferencesKey("score_weight_growth")
    }
}
