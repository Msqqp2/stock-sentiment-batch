package com.musiqq.stockscreener.ui.watchlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.repository.EquityRepository
import com.musiqq.stockscreener.data.repository.WatchlistRepository
import com.musiqq.stockscreener.domain.model.Equity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class WatchlistUiState(
    val items: List<Equity> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(
    private val watchlistRepo: WatchlistRepository,
    private val equityRepo: EquityRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WatchlistUiState())
    val uiState: StateFlow<WatchlistUiState> = _uiState.asStateFlow()

    init {
        loadWatchlist()
    }

    fun loadWatchlist() {
        viewModelScope.launch {
            _uiState.value = WatchlistUiState(isLoading = true)
            try {
                val symbols = watchlistRepo.getSymbols().first()
                if (symbols.isEmpty()) {
                    _uiState.value = WatchlistUiState(isLoading = false)
                    return@launch
                }
                val equities = equityRepo.getBySymbols(symbols)
                // Maintain watchlist order
                val ordered = symbols.mapNotNull { sym -> equities.find { it.symbol == sym } }
                _uiState.value = WatchlistUiState(items = ordered, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = WatchlistUiState(
                    isLoading = false,
                    error = e.message ?: "관심종목 로드 실패",
                )
            }
        }
    }

    fun remove(symbol: String) {
        viewModelScope.launch {
            watchlistRepo.remove(symbol)
            _uiState.value = _uiState.value.copy(
                items = _uiState.value.items.filter { it.symbol != symbol },
            )
        }
    }
}
