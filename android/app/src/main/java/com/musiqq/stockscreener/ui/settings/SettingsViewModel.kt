package com.musiqq.stockscreener.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.local.ScoreWeightPreferences
import com.musiqq.stockscreener.data.local.ThemeMode
import com.musiqq.stockscreener.data.local.ThemePreferences
import com.musiqq.stockscreener.domain.model.ScoreWeights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val themePrefs: ThemePreferences,
    private val scoreWeightPrefs: ScoreWeightPreferences,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = themePrefs.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val scoreWeights: StateFlow<ScoreWeights> = scoreWeightPrefs.weights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScoreWeights())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { themePrefs.setTheme(mode) }
    }

    fun setScoreWeights(weights: ScoreWeights) {
        viewModelScope.launch { scoreWeightPrefs.setWeights(weights) }
    }

    fun resetWeights() {
        viewModelScope.launch { scoreWeightPrefs.resetToDefault() }
    }
}
