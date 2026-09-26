package com.noctyra.app.data.repository

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.SushiAnimesScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AnimeRepository {
    private val scraper = SushiAnimesScraper()

    suspend fun search(query: String): List<Anime> = withContext(Dispatchers.IO) {
        scraper.search(query)
    }

    suspend fun getLatestReleases(): List<Anime> = withContext(Dispatchers.IO) {
        scraper.getLatestReleases()
    }

    suspend fun getAnimeDetail(slug: String): AnimeDetail = withContext(Dispatchers.IO) {
        scraper.getAnimeDetail(slug)
    }

    suspend fun getStream(slug: String, season: Int, episode: Int): StreamResult = withContext(Dispatchers.IO) {
        scraper.getStream(slug, season, episode)
    }
}
