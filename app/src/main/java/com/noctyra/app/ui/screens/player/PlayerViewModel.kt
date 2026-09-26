package com.noctyra.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {
    private val repository = AnimeRepository()

    private val _streamState = MutableStateFlow<UiState<StreamResult>>(UiState.Loading)
    val streamState: StateFlow<UiState<StreamResult>> = _streamState

    private val _animeState = MutableStateFlow<UiState<com.noctyra.app.data.model.AnimeDetail>>(UiState.Loading)
    val animeState: StateFlow<UiState<com.noctyra.app.data.model.AnimeDetail>> = _animeState

    fun loadStream(slug: String, season: Int, episode: Int) {
        viewModelScope.launch {
            _streamState.value = UiState.Loading
            _animeState.value = UiState.Loading
            try {
                val stream = repository.getStream(slug, season, episode)
                _streamState.value = UiState.Success(stream)
            } catch (e: Exception) {
                _streamState.value = UiState.Error(e.message ?: "Failed to load stream")
            }

            try {
                val detail = repository.getAnimeDetail(slug)
                _animeState.value = UiState.Success(detail)
            } catch (e: Exception) {
                _animeState.value = UiState.Error(e.message ?: "Failed to load anime details")
            }
        }
    }
}
