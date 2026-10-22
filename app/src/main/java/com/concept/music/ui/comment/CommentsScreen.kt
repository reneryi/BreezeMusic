package com.concept.music.ui.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.concept.music.data.model.KugouCommentEmojiParser
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.components.smoothFastScrollToItem
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.launch

internal fun formatCommentLikeCount(count: Int): String {
    return when {
        count <= 0 -> "0"
        count < 1_000 -> count.toString()
        count < 10_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1000.0)
        else -> String.format(java.util.Locale.US, "%.1fw", count / 10000.0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    song: Song?,
    comments: List<SongComment>,
    totalCount: Int = 0,
    loading: Boolean,
    currentType: String = "hot",
    hasMore: Boolean = true,
    currentUserAvatar: String = "",
    onBack: () -> Unit,
    onTabSelected: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onLoadReplies: (commentId: String) -> Unit = {},
    onPostComment: (content: String, replyToCommentId: String?, replyToUserName: String?) -> Unit = { _, _, _ -> },
    onLikeComment: (commentId: String) -> Unit = {}
) {
    // 独立楼中楼详情页目标根评论 ID（非空时进入独立楼中楼页面）
    var activeFloorCommentId by remember { mutableStateOf<String?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val activeFloorRootComment = remember(comments, activeFloorCommentId) {
        activeFloorCommentId?.let { id -> comments.find { it.id == id } }
    }

    BackHandler {
        if (previewImageUrl != null) {
            previewImageUrl = null
        } else if (activeFloorCommentId != null) {
            activeFloorCommentId = null
        } else {
            onBack()
        }
    }

    // 评论大图全屏预览弹窗
    previewImageUrl?.let { imgUrl ->
        CommentImagePreviewDialog(
            imageUrl = imgUrl,
            onDismiss = { previewImageUrl = null }
        )
    }

    // 独立楼中楼全部评论详情页面过渡
    AnimatedContent(
        targetState = activeFloorRootComment,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(240)) { -it / 5 } + fadeOut(tween(180)))
            } else {
                (slideInHorizontally(tween(260)) { -it / 5 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(240)) { it / 3 } + fadeOut(tween(180)))
            }
        },
        label = "CommentFloorPageTransition"
    ) { floorComment ->
        if (floorComment != null) {
            CommentFloorDetailScreen(
                song = song,
                rootComment = floorComment,
                currentUserAvatar = currentUserAvatar,
                onBack = { activeFloorCommentId = null },
                onPostReply = { content, replyToId, replyToUser ->
                    onPostComment(content, replyToId ?: floorComment.id, replyToUser ?: floorComment.userName)
                },
                onLikeComment = onLikeComment,
                onPreviewImage = { previewImageUrl = it }
            )
            return@AnimatedContent
        }

        val context = LocalContext.current
        val isDark = LocalIsDarkTheme.current
        val primaryText = MaterialTheme.colorScheme.onSurface
        val subText = MaterialTheme.colorScheme.onSurfaceVariant
        val elevatedBg = conceptElevatedSurfaceColor()
        val barBg = conceptSheetContainerColor()
        val coroutineScope = rememberCoroutineScope()

        var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
        var commentInputText by remember { mutableStateOf("") }
        var showEmojiBar by remember { mutableStateOf(false) }
        val listState = rememberLazyListState()

        // 下滑浏览时自然静默加载更多评论（提前 5 个条目触发，丝滑无感加载）
        val shouldLoadMore by remember {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val totalItems = layoutInfo.totalItemsCount
                val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItems > 0 && lastVisibleIndex >= totalItems - 5
            }
        }
        LaunchedEffect(shouldLoadMore, hasMore, loading, comments.size) {
            if (shouldLoadMore && hasMore && !loading && comments.isNotEmpty()) {
                onLoadMore()
            }
        }

        val showScrollToTop by remember {
            derivedStateOf { listState.firstVisibleItemIndex > 4 }
        }

        ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    TopAppBar(
                        modifier = Modifier.statusBarsPadding(),
                        windowInsets = WindowInsets(0, 0, 0, 0),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (song != null && song.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = song.coverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                } else if (song != null) {
                                    Surface(
                                        modifier = Modifier.size(38.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        color = elevatedBg
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = subText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song?.title ?: "歌曲评论",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.Bold,
                                            color = primaryText,
                                            fontSize = 15.sp
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (song != null) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = song.artist,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = subText,
                                                fontSize = 12.sp
                                            ),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "返回",
                                    tint = primaryText
                                )
                            }
                        },
                        actions = {
                            var commentRefreshSpin by remember { mutableIntStateOf(0) }
                            IconButton(onClick = {
                                commentRefreshSpin += 1
                                onRefresh()
                            }) {
                                ConceptFluidRefreshIcon(
                                    tint = ConceptPrimary,
                                    isRefreshing = loading,
                                    spinTrigger = commentRefreshSpin,
                                    modifier = Modifier.size(19.dp)
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                bottomBar = {
                    CommentComposerBottomBar(
                        barBg = barBg,
                        elevatedBg = elevatedBg,
                        primaryText = primaryText,
                        subText = subText,
                        currentUserAvatar = currentUserAvatar,
                        replyTarget = replyTarget,
                        onClearReplyTarget = { replyTarget = null },
                        commentInputText = commentInputText,
                        onInputChange = { commentInputText = it },
                        showEmojiBar = showEmojiBar,
                        onToggleEmojiBar = { showEmojiBar = !showEmojiBar },
                        onSend = {
                            val content = commentInputText.trim()
                            if (content.isNotBlank()) {
                                onPostComment(content, replyTarget?.first, replyTarget?.second)
                                commentInputText = ""
                                replyTarget = null
                                showEmojiBar = false
                                Toast.makeText(context, "评论已发送", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // 顶部排序切换栏（最热 / 最新 按时间顺序实时拉取）
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilterChip(
                                    selected = currentType == "hot",
                                    onClick = {
                                        if (currentType != "hot") {
                                            onTabSelected("hot")
                                            coroutineScope.launch { listState.scrollToItem(0) }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "最热评论",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (currentType == "hot") FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.18f),
                                        selectedLabelColor = ConceptPrimary,
                                        containerColor = elevatedBg,
                                        labelColor = subText
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(16.dp)
                                )

                                FilterChip(
                                    selected = currentType == "new",
                                    onClick = {
                                        if (currentType != "new") {
                                            onTabSelected("new")
                                            coroutineScope.launch { listState.scrollToItem(0) }
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = "最新评论 · 时间序",
                                            fontSize = 12.5.sp,
                                            fontWeight = if (currentType == "new") FontWeight.ExtraBold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.18f),
                                        selectedLabelColor = ConceptPrimary,
                                        containerColor = elevatedBg,
                                        labelColor = subText
                                    ),
                                    border = null,
                                    shape = RoundedCornerShape(16.dp)
                                )
                            }

                            Text(
                                text = if (currentType == "hot") "按热度权重排序" else "按实时发布时间排序",
                                color = subText.copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }

                        if (loading && comments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = ConceptPrimary, modifier = Modifier.size(36.dp))
                            }
                        } else if (comments.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.Comment,
                                        contentDescription = null,
                                        tint = subText.copy(alpha = 0.4f),
                                        modifier = Modifier.size(56.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "暂无评论，快来抢占沙发吧",
                                        style = MaterialTheme.typography.bodyMedium.copy(color = subText)
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .weight(1f),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(
                                    items = comments,
                                    key = { it.id },
                                    contentType = { "root_comment_card" }
                                ) { comment ->
                                    // 若有子评论但尚未拉取，自动静默拉取前3条平级回复用于展示
                                    LaunchedEffect(comment.id, comment.replyCount, comment.replies.size) {
                                        if (comment.replyCount > 0 && comment.replies.isEmpty()) {
                                            onLoadReplies(comment.id)
                                        }
                                    }

                                    RootCommentCard(
                                        comment = comment,
                                        isDark = isDark,
                                        onOpenAllRepliesFloor = {
                                            onLoadReplies(comment.id)
                                            activeFloorCommentId = comment.id
                                        },
                                        onReplyRoot = {
                                            replyTarget = comment.id to comment.userName
                                        },
                                        onReplySub = { _, replyUserName ->
                                            replyTarget = comment.id to replyUserName
                                        },
                                        onLikeComment = onLikeComment,
                                        onPreviewImage = { previewImageUrl = it }
                                    )
                                }

                                item(contentType = "comment_footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (loading) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                CircularProgressIndicator(
                                                    color = ConceptPrimary,
                                                    modifier = Modifier.size(18.dp),
                                                    strokeWidth = 2.dp
                                                )
                                                Text(
                                                    text = "正在加载更多评论...",
                                                    color = subText.copy(alpha = 0.7f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                        } else if (!hasMore) {
                                            Text(
                                                text = "— 已展示全部评论 —",
                                                color = subText.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 悬浮返回顶部按钮（使用两段式极速平滑滚动，杜绝长列表卡顿）
                    AnimatedVisibility(
                        visible = showScrollToTop,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 18.dp, bottom = 18.dp)
                    ) {
                        Surface(
                            onClick = {
                                coroutineScope.launch { listState.smoothFastScrollToItem(0) }
                            },
                            shape = CircleShape,
                            color = ConceptPrimary.copy(alpha = 0.92f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.VerticalAlignTop,
                                    contentDescription = "返回顶部",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 独立楼中楼评论详情页 (点击“查看全部 X 条回复”后进入的独立页面)
 * 顶部展示楼主原评，下方完整展示所有楼中楼回复（保留分支点连线设计 + 下滑分页加载更多回复）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFloorDetailScreen(
    song: Song?,
    rootComment: SongComment,
    currentUserAvatar: String = "",
    onBack: () -> Unit,
    onPostReply: (content: String, replyToCommentId: String?, replyToUserName: String?) -> Unit,
    onLikeComment: (commentId: String) -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedBg = conceptElevatedSurfaceColor()
    val cardColor = conceptFloatingCardColor()
    val barBg = conceptSheetContainerColor()
    val scope = rememberCoroutineScope()

    var floorReplies by remember(rootComment.id) { mutableStateOf(rootComment.replies) }
    var floorPage by remember(rootComment.id) { mutableIntStateOf(1) }
    var floorLoading by remember(rootComment.id) { mutableStateOf(false) }
    var floorHasMore by remember(rootComment.id) {
        mutableStateOf(rootComment.replyCount > rootComment.replies.size || rootComment.replies.size >= 10)
    }
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }

    // 同步外部 rootComment.replies 更新（例如点赞或刚发布的评论）
    LaunchedEffect(rootComment.replies) {
        if (rootComment.replies.isNotEmpty()) {
            floorReplies = (rootComment.replies + floorReplies).distinctBy { it.id }
        }
    }

    fun fetchFloorReplies(page: Int) {
        if (song == null || floorLoading) return
        floorLoading = true
        scope.launch {
            val fetched = runCatching {
                VipManager.music.songCommentReplies(song, rootComment.id, page = page, pageSize = 25)
            }.getOrDefault(emptyList())
            val merged = (if (page == 1) rootComment.replies + fetched else floorReplies + fetched).distinctBy { it.id }
            val added = merged.size > floorReplies.size || page == 1
            floorReplies = merged
            floorPage = page
            floorHasMore = fetched.size >= 10 && added && merged.size < maxOf(rootComment.replyCount, merged.size + 1)
            floorLoading = false
        }
    }

    LaunchedEffect(rootComment.id) {
        if (floorReplies.size < minOf(rootComment.replyCount, 20)) {
            fetchFloorReplies(1)
        }
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore, floorHasMore, floorLoading, floorReplies.size) {
        if (shouldLoadMore && floorHasMore && !floorLoading && floorReplies.isNotEmpty()) {
            fetchFloorReplies(floorPage + 1)
        }
    }

    val totalCount = maxOf(rootComment.replyCount, floorReplies.size)

    ConceptStageBackground(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    modifier = Modifier.statusBarsPadding(),
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    title = {
                        Column {
                            Text(
                                text = "评论回复详情 ($totalCount)",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText,
                                    fontSize = 16.sp
                                )
                            )
                            if (song != null) {
                                Text(
                                    text = "${song.title} · ${song.artist}",
                                    color = subText,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回评论列表",
                                tint = primaryText
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            },
            bottomBar = {
                CommentComposerBottomBar(
                    barBg = barBg,
                    elevatedBg = elevatedBg,
                    primaryText = primaryText,
                    subText = subText,
                    currentUserAvatar = currentUserAvatar,
                    replyTarget = replyTarget,
                    onClearReplyTarget = { replyTarget = null },
                    commentInputText = inputText,
                    onInputChange = { inputText = it },
                    showEmojiBar = showEmojiBar,
                    onToggleEmojiBar = { showEmojiBar = !showEmojiBar },
                    placeholderOverride = if (replyTarget != null) "回复 @${replyTarget?.second}..." else "回复楼主 @${rootComment.userName}...",
                    onSend = {
                        val content = inputText.trim()
                        if (content.isNotBlank()) {
                            onPostReply(content, rootComment.id, replyTarget?.second ?: rootComment.userName)
                            inputText = ""
                            replyTarget = null
                            showEmojiBar = false
                            Toast.makeText(context, "回复已发送", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 1. 楼主原评置顶卡片
                item(key = "floor_root_${rootComment.id}") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            CommentUserHeaderRow(
                                comment = rootComment,
                                isDark = isDark,
                                isFloorOwner = true,
                                onLikeClick = { onLikeComment(rootComment.id) }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = KugouCommentEmojiParser.formatContent(rootComment.content),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = primaryText.copy(alpha = 0.94f),
                                    fontSize = 14.5.sp,
                                    lineHeight = 22.sp
                                ),
                                modifier = Modifier.padding(start = 48.dp)
                            )
                            if (rootComment.images.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CommentImagesGrid(
                                    images = rootComment.images,
                                    onImageClick = onPreviewImage,
                                    modifier = Modifier.padding(start = 48.dp)
                                )
                            }
                        }
                    }
                }

                // 2. 全部楼中楼回复小标题
                item(key = "floor_section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "全部平级回复 ($totalCount)",
                            color = primaryText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "分支节点连线视图",
                            color = ConceptPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // 3. 带分支点连线的完整平级回复流
                itemsIndexed(
                    items = floorReplies,
                    key = { _, r -> r.id },
                    contentType = { _, _ -> "floor_reply_node" }
                ) { index, reply ->
                    TreeBranchReplyNode(
                        reply = reply,
                        isFirstNode = index == 0,
                        isLastNode = index == floorReplies.lastIndex,
                        hasMoreNodesBelow = false,
                        onReplyClick = { replyTarget = reply.id to reply.userName },
                        onLikeClick = { onLikeComment(reply.id) },
                        onPreviewImage = onPreviewImage
                    )
                }

                item(key = "floor_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (floorLoading) {
                            CircularProgressIndicator(
                                color = ConceptPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else if (floorReplies.isEmpty()) {
                            Text("暂无更多回复", color = subText.copy(alpha = 0.5f), fontSize = 12.sp)
                        } else if (!floorHasMore) {
                            Text("— 已显示全部 $totalCount 条回复 —", color = subText.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RootCommentCard(
    comment: SongComment,
    isDark: Boolean,
    onOpenAllRepliesFloor: () -> Unit,
    onReplyRoot: () -> Unit,
    onReplySub: (replyId: String, replyUserName: String) -> Unit,
    onLikeComment: (commentId: String) -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val cardColor = conceptFloatingCardColor()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            CommentUserHeaderRow(
                comment = comment,
                isDark = isDark,
                isFloorOwner = false,
                onLikeClick = { onLikeComment(comment.id) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = KugouCommentEmojiParser.formatContent(comment.content),
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = primaryText.copy(alpha = 0.93f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                modifier = Modifier.padding(start = 48.dp)
            )

            if (comment.images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                CommentImagesGrid(
                    images = comment.images,
                    onImageClick = onPreviewImage,
                    modifier = Modifier.padding(start = 48.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            val totalRepliesCount = maxOf(comment.replyCount, comment.replies.size)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (totalRepliesCount > 0 && comment.replies.isEmpty()) {
                    Text(
                        text = "查看全部 $totalRepliesCount 条回复 >",
                        color = ConceptPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .softClickable(onClick = onOpenAllRepliesFloor)
                            .padding(vertical = 4.dp)
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                TextButton(
                    onClick = onReplyRoot,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = null,
                        tint = ConceptPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "回复", color = ConceptPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 楼中楼平级回复区域：默认固定展示前 3 条平级评论 + 分支点连线设计；超过 3 条时点击进入独立页面
            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                val visibleReplies = comment.replies.take(3)
                val hasMoreReplies = totalRepliesCount > 3 || comment.replies.size > 3

                Column(modifier = Modifier.padding(start = 18.dp)) {
                    visibleReplies.forEachIndexed { index, reply ->
                        val isFirst = index == 0
                        val isLast = index == visibleReplies.lastIndex
                        TreeBranchReplyNode(
                            reply = reply,
                            isFirstNode = isFirst,
                            isLastNode = isLast,
                            hasMoreNodesBelow = isLast && hasMoreReplies,
                            onReplyClick = { onReplySub(reply.id, reply.userName) },
                            onLikeClick = { onLikeComment(reply.id) },
                            onPreviewImage = onPreviewImage
                        )
                    }

                    if (hasMoreReplies) {
                        TreeBranchViewAllNode(
                            totalCount = totalRepliesCount,
                            onClick = onOpenAllRepliesFloor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentUserHeaderRow(
    comment: SongComment,
    isDark: Boolean,
    isFloorOwner: Boolean,
    onLikeClick: () -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedColor = conceptElevatedSurfaceColor()

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        if (comment.userAvatar.isNotBlank()) {
            AsyncImage(
                model = comment.userAvatar,
                contentDescription = null,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = CircleShape,
                color = elevatedColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = subText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.userName,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = primaryText,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isFloorOwner) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = ConceptPrimary.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "楼主",
                            color = ConceptPrimary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                if (comment.vipLevel > 0) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = VipGold.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "VIP",
                            color = if (isDark) VipGold else Color(0xFFB87900),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (comment.isHot) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.16f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "热评",
                            color = Color(0xFFFF5252),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = comment.timeFormatted,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = subText.copy(alpha = 0.78f),
                        fontSize = 11.sp
                    )
                )
                if (comment.ipLocation.isNotBlank()) {
                    Text(
                        text = "IP属地：${comment.ipLocation}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = subText.copy(alpha = 0.72f),
                            fontSize = 11.sp
                        )
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .softClickable(onClick = onLikeClick)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "点赞",
                tint = if (comment.isLiked) Color(0xFFFF5252) else subText.copy(alpha = 0.7f),
                modifier = Modifier.size(16.dp)
            )
            if (comment.likeCount > 0) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatCommentLikeCount(comment.likeCount),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = if (comment.isLiked) Color(0xFFFF5252) else subText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
            }
        }
    }
}

/**
 * 升级版楼中楼分支点连线节点 (TreeBranchReplyNode)：
 * 采用主干渐变轴线 + 贝塞尔圆弧分叉 + 双环发光分支点 (Branch Node)，
 * 清晰串联三条平级评论与“查看全部回复”入口。
 */
@Composable
private fun TreeBranchReplyNode(
    reply: SongComment,
    isFirstNode: Boolean = false,
    isLastNode: Boolean,
    hasMoreNodesBelow: Boolean = false,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onPreviewImage: (String) -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedColor = conceptElevatedSurfaceColor()
    val cardBg = conceptFloatingCardColor()
    val branchAccent = ConceptPrimary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(
            modifier = Modifier
                .width(34.dp)
                .fillMaxHeight()
        ) {
            val spineColor = branchAccent.copy(alpha = 0.36f)
            val strokeWidthPx = 1.6.dp.toPx()
            val startX = 8.dp.toPx()
            val curveRadius = 9.dp.toPx()
            val branchY = 20.dp.toPx()
            val dotX = size.width - 5.dp.toPx()
            val topY = if (isFirstNode) -4.dp.toPx() else 0f
            val continueSpine = !isLastNode || hasMoreNodesBelow

            // 1. 垂直主干连接线
            drawLine(
                color = spineColor,
                start = Offset(startX, topY),
                end = Offset(startX, if (continueSpine) size.height + 4.dp.toPx() else branchY - curveRadius),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            // 首个分支顶部起始锚点小圆环
            if (isFirstNode) {
                drawCircle(
                    color = branchAccent.copy(alpha = 0.55f),
                    radius = 2.4.dp.toPx(),
                    center = Offset(startX, 0f)
                )
            }

            // 2. 贝塞尔平滑分叉曲线 (Spine -> Branch Node)
            val branchPath = Path().apply {
                moveTo(startX, branchY - curveRadius)
                quadraticBezierTo(startX, branchY, startX + curveRadius, branchY)
                lineTo(dotX, branchY)
            }
            drawPath(
                path = branchPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(spineColor, branchAccent.copy(alpha = 0.75f))
                ),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // 3. 分支点连线核心节点（外层呼吸光晕 + 主题色实心环 + 中心白芯）
            drawCircle(
                color = branchAccent.copy(alpha = 0.22f),
                radius = 5.5.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = branchAccent,
                radius = 3.4.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = cardBg,
                radius = 1.5.dp.toPx(),
                center = Offset(dotX, branchY)
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = elevatedColor,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp)
                .softCardClickable(shape = RoundedCornerShape(14.dp), onClick = onReplyClick)
        ) {
            Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (reply.userAvatar.isNotBlank()) {
                        AsyncImage(
                            model = reply.userAvatar,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text(
                        text = reply.userName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ConceptPrimary,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (reply.ipLocation.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IP属地：${reply.ipLocation}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = subText.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = reply.timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = subText.copy(alpha = 0.65f),
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .softClickable(onClick = onLikeClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = if (reply.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (reply.isLiked) Color(0xFFFF5252) else subText.copy(alpha = 0.6f),
                            modifier = Modifier.size(13.dp)
                        )
                        if (reply.likeCount > 0) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = formatCommentLikeCount(reply.likeCount),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subText,
                                    fontSize = 10.sp
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                val formattedReplyText = KugouCommentEmojiParser.formatContent(reply.content)
                val annotatedContent = buildAnnotatedString {
                    if (!reply.replyUserName.isNullOrBlank()) {
                        append("回复 ")
                        withStyle(SpanStyle(color = ConceptPrimary, fontWeight = FontWeight.SemiBold)) {
                            append("@${reply.replyUserName}")
                        }
                        append("：")
                    }
                    append(formattedReplyText)
                }

                Text(
                    text = annotatedContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = primaryText.copy(alpha = 0.92f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                )

                if (reply.images.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    CommentImagesGrid(
                        images = reply.images,
                        onImageClick = onPreviewImage
                    )
                }
            }
        }
    }
}

/**
 * 分支连线末端节点：「查看全部 X 条回复 >」（点击进入独立楼中楼页面）
 */
@Composable
private fun TreeBranchViewAllNode(
    totalCount: Int,
    onClick: () -> Unit
) {
    val branchAccent = ConceptPrimary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(34.dp)
                .height(32.dp)
        ) {
            val spineColor = branchAccent.copy(alpha = 0.36f)
            val strokeWidthPx = 1.6.dp.toPx()
            val startX = 8.dp.toPx()
            val curveRadius = 9.dp.toPx()
            val branchY = 16.dp.toPx()
            val dotX = size.width - 5.dp.toPx()

            val branchPath = Path().apply {
                moveTo(startX, 0f)
                lineTo(startX, branchY - curveRadius)
                quadraticBezierTo(startX, branchY, startX + curveRadius, branchY)
                lineTo(dotX, branchY)
            }
            drawPath(
                path = branchPath,
                color = spineColor,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            drawCircle(
                color = branchAccent,
                radius = 2.8.dp.toPx(),
                center = Offset(dotX, branchY)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = ConceptPrimary.copy(alpha = 0.12f),
            modifier = Modifier
                .padding(vertical = 2.dp)
                .softCardClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "查看全部 $totalCount 条回复",
                    color = ConceptPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ConceptPrimary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

/**
 * 评论配图缩略图网格（支持单图自适应大图与多图横向画廊，点击全屏查看）
 */
@Composable
internal fun CommentImagesGrid(
    images: List<String>,
    onImageClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (images.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val itemSize = if (images.size == 1) 132.dp else 88.dp
        images.take(6).forEach { url ->
            AsyncImage(
                model = url,
                contentDescription = "评论图片",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(itemSize)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.6.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                    .softClickable { onImageClick(url) }
            )
        }
    }
}

@Composable
private fun CommentImagePreviewDialog(
    imageUrl: String,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .softClickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "评论大图预览",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(16.dp)
                    .background(Color.White.copy(alpha = 0.14f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = Color.White)
            }
        }
    }
}

@Composable
private fun CommentComposerBottomBar(
    barBg: Color,
    elevatedBg: Color,
    primaryText: Color,
    subText: Color,
    currentUserAvatar: String,
    replyTarget: Pair<String, String>?,
    onClearReplyTarget: () -> Unit,
    commentInputText: String,
    onInputChange: (String) -> Unit,
    showEmojiBar: Boolean,
    onToggleEmojiBar: () -> Unit,
    placeholderOverride: String? = null,
    onSend: () -> Unit
) {
    val quickEmojis = remember {
        listOf("😊", "😂", "😭", "😍", "🥺", "👍", "❤️", "🌹", "🎧", "🎵", "🔥", "✨", "👏", "🥳", "😎", "🙏")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barBg)
            .navigationBarsPadding()
    ) {
        AnimatedVisibility(
            visible = replyTarget != null,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            replyTarget?.let { (_, targetUser) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConceptPrimary.copy(alpha = 0.14f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Reply,
                            contentDescription = null,
                            tint = ConceptPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "回复 @$targetUser",
                            color = ConceptPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "取消回复",
                        tint = ConceptPrimary,
                        modifier = Modifier
                            .size(18.dp)
                            .softClickable { onClearReplyTarget() }
                    )
                }
            }
        }

        // 快捷 Emoji 表情面板
        AnimatedVisibility(
            visible = showEmojiBar,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                quickEmojis.forEach { emoji ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = elevatedBg,
                        modifier = Modifier.softClickable {
                            onInputChange(commentInputText + emoji)
                        }
                    ) {
                        Text(
                            text = emoji,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val avatarToUse = currentUserAvatar.ifBlank {
                VipManager.accounts.state.value.profile?.avatarUrl ?: ""
            }
            if (avatarToUse.isNotBlank()) {
                AsyncImage(
                    model = avatarToUse,
                    contentDescription = "用户头像",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                )
            } else {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = elevatedBg
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "用户头像",
                            tint = subText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            TextField(
                value = commentInputText,
                onValueChange = onInputChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 96.dp),
                placeholder = {
                    Text(
                        text = placeholderOverride
                            ?: if (replyTarget != null) "回复 @${replyTarget.second}..." else "分享你的听歌感受...",
                        color = subText.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                },
                trailingIcon = {
                    IconButton(onClick = onToggleEmojiBar, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.SentimentSatisfiedAlt,
                            contentDescription = "表情",
                            tint = if (showEmojiBar) ConceptPrimary else subText.copy(alpha = 0.75f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = elevatedBg,
                    unfocusedContainerColor = elevatedBg,
                    focusedTextColor = primaryText,
                    unfocusedTextColor = primaryText,
                    cursorColor = ConceptPrimary,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(22.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onSend,
                enabled = commentInputText.isNotBlank(),
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (commentInputText.isNotBlank()) ConceptPrimary else elevatedBg,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Send,
                    contentDescription = "发布",
                    tint = if (commentInputText.isNotBlank()) Color.White else subText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
