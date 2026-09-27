package com.noctyra.app.data.remote.providers.sushianimes

import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.http.HttpClient
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

// Responsible for resolving embed links into playable stream URLs
internal object SushiEmbed {
    private val playerEmbedRegex = Regex("""var\s+playerEmbed\s*=\s*"([^"]+)"""")
    private val playerIsHlsRegex = Regex("""window\.playerIsHls\s*=\s*(true|false)""")

    fun parseEmbedRefs(doc: Document): List<SushiEmbedRef> {
        val refs = mutableListOf<SushiEmbedRef>()
        val seen = mutableSetOf<String>()

        fun add(id: String, label: String) {
            val trimmed = id.trim()
            if (trimmed.isEmpty() || !seen.add(trimmed)) return
            refs.add(SushiEmbedRef(trimmed, label.trim()))
        }

        doc.select("button.dropdown-source[data-embed]").forEach { btn ->
            val id = btn.attr("data-embed")
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

        val response = HttpClient.okHttp.newCall(request).execute()
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

                return StreamResult(
                    host = "Direct",
                    streamType = streamType,
                    streamUrl = mediaUrl,
                    headers = mapOf("Referer" to "${SushiUrls.BASE}/"),
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
}
