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
}
