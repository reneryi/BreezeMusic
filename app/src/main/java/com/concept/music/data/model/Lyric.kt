package com.concept.music.data.model

data class LyricWord(
    val startTimeMs: Long,
    val durationMs: Long,
    val text: String
)

data class LyricLine(
    val startTimeMs: Long,
    val durationMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val phonetic: String? = null
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()
}

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
}
