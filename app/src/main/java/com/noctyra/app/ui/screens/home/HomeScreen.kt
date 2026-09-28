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

private val HeaderHeight = 110.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (String) -> Unit,
    onPlay: (String, Int, Int) -> Unit,
    onSearch: () -> Unit,
    onCategory: (Category) -> Unit,
    onOpenMyList: (Int) -> Unit,
    onOpenProfile: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val heroDetails by viewModel.heroDetails.collectAsStateWithLifecycle()
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val continueList by LibraryStore.continueWatching.collectAsStateWithLifecycle()
    val lite = rememberLiteMode()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    val onTab: (Int) -> Unit = { if (it == 4) onOpenMyList(0) else tab = it }
    val toggleFav: (Anime) -> Unit = { LibraryStore.toggleFavorite(it) }
    val playAnime: (Anime) -> Unit = { anime ->
        val last = LibraryStore.lastWatched(anime.slug)
        val detail = heroDetails[anime.slug] ?: AnimeRepository.peekDetail(anime.slug)
        val first = detail?.episodes?.firstOrNull()
        when {
            last != null -> onPlay(anime.slug, last.season, last.episode)
            first != null -> onPlay(anime.slug, first.season, first.number)
            else -> onAnimeClick(anime.slug)
        }
    }

    PullToRefreshBox(
        isRefreshing = ui.refreshing && ui.feed != null,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize().background(BackgroundDark)
    ) {
        if (tab == 0) {
            FeedTab(
                ui = ui,
                heroDetails = heroDetails,
                favorites = favorites,
                continueList = continueList,
                lite = lite,
                onTab = onTab,
                onSearch = onSearch,
                onProfile = onOpenProfile,
                onAnimeClick = onAnimeClick,
                onPlay = onPlay,
                onPlayAnime = playAnime,
                onToggleFav = toggleFav,
                onHeroVisible = viewModel::ensureHeroDetail,
                onRetry = viewModel::refresh,
                onSeeAllHistory = { onOpenMyList(1) },
                onSeeAllTab = { tab = it }
            )
        } else {
            val columns = if (tab == 1) GridCells.Adaptive(108.dp) else GridCells.Fixed(2)
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.statusBarsPadding()) {
                    BrandHeader(onSearch = onSearch, onProfile = onOpenProfile)
                    HomeTabRow(tab, onTab)
                }
                LazyVerticalGrid(
                    columns = columns,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = BottomBarSpace),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    when (tab) {
                        1 -> animeGrid(ui.allAnimes, favorites, onAnimeClick, toggleFav)
                        2 -> items(ui.feed?.newEpisodes.orEmpty(), key = { "${it.slug}/${it.latestSeason}/${it.latestEpisode}" }) { ep ->
                            NewEpisodeCard(ep, onClick = { onPlay(ep.slug, ep.latestSeason, ep.latestEpisode) }, width = null)
                        }
                        3 -> items(Categories, key = { it.name }) { c ->
                            CategoryTile(c.name, c.colors, c.icon, onClick = { onCategory(c) })
                        }
                    }
                    if (ui.feed == null && tab != 3) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val error = ui.error
                            if (error != null) ErrorScreen(error, onRetry = viewModel::refresh) else LoadingScreen(Modifier.height(240.dp))
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.statusBars)
                .background(BackgroundDark.copy(alpha = 0.7f))
        )
    }
}

private fun LazyGridScope.animeGrid(
    list: List<Anime>,
    favorites: Set<String>,
    onAnimeClick: (String) -> Unit,
    onToggleFav: (Anime) -> Unit
) {
    items(list, key = { it.slug }, contentType = { "poster" }) { anime ->
        PosterCard(
            anime = anime,
            isFavorite = anime.slug in favorites,
            onClick = { onAnimeClick(anime.slug) },
            onToggleFavorite = { onToggleFav(anime) },
            width = null
        )
    }
}

