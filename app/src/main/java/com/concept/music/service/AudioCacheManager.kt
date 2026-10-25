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
import coil.imageLoader
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
 * 基于 Media3 SimpleCache + LRU 淘汰策略，实现联网流媒体自动落盘缓存与秒开重播
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

    /**
     * 构造唯一且隔离试听片段与完整正片的业务音频缓存 Key，彻底杜绝 trackercdn 路由同名导致的串歌与试听锁死
     */
    fun buildCacheKey(hash: String, qualityApiValue: String, isFreePart: Boolean = false): String {
        val safeHash = hash.trim().uppercase().ifBlank { "UNKNOWN" }
        val safeQuality = qualityApiValue.trim().lowercase().ifBlank { "128" }
        val streamScope = if (isFreePart) "trial" else "full"
        return "kg_v2_${safeHash}_${safeQuality}_$streamScope"
    }

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
            .setUserAgent("Android15-1070-11440-46-0-NetMusic-wifi")
            .setDefaultRequestProperties(mapOf("Referer" to "https://www.kugou.com/"))
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        // 优先使用 MediaItem.setCustomCacheKey 注入的业务唯一键；兜底提取 query 中的 hash/quality/IsFreePart
        val cacheKeyFactory = androidx.media3.datasource.cache.CacheKeyFactory { dataSpec ->
            dataSpec.key?.takeIf { it.isNotBlank() && it != "kg_audio_" } ?: run {
                val uri = dataSpec.uri
                val queryHash = uri.getQueryParameter("hash")
                if (!queryHash.isNullOrBlank()) {
                    val q = uri.getQueryParameter("quality").orEmpty().ifBlank { "128" }
                    val free = uri.getQueryParameter("IsFreePart") == "1" || uri.getQueryParameter("is_free_part") == "1"
                    buildCacheKey(queryHash, q, free)
                } else {
                    val path = uri.path.orEmpty()
                    val fileName = path.substringAfterLast('/')
                        .replace(Regex("_s\\d+(\\.[a-zA-Z0-9]+)$"), "$1")
                        .trim()
                    if (fileName.isNotBlank() && fileName != "priv_url" && fileName != "url" && fileName != "v2") {
                        "kg_audio_$fileName"
                    } else {
                        "kg_audio_${uri.toString().substringBefore('?').hashCode().toUInt().toString(16)}"
                    }
                }
            }
        }

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
        val appCtx = context.applicationContext
        scope.launch {
            val bytes = runCatching {
                val cache = getCache(appCtx)
                val audioBytes = cache.cacheSpace.takeIf { it > 0L } ?: calculateDirSize(cacheDir)
                val imageBytes = calculateDirSize(File(appCtx.cacheDir, "image_cache"))
                audioBytes + imageBytes
            }.getOrDefault(0L)
            _cacheSizeBytes.value = bytes.coerceAtLeast(0L)
        }
    }

    /**
     * 安全清理流媒体音频缓存与图片磁盘缓存（严禁直接物理删除 SimpleCache 索引文件）
     */
    @kotlin.OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearCache(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        scope.launch {
            val ok = runCatching {
                val cache = getCache(appCtx)
                val keys = cache.keys.toList()
                keys.forEach { key ->
                    runCatching { cache.removeResource(key) }
                }
                runCatching { appCtx.imageLoader.diskCache?.clear() }
                _cacheSizeBytes.value = 0L
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
            if (f.isFile && !f.name.endsWith(".uid") && !f.name.endsWith(".exi")) {
                total += f.length()
            }
        }
        return total
    }
}
