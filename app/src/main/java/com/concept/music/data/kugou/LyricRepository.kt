package com.concept.music.data.kugou

import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
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

    suspend fun getLyric(song: Song): LyricData = withContext(Dispatchers.IO) {
        val cacheKey = song.id.ifBlank { song.hash }
        if (cacheKey.isNotBlank()) {
            synchronized(cache) {
                cache[cacheKey]?.let { return@withContext it }
            }
        }

        val candidates = searchLyricCandidates(song)
        if (candidates.isEmpty()) {
            val empty = LyricData()
            if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = empty }
            return@withContext empty
        }

        // 1. 尝试首个最佳候选
        val firstCandidate = candidates.first()
        var bestData = downloadLyric(firstCandidate.id, firstCandidate.accesskey, fmt = "krc")

        // 2. 若首个候选暂无翻译，且歌曲属于非中文歌曲，优先扩大候选搜索范围以获取含官方中文翻译的歌词
        val isChineseSong = song.title.any { it in '\u4e00'..'\u9fff' }
        val maxCandidatesToCheck = if (!isChineseSong) 6 else 4
        if ((bestData == null || (!bestData.hasTranslation && !isChineseSong)) && candidates.size > 1) {
            for (i in 1 until candidates.size.coerceAtMost(maxCandidatesToCheck)) {
                val alt = candidates[i]
                val altData = downloadLyric(alt.id, alt.accesskey, fmt = "krc")
                if (altData != null && altData.hasTranslation) {
                    bestData = altData
                    break
                } else if (bestData == null && altData != null && altData.lines.isNotEmpty()) {
                    bestData = altData
                }
            }
        }

        if (bestData != null && bestData.lines.isNotEmpty()) {
            if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = bestData }
            return@withContext bestData
        }

        // 3. KRC 不可用时降级 LRC
        val lrcData = downloadLyric(firstCandidate.id, firstCandidate.accesskey, fmt = "lrc")
        val finalData = lrcData ?: LyricData()
        if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = finalData }
        finalData
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
        } catch (_: Exception) {
            null
        }
    }

    private data class LyricCandidate(val id: String, val accesskey: String)
}
