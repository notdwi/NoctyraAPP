package com.noctyra.app.ui.screens.detail

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.model.displayTitle
import com.noctyra.app.data.model.isDubbed
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

@Composable
fun DetailScreen(
    slug: String,
    onBack: () -> Unit,
    onPlayEpisode: (String, Int, Int) -> Unit,
    viewModel: DetailViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedSeason by viewModel.selectedSeason.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    val askNotifications = {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    LaunchedEffect(slug) { viewModel.load(slug) }
    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(BackgroundDark)) {
        when (val s = state) {
            is UiState.Loading -> LoadingScreen()
            is UiState.Error -> ErrorScreen(s.message, onRetry = { viewModel.load(slug, force = true) }, modifier = Modifier.align(Alignment.Center))
            is UiState.Success -> DetailContent(
                detail = s.data,
                selectedSeason = selectedSeason,
                onSeasonSelected = viewModel::selectSeason,
                onPlayEpisode = onPlayEpisode,
                onDownload = { ep -> askNotifications(); viewModel.download(s.data, ep) },
                onDownloadSeason = { askNotifications(); viewModel.downloadSeason(s.data, selectedSeason) },
                onRemoveDownload = viewModel::removeDownload
            )
        }
        CircleIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack,
            modifier = Modifier.statusBarsPadding().padding(12.dp)
        )
        SnackbarHost(
            snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp)
        ) { data ->
            Snackbar(data, containerColor = SurfaceElevated, contentColor = TextPrimary, shape = RoundedCornerShape(14.dp))
        }
    }
}

@Composable
private fun DetailContent(
    detail: AnimeDetail,
    selectedSeason: Int,
    onSeasonSelected: (Int) -> Unit,
    onPlayEpisode: (String, Int, Int) -> Unit,
    onDownload: (Episode) -> Unit,
    onDownloadSeason: () -> Unit,
    onRemoveDownload: (String) -> Unit
) {
    val anime = detail.anime
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val history by LibraryStore.history.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.downloads.collectAsStateWithLifecycle()
    val isFavorite = anime.slug in favorites

    val episodes = remember(detail, selectedSeason) { detail.episodes.filter { it.season == selectedSeason } }
    val progressByKey = remember(history, anime.slug) {
        history.asSequence().filter { it.slug == anime.slug }.associateBy { it.key }
    }
    val last = remember(history, anime.slug) { history.firstOrNull { it.slug == anime.slug } }
    val playTarget: Pair<Int, Int>? = remember(last, detail) {
        when {
            last != null && !last.isFinished -> last.season to last.episode
            last != null -> detail.episodes.firstOrNull {
                it.season > last.season || (it.season == last.season && it.number > last.episode)
            }?.let { it.season to it.number } ?: (last.season to last.episode)
            else -> detail.episodes.firstOrNull()?.let { it.season to it.number }
        }
    }
    val playLabel = when {
        playTarget == null -> "Indisponível"
        anime.isMovie -> if (last != null) "Continuar" else "Assistir filme"
        last != null -> "Continuar T${playTarget.first} E${playTarget.second}"
        else -> "Assistir T${playTarget.first} E${playTarget.second}"
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Box(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(270.dp)) {
                NetImage(anime.posterUrl, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
                Box(Modifier.fillMaxSize().background(BottomFade))
                Box(Modifier.fillMaxWidth().height(120.dp).background(TopScrim))
            }
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 180.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                NetImage(
                    anime.posterUrl,
                    Modifier
                        .width(118.dp)
                        .aspectRatio(0.7f)
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp)),
                    contentDescription = anime.title
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
                    Text(anime.displayTitle, style = MaterialTheme.typography.headlineMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (anime.altTitle.isNotEmpty()) {
                        Text(anime.altTitle, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (anime.isDubbed) { DubBadge(text = "Dublado"); Spacer(Modifier.width(8.dp)) }
                        Text(
                            if (anime.isMovie) "Filme" else "${detail.episodes.size} Episódios",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary)
                        )
                        if (anime.rating.isNotEmpty()) {
                            Spacer(Modifier.width(10.dp))
                            Icon(Icons.Default.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(3.dp))
                            Text(anime.rating, style = MaterialTheme.typography.bodySmall.copy(color = TextPrimary))
                        }
                    }
                }
            }
            }
        }

        item(key = "actions") {
            Row(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PinkButton(
                    playLabel,
                    onClick = { playTarget?.let { onPlayEpisode(anime.slug, it.first, it.second) } },
                    icon = Icons.Default.PlayArrow,
                    enabled = playTarget != null,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(12.dp))
                GhostButton(
                    if (isFavorite) "Na lista" else "Minha Lista",
                    onClick = { LibraryStore.toggleFavorite(anime) },
                    icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    tint = if (isFavorite) Pink else TextPrimary
                )
            }
        }

        item(key = "info") {
            Column(Modifier.padding(top = 18.dp)) {
                if (anime.genres.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(anime.genres) { g -> Pill(g, color = Pink.copy(alpha = 0.12f), textColor = PinkLight) }
                    }
                }
                if (anime.synopsis.isNotEmpty()) SynopsisBlock(anime.synopsis)
            }
        }

        if (detail.seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 20.dp)
                ) {
                    items(detail.seasons) { season ->
                        SeasonChip(season, season == selectedSeason, onClick = { onSeasonSelected(season) })
                    }
                }
            }
        }

        item(key = "ep_header") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 12.dp, top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(4.dp).height(24.dp).clip(RoundedCornerShape(2.dp)).background(Pink))
                Spacer(Modifier.width(12.dp))
                Text(
                    if (anime.isMovie) "Filme" else "Episódios",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                if (episodes.size > 1) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onDownloadSeason)
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, null, tint = Pink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Baixar temporada", style = MaterialTheme.typography.labelMedium.copy(color = Pink, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }

        items(episodes, key = { "${it.season}/${it.number}" }, contentType = { "episode" }) { ep ->
            val id = DownloadCenter.downloadId(anime.slug, ep.season, ep.number)
            EpisodeRow(
                episode = ep,
                fallbackThumb = anime.posterUrl,
                progress = progressByKey[id],
                download = downloads[id],
                isCurrent = last?.season == ep.season && last.episode == ep.number,
                onClick = { onPlayEpisode(anime.slug, ep.season, ep.number) },
                onDownload = { onDownload(ep) },
                onDeleteDownload = { onRemoveDownload(id) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SynopsisBlock(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp)
            .clickable { expanded = !expanded }
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary.copy(alpha = 0.85f)),
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            if (expanded) "Mostrar menos" else "Ler mais",
            style = MaterialTheme.typography.labelMedium.copy(color = Pink, fontWeight = FontWeight.Bold, fontSize = 13.sp),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
