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
    fun addAndRemoveSongFromPlaylist() = runTest {
        var addSongBody: JsonObject? = null
        var deleteSongBody: JsonObject? = null

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

        // 1. 添加单曲到歌单
        val addSuccess = repo.addSongToPlaylist(1001L, song)
        assertTrue(addSuccess)
        assertNotNull(addSongBody)
        assertEquals(1001L, addSongBody?.get("listid")?.asLong)
        val songItemInAdd = addSongBody?.getAsJsonArray("data")?.get(0)?.asJsonObject
        assertEquals("晴天", songItemInAdd?.get("name")?.asString)
        assertEquals(song.hash, songItemInAdd?.get("hash")?.asString)
        assertEquals(12345678L, songItemInAdd?.get("mixsongid")?.asLong)

        // 2. 从歌单移除单曲
        val removeSuccess = repo.removeSongFromPlaylist(1001L, song)
        assertTrue(removeSuccess)
        assertNotNull(deleteSongBody)
        assertEquals(1001L, deleteSongBody?.get("listid")?.asLong)
        val songItemInDel = deleteSongBody?.getAsJsonArray("data")?.get(0)?.asJsonObject
        assertEquals(998877L, songItemInDel?.get("fileid")?.asLong)
    }
}
