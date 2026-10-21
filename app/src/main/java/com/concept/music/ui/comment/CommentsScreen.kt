package com.concept.music.ui.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.concept.music.data.model.Song
import com.concept.music.data.model.SongComment
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.theme.ConceptFluidRefreshIcon
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.ConceptStageBackground
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptFloatingCardColor
import com.concept.music.ui.theme.conceptSheetContainerColor
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
    BackHandler { onBack() }
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedBg = conceptElevatedSurfaceColor()
    val barBg = conceptSheetContainerColor()
    val coroutineScope = rememberCoroutineScope()

    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var commentInputText by remember { mutableStateOf("") }
    var expandedCommentIds by remember { mutableStateOf(setOf<String>()) }
    val listState = rememberLazyListState()

    // 下滑浏览时自然静默加载更多评论，无需手动点击按钮
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, loading, comments.size) {
        if (shouldLoadMore && hasMore && !loading && comments.isNotEmpty()) {
            onLoadMore()
        }
    }

    val showScrollToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 5 }
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
                                        .softClickable { replyTarget = null }
                                )
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
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(36.dp),
                                shape = CircleShape,
                                color = elevatedBg
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.Person,
                                        contentDescription = "用户头像",
                                        tint = subText,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        TextField(
                            value = commentInputText,
                            onValueChange = { commentInputText = it },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp, max = 96.dp),
                            placeholder = {
                                Text(
                                    text = if (replyTarget != null) "回复 @${replyTarget?.second}..." else "分享你的听歌感受...",
                                    color = subText.copy(alpha = 0.7f),
                                    fontSize = 13.sp
                                )
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

                        Spacer(modifier = Modifier.width(10.dp))

                        IconButton(
                            onClick = {
                                val content = commentInputText.trim()
                                if (content.isNotBlank()) {
                                    onPostComment(content, replyTarget?.first, replyTarget?.second)
                                    commentInputText = ""
                                    replyTarget = null
                                    Toast.makeText(context, "评论已发送", Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = commentInputText.isNotBlank(),
                            modifier = Modifier
                                .size(42.dp)
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
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
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
                                    text = "最热",
                                    fontSize = 13.sp,
                                    fontWeight = if (currentType == "hot") FontWeight.Bold else FontWeight.Normal
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
                                    text = "最新",
                                    fontSize = 13.sp,
                                    fontWeight = if (currentType == "new") FontWeight.Bold else FontWeight.Normal
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
                                    text = "暂无评论",
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
                            items(comments, key = { it.id }) { comment ->
                                val isExpanded = expandedCommentIds.contains(comment.id)
                                RootCommentCard(
                                    comment = comment,
                                    isExpanded = isExpanded,
                                    isDark = isDark,
                                    onToggleExpand = {
                                        if (!isExpanded) {
                                            expandedCommentIds = expandedCommentIds + comment.id
                                            onLoadReplies(comment.id)
                                        } else {
                                            expandedCommentIds = expandedCommentIds - comment.id
                                        }
                                    },
                                    onReplyRoot = {
                                        replyTarget = comment.id to comment.userName
                                    },
                                    onReplySub = { _, replyUserName ->
                                        replyTarget = comment.id to replyUserName
                                    },
                                    onLikeComment = onLikeComment
                                )
                            }

                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (loading) {
                                        CircularProgressIndicator(
                                            color = ConceptPrimary,
                                            modifier = Modifier.size(22.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else if (!hasMore) {
                                        Text(
                                            text = "已显示全部评论",
                                            color = subText.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 悬浮返回顶部按钮（简洁纯图标）
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
                            coroutineScope.launch { listState.animateScrollToItem(0) }
                        },
                        shape = CircleShape,
                        color = ConceptPrimary.copy(alpha = 0.9f),
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

@Composable
private fun RootCommentCard(
    comment: SongComment,
    isExpanded: Boolean,
    isDark: Boolean,
    onToggleExpand: () -> Unit,
    onReplyRoot: () -> Unit,
    onReplySub: (replyId: String, replyUserName: String) -> Unit,
    onLikeComment: (commentId: String) -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = conceptFloatingCardColor()
    val elevatedColor = conceptElevatedSurfaceColor()

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardColor,
        shadowElevation = 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
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
                                text = comment.ipLocation,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = subText.copy(alpha = 0.7f),
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .softClickable { onLikeComment(comment.id) }
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = primaryText.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                modifier = Modifier.padding(start = 48.dp)
            )

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
                        text = "查看 $totalRepliesCount 条回复 ▾",
                        color = ConceptPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .softClickable(onClick = onToggleExpand)
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

            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val visibleReplies = if (isExpanded) comment.replies else comment.replies.take(3)
                val remaining = totalRepliesCount - visibleReplies.size

                Column(modifier = Modifier.padding(start = 24.dp)) {
                    visibleReplies.forEachIndexed { index, reply ->
                        val isFirst = index == 0
                        val isLast = index == visibleReplies.lastIndex && (remaining <= 0 || isExpanded)
                        TreeBranchReplyNode(
                            reply = reply,
                            isFirstNode = isFirst,
                            isLastNode = isLast,
                            onReplyClick = { onReplySub(reply.id, reply.userName) },
                            onLikeClick = { onLikeComment(reply.id) }
                        )
                    }

                    if (totalRepliesCount > 3 || remaining > 0) {
                        Row(
                            modifier = Modifier
                                .padding(start = 32.dp, top = 4.dp)
                                .softClickable(onClick = onToggleExpand)
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!isExpanded) {
                                    val countToShow = if (remaining > 0) remaining else (comment.replies.size - 3).coerceAtLeast(1)
                                    "展开更多 $countToShow 条回复 ▾"
                                } else {
                                    "收起回复 ▴"
                                },
                                color = ConceptPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TreeBranchReplyNode(
    reply: SongComment,
    isFirstNode: Boolean = false,
    isLastNode: Boolean,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedColor = conceptElevatedSurfaceColor()
    val cardBg = conceptFloatingCardColor()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            val strokeColor = subText.copy(alpha = 0.35f)
            val strokeWidthPx = 1.5.dp.toPx()
            val startX = 10.dp.toPx()
            val curveRadius = 8.dp.toPx()
            val branchY = 20.dp.toPx()
            val dotX = size.width - 4.dp.toPx()
            val topY = if (isFirstNode) -6.dp.toPx() else 0f

            drawLine(
                color = strokeColor,
                start = Offset(startX, topY),
                end = Offset(startX, if (isLastNode) branchY else size.height),
                strokeWidth = strokeWidthPx
            )

            val branchPath = Path().apply {
                moveTo(startX, branchY - curveRadius)
                quadraticBezierTo(startX, branchY, startX + curveRadius, branchY)
                lineTo(dotX, branchY)
            }
            drawPath(
                path = branchPath,
                color = strokeColor,
                style = Stroke(width = strokeWidthPx)
            )

            drawCircle(
                color = ConceptPrimary,
                radius = 3.5.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = cardBg,
                radius = 1.8.dp.toPx(),
                center = Offset(dotX, branchY)
            )
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = elevatedColor,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reply.userName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = ConceptPrimary,
                            fontSize = 12.sp
                        )
                    )

                    if (reply.ipLocation.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = reply.ipLocation,
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
                            color = subText.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        ),
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

                Text(
                    text = if (!reply.replyUserName.isNullOrBlank()) {
                        "回复 @${reply.replyUserName}：${reply.content}"
                    } else {
                        reply.content
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = primaryText.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Text(
                        text = "回复",
                        color = ConceptPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .softClickable(onClick = onReplyClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
