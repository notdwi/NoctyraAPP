package com.noctyra.app.download

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.noctyra.app.MainActivity
import com.noctyra.app.R
import com.noctyra.app.data.local.progressKey
import com.noctyra.app.data.model.AppError
import com.noctyra.app.data.model.cleanTitle
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.data.remote.providers.sushianimes.SushiUrls
import com.noctyra.app.data.repository.AnimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

@Serializable
data class DownloadMeta(
    val slug: String,
    val title: String,
    val posterUrl: String = "",
    val season: Int,
    val episode: Int,
    val episodeTitle: String = "",
    val thumbUrl: String = "",
    val streamType: String = "mp4",
    val headers: Map<String, String> = emptyMap()
)

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadItem(
    val id: String,
    val meta: DownloadMeta,
    val status: DownloadStatus,
    val percent: Float,
    val bytes: Long,
    val updatedAt: Long
)

@OptIn(UnstableApi::class)
object DownloadCenter {
    const val CHANNEL_ID = "noctyra_downloads"

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var appContext: Context

    private val headersByHost = ConcurrentHashMap<String, Map<String, String>>()
    @Volatile private var activeHeaders: Map<String, String> = emptyMap()

    private val _downloads = MutableStateFlow<Map<String, DownloadItem>>(emptyMap())
    val downloads: StateFlow<Map<String, DownloadItem>> = _downloads.asStateFlow()

    private var pollJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val databaseProvider by lazy { StandaloneDatabaseProvider(appContext) }

    val cache: SimpleCache by lazy {
        val root = appContext.getExternalFilesDir(null) ?: appContext.filesDir
        SimpleCache(File(root, "downloads"), NoOpCacheEvictor(), databaseProvider)
    }

    /**
     * Cada download guarda seus headers (Referer) no DownloadRequest. Como o HlsDownloader cria as
     * requisições dos segmentos sozinho, os headers são reaplicados aqui por host, com fallback
     * para os do download ativo (maxParallelDownloads = 1).
     */
    private val upstreamFactory: DataSource.Factory by lazy {
        val http = OkHttpDataSource.Factory(HttpClient.media).setUserAgent(HttpClient.MOBILE_UA)
        ResolvingDataSource.Factory(http) { spec -> spec.withAdditionalHeaders(headersFor(spec.uri)) }
    }

    private fun headersFor(uri: Uri): Map<String, String> =
        uri.host?.let { headersByHost[it] }
            ?: activeHeaders.ifEmpty { mapOf("Referer" to "${SushiUrls.BASE}/") }

    val notificationHelper by lazy { DownloadNotificationHelper(appContext, CHANNEL_ID) }

    val manager: DownloadManager by lazy {
        DownloadManager(appContext, databaseProvider, cache, upstreamFactory, Executors.newFixedThreadPool(2)).apply {
            maxParallelDownloads = 1
            minRetryCount = 4
            addListener(listener)
        }
    }

    private val listener = object : DownloadManager.Listener {
        override fun onInitialized(downloadManager: DownloadManager) {
            refresh()
        }

        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            val meta = metaOf(download)
            if (download.state == Download.STATE_DOWNLOADING && meta != null) activeHeaders = meta.headers
            if (download.state == Download.STATE_COMPLETED && meta != null) notifyCompleted(download, meta)
            refresh()
        }

