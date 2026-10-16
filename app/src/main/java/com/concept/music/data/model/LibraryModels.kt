package com.concept.music.data.model

data class UserGradeInfo(
    val level: Int = 1,
    val score: Long = 0L,
    val totalListenMinutes: Long = 0L,
    val todayListenMinutes: Long = 0L,
    val nextLevelScore: Long = 100L,
    val progressPercent: Float = 0f,
    val totalListenSeconds: Long = 0L,
    val nextLevelRemainMinutes: Long = 0L
) {
    val totalHoursFormatted: String
        get() = String.format(java.util.Locale.getDefault(), "%.1f", totalListenMinutes / 60.0)

    val displayListenTimeText: String
        get() = if (totalListenMinutes < 60) {
            "${totalListenMinutes} 分钟"
        } else {
            val hours = totalListenMinutes / 60
            val mins = totalListenMinutes % 60
            if (mins > 0) "${hours} 小时 ${mins} 分" else "${hours} 小时"
        }
}

data class FollowedArtist(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val songCount: Int = 0,
    val fansCount: Long = 0L
)

data class PurchasedSongItem(
    val songId: String,
    val songName: String,
    val singerName: String,
    val buyTime: String = "",
    val song: Song? = null
)

data class PurchasedAlbumItem(
    val albumId: String,
    val albumName: String,
    val singerName: String,
    val coverUrl: String,
    val songCount: Int = 0,
    val buyTime: String = ""
)
