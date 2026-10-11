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
    val title: String,
    val artist: String,
    val album: String = "",
    val albumId: String = "",
    val coverUrl: String = "",
    val durationMs: Long = 0,
    val isVip: Boolean = false,
    val isLossless: Boolean = true,
    val playUrl: String = "",
    val albumAudioId: Long = 0L,
    val audioId: Long = 0L,
    val quality: String = "320",
    val isHiRes: Boolean = false,
    val isFavorite: Boolean = false,
    val fileId: Long = 0L
) {
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
