package com.concept.music.data.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Immutable
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.service.AppPermissionManager
import com.google.gson.JsonObject
import java.net.URLEncoder

enum class ShareTargetEdition(
    val label: String,
    val shortBadge: String,
    val description: String
) {
    UNIVERSAL(
        label = "双端通用唤醒（推荐）",
        shortBadge = "概念版 + 标准版",
        description = "同时附带酷狗概念版与酷狗音乐标准版唤醒链接及网页试听页，对方安装任一版本均可直接唤起播放"
    ),
    CONCEPT_EDITION(
        label = "酷狗概念版专属唤醒",
        shortBadge = "酷狗概念版",
        description = "直接唤醒对方手机上的官方「酷狗概念版」(com.kugou.android.lite) 定位播放"
    ),
    STANDARD_EDITION(
        label = "酷狗标准版专属唤醒",
        shortBadge = "酷狗音乐",
        description = "直接唤醒对方手机上的官方「酷狗音乐」(com.kugou.android) 定位播放"
    )
}

@Immutable
data class ShareMusicPayload(
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val isPlaylist: Boolean,
    val rawWakeupJson: String,
    val rawInnerJsonStr: String,
    val conceptSchemeUri: String,
    val standardSchemeUri: String,
    val conceptWakeupWebUrl: String,
    val conceptMiddleH5Url: String,
    val standardWakeupWebUrl: String,
    val standardWeixinH5Url: String,
    val standardH5PlayUrl: String,
    val shareTextForConcept: String,
    val shareTextForStandard: String,
    val shareTextUniversal: String
) {
    fun shareTextForEdition(edition: ShareTargetEdition): String = when (edition) {
        ShareTargetEdition.UNIVERSAL -> shareTextUniversal
        ShareTargetEdition.CONCEPT_EDITION -> shareTextForConcept
        ShareTargetEdition.STANDARD_EDITION -> shareTextForStandard
    }

    fun primaryUrlForEdition(edition: ShareTargetEdition): String = when (edition) {
        ShareTargetEdition.UNIVERSAL -> conceptWakeupWebUrl
        ShareTargetEdition.CONCEPT_EDITION -> conceptWakeupWebUrl
        ShareTargetEdition.STANDARD_EDITION -> standardWakeupWebUrl
    }
}

/**
 * 官方酷狗音乐 (`com.kugou.android`) 与官方酷狗概念版 (`com.kugou.android.lite`)
 * 双向唤醒载荷与分享链接构建器。
 */
object KugouShareManager {
    const val PKG_KUGOU_CONCEPT = "com.kugou.android.lite"
    const val PKG_KUGOU_STANDARD = "com.kugou.android"

    private const val CKEY_CONCEPT = "CK1486481175239"
    private const val CKEY_STANDARD = "CK1350585625598"

    private fun urlEncode(value: String): String {
        return runCatching {
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")
        }.getOrDefault(value)
    }

    private fun bestStandardMp3HashOf(song: Song): String {
        return listOf(
            song.mp3Hash128,
            song.hash128,
            song.hash,
            song.hash320,
            song.hashFlac
        ).firstOrNull { it.trim().length >= 32 }?.trim()?.uppercase().orEmpty()
    }

