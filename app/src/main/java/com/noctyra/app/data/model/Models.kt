package com.noctyra.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val slug: String,
    val title: String,
    val altTitle: String = "",
    val posterUrl: String = "",
    val bannerUrl: String = "",
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val rating: String = "",
    val totalEps: Int = 0,
    val sourceUrl: String = "",
    val isMovie: Boolean = false,
    val subtitle: String = "",
    val latestSeason: Int = 0,
    val latestEpisode: Int = 0
)

val Anime.isDubbed: Boolean
    get() = title.contains("dublado", ignoreCase = true) || slug.contains("dublado")

val Anime.displayTitle: String
    get() = cleanTitle(title)

fun cleanTitle(title: String): String =
    title.replace(Regex("\\(\\s*dublado\\s*\\)", RegexOption.IGNORE_CASE), "").trim()

@Serializable
data class Episode(
    val number: Int,
    val season: Int = 1,
    val title: String = "",
    val slug: String = "",
    val sourceUrl: String = "",
    val thumbUrl: String = ""
)

@Serializable
data class StreamResult(
    val host: String,
    val streamType: String,
    val streamUrl: String,
    val quality: String = "",
    val headers: Map<String, String> = emptyMap(),
    val notes: List<String> = emptyList()
)

@Serializable
data class AnimeDetail(
    val anime: Anime,
    val episodes: List<Episode>,
    val seasons: List<Int> = listOf(1)
)

@Serializable
data class HomeFeed(
    val hero: List<Anime> = emptyList(),
    val newEpisodes: List<Anime> = emptyList(),
    val trending: List<Anime> = emptyList(),
    val mostWatched: List<Anime> = emptyList(),
    val latestAnimes: List<Anime> = emptyList(),
    val latestMovies: List<Anime> = emptyList(),
    val fetchedAt: Long = 0L
) {
    val isEmpty: Boolean
        get() = hero.isEmpty() && newEpisodes.isEmpty() && latestAnimes.isEmpty() && trending.isEmpty()
}

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
