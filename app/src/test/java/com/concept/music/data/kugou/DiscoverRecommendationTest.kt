package com.concept.music.data.kugou

import com.concept.music.data.model.Song
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class DiscoverRecommendationTest {

    @Test
    fun youthChannelsParsingSucceeds() = runTest {
        val mockJson = """
            {
                "status": 1,
                "data": [
                    {
                        "global_collection_id": "ch_study_99",
                        "channel_name": "深度心流舱",
                        "cover": "http://imge.kugou.com/study.jpg",
                        "desc": "专注自习与白噪音",
                        "song_count": 50,
                        "tag": "沉浸学习"
                    },
                    {
                        "global_collection_id": "ch_sleep_88",
                        "channel_name": "晚安梦游记",
                        "cover": "http://imge.kugou.com/sleep.jpg",
                        "desc": "轻柔钢琴与助眠雨声",
                        "song_count": 30,
                        "tag": "深夜助眠"
                    }
                ]
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
            ): ApiResponse = ApiResponse(JsonParser.parseString(mockJson).asJsonObject)

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
            ): ApiResponse = ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val channels = repo.youthChannels()
        assertEquals(2, channels.size)
        assertEquals("ch_study_99", channels[0].id)
        assertEquals("深度心流舱", channels[0].name)
        assertEquals("沉浸学习", channels[0].tag)
        assertEquals(50, channels[0].songCount)

        assertEquals("ch_sleep_88", channels[1].id)
        assertEquals("晚安梦游记", channels[1].name)
    }

    @Test
    fun youthChannelSongsNestedAudioExtraction() = runTest {
        val mockJson = """
            {
                "status": 1,
                "data": [
                    {
                        "name": "心流曲目1",
                        "audio": [
                            {
                                "hash": "AABBCCDDEEFF00112233445566778899",
                                "songname": "Lofi Study Beat",
                                "singername": "Chill Artist",
                                "duration": 180000,
                                "audio_id": "9001"
                            }
                        ]
                    },
                    {
                        "hash": "11223344556677889900AABBCCDDEEFF",
                        "songname": "Raindrops",
                        "singername": "Nature Sound",
                        "duration": 240000,
                        "audio_id": "9002"
                    }
                ]
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
            ): ApiResponse = ApiResponse(JsonParser.parseString(mockJson).asJsonObject)

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
            ): ApiResponse = ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val songs = repo.youthChannelSongs("ch_study_99")
        assertEquals(2, songs.size)
        assertEquals("Lofi Study Beat", songs[0].title)
        assertEquals("Chill Artist", songs[0].artist)
        assertEquals("Raindrops", songs[1].title)
    }

    @Test
    fun personalFmPayloadAndRoutingVerified() = runTest {
        var capturedPath = ""
        var capturedHeader = ""
        var capturedBody: JsonObject? = null

        val mockJson = """
            {
                "status": 1,
                "data": {
                    "song_list": [
                        {
                            "hash": "FM00112233445566778899AABBCCDDEE",
                            "songname": "随心听好歌",
                            "singername": "随心听歌手",
                            "timelen": 200000,
                            "audio_id": "8001"
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
            ): ApiResponse {
                capturedPath = path
                capturedHeader = headers["x-router"].orEmpty()
                capturedBody = body
                return ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val songs = repo.personalFm(size = 15)
        assertEquals("/v2/personal_recommend", capturedPath)
        assertEquals("persnfm.service.kugou.com", capturedHeader)
        assertNotNull(capturedBody)
        assertEquals(15, capturedBody?.get("remain_songcnt")?.asInt)

        assertEquals(1, songs.size)
        assertEquals("随心听好歌", songs[0].title)
    }

    @Test
    fun sceneRecommendSongsDispatchesCorrectly() = runTest {
        var requestedChannel = ""

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
                requestedChannel = query["global_collection_id"].orEmpty()
                val mockJson = """
                    {
                        "status": 1,
                        "data": [
                            {
                                "hash": "SCENE0011223344",
                                "songname": "场景歌曲",
                                "singername": "场景歌手",
                                "audio_id": "7001"
                            }
                        ]
                    }
                """.trimIndent()
                return ApiResponse(JsonParser.parseString(mockJson).asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        // 沉浸学习 -> ch_study
        repo.sceneRecommendSongs("STUDY", size = 10)
        assertEquals("ch_study", requestedChannel)

        // 深夜助眠 -> ch_sleep
        repo.sceneRecommendSongs("SLEEP", size = 10)
        assertEquals("ch_sleep", requestedChannel)

        // 燃动运动 -> ch_workout
        repo.sceneRecommendSongs("WORKOUT", size = 10)
        assertEquals("ch_workout", requestedChannel)

        // 自驾旅行 -> ch_drive
        repo.sceneRecommendSongs("DRIVE", size = 10)
        assertEquals("ch_drive", requestedChannel)

        // 治愈微风 -> ch_healing
        repo.sceneRecommendSongs("HEALING", size = 10)
        assertEquals("ch_healing", requestedChannel)
    }
}
