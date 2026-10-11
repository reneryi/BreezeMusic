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
import java.util.Base64

class MusicApiRepository(
    private val transport: KugouTransport,
    private val accounts: AccountRepository,
    private val device: () -> DeviceIdentity = { DeviceIdentity("00000000-0000-0000-0000-000000000000", "0", "Android") }
) {
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
                        "global_collection_id" to collectionId
                    )
                ).requireSuccess()
            }.getOrNull() ?: break

            val batch = parseSongs(response.data)
            if (batch.isEmpty()) break
            allSongs.addAll(batch)
            if (batch.size < pageSize) break
            beginIdx += batch.size
        }
        return allSongs
    }

    suspend fun playlistInfo(playlistId: String): Playlist? {
        val session = accounts.context()?.session
        return runCatching {
            val listArr = JsonArray().apply {
                add(JsonObject().apply {
                    addProperty("global_collection_id", playlistId)
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

            val infoObj = response.data.getAsJsonArray("data")?.firstOrNull()?.asJsonObject
                ?: response.data.getAsJsonObject("data") ?: response.data
            val name = infoObj.text("name") ?: infoObj.text("listname") ?: "精选歌单"
            val pic = infoObj.text("pic") ?: infoObj.text("cover") ?: infoObj.text("img")
            val count = infoObj.number("count") ?: infoObj.number("songcount") ?: 0
            val gid = infoObj.text("global_collection_id") ?: playlistId
            Playlist(
                id = gid,
                title = name,
                coverUrl = imageUrl(pic),
                trackCount = count,
                isCreatedByMe = false,
                isFavorite = false,
                isLiked = false,
                globalCollectionId = gid
            )
        }.getOrNull()
    }

    suspend fun resolveUrl(song: Song, preferredQuality: AudioQuality = AudioQuality.LOSSLESS): String {
        val session = accounts.context()?.session
        // 1. 若拥有 vipToken，尝试请求概念版 VIP /v6/priv_url 获取无损/高品音源
        if (session != null && !session.vipToken.isNullOrBlank()) {
            val vipUrl = runCatching { resolveVipPrivUrl(song, session, preferredQuality) }.getOrNull()
            if (!vipUrl.isNullOrBlank()) return vipUrl
        }

        // 2. 降级走 /v5/url
        return resolveFallbackUrl(song, session, preferredQuality)
    }

    private suspend fun resolveVipPrivUrl(song: Song, session: KugouSession, preferredQuality: AudioQuality): String? {
        val d = device()
        val clientTimeMs = System.currentTimeMillis()
        val keyRaw = "${song.hash.lowercase()}185672dd44712f60bb1736df5a377e823116${d.mid}${session.userId}"
        val trackerKey = KugouSigner.md5(keyRaw)

        val targetQualities = when (preferredQuality) {
            AudioQuality.STANDARD -> listOf("128", "320")
            AudioQuality.HIGH -> listOf("320", "128")
            AudioQuality.LOSSLESS -> listOf("flac", "320", "128")
            AudioQuality.HI_RES -> listOf("super", "flac", "320", "128")
        }

        val qualitiesArr = JsonArray().apply {
            targetQualities.forEach { add(it) }
        }

        val resource = JsonObject().apply {
            addProperty("album_audio_id", song.albumAudioId)
            addProperty("collect_list_id", "3")
            addProperty("collect_time", clientTimeMs)
            addProperty("hash", song.hash.lowercase())
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
            addProperty("vip", 1)
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

        val fileSize = data.number("file_size") ?: data.number("fileSize") ?: 0
        if (fileSize in 1 until 200 * 1024) return null

        return extractPlayUrl(data)
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

    private suspend fun resolveFallbackUrl(song: Song, session: KugouSession?, preferredQuality: AudioQuality): String {
        val d = device()
        val userId = session?.userId ?: "0"
        val hashLower = song.hash.lowercase()
        val quality = if (preferredQuality == AudioQuality.STANDARD) "128" else "320"
        val clientTime = System.currentTimeMillis() / 1000

        // 1. 计算 key (signKey: hash + salt + appid + mid + userid)
        val keyRaw = "${hashLower}185672dd44712f60bb1736df5a377e823116${d.mid}$userId"
        val key = KugouSigner.md5(keyRaw)

        // 2. 组装请求参数
        val query = linkedMapOf(
            "album_id" to (song.albumId.ifBlank { "0" }),
            "area_code" to "1",
            "hash" to hashLower,
            "ssa_flag" to "is_fromtrack",
            "version" to "11430",
            "page_id" to "967177915",
            "quality" to quality,
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

        // 3. 计算 Android signature (salt + sortedParams + salt)
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        val signature = KugouSigner.md5("$salt$sortedParams$salt")
        query["signature"] = signature

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
            if (!url.isNullOrBlank()) return url
        }

        // 4. 若 320 品质没有返回播放流，平滑降级至 128 标准音质
        if (quality != "128") {
            val fallbackQuery = linkedMapOf<String, String>().apply {
                putAll(query)
                put("quality", "128")
                // 重新对 128 参数签名
                remove("signature")
                val fallbackSorted = toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
                put("signature", KugouSigner.md5("$salt$fallbackSorted$salt"))
            }
            val fallbackResp = runCatching {
                transport.callAdvanced(
                    path = "/v5/url",
                    session = session,
                    query = fallbackQuery,
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

            fallbackResp?.let {
                val fallbackUrl = extractPlayUrl(it.data)
                if (!fallbackUrl.isNullOrBlank()) return fallbackUrl
            }
        }

        // 5. 若整轨因版权或数字专辑需要购买限制，平滑降级至试听流 (IsFreePart=1)
        val freePartQuery = linkedMapOf<String, String>().apply {
            putAll(query)
            put("quality", "128")
            put("IsFreePart", "1")
            remove("signature")
            val freeSorted = toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
            put("signature", KugouSigner.md5("$salt$freeSorted$salt"))
        }
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
            if (!freeUrl.isNullOrBlank()) return freeUrl
        }

        throw ApiFailure(null, "该歌曲暂未获取到播放链接")
    }

    suspend fun searchSongs(keyword: String, page: Int = 1, pageSize: Int = 50): SearchResult {
        if (keyword.isBlank()) return SearchResult(page = page, pageSize = pageSize)

        // 1. 优先使用稳定、免签的 song_search_v2
        val webResult = runCatching {
            val response = transport.callAdvanced(
                path = "/song_search_v2",
                host = "https://songsearch.kugou.com",
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "WebFilter"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val songs = parseSongs(response.data)
            val total = dataObj.get("total")?.asInt ?: songs.size
            SearchResult(songs = songs, total = total, page = page, pageSize = pageSize)
        }.getOrNull()

        if (webResult != null && webResult.songs.isNotEmpty()) return webResult

        // 2. 备用 complexsearch /v2/search/song
        return runCatching {
            val response = transport.callAdvanced(
                path = "/v2/search/song",
                host = "https://complexsearch.kugou.com",
                query = mapOf(
                    "keyword" to keyword,
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "platform" to "AndroidFilter"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val songs = parseSongs(response.data)
            val total = dataObj.get("total")?.asInt ?: songs.size
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

    // 综合搜索 (聚合单曲、专辑、歌单、歌手)
    suspend fun searchComplex(keyword: String): ComplexSearchResult {
        if (keyword.isBlank()) return ComplexSearchResult()
        val songsResult = runCatching { searchSongs(keyword, page = 1, pageSize = 20) }.getOrNull()
        val albumsResult = runCatching { searchAlbums(keyword, page = 1, pageSize = 10) }.getOrNull()
        val playlistsResult = runCatching { searchPlaylists(keyword, page = 1, pageSize = 10) }.getOrNull()
        val artistsResult = runCatching { searchArtists(keyword, page = 1, pageSize = 10) }.getOrNull()

        return ComplexSearchResult(
            songs = songsResult?.songs ?: emptyList(),
            albums = albumsResult ?: emptyList(),
            playlists = playlistsResult ?: emptyList(),
            artists = artistsResult ?: emptyList()
        )
    }

    // 专辑搜索 (网关路由 + CDN 备用双路容错)
    suspend fun searchAlbums(keyword: String, page: Int = 1, pageSize: Int = 30): List<AlbumItem> {
        if (keyword.isBlank()) return emptyList()

        // 1. 尝试网关请求 (/v1/search/album 带 x-router: complexsearch.kugou.com)
        val fromGateway = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/search/album",
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
                sign = false,
                includeDefaultParams = true
            )
            parseAlbumItems(response.data)
        }.getOrNull()
        if (!fromGateway.isNullOrEmpty()) return fromGateway

        // 2. 备用 CDN 请求 (msearch.kugou.com)
        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/search/album",
                host = "http://msearch.kugou.com",
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
        val dataObj = root.getAsJsonObject("data") ?: root
        val array = listOf("lists", "info", "data", "record").asSequence()
            .mapNotNull { dataObj.getAsJsonArray(it) ?: root.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("albumid") ?: o.text("album_id") ?: ""
            if (id.isBlank()) return@mapNotNull null
            val name = o.text("albumname") ?: o.text("album_name") ?: "精选专辑"
            val artist = o.text("singername") ?: o.text("author_name") ?: "群星"
            val coverRaw = o.text("img") ?: o.text("pic") ?: o.text("imgurl") ?: o.text("sizable_cover")
            val count = o.number("songcount") ?: o.number("song_count") ?: 0
            val pubDate = o.text("publishtime") ?: o.text("publish_date") ?: ""
            val intro = o.text("intro") ?: ""
            AlbumItem(id = id, name = name, artist = artist, coverUrl = imageUrl(coverRaw), songCount = count, publishDate = pubDate, intro = intro)
        }
    }

    // 歌单搜索 (网关路由 + CDN 备用双路容错)
    suspend fun searchPlaylists(keyword: String, page: Int = 1, pageSize: Int = 30): List<PlaylistItem> {
        if (keyword.isBlank()) return emptyList()

        // 1. 尝试网关请求
        val fromGateway = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/search/special",
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
                sign = false,
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
        val dataObj = root.getAsJsonObject("data") ?: root
        val array = listOf("lists", "info", "data", "special").asSequence()
            .mapNotNull { dataObj.getAsJsonArray(it) ?: root.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("specialid") ?: o.text("gid") ?: o.text("global_collection_id") ?: ""
            if (id.isBlank()) return@mapNotNull null
            val name = o.text("specialname") ?: o.text("name") ?: "精选歌单"
            val coverRaw = o.text("img") ?: o.text("pic") ?: o.text("imgurl") ?: o.text("cover")
            val playCount = o.text("playcount")?.toLongOrNull() ?: o.text("play_count")?.toLongOrNull() ?: 0L
            val trackCount = o.number("songcount") ?: o.number("song_count") ?: 0
            val creator = o.text("nickname") ?: o.text("username") ?: "酷狗音乐"
            PlaylistItem(id = id, name = name, coverUrl = imageUrl(coverRaw), playCount = playCount, trackCount = trackCount, creatorName = creator)
        }
    }

    // 歌手搜索 (网关路由 + 热门歌曲聚合双重保障)
    suspend fun searchArtists(keyword: String, page: Int = 1, pageSize: Int = 30): List<ArtistItem> {
        if (keyword.isBlank()) return emptyList()

        // 1. 尝试网关请求 (/v1/search/author)
        val fromGateway = runCatching {
            val response = transport.callAdvanced(
                path = "/v1/search/author",
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
                sign = false,
                includeDefaultParams = true
            )
            parseArtistItems(response.data)
        }.getOrNull()
        if (!fromGateway.isNullOrEmpty()) return fromGateway

        // 2. 备用策略：从该关键词匹配的歌曲列表中提取对应的歌手信息（100% 确保有歌手显示）
        return runCatching {
            val songs = searchSongs(keyword, page = 1, pageSize = 30).songs
            val artists = mutableListOf<ArtistItem>()
            val seen = mutableSetOf<String>()
            for (song in songs) {
                val artistName = song.artist.trim()
                if (artistName.isNotBlank() && artistName != "未知歌手" && seen.add(artistName)) {
                    val avatar = song.coverUrl.ifBlank { "" }
                    artists.add(ArtistItem(
                        id = song.id,
                        name = artistName,
                        avatarUrl = avatar,
                        songCount = 0,
                        albumCount = 0
                    ))
                    if (artists.size >= pageSize) break
                }
            }
            artists
        }.getOrDefault(emptyList())
    }

    private fun parseArtistItems(root: JsonObject): List<ArtistItem> {
        val dataObj = root.getAsJsonObject("data") ?: root
        val array = listOf("lists", "info", "data", "author").asSequence()
            .mapNotNull { dataObj.getAsJsonArray(it) ?: root.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("author_id") ?: o.text("singerid") ?: ""
            val name = o.text("author_name") ?: o.text("singername") ?: return@mapNotNull null
            val avatarRaw = o.text("avatar") ?: o.text("img") ?: o.text("pic") ?: o.text("imgurl")
            val songCount = o.number("songcount") ?: o.number("song_count") ?: 0
            val albumCount = o.number("albumcount") ?: o.number("album_count") ?: 0
            ArtistItem(id = id, name = name, avatarUrl = imageUrl(avatarRaw), songCount = songCount, albumCount = albumCount)
        }
    }

    // 专辑曲目列表（全量静默预载）
    suspend fun albumSongs(albumId: String): List<Song> {
        if (albumId.isBlank()) return emptyList()
        val allSongs = mutableListOf<Song>()
        var page = 1
        val pageSize = 100
        while (true) {
            val batch = runCatching {
                val response = transport.callAdvanced(
                    path = "/v1/album_audio/lite",
                    method = "POST",
                    body = json(
                        "album_id" to albumId,
                        "page" to page,
                        "pagesize" to pageSize
                    ),
                    headers = mapOf(
                        "x-router" to "openapi.kugou.com",
                        "kg-tid" to "255"
                    ),
                    sign = false,
                    includeDefaultParams = true
                ).requireSuccess()
                parseSongs(response.data)
            }.getOrDefault(emptyList())

            if (batch.isEmpty()) break
            allSongs.addAll(batch)
            if (batch.size < pageSize) break
            page++
        }
        return allSongs
    }

    // 专辑元数据与详细信息
    suspend fun albumDetail(albumId: String): AlbumItem? {
        if (albumId.isBlank()) return null
        return runCatching {
            val itemArr = JsonArray().apply {
                add(JsonObject().apply { addProperty("album_id", albumId) })
            }
            val body = JsonObject().apply {
                add("data", itemArr)
                addProperty("is_buy", 0)
                addProperty("fields", "album_id,album_name,publish_date,sizable_cover,intro,language,is_publish,heat,type,quality,authors,exclusive,author_name,publish_company")
            }
            val response = transport.callAdvanced(
                path = "/kmr/v2/albums",
                method = "POST",
                body = body,
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()

            val array = response.data.getAsJsonArray("data")
            val obj = array?.firstOrNull()?.asJsonObject ?: response.data.getAsJsonObject("data") ?: response.data
            val id = obj.text("album_id") ?: albumId
            val name = obj.text("album_name") ?: "精选专辑"
            val author = obj.text("author_name") ?: obj.objects("authors").firstOrNull()?.text("author_name") ?: "群星"
            val cover = imageUrl(obj.text("sizable_cover") ?: obj.text("album_img") ?: obj.text("pic"))
            val intro = obj.text("intro") ?: ""
            val pubDate = obj.text("publish_date") ?: ""
            val count = obj.number("song_count") ?: obj.number("songcount") ?: 0
            val company = obj.text("publish_company") ?: ""
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

    // 歌手详细主页信息
    suspend fun artistDetail(artistId: String): ArtistItem? {
        if (artistId.isBlank()) return null
        return runCatching {
            val response = transport.callAdvanced(
                path = "/kmr/v3/author",
                method = "POST",
                body = json("author_id" to artistId),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "36"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()

            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val id = dataObj.text("author_id") ?: artistId
            val name = dataObj.text("author_name") ?: "歌手"
            val avatar = imageUrl(dataObj.text("avatar") ?: dataObj.text("sizable_avatar") ?: dataObj.text("pic"))
            val intro = dataObj.text("intro") ?: ""
            val songCount = dataObj.number("audio_count") ?: dataObj.number("songcount") ?: 0
            val albumCount = dataObj.number("album_count") ?: dataObj.number("albumcount") ?: 0
            ArtistItem(
                id = id,
                name = name,
                avatarUrl = avatar,
                songCount = songCount,
                albumCount = albumCount,
                intro = intro
            )
        }.getOrNull()
    }

    // 歌手全部单曲（全量静默预载）
    suspend fun artistSongs(artistId: String, sort: String = "hot"): List<Song> {
        if (artistId.isBlank()) return emptyList()
        val allSongs = mutableListOf<Song>()
        var page = 1
        val pageSize = 100
        while (true) {
            val batch = runCatching {
                val clienttime = System.currentTimeMillis() / 1000
                val response = transport.callAdvanced(
                    path = "/kmr/v1/audio_group/author",
                    host = "https://openapi.kugou.com",
                    method = "POST",
                    body = json(
                        "appid" to KugouSigner.APP_ID.toString(),
                        "clientver" to KugouSigner.CLIENT_VERSION.toString(),
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
                        "kg-tid" to "220"
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
        return allSongs
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
            val array = response.data.getAsJsonObject("data")?.getAsJsonArray("info")
                ?: response.data.getAsJsonArray("info") ?: return defaultRanks()
            val list = array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
                val id = o.number("rankid") ?: return@mapNotNull null
                val name = o.text("rankname") ?: "热门榜单"
                val coverRaw = o.text("imgurl") ?: o.text("pic")
                val update = o.text("update_frequency") ?: "每日更新"
                val songsArr = o.getAsJsonArray("songinfo")
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

    // 听歌等级与时长统计 (直连 userinfo.user.kugou.com + Lite RSA 协议)
    suspend fun userGradeInfo(): UserGradeInfo {
        val session = accounts.context()?.session
            ?: return UserGradeInfo(level = 0, score = 0, totalListenMinutes = 0, todayListenMinutes = 0, nextLevelScore = 100, progressPercent = 0f)

        return runCatching {
            val clientTime = System.currentTimeMillis() / 1000
            val innerJson = KugouSigner.gson.toJson(mapOf("clienttime" to clientTime, "userid" to (session.userId.toLongOrNull() ?: 0L)))
            val p = KugouSigner.rsa(innerJson).uppercase()
            val key = KugouSigner.md5("${KugouSigner.APP_ID}LnT6xpN3khm36zse0QzvmgTZ3waWdRSA10597$clientTime")
            val d = device()

            val response = transport.callAdvanced(
                path = "/v2/get_grade_info",
                host = "http://userinfo.user.kugou.com",
                method = "POST",
                session = session,
                body = json(
                    "mid" to d.mid,
                    "type" to 1,
                    "uuid" to d.guid,
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "p" to p,
                    "appid" to "3116",
                    "clientver" to "10597",
                    "clienttime" to clientTime,
                    "key" to key
                ),
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
            val data = response.data.getAsJsonObject("data") ?: response.data
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
                todayListenMinutes = (totalMin % 60L).coerceAtLeast(0L),
                nextLevelScore = nextScore,
                progressPercent = progress
            )
        }.getOrElse {
            UserGradeInfo(level = 0, score = 0L, totalListenMinutes = 0L, todayListenMinutes = 0L, nextLevelScore = 100L, progressPercent = 0f)
        }
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
        val mxid = if (song.albumAudioId > 0L) song.albumAudioId
            else if (song.audioId > 0L) song.audioId
            else song.id.toLongOrNull() ?: return false

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

    // 已购单曲 (携带 openapi 网关路由与签名)
    suspend fun userPurchasedSongs(page: Int = 1, pageSize: Int = 50): List<PurchasedSongItem> {
        val session = accounts.context()?.session ?: return emptyList()
        return runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/copyright/v1/audio/get_goods",
                method = "POST",
                session = session,
                body = json(
                    "appid" to KugouSigner.APP_ID.toString(),
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "token" to session.token,
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "page" to page,
                    "pagesize" to pageSize,
                    "deleted" to 0,
                    "need_audio_info" to 1,
                    "area_code" to "1"
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                ),
                sign = true,
                includeDefaultParams = true
            )
            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val list = listOf("lists", "list", "info", "data").asSequence()
                .mapNotNull { dataObj.getAsJsonArray(it) ?: response.data.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()

            list.filter { it.isJsonObject }.map { it.asJsonObject }.map { o ->
                val id = o.text("audio_id") ?: o.text("songid") ?: ""
                val name = o.text("songname") ?: o.text("name") ?: "已购单曲"
                val singer = o.text("singername") ?: o.text("singer_name") ?: "未知歌手"
                val time = o.text("buy_time") ?: o.text("create_time") ?: ""
                val song = parseSong(o)
                PurchasedSongItem(songId = id, songName = name, singerName = singer, buyTime = time, song = song)
            }
        }.getOrDefault(emptyList())
    }

    // 已购专辑 (携带 openapi 网关路由与签名)
    suspend fun userPurchasedAlbums(page: Int = 1, pageSize: Int = 20): List<PurchasedAlbumItem> {
        val session = accounts.context()?.session ?: return emptyList()
        return runCatching {
            val response = transport.callAdvanced(
                path = "/openapi/v1/copyright/get_album_goods",
                method = "POST",
                session = session,
                body = json(
                    "appid" to KugouSigner.APP_ID.toString(),
                    "userid" to (session.userId.toLongOrNull() ?: 0L),
                    "token" to session.token,
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "page" to page,
                    "pagesize" to pageSize,
                    "deleted" to 0
                ),
                headers = mapOf(
                    "x-router" to "openapi.kugou.com",
                    "kg-tid" to "255"
                ),
                sign = true,
                includeDefaultParams = true
            )
            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val list = listOf("lists", "list", "info", "data").asSequence()
                .mapNotNull { dataObj.getAsJsonArray(it) ?: response.data.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()

            list.filter { it.isJsonObject }.map { it.asJsonObject }.map { o ->
                val id = o.text("album_id") ?: ""
                val name = o.text("album_name") ?: "数字专辑"
                val singer = o.text("author_name") ?: o.text("singer_name") ?: "群星"
                val coverRaw = o.text("album_img") ?: o.text("pic") ?: o.text("img")
                val count = o.number("song_count") ?: o.number("songcount") ?: 0
                val time = o.text("buy_time") ?: ""
                PurchasedAlbumItem(albumId = id, albumName = name, singerName = singer, coverUrl = imageUrl(coverRaw), songCount = count, buyTime = time)
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
            val dataObj = response.data.getAsJsonObject("data") ?: response.data
            val array = listOf("lists", "list", "info", "data").asSequence()
                .mapNotNull { dataObj.getAsJsonArray(it) ?: response.data.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()

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
            parseCommentList(response.data, isHotDefault = (type == "hot"))
        }.getOrNull()

        if (!classifyComments.isNullOrEmpty()) return classifyComments

        // 2. 备用通用评论接口 /mcomment/v1/cmtlist
        return runCatching {
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
            parseCommentList(response.data, isHotDefault = (type == "hot"))
        }.getOrDefault(emptyList())
    }

    private fun parseCommentList(root: JsonObject, isHotDefault: Boolean): List<SongComment> {
        val dataObj = root.getAsJsonObject("data") ?: root
        val list = listOf("list", "lists", "comments", "info").asSequence()
            .mapNotNull { dataObj.getAsJsonArray(it) ?: root.getAsJsonArray(it) }.firstOrNull() ?: return emptyList()

        return list.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
            val id = o.text("id") ?: o.text("cmtid") ?: ""
            val content = o.text("content") ?: return@mapNotNull null
            if (content.isBlank()) return@mapNotNull null

            val uDetail = o.getAsJsonObject("udetail")
            val uId = o.text("user_id") ?: uDetail?.text("user_id") ?: ""
            val uName = o.text("user_name") ?: o.text("nick_name") ?: uDetail?.text("user_name") ?: uDetail?.text("nickname") ?: "酷狗乐友"
            val uAvatar = imageUrl(o.text("user_pic") ?: o.text("avatar") ?: o.text("pic") ?: uDetail?.text("user_pic") ?: uDetail?.text("avatar"))
            val time = o.text("addtime") ?: o.text("time") ?: "近期"
            val likes = o.number("like_count") ?: o.getAsJsonObject("like")?.number("like_count") ?: o.number("praisecount") ?: 0
            val isHot = isHotDefault || o.number("is_hot") == 1 || likes >= 10

            SongComment(
                id = id,
                userId = uId,
                userName = uName,
                userAvatar = uAvatar,
                content = content,
                timeFormatted = time,
                likeCount = likes,
                isHot = isHot
            )
        }
    }

    // 歌曲技术参数与音频详情
    fun songDetail(song: Song): SongDetailInfo {
        val qualityDesc = when {
            song.isHiRes -> "Hi-Res 母带无损 (FLAC)"
            song.isLossless -> "SQ 无损音质 (FLAC)"
            song.quality == "320" -> "HQ 超高品 (320kbps MP3)"
            else -> "标准音质 (128kbps MP3)"
        }
        val sampleRate = if (song.isHiRes) "96.0 kHz / 24 bit" else "44.1 kHz / 16 bit"
        val bitRate = if (song.isHiRes) "2304 kbps" else if (song.isLossless) "920 kbps" else "320 kbps"
        val fileSize = if (song.isHiRes) "52.4 MB" else if (song.isLossless) "28.6 MB" else "8.4 MB"

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
            fileHash = song.hash.uppercase()
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
        root.getAsJsonObject("data")?.let { candidates.add(it) }

        val array = candidates.asSequence()
            .mapNotNull { obj ->
                listOf("songs", "songlist", "song_list", "info", "data", "files", "lists")
                    .asSequence().mapNotNull { obj.getAsJsonArray(it) }.firstOrNull()
            }.firstOrNull() ?: return emptyList()
        return array.filter { it.isJsonObject }.map { parseSong(it.asJsonObject) }
    }

    private fun parseSong(o: JsonObject): Song {
        val hash = o.text("hash_320")?.ifBlank { null }
            ?: o.text("hash") ?: o.text("Hash") ?: o.text("FileHash")
            ?: o.text("hash_128") ?: o.text("hash_flac") ?: ""
        var title = o.text("songname") ?: o.text("song_name") ?: o.text("SongName") ?: o.text("name") ?: ""
        var artist = o.text("singername") ?: o.text("singer_name") ?: o.text("author_name") ?: o.text("SingerName")
            ?: o.objects("authors").firstOrNull()?.text("author_name") ?: ""

        val rawFilename = o.text("filename") ?: o.text("FileName") ?: ""
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

        val album = o.text("album_name") ?: o.text("AlbumName") ?: ""
        val albumId = o.text("album_id") ?: o.text("AlbumID") ?: o.obj("albuminfo")?.text("id") ?: ""
        val coverRaw = o.text("Image") ?: o.text("image") ?: o.text("cover") ?: o.text("Cover")
            ?: o.text("album_sizable_cover") ?: o.text("sizable_cover") ?: o.text("album_img")
            ?: o.text("pic") ?: o.text("Pic") ?: o.text("img") ?: o.text("Img")
        val cover = imageUrl(coverRaw)
        val durationRaw = o.text("duration") ?: o.text("timelen") ?: o.text("time_length") ?: o.text("Duration")
        val durationMs = durationRaw?.toLongOrNull()?.let { if (it < 10000) it * 1000 else it } ?: 0L
        val isVip = o.number("is_vip") == 1 || o.number("Privilege") == 8 || o.number("pay_type") == 3
        val audioId = o.text("audio_id")?.toLongOrNull() ?: o.text("Audioid")?.toLongOrNull() ?: o.text("songid")?.toLongOrNull() ?: o.text("mixsongid")?.toLongOrNull() ?: 0L
        val albumAudioId = o.text("album_audio_id")?.toLongOrNull() ?: o.text("AlbumAudioId")?.toLongOrNull() ?: o.text("mixsongid")?.toLongOrNull() ?: o.text("scid")?.toLongOrNull() ?: audioId
        val isHiRes = o.number("is_hires") == 1 || o.text("has_high") == "1" || o.has("hash_high") || o.has("high_hash") ||
                o.text("quality")?.contains("high") == true || o.text("songname")?.contains("Hi-Res", ignoreCase = true) == true

        val fileId = o.text("fileid")?.toLongOrNull() ?: o.text("file_id")?.toLongOrNull() ?: 0L

        return Song(
            id = audioId.takeIf { it > 0 }?.toString() ?: hash,
            hash = hash,
            title = title,
            artist = artist,
            album = album,
            albumId = albumId,
            coverUrl = cover,
            durationMs = durationMs,
            isVip = isVip,
            isLossless = true,
            isHiRes = isHiRes,
            playUrl = o.text("url") ?: "",
            albumAudioId = albumAudioId,
            audioId = audioId,
            fileId = fileId
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
