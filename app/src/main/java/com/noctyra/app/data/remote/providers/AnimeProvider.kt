package com.noctyra.app.data.remote.providers

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult

interface AnimeProvider {
    val name: String
    val baseUrl: String

    suspend fun search(query: String): List<Anime>
    suspend fun getLatestReleases(): List<Anime>
    suspend fun getPopular(): List<Anime>
    fun getAnimeDetail(slug: String): AnimeDetail
    fun getStream(slug: String, season: Int, episode: Int): StreamResult
}
