package com.noctyra.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.BackgroundDark
import com.noctyra.app.ui.theme.Purple

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onPlayEpisode: (String, Int, Int) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    val selectedSeason by viewModel.selectedSeason.collectAsState()

    LaunchedEffect(slug) { viewModel.load(slug) }

    when (val s = state) {
        is UiState.Loading -> LoadingScreen()
        is UiState.Error -> ErrorScreen(message = s.message, onRetry = { viewModel.load(slug) })
        is UiState.Success -> DetailContent(
            detail = s.data,
            selectedSeason = selectedSeason,
            onSeasonSelected = viewModel::selectSeason,
            onBack = onBack,
            onPlayEpisode = onPlayEpisode
        )
    }
}

@Composable
private fun DetailContent(
    detail: AnimeDetail,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onPlayEpisode: (String, Int, Int) -> Unit
) {
    val anime = detail.anime
    val filteredEps = detail.episodes.filter { it.season == selectedSeason }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { BannerSection(anime.posterUrl, onBack) }
        item { HeaderSection(anime) }
        item { StatsRow(anime) }
        item { TagsRow(anime) }
        item { GenresRow(anime.genres) }
        item {
            PlayButton(
                text = "Continuar Assistindo EP 1",
                onClick = { onPlayEpisode(anime.slug, selectedSeason, 1) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(8.dp))
        }
        item { OverviewSection(anime.synopsis) }

        if (detail.seasons.size > 1) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(detail.seasons) { season ->
                        SeasonTab(
                            season = season,
                            isSelected = season == selectedSeason,
                            onClick = { onSeasonSelected(season) }
                        )
                    }
                }
            }
        }

        item { SectionHeader("Episódios") }
        items(filteredEps) { ep ->
            EpisodeCard(
                episode = ep,
                animePoster = anime.posterUrl,
                onClick = { onPlayEpisode(anime.slug, ep.season, ep.number) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun BannerSection(posterUrl: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
        if (posterUrl.isNotEmpty()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, BackgroundDark),
                    startY = 100f
                )
            )
        )
        IconButton(onClick = onBack, modifier = Modifier.padding(16.dp).statusBarsPadding()) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
        }
    }
}

@Composable
private fun HeaderSection(anime: com.noctyra.app.data.model.Anime) {
    Row(modifier = Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp)) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(100.dp).height(140.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.padding(top = 40.dp)) {
            Text(anime.title, style = MaterialTheme.typography.headlineSmall)
            if (anime.altTitle.isNotEmpty()) {
                Text(anime.altTitle, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun StatsRow(anime: com.noctyra.app.data.model.Anime) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(value = anime.rating.ifEmpty { "N/A" }, label = "Nota")
        StatItem(value = "${anime.totalEps}", label = "Episódios")
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun TagsRow(anime: com.noctyra.app.data.model.Anime) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoChip(if (anime.isMovie) "FILME" else "SÉRIE")
        InfoChip("${anime.totalEps} EP")
    }
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GenresRow(genres: List<String>) {
    if (genres.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(genres) { genre -> GenreChip(text = genre) }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun OverviewSection(synopsis: String) {
    if (synopsis.isEmpty()) return
    SectionHeader("Sinopse")
    Text(
        text = synopsis,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
    Spacer(Modifier.height(16.dp))
}
