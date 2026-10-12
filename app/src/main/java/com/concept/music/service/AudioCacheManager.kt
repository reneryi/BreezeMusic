package com.concept.music.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

/**
 * 边听边存音频缓存管理器 ([FUNC-02])
 * 基于 Media3 SimpleCache + LRU 淘汰策略，实现流媒体自动落盘缓存与秒开重播
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {
    private const val CACHE_DIR_NAME = "concept_audio_stream_cache"
    private const val DEFAULT_MAX_CACHE_BYTES = 1024L * 1024L * 1024L // 默认 1 GB 上限

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var cacheDir: File? = null

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        val appContext = context.applicationContext
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

        return CacheDataSource.Factory()
            .setCache(cache)
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

    fun clearCache(context: Context, onComplete: (Boolean) -> Unit = {}) {
        scope.launch {
            val ok = runCatching {
                val cache = getCache(context)
                val keys = cache.keys.toList()
                keys.forEach { key ->
                    runCatching { cache.removeResource(key) }
                }
                cacheDir?.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".uid")) {
                        runCatching { file.delete() }
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
