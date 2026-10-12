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

        val translations = extractKrcTranslations(text)
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
                lines.add(LyricLine(lineStart, lineDuration, lineText, words, translation))
            }
        }

        lines.sortBy { it.startTimeMs }
        return LyricData(lines = lines, rawText = text, isKrc = true)
    }

    private fun extractKrcTranslations(text: String): List<String> {
        val result = mutableListOf<String>()
        val langRegex = Regex("""\[language:([^\]]*)\]""")
        val langMatch = langRegex.find(text) ?: return result
        val b64 = langMatch.groupValues[1].trim()
        if (b64.isEmpty()) return result

        try {
            val padding = "=".repeat((4 - b64.length % 4) % 4)
            val decodedBytes = Base64.getMimeDecoder().decode(b64 + padding)
            val decodedStr = String(decodedBytes, Charsets.UTF_8)
            val root = com.google.gson.JsonParser.parseString(decodedStr).asJsonObject
            val contentArr = root.getAsJsonArray("content") ?: return result

            var translationObj: com.google.gson.JsonObject? = null
            // 优先查找含有汉字内容的对象，保证日韩欧美歌曲提取到的是中文翻译而非罗马音/假名
            for (elem in contentArr) {
                if (!elem.isJsonObject) continue
                val obj = elem.asJsonObject
                val lc = obj.getAsJsonArray("lyricContent") ?: continue
                val hasChinese = lc.any { item ->
                    if (item.isJsonArray) {
                        item.asJsonArray.any { str -> str.asString.any { it in '\u4e00'..'\u9fff' } }
                    } else {
                        item.asString.any { it in '\u4e00'..'\u9fff' }
                    }
                }
                if (hasChinese || obj.get("language")?.asInt == 0 || obj.get("type")?.asInt == 0) {
                    translationObj = obj
                    if (hasChinese) break
                }
            }
            if (translationObj == null) {
                for (elem in contentArr) {
                    if (!elem.isJsonObject) continue
                    val obj = elem.asJsonObject
                    val lc = obj.getAsJsonArray("lyricContent") ?: continue
                    if (lc.size() > 0) {
                        translationObj = obj
                        break
                    }
                }
            }

            if (translationObj != null) {
                val lc = translationObj.getAsJsonArray("lyricContent")
                if (lc != null) {
                    for (lineItem in lc) {
                        if (lineItem.isJsonArray) {
                            val lineArr = lineItem.asJsonArray
                            val joined = lineArr.joinToString(" ") { it.asString.trim() }.trim()
                            result.add(joined)
                        } else {
                            result.add(lineItem.asString.trim())
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 容错返回已提取部分
        }
        return result
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
                // 检查是否为行内中英双语分割: "Hello world / 你好世界" 或 "Hello // 你好"
                val slashParts = if (lineText.contains(" // ")) {
                    lineText.split(" // ", limit = 2)
                } else if (lineText.contains(" / ") && lineText.any { it in '\u4e00'..'\u9fff' }) {
                    lineText.split(" / ", limit = 2)
                } else null

                if (slashParts != null && slashParts.size == 2 && slashParts[0].isNotBlank() && slashParts[1].isNotBlank()) {
                    rawParsed.add(LyricLine(startTimeMs, 0L, slashParts[0].trim(), translation = slashParts[1].trim()))
                } else {
                    rawParsed.add(LyricLine(startTimeMs, 0L, lineText))
                }
            }
        }

        // 合并相同时间戳的翻译行 (例如重复时间戳: [00:10.00]English 与 [00:10.00]中文翻译)
        val lines = mutableListOf<LyricLine>()
        for (entry in rawParsed) {
            val last = lines.lastOrNull()
            if (last != null && kotlin.math.abs(last.startTimeMs - entry.startTimeMs) <= 100L && last.translation == null) {
                lines[lines.size - 1] = last.copy(translation = entry.text)
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
