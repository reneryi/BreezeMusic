package com.concept.music.data.kugou

import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.ComplexSearchResult
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.FollowedArtist
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.PlaylistItem
import com.concept.music.data.model.PurchasedAlbumItem
import com.concept.music.data.model.PurchasedSongItem
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.SearchIntent
import com.concept.music.data.model.SearchResult
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.data.model.UserGradeInfo
import com.concept.music.data.model.NewSongPeriodGroup
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class MusicApiRepository(
    private val transport: KugouTransport,
    private val accounts: AccountRepository,
    private val device: () -> DeviceIdentity = { DeviceIdentity("00000000-0000-0000-0000-000000000000", "0", "Android") }
) {
    private val playbackResultCache = ConcurrentHashMap<String, PlaybackResult>()
    private val playbackResultCacheTimestamp = ConcurrentHashMap<String, Long>()
    private val playbackCacheTtlMs = 12 * 60 * 1000L

    // 记录每个电台与随心听的漫游偏移游标与已推去重池，确保每次刷新/换一批/继续漫游都获取 20 首全新曲目
    private val fmChannelCursorMap = ConcurrentHashMap<String, AtomicInteger>()
    private val fmChannelMetaMap = ConcurrentHashMap<String, FmChannel>()
    private val fmChannelInitialSongsMap = ConcurrentHashMap<String, List<Song>>()
    private val fmChannelPlaylistIdCache = ConcurrentHashMap<String, String>()
    private val seenFmChannelHashes = ConcurrentHashMap<String, MutableSet<String>>()
    private val personalFmPoolCursor = AtomicInteger(0)
    private val seenPersonalFmHashes = ConcurrentHashMap.newKeySet<String>()
    private val userTasteSeedsCache = java.util.concurrent.CopyOnWriteArrayList<Song>()
    private val rankLatestVolMap = ConcurrentHashMap<String, Int>()
    private val albumPublishDateCache = ConcurrentHashMap<String, String>()
    private val albumCompanyCache = ConcurrentHashMap<String, String>()

    internal data class SongStreamSpec(
        val hash: String,
        val sizeBytes: Long,
        val bitrateKbps: Int,
        val extName: String = ""
    )

    internal data class SongTechMeta(
        val specs: Map<AudioQuality, SongStreamSpec> = emptyMap(),
        val albumName: String = "",
        val publishDate: String = "",
        val company: String = "",
        val durationMs: Long = 0L
    )

    private val songTechMetaCache = ConcurrentHashMap<String, SongTechMeta>()

    private fun cacheSongTechMeta(keys: Iterable<String>, meta: SongTechMeta) {
        for (rawKey in keys) {
            val k = rawKey.trim().lowercase()
            if (k.isNotBlank() && k != "0") {
                val existing = songTechMetaCache[k]
                if (existing == null) {
                    songTechMetaCache[k] = meta
                } else {
                    songTechMetaCache[k] = SongTechMeta(
                        specs = existing.specs + meta.specs,
                        albumName = meta.albumName.ifBlank { existing.albumName },
                        publishDate = meta.publishDate.ifBlank { existing.publishDate },
                        company = meta.company.ifBlank { existing.company },
                        durationMs = if (meta.durationMs > 0L) meta.durationMs else existing.durationMs
                    )
                }
            }
        }
    }
    @Volatile
    private var lastPersonalFmSong: Song? = null

    internal fun normalizePublishDate(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()
        if (trimmed == "0" || trimmed.startsWith("0000") || trimmed.startsWith("1900") || trimmed.startsWith("1970-01-01")) return ""
        val dateMatch = Regex("""(\d{4})[-./](\d{1,2})[-./](\d{1,2})""").find(trimmed)
        if (dateMatch != null) {
            val y = dateMatch.groupValues[1].toIntOrNull() ?: return ""
            val m = dateMatch.groupValues[2].toIntOrNull() ?: return ""
            val d = dateMatch.groupValues[3].toIntOrNull() ?: return ""
            if (y in 1971..2100 && m in 1..12 && d in 1..31) {
                return runCatching { java.time.LocalDate.of(y, m, d).toString() }.getOrDefault("")
            }
        }
        if (trimmed.length >= 8 && trimmed.take(8).all { it.isDigit() }) {
            val y = trimmed.substring(0, 4).toIntOrNull() ?: 0
            val m = trimmed.substring(4, 6).toIntOrNull() ?: 0
            val d = trimmed.substring(6, 8).toIntOrNull() ?: 0
            if (y in 2000..2100 && m in 1..12 && d in 1..31) {
                return runCatching { java.time.LocalDate.of(y, m, d).toString() }.getOrDefault("")
            }
        }
        val num = trimmed.toLongOrNull()
        if (num != null && num > 100_000_000L) {
            return runCatching {
                val instant = if (num > 10_000_000_000L) java.time.Instant.ofEpochMilli(num) else java.time.Instant.ofEpochSecond(num)
                instant.atZone(CHINA_ZONE).toLocalDate().toString()
            }.getOrDefault("")
        }
        return ""
    }

    private fun cleanTitleForSort(rawTitle: String): String {
        val stripped = rawTitle.trim()
            .replace(Regex("""^[\s《》〈〉「」『』【】()（）\[\]"'“”‘’·\-_:：]+"""), "")
            .trim()
        return stripped.ifBlank { rawTitle.trim() }
    }

    internal fun extractTitleFirstLetter(rawTitle: String): Char {
        val clean = cleanTitleForSort(rawTitle)
        if (clean.isEmpty()) return '#'
        val firstChar = clean[0]
        if (firstChar in 'a'..'z') return firstChar.uppercaseChar()
        if (firstChar in 'A'..'Z') return firstChar
        if (firstChar in '0'..'9') return firstChar

        // 优先尝试 Android ICU Transliterator (Han-Latin)
        val icuInitial = runCatching {
            val cls = Class.forName("android.icu.text.Transliterator")
            val getInst = cls.getMethod("getInstance", String::class.java)
            val trans = getInst.invoke(null, "Han-Latin; NFD; [[:Nonspacing Mark:]] Remove; Upper()")
            val transliterate = cls.getMethod("transliterate", String::class.java)
            val latin = (transliterate.invoke(trans, firstChar.toString()) as? String)?.trim().orEmpty()
            latin.firstOrNull { it in 'A'..'Z' }
        }.getOrNull()
        if (icuInitial != null) return icuInitial

        // 标准中文拼音首字母边界表 (Collator.getInstance(Locale.CHINA))
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val boundaries = listOf(
            'A' to "阿", 'B' to "八", 'C' to "擦", 'D' to "搭", 'E' to "蛾",
            'F' to "发", 'G' to "噶", 'H' to "哈", 'J' to "击", 'K' to "喀",
            'L' to "垃", 'M' to "妈", 'N' to "拿", 'O' to "哦", 'P' to "啪",
            'Q' to "期", 'R' to "然", 'S' to "撒", 'T' to "塌", 'W' to "挖",
            'X' to "昔", 'Y' to "压", 'Z' to "匝"
        )
        val target = firstChar.toString()
        for (i in boundaries.indices.reversed()) {
            if (collator.compare(target, boundaries[i].second) >= 0) {
                return boundaries[i].first
            }
        }
        return '#'
    }

    internal val newSongExpressComparator = Comparator<Song> { a, b ->
        // 1. 严格按发行日期降序（越新越靠前，如 2026-10-17 在 2026-10-16 之前）
        val dateCmp = b.publishDate.compareTo(a.publishDate)
        if (dateCmp != 0) return@Comparator dateCmp

        // 2. 相同发行时间按歌名首字母升序 (A -> Z)
        val initA = extractTitleFirstLetter(a.title)
        val initB = extractTitleFirstLetter(b.title)
        if (initA != initB) return@Comparator initA.compareTo(initB)

        // 3. 首字母相同时按完整歌名拼音/字典序升序
        val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
        val titleCmp = collator.compare(cleanTitleForSort(a.title), cleanTitleForSort(b.title))
        if (titleCmp != 0) titleCmp else a.artist.compareTo(b.artist)
    }

    private fun recordUserTasteSeeds(songs: List<Song>) {
        if (songs.isEmpty()) return
        val valid = songs.filter { it.hash.isNotBlank() && it.title != "未知歌曲" }
        if (valid.isEmpty()) return
        val merged = (valid + userTasteSeedsCache)
            .distinctBy { it.hash.ifBlank { it.id }.lowercase() }
            .take(160)
        userTasteSeedsCache.clear()
        userTasteSeedsCache.addAll(merged)
    }

    private fun diversifyArtistOrder(songs: List<Song>): List<Song> {
        if (songs.size <= 2) return songs
        val remaining = songs.toMutableList()
        val result = ArrayList<Song>(songs.size)
        while (remaining.isNotEmpty()) {
            val prev = result.lastOrNull()
            val nextIdx = if (prev == null) {
                0
            } else {
                remaining.indexOfFirst { cand ->
                    !cand.artist.equals(prev.artist, ignoreCase = true) &&
                        (cand.coverUrl.isBlank() || cand.coverUrl != prev.coverUrl)
                }.takeIf { it >= 0 } ?: 0
            }
            result.add(remaining.removeAt(nextIdx))
        }
        return result
    }

    // 本地已关注歌手增量缓存（防止关注后因云端延迟未立即出现在列表中）
    private val localFollowedArtistsMap = ConcurrentHashMap<String, FollowedArtist>()

    fun clearPlaybackCache(hash: String? = null) {
        if (hash == null) {
            playbackResultCache.clear()
            playbackResultCacheTimestamp.clear()
        } else {
            val prefix = hash.lowercase()
            playbackResultCache.keys.filter { it.startsWith(prefix) }.forEach {
                playbackResultCache.remove(it)
                playbackResultCacheTimestamp.remove(it)
            }
        }
    }

    private fun getValidCachedPlayback(cacheKey: String): PlaybackResult? {
        val cached = playbackResultCache[cacheKey] ?: return null
        val ts = playbackResultCacheTimestamp[cacheKey] ?: return cached
        if (System.currentTimeMillis() - ts <= playbackCacheTtlMs) {
            return cached
        }
        playbackResultCache.remove(cacheKey)
        playbackResultCacheTimestamp.remove(cacheKey)
        return null
    }

    private fun putCachedPlayback(cacheKey: String, result: PlaybackResult) {
        playbackResultCache[cacheKey] = result
        playbackResultCacheTimestamp[cacheKey] = System.currentTimeMillis()
    }

    private suspend fun <T> auth(block: suspend (AccountContext) -> T): T {
        val context = accounts.context() ?: throw ApiFailure(null, "请先登录")
        return accounts.authenticated(context) { block(context) }
    }

    suspend fun dailyRecommend(): List<Song> {
        val session = accounts.context()?.session
        val primarySongs = if (session != null) {
            runCatching {
                val response = transport.call(
                    "/everyday_song_recommend",
                    session,
                    method = "POST",
                    body = json("platform" to "android", "userid" to session.userId),
                    headers = mapOf("x-router" to "everydayrec.service.kugou.com")
                )
                parseSongs(response.data)
            }.getOrNull()
        } else null

        if (!primarySongs.isNullOrEmpty()) {
            val enrichedPrimary = runCatching { enrichSongsPrivilegeLite(primarySongs) }.getOrDefault(primarySongs)
            recordUserTasteSeeds(enrichedPrimary)
            return enrichedPrimary
        }

        // 当个性化日推暂未生成或未登录时，自动平滑加载概念精选推荐，确保首屏不出现 0 首
        val fallbackSongs = runCatching {
            searchSongs("热歌", page = 1, pageSize = 30)
        }.getOrNull()

        return fallbackSongs?.songs?.ifEmpty { null } ?: primarySongs ?: emptyList()
    }

    suspend fun userPlaylists(): List<Playlist> = auth { ctx ->
        val response = transport.call(
            "/v7/get_all_list",
            ctx.session,
            method = "POST",
            query = mapOf("plat" to "1"),
            body = json("total_ver" to 979, "type" to 2, "page" to 1, "pagesize" to 100),
            headers = mapOf("x-router" to "cloudlist.service.kugou.com")
        ).requireSuccess()

        response.data.objects("info").map { item ->
            val name = item.text("name") ?: item.text("listname") ?: "未命名歌单"
            val listId = item.text("listid")?.toLongOrNull() ?: 0L
            val global = item.text("global_collection_id") ?: item.text("list_create_gid") ?: ""
            val isDefault = item.number("is_def") == 2 || name == "我喜欢"
            val listCreateUserid = item.text("list_create_userid") ?: ""
            val listCreateListid = item.text("list_create_listid") ?: ""
            val listCreateGid = item.text("list_create_gid") ?: ""
            Playlist(
                id = global.ifBlank { listId.toString() },
                title = name,
                coverUrl = imageUrl(item.text("pic") ?: item.text("cover")),
                trackCount = item.number("count") ?: 0,
                isCreatedByMe = item.text("type") != "1",
                isFavorite = item.text("type") == "1",
                isLiked = isDefault,
                listId = listId,
                globalCollectionId = global,
                totalVersion = item.text("total_ver")?.toLongOrNull() ?: 0L,
                listCreateUserid = listCreateUserid,
                listCreateListid = listCreateListid,
                listCreateGid = listCreateGid
            )
        }
    }

    // 新建歌单
    suspend fun createPlaylist(name: String): Boolean = auth { ctx ->
        val session = ctx.session
        val clientTime = System.currentTimeMillis() / 1000
        val body = JsonObject().apply {
            addProperty("userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("token", session.token)
            addProperty("total_ver", 0)
            addProperty("name", name)
            addProperty("type", 0)
            addProperty("source", 1)
            addProperty("is_pri", 0)
            addProperty("list_create_userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("list_create_listid", 0)
            addProperty("list_create_gid", "")
            addProperty("from_shupinmv", 0)
        }
        val query = mapOf(
            "last_time" to clientTime.toString(),
            "last_area" to "gztx",
            "userid" to session.userId,
            "token" to session.token
        )
        val response = runCatching {
            transport.call(
                path = "/cloudlist.service/v5/add_list",
                session = session,
                query = query,
                body = body,
                method = "POST"
            )
        }.getOrNull()
        response != null && response.status == 1
    }

    // 收藏歌单
    suspend fun collectPlaylist(playlist: Playlist): Boolean = auth { ctx ->
        val session = ctx.session
        var authorUid = playlist.listCreateUserid.ifBlank { "0" }
        var authorListId = playlist.listCreateListid.ifBlank { "0" }
        val gid = playlist.globalCollectionId.ifBlank { playlist.id }
        if ((authorUid == "0" || authorListId == "0") && gid.contains("_")) {
            val parts = gid.split("_")
            if (parts.size >= 4) {
                authorUid = parts[2]
                authorListId = parts[3]
            }
        }
        val body = JsonObject().apply {
            addProperty("userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("token", session.token)
            addProperty("total_ver", 0)
            addProperty("name", playlist.title)
            addProperty("type", 1)
            addProperty("source", 1)
            addProperty("is_pri", 0)
            addProperty("list_create_userid", authorUid.toLongOrNull() ?: 0L)
            addProperty("list_create_listid", authorListId.toLongOrNull() ?: 0L)
            addProperty("list_create_gid", gid)
            addProperty("from_shupinmv", 0)
        }
        val response = runCatching {
            transport.call(
                path = "/cloudlist.service/v5/add_list",
                session = session,
                body = body,
                method = "POST"
            )
        }.getOrNull()
        response != null && response.status == 1
    }

    // 取消收藏或删除自建歌单
    suspend fun deleteOrUncollectPlaylist(playlist: Playlist): Boolean = auth { ctx ->
        val session = ctx.session
        val listId = playlist.listId.takeIf { it > 0 }
            ?: playlist.listCreateListid.toLongOrNull()
            ?: playlist.id.toLongOrNull()
            ?: 0L
        val ptype = if (playlist.isCreatedByMe) 0 else 1
        val clientTime = System.currentTimeMillis() / 1000

        val dataMap = JsonObject().apply {
            addProperty("listid", listId)
            addProperty("total_ver", 0)
            addProperty("type", ptype)
            addProperty("userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("token", session.token)
        }

        val (aesKey, aesCipher) = KugouSigner.playlistAesEncrypt(KugouSigner.gson.toJson(dataMap))
        val pInner = KugouSigner.gson.toJson(mapOf(
            "aes" to aesKey,
            "uid" to (session.userId.toLongOrNull() ?: 0L),
            "token" to session.token
        ))
        val p = KugouSigner.rsa(pInner).uppercase()
        val key = KugouSigner.md5("${KugouSigner.APP_ID}LnT6xpN3khm36zse0QzvmgTZ3waWdRSA11440$clientTime")

        val query = mapOf(
            "clienttime" to clientTime.toString(),
            "key" to key,
            "last_area" to "gztx",
            "clientver" to "11440",
            "appid" to "3116",
            "last_time" to clientTime.toString(),
            "p" to p
        )

        val headers = mapOf("x-router" to "cloudlist.service.kugou.com")
        val rawResponse = runCatching {
            transport.callRaw(
                path = "/v2/delete_list",
                session = session,
                query = query,
                rawBody = aesCipher,
                headers = headers,
                method = "POST"
            )
        }.getOrNull()

        if (rawResponse != null && rawResponse.isNotEmpty()) {
            val decrypted = runCatching {
                val b64 = Base64.getEncoder().encodeToString(rawResponse)
                KugouSigner.playlistAesDecrypt(aesKey, b64)
            }.getOrNull() ?: String(rawResponse, Charsets.UTF_8)
            val json = runCatching { JsonParser.parseString(decrypted).asJsonObject }.getOrNull()
            if (json != null && (json.number("status") == 1 || json.number("error_code") == 0)) {
                return@auth true
            }
        }
        rawResponse != null
    }

    // 向歌单添加歌曲
    suspend fun addSongToPlaylist(playlistId: Long, song: Song): Boolean = auth { ctx ->
        val session = ctx.session
        val clientTime = System.currentTimeMillis() / 1000
        val query = mapOf(
            "last_time" to clientTime.toString(),
            "last_area" to "gztx",
            "userid" to session.userId,
            "token" to session.token
        )
        val songItem = JsonObject().apply {
            addProperty("number", 1)
            addProperty("name", song.title)
            addProperty("hash", song.hash)
            addProperty("size", 0)
            addProperty("sort", 0)
            addProperty("timelen", song.durationMs / 1000)
            addProperty("bitrate", 0)
            addProperty("album_id", song.albumId.toLongOrNull() ?: 0L)
            addProperty("mixsongid", song.albumAudioId)
        }
        val dataArr = JsonArray().apply { add(songItem) }
        val body = JsonObject().apply {
            addProperty("listid", playlistId)
            addProperty("userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("token", session.token)
            addProperty("list_ver", 0)
            addProperty("type", 0)
            addProperty("slow_upload", 1)
            addProperty("scene", "false;null")
            add("data", dataArr)
        }
        val response = runCatching {
            transport.call(
                path = "/cloudlist.service/v6/add_song",
                session = session,
                query = query,
                body = body,
                method = "POST"
            )
        }.getOrNull()
        response != null && response.status == 1
    }

    // 从歌单删除歌曲
    suspend fun removeSongFromPlaylist(playlistId: Long, song: Song): Boolean = auth { ctx ->
        val session = ctx.session
        val clientTime = System.currentTimeMillis() / 1000
        val query = mapOf(
            "last_time" to clientTime.toString(),
            "last_area" to "gztx",
            "userid" to session.userId,
            "token" to session.token
        )
        val songItem = JsonObject().apply {
            addProperty("fileid", song.fileId)
            if (song.fileId == 0L) {
                addProperty("hash", song.hash)
            }
        }
        val dataArr = JsonArray().apply { add(songItem) }
        val body = JsonObject().apply {
            addProperty("listid", playlistId)
            addProperty("userid", session.userId.toLongOrNull() ?: 0L)
            addProperty("token", session.token)
            addProperty("type", 0)
            addProperty("list_ver", 0)
            add("data", dataArr)
        }
        val response = runCatching {
            transport.call(
                path = "/cloudlist.service/v4/delete_songs",
                session = session,
                query = query,
                body = body,
                method = "POST"
            )
        }.getOrNull()
        response != null && response.status == 1
    }

    suspend fun playlistSongs(playlist: Playlist): List<Song> = auth { ctx ->
        val collectionId = playlist.globalCollectionId.ifBlank { playlist.id }
        val list = fetchPlaylistSongsAll(collectionId, ctx.session)
        if (playlist.isLiked || playlist.title.contains("喜欢")) {
            recordUserTasteSeeds(list.take(80))
        }
        list
    }

    suspend fun fetchPlaylistSongsAll(collectionId: String, session: KugouSession? = accounts.context()?.session): List<Song> {
        if (collectionId.isBlank()) return emptyList()

        // 若传入的是纯数字 specialid，先尝试通过 playlistInfo 解析其 global_collection_id
        var effectiveGid = collectionId
        if (collectionId.all { it.isDigit() }) {
            val resolvedGid = runCatching { playlistInfo(collectionId)?.globalCollectionId }.getOrNull()
            if (!resolvedGid.isNullOrBlank()) {
                effectiveGid = resolvedGid
            }
        }

        val allSongs = mutableListOf<Song>()
        val seenKeys = mutableSetOf<String>()
        var beginIdx = 0
        val pageSize = 200
        var pageCount = 0
        var expectedTotal = 0
        while (pageCount < 25) {
            val response = runCatching {
                transport.call(
                    "/pubsongs/v2/get_other_list_file_nofilt",
                    session,
                    query = mapOf(
                        "area_code" to "1",
                        "begin_idx" to beginIdx.toString(),
                        "plat" to "1",
                        "type" to "1",
                        "mode" to "1",
                        "personal_switch" to "1",
                        "pagesize" to pageSize.toString(),
                        "global_collection_id" to effectiveGid
                    )
                ).requireSuccess()
            }.getOrNull() ?: break

            val dataObj = response.data.obj("data") ?: response.data
            if (expectedTotal <= 0) {
                expectedTotal = dataObj.number("total") ?: dataObj.number("count")
                    ?: response.data.number("total") ?: response.data.number("count") ?: 0
            }
            val batch = parseSongs(response.data)
            if (batch.isEmpty()) break
            var addedInBatch = 0
            for (song in batch) {
                val key = song.hash.ifBlank { song.id }.lowercase()
                if (seenKeys.add(key)) {
                    allSongs.add(song)
                    addedInBatch++
                }
            }
            pageCount++
            beginIdx += pageSize
            if (addedInBatch == 0) break
            if (expectedTotal > 0 && allSongs.size >= expectedTotal) break
            if (expectedTotal <= 0 && batch.size < 30) break
        }
        if (allSongs.isNotEmpty()) {
            return runCatching { enrichSongsPrivilegeLite(allSongs) }.getOrDefault(allSongs)
        }

        // 备用 1：/v4/get_other_list_file 网关多页拉取
        val v4Songs = mutableListOf<Song>()
        val v4Seen = mutableSetOf<String>()
        for (page in 1..15) {
            val batch = runCatching {
                val response = transport.callAdvanced(
                    path = "/v4/get_other_list_file",
                    session = session,
                    query = mapOf(
                        "global_collection_id" to effectiveGid,
                        "page" to page.toString(),
                        "pagesize" to "100",
                        "is_publish" to "1"
                    ),
                    headers = mapOf("x-router" to "pubsongs.kugou.com"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseSongs(response.data)
            }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            var added = 0
            for (s in batch) {
                if (v4Seen.add(s.hash.ifBlank { s.id }.lowercase())) {
                    v4Songs.add(s)
                    added++
                }
            }
            if (added == 0 || batch.size < 20) break
        }
        if (v4Songs.isNotEmpty()) {
            return runCatching { enrichSongsPrivilegeLite(v4Songs) }.getOrDefault(v4Songs)
        }

        // 备用 2：提取 specialid 走 CDN 歌单曲目接口 (/api/v3/special/song) 多页拉取
        val numericSpecialId = collectionId.takeIf { it.all { ch -> ch.isDigit() } }
            ?: effectiveGid.split("_").getOrNull(3)?.takeIf { it.all { ch -> ch.isDigit() } }
        if (!numericSpecialId.isNullOrBlank()) {
            val cdnSongs = mutableListOf<Song>()
            val cdnSeen = mutableSetOf<String>()
            for (page in 1..15) {
                val batch = runCatching {
                    val response = transport.callAdvanced(
                        path = "/api/v3/special/song",
                        host = "http://mobilecdnbj.kugou.com",
                        query = mapOf(
                            "version" to "9108",
                            "specialid" to numericSpecialId,
                            "plat" to "0",
                            "pagesize" to "100",
                            "page" to page.toString(),
                            "area_code" to "1"
                        ),
                        sign = false,
                        includeDefaultParams = false
                    ).requireSuccess()
                    parseSongs(response.data)
                }.getOrDefault(emptyList())
                if (batch.isEmpty()) break
                var added = 0
                for (s in batch) {
                    if (cdnSeen.add(s.hash.ifBlank { s.id }.lowercase())) {
                        cdnSongs.add(s)
                        added++
                    }
                }
                if (added == 0 || batch.size < 20) break
            }
            if (cdnSongs.isNotEmpty()) {
                return runCatching { enrichSongsPrivilegeLite(cdnSongs) }.getOrDefault(cdnSongs)
            }
        }

        return emptyList()
    }

    suspend fun playlistInfo(playlistId: String): Playlist? {
        if (playlistId.isBlank()) return null
        val session = accounts.context()?.session

        val gatewayPlaylist = runCatching {
            val listArr = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("global_collection_id", playlistId)
                    if (playlistId.all { it.isDigit() }) {
                        addProperty("specialid", playlistId.toLongOrNull() ?: 0L)
                    }
                })
            }
            val body = JsonObject().apply {
                add("data", listArr)
                addProperty("userid", session?.userId?.toLongOrNull() ?: 0L)
                addProperty("token", session?.token.orEmpty())
            }
            val response = transport.callAdvanced(
                path = "/v3/get_list_info",
                method = "POST",
                session = session,
                body = body,
                headers = mapOf("x-router" to "pubsongs.kugou.com"),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()

            val infoObj = response.data.arr("data")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: response.data.obj("data") ?: response.data
            val name = (infoObj.text("name") ?: infoObj.text("listname") ?: infoObj.text("specialname") ?: "")
                .replace("<em>", "").replace("</em>", "").trim()
            val pic = infoObj.text("pic") ?: infoObj.text("cover") ?: infoObj.text("img") ?: infoObj.text("imgurl") ?: infoObj.text("flexible_cover")
            val count = infoObj.number("count") ?: infoObj.number("songcount") ?: infoObj.number("song_count") ?: 0
            val gid = infoObj.text("global_collection_id") ?: infoObj.text("list_create_gid") ?: playlistId
            val desc = infoObj.text("intro") ?: infoObj.text("description") ?: infoObj.text("special_desc") ?: ""
            val playCnt = infoObj.text("play_count") ?: infoObj.text("playcount") ?: ""
            val createUid = infoObj.text("list_create_userid") ?: infoObj.text("userid") ?: ""
            val createListId = infoObj.text("list_create_listid") ?: infoObj.text("listid") ?: infoObj.text("specialid") ?: ""
            val creator = infoObj.text("nickname") ?: infoObj.text("username") ?: infoObj.text("user_name") ?: ""

            if (name.isBlank() && pic.isNullOrBlank() && count <= 0) {
                null
            } else {
                Playlist(
                    id = gid.ifBlank { playlistId },
                    title = name.ifBlank { "精选歌单" },
                    coverUrl = imageUrl(pic),
                    trackCount = count,
                    playCount = playCnt,
                    description = desc,
                    creatorName = creator,
                    isCreatedByMe = false,
                    isFavorite = false,
                    isLiked = false,
                    globalCollectionId = gid.ifBlank { playlistId },
                    listCreateUserid = createUid,
                    listCreateListid = createListId,
                    listCreateGid = gid.ifBlank { playlistId }
                )
            }
        }.getOrNull()

        if (gatewayPlaylist != null && gatewayPlaylist.description.isNotBlank()) return gatewayPlaylist

        // 备用：CDN 歌单详情接口 (/api/v3/special/info)
        val numericSpecialId = playlistId.takeIf { it.all { ch -> ch.isDigit() } }
            ?: playlistId.split("_").getOrNull(3)?.takeIf { it.all { ch -> ch.isDigit() } }
        if (!numericSpecialId.isNullOrBlank()) {
            val cdnPlaylist = runCatching {
                val response = transport.callAdvanced(
                    path = "/api/v3/special/info",
                    host = "http://mobilecdnbj.kugou.com",
                    query = mapOf(
                        "version" to "9108",
                        "specialid" to numericSpecialId,
                        "plat" to "0"
                    ),
                    sign = false,
                    includeDefaultParams = false
                ).requireSuccess()
                val infoObj = response.data.obj("data") ?: response.data
                val name = (infoObj.text("specialname") ?: infoObj.text("name") ?: "精选歌单")
                    .replace("<em>", "").replace("</em>", "").trim()
                val pic = infoObj.text("imgurl") ?: infoObj.text("pic") ?: infoObj.text("cover")
                val count = infoObj.number("songcount") ?: infoObj.number("count") ?: 0
                val gid = infoObj.text("global_collection_id") ?: infoObj.text("gid") ?: playlistId
                val desc = infoObj.text("intro") ?: ""
                val playCnt = infoObj.text("playcount") ?: ""
                val creator = infoObj.text("nickname") ?: infoObj.text("username") ?: infoObj.text("user_name") ?: ""
                Playlist(
                    id = gid.ifBlank { playlistId },
                    title = name,
                    coverUrl = imageUrl(pic),
                    trackCount = count,
                    playCount = playCnt,
                    description = desc,
                    creatorName = creator,
                    isCreatedByMe = false,
                    isFavorite = false,
                    isLiked = false,
                    globalCollectionId = gid.ifBlank { playlistId },
                    listCreateListid = numericSpecialId,
                    listCreateGid = gid.ifBlank { playlistId }
                )
            }.getOrNull()
            if (cdnPlaylist != null) {
                return if (gatewayPlaylist != null) {
                    gatewayPlaylist.copy(
                        description = gatewayPlaylist.description.ifBlank { cdnPlaylist.description },
                        coverUrl = gatewayPlaylist.coverUrl.ifBlank { cdnPlaylist.coverUrl },
                        trackCount = if (gatewayPlaylist.trackCount > 0) gatewayPlaylist.trackCount else cdnPlaylist.trackCount
                    )
                } else cdnPlaylist
            }
        }

        return gatewayPlaylist
    }

    /**
     * 对齐 MakcRe/KuGouMusicApi module/privilege_lite.js:
     * 批量调用 /v2/get_res_privilege/lite (x-router: media.store.kugou.com)
     * 补全专辑或已购曲目中缺失的音质标签 (level 2=128, 4=320, 5=flac/SQ, 6=Hi-Res)
     */
    suspend fun enrichSongsPrivilegeLite(songs: List<Song>): List<Song> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (songs.isEmpty()) return@withContext songs

        val session = accounts.context()?.session
        // 严格对齐 MakcRe/KuGouMusicApi module/privilege_lite.js UPSTREAM_QUALITIES：
        // 上游一次请求完整音质列表才能拿到 128 的 ogg (level=1) 与全部 relate_goods 条目
        val upstreamQualitiesArr = JsonArray().apply {
            listOf("128", "320", "flac", "high", "viper_atmos", "viper_tape", "viper_clear", "super", "multitrack").forEach { add(it) }
        }

        val enrichedByHash = ConcurrentHashMap<String, JsonObject>()
        val hashToSongMap = HashMap<String, Song>()
        songs.forEach { s ->
            listOf(s.hash128, s.hash320, s.hashFlac, s.hashHiRes, s.hash)
                .map { it.trim().lowercase() }
                .filter { isValidHash(it) }
                .forEach { h -> hashToSongMap.putIfAbsent(h, s) }
        }

        // 收集所有待查 hash，按每批最多 40 个 hash 分片请求 /v2/get_res_privilege/lite，并附带真实 album_audio_id 与 album_id
        val allQueryHashes = songs.flatMap { s ->
            val baseHash = s.hash128.ifBlank { s.hash320.ifBlank { s.hashFlac.ifBlank { s.hash } } }.trim().lowercase()
            listOf(baseHash, s.hashHiRes.trim().lowercase(), s.hashFlac.trim().lowercase(), s.hash.trim().lowercase())
        }.filter { isValidHash(it) }.distinct()

        coroutineScope {
            allQueryHashes.chunked(40).map { hashChunk ->
                async {
                    val resourceArr = JsonArray()
                    for (h in hashChunk) {
                        val owner = hashToSongMap[h]
                        resourceArr.add(JsonObject().apply {
                            addProperty("type", "audio")
                            addProperty("page_id", 1)
                            addProperty("id", 0)
                            addProperty("area_code", "1")
                            addProperty("album_audio_id", owner?.albumAudioId ?: 0L)
                            addProperty("album_id", owner?.albumId?.toLongOrNull() ?: 0L)
                            addProperty("hash", h)
                            add("qualities", upstreamQualitiesArr)
                        })
                    }
                    if (resourceArr.size() == 0) return@async

                    val d = device()
                    val body = JsonObject().apply {
                        addProperty("appid", KugouSigner.APP_ID)
                        addProperty("clientver", KugouSigner.CLIENT_VERSION)
                        addProperty("mid", d.mid)
                        addProperty("clienttime", System.currentTimeMillis() / 1000)
                        addProperty("key", "")
                        addProperty("area_code", "1")
                        addProperty("behavior", "play")
                        addProperty("need_hash_offset", 1)
                        addProperty("relate", 1)
                        addProperty("vip_type", 0)
                        add("qualities", upstreamQualitiesArr)
                        add("resource", resourceArr)
                        add("resources", resourceArr)
                    }

                    runCatching {
                        val resp = transport.callAdvanced(
                            path = "/v2/get_res_privilege/lite",
                            method = "POST",
                            session = session,
                            body = body,
                            headers = mapOf("x-router" to "media.store.kugou.com"),
                            sign = true,
                            includeDefaultParams = true
                        )
                        val dataArr = resp.body.arr("data") ?: resp.data.arr("data") ?: resp.data.arr("resource")
                        val items = dataArr?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
                        items.forEachIndexed { idx, item ->
                            val h = (item.text("hash") ?: "").lowercase()
                            if (h.isNotBlank()) {
                                enrichedByHash[h] = item
                            }
                            hashChunk.getOrNull(idx)?.let { reqHash ->
                                if (reqHash.isNotBlank()) enrichedByHash[reqHash] = item
                            }
                            item.objects("relate_goods").forEach { rg ->
                                val rgH = (rg.text("hash") ?: "").lowercase()
                                if (rgH.isNotBlank()) enrichedByHash[rgH] = item
                            }
                        }
                    }
                }
            }.forEach { it.await() }
        }

        if (enrichedByHash.isEmpty()) return@withContext songs

        songs.map { s ->
            val matchedPrivObjs = listOf(s.hash128, s.hash320, s.hashFlac, s.hashHiRes, s.hash)
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .mapNotNull { enrichedByHash[it] }
                .distinct()
            val privObj = matchedPrivObjs.firstOrNull() ?: return@map s
            val relateGoods = matchedPrivObjs
                .flatMap { it.objects("relate_goods") }
                .distinctBy { (it.text("hash") ?: "").lowercase() to (it.number("level") ?: -1) }
            val transParam = privObj.obj("trans_param")

            // 对齐 privilege_lite.js oggOf(quality, resource):
            // 概念版 128 优先取 trans_param.ogg_128_hash / ogg_128_filesize (level=1)，320 支持 trans_param.ogg_320_hash / ogg_320_filesize
            val ogg128Hash = transParam?.text("ogg_128_hash")?.takeIf { isValidHash(it) }.orEmpty()
            val ogg128Size = (transParam?.number("ogg_128_filesize") ?: 0).toLong()
            val ogg320Hash = transParam?.text("ogg_320_hash")?.takeIf { isValidHash(it) }.orEmpty()
            val ogg320Size = (transParam?.number("ogg_320_filesize") ?: 0).toLong()

            var stdHash = ogg128Hash.ifBlank { s.hash128 }
            var hqHash = s.hash320.ifBlank { ogg320Hash }
            var sqHash = s.hashFlac
            var resHash = s.hashHiRes
            var resSize = if (s.isHiRes && s.fileSizeByte > 0L) s.fileSizeByte else 0L
            var sqSize = if (!s.isHiRes && s.isLossless && s.fileSizeByte > 0L) s.fileSizeByte else 0L
            var hqSize = ogg320Size
            var stdSize = ogg128Size
            var resKbps = if (s.isHiRes && s.bitrateKbps > 0) s.bitrateKbps else 0
            var sqKbps = if (!s.isHiRes && s.isLossless && s.bitrateKbps > 0) s.bitrateKbps else 0
            var hqKbps = if (hqSize > 0L) 320 else 0
            var stdKbps = if (stdSize > 0L) 128 else 0
            var effectiveDurMs = s.durationMs
            val specsMap = mutableMapOf<AudioQuality, SongStreamSpec>()
            var hasLevel1OggStd = ogg128Hash.isNotBlank() && ogg128Size > 0L
            if (hasLevel1OggStd) {
                specsMap[AudioQuality.STANDARD] = SongStreamSpec(ogg128Hash, ogg128Size, 128, "ogg")
            }
            if (ogg320Hash.isNotBlank() && ogg320Size > 0L) {
                specsMap[AudioQuality.HIGH] = SongStreamSpec(ogg320Hash, ogg320Size, 320, "ogg")
            }

            // 第一遍扫描：提取准确的 sqSize (level=5, quality=flac) 和 durationMs
            for (rg in relateGoods) {
                val rgInfo = rg.obj("info")
                val status = rg.number("status") ?: rgInfo?.number("status") ?: 1
                if (status == 0) continue
                val durRaw = (rg.number("duration") ?: rgInfo?.number("duration") ?: rgInfo?.number("timelength") ?: 0).toLong()
                if (effectiveDurMs <= 0L && durRaw > 0L) {
                    effectiveDurMs = if (durRaw in 1 until 10000) durRaw * 1000L else durRaw
                }
                val level = rg.number("level") ?: rgInfo?.number("level") ?: -1
                val qStr = (rg.text("quality") ?: rg.text("type") ?: rgInfo?.text("quality") ?: "").lowercase()
                val sz = (rg.text("size")?.toLongOrNull() ?: rg.text("filesize")?.toLongOrNull()
                    ?: rg.text("file_size")?.toLongOrNull() ?: rgInfo?.text("filesize")?.toLongOrNull()
                    ?: rgInfo?.text("size")?.toLongOrNull() ?: 0L)
                if ((level == 5 || qStr == "flac" || qStr == "sq" || qStr == "lossless") && sz > sqSize) {
                    sqSize = sz
                }
            }

            // 第二遍扫描：严格对齐 MakcRe/KuGouMusicApi module/privilege_lite.js:
            // QUALITY_LEVEL = { 128: [1, 2], 320: [4], flac: [5], high: [6], super: [7] }
            // QUALITY_VIP_TYPE = { viper_tape: 'suvip', viper_clear: 'suvip', viper_atmos: 'suvip' } (正统酷狗超级VIP专属，概念版VIP不可播)
            for (rg in relateGoods) {
                val rgInfo = rg.obj("info")
                val rgHash = (rg.text("hash") ?: rgInfo?.text("hash"))?.takeIf { isValidHash(it) } ?: continue
                val status = rg.number("status") ?: rgInfo?.number("status") ?: 1
                if (status == 0) continue
                val level = rg.number("level") ?: rgInfo?.number("level") ?: -1
                val qStr = (rg.text("quality") ?: rg.text("type") ?: rgInfo?.text("quality") ?: "").lowercase()
                val extStr = (rg.text("extname") ?: rgInfo?.text("extname") ?: rg.text("name") ?: "").lowercase()
                val sz = (rg.text("size")?.toLongOrNull() ?: rg.text("filesize")?.toLongOrNull()
                    ?: rg.text("file_size")?.toLongOrNull() ?: rgInfo?.text("filesize")?.toLongOrNull()
                    ?: rgInfo?.text("size")?.toLongOrNull() ?: 0L)
                val brRaw = (rg.number("bitrate") ?: rg.number("bitRate")
                    ?: rgInfo?.number("bitrate") ?: rgInfo?.number("bitRate") ?: 0).toLong()
                val reportedKbps = if (brRaw > 50000L) (brRaw / 1000L).toInt() else brRaw.toInt()
                val sizeDerivedKbps = if (effectiveDurMs >= 30_000L && sz > 0L) {
                    ((sz * 8L) / effectiveDurMs).toInt()
                } else 0
                val bestTierKbps = when {
                    reportedKbps in 64..6500 && reportedKbps !in listOf(999, 2000) -> reportedKbps
                    sizeDerivedKbps in 64..6500 -> sizeDerivedKbps
                    reportedKbps > 0 -> reportedKbps
                    else -> 0
                }

                when {
                    // 严格剔除正统版《酷狗音乐》专属 suvip 合成音效 (viper_tape / viper_clear / viper_atmos / super / multitrack / level=7)
                    level == 7 || qStr == "super" || qStr.startsWith("viper") || qStr == "multitrack" ||
                        extStr.contains("蝰蛇") || extStr.contains("全景") ||
                        (level == 0 && qStr !in listOf("128", "320", "flac", "high", "sq", "hires", "hi-res", "res")) -> {
                        // Ignore formal KuGou suvip-only Viper / DSD / multitrack streams
                    }
                    // 对齐 privilege_lite.js: level == 6 && quality == "high" 为官方概念版 Hi-Res (涵盖 24bit/48kHz ~850-1150kbps 与 24bit/96kHz/192kHz)
                    level == 6 || qStr == "hires" || qStr == "hi-res" || qStr == "res" || (qStr == "high" && (level == 6 || level == 0)) -> {
                        // 仅排除与 16-bit SQ 文件体积完全相同/近似克隆的伪造条目（真实 24-bit 音源体积显著大于 16-bit SQ，或带明确高码率标识）
                        val isDistinctFromSq = sqSize <= 0L || sz <= 0L ||
                            sz >= (sqSize * 1.08).toLong() ||
                            reportedKbps >= 1100
                        val isTrueHiRes = (sz <= 0L || sz >= 7_500_000L) &&
                            (sqSize <= 0L || sz <= 0L || sz > sqSize) &&
                            isDistinctFromSq
                        if (isTrueHiRes) {
                            resHash = rgHash
                            if (sz > resSize) resSize = sz
                            val hiResKbps = bestTierKbps.takeIf { it >= 400 } ?: sizeDerivedKbps.takeIf { it >= 400 } ?: 2304
                            if (hiResKbps > resKbps) resKbps = hiResKbps
                            specsMap[AudioQuality.HI_RES] = SongStreamSpec(
                                hash = rgHash,
                                sizeBytes = sz,
                                bitrateKbps = hiResKbps,
                                extName = extStr.takeIf { it in listOf("flac", "wav", "ape") } ?: "flac"
                            )
                        } else if (sz >= 4_000_000L) {
                            if (sqHash.isBlank()) sqHash = rgHash
                            if (sz > sqSize) sqSize = sz
                            if (bestTierKbps > sqKbps) sqKbps = bestTierKbps
                            if (AudioQuality.LOSSLESS !in specsMap) {
                                specsMap[AudioQuality.LOSSLESS] = SongStreamSpec(rgHash, sz, bestTierKbps.takeIf { it > 0 } ?: 920, "flac")
                            }
                        }
                    }
                    // 对齐 privilege_lite.js: level == 5 && quality == "flac" 为官方 SQ 无损
                    level == 5 || qStr == "flac" || qStr == "sq" || qStr == "lossless" -> {
                        if (sz <= 0L || sz >= 3_500_000L) {
                            sqHash = rgHash
                            if (sz > sqSize) sqSize = sz
                            val flacKbps = bestTierKbps.takeIf { it in 350..1850 } ?: sizeDerivedKbps.takeIf { it in 350..1850 } ?: 920
                            if (flacKbps > sqKbps) sqKbps = flacKbps
                            specsMap[AudioQuality.LOSSLESS] = SongStreamSpec(
                                hash = rgHash,
                                sizeBytes = sz,
                                bitrateKbps = flacKbps,
                                extName = extStr.takeIf { it in listOf("flac", "ape", "wav") } ?: "flac"
                            )
                        }
                    }
                    // 对齐 privilege_lite.js: level == 4 && quality == "320" 为 HQ 高品 (若已有 ogg_320_hash 则保留 ogg 或记录真实 320 规格)
                    level == 4 || qStr == "320" || qStr == "hq" || (qStr == "high" && level != 6 && level != 0) -> {
                        val useOgg320 = ogg320Hash.isNotBlank() && ogg320Size > 0L
                        hqHash = if (useOgg320) ogg320Hash else rgHash
                        hqSize = if (useOgg320) ogg320Size else if (sz > 0L) sz else hqSize
                        hqKbps = reportedKbps.takeIf { it in 220..400 } ?: 320
                        specsMap[AudioQuality.HIGH] = SongStreamSpec(
                            hash = hqHash,
                            sizeBytes = hqSize,
                            bitrateKbps = hqKbps,
                            extName = if (useOgg320) "ogg" else (extStr.takeIf { it in listOf("mp3", "ogg", "m4a", "aac") } ?: "mp3")
                        )
                    }
                    // 对齐 privilege_lite.js: 128 优先 level=1 / ogg_128_hash（概念版播放的 ogg 版本），其次 level=2（mp3 版本）
                    level == 1 || level == 2 || qStr == "128" -> {
                        if (level == 1 || !hasLevel1OggStd) {
                            if (level == 1) hasLevel1OggStd = true
                            val useOgg128 = ogg128Hash.isNotBlank() && ogg128Size > 0L
                            stdHash = if (useOgg128) ogg128Hash else rgHash
                            stdSize = if (useOgg128) ogg128Size else if (sz > 0L) sz else stdSize
                            stdKbps = reportedKbps.takeIf { it in 64..200 } ?: 128
                            specsMap[AudioQuality.STANDARD] = SongStreamSpec(
                                hash = stdHash,
                                sizeBytes = stdSize,
                                bitrateKbps = stdKbps,
                                extName = if (level == 1 || useOgg128) (extStr.takeIf { it.isNotBlank() } ?: "ogg") else (extStr.takeIf { it.isNotBlank() } ?: "mp3")
                            )
                        }
                    }
                }
            }

            if (resHash.isNotBlank()) {
                val sameAsLower = resHash.equals(sqHash, ignoreCase = true) ||
                    resHash.equals(hqHash, ignoreCase = true) ||
                    resHash.equals(stdHash, ignoreCase = true)
                val notLargerThanSq = resSize > 0L && sqSize > 0L && resSize <= sqSize
                if (sameAsLower || notLargerThanSq) {
                    if (sqHash.isBlank() && !resHash.equals(hqHash, ignoreCase = true) && !resHash.equals(stdHash, ignoreCase = true)) {
                        sqHash = resHash
                    }
                    resHash = ""
                    specsMap.remove(AudioQuality.HI_RES)
                }
            }

            val hasHiRes = resHash.isNotBlank() && (resSize <= 0L || resSize >= 7_500_000L)
            val hasFlac = sqHash.isNotBlank() && (relateGoods.isEmpty() || sqSize <= 0L || sqSize >= 3_500_000L)
            val has320 = hqHash.isNotBlank() || hqSize > 0L || s.quality == "320"

            val qualities = buildList {
                add(AudioQuality.STANDARD)
                if (has320 || hasFlac || hasHiRes) add(AudioQuality.HIGH)
                if (hasFlac || hasHiRes) add(AudioQuality.LOSSLESS)
                if (hasHiRes) add(AudioQuality.HI_RES)
            }
            val primaryHash = when {
                hasHiRes -> resHash
                hasFlac && sqHash.isNotBlank() -> sqHash
                has320 && hqHash.isNotBlank() -> hqHash
                stdHash.isNotBlank() -> stdHash
                else -> s.hash
            }
            val bestSize = when {
                hasHiRes && resSize > 0L -> resSize
                hasFlac && sqSize > 0L -> sqSize
                has320 && hqSize > 0L -> hqSize
                stdSize > 0L -> stdSize
                else -> s.fileSizeByte
            }
            val bestKbps = when {
                hasHiRes && resKbps > 0 -> resKbps
                hasFlac && sqKbps > 0 -> sqKbps
                has320 -> 320
                stdKbps > 0 -> stdKbps
                effectiveDurMs >= 15_000L && bestSize > 0L -> ((bestSize * 8L) / effectiveDurMs).toInt()
                else -> s.bitrateKbps
            }

            val topPriv = privObj.number("privilege") ?: privObj.number("Privilege") ?: -1
            val topPayType = privObj.number("pay_type") ?: privObj.number("PayType") ?: -1
            val topFailProcess = privObj.number("fail_process") ?: privObj.number("FailProcess") ?: -1
            val stdGoodsPriv = relateGoods.firstOrNull { (it.number("level") ?: 0) in 1..2 }?.number("privilege") ?: -1
            val anyTierVip = relateGoods.any { rg ->
                val rgInfo = rg.obj("info")
                val lvl = rg.number("level") ?: rgInfo?.number("level") ?: -1
                val q = (rg.text("quality") ?: rg.text("type") ?: rgInfo?.text("quality") ?: "").lowercase()
                val isCoreTier = lvl in 1..6 || q in listOf("128", "320", "flac", "high", "sq", "hires", "hi-res", "res")
                val p = rg.number("privilege") ?: rgInfo?.number("privilege") ?: -1
                val pt = rg.number("pay_type") ?: rgInfo?.number("pay_type") ?: -1
                val fp = rg.number("fail_process") ?: rgInfo?.number("fail_process") ?: -1
                isCoreTier && (p == 10 || pt == 3 || fp == 4)
            }
            val isVipPriv = when {
                s.isVip -> true
                topPriv == 10 || topPayType == 3 || topFailProcess == 4 || stdGoodsPriv == 10 || anyTierVip -> true
                transParam?.number("pay_block_tpl") == 1 -> true
                topPriv in listOf(0, 8) && topPayType == 0 && !anyTierVip -> false
                else -> s.isVip
            }
            // 对齐 privilege_lite.js: const freeListen = isLite && quality === '128' && neesuvip
            val isLiteFreeListen = !isVipPriv || stdGoodsPriv == 0 || transParam?.number("free_for_vip") == 1
            val privCover = imageUrl(
                privObj.text("sizable_cover") ?: privObj.text("album_img") ?: privObj.text("union_cover")
                    ?: transParam?.text("union_cover")
                    ?: privObj.obj("album_info")?.text("sizable_cover")
            )
            val privAlbumId = (privObj.text("album_id") ?: privObj.obj("album_info")?.text("album_id") ?: "")
                .takeIf { it != "0" }.orEmpty()
            val privAlbumName = (privObj.text("album_name") ?: privObj.obj("album_info")?.text("album_name") ?: "").trim()
            val privPubDate = normalizePublishDate(
                privObj.text("publish_date") ?: privObj.text("publish_time") ?: privObj.text("publishtime")
                    ?: privObj.obj("album_info")?.text("publish_date")
                    ?: transParam?.text("publish_date")
            )

            cacheSongTechMeta(
                keys = listOf(s.id, primaryHash, stdHash, hqHash, sqHash, resHash),
                meta = SongTechMeta(
                    specs = specsMap,
                    albumName = privAlbumName.ifBlank { s.album },
                    publishDate = privPubDate.ifBlank { s.publishDate },
                    durationMs = effectiveDurMs
                )
            )

            s.copy(
                hash = primaryHash,
                hash128 = stdHash,
                hash320 = hqHash,
                hashFlac = sqHash,
                hashHiRes = if (hasHiRes) resHash else "",
                album = s.album.ifBlank { privAlbumName },
                albumId = s.albumId.ifBlank { privAlbumId },
                coverUrl = s.coverUrl.ifBlank { privCover },
                durationMs = effectiveDurMs,
                isHiRes = hasHiRes,
                isLossless = hasFlac || hasHiRes,
                isVip = isVipPriv,
                isFreePrivilege = isLiteFreeListen,
                quality = if (hasHiRes) "high" else if (hasFlac) "flac" else if (has320) "320" else "128",
                availableQualities = qualities,
                bitrateKbps = bestKbps,
                fileSizeByte = bestSize,
                publishDate = s.publishDate.ifBlank { privPubDate }
            )
        }
    }

    suspend fun resolvePlayback(
        song: Song,
        preferredQuality: AudioQuality = song.highestQuality,
        useAccompaniment: Boolean = false
    ): PlaybackResult {
        val effectiveSong = if (useAccompaniment) {
            val accHash = song.accompanyHash.ifBlank { runCatching { matchAccompanyHash(song) }.getOrDefault("") }
            if (accHash.isNotBlank()) {
                song.copy(
                    hash = accHash,
                    hash128 = accHash,
                    hash320 = accHash,
                    hashFlac = "",
                    hashHiRes = ""
                )
            } else song
        } else song

        val targetHash = effectiveSong.hashForQuality(preferredQuality).ifBlank { effectiveSong.hash }
        val modeSuffix = if (useAccompaniment) "_acc" else ""
        val cacheKey = "${targetHash.lowercase()}_${preferredQuality.apiValue}$modeSuffix"
        getValidCachedPlayback(cacheKey)?.let { return it }

        val session = accounts.context()?.session

        // 第一阶段：使用当前已知曲目 Hash 走概念版 VIP 专属通道 (/v6/priv_url) 与多级回退 (/v5/url)
        val directAttempt = runCatching {
            resolvePlaybackInternal(effectiveSong, session, preferredQuality)
        }.getOrNull()
        if (directAttempt != null && directAttempt.url.isNotBlank()) {
            putCachedPlayback(cacheKey, directAttempt)
            return directAttempt
        }

        // 第二阶段：若歌曲缺少概念版 ogg_128_hash / relate_goods 多档位 Hash，主动向 /v2/get_res_privilege/lite 补全音源规格后再试
        if (!useAccompaniment) {
            val enrichedSong = runCatching {
                enrichSongsPrivilegeLite(listOf(effectiveSong)).firstOrNull()
            }.getOrNull()
            if (enrichedSong != null) {
                val enrichedAttempt = runCatching {
                    resolvePlaybackInternal(enrichedSong, session, preferredQuality)
                }.getOrNull()
                if (enrichedAttempt != null && enrichedAttempt.url.isNotBlank()) {
                    putCachedPlayback(cacheKey, enrichedAttempt)
                    return enrichedAttempt
                }
            }

            // 第三阶段：若原曲 Hash 因版权迁移或下架失效，自动通过「歌手 + 歌名」检索同曲目可用音源替身
            if (effectiveSong.title.isNotBlank() && effectiveSong.title != "未知歌曲") {
                val rematchedAttempt = runCatching {
                    val cleanTitle = extractBaseSongTitle(effectiveSong.title).ifBlank { effectiveSong.title }
                    val cleanArtist = effectiveSong.artist.takeIf { it.isNotBlank() && it != "未知歌手" }.orEmpty()
                    val queryKw = "$cleanArtist $cleanTitle".trim()
                    val searchRes = searchSongs(queryKw, page = 1, pageSize = 6)
                    val altCandidate = searchRes.songs.firstOrNull { cand ->
                        cand.hash.isNotBlank() &&
                            !cand.hash.equals(effectiveSong.hash, ignoreCase = true) &&
                            (extractBaseSongTitle(cand.title).equals(cleanTitle, ignoreCase = true) ||
                                cand.title.contains(cleanTitle, ignoreCase = true))
                    } ?: searchRes.songs.firstOrNull { it.hash.isNotBlank() }
                    if (altCandidate != null) {
                        resolvePlaybackInternal(altCandidate, session, preferredQuality)
                    } else null
                }.getOrNull()
                if (rematchedAttempt != null && rematchedAttempt.url.isNotBlank()) {
                    putCachedPlayback(cacheKey, rematchedAttempt)
                    return rematchedAttempt
                }
            }
        }

        throw ApiFailure(null, "该歌曲暂未获取到播放链接")
    }

    private suspend fun resolvePlaybackInternal(
        song: Song,
        session: KugouSession?,
        preferredQuality: AudioQuality
    ): PlaybackResult {
        if (session != null && session.token.isNotBlank()) {
            val vipResult = runCatching { resolveVipPrivResult(song, session, preferredQuality) }.getOrNull()
            if (vipResult != null && vipResult.url.isNotBlank()) {
                return vipResult
            }
        }
        return resolveFallbackResult(song, session, preferredQuality)
    }

    suspend fun resolveUrl(
        song: Song,
        preferredQuality: AudioQuality = song.highestQuality,
        useAccompaniment: Boolean = false
    ): String {
        return resolvePlayback(song, preferredQuality, useAccompaniment).url
    }

    suspend fun matchAccompanyHash(song: Song): String {
        if (isValidHash(song.accompanyHash)) return song.accompanyHash
        val keyword = "${song.artist} ${song.title} 伴奏".trim()
        val matched = runCatching {
            val res = searchSongs(keyword, page = 1, pageSize = 8)
            res.songs.firstOrNull { candidate ->
                candidate.title.contains("伴奏") || candidate.title.contains("Instrumental", ignoreCase = true) ||
                    candidate.accompanyHash.isNotBlank()
            }?.let { it.accompanyHash.ifBlank { it.hash320.ifBlank { it.hash } } }
        }.getOrNull().orEmpty()
        return matched
    }

    internal fun deduceQuality(
        url: String,
        bitrateRaw: Long?,
        fileSizeBytes: Long,
        durationMs: Long,
        extName: String?,
        requestedQuality: AudioQuality? = null,
        knownSpecs: Map<AudioQuality, SongStreamSpec> = emptyMap(),
        responseQuality: String? = null
    ): Pair<AudioQuality, Int> {
        val durationSec = (durationMs / 1000.0).coerceAtLeast(1.0)
        val computedKbps = if (fileSizeBytes > 0 && durationMs >= 15_000L) {
            ((fileSizeBytes * 8.0) / durationSec / 1000.0).toInt()
        } else 0

        val reportedKbps = when {
            bitrateRaw == null || bitrateRaw <= 0L -> 0
            bitrateRaw > 50000L -> (bitrateRaw / 1000L).toInt()
            else -> bitrateRaw.toInt()
        }

        // 1. 优先与 /v2/get_res_privilege/lite 缓存的各档位精确字节数或响应明确标注的 quality 比对，消除 24bit/48kHz Hi-Res 与 16bit SQ 的误判
        if (fileSizeBytes > 0L && knownSpecs.isNotEmpty()) {
            val matchedEntry = knownSpecs.entries.firstOrNull { (_, spec) ->
                spec.sizeBytes > 0L && kotlin.math.abs(spec.sizeBytes - fileSizeBytes) <= 65_536L
            }
            if (matchedEntry != null) {
                val matchedKbps = matchedEntry.value.bitrateKbps.takeIf { it > 0 }
                    ?: computedKbps.takeIf { it > 0 }
                    ?: reportedKbps
                return matchedEntry.key to matchedKbps
            }
        }

        val respQ = responseQuality?.trim()?.lowercase().orEmpty()
        if (respQ.isNotBlank()) {
            when (respQ) {
                "high", "hires", "hi-res", "res" -> {
                    val k = computedKbps.takeIf { it in 400..6500 } ?: reportedKbps.takeIf { it in 400..6500 } ?: 2304
                    return AudioQuality.HI_RES to k
                }
                "flac", "sq", "lossless" -> {
                    val k = computedKbps.takeIf { it in 350..1850 } ?: reportedKbps.takeIf { it in 350..1850 } ?: 920
                    return AudioQuality.LOSSLESS to k
                }
                "320", "hq" -> return AudioQuality.HIGH to (reportedKbps.takeIf { it in 220..400 } ?: 320)
                "128" -> return AudioQuality.STANDARD to (reportedKbps.takeIf { it in 64..200 } ?: 128)
            }
        }

        val urlLower = url.lowercase()
        val extLower = (extName ?: "").lowercase()
        val isExplicitLossy = extLower.contains("mp3") || extLower.contains("m4a") || extLower.contains("aac") || extLower.contains("ogg") ||
            urlLower.endsWith(".mp3") || urlLower.contains(".mp3?") || urlLower.endsWith(".m4a") || urlLower.contains(".m4a?") ||
            urlLower.endsWith(".ogg") || urlLower.contains(".ogg?")
        val isExplicitFlac = extLower.contains("flac") || extLower.contains("wav") || extLower.contains("ape") ||
            urlLower.contains(".flac") || urlLower.contains(".wav")

        // 对于 FLAC 无损流，酷狗 tracker 常返回静态枚举 bitRate (如 320 / 999 / 2000)，优先采用真实文件大小与时长反推的实际码率
        val finalKbps = when {
            (isExplicitFlac || fileSizeBytes >= 7_500_000L) && computedKbps in 350..6500 -> computedKbps
            reportedKbps in 64..450 && isExplicitLossy -> reportedKbps
            computedKbps in 64..6500 -> computedKbps
            reportedKbps > 0 -> reportedKbps
            else -> 0
        }

        val quality = when {
            isExplicitLossy -> {
                when {
                    requestedQuality == AudioQuality.STANDARD -> AudioQuality.STANDARD
                    requestedQuality == AudioQuality.HIGH && finalKbps >= 190 -> AudioQuality.HIGH
                    finalKbps >= 220 -> AudioQuality.HIGH
                    else -> AudioQuality.STANDARD
                }
            }
            isExplicitFlac -> {
                val sqSpecSize = knownSpecs[AudioQuality.LOSSLESS]?.sizeBytes ?: 0L
                val resSpecSize = knownSpecs[AudioQuality.HI_RES]?.sizeBytes ?: 0L
                when {
                    resSpecSize > 0L && fileSizeBytes > 0L && kotlin.math.abs(fileSizeBytes - resSpecSize) < kotlin.math.abs(fileSizeBytes - sqSpecSize) -> AudioQuality.HI_RES
                    sqSpecSize > 0L && fileSizeBytes > 0L && fileSizeBytes <= sqSpecSize + 65_536L -> AudioQuality.LOSSLESS
                    requestedQuality == AudioQuality.HI_RES -> AudioQuality.HI_RES
                    requestedQuality == AudioQuality.LOSSLESS -> AudioQuality.LOSSLESS
                    else -> if (finalKbps >= 1150 || reportedKbps >= 1150) AudioQuality.HI_RES else AudioQuality.LOSSLESS
                }
            }
            requestedQuality == AudioQuality.HI_RES && (finalKbps >= 400 || fileSizeBytes >= 7_500_000L) -> AudioQuality.HI_RES
            requestedQuality == AudioQuality.LOSSLESS && (finalKbps >= 350 || fileSizeBytes >= 4_500_000L) -> AudioQuality.LOSSLESS
            (finalKbps >= 1150 || reportedKbps >= 1150) && fileSizeBytes >= 10_000_000L -> AudioQuality.HI_RES
            finalKbps >= 500 || fileSizeBytes >= 7_500_000L -> AudioQuality.LOSSLESS
            finalKbps >= 220 -> AudioQuality.HIGH
            else -> AudioQuality.STANDARD
        }

        return quality to finalKbps
    }

    private suspend fun resolveVipPrivResult(
        song: Song,
        session: KugouSession,
        preferredQuality: AudioQuality
    ): PlaybackResult? {
        val d = device()
        val effectiveDfid = d.dfid.takeIf { it.isNotBlank() && it != "-" }
            ?: KugouSigner.md5("kg_lite_${d.guid}_${d.mid}").take(24)

        val cachedSpecs = listOf(song.id, song.hash, song.hash128, song.hash320, song.hashFlac, song.hashHiRes)
            .map { it.trim().lowercase() }
            .firstNotNullOfOrNull { if (it.isNotBlank()) songTechMetaCache[it]?.specs else null }
            .orEmpty()

        // 构建按请求音质从高到低排列的候选 Hash 队列，避免因单一高音质 Hash 受限导致整个 /v6/priv_url 通道放弃
        val tierOrder = when (preferredQuality) {
            AudioQuality.HI_RES -> listOf(AudioQuality.HI_RES, AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.LOSSLESS -> listOf(AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.STANDARD -> listOf(AudioQuality.STANDARD, AudioQuality.HIGH)
        }
        val candidateTiers = LinkedHashMap<String, AudioQuality>()
        for (tier in tierOrder) {
            val h = song.hashForQuality(tier).ifBlank { cachedSpecs[tier]?.hash.orEmpty() }.trim().lowercase()
            if (h.isNotBlank()) {
                candidateTiers.putIfAbsent(h, tier)
            }
        }
        if (song.hash.isNotBlank()) {
            candidateTiers.putIfAbsent(song.hash.trim().lowercase(), preferredQuality)
        }

        val effectiveVipToken = session.vipToken?.takeIf { it.isNotBlank() } ?: session.token
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"

        for ((targetHash, tierQuality) in candidateTiers) {
            val clientTimeMs = System.currentTimeMillis()
            val keyRaw = "${targetHash.lowercase()}185672dd44712f60bb1736df5a377e823116${d.mid}${session.userId}"
            val trackerKey = KugouSigner.md5(keyRaw)

            val targetQualities = when (tierQuality) {
                AudioQuality.STANDARD -> listOf("128", "320")
                AudioQuality.HIGH -> listOf("320", "128")
                AudioQuality.LOSSLESS -> listOf("flac", "320", "128")
                AudioQuality.HI_RES -> listOf("high", "flac", "320", "128")
            }

            val qualitiesArr = JsonArray().apply {
                targetQualities.forEach { add(it) }
            }

            val resource = JsonObject().apply {
                addProperty("album_audio_id", song.albumAudioId)
                addProperty("collect_list_id", "3")
                addProperty("collect_time", clientTimeMs)
                addProperty("hash", targetHash.lowercase())
                addProperty("id", 0)
                addProperty("page_id", 1)
                addProperty("type", "audio")
            }

            val trackerParam = JsonObject().apply {
                addProperty("all_m", 1)
                addProperty("auth", "")
                addProperty("is_free_part", 0)
                addProperty("key", trackerKey)
                addProperty("module_id", 0)
                addProperty("need_climax", 1)
                addProperty("need_xcdn", 1)
                addProperty("open_time", "")
                addProperty("pid", "411")
                addProperty("pidversion", "3001")
                addProperty("priv_vip_type", "6")
                addProperty("viptoken", effectiveVipToken)
            }

            val body = JsonObject().apply {
                addProperty("area_code", "1")
                addProperty("behavior", "play")
                add("qualities", qualitiesArr)
                add("resource", resource)
                addProperty("token", session.token)
                add("tracker_param", trackerParam)
                addProperty("userid", session.userId)
                addProperty("vip", 6)
                addProperty("vip_type", 6)
                addProperty("vip_token", effectiveVipToken)
                addProperty("key", trackerKey)
            }

            val query = linkedMapOf(
                "appid" to "3116",
                "clientver" to "11440",
                "mid" to d.mid,
                "dfid" to effectiveDfid,
                "clienttime" to (clientTimeMs / 1000).toString(),
                "token" to session.token,
                "userid" to session.userId
            )
            val bodyStr = KugouSigner.gson.toJson(body)
            val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
            query["signature"] = KugouSigner.md5("$salt$sortedParams$bodyStr$salt")

            val reqHeaders = mapOf(
                "x-router" to "tracker.kugou.com",
                "dfid" to effectiveDfid,
                "mid" to d.mid,
                "clienttime" to (clientTimeMs / 1000).toString(),
                "content-type" to "application/json; charset=utf-8"
            )

            val response = runCatching {
                transport.callAdvanced(
                    path = "/v6/priv_url",
                    host = "https://gateway.kugou.com",
                    method = "POST",
                    query = query,
                    body = body,
                    session = session,
                    headers = reqHeaders,
                    sign = false,
                    includeDefaultParams = false
                )
            }.getOrNull()?.takeIf { extractPlayUrl(it.data) != null } ?: runCatching {
                transport.callAdvanced(
                    path = "/v6/priv_url",
                    host = "http://tracker.kugou.com",
                    method = "POST",
                    query = query,
                    body = body,
                    session = session,
                    headers = reqHeaders,
                    sign = false,
                    includeDefaultParams = false
                )
            }.getOrNull() ?: continue

            val data = response.data
            val privStatus = data.number("priv_status") ?: -1
            if (privStatus == 0) continue

            val failProcess = data.arr("fail_process")
            if (failProcess != null && failProcess.size() > 0) continue
            if (data.obj("ext_info")?.number("is_free_part") == 1) continue

            val fileSize = (data.number("file_size") ?: data.number("fileSize") ?: 0).toLong()
            if (fileSize in 1 until 200 * 1024) continue

            val playUrl = extractPlayUrl(data) ?: continue
            val bitrate = (data.number("bitrate") ?: data.number("bitRate") ?: 0).toLong()
            val extName = data.text("extname") ?: data.text("extName")
            val respQualityStr = data.text("quality") ?: data.obj("ext_info")?.text("quality")
            val respDurSec = (data.number("timeLength") ?: data.number("timelength") ?: 0).toLong()
            val effectiveDurMs = song.durationMs.takeIf { it > 0L } ?: if (respDurSec in 1..10000) respDurSec * 1000L else respDurSec

            val expectedVipQuality = when {
                tierQuality == AudioQuality.HI_RES && song.hashHiRes.isNotBlank() && targetHash.equals(song.hashHiRes, ignoreCase = true) -> AudioQuality.HI_RES
                tierQuality >= AudioQuality.LOSSLESS && song.hashFlac.isNotBlank() && targetHash.equals(song.hashFlac, ignoreCase = true) -> AudioQuality.LOSSLESS
                tierQuality >= AudioQuality.HIGH && song.hash320.isNotBlank() && targetHash.equals(song.hash320, ignoreCase = true) -> AudioQuality.HIGH
                else -> tierQuality
            }
            val (actualQuality, kbps) = deduceQuality(
                url = playUrl,
                bitrateRaw = bitrate,
                fileSizeBytes = fileSize,
                durationMs = effectiveDurMs,
                extName = extName,
                requestedQuality = expectedVipQuality,
                knownSpecs = cachedSpecs,
                responseQuality = respQualityStr
            )
            val (climaxStart, climaxEnd) = extractClimaxRange(data)

            return PlaybackResult(
                url = playUrl,
                actualQuality = actualQuality,
                bitrateKbps = kbps,
                fileSizeByte = fileSize,
                climaxStartMs = climaxStart,
                climaxEndMs = climaxEnd
            )
        }
        return null
    }

    private fun extractClimaxRange(data: JsonObject): Pair<Long, Long> {
        val climaxObj = data.obj("climax_info")
            ?: data.objects("climax_info").firstOrNull()
            ?: data.obj("trans_param")?.obj("climax_info")
            ?: data.objects("trans_param").firstOrNull()?.obj("climax_info")
        if (climaxObj != null) {
            val rawStart = (climaxObj.number("start_time") ?: climaxObj.number("starttime") ?: 0).toLong()
            val rawEnd = (climaxObj.number("end_time") ?: climaxObj.number("endtime") ?: 0).toLong()
            val rawDur = (climaxObj.number("timelength") ?: 0).toLong()
            val startMs = if (rawStart in 1..600) rawStart * 1000L else rawStart
            val endMs = when {
                rawEnd in 1..600 -> rawEnd * 1000L
                rawEnd > 600 -> rawEnd
                rawDur > 0 -> startMs + (if (rawDur in 1..120) rawDur * 1000L else rawDur)
                else -> 0L
            }
            if (startMs > 0L && endMs > startMs + 5_000L) {
                return startMs to endMs
            }
        }
        return 0L to 0L
    }

    private fun normalizeStreamUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> null
        }
    }

    private fun extractPlayUrl(obj: JsonObject): String? {
        normalizeStreamUrl(obj.text("url"))?.let { return it }
        normalizeStreamUrl(obj.text("play_url"))?.let { return it }
        normalizeStreamUrl(obj.text("playUrl"))?.let { return it }

        val urlArr = obj.get("url")?.takeIf { it.isJsonArray }?.asJsonArray
        if (urlArr != null) {
            for (el in urlArr) {
                if (el.isJsonPrimitive) {
                    normalizeStreamUrl(el.asString)?.let { return it }
                }
            }
        }

        obj.objects("trans_param").forEach { tp ->
            normalizeStreamUrl(tp.text("url"))?.let { return it }
        }
        obj.obj("trans_param")?.let { tp ->
            normalizeStreamUrl(tp.text("url"))?.let { return it }
        }

        normalizeStreamUrl(obj.text("backup_url"))?.let { return it }
        normalizeStreamUrl(obj.text("backupUrl"))?.let { return it }
        for (backupKey in listOf("backup_url", "backupUrl")) {
            val backupArr = obj.get(backupKey)?.takeIf { it.isJsonArray }?.asJsonArray
            if (backupArr != null) {
                for (el in backupArr) {
                    if (el.isJsonPrimitive) {
                        normalizeStreamUrl(el.asString)?.let { return it }
                    }
                }
            }
        }
        return null
    }

    private suspend fun resolveFallbackResult(
        song: Song,
        session: KugouSession?,
        preferredQuality: AudioQuality
    ): PlaybackResult {
        val d = device()
        val effectiveDfid = d.dfid.takeIf { it.isNotBlank() && it != "-" }
            ?: KugouSigner.md5("kg_lite_${d.guid}_${d.mid}").take(24)
        val userId = session?.userId ?: "0"
        val clientTime = System.currentTimeMillis() / 1000
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"

        val cachedSpecs = listOf(song.id, song.hash, song.hash128, song.hash320, song.hashFlac, song.hashHiRes)
            .map { it.trim().lowercase() }
            .firstNotNullOfOrNull { if (it.isNotBlank()) songTechMetaCache[it]?.specs else null }
            .orEmpty()

        val qualityTiers = when (preferredQuality) {
            AudioQuality.HI_RES -> listOf(AudioQuality.HI_RES, AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.LOSSLESS -> listOf(AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.STANDARD -> listOf(AudioQuality.STANDARD, AudioQuality.HIGH)
        }

        val candidates = LinkedHashSet<Pair<String, String>>()
        for (tier in qualityTiers) {
            when (tier) {
                AudioQuality.HI_RES -> {
                    val h = song.hashHiRes.ifBlank { cachedSpecs[AudioQuality.HI_RES]?.hash.orEmpty() }
                    if (h.isNotBlank()) candidates.add(h.lowercase() to "high")
                }
                AudioQuality.LOSSLESS -> {
                    val h = song.hashFlac.ifBlank { cachedSpecs[AudioQuality.LOSSLESS]?.hash.orEmpty() }
                    if (h.isNotBlank()) candidates.add(h.lowercase() to "flac")
                }
                AudioQuality.HIGH -> {
                    val h = song.hash320.ifBlank { cachedSpecs[AudioQuality.HIGH]?.hash.orEmpty() }
                    if (h.isNotBlank()) candidates.add(h.lowercase() to "320")
                }
                AudioQuality.STANDARD -> {
                    val specStd = cachedSpecs[AudioQuality.STANDARD]?.hash.orEmpty()
                    if (specStd.isNotBlank()) candidates.add(specStd.lowercase() to "128")
                    val std = song.hash128.ifBlank { song.hash }.lowercase()
                    if (std.isNotBlank()) candidates.add(std to "128")
                }
            }
        }
        if (song.hash.isNotBlank()) {
            val primaryLower = song.hash.lowercase()
            val defaultQ = song.quality.ifBlank { "128" }
            candidates.add(primaryLower to defaultQ)
            candidates.add(primaryLower to "128")
        }

        for ((hashLower, qualityParam) in candidates) {
            val keyRaw = "${hashLower}185672dd44712f60bb1736df5a377e823116${d.mid}$userId"
            val key = KugouSigner.md5(keyRaw)

            val query = linkedMapOf(
                "album_id" to (song.albumId.ifBlank { "0" }),
                "area_code" to "1",
                "hash" to hashLower,
                "ssa_flag" to "is_fromtrack",
                "version" to "11440",
                "page_id" to "967177915",
                "quality" to qualityParam,
                "album_audio_id" to song.albumAudioId.toString(),
                "behavior" to "play",
                "pid" to "411",
                "cmd" to "26",
                "pidversion" to "3001",
                "ppage_id" to "356753938,823673182,967485191",
                "cdnBackup" to "1",
                "clientver" to "11440",
                "appid" to "3116",
                "mid" to d.mid,
                "dfid" to effectiveDfid,
                "clienttime" to clientTime.toString(),
                "key" to key
            )
            if (session != null && session.token.isNotBlank()) {
                query["token"] = session.token
                query["userid"] = session.userId
                query["vipType"] = "6"
                query["vipToken"] = session.vipToken?.takeIf { it.isNotBlank() } ?: session.token
            }

            val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
            query["signature"] = KugouSigner.md5("$salt$sortedParams$salt")

            val reqHeaders = mapOf(
                "x-router" to "trackercdn.kugou.com",
                "dfid" to effectiveDfid,
                "mid" to d.mid,
                "clienttime" to clientTime.toString()
            )

            val response = runCatching {
                transport.callAdvanced(
                    path = "/v5/url",
                    session = session,
                    query = query,
                    headers = reqHeaders,
                    sign = false,
                    includeDefaultParams = false
                )
            }.getOrNull()?.takeIf { extractPlayUrl(it.data) != null } ?: runCatching {
                transport.callAdvanced(
                    path = "/i/v2/",
                    host = "http://trackercdn.kugou.com",
                    session = session,
                    query = query,
                    headers = reqHeaders,
                    sign = false,
                    includeDefaultParams = false
                )
            }.getOrNull()

            response?.let {
                val url = extractPlayUrl(it.data)
                if (!url.isNullOrBlank()) {
                    val fSize = (it.data.number("fileSize") ?: it.data.number("file_size") ?: 0).toLong()
                    val bRate = (it.data.number("bitRate") ?: it.data.number("bitrate") ?: 0).toLong()
                    val ext = it.data.text("extName") ?: it.data.text("extname")
                    val respDurSec = (it.data.number("timeLength") ?: it.data.number("timelength") ?: 0).toLong()
                    val effectiveDurMs = song.durationMs.takeIf { it > 0L } ?: if (respDurSec in 1..10000) respDurSec * 1000L else respDurSec
                    val reqTier = when (qualityParam.lowercase()) {
                        "high", "hires", "res" -> AudioQuality.HI_RES
                        "flac", "sq", "lossless" -> AudioQuality.LOSSLESS
                        "320", "hq" -> AudioQuality.HIGH
                        else -> AudioQuality.STANDARD
                    }
                    val (q, kbps) = deduceQuality(url, bRate, fSize, effectiveDurMs, ext, reqTier, cachedSpecs)
                    val (cStart, cEnd) = extractClimaxRange(it.data)
                    return PlaybackResult(url, q, kbps, fSize, cStart, cEnd)
                }
            }
        }

        // 5. 若整轨因版权或数字专辑需要购买限制，平滑降级至试听流 (IsFreePart=1)
        val defaultHash = song.hash128.ifBlank { song.hash }.lowercase()
        val freeKeyRaw = "${defaultHash}185672dd44712f60bb1736df5a377e823116${d.mid}$userId"
        val freePartQuery = linkedMapOf(
            "album_id" to (song.albumId.ifBlank { "0" }),
            "area_code" to "1",
            "hash" to defaultHash,
            "ssa_flag" to "is_fromtrack",
            "version" to "11440",
            "page_id" to "967177915",
            "quality" to "128",
            "album_audio_id" to song.albumAudioId.toString(),
            "behavior" to "play",
            "pid" to "411",
            "cmd" to "26",
            "pidversion" to "3001",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "clientver" to "11440",
            "appid" to "3116",
            "mid" to d.mid,
            "dfid" to effectiveDfid,
            "clienttime" to clientTime.toString(),
            "key" to KugouSigner.md5(freeKeyRaw),
            "IsFreePart" to "1"
        )
        if (session != null) {
            freePartQuery["token"] = session.token
            freePartQuery["userid"] = session.userId
        }
        val freeSorted = freePartQuery.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        freePartQuery["signature"] = KugouSigner.md5("$salt$freeSorted$salt")
        val freeHeaders = mapOf(
            "x-router" to "trackercdn.kugou.com",
            "dfid" to effectiveDfid,
            "mid" to d.mid,
            "clienttime" to clientTime.toString()
        )
        val freeResp = runCatching {
            transport.callAdvanced(
                path = "/v5/url",
                session = session,
                query = freePartQuery,
                headers = freeHeaders,
                sign = false,
                includeDefaultParams = false
            )
        }.getOrNull()?.takeIf { extractPlayUrl(it.data) != null } ?: runCatching {
            transport.callAdvanced(
                path = "/i/v2/",
                host = "http://trackercdn.kugou.com",
                session = session,
                query = freePartQuery,
                headers = freeHeaders,
                sign = false,
                includeDefaultParams = false
            )
        }.getOrNull()

        freeResp?.let {
            val freeUrl = extractPlayUrl(it.data)
            if (!freeUrl.isNullOrBlank()) {
                val fSize = (it.data.number("fileSize") ?: it.data.number("file_size") ?: 0).toLong()
                val bRate = (it.data.number("bitRate") ?: it.data.number("bitrate") ?: 0).toLong()
                val ext = it.data.text("extName") ?: it.data.text("extname")
                val (q, kbps) = deduceQuality(freeUrl, bRate, fSize, song.durationMs, ext, AudioQuality.STANDARD, cachedSpecs)
                val (cStart, cEnd) = extractClimaxRange(it.data)
                return PlaybackResult(freeUrl, q, kbps, fSize, cStart, cEnd)
            }
        }

        throw ApiFailure(null, "该歌曲暂未获取到播放链接")
    }

    suspend fun searchSongs(keyword: String, page: Int = 1, pageSize: Int = 50): SearchResult {
        if (keyword.isBlank()) return SearchResult(page = page, pageSize = pageSize)
        val session = accounts.context()?.session

        val gatewayResult = runCatching {
            val response = transport.callAdvanced(
                path = "/v2/search/song",
                session = session,
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "AndroidFilter",
                    "albumhide" to "0",
                    "nocollect" to "0",
                    "iscorrection" to "1",
                    "privilegefilter" to "0",
                    "area_code" to "1",
                    "dopicfull" to "1",
                    "style_id" to "1"
                ),
                headers = mapOf(
                    "x-router" to "complexsearch.kugou.com"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            val dataObj = response.data.obj("data") ?: response.data
            val rawSongs = parseSongs(response.data)
            val songs = runCatching { enrichSongsPrivilegeLite(rawSongs) }.getOrDefault(rawSongs)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrNull()

        if (gatewayResult != null && gatewayResult.songs.isNotEmpty()) return gatewayResult

        val webResult = runCatching {
            val response = transport.callAdvanced(
                path = "/song_search_v2",
                host = "https://songsearch.kugou.com",
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "WebFilter",
                    "privilegefilter" to "0",
                    "iscorrection" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val dataObj = response.data.obj("data") ?: response.data
            val rawSongs = parseSongs(response.data)
            val songs = runCatching { enrichSongsPrivilegeLite(rawSongs) }.getOrDefault(rawSongs)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrNull()

        if (webResult != null && webResult.songs.isNotEmpty()) return webResult

        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/search/song",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "plat" to "0",
                    "area_code" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val dataObj = response.data.obj("data") ?: response.data
            val rawSongs = parseSongs(response.data)
            val songs = runCatching { enrichSongsPrivilegeLite(rawSongs) }.getOrDefault(rawSongs)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrElse {
            SearchResult(page = page, pageSize = pageSize)
        }
    }

    suspend fun searchHot(): List<String> {
        // 对齐 MakcRe/KuGouMusicApi module/search_hot.js (/api/v3/search/hot_tab, x-router: msearch.kugou.com) + /v3/search/hot 双通道
        val hotTabKeywords = runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/search/hot_tab",
                query = mapOf(
                    "signature" to "ee44edb9d7155821412d220bcaf509dd",
                    "appid" to "3116",
                    "clientver" to "11440",
                    "plat" to "0"
                ),
                headers = mapOf("x-router" to "msearch.kugou.com"),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val lists = response.data.objects("list").flatMap { it.objects("keywords") }
            lists.mapNotNull { it.text("keyword") ?: it.text("name") }.filter { it.isNotBlank() }
        }.getOrNull()
        if (!hotTabKeywords.isNullOrEmpty()) return hotTabKeywords.distinct()

        return try {
            val response = transport.callAdvanced(
                path = "/v3/search/hot",
                query = mapOf("plat" to "0", "count" to "20"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()
            response.data.objects("info").mapNotNull { it.text("keyword") ?: it.text("tagname") }
                .ifEmpty { defaultHotKeywords() }
        } catch (_: Exception) {
            defaultHotKeywords()
        }
    }

    suspend fun searchSuggest(keyword: String): List<String> {
        if (keyword.isBlank()) return emptyList()
        // 优先对齐 MakcRe/KuGouMusicApi module/search_suggest.js (/v2/getSearchTip, x-router: searchtip.kugou.com)
        val v2Tips = runCatching {
            val response = transport.callAdvanced(
                path = "/v2/getSearchTip",
                query = mapOf(
                    "keyword" to keyword,
                    "MusicTipCount" to "8",
                    "AlbumTipCount" to "2",
                    "CorrectTipCount" to "1",
                    "MVTipCount" to "0",
                    "radiotip" to "1"
                ),
                headers = mapOf("x-router" to "searchtip.kugou.com"),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            val candidates = response.data.objects("data").flatMap { it.objects("RecordDatas") }
            candidates.mapNotNull { it.text("HintInfo") }.filter { it.isNotBlank() }.distinct()
        }.getOrNull()
        if (!v2Tips.isNullOrEmpty()) return v2Tips

        return try {
            val response = transport.callAdvanced(
                path = "/getSearchTip",
                host = "https://searchtip.kugou.com",
                query = mapOf("keyword" to keyword),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val candidates = response.data.objects("data").flatMap { it.objects("RecordDatas") }
            candidates.mapNotNull { it.text("HintInfo") }.filter { it.isNotBlank() }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    internal fun normalizeSearchToken(raw: String): String {
        return raw.trim().lowercase()
            .replace(Regex("""[\s《》〈〉「」『』【】()（）\[\]"'“”‘’·\-_:：,，.。!！?？/\\]+"""), "")
    }

    /**
     * 提取歌手名称的多维核心别名集合（解决搜“邓紫棋”无法匹配“G.E.M.邓紫棋”、搜“林俊杰”无法匹配“JJ 林俊杰”、搜“黄丽玲”无法匹配“A-Lin黄丽玲”等问题）：
     * 1. 完整归一化名称（如 "gem邓紫棋"）
     * 2. 括号内外主名与英文名/本名（如 "周深 (Charlie)" -> "周深", "charlie"）
     * 3. 纯中文核心姓名片段（长度 >= 2，如 "G.E.M.邓紫棋" -> "邓紫棋"，"Kirsty刘瑾睿" -> "刘瑾睿"，"LBI利比" -> "利比"）
     * 4. 纯英文/拉丁字母艺名片段（长度 >= 2，如 "G.E.M.邓紫棋" -> "gem"，"A-Lin黄丽玲" -> "alin"）
     */
    internal fun extractArtistNameVariants(rawName: String): Set<String> {
        val cleaned = rawName.replace("<em>", "").replace("</em>", "").trim()
        if (cleaned.isBlank() || cleaned == "未知歌手" || cleaned == "群星") return emptySet()
        val variants = LinkedHashSet<String>()

        val fullNorm = normalizeSearchToken(cleaned)
        if (fullNorm.isNotBlank()) variants.add(fullNorm)

        // 1. 提取括号外主名称与括号内别名
        val outsideBrackets = cleaned.replace(Regex("""[（(\[【][^）)\]】]*[）)\]】]"""), "").trim()
        val outsideNorm = normalizeSearchToken(outsideBrackets)
        if (outsideNorm.isNotBlank()) variants.add(outsideNorm)

        Regex("""[（(\[【]([^）)\]】]+)[）)\]】]""").findAll(cleaned).forEach { m ->
            val insideNorm = normalizeSearchToken(m.groupValues[1])
            if (insideNorm.length >= 2) variants.add(insideNorm)
        }

        // 2. 提取连续中文字符片段（如从 "G.E.M.邓紫棋" 提取 "邓紫棋"，从 "JJ 林俊杰" 提取 "林俊杰"）
        Regex("""[\u4e00-\u9fa5]{2,}""").findAll(outsideBrackets.ifBlank { cleaned }).forEach { m ->
            variants.add(m.value)
        }

        // 3. 提取拉丁/英文艺名片段（将 G.E.M. / A-Lin 等点号与连字符合并后提取，如 "gem", "alin", "kirsty"）
        val latinMerged = (outsideBrackets.ifBlank { cleaned })
            .replace(Regex("""[.\-_·]+"""), "")
            .lowercase()
        Regex("""[a-z0-9]{2,}""").findAll(latinMerged).forEach { m ->
            variants.add(m.value)
        }

        return variants
    }

    /**
     * 提取专辑的核心名称（剥离 Deluxe / 正式版 / 数字专辑 / 典藏版 / 纪念版 等括号后缀）
     */
    internal fun extractBaseAlbumName(rawAlbumName: String): String {
        val cleaned = rawAlbumName.replace("<em>", "").replace("</em>", "").trim()
        val bookMatch = Regex("""《([^》]+)》""").find(cleaned)
        val candidate = if (bookMatch != null && bookMatch.groupValues[1].isNotBlank()) {
            bookMatch.groupValues[1]
        } else {
            cleaned
        }
        val stripped = candidate
            .replace(Regex("""[（(\[【][^）)\]】]*(?:deluxe|edition|version|正式版|数字专辑|专辑|典藏版|纪念版|豪华版|标准版|重制版|remaster|ep|ost|原声|live|现场)[^）)\]】]*[）)\]】]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[（(\[【][^）)\]】]*[）)\]】]$"""), "")
            .trim()
        return normalizeSearchToken(stripped.ifBlank { candidate })
    }

    internal fun extractBaseSongTitle(rawTitle: String): String {
        val bookMatch = Regex("""《([^》]+)》""").find(rawTitle)
        val baseCandidate = if (bookMatch != null && bookMatch.groupValues[1].isNotBlank()) {
            bookMatch.groupValues[1]
        } else {
            rawTitle
        }
        val stripped = baseCandidate
            .replace(Regex("""[（(\[【][^）)\]】]*(?:live|现场|伴奏|inst|remix|版|dj|cover|翻唱|合唱|独唱|0\.\d+x|1\.\d+x|加速|慢速|降调|升调|氛围|片段|纯享|原声|ost|medals)[^）)\]】]*[）)\]】]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[（(\[【][^）)\]】]*[）)\]】]"""), "")
            .trim()
        return normalizeSearchToken(stripped.ifBlank { baseCandidate })
    }

    suspend fun searchComplex(keyword: String): ComplexSearchResult = coroutineScope {
        if (keyword.isBlank()) return@coroutineScope ComplexSearchResult()

        val songsDeferred = async { runCatching { searchSongs(keyword, page = 1, pageSize = 35) }.getOrNull() }
        val albumsDeferred = async { runCatching { searchAlbums(keyword, page = 1, pageSize = 15) }.getOrNull() }
        val playlistsDeferred = async { runCatching { searchPlaylists(keyword, page = 1, pageSize = 15) }.getOrNull() }
        val artistsDeferred = async { runCatching { searchArtists(keyword, page = 1, pageSize = 12, allowSongFallback = false) }.getOrNull() }

        val rawSongs = songsDeferred.await()?.songs ?: emptyList()
        val rawAlbums = albumsDeferred.await() ?: emptyList()
        val rawPlaylists = playlistsDeferred.await() ?: emptyList()
        val rawArtists = artistsDeferred.await() ?: emptyList()

        val cleanKw = keyword.trim()
        val normKw = normalizeSearchToken(cleanKw)
        if (normKw.isEmpty()) {
            return@coroutineScope ComplexSearchResult(
                songs = rawSongs,
                albums = rawAlbums,
                playlists = rawPlaylists,
                artists = emptyList(),
                intent = SearchIntent.SONG
            )
        }

        // 支持多词组合检索拆分（如 "邓紫棋 摩天动物园"、"周杰伦 范特西"、"邓紫棋 泡沫"）
        val queryParts = cleanKw.split(Regex("""\s+""")).map { normalizeSearchToken(it) }.filter { it.isNotBlank() }

        // 统计前 10 首歌曲中各歌手（含所有中英文别名变体）出现的频次与曲目名、专辑名匹配情况
        val top10Songs = rawSongs.take(10)
        fun splitArtists(artistStr: String): List<String> =
            artistStr.split("、", "/", "&", ",", "，", "feat.", "Feat.", "FT.")
                .map { it.trim() }
                .filter { it.isNotBlank() && it != "未知歌手" && it != "群星" }

        val artistAppearanceCount = HashMap<String, Int>()
        for (s in top10Songs) {
            val seenVariantsInSong = HashSet<String>()
            for (a in splitArtists(s.artist)) {
                for (variant in extractArtistNameVariants(a)) {
                    if (variant.isNotBlank() && seenVariantsInSong.add(variant)) {
                        artistAppearanceCount[variant] = (artistAppearanceCount[variant] ?: 0) + 1
                    }
                }
            }
        }

        // 统计前 10 首歌曲中歌名（或核心歌名）与搜索词匹配的曲目数
        val songTitleMatchCount = top10Songs.count { s ->
            val normTitle = normalizeSearchToken(s.title)
            val baseTitle = extractBaseSongTitle(s.title)
            normTitle == normKw || baseTitle == normKw ||
                (normKw.length >= 2 && (normTitle.startsWith(normKw) || baseTitle.contains(normKw)))
        }

        // 统计前 10 首歌曲中所属专辑 (song.album) 与搜索词精确匹配的不同曲目数（用于识别《范特西》《叶惠美》《摩天动物园》等专辑搜索）
        val distinctAlbumTrackTitles = top10Songs.filter { s ->
            val normSongAlb = normalizeSearchToken(s.album)
            val baseSongAlb = extractBaseAlbumName(s.album)
            normSongAlb.isNotBlank() && (
                normSongAlb == normKw || baseSongAlb == normKw ||
                    (queryParts.size >= 2 && queryParts.any { p -> p.length >= 2 && (normSongAlb == p || baseSongAlb == p) })
                )
        }.map { extractBaseSongTitle(it.title).ifBlank { normalizeSearchToken(it.title) } }.toSet()
        val distinctAlbumTrackCount = distinctAlbumTrackTitles.size

        // 构建候选歌手池（合并接口返回歌手与前排歌曲的主唱歌手）
        val candidatePool = mutableListOf<ArtistItem>()
        candidatePool.addAll(rawArtists.filter { it.id.isNotBlank() && it.id != "0" })
        for (song in top10Songs) {
            if (song.artistId.isNotBlank() && song.artistId != "0") {
                val primaryName = splitArtists(song.artist).firstOrNull().orEmpty()
                val primaryVariants = extractArtistNameVariants(primaryName)
                if (primaryName.isNotBlank() && candidatePool.none { existing ->
                        existing.id == song.artistId || extractArtistNameVariants(existing.name).intersect(primaryVariants).isNotEmpty()
                    }
                ) {
                    candidatePool.add(
                        ArtistItem(
                            id = song.artistId,
                            name = primaryName,
                            avatarUrl = song.coverUrl
                        )
                    )
                }
            }
        }

        // 识别搜索词是否直接命中知名歌手本人（支持“邓紫棋”命中“G.E.M.邓紫棋”，同时排除搜索“勋章”时单曲数为5且不在前排唱歌的同名冷门账号）
        val verifiedDirectArtists = candidatePool.mapNotNull { artist ->
            val variants = extractArtistNameVariants(artist.name)
            if (variants.isEmpty()) return@mapNotNull null
            val appearances = variants.maxOfOrNull { artistAppearanceCount[it] ?: 0 } ?: 0
            val isCatalogRich = artist.songCount >= 20 || artist.albumCount >= 2
            val isExactOrCoreName = normKw in variants
            val isContainedMatch = normKw.length >= 2 && variants.any { v ->
                v.contains(normKw) || (v.length >= 2 && normKw.contains(v) && queryParts.size == 1)
            }

            // 核心防误判规则：若歌手名虽然等于搜索词（如“勋章”），但前排热门单曲没有一首是TA唱的，且前排单曲歌名本身就是“勋章”，
            // 说明用户搜的是歌曲《勋章》，该歌手只是撞名冷门账号，必须剔除！
            if (appearances == 0 && songTitleMatchCount >= 2) {
                return@mapNotNull null
            }
            if (appearances == 0 && !isCatalogRich) {
                return@mapNotNull null
            }

            val score = when {
                isExactOrCoreName && appearances >= 3 -> 130 + appearances * 5
                isExactOrCoreName && isCatalogRich && songTitleMatchCount == 0 -> 115
                isExactOrCoreName && appearances >= 1 && isCatalogRich -> 100
                isContainedMatch && appearances >= 4 -> 96 + appearances * 3
                isContainedMatch && isCatalogRich && songTitleMatchCount == 0 && appearances >= 2 -> 92
                else -> 0
            }
            if (score >= 90) artist to score else null
        }
            .sortedWith(compareByDescending<Pair<ArtistItem, Int>> { it.second }.thenByDescending { it.first.songCount })
            .map { it.first }
            .distinctBy { extractArtistNameVariants(it.name).firstOrNull() ?: normalizeSearchToken(it.name) }
            .toMutableList()

        // 判断显式意图后缀或特征词
        val explicitPlaylistQuery = cleanKw.endsWith("歌单") || cleanKw.endsWith("合集") || cleanKw.endsWith("精选集") ||
            cleanKw.contains("歌单") || cleanKw.contains("必听") || cleanKw.contains("全歌曲") ||
            cleanKw.contains("背景音乐") || cleanKw.contains("助眠") || cleanKw.contains("车载")
        val explicitAlbumQuery = cleanKw.endsWith("专辑") || cleanKw.endsWith("原声带") || cleanKw.endsWith("OST", ignoreCase = true)

        // 匹配专辑（支持完整专辑名、剥离版本后缀的核心专辑名、以及“歌手 + 专辑名”组合检索）
        val exactAlbumMatch = rawAlbums.firstOrNull { alb ->
            val normAlb = normalizeSearchToken(alb.name)
            val baseAlb = extractBaseAlbumName(alb.name)
            val albArtistVariants = extractArtistNameVariants(alb.artist)
            val isDirectAlbumName = (normAlb == normKw || baseAlb == normKw) && normAlb.isNotBlank()
            val isCompositeArtistAlbum = queryParts.size >= 2 &&
                queryParts.any { p -> p in albArtistVariants } &&
                queryParts.any { p -> p.length >= 2 && (normAlb == p || baseAlb == p) }
            val isNotSingleTrack = alb.songCount == 0 || alb.songCount >= 2
            (isDirectAlbumName || isCompositeArtistAlbum) && isNotSingleTrack
        }

        // 识别“歌手 + 专辑名”或纯专辑名搜索信号：
        // 1. 存在精确匹配专辑且无同名主打歌 (songTitleMatchCount == 0，如《范特西》《叶惠美》)
        // 2. 存在精确匹配专辑且前10首单曲中包含该专辑的 >= 2 首不同曲目（如搜《摩天动物园》《新的心跳》《启示录》）
        // 3. 用户输入了“歌手 + 专辑名”复合词且命中该歌手的专辑
        val isCompositeAlbumHit = exactAlbumMatch != null && queryParts.size >= 2 &&
            queryParts.any { p -> p in extractArtistNameVariants(exactAlbumMatch.artist) }
        val isStrongAlbumIntent = exactAlbumMatch != null && (
            explicitAlbumQuery ||
                isCompositeAlbumHit ||
                songTitleMatchCount == 0 ||
                distinctAlbumTrackCount >= 2 ||
                (exactAlbumMatch.songCount >= 5 && songTitleMatchCount <= 1)
            )

        // 严格约束歌单意图匹配：仅当歌单名与搜索词精确相等或以较长搜索词开头（绝不用宽泛的 contains 劫持歌手/专辑搜索）
        val exactPlaylistMatch = rawPlaylists.firstOrNull { pl ->
            val normPl = normalizeSearchToken(pl.name)
            (normPl == normKw || (normKw.length >= 4 && normPl.startsWith(normKw))) && pl.trackCount >= 5
        }

        // 综合判定用户的真实搜索意图 SearchIntent
        val topArtistAppearances = verifiedDirectArtists.firstOrNull()?.let { art ->
            extractArtistNameVariants(art.name).maxOfOrNull { artistAppearanceCount[it] ?: 0 } ?: 0
        } ?: 0

        val searchIntent: SearchIntent = when {
            explicitPlaylistQuery && rawPlaylists.isNotEmpty() -> SearchIntent.PLAYLIST
            explicitAlbumQuery && rawAlbums.isNotEmpty() -> SearchIntent.ALBUM
            // 若用户搜的是单词歌手名（如“邓紫棋”、“G.E.M.邓紫棋”、“GEM”、“鹿晗”），且命中直接歌手
            verifiedDirectArtists.isNotEmpty() && queryParts.size == 1 && !isStrongAlbumIntent && (
                topArtistAppearances >= 3 || songTitleMatchCount <= 1
                ) -> SearchIntent.ARTIST
            // 若命中高置信度专辑（如《范特西》《叶惠美》《摩天动物园》或“周杰伦 范特西”），优先判定为专辑意图 ALBUM
            isStrongAlbumIntent -> SearchIntent.ALBUM
            // 若单词直接命中知名歌手且非专辑/单曲
            verifiedDirectArtists.isNotEmpty() && queryParts.size == 1 && songTitleMatchCount <= 1 -> SearchIntent.ARTIST
            // 若前排有多首同名歌曲及翻唱/Live版本（如搜“勋章”、“光年之外”），判定为单曲意图 SONG
            songTitleMatchCount >= 2 -> SearchIntent.SONG
            exactAlbumMatch != null -> SearchIntent.ALBUM
            exactPlaylistMatch != null && verifiedDirectArtists.isEmpty() && songTitleMatchCount == 0 -> SearchIntent.PLAYLIST
            verifiedDirectArtists.isNotEmpty() -> SearchIntent.ARTIST
            else -> SearchIntent.SONG
        }

        // 根据搜索意图对单曲进行精细化优先级重排
        val rankedSongs = when {
            searchIntent == SearchIntent.SONG -> {
                val exactTitleSongs = rawSongs.filter {
                    normalizeSearchToken(it.title) == normKw || extractBaseSongTitle(it.title) == normKw
                }
                val dominantOriginalArtistNorm = exactTitleSongs
                    .flatMap { s ->
                        val weight = (if (s.isHiRes) 4 else if (s.isLossless) 3 else 1) +
                            (if (s.isVip) 2 else 0) +
                            (if (normalizeSearchToken(s.title) == normKw) 3 else 0)
                        splitArtists(s.artist).map { normalizeSearchToken(it) to weight }
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { it.value.sum() }
                    .maxByOrNull { it.value }?.key.orEmpty()

                fun songRankScore(s: Song, originalIndex: Int): Int {
                    val normTitle = normalizeSearchToken(s.title)
                    val baseTitle = extractBaseSongTitle(s.title)
                    val songArtistVariants = splitArtists(s.artist).flatMap { extractArtistNameVariants(it) }.toSet()
                    val isDominantArtist = dominantOriginalArtistNorm.isNotBlank() && songArtistVariants.contains(dominantOriginalArtistNorm)
                    val isQueryInArtistAndTitle = songArtistVariants.any { normKw.contains(it) && it.length >= 2 } &&
                        baseTitle.isNotBlank() && normKw.contains(baseTitle)

                    val titleLower = s.title.lowercase()
                    val isDerivative = titleLower.contains("0.8x") || titleLower.contains("0.9x") ||
                        titleLower.contains("1.1x") || titleLower.contains("1.2x") ||
                        titleLower.contains("dj") || titleLower.contains("伴奏") ||
                        titleLower.contains("铃声") || titleLower.contains("片段")
                    val isLive = titleLower.contains("live") || titleLower.contains("现场") || titleLower.contains("演唱会")

                    var score = 0
                    when {
                        normTitle == normKw || isQueryInArtistAndTitle -> {
                            score += 1000
                            if (isDominantArtist) score += 350
                        }
                        baseTitle == normKw -> {
                            score += 750
                            if (isDominantArtist && isLive) score += 220
                            else if (isDominantArtist) score += 180
                        }
                        normTitle.startsWith(normKw) || baseTitle.contains(normKw) -> {
                            score += 450
                            if (isDominantArtist) score += 100
                        }
                        else -> {
                            score += 100
                        }
                    }

                    if (s.isHiRes) score += 65
                    else if (s.isLossless) score += 45
                    if (s.isVip) score += 25
                    if (s.coverUrl.isNotBlank()) score += 15
                    if (isDerivative) score -= 160
                    score -= originalIndex * 2
                    return score
                }

                rawSongs.mapIndexed { idx, s -> s to songRankScore(s, idx) }
                    .sortedByDescending { it.second }
                    .map { it.first }
            }
            searchIntent == SearchIntent.ARTIST && verifiedDirectArtists.isNotEmpty() -> {
                val targetVariants = extractArtistNameVariants(verifiedDirectArtists.first().name)
                rawSongs.withIndex()
                    .sortedWith(
                        compareByDescending<IndexedValue<Song>> { iv ->
                            splitArtists(iv.value.artist).any { a -> extractArtistNameVariants(a).intersect(targetVariants).isNotEmpty() }
                        }.thenBy { it.index }
                    )
                    .map { it.value }
            }
            searchIntent == SearchIntent.ALBUM && exactAlbumMatch != null -> {
                val targetAlbNorm = normalizeSearchToken(exactAlbumMatch.name)
                val targetAlbBase = extractBaseAlbumName(exactAlbumMatch.name)
                rawSongs.withIndex()
                    .sortedWith(
                        compareByDescending<IndexedValue<Song>> { iv ->
                            val sAlb = normalizeSearchToken(iv.value.album)
                            val sAlbBase = extractBaseAlbumName(iv.value.album)
                            iv.value.albumId == exactAlbumMatch.id ||
                                (sAlb.isNotBlank() && (sAlb == targetAlbNorm || sAlbBase == targetAlbBase))
                        }.thenBy { it.index }
                    )
                    .map { it.value }
            }
            else -> rawSongs
        }

        // 解析原唱歌手、主歌手或专辑创作者信息
        var primaryArtistItem: ArtistItem? = null
        val finalArtists: List<ArtistItem> = when (searchIntent) {
            SearchIntent.ARTIST -> {
                if (verifiedDirectArtists.isNotEmpty()) {
                    val first = verifiedDirectArtists[0]
                    val enrichedFirst = if (first.id.isNotBlank() && (first.avatarUrl.isBlank() || (first.songCount == 0 && first.albumCount == 0))) {
                        runCatching { artistDetail(first.id) }.getOrNull()?.let { detailed ->
                            first.copy(
                                name = detailed.name.ifBlank { first.name },
                                avatarUrl = detailed.avatarUrl.ifBlank { first.avatarUrl },
                                songCount = if (detailed.songCount > 0) detailed.songCount else first.songCount,
                                albumCount = if (detailed.albumCount > 0) detailed.albumCount else first.albumCount,
                                intro = detailed.intro.ifBlank { first.intro }
                            )
                        } ?: first
                    } else first
                    verifiedDirectArtists[0] = enrichedFirst
                    primaryArtistItem = enrichedFirst
                }
                verifiedDirectArtists.take(3)
            }
            SearchIntent.SONG, SearchIntent.ALBUM -> {
                val topHit = rankedSongs.firstOrNull()
                val targetArtistName = if (searchIntent == SearchIntent.ALBUM && exactAlbumMatch != null && exactAlbumMatch.artist.isNotBlank() && exactAlbumMatch.artist != "未知歌手" && exactAlbumMatch.artist != "群星") {
                    splitArtists(exactAlbumMatch.artist).firstOrNull().orEmpty()
                } else {
                    topHit?.let { splitArtists(it.artist).firstOrNull() }.orEmpty()
                }
                val targetVariants = extractArtistNameVariants(targetArtistName)
                if (targetVariants.isNotEmpty()) {
                    val fromPool = candidatePool.firstOrNull { extractArtistNameVariants(it.name).intersect(targetVariants).isNotEmpty() }
                    val baseOrig = fromPool ?: if (topHit != null && topHit.artistId.isNotBlank() && topHit.artistId != "0") {
                        ArtistItem(
                            id = topHit.artistId,
                            name = targetArtistName,
                            avatarUrl = topHit.coverUrl
                        )
                    } else null
                    if (baseOrig != null && baseOrig.id.isNotBlank() && baseOrig.id != "0") {
                        primaryArtistItem = if (baseOrig.songCount == 0 && baseOrig.albumCount == 0) {
                            runCatching { artistDetail(baseOrig.id) }.getOrNull()?.let { detailed ->
                                baseOrig.copy(
                                    name = detailed.name.ifBlank { baseOrig.name },
                                    avatarUrl = detailed.avatarUrl.ifBlank { baseOrig.avatarUrl },
                                    songCount = if (detailed.songCount > 0) detailed.songCount else baseOrig.songCount,
                                    albumCount = if (detailed.albumCount > 0) detailed.albumCount else baseOrig.albumCount,
                                    intro = detailed.intro.ifBlank { baseOrig.intro }
                                )
                            } ?: baseOrig
                        } else baseOrig
                    }
                }
                if (searchIntent == SearchIntent.ALBUM && primaryArtistItem != null) listOf(primaryArtistItem!!) else emptyList()
            }
            else -> emptyList()
        }

        // 对齐综合搜索体验：按相关性排序专辑与歌单，仅在单曲意图下剔除毫无关联的噪音歌单/专辑，保留同名专辑与关联结果
        val sortedAlbums = rawAlbums.sortedByDescending { alb ->
            val normAlb = normalizeSearchToken(alb.name)
            val baseAlb = extractBaseAlbumName(alb.name)
            when {
                normAlb == normKw || baseAlb == normKw -> 120
                queryParts.size >= 2 && queryParts.any { p -> p.length >= 2 && (normAlb == p || baseAlb == p) } -> 110
                normAlb.startsWith(normKw) -> 85
                normAlb.contains(normKw) -> 65
                else -> 10
            }
        }

        val finalAlbums = when (searchIntent) {
            SearchIntent.ALBUM -> sortedAlbums
            SearchIntent.ARTIST -> {
                val targetVariants = primaryArtistItem?.let { extractArtistNameVariants(it.name) }.orEmpty()
                sortedAlbums.filter { alb ->
                    targetVariants.isEmpty() ||
                        extractArtistNameVariants(alb.artist).intersect(targetVariants).isNotEmpty() ||
                        targetVariants.any { v -> normalizeSearchToken(alb.name).contains(v) }
                }.ifEmpty { sortedAlbums.take(6) }
            }
            SearchIntent.SONG -> {
                // 单曲意图下：若存在同名或高度匹配的专辑（如搜《新的心跳》《七里香》《光年之外》），保留展示在综合页！
                sortedAlbums.filter { alb ->
                    val normAlb = normalizeSearchToken(alb.name)
                    val baseAlb = extractBaseAlbumName(alb.name)
                    normAlb == normKw || baseAlb == normKw
                }.take(4)
            }
            SearchIntent.PLAYLIST -> sortedAlbums.take(4)
        }

        val sortedPlaylists = rawPlaylists.sortedByDescending { pl ->
            val normPl = normalizeSearchToken(pl.name)
            when {
                normPl == normKw -> 100
                normPl.startsWith(normKw) -> 85
                normPl.contains(normKw) -> 70
                else -> 20
            }
        }

        val finalPlaylists = when (searchIntent) {
            SearchIntent.SONG -> {
                // 若是如《勋章》这类纯单曲搜索且歌单与搜索词毫无字面关联，过滤无关噪音歌单；若歌单标题包含该曲名则保留
                sortedPlaylists.filter { pl ->
                    normalizeSearchToken(pl.name).contains(normKw)
                }.take(6)
            }
            SearchIntent.ALBUM -> sortedPlaylists.filter { pl ->
                normalizeSearchToken(pl.name).contains(normKw)
            }.take(6)
            SearchIntent.PLAYLIST -> sortedPlaylists
            SearchIntent.ARTIST -> sortedPlaylists
        }

        ComplexSearchResult(
            songs = rankedSongs,
            albums = finalAlbums,
            playlists = finalPlaylists,
            artists = finalArtists,
            intent = searchIntent,
            primaryArtist = primaryArtistItem
        )
    }

    suspend fun searchAlbums(keyword: String, page: Int = 1, pageSize: Int = 30): List<AlbumItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

        val fromGateway = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/search/album",
                session = session,
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "AndroidFilter",
                    "iscorrection" to "1",
                    "category" to "1"
                ),
                headers = mapOf(
                    "x-router" to "complexsearch.kugou.com",
                    "kg-tid" to "1"
                ),
                sign = true,
                includeDefaultParams = true
            )
            parseAlbumItems(response.data)
        }.getOrNull()
        if (!fromGateway.isNullOrEmpty()) return fromGateway

        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/search/album",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "version" to "9108",
                    "plat" to "0"
                ),
                sign = false,
                includeDefaultParams = false
            )
            parseAlbumItems(response.data)
        }.getOrDefault(emptyList())
    }

    private fun parseAlbumItems(root: JsonObject): List<AlbumItem> {
        val dataObj = root.obj("data") ?: root
        val array = listOf("lists", "info", "data", "record", "albums", "album_list").asSequence()
            .mapNotNull { dataObj.arr(it) ?: root.arr(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = (o.text("albumid") ?: o.text("album_id") ?: o.text("AlbumID") ?: o.text("id") ?: "").trim()
            if (id.isBlank() || id == "0") return@mapNotNull null
            val name = (o.text("albumname") ?: o.text("album_name") ?: o.text("AlbumName") ?: o.text("name") ?: "精选专辑")
                .replace("<em>", "").replace("</em>", "").trim()
            val singersJoined = listOfNotNull(
                o.objects("authors").takeIf { it.isNotEmpty() },
                o.objects("singers").takeIf { it.isNotEmpty() },
                o.objects("Singers").takeIf { it.isNotEmpty() }
            ).firstOrNull()?.mapNotNull {
                it.text("author_name") ?: it.text("name") ?: it.text("singername")
            }?.filter { it.isNotBlank() }?.joinToString("、")
            val artist = (o.text("singername") ?: o.text("singer") ?: o.text("author_name")
                ?: o.text("SingerName") ?: o.text("singer_name")
                ?: singersJoined?.takeIf { it.isNotBlank() }
                ?: "未知歌手")
                .replace("<em>", "").replace("</em>", "").trim()
            val coverRaw = o.text("img") ?: o.text("pic") ?: o.text("imgurl") ?: o.text("sizable_cover") ?: o.text("album_img") ?: o.text("cover")
            val count = o.number("songcount") ?: o.number("song_count") ?: o.number("audio_count") ?: 0
            val pubDate = (o.text("publishtime") ?: o.text("publish_date") ?: o.text("publish_time") ?: "").take(10)
            val intro = (o.text("intro") ?: "").replace("<em>", "").replace("</em>", "").trim()
            val company = o.text("publish_company") ?: o.text("company") ?: ""
            AlbumItem(
                id = id,
                name = name,
                artist = artist,
                coverUrl = imageUrl(coverRaw),
                songCount = count,
                publishDate = pubDate,
                intro = intro,
                company = company
            )
        }
    }

    suspend fun searchPlaylists(keyword: String, page: Int = 1, pageSize: Int = 30): List<PlaylistItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

        val fromGateway = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/search/special",
                session = session,
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "AndroidFilter",
                    "iscorrection" to "1"
                ),
                headers = mapOf(
                    "x-router" to "complexsearch.kugou.com",
                    "kg-tid" to "1"
                ),
                sign = true,
                includeDefaultParams = true
            )
            parsePlaylistItems(response.data)
        }.getOrNull()
        if (!fromGateway.isNullOrEmpty()) return fromGateway

        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/search/special",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "version" to "9108",
                    "plat" to "0"
                ),
                sign = false,
                includeDefaultParams = false
            )
            parsePlaylistItems(response.data)
        }.getOrDefault(emptyList())
    }

    private fun parsePlaylistItems(root: JsonObject): List<PlaylistItem> {
        val dataObj = root.obj("data") ?: root
        val array = listOf("lists", "info", "data", "special").asSequence()
            .mapNotNull { dataObj.arr(it) ?: root.arr(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val gid = o.text("global_collection_id") ?: o.text("gid") ?: ""
            val specialId = o.text("specialid") ?: o.text("id") ?: ""
            val id = gid.ifBlank { specialId }
            if (id.isBlank()) return@mapNotNull null
            val name = (o.text("specialname") ?: o.text("name") ?: "精选歌单")
                .replace("<em>", "").replace("</em>", "").trim()
            val coverRaw = o.text("img") ?: o.text("pic") ?: o.text("imgurl") ?: o.text("cover") ?: o.text("flexible_cover")
            val playCount = o.text("play_count")?.toLongOrNull()
                ?: o.text("playcount")?.toLongOrNull()
                ?: o.text("total_play_count")?.toLongOrNull()
                ?: 0L
            val trackCount = o.number("song_count") ?: o.number("songcount") ?: o.number("count") ?: 0
            val creator = (o.text("nickname") ?: o.text("username") ?: "酷狗音乐")
                .replace("<em>", "").replace("</em>", "").trim()
            val intro = (o.text("intro") ?: o.text("description") ?: "")
                .replace("<em>", "").replace("</em>", "").trim()
            PlaylistItem(
                id = id,
                name = name,
                coverUrl = imageUrl(coverRaw),
                playCount = playCount,
                trackCount = trackCount,
                creatorName = creator,
                intro = intro
            )
        }
    }

    suspend fun searchArtists(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30,
        allowSongFallback: Boolean = true
    ): List<ArtistItem> = coroutineScope {
        if (keyword.isBlank()) return@coroutineScope emptyList()
        val session = accounts.context()?.session

        val gatewayDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/v1/search/author",
                    session = session,
                    query = mapOf(
                        "keyword" to keyword,
                        "page" to page.toString(),
                        "pagesize" to pageSize.toString(),
                        "platform" to "AndroidFilter",
                        "iscorrection" to "1"
                    ),
                    headers = mapOf(
                        "x-router" to "complexsearch.kugou.com",
                        "kg-tid" to "1"
                    ),
                    sign = true,
                    includeDefaultParams = true
                )
                parseArtistItems(response.data).ifEmpty { parseArtistItems(response.body) }
            }.getOrNull()
        }

        val cdnDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/api/v3/search/singer",
                    host = "http://mobilecdnbj.kugou.com",
                    query = mapOf(
                        "version" to "9108",
                        "keyword" to keyword,
                        "page" to page.toString(),
                        "pagesize" to pageSize.toString(),
                        "plat" to "0"
                    ),
                    sign = false,
                    includeDefaultParams = false
                )
                parseArtistItems(response.data).ifEmpty { parseArtistItems(response.body) }
            }.getOrNull()
        }

        val fromGateway = gatewayDef.await().orEmpty()
        val fromCdn = cdnDef.await().orEmpty()

        val mergedRaw = when {
            fromGateway.isNotEmpty() && fromCdn.isNotEmpty() -> {
                val cdnById = fromCdn.associateBy { it.id }
                val cdnByName = fromCdn.associateBy { it.name.lowercase() }
                val combined = fromGateway.map { gw ->
                    val cdn = cdnById[gw.id] ?: cdnByName[gw.name.lowercase()]
                    if (cdn != null) {
                        gw.copy(
                            avatarUrl = gw.avatarUrl.ifBlank { cdn.avatarUrl },
                            songCount = maxOf(gw.songCount, cdn.songCount),
                            albumCount = maxOf(gw.albumCount, cdn.albumCount),
                            intro = gw.intro.ifBlank { cdn.intro }
                        )
                    } else gw
                }.toMutableList()
                val seenIds = combined.map { it.id }.toHashSet()
                fromCdn.forEach { cdn ->
                    if (seenIds.add(cdn.id)) combined.add(cdn)
                }
                combined
            }
            fromGateway.isNotEmpty() -> fromGateway
            fromCdn.isNotEmpty() -> fromCdn
            else -> emptyList()
        }

        val baseList = if (mergedRaw.isNotEmpty()) {
            mergedRaw
        } else if (!allowSongFallback) {
            return@coroutineScope emptyList()
        } else {
            runCatching {
                val songs = searchSongs(keyword, page = 1, pageSize = 20).songs
                val artists = mutableListOf<ArtistItem>()
                val seen = mutableSetOf<String>()
                for (song in songs) {
                    val artistId = song.artistId.trim()
                    val artistName = song.artist.split("、", "/", "&", ",").firstOrNull()?.trim().orEmpty()
                    if (artistId.isNotBlank() && artistId != "0" && artistName.isNotBlank() && artistName != "未知歌手" && seen.add(artistId)) {
                        artists.add(
                            ArtistItem(
                                id = artistId,
                                name = artistName,
                                avatarUrl = song.coverUrl,
                                songCount = 0,
                                albumCount = 0
                            )
                        )
                        if (artists.size >= pageSize) break
                    }
                }
                artists
            }.getOrDefault(emptyList())
        }

        // 并发通过 artistDetail 补全前排歌手的头像与单曲/专辑数量（解决歌手分类页部分歌手无头像或作品数为空的问题）
        baseList.mapIndexed { idx, item ->
            async {
                if (idx < 12 && item.id.isNotBlank() && item.id != "0" &&
                    (item.avatarUrl.isBlank() || (item.songCount == 0 && item.albumCount == 0))
                ) {
                    val detailed = runCatching { artistDetail(item.id) }.getOrNull()
                    val mergedItem = if (detailed != null) {
                        item.copy(
                            name = item.name.ifBlank { detailed.name },
                            avatarUrl = item.avatarUrl.ifBlank { detailed.avatarUrl },
                            songCount = maxOf(item.songCount, detailed.songCount),
                            albumCount = maxOf(item.albumCount, detailed.albumCount),
                            intro = item.intro.ifBlank { detailed.intro }
                        )
                    } else item
                    if (mergedItem.avatarUrl.isBlank()) {
                        val fallbackCover = runCatching {
                            artistSongs(item.id, page = 1, pageSize = 3).firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                                ?: artistAlbums(item.id, page = 1, pageSize = 3).firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                        }.getOrNull().orEmpty()
                        if (fallbackCover.isNotBlank()) mergedItem.copy(avatarUrl = fallbackCover) else mergedItem
                    } else mergedItem
                } else item
            }
        }.map { it.await() }
    }

    private fun parseArtistItems(root: JsonObject): List<ArtistItem> {
        val dataObj = root.obj("data") ?: root
        val array = listOf("lists", "info", "data", "author", "authors", "singer").asSequence()
            .mapNotNull { dataObj.arr(it) ?: root.arr(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val baseObj = o.obj("base")
            val extraObj = o.obj("extra")
            val id = (o.text("AuthorID") ?: o.text("AuthorId") ?: o.text("author_id")
                ?: o.text("singerid") ?: o.text("SingerId") ?: o.text("SingerID")
                ?: baseObj?.text("author_id") ?: o.text("id") ?: "").trim()
            if (id.isBlank() || id == "0") return@mapNotNull null
            val rawName = o.text("AuthorName") ?: o.text("author_name")
                ?: o.text("singername") ?: o.text("SingerName")
                ?: baseObj?.text("author_name") ?: o.text("name") ?: return@mapNotNull null
            val name = rawName.replace("<em>", "").replace("</em>", "").trim()
            if (name.isBlank()) return@mapNotNull null
            val avatarRaw = o.text("Avatar") ?: o.text("avatar")
                ?: o.text("SizableAvatar") ?: o.text("sizable_avatar")
                ?: o.text("AuthorPic") ?: o.text("author_pic")
                ?: o.text("ImgUrl") ?: o.text("imgurl") ?: o.text("img") ?: o.text("pic")
                ?: baseObj?.text("sizable_avatar") ?: baseObj?.text("avatar")
            val songCount = o.number("AudioCount") ?: o.number("audio_count")
                ?: o.number("SongCount") ?: o.number("songcount") ?: o.number("song_count")
                ?: o.number("ComplexCount") ?: extraObj?.number("audio_count") ?: baseObj?.number("audio_count") ?: 0
            val albumCount = o.number("AlbumCount") ?: o.number("album_count")
                ?: o.number("albumcount") ?: extraObj?.number("album_count") ?: baseObj?.number("album_count") ?: 0
            val intro = (o.text("intro") ?: extraObj?.text("intro") ?: baseObj?.text("intro") ?: "")
                .replace("<em>", "").replace("</em>", "").trim()
            ArtistItem(
                id = id,
                name = name,
                avatarUrl = imageUrl(avatarRaw),
                songCount = songCount,
                albumCount = albumCount,
                intro = intro
            )
        }
    }

    // 专辑曲目列表（网关签名 /v1/album_audio/lite + /v2/get_res_privilege/lite 音质补全 + CDN 备用双通道）
    suspend fun albumSongs(albumId: String): List<Song> {
        if (albumId.isBlank()) return emptyList()
        val session = accounts.context()?.session
        val allSongs = mutableListOf<Song>()
        val seenKeys = mutableSetOf<String>()
        var page = 1
        val pageSize = 50
        var expectedTotal = 0
        while (page <= 10) {
            val response = runCatching {
                transport.callAdvanced(
                    path = "/v1/album_audio/lite",
                    method = "POST",
                    session = session,
                    body = json(
                        "album_id" to albumId,
                        "is_buy" to "",
                        "page" to page,
                        "pagesize" to pageSize,
                        "area_code" to 1
                    ),
                    headers = mapOf(
                        "x-router" to "openapi.kugou.com",
                        "kg-tid" to "255"
                    ),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
            }.getOrNull() ?: break

            val dataObj = response.data.obj("data") ?: response.data
            if (expectedTotal <= 0) {
                expectedTotal = dataObj.number("total") ?: response.data.number("total") ?: 0
            }
            val batch = parseSongs(response.data).map {
                if (it.albumId.isBlank()) it.copy(albumId = albumId) else it
            }
            if (batch.isEmpty()) break
            var added = 0
            for (s in batch) {
                if (seenKeys.add(s.hash.ifBlank { s.id }.lowercase())) {
                    allSongs.add(s)
                    added++
                }
            }
            if (added == 0) break
            if (expectedTotal > 0 && allSongs.size >= expectedTotal) break
            if (expectedTotal <= 0 && batch.size < 15) break
            page++
        }

        // 同时尝试拉取 CDN 专辑歌曲接口 (/api/v3/album/song)，其中常包含 320hash / sqhash / filesize
        val cdnSongs = mutableListOf<Song>()
        val cdnSeen = mutableSetOf<String>()
        for (p in 1..10) {
            val batch = runCatching {
                val response = transport.callAdvanced(
                    path = "/api/v3/album/song",
                    host = "http://mobilecdnbj.kugou.com",
                    query = mapOf(
                        "version" to "9108",
                        "albumid" to albumId,
                        "plat" to "0",
                        "pagesize" to "50",
                        "page" to p.toString(),
                        "area_code" to "1"
                    ),
                    sign = false,
                    includeDefaultParams = false
                ).requireSuccess()
                parseSongs(response.data).map {
                    if (it.albumId.isBlank()) it.copy(albumId = albumId) else it
                }
            }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            var added = 0
            for (s in batch) {
                if (cdnSeen.add(s.hash.ifBlank { s.id }.lowercase())) {
                    cdnSongs.add(s)
                    added++
                }
            }
            if (added == 0 || batch.size < 15) break
        }

        val mergedBase = if (allSongs.isNotEmpty()) {
            val cdnByTitleOrId = cdnSongs.associateBy { it.audioId.takeIf { id -> id > 0 }?.toString() ?: it.title.lowercase() }
            allSongs.map { gwSong ->
                val cdnMatch = cdnByTitleOrId[gwSong.audioId.takeIf { it > 0 }?.toString()]
                    ?: cdnByTitleOrId[gwSong.title.lowercase()]
                if (cdnMatch != null && !gwSong.isLossless && !gwSong.isHiRes) {
                    val sqH = gwSong.hashFlac.ifBlank { cdnMatch.hashFlac }
                    val hqH = gwSong.hash320.ifBlank { cdnMatch.hash320 }
                    val resH = gwSong.hashHiRes.ifBlank { cdnMatch.hashHiRes }
                    val stdH = gwSong.hash128.ifBlank { cdnMatch.hash128.ifBlank { gwSong.hash } }
                    val hasHiRes = resH.isNotBlank()
                    val hasFlac = sqH.isNotBlank() || cdnMatch.isLossless
                    val has320 = hqH.isNotBlank() || cdnMatch.quality == "320"
                    val qualities = buildList {
                        add(AudioQuality.STANDARD)
                        if (has320 || hasFlac || hasHiRes) add(AudioQuality.HIGH)
                        if (hasFlac || hasHiRes) add(AudioQuality.LOSSLESS)
                        if (hasHiRes) add(AudioQuality.HI_RES)
                    }
                    gwSong.copy(
                        hash = when {
                            hasHiRes -> resH
                            hasFlac && sqH.isNotBlank() -> sqH
                            has320 && hqH.isNotBlank() -> hqH
                            else -> gwSong.hash
                        },
                        hash128 = stdH,
                        hash320 = hqH,
                        hashFlac = sqH,
                        hashHiRes = resH,
                        isHiRes = hasHiRes,
                        isLossless = hasFlac || hasHiRes,
                        quality = if (hasHiRes) "high" else if (hasFlac) "flac" else if (has320) "320" else gwSong.quality,
                        availableQualities = qualities
                    )
                } else gwSong
            }
        } else {
            cdnSongs
        }

        // 最后通过 /v2/get_res_privilege/lite 批量补全真实音质标签（解决已购专辑内歌曲无音质标签问题）
        return runCatching { enrichSongsPrivilegeLite(mergedBase) }.getOrDefault(mergedBase)
    }

    suspend fun albumDetail(albumId: String): AlbumItem? {
        if (albumId.isBlank()) return null
        val session = accounts.context()?.session

        val gatewayDetail = runCatching {
            val itemArr = JsonArray().apply {
                add(JsonObject().apply { addProperty("album_id", albumId) })
            }
            val body = JsonObject().apply {
                add("data", itemArr)
                addProperty("is_buy", 0)
                addProperty("fields", "album_id,album_name,publish_date,sizable_cover,intro,language,is_publish,heat,type,quality,authors,exclusive,author_name,publish_company,song_count")
            }
            val response = transport.callAdvanced(
                path = "/kmr/v2/albums",
                method = "POST",
                session = session,
                body = body,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()

            val array = response.data.arr("data")
            val obj = array?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                ?: response.data.obj("data") ?: response.data
            val id = obj.text("album_id") ?: albumId
            val name = (obj.text("album_name") ?: obj.text("albumname") ?: "").trim()
            val author = (obj.text("author_name") ?: obj.text("singername")
                ?: obj.objects("authors").firstOrNull()?.text("author_name") ?: "群星").trim()
            val cover = imageUrl(obj.text("sizable_cover") ?: obj.text("album_img") ?: obj.text("imgurl") ?: obj.text("pic"))
            val intro = obj.text("intro") ?: ""
            val pubDate = (obj.text("publish_date") ?: obj.text("publishtime") ?: "").take(10)
            val count = obj.number("song_count") ?: obj.number("songcount") ?: 0
            val company = obj.text("publish_company") ?: obj.text("company") ?: ""
            if (name.isBlank() && cover.isBlank()) {
                null
            } else {
                AlbumItem(
                    id = id,
                    name = name.ifBlank { "精选专辑" },
                    artist = author,
                    coverUrl = cover,
                    songCount = count,
                    publishDate = pubDate,
                    intro = intro,
                    company = company
                )
            }
        }.getOrNull()

        if (gatewayDetail != null && gatewayDetail.intro.length >= 30 && gatewayDetail.artist != "群星") return gatewayDetail

        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/album/info",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "albumid" to albumId,
                    "plat" to "0"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val obj = response.data.obj("data") ?: response.data
            val id = obj.text("albumid") ?: obj.text("album_id") ?: albumId
            val name = obj.text("albumname") ?: obj.text("album_name") ?: "精选专辑"
            val author = obj.text("singername") ?: obj.text("author_name") ?: "群星"
            val cover = imageUrl(obj.text("imgurl") ?: obj.text("sizable_cover") ?: obj.text("pic"))
            val intro = obj.text("intro") ?: ""
            val pubDate = (obj.text("publishtime") ?: obj.text("publish_date") ?: "").take(10)
            val count = obj.number("songcount") ?: obj.number("song_count") ?: 0
            val company = obj.text("company") ?: obj.text("publish_company") ?: ""
            val cdnItem = AlbumItem(
                id = id,
                name = name,
                artist = author,
                coverUrl = cover,
                songCount = count,
                publishDate = pubDate,
                intro = intro,
                company = company
            )
            if (gatewayDetail != null) {
                gatewayDetail.copy(
                    artist = if (gatewayDetail.artist.isBlank() || gatewayDetail.artist == "群星") cdnItem.artist else gatewayDetail.artist,
                    intro = if (cdnItem.intro.length > gatewayDetail.intro.length) cdnItem.intro else gatewayDetail.intro,
                    publishDate = gatewayDetail.publishDate.ifBlank { cdnItem.publishDate },
                    company = gatewayDetail.company.ifBlank { cdnItem.company },
                    songCount = maxOf(gatewayDetail.songCount, cdnItem.songCount)
                )
            } else cdnItem
        }.getOrNull() ?: gatewayDetail
    }

    suspend fun artistDetail(artistId: String): ArtistItem? {
        if (artistId.isBlank()) return null
        val session = accounts.context()?.session

        val gatewayArtist = runCatching {
            val response = transport.callAdvanced(
                path = "/kmr/v3/author",
                method = "POST",
                session = session,
                body = json(
                    "author_id" to (artistId.toLongOrNull() ?: artistId),
                    "fields" to "base,extra",
                    "area_code" to 1
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()

            val dataObj = response.data.obj("data") ?: response.data
            val baseObj = dataObj.obj("base") ?: dataObj
            val extraObj = dataObj.obj("extra") ?: dataObj

            val id = baseObj.text("author_id") ?: dataObj.text("author_id") ?: artistId
            val name = (baseObj.text("author_name") ?: dataObj.text("author_name") ?: baseObj.text("singername") ?: "").trim()
            val avatar = imageUrl(
                baseObj.text("sizable_avatar") ?: baseObj.text("avatar")
                    ?: dataObj.text("sizable_avatar") ?: dataObj.text("avatar")
                    ?: baseObj.text("pic") ?: dataObj.text("pic")
            )
            val intro = extraObj.text("long_intro") ?: baseObj.text("long_intro")
                ?: extraObj.text("intro") ?: baseObj.text("intro")
                ?: dataObj.text("intro") ?: ""
            val songCount = extraObj.number("audio_count") ?: baseObj.number("audio_count")
                ?: dataObj.number("audio_count") ?: dataObj.number("songcount") ?: 0
            val albumCount = extraObj.number("album_count") ?: baseObj.number("album_count")
                ?: dataObj.number("album_count") ?: dataObj.number("albumcount") ?: 0

            if (name.isBlank() && avatar.isBlank()) {
                null
            } else {
                ArtistItem(
                    id = id,
                    name = name.ifBlank { "歌手" },
                    avatarUrl = avatar,
                    songCount = songCount,
                    albumCount = albumCount,
                    intro = intro
                )
            }
        }.getOrNull()

        if (gatewayArtist != null && gatewayArtist.avatarUrl.isNotBlank() && gatewayArtist.songCount > 0 && gatewayArtist.intro.length >= 200) {
            return gatewayArtist
        }

        val cdnArtist = runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/singer/info",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "singerid" to artistId,
                    "plat" to "0"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val dataObj = response.data.obj("data") ?: response.data
            val id = dataObj.text("singerid") ?: dataObj.text("author_id") ?: artistId
            val name = dataObj.text("singername") ?: dataObj.text("author_name") ?: gatewayArtist?.name ?: "歌手"
            val avatar = imageUrl(dataObj.text("imgurl") ?: dataObj.text("avatar") ?: dataObj.text("pic"))
                .ifBlank { gatewayArtist?.avatarUrl.orEmpty() }
            val rawIntro = dataObj.text("intro") ?: ""
            val rawProfile = dataObj.text("profile") ?: ""
            val combinedCdnIntro = when {
                rawIntro.isNotBlank() && rawProfile.isNotBlank() && !rawIntro.contains(rawProfile.take(20)) -> "$rawIntro\n\n$rawProfile"
                rawIntro.isNotBlank() -> rawIntro
                else -> rawProfile
            }.trim()
            val gwIntro = gatewayArtist?.intro.orEmpty()
            val intro = if (combinedCdnIntro.length > gwIntro.length) combinedCdnIntro else gwIntro
            val songCount = maxOf(dataObj.number("songcount") ?: dataObj.number("audio_count") ?: 0, gatewayArtist?.songCount ?: 0)
            val albumCount = maxOf(dataObj.number("albumcount") ?: dataObj.number("album_count") ?: 0, gatewayArtist?.albumCount ?: 0)
            ArtistItem(
                id = id,
                name = name,
                avatarUrl = avatar,
                songCount = songCount,
                albumCount = albumCount,
                intro = intro
            )
        }.getOrNull()

        return cdnArtist ?: gatewayArtist
    }

    suspend fun artistSongs(artistId: String, sort: String = "hot", page: Int = 1, pageSize: Int = 100): List<Song> = coroutineScope {
        if (artistId.isBlank()) return@coroutineScope emptyList()
        val session = accounts.context()?.session

        // 对齐 MakcRe/KuGouMusicApi module/artist_audios_new.js (/openapi/kmr/v2/audio_group/author):
        // 携带 replace_api_version=1, replace_need=1, show_audio_honor=1, show_audio_tag=1 获取概念版官方"歌手-单曲"列表（含替换后的 Hi-Res 资源）
        val v2Def = async {
            val queryParams = mapOf(
                "author_id" to artistId,
                "area_code" to "all",
                "Filter" to "filter",
                "page" to page.toString(),
                "pagesize" to pageSize.toString(),
                "replace_api_version" to "1",
                "sort" to (if (sort == "hot") "1" else "2"),
                "mvdata_need" to "1",
                "show_audio_honor" to "1",
                "show_audio_tag" to "1",
                "replace_need" to "1"
            )
            runCatching {
                val resp = transport.callAdvanced(
                    path = "/openapi/kmr/v2/audio_group/author",
                    method = "GET",
                    session = session,
                    query = queryParams,
                    headers = mapOf("kg-tid" to "220"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseSongs(resp.data).ifEmpty { parseSongs(resp.body) }
            }.getOrNull().takeIf { !it.isNullOrEmpty() } ?: runCatching {
                val resp = transport.callAdvanced(
                    path = "/openapi/kmr/v2/audio_group/author",
                    method = "GET",
                    session = session,
                    query = queryParams,
                    headers = mapOf("x-router" to "openapi.kugou.com", "kg-tid" to "220"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseSongs(resp.data).ifEmpty { parseSongs(resp.body) }
            }.getOrNull()
        }

        val v1Def = async {
            runCatching {
                val clienttime = System.currentTimeMillis() / 1000
                val response = transport.callAdvanced(
                    path = "/kmr/v1/audio_group/author",
                    method = "POST",
                    session = session,
                    body = json(
                        "appid" to KugouSigner.APP_ID,
                        "clientver" to KugouSigner.CLIENT_VERSION,
                        "clienttime" to clienttime,
                        "key" to KugouSigner.paramsKey(clienttime),
                        "author_id" to artistId,
                        "pagesize" to pageSize,
                        "page" to page,
                        "sort" to if (sort == "hot") 1 else 2,
                        "area_code" to "all",
                        "replace_api_version" to 1,
                        "replace_need" to 1,
                        "show_audio_honor" to 1,
                        "show_audio_tag" to 1
                    ),
                    headers = mapOf(
                        "x-router" to "openapi.kugou.com",
                        "kg-tid" to "220"
                    ),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseSongs(response.data).ifEmpty { parseSongs(response.body) }
            }.getOrNull()
        }

        val v2Songs = v2Def.await()
        val v1Songs = v1Def.await()
        val baseList = when {
            !v2Songs.isNullOrEmpty() && !v1Songs.isNullOrEmpty() -> {
                val v1ByAudioId = v1Songs.filter { it.audioId > 0L }.groupBy { it.audioId }
                val v1ByTitle = v1Songs.groupBy { it.title.trim().lowercase() }
                v2Songs.map { s2 ->
                    val candidates = (if (s2.audioId > 0L) v1ByAudioId[s2.audioId].orEmpty() else emptyList()) +
                        v1ByTitle[s2.title.trim().lowercase()].orEmpty()
                    val s1 = candidates.firstOrNull { it.hashHiRes.isNotBlank() || it.isHiRes }
                        ?: candidates.firstOrNull { it.hashFlac.isNotBlank() || it.isLossless }
                        ?: candidates.firstOrNull()
                    if (s1 != null) {
                        val mergedRes = s2.hashHiRes.ifBlank { s1.hashHiRes }
                        val mergedFlac = s2.hashFlac.ifBlank { s1.hashFlac }
                        val merged320 = s2.hash320.ifBlank { s1.hash320 }
                        val merged128 = s2.hash128.ifBlank { s1.hash128 }
                        val hasRes = s2.isHiRes || s1.isHiRes || mergedRes.isNotBlank()
                        val hasSq = s2.isLossless || s1.isLossless || mergedFlac.isNotBlank() || hasRes
                        val hasHq = merged320.isNotBlank() || hasSq
                        val mergedQualities = buildList {
                            add(AudioQuality.STANDARD)
                            if (hasHq) add(AudioQuality.HIGH)
                            if (hasSq) add(AudioQuality.LOSSLESS)
                            if (hasRes) add(AudioQuality.HI_RES)
                        }
                        s2.copy(
                            hash = if (mergedRes.isNotBlank()) mergedRes else s2.hash,
                            hash128 = merged128,
                            hash320 = merged320,
                            hashFlac = mergedFlac,
                            hashHiRes = mergedRes,
                            isVip = s2.isVip || s1.isVip,
                            isHiRes = hasRes,
                            isLossless = hasSq,
                            quality = if (hasRes) "high" else if (hasSq) "flac" else if (hasHq) "320" else s2.quality,
                            availableQualities = mergedQualities
                        )
                    } else s2
                }
            }
            !v2Songs.isNullOrEmpty() -> v2Songs
            !v1Songs.isNullOrEmpty() -> v1Songs
            else -> emptyList()
        }
        if (baseList.isNotEmpty()) {
            return@coroutineScope runCatching { enrichSongsPrivilegeLite(baseList) }.getOrDefault(baseList)
        }

        return@coroutineScope runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/singer/song",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "singerid" to artistId,
                    "plat" to "0",
                    "pagesize" to pageSize.toString(),
                    "page" to page.toString(),
                    "sorttype" to (if (sort == "hot") "2" else "1"),
                    "area_code" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val raw = parseSongs(response.data)
            runCatching { enrichSongsPrivilegeLite(raw) }.getOrDefault(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun artistAlbums(artistId: String, page: Int = 1, pageSize: Int = 50, sort: String = "new"): List<AlbumItem> {
        if (artistId.isBlank()) return emptyList()
        val session = accounts.context()?.session

        val gatewayAlbums = runCatching {
            val response = transport.callAdvanced(
                path = "/kmr/v1/author/albums",
                method = "POST",
                session = session,
                body = json(
                    "author_id" to artistId,
                    "pagesize" to pageSize,
                    "page" to page,
                    "sort" to if (sort == "hot") 3 else 1,
                    "category" to 1,
                    "area_code" to "all"
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseAlbumItems(response.data)
        }.getOrNull()
        if (!gatewayAlbums.isNullOrEmpty()) return gatewayAlbums

        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/singer/album",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "singerid" to artistId,
                    "plat" to "0",
                    "pagesize" to pageSize.toString(),
                    "page" to page.toString()
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            parseAlbumItems(response.data)
        }.getOrDefault(emptyList())
    }

    // =========================================================================
    // 排行榜体系（严格对齐 MakcRe/KuGouMusicApi: rank_list.js, rank_vol.js, rank_info.js, rank_audio.js）
    // - 修复此前缺少 showtype=2 及硬编码 rank_cid=0 导致榜单停留在旧快照、未能每日实时刷新的问题
    // - 自动解析每个榜单当日最新期数 volid / rank_cid，并实时刷新发现页榜单卡片的前三名预览曲目
    // =========================================================================

    private suspend fun resolveLatestRankVolId(rankId: Int): Int {
        val todayKey = "${rankId}_${today()}"
        rankLatestVolMap[todayKey]?.let { if (it > 0) return it }
        val minuteStamp = (System.currentTimeMillis() / 60_000L).toString()
        val fetchedVol = runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/rank/vol",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "plat" to "0",
                    "ranktype" to "2",
                    "rankid" to rankId.toString(),
                    "with_res_tag" to "0",
                    "_t" to minuteStamp
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val infoArr = response.data.obj("data")?.arr("info")
                ?: response.data.arr("info")
                ?: response.data.arr("vol_list")
            val latestObj = infoArr?.firstOrNull { it.isJsonObject }?.asJsonObject
            latestObj?.number("volid") ?: latestObj?.number("rank_cid") ?: 0
        }.getOrDefault(0)
        if (fetchedVol > 0) {
            rankLatestVolMap[todayKey] = fetchedVol
        }
        return fetchedVol
    }

    suspend fun rankList(): List<RankItem> = coroutineScope {
        val minuteStamp = (System.currentTimeMillis() / 60_000L).toString()
        val todayKeySuffix = today()
        val rawRanks = runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/rank/list",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "plat" to "0",
                    "showtype" to "2",
                    "withsong" to "1",
                    "parentid" to "0",
                    "apiver" to "6",
                    "area_code" to "1",
                    "_t" to minuteStamp
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val topArray = response.data.obj("data")?.arr("info")
                ?: response.data.arr("info") ?: return@runCatching emptyList<RankItem>()

            // showtype=2 下既可能是平铺榜单，也可能包含 children 子榜单列表
            val flatObjects = mutableListOf<JsonObject>()
            for (el in topArray) {
                if (el.isJsonObject) {
                    val obj = el.asJsonObject
                    val children = obj.arr("children")
                    if (children != null && children.size() > 0) {
                        children.filter { it.isJsonObject }.forEach { flatObjects.add(it.asJsonObject) }
                    }
                    if (obj.number("rankid") != null) {
                        flatObjects.add(obj)
                    }
                }
            }

            val seenRankIds = HashSet<Int>()
            flatObjects.mapNotNull { o ->
                val id = o.number("rankid") ?: return@mapNotNull null
                if (id <= 0 || !seenRankIds.add(id)) return@mapNotNull null
                val name = (o.text("rankname") ?: "").trim()
                if (name.isBlank()) return@mapNotNull null

                val volId = o.number("volid") ?: o.number("rank_cid") ?: 0
                if (volId > 0) {
                    rankLatestVolMap["${id}_$todayKeySuffix"] = volId
                }

                val coverRaw = o.text("imgurl") ?: o.text("pic") ?: o.text("banner7url") ?: o.text("album_img")
                val rawUpdate = (o.text("update_frequency") ?: "").trim()
                val isDailyRank = name.contains("飙升") || name.contains("TOP") || name.contains("新歌") ||
                    name.contains("热歌") || name.contains("红歌") || name.contains("分享") ||
                    name.contains("雷达") || name.contains("每日") || name.contains("DJ") ||
                    name.contains("抖音") || name.contains("快手") || rawUpdate.contains("天") || rawUpdate.contains("日")
                val update = if (isDailyRank) "每日实时更新" else rawUpdate.ifBlank { "每日更新" }

                val classify = o.number("classify") ?: 0
                val category = when {
                    name.contains("飙升") || name.contains("TOP") || name.contains("热歌") || name.contains("新歌") || name.contains("分享") || name.contains("红歌") || classify == 1 -> "官方热榜"
                    name.contains("ビルボード") || name.contains("Billboard") || name.contains("UK") || name.contains("韩国") || name.contains("日本") || name.contains("欧美") || classify == 4 -> "全球权威"
                    else -> "特色曲风"
                }
                val totalCount = o.number("songcount") ?: o.number("count") ?: if (name.contains("500")) 500 else 100
                val songsArr = o.arr("songinfo") ?: o.arr("songs")
                val topSongs = songsArr?.filter { it.isJsonObject }?.take(3)?.mapNotNull {
                    val s = it.asJsonObject
                    val sName = s.text("songname") ?: s.text("name") ?: s.text("audio_name") ?: ""
                    val singer = s.text("singername") ?: s.text("author_name") ?: ""
                    if (sName.isNotBlank()) {
                        if (singer.isNotBlank() && !sName.contains(singer)) "$sName - $singer" else sName
                    } else null
                } ?: emptyList()

                RankItem(
                    rankId = id,
                    rankName = name,
                    coverUrl = imageUrl(coverRaw),
                    updateFrequency = update,
                    topSongsPreview = topSongs,
                    rankType = classify,
                    category = category,
                    totalSongs = totalCount
                )
            }
        }.getOrDefault(emptyList())

        val priorityRankOrder = listOf(6666, 8888, 31308, 23784, 52144, 59703, 24971, 33162, 4681, 4673, 38623)
        val sortedRawRanks = rawRanks.sortedWith(
            compareBy<RankItem> { item ->
                val pIdx = priorityRankOrder.indexOf(item.rankId)
                when {
                    pIdx >= 0 -> pIdx
                    item.category == "官方热榜" -> 100
                    item.category == "特色曲风" -> 200
                    else -> 300
                }
            }
        )
        val baseList = if (sortedRawRanks.isNotEmpty()) sortedRawRanks else defaultRanks()

        // 仅对前 4 个核心展示榜单在缺失预览曲目时按需补全，避免首屏并发数十个请求拖慢加载速度
        baseList.mapIndexed { idx, item ->
            async {
                if (idx < 4 && item.topSongsPreview.isEmpty()) {
                    val liveTop = runCatching { rankSongs(item.rankId, page = 1, pageSize = 5, enrichPrivilege = false) }
                        .getOrDefault(emptyList())
                    if (liveTop.isNotEmpty()) {
                        val previewLines = liveTop.take(3).mapIndexed { i, s ->
                            "${i + 1}. ${s.title} - ${s.artist}"
                        }
                        val liveCover = liveTop.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty()
                        item.copy(
                            topSongsPreview = previewLines,
                            coverUrl = if (liveCover.isNotBlank() && (item.coverUrl.isBlank() || item.coverUrl.contains("20210729"))) liveCover else item.coverUrl
                        )
                    } else item
                } else item
            }
        }.map { it.await() }
    }

    /**
     * 排行榜单页曲目（实时三通道）：
     * 1. 优先解析该榜单今日最新期数 volid / rank_cid，调用实时官方榜单接口 /api/v3/rank/song
     * 2. 网关通道 /openapi/kmr/v2/rank/audio 传入最新 rank_cid（不再硬编码为 0，避免返回历史旧快照）
     * 3. 移动 Web 实时榜单通道 http://m.kugou.com/rank/info/
     */
    suspend fun rankSongs(
        rankId: Int,
        page: Int = 1,
        pageSize: Int = 100,
        enrichPrivilege: Boolean = true
    ): List<Song> {
        val session = accounts.context()?.session
        val minuteStamp = (System.currentTimeMillis() / 60_000L).toString()
        val latestVolId = resolveLatestRankVolId(rankId)

        // 通道 1：官方实时排行榜曲目接口 /api/v3/rank/song（附带最新期数 volid 与实时防缓存时间戳）
        val cdnList = runCatching {
            val queryMap = linkedMapOf(
                "version" to "9108",
                "ranktype" to "2",
                "rankid" to rankId.toString(),
                "page" to page.toString(),
                "pagesize" to pageSize.toString(),
                "plat" to "0",
                "area_code" to "1",
                "_t" to minuteStamp
            )
            if (latestVolId > 0) {
                queryMap["volid"] = latestVolId.toString()
            }
            val response = transport.callAdvanced(
                path = "/api/v3/rank/song",
                host = "http://mobilecdnbj.kugou.com",
                query = queryMap,
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrNull()

        // 若带 volid 请求未返回，尝试不带 volid 直连最新默认期
        val effectiveCdnList = if (!cdnList.isNullOrEmpty()) {
            cdnList
        } else if (latestVolId > 0) {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/api/v3/rank/song",
                    host = "http://mobilecdnbj.kugou.com",
                    query = mapOf(
                        "version" to "9108",
                        "rankid" to rankId.toString(),
                        "page" to page.toString(),
                        "pagesize" to pageSize.toString(),
                        "plat" to "0",
                        "area_code" to "1",
                        "_t" to minuteStamp
                    ),
                    sign = false,
                    includeDefaultParams = false
                ).requireSuccess()
                parseSongs(response.data)
            }.getOrNull()
        } else null

        if (!effectiveCdnList.isNullOrEmpty()) {
            return if (enrichPrivilege) {
                runCatching { enrichSongsPrivilegeLite(effectiveCdnList) }.getOrDefault(effectiveCdnList)
            } else effectiveCdnList
        }

        // 通道 2：KMR 网关接口 /openapi/kmr/v2/rank/audio (传入解析到的今日最新 rank_cid)
        val gatewayList = runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/kmr/v2/rank/audio",
                method = "POST",
                session = session,
                body = json(
                    "show_portrait_mv" to 1,
                    "show_type_total" to 1,
                    "filter_original_remarks" to 1,
                    "area_code" to 1,
                    "pagesize" to pageSize,
                    "rank_cid" to latestVolId,
                    "type" to 1,
                    "page" to page,
                    "rank_id" to rankId
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "369"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            val parsed = parseSongs(response.data)
            if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
        }.getOrNull()

        if (!gatewayList.isNullOrEmpty()) {
            return if (enrichPrivilege) {
                runCatching { enrichSongsPrivilegeLite(gatewayList) }.getOrDefault(gatewayList)
            } else gatewayList
        }

        // 通道 3：M 站实时榜单接口
        val mList = runCatching {
            val response = transport.callAdvanced(
                path = "/rank/info/",
                host = "http://m.kugou.com",
                query = mapOf(
                    "rankid" to rankId.toString(),
                    "page" to page.toString(),
                    "json" to "true",
                    "_t" to minuteStamp
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val songsObj = response.data.obj("songs") ?: response.data
            parseSongs(songsObj)
        }.getOrDefault(emptyList())

        return if (enrichPrivilege && mList.isNotEmpty()) {
            runCatching { enrichSongsPrivilegeLite(mList) }.getOrDefault(mList)
        } else mList
    }

    /**
     * 排行榜全量曲目多页聚合拉取
     */
    suspend fun rankSongsAll(rankId: Int, maxSongs: Int = 500, pageSize: Int = 100): List<Song> {
        val maxPages = ((maxSongs + pageSize - 1) / pageSize).coerceIn(1, 6)
        val allSongs = mutableListOf<Song>()
        val seenKeys = HashSet<String>()
        for (page in 1..maxPages) {
            val batch = runCatching { rankSongs(rankId, page = page, pageSize = pageSize) }.getOrDefault(emptyList())
            if (batch.isEmpty()) break
            var added = 0
            for (song in batch) {
                val key = song.hash.ifBlank { song.id }.lowercase()
                if (seenKeys.add(key)) {
                    allSongs.add(song)
                    added++
                }
            }
            if (allSongs.size >= maxSongs || added == 0 || batch.size < 25) break
        }
        return allSongs.take(maxSongs)
    }

    // =========================================================================
    // 新歌速递 (New Song Express) —— 严格审核近一周（当天往前推6天，共7天）内发行的新歌：
    // 1. 数据源：对接 MakcRe/KuGouMusicApi module/top_song.js (/musicadservice/container/v1/newsong_publish)
    //    结合 /kmr/v2/albums (module/album.js) 批量补全每首歌的精确发行日期 publish_date (yyyy-MM-dd)
    // 2. 严格时间审核：仅保留 publishDate 处于 [today.minusDays(6), today] 近7天窗口内的歌曲
    // 3. 动态数量：不设固定上限或补齐数量，每天随实际近7日发歌量自然变化
    // 4. 严格排序：按发行时间降序排列（最新日期在前）；同一发行日期的歌曲按歌名首字母升序排列 (A -> Z)
    // =========================================================================

    private suspend fun enrichSongsPublishDateByAlbums(songs: List<Song>): List<Song> {
        val missingAlbumIds = songs
            .filter { it.publishDate.isBlank() && it.albumId.isNotBlank() && it.albumId != "0" }
            .map { it.albumId.trim() }
            .distinct()
            .filterNot { albumPublishDateCache.containsKey(it) }

        if (missingAlbumIds.isNotEmpty()) {
            val session = accounts.context()?.session
            coroutineScope {
                missingAlbumIds.chunked(45).map { chunk ->
                    async {
                        runCatching {
                            val itemArr = JsonArray().apply {
                                chunk.forEach { aid ->
                                    add(JsonObject().apply {
                                        val numId = aid.toLongOrNull()
                                        if (numId != null) addProperty("album_id", numId)
                                        else addProperty("album_id", aid)
                                    })
                                }
                            }
                            val body = JsonObject().apply {
                                add("data", itemArr)
                                addProperty("is_buy", 0)
                                addProperty("fields", "album_id,album_name,publish_date,sizable_cover,author_name")
                            }
                            val resp = transport.callAdvanced(
                                path = "/kmr/v2/albums",
                                method = "POST",
                                session = session,
                                body = body,
                                headers = mapOf(
                                    "x-router" to "openapi.kugou.com",
                                    "kg-tid" to "255"
                                ),
                                sign = true,
                                includeDefaultParams = true
                            )
                            val arr = resp.data.arr("data") ?: resp.body.arr("data")
                            arr?.filter { it.isJsonObject }?.map { it.asJsonObject }?.forEach { obj ->
                                val aid = (obj.text("album_id") ?: obj.text("albumid") ?: "").trim()
                                val pub = normalizePublishDate(
                                    obj.text("publish_date") ?: obj.text("publishtime") ?: obj.text("publish_time")
                                )
                                if (aid.isNotBlank() && pub.isNotBlank()) {
                                    albumPublishDateCache[aid] = pub
                                }
                            }
                        }
                    }
                }.forEach { it.await() }
            }
        }

        val afterAlbumEnrich = songs.map { s ->
            if (s.publishDate.isNotBlank()) s
            else {
                val cachedPub = albumPublishDateCache[s.albumId.trim()].orEmpty()
                if (cachedPub.isNotBlank()) s.copy(publishDate = cachedPub) else s
            }
        }

        // 二级兜底：对仍缺少 publishDate 但有 albumAudioId 的单曲，通过 /kmr/v2/audio 批量查询 base,album_info 的 publish_date
        val stillMissingAudioIds = afterAlbumEnrich
            .filter { it.publishDate.isBlank() && it.albumAudioId > 0L }
            .map { it.albumAudioId }
            .distinct()
            .filterNot { albumPublishDateCache.containsKey("aaid_$it") }

        if (stillMissingAudioIds.isNotEmpty()) {
            val session = accounts.context()?.session
            for (chunk in stillMissingAudioIds.chunked(40)) {
                runCatching {
                    val dataArr = JsonArray().apply {
                        chunk.forEach { aaid ->
                            add(JsonObject().apply { addProperty("entity_id", aaid) })
                        }
                    }
                    val body = JsonObject().apply {
                        add("data", dataArr)
                        addProperty("fields", "base,album_info")
                    }
                    val resp = transport.callAdvanced(
                        path = "/kmr/v2/audio",
                        method = "POST",
                        session = session,
                        body = body,
                        headers = mapOf(
                            "x-router" to "openapi.kugou.com",
                            "kg-tid" to "36"
                        ),
                        sign = true,
                        includeDefaultParams = true
                    )
                    val arr = resp.data.arr("data") ?: resp.body.arr("data")
                    arr?.filter { it.isJsonObject }?.map { it.asJsonObject }?.forEach { item ->
                        val baseObj = item.obj("base") ?: item
                        val albObj = item.obj("album_info")
                        val aaid = baseObj.number("album_audio_id")?.toLong()
                            ?: item.number("album_audio_id")?.toLong()
                            ?: item.number("entity_id")?.toLong() ?: 0L
                        val pub = normalizePublishDate(
                            baseObj.text("publish_date") ?: baseObj.text("publish_time")
                                ?: albObj?.text("publish_date") ?: item.text("publish_date")
                        )
                        if (aaid > 0L && pub.isNotBlank()) {
                            albumPublishDateCache["aaid_$aaid"] = pub
                        }
                    }
                }
            }
        }

        return afterAlbumEnrich.map { s ->
            if (s.publishDate.isNotBlank()) s
            else {
                val cachedPub = albumPublishDateCache["aaid_${s.albumAudioId}"].orEmpty()
                if (cachedPub.isNotBlank()) s.copy(publishDate = cachedPub) else s
            }
        }
    }

    suspend fun newSongExpress(
        periodKey: String = "CHINESE",
        size: Int = 200,
        page: Int = 1,
        referenceDate: java.time.LocalDate = java.time.LocalDate.now(CHINA_ZONE)
    ): List<Song> = coroutineScope {
        val session = accounts.context()?.session
        val uidNum = session?.userId?.toLongOrNull() ?: 0L
        val windowStartDate = referenceDate.minusDays(6) // 当天往前推 6 天，共 7 天窗口

        suspend fun fetchOfficialTopSongPublish(rankId: Int, targetPage: Int, pageSize: Int = 40): List<Song> {
            return runCatching {
                val body = JsonObject().apply {
                    addProperty("rank_id", rankId)
                    addProperty("userid", uidNum)
                    addProperty("page", targetPage)
                    addProperty("pagesize", pageSize)
                    add("tags", JsonArray())
                }
                val response = transport.callAdvanced(
                    path = "/musicadservice/container/v1/newsong_publish",
                    method = "POST",
                    session = session,
                    body = body,
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                val parsed = parseSongs(response.data)
                if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
            }.getOrDefault(emptyList())
        }

        suspend fun fetchCandidatePoolForRank(rankId: Int, fallbackChartRankId: Int, pageCount: Int = 4): List<Song> = coroutineScope {
            val publishDefs = (1..pageCount).map { p ->
                async { fetchOfficialTopSongPublish(rankId, targetPage = p, pageSize = 40) }
            }
            val chartDef = async {
                runCatching { rankSongs(fallbackChartRankId, page = 1, pageSize = 80, enrichPrivilege = false) }
                    .getOrDefault(emptyList())
            }
            publishDefs.flatMap { it.await() } + chartDef.await()
        }

        val rawCandidates: List<Song> = when (periodKey.uppercase()) {
            "CHINESE", "21608" -> {
                fetchCandidatePoolForRank(rankId = 21608, fallbackChartRankId = 31308, pageCount = 2)
            }
            "WESTERN", "21609" -> {
                fetchCandidatePoolForRank(rankId = 21609, fallbackChartRankId = 31310, pageCount = 2)
            }
            "JPKR", "21610" -> {
                fetchCandidatePoolForRank(rankId = 21610, fallbackChartRankId = 31311, pageCount = 2)
            }
            "CANTON", "21611" -> {
                fetchCandidatePoolForRank(rankId = 21611, fallbackChartRankId = 31313, pageCount = 2)
            }
            else -> {
                // 综合首发：并发汇总华语(21608)、欧美(21609)、日韩(21610)、粤语(21611) 近7日候选新歌
                val cnDef = async { fetchCandidatePoolForRank(rankId = 21608, fallbackChartRankId = 31308, pageCount = 2) }
                val westDef = async { fetchCandidatePoolForRank(rankId = 21609, fallbackChartRankId = 31310, pageCount = 1) }
                val jpkrDef = async { fetchCandidatePoolForRank(rankId = 21610, fallbackChartRankId = 31311, pageCount = 1) }
                val cantonDef = async { fetchCandidatePoolForRank(rankId = 21611, fallbackChartRankId = 31313, pageCount = 1) }
                cnDef.await() + westDef.await() + jpkrDef.await() + cantonDef.await()
            }
        }

        val seen = HashSet<String>()
        val deduped = rawCandidates.filter { s ->
            val key = s.hash.ifBlank { s.id }.lowercase()
            key.isNotBlank() && seen.add(key)
        }

        // 先通过 privilege/lite 与 /kmr/v2/albums 批量补全每首歌的真实发行时间 publishDate 与音质标签
        val privEnriched = runCatching { enrichSongsPrivilegeLite(deduped) }.getOrDefault(deduped)
        val dateEnriched = runCatching { enrichSongsPublishDateByAlbums(privEnriched) }.getOrDefault(privEnriched)

        // 严格审核发行日期：仅保留发行日期处于 [referenceDate.minusDays(6), referenceDate] 共 7 天内的新歌
        val auditedSevenDaySongs = dateEnriched.filter { song ->
            if (song.publishDate.isBlank()) return@filter false
            val pubLocal = runCatching { java.time.LocalDate.parse(song.publishDate) }.getOrNull() ?: return@filter false
            !pubLocal.isBefore(windowStartDate) && !pubLocal.isAfter(referenceDate)
        }

        // 若当前分类下部分容器歌曲只返回了当周新上架但未携带独立 album_id 导致 auditedSevenDaySongs 偏少，
        // 对缺少 publishDate 的容器前排首发新歌通过单曲详情或新碟检索进一步核验发行时间
        val finalVerifiedSongs = if (auditedSevenDaySongs.isNotEmpty()) {
            auditedSevenDaySongs
        } else {
            // 若网关在离线或特定环境下未返回 publish_date 字段，尝试从最新发行专辑接口 (/v1/search/album) 检索近7日新歌
            val yearStr = referenceDate.year.toString()
            val recentAlbums = runCatching { searchAlbums(yearStr, page = 1, pageSize = 30) }.getOrDefault(emptyList())
                .filter { alb ->
                    val norm = normalizePublishDate(alb.publishDate)
                    val d = runCatching { java.time.LocalDate.parse(norm) }.getOrNull()
                    d != null && !d.isBefore(windowStartDate) && !d.isAfter(referenceDate)
                }
            val fromRecentAlbums = coroutineScope {
                recentAlbums.take(8).map { alb ->
                    async {
                        val normDate = normalizePublishDate(alb.publishDate)
                        runCatching { albumSongs(alb.id) }.getOrDefault(emptyList()).map { s ->
                            s.copy(
                                publishDate = s.publishDate.ifBlank { normDate },
                                coverUrl = s.coverUrl.ifBlank { alb.coverUrl }
                            )
                        }
                    }
                }.flatMap { it.await() }
            }
            fromRecentAlbums.filter { s ->
                val d = runCatching { java.time.LocalDate.parse(s.publishDate) }.getOrNull()
                d != null && !d.isBefore(windowStartDate) && !d.isAfter(referenceDate)
            }
        }

        // 严格排序：1. 发行时间降序（最新日期在前） 2. 相同发行时间按歌名首字母升序 (A -> Z)
        finalVerifiedSongs
            .distinctBy { it.hash.ifBlank { it.id }.lowercase() }
            .sortedWith(newSongExpressComparator)
    }

    // =========================================================================
    // 私人随心听 FM（定位「猜你喜欢」：严格对接 MakcRe/KuGouMusicApi 5 大个性化推荐引擎 + 用户真实听歌口味锚点）
    // 1. module/ai_recommend.js (/recommend, x-router: songlistairec.kugou.com, 传入用户真实红心/历史曲目 album_audio_id)
    // 2. module/ai_recommend_song.js (/concepts/v1/ai/recommend_song, 概念版原生 AI 个性化推荐歌曲)
    // 3. module/top_card.js (/singlecardrec.service/v1/single_card_recommend, card_id=1/2/3 猜你喜欢单卡推荐)
    // 4. module/personal_fm.js (/v2/personal_recommend, x-router: persnfm.service.kugou.com, platform=ios 私人FM推荐流)
    // 5. module/everyday_style_recommend.js (/everydayrec.service/everyday_style_recommend, 个人风格推荐)
    // =========================================================================

    private suspend fun collectUserTasteSeeds(): List<Song> = coroutineScope {
        // 严格界定刷新边界：若已有口味种子缓存，直接复用，绝不在刷新随心听时重拉每日推荐或用户歌单列表
        if (userTasteSeedsCache.isNotEmpty()) {
            return@coroutineScope userTasteSeedsCache.toList()
        }
        val session = accounts.context()?.session
        val recent = if (session != null) {
            runCatching { latestListenSongs(pageSize = 30) }.getOrDefault(emptyList())
        } else emptyList()
        val combined = recent
            .filter { it.hash.isNotBlank() && it.title != "未知歌曲" }
            .distinctBy { it.hash.ifBlank { it.id }.lowercase() }
        recordUserTasteSeeds(combined)
        userTasteSeedsCache.toList()
    }

    suspend fun personalFm(size: Int = 20, mode: String = "normal"): List<Song> = coroutineScope {
        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val uidNum = session?.userId?.toLongOrNull() ?: 0L
        val batchIdx = personalFmPoolCursor.getAndIncrement()

        if (seenPersonalFmHashes.size > 600) {
            seenPersonalFmHashes.clear()
        }

        // 获取用户真实的听歌口味种子（来自最近播放、我喜欢的音乐、每日推荐）
        val allUserSeeds = runCatching { collectUserTasteSeeds() }.getOrDefault(emptyList())
        val rotatedSeeds = if (allUserSeeds.isNotEmpty()) {
            val offset = (batchIdx * 7) % allUserSeeds.size
            (allUserSeeds.drop(offset) + allUserSeeds.take(offset)).take(12)
        } else emptyList()

        // 1. 引擎一：module/personal_fm.js (POST /v2/personal_recommend, x-router: persnfm.service.kugou.com)
        // 单次请求返回约 5 首歌，并发 2 个不同口味锚点的请求获取 10+ 首私人 FM 曲目
        val pfmDeferreds = (0..1).map { subIdx ->
            async {
                runCatching {
                    val anchorSong = rotatedSeeds.getOrNull(subIdx * 3) ?: lastPersonalFmSong
                    val poolId = (batchIdx + subIdx) % 3
                    val body = JsonObject().apply {
                        addProperty("appid", KugouSigner.APP_ID)
                        addProperty("clienttime", clientTime)
                        addProperty("mid", d.mid)
                        addProperty("action", if (anchorSong != null) "play" else "play")
                        addProperty("recommend_source_locked", 0)
                        addProperty("song_pool_id", poolId)
                        addProperty("callerid", 0)
                        addProperty("m_type", 1)
                        addProperty("platform", "ios")
                        addProperty("area_code", 1)
                        addProperty("remain_songcnt", 0)
                        addProperty("clientver", KugouSigner.CLIENT_VERSION)
                        addProperty("is_overplay", 1)
                        addProperty("mode", mode)
                        addProperty("fakem", "ca981cfc583a4c37f28d2d49000013c16a0a")
                        addProperty("key", KugouSigner.paramsKey(clientTime))
                        if (anchorSong != null) {
                            if (anchorSong.hash.isNotBlank()) addProperty("hash", anchorSong.hash.lowercase())
                            val sid = anchorSong.audioId.takeIf { it > 0L } ?: anchorSong.albumAudioId
                            if (sid > 0L) addProperty("songid", sid)
                            addProperty("playtime", 180)
                        }
                        if (session != null) {
                            addProperty("userid", uidNum)
                            addProperty("kguid", uidNum)
                            addProperty("token", session.token)
                            addProperty("vip_type", 6)
                        }
                    }
                    val response = transport.callAdvanced(
                        path = "/v2/personal_recommend",
                        session = session,
                        body = body,
                        method = "POST",
                        headers = mapOf("x-router" to "persnfm.service.kugou.com"),
                        sign = true,
                        includeDefaultParams = true
                    ).requireSuccess()
                    val parsed = parseSongs(response.data)
                    if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
                }.getOrDefault(emptyList())
            }
        }

        // 2. 引擎二：module/ai_recommend.js (POST /recommend, x-router: songlistairec.kugou.com)
        // 基于用户自己「我喜欢 / 最近播放」的真实 album_audio_id 列表驱动酷狗 AI 协同过滤推荐！
        val aiSeedRecDef = async {
            runCatching {
                val seedIds = rotatedSeeds.mapNotNull { s ->
                    s.albumAudioId.takeIf { it > 0L } ?: s.audioId.takeIf { it > 0L }
                }.distinct().take(10)
                if (seedIds.isEmpty()) return@runCatching emptyList<Song>()
                val recSourceArr = JsonArray().apply {
                    seedIds.forEach { idNum ->
                        add(JsonObject().apply { addProperty("ID", idNum) })
                    }
                }
                val body = JsonObject().apply {
                    addProperty("platform", "ios")
                    addProperty("clientver", KugouSigner.CLIENT_VERSION)
                    addProperty("clienttime", clientTime)
                    addProperty("userid", uidNum)
                    add("client_playlist", JsonArray())
                    addProperty("source_type", 2)
                    addProperty("playlist_ver", 2)
                    addProperty("area_code", 1)
                    addProperty("appid", KugouSigner.APP_ID)
                    addProperty("key", KugouSigner.paramsKey(clientTime))
                    addProperty("mid", d.mid)
                    add("recommend_source", recSourceArr)
                }
                val response = transport.callAdvanced(
                    path = "/recommend",
                    method = "POST",
                    session = session,
                    body = body,
                    headers = mapOf("x-router" to "songlistairec.kugou.com"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                val parsed = parseSongs(response.data)
                if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
            }.getOrDefault(emptyList())
        }

        // 3. 引擎三：module/ai_recommend_song.js (POST /concepts/v1/ai/recommend_song 概念版原生 AI 推荐单曲)
        val conceptAiSongDef = async {
            runCatching {
                val targetPage = (batchIdx % 6) + 1
                val body = json(
                    "apiver" to 2,
                    "source" to 2,
                    "pagesize" to 30,
                    "page" to targetPage
                )
                val response = transport.callAdvanced(
                    path = "/concepts/v1/ai/recommend_song",
                    method = "POST",
                    session = session,
                    body = body,
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                val parsed = parseSongs(response.data)
                if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
            }.getOrDefault(emptyList())
        }

        // 4. 引擎四：module/top_card.js (POST /singlecardrec.service/v1/single_card_recommend 猜你喜欢单卡推荐)
        val singleCardDef = async {
            runCatching {
                val cardId = ((batchIdx % 3) + 1).toString()
                val clientPlaylistArr = JsonArray().apply {
                    rotatedSeeds.take(6).forEach { s ->
                        if (s.hash.isNotBlank()) {
                            add(JsonObject().apply {
                                addProperty("hash", s.hash.lowercase())
                                val aid = s.albumAudioId.takeIf { it > 0L } ?: s.audioId
                                if (aid > 0L) addProperty("album_audio_id", aid)
                            })
                        }
                    }
                }
                val body = JsonObject().apply {
                    addProperty("appid", KugouSigner.APP_ID)
                    addProperty("clientver", KugouSigner.CLIENT_VERSION)
                    addProperty("platform", "android")
                    addProperty("clienttime", clientTime)
                    addProperty("userid", uidNum)
                    addProperty("key", KugouSigner.paramsKey(clientTime))
                    addProperty("fakem", "60f7ebf1f812edbac3c63a7310001701760f")
                    addProperty("area_code", 1)
                    addProperty("mid", d.mid)
                    addProperty("uuid", "-")
                    add("client_playlist", clientPlaylistArr)
                    addProperty("u_info", "a0c35cd40af564444b5584c2754dedec")
                }
                val response = transport.callAdvanced(
                    path = "/singlecardrec.service/v1/single_card_recommend",
                    method = "POST",
                    session = session,
                    query = mapOf(
                        "card_id" to cardId,
                        "fakem" to "60f7ebf1f812edbac3c63a7310001701760f",
                        "area_code" to "1",
                        "platform" to "ios"
                    ),
                    body = body,
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                val parsed = parseSongs(response.data)
                if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
            }.getOrDefault(emptyList())
        }

        // 5. 引擎五：module/everyday_style_recommend.js (/everydayrec.service/everyday_style_recommend 个性化风格日推)
        val styleRecDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/everydayrec.service/everyday_style_recommend",
                    method = "POST",
                    session = session,
                    query = mapOf("tagids" to ""),
                    body = JsonObject(),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                val parsed = parseSongs(response.data)
                if (parsed.isNotEmpty()) parsed else parseSongs(response.body)
            }.getOrDefault(emptyList())
        }

        val collected = mutableListOf<Song>()
        val batchKeys = HashSet<String>()

        fun tryCollect(candidates: List<Song>) {
            for (s in candidates) {
                val key = s.hash.ifBlank { s.id }.lowercase()
                if (key.isNotBlank() && s.title != "未知歌曲" && !seenPersonalFmHashes.contains(key) && batchKeys.add(key)) {
                    collected.add(s)
                    if (collected.size >= size) break
                }
            }
        }

        // 交错融合 5 大官方个性化推荐引擎的结果
        val pfmResults = pfmDeferreds.flatMap { it.await() }
        val aiSeedResults = aiSeedRecDef.await()
        val conceptAiResults = conceptAiSongDef.await()
        val singleCardResults = singleCardDef.await()
        val styleResults = styleRecDef.await()

        val maxEngineLen = maxOf(
            pfmResults.size,
            aiSeedResults.size,
            conceptAiResults.size,
            singleCardResults.size,
            styleResults.size
        )
        val interleavedOfficial = mutableListOf<Song>()
        for (i in 0 until maxEngineLen) {
            if (i < pfmResults.size) interleavedOfficial.add(pfmResults[i])
            if (i < aiSeedResults.size) interleavedOfficial.add(aiSeedResults[i])
            if (i < conceptAiResults.size) interleavedOfficial.add(conceptAiResults[i])
            if (i < singleCardResults.size) interleavedOfficial.add(singleCardResults[i])
            if (i < styleResults.size) interleavedOfficial.add(styleResults[i])
        }
        tryCollect(interleavedOfficial)

        // 6. 若网关推荐流返回不满 20 首，严格基于用户真实常听歌手与红心曲风做同好扩展（彻底摒弃固定老歌词库）
        if (collected.size < size) {
            val needed = size - collected.size
            val personalizedExtra = guessYouLike(batchIndex = batchIdx, minSize = needed + 10, userSeeds = rotatedSeeds)
            tryCollect(personalizedExtra)
        }

        val diversified = diversifyArtistOrder(collected.take(size))
        val enriched = runCatching { enrichSongsPrivilegeLite(diversified) }.getOrDefault(diversified)
        for (s in enriched) {
            val key = s.hash.ifBlank { s.id }.lowercase()
            if (key.isNotBlank()) seenPersonalFmHashes.add(key)
        }
        if (enriched.isNotEmpty()) {
            lastPersonalFmSong = enriched.last()
        }
        enriched
    }

    /**
     * 基于用户真实听歌画像（最近播放 / 我喜欢的音乐 / 每日推荐中的常听歌手与曲目）扩展「猜你喜欢」候选池：
     * - 优先提取用户真实常听歌手（artistId 或歌手名），拉取这些歌手的代表作或关联新曲
     * - 结合用户每日推荐池中未听过的曲目交错混排，彻底告别与用户口味无关的硬编码老歌
     */
    suspend fun guessYouLike(
        batchIndex: Int = personalFmPoolCursor.getAndIncrement(),
        minSize: Int = 20,
        userSeeds: List<Song> = emptyList()
    ): List<Song> = coroutineScope {
        val effectiveSeeds = userSeeds.ifEmpty { userTasteSeedsCache.toList() }
        val userArtists = effectiveSeeds
            .flatMap { s ->
                s.artist.split("、", "/", "&", ",")
                    .map { it.trim() }
                    .filter { it.isNotBlank() && it != "未知歌手" && it != "群星" }
                    .map { name -> name to s.artistId }
            }
            .distinctBy { it.first.lowercase() }

        val results = mutableListOf<Song>()

        if (userArtists.isNotEmpty()) {
            // 从用户真实喜爱的歌手中轮流选取 3 位歌手拉取其热门/最新曲目
            val chosenArtists = (0 until 3.coerceAtMost(userArtists.size)).map { idx ->
                userArtists[(batchIndex * 3 + idx) % userArtists.size]
            }
            val page = (batchIndex / userArtists.size.coerceAtLeast(1)) + 1
            val artistDefs = chosenArtists.map { (artistName, artistId) ->
                async {
                    val byId = if (artistId.isNotBlank() && artistId != "0") {
                        runCatching { artistSongs(artistId, sort = if (batchIndex % 2 == 0) "hot" else "new", page = page, pageSize = 15) }
                            .getOrDefault(emptyList())
                    } else emptyList()
                    if (byId.isNotEmpty()) byId
                    else runCatching { searchSongs(artistName, page = page, pageSize = 15).songs }.getOrDefault(emptyList())
                }
            }
            val lists = artistDefs.map { it.await() }
            val maxLen = lists.maxOfOrNull { it.size } ?: 0
            for (i in 0 until maxLen) {
                for (l in lists) {
                    if (i < l.size) results.add(l[i])
                }
            }
        }

        // 同时融合用户真实每日推荐中尚未在随心听出现过的曲目
        val unseenSeeds = effectiveSeeds.filter { s ->
            val k = s.hash.ifBlank { s.id }.lowercase()
            k.isNotBlank() && !seenPersonalFmHashes.contains(k)
        }
        results.addAll(unseenSeeds)

        val filtered = results.filter { s ->
            val k = s.hash.ifBlank { s.id }.lowercase()
            k.isNotBlank() && !seenPersonalFmHashes.contains(k)
        }.distinctBy { it.hash.ifBlank { it.id }.lowercase() }

        if (filtered.size >= minSize) return@coroutineScope filtered

        // 仅当未登录且无任何个人种子时，使用新歌速递与飙升潮流曲目兜底
        val fallbackNew = runCatching {
            newSongExpress("TODAY", size = minSize + 10, page = (batchIndex % 3) + 1)
        }.getOrDefault(emptyList())
        (filtered + fallbackNew).distinctBy { it.hash.ifBlank { it.id }.lowercase() }
    }

    // 场景化音乐推荐歌曲（供心流番茄工作台等调用）
    suspend fun sceneRecommendSongs(sceneId: String, size: Int = 20): List<Song> {
        return when (sceneId.uppercase()) {
            "STUDY", "沉浸学习", "101", "CH_STUDY" -> fmSongs("jazz_lofi", size = size, forceNextBatch = true)
            "SLEEP", "深夜助眠", "102", "CH_SLEEP" -> fmSongs("sleep", size = size, forceNextBatch = true)
            "WORKOUT", "燃动运动", "106", "CH_WORKOUT" -> fmSongs("rock", size = size, forceNextBatch = true)
            "DRIVE", "自驾旅行", "105", "CH_DRIVE" -> fmSongs("hot", size = size, forceNextBatch = true)
            "HEALING", "治愈微风", "103", "CH_HEALING" -> fmSongs("folk", size = size, forceNextBatch = true)
            "CAFE", "独立爵士", "CH_CAFE" -> fmSongs("rnb", size = size, forceNextBatch = true)
            else -> personalFm(size = size)
        }
    }

    // =========================================================================
    // 电台 / FM 体系（对齐 MakcRe/KuGouMusicApi: fm_recommend, fm_class, fm_songs）
    // =========================================================================

    suspend fun fmChannels(): List<FmChannel> = coroutineScope {
        defaultFmChannels().forEach { fmChannelMetaMap[it.id] = it }

        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val uidNum = session?.userId?.toLongOrNull() ?: 0L

        val rcmdDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/v1/rcmd_list",
                    method = "POST",
                    session = session,
                    body = json(
                        "appid" to KugouSigner.APP_ID,
                        "clientver" to KugouSigner.CLIENT_VERSION,
                        "clienttime" to clientTime,
                        "mid" to d.mid,
                        "key" to KugouSigner.paramsKey(clientTime),
                        "rcmdsongcount" to 20,
                        "level" to 0,
                        "area_code" to 1,
                        "get_tracker" to 1,
                        "uid" to uidNum
                    ),
                    headers = mapOf("x-router" to "fm.service.kugou.com"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseFmChannelsFromResponse(response.data, defaultCategory = "推荐电台")
            }.getOrNull().orEmpty()
        }

        val classDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/v1/class_fm_song",
                    method = "POST",
                    session = session,
                    body = json(
                        "kguid" to uidNum,
                        "clienttime" to clientTime,
                        "mid" to d.mid,
                        "platform" to "android",
                        "clientver" to KugouSigner.CLIENT_VERSION,
                        "uid" to uidNum,
                        "get_tracker" to 1,
                        "key" to KugouSigner.paramsKey(clientTime),
                        "appid" to KugouSigner.APP_ID
                    ),
                    headers = mapOf("x-router" to "fm.service.kugou.com"),
                    sign = true,
                    includeDefaultParams = true
                ).requireSuccess()
                parseFmChannelsFromResponse(response.data, defaultCategory = "分类电台")
            }.getOrNull().orEmpty()
        }

        val timeDef = async {
            runCatching {
                val response = transport.callAdvanced(
                    path = "/v1/time_fm_info",
                    method = "GET",
                    query = mapOf("operator" to "7", "plat" to "0", "type" to "11", "area_code" to "1", "req_multi" to "1"),
                    headers = mapOf("x-router" to "fm.service.kugou.com"),
                    sign = false,
                    includeDefaultParams = true
                ).requireSuccess()
                parseFmChannelsFromResponse(response.data, defaultCategory = "心情场景")
            }.getOrNull().orEmpty()
        }

        val rcmdList = rcmdDef.await()
        val classList = classDef.await()
        val timeList = timeDef.await()

        val combined = (rcmdList + classList + timeList + defaultFmChannels()).distinctBy { it.id }
        combined.forEach { fmChannelMetaMap[it.id] = it }

        combined.mapIndexed { idx, fm ->
            async {
                if (fm.coverUrl.isNotBlank() && !fm.coverUrl.contains("20210519")) {
                    fm
                } else if (idx < 8) {
                    val realCover = runCatching {
                        fmChannelInitialSongsMap[fm.id]?.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                            ?: searchPlaylists(fm.name, page = 1, pageSize = 2)
                                .firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                    }.getOrNull().orEmpty()
                    val updated = if (realCover.isNotBlank()) fm.copy(coverUrl = realCover) else fm
                    fmChannelMetaMap[updated.id] = updated
                    updated
                } else {
                    fm
                }
            }
        }.map { it.await() }
    }

    private fun parseFmChannelsFromResponse(root: JsonObject, defaultCategory: String): List<FmChannel> {
        val dataNode = root.obj("data") ?: root
        val rawItems = buildList {
            addAll(root.objects("data"))
            addAll(dataNode.objects("list"))
            addAll(dataNode.objects("channels"))
            addAll(dataNode.objects("fm_list"))
            addAll(dataNode.objects("info"))
            dataNode.objects("data").forEach { group ->
                addAll(group.objects("channels"))
                addAll(group.objects("fm_list"))
                addAll(group.objects("list"))
            }
            root.objects("data").forEach { group ->
                addAll(group.objects("channels"))
                addAll(group.objects("fm_list"))
                addAll(group.objects("list"))
            }
        }
        val seen = HashSet<String>()
        return rawItems.mapNotNull { ch ->
            val id = (ch.text("fmid") ?: ch.text("channel_id") ?: ch.text("id") ?: "").trim()
            if (id.isBlank() || !seen.add(id)) return@mapNotNull null
            val name = (ch.text("fmname") ?: ch.text("channel_name") ?: ch.text("name") ?: "").trim()
            if (name.isBlank()) return@mapNotNull null
            val embeddedSongs = parseSongs(ch)
            if (embeddedSongs.isNotEmpty()) {
                fmChannelInitialSongsMap[id] = embeddedSongs
            }
            val img = imageUrl(
                ch.text("imgurl") ?: ch.text("imgUrl150") ?: ch.text("imgUrl100")
                    ?: ch.text("cover") ?: ch.text("pic") ?: ch.text("img")
            ).ifBlank { embeddedSongs.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl.orEmpty() }
            val desc = ch.text("desc") ?: ch.text("intro") ?: ch.text("description") ?: "全天候动态漫游伴听"
            val fmType = ch.number("fmtype") ?: ch.number("type") ?: 2
            val rawCat = ch.text("class_name") ?: ch.text("category") ?: ""
            val cat = when {
                rawCat.isNotBlank() -> rawCat
                name.contains("粤") || name.contains("英") || name.contains("日") || name.contains("韩") || name.contains("经典") || name.contains("80") || name.contains("90") || name.contains("00") -> "年代语种"
                name.contains("摇滚") || name.contains("民谣") || name.contains("R&B") || name.contains("电子") || name.contains("DJ") || name.contains("说唱") || name.contains("古风") -> "曲风流派"
                name.contains("睡") || name.contains("咖啡") || name.contains("学习") || name.contains("工作") || name.contains("伤感") || name.contains("安静") || name.contains("纯音") || name.contains("轻音") -> "心情场景"
                else -> defaultCategory
            }
            FmChannel(
                id = id,
                name = name,
                coverUrl = img,
                desc = desc,
                category = cat,
                fmType = fmType,
                listenerCountText = "${(12 + (id.hashCode() and 0x7F) % 85)}k 正在漫游"
            )
        }
    }

    private fun defaultFmChannels(): List<FmChannel> = listOf(
        FmChannel("hot", "流行风向标", "", "全网实时热播流行金曲，不间断漫游", "推荐电台", 2, "86.4k 正在漫游"),
        FmChannel("classics", "华语经典台", "", "岁月沉淀的华语时代金曲，首首大合唱", "年代语种", 2, "64.2k 正在漫游"),
        FmChannel("canton", "粤语港乐台", "", "维多利亚港的晚风与经典粤语情怀", "年代语种", 2, "52.8k 正在漫游"),
        FmChannel("english", "欧美潮流台", "", "Billboard 热单与欧美独立流行", "年代语种", 2, "41.5k 正在漫游"),
        FmChannel("acg", "二次元幻想台", "", "高燃番剧 OP/ED 与日系治愈声线", "年代语种", 2, "38.9k 正在漫游"),
        FmChannel("rock", "摇滚失真台", "", "鼓点与失真吉他，释放内心热血", "曲风流派", 2, "29.1k 正在漫游"),
        FmChannel("folk", "民谣理想台", "", "一把木吉他与远方的来信", "曲风流派", 2, "33.6k 正在漫游"),
        FmChannel("rnb", "R&B 微醺台", "", "丝滑转音与律动，适合夜晚独处", "曲风流派", 2, "27.4k 正在漫游"),
        FmChannel("jazz_lofi", "Lofi 咖啡馆", "", "慵懒爵士与黑胶采样，工作阅读伴侣", "心情场景", 2, "45.0k 正在漫游"),
        FmChannel("sleep", "助眠轻音台", "", "纯净钢琴与自然氛围，伴你安然入眠", "心情场景", 2, "59.3k 正在漫游")
    )

    private fun resolveFmCategoryKeywords(channelId: String, meta: FmChannel?): List<String> {
        val name = meta?.name.orEmpty()
        val idLower = channelId.lowercase()
        return when {
            idLower == "canton" || name.contains("粤语") || name.contains("港乐") -> listOf(
                "粤语经典 港乐金曲", "陈奕迅 张敬轩 杨千嬅 粤语", "Beyond 李克勤 王菲 粤语", "张学友 梅艳芳 谭咏麟 陈慧娴", "粤语热歌 港风"
            )
            idLower == "classics" || name.contains("经典") || name.contains("老歌") || name.contains("怀旧") || name.contains("80") || name.contains("90") -> listOf(
                "华语金曲 经典老歌", "周杰伦 林俊杰 陶喆 经典", "孙燕姿 梁静茹 蔡依林 经典", "王力宏 张惠妹 莫文蔚 经典", "时代金曲 华语怀旧"
            )
            idLower == "english" || name.contains("欧美") || name.contains("英文") || name.contains("英语") -> listOf(
                "欧美流行 Billboard 热单", "Taylor Swift Ed Sheeran Bruno Mars", "Coldplay OneRepublic Maroon 5", "The Weeknd Ariana Grande Lady Gaga", "欧美经典 英文金曲"
            )
            idLower == "acg" || name.contains("二次元") || name.contains("动漫") || name.contains("日") -> listOf(
                "二次元 动漫神曲 日语", "YOASOBI 米津玄师 Aimer", "RADWIMPS LiSA 动漫OP", "治愈日系 动漫原声", "泽野弘之 久石让 动漫"
            )
            idLower == "rock" || name.contains("摇滚") || name.contains("乐队") -> listOf(
                "中国摇滚 经典乐队", "五月天 新裤子 痛仰 万能青年旅店", "Beyond 伍佰 唐朝 黑豹", "Linkin Park Queen 摇滚", "独立摇滚 Livehouse"
            )
            idLower == "folk" || name.contains("民谣") -> listOf(
                "民谣 吉他 诗和远方", "毛不易 赵雷 朴树 民谣", "宋冬野 陈粒 房东的猫", "鹿先森 痛仰 独立民谣", "治愈温暖 城市民谣"
            )
            idLower == "rnb" || name.contains("R&B") || name.contains("微醺") || name.contains("蓝调") -> listOf(
                "华语R&B 陶喆 方大同", "节奏布鲁斯 微醺 R&B", "周杰伦 袁娅维 单依纯 R&B", "欧美R&B Soul 律动", "夜晚独处 丝滑R&B"
            )
            idLower == "jazz_lofi" || name.contains("咖啡") || name.contains("爵士") || name.contains("Lofi") || name.contains("阅读") || name.contains("学习") -> listOf(
                "爵士 咖啡馆 Lofi", "Bossa Nova 慵懒 钢琴", "Lofi HipHop 学习 伴奏", "轻爵士 萨克斯 午后", "安静自习 纯音乐"
            )
            idLower == "sleep" || name.contains("助眠") || name.contains("睡") || name.contains("轻音") || name.contains("纯音") || name.contains("安静") -> listOf(
                "助眠 纯音乐 钢琴", "轻音乐 自然 雨声 治愈", "大提琴 舒缓 冥想 睡眠", "晚安 纯音 疗愈", "安静 睡前 轻柔钢琴"
            )
            name.contains("短视频") || name.contains("抖音") || name.contains("网红") -> listOf(
                "抖音热歌 短视频热门", "网红热歌 飙升榜", "全网热播 短视频BGM", "卡点热歌 潮流流行", "爆款热歌 单曲循环"
            )
            name.contains("车载") || name.contains("DJ") || name.contains("电音") -> listOf(
                "车载音乐 兜风流行", "车载DJ 重低音 节奏", "电音 潮流 燃曲", "公路自驾 经典车载"
            )
            name.contains("古风") || name.contains("国风") -> listOf(
                "国风 戏腔 古风精选", "周深 银临 双笙 国风", "古风唯美 诗词仙侠", "国潮新歌 经典古风"
            )
            name.contains("伤感") || name.contains("情歌") || name.contains("治愈") -> listOf(
                "伤感情歌 走心催泪", "深夜治愈 温柔慢歌", "华语苦情歌 薛之谦 陈奕迅", "失恋情歌 抒情独白"
            )
            name.isNotBlank() -> listOf(
                "$name 精选", "$name 热门", "$name 经典", "$name 必听"
            )
            else -> listOf(
                "热歌榜 流行精选", "飙升 热门 华语流行", "概念版 必听 新歌", "全网热播 单曲循环"
            )
        }
    }

    /**
     * 电台歌曲动态流 (修复酷狗 /v1/app_song_list_offset 单次仅返回 5 首歌的问题)：
     * 1. 对数字 fmid 电台一次并发请求 4 个连续 offset 窗口（共 20 首）；
     * 2. 结合当前电台的分类与名称标签（resolveFmCategoryKeywords）自动补齐至完整 20 首且严格匹配电台分类；
     * 3. 维护每个电台独立的已推曲目去重集 seenFmChannelHashes，每次刷新或“继续漫游 +20首”都保证返回 20 首符合该电台分类的全新歌曲！
     */
    suspend fun fmSongs(
        channelId: String = "hot",
        size: Int = 20,
        forceNextBatch: Boolean = true
    ): List<Song> = coroutineScope {
        val cursor = fmChannelCursorMap.getOrPut(channelId) { AtomicInteger(0) }
        val batchIndex = if (forceNextBatch) cursor.getAndIncrement() else cursor.get()
        val seenSet = seenFmChannelHashes.getOrPut(channelId) { ConcurrentHashMap.newKeySet() }
        if (seenSet.size > 500) seenSet.clear()

        val collected = mutableListOf<Song>()
        val batchKeys = HashSet<String>()

        fun collectSongs(items: List<Song>) {
            for (s in items) {
                val key = s.hash.ifBlank { s.id }.lowercase()
                if (key.isNotBlank() && batchKeys.add(key)) {
                    if (!forceNextBatch || !seenSet.contains(key)) {
                        collected.add(s)
                        if (collected.size >= size) break
                    }
                }
            }
        }

        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val uidNum = session?.userId?.toLongOrNull() ?: 0L
        val stationMeta = fmChannelMetaMap[channelId] ?: defaultFmChannels().firstOrNull { it.id == channelId }
        val fmType = stationMeta?.fmType ?: 2

        // 0. 优先纳入 /v1/rcmd_list 或 /v1/class_fm_song 下发的该电台原生初始曲目池
        fmChannelInitialSongsMap[channelId]?.let { initialList ->
            if (initialList.isNotEmpty()) {
                collectSongs(initialList)
            }
        }

        // 1. 若 channelId 为数字 fmid，调用官方 /v1/app_song_list_offset (module/fm_songs.js)
        // 同时请求 4 个连续 offset 切片并附加递增时间戳，确保拉取真实电台曲目流
        if (channelId.all { it.isDigit() } && collected.size < size) {
            val baseOffset = (batchIndex * 15) % 90
            val sliceDeferreds = (0 until 4).map { sliceIdx ->
                async {
                    runCatching {
                        val sliceOffset = baseOffset + sliceIdx * 5
                        val reqTime = clientTime + sliceIdx
                        val fmDataArr = JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("fmid", channelId)
                                addProperty("fmtype", fmType)
                                addProperty("offset", sliceOffset)
                                addProperty("size", 20)
                                addProperty("singername", "")
                            })
                        }
                        val body = JsonObject().apply {
                            addProperty("appid", KugouSigner.APP_ID)
                            addProperty("area_code", 1)
                            addProperty("clienttime", reqTime)
                            addProperty("clientver", KugouSigner.CLIENT_VERSION)
                            add("data", fmDataArr)
                            addProperty("get_tracker", 1)
                            addProperty("key", KugouSigner.paramsKey(reqTime))
                            addProperty("mid", d.mid)
                            addProperty("uid", uidNum)
                        }
                        val response = transport.callAdvanced(
                            path = "/v1/app_song_list_offset",
                            method = "POST",
                            session = session,
                            body = body,
                            headers = mapOf(
                                "x-router" to "fm.service.kugou.com",
                                "Content-Type" to "application/json"
                            ),
                            sign = true,
                            includeDefaultParams = true
                        ).requireSuccess()
                        val songs = mutableListOf<Song>()
                        val dataArr = response.data.arr("data") ?: response.body.arr("data")
                        dataArr?.filter { it.isJsonObject }?.map { it.asJsonObject }?.forEach { group ->
                            songs.addAll(parseSongs(group))
                        }
                        if (songs.isEmpty()) songs.addAll(parseSongs(response.data))
                        songs
                    }.getOrDefault(emptyList())
                }
            }
            sliceDeferreds.forEach { def -> collectSongs(def.await()) }
        }

        // 2. 若官方单次返回不足 20 首，优先匹配与该电台同名的官方精选主题歌单曲库，避免普通关键词搜索混入书名或标题杂曲
        if (collected.size < size && stationMeta != null && stationMeta.name.isNotBlank()) {
            runCatching {
                val cachedPid = fmChannelPlaylistIdCache[channelId]
                val targetPid = if (!cachedPid.isNullOrBlank()) {
                    cachedPid
                } else {
                    val found = searchPlaylists(stationMeta.name, page = 1, pageSize = 5)
                        .firstOrNull { it.id.isNotBlank() && it.trackCount >= 15 }?.id.orEmpty()
                    if (found.isNotBlank()) fmChannelPlaylistIdCache[channelId] = found
                    found
                }
                if (targetPid.isNotBlank()) {
                    val plSongs = fetchPlaylistSongsAll(targetPid, session)
                    if (plSongs.isNotEmpty()) {
                        val shift = (batchIndex * 15) % plSongs.size
                        collectSongs(plSongs.drop(shift) + plSongs.take(shift))
                    }
                }
            }
        }

        // 3. 兜底：使用严格匹配该电台分类的关键词池补齐至完整 20 首
        val keywordRotations = resolveFmCategoryKeywords(channelId, stationMeta)
        var attempt = 0
        while (collected.size < size && attempt < 3) {
            val kwIndex = (batchIndex + attempt) % keywordRotations.size
            val currentKeyword = keywordRotations[kwIndex]
            val currentPage = ((batchIndex + attempt) / keywordRotations.size) + 1
            val searched = runCatching {
                searchSongs(currentKeyword, page = currentPage, pageSize = 30).songs
            }.getOrDefault(emptyList())
            collectSongs(searched)
            attempt++
        }

        val rawFinal = collected.take(size)
        val finalSongs = runCatching { enrichSongsPrivilegeLite(rawFinal) }.getOrDefault(rawFinal)
        for (s in finalSongs) {
            val key = s.hash.ifBlank { s.id }.lowercase()
            if (key.isNotBlank()) seenSet.add(key)
        }
        finalSongs
    }

    @Volatile
    var lastServerRawDsec: Long = 0L
        private set

    // 听歌等级与时长统计
    suspend fun userGradeInfo(dSec: Long? = null, diffSec: Long? = null): UserGradeInfo {
        val session = accounts.context()?.session
            ?: return UserGradeInfo(level = 0, score = 0, totalListenMinutes = 0, todayListenMinutes = 0, nextLevelScore = 100, progressPercent = 0f, totalListenSeconds = 0L)

        return runCatching {
            val clientTime = System.currentTimeMillis() / 1000
            val userIdNum = session.userId.toLongOrNull() ?: 0L
            val isReport = dSec != null && diffSec != null && dSec > 0L && diffSec > 0L
            val p = if (isReport) {
                val md5 = KugouSigner.md5("${dSec}${diffSec}00")
                val reportJson = "{\"token\":\"${session.token}\",\"md5\":\"$md5\"}"
                KugouSigner.rsa(reportJson).uppercase()
            } else {
                val innerJson = "{\"clienttime\":$clientTime,\"userid\":$userIdNum}"
                KugouSigner.rsa(innerJson).uppercase()
            }
            val clientVer = 10597
            val key = KugouSigner.md5("${KugouSigner.APP_ID}LnT6xpN3khm36zse0QzvmgTZ3waWdRSA${clientVer}$clientTime")
            val d = device()

            val requestBody = if (isReport) {
                json(
                    "mid" to d.mid,
                    "type" to 1,
                    "uuid" to "-",
                    "userid" to userIdNum,
                    "d_sec" to dSec!!,
                    "diff_sec" to diffSec!!,
                    "y_type" to 0,
                    "m_type" to 0,
                    "p" to p,
                    "appid" to 3116,
                    "clientver" to clientVer,
                    "clienttime" to clientTime,
                    "key" to key
                )
            } else {
                json(
                    "mid" to d.mid,
                    "type" to 1,
                    "uuid" to "-",
                    "userid" to userIdNum,
                    "p" to p,
                    "appid" to 3116,
                    "clientver" to clientVer,
                    "clienttime" to clientTime,
                    "key" to key
                )
            }

            val response = transport.callAdvanced(
                path = "/v2/get_grade_info",
                host = "http://userinfo.user.kugou.com",
                method = "POST",
                session = session,
                body = requestBody,
                query = mapOf("dfid" to d.dfid),
                headers = mapOf(
                    "Content-Type" to "text/plain; charset=ISO-8859-1",
                    "User-Agent" to "Android15-1070-10597-201-0-get_user_grade_info-wifi",
                    "KG-THash" to String.format("%07x", (System.currentTimeMillis() and 0xfffffff)),
                    "KG-Rec" to "1",
                    "KG-RC" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            )

            val status = response.data.number("status") ?: 1
            val errorCode = response.data.number("error_code") ?: 0
            if (status != 1 || errorCode != 0) {
                throw IllegalStateException("get_grade_info business error: status=$status, error_code=$errorCode")
            }

            val data = response.data.obj("data") ?: response.data
            val level = data.number("p_grade") ?: data.number("pGrade")
                ?: data.number("grade") ?: data.number("level") ?: 0
            val score = data.text("p_current_point")?.toLongOrNull()
                ?: data.text("pCurrentPoint")?.toLongOrNull()
                ?: data.text("score")?.toLongOrNull() ?: 0L
            val nextScore = data.text("p_next_grade_point")?.toLongOrNull()
                ?: data.text("pNextGradePoint")?.toLongOrNull()
                ?: data.text("next_score")?.toLongOrNull() ?: 0L
            val currentGradeBasePoint = data.text("p_grade_point")?.toLongOrNull()
                ?: data.text("pGradePoint")?.toLongOrNull() ?: 0L

            val dSecField = data.text("d_sec")?.toLongOrNull()
                ?: data.text("dSec")?.toLongOrNull()
                ?: data.text("listen_sec")?.toLongOrNull() ?: 0L
            val durationMinField = data.text("duration")?.toLongOrNull()
                ?: data.text("d_min")?.toLongOrNull()
                ?: data.text("listen_time")?.toLongOrNull() ?: 0L

            if (dSecField > 0L) {
                lastServerRawDsec = dSecField
            }

            // 严格对齐酷狗官方客户端展示逻辑：
            // 服务端 duration 字段为官方实际累计听歌分钟数（不受 VIP 签到任务积分虚增影响），d_sec 仅记录供上报使用
            val totalSec = when {
                durationMinField > 0L -> {
                    if (durationMinField > 2_000_000L) durationMinField else durationMinField * 60L
                }
                dSecField > 0L -> dSecField
                else -> 0L
            }
            val totalMin = totalSec / 60L

            val remainSecFromServer = data.text("remain_d_sec")?.toLongOrNull()
                ?: data.text("need_d_sec")?.toLongOrNull()
                ?: data.text("upgrade_d_sec")?.toLongOrNull()
                ?: data.text("next_d_sec")?.toLongOrNull()?.let { nextD ->
                    if (nextD > totalSec) nextD - totalSec else 0L
                } ?: 0L

            val (progressPct, remainMins) = UserGradeInfo.computeLevelProgressAndRemain(
                level = level,
                totalMinutes = totalMin,
                totalSeconds = totalSec,
                score = score,
                nextScore = nextScore,
                currentBaseScore = currentGradeBasePoint,
                remainSecondsFromServer = remainSecFromServer
            )

            UserGradeInfo(
                level = level,
                score = score,
                totalListenMinutes = totalMin,
                todayListenMinutes = 0L,
                nextLevelScore = if (nextScore > 0L) nextScore else 100L,
                progressPercent = progressPct,
                totalListenSeconds = totalSec,
                nextLevelRemainMinutes = remainMins
            )
        }.getOrElse {
            UserGradeInfo(level = 0, score = 0L, totalListenMinutes = 0L, todayListenMinutes = 0L, nextLevelScore = 100L, progressPercent = 0f, totalListenSeconds = 0L)
        }
    }

    // 概念版听歌打卡领VIP任务 (/youth/v2/report/listen_song)
    suspend fun reportListenSong(mixSongId: Long = 666075191L): Boolean {
        val session = accounts.context()?.session ?: return false
        return runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v2/report/listen_song",
                method = "POST",
                session = session,
                body = json("mixsongid" to mixSongId),
                query = mapOf("clientver" to "10566"),
                headers = mapOf(
                    "User-Agent" to "Android13-1070-10566-201-0-ReportPlaySongToServerProtocol-wifi",
                    "Content-Type" to "application/json; charset=utf-8"
                ),
                sign = true,
                includeDefaultParams = true
            )
            response.data.number("status") == 1
        }.getOrDefault(false)
    }

    // 云端最近播放
    suspend fun latestListenSongs(pageSize: Int = 50): List<Song> {
        val session = accounts.context()?.session ?: return emptyList()

        val historyList = runCatching {
            val response = transport.call(
                path = "/playhistory/v1/get_songs",
                session = session,
                method = "POST",
                body = json(
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "token" to session.token,
                    "source_classify" to "app",
                    "to_subdivide_sr" to 1
                )
            )
            parseSongs(response.data)
        }.getOrNull()

        if (!historyList.isNullOrEmpty()) {
            val enrichedHistory = runCatching { enrichSongsPrivilegeLite(historyList) }.getOrDefault(historyList)
            recordUserTasteSeeds(enrichedHistory)
            return enrichedHistory
        }

        return runCatching {
            val response = transport.call(
                path = "/playque/devque/v1/get_latest_songs",
                session = session,
                method = "POST",
                body = json(
                    "area_code" to "1",
                    "sources" to listOf("pc", "mobile", "tv", "car"),
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "ret_info" to 1,
                    "token" to session.token,
                    "pagesize" to pageSize
                )
            )
            parseSongs(response.data)
        }.getOrDefault(emptyList())
    }

    // 同步上报听歌历史至酷狗官方云端
    suspend fun uploadPlayHistory(song: Song): Boolean {
        val session = accounts.context()?.session ?: return false
        var mxid = if (song.albumAudioId > 0L) song.albumAudioId
            else if (song.audioId > 0L) song.audioId
            else song.id.toLongOrNull() ?: 0L

        if (mxid <= 0L && song.hash.isNotBlank()) {
            runCatching {
                val infoResp = transport.callAdvanced(
                    path = "/app/i/getSongInfo.php",
                    host = "http://m.kugou.com",
                    query = mapOf("cmd" to "playInfo", "hash" to song.hash.lowercase()),
                    sign = false,
                    includeDefaultParams = false
                )
                val resolvedId = infoResp.data.number("audio_id")?.toLong()
                    ?: infoResp.data.number("songid")?.toLong() ?: 0L
                if (resolvedId > 0L) mxid = resolvedId
            }
        }

        if (mxid <= 0L) return false

        return runCatching {
            val songEntry = JsonObject().apply {
                addProperty("mxid", mxid)
                addProperty("op", 1)
                addProperty("ot", System.currentTimeMillis() / 1000)
                addProperty("pc", 1)
            }
            val songsArray = JsonArray().apply { add(songEntry) }
            val body = JsonObject().apply {
                add("songs", songsArray)
                addProperty("token", session.token)
                addProperty("userid", session.userId.toLongOrNull() ?: 0L)
                addProperty("source_classify", "app")
            }

            val response = transport.call(
                path = "/playhistory/v1/upload_songs",
                session = session,
                method = "POST",
                query = mapOf("plat" to "3"),
                body = body
            )
            response.data.number("status") == 1
        }.getOrDefault(false)
    }

    private fun extractPurchasedJsonArray(root: JsonObject): JsonArray? {
        val dataElement = root.get("data")
        if (dataElement != null && dataElement.isJsonArray) {
            return dataElement.asJsonArray
        }
        val keys = listOf(
            "goods", "info", "audio_list", "list", "lists", "songs",
            "album_list", "albums", "goods_list", "rows", "records", "data"
        )
        val candidates = listOfNotNull(root.obj("data"), root)
        for (obj in candidates) {
            for (k in keys) {
                val arr = obj.get(k)?.takeIf { it.isJsonArray }?.asJsonArray
                if (arr != null && arr.size() > 0) return arr
            }
            for (entry in obj.entrySet()) {
                val v = entry.value
                if (v != null && v.isJsonArray && v.asJsonArray.size() > 0) return v.asJsonArray
            }
            for (k in keys) {
                val arr = obj.get(k)?.takeIf { it.isJsonArray }?.asJsonArray
                if (arr != null) return arr
            }
            for (entry in obj.entrySet()) {
                val v = entry.value
                if (v != null && v.isJsonArray) return v.asJsonArray
            }
        }
        return null
    }

    // 已购单曲 (严格对齐 KuGouMusicApi: /openapi/copyright/v1/audio/get_goods + 批量补全音质标签)
    suspend fun userPurchasedSongs(page: Int = 1, pageSize: Int = 50): List<PurchasedSongItem> {
        val session = accounts.context()?.session ?: return emptyList()
        val userIdNum = session.userId.toLongOrNull() ?: return emptyList()
        return runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/copyright/v1/audio/get_goods",
                method = "POST",
                session = session,
                body = json(
                    "appid" to KugouSigner.APP_ID,
                    "userid" to userIdNum,
                    "token" to session.token,
                    "page" to page,
                    "pagesize" to pageSize,
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "deleted" to 0,
                    "need_audio_info" to 1,
                    "area_code" to "1"
                ),
                sign = true,
                includeDefaultParams = true
            )
            val list = extractPurchasedJsonArray(response.data) ?: return emptyList()

            val rawItems = list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { rawObj ->
                val o = rawObj.obj("audio_info") ?: rawObj.obj("song_info") ?: rawObj
                val song = parseSong(o)
                val id = o.text("audio_id") ?: o.text("songid") ?: o.text("mixsongid")
                    ?: rawObj.text("audio_id") ?: rawObj.text("goods_id") ?: song.id
                if (id.isBlank() && song.hash.isBlank()) return@mapNotNull null

                val name = o.text("songname") ?: o.text("song_name") ?: o.text("audio_name")
                    ?: rawObj.text("goods_name") ?: song.title.ifBlank { "已购单曲" }
                val singer = o.text("singername") ?: o.text("singer_name") ?: o.text("author_name")
                    ?: rawObj.text("singer_name") ?: song.artist.ifBlank { "未知歌手" }
                val time = rawObj.text("buy_time") ?: rawObj.text("create_time") ?: rawObj.text("pay_time")
                    ?: rawObj.text("add_time") ?: o.text("buy_time") ?: ""

                val mergedSong = if (song.title == "未知歌曲" && name.isNotBlank()) {
                    song.copy(id = id.ifBlank { song.id }, title = name, artist = singer)
                } else {
                    song.copy(id = song.id.ifBlank { id })
                }
                PurchasedSongItem(
                    songId = id.ifBlank { mergedSong.id },
                    songName = mergedSong.title.ifBlank { name },
                    singerName = mergedSong.artist.ifBlank { singer },
                    buyTime = time,
                    song = mergedSong
                )
            }
            val songsToEnrich = rawItems.mapNotNull { it.song }
            val enrichedSongs = runCatching { enrichSongsPrivilegeLite(songsToEnrich) }.getOrDefault(songsToEnrich)
            val enrichedMap = enrichedSongs.associateBy { it.id.ifBlank { it.hash } }
            rawItems.map { item ->
                val updatedSong = item.song?.let { s -> enrichedMap[s.id.ifBlank { s.hash }] ?: s }
                item.copy(song = updatedSong)
            }
        }.getOrDefault(emptyList())
    }

    // 已购专辑 (严格对齐 KuGouMusicApi: /openapi/v1/copyright/get_album_goods)
    suspend fun userPurchasedAlbums(page: Int = 1, pageSize: Int = 20): List<PurchasedAlbumItem> {
        val session = accounts.context()?.session ?: return emptyList()
        val userIdNum = session.userId.toLongOrNull() ?: return emptyList()
        return runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/v1/copyright/get_album_goods",
                method = "POST",
                session = session,
                body = json(
                    "appid" to KugouSigner.APP_ID,
                    "userid" to userIdNum,
                    "token" to session.token,
                    "page" to page,
                    "pagesize" to pageSize,
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "deleted" to 0
                ),
                sign = true,
                includeDefaultParams = true
            )
            val list = extractPurchasedJsonArray(response.data) ?: return emptyList()

            list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { rawObj ->
                val o = rawObj.obj("album_info") ?: rawObj.obj("goods_info") ?: rawObj
                val id = o.text("album_id") ?: o.text("albumid") ?: o.text("AlbumID")
                    ?: rawObj.text("album_id") ?: rawObj.text("albumid") ?: rawObj.text("target_id")
                    ?: rawObj.text("res_id") ?: rawObj.text("goods_id") ?: rawObj.text("id") ?: ""
                if (id.isBlank()) return@mapNotNull null

                var name = o.text("album_name") ?: o.text("AlbumName") ?: o.text("albumname") ?: o.text("name")
                    ?: rawObj.text("album_name") ?: rawObj.text("goods_name") ?: ""
                var singer = o.text("author_name") ?: o.text("singer_name") ?: o.text("singername")
                    ?: o.text("artist_name") ?: rawObj.text("author_name") ?: rawObj.text("singer_name") ?: ""
                val coverRaw = o.text("sizable_cover") ?: o.text("album_img") ?: o.text("img")
                    ?: o.text("imgurl") ?: o.text("cover") ?: o.text("pic") ?: o.text("cover_url")
                    ?: rawObj.text("sizable_cover") ?: rawObj.text("album_img") ?: rawObj.text("img")
                    ?: rawObj.text("cover")
                var coverUrl = imageUrl(coverRaw)
                var count = o.number("song_count") ?: o.number("songcount") ?: o.number("audio_count")
                    ?: rawObj.number("song_count") ?: rawObj.number("audio_count") ?: 0
                val time = rawObj.text("buy_time") ?: rawObj.text("create_time") ?: rawObj.text("pay_time")
                    ?: rawObj.text("add_time") ?: o.text("buy_time") ?: ""

                if (name.isBlank() || coverUrl.isBlank() || singer.isBlank()) {
                    val detail = runCatching { albumDetail(id) }.getOrNull()
                    if (detail != null) {
                        if (name.isBlank() && detail.name.isNotBlank()) name = detail.name
                        if (singer.isBlank() && detail.artist.isNotBlank()) singer = detail.artist
                        if (coverUrl.isBlank() && detail.coverUrl.isNotBlank()) coverUrl = detail.coverUrl
                        if (count <= 0 && detail.songCount > 0) count = detail.songCount
                    }
                }
                if (count <= 0) {
                    count = runCatching { albumSongs(id).size }.getOrDefault(0)
                }

                PurchasedAlbumItem(
                    albumId = id,
                    albumName = name.ifBlank { "数字专辑" },
                    singerName = singer.ifBlank { "群星" },
                    coverUrl = coverUrl,
                    songCount = count,
                    buyTime = time
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 关注歌手列表 (严格对齐 MakcRe/KuGouMusicApi module/user_follow.js:
     * POST /v4/follow_list, query = mapOf("plat" to "1"), headers = mapOf("x-router" to "relationuser.kugou.com"))
     * 修复此前因缺少 query plat=1 及未解析 nickname / ext_params.singerid 导致关注歌手列表为空的 Bug！
     */
    suspend fun followedArtists(page: Int = 1, pageSize: Int = 50): List<FollowedArtist> {
        val ctx = accounts.context()
        val session = if (ctx != null) {
            runCatching { accounts.authenticated(ctx) { it } }.getOrNull() ?: ctx.session
        } else null
        if (session == null) return localFollowedArtistsMap.values.toList()

        val clientTime = System.currentTimeMillis() / 1000
        val pJson = KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "token" to session.token))
        val pEncrypted = runCatching { KugouSigner.rsa(pJson).uppercase() }.getOrDefault("")

        val fromV4 = runCatching {
            val response = transport.callAdvanced(
                path = "/v4/follow_list",
                method = "POST",
                session = session,
                query = mapOf("plat" to "1"),
                body = json(
                    "merge" to 2,
                    "need_iden_type" to 1,
                    "ext_params" to "k_pic,jumptype,singerid,score",
                    "userid" to (session.userId.toLongOrNull() ?: session.userId),
                    "type" to 0,
                    "id_type" to 0,
                    "p" to pEncrypted
                ),
                headers = mapOf("x-router" to "relationuser.kugou.com"),
                sign = true,
                includeDefaultParams = true
            )
            parseFollowedArtistsFromJson(response.body)
        }.getOrNull().orEmpty()

        // 备用通道：若 /v4/follow_list 仍未返回，尝试从关注歌手新歌流 (/feed/v1/follownewsong_album_list, module/artist_follow_newsongs.js) 提取已关注歌手
        val baseList = if (fromV4.isNotEmpty()) {
            fromV4
        } else {
            runCatching {
                val resp = transport.callAdvanced(
                    path = "/feed/v1/follownewsong_album_list",
                    method = "POST",
                    session = session,
                    query = mapOf("last_album_id" to "0", "page_size" to pageSize.toString(), "opt_sort" to "1"),
                    body = json("last_album_id" to 0),
                    sign = true,
                    includeDefaultParams = true
                )
                parseFollowedArtistsFromJson(resp.body)
            }.getOrDefault(emptyList())
        }

        // 若部分关注项只有歌手昵称与酷狗 userid，缺少真实 singerid 或头像，并发补全真实歌手 ID 与作品数
        val enriched = coroutineScope {
            baseList.map { item ->
                async {
                    if (item.id.isNotBlank() && item.id != "0" && item.avatarUrl.isNotBlank() && item.songCount > 0) {
                        item
                    } else if (item.name.isNotBlank() && item.name != "关注歌手") {
                        val detailById = if (item.id.isNotBlank() && item.id != "0") {
                            runCatching { artistDetail(item.id) }.getOrNull()
                        } else null
                        if (detailById != null && detailById.songCount > 0) {
                            item.copy(
                                id = detailById.id.ifBlank { item.id },
                                name = detailById.name.ifBlank { item.name },
                                avatarUrl = item.avatarUrl.ifBlank { detailById.avatarUrl },
                                songCount = detailById.songCount
                            )
                        } else {
                            val searched = runCatching {
                                searchArtists(item.name, page = 1, pageSize = 2, allowSongFallback = true).firstOrNull()
                            }.getOrNull()
                            if (searched != null) {
                                item.copy(
                                    id = searched.id.ifBlank { item.id },
                                    avatarUrl = item.avatarUrl.ifBlank { searched.avatarUrl },
                                    songCount = if (item.songCount > 0) item.songCount else searched.songCount
                                )
                            } else item
                        }
                    } else item
                }
            }.map { it.await() }
        }

        enriched.forEach { if (it.id.isNotBlank()) localFollowedArtistsMap[it.id] = it }
        return (localFollowedArtistsMap.values + enriched).distinctBy { it.id.ifBlank { it.name } }
    }

    private fun parseFollowedArtistsFromJson(root: JsonObject): List<FollowedArtist> {
        val dataObj = root.obj("data") ?: root
        val array = listOf("lists", "list", "info", "follow_list", "authors", "singers", "data").asSequence()
            .mapNotNull { key ->
                (dataObj.arr(key) ?: root.arr(key))?.takeIf { it.size() > 0 }
            }.firstOrNull() ?: return emptyList()

        val seen = HashSet<String>()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val ext = o.obj("ext_params") ?: o.obj("extra")
            val singerId = (o.text("singerid") ?: ext?.text("singerid")
                ?: o.text("author_id") ?: ext?.text("author_id")
                ?: o.text("singer_id") ?: o.text("AuthorID") ?: "").trim().takeIf { it != "0" }.orEmpty()
            val fallbackUserId = (o.text("target_userid") ?: o.text("userid") ?: o.text("id") ?: "").trim().takeIf { it != "0" }.orEmpty()
            val rawName = (o.text("singername") ?: o.text("author_name")
                ?: o.text("nickname") ?: o.text("username")
                ?: o.text("r_nick") ?: o.text("name")
                ?: ext?.text("singername") ?: "").trim()

            // 在 /v4/follow_list 中，若 merge=2 同时返回普通用户与歌手，优先保留有 singerid 或名称有效的条目
            val effectiveId = singerId.ifBlank { fallbackUserId }
            if (effectiveId.isBlank() && rawName.isBlank()) return@mapNotNull null
            val dedupKey = effectiveId.ifBlank { rawName }
            if (!seen.add(dedupKey)) return@mapNotNull null

            val avatarRaw = o.text("k_pic") ?: ext?.text("k_pic")
                ?: o.text("pic") ?: o.text("img")
                ?: o.text("avatar") ?: o.text("sizable_avatar") ?: o.text("imgurl")
            val fans = o.text("fanscount")?.toLongOrNull()
                ?: o.text("fans_count")?.toLongOrNull()
                ?: o.text("fansnum")?.toLongOrNull() ?: 0L
            val songCount = o.number("songcount") ?: o.number("song_count") ?: o.number("audio_count") ?: 0

            FollowedArtist(
                id = effectiveId,
                name = rawName.ifBlank { "关注歌手" },
                avatarUrl = imageUrl(avatarRaw),
                songCount = songCount,
                fansCount = fans
            )
        }
    }

    // 关注歌手 (严格对齐 MakcRe/KuGouMusicApi module/artist_follow.js: /followservice/v3/follow_singer + /v4/follow 双通道)
    suspend fun followArtist(artistId: String): Boolean = auth { ctx ->
        val targetId = artistId.toLongOrNull() ?: return@auth false
        val session = ctx.session
        val clientTime = System.currentTimeMillis() / 1000

        // 通道 1: /followservice/v3/follow_singer (module/artist_follow.js)
        val v3Ok = runCatching {
            val enc = KugouSigner.encrypt(KugouSigner.gson.toJson(mapOf("singerid" to targetId, "token" to session.token)))
            val pRsa = KugouSigner.rsa(KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "key" to enc.key))).uppercase()
            val resp = transport.callAdvanced(
                path = "/followservice/v3/follow_singer",
                method = "POST",
                session = session,
                query = mapOf("clienttime" to clientTime.toString()),
                body = json(
                    "plat" to 0,
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "singerid" to targetId,
                    "source" to 7,
                    "p" to pRsa,
                    "params" to enc.ciphertext
                ),
                sign = true,
                includeDefaultParams = true
            )
            resp.status == 1 || resp.code == 0
        }.getOrDefault(false)

        // 通道 2: relationuser.kugou.com /v4/follow
        val v4Ok = runCatching {
            val pJson = KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "token" to session.token))
            val pEncrypted = KugouSigner.rsa(pJson).uppercase()
            val response = transport.callAdvanced(
                path = "/v4/follow",
                method = "POST",
                session = session,
                query = mapOf("plat" to "1"),
                body = json(
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "target_userid" to targetId,
                    "singerid" to targetId,
                    "type" to 1,
                    "id_type" to 1,
                    "source" to 13,
                    "p" to pEncrypted
                ),
                headers = mapOf("x-router" to "relationuser.kugou.com"),
                sign = true,
                includeDefaultParams = true
            )
            response.status == 1 || response.code == 0
        }.getOrDefault(false)

        v3Ok || v4Ok
    }

    // 取消关注歌手 (双通道)
    suspend fun unfollowArtist(artistId: String): Boolean = auth { ctx ->
        localFollowedArtistsMap.remove(artistId)
        val targetId = artistId.toLongOrNull() ?: return@auth false
        val session = ctx.session
        val clientTime = System.currentTimeMillis() / 1000

        val v3Ok = runCatching {
            val enc = KugouSigner.encrypt(KugouSigner.gson.toJson(mapOf("singerid" to targetId, "token" to session.token)))
            val pRsa = KugouSigner.rsa(KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "key" to enc.key))).uppercase()
            val resp = transport.callAdvanced(
                path = "/followservice/v3/unfollow_singer",
                method = "POST",
                session = session,
                query = mapOf("clienttime" to clientTime.toString()),
                body = json(
                    "plat" to 0,
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "singerid" to targetId,
                    "source" to 7,
                    "p" to pRsa,
                    "params" to enc.ciphertext
                ),
                sign = true,
                includeDefaultParams = true
            )
            resp.status == 1 || resp.code == 0
        }.getOrDefault(false)

        val v4Ok = runCatching {
            val pJson = KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "token" to session.token))
            val pEncrypted = KugouSigner.rsa(pJson).uppercase()
            val response = transport.callAdvanced(
                path = "/v4/unfollow",
                method = "POST",
                session = session,
                query = mapOf("plat" to "1"),
                body = json(
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "target_userid" to targetId,
                    "singerid" to targetId,
                    "type" to 1,
                    "id_type" to 1,
                    "source" to 13,
                    "p" to pEncrypted
                ),
                headers = mapOf("x-router" to "relationuser.kugou.com"),
                sign = true,
                includeDefaultParams = true
            )
            response.status == 1 || response.code == 0
        }.getOrDefault(false)

        v3Ok || v4Ok
    }

    // 外部歌单文本批量匹配导入
    suspend fun importExternalPlaylist(name: String, rawLines: List<String>): Int = auth { _ ->
        val cleanName = name.trim().ifBlank { "导入歌单" }
        val cleanLines = rawLines.map { it.trim() }.filter { it.isNotBlank() }.take(100)
        if (cleanLines.isEmpty()) return@auth 0

        createPlaylist(cleanName)
        val targetPlaylist = runCatching {
            userPlaylists().firstOrNull { it.isCreatedByMe && it.title == cleanName }
        }.getOrNull() ?: return@auth 0

        var importedCount = 0
        for (line in cleanLines) {
            val matchedSong = runCatching {
                searchSongs(line, page = 1, pageSize = 3).songs.firstOrNull()
            }.getOrNull()
            if (matchedSong != null) {
                val added = runCatching { addSongToPlaylist(targetPlaylist.listId, matchedSong) }.getOrDefault(false)
                if (added) importedCount++
            }
        }
        importedCount
    }

    @Volatile
    var lastSongCommentCount: Int = 0
        private set

    private suspend fun resolveCommentMixSongId(song: Song): Long {
        var mixSongId = song.albumAudioId.takeIf { it > 0 } ?: song.audioId.takeIf { it > 0 } ?: 0L
        val cleanHash = song.hash.trim().lowercase()
        if (mixSongId <= 0 && cleanHash.isNotBlank()) {
            runCatching {
                val infoResp = transport.callAdvanced(
                    path = "/app/i/getSongInfo.php",
                    host = "http://m.kugou.com",
                    query = mapOf("cmd" to "playInfo", "hash" to cleanHash),
                    sign = false,
                    includeDefaultParams = false
                )
                val resolvedMixId = infoResp.data.number("album_audio_id")?.toLong()
                    ?: infoResp.data.number("mixsongid")?.toLong()
                    ?: infoResp.data.number("audio_id")?.toLong()
                    ?: infoResp.data.number("songid")?.toLong() ?: 0L
                if (resolvedMixId > 0) mixSongId = resolvedMixId
            }
        }
        return mixSongId
    }

    suspend fun songComments(song: Song, type: String = "hot", page: Int = 1, pageSize: Int = 30): List<SongComment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (page == 1 && lastSongCommentCount < 0) {
            lastSongCommentCount = 0
        }
        val mixSongId = resolveCommentMixSongId(song)
        val cleanHash = song.hash.trim().lowercase()
        if (mixSongId <= 0 && cleanHash.isBlank()) return@withContext emptyList()
        val extData = cleanHash.ifBlank { "0" }
        val isHotTab = type != "new"

        fun extractTotalCount(root: JsonObject): Int {
            val d = root.obj("data")
            return listOfNotNull(
                root.number("count"),
                root.number("total"),
                root.number("total_count"),
                root.number("all_count"),
                root.number("comments_count"),
                d?.number("count"),
                d?.number("total"),
                d?.number("total_count"),
                d?.number("all_count"),
                d?.number("comments_count"),
                d?.number("weight_count")
            ).maxOrNull() ?: 0
        }

        val classifyCommentsDef = async {
            runCatching {
                val response = transport.call(
                    path = "/mcomment/v1/cmt_classify_list",
                    method = "POST",
                    query = mapOf(
                        "mixsongid" to mixSongId.toString(),
                        "need_show_image" to "1",
                        "p" to page.toString(),
                        "pagesize" to pageSize.toString(),
                        "extdata" to extData,
                        "code" to "fc4be23b4e972707f36b8a828a93ba8a",
                        "sort_method" to (if (isHotTab) "1" else "2"),
                        "type" to (if (isHotTab) "1" else "2")
                    )
                )
                val parsedCount = extractTotalCount(response.data)
                if (parsedCount > 0) {
                    lastSongCommentCount = maxOf(lastSongCommentCount, parsedCount)
                }
                parseCommentList(response.data, preferHotList = isHotTab)
            }.getOrDefault(emptyList())
        }

        val cmtListDef = async {
            if (isHotTab && page > 1) {
                emptyList()
            } else {
                runCatching {
                    val response = transport.call(
                        path = "/mcomment/v1/cmtlist",
                        method = "POST",
                        query = mapOf(
                            "ver" to "6",
                            "mixsongid" to mixSongId.toString(),
                            "need_show_image" to "1",
                            "p" to page.toString(),
                            "pagesize" to pageSize.toString(),
                            "show_classify" to "1",
                            "show_hotword_list" to "1",
                            "extdata" to extData,
                            "code" to "fc4be23b4e972707f36b8a828a93ba8a"
                        )
                    )
                    val parsedCount = extractTotalCount(response.data)
                    if (parsedCount > 0) {
                        lastSongCommentCount = maxOf(lastSongCommentCount, parsedCount)
                    }
                    parseCommentList(response.data, preferHotList = isHotTab)
                }.getOrDefault(emptyList())
            }
        }

        val classifyList = classifyCommentsDef.await()
        val fallbackList = cmtListDef.await()

        val mergedRaw = if (isHotTab) {
            (fallbackList + classifyList)
                .distinctBy { it.id }
                .sortedWith(
                    compareByDescending<SongComment> { it.isHot }
                        .thenByDescending { it.likeCount }
                        .thenByDescending { it.replyCount }
                )
        } else {
            (classifyList.ifEmpty { fallbackList })
                .distinctBy { it.id }
                .sortedByDescending { it.timeFormatted }
        }

        if (lastSongCommentCount <= 0 && mergedRaw.isNotEmpty()) {
            lastSongCommentCount = mergedRaw.size
        }

        // 自动并发预取前 6 条含楼中楼回复评论的子回复列表，确保打开评论页即可展示完整楼中楼
        val candidatesForReplyFetch = mergedRaw
            .filter { it.replyCount > 0 && it.replies.isEmpty() && it.id.isNotBlank() }
            .take(6)
            .map { it.id }
            .toSet()

        if (candidatesForReplyFetch.isEmpty()) {
            return@withContext mergedRaw
        }

        val repliesByParentId = coroutineScope {
            candidatesForReplyFetch.map { parentId ->
                async {
                    val fetchedReplies = runCatching {
                        songCommentReplies(song, parentId, page = 1, pageSize = 10, resolvedMixSongId = mixSongId)
                    }.getOrDefault(emptyList())
                    parentId to fetchedReplies
                }
            }.associate { it.await() }
        }

        mergedRaw.map { cmt ->
            val fetched = repliesByParentId[cmt.id]
            if (!fetched.isNullOrEmpty()) {
                cmt.copy(
                    replies = fetched,
                    replyCount = maxOf(cmt.replyCount, fetched.size)
                )
            } else cmt
        }
    }

    suspend fun songCommentReplies(
        song: Song,
        commentId: String,
        page: Int = 1,
        pageSize: Int = 20,
        resolvedMixSongId: Long = 0L
    ): List<SongComment> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (commentId.isBlank()) return@withContext emptyList()
        val mixSongId = resolvedMixSongId.takeIf { it > 0L } ?: resolveCommentMixSongId(song)
        val extData = song.hash.trim().lowercase().ifBlank { "0" }
        val session = accounts.context()?.session

        val primaryReplies = runCatching {
            val response = transport.call(
                path = "/mcomment/v1/replylist",
                method = "POST",
                session = session,
                query = mapOf(
                    "ver" to "6",
                    "mixsongid" to mixSongId.toString(),
                    "cmtid" to commentId,
                    "tid" to commentId,
                    "p" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "extdata" to extData,
                    "code" to "fc4be23b4e972707f36b8a828a93ba8a"
                )
            )
            parseReplyItems(response.data, parentCommentId = commentId)
        }.getOrDefault(emptyList())

        if (primaryReplies.isNotEmpty()) return@withContext primaryReplies

        runCatching {
            val response = transport.call(
                path = "/mcomment/v2/replylist",
                method = "GET",
                session = session,
                query = mapOf(
                    "ver" to "6",
                    "mixsongid" to mixSongId.toString(),
                    "cmtid" to commentId,
                    "tid" to commentId,
                    "p" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "extdata" to extData,
                    "code" to "fc4be23b4e972707f36b8a828a93ba8a"
                )
            )
            parseReplyItems(response.data, parentCommentId = commentId)
        }.getOrDefault(emptyList())
    }

    suspend fun likeSongComment(song: Song, commentId: String, like: Boolean): Boolean {
        val session = accounts.context()?.session ?: return false
        if (session.token.isBlank() || commentId.isBlank()) return false
        val mixSongId = song.albumAudioId.takeIf { it > 0 } ?: song.audioId.takeIf { it > 0 } ?: song.id.toLongOrNull() ?: 0L
        return runCatching {
            val response = transport.call(
                path = "/mcomment/v1/cmt_like",
                method = "POST",
                query = mapOf(
                    "mixsongid" to mixSongId.toString(),
                    "cmtid" to commentId,
                    "action" to (if (like) "1" else "0"),
                    "code" to "fc4be23b4e972707f36b8a828a93ba8a",
                    "token" to session.token,
                    "userid" to session.userId
                ),
                session = session
            )
            response.status == 1 || response.code == 0
        }.getOrDefault(false)
    }

    private fun extractCommentLikeCount(o: JsonObject): Int {
        val likeObj = o.obj("like")
        return listOfNotNull(
            o.number("like_count"),
            likeObj?.number("count"),
            likeObj?.number("like_count"),
            likeObj?.number("praisecount"),
            likeObj?.number("like_num"),
            o.number("praisecount"),
            o.number("like_num"),
            o.number("likes")
        ).maxOrNull() ?: 0
    }

    private fun extractCommentIsLiked(o: JsonObject): Boolean {
        val likeObj = o.obj("like")
        return o.number("is_like") == 1 ||
            likeObj?.number("is_like") == 1 ||
            likeObj?.number("status") == 1
    }

    private fun parseReplyItems(
        root: JsonObject,
        parentCommentId: String,
        defaultReplyUserName: String? = null
    ): List<SongComment> {
        val dataObj = root.obj("data") ?: root
        val arr = listOf("list", "reply_list", "replys", "children", "comments", "lists", "info")
            .asSequence()
            .mapNotNull { key -> (dataObj.arr(key) ?: root.arr(key))?.takeIf { it.size() > 0 } }
            .firstOrNull() ?: return emptyList()

        return arr.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { r ->
            val rId = r.text("id") ?: r.text("reply_id") ?: r.text("cmtid") ?: ""
            val rContent = (r.text("content") ?: r.text("text") ?: "").trim()
            if (rContent.isBlank()) return@mapNotNull null
            val rUdetail = r.obj("udetail") ?: r.obj("user")
            val rUId = r.text("user_id") ?: rUdetail?.text("user_id") ?: r.text("userid") ?: ""
            val rUName = r.text("user_name") ?: r.text("nick_name")
                ?: rUdetail?.text("user_name") ?: rUdetail?.text("nickname") ?: "酷狗乐迷"
            val rAvatar = imageUrl(
                r.text("user_pic") ?: r.text("avatar") ?: r.text("pic")
                    ?: rUdetail?.text("user_pic") ?: rUdetail?.text("avatar")
            )
            val rTime = r.text("addtime") ?: r.text("time") ?: "近期"
            val rLikes = extractCommentLikeCount(r)
            val rIsLiked = extractCommentIsLiked(r)
            val rIp = (r.text("location") ?: r.text("ip_location") ?: r.text("province")
                ?: rUdetail?.text("location") ?: "").removePrefix("IP属地:").removePrefix("IP属地：").trim()
            val rReplyUser = r.text("reply_user_name") ?: r.text("reply_username")
                ?: r.obj("reply_user")?.text("user_name")
                ?: r.obj("puser")?.text("user_name")
                ?: defaultReplyUserName

            SongComment(
                id = rId.ifBlank { "${parentCommentId}_${rTime}_${rContent.hashCode()}" },
                userId = rUId,
                userName = rUName,
                userAvatar = rAvatar,
                content = rContent,
                timeFormatted = rTime,
                likeCount = rLikes,
                isLiked = rIsLiked,
                ipLocation = rIp,
                parentId = parentCommentId,
                rootId = parentCommentId,
                replyUserName = rReplyUser
            )
        }
    }

    private fun parseCommentList(root: JsonObject, preferHotList: Boolean): List<SongComment> {
        val dataObj = root.obj("data") ?: root
        val candidateKeys = if (preferHotList) {
            listOf("weight_list", "hot_list", "list", "lists", "comments", "info")
        } else {
            listOf("list", "lists", "comments", "info", "weight_list", "hot_list")
        }

        var matchedKey = ""
        val list = candidateKeys.asSequence()
            .mapNotNull { key ->
                val arr = (dataObj.arr(key) ?: root.arr(key))?.takeIf { it.size() > 0 }
                if (arr != null) {
                    matchedKey = key
                    arr
                } else null
            }.firstOrNull() ?: return emptyList()

        val fromWeightOrHotArray = matchedKey == "weight_list" || matchedKey == "hot_list"

        return list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("id") ?: o.text("cmtid") ?: ""
            val content = (o.text("content") ?: "").trim()
            if (content.isBlank()) return@mapNotNull null

            val uDetail = o.obj("udetail") ?: o.obj("user")
            val uId = o.text("user_id") ?: uDetail?.text("user_id") ?: ""
            val uName = o.text("user_name") ?: o.text("nick_name")
                ?: uDetail?.text("user_name") ?: uDetail?.text("nickname") ?: "酷狗乐友"
            val uAvatar = imageUrl(
                o.text("user_pic") ?: o.text("avatar") ?: o.text("pic")
                    ?: uDetail?.text("user_pic") ?: uDetail?.text("avatar")
            )
            val time = o.text("addtime") ?: o.text("time") ?: "近期"
            val likes = extractCommentLikeCount(o)
            val isLiked = extractCommentIsLiked(o)
            val isHot = fromWeightOrHotArray || o.number("is_hot") == 1 || o.number("weight") == 1 || likes >= 50
            val vipLvl = o.number("vip_type") ?: uDetail?.number("vip_type") ?: o.number("m_type") ?: 0
            val ipLocation = (o.text("location") ?: o.text("ip_location") ?: o.text("province")
                ?: uDetail?.text("location") ?: "").removePrefix("IP属地:").removePrefix("IP属地：").trim()

            val inlineReplies = parseReplyItems(o, parentCommentId = id, defaultReplyUserName = uName)
            val replyCount = listOfNotNull(
                o.number("reply_num"),
                o.number("reply_count"),
                o.number("replys_count"),
                o.obj("reply")?.number("count"),
                inlineReplies.size
            ).maxOrNull() ?: 0

            SongComment(
                id = id,
                userId = uId,
                userName = uName,
                userAvatar = uAvatar,
                content = content,
                timeFormatted = time,
                likeCount = likes,
                isLiked = isLiked,
                isHot = isHot,
                vipLevel = vipLvl,
                ipLocation = ipLocation,
                replyCount = replyCount,
                replies = inlineReplies
            )
        }
    }

    private fun isValidHash(h: String?): Boolean {
        if (h.isNullOrBlank()) return false
        val clean = h.trim()
        if (clean.length != 32) return false
        if (clean.all { it == '0' }) return false
        return true
    }

    /**
     * 歌曲技术参数与音频详情（严格对齐当前实际播放档位或音源最高已验证档位，消除音质标签与码率/大小不匹配的矛盾）
     */
    fun songDetail(song: Song): SongDetailInfo {
        val cachedMeta = listOf(song.id, song.hash, song.hash128, song.hash320, song.hashFlac, song.hashHiRes)
            .map { it.trim().lowercase() }
            .firstNotNullOfOrNull { if (it.isNotBlank()) songTechMetaCache[it] else null }

        val activeQuality = song.actualPlayQuality ?: when {
            song.isHiRes && song.hashHiRes.isNotBlank() -> AudioQuality.HI_RES
            song.isLossless || song.hashFlac.isNotBlank() -> AudioQuality.LOSSLESS
            song.hash320.isNotBlank() || song.quality == "320" -> AudioQuality.HIGH
            else -> AudioQuality.STANDARD
        }

        val tierSpec = cachedMeta?.specs?.get(activeQuality)
        val effectiveDurMs = song.durationMs.takeIf { it > 0L } ?: cachedMeta?.durationMs ?: 0L

        // 严格对齐 MakcRe/KuGouMusicApi module/privilege_lite.js:
        // 优先使用 /v2/get_res_privilege/lite relate_goods 中对应档位 (level 1/2/4/5/6) 的精确字节大小或实测播放流大小
        val resolvedSizeBytes = when {
            tierSpec != null && tierSpec.sizeBytes > 0L -> tierSpec.sizeBytes
            song.actualPlayQuality == activeQuality && song.fileSizeByte > 0L -> song.fileSizeByte
            song.fileSizeByte > 0L && activeQuality == song.highestQuality -> song.fileSizeByte
            effectiveDurMs >= 15_000L -> {
                val estKbps = when (activeQuality) {
                    AudioQuality.HI_RES -> 2304L
                    AudioQuality.LOSSLESS -> 920L
                    AudioQuality.HIGH -> 320L
                    AudioQuality.STANDARD -> 128L
                }
                (effectiveDurMs * estKbps) / 8L
            }
            else -> 0L
        }

        val sizeDerivedKbps = if (resolvedSizeBytes > 0L && effectiveDurMs >= 15_000L) {
            ((resolvedSizeBytes * 8L) / effectiveDurMs).toInt()
        } else 0

        val resolvedKbps = when (activeQuality) {
            AudioQuality.HI_RES -> {
                tierSpec?.bitrateKbps?.takeIf { it in 400..6500 }
                    ?: sizeDerivedKbps.takeIf { it in 400..6500 }
                    ?: if (song.actualPlayQuality == AudioQuality.HI_RES && song.bitrateKbps in 400..6500) song.bitrateKbps else null
                    ?: song.bitrateKbps.takeIf { it in 400..6500 }
                    ?: 2304
            }
            AudioQuality.LOSSLESS -> {
                tierSpec?.bitrateKbps?.takeIf { it in 350..1850 }
                    ?: sizeDerivedKbps.takeIf { it in 350..1850 }
                    ?: if (song.actualPlayQuality == AudioQuality.LOSSLESS && song.bitrateKbps in 350..1850) song.bitrateKbps else null
                    ?: song.bitrateKbps.takeIf { it in 350..1850 }
                    ?: 920
            }
            AudioQuality.HIGH -> {
                tierSpec?.bitrateKbps?.takeIf { it in 220..400 }
                    ?: if (song.actualPlayQuality == AudioQuality.HIGH && song.bitrateKbps in 220..400) song.bitrateKbps else 320
            }
            AudioQuality.STANDARD -> {
                tierSpec?.bitrateKbps?.takeIf { it in 64..200 }
                    ?: if (song.actualPlayQuality == AudioQuality.STANDARD && song.bitrateKbps in 64..200) song.bitrateKbps else 128
            }
        }

        val extUpper = (tierSpec?.extName?.takeIf { it.isNotBlank() } ?: when (activeQuality) {
            AudioQuality.HI_RES, AudioQuality.LOSSLESS -> "flac"
            AudioQuality.HIGH -> "mp3"
            AudioQuality.STANDARD -> "ogg"
        }).uppercase()

        val sampleRate = when (activeQuality) {
            AudioQuality.HI_RES -> when {
                resolvedKbps >= 3200 -> "192.0 kHz / 24 bit"
                resolvedKbps >= 1850 -> "96.0 kHz / 24 bit"
                else -> "48.0 kHz / 24 bit"
            }
            AudioQuality.LOSSLESS -> if (resolvedKbps >= 1020) "48.0 kHz / 16 bit" else "44.1 kHz / 16 bit"
            AudioQuality.HIGH -> "44.1 kHz / 16 bit ($extUpper)"
            AudioQuality.STANDARD -> "44.1 kHz / 16 bit ($extUpper)"
        }

        val qualityDesc = when (activeQuality) {
            AudioQuality.HI_RES -> "Hi-Res 高解析度无损 ($extUpper ${sampleRate.substringBefore(" /")} / 24bit)"
            AudioQuality.LOSSLESS -> "SQ 无损音质 ($extUpper ${sampleRate.substringBefore(" /")} / 16bit)"
            AudioQuality.HIGH -> "HQ 高品音质 (${resolvedKbps}kbps $extUpper)"
            AudioQuality.STANDARD -> "标准音质 (${resolvedKbps}kbps $extUpper)"
        }

        val fileSizeStr = if (resolvedSizeBytes > 0L) {
            String.format(java.util.Locale.getDefault(), "%.2f MB", resolvedSizeBytes / (1024.0 * 1024.0))
        } else "未知大小"

        val targetHash = (tierSpec?.hash?.takeIf { it.isNotBlank() }
            ?: song.hashForQuality(activeQuality).ifBlank { song.hash }).uppercase()

        val pubDate = song.publishDate.ifBlank {
            cachedMeta?.publishDate.orEmpty().ifBlank {
                albumPublishDateCache[song.albumId.trim()].orEmpty()
            }
        }
        val companyName = cachedMeta?.company.orEmpty().ifBlank {
            albumCompanyCache[song.albumId.trim()].orEmpty()
        }.ifBlank { "正版数字曲库" }
        val albumTitle = song.album.ifBlank { cachedMeta?.albumName.orEmpty() }.ifBlank { "单曲发行" }

        return SongDetailInfo(
            song = song,
            albumName = albumTitle,
            singerName = song.artist,
            publishTime = pubDate.ifBlank { "未知日期" },
            company = companyName,
            audioQuality = qualityDesc,
            sampleRate = sampleRate,
            bitRate = "$resolvedKbps kbps",
            fileSize = fileSizeStr,
            fileHash = targetHash
        )
    }

    /**
     * 异步实时补全单曲完整技术参数与唱片公司/发行时间详情
     * 对接 MakcRe/KuGouMusicApi:
     * 1. /v2/get_res_privilege/lite (module/privilege_lite.js) 获取各档位精确 filesize, bitrate, extname 与 hash
     * 2. /kmr/v2/albums (module/album.js) 与 /kmr/v2/audio (module/krm_audio.js) 获取 publish_company 与 publish_date
     */
    suspend fun fetchSongDetail(song: Song): SongDetailInfo = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val session = accounts.context()?.session
        val enrichedSongDef = async {
            runCatching { enrichSongsPrivilegeLite(listOf(song)).firstOrNull() }.getOrNull() ?: song
        }
        // 对接 MakcRe/KuGouMusicApi module/krm_audio.js (/kmr/v2/audio, KG-TID: 238) 实时补全专辑、发行公司与发行时间
        val krmMetaDef = async {
            val entityId = song.albumAudioId.takeIf { it > 0L } ?: song.audioId.takeIf { it > 0L } ?: 0L
            if (entityId > 0L) {
                runCatching {
                    val dataArr = JsonArray().apply {
                        add(JsonObject().apply { addProperty("entity_id", entityId) })
                    }
                    val body = JsonObject().apply {
                        add("data", dataArr)
                        addProperty("fields", "base,album_info,audio_info")
                    }
                    val resp = transport.callAdvanced(
                        path = "/kmr/v2/audio",
                        method = "POST",
                        session = session,
                        body = body,
                        headers = mapOf("x-router" to "openapi.kugou.com", "KG-TID" to "238"),
                        sign = true,
                        includeDefaultParams = true
                    )
                    resp.data.arr("data")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
                }.getOrNull()
            } else null
        }

        val enrichedBase = enrichedSongDef.await()
        val krmObj = krmMetaDef.await()
        val krmBase = krmObj?.obj("base")
        val krmAlbum = krmObj?.obj("album_info")
        val krmAudio = krmObj?.obj("audio_info")

        val resolvedAlbumId = enrichedBase.albumId.ifBlank {
            (krmAlbum?.text("album_id") ?: krmBase?.text("album_id") ?: "").takeIf { it != "0" }.orEmpty()
        }
        val alb = if (resolvedAlbumId.isNotBlank() && resolvedAlbumId != "0") {
            runCatching { albumDetail(resolvedAlbumId) }.getOrNull()
        } else null

        val krmAlbumName = (krmAlbum?.text("album_name") ?: krmBase?.text("album_name") ?: "").trim()
        val krmPubDate = normalizePublishDate(
            krmAlbum?.text("publish_date") ?: krmBase?.text("publish_date") ?: krmAudio?.text("publish_date")
        )
        val krmCompany = (krmAlbum?.text("publish_company") ?: krmAlbum?.text("company")
            ?: krmBase?.text("publish_company") ?: krmBase?.text("company") ?: "").trim()
        val krmDurMs = (krmAudio?.text("duration")?.toLongOrNull() ?: krmAudio?.text("timelength")?.toLongOrNull() ?: 0L).let {
            if (it in 1L until 10000L) it * 1000L else it
        }

        val finalAlbumName = enrichedBase.album.ifBlank { alb?.name.orEmpty().ifBlank { krmAlbumName } }
        val finalPubDate = enrichedBase.publishDate.ifBlank { normalizePublishDate(alb?.publishDate).ifBlank { krmPubDate } }
        val finalCompany = alb?.company.orEmpty().ifBlank { krmCompany }
        val finalDurMs = enrichedBase.durationMs.takeIf { it > 0L } ?: krmDurMs

        if (resolvedAlbumId.isNotBlank()) {
            if (finalPubDate.isNotBlank()) albumPublishDateCache[resolvedAlbumId] = finalPubDate
            if (finalCompany.isNotBlank()) albumCompanyCache[resolvedAlbumId] = finalCompany
        }
        cacheSongTechMeta(
            keys = listOf(enrichedBase.id, enrichedBase.hash, enrichedBase.hash128, enrichedBase.hash320, enrichedBase.hashFlac, enrichedBase.hashHiRes),
            meta = SongTechMeta(
                albumName = finalAlbumName,
                publishDate = finalPubDate,
                company = finalCompany,
                durationMs = finalDurMs
            )
        )

        val cachedSpecs = listOf(enrichedBase.id, enrichedBase.hash, enrichedBase.hash128, enrichedBase.hash320, enrichedBase.hashFlac, enrichedBase.hashHiRes)
            .map { it.trim().lowercase() }
            .firstNotNullOfOrNull { if (it.isNotBlank()) songTechMetaCache[it]?.specs else null }
            .orEmpty()

        val enrichedSong = enrichedBase.let { s ->
            val playQ = song.actualPlayQuality
            if (playQ != null) {
                val matchedSpec = cachedSpecs[playQ]
                s.copy(
                    actualPlayQuality = playQ,
                    bitrateKbps = matchedSpec?.bitrateKbps?.takeIf { it > 0 } ?: song.bitrateKbps.takeIf { it > 0 } ?: s.bitrateKbps,
                    fileSizeByte = matchedSpec?.sizeBytes?.takeIf { it > 0L } ?: song.fileSizeByte.takeIf { it > 0L } ?: s.fileSizeByte,
                    album = finalAlbumName,
                    albumId = resolvedAlbumId,
                    publishDate = finalPubDate,
                    durationMs = finalDurMs
                )
            } else {
                s.copy(
                    album = finalAlbumName,
                    albumId = resolvedAlbumId,
                    publishDate = finalPubDate,
                    durationMs = finalDurMs
                )
            }
        }
        songDetail(enrichedSong)
    }

    private fun defaultRanks(): List<RankItem> = listOf(
        RankItem(6666, "酷狗飙升榜", "http://imge.kugou.com/commendpic/20210729/20210729175317789524.jpg", "每天更新", listOf("1. 瞬 - 告五人", "2. 奢香夫人 - 凤凰传奇", "3. 离别开出花 - 就是南方凯"), 1, "官方热榜", 100),
        RankItem(8888, "酷狗TOP500", "http://imge.kugou.com/commendpic/20210729/20210729175249561655.jpg", "每天更新", listOf("1. 晴天 - 周杰伦", "2. 爱在西元前 - 周杰伦", "3. 篇章 - 张韶涵"), 1, "官方热榜", 500),
        RankItem(23784, "网络红歌榜", "http://imge.kugou.com/commendpic/20210729/20210729175344315250.jpg", "每天更新", listOf("1. 悬溺 - 葛东琪", "2. 若把你 - Kirsty刘瑾睿", "3. 晚风告白 - 星野"), 1, "官方热榜", 100),
        RankItem(31308, "华语新歌榜", "http://imge.kugou.com/commendpic/20210729/20210729175317789524.jpg", "每天更新", listOf("1. 向云端 - 小霞", "2. 笼 - 张碧晨", "3. 想见你想见你想见你 - 八三夭"), 1, "官方热榜", 100),
        RankItem(24971, "粤语金曲榜", "http://imge.kugou.com/commendpic/20210729/20210729175409477029.jpg", "每周更新", listOf("1. 富士山下 - 陈奕迅", "2. 月半小夜曲 - 李克勤", "3. 海阔天空 - Beyond"), 2, "特色曲风", 100),
        RankItem(33162, "欧美金曲榜", "http://imge.kugou.com/commendpic/20210729/20210729175249561655.jpg", "每周更新", listOf("1. Cruel Summer - Taylor Swift", "2. Counting Stars - OneRepublic", "3. Flowers - Miley Cyrus"), 2, "特色曲风", 100),
        RankItem(4681, "美国BillBoard榜", "http://imge.kugou.com/commendpic/20210729/20210729175344315250.jpg", "每周更新", listOf("1. Die With A Smile - Lady Gaga", "2. Birds of a Feather - Billie Eilish", "3. Espresso - Sabrina Carpenter"), 4, "全球权威", 100),
        RankItem(4673, "日本公信榜", "http://imge.kugou.com/commendpic/20210729/20210729175409477029.jpg", "每周更新", listOf("1. Idol - YOASOBI", "2. Lemon - 米津玄师", "3. 怪兽的花呗 - Vaundy"), 4, "全球权威", 100)
    )

    private fun defaultHotKeywords(): List<String> = listOf(
        "周杰伦", "林俊杰", "陈奕迅", "邓紫棋", "告五人", "陶喆", "薛之谦", "毛不易", "张杰", "五月天"
    )

    private fun parseSongs(root: JsonObject): List<Song> {
        val arrayKeys = listOf(
            "songs", "songlist", "song_list", "info", "data", "files", "lists", "list",
            "audio_list", "audios", "recommend_list", "song_info_list", "album_audio_list",
            "card_list", "module_list", "items", "rows", "result", "newsong_list", "publish_list"
        )
        val collectedObjects = mutableListOf<JsonObject>()

        fun isLikelySongObject(obj: JsonObject): Boolean {
            return obj.has("hash") || obj.has("FileHash") || obj.has("hash_128") || obj.has("hash_320") ||
                obj.has("audio_info") || obj.has("song_info") || obj.has("base") ||
                obj.has("songname") || obj.has("song_name") || obj.has("SongName") ||
                obj.has("audio_name") || obj.has("filename") || obj.has("FileName") ||
                obj.has("OriSongName") || (obj.has("name") && (obj.has("audio_id") || obj.has("album_audio_id") || obj.has("mixsongid") || obj.has("singername") || obj.has("author_name") || obj.has("relate_goods")))
        }

        fun extractFromElement(el: com.google.gson.JsonElement?, depth: Int) {
            if (el == null || depth > 4) return
            if (el.isJsonArray) {
                for (item in el.asJsonArray) {
                    if (item.isJsonObject) {
                        val obj = item.asJsonObject
                        if (isLikelySongObject(obj)) {
                            collectedObjects.add(obj)
                        } else {
                            extractFromElement(obj, depth + 1)
                        }
                    }
                }
            } else if (el.isJsonObject) {
                val obj = el.asJsonObject
                var foundArray = false
                for (k in arrayKeys) {
                    val arr = obj.arr(k)
                    if (arr != null && arr.size() > 0) {
                        foundArray = true
                        extractFromElement(arr, depth + 1)
                    }
                }
                if (!foundArray) {
                    for ((k, v) in obj.entrySet()) {
                        if (k != "relate_goods" && k != "authors" && k != "Singers" && k != "singerinfo" && k != "trans_param") {
                            if (v.isJsonObject || v.isJsonArray) {
                                extractFromElement(v, depth + 1)
                            }
                        }
                    }
                }
            }
        }

        extractFromElement(root, 0)
        return collectedObjects
            .map { parseSong(it) }
            .filter { (it.hash.isNotBlank() || it.id.isNotBlank()) && it.title != "未知歌曲" }
    }

    internal fun parseSong(o: JsonObject): Song {
        val replaceObj = o.obj("replace") ?: o.obj("replace_info")
            ?: o.arr("replace")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
        val replaceAudioInfo = replaceObj?.obj("audio_info") ?: replaceObj
        val base = o.obj("base") ?: o.obj("song")?.obj("base") ?: replaceObj?.obj("base")
        val audioInfo = o.obj("audio_info") ?: o.obj("audio") ?: o.obj("song_info") ?: o.obj("song") ?: o.obj("track") ?: replaceAudioInfo
        val albumInfo = o.obj("album_info") ?: o.obj("albuminfo") ?: audioInfo?.obj("album_info") ?: replaceObj?.obj("album_info")
        val transParam = o.obj("trans_param") ?: audioInfo?.obj("trans_param") ?: base?.obj("trans_param")
        val deprecated = o.obj("deprecated") ?: audioInfo?.obj("deprecated")

        val oriSongName = o.text("OriSongName") ?: o.text("ori_song_name")
        val suffix = o.text("Suffix") ?: o.text("suffix")
        val combinedOriTitle = if (!oriSongName.isNullOrBlank()) {
            if (!suffix.isNullOrBlank() && !oriSongName.contains(suffix)) "$oriSongName $suffix" else oriSongName
        } else null

        var title = combinedOriTitle
            ?: o.text("songname") ?: o.text("song_name") ?: o.text("SongName")
            ?: base?.text("audio_name") ?: o.text("audio_name")
            ?: audioInfo?.text("audio_name") ?: audioInfo?.text("songname")
            ?: o.text("name") ?: ""

        val authorsList = listOfNotNull(
            o.objects("authors").takeIf { it.isNotEmpty() },
            base?.objects("authors")?.takeIf { it.isNotEmpty() },
            o.objects("Singers").takeIf { it.isNotEmpty() },
            o.objects("singerinfo").takeIf { it.isNotEmpty() }
        ).firstOrNull().orEmpty()

        val joinedAuthors = authorsList.mapNotNull {
            it.text("author_name") ?: it.text("name") ?: it.text("singername")
        }.filter { it.isNotBlank() }.joinToString("、")

        var artist = o.text("singername") ?: o.text("singer_name") ?: o.text("author_name") ?: o.text("SingerName")
            ?: base?.text("author_name") ?: audioInfo?.text("author_name") ?: audioInfo?.text("singername")
            ?: joinedAuthors.takeIf { it.isNotBlank() }
            ?: ""

        val artistId = (o.text("AuthorId") ?: o.text("author_id") ?: o.text("SingerId") ?: o.text("singerid")
            ?: base?.text("author_id")
            ?: authorsList.firstOrNull()?.let { it.text("author_id") ?: it.text("id") ?: it.text("singerid") }
            ?: "").takeIf { it != "0" }.orEmpty()

        val rawFilename = o.text("filename") ?: o.text("FileName")
            ?: base?.text("filename") ?: audioInfo?.text("filename") ?: ""
        if (title.isBlank() && rawFilename.contains("-")) {
            val parts = rawFilename.split("-", limit = 2)
            if (artist.isBlank() || artist == "未知歌手" || artist == "群星") artist = parts[0].trim()
            title = parts[1].trim()
        }
        if (title.isBlank()) title = rawFilename.ifBlank { "未知歌曲" }

        if (title.contains(" - ")) {
            val parts = title.split(" - ", limit = 2)
            val prefix = parts[0].trim()
            val cleanTitle = parts[1].trim()
            if (artist.isBlank() || artist == "群星" || artist == "未知歌手" || artist.equals(prefix, ignoreCase = true) || prefix.contains(artist)) {
                if (artist.isBlank() || artist == "群星" || artist == "未知歌手") {
                    artist = prefix
                }
                title = cleanTitle
            }
        }

        title = title.replace("<em>", "").replace("</em>", "").trim()
        artist = artist.replace("<em>", "").replace("</em>", "").trim()
        if (artist.isBlank()) artist = "群星"

        val album = (o.text("album_name") ?: o.text("AlbumName")
            ?: albumInfo?.text("album_name") ?: albumInfo?.text("name")
            ?: base?.text("album_name") ?: "")
            .replace("<em>", "").replace("</em>", "").trim()

        val albumId = (o.text("album_id") ?: o.text("AlbumID")
            ?: base?.text("album_id")
            ?: albumInfo?.text("album_id") ?: albumInfo?.text("id")
            ?: deprecated?.text("album_id") ?: "")
            .takeIf { it != "0" }.orEmpty()

        val coverRaw = o.text("Image") ?: o.text("image") ?: o.text("cover") ?: o.text("Cover")
            ?: o.text("album_sizable_cover") ?: o.text("sizable_cover") ?: o.text("album_img") ?: o.text("union_cover")
            ?: o.text("pic") ?: o.text("Pic") ?: o.text("img") ?: o.text("Img")
            ?: transParam?.text("union_cover") ?: transParam?.text("sizable_cover")
            ?: albumInfo?.text("sizable_cover") ?: albumInfo?.text("cover") ?: albumInfo?.text("img") ?: albumInfo?.text("album_img")
            ?: base?.text("sizable_cover") ?: base?.text("cover") ?: base?.text("img")
            ?: audioInfo?.text("sizable_cover") ?: audioInfo?.text("cover") ?: audioInfo?.text("img")
        val cover = imageUrl(coverRaw)

        val durationRaw = o.text("duration") ?: o.text("timelen") ?: o.text("time_length") ?: o.text("timelength") ?: o.text("Duration")
            ?: audioInfo?.text("duration") ?: audioInfo?.text("timelength") ?: audioInfo?.text("timelen")
            ?: base?.text("duration")
        val durationMs = durationRaw?.toLongOrNull()?.let { if (it in 1 until 10000) it * 1000 else it } ?: 0L

        val privSubObj = o.obj("privilege") ?: audioInfo?.obj("privilege") ?: base?.obj("privilege")
        val hasRelateVip = (o.objects("relate_goods") + audioInfo?.objects("relate_goods").orEmpty() +
            base?.objects("relate_goods").orEmpty() + replaceObj?.objects("relate_goods").orEmpty()).any { rg ->
            val rgInfo = rg.obj("info")
            val lvl = rg.number("level") ?: rgInfo?.number("level") ?: -1
            val p = rg.number("privilege") ?: rgInfo?.number("privilege") ?: -1
            val pt = rg.number("pay_type") ?: rgInfo?.number("pay_type") ?: -1
            val fp = rg.number("fail_process") ?: rgInfo?.number("fail_process") ?: -1
            lvl in 1..6 && (p == 10 || pt == 3 || fp == 4)
        }
        val isVip = o.number("is_vip") == 1 ||
            o.number("Privilege") == 10 || o.number("privilege") == 10 ||
            o.number("pay_type") == 3 || o.number("PayType") == 3 ||
            o.number("fail_process") == 4 || o.number("FailProcess") == 4 ||
            audioInfo?.number("is_vip") == 1 ||
            audioInfo?.number("privilege") == 10 ||
            audioInfo?.number("privilege_128") == 10 ||
            audioInfo?.number("privilege_320") == 10 ||
            audioInfo?.number("privilege_flac") == 10 ||
            audioInfo?.number("privilege_high") == 10 ||
            audioInfo?.number("pay_type") == 3 ||
            audioInfo?.number("fail_process") == 4 ||
            replaceAudioInfo?.number("privilege") == 10 ||
            replaceAudioInfo?.number("privilege_128") == 10 ||
            replaceAudioInfo?.number("privilege_320") == 10 ||
            replaceAudioInfo?.number("privilege_flac") == 10 ||
            replaceAudioInfo?.number("privilege_high") == 10 ||
            replaceAudioInfo?.number("pay_type") == 3 ||
            replaceAudioInfo?.number("fail_process") == 4 ||
            base?.number("is_vip") == 1 ||
            base?.number("privilege") == 10 ||
            base?.number("pay_type") == 3 ||
            privSubObj?.number("privilege") == 10 ||
            privSubObj?.number("pay_type") == 3 ||
            transParam?.number("pay_block_tpl") == 1 ||
            hasRelateVip

        val audioId = (o.text("audio_id") ?: o.text("Audioid") ?: o.text("songid")
            ?: base?.text("audio_id") ?: audioInfo?.text("audio_id") ?: o.text("mixsongid"))?.toLongOrNull() ?: 0L
        val albumAudioId = (o.text("album_audio_id") ?: o.text("AlbumAudioId") ?: o.text("mixsongid") ?: o.text("scid")
            ?: base?.text("album_audio_id") ?: audioInfo?.text("album_audio_id") ?: o.text("id"))?.toLongOrNull() ?: audioId

        // 严格对齐 MakcRe/KuGouMusicApi module/privilege_lite.js 的 relate_goods 等级规范：
        // level: 1/2 = 128k, 4 = 320k(HQ), 5 = flac(SQ 无损), 6 = Hi-Res, 0/7 = 正式版酷狗 suvip 蝰蛇/多轨合成音效（非概念版音质）
        var relateResHash = ""
        var relateSqHash = ""
        var relateHqHash = ""
        var relateStdHash = ""
        var relateAccHash = ""
        var relateResSize = 0L
        var relateSqSize = 0L
        var relateHqSize = 0L
        var relateStdSize = 0L
        var effectiveDurMs = durationMs
        val allRelateGoods = o.objects("relate_goods") + audioInfo?.objects("relate_goods").orEmpty() +
            base?.objects("relate_goods").orEmpty() + replaceObj?.objects("relate_goods").orEmpty()

        for (rg in allRelateGoods) {
            val rgInfo = rg.obj("info")
            val durRaw = (rg.number("duration") ?: rgInfo?.number("duration") ?: rgInfo?.number("timelength") ?: 0).toLong()
            if (effectiveDurMs <= 0L && durRaw > 0L) {
                effectiveDurMs = if (durRaw in 1 until 10000) durRaw * 1000L else durRaw
            }
            val levelNum = rg.number("level") ?: rgInfo?.number("level") ?: -1
            val qStr = (rg.text("quality") ?: rg.text("type") ?: rgInfo?.text("quality") ?: "").lowercase()
            val sz = (rg.text("size")?.toLongOrNull() ?: rg.text("filesize")?.toLongOrNull()
                ?: rg.text("file_size")?.toLongOrNull() ?: rgInfo?.text("filesize")?.toLongOrNull()
                ?: rgInfo?.text("size")?.toLongOrNull() ?: 0L)
            if ((levelNum == 5 || qStr.contains("flac") || qStr.contains("sq") || qStr.contains("lossless")) && sz > relateSqSize) {
                relateSqSize = sz
            }
        }

        for (rg in allRelateGoods) {
            val h = rg.text("hash")?.takeIf { isValidHash(it) } ?: continue
            val rgInfo = rg.obj("info")
            val levelNum = rg.number("level") ?: rgInfo?.number("level") ?: -1
            val qStr = (rg.text("quality") ?: rg.text("type") ?: rgInfo?.text("quality") ?: "").lowercase()
            val nameStr = (rg.text("name") ?: rg.text("extname") ?: rgInfo?.text("extname") ?: "").lowercase()
            val sz = (rg.text("size")?.toLongOrNull() ?: rg.text("filesize")?.toLongOrNull()
                ?: rg.text("file_size")?.toLongOrNull() ?: rgInfo?.text("filesize")?.toLongOrNull()
                ?: rgInfo?.text("size")?.toLongOrNull() ?: 0L)
            val brRaw = (rg.number("bitrate") ?: rg.number("bitRate")
                ?: rgInfo?.number("bitrate") ?: rgInfo?.number("bitRate") ?: 0).toLong()
            val reportedKbps = if (brRaw > 50000L) (brRaw / 1000L).toInt() else brRaw.toInt()

            when {
                qStr.contains("accompany") || qStr.contains("伴奏") || nameStr.contains("伴奏") -> {
                    if (relateAccHash.isBlank()) relateAccHash = h
                }
                // 跳过正统酷狗 suvip 专属蝰蛇母带/全景声/超清/多轨合成流
                levelNum == 7 || qStr.startsWith("viper") || qStr == "super" || qStr == "multitrack" ||
                    nameStr.contains("蝰蛇") || nameStr.contains("全景") ||
                    (levelNum == 0 && qStr !in listOf("128", "320", "flac", "high", "sq", "hires", "hi-res", "res")) -> {
                    // Do not mark synthetic Viper / DSD streams as Concept Hi-Res
                }
                // 对齐 MakcRe/KuGouMusicApi privilege_lite.js: level == 6 && quality == "high" 为酷狗概念版官方 Hi-Res
                levelNum == 6 || qStr == "hires" || qStr == "hi-res" || qStr == "res" || (qStr == "high" && (levelNum == 6 || levelNum == 0)) -> {
                    val isDistinctFromSq = relateSqSize <= 0L || sz <= 0L ||
                        sz >= (relateSqSize * 1.08).toLong() ||
                        reportedKbps >= 1100
                    val isVerifiedHiRes = (sz <= 0L || sz >= 7_500_000L) &&
                        (relateSqSize <= 0L || sz <= 0L || sz > relateSqSize) &&
                        isDistinctFromSq
                    if (isVerifiedHiRes) {
                        if (relateResHash.isBlank()) relateResHash = h
                        if (relateResSize <= 0L && sz > 0L) relateResSize = sz
                    } else if (sz >= 4_000_000L) {
                        if (relateSqHash.isBlank()) relateSqHash = h
                        if (relateSqSize <= 0L && sz > 0L) relateSqSize = sz
                    } else if (sz in 2_500_000L until 4_000_000L && relateHqHash.isBlank()) {
                        relateHqHash = h
                        relateHqSize = sz
                    }
                }
                // 真 SQ 无损 (FLAC): level == 5 或明确标记为 flac / sq / lossless
                levelNum == 5 || qStr.contains("flac") || qStr.contains("sq") || qStr.contains("lossless") || qStr.contains("ape") -> {
                    if (relateSqHash.isBlank()) relateSqHash = h
                    if (relateSqSize <= 0L && sz > 0L) relateSqSize = sz
                }
                // HQ 320K: level == 4 或标记为 320 / hq / high(非level 6)
                levelNum == 4 || qStr.contains("320") || qStr.contains("hq") || (qStr == "high" && levelNum != 6 && levelNum != 0) -> {
                    if (relateHqHash.isBlank()) relateHqHash = h
                    if (relateHqSize <= 0L && sz > 0L) relateHqSize = sz
                }
                // 标准 128K: level == 1 / 2 或标记为 128
                levelNum == 1 || levelNum == 2 || qStr.contains("128") -> {
                    if (relateStdHash.isBlank()) relateStdHash = h
                    if (relateStdSize <= 0L && sz > 0L) relateStdSize = sz
                }
            }
        }

        // 提取全格式品质特征码与文件大小（严禁将 hash_super / SuperFileHash 蝰蛇合成流当成原生 Hi-Res！）
        val topExplicitResSize = (o.number("ResFileSize") ?: o.number("HiResFileSize") ?: o.number("hiresfilesize")
            ?: o.number("filesize_res") ?: audioInfo?.number("filesize_res") ?: replaceAudioInfo?.number("filesize_res") ?: 0).toLong()

        val sqSize = (o.number("SQFileSize") ?: o.number("sqfilesize") ?: o.number("filesize_flac")
            ?: o.number("filesize_sq")
            ?: audioInfo?.number("filesize_flac") ?: audioInfo?.number("sqfilesize")
            ?: replaceAudioInfo?.number("filesize_flac")
            ?: relateSqSize.takeIf { it > 0 } ?: 0).toLong()

        val hqSize = (o.number("HQFileSize") ?: o.number("320filesize") ?: o.number("filesize_320")
            ?: o.number("filesize_hq")
            ?: audioInfo?.number("filesize_320") ?: audioInfo?.number("320filesize")
            ?: replaceAudioInfo?.number("filesize_320")
            ?: transParam?.number("ogg_320_filesize")
            ?: relateHqSize.takeIf { it > 0 } ?: 0).toLong()

        val stdSize = (o.number("FileSize") ?: o.number("filesize") ?: o.number("filesize_128")
            ?: audioInfo?.number("filesize_128") ?: audioInfo?.number("filesize")
            ?: replaceAudioInfo?.number("filesize_128")
            ?: transParam?.number("ogg_128_filesize")
            ?: relateStdSize.takeIf { it > 0 } ?: 0).toLong()

        val sqHash = o.text("SQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("sqhash")?.takeIf { isValidHash(it) }
            ?: o.text("sq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: o.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("sqhash")?.takeIf { isValidHash(it) }
            ?: relateSqHash

        val explicitHqHash = o.text("HQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("320hash")?.takeIf { isValidHash(it) }
            ?: o.text("hqhash")?.takeIf { isValidHash(it) }
            ?: o.text("hq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_320")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("320hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hqhash")?.takeIf { isValidHash(it) }

        // 在 KMR 接口 (artist_audios_new.js / artist_audios.js / krm_audio.js / album_songs.js) 中，audio_info.hash_high 即为官方 Hi-Res (quality="high")
        val audioInfoHighSize = (replaceAudioInfo?.text("filesize_high")?.toLongOrNull()
            ?: audioInfo?.text("filesize_high")?.toLongOrNull()
            ?: o.text("filesize_high")?.toLongOrNull() ?: 0L)
        val audioInfoHighHash = (replaceAudioInfo?.text("hash_high")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hash_hires")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hires_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_high")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_hires")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hires_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_high")?.takeIf { isValidHash(it) }
            ?: o.text("hash_hires")?.takeIf { isValidHash(it) })
        val isKmrHighActuallyHiRes = audioInfoHighHash != null &&
            !audioInfoHighHash.equals(sqHash, ignoreCase = true) &&
            !audioInfoHighHash.equals(explicitHqHash, ignoreCase = true) &&
            (audioInfoHighSize <= 0L || (audioInfoHighSize >= 7_500_000L && (sqSize <= 0L || audioInfoHighSize > sqSize)))

        val explicitTopResHash = o.text("ResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("HiResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("res_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hires_hash")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hash_res")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_res")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("res_hash")?.takeIf { isValidHash(it) }

        val verifiedExplicitResHash = explicitTopResHash?.takeIf {
            ((topExplicitResSize <= 0L && sqSize <= 0L && o.number("ResFileSize") == null) || (topExplicitResSize >= 7_500_000L && (sqSize <= 0L || topExplicitResSize > sqSize)))
        }

        val resHashCandidate = verifiedExplicitResHash
            ?: (if (isKmrHighActuallyHiRes) audioInfoHighHash else null)
            ?: relateResHash.takeIf {
                it.isNotBlank() && ((relateResSize <= 0L && sqSize <= 0L) || (relateResSize >= 7_500_000L && (sqSize <= 0L || relateResSize > sqSize)))
            }.orEmpty()

        val hqHash = explicitHqHash
            ?: (if (!isKmrHighActuallyHiRes) audioInfoHighHash else null)
            ?: o.text("high_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("high_hash")?.takeIf { isValidHash(it) }
            ?: transParam?.text("ogg_320_hash")?.takeIf { isValidHash(it) }
            ?: relateHqHash

        val stdHash = transParam?.text("ogg_128_hash")?.takeIf { isValidHash(it) }
            ?: o.text("FileHash")?.takeIf { isValidHash(it) }
            ?: o.text("hash")?.takeIf { isValidHash(it) }
            ?: o.text("Hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_128")?.takeIf { isValidHash(it) }
            ?: o.text("hash128")?.takeIf { isValidHash(it) }
            ?: o.text("audio_hash")?.takeIf { isValidHash(it) }
            ?: base?.text("hash")?.takeIf { isValidHash(it) }
            ?: base?.text("hash_128")?.takeIf { isValidHash(it) }
            ?: replaceAudioInfo?.text("hash_128")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_128")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("FileHash")?.takeIf { isValidHash(it) }
            ?: deprecated?.text("hash")?.takeIf { isValidHash(it) }
            ?: relateStdHash

        val rawResSize = if (topExplicitResSize > 0L) topExplicitResSize else relateResSize
        val resSize = if (rawResSize > 0L) rawResSize else if (isKmrHighActuallyHiRes) audioInfoHighSize else 0L

        val resHash = resHashCandidate.takeIf {
            it.isNotBlank() &&
                ((resSize <= 0L && sqSize <= 0L && o.number("ResFileSize") == null) || (resSize >= 7_500_000L && (sqSize <= 0L || resSize > sqSize))) &&
                !it.equals(sqHash, ignoreCase = true) &&
                !it.equals(hqHash, ignoreCase = true) &&
                !it.equals(stdHash, ignoreCase = true)
        }.orEmpty()

        val hasHiRes = resHash.isNotBlank()

        val hasFlac = sqHash.isNotBlank() || sqSize > 0L || o.number("is_flac") == 1 || o.number("is_lossless") == 1
            || o.text("has_flac") == "1" || o.text("quality")?.contains("flac", ignoreCase = true) == true

        val has320 = hqHash.isNotBlank() || hqSize > 0L || o.text("has_320") == "1" || o.number("is_320") == 1
            || o.text("quality")?.contains("320") == true

        val qualities = mutableListOf<AudioQuality>()
        qualities.add(AudioQuality.STANDARD)
        if (has320 || hasFlac || hasHiRes) {
            qualities.add(AudioQuality.HIGH)
        }
        if (hasFlac || hasHiRes) {
            qualities.add(AudioQuality.LOSSLESS)
        }
        if (hasHiRes) {
            qualities.add(AudioQuality.HI_RES)
        }

        val primaryHash = when {
            hasHiRes && resHash.isNotBlank() -> resHash
            hasFlac && sqHash.isNotBlank() -> sqHash
            has320 && hqHash.isNotBlank() -> hqHash
            stdHash.isNotBlank() -> stdHash
            else -> o.text("hash") ?: o.text("FileHash") ?: audioInfo?.text("hash") ?: ""
        }

        val isLossless = hasFlac || hasHiRes
        val maxFileSize = when {
            hasHiRes && resSize > 0L -> resSize
            hasFlac && sqSize > 0L -> sqSize
            has320 && hqSize > 0L -> hqSize
            stdSize > 0L -> stdSize
            else -> 0L
        }

        val accHash = o.text("AccompanyFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("accompany_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("accompany_hash")?.takeIf { isValidHash(it) }
            ?: transParam?.text("ogg_320_accompany_hash")?.takeIf { isValidHash(it) }
            ?: transParam?.text("accompany_hash")?.takeIf { isValidHash(it) }
            ?: relateAccHash

        val isFreePrivilege = !isVip && (
            o.number("privilege") == 0 || o.number("Privilege") == 0 ||
                transParam?.number("free_for_vip") == 1 || o.number("fail_process") == 0
            )

        val fileId = o.text("fileid")?.toLongOrNull() ?: o.text("file_id")?.toLongOrNull() ?: 0L
        val rawPublishDate = o.text("publish_date") ?: o.text("publish_time") ?: o.text("publishtime")
            ?: o.text("PublishDate") ?: o.text("PublishTime") ?: o.text("release_date")
            ?: o.text("online_time") ?: o.text("inlist_time")
            ?: base?.text("publish_date") ?: base?.text("publish_time")
            ?: audioInfo?.text("publish_date") ?: audioInfo?.text("publish_time") ?: audioInfo?.text("publishtime")
            ?: albumInfo?.text("publish_date") ?: albumInfo?.text("publishtime") ?: albumInfo?.text("publish_time")
            ?: transParam?.text("publish_date") ?: transParam?.text("publish_time")
            ?: deprecated?.text("publish_date")
        val publishDate = normalizePublishDate(rawPublishDate)

        val finalDurMs = effectiveDurMs.takeIf { it > 0L } ?: durationMs
        val estimatedKbps = when {
            finalDurMs >= 30_000L && maxFileSize > 0L -> ((maxFileSize * 8L) / finalDurMs).toInt()
            hasHiRes -> 2304
            hasFlac -> 920
            has320 -> 320
            else -> 128
        }
        val isStdOgg = transParam?.text("ogg_128_hash")?.equals(stdHash, ignoreCase = true) == true
        val parsedSpecs = buildMap {
            if (stdHash.isNotBlank() || stdSize > 0L) {
                put(AudioQuality.STANDARD, SongStreamSpec(stdHash.ifBlank { primaryHash }, stdSize, 128, if (isStdOgg) "ogg" else "mp3"))
            }
            if (hqHash.isNotBlank() || hqSize > 0L) {
                put(AudioQuality.HIGH, SongStreamSpec(hqHash, hqSize, 320, "mp3"))
            }
            if (sqHash.isNotBlank() || sqSize > 0L) {
                val sqK = if (finalDurMs >= 30_000L && sqSize > 0L) ((sqSize * 8L) / finalDurMs).toInt() else 920
                put(AudioQuality.LOSSLESS, SongStreamSpec(sqHash, sqSize, sqK, "flac"))
            }
            if (hasHiRes && resHash.isNotBlank()) {
                val resK = if (finalDurMs >= 30_000L && resSize > 0L) ((resSize * 8L) / finalDurMs).toInt() else 2304
                put(AudioQuality.HI_RES, SongStreamSpec(resHash, resSize, resK, "flac"))
            }
        }
        val resolvedSongId = audioId.takeIf { it > 0 }?.toString() ?: primaryHash
        cacheSongTechMeta(
            keys = listOf(resolvedSongId, primaryHash, stdHash, hqHash, sqHash, resHash),
            meta = SongTechMeta(
                specs = parsedSpecs,
                albumName = album,
                publishDate = publishDate,
                durationMs = finalDurMs
            )
        )

        return Song(
            id = resolvedSongId,
            hash = primaryHash,
            hash128 = stdHash,
            hash320 = hqHash,
            hashFlac = sqHash,
            hashHiRes = resHash,
            accompanyHash = accHash,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            coverUrl = cover,
            durationMs = finalDurMs,
            isVip = isVip,
            isFreePrivilege = isFreePrivilege,
            isLossless = isLossless,
            isHiRes = hasHiRes,
            playUrl = o.text("url") ?: "",
            albumAudioId = albumAudioId,
            audioId = audioId,
            quality = if (hasHiRes) "high" else if (hasFlac) "flac" else if (has320) "320" else "128",
            fileId = fileId,
            availableQualities = qualities,
            bitrateKbps = estimatedKbps,
            fileSizeByte = maxFileSize,
            publishDate = publishDate
        )
    }

    suspend fun postSongComment(
        song: Song,
        content: String,
        replyToCommentId: String? = null,
        replyToUserName: String? = null
    ): SongComment {
        val session = accounts.context()?.session
        val profile = accounts.state.value.profile
        val userId = session?.userId ?: "10086"
        val userName = profile?.nickname?.takeIf { it.isNotBlank() }
            ?: session?.userId?.let { "乐迷_$it" }
            ?: "概念版乐迷"
        val userAvatar = profile?.avatarUrl ?: ""
        val commentId = "c_${song.id.ifBlank { "song" }}_${System.currentTimeMillis()}"

        if (session != null && !session.token.isNullOrBlank()) {
            runCatching {
                val mixSongId = song.albumAudioId.takeIf { it > 0 } ?: song.audioId.takeIf { it > 0 } ?: song.id.toLongOrNull() ?: 0L
                val path = if (replyToCommentId != null) "/mcomment/v1/reply_add" else "/mcomment/v1/cmt_add"
                val params = mutableMapOf(
                    "mixsongid" to mixSongId.toString(),
                    "content" to content,
                    "token" to session.token,
                    "userid" to session.userId
                )
                if (replyToCommentId != null) {
                    params["cmtid"] = replyToCommentId
                }
                transport.call(
                    path = path,
                    method = "POST",
                    query = params,
                    session = session
                )
            }
        }

        return SongComment(
            id = commentId,
            userId = userId,
            userName = userName,
            userAvatar = userAvatar,
            content = content,
            timeFormatted = "刚刚",
            likeCount = 0,
            isHot = false,
            vipLevel = if (!session?.vipToken.isNullOrBlank()) 6 else 1,
            ipLocation = "广东",
            parentId = replyToCommentId,
            rootId = replyToCommentId,
            replyUserName = replyToUserName,
            replyCount = 0,
            replies = emptyList()
        )
    }

    /**
     * 对齐 MakcRe/KuGouMusicApi v1.6.0 音效相关接口 (get_mode_info / get_model / playlist_effect)：
     * 获取酷狗蝰蛇音效官方与社区调音师详细配置及实时使用热度
     */
    suspend fun fetchViperSoundEffects(): Map<String, com.concept.music.service.ViperOnlineEffectMeta> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val result = LinkedHashMap<String, com.concept.music.service.ViperOnlineEffectMeta>()
        val session = accounts.context()?.session

        // 1. 尝试调用酷狗音效社区与音效详情网关 (get_model / get_mode_info)
        runCatching {
            val resp = transport.call(
                path = "/v1/get_model",
                session = session,
                query = mapOf("page" to "1", "pagesize" to "30"),
                headers = mapOf("x-router" to "ksyx.kugou.com")
            )
            val list = resp.data.objects("list").ifEmpty { resp.data.objects("info") }
            list.forEach { item ->
                val id = item.text("mode_id") ?: item.text("id") ?: return@forEach
                val name = item.text("name") ?: item.text("mode_name") ?: return@forEach
                val intro = item.text("intro") ?: item.text("desc") ?: ""
                val count = item.number("use_count") ?: item.number("user_count") ?: 0
                val author = item.text("author_name") ?: item.text("username") ?: "酷狗认证调音师"
                val usageStr = if (count >= 100_000_000) String.format("%.1f亿人使用", count / 100_000_000.0)
                else if (count >= 10_000) "${count / 10_000}万人使用"
                else "官方精调"
                result[id] = com.concept.music.service.ViperOnlineEffectMeta(
                    modeId = id,
                    name = name,
                    description = intro,
                    usageText = usageStr,
                    authorName = author,
                    tuningSummary = item.text("param_desc").orEmpty()
                )
            }
        }

        // 2. 补充拉取指定模式详情 (get_mode_info)
        listOf("1001", "1002", "1003", "1004", "1005", "1006").forEach { modeId ->
            if (!result.containsKey(modeId)) {
                runCatching {
                    val infoResp = transport.call(
                        path = "/v1/get_mode_info",
                        session = session,
                        query = mapOf("mode_id" to modeId),
                        headers = mapOf("x-router" to "ksyx.kugou.com")
                    )
                    val d = infoResp.data
                    val name = d.text("name") ?: d.text("mode_name")
                    if (!name.isNullOrBlank()) {
                        result[modeId] = com.concept.music.service.ViperOnlineEffectMeta(
                            modeId = modeId,
                            name = name,
                            description = d.text("intro") ?: d.text("desc") ?: "",
                            usageText = d.text("use_count_str") ?: "官方旗舰",
                            authorName = d.text("author_name") ?: "蝰蛇声学实验室",
                            tuningSummary = d.text("eq_summary") ?: ""
                        )
                    }
                }
            }
        }

        result
    }

    private fun imageUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var url = value.replace("{size}", "400").replace("{param}", "400")
        if (url.startsWith("//")) url = "https:$url"
        return url
    }
}
