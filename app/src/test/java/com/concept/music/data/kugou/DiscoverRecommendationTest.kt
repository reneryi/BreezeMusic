package com.concept.music.data.kugou

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
    fun youthChannelSevenOfficialApisWorkCorrectly() = runTest {
        val calledPaths = mutableListOf<String>()

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
                calledPaths.add(path)
                return ApiResponse(JsonParser.parseString("""{"status": 1, "errcode": 0, "data": {}}""").asJsonObject)
            }

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
                calledPaths.add(path)
                val mock = when {
                    path.contains("channel_list_by_id") -> """
                        {"status": 1, "data": {"list": [{"channel_id": "101", "name": "自习室", "desc": "静心学习", "is_sub": 1}]}}
                    """
                    path.contains("amway") -> """
                        {"status": 1, "data": {"list": [{"id": "am_1", "channel_id": "101", "channel_name": "自习室", "content": "深夜自习宝藏频道"}]}}
                    """
                    path.contains("get_friendly_channel") -> """
                        {"status": 1, "data": {"list": [{"channel_id": "102", "name": "晚安助眠"}]}}
                    """
                    path.contains("channel_get_song_audit_passed") -> """
                        {"status": 1, "data": {"list": [{"story_id": "st_1", "content": "一段治愈的旋律", "nickname": "微风听友"}]}}
                    """
                    path.contains("get_song_detail") -> """
                        {"status": 1, "data": {"story_id": "st_1", "content": "完整音乐故事正文", "nickname": "微风听友"}}
                    """
                    else -> """{"status": 1, "errcode": 0, "data": {}}"""
                }
                return ApiResponse(JsonParser.parseString(mock.trimIndent()).asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val details = repo.youthChannelDetail(listOf("101"))
        assertEquals(1, details.size)
        assertEquals("自习室", details[0].name)
        assertTrue(details[0].isSubscribed)

        val amways = repo.youthChannelAmway("101")
        assertTrue(amways.isNotEmpty())
        assertEquals("深夜自习宝藏频道", amways[0].content)

        val similars = repo.youthChannelSimilar("101")
        assertTrue(similars.isNotEmpty())
        assertEquals("102", similars[0].id)

        val subOk = repo.youthChannelSubscribe("101", subscribe = true)
        assertTrue(subOk)

        val stories = repo.youthChannelStories("101", page = 1, pageSize = 10)
        assertTrue(stories.isNotEmpty())

        val storyDetail = repo.youthChannelStoryDetail("st_1", "101")
        assertNotNull(storyDetail)
        assertEquals("完整音乐故事正文", storyDetail?.content)
    }

    @Test
    fun relateGoodsLevelMappingDistinguishesFlacFromHiRes() = runTest {
        // 验证 relate_goods 中 level=5 为无损 FLAC(SQ)，level=6 才是 Hi-Res，不再把普通歌曲误标为 Hi-Res
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
        // 第一首歌最高只有 level=5 (FLAC)，应标记为 isLossless=true, isHiRes=false
        assertTrue(songs[0].isLossless)
        assertFalse(songs[0].isHiRes)

        // 第二首歌含 level=6 (Hi-Res)，应标记为 isLossless=true, isHiRes=true
        assertTrue(songs[1].isLossless)
        assertTrue(songs[1].isHiRes)
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
                requestedChannel = query["channel_id"].orEmpty()
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

        repo.sceneRecommendSongs("STUDY", size = 10)
        assertEquals("101", requestedChannel)

        repo.sceneRecommendSongs("SLEEP", size = 10)
        assertEquals("102", requestedChannel)

        repo.sceneRecommendSongs("WORKOUT", size = 10)
        assertEquals("106", requestedChannel)

        repo.sceneRecommendSongs("DRIVE", size = 10)
        assertEquals("105", requestedChannel)

        repo.sceneRecommendSongs("HEALING", size = 10)
        assertEquals("103", requestedChannel)
    }
}
