package com.noctyra.app.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.widget.Toast
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
import com.noctyra.app.discord.DiscordPresence
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
    DisposableEffect(slug, season, episode) { onDispose { DiscordPresence.clear() } }
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
    val isHost = session?.isHost ?: true
    val partyState = session?.state?.takeIf { it.key == key }
    val followsParty = inParty && (isHost || partyState != null)
    val context = LocalContext.current

    LaunchedEffect(key, inParty, isHost) {
        if (!inParty || !isHost) return@LaunchedEffect
        val quick = viewModel.detail.value ?: withTimeoutOrNull(3_000) { viewModel.detail.first { it != null } }
        Party.openedEpisode(slug, quick?.anime?.title ?: currentMeta.title, quick?.anime?.posterUrl ?: currentMeta.poster, season, episode)
        if (quick != null) return@LaunchedEffect
        val late = withTimeoutOrNull(10_000) { viewModel.detail.first { it != null } } ?: return@LaunchedEffect
        Party.openedEpisode(slug, late.anime.title, late.anime.posterUrl, season, episode)
    }

    val goTo: (Episode?) -> Unit = { ep ->
        when {
            ep == null -> Unit
            inParty && !isHost -> Toast.makeText(context, "Na party, só o host escolhe o episódio", Toast.LENGTH_SHORT).show()
            else -> onEpisodeClick(ep.season, ep.number)
        }
    }
    val canNavigate = !inParty || isHost

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
                            hasPrevious = canNavigate && previousEpisode != null,
                            hasNext = canNavigate && nextEpisode != null,
                            partyKey = if (followsParty) key else null,
                            onPrevious = { goTo(previousEpisode) },
                            onNext = { goTo(nextEpisode) },
                            onUserPlayPause = { playing, pos -> if (followsParty) Party.userPlayPause(playing, pos) },
                            onUserSeek = { pos -> if (followsParty) Party.userSeek(pos) },
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
                                if (next != null && isHost && AppSettings.autoplayNext.value) goTo(next)
                            },
                            onNearEnd = {
                                currentNext?.let { n ->
                                    scope.launch { runCatching { AnimeRepository.getStream(slug, n.season, n.number) } }
                                }
                            },
                            onPlaybackInfo = { playing, pos, dur ->
                                val m = currentMeta
                                DiscordPresence.watching(m.title, season, episode, m.episodeTitle, m.poster, pos, dur, playing)
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
                previous = previousEpisode.takeIf { canNavigate },
                next = nextEpisode.takeIf { canNavigate },
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
