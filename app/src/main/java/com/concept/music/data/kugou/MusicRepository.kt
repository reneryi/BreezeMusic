package com.concept.music.data.kugou

import com.concept.music.data.model.AlbumItem
import com.concept.music.data.model.ArtistItem
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.ComplexSearchResult
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.FollowedArtist
import com.concept.music.data.model.MusicStoryItem
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
import com.concept.music.data.model.YouthChannelAmway
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

    // 记录每个电台与频道的漫游偏移游标，确保每次刷新/换一批/继续漫游都能获取全新曲目而非原地洗牌
    private val fmChannelCursorMap = ConcurrentHashMap<String, AtomicInteger>()
    private val youthChannelPageCursorMap = ConcurrentHashMap<String, AtomicInteger>()
    private val personalFmPoolCursor = AtomicInteger(0)

    // 概念版频道订阅状态缓存（支持云端同步与即时乐观更新）
    private val subscribedChannelIds = ConcurrentHashMap.newKeySet<String>().apply {
        add("ch_healing")
        add("ch_study")
    }

    // 本地已关注歌手增量缓存（防止关注后因云端延迟未立即出现在列表中）
    private val localFollowedArtistsMap = ConcurrentHashMap<String, FollowedArtist>()

    fun isChannelSubscribed(channelId: String): Boolean = subscribedChannelIds.contains(channelId)

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
        if (allSongs.isNotEmpty()) return allSongs

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
        if (v4Songs.isNotEmpty()) return v4Songs

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
    suspend fun enrichSongsPrivilegeLite(songs: List<Song>): List<Song> {
        if (songs.isEmpty()) return songs
        val needEnrich = songs.any { !it.isLossless && !it.isHiRes && it.hashFlac.isBlank() && it.hashHiRes.isBlank() }
        if (!needEnrich) return songs

        val session = accounts.context()?.session
        val qualitiesArr = JsonArray().apply {
            listOf("128", "320", "flac", "high", "super").forEach { add(it) }
        }

        val enrichedByHash = HashMap<String, JsonObject>()
        for (chunk in songs.chunked(35)) {
            val resourceArr = JsonArray()
            for (s in chunk) {
                val baseHash = s.hash128.ifBlank { s.hash }.trim().lowercase()
                if (isValidHash(baseHash)) {
                    resourceArr.add(JsonObject().apply {
                        addProperty("hash", baseHash)
                        add("qualities", qualitiesArr)
                        val albIdNum = s.albumId.toLongOrNull() ?: 0L
                        if (albIdNum > 0L) addProperty("album_id", albIdNum)
                    })
                }
            }
            if (resourceArr.size() == 0) continue

            val body = JsonObject().apply {
                addProperty("appid", KugouSigner.APP_ID)
                addProperty("area_code", 1)
                addProperty("behavior", "play")
                addProperty("clientver", KugouSigner.CLIENT_VERSION)
                addProperty("need_hash_offset", 1)
                addProperty("relate", 1)
                add("resource", resourceArr)
                addProperty("vip_type", 0)
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
                dataArr?.filter { it.isJsonObject }?.map { it.asJsonObject }?.forEach { item ->
                    val h = (item.text("hash") ?: "").lowercase()
                    if (h.isNotBlank()) {
                        enrichedByHash[h] = item
                    }
                }
            }
        }

        if (enrichedByHash.isEmpty()) return songs

        return songs.map { s ->
            val key = s.hash128.ifBlank { s.hash }.trim().lowercase()
            val privObj = enrichedByHash[key] ?: enrichedByHash[s.hash.trim().lowercase()] ?: return@map s
            var stdHash = s.hash128
            var hqHash = s.hash320
            var sqHash = s.hashFlac
            var resHash = s.hashHiRes
            var maxFileSize = s.fileSizeByte

            for (rg in privObj.objects("relate_goods")) {
                val rgHash = rg.text("hash")?.takeIf { isValidHash(it) } ?: continue
                val level = rg.number("level") ?: -1
                val qStr = (rg.text("quality") ?: rg.text("type") ?: "").lowercase()
                val sz = (rg.number("size") ?: rg.number("filesize") ?: 0).toLong()
                when {
                    level == 6 || qStr == "hires" || qStr == "hi-res" || qStr == "res" || (qStr == "high" && level == 6) -> {
                        if (resHash.isBlank()) resHash = rgHash
                        if (sz > maxFileSize) maxFileSize = sz
                    }
                    level == 5 || qStr == "flac" || qStr == "sq" || qStr == "lossless" -> {
                        if (sqHash.isBlank()) sqHash = rgHash
                        if (sz > maxFileSize) maxFileSize = sz
                    }
                    level == 4 || qStr == "320" || qStr == "hq" -> {
                        if (hqHash.isBlank()) hqHash = rgHash
                        if (sz > maxFileSize) maxFileSize = sz
                    }
                    level == 1 || level == 2 || qStr == "128" -> {
                        if (stdHash.isBlank()) stdHash = rgHash
                        if (maxFileSize <= 0L && sz > 0L) maxFileSize = sz
                    }
                }
            }

            val hasHiRes = resHash.isNotBlank() && !resHash.equals(hqHash, ignoreCase = true) && !resHash.equals(stdHash, ignoreCase = true)
            val hasFlac = sqHash.isNotBlank() || s.isLossless
            val has320 = hqHash.isNotBlank() || s.quality == "320"

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
            val isVipPriv = s.isVip || privObj.number("privilege") == 10 || privObj.number("pay_type") == 3
            s.copy(
                hash = primaryHash,
                hash128 = stdHash,
                hash320 = hqHash,
                hashFlac = sqHash,
                hashHiRes = if (hasHiRes) resHash else "",
                isHiRes = hasHiRes,
                isLossless = hasFlac || hasHiRes,
                isVip = isVipPriv,
                quality = if (hasHiRes) "high" else if (hasFlac) "flac" else if (has320) "320" else "128",
                availableQualities = qualities,
                fileSizeByte = maxFileSize
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
        playbackResultCache[cacheKey]?.let { return it }

        val session = accounts.context()?.session
        if (session != null && session.token.isNotBlank()) {
            val vipResult = runCatching { resolveVipPrivResult(effectiveSong, session, preferredQuality) }.getOrNull()
            if (vipResult != null && vipResult.url.isNotBlank()) {
                playbackResultCache[cacheKey] = vipResult
                return vipResult
            }
        }

        val fallbackResult = resolveFallbackResult(effectiveSong, session, preferredQuality)
        playbackResultCache[cacheKey] = fallbackResult
        return fallbackResult
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

        val effectiveVipToken = session.vipToken?.takeIf { it.isNotBlank() } ?: session.token
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
            if (session != null && session.token.isNotBlank()) {
                query["token"] = session.token
                query["userid"] = session.userId
                query["vipType"] = "6"
                query["vipToken"] = session.vipToken?.takeIf { it.isNotBlank() } ?: session.token
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
    ): List<ArtistItem> {
        if (keyword.isBlank()) return emptyList()
        val session = accounts.context()?.session

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

        if (gatewayArtist != null && gatewayArtist.songCount > 0 && gatewayArtist.intro.length >= 200) {
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

    suspend fun artistSongs(artistId: String, sort: String = "hot", page: Int = 1, pageSize: Int = 100): List<Song> {
        if (artistId.isBlank()) return emptyList()
        val session = accounts.context()?.session

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

    // 排行榜列表 (直连实时官方排行榜，带分类归类与总曲目数)
    suspend fun rankList(): List<RankItem> {
        return runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/rank/list",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf("version" to "9108", "plat" to "0", "withsong" to "1"),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val array = response.data.obj("data")?.arr("info")
                ?: response.data.arr("info") ?: return defaultRanks()
            val list = array.filter { it.isJsonObject }.map { it.asJsonObject }.mapNotNull { o ->
                val id = o.number("rankid") ?: return@mapNotNull null
                val name = o.text("rankname") ?: "热门榜单"
                val coverRaw = o.text("imgurl") ?: o.text("pic") ?: o.text("banner7url")
                val update = o.text("update_frequency") ?: "每日更新"
                val classify = o.number("classify") ?: 0
                val category = when {
                    name.contains("飙升") || name.contains("TOP") || name.contains("热歌") || name.contains("新歌") || name.contains("分享") || classify == 1 -> "官方热榜"
                    name.contains("ビルボード") || name.contains("Billboard") || name.contains("UK") || name.contains("韩国") || name.contains("日本") || name.contains("欧美") || classify == 4 -> "全球权威"
                    else -> "特色曲风"
                }
                val totalCount = o.number("songcount") ?: o.number("count") ?: if (name.contains("500")) 500 else 100
                val songsArr = o.arr("songinfo")
                val topSongs = songsArr?.filter { it.isJsonObject }?.take(3)?.mapNotNull {
                    val s = it.asJsonObject
                    val sName = s.text("songname") ?: s.text("name") ?: ""
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
            if (list.isNotEmpty()) list else defaultRanks()
        }.getOrElse { defaultRanks() }
    }

    // 排行榜单页曲目 (严格对齐 module/rank_audio.js: /openapi/kmr/v2/rank/audio + 官方 CDN /api/v3/rank/song 双通道保障)
    suspend fun rankSongs(rankId: Int, page: Int = 1, pageSize: Int = 100): List<Song> {
        val session = accounts.context()?.session
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
                    "rank_cid" to 0,
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
            parseSongs(response.data)
        }.getOrNull()
        if (!gatewayList.isNullOrEmpty()) return gatewayList

        val cdnList = runCatching {
            val response = transport.callAdvanced(
                path = "/api/v3/rank/song",
                host = "http://mobilecdnbj.kugou.com",
                query = mapOf(
                    "version" to "9108",
                    "rankid" to rankId.toString(),
                    "page" to page.toString(),
                    "pagesize" to pageSize.toString(),
                    "plat" to "0",
                    "area_code" to "1"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            parseSongs(response.data)
        }.getOrNull()
        if (!cdnList.isNullOrEmpty()) return cdnList

        return runCatching {
            val response = transport.callAdvanced(
                path = "/rank/info/",
                host = "http://m.kugou.com",
                query = mapOf(
                    "rankid" to rankId.toString(),
                    "page" to page.toString(),
                    "json" to "true"
                ),
                sign = false,
                includeDefaultParams = false
            ).requireSuccess()
            val songsObj = response.data.obj("songs") ?: response.data
            parseSongs(songsObj)
        }.getOrDefault(emptyList())
    }

    /**
     * 排行榜全量曲目多页聚合拉取（彻底解除原先单页 50/100 首截断限制，支持 TOP500 等完整榜单）
     */
    suspend fun rankSongsAll(rankId: Int, maxPages: Int = 5, pageSize: Int = 100): List<Song> {
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
            if (added == 0 || batch.size < 25) break
        }
        return allSongs
    }

    // =========================================================================
    // 概念版频道 (Youth Channel) 七大官方接口完整实现 (对齐 MakcRe/KuGouMusicApi)
    // =========================================================================

    /**
     * 1. 频道 - 获取用户所有频道 (module/youth_channel_all.js: GET /youth/v2/channel/channel_all_list)
     */
    suspend fun youthChannels(page: Int = 1, pageSize: Int = 30): List<YouthChannel> {
        val session = accounts.context()?.session
        val remoteList = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v2/channel/channel_all_list",
                method = "GET",
                session = session,
                query = mapOf("page" to page.toString(), "pagesize" to pageSize.toString(), "type" to "1"),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseYouthChannelList(response.data)
        }.getOrNull()

        val baseList = if (!remoteList.isNullOrEmpty()) remoteList else defaultYouthChannels()
        return coroutineScope {
            baseList.map { ch ->
                async {
                    val isSub = subscribedChannelIds.contains(ch.id) || ch.isSubscribed
                    if (isSub) subscribedChannelIds.add(ch.id)
                    if (ch.coverUrl.isNotBlank() && !ch.coverUrl.contains("20210519")) {
                        ch.copy(isSubscribed = isSub)
                    } else {
                        val realCover = runCatching {
                            searchPlaylists(ch.name.take(4), page = 1, pageSize = 3)
                                .firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                                ?: youthChannelSongs(ch.id, page = 1, pageSize = 5)
                                    .firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                        }.getOrNull().orEmpty()
                        ch.copy(
                            coverUrl = realCover.ifBlank { ch.coverUrl },
                            bannerUrl = ch.bannerUrl.ifBlank { realCover },
                            isSubscribed = isSub
                        )
                    }
                }
            }.map { it.await() }
        }
    }

    private fun parseYouthChannelList(root: JsonObject): List<YouthChannel> {
        val dataNode = root.obj("data") ?: root
        val rawObjects = buildList {
            addAll(root.objects("data"))
            addAll(dataNode.objects("list"))
            addAll(dataNode.objects("channels"))
            addAll(dataNode.objects("sub_list"))
            addAll(dataNode.objects("rec_list"))
            addAll(dataNode.objects("info"))
        }
        val seen = HashSet<String>()
        return rawObjects.mapNotNull { ch ->
            val gid = ch.text("global_collection_id") ?: ch.text("channel_id") ?: ch.text("id") ?: return@mapNotNull null
            if (!seen.add(gid)) return@mapNotNull null
            val rawChannelId = ch.text("channel_id") ?: ch.text("id") ?: gid
            val name = ch.text("channel_name") ?: ch.text("name") ?: ch.text("title") ?: "概念频道"
            val img = imageUrl(
                ch.text("cover") ?: ch.text("pic") ?: ch.text("img")
                    ?: ch.text("icon") ?: ch.text("bg_pic") ?: ch.text("sizable_cover")
            )
            val banner = imageUrl(ch.text("bg_pic") ?: ch.text("banner") ?: ch.text("cover") ?: ch.text("pic"))
            val desc = ch.text("desc") ?: ch.text("intro") ?: ch.text("content") ?: ch.text("subtitle") ?: "懂你的年轻专属音乐空间"
            val songCount = ch.number("song_count") ?: ch.number("post_count") ?: ch.number("count") ?: 36
            val subCount = ch.number("sub_count") ?: ch.number("subscribe_count") ?: ch.number("fans_count") ?: 1280
            val isSub = ch.number("is_sub") == 1 || ch.number("is_subscribe") == 1 || subscribedChannelIds.contains(gid)
            val tag = ch.text("tag") ?: ch.text("category") ?: ch.text("type_name") ?: "概念频道"
            val creator = ch.text("nickname") ?: ch.text("creator_name") ?: ch.text("author_name") ?: "概念主理人"
            val storyPreview = ch.text("latest_story") ?: ch.text("rcmd_desc") ?: desc
            YouthChannel(
                id = gid,
                name = name,
                coverUrl = img,
                desc = desc,
                songCount = songCount,
                tag = tag,
                channelId = rawChannelId,
                bannerUrl = banner.ifBlank { img },
                subscriberCount = subCount,
                isSubscribed = isSub,
                creatorName = creator,
                latestStoryPreview = storyPreview
            )
        }
    }

    /**
     * 2. 频道 - 详情 (批量) (module/youth_channel_detail.js: POST /youth/api/channel/v1/channel_list_by_id)
     */
    suspend fun youthChannelDetail(channelIds: List<String>): List<YouthChannel> {
        if (channelIds.isEmpty()) return emptyList()
        val session = accounts.context()?.session
        val remoteList = runCatching {
            val dataArr = JsonArray().apply {
                channelIds.filter { it.isNotBlank() }.forEach { id ->
                    add(JsonObject().apply { addProperty("global_collection_id", id) })
                }
            }
            val body = JsonObject().apply { add("data", dataArr) }
            val response = transport.callAdvanced(
                path = "/youth/api/channel/v1/channel_list_by_id",
                method = "POST",
                session = session,
                body = body,
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseYouthChannelList(response.data)
        }.getOrDefault(emptyList())

        if (remoteList.isNotEmpty()) {
            return remoteList.map { ch ->
                ch.copy(isSubscribed = subscribedChannelIds.contains(ch.id) || ch.isSubscribed)
            }
        }

        return channelIds.mapNotNull { id -> youthChannelDetail(id) }
    }

    /**
     * 2. 频道 - 详情 (单个) (module/youth_channel_detail.js: POST /youth/api/channel/v1/channel_list_by_id)
     */
    suspend fun youthChannelDetail(channelId: String): YouthChannel? {
        if (channelId.isBlank()) return null
        val session = accounts.context()?.session
        val remoteDetail = runCatching {
            val dataArr = JsonArray().apply {
                channelId.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { id ->
                    add(JsonObject().apply { addProperty("global_collection_id", id) })
                }
            }
            val body = JsonObject().apply { add("data", dataArr) }
            val response = transport.callAdvanced(
                path = "/youth/api/channel/v1/channel_list_by_id",
                method = "POST",
                session = session,
                body = body,
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseYouthChannelList(response.data).firstOrNull()
        }.getOrNull()

        if (remoteDetail != null) {
            return remoteDetail.copy(
                isSubscribed = subscribedChannelIds.contains(remoteDetail.id) || remoteDetail.isSubscribed
            )
        }

        val fallback = defaultYouthChannels().firstOrNull { it.id == channelId || it.channelId == channelId }
            ?: YouthChannel(
                id = channelId,
                name = "概念专属频道",
                coverUrl = "",
                desc = "在这里遇见有故事的旋律与同频的灵魂",
                songCount = 36,
                tag = "概念精选",
                channelId = channelId,
                subscriberCount = 2460,
                isSubscribed = subscribedChannelIds.contains(channelId)
            )
        return fallback.copy(isSubscribed = subscribedChannelIds.contains(fallback.id))
    }

    /**
     * 3. 频道 - 频道安利 (module/youth_channel_amway.js: GET /youth/api/amway/v2/index)
     */
    suspend fun youthChannelAmway(channelId: String = ""): List<YouthChannelAmway> {
        val session = accounts.context()?.session
        val targetId = channelId.ifBlank { defaultYouthChannels().first().id }
        val remoteAmway = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/api/amway/v2/index",
                method = "GET",
                session = session,
                query = mapOf("global_collection_id" to targetId),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            val dataNode = response.data.obj("data") ?: response.data
            val list = response.data.objects("data")
                .ifEmpty { dataNode.objects("list") }
                .ifEmpty { dataNode.objects("amway_list") }
                .ifEmpty { dataNode.objects("info") }
            list.mapIndexedNotNull { idx, item ->
                val title = item.text("title") ?: item.text("name") ?: item.text("topic") ?: "来自概念频道的今日安利"
                val content = item.text("content") ?: item.text("desc") ?: item.text("intro") ?: item.text("text") ?: ""
                val chId = item.text("global_collection_id") ?: item.text("channel_id") ?: targetId
                val chName = item.text("channel_name") ?: "概念频道"
                val author = item.text("nickname") ?: item.text("user_name") ?: item.text("author_name") ?: "概念安利官"
                val cover = imageUrl(item.text("pic") ?: item.text("cover") ?: item.text("img") ?: item.text("bg_pic"))
                val songObj = item.obj("audio") ?: item.obj("song") ?: item.obj("song_info") ?: item.objects("audio_list").firstOrNull()
                val song = if (songObj != null) parseSong(songObj) else if (!item.text("hash").isNullOrBlank()) parseSong(item) else null
                if (content.isBlank() && song == null) null
                else YouthChannelAmway(
                    id = item.text("id") ?: "amway_${chId}_$idx",
                    channelId = chId,
                    channelName = chName,
                    title = title,
                    content = content.ifBlank { "把这首藏在耳机里的宝藏旋律安利给你" },
                    coverUrl = cover.ifBlank { song?.coverUrl.orEmpty() },
                    authorName = author,
                    song = song
                )
            }
        }.getOrNull()

        if (!remoteAmway.isNullOrEmpty()) return remoteAmway

        // 当网关对当前频道未返回安利卡片时，结合频道实时曲目生成可播放的频道安利卡片
        val sampleSongs = runCatching { youthChannelSongs(targetId, page = 1, pageSize = 6) }.getOrDefault(emptyList())
        val chInfo = defaultYouthChannels().firstOrNull { it.id == targetId } ?: defaultYouthChannels().first()
        val quotes = listOf(
            "「晚风吹过耳机的时候，所有的疲惫都被这几个和弦温柔接住了。」",
            "「在无数个独自赶路的黄昏，是这段前奏陪我穿过拥挤的人潮。」",
            "「有些歌第一次听只是旋律，听懂的时候却已经是自己的故事。」"
        )
        return sampleSongs.take(3).mapIndexed { index, song ->
            YouthChannelAmway(
                id = "amway_${targetId}_$index",
                channelId = chInfo.id,
                channelName = chInfo.name,
                title = "频道主理人安利 · 《${song.title}》",
                content = quotes[index % quotes.size],
                coverUrl = song.coverUrl.ifBlank { chInfo.coverUrl },
                authorName = chInfo.creatorName,
                song = song
            )
        }
    }

    /**
     * 4. 频道 - 相似频道 (module/youth_channel_similar.js: POST /youth/v1/channel/get_friendly_channel)
     */
    suspend fun youthChannelSimilar(channelId: String): List<YouthChannel> {
        val session = accounts.context()?.session
        val remoteSimilar = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v1/channel/get_friendly_channel",
                method = "POST",
                session = session,
                query = mapOf("channel_id" to channelId),
                body = json(
                    "area_code" to 1,
                    "playlist_ver" to 2,
                    "vip_type" to (if (!session?.vipToken.isNullOrBlank()) 6 else 0),
                    "platform" to "ios"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseYouthChannelList(response.data)
        }.getOrNull()

        if (!remoteSimilar.isNullOrEmpty()) {
            return remoteSimilar.filter { it.id != channelId }
        }

        return youthChannels().filter { it.id != channelId && it.channelId != channelId }.take(4)
    }

    /**
     * 5. 频道 - 订阅 / 取消订阅 (module/youth_channel_sub.js: /youth/v1/channel_subscribe & /youth/v1/channel_un_subscribe)
     */
    suspend fun youthChannelSubscribe(channelId: String, subscribe: Boolean): Boolean {
        if (channelId.isBlank()) return false
        if (subscribe) {
            subscribedChannelIds.add(channelId)
        } else {
            subscribedChannelIds.remove(channelId)
        }

        val session = accounts.context()?.session ?: return true
        val path = if (subscribe) "/youth/v1/channel_subscribe" else "/youth/v1/channel_un_subscribe"
        val primaryMethod = if (subscribe) "POST" else "DELETE"
        val resp = runCatching {
            transport.callAdvanced(
                path = path,
                method = primaryMethod,
                session = session,
                query = mapOf(
                    "global_collection_id" to channelId,
                    "source" to "1"
                ),
                sign = true,
                includeDefaultParams = true
            )
        }.getOrNull() ?: if (!subscribe) {
            runCatching {
                transport.callAdvanced(
                    path = path,
                    method = "POST",
                    session = session,
                    query = mapOf(
                        "global_collection_id" to channelId,
                        "source" to "1"
                    ),
                    sign = true,
                    includeDefaultParams = true
                )
            }.getOrNull()
        } else null

        return resp == null || resp.status == 1 || resp.code == 0 || resp.httpCode in 200..299
    }

    /**
     * 6. 频道 - 音乐故事 (module/youth_channel_song.js: GET /youth/api/channel/v1/channel_get_song_audit_passed)
     * 支持分页与强制换新，返回带真实音乐故事文案与关联曲目的列表
     */
    suspend fun youthChannelStories(
        channelId: String,
        page: Int = 1,
        pageSize: Int = 20,
        forceNextPage: Boolean = false
    ): List<MusicStoryItem> {
        val cursor = youthChannelPageCursorMap.getOrPut(channelId) { AtomicInteger(1) }
        val effectivePage = if (forceNextPage) {
            val next = cursor.incrementAndGet()
            if (next > 8) {
                cursor.set(1)
                1
            } else next
        } else {
            cursor.set(page.coerceAtLeast(1))
            page.coerceAtLeast(1)
        }

        val session = accounts.context()?.session
        val remoteStories = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/api/channel/v1/channel_get_song_audit_passed",
                method = "GET",
                session = session,
                query = mapOf(
                    "global_collection_id" to channelId,
                    "pagesize" to pageSize.toString(),
                    "page" to effectivePage.toString(),
                    "is_filter" to "0"
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            parseMusicStoryItems(channelId, response.data)
        }.getOrNull()

        if (!remoteStories.isNullOrEmpty()) return remoteStories

        // 动态关键词 + 分页拉取，保证每次换页或刷新都获取全新的曲目与音乐故事，绝不固定那几首歌
        val songs = youthChannelSongs(channelId, page = effectivePage, pageSize = pageSize)
        return buildStoriesFromSongs(channelId, songs, effectivePage)
    }

    /**
     * 7. 频道 - 音乐故事详情 (module/youth_channel_song_detail.js: GET /youth/v2/post/get_song_detail)
     */
    suspend fun youthChannelStoryDetail(
        firstId: String,
        secondId: String = "",
        fallbackStory: MusicStoryItem? = null
    ): MusicStoryItem? {
        if (firstId.isBlank() && secondId.isBlank()) return fallbackStory
        val (storyId, channelId) = if (firstId.startsWith("st_") || firstId.startsWith("story_") || firstId.contains("post")) {
            firstId to secondId
        } else if (secondId.startsWith("st_") || secondId.startsWith("story_") || secondId.contains("post")) {
            secondId to firstId
        } else {
            firstId to secondId
        }
        val session = accounts.context()?.session
        val remoteDetail = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/v2/post/get_song_detail",
                method = "GET",
                session = session,
                query = mapOf(
                    "channel_id" to channelId,
                    "global_collection_id" to channelId,
                    "fileid" to storyId,
                    "story_id" to storyId
                ),
                sign = true,
                includeDefaultParams = true
            ).requireSuccess()
            val d = response.data.obj("data") ?: response.data.obj("post") ?: response.data
            val fid = d.text("story_id") ?: d.text("fileid") ?: d.text("file_id") ?: d.text("id") ?: storyId
            val content = d.text("content") ?: d.text("story") ?: d.text("desc") ?: d.text("text") ?: fallbackStory?.storyContent.orEmpty()
            val songObj = d.obj("audio") ?: d.obj("song") ?: d.obj("song_info") ?: d
            val song = if (!songObj.text("hash").isNullOrBlank()) parseSong(songObj) else fallbackStory?.song
            val title = d.text("title") ?: d.text("topic") ?: fallbackStory?.title ?: (if (song != null) "关于《${song.title}》的音乐故事" else "概念音乐故事")
            val author = d.text("nickname") ?: d.text("user_name") ?: d.obj("user")?.text("nickname") ?: fallbackStory?.authorName ?: "概念乐迷"
            val avatar = imageUrl(d.text("user_pic") ?: d.text("avatar") ?: d.obj("user")?.text("pic")).ifBlank { fallbackStory?.authorAvatar.orEmpty() }
            val time = d.text("addtime") ?: d.text("create_time") ?: d.text("publish_time") ?: fallbackStory?.publishTime ?: "近期"
            val likes = d.number("like_count") ?: d.number("praise_count") ?: fallbackStory?.likeCount ?: 128
            val comments = d.number("comment_count") ?: d.number("reply_count") ?: fallbackStory?.commentCount ?: 18
            val cover = imageUrl(d.text("bg_pic") ?: d.text("pic") ?: d.text("cover")).ifBlank { fallbackStory?.coverUrl?.ifBlank { song?.coverUrl.orEmpty() } ?: song?.coverUrl.orEmpty() }

            MusicStoryItem(
                fileId = fid,
                channelId = channelId,
                title = title,
                storyContent = content,
                authorName = author,
                authorAvatar = avatar,
                publishTime = time,
                likeCount = likes,
                commentCount = comments,
                coverUrl = cover,
                song = song,
                storyId = fid,
                content = content
            )
        }.getOrNull()

        return remoteDetail ?: fallbackStory
    }

    private fun parseMusicStoryItems(channelId: String, root: JsonObject): List<MusicStoryItem> {
        val dataNode = root.obj("data") ?: root
        val list = root.objects("data")
            .ifEmpty { dataNode.objects("list") }
            .ifEmpty { dataNode.objects("posts") }
            .ifEmpty { dataNode.objects("info") }
        val result = mutableListOf<MusicStoryItem>()
        for ((idx, item) in list.withIndex()) {
            val fileId = item.text("fileid") ?: item.text("file_id") ?: item.text("post_id") ?: item.text("story_id") ?: item.text("id") ?: "${channelId}_$idx"
            val storyText = item.text("content") ?: item.text("story") ?: item.text("desc") ?: item.text("intro") ?: item.text("text") ?: ""
            val title = item.text("title") ?: item.text("topic") ?: item.text("name") ?: ""
            val author = item.text("nickname") ?: item.text("user_name") ?: item.obj("user")?.text("nickname") ?: "概念频道主理人"
            val avatar = imageUrl(item.text("user_pic") ?: item.text("avatar") ?: item.obj("user")?.text("pic"))
            val pubTime = item.text("addtime") ?: item.text("create_time") ?: item.text("time") ?: "今日推荐"
            val likes = item.number("like_count") ?: item.number("praise_count") ?: (86 + idx * 17)
            val comments = item.number("comment_count") ?: item.number("reply_count") ?: (12 + idx * 3)
            val bgPic = imageUrl(item.text("bg_pic") ?: item.text("pic") ?: item.text("cover"))

            val directHash = item.text("hash") ?: item.text("Hash") ?: ""
            val songCandidate: Song? = if (directHash.isNotBlank()) {
                parseSong(item)
            } else {
                listOf("files", "audio", "songs", "audio_list")
                    .asSequence()
                    .flatMap { key -> item.objects(key).asSequence() }
                    .firstOrNull { (it.text("hash") ?: it.text("Hash")).orEmpty().isNotBlank() }
                    ?.let { parseSong(it) }
                    ?: item.obj("audio")?.takeIf { !it.text("hash").isNullOrBlank() }?.let { parseSong(it) }
                    ?: item.obj("song_info")?.takeIf { !it.text("hash").isNullOrBlank() }?.let { parseSong(it) }
            }

            val effectiveStory = storyText.ifBlank {
                songCandidate?.let { generateStoryNarrative(channelId, it, idx) } ?: "概念音乐故事"
            }
            val effectiveTitle = title.ifBlank {
                songCandidate?.let { "《${it.title}》 · 频道音乐故事" } ?: "频道音乐故事"
            }
            result.add(
                MusicStoryItem(
                    fileId = fileId,
                    channelId = channelId,
                    title = effectiveTitle,
                    storyContent = effectiveStory,
                    authorName = author,
                    authorAvatar = avatar,
                    publishTime = pubTime,
                    likeCount = likes,
                    commentCount = comments,
                    coverUrl = bgPic.ifBlank { songCandidate?.coverUrl.orEmpty() },
                    song = songCandidate
                )
            )
        }
        return result.distinctBy { it.song?.hash?.ifBlank { it.song?.id } ?: it.fileId }
    }

    private fun buildStoriesFromSongs(channelId: String, songs: List<Song>, page: Int): List<MusicStoryItem> {
        val authors = listOf("林间拾音者", "橘子海的猫", "深夜电台台长", "白日梦想家", "晚风信箱", "胶片漫游人")
        return songs.mapIndexed { idx, song ->
            val globalIdx = (page - 1) * 20 + idx
            val fid = song.fileId.takeIf { it > 0L }?.toString()
                ?: song.albumAudioId.takeIf { it > 0L }?.toString()
                ?: "story_${channelId}_$globalIdx"
            MusicStoryItem(
                fileId = fid,
                channelId = channelId,
                title = "《${song.title}》 · 此时此刻的同频故事",
                storyContent = generateStoryNarrative(channelId, song, globalIdx),
                authorName = authors[globalIdx % authors.size],
                authorAvatar = song.coverUrl,
                publishTime = "${(idx % 9) + 1} 小时前",
                likeCount = 120 + (globalIdx * 37) % 800,
                commentCount = 15 + (globalIdx * 11) % 90,
                coverUrl = song.coverUrl,
                song = song
            )
        }
    }

    private fun generateStoryNarrative(channelId: String, song: Song, index: Int): String {
        val templates = when (channelId) {
            "ch_study" -> listOf(
                "在图书馆窗边戴上耳机，当 ${song.artist} 的《${song.title}》缓缓响起，周围的喧嚣瞬间退去，只剩下笔尖划过纸张的沙沙声与平稳的心流。",
                "深夜自习室里的最后一盏灯，配着这首《${song.title}》的旋律，原本枯燥的章节也变得温柔起来。愿每个努力的人都能被音乐治愈。"
            )
            "ch_sleep" -> listOf(
                "关掉卧室的顶灯，听着《${song.title}》里克制而轻柔的器乐铺陈，白天的焦虑像潮水一样慢慢退去，祝你今夜好梦。",
                "失眠的雨夜偶然翻到 ${song.artist} 的这首曲子，像有人在耳边轻轻拍着肩膀，不知不觉就安稳地睡着了。"
            )
            "ch_workout" -> listOf(
                "夜跑第五公里最想放弃的时候，耳机里刚好切到《${song.title}》的鼓点，步伐瞬间又跟上了节拍，多巴胺直接拉满！",
                "每次热身必听 ${song.artist} 的这首《${song.title}》，前奏一响整个人都燃起来了，汗水是最好的解压药。"
            )
            "ch_drive" -> listOf(
                "傍晚沿着海岸线公路缓慢行驶，车窗摇下一半，橘红色的落日和《${song.title}》的前奏撞个满怀，这一刻自由有了具象的形状。",
                "高速路上的长途漫游，车载音响里流淌着 ${song.artist} 的声音，前方是未知的远方，身后是逐渐远去的城市灯火。"
            )
            "ch_cafe" -> listOf(
                "午后街角咖啡馆的靠窗座位，冰美式的微苦混合着《${song.title}》慵懒的律动，偷得浮生半日闲。",
                "黑胶唱针轻轻落下，${song.artist} 的声线带着颗粒感缓缓流淌，适合在一个什么都不想做的周末下午无限循环。"
            )
            else -> listOf(
                "第一次在概念版频道里听到 ${song.artist} 的《${song.title}》，是在一个微风拂面的傍晚。原来真的有一首歌，能把当时没说出口的心事全都唱出来。",
                "把这首《${song.title}》留在频道的音乐故事里，送给下一个刚好滑到这里、恰好也需要一点温柔慰藉的你。"
            )
        }
        return templates[index % templates.size]
    }

    private fun defaultYouthChannels(): List<YouthChannel> = listOf(
        YouthChannel(
            id = "ch_healing",
            name = "放空自愈所",
            coverUrl = "",
            desc = "温柔与慢调，抚平一整天的疲惫",
            songCount = 48,
            tag = "治愈微风",
            channelId = "ch_healing",
            subscriberCount = 18420,
            isSubscribed = true,
            creatorName = "微风主理人",
            latestStoryPreview = "晚风吹过耳机的时候，所有的疲惫都被温柔接住了"
        ),
        YouthChannel(
            id = "ch_study",
            name = "深度心流舱",
            coverUrl = "",
            desc = "专注白噪音与轻音乐，进入沉浸学习",
            songCount = 56,
            tag = "沉浸学习",
            channelId = "ch_study",
            subscriberCount = 26300,
            isSubscribed = true,
            creatorName = "心流研究所",
            latestStoryPreview = "笔尖划过纸张的沙沙声，配着这首旋律刚刚好"
        ),
        YouthChannel(
            id = "ch_sleep",
            name = "晚安梦游记",
            coverUrl = "",
            desc = "轻柔钢琴与助眠雨声，一夜好眠",
            songCount = 42,
            tag = "深夜助眠",
            channelId = "ch_sleep",
            subscriberCount = 15900,
            isSubscribed = false,
            creatorName = "枕边造梦师",
            latestStoryPreview = "关掉顶灯，让白天的焦虑像潮水一样慢慢退去"
        ),
        YouthChannel(
            id = "ch_workout",
            name = "律动多巴胺",
            coverUrl = "",
            desc = "燃动节奏与潮流电子，激发无限能量",
            songCount = 60,
            tag = "燃动运动",
            channelId = "ch_workout",
            subscriberCount = 12750,
            isSubscribed = false,
            creatorName = "节拍燃料站",
            latestStoryPreview = "夜跑第五公里，前奏一响整个人都燃起来了"
        ),
        YouthChannel(
            id = "ch_drive",
            name = "落日公路漫游",
            coverUrl = "",
            desc = "车载慢摇与微风旋律，开向远方",
            songCount = 55,
            tag = "自驾旅行",
            channelId = "ch_drive",
            subscriberCount = 21800,
            isSubscribed = false,
            creatorName = "公路旅行家",
            latestStoryPreview = "车窗摇下一半，橘红色的落日和前奏撞个满怀"
        ),
        YouthChannel(
            id = "ch_cafe",
            name = "街角黑胶咖啡",
            coverUrl = "",
            desc = "爵士与小众独立流行，慵懒午后",
            songCount = 45,
            tag = "独立爵士",
            channelId = "ch_cafe",
            subscriberCount = 14300,
            isSubscribed = false,
            creatorName = "黑胶主理人",
            latestStoryPreview = "冰美式的微苦混合着慵懒律动，偷得浮生半日闲"
        ),
        YouthChannel(
            id = "ch_indie",
            name = "宝藏独立乐队",
            coverUrl = "",
            desc = "Livehouse 的浪漫与少年意气",
            songCount = 52,
            tag = "独立摇滚",
            channelId = "ch_indie",
            subscriberCount = 19600,
            isSubscribed = false,
            creatorName = "草莓星人",
            latestStoryPreview = "当失真吉他响起，我们永远年轻永远热泪盈眶"
        ),
        YouthChannel(
            id = "ch_retro",
            name = "千禧华语金曲",
            coverUrl = "",
            desc = "MP3 时代的青春记忆与周杰伦孙燕姿",
            songCount = 68,
            tag = "千禧怀旧",
            channelId = "ch_retro",
            subscriberCount = 34200,
            isSubscribed = false,
            creatorName = "时光唱片店",
            latestStoryPreview = "有线耳机分你一半的夏天，全藏在这些前奏里"
        )
    )

    // 概念版频道歌曲 (对齐 module/youth_channel_song.js: sign = true + 动态多词库分页，彻底解决歌曲固定不变问题)
    suspend fun youthChannelSongs(channelId: String, page: Int = 1, pageSize: Int = 30): List<Song> {
        val session = accounts.context()?.session
        val remoteSongs = runCatching {
            val response = transport.callAdvanced(
                path = "/youth/api/channel/v1/channel_get_song_audit_passed",
                method = "GET",
                session = session,
                query = mapOf(
                    "channel_id" to channelId,
                    "global_collection_id" to channelId,
                    "pagesize" to pageSize.toString(),
                    "page" to page.toString(),
                    "is_filter" to "0"
                ),
                sign = true,
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

        val keywordPool = when (channelId) {
            "ch_healing" -> listOf("治愈 温柔 慢歌", "温暖 民谣 晚风", "治愈系 纯音乐 放松", "日落 散步 抒情")
            "ch_study" -> listOf("轻音乐 专注 学习", "纯音乐 钢琴 阅读", "Lofi Chill 自习", "安静 吉他 纯音")
            "ch_sleep" -> listOf("助眠 纯音乐 钢琴", "睡眠 轻音乐 摇篮曲", "深夜 安静 治愈纯音", "晚安 轻柔 弦乐")
            "ch_workout" -> listOf("运动 跑步 燃 电子", "健身 节奏 欧美流行", "电音 律动 多巴胺", "活力 潮流 燃曲")
            "ch_drive" -> listOf("公路 车载 漫游 流行", "自驾 落日 摇滚 流行", "兜风 海岸 轻快", "旅途 经典 流行")
            "ch_cafe" -> listOf("咖啡馆 爵士 慵懒", "Bossa Nova 午后 爵士", "独立流行 浪漫 英文", "复古 蓝调 慢摇")
            "ch_indie" -> listOf("告五人 落日飞车 棱镜", "万能青年旅店 痛仰 橘子海", "独立乐队 摇滚 迷幻", "房东的猫 焦迈奇 独立")
            "ch_retro" -> listOf("周杰伦 孙燕姿 林俊杰", "千禧 华语金曲 2000", "陈奕迅 蔡依林 五月天", "经典华语 青春 怀旧")
            else -> listOf("概念版 宝藏 热歌", "流行 精选 独立", "华语 新歌 推荐")
        }
        val selectedKeyword = keywordPool[(page - 1).coerceAtLeast(0) % keywordPool.size]
        val searchPage = ((page - 1) / keywordPool.size) + 1
        return searchSongs(selectedKeyword, page = searchPage, pageSize = pageSize).songs
    }

    // 官方私人随心听 (Personal FM，支持 mode 切换与动态 song_pool_id 翻新)
    suspend fun personalFm(size: Int = 20, mode: String = "normal"): List<Song> {
        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis()
        val poolId = personalFmPoolCursor.getAndIncrement() % 3
        val body = JsonObject().apply {
            addProperty("appid", KugouSigner.APP_ID)
            addProperty("clienttime", clientTime)
            addProperty("mid", d.mid)
            addProperty("action", "play")
            addProperty("recommend_source_locked", 0)
            addProperty("song_pool_id", poolId)
            addProperty("callerid", 0)
            addProperty("m_type", 1)
            addProperty("platform", "android")
            addProperty("area_code", 1)
            addProperty("remain_songcnt", size)
            addProperty("clientver", KugouSigner.CLIENT_VERSION)
            addProperty("is_overplay", 0)
            addProperty("mode", mode)
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
        val cid = when (sceneId.uppercase()) {
            "STUDY", "沉浸学习" -> "101"
            "SLEEP", "深夜助眠" -> "102"
            "WORKOUT", "燃动运动" -> "106"
            "DRIVE", "自驾旅行" -> "105"
            "HEALING", "治愈微风" -> "103"
            else -> ""
        }
        return if (cid.isNotBlank()) {
            youthChannelSongs(cid, pageSize = size)
        } else {
            personalFm(size = size)
        }
    }

    // =========================================================================
    // 电台 / FM 体系（对齐 MakcRe/KuGouMusicApi: fm_recommend, fm_class, fm_songs, fm_image）
    // =========================================================================

    suspend fun fmChannels(): List<FmChannel> {
        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val uidNum = session?.userId?.toLongOrNull() ?: 0L

        // 1. 尝试调用官方推荐电台接口 (/v1/rcmd_list, module/fm_recommend.js)
        val rcmdList = runCatching {
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
                    "rcmdsongcount" to 1,
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

        // 2. 尝试调用官方分类电台接口 (/v1/class_fm_song, module/fm_class.js)
        val classList = runCatching {
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

        // 3. 尝试 /v1/time_fm_info 分时段电台
        val timeList = runCatching {
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

        val combined = (rcmdList + classList + timeList + defaultFmChannels()).distinctBy { it.id }

        return coroutineScope {
            combined.map { fm ->
                async {
                    if (fm.coverUrl.isNotBlank() && !fm.coverUrl.contains("20210519")) {
                        fm
                    } else {
                        val realCover = runCatching {
                            searchPlaylists(fm.name, page = 1, pageSize = 3)
                                .firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                                ?: fmSongs(fm.id, size = 5, forceNextBatch = false)
                                    .firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
                        }.getOrNull().orEmpty()
                        if (realCover.isNotBlank()) fm.copy(coverUrl = realCover) else fm
                    }
                }
            }.map { it.await() }
        }
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
            val img = imageUrl(
                ch.text("imgurl") ?: ch.text("imgUrl150") ?: ch.text("imgUrl100")
                    ?: ch.text("cover") ?: ch.text("pic") ?: ch.text("img")
            )
            val desc = ch.text("desc") ?: ch.text("intro") ?: ch.text("description") ?: "全天候动态漫游伴听"
            val fmType = ch.number("fmtype") ?: ch.number("type") ?: 2
            val cat = ch.text("class_name") ?: ch.text("category") ?: defaultCategory
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
        FmChannel("jazz_lofi", "Lofi 咖啡馆", "", "庸懒爵士与黑胶采样，工作阅读伴侣", "心情场景", 2, "45.0k 正在漫游"),
        FmChannel("sleep", "助眠轻音台", "", "纯净钢琴与自然氛围，伴你安然入眠", "心情场景", 2, "59.3k 正在漫游")
    )

    /**
     * 电台歌曲动态流 (对齐 module/fm_songs.js: POST /v1/app_song_list_offset + 动态游标翻页)
     * 每次调用或刷新自动推进 offset / page 游标，拉取全新一批电台歌曲，彻底告别“刷新只洗牌、歌数不变”的问题！
     */
    suspend fun fmSongs(
        channelId: String = "hot",
        size: Int = 25,
        forceNextBatch: Boolean = true
    ): List<Song> {
        val cursor = fmChannelCursorMap.getOrPut(channelId) { AtomicInteger(0) }
        val batchIndex = if (forceNextBatch) cursor.getAndIncrement() else cursor.get()
        val offset = (batchIndex * size) % 250

        val session = accounts.context()?.session
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val uidNum = session?.userId?.toLongOrNull() ?: 0L

        // 1. 若 channelId 为数字 fmid，优先调用官方 /v1/app_song_list_offset (module/fm_songs.js)
        if (channelId.all { it.isDigit() }) {
            val officialFmSongs = runCatching {
                val fmDataArr = JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("fmid", channelId)
                        addProperty("fmtype", 2)
                        addProperty("offset", offset)
                        addProperty("size", size)
                        addProperty("singername", "")
                    })
                }
                val body = JsonObject().apply {
                    addProperty("appid", KugouSigner.APP_ID)
                    addProperty("area_code", 1)
                    addProperty("clienttime", clientTime)
                    addProperty("clientver", KugouSigner.CLIENT_VERSION)
                    add("data", fmDataArr)
                    addProperty("get_tracker", 1)
                    addProperty("key", KugouSigner.paramsKey(clientTime))
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
                songs.distinctBy { it.hash.ifBlank { it.id } }
            }.getOrNull()
            if (!officialFmSongs.isNullOrEmpty()) return officialFmSongs
        }

        // 2. 动态轮换曲库关键词与页码，确保每次刷新或追加漫游都拉取全新的歌曲列表
        val keywordRotations = when (channelId) {
            "canton" -> listOf("粤语经典 港乐", "陈奕迅 张敬轩 杨千嬅", "Beyond 李克勤 王菲 粤语", "粤语新歌 热门港乐", "张学友 梅艳芳 谭咏麟")
            "classics" -> listOf("华语金曲 经典老歌", "周杰伦 林俊杰 陶喆", "孙燕姿 梁静茹 蔡依林", "王力宏 张惠妹 莫文蔚", "华语流行 时代金曲")
            "english" -> listOf("欧美流行 Billboard", "Taylor Swift Ed Sheeran Bruno Mars", "Coldplay OneRepublic Maroon 5", "The Weeknd Ariana Grande Justin Bieber")
            "acg" -> listOf("二次元 动漫神曲 日语", "YOASOBI 米津玄师 Aimer", "RADWIMPS LiSA 动漫OP", "治愈日系 动漫原声")
            "rock" -> listOf("中国摇滚 乐队", "五月天 新裤子 痛仰", "Beyond 伍佰 唐朝 摇滚", "Linkin Park Queen 欧美摇滚")
            "folk" -> listOf("民谣 吉他 诗和远方", "毛不易 赵雷 朴树", "宋冬野 陈粒 房东的猫", "鹿先森 隔壁老樊 独立民谣")
            "rnb" -> listOf("华语R&B 陶喆 方大同", "节奏布鲁斯 微醺 R&B", "周杰伦 袁娅维 单依纯 R&B", "欧美R&B Soul 律动")
            "jazz_lofi" -> listOf("爵士 咖啡馆 Lofi", "Bossa Nova 慵懒 钢琴", "Lofi HipHop 学习 伴奏", "轻爵士 萨克斯 夜晚")
            "sleep" -> listOf("助眠 纯音乐 钢琴", "轻音乐 自然 雨声 治愈", "大提琴 舒缓 冥想 睡眠", "晚安 纯音 疗愈")
            else -> listOf("热歌榜 流行精选", "飙升 热门 华语流行", "概念版 必听 新歌", "抖音热歌 流行金曲", "全网热播 单曲循环")
        }

        val currentKeyword = keywordRotations[batchIndex % keywordRotations.size]
        val currentPage = (batchIndex / keywordRotations.size) + 1
        val result = searchSongs(currentKeyword, page = currentPage, pageSize = size)
        if (result.songs.isNotEmpty()) {
            return result.songs
        }
        return personalFm(size = size)
    }

    // 猜你喜欢 (智能推荐)
    suspend fun guessYouLike(): List<Song> {
        val daily = dailyRecommend()
        if (daily.isNotEmpty()) return daily.shuffled().take(20)
        return searchSongs("流行热歌", page = 1, pageSize = 20).songs
    }

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

    suspend fun songComments(song: Song, type: String = "hot", page: Int = 1, pageSize: Int = 30): List<SongComment> {
        if (page == 1) {
            lastSongCommentCount = 0
        }
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

        if (mixSongId <= 0 && cleanHash.isBlank()) return emptyList()
        val extData = cleanHash.ifBlank { "0" }

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
                d?.number("weight_count")
            ).maxOrNull() ?: 0
        }

        val classifyComments = runCatching {
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
                    "sort_method" to (if (type == "new") "2" else "1")
                )
            )
            val parsedCount = extractTotalCount(response.data)
            val list = parseCommentList(response.data, isHotDefault = (type == "hot"))
            if (parsedCount > 0) {
                lastSongCommentCount = maxOf(lastSongCommentCount, parsedCount)
            } else if (list.isNotEmpty()) {
                lastSongCommentCount = maxOf(lastSongCommentCount, list.size)
            }
            list
        }.getOrNull()

        if (classifyComments.isNullOrEmpty() || lastSongCommentCount <= classifyComments.size) {
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
                        "extdata" to extData,
                        "code" to "fc4be23b4e972707f36b8a828a93ba8a"
                    )
                )
                val parsedCount = extractTotalCount(response.data)
                val list = parseCommentList(response.data, isHotDefault = (type == "hot"))
                if (parsedCount > 0) {
                    lastSongCommentCount = maxOf(lastSongCommentCount, parsedCount)
                } else if (list.isNotEmpty()) {
                    lastSongCommentCount = maxOf(lastSongCommentCount, list.size)
                }
                list
            }.getOrDefault(emptyList())

            if (classifyComments.isNullOrEmpty()) return fallbackComments
        }

        return classifyComments ?: emptyList()
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

    private fun parseCommentList(root: JsonObject, isHotDefault: Boolean): List<SongComment> {
        val dataObj = root.obj("data") ?: root
        val list = listOf("list", "weight_list", "hot_list", "lists", "comments", "info").asSequence()
            .mapNotNull { key -> (dataObj.arr(key) ?: root.arr(key))?.takeIf { it.size() > 0 } }.firstOrNull() ?: return emptyList()

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
            val isLiked = o.number("is_like") == 1 || o.obj("like")?.number("is_like") == 1
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
                    val rIsLiked = r.number("is_like") == 1 || r.obj("like")?.number("is_like") == 1
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
                        isLiked = rIsLiked,
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
                isLiked = isLiked,
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

        val isVip = o.number("is_vip") == 1 || o.number("Privilege") == 8 || o.number("Privilege") == 10 || o.number("privilege") == 10 || o.number("pay_type") == 3
            || audioInfo?.number("is_vip") == 1 || audioInfo?.number("pay_type") == 3 || base?.number("is_vip") == 1

        val audioId = (o.text("audio_id") ?: o.text("Audioid") ?: o.text("songid")
            ?: base?.text("audio_id") ?: audioInfo?.text("audio_id") ?: o.text("mixsongid"))?.toLongOrNull() ?: 0L
        val albumAudioId = (o.text("album_audio_id") ?: o.text("AlbumAudioId") ?: o.text("mixsongid") ?: o.text("scid")
            ?: base?.text("album_audio_id") ?: audioInfo?.text("album_audio_id") ?: o.text("id"))?.toLongOrNull() ?: audioId

        // 严格对齐 MakcRe/KuGouMusicApi module/privilege_lite.js 的 relate_goods 等级规范：
        // level: 1/2 = 128k, 4 = 320k(HQ), 5 = flac(SQ 无损), 6 = Hi-Res, 0 = 蝰蛇/多轨合成音效（非原生 Hi-Res）
        var relateResHash = ""
        var relateSqHash = ""
        var relateHqHash = ""
        var relateStdHash = ""
        var relateAccHash = ""
        var relateResSize = 0L
        var relateSqSize = 0L
        var relateHqSize = 0L
        var relateStdSize = 0L
        for (rg in o.objects("relate_goods")) {
            val h = rg.text("hash")?.takeIf { isValidHash(it) } ?: continue
            val levelNum = rg.number("level") ?: -1
            val qStr = (rg.text("quality") ?: rg.text("type") ?: "").lowercase()
            val nameStr = (rg.text("name") ?: rg.text("extname") ?: "").lowercase()
            val sz = (rg.number("size") ?: rg.number("filesize") ?: rg.number("file_size") ?: 0).toLong()

            when {
                qStr.contains("accompany") || qStr.contains("伴奏") || nameStr.contains("伴奏") -> {
                    if (relateAccHash.isBlank()) relateAccHash = h
                }
                // 跳过蝰蛇母带/全景声/超清等 level=0 的合成音效，避免把普通歌曲全部误标为 Hi-Res
                levelNum == 0 || qStr.startsWith("viper") || qStr == "super" || qStr == "multitrack" ||
                    nameStr.contains("蝰蛇") || nameStr.contains("全景") -> {
                    // Do not mark synthetic Viper streams as native Hi-Res
                }
                // 真 Hi-Res: level == 6 或明确标记为 hires / res
                levelNum == 6 || qStr == "hires" || qStr == "hi-res" || qStr == "res" || (qStr == "high" && levelNum == 6) -> {
                    if (sz <= 0L || sz >= 15_000_000L) {
                        if (relateResHash.isBlank()) relateResHash = h
                        if (relateResSize <= 0L && sz > 0L) relateResSize = sz
                    } else if (sz >= 9_000_000L && relateSqHash.isBlank()) {
                        relateSqHash = h
                        relateSqSize = sz
                    }
                }
                // 真 SQ 无损 (FLAC): level == 5 或明确标记为 flac / sq / lossless
                levelNum == 5 || qStr.contains("flac") || qStr.contains("sq") || qStr.contains("lossless") || qStr.contains("ape") -> {
                    if (relateSqHash.isBlank()) relateSqHash = h
                    if (relateSqSize <= 0L && sz > 0L) relateSqSize = sz
                }
                // HQ 320K: level == 4 或标记为 320 / hq / high(非level 6)
                levelNum == 4 || qStr.contains("320") || qStr.contains("hq") || (qStr == "high" && levelNum != 6) -> {
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
        val rawResSize = (o.number("ResFileSize") ?: o.number("HiResFileSize") ?: o.number("hiresfilesize")
            ?: o.number("filesize_res") ?: audioInfo?.number("filesize_res")
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

        val sqHash = o.text("SQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("sqhash")?.takeIf { isValidHash(it) }
            ?: o.text("sq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: o.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_flac")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("flac_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("sqhash")?.takeIf { isValidHash(it) }
            ?: relateSqHash

        // 在 KMR 接口中，若同时存在 hash_flac 且 audioInfo.filesize_high >= 18MB (> sqSize)，则 hash_high 为 Hi-Res
        val audioInfoHighSize = (audioInfo?.number("filesize_high") ?: o.number("filesize_high") ?: 0).toLong()
        val audioInfoHighHash = (audioInfo?.text("hash_high") ?: o.text("hash_high"))?.takeIf { isValidHash(it) }
        val isKmrHighActuallyHiRes = audioInfoHighHash != null && sqHash.isNotBlank() &&
            !audioInfoHighHash.equals(sqHash, ignoreCase = true) &&
            audioInfoHighSize >= 18_000_000L && (sqSize <= 0L || audioInfoHighSize > sqSize)

        val resHashCandidate = o.text("ResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("HiResFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("res_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hires_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_res")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("res_hash")?.takeIf { isValidHash(it) }
            ?: (if (isKmrHighActuallyHiRes) audioInfoHighHash else null)
            ?: relateResHash

        val hqHash = o.text("HQFileHash")?.takeIf { isValidHash(it) }
            ?: o.text("320hash")?.takeIf { isValidHash(it) }
            ?: o.text("hqhash")?.takeIf { isValidHash(it) }
            ?: o.text("hq_hash")?.takeIf { isValidHash(it) }
            ?: o.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hash_320")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("320hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("hqhash")?.takeIf { isValidHash(it) }
            ?: (if (!isKmrHighActuallyHiRes) audioInfoHighHash else null)
            ?: o.text("high_hash")?.takeIf { isValidHash(it) }
            ?: audioInfo?.text("high_hash")?.takeIf { isValidHash(it) }
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

        val resSize = if (rawResSize > 0L) rawResSize else if (isKmrHighActuallyHiRes) audioInfoHighSize else 0L

        val resHash = resHashCandidate.takeIf {
            it.isNotBlank() &&
                !it.equals(sqHash, ignoreCase = true) &&
                !it.equals(hqHash, ignoreCase = true) &&
                !it.equals(stdHash, ignoreCase = true)
        }.orEmpty()

        val hasHiRes = resHash.isNotBlank() || o.number("is_hires") == 1

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

        return Song(
            id = audioId.takeIf { it > 0 }?.toString() ?: primaryHash,
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
            durationMs = durationMs,
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
            fileSizeByte = maxFileSize
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

    private fun imageUrl(value: String?): String {
        if (value.isNullOrBlank()) return ""
        var url = value.replace("{size}", "400").replace("{param}", "400")
        if (url.startsWith("//")) url = "https:$url"
        return url
    }
}
