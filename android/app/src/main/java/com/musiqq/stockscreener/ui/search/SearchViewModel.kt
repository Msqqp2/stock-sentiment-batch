package com.musiqq.stockscreener.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musiqq.stockscreener.data.remote.SupabaseApi
import com.musiqq.stockscreener.domain.model.Equity
import com.musiqq.stockscreener.domain.model.toDomain
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
    private val api: SupabaseApi,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<List<Equity>>(emptyList())
    val results: StateFlow<List<Equity>> = _results.asStateFlow()

    private var searchJob: Job? = null

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()

        if (newQuery.length < 2) {
            _results.value = emptyList()
            return
        }

        searchJob = viewModelScope.launch {
            delay(300)
            try {
                val orFilter = "(symbol.ilike.*$newQuery*,name.ilike.*$newQuery*)"
                val dtos = api.searchEquities(query = orFilter, limit = 15)
                _results.value = dtos.map { it.toDomain() }
            } catch (_: Exception) {
                _results.value = emptyList()
            }
        }
    }

    fun clear() {
        _query.value = ""
        _results.value = emptyList()
    }
}
