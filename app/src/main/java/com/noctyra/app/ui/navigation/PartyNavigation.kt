package com.noctyra.app.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.noctyra.app.party.Party

fun NavHostController.openPartyPlayer(slug: String, season: Int, episode: Int) {
    val route = Routes.player(slug, season, episode)
    if (currentBackStackEntry?.destination?.route == Routes.PLAYER &&
        currentBackStackEntry?.arguments?.let {
            it.getString("slug") == slug && it.getInt("season") == season && it.getInt("episode") == episode
        } == true
    ) return
    navigate(route) {
        popUpTo(Routes.PLAYER) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun PartyNavigationEffect(navController: NavHostController) {
    val context = LocalContext.current
    LaunchedEffect(navController) {
        Party.navigate.collect { st -> navController.openPartyPlayer(st.slug, st.season, st.episode) }
    }
    LaunchedEffect(Unit) {
        Party.notice.collect { msg ->
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                Party.consumeNotice()
            }
        }
    }
}
