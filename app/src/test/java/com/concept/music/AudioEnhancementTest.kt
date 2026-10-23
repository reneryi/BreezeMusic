package com.concept.music

import com.concept.music.data.kugou.*
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.service.EffectPreset
import com.concept.music.service.ReverbMode
import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class AudioEnhancementTest {

    @Test
    fun testSongHighestQualityDetection() {
        val songStandard = Song(
            id = "1",
            hash = "h1",
            title = "Test Standard",
            artist = "歌手A",
            availableQualities = listOf(AudioQuality.STANDARD)
        )
        assertEquals(AudioQuality.STANDARD, songStandard.highestQuality)

        val songHigh = Song(
            id = "2",
            hash = "h2",
            title = "Test High",
            artist = "歌手B",
            availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.HIGH)
        )
        assertEquals(AudioQuality.HIGH, songHigh.highestQuality)

        val songLossless = Song(
            id = "3",
            hash = "h3",
            title = "Test Lossless",
            artist = "歌手C",
            availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.HIGH, AudioQuality.LOSSLESS)
        )
        assertEquals(AudioQuality.LOSSLESS, songLossless.highestQuality)

        val songHiRes = Song(
            id = "4",
            hash = "h4",
            title = "Test HiRes",
            artist = "歌手D",
            availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.HIGH, AudioQuality.LOSSLESS, AudioQuality.HI_RES)
        )
        assertEquals(AudioQuality.HI_RES, songHiRes.highestQuality)
    }

    @Test
    fun testDeduceQuality_LossyNeverClassifiedAsLossless() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        // 即使 mp3 码率很大或虚标，有损格式永远判定为 HIGH (320) 或 STANDARD (128)，绝不判定为 LOSSLESS 或 HI_RES
        val (mp3Quality, _) = repo.deduceQuality(
            url = "http://fs.kgimg.com/song.mp3",
            bitrateRaw = 320L,
            fileSizeBytes = 10_000_000L,
            durationMs = 240_000L,
            extName = "mp3"
        )
        assertEquals(AudioQuality.HIGH, mp3Quality)

        val (m4aQuality, _) = repo.deduceQuality(
            url = "http://fs.kgimg.com/song.m4a",
            bitrateRaw = 256L,
            fileSizeBytes = 8_000_000L,
            durationMs = 240_000L,
            extName = "m4a"
        )
        assertEquals(AudioQuality.HIGH, m4aQuality)

        // 真实无损 flac 且码率 > 1500kbps 判定为 HI_RES
        val (flacHiResQuality, _) = repo.deduceQuality(
            url = "http://fs.kgimg.com/song.flac",
            bitrateRaw = 2300L,
            fileSizeBytes = 45_000_000L,
            durationMs = 240_000L,
            extName = "flac"
        )
        assertEquals(AudioQuality.HI_RES, flacHiResQuality)

        // 真实无损 flac 且标准母带码率 800kbps 判定为 LOSSLESS
        val (flacLosslessQuality, _) = repo.deduceQuality(
            url = "http://fs.kgimg.com/song.flac",
            bitrateRaw = 850L,
            fileSizeBytes = 25_000_000L,
            durationMs = 240_000L,
            extName = "flac"
        )
        assertEquals(AudioQuality.LOSSLESS, flacLosslessQuality)
    }

    @Test
    fun testKrcTranslationMetadataExtraction() {
        val rawTranslationJson = """{"content":[{"language":0,"lyricContent":[["第一行翻译"],["第二行翻译"]]}]}"""
        val encodedBase64 = Base64.getEncoder().encodeToString(rawTranslationJson.toByteArray(Charsets.UTF_8))

        val krcText = """
            [ti:Test Title]
            [language:$encodedBase64]
            [1000,2000]<0,500,0>Line<500,500,0>1
            [3000,2000]<0,500,0>Line<500,500,0>2
        """.trimIndent()

        val lyricData = KrcDecoder.parseKrcText(krcText)
        assertTrue(lyricData.hasTranslation)
        assertEquals("第一行翻译", lyricData.lines[0].translation)
        assertEquals("第二行翻译", lyricData.lines[1].translation)
    }

    @Test
    fun testViperEffectPresetsPresence() {
        // 校验 7 种 Viper 官方音效预设均存在
        val presets = EffectPreset.values()
        val presetNames = presets.map { it.name }

        assertTrue(presetNames.contains("VIPER_3D"))
        assertTrue(presetNames.contains("VIPER_VOCAL"))
        assertTrue(presetNames.contains("VIPER_BASS"))
        assertTrue(presetNames.contains("VIPER_HIFI"))
        assertTrue(presetNames.contains("VIPER_VINYL"))
        assertTrue(presetNames.contains("VIPER_SURROUND"))
        assertTrue(presetNames.contains("VIPER_DYNAMIC"))

        // 校验混响模式枚举包含主要场景
        val reverbModes = ReverbMode.values()
        assertTrue(reverbModes.any { it.name == "NONE" })
        assertTrue(reverbModes.any { it.name == "LARGE_HALL" })
        assertTrue(reverbModes.any { it.name == "PLATE" })
    }

    @Test
    fun testTreeBranchCommentsStructure() {
        val reply1 = SongComment(
            id = "r1",
            userId = "u2",
            userName = "乐友小张",
            userAvatar = "",
            content = "特别喜欢这里的钢琴转位",
            timeFormatted = "1小时前",
            likeCount = 12,
            ipLocation = "北京",
            parentId = "c1",
            rootId = "c1",
            replyUserName = "乐评老王"
        )

        val rootComment = SongComment(
            id = "c1",
            userId = "u1",
            userName = "乐评老王",
            userAvatar = "",
            content = "整首歌的情绪铺垫堪称典范！",
            timeFormatted = "2小时前",
            likeCount = 108,
            ipLocation = "广东",
            replyCount = 1,
            replies = listOf(reply1)
        )

        assertEquals("广东", rootComment.ipLocation)
        assertEquals(1, rootComment.replies.size)
        assertEquals("c1", rootComment.replies[0].parentId)
        assertEquals("乐评老王", rootComment.replies[0].replyUserName)
        assertEquals("北京", rootComment.replies[0].ipLocation)
    }

    @Test
    fun testSongSearchV2QualityParsing() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        val jsonHiRes = JsonObject().apply {
            addProperty("songname", "七里香")
            addProperty("singername", "周杰伦")
            addProperty("ResFileHash", "11111111111111111111111111111111")
            addProperty("ResFileSize", 60000000)
            addProperty("SQFileHash", "22222222222222222222222222222222")
            addProperty("SQFileSize", 30000000)
            addProperty("HQFileHash", "33333333333333333333333333333333")
            addProperty("HQFileSize", 10000000)
            addProperty("FileHash", "44444444444444444444444444444444")
            addProperty("FileSize", 4000000)
        }
        val songHiRes = repo.parseSong(jsonHiRes)
        assertEquals(AudioQuality.HI_RES, songHiRes.highestQuality)
        assertEquals(4, songHiRes.availableQualities.size)
        assertEquals("11111111111111111111111111111111", songHiRes.hash)
        assertEquals("11111111111111111111111111111111", songHiRes.hashForQuality(AudioQuality.HI_RES))
        assertEquals("22222222222222222222222222222222", songHiRes.hashForQuality(AudioQuality.LOSSLESS))
        assertEquals("33333333333333333333333333333333", songHiRes.hashForQuality(AudioQuality.HIGH))
        assertEquals("44444444444444444444444444444444", songHiRes.hashForQuality(AudioQuality.STANDARD))
    }

    @Test
    fun testQualityFilteringForStandardOnlyAndHighOnly() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val auth = AuthRepository(transport, store)
        val accounts = AccountRepository(store, auth, transport)
        val repo = MusicApiRepository(transport, accounts)

        // 只有 128k 文件的冷门歌曲：仅包含 STANDARD，对话框中决不能出现无损或 Hi-Res
        val jsonStandard = JsonObject().apply {
            addProperty("songname", "标准音质单曲")
            addProperty("singername", "独立音乐人")
            addProperty("FileHash", "55555555555555555555555555555555")
            addProperty("FileSize", 3000000)
        }
        val songStandard = repo.parseSong(jsonStandard)
        assertEquals(listOf(AudioQuality.STANDARD), songStandard.availableQualities)
        assertEquals(AudioQuality.STANDARD, songStandard.highestQuality)
        assertFalse(songStandard.isLossless)
        assertFalse(songStandard.isHiRes)

        // 仅有 128k 和 320k 的歌曲：不包含 LOSSLESS 与 HI_RES
        val jsonHigh = JsonObject().apply {
            addProperty("songname", "高品质单曲")
            addProperty("singername", "电台原声")
            addProperty("FileHash", "66666666666666666666666666666666")
            addProperty("FileSize", 3500000)
            addProperty("HQFileHash", "77777777777777777777777777777777")
            addProperty("HQFileSize", 9000000)
        }
        val songHigh = repo.parseSong(jsonHigh)
        assertEquals(listOf(AudioQuality.STANDARD, AudioQuality.HIGH), songHigh.availableQualities)
        assertEquals(AudioQuality.HIGH, songHigh.highestQuality)
        assertFalse(songHigh.isLossless)
        assertFalse(songHigh.isHiRes)
    }

    @Test
    fun testKrcChineseTranslationPriorityOverRomaji() {
        // 构造日韩歌曲场景：content[0] 为罗马音/注音 (无中文)，content[1] 为真正中文翻译 (含汉字)
        val jsonWithKanaAndChinese = """{
            "content": [
                {
                    "language": 0,
                    "lyricContent": [["arigatou gozaimasu"], ["sayounara"]]
                },
                {
                    "language": 1,
                    "lyricContent": [["非常感谢您"], ["再会"]]
                }
            ]
        }"""
        val encodedBase64 = Base64.getEncoder().encodeToString(jsonWithKanaAndChinese.toByteArray(Charsets.UTF_8))
        val krcText = """
            [ti:Japanese Song]
            [language:$encodedBase64]
            [1000,2000]<0,500,0>Line<500,500,0>1
            [3000,2000]<0,500,0>Line<500,500,0>2
        """.trimIndent()

        val lyricData = KrcDecoder.parseKrcText(krcText)
        assertTrue(lyricData.hasTranslation)
        assertEquals("非常感谢您", lyricData.lines[0].translation)
        assertEquals("再会", lyricData.lines[1].translation)
    }

    @Test
    fun testSongFallbackQualityResolution() {
        // 降级哈希测试：当请求 Hi-Res 时若未配置 Hi-Res hash，按档位向下平滑 fallback
        val songLosslessOnly = Song(
            id = "s_flac",
            hash = "22222222222222222222222222222222",
            title = "FLAC Only Song",
            artist = "歌手",
            hashFlac = "22222222222222222222222222222222",
            hash320 = "33333333333333333333333333333333",
            hash128 = "44444444444444444444444444444444",
            availableQualities = listOf(AudioQuality.STANDARD, AudioQuality.HIGH, AudioQuality.LOSSLESS)
        )
        // 请求不存在的 HI_RES，hashForQuality 降级为最高的可用 FLAC
        assertEquals("22222222222222222222222222222222", songLosslessOnly.hashForQuality(AudioQuality.HI_RES))
        assertEquals("33333333333333333333333333333333", songLosslessOnly.hashForQuality(AudioQuality.HIGH))
    }

    @Test
    fun testAudioEffectBandsSerializationAndPresetFallback() {
        val customBands = listOf(120, -40, 80, 150, -90)
        val serialized = com.concept.music.service.AudioEffectManager.serializeBands(customBands)
        assertEquals("120,-40,80,150,-90", serialized)
        val restored = com.concept.music.service.AudioEffectManager.parseBandsString(serialized, EffectPreset.CUSTOM)
        assertEquals(customBands, restored)

        val viper3dBands = com.concept.music.service.AudioEffectManager.parseBandsString(null, EffectPreset.VIPER_3D)
        assertEquals(listOf(60, 20, -40, 60, 90), viper3dBands)
    }

    @Test
    fun testAccompanyHashAndFreePrivilegeExtraction() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val repo = MusicApiRepository(transport, AccountRepository(store, AuthRepository(transport, store), transport))
        val json = JsonObject().apply {
            addProperty("songname", "晴天")
            addProperty("singername", "周杰伦")
            addProperty("FileHash", "44444444444444444444444444444444")
            addProperty("AccompanyFileHash", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            addProperty("privilege", 0)
        }
        val parsed = repo.parseSong(json)
        assertTrue(parsed.hasAccompaniment)
        assertEquals("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", parsed.accompanyHash)
        assertTrue(parsed.isFreePrivilege)
    }

    @Test
    fun testEmptySongCommentsReturnsEmptyInsteadOfFakeComments() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val repo = MusicApiRepository(transport, AccountRepository(store, AuthRepository(transport, store), transport))
        val song = Song(id = "999", albumAudioId = 999L, title = "无评论新歌", artist = "独立音乐人")
        val comments = repo.songComments(song, type = "hot", page = 1)
        assertTrue("无评论歌曲不应注入伪造评论", comments.isEmpty())
    }

    @Test
    fun testKrcPhoneticAndTranslationDualLayerExtraction() {
        val langJson = "{\"content\":[{\"type\":0,\"lyricContent\":[[\"穿过夜空\"],[\"追逐星光\"]]},{\"type\":1,\"lyricContent\":[[\"ka \",\"ke \",\"nu \",\"ke\"],[\"ho \",\"shi \",\"wo \",\"o \",\"u\"]]}]}"
        val b64 = java.util.Base64.getEncoder().encodeToString(langJson.toByteArray(Charsets.UTF_8))
        val rawKrc = "[ti:夜に駆ける]\n[language:$b64]\n[1000,2000]<0,500,0>夜<500,500,0>に<1000,1000,0>駆ける\n[3500,2000]<0,800,0>星<800,1200,0>を追う"

        val parsed = com.concept.music.data.kugou.KrcDecoder.parseKrcText(rawKrc)
        assertEquals(2, parsed.lines.size)
        assertEquals("穿过夜空", parsed.lines[0].translation)
        assertEquals("ka ke nu ke", parsed.lines[0].phonetic)
        assertEquals("追逐星光", parsed.lines[1].translation)
        assertEquals("ho shi wo o u", parsed.lines[1].phonetic)
    }

    @Test
    fun testThemeModesAndPlaylistSortModesExist() {
        val modes = com.concept.music.ui.theme.AppThemeMode.entries
        assertEquals(3, modes.size)
        assertTrue(modes.contains(com.concept.music.ui.theme.AppThemeMode.DARK_ABYSS_BLUE))
        assertTrue(modes.contains(com.concept.music.ui.theme.AppThemeMode.LIGHT_WATERCOLOR))

        val sortModes = com.concept.music.ui.library.PlaylistSortMode.entries
        assertEquals(4, sortModes.size)
    }

    @Test
    fun testHighQualityInRelateGoodsAndHashHighIsNotMisclassifiedAsHiRes() {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ) = ApiResponse(JsonObject())
        }
        val repo = MusicApiRepository(transport, AccountRepository(store, AuthRepository(transport, store), transport))
        val rawJson = com.google.gson.JsonParser.parseString(
            """
            {
              "songname": "We Won't Be Alone",
              "singername": "Feint",
              "hash": "11111111111111111111111111111111",
              "hash_high": "22222222222222222222222222222222",
              "filesize_high": 9400000,
              "relate_goods": [
                {"hash": "11111111111111111111111111111111", "quality": "128", "size": 3800000},
                {"hash": "22222222222222222222222222222222", "quality": "high", "size": 9400000},
                {"hash": "33333333333333333333333333333333", "quality": "flac", "size": 26500000}
              ]
            }
            """
        ).asJsonObject
        val song = repo.parseSong(rawJson)
        assertFalse("320k high 与普通 flac 不应被误标为 Hi-Res", song.isHiRes)
        assertTrue("应正确识别 SQ 无损", song.isLossless)
        assertEquals("22222222222222222222222222222222", song.hash320)
        assertEquals("33333333333333333333333333333333", song.hashFlac)
    }

    @Test
    fun testPlaylistPaginationContinuesWhenFirstPageHas198FilteredSongs() = kotlinx.coroutines.test.runTest {
        val store = MemoryStore()
        val transport = object : KugouTransport {
            override suspend fun call(
                path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
                host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?
            ): ApiResponse {
                if (path == "/pubsongs/v2/get_other_list_file_nofilt") {
                    val beginIdx = query["begin_idx"]?.toIntOrNull() ?: 0
                    val arr = com.google.gson.JsonArray()
                    val countInPage = if (beginIdx == 0) 198 else if (beginIdx == 200) 135 else 0
                    for (i in 1..countInPage) {
                        val idx = beginIdx + i
                        val h = idx.toString().padStart(32, 'a')
                        arr.add(com.google.gson.JsonObject().apply {
                            addProperty("songname", "Song_$idx")
                            addProperty("hash", h)
                            addProperty("audio_id", idx)
                        })
                    }
                    val data = com.google.gson.JsonObject().apply {
                        addProperty("total", 333)
                        add("info", arr)
                    }
                    return ApiResponse(com.google.gson.JsonObject().apply {
                        addProperty("status", 1)
                        add("data", data)
                    })
                }
                return ApiResponse(JsonObject())
            }
        }
        val repo = MusicApiRepository(transport, AccountRepository(store, AuthRepository(transport, store), transport))
        val allSongs = repo.fetchPlaylistSongsAll("collection_3_123_456_0")
        assertEquals(333, allSongs.size)
    }

    @Test
    fun testConceptMasteringAudioProcessorLittleEndianAndBitPerfectBypass() {
        val processor = com.concept.music.service.ConceptMasteringAudioProcessor()
        val format = androidx.media3.common.audio.AudioProcessor.AudioFormat(44100, 2, androidx.media3.common.C.ENCODING_PCM_16BIT)
        val outFormat = processor.configure(format)
        processor.flush()
        assertEquals(44100, outFormat.sampleRate)
        assertEquals(2, outFormat.channelCount)

        // 构造小端序 16-bit PCM 峰值采样（32000 与 -32000，超过 0.88 * 32767 = 28835）
        val input = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        input.putShort(32000.toShort())
        input.putShort((-32000).toShort())
        input.putShort(16384.toShort())
        input.putShort((-16384).toShort())
        input.flip()

        // 默认 OFF 直通态下应保持 Bit-Perfect 无损原值，不被 softLimit 压缩
        processor.queueInput(input)
        val output = processor.output.order(java.nio.ByteOrder.LITTLE_ENDIAN)
        assertEquals(8, output.remaining())
        assertEquals(32000.toShort(), output.short)
        assertEquals((-32000).toShort(), output.short)
        assertEquals(16384.toShort(), output.short)
        assertEquals((-16384).toShort(), output.short)
    }
}
