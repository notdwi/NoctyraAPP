package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AppError
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.HomeFeed
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.data.remote.providers.AnimeProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jsoup.nodes.Document

class SushiAnimesProvider : AnimeProvider {
    override val name = "SushiAnimes"
    override val baseUrl = SushiUrls.BASE

    override suspend fun search(query: String): List<Anime> {
        val slug = SushiUtils.slugify(query)
        val term = slug.replace("-", " ")

        val doc = HttpClient.fetchDoc(SushiUrls.search(term), referer = "$baseUrl/")
        val sorted = sortByRelevance(SushiParser.parseSearchCards(doc), slug, term)

        val withPosters = sorted.filter { it.posterUrl.isNotEmpty() }
        if (withPosters.isNotEmpty()) return withPosters

        return coroutineScope {
            sorted.take(10).map { anime ->
                async {
                    if (anime.posterUrl.isNotEmpty()) return@async anime
                    val rawSlug = if (anime.isMovie) anime.slug.removeSuffix("-filme") else anime.slug
                    val detailUrl = if (anime.isMovie) SushiUrls.movieDetail(rawSlug) else SushiUrls.animeDetail(rawSlug)
                    val posterUrl = runCatching {
                        HttpClient.fetchDoc(detailUrl, referer = "$baseUrl/")
                            .select("meta[property=og:image]").attr("content").trim()
                    }.getOrDefault("")
                    anime.copy(posterUrl = posterUrl)
                }
            }.awaitAll()
        }
    }

    private fun sortByRelevance(list: List<Anime>, slugQuery: String, termQuery: String): List<Anime> {
        val term = termQuery.lowercase()
        val queryWords = term.split(" ").filter { it.isNotEmpty() }
        return list.sortedByDescending { anime ->
            val slugName = SushiUtils.slugName(anime.slug).lowercase()
            val titleLow = anime.title.lowercase()
            var score = 0
            if (slugName == slugQuery) score += 1000
            if (titleLow.startsWith(term)) score += 500
            if (slugName.startsWith(slugQuery)) score += 400
            if (queryWords.all { titleLow.contains(it) }) score += 300
            if (queryWords.all { slugName.contains(it) }) score += 200
            score += queryWords.count { titleLow.contains(it) } * 50
            score += queryWords.count { slugName.contains(it) } * 30
            score -= (titleLow.length - term.length).coerceAtLeast(0)
            score
        }
    }

    override fun getHome(): HomeFeed {
        val doc = HttpClient.fetchDoc("$baseUrl/", referer = "$baseUrl/")
        return SushiParser.parseHome(doc)
    }

    override fun getCategory(slug: String, page: Int): List<Anime> {
        val doc = HttpClient.fetchDoc(SushiUrls.category(slug, page), referer = "$baseUrl/categories")
        return SushiParser.parseListMovies(doc)
    }

    override fun getAnimeDetail(slug: String): AnimeDetail {
        val parsed = SushiUtils.parseSlugInput(slug)
        val (resolvedSlug, doc) = resolveDetail(parsed)
        return SushiParser.parseAnimeDetail(slug, resolvedSlug, parsed.isMovie, doc)
    }

    override fun getStream(slug: String, season: Int, episode: Int): StreamResult {
        val parsed = SushiUtils.parseSlugInput(slug)
        fun pageUrlFor(s: String) = if (parsed.isMovie) SushiUrls.movieStream(s)
                                    else SushiUrls.animeStream(s, season, episode)

        var pageUrl = pageUrlFor("${parsed.name}-${parsed.id}")
        val doc = runCatching {
            if (parsed.id <= 0) error("sem id")
            HttpClient.fetchDoc(pageUrl, referer = "$baseUrl/")
        }.getOrElse {
            pageUrl = pageUrlFor(resolveDetail(parsed).first)
            HttpClient.fetchDoc(pageUrl, referer = "$baseUrl/")
        }
        val embedRefs = SushiEmbed.parseEmbedRefs(doc)
        if (embedRefs.isEmpty()) throw AppError("Nenhum player disponível para o episódio $episode")

        var fallback: StreamResult? = null
        for (ref in embedRefs) {
            val result = runCatching { SushiEmbed.resolveEmbed(ref, pageUrl) }.getOrNull() ?: continue
            if (result.host == "Direct") return result
            if (fallback == null) fallback = result
        }
        return fallback ?: throw AppError("Nenhuma fonte de vídeo disponível para o episódio $episode")
    }

    private fun resolveDetail(parsed: SushiParsedSlug): Pair<String, Document> {
        fun fetch(s: String) = if (parsed.isMovie) HttpClient.fetchDoc(SushiUrls.movieDetail(s), referer = "$baseUrl/")
                               else HttpClient.fetchDoc(SushiUrls.animeDetail(s), referer = "$baseUrl/")

        if (parsed.id > 0) {
            val slug = "${parsed.name}-${parsed.id}"
            runCatching { return slug to fetch(slug) }
        }
        val term = parsed.name.replace("-", " ")
        val candidates = SushiParser.parseSearchResults(HttpClient.fetchDoc(SushiUrls.search(term), referer = "$baseUrl/"))
        if (candidates.isEmpty()) throw AppError("Anime não encontrado")

        val chosen = (if (parsed.isMovie) candidates.firstOrNull { it.second } else null)
            ?: candidates.firstOrNull { SushiUtils.slugName(it.first) == parsed.name }
            ?: candidates.minByOrNull { SushiUtils.slugName(it.first).length }
            ?: candidates.first()
        return chosen.first to fetch(chosen.first)
    }
}
