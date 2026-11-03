package com.concept.music.data.share

import com.concept.music.data.kugou.NetworkClientProvider
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionType
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI
import java.net.URLDecoder
import java.util.Base64

sealed class ParsedKugouShareDescriptor {
    data class SingleSongDescriptor(
        val hash: String = "",
        val hash320: String = "",
        val hashFlac: String = "",
        val albumId: String = "",
        val albumAudioId: String = "",
        val audioId: String = "",
        val titleHint: String = "",
        val artistHint: String = "",
        val albumNameHint: String = "",
        val coverUrlHint: String = "",
        val durationSecHint: Long = 0L,
        val sourceUrl: String = ""
    ) : ParsedKugouShareDescriptor() {
        val isComplete: Boolean
            get() = hash.length >= 32 || albumAudioId.isNotBlank()
    }

    data class PlaylistDescriptor(
        val globalCollectionId: String = "",
        val specialId: String = "",
        val listId: String = "",
        val uid: String = "",
        val titleHint: String = "",
        val creatorNameHint: String = "",
        val coverUrlHint: String = "",
        val embeddedSongs: List<SingleSongDescriptor> = emptyList(),
        val sourceUrl: String = ""
    ) : ParsedKugouShareDescriptor() {
        val preferredPlaylistId: String
            get() = globalCollectionId.ifBlank { specialId }.ifBlank { listId }

        val isComplete: Boolean
            get() = preferredPlaylistId.isNotBlank() || embeddedSongs.isNotEmpty()
    }
}

sealed class ResolvedSharedMusicTarget {
    data class SharedSong(val song: Song) : ResolvedSharedMusicTarget()
    data class SharedPlaylist(
        val playlistId: String,
        val playlist: Playlist?,
        val fallbackSongs: List<Song> = emptyList()
    ) : ResolvedSharedMusicTarget()
}

/**
 * 酷狗音乐标准版与酷狗概念版分享链接/口令全格式解析器：
 * 支持：
 * 1. `kugouyouth://start.weixin?...` / `kugou://start.weixin?...` / `kugouurl://start.weixin?...`
 * 2. 腾讯应用宝微下载直达链 `https://a.app.qq.com/o/simple.jsp?pkgname=com.kugou.android...&android_schema=...`
 * 3. 酷狗概念版 H5 唤端页 `https://activity.kugou.com/middle2native/...`
 * 4. 酷狗短链 `https://t1.kugou.com/...`、`https://t.kugou.com/...`、`https://t4.kugou.com/...`、`https://kgurl.cn/...`
 * 5. 移动端落地页 `https://m.kugou.com/mixsong/xxx.html`、`https://m3ws.kugou.com/plist/list/xxx`、`https://m3ws.kugou.com/share/weixin.html`
 * 6. 桌面端分享页 `https://www.kugou.com/song/#hash=...`、`https://www.kugou.com/share/zlist.html?...`
 */
object KugouShareLinkParser {

    private val SCHEME_REGEX = Regex(
        """kugou(?:youth|url|URL)?://start\.(?:weixin|music)\?[^\s"'<>]+""",
        RegexOption.IGNORE_CASE
    )

    private val HTTP_URL_REGEX = Regex(
        """https?://[^\s"'<>|，。！？、；：（）【】《》“”‘’]+""",
        RegexOption.IGNORE_CASE
    )

    private val KUGOU_DOMAIN_SIGNAL_REGEX = Regex(
        """(?:kugou\.com|kgurl\.cn|kugouyouth://|kugou://|kugouurl://|com\.kugou\.android)""",
        RegexOption.IGNORE_CASE
    )

    private val HEX_32_REGEX = Regex("""^[0-9a-fA-F]{32}$""")

    private val PHP_PARAM_REGEX = Regex(
        """var\s+phpParam\s*=\s*(\{[\s\S]*?\})\s*;"""
    )

    private val SMARTY_DATA_REGEX = Regex(
        """var\s+dataFromSmarty\s*=\s*(\[[\s\S]*?\])\s*;"""
    )

