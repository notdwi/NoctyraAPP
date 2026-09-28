package com.noctyra.app.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.noctyra.app.ui.components.BottomTab
import com.noctyra.app.ui.components.Category
import com.noctyra.app.ui.components.NoctyraBottomBar
import com.noctyra.app.ui.components.rememberLiteMode
import com.noctyra.app.ui.screens.category.CategoryScreen
import com.noctyra.app.ui.screens.detail.DetailScreen
import com.noctyra.app.ui.screens.explore.ExploreScreen
import com.noctyra.app.ui.screens.home.HomeScreen
import com.noctyra.app.ui.screens.home.HomeViewModel
import com.noctyra.app.ui.screens.mylist.MyListScreen
import com.noctyra.app.ui.screens.party.PartyScreen
import com.noctyra.app.ui.screens.player.PlayerScreen
import com.noctyra.app.ui.screens.search.SearchScreen
import com.noctyra.app.ui.screens.settings.ProfileScreen
import com.noctyra.app.ui.theme.BackgroundDark

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun NoctyraNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val homeViewModel: HomeViewModel = viewModel()
    val lite = rememberLiteMode()

    val openDetail: (String) -> Unit = { navController.navigate(Routes.detail(it)) }
    val openPlayer: (String, Int, Int) -> Unit = { s, season, ep -> navController.navigate(Routes.player(s, season, ep)) }
    val openSearch: (String) -> Unit = { navController.navigate(Routes.search(it)) }
    val openCategory: (Category) -> Unit = { navController.navigate(Routes.category(it.slug, it.name)) }

    PartyNavigationEffect(navController)

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.fillMaxSize(),
            enterTransition = { if (lite) EnterTransition.None else fadeIn(tween(180)) },
            exitTransition = { if (lite) ExitTransition.None else fadeOut(tween(120)) },
            popEnterTransition = { if (lite) EnterTransition.None else fadeIn(tween(180)) },
            popExitTransition = { if (lite) ExitTransition.None else fadeOut(tween(120)) }
        ) {
            composable(Routes.HOME) {
                HomeScreen(
                    viewModel = homeViewModel,
                    onAnimeClick = openDetail,
                    onPlay = openPlayer,
                    onSearch = { openSearch("") },
                    onCategory = openCategory,
                    onOpenMyList = { tab -> navController.navigateTab(Routes.myList(tab)) },
                    onOpenProfile = { navController.navigateTab(Routes.PROFILE) }
                )
            }
            composable(Routes.EXPLORE) {
                ExploreScreen(
                    homeViewModel = homeViewModel,
                    onSearch = { openSearch("") },
                    onCategory = openCategory,
                    onAnimeClick = openDetail
                )
            }
            composable(
                Routes.MYLIST,
                arguments = listOf(navArgument("tab") { type = NavType.IntType; defaultValue = 0 })
            ) { entry ->
                MyListScreen(
                    initialTab = entry.arguments?.getInt("tab") ?: 0,
                    onAnimeClick = openDetail,
                    onPlay = openPlayer,
                    onExplore = { navController.navigateTab(Routes.EXPLORE) }
                )
            }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    onOpenParty = { navController.navigate(Routes.PARTY) },
                    onOpenDownloads = { navController.navigateTab(Routes.myList(2)) },
                    onOpenHistory = { navController.navigateTab(Routes.myList(1)) }
                )
            }
            composable(
                Routes.SEARCH,
                arguments = listOf(navArgument("q") { type = NavType.StringType; defaultValue = "" })
            ) { entry ->
                SearchScreen(
                    initialQuery = entry.arguments?.getString("q").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onAnimeClick = openDetail,
                    onCategory = openCategory
                )
            }
            composable(
                Routes.CATEGORY,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" }
                )
            ) { entry ->
                CategoryScreen(
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    name = entry.arguments?.getString("name").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onAnimeClick = openDetail
                )
            }
            composable(
                Routes.DETAIL,
                arguments = listOf(navArgument("slug") { type = NavType.StringType })
            ) { entry ->
                DetailScreen(
                    slug = entry.arguments?.getString("slug").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = openPlayer
                )
            }
            composable(
                Routes.PLAYER,
                arguments = listOf(
                    navArgument("slug") { type = NavType.StringType },
                    navArgument("season") { type = NavType.IntType },
                    navArgument("episode") { type = NavType.IntType }
                )
            ) { entry ->
                val slug = entry.arguments?.getString("slug").orEmpty()
                PlayerScreen(
                    slug = slug,
                    season = entry.arguments?.getInt("season") ?: 1,
                    episode = entry.arguments?.getInt("episode") ?: 1,
                    onBack = { navController.popBackStack() },
                    onEpisodeClick = { season, ep ->
                        navController.navigate(Routes.player(slug, season, ep)) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                        }
                    },
                    onOpenParty = { navController.navigate(Routes.PARTY) }
                )
            }
            composable(Routes.PARTY) {
                PartyScreen(
                    onBack = { navController.popBackStack() },
                    onPickAnime = { navController.navigateTab(Routes.HOME) },
                    onOpenPlayer = { st -> navController.openPartyPlayer(st.slug, st.season, st.episode) }
                )
            }
        }

        if (currentRoute in Routes.tabRoutes) {
            NoctyraBottomBar(
                currentRoute = currentRoute,
                onTab = { tab ->
                    val route = if (tab == BottomTab.MyList) Routes.myList(0) else tab.route
                    navController.navigateTab(route)
                },
                onSearch = { openSearch("") },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
