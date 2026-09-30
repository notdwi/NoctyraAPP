package com.noctyra.app.discord

/** Métodos nativos de discord_bridge.cpp. Os nomes são usados pelo JNI: não renomear. */
internal object DiscordBridge {
    external fun nativeInit(appId: Long)
    external fun nativeRunCallbacks()
    external fun nativeLogin()
    external fun nativeConnect(accessToken: String)
    external fun nativeRefresh(refreshToken: String)
    external fun nativeSetActivity(
        details: String,
        state: String,
        largeImage: String,
        largeText: String,
        smallImage: String,
        startMs: Long,
        endMs: Long
    )
    external fun nativeClearActivity()
    external fun nativeDisconnect()

    fun onStatus(status: Int) = DiscordPresence.onNativeStatus(status)

    fun onTokens(access: String, refresh: String, expiresIn: Int) = DiscordPresence.onNativeTokens(access, refresh, expiresIn)

    fun onError(message: String) = DiscordPresence.onNativeError(message)

    fun onUser(name: String, avatarUrl: String) = DiscordPresence.onNativeUser(name, avatarUrl)
}
