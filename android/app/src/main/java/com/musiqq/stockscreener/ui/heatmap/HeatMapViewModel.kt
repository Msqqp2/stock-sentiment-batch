package com.musiqq.stockscreener.ui.heatmap

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.remote.dto.EquityDto
import com.musiqq.stockscreener.data.repository.EquityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HeatMapUiState(
    val data: List<EquityDto> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class HeatMapViewModel @Inject constructor(
    private val repository: EquityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HeatMapUiState())
    val uiState: StateFlow<HeatMapUiState> = _uiState.asStateFlow()

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

        val sb = StringBuilder("[")
        items.forEachIndexed { index, dto ->
            if (index > 0) sb.append(",")
            val symbol = dto.symbol.replace("\"", "\\\"")
            val name = dto.name.replace("\"", "\\\"")
            val sector = (dto.sector ?: "Other").replace("\"", "\\\"")
            val marketCap = dto.marketCap ?: 0L
            val changePct = dto.changePct ?: 0.0
            sb.append("""{"s":"$symbol","n":"$name","sec":"$sector","mc":$marketCap,"cp":$changePct}""")
        }
        sb.append("]")
        return sb.toString()
    }
}
