package com.musiqq.stockscreener.ui.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.musiqq.stockscreener.data.repository.EquityRepository
import com.musiqq.stockscreener.domain.model.Equity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeatMapUiState(
    val data: List<Equity> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HeatMapViewModel @Inject constructor(
    private val repository: EquityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeatMapUiState())
    val uiState: StateFlow<HeatMapUiState> = _uiState.asStateFlow()

    private val gson = Gson()

    init {
        loadHeatmapData()
    }

    fun loadHeatmapData(sector: String? = null) {
        viewModelScope.launch {
            _uiState.value = HeatMapUiState(isLoading = true)
            try {
                val data = repository.getHeatmapData(sector)
                _uiState.value = HeatMapUiState(data = data, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = HeatMapUiState(
                    isLoading = false,
                    error = e.message ?: "히트맵 데이터 로드 실패",
                )
            }
        }
    }

    fun toJsonForWebView(): String {
        val items = _uiState.value.data
        if (items.isEmpty()) return "[]"

        val list = items.map { eq ->
            mapOf(
                "s" to eq.symbol,
                "n" to eq.name,
                "sec" to (eq.sector ?: "Other"),
                "mc" to (eq.marketCap ?: 0L),
                "cp" to (eq.changePct ?: 0.0),
            )
        }
        return gson.toJson(list)
    }
}
