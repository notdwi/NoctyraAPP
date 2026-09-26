package com.noctyra.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Bookmarks : Screen("bookmarks")
    data object Downloads : Screen("downloads")
    data object Settings : Screen("settings")
    data object Detail : Screen("detail/{slug}") {
        fun createRoute(slug: String) = "detail/$slug"
    }
    data object Player : Screen("player/{slug}/{season}/{episode}") {
        fun createRoute(slug: String, season: Int, episode: Int) = "player/$slug/$season/$episode"
    }
}

data class BottomNavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, Icons.Default.Explore, "Explorar"),
    BottomNavItem(Screen.Search, Icons.Default.Search, "Buscar"),
    BottomNavItem(Screen.Bookmarks, Icons.Default.Bookmark, "Biblioteca"),
    BottomNavItem(Screen.Downloads, Icons.Default.Download, "Downloads"),
    BottomNavItem(Screen.Settings, Icons.Default.Settings, "Ajustes")
)
