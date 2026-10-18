package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
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
    val effectiveListenMinutes: Long
        get() = if (totalListenSeconds > 0L) totalListenSeconds / 60L else totalListenMinutes

    val totalHoursFormatted: String
        get() = String.format(java.util.Locale.getDefault(), "%.1f", effectiveListenMinutes / 60.0)

    val displayListenTimeText: String
        get() {
            val totalMins = effectiveListenMinutes.coerceAtLeast(0L)
            return if (totalMins < 60L) {
                "${totalMins} 分钟"
            } else {
                val hours = totalMins / 60L
                val mins = totalMins % 60L
                "${hours} 小时 ${mins} 分"
            }
        }

    val effectiveProgressFraction: Float
        get() {
            val raw = when {
                progressPercent > 1f -> progressPercent / 100f
                progressPercent > 0f -> progressPercent
                else -> computeLevelProgressAndRemain(
                    level = level,
                    totalMinutes = effectiveListenMinutes,
                    totalSeconds = totalListenSeconds,
                    score = score,
                    nextScore = nextLevelScore
                ).first / 100f
            }
            return raw.coerceIn(0.06f, 0.99f)
        }

    fun withUpdatedSeconds(newTotalSeconds: Long): UserGradeInfo {
        val safeSec = newTotalSeconds.coerceAtLeast(0L)
        val newMins = safeSec / 60L
        val (pct, remainMins) = computeLevelProgressAndRemain(
            level = level,
            totalMinutes = newMins,
            totalSeconds = safeSec,
            score = score,
            nextScore = nextLevelScore
        )
        return copy(
            totalListenSeconds = safeSec,
            totalListenMinutes = newMins,
            progressPercent = pct,
            nextLevelRemainMinutes = remainMins
        )
    }

    companion object {
        // 酷狗概念版听歌等级累计分钟阶梯表 (Lv.0 ~ Lv.20)
        // Lv.11 对应 2800 小时 (168,000 分钟) ~ 4000 小时 (240,000 分钟)
        private val LEVEL_MIN_THRESHOLDS = longArrayOf(
            0L,          // Lv.0: 0h
            60L,         // Lv.1: 1h
            300L,        // Lv.2: 5h
            900L,        // Lv.3: 15h
            1_800L,      // Lv.4: 30h
            3_600L,      // Lv.5: 60h
            7_200L,      // Lv.6: 120h
            15_000L,     // Lv.7: 250h
            30_000L,     // Lv.8: 500h
            60_000L,     // Lv.9: 1000h
            108_000L,    // Lv.10: 1800h
            168_000L,    // Lv.11: 2800h
            240_000L,    // Lv.12: 4000h
            330_000L,    // Lv.13: 5500h
            450_000L,    // Lv.14: 7500h
            600_000L,    // Lv.15: 10000h
            780_000L,    // Lv.16: 13000h
            990_000L,    // Lv.17: 16500h
            1_230_000L,  // Lv.18: 20500h
            1_500_000L,  // Lv.19: 25000h
            1_800_000L   // Lv.20: 30000h
        )

        fun computeLevelProgressAndRemain(
            level: Int,
            totalMinutes: Long,
            totalSeconds: Long = totalMinutes * 60L,
            score: Long = 0L,
            nextScore: Long = 0L,
            currentBaseScore: Long = 0L,
            remainSecondsFromServer: Long = 0L
        ): Pair<Float, Long> {
            val safeMins = (if (totalSeconds > 0L) totalSeconds / 60L else totalMinutes).coerceAtLeast(0L)
            if (remainSecondsFromServer > 0L && totalSeconds > 0L) {
                val remainMins = (remainSecondsFromServer / 60L).coerceAtLeast(1L)
                val lvIdx = level.coerceIn(0, LEVEL_MIN_THRESHOLDS.lastIndex - 1)
                val baseMin = LEVEL_MIN_THRESHOLDS[lvIdx].coerceAtMost(safeMins)
                val targetMin = safeMins + remainMins
                val span = (targetMin - baseMin).coerceAtLeast(1L)
                val pct = (((safeMins - baseMin).toDouble() / span.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
                return pct to remainMins
            }

            if (score > 0L && nextScore > score && nextScore > currentBaseScore) {
                val base = if (score >= currentBaseScore) currentBaseScore else 0L
                val span = (nextScore - base).coerceAtLeast(1L)
                val pct = (((score - base).toDouble() / span.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
                val remain = (nextScore - score).coerceAtLeast(0L)
                return pct to remain
            }

            val lvIdx = level.coerceIn(0, LEVEL_MIN_THRESHOLDS.lastIndex - 1)
            var lowerMin = LEVEL_MIN_THRESHOLDS[lvIdx]
            var upperMin = LEVEL_MIN_THRESHOLDS[(lvIdx + 1).coerceAtMost(LEVEL_MIN_THRESHOLDS.lastIndex)]

            // 若用户实际时长超出当前等级常规上下界，自动匹配所在时长阶梯区间，杜绝进度条回退或归零
            if (safeMins < lowerMin || safeMins >= upperMin) {
                var matchedIdx = 0
                for (i in 0 until LEVEL_MIN_THRESHOLDS.lastIndex) {
                    if (safeMins >= LEVEL_MIN_THRESHOLDS[i]) {
                        matchedIdx = i
                    } else {
                        break
                    }
                }
                lowerMin = LEVEL_MIN_THRESHOLDS[matchedIdx]
                upperMin = if (matchedIdx + 1 <= LEVEL_MIN_THRESHOLDS.lastIndex && safeMins < LEVEL_MIN_THRESHOLDS[matchedIdx + 1]) {
                    LEVEL_MIN_THRESHOLDS[matchedIdx + 1]
                } else {
                    lowerMin + 180_000L
                }
            }

            val spanSec = ((upperMin - lowerMin) * 60L).coerceAtLeast(60L)
            val curSec = (if (totalSeconds > 0L) totalSeconds else safeMins * 60L).coerceAtLeast(lowerMin * 60L)
            val gainedSec = (curSec - lowerMin * 60L).coerceAtLeast(0L)
            val pct = ((gainedSec.toDouble() / spanSec.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
            val remainMins = ((upperMin * 60L - curSec).coerceAtLeast(0L)) / 60L
            return pct to remainMins
        }
    }
}

@Immutable
data class FollowedArtist(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val songCount: Int = 0,
    val fansCount: Long = 0L
)

@Immutable
data class PurchasedSongItem(
    val songId: String,
    val songName: String,
    val singerName: String,
    val buyTime: String = "",
    val song: Song? = null
)

@Immutable
data class PurchasedAlbumItem(
    val albumId: String,
    val albumName: String,
    val singerName: String,
    val coverUrl: String,
    val songCount: Int = 0,
    val buyTime: String = ""
)
