package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class LyricWord(
    val startTimeMs: Long,
    val durationMs: Long,
    val text: String
)

@Immutable
data class LyricLine(
    val startTimeMs: Long,
    val durationMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val phonetic: String? = null
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()

    /**
     * 当前行实际人声唱诵结束时间戳（毫秒）：
     * 优先取 KRC 最后一个字词的精确结束时间 (startTimeMs + durationMs)，否则取行级持续时长。
     */
    val effectiveEndTimeMs: Long
        get() = words.lastOrNull()?.let { (it.startTimeMs + it.durationMs).coerceAtLeast(startTimeMs) }
            ?: (startTimeMs + durationMs.coerceAtLeast(0L))
}

@Immutable
data class LyricData(
    val lines: List<LyricLine> = emptyList(),
    val rawText: String = "",
    val isKrc: Boolean = false
) {
    val hasTranslation: Boolean get() = lines.any { !it.translation.isNullOrBlank() }
    val hasPhonetic: Boolean get() = lines.any { !it.phonetic.isNullOrBlank() }

    fun getLineIndexAt(positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.size - 1
        var candidate = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            val line = lines[mid]
            if (line.startTimeMs <= positionMs) {
                candidate = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return candidate
    }

    /**
     * 计算当前播放位置是否处于「前奏等待」或「器乐间奏（≥ minGapMs 空白间隔）」区间：
     * - 若处于前奏或间奏区间且距离下一句开唱还有至少 150ms，返回归一化进度 [0f, 1f]；
     * - 否则返回 -1f。
     * 深度利用酷狗 KRC 歌词的逐字结束时间戳与行级持续时长 (durationMs) 精准识别人声空白期。
     */
    fun getInterludeProgressAt(
        positionMs: Long,
        currentIndex: Int = getLineIndexAt(positionMs),
        minGapMs: Long = 4000L
    ): Float {
        if (lines.isEmpty() || positionMs < 0L) return -1f
        val firstStart = lines.first().startTimeMs
        // 1. 前奏区间 (Prelude)
        if (currentIndex < 0 || positionMs < firstStart) {
            if (firstStart >= 3000L && positionMs < firstStart - 150L) {
                return (positionMs.toFloat() / firstStart.toFloat()).coerceIn(0f, 1f)
            }
            return -1f
        }
        // 2. 行间器乐间奏区间 (Instrumental Interlude)
        val currentLine = lines.getOrNull(currentIndex) ?: return -1f
        val nextLine = lines.getOrNull(currentIndex + 1) ?: return -1f
        val vocalEndMs = when {
            currentLine.words.isNotEmpty() -> currentLine.effectiveEndTimeMs
            isKrc && currentLine.durationMs > 0L -> currentLine.startTimeMs + currentLine.durationMs
            else -> -1L
        }
        if (vocalEndMs <= currentLine.startTimeMs) return -1f
        val gapMs = nextLine.startTimeMs - vocalEndMs
        if (gapMs >= minGapMs && positionMs in vocalEndMs..(nextLine.startTimeMs - 150L)) {
            return ((positionMs - vocalEndMs).toFloat() / gapMs.toFloat()).coerceIn(0f, 1f)
        }
        return -1f
    }

    /**
     * 返回当前前奏或间奏距离下一句开唱的剩余秒数（向上取整），若不在前奏/间奏区间则返回 0。
     */
    fun getInterludeRemainingSecondsAt(
        positionMs: Long,
        currentIndex: Int = getLineIndexAt(positionMs),
        minGapMs: Long = 4000L
    ): Int {
        val progress = getInterludeProgressAt(positionMs, currentIndex, minGapMs)
        if (progress < 0f) return 0
        val nextStart = if (currentIndex < 0 || positionMs < lines.first().startTimeMs) {
            lines.first().startTimeMs
        } else {
            lines.getOrNull(currentIndex + 1)?.startTimeMs ?: return 0
        }
        val remainMs = (nextStart - positionMs).coerceAtLeast(0L)
        return ((remainMs + 999L) / 1000L).toInt()
    }

    companion object {
        val Empty = LyricData()
    }
}
