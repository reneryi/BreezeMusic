package com.concept.music.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.viewmodel.PlayMode
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    song: Song?,
    isPlaying: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    playMode: PlayMode,
    currentQualityLabel: String,
    lyricData: LyricData,
    currentLyricIndex: Int,
    queue: List<Song>,
    currentIndex: Int,
    comments: List<SongComment> = emptyList(),
    commentsLoading: Boolean = false,
    commentsHasMore: Boolean = true,
    commentType: String = "hot",
    onSelectCommentType: (String) -> Unit = {},
    songDetail: SongDetailInfo? = null,
    onDismiss: () -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekToLyricLine: (Int) -> Unit,
    onCyclePlayMode: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit,
    onPlayQueueSong: (Song) -> Unit,
    onRemoveFromQueue: (Song) -> Unit,
    onClearQueue: () -> Unit,
    onRequestComments: () -> Unit = {},
    onLoadMoreComments: () -> Unit = {},
    onRefreshComments: () -> Unit = {}
) {
    if (song == null) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showLyricView by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showAudioEffectDialog by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var showDetailSheet by remember { mutableStateOf(false) }
    var isFavorite by remember(song.id) { mutableStateOf(song.isFavorite) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color(0xFF0F1115)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 1. 毛玻璃模糊氛围背景
            if (song.coverUrl.isNotEmpty()) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(60.dp)
                )
            }
            // 深度暗色遮罩
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC0F1115),
                                Color(0xE614181E),
                                Color(0xF20F1115)
                            )
                        )
                    )
            )

            // 2. 主体内容 Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 顶部控制与音质胶囊
                TopHeaderBar(
                    title = song.title,
                    artist = song.artist,
                    qualityLabel = currentQualityLabel,
                    isHiRes = song.isHiRes,
                    onDismiss = onDismiss,
                    onQualityClick = { showQualityDialog = true }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 主舞台区（黑胶唱片 vs 沉浸歌词）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showLyricView = !showLyricView },
                    contentAlignment = Alignment.Center
                ) {
                    if (!showLyricView) {
                        VinylRecordView(
                            coverUrl = song.coverUrl,
                            isPlaying = isPlaying
                        )
                    } else {
                        ImmersiveLyricView(
                            lyricData = lyricData,
                            currentLyricIndex = currentLyricIndex,
                            currentPositionMs = currentPositionMs,
                            onLineClick = onSeekToLyricLine
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 快捷功能按键栏：喜欢、音效、评论、详情
                ActionButtonsRow(
                    isFavorite = isFavorite,
                    onToggleFavorite = { isFavorite = !isFavorite },
                    onEffectClick = { showAudioEffectDialog = true },
                    onCommentsClick = {
                        onRequestComments()
                        showCommentsSheet = true
                    },
                    onDetailClick = { showDetailSheet = true }
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 底部滑杆控制台
                PlayerControlsSection(
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isPlaying = isPlaying,
                    playMode = playMode,
                    onSeekTo = onSeekTo,
                    onTogglePlay = onTogglePlay,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onCyclePlayMode = onCyclePlayMode,
                    onQueueClick = { showQueueSheet = true }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // 弹窗与抽屉
    if (showQueueSheet) {
        QueueBottomSheet(
            queue = queue,
            currentIndex = currentIndex,
            onDismiss = { showQueueSheet = false },
            onSongClick = {
                onPlayQueueSong(it)
                showQueueSheet = false
            },
            onRemoveSong = onRemoveFromQueue,
            onClear = onClearQueue
        )
    }

    if (showQualityDialog) {
        QualitySelectDialog(
            currentQualityLabel = currentQualityLabel,
            onDismiss = { showQualityDialog = false },
            onSelectQuality = {
                onSelectQuality(it)
                showQualityDialog = false
            }
        )
    }

    if (showAudioEffectDialog) {
        AudioEffectDialog(onDismiss = { showAudioEffectDialog = false })
    }

    if (showCommentsSheet) {
        CommentsSheet(
            comments = comments,
            loading = commentsLoading,
            currentType = commentType,
            onTabSelected = onSelectCommentType,
            hasMore = commentsHasMore,
            onLoadMore = onLoadMoreComments,
            onRefresh = onRefreshComments,
            onDismiss = { showCommentsSheet = false }
        )
    }

    if (showDetailSheet && songDetail != null) {
        SongDetailSheet(
            detail = songDetail,
            onDismiss = { showDetailSheet = false }
        )
    }
}

@Composable
private fun ActionButtonsRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onEffectClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onDetailClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 喜欢
        IconButton(onClick = onToggleFavorite) {
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "喜欢",
                tint = if (isFavorite) Color(0xFFFF5252) else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )
        }

        // 音效
        IconButton(onClick = onEffectClick) {
            Icon(
                imageVector = Icons.Rounded.GraphicEq,
                contentDescription = "音效设置",
                tint = ConceptPrimary,
                modifier = Modifier.size(24.dp)
            )
        }

        // 评论
        IconButton(onClick = onCommentsClick) {
            Icon(
                imageVector = Icons.Rounded.Comment,
                contentDescription = "歌曲评论",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )
        }

        // 音乐详情
        IconButton(onClick = onDetailClick) {
            Icon(
                imageVector = Icons.Rounded.Info,
                contentDescription = "技术参数",
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun TopHeaderBar(
    title: String,
    artist: String,
    qualityLabel: String,
    isHiRes: Boolean,
    onDismiss: () -> Unit,
    onQualityClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = "收起",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(32.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.65f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            color = if (isHiRes) VipGold.copy(alpha = 0.25f) else ConceptPrimary.copy(alpha = 0.2f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onQualityClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (isHiRes) {
                    Text(
                        text = "Hi-Res",
                        color = VipGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = qualityLabel,
                    color = if (isHiRes) VipGold else ConceptPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun VinylRecordView(
    coverUrl: String,
    isPlaying: Boolean
) {
    val rotationAnim = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isActive) {
                val target = rotationAnim.value + 360f
                rotationAnim.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = 20000, easing = LinearEasing)
                )
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(285.dp)
                .shadow(28.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2E2E33),
                            Color(0xFF1C1C20),
                            Color(0xFF0C0C0E)
                        )
                    )
                )
                .border(6.dp, Color(0xFF1B1B1E), CircleShape)
                .rotate(rotationAnim.value % 360f),
            contentAlignment = Alignment.Center
        ) {
            // 黑胶精雕同心微环
            Box(
                modifier = Modifier
                    .size(245.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape)
            )
            Box(
                modifier = Modifier
                    .size(205.dp)
                    .border(1.dp, Color.White.copy(alpha = 0.05f), CircleShape)
            )

            // 唱片中央专辑图
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .clip(CircleShape)
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(60.dp)
                    )
                }

                // 轴心孔与金属圈
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF14181B))
                        .border(3.dp, Color(0xFF444444), CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Surface(
            color = Color.White.copy(alpha = 0.08f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = "轻触切换至沉浸歌词",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp
                ),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImmersiveLyricView(
    lyricData: LyricData,
    currentLyricIndex: Int,
    currentPositionMs: Long,
    onLineClick: (Int) -> Unit
) {
    if (lyricData.lines.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "纯音乐，暂无歌词或歌词加载中...",
                style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }
        return
    }

    val listState = rememberLazyListState()

    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex in lyricData.lines.indices) {
            listState.animateScrollToItem(
                index = currentLyricIndex,
                scrollOffset = -200
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 140.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        itemsIndexed(lyricData.lines) { index, line ->
            val isCurrent = index == currentLyricIndex

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onLineClick(index) }
                    .padding(vertical = 8.dp, horizontal = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrent && line.hasWordTiming) {
                    // KRC 逐字动态卡拉OK染色
                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        line.words.forEach { word ->
                            val isSung = currentPositionMs >= word.startTimeMs + word.durationMs
                            val isSinging = currentPositionMs in word.startTimeMs until (word.startTimeMs + word.durationMs)
                            val wordColor = when {
                                isSung || isSinging -> ConceptPrimary
                                else -> Color.White.copy(alpha = 0.45f)
                            }
                            val wordWeight = if (isSung || isSinging) FontWeight.Black else FontWeight.Medium

                            Text(
                                text = word.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = wordWeight,
                                    color = wordColor,
                                    fontSize = 22.sp
                                )
                            )
                        }
                    }
                } else {
                    // 常规 LRC 或非当前行整行渲染
                    val textColor = when {
                        isCurrent -> ConceptPrimary
                        index < currentLyricIndex -> Color.White.copy(alpha = 0.65f)
                        else -> Color.White.copy(alpha = 0.35f)
                    }
                    val textWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    val textSize = if (isCurrent) 22.sp else 16.sp

                    Text(
                        text = line.text,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = textWeight,
                            color = textColor,
                            fontSize = textSize,
                            textAlign = TextAlign.Center
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerControlsSection(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    playMode: PlayMode,
    onSeekTo: (Long) -> Unit,
    onTogglePlay: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCyclePlayMode: () -> Unit,
    onQueueClick: () -> Unit
) {
    val context = LocalContext.current
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val effectiveFraction = if (isDraggingSlider) dragFraction else currentFraction

    Column(modifier = Modifier.fillMaxWidth()) {
        // 进度滑块
        Slider(
            value = effectiveFraction,
            onValueChange = {
                isDraggingSlider = true
                dragFraction = it
            },
            onValueChangeFinished = {
                onSeekTo((dragFraction * durationMs).toLong())
                isDraggingSlider = false
            },
            colors = SliderDefaults.colors(
                thumbColor = ConceptPrimary,
                activeTrackColor = ConceptPrimary,
                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // 时间展示
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayCurrent = if (isDraggingSlider) (dragFraction * durationMs).toLong() else currentPositionMs
            Text(
                text = formatDuration(displayCurrent),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
            )
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.bodySmall.copy(color = Color.White.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 控制按键栏
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 循环模式
            IconButton(onClick = {
                onCyclePlayMode()
                val nextDesc = when (playMode) {
                    PlayMode.REPEAT_ALL -> "已切换为单曲循环"
                    PlayMode.REPEAT_ONE -> "已切换为随机播放"
                    PlayMode.SHUFFLE -> "已切换为列表循环"
                }
                Toast.makeText(context, nextDesc, Toast.LENGTH_SHORT).show()
            }) {
                val icon = when (playMode) {
                    PlayMode.REPEAT_ALL -> Icons.Rounded.Repeat
                    PlayMode.REPEAT_ONE -> Icons.Rounded.RepeatOne
                    PlayMode.SHUFFLE -> Icons.Rounded.Shuffle
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "播放模式",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(24.dp)
                )
            }

            // 上一首
            IconButton(onClick = onPrevious) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    contentDescription = "上一首",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 播放/暂停大按键
            Surface(
                color = ConceptPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onTogglePlay),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // 下一首
            IconButton(onClick = onNext) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "下一首",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 播放队列
            IconButton(onClick = onQueueClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = "播放列表",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueBottomSheet(
    queue: List<Song>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onClear: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2226),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "当前播放队列 (${queue.size})",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                )
                TextButton(onClick = onClear) {
                    Text("清空队列", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                itemsIndexed(queue) { index, item ->
                    val isPlayingThis = index == currentIndex
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { onSongClick(item) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = (index + 1).toString().padStart(2, '0'),
                            color = if (isPlayingThis) ConceptPrimary else Color.White.copy(alpha = 0.4f),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.width(28.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                color = if (isPlayingThis) ConceptPrimary else Color.White,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.artist,
                                color = Color.White.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { onRemoveSong(item) }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "移除",
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun QualitySelectDialog(
    currentQualityLabel: String,
    onDismiss: () -> Unit,
    onSelectQuality: (AudioQuality) -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("选择音质偏好", fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                listOf(
                    AudioQuality.HI_RES to "Hi-Res 母带 (24bit/96kHz 超高解析)",
                    AudioQuality.LOSSLESS to "无损 SQ (FLAC/高码率无损)",
                    AudioQuality.HIGH to "高品 320K (高品质 MP3)",
                    AudioQuality.STANDARD to "标准 128K (省流量模式)"
                ).forEach { (quality, desc) ->
                    val isSelected = currentQualityLabel.contains(quality.label.substring(0, 2))
                    val onSelect = {
                        onSelectQuality(quality)
                        Toast.makeText(context, "已切换音质偏好：${quality.label}", Toast.LENGTH_SHORT).show()
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect() }
                            .padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { onSelect() }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ConceptPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
}