        override fun onDownloadRemoved(downloadManager: DownloadManager, download: Download) {
            refresh()
        }
    }

    fun start() {
        manager
        refresh()
    }

    fun downloadId(slug: String, season: Int, episode: Int) = progressKey(slug, season, episode)

    suspend fun enqueue(
        slug: String,
        title: String,
        posterUrl: String,
        season: Int,
        episode: Int,
        episodeTitle: String = "",
        thumbUrl: String = ""
    ) {
        val stream = AnimeRepository.getStream(slug, season, episode, force = true)
        if (stream.streamType == "embed") {
            throw AppError("Este episódio só tem player externo e não pode ser baixado")
        }
        val meta = DownloadMeta(
            slug = slug, title = cleanTitle(title), posterUrl = posterUrl, season = season, episode = episode,
            episodeTitle = episodeTitle, thumbUrl = thumbUrl, streamType = stream.streamType, headers = stream.headers
        )
        val uri = Uri.parse(stream.streamUrl)
        uri.host?.let { headersByHost[it] = stream.headers }

        val request = DownloadRequest.Builder(downloadId(slug, season, episode), uri)
            .setMimeType(if (stream.streamType == "m3u8") MimeTypes.APPLICATION_M3U8 else null)
            .setData(json.encodeToString(DownloadMeta.serializer(), meta).toByteArray())
            .build()

        withContext(Dispatchers.Main) {
            manager
            DownloadService.sendAddDownload(appContext, NoctyraDownloadService::class.java, request, false)
            refresh()
        }
    }

    fun remove(id: String) {
        DownloadService.sendRemoveDownload(appContext, NoctyraDownloadService::class.java, id, false)
    }

    fun offlineMediaItem(slug: String, season: Int, episode: Int): MediaItem? {
        val item = _downloads.value[downloadId(slug, season, episode)]
        if (item?.status != DownloadStatus.COMPLETED) return null
        return runCatching { manager.downloadIndex.getDownload(item.id)?.request?.toMediaItem() }.getOrNull()
    }

    fun offlineMeta(slug: String, season: Int, episode: Int): DownloadMeta? =
        _downloads.value[downloadId(slug, season, episode)]?.takeIf { it.status == DownloadStatus.COMPLETED }?.meta

    fun offlineDataSourceFactory(): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setCacheWriteDataSinkFactory(null)

    fun usedBytes(): Long = runCatching { cache.cacheSpace }.getOrDefault(0L)

    private fun metaOf(download: Download): DownloadMeta? = runCatching {
        json.decodeFromString(DownloadMeta.serializer(), String(download.request.data))
    }.getOrNull()

    private fun toItem(download: Download): DownloadItem? {
        val meta = metaOf(download) ?: return null
        val status = when (download.state) {
            Download.STATE_COMPLETED -> DownloadStatus.COMPLETED
            Download.STATE_FAILED -> DownloadStatus.FAILED
            Download.STATE_DOWNLOADING -> DownloadStatus.DOWNLOADING
            Download.STATE_STOPPED -> DownloadStatus.PAUSED
            Download.STATE_REMOVING -> return null
            else -> DownloadStatus.QUEUED
        }
        val percent = if (status == DownloadStatus.COMPLETED) 100f else download.percentDownloaded.coerceAtLeast(0f)
        return DownloadItem(download.request.id, meta, status, percent, download.bytesDownloaded, download.updateTimeMs)
    }

    private fun refresh() {
        scope.launch {
            val stored = withContext(Dispatchers.IO) {
                runCatching {
                    buildList {
                        manager.downloadIndex.getDownloads().use { cursor ->
                            while (cursor.moveToNext()) add(cursor.download)
                        }
                    }
                }.getOrDefault(emptyList())
            }
            val merged = LinkedHashMap<String, DownloadItem>()
            stored.forEach { d ->
                val host = d.request.uri.host
                if (host != null) metaOf(d)?.let { headersByHost.putIfAbsent(host, it.headers) }
                toItem(d)?.let { merged[it.id] = it }
            }
            manager.currentDownloads.forEach { d -> toItem(d)?.let { merged[it.id] = it } }
            _downloads.value = merged
            ensurePolling()
        }
    }

    private fun ensurePolling() {
        val active = _downloads.value.values.any { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        if (!active) { pollJob?.cancel(); pollJob = null; return }
        if (pollJob?.isActive == true) return
        runCatching { DownloadService.start(appContext, NoctyraDownloadService::class.java) }
        pollJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = manager.currentDownloads
                if (current.isEmpty()) { refresh(); break }
                _downloads.value = _downloads.value.toMutableMap().apply {
                    current.forEach { d -> toItem(d)?.let { put(it.id, it) } }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun notifyCompleted(download: Download, meta: DownloadMeta) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = notificationHelper.buildDownloadCompletedNotification(
            appContext, R.drawable.ic_stat_download, openAppIntent(appContext),
            "${meta.title} • T${meta.season} EP ${meta.episode}"
        )
        runCatching { NotificationManagerCompat.from(appContext).notify(download.request.id.hashCode(), notification) }
    }

    fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
