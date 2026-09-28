package com.noctyra.app.data.local

import android.content.Context
import com.noctyra.app.data.model.Anime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class FavoriteAnime(
    val slug: String,
    val title: String,
    val posterUrl: String = "",
    val isMovie: Boolean = false,
    val genres: List<String> = emptyList(),
    val rating: String = "",
    val totalEps: Int = 0,
    val addedAt: Long = 0L
) {
    fun toAnime() = Anime(
        slug = slug, title = title, posterUrl = posterUrl, isMovie = isMovie,
        genres = genres, rating = rating, totalEps = totalEps
    )
}

@Serializable
data class WatchProgress(
    val slug: String,
    val title: String,
    val posterUrl: String = "",
    val season: Int,
    val episode: Int,
    val episodeTitle: String = "",
    val thumbUrl: String = "",
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val updatedAt: Long = 0L
) {
    val key get() = progressKey(slug, season, episode)
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isFinished: Boolean get() = fraction >= 0.92f
    val remainingMinutes: Int get() = ((durationMs - positionMs).coerceAtLeast(0) / 60_000L).toInt()
}

fun progressKey(slug: String, season: Int, episode: Int) = "$slug|$season|$episode"

@Serializable
private data class LibraryData(
    val favorites: List<FavoriteAnime> = emptyList(),
    val history: List<WatchProgress> = emptyList()
)

object LibraryStore {
    private const val MAX_HISTORY = 400

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var file: File? = null
    private var saveJob: Job? = null

    private val _favorites = MutableStateFlow<List<FavoriteAnime>>(emptyList())
    val favorites: StateFlow<List<FavoriteAnime>> = _favorites.asStateFlow()

    private val _history = MutableStateFlow<List<WatchProgress>>(emptyList())
    val history: StateFlow<List<WatchProgress>> = _history.asStateFlow()

    val favoriteSlugs: StateFlow<Set<String>> = _favorites
        .map { list -> list.mapTo(HashSet()) { it.slug } }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())

    val continueWatching: StateFlow<List<WatchProgress>> = _history
        .map { list -> list.distinctBy { it.slug }.filter { !it.isFinished && it.positionMs > 15_000 }.take(15) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun init(context: Context) {
        val f = File(context.filesDir, "library.json")
        file = f
        val data = runCatching { json.decodeFromString(LibraryData.serializer(), f.readText()) }.getOrNull() ?: return
        _favorites.value = data.favorites
        _history.value = data.history
    }

    fun isFavorite(slug: String) = _favorites.value.any { it.slug == slug }

    fun toggleFavorite(anime: Anime) {
        _favorites.update { list ->
            if (list.any { it.slug == anime.slug }) list.filterNot { it.slug == anime.slug }
            else listOf(
                FavoriteAnime(
                    slug = anime.slug, title = anime.title, posterUrl = anime.posterUrl,
                    isMovie = anime.isMovie, genres = anime.genres.take(3), rating = anime.rating,
                    totalEps = anime.totalEps, addedAt = System.currentTimeMillis()
                )
            ) + list
        }
        scheduleSave()
    }

    fun removeFavorite(slug: String) {
        _favorites.update { list -> list.filterNot { it.slug == slug } }
        scheduleSave()
    }

    fun saveProgress(progress: WatchProgress) {
        val entry = progress.copy(updatedAt = System.currentTimeMillis())
        _history.update { list -> (listOf(entry) + list.filterNot { it.key == entry.key }).take(MAX_HISTORY) }
        scheduleSave()
    }

    fun progressFor(slug: String, season: Int, episode: Int): WatchProgress? {
        val key = progressKey(slug, season, episode)
        return _history.value.firstOrNull { it.key == key }
    }

    fun lastWatched(slug: String): WatchProgress? = _history.value.firstOrNull { it.slug == slug }

    fun removeFromHistory(slug: String) {
        _history.update { list -> list.filterNot { it.slug == slug } }
        scheduleSave()
    }

    fun clearHistory() {
        _history.value = emptyList()
        scheduleSave()
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(400)
            val f = file ?: return@launch
            val data = LibraryData(_favorites.value, _history.value)
            runCatching {
                val tmp = File(f.parentFile, "library.json.tmp")
                tmp.writeText(json.encodeToString(LibraryData.serializer(), data))
                tmp.renameTo(f)
            }
        }
    }
}
