package com.noctyra.app.ui.screens.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noctyra.app.R
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

@Composable
fun HomeScreen(onAnimeClick: (String) -> Unit, viewModel: HomeViewModel = viewModel()) {
    val heroState   by viewModel.heroState.collectAsState()
    val recentState by viewModel.recentState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val isekaiState by viewModel.isekaiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(BackgroundDark),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top bar with logo
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.noc_logo),
                        contentDescription = "Noctyra",
                        modifier = Modifier.height(26.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Noctyra",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                        color = TextPrimary
                    )
                }
            }
        }

        // Hero Carousel
        item { HeroCarousel(state = heroState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        // Lançamentos
        item { SectionHeader("Lançamentos", showSeeAll = false) }
        item { AnimeRow(state = recentState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        // Ação
        item { SectionHeader("Ação", showSeeAll = false) }
        item { AnimeRow(state = actionState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        // Isekai
        item { SectionHeader("Isekai", showSeeAll = false) }
        item { AnimeRow(state = isekaiState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }
    }
}

@Composable
private fun AnimeRow(state: UiState<List<Anime>>, onAnimeClick: (String) -> Unit, onRetry: () -> Unit) {
    when (state) {
        is UiState.Loading ->
            Box(Modifier.fillMaxWidth().height(200.dp)) { LoadingScreen() }
        is UiState.Error ->
            Box(Modifier.fillMaxWidth().height(200.dp)) { ErrorScreen(message = state.message, onRetry = onRetry) }
        is UiState.Success ->
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.data) { anime ->
                    AnimeCard(anime = anime, onClick = { onAnimeClick(anime.slug) })
                }
            }
    }
}

@Composable
fun HeroCarousel(state: UiState<List<Anime>>, onAnimeClick: (String) -> Unit, onRetry: () -> Unit) {
    when (state) {
        is UiState.Loading ->
            Box(Modifier.fillMaxWidth().height(380.dp).background(SurfaceDark)) { LoadingScreen() }
        is UiState.Error ->
            Box(Modifier.fillMaxWidth().height(380.dp)) { ErrorScreen(message = state.message, onRetry = onRetry) }
        is UiState.Success -> {
            val animes = state.data
            if (animes.isEmpty()) return
            val pagerState = rememberPagerState(pageCount = { animes.size })
            val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

            LaunchedEffect(isDragged) {
                if (!isDragged) {
                    while (true) {
                        kotlinx.coroutines.delay(4500)
                        val next = (pagerState.currentPage + 1) % animes.size
                        pagerState.animateScrollToPage(next, animationSpec = tween(600))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(420.dp)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    val anime = animes[page]
                    Box(Modifier.fillMaxSize().clickable { onAnimeClick(anime.slug) }) {
                        // Poster image
                        AsyncImage(
                            model = anime.posterUrl,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Multi-stop gradient overlay
                        Box(
                            modifier = Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(0.2f),
                                        0.45f to Color.Transparent,
                                        1.0f to BackgroundDark
                                    )
                                )
                            )
                        )
                        // Content at bottom
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(horizontal = 20.dp, vertical = 20.dp)
                        ) {
                            // Genres
                            if (anime.genres.isNotEmpty()) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    anime.genres.take(3).forEach { GenreChip(it) }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            // Title
                            Text(
                                text = anime.title,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Black, lineHeight = 30.sp
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(14.dp))
                            // CTA Buttons
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onAnimeClick(anime.slug) },
                                    shape = RoundedCornerShape(22.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Accent),
                                    modifier = Modifier.height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Assistir", style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = { onAnimeClick(anime.slug) },
                                    shape = RoundedCornerShape(22.dp),
                                    modifier = Modifier.height(42.dp),
                                    contentPadding = PaddingValues(horizontal = 20.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        brush = Brush.linearGradient(listOf(Color.White.copy(0.4f), Color.White.copy(0.4f)))
                                    )
                                ) {
                                    Text("Detalhes", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            // Dot indicators
                            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                repeat(animes.size) { i ->
                                    val isSelected = pagerState.currentPage == i
                                    Box(
                                        modifier = Modifier
                                            .height(4.dp)
                                            .width(if (isSelected) 20.dp else 6.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Accent else Color.White.copy(0.3f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
