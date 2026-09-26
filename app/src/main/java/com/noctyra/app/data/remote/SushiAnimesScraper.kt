package com.noctyra.app.data.remote

import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.StreamResult
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

import okhttp3.CipherSuite
import okhttp3.ConnectionSpec
import okhttp3.TlsVersion

class SushiAnimesScraper {
    private val baseUrl = "https://sushianimes.com.br"

    private val chromeSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(TlsVersion.TLS_1_3, TlsVersion.TLS_1_2)
        .cipherSuites(
            CipherSuite.TLS_AES_128_GCM_SHA256,
            CipherSuite.TLS_AES_256_GCM_SHA384,
            CipherSuite.TLS_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,
            CipherSuite.TLS_ECDHE_ECDSA_WITH_CHACHA20_POLY1305_SHA256,
            CipherSuite.TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256
        )
        .build()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .connectionSpecs(listOf(chromeSpec, ConnectionSpec.COMPATIBLE_TLS, ConnectionSpec.CLEARTEXT))
        .followRedirects(false)
        .build()

    private fun fetchDoc(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            .header("Referer", "$baseUrl/")
            .build()
        val response = client.newCall(request).execute()
        if (response.code in 300..399) throw Exception("Redirect: anime not found")
        if (response.code == 404 || response.code == 410) throw Exception("Not found")
        if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
        return Jsoup.parse(response.body?.string() ?: "")
    }

