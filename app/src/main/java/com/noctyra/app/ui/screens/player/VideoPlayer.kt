package com.noctyra.app.ui.screens.player

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.download.DownloadCenter
import com.noctyra.app.ui.components.rememberLiteMode
import kotlinx.coroutines.delay

private const val PREFETCH_BEFORE_END_MS = 150_000L

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    source: PlaybackSource,
    startPositionMs: Long,
    hasPrevious: Boolean,
    hasNext: Boolean,
    partyKey: String?,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onUserPlayPause: (Boolean, Long) -> Unit,
    onUserSeek: (Long) -> Unit,
    onProgress: (Long, Long) -> Unit,
    onEnded: () -> Unit,
    onNearEnd: () -> Unit,
    onPlaybackInfo: (Boolean, Long, Long) -> Unit,
    onFullscreenToggle: () -> Unit
) {
    val context = LocalContext.current
    val lite = rememberLiteMode()
    val currentOnProgress by rememberUpdatedState(onProgress)
    val currentOnEnded by rememberUpdatedState(onEnded)
    val currentOnNearEnd by rememberUpdatedState(onNearEnd)
    val currentOnInfo by rememberUpdatedState(onPlaybackInfo)
    val currentActions by rememberUpdatedState(
        object : PlayerActions {
            override fun onPrevious() = onPrevious()
            override fun onNext() = onNext()
            override fun onUserPlayPause(playing: Boolean, positionMs: Long) = onUserPlayPause(playing, positionMs)
            override fun onUserSeek(positionMs: Long) = onUserSeek(positionMs)
        }
    )

    val player = remember(source) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(if (lite) 10_000 else 20_000, if (lite) 25_000 else 50_000, 1_500, 3_000)
            .build()
        ExoPlayer.Builder(context).setLoadControl(loadControl).build().apply {
            setMediaSource(buildMediaSource(source))
            if (partyKey == null && startPositionMs > 0) seekTo(startPositionMs)
            prepare()
            playWhenReady = partyKey == null
        }
    }
    val controls = remember(player, hasPrevious, hasNext) {
        ControlPlayer(player, hasPrevious, hasNext, object : PlayerActions {
            override fun onPrevious() = currentActions.onPrevious()
            override fun onNext() = currentActions.onNext()
            override fun onUserPlayPause(playing: Boolean, positionMs: Long) = currentActions.onUserPlayPause(playing, positionMs)
            override fun onUserSeek(positionMs: Long) = currentActions.onUserSeek(positionMs)
        })
    }

    fun report() {
        val dur = player.duration
        if (dur > 0 && player.currentPosition > 0) currentOnProgress(player.currentPosition, dur)
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                currentOnInfo(isPlaying, player.currentPosition, player.duration.coerceAtLeast(0))
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
            ) {
                currentOnInfo(player.isPlaying, newPosition.positionMs, player.duration.coerceAtLeast(0))
            }

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
        var nearEndSent = false
        while (true) {
            delay(5_000)
            if (player.isPlaying) report()
            val dur = player.duration
            if (!nearEndSent && dur > 0 && dur - player.currentPosition < PREFETCH_BEFORE_END_MS) {
                nearEndSent = true
                currentOnNearEnd()
            }
        }
    }

    if (partyKey != null) PartySyncEffect(player, partyKey)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player, partyKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                report()
                if (partyKey == null) player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = true
                keepScreenOn = true
                setFullscreenButtonClickListener { onFullscreenToggle() }
            }
        },
        update = {
            it.setShowPreviousButton(hasPrevious)
            it.setShowNextButton(hasNext)
            if (it.player !== controls) it.player = controls
        },
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
fun EmbedPlayer(url: String) {
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
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.javaScriptCanOpenWindowsAutomatically = false
                settings.setSupportMultipleWindows(false)
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
