package com.noctyra.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*

@Composable
fun PlayerScreen(
    slug: String,
    season: Int,
    episode: Int,
    onBack: () -> Unit,
    onEpisodeClick: (Int, Int) -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val streamState by viewModel.streamState.collectAsState()
    val animeState  by viewModel.animeState.collectAsState()
    var isFullscreen by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(slug, season, episode) { viewModel.loadStream(slug, season, episode) }

    // Lock/unlock orientation based on fullscreen
    DisposableEffect(isFullscreen) {
        activity?.requestedOrientation = if (isFullscreen)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {

        // ── Video area ──────────────────────────────────────────────────────
        Box(
            modifier = if (isFullscreen) Modifier.fillMaxSize()
                       else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        ) {
            when (val s = streamState) {
                is UiState.Loading ->
                    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        LoadingScreen()
                    }
                is UiState.Error ->
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        ErrorScreen(message = s.message, onRetry = { viewModel.loadStream(slug, season, episode) })
                    }
                is UiState.Success ->
                    VideoPlayer(stream = s.data, isFullscreen = isFullscreen)
            }

            // Back button (always visible)
            if (!isFullscreen) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp)
                        .size(40.dp).clip(CircleShape).background(Color.Black.copy(0.4f))
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = Color.White,
                        modifier = Modifier.size(20.dp))
                }
            }

            // Fullscreen toggle
            IconButton(
                onClick = { isFullscreen = !isFullscreen },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    .size(40.dp).clip(CircleShape).background(Color.Black.copy(0.4f))
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    "Tela cheia", tint = Color.White, modifier = Modifier.size(20.dp)
                )
            }
        }

        // ── Info + episodes (only when not fullscreen) ──────────────────────
        if (!isFullscreen) {
            val animeDetail = (animeState as? UiState.Success)?.data

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Anime info card
                if (animeDetail != null) {
                    item { AnimeInfoCard(detail = animeDetail, episode = episode, season = season) }
                }

                // Episode selector header
                item {
                    SectionHeader(
                        title = "Episódios",
                        showSeeAll = animeDetail != null,
                        onSeeAll = null
                    )
                }

                // Other episodes
                val otherEps = animeDetail?.episodes?.filter {
                    it.season > season || (it.season == season && it.number > episode)
                } ?: emptyList()

                if (otherEps.isEmpty() && animeDetail != null) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Sem mais episódios", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                } else {
                    items(otherEps) { ep ->
                        EpisodeCard(
                            episode = ep,
                            animePoster = animeDetail?.anime?.posterUrl ?: "",
                            onClick = { onEpisodeClick(ep.season, ep.number) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun AnimeInfoCard(detail: AnimeDetail, episode: Int, season: Int) {
    val anime = detail.anime
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceCard)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(72.dp).height(100.dp).clip(RoundedCornerShape(10.dp))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(anime.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (anime.rating.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⭐", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.width(3.dp))
                        Text(anime.rating, style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Pink.copy(0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "EP $episode",
                        style = MaterialTheme.typography.labelMedium,
                        color = PinkLight
                    )
                }
            }
            if (anime.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    anime.genres.take(2).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(stream: StreamResult, isFullscreen: Boolean) {
    val context = LocalContext.current

    if (stream.streamType == "embed") {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    // Spoof a real Chrome desktop browser to bypass Cloudflare/anti-WebView blocks
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.allowContentAccess = true
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(stream.streamUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                    if (stream.headers.isNotEmpty()) setDefaultRequestProperties(stream.headers)
                }
                val uri = Uri.parse(stream.streamUrl)
                val mediaSource = when (stream.streamType) {
                    "m3u8" -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
                    else   -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
                }
                setMediaSource(mediaSource)
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(Unit) { onDispose { player.release() } }

        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
