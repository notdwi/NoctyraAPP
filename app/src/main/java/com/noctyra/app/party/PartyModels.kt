package com.noctyra.app.party

import kotlinx.serialization.Serializable

const val PARTY_PORT = 47321
const val MAX_GUESTS = 7

@Serializable
data class PartyState(
    val slug: String,
    val title: String = "",
    val poster: String = "",
    val season: Int,
    val episode: Int,
    val positionMs: Long = 0L,
    val anchorAt: Long = 0L,
    val playing: Boolean = true,
    val hold: Boolean = false,
    val waitingFor: List<String> = emptyList(),
    val rev: Long = 0L
) {
    val key: String get() = partyKey(slug, season, episode)
}

fun partyKey(slug: String, season: Int, episode: Int) = "$slug|$season|$episode"

@Serializable
data class Member(val id: String, val nick: String, val isHost: Boolean = false)

@Serializable
data class Msg(
    val t: String,
    val id: String? = null,
    val nick: String? = null,
    val members: List<Member>? = null,
    val state: PartyState? = null,
    val text: String? = null,
    val key: String? = null,
    val ready: Boolean? = null,
    val pos: Long? = null,
    val playing: Boolean? = null,
    val ts: Long? = null,
    val ts2: Long? = null
)

object MsgType {
    const val HELLO = "hello"
    const val WELCOME = "welcome"
    const val FULL = "full"
    const val MEMBERS = "members"
    const val STATE = "state"
    const val CHAT = "chat"
    const val PING = "ping"
    const val PONG = "pong"
    const val STATUS = "status"
    const val PLAY = "play"
    const val SEEK = "seek"
    const val BYE = "bye"
}

const val SYSTEM_ID = "sys"

data class ChatMessage(
    val seq: Long,
    val senderId: String,
    val nick: String,
    val text: String,
    val mine: Boolean
) {
    val isSystem: Boolean get() = senderId == SYSTEM_ID
}

enum class PartyRole { HOST, GUEST }

data class RoomAddress(val label: String, val ip: String, val code: String)

data class PartySession(
    val role: PartyRole,
    val myId: String,
    val nick: String,
    val hostNick: String = "",
    val members: List<Member> = emptyList(),
    val state: PartyState? = null,
    val addresses: List<RoomAddress> = emptyList(),
    val connected: Boolean = true,
    val error: String? = null
) {
    val isHost: Boolean get() = role == PartyRole.HOST
}

fun sanitizeNick(raw: String?): String =
    raw.orEmpty().replace(Regex("[\\p{Cntrl}]"), "").trim().take(20).ifEmpty { "Otaku" }

fun sanitizeChat(raw: String?): String =
    raw.orEmpty().replace(Regex("[\\p{Cntrl}&&[^\n]]"), "").trim().take(300)
