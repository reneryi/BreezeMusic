package com.concept.music.data.share

import com.concept.music.data.model.Playlist
import com.concept.music.data.model.Song
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KugouShareTest {

    @Test
    fun `单曲分享载荷生成支持同时唤醒官方酷狗与官方概念版`() {
        val song = Song(
            id = "38291042",
            hash = "b0ef8b90e4eae3a9ebc2b02f1b2b3d4e",
            title = "晴天",
            artist = "周杰伦",
            album = "叶惠美",
            albumId = "979871",
            durationMs = 269_000L,
            albumAudioId = 32155307L,
            mixSongId = "32155307"
        )

        val payload = KugouShareManager.buildSongSharePayload(song)
        assertFalse(payload.isPlaylist)
        assertEquals("晴天", payload.title)
        assertTrue(payload.conceptSchemeUri.startsWith("kugouyouth://start.weixin?"))
        assertTrue(payload.standardSchemeUri.startsWith("kugou://start.weixin?"))
        assertTrue(payload.conceptWakeupWebUrl.contains("pkgname=com.kugou.android.lite"))
        assertTrue(payload.conceptWakeupWebUrl.contains("ckey=CK1486481175239"))
        assertTrue(payload.standardWakeupWebUrl.contains("pkgname=com.kugou.android"))
        assertTrue(payload.standardWakeupWebUrl.contains("ckey=CK1350585625598"))
        assertTrue(payload.standardH5PlayUrl.contains("hash=B0EF8B90E4EAE3A9EBC2B02F1B2B3D4E"))
        assertTrue(payload.standardH5PlayUrl.contains("album_id=979871"))
        assertTrue(payload.standardH5PlayUrl.contains("album_audio_id=32155307"))

        // 验证生成的概念版与标准版分享文案均可被 KugouShareLinkParser 零网络逆向还原
        val parsedFromConcept = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextForConcept)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(parsedFromConcept)
        assertEquals("B0EF8B90E4EAE3A9EBC2B02F1B2B3D4E", parsedFromConcept!!.hash)
        assertEquals("32155307", parsedFromConcept.albumAudioId)
        assertEquals("晴天", parsedFromConcept.titleHint)
        assertEquals("周杰伦", parsedFromConcept.artistHint)

        val parsedFromStandard = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextForStandard)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(parsedFromStandard)
        assertEquals("B0EF8B90E4EAE3A9EBC2B02F1B2B3D4E", parsedFromStandard!!.hash)
        assertEquals("32155307", parsedFromStandard.albumAudioId)
    }

    @Test
    fun `歌单分享载荷生成支持同时唤醒官方酷狗与官方概念版`() {
        val playlist = Playlist(
            id = "collection_3_1340777928_4113434_0",
            title = "深夜微风独立电子精选",
            trackCount = 42,
            creatorName = "微风音乐人",
            globalCollectionId = "collection_3_1340777928_4113434_0",
            listCreateUserid = "1340777928",
            listCreateListid = "4113434"
        )

        val payload = KugouShareManager.buildPlaylistSharePayload(playlist)
        assertTrue(payload.isPlaylist)
        assertTrue(payload.conceptSchemeUri.startsWith("kugouyouth://start.weixin?"))
        assertTrue(payload.standardSchemeUri.startsWith("kugou://start.weixin?"))
        assertTrue(payload.conceptWakeupWebUrl.contains("pkgname=com.kugou.android.lite"))
        assertTrue(payload.standardWakeupWebUrl.contains("pkgname=com.kugou.android"))
        assertTrue(payload.standardH5PlayUrl.contains("global_collection_id=collection_3_1340777928_4113434_0"))

        val parsedFromUniversal = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextUniversal)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(parsedFromUniversal)
        assertEquals("collection_3_1340777928_4113434_0", parsedFromUniversal!!.globalCollectionId)
        assertEquals("4113434", parsedFromUniversal.specialId)
        assertEquals("深夜微风独立电子精选", parsedFromUniversal.titleHint)
    }

    @Test
    fun `内向解析器支持解析官方移动端单曲落地页phpParam与歌单页globalCollectionId`() {
        val mixSongHtml = """
            <!DOCTYPE html>
            <html>
            <head><script>
            var phpParam = {"hash":"70C858B654B89B75A5381022E5F29D54","album_id":"554433","album_audio_id":"99887766","song_info":{"data":{"hash":"70C858B654B89B75A5381022E5F29D54","320hash":"11223344556677889900AABBCCDDEEFF","sqhash":"FFEEDDCCBBAA00998877665544332211","songName":"兰亭序","singerName":"周杰伦","album_name":"魔杰座","timeLength":253}}};
            </script></head><body></body></html>
        """.trimIndent()

        val songDesc = KugouShareLinkParser.parseHtmlContent(
            mixSongHtml,
            "https://m.kugou.com/mixsong/abc123.html"
        ) as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(songDesc)
        assertEquals("70C858B654B89B75A5381022E5F29D54", songDesc!!.hash)
        assertEquals("11223344556677889900AABBCCDDEEFF", songDesc.hash320)
        assertEquals("FFEEDDCCBBAA00998877665544332211", songDesc.hashFlac)
        assertEquals("554433", songDesc.albumId)
        assertEquals("99887766", songDesc.albumAudioId)
        assertEquals("兰亭序", songDesc.titleHint)
        assertEquals("周杰伦", songDesc.artistHint)
        assertEquals(253L, songDesc.durationSecHint)

        val mobilePlistHtml = """
            <html>
            <body data-global-collection-id="Y29sbGVjdGlvbl8zXzEzNDA3Nzc5MjhfNDExMzQzNF8w">
            <script>
            var specialid = Number("4113434"),
                specialname = "官方概念版宝藏歌单",
                globalCollectionId = "collection_3_1340777928_4113434_0";
            </script>
            </body></html>
        """.trimIndent()

        val plistDesc = KugouShareLinkParser.parseHtmlContent(
            mobilePlistHtml,
            "https://m3ws.kugou.com/plist/list/2g5y2ee"
        ) as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(plistDesc)
        assertEquals("collection_3_1340777928_4113434_0", plistDesc!!.globalCollectionId)
        assertEquals("4113434", plistDesc.specialId)
        assertEquals("官方概念版宝藏歌单", plistDesc.titleHint)
    }

    @Test
    fun `剪贴板权限已同步至AppPermissionManager并支持独立开关控制`() {
        AppPermissionManager.resetForTest()
        try {
            assertTrue(AppPermissionManager.isPermissionEnabled(AppPermissionType.CLIPBOARD_ACCESS))
            AppPermissionManager.setPermissionEnabled(null, AppPermissionType.CLIPBOARD_ACCESS, false)
            assertFalse(AppPermissionManager.isPermissionEnabled(AppPermissionType.CLIPBOARD_ACCESS))
            AppPermissionManager.setPermissionEnabled(null, AppPermissionType.CLIPBOARD_ACCESS, true)
            assertTrue(AppPermissionManager.isPermissionEnabled(AppPermissionType.CLIPBOARD_ACCESS))
        } finally {
            AppPermissionManager.resetForTest()
        }
    }
}
