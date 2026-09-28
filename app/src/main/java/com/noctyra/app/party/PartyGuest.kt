package com.noctyra.app.party

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetSocketAddress
import java.net.Socket

internal class PartyGuest(private val nick: String, private val events: PartyEvents) : PartyEngine {
    private val scope = confinedScope("party-guest")
    private var conn: PartyConnection? = null
    private val welcome = CompletableDeferred<Msg?>()

    @Volatile private var clockOffset = 0L
    @Volatile private var clockReady = false
    private var bestRtt = 10_000L
    private var state: PartyState? = null
    private var ignoreUntilRev = -1L
    private var ignoreDeadline = 0L
    private var lastStatus: Pair<String?, Boolean>? = null
    @Volatile private var leaving = false

    /** Conecta e espera o "welcome" do host. Retorna null em caso de sucesso ou a mensagem de erro. */
    suspend fun connect(ip: String): String? {
        val socket = withContext(Dispatchers.IO) {
            runCatching {
                Socket().apply {
                    tcpNoDelay = true
                    soTimeout = 15_000
                    connect(InetSocketAddress(ip, PARTY_PORT), 6_000)
                }
            }.getOrNull()
        } ?: return "Não foi possível encontrar a sala. Vocês estão na mesma rede (Wi‑Fi ou ZeroTier)?"

        val connection = PartyConnection(
            socket, scope,
            onMessage = { msg -> scope.launch { handle(msg) } },
            onClosed = { scope.launch { onClosed() } }
        )
        conn = connection
        connection.start()
        connection.send(Msg(MsgType.HELLO, nick = nick, ts = now()))

        val reply = withTimeoutOrNull(6_000) { welcome.await() }
        return when {
            reply == null -> { leaving = true; connection.close(); "A sala não respondeu. Tente de novo." }
            reply.t == MsgType.FULL -> { leaving = true; connection.close(); "A sala está cheia." }
            else -> { startPing(); null }
        }
    }

    override fun hostNow(): Long? = if (clockReady) now() + clockOffset else null

    private fun hostClock(): Long = now() + clockOffset

    override fun openedEpisode(state: PartyState) = Unit

    override fun userPlayPause(playing: Boolean, positionMs: Long) {
        scope.launch {
            state?.let { optimistic(it.copy(playing = playing, positionMs = positionMs, anchorAt = hostClock())) }
            conn?.send(Msg(MsgType.PLAY, playing = playing, pos = positionMs))
        }
    }

    override fun userSeek(positionMs: Long) {
        scope.launch {
            state?.let { optimistic(it.copy(positionMs = positionMs, anchorAt = hostClock())) }
            conn?.send(Msg(MsgType.SEEK, pos = positionMs))
        }
    }

    override fun reportPlayer(key: String?, ready: Boolean, positionMs: Long) {
        scope.launch {
            val status = key to ready
            if (status == lastStatus) return@launch
            lastStatus = status
            conn?.send(Msg(MsgType.STATUS, key = key, ready = ready))
        }
    }

    override fun sendChat(text: String) {
        scope.launch { conn?.send(Msg(MsgType.CHAT, text = text)) }
    }

    override fun stop() {
        scope.launch {
            leaving = true
            conn?.send(Msg(MsgType.BYE))
            delay(150)
            conn?.close()
            scope.cancel()
        }
    }

    private fun handle(msg: Msg) {
        when (msg.t) {
            MsgType.WELCOME -> {
                events.updateSession { it.copy(myId = msg.id.orEmpty(), hostNick = msg.nick.orEmpty(), connected = true) }
                welcome.complete(msg)
            }
            MsgType.FULL -> welcome.complete(msg)
            MsgType.MEMBERS -> events.updateSession { it.copy(members = msg.members.orEmpty()) }
            MsgType.STATE -> msg.state?.let(::onState)
            MsgType.PONG -> onPong(msg)
            MsgType.CHAT -> events.chat(msg.id.orEmpty(), msg.nick.orEmpty(), msg.text.orEmpty())
            MsgType.BYE -> { leaving = true; events.ended("O host encerrou a sala") }
        }
    }

    private fun onState(incoming: PartyState) {
        if (incoming.rev <= ignoreUntilRev && now() < ignoreDeadline) return
        val previousKey = state?.key
        state = incoming
        events.updateSession { it.copy(state = incoming) }
        if (previousKey != null && previousKey != incoming.key) events.navigate(incoming)
    }

    /** Aplica o comando local na hora e ignora estados antigos do host até ele confirmar. */
    private fun optimistic(next: PartyState) {
        ignoreUntilRev = state?.rev ?: -1L
        ignoreDeadline = now() + 1_500
        state = next
        events.updateSession { it.copy(state = next) }
    }

    private fun onPong(msg: Msg) {
        val sentAt = msg.ts ?: return
        val hostAt = msg.ts2 ?: return
        val t = now()
        val rtt = t - sentAt
        if (rtt < 0) return
        val offset = hostAt + rtt / 2 - t
        if (rtt <= bestRtt + 30) {
            bestRtt = minOf(bestRtt, rtt)
            clockOffset = if (!clockReady) offset else (clockOffset * 3 + offset) / 4
            clockReady = true
        }
    }

    private fun startPing() {
        scope.launch {
            repeat(5) {
                conn?.send(Msg(MsgType.PING, ts = now()))
                delay(250)
            }
            while (isActive) {
                conn?.send(Msg(MsgType.PING, ts = now()))
                delay(2_000)
                bestRtt += 5
            }
        }
    }

    private fun onClosed() {
        if (!welcome.isCompleted) welcome.complete(null)
        if (!leaving) events.ended("Conexão com a sala perdida")
    }
}
