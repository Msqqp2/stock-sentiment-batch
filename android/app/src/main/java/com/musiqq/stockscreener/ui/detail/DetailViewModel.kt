package com.musiqq.stockscreener.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.remote.AnalystRatingDto
import com.musiqq.stockscreener.data.remote.RecommendationHistoryDto
import com.musiqq.stockscreener.data.remote.dto.EtfCountryExposureDto
import com.musiqq.stockscreener.data.remote.dto.EtfHoldingDto
import com.musiqq.stockscreener.data.remote.dto.EtfSectorExposureDto
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
    val etfHoldings: List<EtfHoldingDto> = emptyList(),
    val etfSectors: List<EtfSectorExposureDto> = emptyList(),
    val etfCountries: List<EtfCountryExposureDto> = emptyList(),
    val recommendationHistory: List<RecommendationHistoryDto> = emptyList(),
    val analystRatings: List<AnalystRatingDto> = emptyList(),
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
                // ETF 데이터 로딩
                val isEtf = equity?.assetType.equals("etf", ignoreCase = true)
                val holdings = if (isEtf) {
                    try { equityRepository.getEtfHoldings(symbol) } catch (_: Exception) { emptyList() }
                } else emptyList()
                val sectors = if (isEtf) {
                    try { equityRepository.getEtfSectorExposure(symbol) } catch (_: Exception) { emptyList() }
                } else emptyList()
                val countries = if (isEtf) {
                    try { equityRepository.getEtfCountryExposure(symbol) } catch (_: Exception) { emptyList() }
                } else emptyList()

                // Recommendation history + analyst ratings
                val recHistory = if (!isEtf) {
                    try { equityRepository.getRecommendationHistory(symbol) } catch (_: Exception) { emptyList() }
                } else emptyList()
                val analystRatings = if (!isEtf) {
                    try { equityRepository.getAnalystRatingsHistory(symbol) } catch (_: Exception) { emptyList() }
                } else emptyList()

                _uiState.value = DetailUiState(
                    equity = equity,
                    insiderTrades = insiders,
                    etfHoldings = holdings,
                    etfSectors = sectors,
                    etfCountries = countries,
                    recommendationHistory = recHistory,
                    analystRatings = analystRatings,
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

    private var watchlistToggling = false

    fun toggleWatchlist() {
        val eq = _uiState.value.equity ?: return
        if (watchlistToggling) return
        watchlistToggling = true
        val wasWatchlisted = _uiState.value.isWatchlisted
        // 즉시 UI 반영
        _uiState.value = _uiState.value.copy(isWatchlisted = !wasWatchlisted)
        viewModelScope.launch {
            try {
                if (wasWatchlisted) {
                    watchlistRepository.remove(eq.symbol)
                } else {
                    watchlistRepository.add(eq.symbol, eq.name, eq.assetType)
                }
            } catch (_: Exception) {
                // 실패 시 롤백
                _uiState.value = _uiState.value.copy(isWatchlisted = wasWatchlisted)
            } finally {
                watchlistToggling = false
            }
        }
    }

    fun refresh() = loadDetail()
}
