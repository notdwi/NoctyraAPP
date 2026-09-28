package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.http.HttpClient
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// Responsible for resolving embed links into playable stream URLs
internal object SushiEmbed {
    private val playerEmbedRegex  = Regex("""var\s+playerEmbed\s*=\s*"([^"]+)"""")
    private val playerIsHlsRegex  = Regex("""window\.playerIsHls\s*=\s*(true|false)""")

    // Regexes to find video URLs directly inside any HTML page
    private val genericVideoUrlRegex = Regex(
        """["'](https?://[^"']+\.(?:m3u8|mp4)(?:\?[^"']*)?)["']""",
        RegexOption.IGNORE_CASE
    )
    private val fileUrlRegex = Regex(
        """"file"\s*:\s*"(https?://[^"]+)"""",
        RegexOption.IGNORE_CASE
    )
    private val sourceUrlRegex = Regex(
        """source\s*:\s*["'](https?://[^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    // Desktop UA — avoids anti-WebView blocks on sites like playsus.online / blogger
    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    fun parseEmbedRefs(doc: Document): List<SushiEmbedRef> {
        val refs = mutableListOf<SushiEmbedRef>()
        val seen = mutableSetOf<String>()

        fun add(id: String, label: String) {
            val trimmed = id.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed)) return
            refs.add(SushiEmbedRef(trimmed, label.trim()))
        }

        doc.select("button.dropdown-source[data-embed]").forEach { btn ->
            val id    = btn.attr("data-embed")
            val label = btn.attr("data-player-name").ifEmpty { btn.select(".name").text() }
            add(id, label)
        }
        if (refs.isEmpty()) doc.select(".play-btn[data-embed]").forEach { add(it.attr("data-embed"), "") }
        if (refs.isEmpty()) doc.select("[data-embed]").forEach { add(it.attr("data-embed"), "") }
        return refs
    }

    fun resolveEmbed(ref: SushiEmbedRef, referer: String): StreamResult? {
        val body = FormBody.Builder().add("id", ref.id).build()
        val request = Request.Builder()
            .url(SushiUrls.AJAX_EMBED)
            .post(body)
            .header("User-Agent", HttpClient.MOBILE_UA)
            .header("Referer", referer)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Accept", "*/*")
            .build()

        val html = HttpClient.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()
        } ?: return null
        val doc  = Jsoup.parse(html)
        val iframe = doc.select("iframe").first() ?: return null

        // ── 1. srcdoc with embedded playerEmbed variable (best case) ──────────
        val srcdoc = iframe.attr("srcdoc")
        if (srcdoc.isNotEmpty()) {
            for (candidate in listOf(srcdoc, org.jsoup.parser.Parser.unescapeEntities(srcdoc, false))) {
                val match    = playerEmbedRegex.find(candidate) ?: continue
                val mediaUrl = match.groupValues[1].trim()
                if (mediaUrl.isEmpty() || !mediaUrl.startsWith("http")) continue

                val hlsMatch   = playerIsHlsRegex.find(candidate)
                val isHls      = hlsMatch?.groupValues?.get(1) == "true" || mediaUrl.lowercase().endsWith(".m3u8")
                val streamType = if (isHls) "m3u8" else "mp4"

                return StreamResult(
                    host       = "Direct",
                    streamType = streamType,
                    streamUrl  = mediaUrl,
                    headers    = mapOf("Referer" to "${SushiUrls.BASE}/"),
                    notes      = if (isHls) listOf("HLS .webp extension = MPEG-TS") else emptyList()
                )
            }
        }

        // ── 2. iframe src — try to resolve the external page directly ─────────
        val src = iframe.attr("src").trim()
        if (src.isNotEmpty()) {
            // Try to fetch the embed page with a desktop UA and extract the video URL
            val directResult = tryResolveExternalEmbed(src, referer)
            if (directResult != null) return directResult

            // Give up and return as embed (WebView fallback)
            return StreamResult(
                host       = if (src.contains("blogger.com") || src.contains("playsus.online")) "Blogger" else "External",
                streamType = "embed",
                streamUrl  = src
            )
        }
        return null
    }

    /**
     * Tries to extract a direct video URL from an embed page by fetching it
     * server-side with a desktop Chrome UA (bypasses anti-WebView checks).
     */
    private fun tryResolveExternalEmbed(embedUrl: String, originalReferer: String): StreamResult? {
        return try {
            val req = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", DESKTOP_UA)
                .header("Referer", originalReferer)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "pt-BR,pt;q=0.9,en;q=0.8")
                .build()

            val pageHtml = HttpClient.media.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            } ?: return null

            // Try playerEmbed pattern first
            for (candidate in listOf(pageHtml, org.jsoup.parser.Parser.unescapeEntities(pageHtml, false))) {
                val m = playerEmbedRegex.find(candidate)
                if (m != null) {
                    val url = m.groupValues[1].trim()
                    if (url.startsWith("http")) {
                        val isHls = playerIsHlsRegex.find(candidate)?.groupValues?.get(1) == "true"
                            || url.lowercase().endsWith(".m3u8")
                        return StreamResult(
                            host       = "Direct",
                            streamType = if (isHls) "m3u8" else "mp4",
                            streamUrl  = url,
                            headers    = mapOf("Referer" to embedUrl)
                        )
                    }
                }
            }

            // Try "file": "url" pattern (JWPlayer / VideoJS)
            fileUrlRegex.find(pageHtml)?.groupValues?.get(1)?.trim()?.let { url ->
                if (url.startsWith("http")) {
                    val isHls = url.lowercase().endsWith(".m3u8")
                    return StreamResult(
                        host       = "Direct",
                        streamType = if (isHls) "m3u8" else "mp4",
                        streamUrl  = url,
                        headers    = mapOf("Referer" to embedUrl)
                    )
                }
            }

            // Try source: "url" pattern
            sourceUrlRegex.find(pageHtml)?.groupValues?.get(1)?.trim()?.let { url ->
                if (url.startsWith("http")) {
                    val isHls = url.lowercase().endsWith(".m3u8")
                    return StreamResult(
                        host       = "Direct",
                        streamType = if (isHls) "m3u8" else "mp4",
                        streamUrl  = url,
                        headers    = mapOf("Referer" to embedUrl)
                    )
                }
            }

            // Try any direct .m3u8 or .mp4 URL in the page
            genericVideoUrlRegex.find(pageHtml)?.groupValues?.get(1)?.trim()?.let { url ->
                val isHls = url.lowercase().contains(".m3u8")
                return StreamResult(
                    host       = "Direct",
                    streamType = if (isHls) "m3u8" else "mp4",
                    streamUrl  = url,
                    headers    = mapOf("Referer" to embedUrl)
                )
            }

            null
        } catch (_: Exception) {
            null
        }
    }
}
