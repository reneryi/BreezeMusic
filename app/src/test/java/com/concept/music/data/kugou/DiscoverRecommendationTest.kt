package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiscoverRecommendationTest {

    @Test
    fun newSongExpressStrictlyAuditsSevenDayWindowAndSortsByDateDescThenFirstLetterAsc() = runTest {
        val refDate = LocalDate.of(2026, 10, 17)
        // 7天有效窗口：2026-10-11 至 2026-10-17（当天往前推6天共7天）
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                val page = body?.number("page") ?: 1
                if (path == "/musicadservice/container/v1/newsong_publish" && page == 1) {
                    val mockJson = """
                        {
                            "status": 1,
                            "error_code": 0,
                            "data": {
                                "list": [
                                    {
                                        "songname": "借名入局",
                                        "singername": "刘宇宁",
                                        "hash": "11111111111111111111111111111101",
                                        "audio_id": 80001,
                                        "publish_date": "2026-10-17"
                                    },
                                    {
                                        "songname": "爱上",
                                        "singername": "刘宇宁",
                                        "hash": "11111111111111111111111111111102",
                                        "audio_id": 80002,
                                        "publish_date": "2026-10-17"
                                    },
                                    {
                                        "songname": "光芒",
                                        "singername": "黄子弘凡",
                                        "hash": "11111111111111111111111111111103",
                                        "audio_id": 80003,
                                        "publish_date": "2026-10-17"
                                    },
                                    {
                                        "songname": "晴天",
                                        "singername": "歌手Q",
                                        "hash": "11111111111111111111111111111104",
                                        "audio_id": 80004,
                                        "publish_date": "2026-10-11"
                                    },
                                    {
                                        "songname": "Blue Sky",
                                        "singername": "SingerB",
                                        "hash": "11111111111111111111111111111105",
                                        "audio_id": 80005,
                                        "publish_date": "2026-10-11"
                                    },
                                    {
                                        "songname": "超期老歌_第8天前发行",
                                        "singername": "老歌手",
                                        "hash": "11111111111111111111111111111106",
                                        "audio_id": 80006,
                                        "publish_date": "2026-10-10"
                                    },
                                    {
                                        "songname": "远古歌曲_2025年发行",
                                        "singername": "老歌手2",
                                        "hash": "11111111111111111111111111111107",
                                        "audio_id": 80007,
                                        "publish_date": "2025-05-01"
                                    }
                                ]
                            }
                        }
                    """.trimIndent()
                    return ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
                }
                return ApiResponse(JsonParser.parseString("""{"status":1,"data":[]}""").asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val songs = repo.newSongExpress("CHINESE", referenceDate = refDate)

        // 严格过滤掉 2026-10-10（往前第7天，超出近一周窗口）和 2025-05-01 的老歌，仅保留 2026-10-11 ~ 2026-10-17 共 5 首
        assertEquals(5, songs.size)
        assertTrue(songs.none { it.publishDate == "2026-10-10" || it.publishDate == "2025-05-01" })

        // 验证排序规则：1. 按发行日期降序（2026-10-17 在 2026-10-11 之前）
        // 2. 同一天 (2026-10-17) 内按首字母升序：爱上 (A) -> 光芒 (G) -> 借名入局 (J)
        // 3. 同一天 (2026-10-11) 内按首字母升序：Blue Sky (B) -> 晴天 (Q)
        assertEquals(
            listOf("爱上", "光芒", "借名入局", "Blue Sky", "晴天"),
            songs.map { it.title }
        )
    }

    @Test
    fun rankSongsResolvesLatestDailyVolIdAndRefreshesRealTime() = runTest {
        var requestedVolEndpoint = false
        var passedVolIdToRankSong = ""

        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/api/v3/rank/vol") {
                    requestedVolEndpoint = true
                    val mockVol = """
                        {
                            "status": 1,
                            "data": {
                                "info": [
                                    {"volid": 68901, "volname": "2026-10-17 期"}
                                ]
                            }
                        }
                    """.trimIndent()
                    return ApiResponse(JsonParser.parseString(mockVol).asJsonObject)
                }
                if (path == "/api/v3/rank/song") {
                    passedVolIdToRankSong = query["volid"].orEmpty()
                    val mockSongs = """
                        {
                            "status": 1,
                            "data": {
                                "info": [
                                    {
                                        "filename": "周杰伦 - 今日实时榜首",
                                        "hash": "22222222222222222222222222222201",
                                        "audio_id": 9001
                                    }
                                ]
                            }
                        }
                    """.trimIndent()
                    return ApiResponse(JsonParser.parseString(mockSongs).asJsonObject)
                }
                return ApiResponse(JsonParser.parseString("""{"status":1,"data":[]}""").asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val rankTracks = repo.rankSongs(6666, page = 1, pageSize = 20, enrichPrivilege = false)
        assertTrue(requestedVolEndpoint)
        assertEquals("68901", passedVolIdToRankSong)
        assertEquals(1, rankTracks.size)
        assertEquals("今日实时榜首", rankTracks[0].title)
    }

    @Test
    fun personalFmCombinesMultiEnginePersonalizedRecommendersAndNeverRepeats() = runTest {
        var batchSeq = 0

        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/v2/personal_recommend" || path == "/concepts/v1/ai/recommend_song" || path == "/singlecardrec.service/v1/single_card_recommend") {
                    val seq = synchronized(this) { ++batchSeq }
                    val songsJson = (1..12).joinToString(",") { idx ->
                        val uid = seq * 100 + idx
                        val hashHex = String.format("%032x", uid.toLong())
                        """
                        {
                            "hash": "$hashHex",
                            "songname": "个性推荐_$uid",
                            "singername": "偏好歌手_$uid",
                            "audio_id": "${90000 + uid}",
                            "album_audio_id": "${190000 + uid}"
                        }
                        """.trimIndent()
                    }
                    return ApiResponse(
                        JsonParser.parseString("""{"status": 1, "error_code": 0, "data": {"song_list": [$songsJson]}}""").asJsonObject
                    )
                }
                return ApiResponse(JsonObject())
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val firstBatch = repo.personalFm(size = 20)
        val secondBatch = repo.personalFm(size = 20)

        assertEquals(20, firstBatch.size)
        assertEquals(20, secondBatch.size)
        val overlap = firstBatch.map { it.hash }.intersect(secondBatch.map { it.hash }.toSet())
        assertTrue("Personal FM refresh must return 20 brand new personalized songs", overlap.isEmpty())
    }

    @Test
    fun enrichSongsPrivilegeLiteAndParseSongStrictlyDistinguishSqLosslessFromHiRes() = runTest {
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/v2/get_res_privilege/lite") {
                    // Song 1: Normal 16-bit SQ FLAC (26MB, 920kbps) + Viper "super" stream + unverified/26MB level=6 "high"
                    // Song 2: True 24-bit/96kHz Hi-Res Master (54MB, 2304kbps) + 16-bit SQ FLAC (27MB)
                    val mockPriv = """
                        {
                            "status": 1,
                            "data": [
                                {
                                    "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                                    "relate_goods": [
                                        {"hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "level": 2, "quality": "128", "info": {"filesize": 3600000, "bitrate": 128}},
                                        {"hash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", "level": 4, "quality": "320", "info": {"filesize": 9200000, "bitrate": 320}},
                                        {"hash": "cccccccccccccccccccccccccccccccc", "level": 5, "quality": "flac", "info": {"filesize": 26500000, "bitrate": 920}},
                                        {"hash": "dddddddddddddddddddddddddddddddd", "level": 6, "quality": "high", "info": {"filesize": 26800000, "bitrate": 940}},
                                        {"hash": "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee", "level": 6, "quality": "super", "info": {"filesize": 62000000, "bitrate": 2400}}
                                    ]
                                },
                                {
                                    "hash": "11111111111111111111111111111111",
                                    "relate_goods": [
                                        {"hash": "11111111111111111111111111111111", "level": 2, "quality": "128", "info": {"filesize": 3800000, "bitrate": 128}},
                                        {"hash": "22222222222222222222222222222222", "level": 5, "quality": "flac", "info": {"filesize": 27000000, "bitrate": 950}},
                                        {"hash": "33333333333333333333333333333333", "level": 6, "quality": "high", "info": {"filesize": 54000000, "bitrate": 2304}}
                                    ]
                                }
                            ]
                        }
                    """.trimIndent()
                    return ApiResponse(JsonParser.parseString(mockPriv).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val baseSongs = listOf(
            com.concept.music.data.model.Song(
                id = "1",
                hash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                hash128 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                title = "标准无损曲目",
                artist = "歌手A"
            ),
            com.concept.music.data.model.Song(
                id = "2",
                hash = "11111111111111111111111111111111",
                hash128 = "11111111111111111111111111111111",
                title = "真母带HiRes曲目",
                artist = "歌手B"
            )
        )

        val enriched = repo.enrichSongsPrivilegeLite(baseSongs)
        // Song 1 must be SQ Lossless (isLossless = true, isHiRes = false)
        assertTrue(enriched[0].isLossless)
        assertFalse("26MB 16-bit FLAC and synthetic super stream must NOT be labeled Hi-Res", enriched[0].isHiRes)

        // Song 2 must be True Hi-Res (isHiRes = true, isLossless = true)
        assertTrue(enriched[1].isHiRes)
        assertTrue(enriched[1].isLossless)
        assertEquals("33333333333333333333333333333333", enriched[1].hashHiRes)
    }

    @Test
    fun searchComplexDistinguishesSongIntentFromArtistIntentAndFiltersFakeNamesakeArtist() = runTest {
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                val kw = query["keyword"].orEmpty()
                if (kw == "勋章") {
                    when (path) {
                        "/v2/search/song" -> {
                            val mockSongs = """
                                {
                                    "status": 1,
                                    "data": {
                                        "total": 6,
                                        "lists": [
                                            {"SongName": "勋章", "SingerName": "鹿晗", "AuthorId": 10086, "AlbumName": "勋章", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "SQFileHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb1", "ResFileHash": "ccccccccccccccccccccccccccccccc1", "ResFileSize": 52000000, "ResBitrate": 2304, "Privilege": 10},
                                            {"SongName": "勋章 (Medals)(Live)", "SingerName": "鹿晗", "AuthorId": 10086, "AlbumName": "2018鹿晗RE:X巡回演唱会", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2", "SQFileHash": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb2", "Privilege": 10},
                                            {"SongName": "勋章", "SingerName": "请叫我靓仔", "AuthorId": 99001, "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3"},
                                            {"SongName": "小枫《勋章 (0.8X版)(氛围感)》", "SingerName": "宝藏歌单挖掘姬", "AuthorId": 99002, "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa4"},
                                            {"SongName": "勋章 (Live)", "SingerName": "鹿晗", "AuthorId": 10086, "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa5"}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockSongs).asJsonObject)
                        }
                        "/v1/search/author" -> {
                            // 模拟接口返回一个同名叫“勋章”但只有5首歌的冷门用户歌手
                            val mockAuthors = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"AuthorID": 77777, "AuthorName": "勋章", "AudioCount": 5, "AlbumCount": 0}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockAuthors).asJsonObject)
                        }
                        "/v1/search/special" -> {
                            val mockPlaylists = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"specialid": 12345, "specialname": "无关歌单测试", "song_count": 30}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockPlaylists).asJsonObject)
                        }
                    }
                }
                if (kw == "鹿晗") {
                    when (path) {
                        "/v2/search/song" -> {
                            val mockSongs = """
                                {
                                    "status": 1,
                                    "data": {
                                        "total": 4,
                                        "lists": [
                                            {"SongName": "勋章", "SingerName": "鹿晗", "AuthorId": 10086, "FileHash": "11111111111111111111111111111111"},
                                            {"SongName": "我们的明天", "SingerName": "鹿晗", "AuthorId": 10086, "FileHash": "22222222222222222222222222222222"},
                                            {"SongName": "致爱 Your Song", "SingerName": "鹿晗", "AuthorId": 10086, "FileHash": "33333333333333333333333333333333"},
                                            {"SongName": "吹吹风", "SingerName": "鹿晗", "AuthorId": 10086, "FileHash": "44444444444444444444444444444444"}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockSongs).asJsonObject)
                        }
                        "/v1/search/author" -> {
                            val mockAuthors = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"AuthorID": 10086, "AuthorName": "鹿晗", "AudioCount": 542, "AlbumCount": 63}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockAuthors).asJsonObject)
                        }
                    }
                }
                if (kw == "邓紫棋") {
                    when (path) {
                        "/v2/search/song" -> {
                            val mockSongs = """
                                {
                                    "status": 1,
                                    "data": {
                                        "total": 4,
                                        "lists": [
                                            {"SongName": "多远都要在一起", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "新的心跳", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1"},
                                            {"SongName": "光年之外", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "光年之外", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2"},
                                            {"SongName": "泡沫", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "Xposed", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3"},
                                            {"SongName": "唯一", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "T.I.M.E.", "FileHash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa4"}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockSongs).asJsonObject)
                        }
                        "/v1/search/author" -> {
                            val mockAuthors = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"AuthorID": 3520, "AuthorName": "G.E.M.邓紫棋", "AudioCount": 680, "AlbumCount": 45}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockAuthors).asJsonObject)
                        }
                        "/v1/search/special" -> {
                            val mockPlaylists = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"specialid": 88801, "specialname": "邓紫棋必听热歌", "song_count": 101}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockPlaylists).asJsonObject)
                        }
                    }
                }
                if (kw == "摩天动物园") {
                    when (path) {
                        "/v2/search/song" -> {
                            val mockSongs = """
                                {
                                    "status": 1,
                                    "data": {
                                        "total": 3,
                                        "lists": [
                                            {"SongName": "摩天动物园", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "摩天动物园", "FileHash": "ccccccccccccccccccccccccccccccc1"},
                                            {"SongName": "句号", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "摩天动物园", "FileHash": "ccccccccccccccccccccccccccccccc2"},
                                            {"SongName": "透明", "SingerName": "G.E.M.邓紫棋", "AuthorId": 3520, "AlbumName": "摩天动物园", "FileHash": "ccccccccccccccccccccccccccccccc3"}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockSongs).asJsonObject)
                        }
                        "/v1/search/album" -> {
                            val mockAlbums = """
                                {
                                    "status": 1,
                                    "data": {
                                        "lists": [
                                            {"albumid": 9901, "albumname": "摩天动物园", "singername": "G.E.M.邓紫棋", "songcount": 13}
                                        ]
                                    }
                                }
                            """.trimIndent()
                            return ApiResponse(JsonParser.parseString(mockAlbums).asJsonObject)
                        }
                    }
                }
                return ApiResponse(JsonObject())
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        // 1. 搜索单曲「勋章」：应识别为 SONG 意图，剔除名为“勋章”的冷门歌手，隐藏无关歌单，且原唱版本排第一
        val songSearch = repo.searchComplex("勋章")
        assertEquals(com.concept.music.data.model.SearchIntent.SONG, songSearch.intent)
        assertTrue("Namesake obscure artist '勋章' must be filtered out", songSearch.artists.isEmpty())
        assertTrue("Irrelevant playlists must be hidden in SONG intent", songSearch.playlists.isEmpty())
        assertEquals("鹿晗", songSearch.songs.first().artist)
        assertEquals("鹿晗", songSearch.primaryArtist?.name)

        // 2. 搜索歌手「鹿晗」：应识别为 ARTIST 意图，保留歌手卡片置顶
        val artistSearch = repo.searchComplex("鹿晗")
        assertEquals(com.concept.music.data.model.SearchIntent.ARTIST, artistSearch.intent)
        assertEquals(1, artistSearch.artists.size)
        assertEquals("鹿晗", artistSearch.artists.first().name)
        // 3. 搜索非完整复合歌手名「邓紫棋」（曲库登记为「G.E.M.邓紫棋」）：必须识别为 ARTIST 意图而非 PLAYLIST
        val gemSearch = repo.searchComplex("邓紫棋")
        assertEquals(com.concept.music.data.model.SearchIntent.ARTIST, gemSearch.intent)
        assertTrue(gemSearch.artists.isNotEmpty())
        assertEquals("G.E.M.邓紫棋", gemSearch.artists.first().name)

        // 4. 搜索完整专辑名「摩天动物园」：必须识别为 ALBUM 意图并置顶专辑
        val albumSearch = repo.searchComplex("摩天动物园")
        assertEquals(com.concept.music.data.model.SearchIntent.ALBUM, albumSearch.intent)
        assertTrue(albumSearch.albums.isNotEmpty())
        assertEquals("摩天动物园", albumSearch.albums.first().name)
    }

    @Test
    fun artistSongsPreservesVipBadgesAndSearchArtistsEnrichesAvatars() = runTest {
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                body: JsonObject?,
                host: String,
                method: String,
                web: Boolean,
                headers: Map<String, String>,
                device: DeviceIdentity?,
                sign: Boolean,
                includeDefaultParams: Boolean
            ): ApiResponse {
                when (path) {
                    "/v1/search/author" -> {
                        val mock = """
                            {
                                "status": 1,
                                "data": {
                                    "lists": [
                                        {"AuthorId": 1001, "AuthorName": "鹿晗", "Avatar": "", "AudioCount": 0, "AlbumCount": 0}
                                    ]
                                }
                            }
                        """.trimIndent()
                        return ApiResponse(JsonParser.parseString(mock).asJsonObject)
                    }
                    "/kmr/v3/author" -> {
                        val mock = """
                            {
                                "status": 1,
                                "data": {
                                    "base": {"author_id": "1001", "author_name": "鹿晗", "sizable_avatar": "http://imge.kugou.com/stdmusic/{size}/luhan.jpg"},
                                    "extra": {"audio_count": 545, "album_count": 63, "long_intro": "${"简介".repeat(120)}"}
                                }
                            }
                        """.trimIndent()
                        return ApiResponse(JsonParser.parseString(mock).asJsonObject)
                    }
                    "/openapi/kmr/v2/audio_group/author" -> {
                        val mock = """
                            {
                                "status": 1,
                                "data": {
                                    "song_list": [
                                        {
                                            "base": {"audio_id": 5001, "audio_name": "勋章", "author_name": "鹿晗"},
                                            "audio_info": {
                                                "hash_128": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1",
                                                "hash_flac": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2",
                                                "hash_high": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3",
                                                "filesize_flac": 26000000,
                                                "filesize_high": 48000000,
                                                "privilege_128": 0,
                                                "privilege_flac": 10,
                                                "privilege_high": 10
                                            }
                                        }
                                    ]
                                }
                            }
                        """.trimIndent()
                        return ApiResponse(JsonParser.parseString(mock).asJsonObject)
                    }
                    "/v2/get_res_privilege/lite" -> {
                        val mock = """
                            {
                                "status": 1,
                                "data": [
                                    {
                                        "hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3",
                                        "privilege": 0,
                                        "pay_type": 0,
                                        "relate_goods": [
                                            {"hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa1", "level": 1, "quality": "128", "privilege": 0, "size": 3200000},
                                            {"hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa2", "level": 5, "quality": "flac", "privilege": 10, "size": 26000000},
                                            {"hash": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa3", "level": 6, "quality": "high", "privilege": 10, "size": 48000000}
                                        ]
                                    }
                                ]
                            }
                        """.trimIndent()
                        return ApiResponse(JsonParser.parseString(mock).asJsonObject)
                    }
                }
                return ApiResponse(JsonObject())
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val artists = repo.searchArtists("鹿晗")
        assertEquals(1, artists.size)
        assertEquals("http://imge.kugou.com/stdmusic/400/luhan.jpg", artists.first().avatarUrl)
        assertEquals(545, artists.first().songCount)
        assertEquals(63, artists.first().albumCount)

        val songs = repo.artistSongs("1001")
        assertEquals(1, songs.size)
        assertTrue("Artist detail songs with VIP lossless/Hi-Res tiers must carry isVip = true", songs.first().isVip)
        assertTrue(songs.first().isHiRes)
    }
}
