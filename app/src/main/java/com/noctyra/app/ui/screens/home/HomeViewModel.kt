package com.noctyra.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _heroState = MutableStateFlow<UiState<List<Anime>>>(UiState.Loading)
    val heroState: StateFlow<UiState<List<Anime>>> = _heroState

    private val _recentState = MutableStateFlow<UiState<List<Anime>>>(UiState.Loading)
    val recentState: StateFlow<UiState<List<Anime>>> = _recentState

    private val _actionState = MutableStateFlow<UiState<List<Anime>>>(UiState.Loading)
    val actionState: StateFlow<UiState<List<Anime>>> = _actionState

    private val _isekaiState = MutableStateFlow<UiState<List<Anime>>>(UiState.Loading)
    val isekaiState: StateFlow<UiState<List<Anime>>> = _isekaiState

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _heroState.value = UiState.Loading
            _recentState.value = UiState.Loading
            try {
                val results = repository.getLatestReleases()
                _heroState.value = UiState.Success(results.take(5))
                _recentState.value = UiState.Success(results.drop(5).take(15))
            } catch (e: Exception) {
                _heroState.value = UiState.Error(e.message ?: "Erro ao carregar")
                _recentState.value = UiState.Error(e.message ?: "Erro ao carregar")
            }
        }

        viewModelScope.launch {
            _actionState.value = UiState.Loading
            try {
                _actionState.value = UiState.Success(repository.search("ação").take(10))
            } catch (e: Exception) {
                _actionState.value = UiState.Error(e.message ?: "Erro")
            }
        }

        viewModelScope.launch {
            _isekaiState.value = UiState.Loading
            try {
                _isekaiState.value = UiState.Success(repository.search("isekai").take(10))
            } catch (e: Exception) {
                _isekaiState.value = UiState.Error(e.message ?: "Erro")
            }
        }
    }
}
