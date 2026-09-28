package com.noctyra.app.party

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors

internal interface PartyEvents {
    fun updateSession(transform: (PartySession) -> PartySession)
    fun chat(senderId: String, nick: String, text: String)
    fun navigate(state: PartyState)
    fun ended(reason: String)
}

internal interface PartyEngine {
    fun hostNow(): Long
    fun openedEpisode(state: PartyState)
    fun userPlayPause(playing: Boolean, positionMs: Long)
    fun userSeek(positionMs: Long)
    fun reportPlayer(key: String?, ready: Boolean, positionMs: Long)
    fun sendChat(text: String)
    fun stop()
}

internal fun now(): Long = SystemClock.elapsedRealtime()

internal fun confinedScope(name: String): CoroutineScope {
    val dispatcher = Executors.newSingleThreadExecutor { r -> Thread(r, name).apply { isDaemon = true } }
        .asCoroutineDispatcher()
    return CoroutineScope(SupervisorJob() + dispatcher)
}

internal fun PartyState.positionAt(hostNow: Long): Long =
    if (playing && !hold) positionMs + (hostNow - anchorAt).coerceAtLeast(0) else positionMs
