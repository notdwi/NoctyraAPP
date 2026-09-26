package com.noctyra.app.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query

    private val _results = MutableStateFlow<UiState<List<Anime>>?>(null)
    val results: StateFlow<UiState<List<Anime>>?> = _results

    private var searchJob: Job? = null

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.length < 2) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _results.value = UiState.Loading
            try {
                val data = repository.search(newQuery)
                _results.value = UiState.Success(data)
            } catch (e: Exception) {
                _results.value = UiState.Error(e.message ?: "Search failed")
            }
        }
    }
}
