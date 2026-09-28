package com.noctyra.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.HomeFeed
import com.noctyra.app.data.model.userMessage
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUi(
    val feed: HomeFeed? = null,
    val allAnimes: List<Anime> = emptyList(),
    val hot: List<Anime> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null
)

class HomeViewModel : ViewModel() {
    private val _ui = MutableStateFlow(HomeUi())
    val ui: StateFlow<HomeUi> = _ui.asStateFlow()

    private val _heroDetails = MutableStateFlow<Map<String, AnimeDetail>>(emptyMap())
    val heroDetails: StateFlow<Map<String, AnimeDetail>> = _heroDetails.asStateFlow()
    private val requestedDetails = HashSet<String>()

    init {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { AnimeRepository.cachedHome() }
            if (cached != null) _ui.value = build(cached)
            if (AnimeRepository.isHomeStale(cached)) refresh()
        }
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        viewModelScope.launch {
            _ui.update { it.copy(refreshing = true, error = null, loading = it.feed == null) }
            try {
                val feed = AnimeRepository.refreshHome()
                _ui.value = build(feed)
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = if (it.feed == null) e.userMessage("Não foi possível carregar a página inicial") else null
                    )
                }
            }
        }
    }

    fun ensureHeroDetail(slug: String) {
        if (!requestedDetails.add(slug)) return
        viewModelScope.launch {
            runCatching { AnimeRepository.getAnimeDetail(slug) }
                .onSuccess { detail -> _heroDetails.update { it + (slug to detail) } }
                .onFailure { requestedDetails.remove(slug) }
        }
    }

    private fun build(feed: HomeFeed): HomeUi {
        val hot = (feed.trending + feed.mostWatched).distinctBy { it.slug }
        val all = (feed.latestAnimes + hot + feed.latestMovies).distinctBy { it.slug }
        return HomeUi(feed = feed, allAnimes = all, hot = hot, loading = false, refreshing = false)
    }
}
