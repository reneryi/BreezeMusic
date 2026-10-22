package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class UserGradeInfo(
    val level: Int = 1,
    val score: Long = 0L,
    val totalListenMinutes: Long = 0L,
    val todayListenMinutes: Long = 0L,
    val nextLevelScore: Long = 100L,
    val currentBaseScore: Long = 0L,
    val progressPercent: Float = 0f,
    val totalListenSeconds: Long = 0L,
    val nextLevelRemainMinutes: Long = 0L
) {
    // 兼容别名与辅助构造函数
    constructor(
        level: Int,
        listenDurationMinutes: Long,
        listenDurationSeconds: Long,
        nextLevelProgress: Float,
        nextLevelRemainMinutes: Long,
        currentBaseScore: Long = 0L,
        nextLevelMinMinutes: Long = 222_000L
    ) : this(
        level = level,
        score = listenDurationMinutes,
        totalListenMinutes = listenDurationMinutes,
        todayListenMinutes = 0L,
        nextLevelScore = nextLevelMinMinutes,
        currentBaseScore = currentBaseScore,
        progressPercent = if (nextLevelProgress <= 1f) nextLevelProgress * 100f else nextLevelProgress,
        totalListenSeconds = listenDurationSeconds,
        nextLevelRemainMinutes = nextLevelRemainMinutes
    )

    val effectiveListenMinutes: Long
        get() = if (totalListenSeconds > 0L) totalListenSeconds / 60L else totalListenMinutes

    val listenDurationMinutes: Long
        get() = effectiveListenMinutes

    val listenDurationSeconds: Long
        get() = if (totalListenSeconds > 0L) totalListenSeconds else totalListenMinutes * 60L

    val formattedDuration: String
        get() = displayListenTimeText

    val nextLevelProgress: Float
        get() = effectiveProgressFraction

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
                    nextScore = nextLevelScore,
                    currentBaseScore = currentBaseScore
                ).first / 100f
            }
            return raw.coerceIn(0.06f, 0.99f)
        }

    fun withUpdatedSeconds(newTotalSeconds: Long): UserGradeInfo {
        val safeSec = newTotalSeconds.coerceAtLeast(0L)
        val newMins = safeSec / 60L
        val deltaMins = (newMins - totalListenMinutes).coerceAtLeast(0L)
        val updatedScore = if (score > 0L) score + deltaMins else 0L
        val (pct, remainMins) = computeLevelProgressAndRemain(
            level = level,
            totalMinutes = newMins,
            totalSeconds = safeSec,
            score = updatedScore,
            nextScore = nextLevelScore,
            currentBaseScore = currentBaseScore
        )
        return copy(
            score = updatedScore,
            totalListenSeconds = safeSec,
            totalListenMinutes = newMins,
            progressPercent = pct,
            nextLevelRemainMinutes = remainMins
        )
    }

    companion object {
        // 酷狗听歌等级离线兜底分钟阶梯表 (Lv.0 ~ Lv.20)
        // 注：在线状态下优先以 /v2/get_grade_info 与 /v4/get_grade_info 返回的 p_next_grade_point 与 p_grade_point 为准
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
            150_000L,    // Lv.11: 2500h
            222_000L,    // Lv.12: 3700h (222,000 分钟)
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
                val baseMin = if (currentBaseScore in 1L..safeMins) currentBaseScore else LEVEL_MIN_THRESHOLDS[lvIdx].coerceAtMost(safeMins)
                val targetMin = safeMins + remainMins
                val span = (targetMin - baseMin).coerceAtLeast(1L)
                val pct = (((safeMins - baseMin).toDouble() / span.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
                return pct to remainMins
            }

            // 1. 优先采用服务端返回的等级门槛 (p_next_grade_point / p_current_point / p_grade_point)
            // 若 safeMins (如 d_sec 折算的 220904 分钟) 比滞后的 p_current_point (如 220630) 略高且处于同一量级 (< nextScore)，
            // 则以最新的 safeMins 校准当前进度与剩余升级分钟数，彻底杜绝因硬编码 240,000 分钟导致误算为“差 322h”的问题
            if (nextScore > 100L && (score > 0L || safeMins > 0L)) {
                val effectiveCurrent = when {
                    score > 0L && safeMins in (score + 1L) until nextScore && (safeMins - score) <= 10_000L -> safeMins
                    score > 0L -> score
                    else -> safeMins
                }
                if (nextScore > effectiveCurrent) {
                    val lvIdx = level.coerceIn(0, LEVEL_MIN_THRESHOLDS.lastIndex - 1)
                    val base = when {
                        currentBaseScore in 1L..effectiveCurrent -> currentBaseScore
                        LEVEL_MIN_THRESHOLDS[lvIdx] < effectiveCurrent -> LEVEL_MIN_THRESHOLDS[lvIdx]
                        else -> 0L
                    }
                    val span = (nextScore - base).coerceAtLeast(1L)
                    val pct = (((effectiveCurrent - base).toDouble() / span.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
                    val remainMins = (nextScore - effectiveCurrent).coerceAtLeast(0L)
                    return pct to remainMins
                }
            }

            // 2. 离线兜底：按听歌等级分钟阶梯表估算
            val lvIdx = level.coerceIn(0, LEVEL_MIN_THRESHOLDS.lastIndex - 1)
            var lowerMin = LEVEL_MIN_THRESHOLDS[lvIdx]
            var upperMin = LEVEL_MIN_THRESHOLDS[(lvIdx + 1).coerceAtMost(LEVEL_MIN_THRESHOLDS.lastIndex)]

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
                    lowerMin + 72_000L
                }
            }

            val spanSec = ((upperMin - lowerMin) * 60L).coerceAtLeast(60L)
            val curSec = (if (totalSeconds > 0L) totalSeconds else safeMins * 60L).coerceAtLeast(lowerMin * 60L)
            val gainedSec = (curSec - lowerMin * 60L).coerceAtLeast(0L)
            val durationPct = ((gainedSec.toDouble() / spanSec.toDouble()) * 100.0).toFloat().coerceIn(5f, 99f)
            val durationRemainMins = ((upperMin * 60L - curSec).coerceAtLeast(0L)) / 60L
            return durationPct to durationRemainMins
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
