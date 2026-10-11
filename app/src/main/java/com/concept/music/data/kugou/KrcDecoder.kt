package com.concept.music.data.kugou

import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.LyricWord
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

object KrcDecoder {
    private val XOR_KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)

    private val LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val WORD_REGEX = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>([^<]*)""")
    private val LRC_REGEX = Regex("""^\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\](.*)$""")

    private val METADATA_PREFIXES = listOf(
        "[id:", "[ar:", "[ti:", "[by:", "[hash:", "[al:", "[sign:", "[qq:", "[total:", "[offset:", "[language:", "[kana:"
    )

    fun decodeKrc(base64Content: String): String {
        return try {
            val rawBytes = Base64.getMimeDecoder().decode(base64Content.trim())
            if (rawBytes.size <= 4) return ""

            val decrypted = ByteArray(rawBytes.size - 4)
            for (i in decrypted.indices) {
                val b = rawBytes[i + 4].toInt() and 0xFF
                val k = XOR_KEY[i % XOR_KEY.size]
                decrypted[i] = (b xor k).toByte()
            }

            val inflater = InflaterInputStream(ByteArrayInputStream(decrypted))
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            var len: Int
            while (inflater.read(buffer).also { len = it } != -1) {
                output.write(buffer, 0, len)
            }
            output.toString("UTF-8")
        } catch (_: Exception) {
            ""
        }
    }

    fun parseKrcText(text: String): LyricData {
        if (text.isBlank()) return LyricData()

        val lines = mutableListOf<LyricLine>()
        val rawLines = text.split("\r\n", "\n")

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (METADATA_PREFIXES.any { line.startsWith(it, ignoreCase = true) }) continue

            val match = LINE_REGEX.find(line) ?: continue
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = match.groupValues[2].toLongOrNull() ?: continue
            val rest = match.groupValues[3]

            val wordMatches = WORD_REGEX.findAll(rest).toList()
            val words = mutableListOf<LyricWord>()
            val lineTextBuilder = StringBuilder()

            for (m in wordMatches) {
                val offset = m.groupValues[1].toLongOrNull() ?: 0L
                val duration = m.groupValues[2].toLongOrNull() ?: 0L
                val wordText = m.groupValues[3]
                if (wordText.isNotEmpty()) {
                    words.add(LyricWord(lineStart + offset, duration, wordText))
                    lineTextBuilder.append(wordText)
                }
            }

            val lineText = if (words.isNotEmpty()) lineTextBuilder.toString() else rest.replace(Regex("<[^>]+>"), "").trim()
            if (lineText.isNotEmpty()) {
                lines.add(LyricLine(lineStart, lineDuration, lineText, words))
            }
        }

        lines.sortBy { it.startTimeMs }
        return LyricData(lines = lines, rawText = text, isKrc = true)
    }

    fun parseLrcText(text: String): LyricData {
        if (text.isBlank()) return LyricData()

        val lines = mutableListOf<LyricLine>()
        val rawLines = text.split("\r\n", "\n")

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (METADATA_PREFIXES.any { line.startsWith(it, ignoreCase = true) }) continue

            val match = LRC_REGEX.find(line) ?: continue
            val min = match.groupValues[1].toLongOrNull() ?: continue
            val sec = match.groupValues[2].toLongOrNull() ?: continue
            val fractionRaw = match.groupValues[3]
            val ms = when (fractionRaw.length) {
                1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100
                2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10
                3 -> fractionRaw.toLongOrNull() ?: 0L
                else -> 0L
            }
            val startTimeMs = min * 60_000L + sec * 1000L + ms
            val lineText = match.groupValues[4].trim()
            if (lineText.isNotEmpty()) {
                lines.add(LyricLine(startTimeMs, 0L, lineText))
            }
        }

        for (i in 0 until lines.size - 1) {
            val current = lines[i]
            val next = lines[i + 1]
            val duration = (next.startTimeMs - current.startTimeMs).coerceAtLeast(500L)
            lines[i] = current.copy(durationMs = duration)
        }
        if (lines.isNotEmpty()) {
            val last = lines.last()
            lines[lines.size - 1] = last.copy(durationMs = 5000L)
        }

        lines.sortBy { it.startTimeMs }
        return LyricData(lines = lines, rawText = text, isKrc = false)
    }

    fun parseAuto(content: String, isBase64Krc: Boolean = false): LyricData {
        if (isBase64Krc) {
            val decoded = decodeKrc(content)
            if (decoded.isNotBlank()) {
                val data = parseKrcText(decoded)
                if (data.lines.isNotEmpty()) return data
            }
        }
        val fromKrc = parseKrcText(content)
        if (fromKrc.lines.isNotEmpty()) return fromKrc
        return parseLrcText(content)
    }
}
