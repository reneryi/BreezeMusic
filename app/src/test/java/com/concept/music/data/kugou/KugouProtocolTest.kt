package com.concept.music.data.kugou

import com.concept.music.data.model.Song
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset

class KugouProtocolTest {
    @Test fun md5MidAndSignatureMatchUpstreamFixture() {
        assertEquals("104602365767877882724869659726014956567", KugouSigner.mid("fixture-guid"))
        val params = mapOf("mid" to "1234", "appid" to "3116", "clienttime" to "1770000000", "token" to "fixture-token", "userid" to "123")
        assertEquals("c2f83c3eeb8c6d72bfcdeedb0511c87e", KugouSigner.signature(params, "{\"mobile\":\"13800000000\",\"code\":\"123456\"}"))
        assertEquals("358d8457b5451893c970670a977256c4", KugouSigner.paramsKey(1770000000123L))
    }

    @Test fun fixedAesAndRawRsaAreDeterministic() {
        assertEquals("8270cd5eba410362918595d0cc6188dd", KugouSigner.aes("|1770000000123", "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98"))
        assertEquals("73143dd1ae3d044b1eb18cb4178c7ead1c10607262e8afe75d954c2b4a5eb365b1913e1ab60fc516ad3154114c6eefe9b47a7b6895fb0f67ce7422b62f75255a574be399ac6cc2f39e5fb9a543c55523be17e52f4aecee6f8a497568d65f61559a204967195ab22dca1c6d27d85e2f7b0ae9262bb51808e2ce0d9190e5d5c998", KugouSigner.rsa("{\"clienttime_ms\":1770000000123,\"key\":\"0123456789abcdef\"}"))
        val pkcs1Hex = KugouSigner.rsaPkcs1("{\"aes\":\"abcdef\",\"uid\":123,\"token\":\"t\"}")
        assertEquals(256, pkcs1Hex.length)
        assertEquals(pkcs1Hex.lowercase(), pkcs1Hex)
    }

