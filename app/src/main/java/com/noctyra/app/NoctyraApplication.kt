package com.noctyra.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.noctyra.app.data.local.AppSettings
import com.noctyra.app.data.local.LibraryStore
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.data.repository.AnimeRepository
import com.noctyra.app.discord.DiscordPresence
import com.noctyra.app.download.DownloadCenter

class NoctyraApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        HttpClient.init(this)
        AnimeRepository.init(this)
        LibraryStore.init(this)
        DownloadCenter.init(this)
        DiscordPresence.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val lite = AppSettings.liteMode.value
        return ImageLoader.Builder(this)
            .okHttpClient { HttpClient.media }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(if (lite) 0.15 else 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("images"))
                    .maxSizeBytes(if (lite) 120L * 1024 * 1024 else 250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowRgb565(lite)
            .crossfade(if (lite) 0 else 180)
            .build()
    }
}
