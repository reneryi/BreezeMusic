package com.concept.music.data.kugou

import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class PlaylistManagementTest {

    @Test
    fun playlistAesEncryptionRoundTrip() {
        val originalText = """{"listid":123456,"total_ver":0,"type":0,"userid":88888,"token":"test_token"}"""
        val (key, base64Cipher) = KugouSigner.playlistAesEncrypt(originalText)

        assertNotNull(key)
        assertEquals(6, key.length)
        assertTrue(base64Cipher.isNotBlank())

        val decrypted = KugouSigner.playlistAesDecrypt(key, base64Cipher)
        assertEquals(originalText, decrypted)

        val rawCipherBytes = Base64.getDecoder().decode(base64Cipher)
        val decryptedFromBytes = KugouSigner.playlistAesDecrypt(rawCipherBytes, key)
        assertEquals(originalText, decryptedFromBytes)
    }

    @Test
    fun createPlaylistSendsCorrectPayload() = runTest {
        var calledPath = ""
        var calledBody: JsonObject? = null
        var calledQuery: Map<String, String>? = null

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
                calledPath = path
                calledBody = body
                calledQuery = query
                return ApiResponse(JsonParser.parseString("""{"status":1,"data":{"listid":9988}}""").asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        // 注入登录上下文
        store.saveSession(KugouSession("123", "mock-token"))

        val success = repo.createPlaylist("我的新歌单")
        assertTrue(success)
        assertEquals("/cloudlist.service/v5/add_list", calledPath)
        assertNotNull(calledBody)
        assertEquals("我的新歌单", calledBody?.get("name")?.asString)
        assertEquals(0, calledBody?.get("type")?.asInt)
        assertEquals(1, calledBody?.get("source")?.asInt)
        assertEquals("123", calledQuery?.get("userid"))
    }

    @Test
    fun collectPlaylistParsesGidAndSendsType1() = runTest {
        var calledPath = ""
        var calledBody: JsonObject? = null

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
                calledPath = path
                calledBody = body
                return ApiResponse(JsonParser.parseString("""{"status":1,"data":{}}""").asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        store.saveSession(KugouSession("123", "mock-token"))

        val targetPlaylist = Playlist(
            id = "collection_1_666888_999000",
            title = "周杰伦精选",
            globalCollectionId = "collection_1_666888_999000",
            isCreatedByMe = false,
            isFavorite = false
        )

        val success = repo.collectPlaylist(targetPlaylist)
        assertTrue(success)
        assertEquals("/cloudlist.service/v5/add_list", calledPath)
        assertEquals(1, calledBody?.get("type")?.asInt)
        assertEquals(666888L, calledBody?.get("list_create_userid")?.asLong)
        assertEquals(999000L, calledBody?.get("list_create_listid")?.asLong)
        assertEquals("collection_1_666888_999000", calledBody?.get("list_create_gid")?.asString)
    }

    @Test
    fun deleteOrUncollectPlaylistRejectsErrorResponseAndDecryptsEncryptedSuccess() = runTest {
        var capturedP = ""
        var mockRawBytes: ByteArray = """{"status":0,"err_code":30001}""".toByteArray(Charsets.UTF_8)

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

            override suspend fun requestRawBytes(
                path: String,
                session: KugouSession?,
                query: Map<String, String>,
                rawBody: String?,
                host: String,
                method: String,
                headers: Map<String, String>,
                sign: Boolean,
                includeDefaultParams: Boolean,
                device: DeviceIdentity?
            ): ByteArray {
                assertEquals("/v2/delete_list", path)
                capturedP = query["p"].orEmpty()
                return mockRawBytes
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        store.saveSession(KugouSession("123", "mock-token"))

        val targetPlaylist = Playlist(
            id = "556677",
            listId = 556677L,
            title = "测试待删歌单",
            isCreatedByMe = true
        )

        // 1. 服务端返回错误 JSON (status=0, err_code=30001) 时绝不能假成功返回 true
        val failedResult = repo.deleteOrUncollectPlaylist(targetPlaylist)
        assertFalse("delete_list 返回 status=0 时必须返回 false", failedResult)
        assertEquals(256, capturedP.length)
        assertEquals(capturedP.lowercase(), capturedP)

        // 2. 服务端返回 status=1, err_code=0 时返回 true
        mockRawBytes = """{"status":1,"err_code":0}""".toByteArray(Charsets.UTF_8)
        val okResult = repo.deleteOrUncollectPlaylist(targetPlaylist)
        assertTrue("delete_list 返回 status=1 且 err_code=0 时必须返回 true", okResult)
    }

    @Test
    fun addAndRemoveSongFromPlaylistAndBatchAdd() = runTest {
        var addSongBody: JsonObject? = null
        var deleteSongBody: JsonObject? = null
        var addCallCount = 0

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
                if (path.contains("add_song")) {
                    addCallCount++
                    addSongBody = body
                } else if (path.contains("delete_songs")) {
                    deleteSongBody = body
                }
                return ApiResponse(JsonParser.parseString("""{"status":1,"data":{}}""").asJsonObject)
            }
        }

        val store = MemoryStore()
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)
        store.saveSession(KugouSession("123", "mock-token"))

        val song = Song(
            id = "song_1",
            hash = "E7B82245C4B40B5DB3884D7759086111",
            title = "晴天",
            artist = "周杰伦",
            durationMs = 269000,
            albumAudioId = 12345678L,
            fileId = 998877L
        )

        // 1. 添加单曲到歌单（对齐 KuGou 云歌单标准「歌手 - 歌名」格式）
        val addSuccess = repo.addSongToPlaylist(1001L, song)
        assertTrue(addSuccess)
        assertNotNull(addSongBody)
        assertEquals(1001L, addSongBody?.get("listid")?.asLong)
        val songItemInAdd = addSongBody?.getAsJsonArray("data")?.get(0)?.asJsonObject
        assertEquals("周杰伦 - 晴天", songItemInAdd?.get("name")?.asString)
        assertEquals(song.hash, songItemInAdd?.get("hash")?.asString)
        assertEquals(12345678L, songItemInAdd?.get("mixsongid")?.asLong)

        // 2. 批量添加多首歌曲到歌单（单次网络请求提交全部歌曲）
        addCallCount = 0
        val song2 = song.copy(id = "song_2", hash = "F8C93356D5C51C6EC4995E8860197222", title = "七里香")
        val batchAdded = repo.addSongsToPlaylistBatch(1001L, listOf(song, song2))
        assertEquals(2, batchAdded)
        assertEquals("批量添加应只发起 1 次请求", 1, addCallCount)
        assertEquals(2, addSongBody?.getAsJsonArray("data")?.size())

        // 3. 从歌单移除单曲
        val removeSuccess = repo.removeSongFromPlaylist(1001L, song)
        assertTrue(removeSuccess)
        assertNotNull(deleteSongBody)
        assertEquals(1001L, deleteSongBody?.get("listid")?.asLong)
        val songItemInDel = deleteSongBody?.getAsJsonArray("data")?.get(0)?.asJsonObject
        assertEquals(998877L, songItemInDel?.get("fileid")?.asLong)
    }
}
