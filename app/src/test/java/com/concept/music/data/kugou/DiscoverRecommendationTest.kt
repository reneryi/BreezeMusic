package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiscoverRecommendationTest {

    @Test
    fun newSongExpressSupportsTodayAndMultiDayPeriods() = runTest {
        var callCounter = 0

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
            ): ApiResponse {
                callCounter++
                val page = query["page"]?.toIntOrNull() ?: 1
                val rankId = query["rankid"] ?: "1"
                val items = (1..20).joinToString(",") { idx ->
                    val globalIdx = (page - 1) * 20 + idx
                    """
                    {
                        "name": "歌手$globalIdx - 新歌_${rankId}_$globalIdx",
                        "hash": "NEWSONG_${rankId}_${page}_$idx",
                        "audio_id": ${80000 + globalIdx}
                    }
                    """.trimIndent()
                }
                val mockJson = """
                    {
                        "status": 1,
                        "data": {
                            "info": [$items]
                        }
                    }
                """.trimIndent()
                return ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val todaySongs = repo.newSongExpress("TODAY", size = 30)
        val day3Songs = repo.newSongExpress("DAY3", size = 30)
        val day5Songs = repo.newSongExpress("DAY5", size = 30)

        assertEquals(30, todaySongs.size)
        assertEquals(30, day3Songs.size)
        assertEquals(30, day5Songs.size)
        assertNotEquals(todaySongs.map { it.hash }, day3Songs.map { it.hash })
        assertNotEquals(day3Songs.map { it.hash }, day5Songs.map { it.hash })
        assertTrue(callCounter > 0)
    }

    @Test
    fun personalFmRefreshReturnsBrandNewSongsInsteadOfShufflingSameTwenty() = runTest {
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
                if (path.contains("personal_recommend")) {
                    batchSeq++
                    val songsJson = (1..20).joinToString(",") { idx ->
                        val uid = (batchSeq - 1) * 20 + idx
                        """
                        {
                            "hash": "PFM_HASH_${batchSeq}_$idx",
                            "songname": "猜你喜欢曲目_$uid",
                            "singername": "推荐歌手_$uid",
                            "audio_id": "${90000 + uid}"
                        }
                        """.trimIndent()
                    }
                    return ApiResponse(
                        JsonParser.parseString("""{"status": 1, "data": {"song_list": [$songsJson]}}""").asJsonObject
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
        assertTrue("Personal FM refresh must return brand new songs rather than shuffling old ones", overlap.isEmpty())
    }

    @Test
    fun fmSongsLoadsFullTwentyCategoryMatchedSongsEvenWhenGatewayCapsAtFivePerSlice() = runTest {
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
                if (path.contains("app_song_list_offset")) {
                    val reqStr = body?.toString().orEmpty()
                    val offsetVal = Regex(""""offset"\s*:\s*(\d+)""").find(reqStr)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val fiveSongs = (1..5).joinToString(",") { idx ->
                        val num = offsetVal + idx
                        """
                        {
                            "hash": "FM_CH101_OFFSET_${offsetVal}_$idx",
                            "songname": "热歌电台曲目_$num",
                            "singername": "电台歌手_$num",
                            "audio_id": "${70000 + num}"
                        }
                        """.trimIndent()
                    }
                    val mockJson = """
                        {
                            "status": 1,
                            "data": [
                                {
                                    "channel_id": 101,
                                    "songlist": [$fiveSongs]
                                }
                            ]
                        }
                    """.trimIndent()
                    return ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val initialBatch = repo.fmSongs("101", size = 20, forceNextBatch = true)
        val loadMoreBatch = repo.fmSongs("101", size = 20, forceNextBatch = true)

        assertEquals(20, initialBatch.size)
        assertEquals(20, loadMoreBatch.size)
        val overlap = initialBatch.map { it.hash }.intersect(loadMoreBatch.map { it.hash }.toSet())
        assertTrue("Each +20 load on FM station must return 20 fresh tracks", overlap.isEmpty())
    }

    @Test
    fun relateGoodsLevelMappingDistinguishesFlacFromHiRes() = runTest {
        val mockPlaylistJson = """
            {
                "status": 1,
                "data": {
                    "info": [
                        {
                            "name": "周杰伦 - 晴天",
                            "hash": "11111111111111111111111111111111",
                            "audio_id": 1001,
                            "relate_goods": [
                                {"level": 2, "hash": "11111111111111111111111111111111"},
                                {"level": 4, "hash": "22222222222222222222222222222222"},
                                {"level": 5, "hash": "33333333333333333333333333333333"}
                            ]
                        },
                        {
                            "name": "林俊杰 - 江南",
                            "hash": "44444444444444444444444444444444",
                            "audio_id": 1002,
                            "relate_goods": [
                                {"level": 2, "hash": "44444444444444444444444444444444"},
                                {"level": 5, "hash": "55555555555555555555555555555555"},
                                {"level": 6, "hash": "66666666666666666666666666666666"}
                            ]
                        }
                    ]
                }
            }
        """.trimIndent()

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
            ): ApiResponse = ApiResponse(JsonParser.parseString(mockPlaylistJson).asJsonObject)
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val songs = repo.rankSongs(6666)
        assertEquals(2, songs.size)
        assertTrue(songs[0].isLossless)
        assertFalse(songs[0].isHiRes)
        assertTrue(songs[1].isLossless)
        assertTrue(songs[1].isHiRes)
    }
}
