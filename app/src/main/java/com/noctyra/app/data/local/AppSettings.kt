package com.noctyra.app.data.local

import android.app.ActivityManager
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object AppSettings {
    private lateinit var prefs: SharedPreferences

    var isLowEndDevice: Boolean = false
        private set

    private val _liteMode = MutableStateFlow(false)
    val liteMode: StateFlow<Boolean> = _liteMode.asStateFlow()

    private val _autoplayNext = MutableStateFlow(true)
    val autoplayNext: StateFlow<Boolean> = _autoplayNext.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        isLowEndDevice = am.isLowRamDevice || am.memoryClass <= 192 || Runtime.getRuntime().availableProcessors() <= 4
        _liteMode.value = if (prefs.contains("lite_mode")) prefs.getBoolean("lite_mode", false) else isLowEndDevice
        _autoplayNext.value = prefs.getBoolean("autoplay_next", true)
    }

    fun setLiteMode(enabled: Boolean) {
        _liteMode.value = enabled
        prefs.edit().putBoolean("lite_mode", enabled).apply()
    }

    fun setAutoplayNext(enabled: Boolean) {
        _autoplayNext.value = enabled
        prefs.edit().putBoolean("autoplay_next", enabled).apply()
    }
}
