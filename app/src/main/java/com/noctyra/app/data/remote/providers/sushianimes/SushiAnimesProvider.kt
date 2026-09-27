package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.data.remote.providers.AnimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import okhttp3.Request

class SushiAnimesProvider : AnimeProvider {
    override val name = "SushiAnimes"
    override val baseUrl = SushiUrls.BASE

    override suspend fun search(query: String): List<Anime> {
        val slug = SushiUtils.slugify(query)
        val term = slug.replace("-", " ")

        val doc = HttpClient.fetchDoc(SushiUrls.search(term), referer = "$baseUrl/")
        val results = SushiParser.parseSearchCards(doc)

        // Sort by relevance before fetching posters
        val sorted = sortByRelevance(results, slug, term)

        // Return results with posters directly (fast path)
        val withPosters = sorted.filter { it.posterUrl.isNotEmpty() }
        if (withPosters.isNotEmpty()) return withPosters

        // Fallback: fetch posters in parallel (max 10)
        return coroutineScope {
            sorted.take(10).map { anime ->
                async {
                    if (anime.posterUrl.isNotEmpty()) return@async anime
                    val rawSlug = if (anime.isMovie) anime.slug.removeSuffix("-filme") else anime.slug
                    val detailUrl = if (anime.isMovie) SushiUrls.movieDetail(rawSlug) else SushiUrls.animeDetail(rawSlug)
                    var posterUrl = ""
                    try {
                        val detailDoc = HttpClient.fetchDoc(detailUrl, referer = "$baseUrl/")
                        posterUrl = detailDoc.select("meta[property=og:image]").attr("content").trim()
                    } catch (_: Exception) {}
                    anime.copy(posterUrl = posterUrl)
                }
            }.awaitAll()
        }
    }

    /** Ranks results by how closely they match the query. Higher score = better match. */
    private fun sortByRelevance(list: List<Anime>, slugQuery: String, termQuery: String): List<Anime> {
        val queryWords = termQuery.lowercase().split(" ").filter { it.isNotEmpty() }
        return list.sortedByDescending { anime ->
            val slugName = SushiUtils.slugName(anime.slug).lowercase()
            val titleLow = anime.title.lowercase()
            var score = 0

            // Exact slug match
            if (slugName == slugQuery) score += 1000
            // Title starts with query
            if (titleLow.startsWith(termQuery.lowercase())) score += 500
            // Slug starts with query slug
            if (slugName.startsWith(slugQuery)) score += 400
            // All query words present in title
            if (queryWords.all { titleLow.contains(it) }) score += 300
            // All query words present in slug
            if (queryWords.all { slugName.contains(it) }) score += 200
            // Partial: some words match
            score += queryWords.count { titleLow.contains(it) } * 50
            score += queryWords.count { slugName.contains(it) } * 30
            // Prefer shorter, more specific titles (closer to the query length)
            score -= (titleLow.length - termQuery.length).coerceAtLeast(0)

            score
        }
    }

    override suspend fun getLatestReleases(): List<Anime> {
        val doc = HttpClient.fetchDoc(SushiUrls.EPISODES_PAGE, referer = "$baseUrl/")
        val releases = SushiParser.parseLatestReleases(doc)
        return coroutineScope {
            releases.map { (slug, title, thumbUrl) ->
                async {
                    val kitsuPoster = fetchKitsuPoster(title)
                    Anime(slug = slug, title = title, posterUrl = kitsuPoster ?: thumbUrl, isMovie = false)
                }
            }.awaitAll()
        }
    }

    override suspend fun getPopular(): List<Anime> {
        // SushiAnimes doesn't have a dedicated popular page, reuse latest
        return getLatestReleases()
    }

    override fun getAnimeDetail(slug: String): AnimeDetail {
        val parsed = SushiUtils.parseSlugInput(slug)
        val resolvedSlug = resolveSlug(parsed)
        val doc = if (parsed.isMovie) {
            HttpClient.fetchDoc(SushiUrls.movieDetail(resolvedSlug), referer = "$baseUrl/")
        } else {
            HttpClient.fetchDoc(SushiUrls.animeDetail(resolvedSlug), referer = "$baseUrl/")
        }
        return SushiParser.parseAnimeDetail(slug, resolvedSlug, parsed.isMovie, doc)
    }

    override fun getStream(slug: String, season: Int, episode: Int): StreamResult {
        val parsed = SushiUtils.parseSlugInput(slug)
        val resolvedSlug = resolveSlug(parsed)
        val pageUrl = if (parsed.isMovie) {
            SushiUrls.movieStream(resolvedSlug)
        } else {
            SushiUrls.animeStream(resolvedSlug, season, episode)
        }
        val doc = HttpClient.fetchDoc(pageUrl, referer = "$baseUrl/")
        val embedRefs = SushiEmbed.parseEmbedRefs(doc)
        if (embedRefs.isEmpty()) throw Exception("Nenhum player encontrado para o episódio $episode")

        val results = embedRefs.mapNotNull { ref ->
            try { SushiEmbed.resolveEmbed(ref, pageUrl) } catch (_: Exception) { null }
        }
        if (results.isEmpty()) throw Exception("Nenhuma fonte encontrada para $slug EP$episode")

        return results.firstOrNull { it.host == "Direct" } ?: results.first()
    }

    private fun resolveSlug(parsed: SushiParsedSlug): String {
        if (parsed.id > 0) {
            val slug = "${parsed.name}-${parsed.id}"
            try {
                if (parsed.isMovie) HttpClient.fetchDoc(SushiUrls.movieDetail(slug), referer = "$baseUrl/")
                else HttpClient.fetchDoc(SushiUrls.animeDetail(slug), referer = "$baseUrl/")
                return slug
            } catch (_: Exception) {}
        }
        val term = parsed.name.replace("-", " ")
        val doc = HttpClient.fetchDoc(SushiUrls.searchFallback(term), referer = "$baseUrl/")
        val candidates = SushiParser.parseSearchResults(doc)
        if (candidates.isEmpty()) throw Exception("Anime '$term' não encontrado")
        if (parsed.isMovie) {
            val movies = candidates.filter { it.second }
            if (movies.isNotEmpty()) return movies.first().first
        }
        val exact = candidates.firstOrNull { SushiUtils.slugName(it.first) == parsed.name }
        if (exact != null) return exact.first
        return candidates.minByOrNull { SushiUtils.slugName(it.first).length }?.first
            ?: candidates.first().first
    }

    private suspend fun fetchKitsuPoster(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = title.replace("(Dublado)", "").trim()
            val url = "${SushiUrls.KITSU_API}?filter[text]=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&page[limit]=1"
            val request = Request.Builder().url(url).build()
            val response = HttpClient.okHttp.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val regex = Regex(""""posterImage":\{.*?"original":"([^"]+)"""")
            regex.find(body)?.groupValues?.get(1)
        } catch (_: Exception) { null }
    }
}
