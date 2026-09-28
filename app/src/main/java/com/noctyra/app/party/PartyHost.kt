package com.noctyra.app.party

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * O host é a fonte da verdade: só ele escolhe o episódio, repassa play/pause/seek de todos
 * e segura a sala (hold) enquanto alguém que já estava assistindo carrega ou trava.
 * Quem entra no meio não segura ninguém: só pula para o ponto certo ao carregar.
 */
internal class PartyHost(private val nick: String, private val events: PartyEvents) : PartyEngine {
    private class Viewer(val id: String, var nick: String) {
        var key: String? = null
        var ready = false
        var expected = false
        var synced = false
        var leftAt = 0L
    }

    private class Peer(val conn: PartyConnection, val viewer: Viewer) {
        var joined = false
    }

    private val scope = confinedScope("party-host")
    private var server: ServerSocket? = null
    private val peers = LinkedHashMap<String, Peer>()
    private val self = Viewer(HOST_ID, nick)
    private var nextId = 1

    private var state: PartyState? = null
    private var hostPos = 0L
    private var hostPosAt = 0L
    private var holdSince = 0L
    private var lastJump = 0L
    private val skipped = HashSet<String>()

    fun start(): Boolean {
        val socket = runCatching {
            ServerSocket().apply {
                reuseAddress = true
                bind(InetSocketAddress(PARTY_PORT))
            }
        }.getOrNull() ?: return false
        server = socket
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                scope.launch { onSocket(client) }
            }
        }
        scope.launch {
            var ticks = 0
            while (isActive) {
                delay(1000)
                ticks++
                expireLeftViewers()
                checkHoldTimeout()
                if (ticks % 3 == 0) heartbeat()
            }
        }
        publishMembers()
        return true
    }

    override fun hostNow(): Long? = now()

    override fun openedEpisode(state: PartyState) { scope.launch { applyEpisode(state) } }
    override fun userPlayPause(playing: Boolean, positionMs: Long) { scope.launch { applyPlay(playing, positionMs) } }
    override fun userSeek(positionMs: Long) { scope.launch { applySeek(positionMs) } }
    override fun sendChat(text: String) { scope.launch { broadcastChat(HOST_ID, nick, text) } }

    override fun reportPlayer(key: String?, ready: Boolean, positionMs: Long) {
        scope.launch {
            if (key != null) { hostPos = positionMs; hostPosAt = now() }
            updateStatus(self, key, ready)
        }
    }

    override fun stop() {
        scope.launch {
            peers.values.forEach { it.conn.send(Msg(MsgType.BYE)); it.conn.close() }
            peers.clear()
            runCatching { server?.close() }
            scope.cancel()
        }
    }

    private fun viewers(): List<Viewer> = listOf(self) + peers.values.filter { it.joined }.map { it.viewer }

    private fun onSocket(socket: Socket) {
        socket.tcpNoDelay = true
        socket.soTimeout = 15_000
        val id = "p${nextId++}"
        val conn = PartyConnection(
            socket, scope,
            onMessage = { msg -> scope.launch { handle(id, msg) } },
            onClosed = { scope.launch { removePeer(id) } }
        )
        conn.start()
        if (peers.count { it.value.joined } >= MAX_GUESTS) {
            conn.send(Msg(MsgType.FULL))
            scope.launch { delay(300); conn.close() }
            return
        }
        peers[id] = Peer(conn, Viewer(id, ""))
    }

    private fun handle(id: String, msg: Msg) {
        val peer = peers[id] ?: return
        when (msg.t) {
            MsgType.HELLO -> {
                peer.viewer.nick = uniqueNick(sanitizeNick(msg.nick))
                peer.joined = true
                peer.conn.send(Msg(MsgType.WELCOME, id = id, nick = nick))
                publishMembers()
                state?.let { peer.conn.send(Msg(MsgType.STATE, state = it)) }
                broadcastChat(SYSTEM_ID, "", "${peer.viewer.nick} entrou na sala")
            }
            MsgType.PING -> peer.conn.send(Msg(MsgType.PONG, ts = msg.ts, ts2 = now()))
            MsgType.STATUS -> if (peer.joined) updateStatus(peer.viewer, msg.key, msg.ready == true)
            MsgType.PLAY -> if (peer.joined) applyPlay(msg.playing == true, msg.pos ?: 0L)
            MsgType.SEEK -> if (peer.joined) applySeek(msg.pos ?: 0L)
            MsgType.CHAT -> if (peer.joined) broadcastChat(id, peer.viewer.nick, sanitizeChat(msg.text))
            MsgType.BYE -> peer.conn.close()
        }
    }

    private fun removePeer(id: String) {
        val peer = peers.remove(id) ?: return
        if (!peer.joined) return
        publishMembers()
        broadcastChat(SYSTEM_ID, "", "${peer.viewer.nick} saiu da sala")
        recomputeHold()
    }

    private fun updateStatus(viewer: Viewer, key: String?, ready: Boolean) {
        val st = state
        val changed = key != viewer.key || ready != viewer.ready
        viewer.key = key
        viewer.ready = ready
        viewer.leftAt = if (key == null) now() else 0L
        when {
            key == null -> viewer.synced = false
            st != null && key == st.key && ready -> { viewer.expected = false; viewer.synced = true; skipped.remove(viewer.id) }
            st != null && key != st.key -> viewer.synced = false
        }
        if (changed) recomputeHold()
    }

    private fun applyEpisode(incoming: PartyState) {
        val current = state
        if (current?.key == incoming.key) {
            val richer = (incoming.poster.isNotEmpty() && incoming.poster != current.poster) ||
                (incoming.title.isNotEmpty() && incoming.title != current.title)
            if (richer) {
                state = current.copy(
                    title = incoming.title.ifEmpty { current.title },
                    poster = incoming.poster.ifEmpty { current.poster }
                )
                broadcastState()
            }
            return
        }
        skipped.clear()
        lastJump = now()
        viewers().forEach { v ->
            v.expected = v.key != null || v === self
            v.synced = false
        }
        state = incoming.copy(
            positionMs = 0L, anchorAt = now(), playing = true, hold = false,
            waitingFor = emptyList(), rev = (current?.rev ?: 0L) + 1
        )
        broadcastChat(SYSTEM_ID, "", "▶ ${incoming.title.ifEmpty { "Episódio" }} • T${incoming.season} EP ${incoming.episode}")
        recomputeHold(forceBroadcast = true)
    }

    private fun applyPlay(playing: Boolean, positionMs: Long) {
        val st = state ?: return
        lastJump = now()
        state = st.copy(playing = playing, positionMs = positionMs.coerceAtLeast(0), anchorAt = now(), rev = st.rev + 1)
        broadcastState()
    }

    private fun applySeek(positionMs: Long) {
        val st = state ?: return
        lastJump = now()
        state = st.copy(positionMs = positionMs.coerceAtLeast(0), anchorAt = now(), rev = st.rev + 1)
        broadcastState()
    }

    private fun isWaiting(v: Viewer, st: PartyState): Boolean {
        if (v.id in skipped) return false
        val loadingNew = v.expected && (v.key != st.key || !v.ready)
        val stalled = v.synced && v.key == st.key && !v.ready
        return loadingNew || stalled
    }

    private fun recomputeHold(forceBroadcast: Boolean = false) {
        val st = state ?: return
        val waiting = viewers().filter { isWaiting(it, st) }.map { it.nick }
        val t = now()
        val next = when {
            waiting.isNotEmpty() && !st.hold -> {
                holdSince = t
                st.copy(positionMs = st.positionAt(t), anchorAt = t, hold = true, waitingFor = waiting)
            }
            waiting.isNotEmpty() && waiting != st.waitingFor -> st.copy(waitingFor = waiting)
            waiting.isEmpty() && st.hold -> {
                lastJump = t
                st.copy(hold = false, waitingFor = emptyList(), anchorAt = t)
            }
            else -> st
        }
        if (next !== st) state = next.copy(rev = st.rev + 1)
        if (next !== st || forceBroadcast) broadcastState()
    }

    private fun expireLeftViewers() {
        val t = now()
        val left = viewers().filter { it.expected && it.key == null && it.leftAt > 0 && t - it.leftAt > LEFT_GRACE_MS }
        if (left.isEmpty()) return
        left.forEach { it.expected = false }
        recomputeHold()
    }

    private fun checkHoldTimeout() {
        val st = state ?: return
        if (!st.hold || now() - holdSince < HOLD_TIMEOUT_MS) return
        viewers().filter { isWaiting(it, st) }.forEach { skipped.add(it.id) }
        broadcastChat(SYSTEM_ID, "", "Continuando sem ${st.waitingFor.joinToString()}")
        recomputeHold()
    }

    private fun heartbeat() {
        val st = state ?: return
        val t = now()
        val canAnchor = st.playing && !st.hold && self.key == st.key && self.ready &&
            t - lastJump > 2_000 && t - hostPosAt < 1_500
        if (canAnchor) state = st.copy(positionMs = hostPos + (t - hostPosAt), anchorAt = t)
        broadcastState()
    }

    private fun broadcastState() {
        val st = state ?: return
        events.updateSession { it.copy(state = st) }
        val msg = Msg(MsgType.STATE, state = st)
        peers.values.forEach { if (it.joined) it.conn.send(msg) }
    }

    private fun publishMembers() {
        val members = listOf(Member(HOST_ID, nick, isHost = true)) +
            peers.values.filter { it.joined }.map { Member(it.viewer.id, it.viewer.nick) }
        events.updateSession { it.copy(members = members) }
        val msg = Msg(MsgType.MEMBERS, members = members)
        peers.values.forEach { if (it.joined) it.conn.send(msg) }
    }

    private fun broadcastChat(senderId: String, senderNick: String, text: String) {
        if (text.isEmpty()) return
        events.chat(senderId, senderNick, text)
        val msg = Msg(MsgType.CHAT, id = senderId, nick = senderNick, text = text)
        peers.values.forEach { if (it.joined) it.conn.send(msg) }
    }

    private fun uniqueNick(base: String): String {
        val taken = viewers().map { it.nick.lowercase() }.toSet()
        if (base.lowercase() !in taken) return base
        return (2..99).map { "$base $it" }.first { it.lowercase() !in taken }
    }

    companion object {
        const val HOST_ID = "host"
        private const val HOLD_TIMEOUT_MS = 12_000L
        private const val LEFT_GRACE_MS = 2_500L
    }
}