    private val GLOBAL_COLLECTION_ID_VAR_REGEX = Regex(
        """(?:var\s+)?globalCollectionId\s*=\s*["']([^"']+)["']"""
    )

    private val SPECIAL_ID_VAR_REGEX = Regex(
        """(?:var\s+)?specialid\s*=\s*(?:Number\(\s*)?["']?(\d+)["']?\s*\)?"""
    )

    private val SPECIAL_NAME_VAR_REGEX = Regex(
        """(?:var\s+)?specialname\s*=\s*["']([^"']*)["']"""
    )

    private val DATA_GCID_ATTR_REGEX = Regex(
        """data-global-collection-id\s*=\s*["']([^"']+)["']"""
    )

    private val MIXSONG_PATH_REGEX = Regex(
        """/mixsong/([a-zA-Z0-9]+)\.html""",
        RegexOption.IGNORE_CASE
    )

    private const val MOBILE_UA =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private const val DESKTOP_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * 零网络开销快速检测文本中是否包含酷狗音乐或酷狗概念版分享特征
     */
    fun containsKugouShareSignal(rawText: String?): Boolean {
        val text = rawText?.trim().orEmpty()
        if (text.isEmpty()) return false
        return KUGOU_DOMAIN_SIGNAL_REGEX.containsMatchIn(text)
    }

    /**
     * 提取文本中的所有候选酷狗相关链接
     */
    fun extractCandidateUrls(rawText: String): List<String> {
        val results = LinkedHashSet<String>()
        SCHEME_REGEX.findAll(rawText).forEach { match ->
            results.add(cleanTrailingPunctuation(match.value))
        }
        HTTP_URL_REGEX.findAll(rawText).forEach { match ->
            val cleaned = cleanTrailingPunctuation(match.value)
            if (KUGOU_DOMAIN_SIGNAL_REGEX.containsMatchIn(cleaned)) {
                results.add(cleaned)
            }
        }
        return results.toList()
    }

    private fun cleanTrailingPunctuation(url: String): String {
        return url.trim().trimEnd(',', '.', '!', '?', ';', ':', ')', ']', '}', '>', '"', '\'', '，', '。', '！', '？', '；', '：', '）', '】', '》')
    }

    private fun urlDecodeRepeatedly(raw: String, maxDepth: Int = 3): String {
        var current = raw.trim()
        repeat(maxDepth) {
            val decoded = runCatching { URLDecoder.decode(current, "UTF-8") }.getOrDefault(current)
            if (decoded == current) return current
            current = decoded
        }
        return current
    }

    /**
     * 第一级（纯本地零网络开销）：直接从分享文本、Scheme、Query、Fragment 中解包歌曲或歌单标识
     */
    fun parseShareTextDirectly(rawText: String): ParsedKugouShareDescriptor? {
        val text = rawText.trim()
        if (text.isEmpty()) return null

        // 1. 直接匹配 kugouyouth:// 或 kugou:// Scheme
        SCHEME_REGEX.find(text)?.value?.let { schemeUrl ->
            parseSchemeUri(cleanTrailingPunctuation(schemeUrl))?.let { return it }
        }

        // 2. 扫描文本中的 HTTP URL
        val urls = extractCandidateUrls(text)
        for (url in urls) {
            if (url.startsWith("kugou", ignoreCase = true)) {
                parseSchemeUri(url)?.let { return it }
            } else {
                parseUrlParametersOnly(url)?.let { return it }
            }
        }

        // 3. 若整段文本本身就是 URL 编码过的或裸 JSON
        if (text.startsWith("{") && text.endsWith("}")) {
            parseWakeupJsonPayload(text, sourceUrl = "")?.let { return it }
        }

        return null
    }