@Composable
private fun FeedTab(
    ui: HomeUi,
    heroDetails: Map<String, AnimeDetail>,
    favorites: Set<String>,
    continueList: List<WatchProgress>,
    lite: Boolean,
    onTab: (Int) -> Unit,
    onSearch: () -> Unit,
    onProfile: () -> Unit,
    onAnimeClick: (String) -> Unit,
    onPlay: (String, Int, Int) -> Unit,
    onPlayAnime: (Anime) -> Unit,
    onToggleFav: (Anime) -> Unit,
    onHeroVisible: (String) -> Unit,
    onRetry: () -> Unit,
    onSeeAllHistory: () -> Unit,
    onSeeAllTab: (Int) -> Unit
) {
    val feed = ui.feed
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = BottomBarSpace)
    ) {
        item(key = "hero", contentType = "hero") {
            val hero = feed?.hero.orEmpty().ifEmpty { feed?.latestAnimes.orEmpty().take(5) }
            if (hero.isNotEmpty()) {
                HeroCarousel(
                    items = hero,
                    details = heroDetails,
                    favorites = favorites,
                    lite = lite,
                    onTab = onTab,
                    onSearch = onSearch,
                    onProfile = onProfile,
                    onPlay = onPlayAnime,
                    onToggleFav = onToggleFav,
                    onVisible = onHeroVisible
                )
            } else {
                Column(Modifier.statusBarsPadding()) {
                    BrandHeader(onSearch = onSearch, onProfile = onProfile)
                    HomeTabRow(0, onTab)
                    val error = ui.error
                    if (error != null) ErrorScreen(error, onRetry = onRetry)
                    else SkeletonBox(Modifier.padding(16.dp).fillMaxWidth().height(300.dp))
                }
            }
        }

        if (feed == null) {
            if (ui.error == null) item(key = "skeleton") { SkeletonRow() }
            return@LazyColumn
        }

        if (feed.latestAnimes.isNotEmpty()) {
            item(key = "h_launch") { SectionHeader("Lançamentos", onSeeAll = { onSeeAllTab(1) }) }
            item(key = "r_launch", contentType = "posterRow") {
                PosterRow(feed.latestAnimes, favorites, 124, onAnimeClick, onToggleFav)
            }
        }

        if (continueList.isNotEmpty()) {
            item(key = "h_continue") { SectionHeader("Continue assistindo", onSeeAll = onSeeAllHistory) }
            item(key = "r_continue", contentType = "continueRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(continueList, key = { it.key }) { progress ->
                        ContinueWatchingCard(
                            progress = progress,
                            onClick = { onPlay(progress.slug, progress.season, progress.episode) },
                            onRemove = { LibraryStore.removeFromHistory(progress.slug) },
                            modifier = Modifier.fillParentMaxWidth(if (continueList.size == 1) 1f else 0.9f)
                        )
                    }
                }
            }
        }

        if (ui.hot.isNotEmpty()) {
            item(key = "h_hot") { SectionHeader("Animes em alta", onSeeAll = { onSeeAllTab(1) }) }
            item(key = "r_hot", contentType = "posterRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(ui.hot) { index, anime ->
                        PosterCard(
                            anime = anime,
                            isFavorite = anime.slug in favorites,
                            onClick = { onAnimeClick(anime.slug) },
                            onToggleFavorite = { onToggleFav(anime) },
                            width = 118.dp,
                            rank = if (index < 10) index + 1 else null
                        )
                    }
                }
            }
        }

        if (feed.newEpisodes.isNotEmpty()) {
            item(key = "h_new") { SectionHeader("Novos episódios", onSeeAll = { onSeeAllTab(2) }) }
            item(key = "r_new", contentType = "episodeRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feed.newEpisodes, key = { "${it.slug}/${it.latestSeason}/${it.latestEpisode}" }) { ep ->
                        NewEpisodeCard(ep, onClick = { onPlay(ep.slug, ep.latestSeason, ep.latestEpisode) })
                    }
                }
            }
        }

        if (feed.latestMovies.isNotEmpty()) {
            item(key = "h_movies") { SectionHeader("Filmes recentes") }
            item(key = "r_movies", contentType = "posterRow") {
                PosterRow(feed.latestMovies, favorites, 124, onAnimeClick, onToggleFav)
            }
        }
    }
}

@Composable
private fun PosterRow(
    list: List<Anime>,
    favorites: Set<String>,
    width: Int,
    onAnimeClick: (String) -> Unit,
    onToggleFav: (Anime) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(list, key = { it.slug }) { anime ->
            PosterCard(
                anime = anime,
                isFavorite = anime.slug in favorites,
                onClick = { onAnimeClick(anime.slug) },
                onToggleFavorite = { onToggleFav(anime) },
                width = width.dp
            )
        }
    }
}

@Composable
private fun SkeletonRow() {
    Column {
        Spacer(Modifier.height(26.dp))
        SkeletonBox(Modifier.padding(horizontal = 16.dp).width(160.dp).height(22.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(4) { SkeletonBox(Modifier.width(124.dp).aspectRatio(0.72f)) }
        }
    }
}

@Composable
private fun HeroCarousel(
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
                .padding(top = HeaderHeight + 64.dp, start = 20.dp, end = 20.dp, bottom = 40.dp)
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
