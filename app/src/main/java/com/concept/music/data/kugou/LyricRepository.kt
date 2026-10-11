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

        val candidate = searchLyricCandidate(song)
        if (candidate == null) {
            val empty = LyricData()
            if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = empty }
            return@withContext empty
        }

        val krcData = downloadLyric(candidate.id, candidate.accesskey, fmt = "krc")
        if (krcData != null && krcData.lines.isNotEmpty()) {
            if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = krcData }
            return@withContext krcData
        }

        val lrcData = downloadLyric(candidate.id, candidate.accesskey, fmt = "lrc")
        val finalData = lrcData ?: LyricData()
        if (cacheKey.isNotBlank()) synchronized(cache) { cache[cacheKey] = finalData }
        finalData
    }

    private suspend fun searchLyricCandidate(song: Song): LyricCandidate? {
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
            val best = candidates.firstOrNull() ?: return null
            val id = best.text("id") ?: return null
            val accesskey = best.text("accesskey") ?: return null
            LyricCandidate(id, accesskey)
        } catch (_: Exception) {
            null
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
