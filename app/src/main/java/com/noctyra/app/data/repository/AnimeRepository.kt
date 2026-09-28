package com.noctyra.app.data.repository

import android.content.Context
import com.noctyra.app.data.model.Anime
import com.noctyra.app.data.model.AnimeDetail
import com.noctyra.app.data.model.HomeFeed
import com.noctyra.app.data.model.StreamResult
import com.noctyra.app.data.remote.http.HttpClient
import com.noctyra.app.data.remote.providers.AnimeProvider
import com.noctyra.app.data.remote.providers.sushianimes.SushiAnimesProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private class TtlCache<K, V>(private val maxSize: Int, private val ttlMs: Long) {
    private data class Entry<V>(val value: V, val at: Long)

    private val map = object : LinkedHashMap<K, Entry<V>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, Entry<V>>?) = size > maxSize
    }

    @Synchronized
    fun get(key: K): V? {
        val e = map[key] ?: return null
        if (System.currentTimeMillis() - e.at > ttlMs) { map.remove(key); return null }
        return e.value
    }

    @Synchronized
    fun put(key: K, value: V) { map[key] = Entry(value, System.currentTimeMillis()) }

    @Synchronized
    fun clear() = map.clear()
}

object AnimeRepository {
    private const val HOME_TTL = 15 * 60_000L

    private val provider: AnimeProvider = SushiAnimesProvider()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var dataDir: File? = null

    @Volatile private var homeMemory: HomeFeed? = null
    private val detailCache = TtlCache<String, AnimeDetail>(40, 30 * 60_000L)
    private val searchCache = TtlCache<String, List<Anime>>(30, 10 * 60_000L)
    private val streamCache = TtlCache<String, StreamResult>(8, 3 * 60_000L)
    private val inflightDetails = ConcurrentHashMap<String, Deferred<AnimeDetail>>()

    fun init(context: Context) {
        dataDir = File(context.cacheDir, "data").apply { mkdirs() }
    }

    fun cachedHome(): HomeFeed? =
        homeMemory ?: readDisk("home.json", HomeFeed.serializer())?.also { homeMemory = it }

    fun isHomeStale(feed: HomeFeed?): Boolean =
        feed == null || System.currentTimeMillis() - feed.fetchedAt > HOME_TTL

    suspend fun refreshHome(): HomeFeed = withContext(Dispatchers.IO) {
        val feed = provider.getHome()
        if (feed.isEmpty) throw Exception("Não foi possível carregar a página inicial")
        homeMemory = feed
        writeDisk("home.json", HomeFeed.serializer(), feed)
        feed
    }

    fun peekDetail(slug: String): AnimeDetail? = detailCache.get(slug)

    suspend fun getAnimeDetail(slug: String, force: Boolean = false): AnimeDetail {
        if (!force) detailCache.get(slug)?.let { return it }
        val deferred = inflightDetails.getOrPut(slug) {
            scope.async {
                try {
                    val detail = provider.getAnimeDetail(slug)
                    detailCache.put(slug, detail)
                    writeDisk(detailFile(slug), AnimeDetail.serializer(), detail)
                    detail
                } catch (e: Exception) {
                    readDisk(detailFile(slug), AnimeDetail.serializer())?.also { detailCache.put(slug, it) } ?: throw e
                } finally {
                    inflightDetails.remove(slug)
                }
            }
        }
        return deferred.await()
    }

    suspend fun search(query: String): List<Anime> {
        val key = query.trim().lowercase()
        searchCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            provider.search(query).also { searchCache.put(key, it) }
        }
    }

    suspend fun getCategory(slug: String, page: Int): List<Anime> {
        val key = "category:$slug:$page"
        searchCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            provider.getCategory(slug, page).also { searchCache.put(key, it) }
        }
    }

    suspend fun getStream(slug: String, season: Int, episode: Int, force: Boolean = false): StreamResult {
        val key = "$slug/$season/$episode"
        if (!force) streamCache.get(key)?.let { return it }
        return withContext(Dispatchers.IO) {
            provider.getStream(slug, season, episode).also { streamCache.put(key, it) }
        }
    }

    fun cacheSizeBytes(context: Context): Long =
        context.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clearCaches() {
        detailCache.clear(); searchCache.clear(); streamCache.clear()
        homeMemory = null
        HttpClient.clearCache()
        dataDir?.listFiles()?.forEach { it.delete() }
    }

    private fun detailFile(slug: String) = "detail_${slug.hashCode().toUInt()}.json"

    private fun <T> readDisk(name: String, serializer: KSerializer<T>): T? = runCatching {
        val file = File(dataDir ?: return null, name)
        if (!file.exists()) return null
        json.decodeFromString(serializer, file.readText())
    }.getOrNull()

    private fun <T> writeDisk(name: String, serializer: KSerializer<T>, value: T) {
        runCatching {
            val dir = dataDir ?: return
            val tmp = File(dir, "$name.tmp")
            tmp.writeText(json.encodeToString(serializer, value))
            tmp.renameTo(File(dir, name))
        }
    }
}