    internal fun parseSchemeUri(schemeUri: String): ParsedKugouShareDescriptor? {
        val qIdx = schemeUri.indexOf('?')
        if (qIdx < 0 || qIdx + 1 >= schemeUri.length) return null
        val rawQuery = schemeUri.substring(qIdx + 1)
        val decoded = urlDecodeRepeatedly(rawQuery)
        if (decoded.trim().startsWith("{")) {
            parseWakeupJsonPayload(decoded.trim(), sourceUrl = schemeUri)?.let { return it }
        }
        val queryMap = parseQueryKeyValues(rawQuery)
        for (key in listOf("data", "params", "json", "Json", "jsonStr")) {
            val v = queryMap[key] ?: continue
            val decodedVal = urlDecodeRepeatedly(v)
            if (decodedVal.startsWith("{")) {
                parseWakeupJsonPayload(decodedVal, sourceUrl = schemeUri)?.let { return it }
            }
        }
        return null
    }

    internal fun parseUrlParametersOnly(url: String): ParsedKugouShareDescriptor? {
        val queryPart = url.substringAfter('?', "").substringBefore('#')
        val fragmentPart = url.substringAfter('#', "")

        val params = LinkedHashMap<String, String>()
        params.putAll(parseQueryKeyValues(queryPart))
        if (fragmentPart.isNotBlank()) {
            val cleanFrag = fragmentPart.removePrefix("?").removePrefix("/")
            params.putAll(parseQueryKeyValues(cleanFrag))
        }

        // 1) 检查 android_schema 参数 (应用宝微下载链接)
        params["android_schema"]?.let { schemaParam ->
            val decodedSchema = urlDecodeRepeatedly(schemaParam)
            parseSchemeUri(decodedSchema)?.let { return it }
            if (decodedSchema.startsWith("{")) {
                parseWakeupJsonPayload(decodedSchema, sourceUrl = url)?.let { return it }
            }
        }

        // 2) 检查 data / params / jsonStr 参数 (weixin.html / middle2native)
        for (jsonKey in listOf("data", "params", "jsonStr", "Json")) {
            val rawJsonParam = params[jsonKey] ?: continue
            val decodedJson = urlDecodeRepeatedly(rawJsonParam)
            if (decodedJson.trim().startsWith("{")) {
                parseWakeupJsonPayload(decodedJson.trim(), sourceUrl = url)?.let { return it }
            }
        }

        // 3) 检查歌单标识参数 (global_collection_id / gcid / specialid / listid)
        val gcid = (params["global_collection_id"] ?: params["gcid"] ?: params["globalCollectionId"]).orEmpty().trim()
        val specialId = (params["specialid"] ?: params["special_id"] ?: params["specialID"]).orEmpty().trim()
        val listId = (params["listid"] ?: params["list_id"] ?: params["listID"]).orEmpty().trim()
        val uid = (params["uid"] ?: params["userid"] ?: params["userID"]).orEmpty().trim()
        val isPlaylistPath = url.contains("zlist.html", ignoreCase = true) ||
            url.contains("/plist/", ignoreCase = true) ||
            params["share_type"].equals("collect", ignoreCase = true) ||
            params["ctype"] == "1"

        if (gcid.isNotBlank() || specialId.isNotBlank() || (isPlaylistPath && listId.isNotBlank())) {
            val resolvedSpecialId = specialId.ifBlank {
                if (gcid.startsWith("collection_", ignoreCase = true)) {
                    gcid.split("_").getOrNull(3)?.takeIf { it.all(Char::isDigit) }.orEmpty()
                } else {
                    listId.takeIf { it.all(Char::isDigit) && it != "2" && it != "0" }.orEmpty()
                }
            }
            return ParsedKugouShareDescriptor.PlaylistDescriptor(
                globalCollectionId = gcid,
                specialId = resolvedSpecialId,
                listId = listId,
                uid = uid,
                sourceUrl = url
            )
        }

        // 4) 检查单曲标识参数 (hash / album_audio_id / mixsongid)
        val hash = (params["hash"] ?: params["Hash"] ?: params["fileHash"]).orEmpty().trim().uppercase()
        val albumId = (params["album_id"] ?: params["albumId"] ?: params["albumid"]).orEmpty().trim()
        val albumAudioId = (
            params["album_audio_id"]
                ?: params["albumAudioId"]
                ?: params["mixsongid"]
                ?: params["mix_song_id"]
            ).orEmpty().trim()
        val audioId = (params["audio_id"] ?: params["audioId"]).orEmpty().trim()

        if ((hash.length == 32 && HEX_32_REGEX.matches(hash)) ||
            (albumAudioId.isNotBlank() && albumAudioId != "0" && albumAudioId.all(Char::isDigit))
        ) {
            return ParsedKugouShareDescriptor.SingleSongDescriptor(
                hash = if (HEX_32_REGEX.matches(hash)) hash else "",
                albumId = albumId.takeIf { it != "0" }.orEmpty(),
                albumAudioId = albumAudioId.takeIf { it != "0" }.orEmpty(),
                audioId = audioId.takeIf { it != "0" }.orEmpty(),
                sourceUrl = url
            )
        }

        return null
    }

