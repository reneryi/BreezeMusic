package com.concept.music.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.concept.music.data.model.Song
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * 滑动感知的静默微批次渐进加载器（Scroll-Aware Silent Micro-Batch Progressive Loader）：
 * 1. 首屏瞬时呈现：队列到达瞬间仅挂载前 [initialBatchSize] 首（默认 15 首，刚好铺满首屏 + 预取缓冲），消除百首/五百首同时挂载带来的首帧卡顿。
 * 2. 静默微批次推进：后台按每批 [microBatchSize] 首（默认 10 首）的小粒度持续向 UI 列表静默追加，直至全量队列 100% 加载完毕。
 * 3. 滑动零掉帧保护：当用户手指正在滑动或惯性滚屏（`listState.isScrollInProgress == true`）且距离已渲染末尾尚有充足余量（> [bottomPreloadThreshold] 首）时，
 *    自动挂起列表追加，避免滚屏途中触发 LazyLayout 重测算导致掉帧；一旦滑动停歇或接近底部则立即无缝补齐。
 * 4. 增量分页无缝衔接：当后台分页拉取使 [sourceSongs] 从 100 首增长至 200/300/500 首时，自动保持已渲染进度并继续以 10 首微批次静默推进，绝不闪烁或重置。
 */
@Composable
fun rememberProgressiveSongList(
    sourceSongs: List<Song>,
    listState: LazyListState,
    resetKey: Any? = Unit,
    initialBatchSize: Int = 60,
    microBatchSize: Int = 100,
    frameIntervalMs: Long = 96L,
    bottomPreloadThreshold: Int = 24
): State<List<Song>> {
    val latestSource by rememberUpdatedState(sourceSongs)
    var renderedCount by remember(resetKey) {
        mutableIntStateOf(sourceSongs.size.coerceAtMost(initialBatchSize))
    }
    var lastFirstSongIdentity by remember(resetKey) {
        mutableIntStateOf(sourceSongs.firstOrNull()?.let { (it.id + "|" + it.hash).hashCode() } ?: 0)
    }

    val currentFirstIdentity = sourceSongs.firstOrNull()?.let { (it.id + "|" + it.hash).hashCode() } ?: 0
    if (sourceSongs.isEmpty()) {
        if (renderedCount != 0) renderedCount = 0
        lastFirstSongIdentity = 0
    } else if (currentFirstIdentity != lastFirstSongIdentity) {
        lastFirstSongIdentity = currentFirstIdentity
        renderedCount = sourceSongs.size.coerceAtMost(initialBatchSize)
    } else if (renderedCount == 0) {
        renderedCount = sourceSongs.size.coerceAtMost(initialBatchSize)
    } else if (renderedCount > sourceSongs.size) {
        renderedCount = sourceSongs.size
    }

    LaunchedEffect(resetKey, sourceSongs.size, currentFirstIdentity) {
        val total = latestSource.size
        if (total == 0) {
            renderedCount = 0
            return@LaunchedEffect
        }
        if (renderedCount < initialBatchSize && total > renderedCount) {
            renderedCount = total.coerceAtMost(initialBatchSize)
        }
        while (renderedCount < latestSource.size) {
            // 若用户正在滑动且距离当前已渲染列表底部还有充足缓冲，则等待滑动停歇或接近底部再发射下一微批次
            if (listState.isScrollInProgress) {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val currentCap = renderedCount
                if (lastVisible < currentCap - bottomPreloadThreshold) {
                    snapshotFlow {
                        !listState.isScrollInProgress ||
                            (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= currentCap - bottomPreloadThreshold
                    }.first { it }
                }
            }
            delay(frameIntervalMs)
            val newTotal = latestSource.size
            if (renderedCount < newTotal) {
                renderedCount = (renderedCount + microBatchSize).coerceAtMost(newTotal)
            }
        }
    }

    return remember(sourceSongs) {
        derivedStateOf {
            val total = sourceSongs.size
            val cap = renderedCount.coerceIn(0, total)
            if (cap >= total) {
                sourceSongs
            } else {
                sourceSongs.subList(0, cap)
            }
        }
    }
}
