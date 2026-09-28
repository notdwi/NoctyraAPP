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
 * O host é a fonte da verdade: guarda o estado de reprodução, repassa comandos dos convidados
 * e segura todo mundo (hold) enquanto alguém ainda está carregando o episódio.
 */
internal class PartyHost(private val nick: String, private val events: PartyEvents) : PartyEngine {
    private class Peer(val id: String, val conn: PartyConnection) {
        var nick = ""
        var joined = false
        var key: String? = null
        var ready = false
    }

    private val scope = confinedScope("party-host")
    private var server: ServerSocket? = null
    private val peers = LinkedHashMap<String, Peer>()
    private var nextId = 1

    private var state: PartyState? = null
    private var hostKey: String? = null
    private var hostReady = false
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
                checkHoldTimeout()
                if (ticks % 3 == 0) heartbeat()
            }
        }
        publishMembers()
        return true
    }

    override fun hostNow(): Long = now()

    override fun openedEpisode(state: PartyState) { scope.launch { applyEpisode(state, remote = false) } }
    override fun userPlayPause(playing: Boolean, positionMs: Long) { scope.launch { applyPlay(playing, positionMs) } }
    override fun userSeek(positionMs: Long) { scope.launch { applySeek(positionMs) } }
    override fun sendChat(text: String) { scope.launch { broadcastChat(HOST_ID, nick, text) } }

    override fun reportPlayer(key: String?, ready: Boolean, positionMs: Long) {
        scope.launch {
            val changed = key != hostKey || ready != hostReady
            hostKey = key
            hostReady = ready
            if (key != null) { hostPos = positionMs; hostPosAt = now() }
            if (ready && key == state?.key) skipped.remove(HOST_ID)
            if (changed) recomputeHold()
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
        peers[id] = Peer(id, conn)
    }

    private fun handle(id: String, msg: Msg) {
        val peer = peers[id] ?: return
        when (msg.t) {
            MsgType.HELLO -> {
                peer.nick = uniqueNick(sanitizeNick(msg.nick))
                peer.joined = true
                peer.conn.send(Msg(MsgType.WELCOME, id = id, nick = nick))
                publishMembers()
                state?.let { peer.conn.send(Msg(MsgType.STATE, state = it)) }
                broadcastChat(SYSTEM_ID, "", "${peer.nick} entrou na sala")
                recomputeHold()
            }
            MsgType.PING -> peer.conn.send(Msg(MsgType.PONG, ts = msg.ts, ts2 = now()))
            MsgType.STATUS -> {
                peer.key = msg.key
                peer.ready = msg.ready == true
                if (peer.ready && peer.key == state?.key) skipped.remove(id)
                recomputeHold()
            }
            MsgType.PLAY -> if (peer.joined) applyPlay(msg.playing == true, msg.pos ?: 0L)
            MsgType.SEEK -> if (peer.joined) applySeek(msg.pos ?: 0L)
            MsgType.EPISODE -> if (peer.joined) msg.state?.let { applyEpisode(it, remote = true) }
            MsgType.CHAT -> if (peer.joined) broadcastChat(id, peer.nick, sanitizeChat(msg.text))
            MsgType.BYE -> peer.conn.close()
        }
    }

    private fun removePeer(id: String) {
        val peer = peers.remove(id) ?: return
        if (!peer.joined) return
        publishMembers()
        broadcastChat(SYSTEM_ID, "", "${peer.nick} saiu da sala")
        recomputeHold()
    }

    private fun applyEpisode(incoming: PartyState, remote: Boolean) {
        val current = state
        if (current?.key == incoming.key) return
        skipped.clear()
        lastJump = now()
        state = incoming.copy(
            positionMs = 0L, anchorAt = now(), playing = true, hold = false,
            waitingFor = emptyList(), rev = (current?.rev ?: 0L) + 1
        )
        if (remote) state?.let(events::navigate)
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

    private fun waitingMembers(st: PartyState): List<Pair<String, String>> = buildList {
        if (!(hostKey == st.key && hostReady) && HOST_ID !in skipped) add(HOST_ID to nick)
        peers.values.filter { it.joined }.forEach {
            if (!(it.key == st.key && it.ready) && it.id !in skipped) add(it.id to it.nick)
        }
    }

    private fun recomputeHold(forceBroadcast: Boolean = false) {
        val st = state ?: return
        val waiting = waitingMembers(st).map { it.second }
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

    private fun checkHoldTimeout() {
        val st = state ?: return
        if (!st.hold || now() - holdSince < HOLD_TIMEOUT_MS) return
        waitingMembers(st).forEach { skipped.add(it.first) }
        broadcastChat(SYSTEM_ID, "", "Continuando sem ${st.waitingFor.joinToString()}")
        recomputeHold()
    }

    private fun heartbeat() {
        val st = state ?: return
        val t = now()
        val canAnchor = st.playing && !st.hold && hostKey == st.key && hostReady &&
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
            peers.values.filter { it.joined }.map { Member(it.id, it.nick) }
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
        val taken = peers.values.filter { it.joined }.map { it.nick.lowercase() }.toSet() + nick.lowercase()
        if (base.lowercase() !in taken) return base
        return (2..99).map { "$base $it" }.first { it.lowercase() !in taken }
    }

    companion object {
        const val HOST_ID = "host"
        private const val HOLD_TIMEOUT_MS = 12_000L
    }
}