    internal fun parseWakeupJsonPayload(jsonText: String, sourceUrl: String = ""): ParsedKugouShareDescriptor? {
        val rootObj = runCatching { JsonParser.parseString(jsonText).asJsonObject }.getOrNull() ?: return null
        val cmdStr = rootObj.get("cmd")?.asString?.trim().orEmpty()

        val innerObj: JsonObject = when {
            rootObj.has("jsonStr") -> {
                val el: JsonElement = rootObj.get("jsonStr")
                when {
                    el.isJsonObject -> el.asJsonObject
                    el.isJsonPrimitive && el.asJsonPrimitive.isString -> {
                        val str = urlDecodeRepeatedly(el.asString)
                        runCatching { JsonParser.parseString(str).asJsonObject }.getOrNull() ?: rootObj
                    }
                    else -> rootObj
                }
            }
            else -> rootObj
        }

        // 检查歌单字段
        val gcid = innerObj.stringOrEmpty("globalCollectionId")
            .ifBlank { innerObj.stringOrEmpty("global_collection_id") }
            .ifBlank { innerObj.stringOrEmpty("gcid") }
        val specialId = innerObj.stringOrEmpty("specialID")
            .ifBlank { innerObj.stringOrEmpty("specialid") }
            .ifBlank { innerObj.stringOrEmpty("special_id") }
        val listId = innerObj.stringOrEmpty("listID")
            .ifBlank { innerObj.stringOrEmpty("listid") }
        val listName = innerObj.stringOrEmpty("listName")
            .ifBlank { innerObj.stringOrEmpty("specialname") }
        val userId = innerObj.stringOrEmpty("userID")
            .ifBlank { innerObj.stringOrEmpty("userid") }
            .ifBlank { innerObj.stringOrEmpty("uid") }

        if (cmdStr == "302" || gcid.isNotBlank() || (specialId.isNotBlank() && specialId != "0")) {
            val cleanSpecialId = specialId.takeIf { it != "0" }
                ?: listId.takeIf { it != "0" }
                ?: if (gcid.startsWith("collection_", ignoreCase = true)) {
                    gcid.split("_").getOrNull(3)?.takeIf { it.all(Char::isDigit) }.orEmpty()
                } else ""
            return ParsedKugouShareDescriptor.PlaylistDescriptor(
                globalCollectionId = gcid,
                specialId = cleanSpecialId,
                listId = listId,
                uid = userId,
                titleHint = listName,
                sourceUrl = sourceUrl
            )
        }

        // 检查单曲字段
        val hash = innerObj.stringOrEmpty("hash")
            .ifBlank { innerObj.stringOrEmpty("Hash") }
            .uppercase()
        val albumAudioId = innerObj.stringOrEmpty("album_audio_id")
            .ifBlank { innerObj.stringOrEmpty("albumAudioId") }
            .ifBlank { innerObj.stringOrEmpty("mixsongid") }
        val albumId = innerObj.stringOrEmpty("album_id")
            .ifBlank { innerObj.stringOrEmpty("albumId") }
        val fileName = innerObj.stringOrEmpty("filename")
            .ifBlank { innerObj.stringOrEmpty("songname") }
        val durationSec = innerObj.longOrZero("duration")
            .takeIf { it > 0L } ?: innerObj.longOrZero("timelength")

        if (cmdStr == "212" || (hash.length == 32 && HEX_32_REGEX.matches(hash)) ||
            (albumAudioId.isNotBlank() && albumAudioId != "0")
        ) {
            val (artistHint, titleHint) = splitArtistAndTitle(fileName)
            return ParsedKugouShareDescriptor.SingleSongDescriptor(
                hash = if (HEX_32_REGEX.matches(hash)) hash else "",
                albumId = albumId.takeIf { it != "0" }.orEmpty(),
                albumAudioId = albumAudioId.takeIf { it != "0" }.orEmpty(),
                titleHint = titleHint,
                artistHint = artistHint,
                durationSecHint = durationSec,
                sourceUrl = sourceUrl
            )
        }

        // 若是 H5 唤醒包装 cmd=103303 且内含 url
        val nestedUrl = innerObj.stringOrEmpty("url")
        if (nestedUrl.isNotBlank()) {
            parseUrlParametersOnly(nestedUrl)?.let { return it }
        }

        return null
    }

