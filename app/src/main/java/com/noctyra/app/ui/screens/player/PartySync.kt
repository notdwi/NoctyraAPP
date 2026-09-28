package com.noctyra.app.ui.screens.player

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.noctyra.app.party.Party
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val TICK_MS = 400L
private const val NOT_READY_GRACE_MS = 700L
private const val HARD_SEEK_PLAYING_MS = 1_500L
private const val HARD_SEEK_PAUSED_MS = 300L
private const val SOFT_DRIFT_MS = 250L

/**
 * Mantém o ExoPlayer local colado no estado da party: segue play/pause/hold, corrige desvios
 * grandes com seek e desvios pequenos variando a velocidade (sem o usuário perceber).
 */
@Composable
fun PartySyncEffect(player: ExoPlayer, key: String) {
    DisposableEffect(player, key) {
        onDispose {
            player.setPlaybackSpeed(1f)
            Party.reportPlayer(null, false, 0L)
        }
    }

    LaunchedEffect(player, key) {
        var reportedReady = false
        var notReadySince = 0L
        var speed = 1f

        while (true) {
            val t = SystemClock.elapsedRealtime()
            val ready = player.playbackState == Player.STATE_READY
            if (ready) {
                reportedReady = true
                notReadySince = 0L
            } else {
                if (notReadySince == 0L) notReadySince = t
                if (t - notReadySince > NOT_READY_GRACE_MS) reportedReady = false
            }
            Party.reportPlayer(key, reportedReady, player.currentPosition)

            val st = Party.session.value?.state
            if (st != null && st.key == key) {
                val shouldPlay = st.playing && !st.hold
                if (player.playWhenReady != shouldPlay) player.playWhenReady = shouldPlay

                var wanted = 1f
                if (ready) {
                    val target = Party.targetPosition(st)
                    val diff = target - player.currentPosition
                    val hardLimit = if (shouldPlay) HARD_SEEK_PLAYING_MS else HARD_SEEK_PAUSED_MS
                    when {
                        abs(diff) > hardLimit -> player.seekTo(target)
                        shouldPlay && abs(diff) > SOFT_DRIFT_MS -> wanted = if (diff > 0) 1.05f else 0.95f
                    }
                }
                if (wanted != speed) {
                    speed = wanted
                    player.setPlaybackSpeed(wanted)
                }
            }
            delay(TICK_MS)
        }
    }
}
