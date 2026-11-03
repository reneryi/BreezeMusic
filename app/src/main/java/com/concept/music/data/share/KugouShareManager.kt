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

    private fun bestHashOf(song: Song): String {
        return listOf(
            song.hash,
            song.hash128,
            song.hash320,
            song.hashFlac,
            song.hashHiRes
        ).firstOrNull { it.trim().length >= 32 }?.trim()?.uppercase().orEmpty()
    }

    /**
     * 构建单曲分享与官方双端唤醒载荷 (`cmd = 212, type = 1, action = 1`)
     */
    fun buildSongSharePayload(song: Song): ShareMusicPayload {
        val cleanTitle = song.title.trim().ifBlank { "未知歌曲" }
        val cleanArtist = song.artist.trim().ifBlank { "未知歌手" }
        val cleanHash = bestHashOf(song)
        val cleanAlbumId = song.albumId.trim().takeIf { it.isNotEmpty() && it != "0" } ?: "0"
        val resolvedAlbumAudioId = song.mixSongId.trim().takeIf { it.isNotEmpty() && it != "0" }
            ?: song.albumAudioId.takeIf { it > 0L }?.toString()
            ?: song.audioId.takeIf { it > 0L }?.toString()
            ?: "0"
        val durationSec = ((song.durationMs / 1000L).takeIf { it > 0L } ?: 210L)
        val fileName = "$cleanArtist - $cleanTitle"

        val innerJson = JsonObject().apply {
            addProperty("hash", cleanHash)
            addProperty("filename", fileName)
            addProperty("bitrate", 128)
            addProperty("duration", durationSec)
            addProperty("extname", "mp3")
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

        val outerJson = JsonObject().apply {
            addProperty("cmd", 212)
            addProperty("type", 1)
            addProperty("action", 1)
            addProperty("need_toast", 0)
            add("jsonStr", innerJson)
        }

        val rawInnerStr = innerJson.toString()
        val rawOuterStr = outerJson.toString()
        val encodedOuter = urlEncode(rawOuterStr)
        val encodedInner = urlEncode(rawInnerStr)

        val conceptScheme = "kugouyouth://start.weixin?$encodedOuter"
        val standardScheme = "kugou://start.weixin?$encodedOuter"

        val conceptWakeupWebUrl =
            "https://a.app.qq.com/o/simple.jsp?pkgname=$PKG_KUGOU_CONCEPT&ckey=$CKEY_CONCEPT&android_schema=${urlEncode(conceptScheme)}&schema_version=7950"
        val conceptMiddleH5Url =
            "https://activity.kugou.com/middle2native/v-c8ba3018/index.html?cmd=212&params=$encodedInner&data=$encodedOuter"

        val standardWakeupWebUrl =
            "https://a.app.qq.com/o/simple.jsp?pkgname=$PKG_KUGOU_STANDARD&ckey=$CKEY_STANDARD&android_schema=${urlEncode(standardScheme)}"
        val standardWeixinH5Url =
            "https://m3ws.kugou.com/share/weixin.html?data=$encodedOuter"
        val standardH5PlayUrl = buildString {
            append("https://www.kugou.com/song/#hash=").append(cleanHash)
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
            append("👉 唤起酷狗概念版收听：").append(conceptWakeupWebUrl).append("\n")
            append("🌐 网页试听：").append(standardH5PlayUrl)
        }

        val shareTextForStandard = buildString {
            append("🎵 分享歌曲：").append(cleanArtist).append("《").append(cleanTitle).append("》\n")
            append("👉 唤起酷狗音乐收听：").append(standardWakeupWebUrl).append("\n")
            append("🌐 网页试听：").append(standardH5PlayUrl)
        }

        val shareTextUniversal = buildString {
            append("🎵 分享 ").append(cleanArtist).append(" 的歌曲《").append(cleanTitle).append("》\n")
            append("✨ 酷狗概念版直达：").append(conceptWakeupWebUrl).append("\n")
            append("🎧 酷狗音乐直达：").append(standardWakeupWebUrl).append("\n")
            append("🌐 网页试听：").append(standardH5PlayUrl)
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
     * 构建歌单分享与官方双端唤醒载荷 (`cmd = 302, type = 9, action = 0`)
     */
    fun buildPlaylistSharePayload(playlist: Playlist): ShareMusicPayload {
        val cleanTitle = playlist.title.trim().ifBlank { "精选歌单" }
        val gid = playlist.globalCollectionId.trim()
            .ifBlank { playlist.listCreateGid.trim() }
            .ifBlank { playlist.id.trim() }

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

        val numericListIdStr = playlist.listCreateListid.trim().takeIf { it.isNotEmpty() && it != "0" && it.all(Char::isDigit) }
            ?: embeddedNumericFromGid
            ?: playlist.listId.takeIf { it > 0L }?.toString()
            ?: playlist.id.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
            ?: "0"
        val numericListId = numericListIdStr.toLongOrNull() ?: 0L

        val creatorUid = playlist.listCreateUserid.trim().takeIf { it.isNotEmpty() && it != "0" }
            ?: embeddedCreatorFromGid
            ?: "0"

        val innerJson = JsonObject().apply {
            addProperty("listName", cleanTitle)
            addProperty("userID", creatorUid)
            addProperty("listID", numericListId)
            addProperty("ctype", "1")
            addProperty("isplay", "2")
            addProperty("specialID", numericListId)
            addProperty("globalCollectionId", gid)
            addProperty("share_subtype", "0")
        }

        val outerJson = JsonObject().apply {
            addProperty("cmd", 302)
            addProperty("type", 9)
            addProperty("action", 0)
            addProperty("need_toast", 0)
            add("jsonStr", innerJson)
        }

        val rawInnerStr = innerJson.toString()
        val rawOuterStr = outerJson.toString()
        val encodedOuter = urlEncode(rawOuterStr)
        val encodedInner = urlEncode(rawInnerStr)

        val conceptScheme = "kugouyouth://start.weixin?$encodedOuter"
        val standardScheme = "kugou://start.weixin?$encodedOuter"

        val conceptWakeupWebUrl =
            "https://a.app.qq.com/o/simple.jsp?pkgname=$PKG_KUGOU_CONCEPT&ckey=$CKEY_CONCEPT&android_schema=${urlEncode(conceptScheme)}&schema_version=7950"
        val conceptMiddleH5Url =
            "https://activity.kugou.com/middle2native/v-c8ba3018/index.html?cmd=302&params=$encodedInner&data=$encodedOuter"

        val standardWakeupWebUrl =
            "https://a.app.qq.com/o/simple.jsp?pkgname=$PKG_KUGOU_STANDARD&ckey=$CKEY_STANDARD&android_schema=${urlEncode(standardScheme)}"
        val standardWeixinH5Url =
            "https://m3ws.kugou.com/share/weixin.html?data=$encodedOuter"
        val standardH5PlayUrl = buildString {
            append("https://www.kugou.com/share/zlist.html?listid=")
            append(if (numericListId > 0L) numericListId.toString() else "2")
            append("&type=0&uid=").append(creatorUid)
            append("&global_collection_id=").append(urlEncode(gid))
            append("&share_type=collect")
        }

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
            append("👉 唤起酷狗概念版打开：").append(conceptWakeupWebUrl).append("\n")
            append("🌐 网页查看：").append(standardH5PlayUrl)
        }

        val shareTextForStandard = buildString {
            append("📀 分享歌单《").append(cleanTitle).append("》")
            if (playlist.trackCount > 0) append("（共 ").append(playlist.trackCount).append(" 首）")
            append("\n")
            append("👉 唤起酷狗音乐打开：").append(standardWakeupWebUrl).append("\n")
            append("🌐 网页查看：").append(standardH5PlayUrl)
        }

        val shareTextUniversal = buildString {
            append("📀 分享歌单《").append(cleanTitle).append("》")
            if (playlist.trackCount > 0) append("（共 ").append(playlist.trackCount).append(" 首）")
            append("\n")
            append("✨ 酷狗概念版直达：").append(conceptWakeupWebUrl).append("\n")
            append("🎧 酷狗音乐直达：").append(standardWakeupWebUrl).append("\n")
            append("🌐 网页查看：").append(standardH5PlayUrl)
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
