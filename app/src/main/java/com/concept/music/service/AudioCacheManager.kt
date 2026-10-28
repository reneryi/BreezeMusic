package com.concept.music.service

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import coil.imageLoader
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/**
 * 音频缓存容量上限档位
 */
enum class AudioCacheLimit(val label: String, val maxBytes: Long) {
    MB_512("512 MB", 512L * 1024L * 1024L),
    GB_1("1 GB", 1024L * 1024L * 1024L),
    GB_2("2 GB", 2048L * 1024L * 1024L),
    GB_5("5 GB", 5120L * 1024L * 1024L),
    GB_10("10 GB", 10240L * 1024L * 1024L)
}

/**
 * 项目全维度缓存分项统计快照：
 * 1. 流媒体音频缓存 (`concept_audio_stream_cache` - Media3 SimpleCache)
 * 2. 封面与评论图片缓存 (`image_cache` - Coil DiskCache + MemoryCache)
 * 3. 歌单与发现页 SWR 秒开缓存 (`swr_playlists` + `concept_discover_swr_cache_v3.xml`)
 * 4. KRC 逐字歌词、音源解析内存缓存与 WebView 验证临时缓存
 */
@Immutable
data class DetailedCacheStats(
    val audioCacheBytes: Long = 0L,
    val audioCachedKeysCount: Int = 0,
    val audioCacheMaxBytes: Long = 1024L * 1024L * 1024L,
    val imageDiskCacheBytes: Long = 0L,
    val imageMemoryCacheBytes: Long = 0L,
    val swrPlaylistDiskBytes: Long = 0L,
    val swrPlaylistCount: Int = 0,
    val swrDiscoverBytes: Long = 0L,
    val lyricCacheCount: Int = 0,
    val lyricEstimatedBytes: Long = 0L,
    val runtimeMetaCacheCount: Int = 0,
    val webViewAndTempBytes: Long = 0L,
    val audioBytes: Long = audioCacheBytes,
    val audioTrackCount: Int = audioCachedKeysCount,
    val imageDiskBytes: Long = imageDiskCacheBytes,
    val imageMemoryBytes: Long = imageMemoryCacheBytes,
    val swrMetadataBytes: Long = (swrPlaylistDiskBytes + swrDiscoverBytes),
    val swrSnapshotCount: Int = swrPlaylistCount,
    val lyricMemoryCount: Int = lyricCacheCount,
    val runtimeMetaCount: Int = runtimeMetaCacheCount,
    val otherTempBytes: Long = webViewAndTempBytes
) {
    val totalImageBytes: Long
        get() = maxOf(imageDiskBytes + imageMemoryBytes, imageDiskCacheBytes + imageMemoryCacheBytes).coerceAtLeast(0L)

    val totalSwrMetadataBytes: Long
        get() = maxOf(swrMetadataBytes, swrPlaylistDiskBytes + swrDiscoverBytes).coerceAtLeast(0L)

    val totalBytes: Long
        get() = (maxOf(audioBytes, audioCacheBytes) +
            totalImageBytes +
            totalSwrMetadataBytes +
            lyricEstimatedBytes +
            maxOf(otherTempBytes, webViewAndTempBytes)).coerceAtLeast(0L)

    val totalAllBytes: Long
        get() = totalBytes
}

/**
 * 边听边存音频与全局多级缓存管理器 ([FUNC-02])
 * 基于 Media3 SimpleCache + LRU 淘汰策略，提供全项目分项缓存统计、独立清理与一键全量清理能力
 */
@OptIn(UnstableApi::class)
object AudioCacheManager {
    private const val CACHE_DIR_NAME = "concept_audio_stream_cache"
    private const val IMAGE_CACHE_DIR_NAME = "image_cache"
    private const val SWR_CACHE_DIR_NAME = "swr_playlists"
    private const val PREFS_NAME = "concept_player_prefs"
    private const val KEY_AUDIO_CACHE_LIMIT = "audio_cache_limit"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var simpleCache: SimpleCache? = null

    @Volatile
    private var cacheDir: File? = null

    private val _cacheSizeBytes = MutableStateFlow(0L)
    val cacheSizeBytes = _cacheSizeBytes.asStateFlow()

