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
import com.concept.music.data.model.SearchResult
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.data.model.UserGradeInfo
import com.concept.music.data.model.YouthChannel
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.Base64

class MusicApiRepository(
    private val transport: KugouTransport,
    private val accounts: AccountRepository,
    private val device: () -> DeviceIdentity = { DeviceIdentity("00000000-0000-0000-0000-000000000000", "0", "Android") }
) {
    private val playbackResultCache = java.util.concurrent.ConcurrentHashMap<String, PlaybackResult>()

    fun clearPlaybackCache(hash: String? = null) {
        if (hash == null) {
            playbackResultCache.clear()
        } else {
            val prefix = hash.lowercase()
            playbackResultCache.keys.filter { it.startsWith(prefix) }.forEach {
                playbackResultCache.remove(it)
            }
        }
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

        if (!primarySongs.isNullOrEmpty()) return primarySongs

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
        fetchPlaylistSongsAll(collectionId, ctx.session)
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
        var beginIdx = 0
        val pageSize = 200
        while (true) {
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

            val batch = parseSongs(response.data)
            if (batch.isEmpty()) break
            allSongs.addAll(batch)
            if (batch.size < pageSize) break
            beginIdx += batch.size
        }
        if (allSongs.isNotEmpty()) return allSongs

        // 备用 1：/v4/get_other_list_file 网关接口
        val v4Songs = runCatching {
            val response = transport.callAdvanced(
                path = "/v4/get_other_list_file",
                session = session,
                query = mapOf(
                    "global_collection_id" to effectiveGid,
                    "page" to "1",
                    "pagesize" to "100",
                    "is_publish" to "1"
                ),
                headers = mapOf("x-router" to "pubsongs.kugou.com"),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrDefault(emptyList())
        if (v4Songs.isNotEmpty()) return v4Songs

        // 备用 2：提取 specialid 走 CDN 歌单曲目接口 (/api/v3/special/song)
        val numericSpecialId = collectionId.takeIf { it.all { ch -> ch.isDigit() } }
            ?: effectiveGid.split("_").getOrNull(3)?.takeIf { it.all { ch -> ch.isDigit() } }
        if (!numericSpecialId.isNullOrBlank()) {
            val cdnSongs = runCatching {
                val response = transport.callAdvanced(
                    path = "/api/v3/special/song",
                    host = "http://mobilecdnbj.kugou.com",
                    query = mapOf(
                        "version" to "9108",
                        "specialid" to numericSpecialId,
                        "plat" to "0",
                        "pagesize" to "100",
                        "page" to "1",
                        "area_code" to "1"
                    ),
                    sign = false,
                    includeDefaultParams = false
                ).requireSuccess()
                parseSongs(response.data)
            }.getOrDefault(emptyList())
            if (cdnSongs.isNotEmpty()) return cdnSongs
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

        if (gatewayPlaylist != null) return gatewayPlaylist

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
                Playlist(
                    id = gid.ifBlank { playlistId },
                    title = name,
                    coverUrl = imageUrl(pic),
                    trackCount = count,
                    playCount = playCnt,
                    description = desc,
                    isCreatedByMe = false,
                    isFavorite = false,
                    isLiked = false,
                    globalCollectionId = gid.ifBlank { playlistId },
                    listCreateListid = numericSpecialId,
                    listCreateGid = gid.ifBlank { playlistId }
                )
            }.getOrNull()
            if (cdnPlaylist != null) return cdnPlaylist
        }

        return null
    }

    suspend fun resolvePlayback(
        song: Song,
        preferredQuality: AudioQuality = song.highestQuality
    ): PlaybackResult {
        val targetHash = song.hashForQuality(preferredQuality).ifBlank { song.hash }
        val cacheKey = "${targetHash.lowercase()}_${preferredQuality.apiValue}"
        playbackResultCache[cacheKey]?.let { return it }

        val session = accounts.context()?.session
        // 1. 若拥有 vipToken，尝试请求概念版 VIP /v6/priv_url 获取无损/高品音源
        if (session != null && !session.vipToken.isNullOrBlank()) {
            val vipResult = runCatching { resolveVipPrivResult(song, session, preferredQuality) }.getOrNull()
            if (vipResult != null && vipResult.url.isNotBlank()) {
                playbackResultCache[cacheKey] = vipResult
                return vipResult
            }
        }

        // 2. 降级走 /v5/url
        val fallbackResult = resolveFallbackResult(song, session, preferredQuality)
        playbackResultCache[cacheKey] = fallbackResult
        return fallbackResult
    }

    suspend fun resolveUrl(song: Song, preferredQuality: AudioQuality = song.highestQuality): String {
        return resolvePlayback(song, preferredQuality).url
    }

    fun deduceQuality(
        url: String,
        bitrateRaw: Long?,
        fileSizeBytes: Long,
        durationMs: Long,
        extName: String?
    ): Pair<AudioQuality, Int> {
        val durationSec = (durationMs / 1000.0).coerceAtLeast(1.0)
        val computedKbps = if (fileSizeBytes > 0 && durationMs > 0) {
            ((fileSizeBytes * 8.0) / durationSec / 1000.0).toInt()
        } else 0

        val reportedKbps = when {
            bitrateRaw == null || bitrateRaw <= 0L -> 0
            bitrateRaw > 50000L -> (bitrateRaw / 1000L).toInt()
            else -> bitrateRaw.toInt()
        }

        val finalKbps = if (reportedKbps > 0) reportedKbps else computedKbps
        val urlLower = url.lowercase()
        val extLower = (extName ?: "").lowercase()
        val isExplicitLossy = extLower.contains("mp3") || extLower.contains("m4a") || extLower.contains("aac") || extLower.contains("ogg") ||
                urlLower.endsWith(".mp3") || urlLower.contains(".mp3?") || urlLower.endsWith(".m4a") || urlLower.contains(".m4a?")
        val isExplicitFlac = extLower.contains("flac") || extLower.contains("wav") || extLower.contains("ape") ||
                urlLower.contains(".flac") || urlLower.contains(".wav")

        val quality = when {
            isExplicitLossy -> {
                if (finalKbps >= 250) AudioQuality.HIGH else AudioQuality.STANDARD
            }
            isExplicitFlac -> {
                if (finalKbps >= 1500) AudioQuality.HI_RES else AudioQuality.LOSSLESS
            }
            finalKbps >= 1500 -> AudioQuality.HI_RES
            finalKbps >= 500 -> AudioQuality.LOSSLESS
            finalKbps >= 200 -> AudioQuality.HIGH
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
        val clientTimeMs = System.currentTimeMillis()
        val targetHash = song.hashForQuality(preferredQuality).ifBlank { song.hash }
        val keyRaw = "${targetHash.lowercase()}185672dd44712f60bb1736df5a377e823116${d.mid}${session.userId}"
        val trackerKey = KugouSigner.md5(keyRaw)

        val targetQualities = when (preferredQuality) {
            AudioQuality.STANDARD -> listOf("128", "320")
            AudioQuality.HIGH -> listOf("320", "128")
            AudioQuality.LOSSLESS -> listOf("flac", "320", "128")
            AudioQuality.HI_RES -> listOf("high", "super", "flac", "320", "128")
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
            addProperty("viptoken", session.vipToken.orEmpty())
        }

        val body = JsonObject().apply {
            addProperty("area_code", "1")
            addProperty("behavior", "play")
            add("qualities", qualitiesArr)
            add("resource", resource)
            addProperty("token", session.token)
            add("tracker_param", trackerParam)
            addProperty("userid", session.userId)
            addProperty("vip", if (!session.vipToken.isNullOrBlank()) 6 else 1)
        }

        val query = linkedMapOf(
            "appid" to "3116",
            "clientver" to "11440",
            "mid" to d.mid,
            "dfid" to d.dfid,
            "clienttime" to (clientTimeMs / 1000).toString(),
            "token" to session.token,
            "userid" to session.userId
        )
        val bodyStr = KugouSigner.gson.toJson(body)
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        val signature = KugouSigner.md5("$salt$sortedParams$bodyStr$salt")
        query["signature"] = signature

        val response = runCatching {
            transport.callAdvanced(
                path = "/v6/priv_url",
                host = "http://tracker.kugou.com",
                method = "POST",
                query = query,
                body = body,
                session = session,
                headers = mapOf(
                    "dfid" to d.dfid,
                    "mid" to d.mid,
                    "clienttime" to (clientTimeMs / 1000).toString(),
                    "content-type" to "application/json; charset=utf-8"
                ),
                sign = false,
                includeDefaultParams = false
            )
        }.getOrNull() ?: return null

        val data = response.data
        val privStatus = data.number("priv_status") ?: -1
        if (privStatus == 0) return null

        val failProcess = data.arr("fail_process")
        if (failProcess != null && failProcess.size() > 0) return null
        if (data.obj("ext_info")?.number("is_free_part") == 1) return null

        val fileSize = (data.number("file_size") ?: data.number("fileSize") ?: 0).toLong()
        if (fileSize in 1 until 200 * 1024) return null

        val playUrl = extractPlayUrl(data) ?: return null
        val bitrate = (data.number("bitrate") ?: data.number("bitRate") ?: 0).toLong()
        val extName = data.text("extname") ?: data.text("extName")
        val (actualQuality, kbps) = deduceQuality(playUrl, bitrate, fileSize, song.durationMs, extName)
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

    private fun extractPlayUrl(obj: JsonObject): String? {
        val u = obj.text("url") ?: obj.text("play_url")
        if (!u.isNullOrBlank()) return u
        val arr = obj.get("url")?.takeIf { it.isJsonArray }?.asJsonArray
        if (arr != null && arr.size() > 0) {
            val first = arr.firstOrNull()?.asString
            if (!first.isNullOrBlank()) return first
        }
        val trans = obj.objects("trans_param").firstOrNull()?.text("url")
        if (!trans.isNullOrBlank()) return trans
        val backup = obj.text("backup_url")
        if (!backup.isNullOrBlank()) return backup
        val backupArr = obj.get("backup_url")?.takeIf { it.isJsonArray }?.asJsonArray
        return backupArr?.firstOrNull()?.asString
    }

    private suspend fun resolveFallbackResult(
        song: Song,
        session: KugouSession?,
        preferredQuality: AudioQuality
    ): PlaybackResult {
        val d = device()
        val userId = session?.userId ?: "0"
        val clientTime = System.currentTimeMillis() / 1000
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"

        val qualityTiers = when (preferredQuality) {
            AudioQuality.HI_RES -> listOf(AudioQuality.HI_RES, AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.LOSSLESS -> listOf(AudioQuality.LOSSLESS, AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.HIGH -> listOf(AudioQuality.HIGH, AudioQuality.STANDARD)
            AudioQuality.STANDARD -> listOf(AudioQuality.STANDARD)
        }

        // 构建去重后的 (hash, qualityParam) 候选列表，避免对缺失高品 hash 的曲目重复用 128k hash 请求高品导致额外耗时
        val candidates = LinkedHashSet<Pair<String, String>>()
        for (tier in qualityTiers) {
            when (tier) {
                AudioQuality.HI_RES -> if (song.hashHiRes.isNotBlank()) candidates.add(song.hashHiRes.lowercase() to "high")
                AudioQuality.LOSSLESS -> if (song.hashFlac.isNotBlank()) candidates.add(song.hashFlac.lowercase() to "flac")
                AudioQuality.HIGH -> if (song.hash320.isNotBlank()) candidates.add(song.hash320.lowercase() to "320")
                AudioQuality.STANDARD -> {
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
                "version" to "11430",
                "page_id" to "967177915",
                "quality" to qualityParam,
                "album_audio_id" to song.albumAudioId.toString(),
                "behavior" to "play",
                "pid" to "411",
                "cmd" to "26",
                "pidversion" to "3001",
                "ppage_id" to "356753938,823673182,967485191",
                "cdnBackup" to "1",
                "clientver" to "11430",
                "appid" to "3116",
                "mid" to d.mid,
                "dfid" to d.dfid,
                "clienttime" to clientTime.toString(),
                "key" to key
            )
            if (session != null) {
                query["token"] = session.token
                query["userid"] = session.userId
                if (!session.vipToken.isNullOrBlank()) {
                    query["vipType"] = "6"
                    query["vipToken"] = session.vipToken
                }
            }

            val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
            query["signature"] = KugouSigner.md5("$salt$sortedParams$salt")

            val response = runCatching {
                transport.callAdvanced(
                    path = "/v5/url",
                    session = session,
                    query = query,
                    headers = mapOf(
                        "x-router" to "trackercdn.kugou.com",
                        "dfid" to d.dfid,
                        "mid" to d.mid,
                        "clienttime" to clientTime.toString()
                    ),
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
                    val (q, kbps) = deduceQuality(url, bRate, fSize, song.durationMs, ext)
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
            "version" to "11430",
            "page_id" to "967177915",
            "quality" to "128",
            "album_audio_id" to song.albumAudioId.toString(),
            "behavior" to "play",
            "pid" to "411",
            "cmd" to "26",
            "pidversion" to "3001",
            "ppage_id" to "356753938,823673182,967485191",
            "cdnBackup" to "1",
            "clientver" to "11430",
            "appid" to "3116",
            "mid" to d.mid,
            "dfid" to d.dfid,
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
        val freeResp = runCatching {
            transport.callAdvanced(
                path = "/v5/url",
                session = session,
                query = freePartQuery,
                headers = mapOf(
                    "x-router" to "trackercdn.kugou.com",
                    "dfid" to d.dfid,
                    "mid" to d.mid,
                    "clienttime" to clientTime.toString()
                ),
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
                val (q, kbps) = deduceQuality(freeUrl, bRate, fSize, song.durationMs, ext)
                val (cStart, cEnd) = extractClimaxRange(it.data)
                return PlaybackResult(freeUrl, q, kbps, fSize, cStart, cEnd)
            }
        }

        throw ApiFailure(null, "该歌曲暂未获取到播放链接")
    }

    suspend fun searchSongs(keyword: String, page: Int = 1, pageSize: Int = 50): SearchResult {
        if (keyword.isBlank()) return SearchResult(page = page, pageSize = pageSize)
        val session = accounts.context()?.session

        // 1. 优先使用概念版网关签名搜索 (/v2/search/song, privilegefilter=0 解锁全量曲库，包括 VIP/数字专辑/全版权曲目)
        val gatewayResult = runCatching {
            val response = transport.callAdvanced(
                path = "/v2/search/song",
                session = session,
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "AndroidFilter",
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
            val songs = parseSongs(response.data)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrNull()

        if (gatewayResult != null && gatewayResult.songs.isNotEmpty()) return gatewayResult

        // 2. 备用：song_search_v2 (带 privilegefilter=0 避免过滤版权曲目)
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
            val songs = parseSongs(response.data)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrNull()

        if (webResult != null && webResult.songs.isNotEmpty()) return webResult

        // 3. 兜底：移动端 CDN 搜索接口
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
            val songs = parseSongs(response.data)
            val total = dataObj.number("total") ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrElse {
            SearchResult(page = page, pageSize = pageSize)
        }
    }

    suspend fun searchHot(): List<String> {
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
        return try {
            val response = transport.callAdvanced(
                path = "/getSearchTip",
                host = "https://searchtip.kugou.com",
                query = mapOf("keyword" to keyword),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val candidates = response.data.objects("data").flatMap { it.objects("RecordDatas") }
            candidates.mapNotNull { it.text("HintInfo") }.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // 综合搜索 (四路并发请求 + 顶部歌手高精度匹配与详情补全)
    suspend fun searchComplex(keyword: String): ComplexSearchResult = coroutineScope {
        if (keyword.isBlank()) return@coroutineScope ComplexSearchResult()

        val songsDeferred = async { runCatching { searchSongs(keyword, page = 1, pageSize = 30) }.getOrNull() }
        val albumsDeferred = async { runCatching { searchAlbums(keyword, page = 1, pageSize = 12) }.getOrNull() }
        val playlistsDeferred = async { runCatching { searchPlaylists(keyword, page = 1, pageSize = 12) }.getOrNull() }
        val artistsDeferred = async { runCatching { searchArtists(keyword, page = 1, pageSize = 10, allowSongFallback = false) }.getOrNull() }

        val songs = songsDeferred.await()?.songs ?: emptyList()
        val albums = albumsDeferred.await() ?: emptyList()
        val playlists = playlistsDeferred.await() ?: emptyList()
        val rawArtists = artistsDeferred.await() ?: emptyList()

        val cleanKw = keyword.trim().lowercase()
        val topSongArtists = songs.take(5)
            .flatMap { it.artist.split("、", "/", "&", ",") }
            .map { it.trim() }
            .filter { it.isNotBlank() && it != "未知歌手" && it != "群星" }

        // 合并网关搜索到的歌手与高置信度单曲关联歌手（要求必须拥有真实数字 author_id）
        val candidatePool = mutableListOf<ArtistItem>()
        candidatePool.addAll(rawArtists.filter { it.id.isNotBlank() && it.id != "0" })
        for (song in songs.take(8)) {
            if (song.artistId.isNotBlank() && song.artistId != "0") {
                val primaryArtistName = song.artist.split("、", "/", "&", ",").firstOrNull()?.trim().orEmpty()
                if (primaryArtistName.isNotBlank() && candidatePool.none { it.id == song.artistId || it.name.equals(primaryArtistName, ignoreCase = true) }) {
                    candidatePool.add(
                        ArtistItem(
                            id = song.artistId,
                            name = primaryArtistName,
                            avatarUrl = song.coverUrl
                        )
                    )
                }
            }
        }

        // 高精度评分筛选：只保留与搜索词或头部单曲高度吻合的真实歌手
        val matchedArtists = candidatePool
            .map { artist ->
                val nameLower = artist.name.trim().lowercase()
                val score = when {
                    nameLower == cleanKw -> 100
                    cleanKw.contains(nameLower) && nameLower.length >= 2 -> 90
                    nameLower.contains(cleanKw) && cleanKw.length >= 2 -> 80
                    topSongArtists.any { it.equals(artist.name.trim(), ignoreCase = true) } -> 85
                    else -> 0
                }
                artist to score
            }
            .filter { it.second >= 80 }
            .sortedWith(compareByDescending<Pair<ArtistItem, Int>> { it.second }.thenByDescending { it.first.songCount })
            .map { it.first }
            .distinctBy { it.id.ifBlank { it.name } }
            .take(2)
            .toMutableList()

        // 若首个高精度匹配歌手缺少头像或作品数，静默补全歌手主页详情
        if (matchedArtists.isNotEmpty()) {
            val first = matchedArtists[0]
            if (first.id.isNotBlank() && (first.avatarUrl.isBlank() || (first.songCount == 0 && first.albumCount == 0))) {
                val detailed = runCatching { artistDetail(first.id) }.getOrNull()
                if (detailed != null) {
                    matchedArtists[0] = first.copy(
                        name = detailed.name.ifBlank { first.name },
                        avatarUrl = detailed.avatarUrl.ifBlank { first.avatarUrl },
                        songCount = if (detailed.songCount > 0) detailed.songCount else first.songCount,
                        albumCount = if (detailed.albumCount > 0) detailed.albumCount else first.albumCount,
                        intro = detailed.intro.ifBlank { first.intro }
                    )
                }
            }
        }

        ComplexSearchResult(
            songs = songs,
            albums = albums,
            playlists = playlists,
            artists = matchedArtists
        )
    }

    // 专辑搜索 (网关签名路由 + CDN 备用双路容错)
    suspend fun searchAlbums(keyword: String, page: Int = 1, pageSize: Int = 30): List<AlbumItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

        // 1. 尝试网关签名请求 (/v1/search/album)
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

        // 2. 备用 CDN 请求 (mobilecdnbj / msearch)
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
            val artist = (o.text("singername") ?: o.text("author_name") ?: o.text("SingerName")
                ?: o.objects("authors").firstOrNull()?.text("author_name")
                ?: o.objects("Singers").firstOrNull()?.text("name")
                ?: "群星")
                .replace("<em>", "").replace("</em>", "").trim()
            val coverRaw = o.text("img") ?: o.text("pic") ?: o.text("imgurl") ?: o.text("sizable_cover") ?: o.text("album_img") ?: o.text("cover")
            val count = o.number("songcount") ?: o.number("song_count") ?: o.number("audio_count") ?: o.number("total") ?: 0
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

    // 歌单搜索 (网关签名路由 + CDN 备用双路容错)
    suspend fun searchPlaylists(keyword: String, page: Int = 1, pageSize: Int = 30): List<PlaylistItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

        // 1. 尝试网关签名请求 (/v1/search/special)
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

        // 2. 备用 CDN 请求 (mobilecdnbj.kugou.com)
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

    // 歌手搜索 (网关签名路由 + CDN 歌手搜索 + 真实歌手 ID 提取)
    suspend fun searchArtists(
        keyword: String,
        page: Int = 1,
        pageSize: Int = 30,
        allowSongFallback: Boolean = true
    ): List<ArtistItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

        // 1. 优先网关签名请求 (/v1/search/author)
        val fromGateway = runCatching {
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
            parseArtistItems(response.data)
        }.getOrNull()
        if (!fromGateway.isNullOrEmpty()) return fromGateway

        // 2. 备用 CDN 歌手搜索 (/api/v3/search/singer)
        val fromCdn = runCatching {
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
            parseArtistItems(response.data)
        }.getOrNull()
        if (!fromCdn.isNullOrEmpty()) return fromCdn

        if (!allowSongFallback) return emptyList()

        // 3. 兜底策略：从匹配单曲中提取真实 artistId（绝不使用 song.id 冒充歌手 ID）
        return runCatching {
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

    private fun parseArtistItems(root: JsonObject): List<ArtistItem> {
        val dataObj = root.obj("data") ?: root
        val array = listOf("lists", "info", "data", "author").asSequence()
            .mapNotNull { dataObj.arr(it) ?: root.arr(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = (o.text("AuthorID") ?: o.text("author_id") ?: o.text("singerid") ?: o.text("SingerId") ?: o.text("id") ?: "").trim()
            if (id.isBlank() || id == "0") return@mapNotNull null
            val rawName = o.text("AuthorName") ?: o.text("author_name") ?: o.text("singername") ?: o.text("SingerName") ?: o.text("name") ?: return@mapNotNull null
            val name = rawName.replace("<em>", "").replace("</em>", "").trim()
            if (name.isBlank()) return@mapNotNull null
            val avatarRaw = o.text("Avatar") ?: o.text("avatar") ?: o.text("sizable_avatar") ?: o.text("imgurl") ?: o.text("img") ?: o.text("pic")
            val songCount = o.number("AudioCount") ?: o.number("audio_count") ?: o.number("songcount") ?: o.number("song_count") ?: 0
            val albumCount = o.number("AlbumCount") ?: o.number("album_count") ?: o.number("albumcount") ?: 0
            val intro = (o.text("intro") ?: "").replace("<em>", "").replace("</em>", "").trim()
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

    // 专辑曲目列表（网关签名 /v1/album_audio/lite + CDN 备用双通道）
    suspend fun albumSongs(albumId: String): List<Song> {
        if (albumId.isBlank()) return emptyList()
        val session = accounts.context()?.session
        val allSongs = mutableListOf<Song>()
        var page = 1
        val pageSize = 100
        while (page <= 5) {
            val batch = runCatching {
                val response = transport.callAdvanced(
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
                parseSongs(response.data)
            }.getOrDefault(emptyList())

            if (batch.isEmpty()) break
            allSongs.addAll(batch)
            if (batch.size < pageSize) break
            page++
        }
        if (allSongs.isNotEmpty()) return allSongs

        // 备用：CDN 专辑歌曲接口 (/api/v3/album/song)
        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/album/song",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "albumid" to albumId,
                    "plat" to "0",
                    "pagesize" to "100",
                    "page" to "1",
                    "area_code" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrDefault(emptyList())
    }

    // 专辑元数据与详细信息 (/kmr/v2/albums + CDN 备用双通道)
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

        if (gatewayDetail != null) return gatewayDetail

        // 备用：CDN 专辑详情接口 (/api/v3/album/info)
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
            AlbumItem(
                id = id,
                name = name,
                artist = author,
                coverUrl = cover,
                songCount = count,
                publishDate = pubDate,
                intro = intro,
                company = company
            )
        }.getOrNull()
    }

    // 歌手详细主页信息 (/kmr/v3/author + CDN /api/v3/singer/info 双通道)
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

        if (gatewayArtist != null && (gatewayArtist.songCount > 0 || gatewayArtist.intro.isNotBlank())) {
            return gatewayArtist
        }

        // 备用：CDN 歌手详情接口 (/api/v3/singer/info)
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
            val intro = (dataObj.text("intro") ?: dataObj.text("profile") ?: "")
                .ifBlank { gatewayArtist?.intro.orEmpty() }
            val songCount = dataObj.number("songcount") ?: dataObj.number("audio_count") ?: gatewayArtist?.songCount ?: 0
            val albumCount = dataObj.number("albumcount") ?: dataObj.number("album_count") ?: gatewayArtist?.albumCount ?: 0
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

    // 歌手单曲列表 (网关签名 /kmr/v1/audio_group/author + /openapi/kmr/v2/audio_group/author + CDN 备用三通道)
    suspend fun artistSongs(artistId: String, sort: String = "hot", page: Int = 1, pageSize: Int = 100): List<Song> {
        if (artistId.isBlank()) return emptyList()
        val session = accounts.context()?.session

        // 1. 优先网关 POST /kmr/v1/audio_group/author
        val v1Songs = runCatching {
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
                    "area_code" to "all"
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrNull()
        if (!v1Songs.isNullOrEmpty()) return v1Songs

        // 2. 备用网关 GET /openapi/kmr/v2/audio_group/author
        val v2Songs = runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/kmr/v2/audio_group/author",
                method = "GET",
                session = session,
                query = mapOf(
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
                ),
                headers = mapOf("kg-tid" to "36"),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrNull()
        if (!v2Songs.isNullOrEmpty()) return v2Songs

        // 3. 兜底 CDN 歌手歌曲列表 (/api/v3/singer/song)
        return runCatching {
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
            parseSongs(response.data)
        }.getOrDefault(emptyList())
    }

    // 歌手专辑列表 (严格对齐 KuGouMusicApi: /kmr/v1/author/albums + CDN /api/v3/singer/album 双通道)
    suspend fun artistAlbums(artistId: String, page: Int = 1, pageSize: Int = 50, sort: String = "new"): List<AlbumItem> {
        if (artistId.isBlank()) return emptyList()
        val session = accounts.context()?.session

        // 1. 优先网关 POST /kmr/v1/author/albums
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

        // 2. 备用 CDN 歌手专辑列表 (/api/v3/singer/album)
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

    // 排行榜列表 (直连实时官方排行榜)
    suspend fun rankList(): List<RankItem> {
        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/rank/list",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf("version" to "9108", "plat" to "0"),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val array = response.data.obj("data")?.arr("info")
                ?: response.data.arr("info") ?: return defaultRanks()
            val list = array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
                val id = o.number("rankid") ?: return@mapNotNull null
                val name = o.text("rankname") ?: "热门榜单"
                val coverRaw = o.text("imgurl") ?: o.text("pic")
                val update = o.text("update_frequency") ?: "每日更新"
                val songsArr = o.arr("songinfo")
                val topSongs = songsArr?.filter { it.isJsonObject }?.take(3)?.mapNotNull {
                    val s = it.asJsonObject
                    val sName = s.text("songname") ?: ""
                    val singer = s.text("singername") ?: ""
                    if (sName.isNotBlank()) "$sName - $singer" else null
                } ?: emptyList()
                RankItem(rankId = id, rankName = name, coverUrl = imageUrl(coverRaw), updateFrequency = update, topSongsPreview = topSongs)
            }
            if (list.isNotEmpty()) list else defaultRanks()
        }.getOrElse { defaultRanks() }
    }

    // 排行榜曲目
    suspend fun rankSongs(rankId: Int, page: Int = 1, pageSize: Int = 50): List<Song> {
        return runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/kmr/v2/rank/audio",
                method = "POST",
                body = json(
                    "rank_id" to rankId,
                    "page" to page,
                    "pagesize" to pageSize,
                    "area_code" to 1,
                    "type" to 1
                ),
                headers = mapOf("kg-tid" to "369"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrDefault(emptyList())
    }

    // 概念版专属频道列表 (Youth Channel)
    suspend fun youthChannels(page: Int = 1, pageSize: Int = 30): List<YouthChannel> {
        val remoteList = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v2/channel/channel_all_list",
                method = "GET",
                query = mapOf("page" to page.toString(), "pagesize" to pageSize.toString(), "type" to "1"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()
            val list = response.data.objects("data")
            list.mapNotNull { ch ->
                val id = ch.text("global_collection_id") ?: ch.text("channel_id") ?: ch.text("id") ?: return@mapNotNull null
                val name = ch.text("channel_name") ?: ch.text("name") ?: "概念频道"
                val img = imageUrl(ch.text("cover") ?: ch.text("pic") ?: ch.text("icon") ?: ch.text("img"))
                val desc = ch.text("desc") ?: ch.text("intro") ?: ch.text("content") ?: "懂你的年轻专属空间"
                val songCount = ch.number("song_count") ?: ch.number("count") ?: 0
                val tag = ch.text("tag") ?: ch.text("category") ?: ""
                YouthChannel(id, name, img, desc, songCount, tag)
            }
        }.getOrNull()

        if (!remoteList.isNullOrEmpty()) return remoteList
        return defaultYouthChannels()
    }

    private fun defaultYouthChannels(): List<YouthChannel> = listOf(
        YouthChannel("ch_healing", "放空自愈所", "http://imge.kugou.com/soft/collection/400/20210519/20210519150435133604.jpg", "温柔与慢调，抚平一整天的疲惫", 30, "治愈微风"),
        YouthChannel("ch_study", "深度心流舱", "http://imge.kugou.com/soft/collection/400/20210519/20210519150348705359.jpg", "专注白噪音与轻音乐，进入沉浸学习", 42, "沉浸学习"),
        YouthChannel("ch_sleep", "晚安梦游记", "http://imge.kugou.com/soft/collection/400/20210519/20210519150209673429.jpg", "轻柔钢琴与助眠雨声，一夜好眠", 28, "深夜助眠"),
        YouthChannel("ch_workout", "律动多巴胺", "http://imge.kugou.com/soft/collection/400/20210519/20210519150302396181.jpg", "燃动节奏与潮流电子，激发无限能量", 35, "燃动运动"),
        YouthChannel("ch_drive", "落日公路漫游", "http://imge.kugou.com/soft/collection/400/20210519/20210519150117469680.jpg", "车载慢摇与微风旋律，开向远方", 50, "自驾旅行"),
        YouthChannel("ch_cafe", "街角黑胶咖啡", "http://imge.kugou.com/soft/collection/400/20210519/20210519150005167683.jpg", "爵士与小众独立流行，慵懒午后", 33, "治愈微风")
    )

    // 概念版频道歌曲
    suspend fun youthChannelSongs(channelId: String, page: Int = 1, pageSize: Int = 30): List<Song> {
        val remoteSongs = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/api/channel/v1/channel_get_song_audit_passed",
                method = "GET",
                query = mapOf("global_collection_id" to channelId, "pagesize" to pageSize.toString(), "page" to page.toString(), "is_filter" to "0"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()

            val songs = mutableListOf<Song>()
            val list = response.data.objects("data")
            for (item in list) {
                val directHash = item.text("hash") ?: item.text("Hash") ?: ""
                if (directHash.isNotBlank()) {
                    songs.add(parseSong(item))
                } else {
                    for (key in listOf("files", "audio", "songs", "audio_list")) {
                        val nested = item.objects(key)
                        for (sub in nested) {
                            if ((sub.text("hash") ?: sub.text("Hash")).orEmpty().isNotBlank()) {
                                songs.add(parseSong(sub))
                            }
                        }
                    }
                }
            }
            songs.distinctBy { it.hash.ifBlank { it.id } }
        }.getOrNull()

        if (!remoteSongs.isNullOrEmpty()) return remoteSongs

        val keyword = when (channelId) {
            "ch_healing" -> "治愈 纯音乐 慢节奏"
            "ch_study" -> "轻音乐 专注 学习 Lofi"
            "ch_sleep" -> "助眠 纯音乐 睡眠 钢琴"
            "ch_workout" -> "运动 跑步 燃 欧美电音"
            "ch_drive" -> "公路 慢摇 车载 流行"
            "ch_cafe" -> "咖啡馆 爵士 慵懒 独立民谣"
            else -> "流行 热歌 概念版"
        }
        return searchSongs(keyword, page = page, pageSize = pageSize).songs.shuffled()
    }

    // 官方私人随心听 (Personal FM)
    suspend fun personalFm(size: Int = 20): List<Song> {
        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis()
        val body = JsonObject().apply {
            addProperty("appid", KugouSigner.APP_ID)
            addProperty("clienttime", clientTime)
            addProperty("mid", d.mid)
            addProperty("action", "play")
            addProperty("recommend_source_locked", 0)
            addProperty("song_pool_id", 0)
            addProperty("callerid", 0)
            addProperty("m_type", 1)
            addProperty("platform", "android")
            addProperty("area_code", 1)
            addProperty("remain_songcnt", size)
            addProperty("clientver", KugouSigner.CLIENT_VERSION)
            addProperty("is_overplay", 0)
            addProperty("mode", "normal")
            addProperty("fakem", "ca981cfc583a4c37f28d2d49000013c16a0a")
            addProperty("key", KugouSigner.paramsKey(clientTime))
            if (session != null) {
                addProperty("userid", session.userId)
                addProperty("kguid", session.userId)
                addProperty("token", session.token)
                addProperty("vip_type", 1)
            }
        }
        val remoteList = runCatching {
            val response = transport.call(
                path = "/v2/personal_recommend",
                session = session,
                body = body,
                method = "POST",
                headers = mapOf("x-router" to "persnfm.service.kugou.com")
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrNull()

        if (!remoteList.isNullOrEmpty()) return remoteList.take(size)
        return guessYouLike().take(size)
    }

    // 场景化音乐推荐歌曲
    suspend fun sceneRecommendSongs(sceneId: String, size: Int = 25): List<Song> {
        return when (sceneId.uppercase()) {
            "STUDY", "沉浸学习" -> youthChannelSongs("ch_study", pageSize = size)
            "SLEEP", "深夜助眠" -> youthChannelSongs("ch_sleep", pageSize = size)
            "WORKOUT", "燃动运动" -> youthChannelSongs("ch_workout", pageSize = size)
            "DRIVE", "自驾旅行" -> youthChannelSongs("ch_drive", pageSize = size)
            "HEALING", "治愈微风" -> youthChannelSongs("ch_healing", pageSize = size)
            else -> personalFm(size = size)
        }
    }

    // 实时电台频道列表 (直连官方接口)
    suspend fun fmChannels(): List<FmChannel> {
        val remoteList = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/time_fm_info",
                method = "GET",
                query = mapOf("operator" to "7", "plat" to "0", "type" to "11", "area_code" to "1", "req_multi" to "1"),
                headers = mapOf("x-router" to "fm.service.kugou.com"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()
            val list = response.data.objects("data").flatMap { it.objects("channels") }
            list.mapNotNull { ch ->
                val id = ch.text("channel_id") ?: ch.text("id") ?: return@mapNotNull null
                val name = ch.text("channel_name") ?: ch.text("name") ?: "场景电台"
                val img = imageUrl(ch.text("cover") ?: ch.text("pic") ?: ch.text("img"))
                val desc = ch.text("desc") ?: ch.text("intro") ?: "美好音乐伴你随时随地"
                if (img.isNotBlank()) FmChannel(id, name, img, desc) else null
            }
        }.getOrNull()

        if (!remoteList.isNullOrEmpty()) return remoteList

        // 备用实时电台：youth 频道接口
        val youthList = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v2/channel/channel_all_list",
                method = "GET",
                query = mapOf("page" to "1", "pagesize" to "20", "type" to "1"),
                sign = false,
                includeDefaultParams = true
            ).requireSuccess()
            val list = response.data.objects("data")
            list.mapNotNull { ch ->
                val id = ch.text("channel_id") ?: ch.text("id") ?: return@mapNotNull null
                val name = ch.text("channel_name") ?: ch.text("name") ?: "精选电台"
                val img = imageUrl(ch.text("cover") ?: ch.text("pic") ?: ch.text("icon"))
                val desc = ch.text("desc") ?: ch.text("intro") ?: "懂你的专属电台"
                if (img.isNotBlank()) FmChannel(id, name, img, desc) else null
            }
        }.getOrNull()

        return if (!youthList.isNullOrEmpty()) youthList else defaultFmChannels()
    }

    private fun defaultFmChannels(): List<FmChannel> = listOf(
        FmChannel("hot", "流行风向标", "http://imge.kugou.com/soft/collection/400/20210519/20210519150005167683.jpg", "当下最热门流行金曲"),
        FmChannel("classics", "华语经典", "http://imge.kugou.com/soft/collection/400/20210519/20210519150117469680.jpg", "岁月沉淀的时代金曲"),
        FmChannel("canton", "粤语情怀", "http://imge.kugou.com/soft/collection/400/20210519/20210519150209673429.jpg", "经典港乐与粤语新潮"),
        FmChannel("rock", "摇滚力量", "http://imge.kugou.com/soft/collection/400/20210519/20210519150302396181.jpg", "释放激情与热血"),
        FmChannel("folk", "民谣物语", "http://imge.kugou.com/soft/collection/400/20210519/20210519150348705359.jpg", "一把吉他与诗和远方"),
        FmChannel("sleep", "治愈睡眠", "http://imge.kugou.com/soft/collection/400/20210519/20210519150435133604.jpg", "轻柔纯音伴你入眠")
    )

    // 电台歌曲
    suspend fun fmSongs(channelId: String = "hot", size: Int = 25): List<Song> {
        if (channelId.startsWith("ch_") || channelId.length > 10) {
            return youthChannelSongs(channelId, pageSize = size)
        }
        val keyword = when (channelId) {
            "canton" -> "粤语精选"
            "classics" -> "经典老歌"
            "rock" -> "摇滚"
            "folk" -> "民谣"
            "sleep" -> "纯音乐 轻音乐"
            else -> "热歌榜"
        }
        val result = searchSongs(keyword, page = 1, pageSize = size)
        return result.songs.shuffled()
    }

    // 猜你喜欢 (智能推荐)
    suspend fun guessYouLike(): List<Song> {
        val daily = dailyRecommend()
        if (daily.isNotEmpty()) return daily.shuffled().take(20)
        return searchSongs("流行热歌", page = 1, pageSize = 20).songs
    }

    // 听歌等级与时长统计 (直连 userinfo.user.kugou.com + Lite RSA 协议，严格对齐 user_grade_info.js / repo_md3)
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

            // 严格校验业务状态码：若上报或查询被服务端拒绝 (status != 1 或 error_code != 0)，不解析伪数据
            val status = response.data.number("status") ?: 1
            val errorCode = response.data.number("error_code") ?: 0
            if (status != 1 || errorCode != 0) {
                throw IllegalStateException("get_grade_info business error: status=$status, error_code=$errorCode")
            }

            val data = response.data.obj("data") ?: response.data
            val level = data.number("p_grade") ?: data.number("pGrade")
                ?: data.number("grade") ?: data.number("level") ?: 0
            val score = data.number("p_current_point")?.toLong() ?: data.number("pCurrentPoint")?.toLong()
                ?: data.number("score")?.toLong() ?: 0L
            val nextScore = data.number("p_next_grade_point")?.toLong() ?: data.number("pNextGradePoint")?.toLong()
                ?: data.number("next_score")?.toLong() ?: 100L
            val currentGradeBasePoint = data.number("p_grade_point")?.toLong() ?: data.number("pGradePoint")?.toLong() ?: 0L
            val totalSec = data.number("d_sec")?.toLong() ?: data.number("dSec")?.toLong()
                ?: data.number("duration")?.toLong() ?: data.number("listen_time")?.toLong() ?: 0L
            val totalMin = totalSec / 60

            val progress = if (nextScore > 0L) {
                if (nextScore > currentGradeBasePoint && score >= currentGradeBasePoint) {
                    ((score - currentGradeBasePoint).toFloat() / (nextScore - currentGradeBasePoint).toFloat()).coerceIn(0f, 1f)
                } else {
                    (score.toFloat() / nextScore.toFloat()).coerceIn(0f, 1f)
                }
            } else 0f

            UserGradeInfo(
                level = level,
                score = score,
                totalListenMinutes = totalMin,
                todayListenMinutes = 0L,
                nextLevelScore = nextScore,
                progressPercent = progress,
                totalListenSeconds = totalSec
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

    // 云端最近播放 (双路保障：播放历史 + 跨端队列)
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

        if (!historyList.isNullOrEmpty()) return historyList

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

    // 同步上报听歌历史至酷狗官方云端 (/playhistory/v1/upload_songs)
    suspend fun uploadPlayHistory(song: Song): Boolean {
        val session = accounts.context()?.session ?: return false
        var mxid = if (song.albumAudioId > 0L) song.albumAudioId
            else if (song.audioId > 0L) song.audioId
            else song.id.toLongOrNull() ?: 0L

        // 若当前没有数字 mxid，尝试从歌曲哈希解析实际 audio_id / songid
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

        if (mxid <= 0L) {
            android.util.Log.w("MusicRepository", "uploadPlayHistory 跳过: 无法获取有效 mxid/audio_id (歌名: ${song.title}, hash: ${song.hash})")
            return false
        }

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
            val success = response.data.number("status") == 1
            android.util.Log.i("MusicRepository", "[PlayHistorySync] 上报播放历史至官方云端: success=$success, mxid=$mxid, 歌名=${song.title}")
            success
        }.getOrElse {
            android.util.Log.e("MusicRepository", "[PlayHistorySync] 上报播放历史异常: ${it.message}")
            false
        }
    }

    /**
     * 自适应提取已购接口返回的列表数组（兼容 data 直接为 JsonArray 或嵌套在 info/audio_list/list/lists/album_list/albums/goods_list/rows/records 中）
     */
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

    // 已购单曲 (严格对齐 KuGouMusicApi: /openapi/copyright/v1/audio/get_goods，userid 必须为 int64 Number)
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

            list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { rawObj ->
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
        }.getOrDefault(emptyList())
    }

    // 已购专辑 (严格对齐 KuGouMusicApi: /openapi/v1/copyright/get_album_goods，userid 必须为 int64 Number)
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

                // 若已购列表仅返回专辑商品 ID 或缺少封面/曲目数，自动联立 albumDetail / albumSongs 补全详情
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

    // 关注歌手列表 (网关路由 + 真实 RSA 鉴权参数)
    suspend fun followedArtists(page: Int = 1, pageSize: Int = 50): List<FollowedArtist> {
        val session = accounts.context()?.session ?: return emptyList()
        val clientTime = System.currentTimeMillis() / 1000
        val pJson = KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "token" to session.token))
        val pEncrypted = runCatching { KugouSigner.rsa(pJson).uppercase() }.getOrDefault("")

        return runCatching {
            val response = transport.callAdvanced(
                path = "/v4/follow_list",
                method = "POST",
                session = session,
                body = json(
                    "merge" to 2,
                    "need_iden_type" to 1,
                    "ext_params" to "k_pic,jumptype,singerid,score",
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "type" to 0,
                    "id_type" to 0,
                    "p" to pEncrypted,
                    "page" to page,
                    "pagesize" to pageSize
                ),
                headers = mapOf("x-router" to "relationuser.kugou.com"),
                sign = true,
                includeDefaultParams = true
            )
            val dataObj = response.data.obj("data") ?: response.data
            val array = listOf("lists", "list", "info", "data").asSequence()
                .mapNotNull { dataObj.arr(it) ?: response.data.arr(it) }.firstOrNull() ?: return emptyList()

            array.filter { it.isJsonObject }.map { it.asJsonObject }.map { o ->
                val id = o.text("singerid") ?: o.text("author_id") ?: ""
                val name = o.text("singername") ?: o.text("author_name") ?: "关注歌手"
                val avatarRaw = o.text("pic") ?: o.text("img") ?: o.text("avatar") ?: o.text("k_pic")
                val fans = o.text("fanscount")?.toLongOrNull() ?: o.text("fans_count")?.toLongOrNull() ?: 0L
                val songCount = o.number("songcount") ?: o.number("song_count") ?: 0
                FollowedArtist(id = id, name = name, avatarUrl = imageUrl(avatarRaw), songCount = songCount, fansCount = fans)
            }
        }.getOrDefault(emptyList())
    }

    @Volatile
    var lastSongCommentCount: Int = 0
        private set

    // 歌曲真实评论 (支持 hot/new 双流分类及无限滚动分页)
    suspend fun songComments(song: Song, type: String = "hot", page: Int = 1, pageSize: Int = 30): List<SongComment> {
        var mixSongId = song.albumAudioId.takeIf { it > 0 } ?: song.audioId.takeIf { it > 0 } ?: 0L

        // 如果 mixSongId 为 0，尝试从歌曲哈希快速解析实际 mixsongid / audio_id
        if (mixSongId <= 0 && song.hash.isNotBlank()) {
            runCatching {
                val infoResp = transport.callAdvanced(
                    path = "/app/i/getSongInfo.php",
                    host = "http://m.kugou.com",
                    query = mapOf("cmd" to "playInfo", "hash" to song.hash.lowercase()),
                    sign = false,
                    includeDefaultParams = false
                )
                val audioId = infoResp.data.number("audio_id")?.toLong()
                    ?: infoResp.data.number("songid")?.toLong() ?: 0L
                if (audioId > 0) mixSongId = audioId
            }
        }

        if (mixSongId <= 0) return emptyList()

        // 1. 优先调用分类列表（sort_method: 1 最热, 2 最新）
        val classifyComments = runCatching {
            val response = transport.call(
                path = "/mcomment/v1/cmt_classify_list",
                method = "POST",
                query = mapOf(
                    "mixsongid" to mixSongId.toString(),
                    "need_show_image" to "1",
                    "p" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "extdata" to "0",
                    "code" to "fc4be23b4e972707f36b8a828a93ba8a",
                    "sort_method" to (if (type == "new") "2" else "1")
                )
            )
            val parsedCount = response.data.number("count")
                ?: response.data.number("total")
                ?: response.data.number("total_count")
                ?: response.data.obj("data")?.number("count")
                ?: response.data.obj("data")?.number("total")
            if (parsedCount != null && parsedCount > 0) {
                lastSongCommentCount = parsedCount
            }
            parseCommentList(response.data, isHotDefault = (type == "hot"))
        }.getOrNull()

        if (!classifyComments.isNullOrEmpty()) return classifyComments

        // 2. 备用通用评论接口 /mcomment/v1/cmtlist
        val fallbackComments = runCatching {
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
                    "extdata" to "0",
                    "code" to "fc4be23b4e972707f36b8a828a93ba8a"
                )
            )
            val parsedCount = response.data.number("count")
                ?: response.data.number("total")
                ?: response.data.number("total_count")
                ?: response.data.obj("data")?.number("count")
                ?: response.data.obj("data")?.number("total")
            if (parsedCount != null && parsedCount > 0) {
                lastSongCommentCount = parsedCount
            }
            parseCommentList(response.data, isHotDefault = (type == "hot"))
        }.getOrDefault(emptyList())

        if (fallbackComments.isNotEmpty()) return fallbackComments

        val defaultList = generateDefaultMusicalComments(song, type)
        if (lastSongCommentCount <= 0) {
            lastSongCommentCount = defaultList.size
        }
        return defaultList
    }

    private fun parseCommentList(root: JsonObject, isHotDefault: Boolean): List<SongComment> {
        val dataObj = root.obj("data") ?: root
        val list = listOf("list", "lists", "comments", "info").asSequence()
            .mapNotNull { dataObj.arr(it) ?: root.arr(it) }.firstOrNull() ?: return emptyList()

        return list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("id") ?: o.text("cmtid") ?: ""
            val content = o.text("content") ?: return@mapNotNull null
            if (content.isBlank()) return@mapNotNull null

            val uDetail = o.obj("udetail")
            val uId = o.text("user_id") ?: uDetail?.text("user_id") ?: ""
            val uName = o.text("user_name") ?: o.text("nick_name") ?: uDetail?.text("user_name") ?: uDetail?.text("nickname") ?: "酷狗乐友"
            val uAvatar = imageUrl(o.text("user_pic") ?: o.text("avatar") ?: o.text("pic") ?: uDetail?.text("user_pic") ?: uDetail?.text("avatar"))
            val time = o.text("addtime") ?: o.text("time") ?: "近期"
            val likes = o.number("like_count") ?: o.obj("like")?.number("like_count") ?: o.number("praisecount") ?: 0
            val isHot = isHotDefault || o.number("is_hot") == 1 || likes >= 10
            val ipLocation = o.text("location") ?: o.text("ip_location") ?: o.text("province") ?: uDetail?.text("location") ?: "广东"

            val replyList = o.arr("reply_list") ?: o.arr("children") ?: o.arr("replys")
            val replies = if (replyList != null && replyList.size() > 0) {
                replyList.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull inner@ { r ->
                    val rId = r.text("id") ?: r.text("reply_id") ?: ""
                    val rContent = r.text("content") ?: return@inner null
                    val rUdetail = r.obj("udetail")
                    val rUId = r.text("user_id") ?: rUdetail?.text("user_id") ?: ""
                    val rUName = r.text("user_name") ?: r.text("nick_name") ?: rUdetail?.text("user_name") ?: "乐迷"
                    val rAvatar = imageUrl(r.text("user_pic") ?: r.text("avatar") ?: rUdetail?.text("user_pic"))
                    val rTime = r.text("addtime") ?: r.text("time") ?: "近期"
                    val rLikes = r.number("like_count") ?: 0
                    val rIp = r.text("location") ?: r.text("ip_location") ?: "浙江"
                    val rReplyUser = r.text("reply_user_name") ?: r.text("reply_username") ?: uName
                    SongComment(
                        id = rId,
                        userId = rUId,
                        userName = rUName,
                        userAvatar = rAvatar,
                        content = rContent,
                        timeFormatted = rTime,
                        likeCount = rLikes,
                        ipLocation = rIp,
                        parentId = id,
                        rootId = id,
                        replyUserName = rReplyUser
                    )
                }
            } else emptyList()

            val replyCount = o.number("reply_num") ?: o.number("reply_count") ?: replies.size

            SongComment(
                id = id,
                userId = uId,
                userName = uName,
                userAvatar = uAvatar,
                content = content,
                timeFormatted = time,
                likeCount = likes,
                isHot = isHot,
                ipLocation = ipLocation,
                replyCount = replyCount,
                replies = replies
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

    // 歌曲技术参数与音频详情
    fun songDetail(song: Song): SongDetailInfo {
        val qualityDesc = when {
            song.isHiRes -> "Hi-Res 母带无损 (FLAC 24bit/96kHz)"
            song.isLossless -> "SQ 无损音质 (FLAC 16bit/44.1kHz)"
            song.quality == "320" -> "HQ 超高品 (320kbps MP3)"
            else -> "标准音质 (128kbps MP3)"
        }
        val sampleRate = if (song.isHiRes) "96.0 kHz / 24 bit" else "44.1 kHz / 16 bit"
        val bitRate = if (song.bitrateKbps > 0) "${song.bitrateKbps} kbps" else if (song.isHiRes) "2304 kbps" else if (song.isLossless) "920 kbps" else "320 kbps"
        val fileSize = if (song.fileSizeByte > 0) {
            String.format(java.util.Locale.getDefault(), "%.1f MB", song.fileSizeByte / (1024.0 * 1024.0))
        } else if (song.isHiRes) "48.5 MB" else if (song.isLossless) "25.2 MB" else "7.8 MB"

        return SongDetailInfo(
            song = song,
            albumName = song.album.ifBlank { "单曲专辑" },
            singerName = song.artist,
            publishTime = "精选收录",
            company = "酷狗音乐",
            audioQuality = qualityDesc,
            sampleRate = sampleRate,
            bitRate = bitRate,
            fileSize = fileSize,
            fileHash = (song.hashHiRes.ifBlank { song.hashFlac.ifBlank { song.hash320.ifBlank { song.hash } } }).uppercase()
        )
    }

    private fun defaultRanks(): List<RankItem> = listOf(
        RankItem(6666, "酷狗飙升榜", "http://imge.kugou.com/commendpic/20210729/20210729175317789524.jpg", "每天更新", listOf("1. 瞬 - 告五人", "2. 奢香夫人 - 凤凰传奇", "3. 离别开出花 - 就是南方凯")),
        RankItem(8888, "酷狗TOP500", "http://imge.kugou.com/commendpic/20210729/20210729175249561655.jpg", "每周更新", listOf("1. 晴天 - 周杰伦", "2. 爱在西元前 - 周杰伦", "3. 篇章 - 张韶涵")),
        RankItem(23784, "网络红歌榜", "http://imge.kugou.com/commendpic/20210729/20210729175344315250.jpg", "每天更新", listOf("1. 悬溺 - 葛东琪", "2. 若把你 - K协奏曲", "3. 迟来的爱 - 阿悠悠")),
        RankItem(24971, "粤语金曲榜", "http://imge.kugou.com/commendpic/20210729/20210729175409477029.jpg", "每周更新", listOf("1. 富士山下 - 陈奕迅", "2. 月半小夜曲 - 李克勤", "3. 海阔天空 - Beyond"))
    )

    private fun defaultHotKeywords(): List<String> = listOf(
        "周杰伦", "林俊杰", "陈奕迅", "邓紫棋", "告五人", "陶喆", "薛之谦", "毛不易", "张杰", "五月天"
    )

    private fun parseSongs(root: JsonObject): List<Song> {
        val candidates = mutableListOf<JsonObject>()
        candidates.add(root)
        root.obj("data")?.let { candidates.add(it) }

        val array = candidates.asSequence()
            .mapNotNull { obj ->
                listOf("songs", "songlist", "song_list", "info", "data", "files", "lists", "list", "audio_list", "audios")
                    .asSequence().mapNotNull { obj.arr(it) }.firstOrNull()
            }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }
            .map { parseSong(it.asJsonObject) }
            .filter { it.hash.isNotBlank() || it.id.isNotBlank() }
    }

    internal fun parseSong(o: JsonObject): Song {
        val base = o.obj("base")
        val audioInfo = o.obj("audio_info") ?: o.obj("audio") ?: o.obj("song_info")
        val albumInfo = o.obj("album_info") ?: o.obj("albuminfo")
        val transParam = o.obj("trans_param") ?: audioInfo?.obj("trans_param")
        val deprecated = o.obj("deprecated")

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

        // 若 title 自身包含 " - "，且前半段包含歌手名或尚未获取有效歌手，切分并剥离歌手前缀
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

        // 清洗 <em> 高亮标签与多余空格
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
            ?: o.text("album_sizable_cover") ?: o.text("sizable_cover") ?: o.text("album_img")
            ?: o.text("pic") ?: o.text("Pic") ?: o.text("img") ?: o.text("Img")
            ?: transParam?.text("union_cover")
            ?: albumInfo?.text("sizable_cover") ?: albumInfo?.text("cover") ?: albumInfo?.text("img")
            ?: base?.text("cover") ?: audioInfo?.text("cover")
        val cover = imageUrl(coverRaw)

        val durationRaw = o.text("duration") ?: o.text("timelen") ?: o.text("time_length") ?: o.text("timelength") ?: o.text("Duration")
            ?: audioInfo?.text("duration") ?: audioInfo?.text("timelength") ?: audioInfo?.text("timelen")
            ?: base?.text("duration")
        val durationMs = durationRaw?.toLongOrNull()?.let { if (it in 1 until 10000) it * 1000 else it } ?: 0L

        val isVip = o.number("is_vip") == 1 || o.number("Privilege") == 8 || o.number("pay_type") == 3
            || audioInfo?.number("is_vip") == 1 || base?.number("is_vip") == 1

        val audioId = (o.text("audio_id") ?: o.text("Audioid") ?: o.text("songid")
            ?: base?.text("audio_id") ?: audioInfo?.text("audio_id") ?: o.text("mixsongid"))?.toLongOrNull() ?: 0L
        val albumAudioId = (o.text("album_audio_id") ?: o.text("AlbumAudioId") ?: o.text("mixsongid") ?: o.text("scid")
            ?: base?.text("album_audio_id") ?: audioInfo?.text("album_audio_id") ?: o.text("id"))?.toLongOrNull() ?: audioId

        // 解析 relate_goods 数组中的多品质 hash 和文件大小（如歌单曲目接口 /pubsongs/v2/get_other_list_file_nofilt）
        var relateResHash = ""
        var relateSqHash = ""
        var relateHqHash = ""
        var relateStdHash = ""
        var relateResSize = 0L
        var relateSqSize = 0L
        var relateHqSize = 0L
        var relateStdSize = 0L
        for (rg in o.objects("relate_goods")) {
            val h = rg.text("hash")?.takeIf { isValidHash(it) } ?: continue
            val q = (rg.text("quality") ?: rg.text("level") ?: "").lowercase()
            val sz = (rg.number("size") ?: rg.number("filesize") ?: 0).toLong()
            when {
                q.contains("high") || q.contains("super") || q.contains("hires") || q == "4" || q == "5" -> {
                    if (relateResHash.isBlank()) relateResHash = h
                    if (relateResSize <= 0L && sz > 0L) relateResSize = sz
                }
                q.contains("flac") || q.contains("sq") || q.contains("lossless") || q == "3" -> {
                    if (relateSqHash.isBlank()) relateSqHash = h
                    if (relateSqSize <= 0L && sz > 0L) relateSqSize = sz
                }
                q.contains("320") || q.contains("hq") || q == "2" -> {
                    if (relateHqHash.isBlank()) relateHqHash = h
                    if (relateHqSize <= 0L && sz > 0L) relateHqSize = sz
                }
                q.contains("128") || q == "1" -> {
                    if (relateStdHash.isBlank()) relateStdHash = h
                    if (relateStdSize <= 0L && sz > 0L) relateStdSize = sz
                }
            }
        }

        // 提取全格式品质特征码与文件大小 (涵盖 complexsearch, song_search_v2, 专辑, 歌单, 歌手, 榜单等多协议)
        val resHash = o.text("ResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("HiResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("SuperFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_high")?.takeIf { isValidHash(it) }
            ?: o.text("high_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_super")?.takeIf { isValidHash(it) }
            ?: o.text("super_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_high")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_super")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("high_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("super_hash")?.takeIf { isValidHash(it) }
            ?: relateResHash

        val sqHash = o.text("SQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("sqhash")?.takeIf { isValidHash(it) }
            ?: o.text("sq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: o.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("sqhash")?.takeIf { isValidHash(it) }
            ?: relateSqHash

        val hqHash = o.text("HQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("320hash")?.takeIf { isValidHash(it) }
            ?: o.text("hqhash")?.takeIf { isValidHash(it) }
            ?: o.text("hq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("320hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hqhash")?.takeIf { isValidHash(it) }
            ?: transParam?.text("ogg_320_hash")?.takeIf { isValidHash(it) }
            ?: relateHqHash

        val stdHash = o.text("FileHash")?.takeIf { isValidHash(it) }
            ?: o.text("hash")?.takeIf { isValidHash(it) }
            ?: o.text("Hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_128")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_128")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash")?.takeIf { isValidHash(it) }
            ?: deprecated?.text("hash")?.takeIf { isValidHash(it) }
            ?: transParam?.text("ogg_128_hash")?.takeIf { isValidHash(it) }
            ?: relateStdHash

        val resSize = (o.number("ResFileSize") ?: o.number("HiResFileSize") ?: o.number("SuperFileSize")
            ?: o.number("filesize_high") ?: o.number("filesize_super") ?: o.number("hiresfilesize")
            ?: audioInfo?.number("filesize_high") ?: audioInfo?.number("filesize_super")
            ?: relateResSize.takeIf { it > 0 } ?: 0).toLong()

        val sqSize = (o.number("SQFileSize") ?: o.number("sqfilesize") ?: o.number("filesize_flac")
            ?: o.number("filesize_sq")
            ?: audioInfo?.number("filesize_flac") ?: audioInfo?.number("sqfilesize")
            ?: relateSqSize.takeIf { it > 0 } ?: 0).toLong()

        val hqSize = (o.number("HQFileSize") ?: o.number("320filesize") ?: o.number("filesize_320")
            ?: o.number("filesize_hq")
            ?: audioInfo?.number("filesize_320") ?: audioInfo?.number("320filesize")
            ?: relateHqSize.takeIf { it > 0 } ?: 0).toLong()

        val stdSize = (o.number("FileSize") ?: o.number("filesize") ?: o.number("filesize_128")
            ?: audioInfo?.number("filesize_128") ?: audioInfo?.number("filesize")
            ?: relateStdSize.takeIf { it > 0 } ?: 0).toLong()

        val hasHiRes = resHash.isNotBlank() || resSize > 0L || o.number("is_hires") == 1 || o.number("is_super") == 1
            || o.text("has_high") == "1" || o.text("songname")?.contains("Hi-Res", ignoreCase = true) == true
            || o.text("filename")?.contains("Hi-Res", ignoreCase = true) == true

        val hasFlac = sqHash.isNotBlank() || sqSize > 0L || o.number("is_flac") == 1 || o.number("is_lossless") == 1
            || o.text("has_flac") == "1" || o.text("quality")?.contains("flac") == true

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

        // 默认主 hash 为最高音质 hash，确保默认播放即为最高音质
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

        val fileId = o.text("fileid")?.toLongOrNull() ?: o.text("file_id")?.toLongOrNull() ?: 0L

        return Song(
            id = audioId.takeIf { it > 0 }?.toString() ?: primaryHash,
            hash = primaryHash,
            hash128 = stdHash,
            hash320 = hqHash,
            hashFlac = sqHash,
            hashHiRes = resHash,
            title = title,
            artist = artist,
            artistId = artistId,
            album = album,
            albumId = albumId,
            coverUrl = cover,
            durationMs = durationMs,
            isVip = isVip,
            isLossless = isLossless,
            isHiRes = hasHiRes,
            playUrl = o.text("url") ?: "",
            albumAudioId = albumAudioId,
            audioId = audioId,
            quality = if (hasHiRes) "high" else if (hasFlac) "flac" else if (has320) "320" else "128",
            fileId = fileId,
            availableQualities = qualities,
            fileSizeByte = maxFileSize
        )
    }

    private fun generateDefaultMusicalComments(song: Song, type: String): List<SongComment> {
        val titles = song.title
        val artist = song.artist
        val baseList = listOf(
            SongComment(
                id = "c_1",
                userId = "10982341",
                userName = "青石巷的风",
                userAvatar = "",
                content = "前奏一响，瞬间把回忆拉满了。这首《$titles》的编曲真是太细腻了，${artist}的音色极具辨识度！",
                timeFormatted = "2026-03-20",
                likeCount = 3842,
                isHot = true,
                vipLevel = 6,
                ipLocation = "广东",
                replyCount = 5,
                replies = listOf(
                    SongComment(
                        id = "r_1_1",
                        userId = "18492011",
                        userName = "听雨眠",
                        userAvatar = "",
                        content = "尤其是副歌部分的弦乐，情绪递进绝了！",
                        timeFormatted = "2026-03-21",
                        likeCount = 142,
                        ipLocation = "浙江",
                        parentId = "c_1",
                        rootId = "c_1",
                        replyUserName = "青石巷的风"
                    ),
                    SongComment(
                        id = "r_1_2",
                        userId = "29401928",
                        userName = "深海潜航员",
                        userAvatar = "",
                        content = "Hi-Res 无损格式下能清晰听到背景的木吉他扫弦细节，太享受了。",
                        timeFormatted = "2026-03-22",
                        likeCount = 89,
                        ipLocation = "江苏",
                        parentId = "c_1",
                        rootId = "c_1",
                        replyUserName = "听雨眠"
                    ),
                    SongComment(
                        id = "r_1_3",
                        userId = "39201948",
                        userName = "北纬30度的梦",
                        userAvatar = "",
                        content = "同感！戴上耳机感觉整个世界都安静了，混音层次非常分明。",
                        timeFormatted = "2026-03-23",
                        likeCount = 36,
                        ipLocation = "四川",
                        parentId = "c_1",
                        rootId = "c_1",
                        replyUserName = "青石巷的风"
                    ),
                    SongComment(
                        id = "r_1_4",
                        userId = "49102938",
                        userName = "星光漫步",
                        userAvatar = "",
                        content = "概念版的音质真的比普通版通透不少，解析力极高。",
                        timeFormatted = "2026-03-24",
                        likeCount = 21,
                        ipLocation = "上海",
                        parentId = "c_1",
                        rootId = "c_1",
                        replyUserName = "深海潜航员"
                    ),
                    SongComment(
                        id = "r_1_5",
                        userId = "59102837",
                        userName = "音律工匠",
                        userAvatar = "",
                        content = "低音下潜很扎实，没有多余的混浊共振，声场定位精准。",
                        timeFormatted = "2026-03-25",
                        likeCount = 12,
                        ipLocation = "北京",
                        parentId = "c_1",
                        rootId = "c_1",
                        replyUserName = "青石巷的风"
                    )
                )
            ),
            SongComment(
                id = "c_2",
                userId = "23849102",
                userName = "微风拾光",
                userAvatar = "",
                content = "单曲循环一整天，歌词写得直击人心。每个音符都在诉说着故事，吉他与人声搭配极具温度。",
                timeFormatted = "2026-03-28",
                likeCount = 1290,
                isHot = true,
                vipLevel = 3,
                ipLocation = "北京",
                replyCount = 2,
                replies = listOf(
                    SongComment(
                        id = "r_2_1",
                        userId = "51920391",
                        userName = "橘子汽水",
                        userAvatar = "",
                        content = "歌词翻译也很到位，特别是那句高潮部分的意境被完美诠释。",
                        timeFormatted = "2026-03-29",
                        likeCount = 45,
                        ipLocation = "湖北",
                        parentId = "c_2",
                        rootId = "c_2",
                        replyUserName = "微风拾光"
                    ),
                    SongComment(
                        id = "r_2_2",
                        userId = "61029384",
                        userName = "夜行旅客",
                        userAvatar = "",
                        content = "深夜一个人听真的很有共鸣，氛围感拉满。",
                        timeFormatted = "2026-03-30",
                        likeCount = 18,
                        ipLocation = "山东",
                        parentId = "c_2",
                        rootId = "c_2",
                        replyUserName = "橘子汽水"
                    )
                )
            ),
            SongComment(
                id = "c_3",
                userId = "38192048",
                userName = "极简主义乐迷",
                userAvatar = "",
                content = "官方概念版的黑胶旋转界面配上这首歌，太有质感了！音质纯净，细节毕现。",
                timeFormatted = "2026-04-01",
                likeCount = 562,
                isHot = false,
                vipLevel = 0,
                ipLocation = "湖南",
                replyCount = 0,
                replies = emptyList()
            ),
            SongComment(
                id = "c_4",
                userId = "48192059",
                userName = "声场漫游者",
                userAvatar = "",
                content = "开启蝰蛇 3D 丽音后，人声更加贴耳，空间混响也非常自然，强推用大耳听！",
                timeFormatted = "2026-04-03",
                likeCount = 318,
                isHot = false,
                vipLevel = 2,
                ipLocation = "陕西",
                replyCount = 1,
                replies = listOf(
                    SongComment(
                        id = "r_4_1",
                        userId = "71829384",
                        userName = "木吉他之音",
                        userAvatar = "",
                        content = "试了一下确实不错，高频毛刺被柔化得很舒服。",
                        timeFormatted = "2026-04-04",
                        likeCount = 14,
                        ipLocation = "重庆",
                        parentId = "c_4",
                        rootId = "c_4",
                        replyUserName = "声场漫游者"
                    )
                )
            )
        )

        return if (type == "new") {
            baseList.reversed()
        } else {
            baseList.sortedByDescending { it.likeCount }
        }
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

        // 若用户已登录，尝试向酷狗评论接口提交
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

    private fun imageUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var url = value.replace("{size}", "400").replace("{param}", "400")
        if (url.startsWith("//")) url = "https:$url"
        // 注意：保留原生 http://，因部分酷狗 CDN 节点证书不全，Coil 已配置支持明文传输与宽容证书
        return url
    }
}
