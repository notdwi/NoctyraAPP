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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<Anime>>?>(null)
    val results: StateFlow<UiState<List<Anime>>?> = _results.asStateFlow()

    private var searchJob: Job? = null
    private var initialApplied = false

    fun applyInitial(query: String) {
        if (initialApplied || query.isBlank()) return
        initialApplied = true
        updateQuery(query, immediate = true)
    }

    fun updateQuery(newQuery: String, immediate: Boolean = false) {
        _query.value = newQuery
        searchJob?.cancel()
        if (newQuery.trim().length < 2) {
            _results.value = null
            return
        }
        searchJob = viewModelScope.launch {
            if (!immediate) delay(450)
            _results.value = UiState.Loading
            _results.value = try {
                UiState.Success(AnimeRepository.search(newQuery.trim()))
            } catch (e: Exception) {
                UiState.Error(e.message ?: "Falha na busca")
            }
        }
    }
}
