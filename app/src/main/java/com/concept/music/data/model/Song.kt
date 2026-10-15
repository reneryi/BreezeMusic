package com.concept.music.data.model

enum class AudioQuality(val apiValue: String, val label: String) {
    STANDARD("128", "标准 128K"),
    HIGH("320", "高品 320K"),
    LOSSLESS("flac", "无损 SQ"),
    HI_RES("high", "Hi-Res");

    companion object {
        fun fromValue(value: String?): AudioQuality = when (value?.lowercase()) {
            "high", "hires", "super" -> HI_RES
            "flac", "lossless", "sq" -> LOSSLESS
            "320", "high_quality", "hq" -> HIGH
            else -> STANDARD
        }
    }
}

data class Song(
    val id: String,
    val hash: String = "",
    val hash128: String = "",
    val hash320: String = "",
    val hashFlac: String = "",
    val hashHiRes: String = "",
    val accompanyHash: String = "",
    val title: String,
    val artist: String,
    val artistId: String = "",
    val album: String = "",
    val albumId: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val isVip: Boolean = false,
    val isFreePrivilege: Boolean = false,
    val isLossless: Boolean = false,
    val playUrl: String = "",
    val albumAudioId: Long = 0L,
    val audioId: Long = 0L,
    val quality: String = "320",
    val isHiRes: Boolean = false,
    val isFavorite: Boolean = false,
    val fileId: Long = 0L,
    val availableQualities: List<AudioQuality> = emptyList(),
    val actualPlayQuality: AudioQuality? = null,
    val bitrateKbps: Int = 0,
    val fileSizeByte: Long = 0L,
    val climaxStartMs: Long = 0L,
    val climaxEndMs: Long = 0L
) {
    val hasAccompaniment: Boolean
        get() = accompanyHash.isNotBlank()

    /**
     * 有效副歌（高潮）起点毫秒数 ([INNOV-01])
     * 若服务端返回了有效的 climax_info 则优先使用；否则智能截取黄金副歌区间（30% 处起播，持续 28 秒）
     */
    val effectiveClimaxStartMs: Long
        get() {
            if (climaxStartMs > 0L && climaxEndMs > climaxStartMs + 8_000L) {
                return climaxStartMs
            }
            val dur = durationMs.takeIf { it > 45_000L } ?: 180_000L
            return (dur * 30 / 100).coerceIn(25_000L, (dur - 35_000L).coerceAtLeast(25_000L))
        }

    /**
     * 有效副歌（高潮）终点毫秒数 ([INNOV-01])
     */
    val effectiveClimaxEndMs: Long
        get() {
            if (climaxStartMs > 0L && climaxEndMs > climaxStartMs + 8_000L) {
                return climaxEndMs
            }
            val dur = durationMs.takeIf { it > 45_000L } ?: 180_000L
            return (effectiveClimaxStartMs + 28_000L).coerceAtMost((dur - 4_000L).coerceAtLeast(effectiveClimaxStartMs + 15_000L))
        }

    val effectiveQualities: List<AudioQuality>
        get() {
            if (availableQualities.isNotEmpty()) return availableQualities
            val list = mutableListOf(AudioQuality.STANDARD)
            if (hash320.isNotBlank() || quality == "320" || isLossless || isHiRes) {
                list.add(AudioQuality.HIGH)
            }
            if (hashFlac.isNotBlank() || isLossless || isHiRes) {
                list.add(AudioQuality.LOSSLESS)
            }
            if (hashHiRes.isNotBlank() || isHiRes) {
                list.add(AudioQuality.HI_RES)
            }
            actualPlayQuality?.let { if (it !in list) list.add(it) }
            return list.distinct().sortedBy { it.ordinal }
        }

    val highestQuality: AudioQuality
        get() = effectiveQualities.maxByOrNull { it.ordinal }
            ?: when {
                isHiRes -> AudioQuality.HI_RES
                isLossless -> AudioQuality.LOSSLESS
                quality == "320" -> AudioQuality.HIGH
                else -> AudioQuality.STANDARD
            }

    fun hashForQuality(q: AudioQuality): String = when (q) {
        AudioQuality.HI_RES -> hashHiRes.ifBlank { hashFlac.ifBlank { hash320.ifBlank { hash } } }
        AudioQuality.LOSSLESS -> hashFlac.ifBlank { hash320.ifBlank { hash } }
        AudioQuality.HIGH -> hash320.ifBlank { hash }
        AudioQuality.STANDARD -> hash128.ifBlank { hash }
    }

    val durationFormatted: String
        get() {
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
}

data class SearchResult(
    val songs: List<Song> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val pageSize: Int = 50
) {
    val hasMore: Boolean
        get() = songs.isNotEmpty() && page * pageSize < total
}
