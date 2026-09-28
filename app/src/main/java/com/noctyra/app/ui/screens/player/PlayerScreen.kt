package com.noctyra.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.noctyra.app.data.local.AppSettings
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.local.WatchProgress
import com.noctyra.app.data.model.Episode
import com.noctyra.app.data.model.UiState
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.party.Party
import com.noctyra.app.party.partyKey
import com.noctyra.app.ui.components.*
import com.noctyra.app.ui.theme.BackgroundDark
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

internal data class EpisodeMeta(val title: String, val poster: String, val episodeTitle: String, val thumb: String)

@Composable
fun PlayerScreen(
    slug: String,
    season: Int,
    episode: Int,
    onBack: () -> Unit,
    onEpisodeClick: (Int, Int) -> Unit,
    onOpenParty: () -> Unit,
    viewModel: PlayerViewModel = viewModel()
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val session by Party.session.collectAsStateWithLifecycle()
    val chat by Party.chat.collectAsStateWithLifecycle()
    var isFullscreen by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(slug, season, episode) { viewModel.load(slug, season, episode) }
    FullscreenEffect(isFullscreen)
    BackHandler(enabled = isFullscreen) { isFullscreen = false }

    val ordered = remember(detail) { detail?.episodes?.sortedWith(compareBy({ it.season }, { it.number })).orEmpty() }
    val index = remember(ordered, season, episode) { ordered.indexOfFirst { it.season == season && it.number == episode } }
    val previousEpisode = ordered.getOrNull(index - 1)?.takeIf { index > 0 }
    val nextEpisode = if (index >= 0) ordered.getOrNull(index + 1) else null
    val currentEp = ordered.getOrNull(index)

    val offlineMeta = ((source as? UiState.Success)?.data as? PlaybackSource.Offline)?.meta
    val meta = EpisodeMeta(
        title = detail?.anime?.title ?: offlineMeta?.title ?: slug,
        poster = detail?.anime?.posterUrl ?: offlineMeta?.posterUrl.orEmpty(),
        episodeTitle = currentEp?.title ?: offlineMeta?.episodeTitle.orEmpty(),
        thumb = currentEp?.thumbUrl ?: offlineMeta?.thumbUrl.orEmpty()
    )
    val currentMeta by rememberUpdatedState(meta)
    val currentNext by rememberUpdatedState(nextEpisode)

    val key = partyKey(slug, season, episode)
    val inParty = session != null
    val partyState = session?.state?.takeIf { it.key == key }

    LaunchedEffect(key, inParty) {
        if (!inParty) return@LaunchedEffect
        withTimeoutOrNull(1_500) { viewModel.detail.first { it != null } }
        val m = currentMeta
        Party.openedEpisode(slug, m.title, m.poster, season, episode)
    }

    val goTo: (Episode?) -> Unit = { ep -> ep?.let { onEpisodeClick(it.season, it.number) } }

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
                            startPositionMs = remember(key) {
                                LibraryStore.progressFor(slug, season, episode)
                                    ?.takeIf { !it.isFinished && it.positionMs > 10_000 }?.positionMs ?: 0L
                            },
                            hasPrevious = previousEpisode != null,
                            hasNext = nextEpisode != null,
                            partyKey = if (inParty) key else null,
                            onPrevious = { goTo(previousEpisode) },
                            onNext = { goTo(nextEpisode) },
                            onUserPlayPause = { playing, pos -> if (inParty) Party.userPlayPause(playing, pos) },
                            onUserSeek = { pos -> if (inParty) Party.userSeek(pos) },
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
                                val canAdvance = session?.isHost ?: true
                                if (next != null && canAdvance && AppSettings.autoplayNext.value) goTo(next)
                            },
                            onNearEnd = {
                                currentNext?.let { n ->
                                    scope.launch { runCatching { AnimeRepository.getStream(slug, n.season, n.number) } }
                                }
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
            session?.let { s ->
                PartyChip(s.members.size.coerceAtLeast(1), onOpenParty, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }
            if (partyState?.hold == true) {
                PartyHoldBanner(partyState.waitingFor, Modifier.align(Alignment.TopCenter).padding(top = 56.dp))
            }
            if (isFullscreen && inParty) {
                FullscreenChatOverlay(chat, Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 72.dp))
            }
        }

        if (!isFullscreen) {
            PlayerBelowVideo(
                slug = slug,
                season = season,
                episode = episode,
                meta = meta,
                detail = detail,
                isOffline = offlineMeta != null,
                inParty = inParty,
                chat = chat,
                previous = previousEpisode,
                next = nextEpisode,
                onEpisode = goTo
            )
        }
    }
}

@Composable
private fun FullscreenEffect(isFullscreen: Boolean) {
    val activity = LocalContext.current as? Activity
    val view = LocalView.current
    DisposableEffect(isFullscreen) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
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
}
