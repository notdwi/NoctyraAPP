package com.noctyra.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _state = MutableStateFlow<UiState<AnimeDetail>>(UiState.Loading)
    val state: StateFlow<UiState<AnimeDetail>> = _state

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason

    fun load(slug: String) {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val detail = repository.getAnimeDetail(slug)
                _state.value = UiState.Success(detail)
                if (detail.seasons.isNotEmpty()) _selectedSeason.value = detail.seasons.first()
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Failed to load anime")
            }
        }
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }
}
