package com.concept.music.service

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import com.concept.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 边听边存音频缓存管理器 ([FUNC-02] + [维度6：固定离线收藏 Pin Offline])
 * 基于 Media3 SimpleCache + LRU 淘汰策略，实现流媒体自动落盘缓存、秒开重播与固定离线免清理保护
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {
    private const val CACHE_DIR_NAME = "concept_audio_stream_cache"
    private const val PREFS_NAME = "concept_audio_cache_prefs"
    private const val KEY_PINNED_SONGS = "pinned_song_hashes"
    private const val DEFAULT_MAX_CACHE_BYTES = 1024L * 1024L * 1024L // 默认 1 GB 上限

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var cacheDir: File? = null

    private var prefs: SharedPreferences? = null

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    private val _pinnedSongIds = MutableStateFlow<Set<String>>(emptySet())
    val pinnedSongIds = _pinnedSongIds.asStateFlow()

    private fun ensurePrefs(context: Context): SharedPreferences {
        return prefs ?: context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).also {
            prefs = it
            _pinnedSongIds.value = it.getStringSet(KEY_PINNED_SONGS, emptySet())?.toSet() ?: emptySet()
        }
    }

    fun isSongPinned(context: Context, song: Song): Boolean {
        ensurePrefs(context)
        val key = songPinKey(song)
        return key.isNotBlank() && _pinnedSongIds.value.contains(key)
    }

    fun togglePinSong(context: Context, song: Song): Boolean {
        val sp = ensurePrefs(context)
        val key = songPinKey(song)
        if (key.isBlank()) return false
        val current = _pinnedSongIds.value.toMutableSet()
        val nowPinned = if (current.contains(key)) {
            current.remove(key)
            false
        } else {
            current.add(key)
            true
        }
        _pinnedSongIds.value = current
        sp.edit().putStringSet(KEY_PINNED_SONGS, current).apply()
        return nowPinned
    }

    private fun songPinKey(song: Song): String {
        return song.hash.ifBlank { song.id }.lowercase()
    }

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        val appContext = context.applicationContext
        ensurePrefs(appContext)
        val dir = File(appContext.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        cacheDir = dir
        val evictor = LeastRecentlyUsedCacheEvictor(DEFAULT_MAX_CACHE_BYTES)
        val dbProvider = StandaloneDatabaseProvider(appContext)
        return SimpleCache(dir, evictor, dbProvider).also { cache ->
            simpleCache = cache
            refreshCacheSize(appContext)
        }
    }

    fun getDataSourceFactory(context: Context): DataSource.Factory {
        val cache = getCache(context)
        val upstreamFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        // 剥离酷狗流媒体 URL 中每次动态变化的签名/时间戳与末尾 _s<随机数> 后缀，以稳定的哈希/资源名作为缓存 Key
        val cacheKeyFactory = androidx.media3.datasource.cache.CacheKeyFactory { dataSpec ->
            dataSpec.key?.takeIf { it.isNotBlank() } ?: run {
                val uri = dataSpec.uri
                val path = uri.path
                if (!path.isNullOrBlank()) {
                    val fileName = path.substringAfterLast('/')
                        .replace(Regex("_s\\d+(\\.[a-zA-Z0-9]+)$"), "$1")
                    "kg_audio_$fileName"
                } else {
                    uri.toString().substringBefore('?')
                }
            }
        }

        // 将单分片大小提升至 32MB，避免无损 FLAC 播放途中频繁触发 5MB 分片切分与 SQLite 同步落盘阻塞
        val cacheWriteDataSinkFactory = androidx.media3.datasource.cache.CacheDataSink.Factory()
            .setCache(cache)
            .setFragmentSize(32L * 1024L * 1024L)

        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheKeyFactory(cacheKeyFactory)
            .setCacheWriteDataSinkFactory(cacheWriteDataSinkFactory)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun refreshCacheSize(context: Context) {
        scope.launch {
            val bytes = runCatching {
                val cache = getCache(context)
                val cacheSpace = cache.cacheSpace
                if (cacheSpace > 0L) cacheSpace else calculateDirSize(cacheDir)
            }.getOrDefault(0L)
            _cacheSizeBytes.value = bytes.coerceAtLeast(0L)
        }
    }

    /**
     * 清理缓存时默认保留用户手动标记「固定离线收藏 (Pin Offline)」的曲目缓存分片
     */
    fun clearCache(context: Context, keepPinned: Boolean = true, onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = runCatching {
                ensurePrefs(context)
                val pinned = if (keepPinned) _pinnedSongIds.value else emptySet()
                val cache = getCache(context)
                val keys = cache.keys.toList()
                keys.forEach { key ->
                    val isProtected = pinned.any { pinHash ->
                        pinHash.isNotBlank() && key.lowercase().contains(pinHash)
                    }
                    if (!isProtected) {
                        runCatching { cache.removeResource(key) }
                    }
                }
                if (pinned.isEmpty()) {
                    cacheDir?.listFiles()?.forEach { file ->
                        if (file.isFile && !file.name.endsWith(".uid")) {
                            runCatching { file.delete() }
                        }
                    }
                }
                _cacheSizeBytes.value = cache.cacheSpace.coerceAtLeast(0L)
                true
            }.getOrDefault(false)
            kotlinx.coroutines.withContext(Dispatchers.Main) {
                onComplete(ok)
            }
        }
    }

    fun formatSize(bytes: Long): String {
        val b = bytes.coerceAtLeast(0L)
        return when {
            b >= 1024L * 1024L * 1024L -> String.format(Locale.getDefault(), "%.2f GB", b / (1024.0 * 1024.0 * 1024.0))
            b >= 1024L * 1024L -> String.format(Locale.getDefault(), "%.1f MB", b / (1024.0 * 1024.0))
            b >= 1024L -> String.format(Locale.getDefault(), "%.1f KB", b / 1024.0)
            else -> "$b B"
        }
    }

    private fun calculateDirSize(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { f ->
            if (f.isFile) total += f.length()
        }
        return total
    }
}
