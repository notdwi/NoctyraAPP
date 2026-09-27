package com.noctyra.app.data.remote.providers.sushianimes

// Utility functions used across SushiAnimes provider
internal object SushiUtils {

    fun slugify(input: String): String {
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

    fun slugName(slug: String): String {
        val match = Regex("^(.+)-(\\d+)$").find(slug)
        return match?.groupValues?.get(1) ?: slug
    }

    fun titleFromSlug(slug: String): String {
        val base = slugName(slug)
        return base.split("-").joinToString(" ") { part ->
            if (part == "dublado") "(Dublado)"
            else part.replaceFirstChar { it.uppercase() }
        }
    }

    fun parseSlugInput(raw: String): SushiParsedSlug {
        var s = slugify(raw)
        var isMovie = false
        var season = 1
        var id = 0

        Regex("^(.+-\\d+)-filme$").find(s)?.let { s = it.groupValues[1]; isMovie = true }

        Regex("^(.+)-(\\d+)-season$").find(s)?.let { match ->
            val n = match.groupValues[2].toIntOrNull()
            if (n != null && n >= 1) { s = match.groupValues[1]; season = n }
        }

        Regex("^(.+)-(\\d+)$").find(s)?.let { match ->
            val n = match.groupValues[2].toIntOrNull()
            if (n != null && n > 0) { s = match.groupValues[1]; id = n }
        }

        return SushiParsedSlug(name = s, id = id, season = season, isMovie = isMovie)
    }
}
