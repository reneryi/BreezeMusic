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
    private const val MAX_DECOMPRESSED_BYTES = 2 * 1024 * 1024 // 2 MB 解压上限防炸弹

    private val LINE_REGEX = Regex("""^\[(\d+),(\d+)\](.*)$""")
    private val WORD_REGEX = Regex("""<(-?\d+),(-?\d+)(?:,-?\d+)?>([^<]*)""")
    private val LRC_TAG_REGEX = Regex("""\[(\d{1,2}):(\d{2})(?:\.(\d{1,3}))?\]""")

    private val METADATA_PREFIXES = listOf(
        "[id:", "[ar:", "[ti:", "[by:", "[hash:", "[al:", "[sign:", "[qq:", "[total:", "[offset:", "[language:", "[kana:"
    )

    internal fun unescapeLyricEntities(raw: String): String {
        if (!raw.contains('&')) return raw
        return raw
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&#x27;", "'")
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
    }

    fun decodeKrc(base64Content: String): String {
        return try {
            val rawBytes = Base64.getMimeDecoder().decode(base64Content.trim())
            if (rawBytes.size <= 4) return ""
            // 校验 krc1 魔数头
            val isKrc1 = rawBytes[0] == 'k'.code.toByte() &&
                rawBytes[1] == 'r'.code.toByte() &&
                rawBytes[2] == 'c'.code.toByte() &&
                rawBytes[3] == '1'.code.toByte()
            if (!isKrc1) return ""

            val decrypted = ByteArray(rawBytes.size - 4)
            for (i in decrypted.indices) {
                val b = rawBytes[i + 4].toInt() and 0xFF
                val k = XOR_KEY[i % XOR_KEY.size]
                decrypted[i] = (b xor k).toByte()
            }

            // 使用 .use 确保底层原生 Zlib Inflater 立即释放，同时限制最大解压字节数防 OOM
            InflaterInputStream(ByteArrayInputStream(decrypted)).use { inflater ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(1024)
                var totalRead = 0
                var len: Int
                while (inflater.read(buffer).also { len = it } != -1) {
                    totalRead += len
                    if (totalRead > MAX_DECOMPRESSED_BYTES) {
                        throw IllegalStateException("KRC 解压体积超出安全上限")
                    }
                    output.write(buffer, 0, len)
                }
                output.toString("UTF-8")
            }
        } catch (_: Exception) {
            ""
        }
    }

    fun parseKrcText(text: String): LyricData {
        if (text.isBlank()) return LyricData()

        val (translations, phonetics) = extractKrcLanguageLayers(text)
        val lines = mutableListOf<LyricLine>()
        val rawLines = text.split("\r\n", "\n")

        val totalTimeLines = rawLines.count { raw ->
            val trimmed = raw.trim()
            trimmed.isNotEmpty() &&
                METADATA_PREFIXES.none { trimmed.startsWith(it, ignoreCase = true) } &&
                LINE_REGEX.matches(trimmed)
        }
        val useRawTimeIndexForTrans = translations.size == totalTimeLines
        val useRawTimeIndexForPhonetic = phonetics.size == totalTimeLines
        var rawTimeLineIndex = 0

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (METADATA_PREFIXES.any { line.startsWith(it, ignoreCase = true) }) continue

            val match = LINE_REGEX.find(line) ?: continue
            val currentTimeIdx = rawTimeLineIndex++
            val lineStart = match.groupValues[1].toLongOrNull() ?: continue
            val lineDuration = match.groupValues[2].toLongOrNull() ?: continue
            val rest = match.groupValues[3]

            val wordMatches = WORD_REGEX.findAll(rest).toList()
            val words = mutableListOf<LyricWord>()
            val lineTextBuilder = StringBuilder()

            for (m in wordMatches) {
                val offset = m.groupValues[1].toLongOrNull() ?: 0L
                val duration = m.groupValues[2].toLongOrNull() ?: 0L
                val wordText = unescapeLyricEntities(m.groupValues[3])
                if (wordText.isNotEmpty()) {
                    words.add(LyricWord(lineStart + offset, duration, wordText))
                    lineTextBuilder.append(wordText)
                }
            }

            val lineText = if (words.isNotEmpty()) lineTextBuilder.toString() else unescapeLyricEntities(rest.replace(Regex("<[^>]+>"), "").trim())
            if (lineText.isNotEmpty()) {
                val nonBlankIndex = lines.size
                val transIdx = if (useRawTimeIndexForTrans) currentTimeIdx else nonBlankIndex
                val phoneticIdx = if (useRawTimeIndexForPhonetic) currentTimeIdx else nonBlankIndex
                val translation = translations.getOrNull(transIdx)?.ifBlank { null }
                val phonetic = phonetics.getOrNull(phoneticIdx)?.ifBlank { null }
                lines.add(LyricLine(lineStart, lineDuration, lineText, words, translation, phonetic))
            }
        }

        lines.sortBy { it.startTimeMs }
        return LyricData(lines = lines, rawText = text, isKrc = true)
    }

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
                var hasChinese = false
                var multiTokenRowCount = 0
                var nonEmptyRowCount = 0

                for (item in lc) {
                    if (item.isJsonArray) {
                        val arr = item.asJsonArray
                        val nonBlankTokens = arr.mapNotNull {
                            val s = it.asString.trim()
                            if (s.isNotEmpty() && s != "//") s else null
                        }
                        if (nonBlankTokens.isNotEmpty()) {
                            nonEmptyRowCount++
                            if (arr.size() > 1) multiTokenRowCount++
                            if (nonBlankTokens.any { tok -> tok.any { ch -> ch in '\u4e00'..'\u9fff' } }) {
                                hasChinese = true
                            }
                        }
                    } else if (item.isJsonPrimitive) {
                        val s = item.asString.trim()
                        if (s.isNotEmpty() && s != "//") {
                            nonEmptyRowCount++
                            if (s.any { ch -> ch in '\u4e00'..'\u9fff' }) {
                                hasChinese = true
                            }
                        }
                    }
                }

                val isMultiTokenPerLine = nonEmptyRowCount > 0 && multiTokenRowCount * 2 >= nonEmptyRowCount

                when {
                    hasChinese && !isMultiTokenPerLine -> {
                        if (translationObj == null || typeVal == 1) {
                            translationObj = obj
                        } else if (phoneticObj == null) {
                            phoneticObj = obj
                        }
                    }
                    !hasChinese || isMultiTokenPerLine || typeVal == 0 -> {
                        if (phoneticObj == null || typeVal == 0) {
                            phoneticObj = obj
                        } else if (translationObj == null && hasChinese) {
                            translationObj = obj
                        }
                    }
                    typeVal == 1 -> {
                        if (translationObj == null) translationObj = obj
                    }
                }
            }

            fun cleanLayerText(raw: String): String {
                val trimmed = unescapeLyricEntities(raw.trim())
                return if (trimmed == "//" || trimmed == "/") "" else trimmed
            }

            fun parseLayerLines(obj: com.google.gson.JsonObject?, target: MutableList<String>) {
                val lc = obj?.getAsJsonArray("lyricContent") ?: return
                for (lineItem in lc) {
                    if (lineItem.isJsonArray) {
                        val lineArr = lineItem.asJsonArray
                        val joined = lineArr.mapNotNull {
                            val token = cleanLayerText(it.asString)
                            token.takeIf { t -> t.isNotEmpty() }
                        }.joinToString(" ").replace(Regex("\\s+"), " ").trim()
                        target.add(cleanLayerText(joined))
                    } else {
                        target.add(cleanLayerText(lineItem.asString))
                    }
                }
            }

            parseLayerLines(translationObj, translations)
            parseLayerLines(phoneticObj, phonetics)
        } catch (_: Exception) {
        }
        return translations to phonetics
    }

    /**
     * 解析标准与扩展 LRC 文本：
     * 1. 支持单行多时间标签（如 `[00:15.00][01:30.00]副歌`）全量展开；
     * 2. 先按时间戳稳定排序，再合并相同时间戳的双语分离块（上半块原文 + 下半块译文），最后计算精确的每句持续时长。
     */
    fun parseLrcText(text: String): LyricData {
        if (text.isBlank()) return LyricData()

        data class RawLrcEntry(val order: Int, val line: LyricLine)
        val rawLines = text.lines()
        val rawParsed = mutableListOf<RawLrcEntry>()
        var seqOrder = 0

        for (rawLine in rawLines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (METADATA_PREFIXES.any { line.startsWith(it, ignoreCase = true) }) continue

            val tagMatches = LRC_TAG_REGEX.findAll(line).toList()
            if (tagMatches.isEmpty()) continue
            // 仅取行首连续的时间标签
            val leadingTags = mutableListOf<MatchResult>()
            var expectedStart = 0
            for (tm in tagMatches) {
                if (tm.range.first == expectedStart) {
                    leadingTags.add(tm)
                    expectedStart = tm.range.last + 1
                } else {
                    break
                }
            }
            if (leadingTags.isEmpty()) continue

            val lineText = unescapeLyricEntities(line.substring(expectedStart).trim())
            if (lineText.isEmpty() || lineText == "//") continue

            val doubleSlashParts = if (lineText.contains(" // ")) {
                lineText.split(" // ", limit = 3).map { it.trim() }.filter { it.isNotBlank() && it != "//" }
            } else null

            for (tag in leadingTags) {
                val min = tag.groupValues[1].toLongOrNull() ?: continue
                val sec = tag.groupValues[2].toLongOrNull() ?: continue
                val fractionRaw = tag.groupValues[3]
                val ms = when (fractionRaw.length) {
                    1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100
                    2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10
                    3 -> fractionRaw.toLongOrNull() ?: 0L
                    else -> 0L
                }
                val startTimeMs = min * 60_000L + sec * 1000L + ms
                val parsedLine = when {
                    doubleSlashParts != null && doubleSlashParts.size == 3 ->
                        LyricLine(startTimeMs, 0L, doubleSlashParts[0], translation = doubleSlashParts[2], phonetic = doubleSlashParts[1])
                    doubleSlashParts != null && doubleSlashParts.size == 2 ->
                        LyricLine(startTimeMs, 0L, doubleSlashParts[0], translation = doubleSlashParts[1])
                    lineText.contains(" / ") && lineText.any { it in '\u4e00'..'\u9fff' } -> {
                        val slashParts = lineText.split(" / ", limit = 2)
                        LyricLine(startTimeMs, 0L, slashParts[0].trim(), translation = slashParts[1].trim())
                    }
                    else -> LyricLine(startTimeMs, 0L, lineText)
                }
                rawParsed.add(RawLrcEntry(seqOrder++, parsedLine))
            }
        }

        // 先按时间戳与原始行序稳定排序，以支持“前半段全原文、后半段全翻译”的分块式双语 LRC
        val sortedRaw = rawParsed.sortedWith(compareBy<RawLrcEntry> { it.line.startTimeMs }.thenBy { it.order }).map { it.line }

        val lines = mutableListOf<LyricLine>()
        for (entry in sortedRaw) {
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
