package com.noctyra.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.download.DownloadCenter
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class DetailViewModel : ViewModel() {
    private val _state = MutableStateFlow<UiState<AnimeDetail>>(UiState.Loading)
    val state: StateFlow<UiState<AnimeDetail>> = _state.asStateFlow()

    private val _selectedSeason = MutableStateFlow(1)
    val selectedSeason: StateFlow<Int> = _selectedSeason.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    private var loadedSlug: String? = null

    fun load(slug: String, force: Boolean = false) {
        if (!force && loadedSlug == slug && _state.value is UiState.Success) return
        loadedSlug = slug
        AnimeRepository.peekDetail(slug)?.takeIf { !force }?.let { onLoaded(slug, it); return }
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                onLoaded(slug, AnimeRepository.getAnimeDetail(slug, force))
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Falha ao carregar o anime")
            }
        }
    }

    private fun onLoaded(slug: String, detail: AnimeDetail) {
        _state.value = UiState.Success(detail)
        val lastSeason = LibraryStore.lastWatched(slug)?.season
        _selectedSeason.value = lastSeason?.takeIf { it in detail.seasons } ?: detail.seasons.firstOrNull() ?: 1
    }

    fun selectSeason(season: Int) {
        _selectedSeason.value = season
    }

    fun download(detail: AnimeDetail, episode: Episode) {
        viewModelScope.launch {
            _messages.send("Preparando download do episódio ${episode.number}…")
            try {
                DownloadCenter.enqueue(
                    slug = detail.anime.slug,
                    title = detail.anime.title,
                    posterUrl = detail.anime.posterUrl,
                    season = episode.season,
                    episode = episode.number,
                    episodeTitle = episode.title,
                    thumbUrl = episode.thumbUrl
                )
                _messages.send("Episódio ${episode.number} adicionado aos downloads")
            } catch (e: Exception) {
                _messages.send(e.message ?: "Não foi possível baixar este episódio")
            }
        }
    }

    fun downloadSeason(detail: AnimeDetail, season: Int) {
        val pending = detail.episodes.filter {
            it.season == season && DownloadCenter.downloads.value[DownloadCenter.downloadId(detail.anime.slug, it.season, it.number)] == null
        }
        if (pending.isEmpty()) {
            viewModelScope.launch { _messages.send("Todos os episódios desta temporada já estão nos downloads") }
            return
        }
        viewModelScope.launch {
            _messages.send("Adicionando ${pending.size} episódios aos downloads…")
            var failed = 0
            for (ep in pending) {
                runCatching {
                    DownloadCenter.enqueue(
                        detail.anime.slug, detail.anime.title, detail.anime.posterUrl,
                        ep.season, ep.number, ep.title, ep.thumbUrl
                    )
                }.onFailure { failed++ }
            }
            _messages.send(
                if (failed == 0) "Temporada $season adicionada aos downloads"
                else "${pending.size - failed} episódios adicionados, $failed sem fonte baixável"
            )
        }
    }

    fun removeDownload(id: String) {
        DownloadCenter.remove(id)
        viewModelScope.launch { _messages.send("Download removido") }
    }
}
