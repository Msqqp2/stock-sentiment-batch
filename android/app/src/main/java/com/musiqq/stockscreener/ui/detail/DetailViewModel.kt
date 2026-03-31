package com.musiqq.stockscreener.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.remote.dto.InsiderTradeDto
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

data class DetailUiState(
    val equity: Equity? = null,
    val insiderTrades: List<InsiderTradeDto> = emptyList(),
    val isLoading: Boolean = true,
    val isWatchlisted: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val equityRepository: EquityRepository,
    private val watchlistRepository: WatchlistRepository,
) : ViewModel() {

    private val symbol: String = savedStateHandle["symbol"] ?: ""

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    init {
        loadDetail()
    }

    private fun loadDetail() {
        viewModelScope.launch {
            _uiState.value = DetailUiState(isLoading = true)
            try {
                val equity = equityRepository.getBySymbol(symbol)
                val isWatchlisted = watchlistRepository.isWatchlisted(symbol).first()
                val insiders = try {
                    equityRepository.getInsiderTrades(symbol)
                } catch (_: Exception) {
                    emptyList()
                }
                _uiState.value = DetailUiState(
                    equity = equity,
                    insiderTrades = insiders,
                    isLoading = false,
                    isWatchlisted = isWatchlisted,
                )
            } catch (e: Exception) {
                _uiState.value = DetailUiState(
                    isLoading = false,
                    error = e.message ?: "로드 실패",
                )
            }
        }
    }

    fun toggleWatchlist() {
        val eq = _uiState.value.equity ?: return
        viewModelScope.launch {
            if (_uiState.value.isWatchlisted) {
                watchlistRepository.remove(eq.symbol)
            } else {
                watchlistRepository.add(eq.symbol, eq.name, eq.assetType)
            }
            _uiState.value = _uiState.value.copy(isWatchlisted = !_uiState.value.isWatchlisted)
        }
    }

    fun refresh() = loadDetail()
}
