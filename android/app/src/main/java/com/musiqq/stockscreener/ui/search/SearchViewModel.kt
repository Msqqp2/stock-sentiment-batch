package com.musiqq.stockscreener.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.repository.EquityRepository
import com.musiqq.stockscreener.domain.model.Equity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val repository: EquityRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Equity>>(emptyList())
    val results: StateFlow<List<Equity>> = _results.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        _error.value = null

        if (newQuery.length < 2) {
            _results.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            try {
                _results.value = repository.search(newQuery, limit = 15)
            } catch (e: Exception) {
                _results.value = emptyList()
                _error.value = e.message ?: "검색 실패"
            }
        }
    }

    fun clear() {
        _query.value = ""
        _results.value = emptyList()
        _error.value = null
    }
}