    suspend fun search(query: String): List<Anime> {
        val slug = slugify(query)
        val term = slug.replace("-", " ")
        val doc = fetchDoc("$baseUrl/?s=$term")
        val results = mutableListOf<Anime>()
        val seen = mutableSetOf<String>()

        val animePattern = Regex("^/anime/([a-z0-9-]+-\\d+)$")
        val moviePattern = Regex("^/assistir/([a-z0-9-]+-\\d+)$")

        val links = doc.select("a[href*=/anime/], a[href*=/assistir/]")
        
        return coroutineScope {
            val deferreds = links.mapNotNull { link ->
                val href = link.attr("href").trimEnd('/')
                val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@mapNotNull null } catch (_: Exception) { return@mapNotNull null }

                val matchAnime = animePattern.find(path)
                val matchMovie = moviePattern.find(path)
                val foundSlug = matchAnime?.groupValues?.get(1) ?: matchMovie?.groupValues?.get(1) ?: return@mapNotNull null
                val isMovie = matchMovie != null

                if (seen.contains(foundSlug)) return@mapNotNull null
                seen.add(foundSlug)

                async {
                    val detailUrl = if (isMovie) "$baseUrl/assistir/$foundSlug" else "$baseUrl/anime/$foundSlug"
                    var posterUrl = ""
                    try {
                        val detailDoc = fetchDoc(detailUrl)
                        posterUrl = detailDoc.select("meta[property=og:image]").attr("content").trim()
                    } catch (e: Exception) {}

                    Anime(
                        slug = if (isMovie) "$foundSlug-filme" else foundSlug,
                        title = titleFromSlug(foundSlug),
                        posterUrl = posterUrl,
                        isMovie = isMovie
                    )
                }
            }
            deferreds.awaitAll()
        }
    }

    suspend fun getLatestReleases(): List<Anime> {
        val doc = fetchDoc("$baseUrl/episodios")
        val results = mutableListOf<Anime>()
        val seen = mutableSetOf<String>()
        
        val links = doc.select("a[href*=-season-]")
        
        return coroutineScope {
            val deferreds = links.mapNotNull { link ->
                val href = link.attr("href").trim()
                if (href.isEmpty()) return@mapNotNull null
                
                val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@mapNotNull null } catch (_: Exception) { return@mapNotNull null }
                val epPattern = Regex("-(\\d+)-season-(\\d+)-episode/?$")
                val baseSlug = path.substringAfterLast("/")
                val match = epPattern.find(baseSlug) ?: return@mapNotNull null
                val slugStr = baseSlug.substring(0, match.range.first)
                
                if (seen.contains(slugStr)) return@mapNotNull null
                seen.add(slugStr)
                
                val title = link.select(".list-title").first()?.text()?.trim() 
                    ?: link.select(".epx-desc").first()?.text()?.trim() ?: titleFromSlug(slugStr)
                
                var thumbUrl = ""
                for (sel in listOf(".media-episode", ".epx-thumb", "[data-src]")) {
                    val v = link.select(sel).first()?.attr("data-src")?.trim() ?: ""
                    if (v.isNotEmpty()) {
                        thumbUrl = v
                        break
                    }
                }
                if (thumbUrl.isNotEmpty() && !thumbUrl.startsWith("http")) thumbUrl = "$baseUrl$thumbUrl"
                
                async {
                    val kitsuPoster = fetchKitsuPoster(title)
                    Anime(
                        slug = slugStr,
                        title = title,
                        posterUrl = kitsuPoster ?: thumbUrl,
                        isMovie = false
                    )
                }
            }
            deferreds.awaitAll()
        }
    }

    fun getAnimeDetail(slug: String): AnimeDetail {
        val parsed = parseSlugInput(slug)
        val resolvedSlug = resolveSlug(parsed)

        val doc = if (parsed.isMovie) {
            fetchDoc("$baseUrl/assistir/$resolvedSlug")
        } else {
            fetchDoc("$baseUrl/anime/$resolvedSlug")
        }

        val title = doc.select("h1#title").first()?.text()?.trim() ?: ""
        val altTitle = doc.select("h1#title + h2").first()?.text()?.trim() ?: ""
        val synopsis = doc.select(".text-content").first()?.text()?.trim() ?: ""
        val posterUrl = doc.select("meta[property=og:image]").first()?.attr("content")?.trim() ?: ""
        val rating = doc.select("#certification-anime").first()?.text()?.trim() ?: ""
        val externalId = doc.select("h1#title").first()?.attr("data-content-id")?.trim() ?: ""

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

        if (parsed.isMovie) {
            episodes.add(Episode(number = 1, season = 1, title = title, sourceUrl = "$baseUrl/filme/$resolvedSlug"))
        } else {
            doc.select("a.epx-card").forEach { card ->
                val href = card.attr("href").trim()
                val match = epPattern.find(href) ?: return@forEach
                val season = match.groupValues[1].toIntOrNull() ?: return@forEach
                val epNum = match.groupValues[2].toIntOrNull() ?: return@forEach
                val key = "$season/$epNum"
                if (seenEps.contains(key)) return@forEach
                seenEps.add(key)

                val epTitle = card.select(".epx-desc").first()?.text()?.trim()
                    ?: card.select(".epx-title").first()?.text()?.trim() ?: ""
                val thumbUrl = card.select(".epx-thumb").first()?.attr("data-src")?.trim() ?: ""

                episodes.add(Episode(
                    number = epNum,
                    season = season,
                    title = epTitle,
                    sourceUrl = href,
                    thumbUrl = thumbUrl
                ))
            }
            episodes.sortWith(compareBy({ it.season }, { it.number }))
        }

        val seasons = episodes.map { it.season }.distinct().sorted()

        val anime = Anime(
            slug = slug,
            title = title,
            altTitle = altTitle,
            posterUrl = posterUrl,
            synopsis = synopsis,
            genres = genres,
            rating = rating,
            totalEps = episodes.size,
            sourceUrl = if (parsed.isMovie) "$baseUrl/assistir/$resolvedSlug" else "$baseUrl/anime/$resolvedSlug",
            isMovie = parsed.isMovie
        )
        return AnimeDetail(anime = anime, episodes = episodes, seasons = seasons)
    }

    fun getStream(slug: String, season: Int, episode: Int): StreamResult {
        val parsed = parseSlugInput(slug)
        val resolvedSlug = resolveSlug(parsed)

        val pageUrl = if (parsed.isMovie) {
            "$baseUrl/filme/$resolvedSlug"
        } else {
            "$baseUrl/anime/$resolvedSlug-$season-season-$episode-episode"
        }

        val doc = fetchDoc(pageUrl)
        val embedRefs = parseEmbedRefs(doc)
        if (embedRefs.isEmpty()) throw Exception("No players found for episode $episode")

        val results = mutableListOf<StreamResult>()
        for (ref in embedRefs) {
            try {
                val result = resolveEmbed(ref, pageUrl)
                if (result != null) results.add(result)
            } catch (_: Exception) { }
        }

        if (results.isEmpty()) throw Exception("No playable source found for episode $episode of $slug")
        
        // Prioritize Direct (mp4/m3u8) over embed (Blogger)
        val direct = results.firstOrNull { it.host == "Direct" }
        return direct ?: results.first()
    }

    private data class EmbedRef(val id: String, val label: String)

    private fun parseEmbedRefs(doc: Document): List<EmbedRef> {
        val refs = mutableListOf<EmbedRef>()
        val seen = mutableSetOf<String>()

        fun add(id: String, label: String) {
            val trimmed = id.trim()
            if (trimmed.isEmpty() || seen.contains(trimmed)) return
            seen.add(trimmed)
            refs.add(EmbedRef(trimmed, label.trim()))
        }

        doc.select("button.dropdown-source[data-embed]").forEach { btn ->
            val id = btn.attr("data-embed")
            val label = btn.attr("data-player-name").ifEmpty { btn.select(".name").text() }
            add(id, label)
        }
        if (refs.isEmpty()) {
            doc.select(".play-btn[data-embed]").forEach { btn -> add(btn.attr("data-embed"), "") }
        }
        if (refs.isEmpty()) {
            doc.select("[data-embed]").forEach { el -> add(el.attr("data-embed"), "") }
        }
        return refs
    }

    private val playerEmbedRegex = Regex("""var\s+playerEmbed\s*=\s*"([^"]+)"""")
    private val playerIsHlsRegex = Regex("""window\.playerIsHls\s*=\s*(true|false)""")

    private fun resolveEmbed(ref: EmbedRef, referer: String): StreamResult? {
        val body = FormBody.Builder().add("id", ref.id).build()
        val request = Request.Builder()
            .url("$baseUrl/ajax/embed")
            .post(body)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "*/*")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null

        val html = response.body?.string() ?: return null
        val doc = Jsoup.parse(html)
        val iframe = doc.select("iframe").first() ?: return null

        val srcdoc = iframe.attr("srcdoc")
        if (srcdoc.isNotEmpty()) {
            for (candidate in listOf(srcdoc, org.jsoup.parser.Parser.unescapeEntities(srcdoc, false))) {
                val match = playerEmbedRegex.find(candidate) ?: continue
                val mediaUrl = match.groupValues[1].trim()
                if (mediaUrl.isEmpty() || !mediaUrl.startsWith("http")) continue

                val hlsMatch = playerIsHlsRegex.find(candidate)
                val isHls = hlsMatch?.groupValues?.get(1) == "true" || mediaUrl.lowercase().endsWith(".m3u8")
                val streamType = if (isHls) "m3u8" else "mp4"

                val headers = mutableMapOf<String, String>()
                headers["Referer"] = "$baseUrl/"

                return StreamResult(
                    host = "Direct",
                    streamType = streamType,
                    streamUrl = mediaUrl,
                    headers = headers,
                    notes = if (isHls) listOf("HLS segments have .webp extension but are MPEG-TS.") else emptyList()
                )
            }
        }

        val src = iframe.attr("src").trim()
        if (src.isNotEmpty()) {
            return StreamResult(
                host = if (src.contains("blogger.com") || src.contains("playsus.online")) "Blogger" else "External",
                streamType = "embed",
                streamUrl = src
            )
        }
        return null
    }

    private data class ParsedSlug(val name: String, val id: Int, val season: Int, val isMovie: Boolean)

    private fun parseSlugInput(raw: String): ParsedSlug {
        var s = slugify(raw)
        var isMovie = false
        var season = 1
        var id = 0

        val movieSuffix = Regex("^(.+-\\d+)-filme$")
        movieSuffix.find(s)?.let { s = it.groupValues[1]; isMovie = true }

        val seasonSuffix = Regex("^(.+)-(\\d+)-season$")
        seasonSuffix.find(s)?.let { match ->
            val n = match.groupValues[2].toIntOrNull()
            if (n != null && n >= 1) { s = match.groupValues[1]; season = n }
        }

        val idSuffix = Regex("^(.+)-(\\d+)$")
        idSuffix.find(s)?.let { match ->
            val n = match.groupValues[2].toIntOrNull()
            if (n != null && n > 0) { s = match.groupValues[1]; id = n }
        }

        return ParsedSlug(name = s, id = id, season = season, isMovie = isMovie)
    }

    private fun resolveSlug(parsed: ParsedSlug): String {
        if (parsed.id > 0) {
            val slug = "${parsed.name}-${parsed.id}"
            try {
                if (parsed.isMovie) fetchDoc("$baseUrl/assistir/$slug")
                else fetchDoc("$baseUrl/anime/$slug")
                return slug
            } catch (_: Exception) { }
        }

        val term = parsed.name.replace("-", " ")
        val doc = fetchDoc("$baseUrl/search/$term")
        val animePattern = Regex("^/anime/([a-z0-9-]+-\\d+)$")
        val moviePattern = Regex("^/assistir/([a-z0-9-]+-\\d+)$")

        val candidates = mutableListOf<Pair<String, Boolean>>()
        doc.select("a[href*=/anime/], a[href*=/assistir/]").forEach { link ->
            val href = link.attr("href").trimEnd('/')
            val path = try { java.net.URI(href).path?.trimEnd('/') ?: return@forEach } catch (_: Exception) { return@forEach }
            val matchA = animePattern.find(path)
            val matchM = moviePattern.find(path)
            val foundSlug = matchA?.groupValues?.get(1) ?: matchM?.groupValues?.get(1) ?: return@forEach
            val isMovie = matchM != null
            if (!candidates.any { it.first == foundSlug }) candidates.add(foundSlug to isMovie)
        }

        if (candidates.isEmpty()) throw Exception("Anime '$term' not found")

        if (parsed.isMovie) {
            val movies = candidates.filter { it.second }
            if (movies.isNotEmpty()) return movies.first().first
        }

        val exact = candidates.firstOrNull { slugName(it.first) == parsed.name }
        if (exact != null) return exact.first
        return candidates.minByOrNull { slugName(it.first).length }?.first ?: candidates.first().first
    }

    private fun slugName(slug: String): String {
        val match = Regex("^(.+)-(\\d+)$").find(slug)
        return match?.groupValues?.get(1) ?: slug
    }

    private fun slugify(input: String): String {
        val sb = StringBuilder()
        var prev = '-'
        for (c in input.lowercase().trim()) {
            val mapped = when (c) {
                'á', 'à', 'â', 'ã', 'ä' -> 'a'
                'é', 'è', 'ê', 'ë' -> 'e'
                'í', 'ì', 'î', 'ï' -> 'i'
                'ó', 'ò', 'ô', 'õ', 'ö' -> 'o'
                'ú', 'ù', 'û', 'ü' -> 'u'
                'ç' -> 'c'
                'ñ' -> 'n'
                else -> c
            }
            when {
                mapped in 'a'..'z' || mapped in '0'..'9' -> { sb.append(mapped); prev = mapped }
                prev != '-' -> { sb.append('-'); prev = '-' }
            }
        }
        return sb.toString().trim('-')
    }

    private fun titleFromSlug(slug: String): String {
        val base = slugName(slug)
        return base.split("-").joinToString(" ") { part ->
            if (part == "dublado") "(Dublado)"
            else part.replaceFirstChar { it.uppercase() }
        }
    }

    suspend fun fetchKitsuPoster(title: String): String? {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cleanTitle = title.replace("(Dublado)", "").trim()
                val url = "https://kitsu.io/api/edge/anime?filter[text]=${java.net.URLEncoder.encode(cleanTitle, "UTF-8")}&page[limit]=1"
                val request = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body = response.body?.string() ?: return@withContext null
                
                // Very simple regex parsing to avoid adding JSON library dependency
                val regex = Regex(""""posterImage":\{.*?"original":"([^"]+)"""")
                val match = regex.find(body)
                match?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
        }
    }
}
