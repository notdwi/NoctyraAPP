package com.noctyra.app.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.model.userMessage
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.download.DownloadMeta
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PlaybackSource {
    data class Online(val stream: StreamResult) : PlaybackSource
    data class Offline(val item: MediaItem, val meta: DownloadMeta) : PlaybackSource
}

class PlayerViewModel : ViewModel() {
    private val _source = MutableStateFlow<UiState<PlaybackSource>>(UiState.Loading)
    val source: StateFlow<UiState<PlaybackSource>> = _source.asStateFlow()

    private val _detail = MutableStateFlow<AnimeDetail?>(null)
    val detail: StateFlow<AnimeDetail?> = _detail.asStateFlow()

    private var loadedKey: String? = null

    fun load(slug: String, season: Int, episode: Int, force: Boolean = false) {
        val key = "$slug/$season/$episode"
        if (!force && loadedKey == key) return
        loadedKey = key

        _detail.value = AnimeRepository.peekDetail(slug)
        if (_detail.value == null) {
            viewModelScope.launch {
                _detail.value = runCatching { AnimeRepository.getAnimeDetail(slug) }.getOrNull()
            }
        }

        val offline = DownloadCenter.offlineMediaItem(slug, season, episode)
        val meta = DownloadCenter.offlineMeta(slug, season, episode)
        if (offline != null && meta != null) {
            _source.value = UiState.Success(PlaybackSource.Offline(offline, meta))
            return
        }

        viewModelScope.launch {
            _source.value = UiState.Loading
            try {
                val stream = AnimeRepository.getStream(slug, season, episode, force)
                _source.value = UiState.Success(PlaybackSource.Online(stream))
            } catch (e: Exception) {
                _source.value = UiState.Error(e.userMessage("Falha ao carregar o vídeo"))
            }
        }
    }
}
