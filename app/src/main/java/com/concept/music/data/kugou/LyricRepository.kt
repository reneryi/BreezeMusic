package com.concept.music.data.kugou

import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

class LyricRepository(
    private val transport: KugouTransport,
    private val device: () -> DeviceIdentity = { DeviceIdentity("00000000-0000-0000-0000-000000000000", "0", "Android") }
) {

    private val cache = object : LinkedHashMap<String, LyricData>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, LyricData>?): Boolean {
            return size > 50
        }
    }

    internal fun putCacheForTest(key: String, data: LyricData) {
        synchronized(cache) {
            cache[key] = data
        }
    }

    fun cachedEntryCount(): Int = synchronized(cache) { cache.size }

    fun clearCache() {
        synchronized(cache) {
            cache.clear()
        }
    }

    private fun isPredominantlyChinese(data: LyricData?): Boolean {
        if (data == null || data.lines.isEmpty()) return false
        var totalLetters = 0
        var cjkCount = 0
        var kanaHangulCount = 0
        for (line in data.lines) {
            for (ch in line.text) {
                if (ch.isWhitespace()) continue
                if (ch in '\u3040'..'\u30ff' || ch in '\uac00'..'\ud7af') {
                    kanaHangulCount++
                    totalLetters++
                } else if (ch in '\u4e00'..'\u9fff') {
                    cjkCount++
                    totalLetters++
                } else if (ch.isLetter()) {
                    totalLetters++
                }
            }
        }
        if (totalLetters == 0 || kanaHangulCount > 0) return false
        return cjkCount.toFloat() / totalLetters.toFloat() >= 0.60f
    }

    suspend fun getLyric(song: Song): LyricData {
        val cacheKey = song.id.ifBlank { song.hash }
        if (cacheKey.isNotBlank()) {
            synchronized(cache) {
                cache[cacheKey]?.takeIf { it.lines.isNotEmpty() }?.let { return it }
            }
        }

        return withContext(Dispatchers.IO) {
            val candidates = searchLyricCandidates(song)
            if (candidates.isEmpty()) {
                return@withContext LyricData()
            }

            fun scoreLyricData(data: LyricData?): Int {
                if (data == null || data.lines.isEmpty()) return 0
                var s = 10
                if (data.isKrc) s += 5
                if (data.hasTranslation) s += 20
                if (data.hasPhonetic) s += 20
                return s
            }

            // 1. 尝试首个最佳候选
            val firstCandidate = candidates.first()
            var bestData = downloadLyric(firstCandidate.id, firstCandidate.accesskey, fmt = "krc")
            var bestScore = scoreLyricData(bestData)

            // 2. 若首个候选为外语/粤语歌且缺少翻译或音译注音，扩大候选搜索范围择优挑选含官方翻译与注音的 KRC；
            //    对纯中文歌曲（中文占比 >= 60% 且无假名/韩文）直接采纳首个有效 KRC，避免无意义串行下载 6 次
            val maxCandidatesToCheck = 6
            val needExtraLayers = bestData == null ||
                (!isPredominantlyChinese(bestData) && (!bestData.hasTranslation || !bestData.hasPhonetic))
            if (needExtraLayers && candidates.size > 1) {
                for (i in 1 until candidates.size.coerceAtMost(maxCandidatesToCheck)) {
                    val alt = candidates[i]
                    val altData = downloadLyric(alt.id, alt.accesskey, fmt = "krc") ?: continue
                    val altScore = scoreLyricData(altData)
                    if (altScore > bestScore) {
                        bestData = altData
                        bestScore = altScore
                        if (altData.hasTranslation && altData.hasPhonetic) break
                    } else if (bestData != null && altData.lines.size == bestData.lines.size) {
                        val needTrans = !bestData.hasTranslation && altData.hasTranslation
                        val needPhonetic = !bestData.hasPhonetic && altData.hasPhonetic
                        if (needTrans || needPhonetic) {
                            val mergedLines = bestData.lines.mapIndexed { idx, line ->
                                val other = altData.lines.getOrNull(idx)
                                line.copy(
                                    translation = line.translation ?: other?.translation,
                                    phonetic = line.phonetic ?: other?.phonetic
                                )
                            }
                            bestData = bestData.copy(lines = mergedLines)
                            bestScore = scoreLyricData(bestData)
                            if (bestData.hasTranslation && bestData.hasPhonetic) break
                        }
                    }
                }
            }

            if (bestData != null && bestData.lines.isNotEmpty()) {
                if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = bestData }
                return@withContext bestData
            }

            // 3. KRC 不可用时降级 LRC（仅非空歌词才写入 LRU 缓存，杜绝网络抖动或取消协程导致空歌词永久投毒）
            val lrcData = downloadLyric(firstCandidate.id, firstCandidate.accesskey, fmt = "lrc")
            val finalData = lrcData ?: LyricData()
            if (cacheKey.isNotBlank() && finalData.lines.isNotEmpty()) {
                synchronized(cache) { cache[cacheKey] = finalData }
            }
            finalData
        }
    }

    private suspend fun searchLyricCandidates(song: Song): List<LyricCandidate> {
        val d = device()
        val clientTime = System.currentTimeMillis() / 1000
        val query = linkedMapOf(
            "album_audio_id" to song.albumAudioId.toString(),
            "appid" to "3116",
            "clientver" to "11440",
            "duration" to song.durationMs.toString(),
            "hash" to song.hash.lowercase(),
            "keyword" to song.title,
            "lrctxt" to "1",
            "man" to "yes",
            "clienttime" to clientTime.toString(),
            "mid" to d.mid,
            "dfid" to d.dfid
        )
        val salt = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
        val sortedParams = query.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        val signature = KugouSigner.md5("$salt$sortedParams$salt")
        query["signature"] = signature

        return try {
            val response = transport.callAdvanced(
                path = "/v1/search",
                host = "https://lyrics.kugou.com",
                query = query,
                headers = mapOf(
                    "dfid" to d.dfid,
                    "mid" to d.mid,
                    "clienttime" to clientTime.toString()
                ),
                sign = false,
                includeDefaultParams = false
            )
            val candidates = response.data.objects("candidates").ifEmpty { response.body.objects("candidates") }
            candidates.mapNotNull {
                val id = it.text("id") ?: return@mapNotNull null
                val accesskey = it.text("accesskey") ?: return@mapNotNull null
                LyricCandidate(id, accesskey)
            }
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun downloadLyric(id: String, accesskey: String, fmt: String): LyricData? {
        val query = mapOf(
            "ver" to "1",
            "client" to "android",
            "id" to id,
            "accesskey" to accesskey,
            "fmt" to fmt,
            "charset" to "utf8"
        )
        return try {
            val response = transport.callAdvanced(
                path = "/download",
                host = "https://lyrics.kugou.com",
                query = query,
                sign = false,
                includeDefaultParams = false
            )
            val content = response.body.text("content") ?: response.data.text("content") ?: return null
            if (fmt == "krc") {
                val parsed = KrcDecoder.parseAuto(content, isBase64Krc = true)
                if (parsed.lines.isNotEmpty()) return parsed
            }
            val raw = runCatching { String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8) }.getOrNull() ?: content
            KrcDecoder.parseLrcText(raw)
        } catch (c: CancellationException) {
            throw c
        } catch (_: Exception) {
            null
        }
    }

    private data class LyricCandidate(val id: String, val accesskey: String)
}
