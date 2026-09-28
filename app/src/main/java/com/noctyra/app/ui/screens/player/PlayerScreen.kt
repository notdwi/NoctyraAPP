package com.noctyra.app.ui.screens.player

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import com.noctyra.app.data.local.AppSettings
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.local.WatchProgress
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.*
import kotlinx.coroutines.delay

private data class EpisodeMeta(val title: String, val poster: String, val episodeTitle: String, val thumb: String)

@Composable
fun PlayerScreen(
    slug: String,
    season: Int,
    episode: Int,
    onBack: () -> Unit,
    onEpisodeClick: (Int, Int) -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val favorites by LibraryStore.favoriteSlugs.collectAsStateWithLifecycle()
    val history by LibraryStore.history.collectAsStateWithLifecycle()
    val downloads by DownloadCenter.downloads.collectAsStateWithLifecycle()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    val view = LocalView.current

    LaunchedEffect(slug, season, episode) { viewModel.load(slug, season, episode) }

    DisposableEffect(isFullscreen) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (isFullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val seasonEpisodes = remember(detail, season) { detail?.episodes?.filter { it.season == season }.orEmpty() }
    val nextEpisode = remember(detail, season, episode) {
        detail?.episodes?.firstOrNull { it.season > season || (it.season == season && it.number > episode) }
    }
    val currentEp = remember(detail, season, episode) { detail?.episodes?.firstOrNull { it.season == season && it.number == episode } }

    val offlineMeta = (source as? UiState.Success)?.data.let { it as? PlaybackSource.Offline }?.meta
    val meta = EpisodeMeta(
        title = detail?.anime?.title ?: offlineMeta?.title ?: slug,
        poster = detail?.anime?.posterUrl ?: offlineMeta?.posterUrl.orEmpty(),
        episodeTitle = currentEp?.title ?: offlineMeta?.episodeTitle.orEmpty(),
        thumb = currentEp?.thumbUrl ?: offlineMeta?.thumbUrl.orEmpty()
    )
    val currentMeta by rememberUpdatedState(meta)
    val currentNext by rememberUpdatedState(nextEpisode)

    Column(
        Modifier
            .fillMaxSize()
            .background(if (isFullscreen) Color.Black else BackgroundDark)
    ) {
        Box(
            modifier = if (isFullscreen) Modifier.fillMaxSize()
                       else Modifier.statusBarsPadding().fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)
        ) {
            when (val s = source) {
                is UiState.Loading -> LoadingScreen()
                is UiState.Error -> ErrorScreen(s.message, onRetry = { viewModel.load(slug, season, episode, force = true) })
                is UiState.Success -> {
                    val data = s.data
                    if (data is PlaybackSource.Online && data.stream.streamType == "embed") {
                        EmbedPlayer(data.stream.streamUrl)
                    } else {
                        VideoPlayer(
                            source = data,
                            startPositionMs = remember(slug, season, episode) {
                                LibraryStore.progressFor(slug, season, episode)
                                    ?.takeIf { !it.isFinished && it.positionMs > 10_000 }?.positionMs ?: 0L
                            },
                            onProgress = { pos, dur ->
                                val m = currentMeta
                                LibraryStore.saveProgress(
                                    WatchProgress(
                                        slug = slug, title = m.title, posterUrl = m.poster,
                                        season = season, episode = episode, episodeTitle = m.episodeTitle,
                                        thumbUrl = m.thumb, positionMs = pos, durationMs = dur
                                    )
                                )
                            },
                            onEnded = {
                                val next = currentNext
                                if (next != null && AppSettings.autoplayNext.value) onEpisodeClick(next.season, next.number)
                            },
                            onFullscreenToggle = { isFullscreen = !isFullscreen }
                        )
                    }
                }
            }
            if (!isFullscreen) {
                CircleIconButton(
                    Icons.AutoMirrored.Filled.ArrowBack, "Voltar", onBack,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp), size = 38.dp
                )
            }
        }

        if (!isFullscreen) {
            val progressByKey = remember(history, slug) { history.asSequence().filter { it.slug == slug }.associateBy { it.key } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "info") {
                    EpisodeInfo(
                        title = meta.title,
                        season = season,
                        episode = episode,
                        episodeTitle = meta.episodeTitle,
                        isOffline = offlineMeta != null,
                        isFavorite = slug in favorites,
                        detail = detail,
                        next = nextEpisode,
                        onNext = { nextEpisode?.let { onEpisodeClick(it.season, it.number) } }
                    )
                }
                if (seasonEpisodes.isNotEmpty()) {
                    item(key = "header") { SectionHeader("Episódios • Temporada $season") }
                    items(seasonEpisodes, key = { "${it.season}/${it.number}" }, contentType = { "episode" }) { ep ->
                        val id = DownloadCenter.downloadId(slug, ep.season, ep.number)
                        EpisodeRow(
                            episode = ep,
                            fallbackThumb = detail?.anime?.posterUrl.orEmpty(),
                            progress = progressByKey[id],
                            download = downloads[id],
                            isCurrent = ep.number == episode,
                            onClick = { if (ep.number != episode) onEpisodeClick(ep.season, ep.number) },
                            onDownload = null,
                            onDeleteDownload = null,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeInfo(
    title: String,
    season: Int,
    episode: Int,
    episodeTitle: String,
    isOffline: Boolean,
    isFavorite: Boolean,
    detail: AnimeDetail?,
    next: Episode?,
    onNext: () -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
        Text(cleanTitle(title), style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Pill("T$season • EP $episode", color = Pink.copy(alpha = 0.16f), textColor = PinkLight)
            if (isOffline) {
                Spacer(Modifier.width(8.dp))
                Pill("Offline", color = SuccessGreen.copy(alpha = 0.14f), textColor = SuccessGreen)
            }
        }
        if (episodeTitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(episodeTitle, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (next != null) {
                PinkButton("Próximo: EP ${next.number}", onNext, icon = Icons.Default.SkipNext, modifier = Modifier.weight(1f), height = 46.dp)
                Spacer(Modifier.width(12.dp))
            }
            if (detail != null) {
                GhostButton(
                    if (isFavorite) "Na lista" else "Minha Lista",
                    onClick = { LibraryStore.toggleFavorite(detail.anime) },
                    icon = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    tint = if (isFavorite) Pink else TextPrimary,
                    height = 46.dp,
                    modifier = if (next == null) Modifier.fillMaxWidth() else Modifier
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(
    source: PlaybackSource,
    startPositionMs: Long,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onFullscreenToggle: () -> Unit
) {
    val context = LocalContext.current
    val lite = rememberLiteMode()
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnEnded by rememberUpdatedState(onEnded)

    val player = remember(source) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (lite) 10_000 else 20_000, if (lite) 25_000 else 50_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(buildMediaSource(source))
            if (startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = true
        }
    }

    fun report() {
        val dur = player.duration
        if (dur > 0 && player.currentPosition > 0) currentOnProgress(player.currentPosition, dur)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val dur = player.duration
                    if (dur > 0) currentOnProgress(dur, dur)
                    currentOnEnded()
                }
            }
        }
        player.addListener(listener)
        onDispose {
            report()
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(player) {
        while (true) {
            delay(5_000)
            if (player.isPlaying) report()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { report(); player.pause() }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = true
                keepScreenOn = true
                setShowNextButton(false)
                setShowPreviousButton(false)
                setFullscreenButtonClickListener { onFullscreenToggle() }
            }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )
}

@OptIn(UnstableApi::class)
private fun buildMediaSource(source: PlaybackSource): MediaSource = when (source) {
    is PlaybackSource.Offline ->
        DefaultMediaSourceFactory(DownloadCenter.offlineDataSourceFactory()).createMediaSource(source.item)
    is PlaybackSource.Online -> {
        val stream = source.stream
        val factory = OkHttpDataSource.Factory(HttpClient.media)
            .setUserAgent(HttpClient.MOBILE_UA)
            .setDefaultRequestProperties(stream.headers)
        val item = MediaItem.fromUri(Uri.parse(stream.streamUrl))
        if (stream.streamType == "m3u8") HlsMediaSource.Factory(factory).createMediaSource(item)
        else ProgressiveMediaSource.Factory(factory).createMediaSource(item)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EmbedPlayer(url: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.userAgentString =
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl(url)
            }
        },
        onRelease = { it.stopLoading(); it.destroy() },
        modifier = Modifier.fillMaxSize()
    )
}
