package com.noctyra.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noctyra.app.ui.screens.detail.DetailScreen
import com.noctyra.app.ui.screens.home.HomeScreen
import com.noctyra.app.ui.screens.player.PlayerScreen
import com.noctyra.app.ui.screens.search.SearchScreen
import com.noctyra.app.ui.screens.settings.SettingsScreen
import com.noctyra.app.ui.theme.BackgroundDark
import com.noctyra.app.ui.theme.Purple

@Composable
fun NoctyraNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = bottomNavItems.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
    }

    Scaffold(
        containerColor = BackgroundDark,
        bottomBar = {
            if (showBottomBar) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 24.dp, end = 24.dp, bottom = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                ) {
                    NavigationBar(
                        modifier = Modifier.height(64.dp),
                        containerColor = Color(0xFF1E1E2A), 
                        contentColor = Color.White
                    ) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                        bottomNavItems.forEach { item ->
                            val selected = currentDestination?.hierarchy?.any { it.route == item.screen.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Purple,
                                    unselectedIconColor = Color.Gray,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        }
                        androidx.compose.foundation.layout.Spacer(Modifier.width(12.dp))
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding).background(BackgroundDark),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) }
        ) {
            composable(Screen.Home.route) {
                HomeScreen(onAnimeClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) })
            }
            composable(Screen.Search.route) {
                SearchScreen(onAnimeClick = { slug -> navController.navigate(Screen.Detail.createRoute(slug)) })
            }
            composable(Screen.Bookmarks.route) {
                PlaceholderScreen("Library")
            }
            composable(Screen.Downloads.route) {
                PlaceholderScreen("Downloads")
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(
                route = Screen.Detail.route,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { entry ->
                val slug = entry.arguments?.getString("slug") ?: ""
                DetailScreen(
                    slug = slug,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { s, season, ep ->
                        navController.navigate(Screen.Player.createRoute(s, season, ep))
                    }
                )
            }
            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType }
                )
            ) { entry ->
                val slug = entry.arguments?.getString("slug") ?: ""
                val season = entry.arguments?.getInt("season") ?: 1
                val episode = entry.arguments?.getInt("episode") ?: 1
                PlayerScreen(
                    slug = slug,
                    season = season,
                    episode = episode,
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { s, ep ->
                        navController.navigate(Screen.Player.createRoute(slug, s, ep)) {
                            popUpTo(Screen.Player.route) { inclusive = true }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(title: String) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
    }
}
