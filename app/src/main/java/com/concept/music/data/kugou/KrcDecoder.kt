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

        val (translations, phonetics) = extractKrcLanguageLayers(text)
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
                val lineIndex = lines.size
                val translation = translations.getOrNull(lineIndex)?.ifBlank { null }
                val phonetic = phonetics.getOrNull(lineIndex)?.ifBlank { null }
                lines.add(LyricLine(lineStart, lineDuration, lineText, words, translation, phonetic))
            }
        }

        lines.sortBy { it.startTimeMs }
        return LyricData(lines = lines, rawText = text, isKrc = true)
    }

    /**
     * [创新3] 同时提取 KRC `[language:...]` 中的中文翻译层 (translations) 与音译注音层 (phonetics: 罗马音/粤拼/韩文音译)
     */
    internal fun extractKrcLanguageLayers(text: String): Pair<List<String>, List<String>> {
        val translations = mutableListOf<String>()
        val phonetics = mutableListOf<String>()
        val langRegex = Regex("""\[language:([^\]]*)\]""")
        val langMatch = langRegex.find(text) ?: return translations to phonetics
        val b64 = langMatch.groupValues[1].trim()
        if (b64.isEmpty()) return translations to phonetics

        try {
            val padding = "=".repeat((4 - b64.length % 4) % 4)
            val decodedBytes = Base64.getMimeDecoder().decode(b64 + padding)
            val decodedStr = String(decodedBytes, Charsets.UTF_8)
            val root = com.google.gson.JsonParser.parseString(decodedStr).asJsonObject
            val contentArr = root.getAsJsonArray("content") ?: return translations to phonetics

            var translationObj: com.google.gson.JsonObject? = null
            var phoneticObj: com.google.gson.JsonObject? = null

            for (elem in contentArr) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val lc = obj.getAsJsonArray("lyricContent") ?: continue
                if (lc.size() == 0) continue

                val typeVal = obj.get("type")?.asInt ?: -1
                val hasChinese = lc.any { item ->
                    if (item.isJsonArray) {
                        item.asJsonArray.any { str -> str.asString.any { it in '\u4e00'..'\u9fff' } }
                    } else {
                        item.asString.any { it in '\u4e00'..'\u9fff' }
                    }
                }

                if (typeVal == 0 || (hasChinese && typeVal != 1)) {
                    if (translationObj == null || hasChinese) {
                        translationObj = obj
                    }
                } else if (typeVal == 1 || !hasChinese) {
                    if (phoneticObj == null) {
                        phoneticObj = obj
                    }
                }
            }

            if (translationObj == null && contentArr.size() == 1 && phoneticObj != null) {
                // 若仅有一个语言层且未标记 type=1，兼容旧测试将其作为默认翻译层，同时若非纯中文也可供音译使用
                val onlyObj = contentArr[0].asJsonObject
                val onlyType = onlyObj.get("type")?.asInt
                if (onlyType == null || onlyType == 0) {
                    translationObj = onlyObj
                }
            }

            fun parseLayerLines(obj: com.google.gson.JsonObject?, target: MutableList<String>) {
                val lc = obj?.getAsJsonArray("lyricContent") ?: return
                for (lineItem in lc) {
                    if (lineItem.isJsonArray) {
                        val lineArr = lineItem.asJsonArray
                        val joined = lineArr.joinToString(" ") { it.asString.trim() }.replace(Regex("\\s+"), " ").trim()
                        target.add(joined)
                    } else {
                        target.add(lineItem.asString.trim())
                    }
                }
            }

            parseLayerLines(translationObj, translations)
            parseLayerLines(phoneticObj, phonetics)
        } catch (_: Exception) {
            // 容错返回已提取部分
        }
        return translations to phonetics
    }

    fun parseLrcText(text: String): LyricData {
        if (text.isBlank()) return LyricData()

        val rawLines = text.lines()
        val rawParsed = mutableListOf<LyricLine>()
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
                // 检查是否为行内中英/音译分割: "原文 // 音译 // 翻译" 或 "Hello // 你好"
                val doubleSlashParts = if (lineText.contains(" // ")) {
                    lineText.split(" // ", limit = 3).map { it.trim() }.filter { it.isNotBlank() }
                } else null

                if (doubleSlashParts != null && doubleSlashParts.size == 3) {
                    rawParsed.add(LyricLine(startTimeMs, 0L, doubleSlashParts[0], translation = doubleSlashParts[2], phonetic = doubleSlashParts[1]))
                } else if (doubleSlashParts != null && doubleSlashParts.size == 2) {
                    rawParsed.add(LyricLine(startTimeMs, 0L, doubleSlashParts[0], translation = doubleSlashParts[1]))
                } else if (lineText.contains(" / ") && lineText.any { it in '\u4e00'..'\u9fff' }) {
                    val slashParts = lineText.split(" / ", limit = 2)
                    rawParsed.add(LyricLine(startTimeMs, 0L, slashParts[0].trim(), translation = slashParts[1].trim()))
                } else {
                    rawParsed.add(LyricLine(startTimeMs, 0L, lineText))
                }
            }
        }

        // 合并相同时间戳的翻译/音译行
        val lines = mutableListOf<LyricLine>()
        for (entry in rawParsed) {
            val last = lines.lastOrNull()
            if (last != null && kotlin.math.abs(last.startTimeMs - entry.startTimeMs) <= 100L) {
                if (last.translation == null) {
                    lines[lines.size - 1] = last.copy(translation = entry.text)
                } else if (last.phonetic == null) {
                    lines[lines.size - 1] = last.copy(phonetic = last.translation, translation = entry.text)
                } else {
                    lines.add(entry)
                }
            } else {
                lines.add(entry)
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
