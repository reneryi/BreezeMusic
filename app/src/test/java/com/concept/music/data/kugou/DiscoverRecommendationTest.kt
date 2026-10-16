package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiscoverRecommendationTest {

    @Test
    fun newSongExpressUsesOfficialNewsongPublishContainerAndBreaksThirtySixLimit() = runTest {
        val requestedPaths = mutableListOf<String>()
        val requestedRankIds = mutableListOf<Int>()

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
                synchronized(requestedPaths) {
                    requestedPaths.add(path)
                }
                if (path == "/musicadservice/container/v1/newsong_publish") {
                    val rankId = body?.number("rank_id") ?: 21608
                    val page = body?.number("page") ?: 1
                    synchronized(requestedRankIds) {
                        requestedRankIds.add(rankId)
                    }
                    val items = (1..35).joinToString(",") { idx ->
                        val globalIdx = (page - 1) * 35 + idx
                        val hashHex = String.format("%032x", rankId.toLong() * 10000L + globalIdx)
                        """
                        {
                            "songname": "新歌_${rankId}_$globalIdx",
                            "singername": "首发歌手_${globalIdx}",
                            "hash": "$hashHex",
                            "audio_id": ${80000 + globalIdx},
                            "album_audio_id": ${90000 + globalIdx}
                        }
                        """.trimIndent()
                    }
                    val mockJson = """
                        {
                            "status": 1,
                            "error_code": 0,
                            "data": {
                                "list": [$items]
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

        val chineseSongs = repo.newSongExpress("CHINESE", size = 100, page = 1)
        val westernSongs = repo.newSongExpress("WESTERN", size = 100, page = 1)
        val jpkrSongs = repo.newSongExpress("JPKR", size = 100, page = 1)

        // 验证突破原先 36 首上限，单次可加载 100 首最新歌曲，且严格调用 /musicadservice/container/v1/newsong_publish
        assertEquals(100, chineseSongs.size)
        assertEquals(100, westernSongs.size)
        assertEquals(100, jpkrSongs.size)
        assertTrue(requestedPaths.contains("/musicadservice/container/v1/newsong_publish"))
        assertTrue(requestedRankIds.containsAll(listOf(21608, 21609, 21610)))
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
}