    /**
     * 第三级（HTML 落地页解析）：从 `m.kugou.com/mixsong/xxx.html`、`m3ws.kugou.com/plist/list/xxx`、
     * `www.kugou.com/share/zlist.html` 等页面的内嵌脚本变量与 DOM 属性中提取歌曲或歌单信息。
     */
    fun parseHtmlContent(html: String, currentUrl: String = ""): ParsedKugouShareDescriptor? {
        if (html.isBlank()) return null

        // 1. 匹配移动端单曲页 `var phpParam = {...};`
        PHP_PARAM_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { jsonStr ->
            val obj = runCatching { JsonParser.parseString(jsonStr).asJsonObject }.getOrNull()
            if (obj != null) {
                val songInfoData = obj.getAsJsonObject("song_info")?.getAsJsonObject("data")
                val hash = obj.stringOrEmpty("hash")
                    .ifBlank { songInfoData?.stringOrEmpty("hash").orEmpty() }
                    .uppercase()
                val hash320 = songInfoData?.stringOrEmpty("320hash")?.uppercase().orEmpty()
                val hashFlac = songInfoData?.stringOrEmpty("sqhash")?.uppercase().orEmpty()
                val albumId = obj.stringOrEmpty("album_id")
                    .ifBlank { songInfoData?.stringOrEmpty("album_id").orEmpty() }
                val albumAudioId = obj.stringOrEmpty("album_audio_id")
                    .ifBlank { songInfoData?.stringOrEmpty("album_audio_id").orEmpty() }
                val audioId = songInfoData?.stringOrEmpty("audio_id").orEmpty()
                val fileName = songInfoData?.stringOrEmpty("fileName")
                    .orEmpty()
                    .ifBlank { obj.stringOrEmpty("song_name") }
                val songName = songInfoData?.stringOrEmpty("songName")
                    .orEmpty()
                    .ifBlank { obj.stringOrEmpty("song_name") }
                val singerName = songInfoData?.stringOrEmpty("singerName")
                    .orEmpty()
                    .ifBlank { songInfoData?.stringOrEmpty("author_name").orEmpty() }
                val albumName = songInfoData?.stringOrEmpty("album_name").orEmpty()
                val imgUrl = songInfoData?.stringOrEmpty("imgUrl")
                    ?.replace("{size}", "480")
                    .orEmpty()
                val durationSec = songInfoData?.longOrZero("timeLength") ?: 0L

                val (parsedArtist, parsedTitle) = splitArtistAndTitle(fileName)
                val finalTitle = songName.ifBlank { parsedTitle }
                val finalArtist = singerName.ifBlank { parsedArtist }

                if (hash.length == 32 || albumAudioId.isNotBlank()) {
                    return ParsedKugouShareDescriptor.SingleSongDescriptor(
                        hash = if (HEX_32_REGEX.matches(hash)) hash else "",
                        hash320 = if (HEX_32_REGEX.matches(hash320)) hash320 else "",
                        hashFlac = if (HEX_32_REGEX.matches(hashFlac)) hashFlac else "",
                        albumId = albumId.takeIf { it != "0" }.orEmpty(),
                        albumAudioId = albumAudioId.takeIf { it != "0" }.orEmpty(),
                        audioId = audioId.takeIf { it != "0" }.orEmpty(),
                        titleHint = finalTitle,
                        artistHint = finalArtist,
                        albumNameHint = albumName,
                        coverUrlHint = imgUrl,
                        durationSecHint = durationSec,
                        sourceUrl = currentUrl
                    )
                }
            }
        }

        // 2. 匹配移动端歌单页 `var globalCollectionId = "..."` / `var specialid = Number("...")` / `data-global-collection-id`
        val gcidFromVar = GLOBAL_COLLECTION_ID_VAR_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val gcidFromBase64Attr = DATA_GCID_ATTR_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim()?.let { b64 ->
            runCatching {
                String(Base64.getDecoder().decode(b64), Charsets.UTF_8).trim()
            }.getOrNull()
        }.orEmpty()
        val specialIdFromVar = SPECIAL_ID_VAR_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val specialNameFromVar = SPECIAL_NAME_VAR_REGEX.find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()

        val resolvedGcid = gcidFromVar.ifBlank { gcidFromBase64Attr }
        if (resolvedGcid.isNotBlank() || (specialIdFromVar.isNotBlank() && specialIdFromVar != "0")) {
            return ParsedKugouShareDescriptor.PlaylistDescriptor(
                globalCollectionId = resolvedGcid,
                specialId = specialIdFromVar.takeIf { it != "0" }.orEmpty(),
                titleHint = specialNameFromVar,
                sourceUrl = currentUrl
            )
        }

        // 3. 匹配桌面端分享页 `var dataFromSmarty = [...];`
        SMARTY_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)?.let { arrayStr ->
            val arr = runCatching { JsonParser.parseString(arrayStr).asJsonArray }.getOrNull()
            if (arr != null && arr.size() > 0) {
                val songs = mutableListOf<ParsedKugouShareDescriptor.SingleSongDescriptor>()
                for (el in arr) {
                    if (!el.isJsonObject) continue
                    val item = el.asJsonObject
                    val h = item.stringOrEmpty("hash").uppercase()
                    val songName = item.stringOrEmpty("song_name")
                    val authorName = item.stringOrEmpty("author_name")
                    val albumId = item.stringOrEmpty("album_id")
                    val audioId = item.stringOrEmpty("audio_id")
                    val mixSongId = item.stringOrEmpty("album_audio_id").ifBlank { item.stringOrEmpty("mixsongid") }
                    val durationMsOrSec = item.longOrZero("timelength")
                    val durationSec = if (durationMsOrSec > 1000L) durationMsOrSec / 1000L else durationMsOrSec
                    if (HEX_32_REGEX.matches(h) || mixSongId.isNotBlank()) {
                        songs.add(
                            ParsedKugouShareDescriptor.SingleSongDescriptor(
                                hash = if (HEX_32_REGEX.matches(h)) h else "",
                                albumId = albumId.takeIf { it != "0" }.orEmpty(),
                                albumAudioId = mixSongId.takeIf { it != "0" }.orEmpty(),
                                audioId = audioId.takeIf { it != "0" }.orEmpty(),
                                titleHint = songName,
                                artistHint = authorName,
                                durationSecHint = durationSec,
                                sourceUrl = currentUrl
                            )
                        )
                    }
                }

                val urlDesc = parseUrlParametersOnly(currentUrl)
                if (urlDesc is ParsedKugouShareDescriptor.PlaylistDescriptor) {
                    return urlDesc.copy(embeddedSongs = songs)
                }
                val isPlaylistPage = currentUrl.contains("zlist.html", ignoreCase = true) ||
                    currentUrl.contains("plist", ignoreCase = true) ||
                    songs.size > 1
                if (isPlaylistPage && songs.isNotEmpty()) {
                    return ParsedKugouShareDescriptor.PlaylistDescriptor(
                        embeddedSongs = songs,
                        sourceUrl = currentUrl
                    )
                }
                if (songs.isNotEmpty()) {
                    return songs.first()
                }
            }
        }

