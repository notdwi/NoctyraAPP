package com.noctyra.app.discord

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.discord.socialsdk.DiscordSocialSdkInit
import com.noctyra.app.data.model.cleanTitle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DiscordStatus { Unavailable, LoggedOut, Connecting, Connected }

data class DiscordUser(val name: String, val avatarUrl: String)

private data class PresenceActivity(
    val details: String,
    val state: String,
    val largeImage: String,
    val largeText: String,
    val startMs: Long,
    val endMs: Long
)

/**
 * Rich Presence pelo Discord Social SDK oficial: o login é feito pelo próprio Discord (OAuth2 com PKCE)
 * e o app só guarda o token de acesso dele, nunca senha ou token da conta.
 */
object DiscordPresence {
    private const val APP_ID = 1554855985305096232L
    private const val CALLBACK_INTERVAL_MS = 500L
    private const val REFRESH_MARGIN_MS = 24 * 60 * 60 * 1000L

    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private var available = false
    private var initialized = false
    private var pumping = false
    private var pending: PresenceActivity? = null
    private var lastSent: String? = null

    private val _status = MutableStateFlow(DiscordStatus.Unavailable)
    val status: StateFlow<DiscordStatus> = _status.asStateFlow()

    private val _showActivity = MutableStateFlow(true)
    val showActivity: StateFlow<Boolean> = _showActivity.asStateFlow()

    private val _user = MutableStateFlow<DiscordUser?>(null)
    val user: StateFlow<DiscordUser?> = _user.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val pump = object : Runnable {
        override fun run() {
            if (!pumping) return
            runCatching { DiscordBridge.nativeRunCallbacks() }
            main.postDelayed(this, CALLBACK_INTERVAL_MS)
        }
    }

    fun init(context: Context) {
        prefs = context.getSharedPreferences("discord", Context.MODE_PRIVATE)
        _showActivity.value = prefs.getBoolean("show", true)
        val savedName = prefs.getString("user_name", null)
        if (savedName != null) _user.value = DiscordUser(savedName, prefs.getString("user_avatar", "").orEmpty())
        available = runCatching {
            System.loadLibrary("discord_partner_sdk")
            System.loadLibrary("noctyra_discord")
        }.onFailure { Log.w("Noctyra", "Discord indisponível neste aparelho") }.isSuccess
        _status.value = if (available) DiscordStatus.LoggedOut else DiscordStatus.Unavailable
    }

    fun attach(activity: Activity) {
        if (!available) return
        runCatching { DiscordSocialSdkInit.setEngineActivity(activity) }
        if (prefs.getString("refresh", null) != null) resume()
    }

    fun login() {
        if (!ensureClient()) return
        _error.value = null
        _status.value = DiscordStatus.Connecting
        DiscordBridge.nativeLogin()
    }

    fun logout() {
        if (initialized) {
            DiscordBridge.nativeClearActivity()
            DiscordBridge.nativeDisconnect()
        }
        prefs.edit().remove("access").remove("refresh").remove("expires").remove("user_name").remove("user_avatar").apply()
        _user.value = null
        lastSent = null
        stopPump()
        _status.value = if (available) DiscordStatus.LoggedOut else DiscordStatus.Unavailable
    }

    fun setShowActivity(show: Boolean) {
        _showActivity.value = show
        prefs.edit().putBoolean("show", show).apply()
        if (!show) clear()
    }

    fun watching(
        title: String, season: Int, episode: Int, episodeTitle: String,
        poster: String, positionMs: Long, durationMs: Long, playing: Boolean
    ) {
        if (!available || !_showActivity.value) return
        val name = fit(cleanTitle(title).ifBlank { "Anime" })
        val ep = buildString {
            append("T$season • EP $episode")
            if (episodeTitle.isNotBlank()) append(" • ").append(episodeTitle)
            if (!playing) append(" (pausado)")
        }
        val start = if (playing) System.currentTimeMillis() - positionMs.coerceAtLeast(0) else 0L
        val end = 0L
        val image = poster.takeIf { it.startsWith("https://") && it.length <= 300 }.orEmpty()
        val signature = "$name|$ep|$image|${start / 3_000}"
        if (signature == lastSent) return
        lastSent = signature
        val activity = PresenceActivity(name, fit(ep), image, name, start, end)
        if (_status.value == DiscordStatus.Connected) send(activity) else pending = activity
    }

    fun clear() {
        pending = null
        lastSent = null
        if (initialized && _status.value == DiscordStatus.Connected) DiscordBridge.nativeClearActivity()
    }

    internal fun onNativeStatus(status: Int) {
        _status.value = when (status) {
            3 -> DiscordStatus.Connected
            0 -> if (prefs.getString("refresh", null) != null) DiscordStatus.Connecting else DiscordStatus.LoggedOut
            else -> DiscordStatus.Connecting
        }
        if (status == 3) pending?.let { send(it); pending = null }
    }

    internal fun onNativeTokens(access: String, refresh: String, expiresIn: Int) {
        prefs.edit()
            .putString("access", access)
            .putString("refresh", refresh)
            .putLong("expires", System.currentTimeMillis() + expiresIn * 1000L)
            .apply()
    }

    internal fun onNativeUser(name: String, avatarUrl: String) {
        _user.value = DiscordUser(name, avatarUrl)
        prefs.edit().putString("user_name", name).putString("user_avatar", avatarUrl).apply()
    }

    internal fun onNativeError(message: String) {
        Log.w("Noctyra", "Discord: $message")
        _error.value = "Não foi possível conectar ao Discord. Tente de novo."
        if (_status.value == DiscordStatus.Connecting) _status.value = DiscordStatus.LoggedOut
    }

    private fun resume() {
        if (!ensureClient()) return
        val access = prefs.getString("access", null)
        val refresh = prefs.getString("refresh", null) ?: return
        val expires = prefs.getLong("expires", 0L)
        _status.value = DiscordStatus.Connecting
        if (access != null && expires - System.currentTimeMillis() > REFRESH_MARGIN_MS) DiscordBridge.nativeConnect(access)
        else DiscordBridge.nativeRefresh(refresh)
    }

    private fun ensureClient(): Boolean {
        if (!available) return false
        if (!initialized) {
            DiscordBridge.nativeInit(APP_ID)
            initialized = true
        }
        if (!pumping) {
            pumping = true
            main.post(pump)
        }
        return true
    }

    private fun stopPump() {
        pumping = false
        main.removeCallbacks(pump)
    }

    private fun send(a: PresenceActivity) {
        DiscordBridge.nativeSetActivity(a.details, a.state, a.largeImage, a.largeText, "", a.startMs, a.endMs)
    }

    private fun fit(text: String): String = text.take(128).padEnd(2, ' ')
}
