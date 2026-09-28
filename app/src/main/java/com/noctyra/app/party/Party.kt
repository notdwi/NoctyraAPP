package com.noctyra.app.party

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.atomic.AtomicLong

object Party {
    private const val MAX_CHAT = 150

    private var engine: PartyEngine? = null
    private val seq = AtomicLong()

    private val _session = MutableStateFlow<PartySession?>(null)
    val session: StateFlow<PartySession?> = _session.asStateFlow()

    private val _chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chat: StateFlow<List<ChatMessage>> = _chat.asStateFlow()

    private val _navigate = MutableSharedFlow<PartyState>(extraBufferCapacity = 4)
    val navigate: SharedFlow<PartyState> = _navigate.asSharedFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    val isActive: Boolean get() = engine != null

    /** Fora da party qualquer um escolhe; dentro, só o host. */
    val canChooseEpisode: Boolean get() = _session.value?.isHost ?: true

    private val events = object : PartyEvents {
        override fun updateSession(transform: (PartySession) -> PartySession) {
            _session.update { current -> current?.let(transform) }
        }

        override fun chat(senderId: String, nick: String, text: String) {
            val myId = _session.value?.myId
            val msg = ChatMessage(seq.incrementAndGet(), senderId, nick, text, mine = senderId == myId)
            _chat.update { (it + msg).takeLast(MAX_CHAT) }
        }

        override fun navigate(state: PartyState) {
            _navigate.tryEmit(state)
        }

        override fun ended(reason: String) {
            reset()
            _notice.value = reason
        }
    }

    fun host(nick: String): String? {
        leave()
        val clean = sanitizeNick(nick)
        _session.value = PartySession(PartyRole.HOST, PartyHost.HOST_ID, clean, hostNick = clean, addresses = RoomCode.localAddresses())
        val host = PartyHost(clean, events)
        if (!host.start()) {
            _session.value = null
            return "Não foi possível abrir a sala neste aparelho"
        }
        engine = host
        _chat.value = emptyList()
        events.chat(SYSTEM_ID, "", "Sala criada. Mande o código para seus amigos!")
        return null
    }

    suspend fun join(codeOrIp: String, nick: String): String? {
        val ip = RoomCode.decode(codeOrIp) ?: return "Código inválido"
        leave()
        val clean = sanitizeNick(nick)
        _session.value = PartySession(PartyRole.GUEST, "", clean, connected = false)
        _chat.value = emptyList()
        val guest = PartyGuest(clean, events)
        engine = guest
        val error = guest.connect(ip)
        if (error != null) {
            engine = null
            _session.value = null
        }
        return error
    }

    fun leave() {
        engine?.stop()
        reset()
    }

    fun refreshAddresses() {
        _session.update { s -> s?.takeIf { it.isHost }?.copy(addresses = RoomCode.localAddresses()) ?: s }
    }

    fun consumeNotice() { _notice.value = null }

    fun sendChat(text: String) {
        val clean = sanitizeChat(text)
        if (clean.isNotEmpty()) engine?.sendChat(clean)
    }

    fun openedEpisode(slug: String, title: String, poster: String, season: Int, episode: Int) {
        engine?.openedEpisode(PartyState(slug = slug, title = title, poster = poster, season = season, episode = episode))
    }

    fun userPlayPause(playing: Boolean, positionMs: Long) { engine?.userPlayPause(playing, positionMs) }
    fun userSeek(positionMs: Long) { engine?.userSeek(positionMs) }
    fun reportPlayer(key: String?, ready: Boolean, positionMs: Long) { engine?.reportPlayer(key, ready, positionMs) }

    fun targetPosition(state: PartyState): Long? = engine?.hostNow()?.let(state::positionAt)

    private fun reset() {
        engine = null
        _session.value = null
    }
}
