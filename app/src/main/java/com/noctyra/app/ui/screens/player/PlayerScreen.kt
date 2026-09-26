package com.noctyra.app.ui.screens.player

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.model.UiState
import com.noctyra.app.ui.components.ErrorScreen
import com.noctyra.app.ui.components.LoadingScreen
import com.noctyra.app.ui.theme.BackgroundDark

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.noctyra.app.ui.components.EpisodeCard
import com.noctyra.app.ui.components.SectionHeader

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
    val animeState by viewModel.animeState.collectAsState()

    LaunchedEffect(slug, season, episode) { viewModel.loadStream(slug, season, episode) }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        Row(modifier = Modifier.padding(8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar", tint = Color.White)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Episódio $episode",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        when (val s = streamState) {
            is UiState.Loading -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { LoadingScreen() }
            is UiState.Error -> Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) { ErrorScreen(message = s.message, onRetry = { viewModel.loadStream(slug, season, episode) }) }
            is UiState.Success -> VideoPlayer(stream = s.data)
        }

        Spacer(Modifier.height(16.dp))

        when (val a = animeState) {
            is UiState.Success -> {
                val detail = a.data
                val nextEps = detail.episodes.filter { 
                    it.season > season || (it.season == season && it.number > episode) 
                }
                
                if (nextEps.isNotEmpty()) {
                    SectionHeader("Próximos Episódios")
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(nextEps) { ep ->
                            EpisodeCard(
                                episode = ep,
                                animePoster = detail.anime.posterUrl,
                                onClick = { onEpisodeClick(ep.season, ep.number) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(stream: StreamResult) {
    val context = LocalContext.current

    if (stream.streamType == "embed") {
        AndroidView(
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webViewClient = android.webkit.WebViewClient()
                    webChromeClient = android.webkit.WebChromeClient()
                    loadUrl(stream.streamUrl)
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
    } else {
        val player = remember {
            ExoPlayer.Builder(context).build().apply {
                val dataSourceFactory = DefaultHttpDataSource.Factory().apply {
                    setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36")
                    if (stream.headers.isNotEmpty()) {
                        setDefaultRequestProperties(stream.headers)
                    }
                }

                val uri = Uri.parse(stream.streamUrl)
                val mediaSource = when (stream.streamType) {
                    "m3u8" -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
                    else -> ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(MediaItem.fromUri(uri))
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
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
    }
}
