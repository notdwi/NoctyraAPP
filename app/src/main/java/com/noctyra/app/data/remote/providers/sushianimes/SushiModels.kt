package com.noctyra.app.data.remote.providers.sushianimes

// Internal models only used within the SushiAnimes provider
internal data class SushiEmbedRef(val id: String, val label: String)

internal data class SushiParsedSlug(
    val name: String,
    val id: Int,
    val season: Int,
    val isMovie: Boolean
)
