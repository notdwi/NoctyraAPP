package com.noctyra.app.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.local.WatchProgress
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.displayTitle
import com.noctyra.app.data.model.isDubbed
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.delay

private val HeroHeaderHeight = 110.dp

@Composable
internal fun HeroCarousel(
    items: List<Anime>,
    details: Map<String, AnimeDetail>,
    favorites: Set<String>,
    lite: Boolean,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onPlay: (Anime) -> Unit,
    onToggleFav: (Anime) -> Unit,
    onVisible: (String) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged, items.size, lite) {
        if (isDragged || items.size < 2) return@LaunchedEffect
        while (true) {
            delay(if (lite) 9000 else 6000)
            val next = (pagerState.currentPage + 1) % items.size
            if (lite) pagerState.scrollToPage(next) else pagerState.animateScrollToPage(next, animationSpec = tween(650))
        }
    }
    LaunchedEffect(pagerState.settledPage, items) {
        items.getOrNull(pagerState.settledPage)?.let { onVisible(it.slug) }
    }

    Box(Modifier.fillMaxWidth()) {
        HorizontalPager(state = pagerState, key = { items[it].slug }) { page ->
            val anime = items[page]
            HeroPage(
                anime = anime,
                detail = details[anime.slug],
                isFavorite = anime.slug in favorites,
                onPlay = { onPlay(anime) },
                onToggleFav = { onToggleFav(anime) }
            )
        }
        Column(Modifier.statusBarsPadding()) {
            BrandHeader(onSearch = onSearch, onProfile = onProfile)
            HomeTabRow(0, onTab)
        }
        Row(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(items.size) { i ->
                val selected = pagerState.currentPage == i
                val width by animateDpAsState(if (selected) 30.dp else 9.dp, label = "dot")
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .height(9.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(if (selected) Pink else Color.White.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
private fun HeroPage(
    anime: Anime,
    detail: AnimeDetail?,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFav: () -> Unit
) {
    val meta = remember(anime, detail) {
        buildList {
            add(anime.subtitle.ifEmpty { if (anime.isMovie) "Filme" else "Anime" })
            addAll((detail?.anime?.genres?.takeIf { it.isNotEmpty() } ?: anime.genres).take(2))
            val eps = detail?.episodes?.size ?: 0
            if (eps > 0 && !anime.isMovie) add("$eps Episódios")
        }.joinToString("  •  ")
    }
    val synopsis = anime.synopsis.ifEmpty { detail?.anime?.synopsis.orEmpty() }

    Box(Modifier.fillMaxWidth()) {
        NetImage(anime.bannerUrl.ifEmpty { anime.posterUrl }, Modifier.matchParentSize(), contentDescription = anime.title)
        Box(Modifier.matchParentSize().background(LeftScrim))
        Box(Modifier.matchParentSize().background(BottomFade))
        Box(Modifier.fillMaxWidth().height(200.dp).background(TopScrim))

        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = HeroHeaderHeight + 64.dp, start = 20.dp, end = 20.dp, bottom = 40.dp)
        ) {
            Text(
                anime.displayTitle,
                style = MaterialTheme.typography.displaySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (anime.isDubbed) {
                Spacer(Modifier.height(6.dp))
                DubBadge(text = "Dublado", large = true)
            }
            Spacer(Modifier.height(12.dp))
            Text(meta, style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary.copy(alpha = 0.82f)), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (synopsis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    synopsis,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.66f)
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PinkButton("Assistir agora", onPlay, icon = Icons.Default.PlayArrow, modifier = Modifier.widthIn(min = 160.dp))
                Spacer(Modifier.width(14.dp))
                GhostButton(
                    "Minha Lista",
                    onToggleFav,
                    icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    tint = if (isFavorite) Pink else TextPrimary
                )
            }
        }
    }
}