    /**
     * 构建单曲分享与官方双端唤醒载荷
     * - 酷狗概念版 (`com.kugou.android.lite`): `cmd = 103301` + 官方概念版 H5 (`h5.kugou.com/v2/v-5a15aeb1/`)
     * - 酷狗标准版 (`com.kugou.android`): `cmd = 212, type = 1, action = 1` + 官方标准版 H5 (`m.kugou.com/share/?action=single`)
     */
    fun buildSongSharePayload(song: Song): ShareMusicPayload {
        val cleanTitle = song.title.trim().ifBlank { "未知歌曲" }
        val cleanArtist = song.artist.trim().ifBlank { "未知歌手" }
        val mp3Hash = bestStandardMp3HashOf(song)
        val hash320 = song.hash320.trim().takeIf { it.length >= 32 }?.uppercase().orEmpty()
        val hashFlac = song.hashFlac.trim().takeIf { it.length >= 32 }?.uppercase().orEmpty()
        val cleanAlbumId = song.albumId.trim().takeIf { it.isNotEmpty() && it != "0" } ?: "0"
        val albumIdLong = cleanAlbumId.toLongOrNull() ?: 0L
        val resolvedAlbumAudioId = (
            song.albumAudioId.takeIf { it > 0L && it != song.audioId }
                ?: song.mixSongId.trim().toLongOrNull()?.takeIf { it > 0L && it != song.audioId }
                ?: song.albumAudioId.takeIf { it > 0L }
                ?: song.mixSongId.trim().toLongOrNull()?.takeIf { it > 0L }
                ?: song.audioId.takeIf { it > 0L }
                ?: 0L
            ).toString()
        val durationSec = ((song.durationMs / 1000L).takeIf { it > 0L } ?: 213L)
        val fileSize128 = song.fileSize128.takeIf { it > 0L }
            ?: (durationSec * 16_000L)
        val fileSize320 = song.fileSize320.takeIf { it > 0L }
            ?: if (hash320.isNotBlank()) (durationSec * 40_000L) else 0L
        val fileSizeFlac = song.fileSizeFlac.takeIf { it > 0L }
            ?: if (hashFlac.isNotBlank()) (durationSec * 115_000L) else 0L
        val fileName = "$cleanArtist - $cleanTitle"

        // 1. 官方酷狗概念版专属唤醒载荷 (cmd = 103301，对齐 h5.kugou.com/v2/v-5a15aeb1/ index.39fe443c.chunk.js)
        val conceptInnerJson = JsonObject().apply {
            addProperty("filename", fileName)
            addProperty("filesize", fileSize128)
            addProperty("hash", mp3Hash)
            addProperty("bitrate", 128)
            addProperty("extname", "mp3")
            addProperty("duration", durationSec)
            addProperty("mvhash", "")
            addProperty("320hash", hash320)
            addProperty("320filesize", fileSize320)
            addProperty("sqhash", hashFlac)
            addProperty("sqfilesize", fileSizeFlac)
            addProperty("isplay", 1)
            addProperty("action", 1)
            addProperty("320privilege", 10)
            addProperty("privilege", 10)
            addProperty("sqprivilege", 10)
            addProperty("album_id", albumIdLong)
            addProperty("album_audio_id", resolvedAlbumAudioId)
        }
        val conceptOuterJson = JsonObject().apply {
            addProperty("cmd", 103301)
            add("jsonStr", conceptInnerJson)
        }

        // 2. 官方酷狗音乐标准版专属唤醒载荷 (cmd = 212，对齐 openKugouApp_0.12.4.min.js)
        val standardInnerJson = JsonObject().apply {
            addProperty("hash", mp3Hash)
            addProperty("filename", fileName)
            addProperty("filesize", fileSize128)
            addProperty("bitrate", 128)
            addProperty("duration", durationSec)
            addProperty("extname", "mp3")
            addProperty("mvhash", "")
            addProperty("320hash", hash320)
            addProperty("320filesize", fileSize320)
            addProperty("sqhash", hashFlac)
            addProperty("sqfilesize", fileSizeFlac)
            addProperty("privilege", 10)
            addProperty("320privilege", 10)
            addProperty("sqprivilege", 10)
            addProperty("album_audio_id", resolvedAlbumAudioId)
            if (cleanAlbumId != "0") {
                addProperty("album_id", cleanAlbumId)
            }
            addProperty("needfav", false)
            addProperty("share_subtype", 0)
        }
        val standardOuterJson = JsonObject().apply {
            addProperty("cmd", 212)
            addProperty("type", 1)
            addProperty("action", 1)
            addProperty("need_toast", 0)
            add("jsonStr", standardInnerJson)
        }

        val rawInnerStr = standardInnerJson.toString()
        val rawOuterStr = standardOuterJson.toString()
        val encodedConceptOuter = urlEncode(conceptOuterJson.toString())
        val encodedStandardOuter = urlEncode(rawOuterStr)

        val conceptScheme = "kugouyouth://start.weixin?$encodedConceptOuter"
        val standardScheme = "kugou://start.weixin?$encodedStandardOuter"

        // 使用酷狗官方 H5 落地页替代 a.app.qq.com，彻底消除 ColorOS 误识别为「儿歌点点」的问题
        val conceptWakeupWebUrl = buildString {
            append("https://h5.kugou.com/v2/v-5a15aeb1/index.html?hash=")
            append(mp3Hash.lowercase())
            append("&album_id=").append(cleanAlbumId)
            append("&album_audio_id=").append(resolvedAlbumAudioId)
        }
        val conceptMiddleH5Url =
            "https://activity.kugou.com/middle2native/v-c8ba3018/index.html?cmd=103301&params=${urlEncode(conceptInnerJson.toString())}&data=$encodedConceptOuter"

        val standardWakeupWebUrl = buildString {
            append("https://m.kugou.com/share/?action=single&hash=")
            append(mp3Hash.lowercase())
            append("&album_id=").append(cleanAlbumId)
            append("&album_audio_id=").append(resolvedAlbumAudioId)
        }
        val standardWeixinH5Url =
            "https://m3ws.kugou.com/share/weixin.html?data=$encodedStandardOuter"
        val standardH5PlayUrl = buildString {
            append("https://www.kugou.com/song/#hash=").append(mp3Hash)
            if (cleanAlbumId != "0") {
                append("&album_id=").append(cleanAlbumId)
            }
            if (resolvedAlbumAudioId != "0") {
                append("&album_audio_id=").append(resolvedAlbumAudioId)
            }
        }

        val displaySubtitle = buildString {
            append(cleanArtist)
            if (song.album.isNotBlank()) {
                append(" · 《").append(song.album.trim()).append("》")
            }
        }

        val shareTextForConcept = buildString {
            append("🎵 分享歌曲：").append(cleanArtist).append("《").append(cleanTitle).append("》\n")
            append("👉 酷狗概念版收听：").append(conceptWakeupWebUrl)
        }

        val shareTextForStandard = buildString {
            append("🎵 分享歌曲：").append(cleanArtist).append("《").append(cleanTitle).append("》\n")
            append("👉 酷狗音乐收听：").append(standardWakeupWebUrl)
        }

        val shareTextUniversal = buildString {
            append("🎵 分享 ").append(cleanArtist).append(" 的歌曲《").append(cleanTitle).append("》\n")
            append("✨ 酷狗概念版直达：").append(conceptWakeupWebUrl).append("\n")
            append("🎧 酷狗音乐直达：").append(standardWakeupWebUrl)
        }

        return ShareMusicPayload(
            title = cleanTitle,
            subtitle = displaySubtitle,
            coverUrl = song.coverUrl,
            isPlaylist = false,
            rawWakeupJson = rawOuterStr,
            rawInnerJsonStr = rawInnerStr,
            conceptSchemeUri = conceptScheme,
            standardSchemeUri = standardScheme,
            conceptWakeupWebUrl = conceptWakeupWebUrl,
            conceptMiddleH5Url = conceptMiddleH5Url,
            standardWakeupWebUrl = standardWakeupWebUrl,
            standardWeixinH5Url = standardWeixinH5Url,
            standardH5PlayUrl = standardH5PlayUrl,
            shareTextForConcept = shareTextForConcept,
            shareTextForStandard = shareTextForStandard,
            shareTextUniversal = shareTextUniversal
        )
    }

