package com.musiqq.stockscreener.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.repository.EquityRepository
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.domain.model.FilterCriteria
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

    // 기본 종목 리스트 (검색어 없을 때 표시)
    private val _defaultItems = MutableStateFlow<List<Equity>>(emptyList())
    val defaultItems: StateFlow<List<Equity>> = _defaultItems.asStateFlow()

    private val _isLoadingDefault = MutableStateFlow(false)
    val isLoadingDefault: StateFlow<Boolean> = _isLoadingDefault.asStateFlow()

    private var defaultHasMore = true
    private var defaultPage = 0
    private val pageSize = 50

    private var searchJob: Job? = null

    init {
        loadDefaultPage()
    }

    private fun loadDefaultPage() {
        viewModelScope.launch {
            _isLoadingDefault.value = true
            try {
                val criteria = FilterCriteria(limit = pageSize, offset = 0)
                val items = repository.getEquities(criteria)
                _defaultItems.value = items
                defaultHasMore = items.size >= pageSize
                defaultPage = 0
            } catch (e: Exception) {
                _error.value = e.message ?: "로드 실패"
            } finally {
                _isLoadingDefault.value = false
            }
        }
    }

    fun loadNextDefaultPage() {
        if (_isLoadingDefault.value || !defaultHasMore) return
        viewModelScope.launch {
            _isLoadingDefault.value = true
            try {
                val nextPage = defaultPage + 1
                val criteria = FilterCriteria(
                    limit = pageSize,
                    offset = nextPage * pageSize,
                )
                val newItems = repository.getEquities(criteria)
                _defaultItems.value = _defaultItems.value + newItems
                defaultHasMore = newItems.size >= pageSize
                defaultPage = nextPage
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoadingDefault.value = false
            }
        }
    }

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
