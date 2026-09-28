package com.noctyra.app.ui.navigation

import android.net.Uri

object Routes {
    const val HOME = "home"
    const val EXPLORE = "explore"
    const val PROFILE = "profile"
    const val MYLIST = "mylist?tab={tab}"
    const val SEARCH = "search?q={q}"
    const val DETAIL = "detail/{slug}"
    const val PLAYER = "player/{slug}/{season}/{episode}"
    const val CATEGORY = "category/{slug}?name={name}"
    const val PARTY = "party"

    fun myList(tab: Int = 0) = "mylist?tab=$tab"
    fun search(query: String = "") = "search?q=${Uri.encode(query)}"
    fun detail(slug: String) = "detail/$slug"
    fun category(slug: String, name: String) = "category/$slug?name=${Uri.encode(name)}"
    fun player(slug: String, season: Int, episode: Int) = "player/$slug/$season/$episode"

    val tabRoutes = setOf(HOME, EXPLORE, MYLIST, PROFILE)
}
