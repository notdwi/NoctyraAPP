package com.noctyra.app.ui.screens.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.draw.blur
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
        // Top bar
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.noc_logo),
                    contentDescription = "Noctyra",
                    modifier = Modifier.height(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Noctyra",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = TextPrimary
                )
            }
        }

        // Hero Carousel
        item { HeroCarousel(state = heroState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { SectionHeader("Lançamentos") }
        item { AnimeRow(state = recentState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { SectionHeader("Ação") }
        item { AnimeRow(state = actionState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { SectionHeader("Isekai") }
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
            Box(
                Modifier.fillMaxWidth().height(440.dp).background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) { LoadingScreen() }

        is UiState.Error ->
            Box(Modifier.fillMaxWidth().height(440.dp)) { ErrorScreen(message = state.message, onRetry = onRetry) }

        is UiState.Success -> {
            val animes = state.data
            if (animes.isEmpty()) return
            val pagerState = rememberPagerState(pageCount = { animes.size })
            val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

            LaunchedEffect(isDragged) {
                if (!isDragged) {
                    while (true) {
                        kotlinx.coroutines.delay(5000)
                        val next = (pagerState.currentPage + 1) % animes.size
                        pagerState.animateScrollToPage(next, animationSpec = tween(700))
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val anime = animes[page]
                    Box(Modifier.fillMaxSize()) {
                        // Blurred background layer
                        AsyncImage(
                            model = anime.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(20.dp)
                        )
                        // Dark veil over blur
                        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))

                        // Centered poster card
                        AsyncImage(
                            model = anime.posterUrl,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 24.dp)
                                .width(160.dp)
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )

                        // Bottom gradient fade into background
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, BackgroundDark)
                                    )
                                )
                        )

                        // Text + buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 24.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = anime.title,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 20.sp
                                ),
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (anime.genres.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = anime.genres.take(3).joinToString(" • "),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(
                                    onClick = { onAnimeClick(anime.slug) },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Pink),
                                    modifier = Modifier.height(42.dp).weight(1f),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Assistir", style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = { onAnimeClick(anime.slug) },
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier.height(42.dp).weight(1f),
                                    contentPadding = PaddingValues(horizontal = 16.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                    border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                                        brush = Brush.linearGradient(
                                            listOf(Color.White.copy(0.35f), Color.White.copy(0.35f))
                                        )
                                    )
                                ) {
                                    Text("Detalhes", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            // Pill indicators
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                repeat(animes.size) { i ->
                                    val isSelected = pagerState.currentPage == i
                                    val width by animateDpAsState(
                                        targetValue = if (isSelected) 22.dp else 6.dp,
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        label = "dot_width"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 3.dp)
                                            .height(6.dp)
                                            .width(width)
                                            .clip(CircleShape)
                                            .background(
                                                if (isSelected) Pink
                                                else Color.White.copy(0.3f)
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
