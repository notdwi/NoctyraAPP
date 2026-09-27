package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.remote.http.HttpClient
import org.jsoup.nodes.Document

// Responsible for parsing SushiAnimes HTML pages into domain models
internal object SushiParser {

    // Returns list of (slug, title, posterUrl, isMovie)
    fun parseSearchResults(doc: Document): List<Pair<String, Boolean>> {
        val animePattern = Regex("^/anime/([a-z0-9-]+-\\d+)$")
        val moviePattern = Regex("^/assistir/([a-z0-9-]+-\\d+)$")
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Pair<String, Boolean>>()

        doc.select("a[href*=/anime/], a[href*=/assistir/]").forEach { link ->
            val href = link.attr("href").trimEnd('/')
            val path = try {
                java.net.URI(href).path?.trimEnd('/') ?: return@forEach
            } catch (_: Exception) { return@forEach }

            val matchAnime = animePattern.find(path)
            val matchMovie = moviePattern.find(path)
            val foundSlug = matchAnime?.groupValues?.get(1)
                ?: matchMovie?.groupValues?.get(1) ?: return@forEach
            val isMovie = matchMovie != null

            if (seen.add(foundSlug)) results.add(foundSlug to isMovie)
        }
        return results
    }

    // Enriched version: extracts title + poster from search card HTML directly
    fun parseSearchCards(doc: Document): List<Anime> {
        val animePattern = Regex("^/anime/([a-z0-9-]+-\\d+)$")
        val moviePattern = Regex("^/assistir/([a-z0-9-]+-\\d+)$")
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Anime>()

        // Try article cards first (WP theme typical structure)
        val cards = doc.select("article.TPostMv, article.TPost, .TPostMv, .TPost, .flw-item, .film-poster, a.TPostMv")
        if (cards.isNotEmpty()) {
            cards.forEach { card ->
                val link = card.selectFirst("a[href]") ?: card as? org.jsoup.nodes.Element ?: return@forEach
                val href = link.attr("href").trimEnd('/')
                val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@forEach } catch (_: Exception) { return@forEach }

                val matchAnime = animePattern.find(path)
                val matchMovie = moviePattern.find(path)
                val foundSlug = matchAnime?.groupValues?.get(1) ?: matchMovie?.groupValues?.get(1) ?: return@forEach
                val isMovie = matchMovie != null
                if (!seen.add(foundSlug)) return@forEach

                var title = card.selectFirst(".Title, .film-name, .dynamic-name, .list-title")?.text()?.trim()
                if (title.isNullOrEmpty() || title.contains("FULLHD", ignoreCase = true) || title.contains("HD", ignoreCase = true) || title.contains("FHD", ignoreCase = true)) {
                    title = card.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() && !it.contains("FULLHD", true) }
                        ?: link.attr("title").trim().takeIf { it.isNotEmpty() && !it.contains("FULLHD", true) }
                        ?: SushiUtils.titleFromSlug(foundSlug)
                }
                val poster = card.selectFirst("img[src], img[data-src]")?.let {
                    it.attr("data-src").ifEmpty { it.attr("src") }
                } ?: ""

                results.add(Anime(
                    slug = if (isMovie) "$foundSlug-filme" else foundSlug,
                    title = title,
                    posterUrl = poster,
                    isMovie = isMovie
                ))
            }
            if (results.isNotEmpty()) return results
        }

        // Fallback: extract from any links on the page
        doc.select("a[href*=/anime/], a[href*=/assistir/]").forEach { link ->
            val href = link.attr("href").trimEnd('/')
            val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@forEach } catch (_: Exception) { return@forEach }
            val matchAnime = animePattern.find(path)
            val matchMovie = moviePattern.find(path)
            val foundSlug = matchAnime?.groupValues?.get(1) ?: matchMovie?.groupValues?.get(1) ?: return@forEach
            val isMovie = matchMovie != null
            if (!seen.add(foundSlug)) return@forEach

            val title = link.text().trim().ifEmpty { SushiUtils.titleFromSlug(foundSlug) }
            val poster = link.selectFirst("img")?.let {
                it.attr("data-src").ifEmpty { it.attr("src") }
            } ?: ""

            results.add(Anime(
                slug = if (isMovie) "$foundSlug-filme" else foundSlug,
                title = title,
                posterUrl = poster,
                isMovie = isMovie
            ))
        }
        return results
    }


    fun parseLatestReleases(doc: Document): List<Triple<String, String, String>> {
        // Returns list of (slug, title, thumbUrl)
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Triple<String, String, String>>()
        val epPattern = Regex("-(\\d+)-season-(\\d+)-episode/?$")

        doc.select("a[href*=-season-]").forEach { link ->
            val href = link.attr("href").trim()
            if (href.isEmpty()) return@forEach
            val path = try {
                java.net.URI(href).path?.trimEnd('/') ?: return@forEach
            } catch (_: Exception) { return@forEach }

            val baseSlug = path.substringAfterLast("/")
            val match = epPattern.find(baseSlug) ?: return@forEach
            val slugStr = baseSlug.substring(0, match.range.first)

            if (!seen.add(slugStr)) return@forEach

            val title = link.select(".list-title").first()?.text()?.trim()
                ?: link.select(".epx-desc").first()?.text()?.trim()
                ?: SushiUtils.titleFromSlug(slugStr)

            var thumbUrl = ""
            for (sel in listOf(".media-episode", ".epx-thumb", "[data-src]")) {
                val v = link.select(sel).first()?.attr("data-src")?.trim() ?: ""
                if (v.isNotEmpty()) { thumbUrl = v; break }
            }
            if (thumbUrl.isNotEmpty() && !thumbUrl.startsWith("http")) {
                thumbUrl = "${SushiUrls.BASE}$thumbUrl"
            }
            results.add(Triple(slugStr, title, thumbUrl))
        }
        return results
    }

    fun parseAnimeDetail(slug: String, resolvedSlug: String, isMovie: Boolean, doc: Document): AnimeDetail {
        val title = doc.select("h1#title").first()?.text()?.trim() ?: ""
        val altTitle = doc.select("h1#title + h2").first()?.text()?.trim() ?: ""
        val synopsis = doc.select(".text-content").first()?.text()?.trim() ?: ""
        val posterUrl = doc.select("meta[property=og:image]").first()?.attr("content")?.trim() ?: ""
        val rating = doc.select("#certification-anime").first()?.text()?.trim() ?: ""

        val genres = mutableListOf<String>()
        doc.select("a[itemprop=genre]").forEach { g ->
            val name = g.text().trim()
            if (name.isNotEmpty()) genres.add(name)
        }
        if (genres.isEmpty()) {
            doc.select(".categories a[href*=/category/]").forEach { g ->
                val name = g.text().trim()
                if (name.isNotEmpty() && !genres.contains(name)) genres.add(name)
            }
        }

        val episodes = mutableListOf<Episode>()
        val seenEps = mutableSetOf<String>()
        val epPattern = Regex("-(\\d+)-season-(\\d+)-episode/?$")

        if (isMovie) {
            episodes.add(Episode(
                number = 1, season = 1, title = title,
                sourceUrl = SushiUrls.movieStream(resolvedSlug)
            ))
        } else {
            doc.select("a.epx-card").forEach { card ->
                val href = card.attr("href").trim()
                val match = epPattern.find(href) ?: return@forEach
                val season = match.groupValues[1].toIntOrNull() ?: return@forEach
                val epNum = match.groupValues[2].toIntOrNull() ?: return@forEach
                val key = "$season/$epNum"
                if (!seenEps.add(key)) return@forEach

                val epTitle = card.select(".epx-desc").first()?.text()?.trim()
                    ?: card.select(".epx-title").first()?.text()?.trim() ?: ""
                val thumbUrl = card.select(".epx-thumb").first()?.attr("data-src")?.trim() ?: ""

                episodes.add(Episode(
                    number = epNum, season = season,
                    title = epTitle, sourceUrl = href, thumbUrl = thumbUrl
                ))
            }
            episodes.sortWith(compareBy({ it.season }, { it.number }))
        }

        val seasons = episodes.map { it.season }.distinct().sorted()
        val anime = Anime(
            slug = slug, title = title, altTitle = altTitle,
            posterUrl = posterUrl, synopsis = synopsis, genres = genres,
            rating = rating, totalEps = episodes.size,
            sourceUrl = if (isMovie) SushiUrls.movieDetail(resolvedSlug)
                        else SushiUrls.animeDetail(resolvedSlug),
            isMovie = isMovie
        )
        return AnimeDetail(anime = anime, episodes = episodes, seasons = seasons)
    }
}
