package com.noctyra.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Anime(
    val slug: String,
    val title: String,
    val altTitle: String = "",
    val posterUrl: String = "",
    val synopsis: String = "",
    val genres: List<String> = emptyList(),
    val rating: String = "",
    val totalEps: Int = 0,
    val sourceUrl: String = "",
    val isMovie: Boolean = false
)

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

data class AnimeDetail(
    val anime: Anime,
    val episodes: List<Episode>,
    val seasons: List<Int> = listOf(1)
)

sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}