    private val _detailedStats = MutableStateFlow(DetailedCacheStats())
    val detailedStats = _detailedStats.asStateFlow()

    private val _cacheLimit = MutableStateFlow(AudioCacheLimit.GB_1)
    val cacheLimit = _cacheLimit.asStateFlow()

    fun loadSavedCacheLimit(context: Context): AudioCacheLimit {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_AUDIO_CACHE_LIMIT, AudioCacheLimit.GB_1.name) ?: AudioCacheLimit.GB_1.name
        val resolved = runCatching { AudioCacheLimit.valueOf(raw) }.getOrDefault(AudioCacheLimit.GB_1)
        _cacheLimit.value = resolved
        return resolved
    }

    fun setCacheLimit(context: Context, limit: AudioCacheLimit) {
        val appCtx = context.applicationContext
        _cacheLimit.value = limit
        appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_AUDIO_CACHE_LIMIT, limit.name)
            .apply()
        scope.launch {
            runCatching {
                val cache = getCache(appCtx)
                trimCacheToLimit(cache, limit.maxBytes)
            }
            refreshCacheSize(appCtx)
        }
    }

    private fun trimCacheToLimit(cache: SimpleCache, maxBytes: Long) {
        if (cache.cacheSpace <= maxBytes) return
        val keysWithOldestTouch = cache.keys.map { key ->
            val lastTouch = cache.getCachedSpans(key).maxOfOrNull { it.lastTouchTimestamp } ?: 0L
            key to lastTouch
        }.sortedBy { it.second }

        for ((key, _) in keysWithOldestTouch) {
            if (cache.cacheSpace <= maxBytes) break
            runCatching { cache.removeResource(key) }
        }
    }

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
        val configuredLimit = loadSavedCacheLimit(appContext)
        val dir = File(appContext.cacheDir, CACHE_DIR_NAME).apply {
            if (!exists()) mkdirs()
        }
        cacheDir = dir
        val evictor = LeastRecentlyUsedCacheEvictor(configuredLimit.maxBytes)
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

    private fun computeDetailedStats(appCtx: Context): DetailedCacheStats {
        val cache = getCache(appCtx)
        val audioBytes = (cache.cacheSpace.takeIf { it > 0L } ?: calculateDirSize(cacheDir)).coerceAtLeast(0L)
        val audioTrackCount = runCatching { cache.keys.size }.getOrDefault(0)

        val imgDir = File(appCtx.cacheDir, IMAGE_CACHE_DIR_NAME)
        val imageDiskBytes = calculateDirSize(imgDir).coerceAtLeast(0L)
        val imageMemoryBytes = runCatching {
            appCtx.imageLoader.memoryCache?.size?.toLong() ?: 0L
        }.getOrDefault(0L).coerceAtLeast(0L)

        val swrDir = File(appCtx.cacheDir, SWR_CACHE_DIR_NAME)
        val swrDirBytes = calculateDirSize(swrDir)
        val swrFileCount = runCatching { swrDir.listFiles()?.count { it.isFile } ?: 0 }.getOrDefault(0)
        val swrPrefsFile = File(File(appCtx.applicationInfo.dataDir, "shared_prefs"), "concept_discover_swr_cache_v3.xml")
        val swrPrefsBytes = if (swrPrefsFile.exists()) swrPrefsFile.length() else 0L
        val swrMetadataBytes = (swrDirBytes + swrPrefsBytes).coerceAtLeast(0L)

        val lyricMemCount = runCatching { VipManager.lyric.cachedEntryCount() }.getOrDefault(0)
        val runtimeMetaCount = runCatching { VipManager.music.cachedMetadataCount() }.getOrDefault(0)

        var otherTempBytes = 0L
        runCatching {
            appCtx.cacheDir.listFiles()?.forEach { child ->
                if (child.name != CACHE_DIR_NAME &&
                    child.name != IMAGE_CACHE_DIR_NAME &&
                    child.name != SWR_CACHE_DIR_NAME
                ) {
                    otherTempBytes += if (child.isDirectory) calculateDirSize(child) else child.length()
                }
            }
        }

        return DetailedCacheStats(
            audioBytes = audioBytes,
            audioTrackCount = audioTrackCount,
            imageDiskBytes = imageDiskBytes,
            imageMemoryBytes = imageMemoryBytes,
            swrMetadataBytes = swrMetadataBytes,
            swrSnapshotCount = swrFileCount,
            lyricMemoryCount = lyricMemCount,
            runtimeMetaCount = runtimeMetaCount,
            otherTempBytes = otherTempBytes.coerceAtLeast(0L)
        )
    }

    fun refreshCacheSize(context: Context) {
        val appCtx = context.applicationContext
        loadSavedCacheLimit(appCtx)
        scope.launch {
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
        }
    }

    /**
     * 单独清理流媒体音频缓存 (`concept_audio_stream_cache`)
     */
    fun clearAudioCacheOnly(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        scope.launch {
            val ok = runCatching {
                val cache = getCache(appCtx)
                cache.keys.toList().forEach { key ->
                    runCatching { cache.removeResource(key) }
                }
                true
            }.getOrDefault(false)
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    /**
     * 单独清理封面与评论图片缓存 (Coil 磁盘缓存 + 内存位图缓存)
     */
    @kotlin.OptIn(coil.annotation.ExperimentalCoilApi::class)
    fun clearImageCacheOnly(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        scope.launch {
            val ok = runCatching {
                runCatching { appCtx.imageLoader.memoryCache?.clear() }
                runCatching { appCtx.imageLoader.diskCache?.clear() }
                true
            }.getOrDefault(false)
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    /**
     * 单独清理歌单与发现页 SWR 本地秒开快照缓存 (`swr_playlists` + `concept_discover_swr_cache_v3`)
     */
    fun clearSwrMetadataCacheOnly(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        scope.launch {
            val ok = runCatching {
                runCatching { VipManager.discoverCache.clearAll() }
                val swrDir = File(appCtx.cacheDir, SWR_CACHE_DIR_NAME)
                swrDir.listFiles()?.forEach { f ->
                    if (f.isFile) runCatching { f.delete() }
                }
                true
            }.getOrDefault(false)
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    /**
     * 单独清理 KRC 歌词内存缓存、音源解析缓存与 WebView 临时缓存
     */
    fun clearLyricAndTempCacheOnly(context: Context, onComplete: (Boolean) -> Unit = {}) {
        val appCtx = context.applicationContext
        scope.launch {
            val ok = runCatching {
                runCatching { VipManager.lyric.clearCache() }
                runCatching { VipManager.music.clearRuntimeMetadataCache() }
                appCtx.cacheDir.listFiles()?.forEach { child ->
                    if (child.name != CACHE_DIR_NAME &&
                        child.name != IMAGE_CACHE_DIR_NAME &&
                        child.name != SWR_CACHE_DIR_NAME
                    ) {
                        runCatching { child.deleteRecursively() }
                    }
                }
                true
            }.getOrDefault(false)
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
            withContext(Dispatchers.Main) { onComplete(ok) }
        }
    }

    /**
     * 一键全量安全清理流媒体音频缓存、图片缓存、歌单 SWR 快照、歌词与临时缓存
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
                runCatching { appCtx.imageLoader.memoryCache?.clear() }
                runCatching { appCtx.imageLoader.diskCache?.clear() }
                runCatching { VipManager.discoverCache.clearAll() }
                runCatching { VipManager.lyric.clearCache() }
                runCatching { VipManager.music.clearRuntimeMetadataCache() }
                appCtx.cacheDir.listFiles()?.forEach { child ->
                    if (child.name != CACHE_DIR_NAME &&
                        child.name != IMAGE_CACHE_DIR_NAME &&
                        child.name != SWR_CACHE_DIR_NAME
                    ) {
                        runCatching { child.deleteRecursively() }
                    }
                }
                true
            }.getOrDefault(false)
            val stats = runCatching { computeDetailedStats(appCtx) }.getOrDefault(DetailedCacheStats())
            _detailedStats.value = stats
            _cacheSizeBytes.value = stats.totalBytes
            withContext(Dispatchers.Main) {
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