    /**
     * 构建歌单分享与官方双端唤醒载荷
     * - 酷狗概念版 (`com.kugou.android.lite`): `cmd = 103302` + 官方概念版歌单 H5 (`activity.kugou.com/share/v-a00a45b0/`)
     * - 酷狗标准版 (`com.kugou.android`): `cmd = 302, type = 9, action = 0` + 官方标准版歌单 H5 (`m.kugou.com/share/zlist.html`)
     */
    fun buildPlaylistSharePayload(playlist: Playlist): ShareMusicPayload {
        val cleanTitle = playlist.title.trim().ifBlank { "精选歌单" }
        val gid = playlist.globalCollectionId.trim()
            .ifBlank { playlist.listCreateGid.trim() }
            .ifBlank { playlist.id.trim().takeIf { it.startsWith("collection_", ignoreCase = true) }.orEmpty() }

        val embeddedNumericFromGid = if (gid.startsWith("collection_", ignoreCase = true)) {
            gid.split("_").getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        } else {
            null
        }
        val embeddedCreatorFromGid = if (gid.startsWith("collection_", ignoreCase = true)) {
            gid.split("_").getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        } else {
            null
        }

        val numericListIdStr = embeddedNumericFromGid
            ?: playlist.listCreateListid.trim().takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
            ?: playlist.listId.takeIf { it > 0L }?.toString()
            ?: "2"
        val numericListId = numericListIdStr.toLongOrNull() ?: 2L

        val specialIdStr = playlist.specialId.trim().takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
            ?: playlist.id.trim().takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
            ?: playlist.listCreateListid.trim().takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) && it != embeddedNumericFromGid }
            ?: numericListIdStr
        val specialIdLong = specialIdStr.toLongOrNull() ?: numericListId

        val creatorUid = playlist.listCreateUserid.trim().takeIf { it.isNotEmpty() && it != "0" }
            ?: embeddedCreatorFromGid
            ?: "0"

        // 1. 官方酷狗概念版歌单专属唤醒载荷 (cmd = 103302，对齐 activity.kugou.com/share/v-a00a45b0/ index.31270fba.chunk.js)
        val conceptInnerJson = JsonObject().apply {
            addProperty("listName", cleanTitle)
            addProperty("userID", creatorUid)
            addProperty("listID", numericListId)
            addProperty("specialID", specialIdLong)
            addProperty("globalCollectionId", gid)
            addProperty("ctype", 1)
            addProperty("isplay", 1)
        }
        val conceptOuterJson = JsonObject().apply {
            addProperty("cmd", 103302)
            add("jsonStr", conceptInnerJson)
        }

        // 2. 官方酷狗音乐标准版歌单专属唤醒载荷 (cmd = 302，对齐 m.kugou.com/plist/list/ openClientPlayList)
        val standardInnerJson = JsonObject().apply {
            addProperty("listName", cleanTitle)
            addProperty("userID", creatorUid)
            addProperty("listID", specialIdLong)
            addProperty("ctype", "1")
            addProperty("isplay", "2")
            addProperty("specialID", specialIdLong)
            addProperty("globalCollectionId", gid)
            addProperty("share_subtype", "0")
        }
        val standardOuterJson = JsonObject().apply {
            addProperty("cmd", 302)
            addProperty("type", 9)
            addProperty("action", 0)
            addProperty("need_toast", 0)
            add("jsonStr", standardInnerJson)
        }

        val rawInnerStr = standardInnerJson.toString()
        val rawOuterStr = standardOuterJson.toString()
        val encodedConceptOuter = urlEncode(conceptOuterJson.toString())
        val encodedStandardOuter = urlEncode(rawOuterStr)

        val conceptScheme = "kugouyouth://start.weixin?$encodedConceptOuter"
        val standardScheme = "kugou://start.weixin?$encodedStandardOuter"

        val conceptWakeupWebUrl = buildString {
            append("https://activity.kugou.com/share/v-a00a45b0/index.html?specialid=")
            append(specialIdLong)
            if (gid.isNotBlank()) {
                append("&global_specialid=").append(urlEncode(gid))
            }
            append("&cType=1")
        }
        val conceptMiddleH5Url =
            "https://activity.kugou.com/middle2native/v-c8ba3018/index.html?cmd=103302&params=${urlEncode(conceptInnerJson.toString())}&data=$encodedConceptOuter"

        val standardWakeupWebUrl = buildString {
            append("https://m.kugou.com/plist/list/").append(specialIdLong).append("/")
            if (gid.isNotBlank()) {
                append("?global_collection_id=").append(urlEncode(gid))
            }
        }
        val standardWeixinH5Url =
            "https://m3ws.kugou.com/share/weixin.html?data=$encodedStandardOuter"
        val standardH5PlayUrl = standardWakeupWebUrl

        val displaySubtitle = buildString {
            if (playlist.creatorName.isNotBlank()) {
                append(playlist.creatorName.trim())
            } else {
                append("精选歌单")
            }
            if (playlist.trackCount > 0) {
                append(" · 共 ").append(playlist.trackCount).append(" 首")
            }
        }

        val shareTextForConcept = buildString {
            append("📀 分享歌单《").append(cleanTitle).append("》")
            if (playlist.trackCount > 0) append("（共 ").append(playlist.trackCount).append(" 首）")
            append("\n")
            append("👉 酷狗概念版打开：").append(conceptWakeupWebUrl)
        }

        val shareTextForStandard = buildString {
            append("📀 分享歌单《").append(cleanTitle).append("》")
            if (playlist.trackCount > 0) append("（共 ").append(playlist.trackCount).append(" 首）")
            append("\n")
            append("👉 酷狗音乐打开：").append(standardWakeupWebUrl)
        }

        val shareTextUniversal = buildString {
            append("📀 分享歌单《").append(cleanTitle).append("》")
            if (playlist.trackCount > 0) append("（共 ").append(playlist.trackCount).append(" 首）")
            append("\n")
            append("✨ 酷狗概念版直达：").append(conceptWakeupWebUrl).append("\n")
            append("🎧 酷狗音乐直达：").append(standardWakeupWebUrl)
        }

        return ShareMusicPayload(
            title = cleanTitle,
            subtitle = displaySubtitle,
            coverUrl = playlist.coverUrl,
            isPlaylist = true,
            rawWakeupJson = rawOuterStr,
            rawInnerJsonStr = rawInnerStr,
            conceptSchemeUri = conceptScheme,
            standardSchemeUri = standardScheme,
            conceptWakeupWebUrl = conceptWakeupWebUrl,
            conceptMiddleH5Url = conceptMiddleH5Url,
            standardWakeupWebUrl = standardWakeupWebUrl,
            standardWeixinH5Url = standardWeixinH5Url,
            standardH5PlayUrl = standardH5PlayUrl,
            shareTextForConcept = shareTextForConcept,
            shareTextForStandard = shareTextForStandard,
            shareTextUniversal = shareTextUniversal
        )
    }

    /**
     * 调用系统原生分享面板 (`ACTION_SEND`)
     */
    fun shareViaSystemSheet(
        context: Context,
        payload: ShareMusicPayload,
        edition: ShareTargetEdition = ShareTargetEdition.UNIVERSAL
    ) {
        val shareText = payload.shareTextForEdition(edition)
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, payload.title)
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            val chooser = Intent.createChooser(sendIntent, "分享「${payload.title}」").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }.onFailure {
            Toast.makeText(context, "无法调起系统分享面板", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 复制分享口令与唤醒链接到剪贴板（严格遵循「设置 - 权限管理 - 剪贴板读取与写入」开关）
     */
    fun copyShareText(
        context: Context,
        payload: ShareMusicPayload,
        edition: ShareTargetEdition = ShareTargetEdition.UNIVERSAL
    ): Boolean {
        val text = payload.shareTextForEdition(edition)
        return AppPermissionManager.copyTextToClipboard(
            context = context,
            label = payload.title,
            text = text,
            toastMessage = "已复制「${payload.title}」分享口令与唤醒链接"
        )
    }

    /**
     * 仅复制纯唤醒直达链接（严格遵循剪贴板权限开关）
     */
    fun copyDirectWakeupUrl(
        context: Context,
        payload: ShareMusicPayload,
        edition: ShareTargetEdition
    ): Boolean {
        val url = payload.primaryUrlForEdition(edition)
        return AppPermissionManager.copyTextToClipboard(
            context = context,
            label = "${payload.title} 唤醒链接",
            text = url,
            toastMessage = "已复制${edition.shortBadge}唤醒直达链接"
        )
    }

    /**
     * 直接唤醒官方「酷狗概念版」(`com.kugou.android.lite`) 播放该歌曲或打开该歌单；
     * 若本机未安装概念版，则打开概念版官方唤醒网页。
     */
    fun launchOfficialKugouConcept(context: Context, payload: ShareMusicPayload) {
        val schemeUri = Uri.parse(payload.conceptSchemeUri)
        val launchedExplicit = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                setPackage(PKG_KUGOU_CONCEPT)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launchedExplicit) return

        val launchedImplicit = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launchedImplicit) return

        runCatching {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(payload.conceptWakeupWebUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }.onFailure {
            Toast.makeText(context, "未检测到酷狗概念版，且无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 直接唤醒官方「酷狗音乐标准版」(`com.kugou.android`) 播放该歌曲或打开该歌单；
     * 若本机未安装标准版，则打开标准版官方唤醒网页。
     */
    fun launchOfficialKugouStandard(context: Context, payload: ShareMusicPayload) {
        val schemeUri = Uri.parse(payload.standardSchemeUri)
        val launchedExplicit = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                setPackage(PKG_KUGOU_STANDARD)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launchedExplicit) return

        val launchedImplicit = runCatching {
            val intent = Intent(Intent.ACTION_VIEW, schemeUri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)

        if (launchedImplicit) return

        runCatching {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse(payload.standardWakeupWebUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }.onFailure {
            Toast.makeText(context, "未检测到酷狗音乐，且无法打开浏览器", Toast.LENGTH_SHORT).show()
        }
    }
}
