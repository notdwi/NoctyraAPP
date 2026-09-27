package com.noctyra.app.data.repository

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.providers.AnimeProvider
import com.noctyra.app.data.remote.providers.sushianimes.SushiAnimesProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeRepository {
    // In the future, more providers can be added and selected here
    private val provider: AnimeProvider = SushiAnimesProvider()

    suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        provider.search(query)
    }

    suspend fun getLatestReleases(): List<Anime> = withContext(Dispatchers.IO) {
        provider.getLatestReleases()
    }

    suspend fun getPopular(): List<Anime> = withContext(Dispatchers.IO) {
        provider.getPopular()
    }

    suspend fun getAnimeDetail(slug: String): AnimeDetail = withContext(Dispatchers.IO) {
        provider.getAnimeDetail(slug)
    }

    suspend fun getStream(slug: String, season: Int, episode: Int): StreamResult = withContext(Dispatchers.IO) {
        provider.getStream(slug, season, episode)
    }
}
