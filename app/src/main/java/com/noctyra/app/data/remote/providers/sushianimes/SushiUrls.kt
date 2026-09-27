package com.noctyra.app.data.remote.providers.sushianimes

// Internal URL patterns for SushiAnimes
object SushiUrls {
    const val BASE = "https://sushianimes.com.br"
    const val EPISODES_PAGE = "$BASE/episodios"
    const val KITSU_API = "https://kitsu.io/api/edge/anime"
    const val AJAX_EMBED = "$BASE/ajax/embed"

    fun animeDetail(slug: String) = "$BASE/anime/$slug"
    fun movieDetail(slug: String) = "$BASE/assistir/$slug"
    fun movieStream(slug: String) = "$BASE/filme/$slug"
    fun animeStream(slug: String, season: Int, episode: Int) =
        "$BASE/anime/$slug-$season-season-$episode-episode"
    fun search(term: String) = "$BASE/search/$term"
}