        return null
    }

    /**
     * 完整多级异步解析入口：
     * 1. 先尝试第 1 级本地零网络直接解包；
     * 2. 若是短链 (`t1.kugou.com` 等) 或落地页，通过 OkHttp 逐跳追踪重定向并解析 HTML 内嵌变量。
     */
    suspend fun parseShareText(rawText: String): ParsedKugouShareDescriptor? = withContext(Dispatchers.IO) {
        val direct = parseShareTextDirectly(rawText)
        if (direct != null) {
            when (direct) {
                is ParsedKugouShareDescriptor.SingleSongDescriptor -> if (direct.isComplete) return@withContext direct
                is ParsedKugouShareDescriptor.PlaylistDescriptor -> if (direct.isComplete) return@withContext direct
            }
        }

        if (!AppPermissionManager.isPermissionEnabled(AppPermissionType.NETWORK_ACCESS)) {
            return@withContext direct
        }

        val candidateUrls = extractCandidateUrls(rawText).filter { it.startsWith("http", ignoreCase = true) }
        for (initialUrl in candidateUrls) {
            resolveHttpShareUrl(initialUrl, userAgent = MOBILE_UA)?.let { return@withContext it }
            // 若移动端 UA 被某些旧版短链重定向到酷狗错误页，使用桌面端 UA 重试以捕获 zlist.html?global_collection_id=...
            resolveHttpShareUrl(initialUrl, userAgent = DESKTOP_UA)?.let { return@withContext it }
        }

        return@withContext direct
    }

    private fun resolveHttpShareUrl(startUrl: String, userAgent: String): ParsedKugouShareDescriptor? {
        val noRedirectClient = NetworkClientProvider.baseClient.newBuilder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

        var currentUrl = startUrl
        var bestPartialDescriptor: ParsedKugouShareDescriptor? = parseUrlParametersOnly(currentUrl)

        for (hop in 0 until 6) {
            parseUrlParametersOnly(currentUrl)?.let { desc ->
                bestPartialDescriptor = mergeDescriptors(bestPartialDescriptor, desc)
                if (isDescriptorSufficientWithoutHtml(desc, currentUrl)) {
                    return bestPartialDescriptor
                }
            }

            val request = runCatching {
                Request.Builder()
                    .url(currentUrl)
                    .header("User-Agent", userAgent)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .get()
                    .build()
            }.getOrNull() ?: break

            val response = runCatching { noRedirectClient.newCall(request).execute() }.getOrNull() ?: break
            response.use { resp ->
                val code = resp.code
                if (code in 300..399) {
                    val location = resp.header("Location")?.trim().orEmpty()
                    if (location.isEmpty()) return bestPartialDescriptor

                    if (location.startsWith("kugou", ignoreCase = true)) {
                        parseSchemeUri(location)?.let { return mergeDescriptors(bestPartialDescriptor, it) }
                    }

                    val nextUrl = resolveRelativeUrl(currentUrl, location)
                    parseUrlParametersOnly(nextUrl)?.let { locDesc ->
                        bestPartialDescriptor = mergeDescriptors(bestPartialDescriptor, locDesc)
                        if (isDescriptorSufficientWithoutHtml(locDesc, nextUrl)) {
                            return bestPartialDescriptor
                        }
                    }
                    currentUrl = nextUrl
                } else {
                    val bodyStr = runCatching { resp.body?.string().orEmpty() }.getOrDefault("")
                    parseHtmlContent(bodyStr, currentUrl)?.let { htmlDesc ->
                        return mergeDescriptors(bestPartialDescriptor, htmlDesc)
                    }
                    return bestPartialDescriptor
                }
            }
        }
        return bestPartialDescriptor
    }

    private fun isDescriptorSufficientWithoutHtml(
        descriptor: ParsedKugouShareDescriptor,
        url: String
    ): Boolean {
        return when (descriptor) {
            is ParsedKugouShareDescriptor.SingleSongDescriptor -> {
                // 若已经有 32 位 Hash，直接足够；若只有 mixsong 路径但没有 hash，继续请求落地页拿 phpParam
                descriptor.hash.length == 32 && !MIXSONG_PATH_REGEX.containsMatchIn(url)
            }
            is ParsedKugouShareDescriptor.PlaylistDescriptor -> {
                descriptor.globalCollectionId.isNotBlank() ||
                    (descriptor.specialId.isNotBlank() && descriptor.specialId != "0")
            }
        }
    }

    private fun mergeDescriptors(
        older: ParsedKugouShareDescriptor?,
        newer: ParsedKugouShareDescriptor
    ): ParsedKugouShareDescriptor {
        if (older == null) return newer
        if (older is ParsedKugouShareDescriptor.PlaylistDescriptor &&
            newer is ParsedKugouShareDescriptor.PlaylistDescriptor
        ) {
            return ParsedKugouShareDescriptor.PlaylistDescriptor(
                globalCollectionId = newer.globalCollectionId.ifBlank { older.globalCollectionId },
                specialId = newer.specialId.ifBlank { older.specialId },
                listId = newer.listId.ifBlank { older.listId },
                uid = newer.uid.ifBlank { older.uid },
                titleHint = newer.titleHint.ifBlank { older.titleHint },
                creatorNameHint = newer.creatorNameHint.ifBlank { older.creatorNameHint },
                coverUrlHint = newer.coverUrlHint.ifBlank { older.coverUrlHint },
                embeddedSongs = newer.embeddedSongs.ifEmpty { older.embeddedSongs },
                sourceUrl = newer.sourceUrl.ifBlank { older.sourceUrl }
            )
        }
        if (older is ParsedKugouShareDescriptor.SingleSongDescriptor &&
            newer is ParsedKugouShareDescriptor.SingleSongDescriptor
        ) {
            return ParsedKugouShareDescriptor.SingleSongDescriptor(
                hash = newer.hash.ifBlank { older.hash },
                hash320 = newer.hash320.ifBlank { older.hash320 },
                hashFlac = newer.hashFlac.ifBlank { older.hashFlac },
                albumId = newer.albumId.ifBlank { older.albumId },
                albumAudioId = newer.albumAudioId.ifBlank { older.albumAudioId },
                audioId = newer.audioId.ifBlank { older.audioId },
                titleHint = newer.titleHint.ifBlank { older.titleHint },
                artistHint = newer.artistHint.ifBlank { older.artistHint },
                albumNameHint = newer.albumNameHint.ifBlank { older.albumNameHint },
                coverUrlHint = newer.coverUrlHint.ifBlank { older.coverUrlHint },
                durationSecHint = if (newer.durationSecHint > 0L) newer.durationSecHint else older.durationSecHint,
                sourceUrl = newer.sourceUrl.ifBlank { older.sourceUrl }
            )
        }
        return newer
    }

    private fun resolveRelativeUrl(baseUrl: String, location: String): String {
        if (location.startsWith("http://", ignoreCase = true) ||
            location.startsWith("https://", ignoreCase = true)
        ) {
            return location
        }
        return runCatching {
            URI(baseUrl).resolve(location).toString()
        }.getOrDefault(location)
    }

    private fun parseQueryKeyValues(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val map = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val k = pair.substring(0, eqIdx).trim()
                val v = pair.substring(eqIdx + 1).trim()
                if (k.isNotEmpty()) {
                    map[k] = v
                }
            }
        }
        return map
    }

    private fun splitArtistAndTitle(rawFileName: String): Pair<String, String> {
        val clean = rawFileName.trim()
        if (clean.isEmpty()) return "" to ""
        val sepIdx = clean.indexOf(" - ")
        return if (sepIdx > 0) {
            val artist = clean.substring(0, sepIdx).trim()
            val title = clean.substring(sepIdx + 3).trim()
            artist to title
        } else {
            "" to clean
        }
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        val el = get(key) ?: return ""
        if (el.isJsonNull) return ""
        return runCatching { el.asString.trim() }.getOrDefault("")
    }

    private fun JsonObject.longOrZero(key: String): Long {
        val el = get(key) ?: return 0L
        if (el.isJsonNull) return 0L
        return runCatching { el.asLong }.getOrDefault(0L)
    }
}
