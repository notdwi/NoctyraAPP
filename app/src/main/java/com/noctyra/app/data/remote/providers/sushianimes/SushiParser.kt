package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.HomeFeed
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

internal object SushiParser {

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

    fun parseSearchCards(doc: Document): List<Anime> {
        val animePattern = Regex("^/anime/([a-z0-9-]+-\\d+)$")
        val moviePattern = Regex("^/assistir/([a-z0-9-]+-\\d+)$")
        val seen = mutableSetOf<String>()
        val results = mutableListOf<Anime>()

        val cards = doc.select("article.TPostMv, article.TPost, .TPostMv, .TPost, .flw-item, .film-poster, a.TPostMv")
        if (cards.isNotEmpty()) {
            cards.forEach { card ->
                val link = card.selectFirst("a[href]") ?: card as? Element ?: return@forEach
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

        doc.select("a[href*=/anime/], a[href*=/assistir/]").forEach { link ->
            val href = link.attr("href").trimEnd('/')
            val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@forEach } catch (_: Exception) { return@forEach }
            val matchAnime = animePattern.find(path)
            val matchMovie = moviePattern.find(path)
            val foundSlug = matchAnime?.groupValues?.get(1) ?: matchMovie?.groupValues?.get(1) ?: return@forEach
            val isMovie = matchMovie != null
            if (!seen.add(foundSlug)) return@forEach

            var title = link.text().trim()
            if (title.isNullOrEmpty() || title.contains("FULLHD", true) || title.contains("HD", true) || title.contains("FHD", true)) {
                title = link.selectFirst("img")?.attr("alt")?.trim()?.takeIf { it.isNotEmpty() && !it.contains("FULLHD", true) }
                    ?: link.attr("title").trim().takeIf { it.isNotEmpty() && !it.contains("FULLHD", true) }
                    ?: SushiUtils.titleFromSlug(foundSlug)
            }
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


    private val animePathRegex = Regex("^/anime/([a-z0-9-]+-\\d+)$")
    private val moviePathRegex = Regex("^/assistir/([a-z0-9-]+-\\d+)$")
    private val episodePathRegex = Regex("""^/anime/([a-z0-9-]+-\d+)-(\d+)-season-(\d+)-episode$""")
    private val seasonEpisodeRegex = Regex("""(\d+)\D+(\d+)""")

    private fun pathOf(href: String): String? =
        runCatching { java.net.URI(href.trim()).path?.trimEnd('/') }.getOrNull()

    private fun appSlugFromHref(href: String): Pair<String, Boolean>? {
        val path = pathOf(href) ?: return null
        animePathRegex.find(path)?.let { return it.groupValues[1] to false }
        moviePathRegex.find(path)?.let { return "${it.groupValues[1]}-filme" to true }
        return null
    }

    private fun Element.lazyImage(): String =
        attr("data-src").ifEmpty { attr("data-bg") }.ifEmpty { attr("src") }.trim()

    private fun formatScore(raw: String): String =
        raw.trim().toDoubleOrNull()?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: ""

    fun parseHome(doc: Document): HomeFeed {
        val hero = doc.select("#slider a.slide").mapNotNull { a ->
            val (slug, isMovie) = appSlugFromHref(a.attr("href")) ?: return@mapNotNull null
            val header = a.select(".slide-header > div").map { it.text().trim() }
                .filter { it.isNotEmpty() && it != "|" }
            val banner = a.lazyImage()
            Anime(
                slug = slug,
                title = a.selectFirst(".title")?.text()?.trim().orEmpty().ifEmpty { SushiUtils.titleFromSlug(slug) },
                bannerUrl = banner,
                posterUrl = banner,
                synopsis = a.selectFirst(".description")?.text()?.trim().orEmpty(),
                genres = header.drop(1),
                subtitle = header.firstOrNull().orEmpty(),
                isMovie = isMovie || header.firstOrNull()?.contains("filme", true) == true
            )
        }.distinctBy { it.slug }

        var newEpisodes = emptyList<Anime>()
        var trending = emptyList<Anime>()
        var mostWatched = emptyList<Anime>()
        var latestAnimes = emptyList<Anime>()
        var latestMovies = emptyList<Anime>()

        doc.select(".app-section").forEach { section ->
            val heading = section.selectFirst(".app-heading .text")?.text()?.trim()?.lowercase() ?: return@forEach
            when {
                heading.contains("hentai") || heading.contains("cole") -> Unit
                heading.contains("novos epis") -> newEpisodes = newEpisodes.ifEmpty { parseEpisodeCards(section) }
                heading.contains("em alta") -> trending = trending.ifEmpty { parseListMovies(section) }
                heading.contains("mais assistidos") -> mostWatched = mostWatched.ifEmpty { parseListMovies(section) }
                heading.contains("animes mais recentes") -> latestAnimes = latestAnimes.ifEmpty { parseListMovies(section) }
                heading.contains("filmes mais recentes") -> latestMovies = latestMovies.ifEmpty { parseListMovies(section) }
            }
        }

        return HomeFeed(
            hero = hero,
            newEpisodes = newEpisodes,
            trending = trending,
            mostWatched = mostWatched,
            latestAnimes = latestAnimes,
            latestMovies = latestMovies,
            fetchedAt = System.currentTimeMillis()
        )
    }

    fun parseListMovies(section: Element): List<Anime> =
        section.select(".list-movie").mapNotNull { card ->
            val link = card.selectFirst("a.list-media[href], a.list-title[href]") ?: return@mapNotNull null
            val (slug, isMovie) = appSlugFromHref(link.attr("href")) ?: return@mapNotNull null
            Anime(
                slug = slug,
                title = card.selectFirst(".list-title")?.text()?.trim().orEmpty().ifEmpty { SushiUtils.titleFromSlug(slug) },
                posterUrl = card.selectFirst(".media-cover")?.lazyImage().orEmpty(),
                genres = listOfNotNull(card.selectFirst(".list-category")?.text()?.trim()?.takeIf { it.isNotEmpty() }),
                rating = formatScore(card.selectFirst(".imdb span")?.text().orEmpty()),
                isMovie = isMovie
            )
        }.distinctBy { it.slug }

    private fun parseEpisodeCards(section: Element): List<Anime> =
        section.select("a.list-movie[href]").mapNotNull { card ->
            val path = pathOf(card.attr("href")) ?: return@mapNotNull null
            val match = episodePathRegex.find(path) ?: return@mapNotNull null
            val slug = match.groupValues[1]
            val season = match.groupValues[2].toIntOrNull() ?: 1
            val episode = match.groupValues[3].toIntOrNull() ?: 1
            val thumb = card.selectFirst(".media-episode")?.lazyImage().orEmpty()
            val label = card.selectFirst(".list-category")?.text()?.trim().orEmpty()
            val (s, e) = seasonEpisodeRegex.find(label)?.let {
                (it.groupValues[1].toIntOrNull() ?: season) to (it.groupValues[2].toIntOrNull() ?: episode)
            } ?: (season to episode)
            Anime(
                slug = slug,
                title = card.selectFirst(".list-title")?.text()?.trim().orEmpty().ifEmpty { SushiUtils.titleFromSlug(slug) },
                posterUrl = thumb,
                bannerUrl = thumb,
                subtitle = "T$s • EP $e",
                latestSeason = s,
                latestEpisode = e
            )
        }.distinctBy { "${it.slug}/${it.latestSeason}/${it.latestEpisode}" }

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