    @Test fun vipParsesTypesAndDateRepresentationsIncludingDvipQvip() {
        val root = JsonObject().apply { add("busi_vip", com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("product_type", "dvip"); addProperty("is_vip", 1); addProperty("vip_end_time", "2026-12-01 10:00:00") })
            add(JsonObject().apply { addProperty("product_type", "svip"); addProperty("is_vip", 0); addProperty("vip_end_time", "0") })
        }) }
        val snapshot = VipSnapshot.parse(root, java.time.Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), java.time.ZoneOffset.UTC))
        assertTrue(snapshot.active("tvip")); assertFalse(snapshot.active("svip"))
        assertEquals(Instant.parse("2026-12-01T02:00:00Z"), snapshot.benefits.first().expiresAt)
    }

    @Test fun responseDoesNotTreatMissingStatusAsSuccess() {
        val response = ApiResponse(JsonObject().apply { add("data", JsonObject()) })
        assertFalse(response.success)
        try { response.requireSuccess(); fail("missing status must fail") } catch (expected: ApiFailure) { assertNull(expected.code) }
    }

    @Test fun registerDeviceSavesDfidWhenCurrentDfidIsPlaceholderOrUnregistered() = kotlinx.coroutines.test.runTest {
        var savedDfid = "2localfallbackdfid000000"
        var registeredFlag = false
        val store = object : SessionStore by MemoryStore() {
            override fun device() = DeviceIdentity("fixture-guid", "1234", "dev", dfid = savedDfid)
            override fun isDfidRegistered() = registeredFlag
            override fun saveDfid(dfid: String) {
                savedDfid = dfid
                registeredFlag = true
            }
        }
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun requestRawBytes(
                path: String, session: KugouSession?, query: Map<String, String>, rawBody: String?,
                host: String, method: String, headers: Map<String, String>, sign: Boolean,
                includeDefaultParams: Boolean, device: DeviceIdentity?
            ): ByteArray {
                assertEquals("/risk/v2/r_register_dev", path)
                assertEquals("https://userservice.kugou.com", host)
                return "{\"status\":1,\"data\":{\"dfid\":\"dfid_concept_2026\"}}".toByteArray(Charsets.UTF_8)
            }
        }
        val auth = AuthRepository(transport, store)
        val dfid = auth.registerDevice()
        assertEquals("dfid_concept_2026", dfid)
        assertEquals("dfid_concept_2026", savedDfid)
        assertTrue(store.isDfidRegistered())
    }

    @Test fun csccReporterMaintainsConsistentSongStateOnRapidSwitch() {
        val songA = Song(id = "song_a", title = "Song A", artist = "Artist A")
        val songB = Song(id = "song_b", title = "Song B", artist = "Artist B")
        KugouCsccReporter.currentPlayingSongId = songB.id
        KugouCsccReporter.songStartTimeMs = 123456L
        // Ending previous songA after songB already started must NOT clear songB's state
        KugouCsccReporter.onSongEnd(songA, playedDurationMs = 5000L)
        assertEquals("song_b", KugouCsccReporter.currentPlayingSongId)
        assertEquals(123456L, KugouCsccReporter.songStartTimeMs)
    }

    @Test fun resolvePlaybackUsesPrivUrlEvenWhenVipTokenIsNull() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        var privUrlCalled = false
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
                sign: Boolean, includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/v6/priv_url") {
                    privUrlCalled = true
                    assertEquals(6, body?.get("vip")?.asInt)
                    assertEquals("a", body?.getAsJsonObject("tracker_param")?.get("viptoken")?.asString)
                    val mockPriv = "{\"status\":1,\"priv_status\":1,\"file_size\":28000000,\"bitrate\":920,\"extname\":\"flac\",\"url\":[\"http://vip.kugou.com/lossless.flac\"]}"
                    return ApiResponse(com.google.gson.JsonParser.parseString(mockPriv).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        val song = com.concept.music.data.model.Song(
            id = "s1",
            hash = "11111111111111111111111111111111",
            hashFlac = "22222222222222222222222222222222",
            title = "概念版无损测试",
            artist = "歌手",
            durationMs = 240_000L,
            isLossless = true
        )
        val res = repo.resolvePlayback(song, com.concept.music.data.model.AudioQuality.LOSSLESS)
        assertTrue(privUrlCalled)
        assertEquals("http://vip.kugou.com/lossless.flac", res.url)
        assertEquals(com.concept.music.data.model.AudioQuality.LOSSLESS, res.actualQuality)
    }

    @Test fun standardRsaKeyAndStandardSignaturesWorkWithoutBase64Exception() {
        val encrypted = KugouSigner.rsa("{\"clienttime_ms\":1770000000123,\"key\":\"0123456789abcdef\"}", useStandardKey = true)
        assertEquals(256, encrypted.length)
        assertEquals(encrypted.lowercase(), encrypted)

        val stdParamsKey = KugouSigner.paramsKeyStandard(1770000000123L)
        assertEquals(32, stdParamsKey.length)

        val stdSig = KugouSigner.signatureStandard(mapOf("appid" to "1005", "clientver" to "20489"), "{\"test\":1}")
        assertEquals(32, stdSig.length)
    }

    @Test fun userGradeInfoSelectsMaxDurationAcrossChannelsForAccurateOfficialTime() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                if (path == "/v3/get_my_info") {
                    // Simulate Lite endpoint returning lagging 220788 minutes
                    val json = """{"status":1,"data":{"user_y_grade":11,"dura_With_M":220788,"r_listen":17435}}"""
                    return ApiResponse(com.google.gson.JsonParser.parseString(json).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }

            override suspend fun callAdvanced(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
                sign: Boolean, includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/v4/get_grade_info") {
                    // Simulate Standard /v4/get_grade_info returning up-to-date 221062 minutes (~3684.4h)
                    val json = """{"status":1,"data":{"grade":11,"duration":221062,"next_grade_duration":225000}}"""
                    return ApiResponse(com.google.gson.JsonParser.parseString(json).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        val gradeInfo = repo.userGradeInfo()
        assertNotNull(gradeInfo)
        assertEquals(11, gradeInfo.level)
        assertEquals(221062L, gradeInfo.listenDurationMinutes)
        assertEquals("3684 小时 22 分", gradeInfo.displayListenTimeText)
    }

    @Test fun userGradeInfoAlignsWithStandardClientLocalDiffSecWhenCloudDsecIs220788Minutes() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore().apply {
            rows = listOf(SavedAccount("874555157"))
            activeKey = "874555157"
            sessions["874555157"] = KugouSession("874555157", "lite-token")
        }
        var capturedV4AppId: String? = null
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
                sign: Boolean, includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/v4/get_grade_info") {
                    capturedV4AppId = query["appid"]
                    val json = """{"status":1,"error_code":0,"data":{"p_grade":11,"p_current_point":298810,"p_next_grade_point":300000,"d_sec":13247329,"duration":220788}}"""
                    return ApiResponse(com.google.gson.JsonParser.parseString(json).asJsonObject)
                }
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        val gradeInfo = repo.userGradeInfo()
        assertEquals("3116", capturedV4AppId)
        assertEquals(11, gradeInfo.level)
        assertEquals(13247329L, repo.lastServerRawDsec)
        assertEquals(13263720L, gradeInfo.totalListenSeconds)
        assertEquals(221062L, gradeInfo.listenDurationMinutes)
        assertEquals("3684 小时 22 分", gradeInfo.displayListenTimeText)
    }

    @Test fun rankSongsAllLoadsUpTo500SongsAndStreamsProgressPerPage() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse = ApiResponse(JsonObject())

            override suspend fun callAdvanced(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
                sign: Boolean, includeDefaultParams: Boolean
            ): ApiResponse {
                if (path == "/api/v3/rank/song") {
                    val page = query["page"]?.toIntOrNull() ?: 1
                    val pageSize = query["pagesize"]?.toIntOrNull() ?: 100
                    val startIdx = (page - 1) * pageSize + 1
                    val listArr = com.google.gson.JsonArray()
                    for (i in startIdx until (startIdx + pageSize)) {
                        listArr.add(JsonObject().apply {
                            addProperty("audio_id", i)
                            addProperty("filename", "Singer $i - TopSong $i")
                            addProperty("hash", "hash_${i}_000000000000000000000000")
                            addProperty("duration", 200)
                        })
                    }
                    val resp = JsonObject().apply {
                        addProperty("status", 1)
                        add("data", JsonObject().apply {
                            addProperty("total", 500)
                            add("info", listArr)
                        })
                    }
                    return ApiResponse(resp)
                }
                return ApiResponse(JsonObject())
            }
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        val progressSizes = mutableListOf<Int>()
        val allSongs = repo.rankSongsAll(8888, maxSongs = 500) { partial ->
            progressSizes += partial.size
        }
        assertEquals(500, allSongs.size)
        assertEquals(listOf(100, 200, 300, 400, 500), progressSizes)
    }
}
