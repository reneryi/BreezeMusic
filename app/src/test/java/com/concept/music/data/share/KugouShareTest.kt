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
    fun `单曲分享载荷生成支持同时唤醒官方酷狗与官方概念版且不含应用宝误识别域名`() {
        val song = Song(
            id = "18758291",
            hash = "944CB2E541CDB0A6DD77364A561C97EE",
            hash128 = "CD890FD40B13EF3A2BE78CA99119C79F",
            mp3Hash128 = "6FB3EB3F13CD64DF6E9784F6E487BAB1",
            hash320 = "24EC0CBAE11A3E8D014A22219A6AFEE6",
            hashFlac = "A57CD29CB103445F42F3826819F837C7",
            hashHiRes = "944CB2E541CDB0A6DD77364A561C97EE",
            fileSize128 = 3423691L,
            fileSize320 = 8558947L,
            fileSizeFlac = 24903667L,
            title = "勋章",
            artist = "鹿晗",
            album = "Reloaded 重启",
            albumId = "2059004",
            durationMs = 213_000L,
            audioId = 18758291L,
            albumAudioId = 56718839L,
            mixSongId = "56718839"
        )

        val payload = KugouShareManager.buildSongSharePayload(song)
        assertFalse(payload.isPlaylist)
        assertEquals("勋章", payload.title)
        assertTrue(payload.conceptSchemeUri.startsWith("kugouyouth://start.weixin?"))
        assertTrue(payload.standardSchemeUri.startsWith("kugou://start.weixin?"))

        // 验证概念版 Scheme 使用 cmd=103301，标准版 Scheme 使用 cmd=212
        val parsedConceptScheme = KugouShareLinkParser.parseShareTextDirectly(payload.conceptSchemeUri)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(parsedConceptScheme)
        assertEquals("6FB3EB3F13CD64DF6E9784F6E487BAB1", parsedConceptScheme!!.hash)
        assertEquals("24EC0CBAE11A3E8D014A22219A6AFEE6", parsedConceptScheme.hash320)
        assertEquals("A57CD29CB103445F42F3826819F837C7", parsedConceptScheme.hashFlac)
        assertEquals("56718839", parsedConceptScheme.albumAudioId)
        assertEquals("2059004", parsedConceptScheme.albumId)

        // 验证分享文案全部使用官方酷狗域名，彻底剔除 a.app.qq.com
        assertFalse(payload.shareTextUniversal.contains("a.app.qq.com"))
        assertTrue(payload.conceptWakeupWebUrl.startsWith("https://h5.kugou.com/v2/v-5a15aeb1/index.html"))
        assertTrue(payload.standardWakeupWebUrl.startsWith("https://m.kugou.com/share/?action=single"))
        assertTrue(payload.standardH5PlayUrl.contains("hash=6FB3EB3F13CD64DF6E9784F6E487BAB1"))
        assertTrue(payload.standardH5PlayUrl.contains("album_id=2059004"))
        assertTrue(payload.standardH5PlayUrl.contains("album_audio_id=56718839"))

        // 验证生成的概念版与标准版分享文案均可被 KugouShareLinkParser 零网络逆向还原
        val parsedFromConcept = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextForConcept)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(parsedFromConcept)
        assertEquals("6FB3EB3F13CD64DF6E9784F6E487BAB1", parsedFromConcept!!.hash)
        assertEquals("56718839", parsedFromConcept.albumAudioId)

        val parsedFromStandard = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextForStandard)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(parsedFromStandard)
        assertEquals("6FB3EB3F13CD64DF6E9784F6E487BAB1", parsedFromStandard!!.hash)
        assertEquals("56718839", parsedFromStandard.albumAudioId)
    }

    @Test
    fun `歌单分享载荷生成支持同时唤醒官方酷狗与官方概念版`() {
        val playlist = Playlist(
            id = "collection_3_2132029040_279_0",
            title = "2026最火伤感歌曲：刀刀催人泪，句句皆心碎",
            trackCount = 96,
            creatorName = "南音",
            globalCollectionId = "collection_3_2132029040_279_0",
            listCreateUserid = "2132029040",
            listCreateListid = "279",
            specialId = "6364989"
        )

        val payload = KugouShareManager.buildPlaylistSharePayload(playlist)
        assertTrue(payload.isPlaylist)
        assertTrue(payload.conceptSchemeUri.startsWith("kugouyouth://start.weixin?"))
        assertTrue(payload.standardSchemeUri.startsWith("kugou://start.weixin?"))
        assertFalse(payload.shareTextUniversal.contains("a.app.qq.com"))
        assertTrue(payload.conceptWakeupWebUrl.startsWith("https://activity.kugou.com/share/v-a00a45b0/index.html"))
        assertTrue(payload.conceptWakeupWebUrl.contains("specialid=6364989"))
        assertTrue(payload.conceptWakeupWebUrl.contains("global_specialid=collection_3_2132029040_279_0"))
        assertTrue(payload.standardWakeupWebUrl.startsWith("https://m.kugou.com/plist/list/6364989/"))
        assertTrue(payload.standardWakeupWebUrl.contains("global_collection_id=collection_3_2132029040_279_0"))
        assertFalse(payload.shareTextUniversal.contains("qq_client"))

        val parsedFromConceptScheme = KugouShareLinkParser.parseShareTextDirectly(payload.conceptSchemeUri)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(parsedFromConceptScheme)
        assertEquals("collection_3_2132029040_279_0", parsedFromConceptScheme!!.globalCollectionId)
        assertEquals("6364989", parsedFromConceptScheme.specialId)

        val parsedFromUniversal = KugouShareLinkParser.parseShareTextDirectly(payload.shareTextUniversal)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(parsedFromUniversal)
        assertEquals("collection_3_2132029040_279_0", parsedFromUniversal!!.globalCollectionId)
        assertEquals("6364989", parsedFromUniversal.specialId)
    }

    @Test
    fun `内向解析器支持解析官方酷狗音乐歌单songlist_gcid与window_output及概念版H5链接`() {
        // 1. 测试官方酷狗音乐歌单长链 (gcid_xxx 零网络离线解码还原为 collection_xxx) 与 window.$output HTML 解析
        val kugouStandardPlaylistUrl =
            "https://m.kugou.com/songlist/gcid_3z15kqlufz7yz04d/?src_cid=3z15kqlufz7yz04d&uid=1503516299&chl=qq_client&iszlist=1"
        val partialDesc = KugouShareLinkParser.parseUrlParametersOnly(kugouStandardPlaylistUrl)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(partialDesc)
        assertEquals("collection_3_2132029040_279_0", partialDesc!!.globalCollectionId)
        assertTrue(partialDesc.isComplete)

        val songlistHtml = """
            <!DOCTYPE html>
            <html><head><script>
            window.${'$'}output = {"status":1,"gcid":"gcid_3z15kqlufz7yz04d","info":{"listinfo":{"id":"gcid_3z15kqlufz7yz04d","specialid":6364989,"name":"2026最火伤感歌曲：刀刀催人泪，句句皆心碎","count":96,"username":"南音","img":"http://imge.kugou.com/soft/collection/{size}/20240223/20240223163701362709.jpg"},"list":[{"hash":"2F1AE2A3697270C80BB2DB1AEBF89B4D","name":"任夏 - 祝你","timelen":211000,"album_id":84829248,"audio_id":302627804}]}};
            </script></head><body></body></html>
        """.trimIndent()

        val htmlDesc = KugouShareLinkParser.parseHtmlContent(songlistHtml, kugouStandardPlaylistUrl)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(htmlDesc)
        assertTrue(htmlDesc!!.isComplete)
        assertEquals("6364989", htmlDesc.specialId)
        assertEquals("6364989", htmlDesc.preferredPlaylistId)
        assertEquals("2026最火伤感歌曲：刀刀催人泪，句句皆心碎", htmlDesc.titleHint)
        assertEquals("南音", htmlDesc.creatorNameHint)
        assertEquals(1, htmlDesc.embeddedSongs.size)
        assertEquals("2F1AE2A3697270C80BB2DB1AEBF89B4D", htmlDesc.embeddedSongs.first().hash)

        // 2. 测试官方酷狗概念版歌单长链解析
        val conceptPlaylistUrl =
            "https://activity.kugou.com/share/v-a00a45b0/index.html?specialid=6364989&global_specialid=collection_3_2132029040_279_0&src_uid=1503516299&cType=1"
        val conceptPlDesc = KugouShareLinkParser.parseShareTextDirectly(conceptPlaylistUrl)
            as? ParsedKugouShareDescriptor.PlaylistDescriptor
        assertNotNull(conceptPlDesc)
        assertTrue(conceptPlDesc!!.isComplete)
        assertEquals("collection_3_2132029040_279_0", conceptPlDesc.globalCollectionId)
        assertEquals("6364989", conceptPlDesc.specialId)

        // 3. 测试官方酷狗音乐单曲分享链接 e_album_audio_id=xrojbed 离线解码与 phpParam 优先提取 album_img 而非歌手头像
        val standardSongShareUrl =
            "https://m.kugou.com/share/?action=single&hash=6fb3eb3f13cd64df6e9784f6e487bab1&album_id=2059004&e_album_audio_id=xrojbed"
        val stdSongDesc = KugouShareLinkParser.parseShareTextDirectly(standardSongShareUrl)
            as? ParsedKugouShareDescriptor.SingleSongDescriptor
        assertNotNull(stdSongDesc)
        assertEquals("6FB3EB3F13CD64DF6E9784F6E487BAB1", stdSongDesc!!.hash)
        assertEquals("56718839", stdSongDesc.albumAudioId)

        val mixSongHtml = """
            <!DOCTYPE html>
            <html>
            <head><script>
            var phpParam = {"hash":"70C858B654B89B75A5381022E5F29D54","album_id":"554433","album_audio_id":"99887766","song_info":{"data":{"hash":"70C858B654B89B75A5381022E5F29D54","320hash":"11223344556677889900AABBCCDDEEFF","sqhash":"FFEEDDCCBBAA00998877665544332211","songName":"兰亭序","singerName":"周杰伦","album_name":"魔杰座","imgUrl":"http://singerimg.kugou.com/uploadpic/softhead/{size}/20241225/20241225182920226714.jpg","album_img":"http://imge.kugou.com/stdmusic/{size}/20250217/20250217144212257577.jpg","timeLength":253}}};
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
        assertEquals("http://imge.kugou.com/stdmusic/480/20250217/20250217144212257577.jpg", songDesc.coverUrlHint)
        assertEquals(253L, songDesc.durationSecHint)
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
