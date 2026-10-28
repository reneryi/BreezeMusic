package com.concept.music.service

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.Immutable
import com.concept.music.data.kugou.NetworkClientProvider
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

enum class DownloadTaskStatus(val label: String) {
    QUEUED("排队中"),
    RESOLVING("解析音源..."),
    DOWNLOADING("下载音频..."),
    EMBEDDING("内嵌封面与歌词..."),
    COMPLETED("已完成"),
    SKIPPED_EXISTING("本地已存在"),
    FAILED("下载失败")
}

@Immutable
data class DownloadTaskItem(
    val taskId: String,
    val song: Song,
    val requestedQuality: AudioQuality,
    val actualQuality: AudioQuality = requestedQuality,
    val status: DownloadTaskStatus = DownloadTaskStatus.QUEUED,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val fileName: String = "",
    val savedAudioPath: String = "",
    val savedLrcPath: String = "",
    val embeddedCover: Boolean = false,
    val embeddedLyric: Boolean = false,
    val embeddedMetadata: Boolean = false,
    val errorMessage: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Immutable
data class MusicDownloadConfig(
    val downloadDirectoryPath: String = MusicDownloadManager.DEFAULT_AUDIO_DOWNLOAD_PATH,
    val lyricDirectoryPath: String = MusicDownloadManager.DEFAULT_LYRIC_DOWNLOAD_PATH,
    val defaultDownloadQuality: AudioQuality = AudioQuality.HI_RES,
    val embedHdCover: Boolean = true,
    val embedLyrics: Boolean = true,
    val saveStandaloneLrcFile: Boolean = false,
    val includeTranslationInLyric: Boolean = true,
    val skipExistingFiles: Boolean = true
)

/**
 * 酷狗概念版多音质音乐下载与元数据/高清封面/歌词内嵌管理器
 * 参考实现：lisalee23042605/kgmusicbot & MakcRe/KuGouMusicApi
 *
 * 核心特性：
 * 1. 多音质自动解析与平滑降级：支持 Hi-Res (res_hash) -> SQ 无损 FLAC (sq_hash) -> 高品 320K (hq_hash) -> 标准 128K；
 * 2. 歌曲详细信息自动内嵌：标题、歌手、专辑名、发行日期、音质与 Hash 备注写入 ID3v2.3 / FLAC Vorbis Comment；
 * 3. 高清封面默认内嵌：优先升级酷狗封面 URL 为 1000x1000 高清大图（或通过 album_id 查询专辑封面），内嵌至 APIC / FLAC Picture；
 * 4. 歌词默认内嵌 + 可选独立保存 `.lrc` 文件：自动获取 LRC/KRC 双语时间轴歌词，默认内嵌至 USLT / LYRICS + UNSYNCEDLYRICS，
 *    当开启「同时独立保存 .lrc 歌词文件」时，自动写入用户自定义的歌词保存路径；
 * 5. 自定义下载保存路径与歌词保存路径：支持预设目录、手动输入路径与系统文件夹选择器 (SAF Tree Uri 转换)，兼容 Android Scoped Storage 与 MediaStore。
 */
object MusicDownloadManager {
    private const val PREFS_NAME = "concept_download_prefs"
    private const val KEY_AUDIO_DIR = "download_audio_dir"
    private const val KEY_LYRIC_DIR = "download_lyric_dir"
    private const val KEY_DEFAULT_QUALITY = "download_default_quality"
    private const val KEY_EMBED_HD_COVER = "download_embed_hd_cover"
    private const val KEY_EMBED_LYRICS = "download_embed_lyrics"
    private const val KEY_SAVE_STANDALONE_LRC = "download_save_standalone_lrc"
    private const val KEY_INCLUDE_TRANSLATION_LRC = "download_include_translation_lrc"
    private const val KEY_SKIP_EXISTING = "download_skip_existing"
    private const val KEY_HISTORY_JSON = "download_tasks_history_json"

    const val DEFAULT_AUDIO_DOWNLOAD_PATH = "/storage/emulated/0/Music/BreezeMusic"
    const val DEFAULT_LYRIC_DOWNLOAD_PATH = "/storage/emulated/0/Music/BreezeMusic/Lyrics"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _config = MutableStateFlow(MusicDownloadConfig())
    val config = _config.asStateFlow()

    private val _tasks = MutableStateFlow<List<DownloadTaskItem>>(emptyList())
    val tasks = _tasks.asStateFlow()

    private val KUGOU_THUMB_SIZE_REGEX = Regex("""/(100|120|150|200|240|300|400|480)/""")

    fun init(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        val p = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = p

        val defaultAudioPath = resolveDefaultPublicMusicDir("BreezeMusic")
        val defaultLyricPath = resolveDefaultPublicMusicDir("BreezeMusic/Lyrics")

        val savedAudioDir = p.getString(KEY_AUDIO_DIR, defaultAudioPath)?.takeIf { it.isNotBlank() } ?: defaultAudioPath
        val savedLyricDir = p.getString(KEY_LYRIC_DIR, defaultLyricPath)?.takeIf { it.isNotBlank() } ?: defaultLyricPath
        val savedQuality = runCatching {
            AudioQuality.valueOf(p.getString(KEY_DEFAULT_QUALITY, AudioQuality.HI_RES.name) ?: AudioQuality.HI_RES.name)
        }.getOrDefault(AudioQuality.HI_RES)
        val savedEmbedCover = p.getBoolean(KEY_EMBED_HD_COVER, true)
        val savedEmbedLyrics = p.getBoolean(KEY_EMBED_LYRICS, true)
        val savedStandaloneLrc = p.getBoolean(KEY_SAVE_STANDALONE_LRC, false)
        val savedIncludeTrans = p.getBoolean(KEY_INCLUDE_TRANSLATION_LRC, true)
        val savedSkipExisting = p.getBoolean(KEY_SKIP_EXISTING, true)

        _config.value = MusicDownloadConfig(
            downloadDirectoryPath = savedAudioDir,
            lyricDirectoryPath = savedLyricDir,
            defaultDownloadQuality = savedQuality,
            embedHdCover = savedEmbedCover,
            embedLyrics = savedEmbedLyrics,
            saveStandaloneLrcFile = savedStandaloneLrc,
            includeTranslationInLyric = savedIncludeTrans,
            skipExistingFiles = savedSkipExisting
        )

        val historyJson = p.getString(KEY_HISTORY_JSON, null)
        if (!historyJson.isNullOrBlank()) {
            runCatching {
                val type = object : TypeToken<List<DownloadTaskItem>>() {}.type
                val restored: List<DownloadTaskItem>? = com.concept.music.data.kugou.KugouSigner.gson.fromJson(historyJson, type)
                if (!restored.isNullOrEmpty()) {
                    _tasks.value = restored.take(100)
                }
            }
        }
    }

    fun resetForTest() {
        _config.value = MusicDownloadConfig()
        _tasks.value = emptyList()
    }

    private fun resolveDefaultPublicMusicDir(subFolder: String): String {
        return runCatching {
            val pubMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (pubMusic != null && pubMusic.absolutePath.isNotBlank()) {
                File(pubMusic, subFolder).absolutePath
            } else {
                "/storage/emulated/0/Music/$subFolder"
            }
        }.getOrDefault("/storage/emulated/0/Music/$subFolder")
    }

    /**
     * 将系统文件夹选择器 (`ActivityResultContracts.OpenDocumentTree`) 返回的 `content://` Tree Uri
     * 解析为可读写的手机存储文件夹路径（如 `/storage/emulated/0/Music/MySongs`）
     */
    fun resolveTreeUriToFilePath(context: Context, treeUri: Uri): String? {
        return runCatching {
            val docId = DocumentsContract.getTreeDocumentId(treeUri)
            val split = docId.split(":")
            if (split.isNotEmpty()) {
                val type = split[0]
                val relativePath = if (split.size > 1) split[1].trimStart('/') else ""
                val baseRoot = if (type.equals("primary", ignoreCase = true)) {
                    runCatching { Environment.getExternalStorageDirectory().absolutePath }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "/storage/emulated/0"
                } else {
                    "/storage/$type"
                }
                if (relativePath.isBlank()) baseRoot else "$baseRoot/$relativePath"
            } else null
        }.getOrNull()
    }

    fun setDownloadDirectoryPath(path: String) {
        val cleaned = path.trim().ifBlank { DEFAULT_AUDIO_DOWNLOAD_PATH }
        _config.update { it.copy(downloadDirectoryPath = cleaned) }
        prefs?.edit()?.putString(KEY_AUDIO_DIR, cleaned)?.apply()
    }

    fun setLyricDirectoryPath(path: String) {
        val cleaned = path.trim().ifBlank { DEFAULT_LYRIC_DOWNLOAD_PATH }
        _config.update { it.copy(lyricDirectoryPath = cleaned) }
        prefs?.edit()?.putString(KEY_LYRIC_DIR, cleaned)?.apply()
    }

    fun resetDirectoriesToDefault() {
        val defaultAudio = resolveDefaultPublicMusicDir("BreezeMusic")
        val defaultLyric = resolveDefaultPublicMusicDir("BreezeMusic/Lyrics")
        _config.update {
            it.copy(
                downloadDirectoryPath = defaultAudio,
                lyricDirectoryPath = defaultLyric
            )
        }
        prefs?.edit()
            ?.putString(KEY_AUDIO_DIR, defaultAudio)
            ?.putString(KEY_LYRIC_DIR, defaultLyric)
            ?.apply()
    }

    fun setDefaultDownloadQuality(quality: AudioQuality) {
        _config.update { it.copy(defaultDownloadQuality = quality) }
        prefs?.edit()?.putString(KEY_DEFAULT_QUALITY, quality.name)?.apply()
    }

    fun setEmbedHdCover(enabled: Boolean) {
        _config.update { it.copy(embedHdCover = enabled) }
        prefs?.edit()?.putBoolean(KEY_EMBED_HD_COVER, enabled)?.apply()
    }

    fun setEmbedLyrics(enabled: Boolean) {
        _config.update { it.copy(embedLyrics = enabled) }
        prefs?.edit()?.putBoolean(KEY_EMBED_LYRICS, enabled)?.apply()
    }

    fun setSaveStandaloneLrcFile(enabled: Boolean) {
        _config.update { it.copy(saveStandaloneLrcFile = enabled) }
        prefs?.edit()?.putBoolean(KEY_SAVE_STANDALONE_LRC, enabled)?.apply()
    }

    fun setIncludeTranslationInLyric(enabled: Boolean) {
        _config.update { it.copy(includeTranslationInLyric = enabled) }
        prefs?.edit()?.putBoolean(KEY_INCLUDE_TRANSLATION_LRC, enabled)?.apply()
    }

    fun setSkipExistingFiles(enabled: Boolean) {
        _config.update { it.copy(skipExistingFiles = enabled) }
        prefs?.edit()?.putBoolean(KEY_SKIP_EXISTING, enabled)?.apply()
    }

    fun clearCompletedTasks() {
        _tasks.update { list ->
            list.filter {
                it.status == DownloadTaskStatus.QUEUED ||
                    it.status == DownloadTaskStatus.RESOLVING ||
                    it.status == DownloadTaskStatus.DOWNLOADING ||
                    it.status == DownloadTaskStatus.EMBEDDING
            }
        }
        persistTasksHistory()
    }

    private fun persistTasksHistory() {
        val p = prefs ?: return
        runCatching {
            val snapshot = _tasks.value.take(60)
            val json = com.concept.music.data.kugou.KugouSigner.gson.toJson(snapshot)
            p.edit().putString(KEY_HISTORY_JSON, json).apply()
        }
    }

    /**
     * 生成安全规范的「歌手 - 歌名」基础文件名（参考 kgmusicbot: safe_name）
     */
    internal fun buildSafeBaseFileName(artist: String, title: String): String {
        val cleanArtist = artist.trim().ifBlank { "未知歌手" }
        val cleanTitle = title.trim().ifBlank { "未知歌曲" }
        val raw = "$cleanArtist - $cleanTitle"
        return raw
            .replace(Regex("""[/\\:*?"<>|\u0000-\u001F]+"""), "_")
            .replace("、", "_")
            .trim()
            .trimEnd('.')
            .ifBlank { "BreezeMusic_Track_${System.currentTimeMillis()}" }
    }

    /**
     * 将酷狗封面地址升级为高清封面 URL（优先 1000px，回退 480px，对齐 kgmusicbot 的 get_album_cover 逻辑与项目海报高清策略）
     */
    internal fun buildHighDefCoverUrlCandidates(rawCoverUrl: String): List<String> {
        val trimmed = rawCoverUrl.trim()
        if (trimmed.isBlank()) return emptyList()
        val url1000 = trimmed.replace("{size}", "1000").let { u ->
            if (KUGOU_THUMB_SIZE_REGEX.containsMatchIn(u)) KUGOU_THUMB_SIZE_REGEX.replace(u, "/1000/") else u
        }
        val url480 = trimmed.replace("{size}", "480").let { u ->
            if (KUGOU_THUMB_SIZE_REGEX.containsMatchIn(u)) KUGOU_THUMB_SIZE_REGEX.replace(u, "/480/") else u
        }
        return listOf(url1000, url480, trimmed).distinct().filter { it.startsWith("http") }
    }

    /**
     * 根据解析出的播放 URL 与实际音质推断文件扩展名（.flac / .mp3 / .ogg / .m4a）
     */
    internal fun deduceAudioFileExtension(url: String, actualQuality: AudioQuality): String {
        val cleanPath = url.substringBefore('?').lowercase(Locale.ROOT)
        return when {
            cleanPath.endsWith(".flac") -> ".flac"
            cleanPath.endsWith(".mp3") -> ".mp3"
            cleanPath.endsWith(".ogg") -> ".ogg"
            cleanPath.endsWith(".m4a") -> ".m4a"
            cleanPath.endsWith(".aac") -> ".aac"
            cleanPath.endsWith(".wav") -> ".wav"
            cleanPath.endsWith(".ape") -> ".ape"
            actualQuality == AudioQuality.HI_RES || actualQuality == AudioQuality.LOSSLESS -> ".flac"
            else -> ".mp3"
        }
    }

    /**
     * 触发单曲下载任务（含存储与网络权限检查、多音质解析、音频流下载、高清封面与歌词内嵌、独立 .lrc 保存）
     */
    fun enqueueDownload(
        context: Context,
        song: Song,
        quality: AudioQuality = _config.value.defaultDownloadQuality,
        onPermissionRequired: ((Array<String>) -> Unit)? = null
    ) {
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.NETWORK_ACCESS)) {
            Toast.makeText(context, "已在权限管理中关闭「网络访问」权限，无法下载音乐", Toast.LENGTH_SHORT).show()
            return
        }
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.MUSIC_DOWNLOAD)) {
            Toast.makeText(context, "音乐下载默认关闭，请先在「设置 - 权限管理」中手动开启「音乐下载与歌词保存」开关", Toast.LENGTH_LONG).show()
            return
        }
        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.STORAGE_EXPORT)) {
            Toast.makeText(context, "已在权限管理中关闭「存储读写与相册导出」权限，请先在设置-权限管理中开启", Toast.LENGTH_LONG).show()
            return
        }
        if (!AppPermissionManager.hasMusicDownloadStoragePermission(context)) {
            val perms = AppPermissionManager.requiredStorageRuntimePermissions()
            if (onPermissionRequired != null && perms.isNotEmpty()) {
                onPermissionRequired(perms)
                return
            }
        }

        val taskId = "${song.id.ifBlank { song.hash }}_${quality.apiValue}_${System.currentTimeMillis()}"
        val newTask = DownloadTaskItem(
            taskId = taskId,
            song = song,
            requestedQuality = quality,
            status = DownloadTaskStatus.QUEUED
        )
        _tasks.update { listOf(newTask) + it }
        Toast.makeText(
            context,
            "已开始下载：《${song.title}》(${quality.label})",
            Toast.LENGTH_SHORT
        ).show()

        scope.launch {
            executeDownloadTask(context.applicationContext, newTask)
        }
    }

    private fun updateTask(taskId: String, transform: (DownloadTaskItem) -> DownloadTaskItem) {
        _tasks.update { list ->
            list.map { if (it.taskId == taskId) transform(it) else it }
        }
    }

    private suspend fun executeDownloadTask(context: Context, initialTask: DownloadTaskItem) {
        val taskId = initialTask.taskId
        val currentCfg = _config.value
        try {
            updateTask(taskId) { it.copy(status = DownloadTaskStatus.RESOLVING, progress = 0.05f) }

            // 1. 补全歌曲完整音质规格 (res_hash / sq_hash / 320_hash) 与真实专辑详情（对齐 kgmusicbot fetch_gateway_track_info）
            val enrichedSong = runCatching {
                VipManager.music.enrichSongsPrivilegeLite(listOf(initialTask.song)).firstOrNull()
            }.getOrNull() ?: initialTask.song

            val (resolvedAlbumName, resolvedAlbumId) = runCatching {
                VipManager.music.resolveSongAlbumMeta(enrichedSong)
            }.getOrDefault(enrichedSong.album to enrichedSong.albumId)

            val fullSong = enrichedSong.copy(
                album = resolvedAlbumName.ifBlank { enrichedSong.album },
                albumId = resolvedAlbumId.ifBlank { enrichedSong.albumId }
            )

            // 2. 按请求音质解析最佳可用下载流（内部支持 Hi-Res -> SQ 无损 -> 高品 320K -> 标准 128K 自动降级）
            val playbackResult = VipManager.music.resolvePlayback(
                song = fullSong,
                preferredQuality = initialTask.requestedQuality,
                useAccompaniment = false
            )
            if (playbackResult.url.isBlank()) {
                throw IllegalStateException("未能获取到有效的音频下载链接")
            }

            val actualQuality = playbackResult.actualQuality
            val ext = deduceAudioFileExtension(playbackResult.url, actualQuality)
            val safeBaseName = buildSafeBaseFileName(fullSong.artist, fullSong.title)
            val audioFileName = "$safeBaseName$ext"

            val targetAudioDir = File(currentCfg.downloadDirectoryPath)
            val directAudioFile = File(targetAudioDir, audioFileName)

            // 检查本地是否已存在同名文件（对齐 kgmusicbot: 本地已存在，跳过下载）
            if (currentCfg.skipExistingFiles && directAudioFile.exists() && directAudioFile.length() > 128 * 1024L) {
                var savedLrcPath = ""
                if (currentCfg.saveStandaloneLrcFile) {
                    val lrcText = runCatching {
                        VipManager.lyric.getLrcTextForExport(fullSong, includeTranslation = currentCfg.includeTranslationInLyric)
                    }.getOrDefault("")
                    if (lrcText.isNotBlank()) {
                        savedLrcPath = saveStandaloneLrcFile(
                            context = context,
                            lyricDirPath = currentCfg.lyricDirectoryPath,
                            safeBaseName = safeBaseName,
                            lrcText = lrcText
                        )
                    }
                }
                updateTask(taskId) {
                    it.copy(
                        song = fullSong,
                        actualQuality = actualQuality,
                        status = DownloadTaskStatus.SKIPPED_EXISTING,
                        progress = 1f,
                        fileName = audioFileName,
                        savedAudioPath = directAudioFile.absolutePath,
                        savedLrcPath = savedLrcPath,
                        embeddedCover = currentCfg.embedHdCover,
                        embeddedLyric = currentCfg.embedLyrics,
                        embeddedMetadata = true
                    )
                }
                persistTasksHistory()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "本地已存在：《${fullSong.title}》(${actualQuality.label})", Toast.LENGTH_SHORT).show()
                }
                return
            }

            // 3. 先下载音频流至应用缓存临时文件，以便高速完成二进制标签、高清封面与歌词内嵌，再落盘至目标自定义路径
            updateTask(taskId) {
                it.copy(
                    song = fullSong,
                    actualQuality = actualQuality,
                    status = DownloadTaskStatus.DOWNLOADING,
                    fileName = audioFileName,
                    progress = 0.10f
                )
            }

            val tempDir = File(context.cacheDir, "music_downloads_staging").apply { mkdirs() }
            val stagingAudioFile = File(tempDir, "${taskId}_$audioFileName")

            downloadAudioStreamToFile(
                url = playbackResult.url,
                destFile = stagingAudioFile,
                expectedSize = playbackResult.fileSizeByte
            ) { downloaded, total ->
                val frac = if (total > 0L) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0.5f
                updateTask(taskId) {
                    it.copy(
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        progress = (0.10f + frac * 0.72f).coerceIn(0.10f, 0.82f)
                    )
                }
            }

            // 根据下载后文件的真实魔数修正扩展名（例如酷狗部分 128K 接口返回 OGG 容器）
            val detectedContainer = AudioMetadataEmbedder.detectFormat(stagingAudioFile, ext)
            val finalExt = when (detectedContainer) {
                AudioMetadataEmbedder.AudioContainerFormat.FLAC -> ".flac"
                AudioMetadataEmbedder.AudioContainerFormat.MP3 -> ".mp3"
                AudioMetadataEmbedder.AudioContainerFormat.OGG -> ".ogg"
                AudioMetadataEmbedder.AudioContainerFormat.OTHER -> ext
            }
            val finalAudioFileName = "$safeBaseName$finalExt"

            updateTask(taskId) {
                it.copy(
                    status = DownloadTaskStatus.EMBEDDING,
                    fileName = finalAudioFileName,
                    progress = 0.85f
                )
            }

            // 4. 获取高清专辑封面字节流（默认内嵌高清封面）
            val hdCoverBytes = if (currentCfg.embedHdCover) {
                fetchHighDefCoverBytes(fullSong)
            } else null

            // 5. 获取 LRC 歌词文本（用于默认内嵌音频文件 及 可选独立保存 .lrc 文件）
            val needLyricText = currentCfg.embedLyrics || currentCfg.saveStandaloneLrcFile
            val lrcText = if (needLyricText) {
                runCatching {
                    VipManager.lyric.getLrcTextForExport(
                        song = fullSong,
                        includeTranslation = currentCfg.includeTranslationInLyric
                    )
                }.getOrDefault("")
            } else ""

            // 6. 将歌曲详细信息、高清封面、歌词内嵌到音频文件
            val tagMeta = AudioTagMetadata(
                title = fullSong.title,
                artist = fullSong.artist,
                album = fullSong.album,
                publishDate = fullSong.publishDate,
                songHash = playbackResult.resolvedHash.ifBlank { fullSong.hash },
                qualityLabel = actualQuality.label,
                coverBytes = hdCoverBytes,
                coverMimeType = AudioMetadataEmbedder.detectImageMimeType(hdCoverBytes),
                lrcText = if (currentCfg.embedLyrics) lrcText else ""
            )
            val embedSuccess = runCatching {
                AudioMetadataEmbedder.embedMetadata(stagingAudioFile, tagMeta)
            }.getOrDefault(false)

            // 7. 将内嵌完成的音频文件保存到用户设定的下载保存路径
            val finalSavedAudioPath = exportFileToTargetDirectory(
                context = context,
                sourceFile = stagingAudioFile,
                targetDirPath = currentCfg.downloadDirectoryPath,
                fileName = finalAudioFileName,
                mimeType = when (finalExt) {
                    ".flac" -> "audio/flac"
                    ".ogg" -> "audio/ogg"
                    ".wav" -> "audio/wav"
                    else -> "audio/mpeg"
                },
                isAudio = true
            )
            stagingAudioFile.delete()

            // 8. 若开启了「同时独立保存 .lrc 歌词文件」，将歌词保存到自定义歌词路径
            var finalSavedLrcPath = ""
            if (currentCfg.saveStandaloneLrcFile && lrcText.isNotBlank()) {
                finalSavedLrcPath = saveStandaloneLrcFile(
                    context = context,
                    lyricDirPath = currentCfg.lyricDirectoryPath,
                    safeBaseName = safeBaseName,
                    lrcText = lrcText
                )
            }

            updateTask(taskId) {
                it.copy(
                    song = fullSong,
                    actualQuality = actualQuality,
                    status = DownloadTaskStatus.COMPLETED,
                    progress = 1f,
                    fileName = finalAudioFileName,
                    savedAudioPath = finalSavedAudioPath,
                    savedLrcPath = finalSavedLrcPath,
                    embeddedCover = hdCoverBytes != null && hdCoverBytes.isNotEmpty() && embedSuccess,
                    embeddedLyric = currentCfg.embedLyrics && lrcText.isNotBlank() && embedSuccess,
                    embeddedMetadata = embedSuccess
                )
            }
            persistTasksHistory()

            withContext(Dispatchers.Main) {
                val lrcNote = when {
                    finalSavedLrcPath.isNotBlank() && currentCfg.embedLyrics -> "（封面/歌词已内嵌 + 独立.lrc已保存）"
                    currentCfg.embedLyrics && lrcText.isNotBlank() -> "（高清封面与歌词已内嵌）"
                    else -> ""
                }
                Toast.makeText(
                    context,
                    "下载完成：《${fullSong.title}》[${actualQuality.label}] $lrcNote",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            updateTask(taskId) {
                it.copy(
                    status = DownloadTaskStatus.FAILED,
                    errorMessage = e.message ?: "下载失败，请检查网络或存储权限"
                )
            }
            persistTasksHistory()
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    "《${initialTask.song.title}》下载失败：${e.message ?: "请检查网络或权限"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun downloadAudioStreamToFile(
        url: String,
        destFile: File,
        expectedSize: Long,
        onProgress: (Long, Long) -> Unit
    ) {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", "Android15-1070-11440-46-0-DiscoveryDRADProtocol-wifi")
            .header("Referer", "https://www.kugou.com/")
            .get()
            .build()

        NetworkClientProvider.baseClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw IllegalStateException("音频下载响应异常 (HTTP ${resp.code})")
            }
            val body = resp.body ?: throw IllegalStateException("音频响应体为空")
            val total = body.contentLength().takeIf { it > 0L } ?: expectedSize
            var downloaded = 0L
            var lastNotifyBytes = 0L
            destFile.parentFile?.mkdirs()
            body.byteStream().use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastNotifyBytes >= 128 * 1024L || downloaded == total) {
                            lastNotifyBytes = downloaded
                            onProgress(downloaded, total)
                        }
                    }
                    output.flush()
                }
            }
        }
    }

    /**
     * 获取歌曲高清专辑封面图片字节（优先 1000x1000 原图，其次 480x480，若 Song 缺少 coverUrl 则通过 albumId 查询专辑详情，对齐 kgmusicbot get_album_cover）
     */
    private suspend fun fetchHighDefCoverBytes(song: Song): ByteArray? = withContext(Dispatchers.IO) {
        var baseCoverUrl = song.coverUrl.trim()
        if (baseCoverUrl.isBlank() && song.albumId.isNotBlank() && song.albumId != "0") {
            baseCoverUrl = runCatching {
                VipManager.music.albumDetail(song.albumId)?.coverUrl.orEmpty()
            }.getOrDefault("")
        }
        val candidates = buildHighDefCoverUrlCandidates(baseCoverUrl)
        for (url in candidates) {
            val bytes = runCatching {
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    .header("Referer", "https://www.kugou.com/")
                    .get()
                    .build()
                NetworkClientProvider.baseClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        resp.body?.bytes()?.takeIf { it.size > 512 }
                    } else null
                }
            }.getOrNull()
            if (bytes != null) return@withContext bytes
        }
        null
    }

    /**
     * 将临时文件导出到用户自定义的保存路径：
     * 1. 优先尝试直接通过 `java.io.File` 写入目标自定义目录并通知系统 `MediaScannerConnection` 扫描入库；
     * 2. 若在 Android 10+ 未授予全盘文件访问且直接写入受 Scoped Storage 限制，则自动映射 `RELATIVE_PATH` 通过 `MediaStore` 写入对应目录。
     */
    private fun exportFileToTargetDirectory(
        context: Context,
        sourceFile: File,
        targetDirPath: String,
        fileName: String,
        mimeType: String,
        isAudio: Boolean
    ): String {
        val targetDir = File(targetDirPath)
        val targetFile = File(targetDir, fileName)

        // 1. 尝试直接文件系统写入
        val directSuccess = runCatching {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            sourceFile.copyTo(targetFile, overwrite = true)
            targetFile.exists() && targetFile.length() > 0L
        }.getOrDefault(false)

        if (directSuccess) {
            runCatching {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            }
            return targetFile.absolutePath
        }

        // 2. Android 10+ (API 29+) MediaStore 兜底写入（将自定义路径映射为公共目录相对路径）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relativePath = resolveMediaStoreRelativePath(targetDirPath, isAudio = isAudio)
            val resolver = context.contentResolver
            val collection = if (isAudio && relativePath.startsWith(Environment.DIRECTORY_MUSIC)) {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Downloads.EXTERNAL_CONTENT_URI
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
            if (uri != null) {
                try {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: error("无法打开 MediaStore 输出流")
                    resolver.update(
                        uri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null
                    )
                    return "/storage/emulated/0/$relativePath/$fileName".replace("//", "/")
                } catch (e: Exception) {
                    resolver.delete(uri, null, null)
                    throw e
                }
            }
        }

        // 3. 最终兜底：应用专属外部 Music 目录（免任何权限 100% 可写）
        val appExternalDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "BreezeMusic").apply { mkdirs() }
        val fallbackFile = File(appExternalDir, fileName)
        sourceFile.copyTo(fallbackFile, overwrite = true)
        return fallbackFile.absolutePath
    }

    /**
     * 独立保存 `.lrc` 歌词文件到用户自定义的歌词保存路径
     */
    internal fun saveStandaloneLrcFile(
        context: Context,
        lyricDirPath: String,
        safeBaseName: String,
        lrcText: String
    ): String {
        if (lrcText.isBlank()) return ""
        val lrcFileName = "$safeBaseName.lrc"
        val tempDir = File(context.cacheDir, "music_downloads_staging").apply { mkdirs() }
        val tempLrc = File(tempDir, "tmp_$lrcFileName")
        return try {
            tempLrc.writeText(lrcText, Charsets.UTF_8)
            exportFileToTargetDirectory(
                context = context,
                sourceFile = tempLrc,
                targetDirPath = lyricDirPath,
                fileName = lrcFileName,
                mimeType = "application/octet-stream",
                isAudio = false
            )
        } catch (_: Exception) {
            ""
        } finally {
            tempLrc.delete()
        }
    }

    internal fun resolveMediaStoreRelativePath(rawPath: String, isAudio: Boolean): String {
        val cleaned = rawPath.trim()
            .removePrefix("/storage/emulated/0/")
            .removePrefix("/sdcard/")
            .trim('/')
        if (cleaned.isBlank()) {
            return if (isAudio) "${Environment.DIRECTORY_MUSIC}/BreezeMusic" else "${Environment.DIRECTORY_DOWNLOADS}/BreezeMusic/Lyrics"
        }
        val allowedRoots = listOf(
            Environment.DIRECTORY_MUSIC,
            Environment.DIRECTORY_DOWNLOADS,
            Environment.DIRECTORY_DOCUMENTS,
            Environment.DIRECTORY_PODCASTS
        )
        if (allowedRoots.any { cleaned.startsWith(it, ignoreCase = true) }) {
            return cleaned
        }
        return if (isAudio) {
            "${Environment.DIRECTORY_MUSIC}/$cleaned"
        } else {
            "${Environment.DIRECTORY_DOWNLOADS}/$cleaned"
        }
    }
}
