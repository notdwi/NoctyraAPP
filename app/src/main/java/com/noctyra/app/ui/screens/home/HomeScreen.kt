package com.noctyra.app.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.*

import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import coil.compose.AsyncImage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun HomeScreen(
    onAnimeClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val heroState by viewModel.heroState.collectAsState()
    val recentState by viewModel.recentState.collectAsState()
    val actionState by viewModel.actionState.collectAsState()
    val isekaiState by viewModel.isekaiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 16.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = com.noctyra.app.R.drawable.noc_logo),
                    contentDescription = "Noctyra Logo",
                    modifier = Modifier.height(32.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Noctyra",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        }

        item { HeroCarousel(state = heroState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { Spacer(Modifier.height(24.dp)) }

        item { SectionHeader("Lançamentos") }
        item { AnimeRow(state = recentState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { Spacer(Modifier.height(16.dp)) }

        item { SectionHeader("Ação") }
        item { AnimeRow(state = actionState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }

        item { Spacer(Modifier.height(16.dp)) }

        item { SectionHeader("Isekai") }
        item { AnimeRow(state = isekaiState, onAnimeClick = onAnimeClick, onRetry = viewModel::loadHomeData) }
    }
}

@Composable
private fun AnimeRow(
    state: UiState<List<Anime>>,
    onAnimeClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) { LoadingScreen() }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxWidth().height(220.dp)) {
                ErrorScreen(message = state.message, onRetry = onRetry)
            }
        }
        is UiState.Success -> {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.data) { anime ->
                    AnimeCard(anime = anime, onClick = { onAnimeClick(anime.slug) })
                }
            }
        }
    }
}
@Composable
fun HeroCarousel(
    state: UiState<List<Anime>>,
    onAnimeClick: (String) -> Unit,
    onRetry: () -> Unit
) {
    when (state) {
        is UiState.Loading -> Box(modifier = Modifier.fillMaxWidth().height(250.dp)) { LoadingScreen() }
        is UiState.Error -> Box(modifier = Modifier.fillMaxWidth().height(250.dp)) { ErrorScreen(message = state.message, onRetry = onRetry) }
        is UiState.Success -> {
            val animes = state.data
            if (animes.isEmpty()) return
            val pagerState = rememberPagerState(pageCount = { animes.size })
            
            val isDragged by pagerState.interactionSource.collectIsDraggedAsState()
            androidx.compose.runtime.LaunchedEffect(isDragged) {
                if (!isDragged) {
                    while (true) {
                        kotlinx.coroutines.delay(4000)
                        val nextPage = (pagerState.currentPage + 1) % animes.size
                        pagerState.animateScrollToPage(nextPage)
                    }
                }
            }
            
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth().height(250.dp)
            ) { page ->
                val anime = animes[page]
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onAnimeClick(anime.slug) }
                ) {
                    AsyncImage(
                        model = anime.posterUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                    startY = 200f
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier
                            .align(androidx.compose.ui.Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = anime.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = { onAnimeClick(anime.slug) },
                            colors = ButtonDefaults.buttonColors(containerColor = com.noctyra.app.ui.theme.Purple),
                            modifier = Modifier.height(36.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)
                        ) {
                            Text("Assistir", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth(0.6f)
                        ) {
                            repeat(animes.size) { iteration ->
                                val color = if (pagerState.currentPage == iteration) com.noctyra.app.ui.theme.Purple else Color.White.copy(alpha = 0.3f)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
