package com.concept.music.data.kugou

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.DeflaterOutputStream

class KrcLyricTest {

    @Test
    fun parseKrcText_extractsWordTimingAndLines() {
        val krcSample = """
            [ti:测试歌曲]
            [ar:测试歌手]
            [al:测试专辑]
            [12500,4200]<0,300,0>命<300,400,0>运<700,500,0>之<1200,600,0>华
            [17000,3000]<0,400,0>心<400,500,0>之<900,600,0>所<1500,500,0>向
        """.trimIndent()

        val lyricData = KrcDecoder.parseKrcText(krcSample)
        assertTrue(lyricData.isKrc)
        assertEquals(2, lyricData.lines.size)

        val firstLine = lyricData.lines[0]
        assertEquals(12500L, firstLine.startTimeMs)
        assertEquals(4200L, firstLine.durationMs)
        assertEquals("命运之华", firstLine.text)
        assertEquals(4, firstLine.words.size)
        assertEquals(12500L, firstLine.words[0].startTimeMs)
        assertEquals("命", firstLine.words[0].text)
        assertEquals(12800L, firstLine.words[1].startTimeMs)
        assertEquals("运", firstLine.words[1].text)

        val secondLine = lyricData.lines[1]
        assertEquals(17000L, secondLine.startTimeMs)
        assertEquals(3000L, secondLine.durationMs)
        assertEquals("心之所向", secondLine.text)
        assertEquals(4, secondLine.words.size)

        // 测试索引定位
        assertEquals(-1, lyricData.getLineIndexAt(10000L))
        assertEquals(0, lyricData.getLineIndexAt(12500L))
        assertEquals(0, lyricData.getLineIndexAt(15000L))
        assertEquals(1, lyricData.getLineIndexAt(17000L))
        assertEquals(1, lyricData.getLineIndexAt(22000L))
    }

    @Test
    fun parseLrcText_extractsLrcLinesAndCalculatesDurations() {
        val lrcSample = """
            [ar:测试歌手]
            [00:05.50]第一句歌词
            [00:10.200]第二句歌词
            [00:18.00]第三句歌词
        """.trimIndent()

        val lyricData = KrcDecoder.parseLrcText(lrcSample)
        assertFalse(lyricData.isKrc)
        assertEquals(3, lyricData.lines.size)

        assertEquals(5500L, lyricData.lines[0].startTimeMs)
        assertEquals("第一句歌词", lyricData.lines[0].text)
        assertEquals(4700L, lyricData.lines[0].durationMs)

        assertEquals(10200L, lyricData.lines[1].startTimeMs)
        assertEquals("第二句歌词", lyricData.lines[1].text)
        assertEquals(7800L, lyricData.lines[1].durationMs)

        assertEquals(18000L, lyricData.lines[2].startTimeMs)
        assertEquals("第三句歌词", lyricData.lines[2].text)
    }

    @Test
    fun decodeKrc_roundtripVerification() {
        val originalText = "[1000,2000]<0,500,0>酷<500,500,0>狗"
        // 使用 zlib deflater 压缩
        val deflaterStream = ByteArrayOutputStream()
        val deflater = DeflaterOutputStream(deflaterStream)
        deflater.write(originalText.toByteArray(Charsets.UTF_8))
        deflater.finish()
        deflater.flush()
        val compressed = deflaterStream.toByteArray()

        // 异或加密
        val xorKey = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        val encrypted = ByteArray(compressed.size)
        for (i in compressed.indices) {
            val b = compressed[i].toInt() and 0xFF
            val k = xorKey[i % xorKey.size]
            encrypted[i] = (b xor k).toByte()
        }

        // 拼接 4 字节魔数头并 Base64 编码
        val header = byteArrayOf(0x6B, 0x72, 0x63, 0x31)
        val full = header + encrypted
        val base64 = Base64.getEncoder().encodeToString(full)

        val decoded = KrcDecoder.decodeKrc(base64)
        assertEquals(originalText, decoded)

        val parsed = KrcDecoder.parseAuto(base64, isBase64Krc = true)
        assertEquals(1, parsed.lines.size)
        assertEquals("酷狗", parsed.lines[0].text)
        assertEquals(2, parsed.lines[0].words.size)
    }
}
