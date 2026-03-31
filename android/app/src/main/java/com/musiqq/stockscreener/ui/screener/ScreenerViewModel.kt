package com.musiqq.stockscreener.ui.screener

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.local.ScoreWeightPreferences
import com.musiqq.stockscreener.data.repository.EquityRepository
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.domain.model.FilterCriteria
import com.musiqq.stockscreener.domain.model.PresetSignal
import com.musiqq.stockscreener.domain.model.ScoreWeights
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScreenerUiState(
    val items: List<Equity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val criteria: FilterCriteria = FilterCriteria(),
    val activePreset: PresetSignal? = null,
    val hasMore: Boolean = true,
    val page: Int = 0,
)

@HiltViewModel
class ScreenerViewModel @Inject constructor(
    private val repository: EquityRepository,
    scoreWeightPrefs: ScoreWeightPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScreenerUiState())
    val uiState: StateFlow<ScreenerUiState> = _uiState.asStateFlow()

    val scoreWeights: StateFlow<ScoreWeights> = scoreWeightPrefs.weights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScoreWeights())

    private val pageSize = 50

    init {
        loadFirstPage()
    }

    fun loadFirstPage() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, page = 0)
            try {
                val criteria = _uiState.value.criteria.copy(limit = pageSize, offset = 0)
                val items = repository.getEquities(criteria)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false,
                    hasMore = items.size >= pageSize,
                    page = 0,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "로드 실패",
                )
            }
        }
    }

    fun loadNextPage() {
        if (_uiState.value.isLoading || !_uiState.value.hasMore) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val nextPage = _uiState.value.page + 1
                val criteria = _uiState.value.criteria.copy(
                    limit = pageSize,
                    offset = nextPage * pageSize,
                )
                val newItems = repository.getEquities(criteria)
                _uiState.value = _uiState.value.copy(
                    items = _uiState.value.items + newItems,
                    isLoading = false,
                    hasMore = newItems.size >= pageSize,
                    page = nextPage,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun applyPreset(preset: PresetSignal?) {
        val newCriteria = if (preset != null) {
            FilterCriteria(orderBy = preset.orderBy, orderDesc = preset.orderDesc)
        } else {
            FilterCriteria()
        }
        _uiState.value = _uiState.value.copy(criteria = newCriteria, activePreset = preset)
        loadFirstPageWithOverrides(preset?.filters)
    }

    fun updateCriteria(criteria: FilterCriteria) {
        _uiState.value = _uiState.value.copy(criteria = criteria, activePreset = null)
        loadFirstPage()
    }

    fun toggleSort(column: String) {
        val current = _uiState.value.criteria
        val newDesc = if (current.orderBy == column) !current.orderDesc else true
        _uiState.value = _uiState.value.copy(
            criteria = current.copy(orderBy = column, orderDesc = newDesc),
        )
        loadFirstPage()
    }

    fun switchAssetType(type: String) {
        _uiState.value = _uiState.value.copy(
            criteria = _uiState.value.criteria.copy(assetType = type),
            activePreset = null,
        )
        loadFirstPage()
    }

    private fun loadFirstPageWithOverrides(overrideFilters: Map<String, String>?) {
        if (overrideFilters == null) {
            loadFirstPage()
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, page = 0)
            try {
                val criteria = _uiState.value.criteria.copy(limit = pageSize, offset = 0)
                val items = repository.getEquitiesWithOverrides(criteria, overrideFilters)
                _uiState.value = _uiState.value.copy(
                    items = items,
                    isLoading = false,
                    hasMore = items.size >= pageSize,
                    page = 0,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "로드 실패",
                )
            }
        }
    }
}
