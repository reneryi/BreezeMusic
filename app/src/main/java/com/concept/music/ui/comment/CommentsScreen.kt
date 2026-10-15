package com.concept.music.ui.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.theme.VipGold

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
    onPostComment: (content: String, replyToCommentId: String?, replyToUserName: String?) -> Unit = { _, _, _ -> },
    onLikeComment: (commentId: String) -> Unit = {}
) {
    BackHandler { onBack() }
    val context = LocalContext.current

    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) } // (commentId, userName)
    var commentInputText by remember { mutableStateOf("") }
    var expandedCommentIds by remember { mutableStateOf(setOf<String>()) }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = Color(0xFF101216),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // 左侧歌曲封面缩略图 (参考图三)
                        if (song != null && song.coverUrl.isNotBlank()) {
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        } else if (song != null) {
                            Surface(
                                modifier = Modifier.size(38.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF232733)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Rounded.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.5f),
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
                                    color = Color.White,
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
                                        color = Color.White.copy(alpha = 0.6f),
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
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "刷新",
                            tint = ConceptPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF14171E))
            )
        },
        bottomBar = {
            // 底部常驻评论发布栏 (参考图三：左侧用户头像、圆角胶囊输入框“写下你想说的话吧~”)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161820))
                    .navigationBarsPadding()
            ) {
                // 若正在回复某人，显示快捷取消标签
                AnimatedVisibility(
                    visible = replyTarget != null,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    replyTarget?.let { (_, targetUser) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConceptPrimary.copy(alpha = 0.15f))
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
                                    text = "正在回复 @$targetUser",
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
                                    .clickable { replyTarget = null }
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
                    // 左侧当前用户头像 (参考图三)
                    val avatarToUse = currentUserAvatar.ifBlank {
                        VipManager.accounts.state.value.profile?.avatarUrl ?: ""
                    }
                    if (avatarToUse.isNotBlank()) {
                        AsyncImage(
                            model = avatarToUse,
                            contentDescription = "当前用户头像",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Surface(
                            modifier = Modifier.size(36.dp),
                            shape = CircleShape,
                            color = Color(0xFF2C313E)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Person,
                                    contentDescription = "用户头像",
                                    tint = Color.White.copy(alpha = 0.65f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // 圆角胶囊风格输入框 (参考图三: “写下你想说的话吧~”)
                    TextField(
                        value = commentInputText,
                        onValueChange = { commentInputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp, max = 96.dp),
                        placeholder = {
                            Text(
                                text = if (replyTarget != null) "回复 @${replyTarget?.second}: 写下你想说的话吧~" else "写下你想说的话吧~",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 13.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF232733),
                            unfocusedContainerColor = Color(0xFF232733),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ConceptPrimary,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // 发送按钮
                    IconButton(
                        onClick = {
                            val content = commentInputText.trim()
                            if (content.isNotBlank()) {
                                onPostComment(content, replyTarget?.first, replyTarget?.second)
                                commentInputText = ""
                                replyTarget = null
                                Toast.makeText(context, "评论发表成功", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = commentInputText.isNotBlank(),
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = if (commentInputText.isNotBlank()) ConceptPrimary else Color.White.copy(alpha = 0.15f),
                                shape = CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "发布",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 最热 / 最新 切换 Tab
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14171E))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilterChip(
                    selected = currentType == "hot",
                    onClick = { if (currentType != "hot") onTabSelected("hot") },
                    label = {
                        Text(
                            text = "最热评论",
                            fontSize = 13.sp,
                            fontWeight = if (currentType == "hot") FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = ConceptPrimary,
                        containerColor = Color(0xFF20242E),
                        labelColor = Color.White.copy(alpha = 0.65f)
                    ),
                    border = null,
                    shape = RoundedCornerShape(16.dp)
                )

                FilterChip(
                    selected = currentType == "new",
                    onClick = { if (currentType != "new") onTabSelected("new") },
                    label = {
                        Text(
                            text = "最新评论",
                            fontSize = 13.sp,
                            fontWeight = if (currentType == "new") FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ConceptPrimary.copy(alpha = 0.2f),
                        selectedLabelColor = ConceptPrimary,
                        containerColor = Color(0xFF20242E),
                        labelColor = Color.White.copy(alpha = 0.65f)
                    ),
                    border = null,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            // 评论列表主体
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
                            tint = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无音乐评论，成为第一个留下足迹的人吧",
                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.45f))
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(comments, key = { it.id }) { comment ->
                        val isExpanded = expandedCommentIds.contains(comment.id)
                        RootCommentCard(
                            comment = comment,
                            isExpanded = isExpanded,
                            onToggleExpand = {
                                expandedCommentIds = if (isExpanded) {
                                    expandedCommentIds - comment.id
                                } else {
                                    expandedCommentIds + comment.id
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
                        if (hasMore) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (loading) {
                                    CircularProgressIndicator(color = ConceptPrimary, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                } else {
                                    OutlinedButton(
                                        onClick = onLoadMore,
                                        shape = RoundedCornerShape(20.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        Text("加载更多评论", fontSize = 13.sp)
                                    }
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 18.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "—— 已经到底啦 · 更多心声等你开启 ——",
                                    color = Color.White.copy(alpha = 0.35f),
                                    fontSize = 12.sp
                                )
                            }
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
    onToggleExpand: () -> Unit,
    onReplyRoot: () -> Unit,
    onReplySub: (replyId: String, replyUserName: String) -> Unit,
    onLikeComment: (commentId: String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1E26)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 主楼层头部：头像、昵称、ID、IP 属地、时间、点赞
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
                        color = Color(0xFF2C313E)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Person,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
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
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        )
                        if (comment.vipLevel > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = VipGold.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "VIP${comment.vipLevel}",
                                    color = VipGold,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (comment.isHot) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFF5252).copy(alpha = 0.2f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "精选神评",
                                    color = Color(0xFFFF5252),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    // 用户 ID、IP 属地、发布时间
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "UID: ${comment.userId.take(8)}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = "IP属地: ${comment.ipLocation}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        )
                        Text(
                            text = comment.timeFormatted,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 10.sp
                            )
                        )
                    }
                }

                // 点赞数
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onLikeComment(comment.id) }
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Icon(
                        imageVector = if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "点赞",
                        tint = if (comment.isLiked) Color(0xFFFF5252) else Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (comment.likeCount > 10000) "${comment.likeCount / 10000}万" else "${comment.likeCount}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 评论主内容
            Text(
                text = comment.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 14.sp,
                    lineHeight = 21.sp
                ),
                modifier = Modifier.padding(start = 48.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // 根评论回复按键
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 48.dp),
                horizontalArrangement = Arrangement.End
            ) {
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
                    Text(text = "回复", color = ConceptPrimary, fontSize = 12.sp)
                }
            }

            // 树状分支节点楼中楼回复区 (Tree-branch hierarchy)
            if (comment.replies.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                val visibleReplies = if (isExpanded) comment.replies else comment.replies.take(3)
                val totalRepliesCount = maxOf(comment.replyCount, comment.replies.size)
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

                    // 展开更多回复 / 折叠控制器
                    if (totalRepliesCount > 3) {
                        Row(
                            modifier = Modifier
                                .padding(start = 32.dp, top = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onToggleExpand
                                )
                                .padding(vertical = 4.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (!isExpanded) {
                                    val countToShow = if (remaining > 0) remaining else comment.replies.size - 3
                                    "展开剩余 $countToShow 条回复 ▾"
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

private fun SongComment.hasMoreReplies(displayedCount: Int): Boolean {
    return replies.size > displayedCount
}

/**
 * 树状分支节点 (Tree Branch Node)：
 * 左侧绘制带有平滑折角的树形导轨连接线 (└── / ├──)，高度通过 IntrinsicSize.Min 随卡片自适应，
 * 解决长文字气泡断轨问题。右侧承载带点赞数与回复操作的回复内容卡片。
 */
@Composable
private fun TreeBranchReplyNode(
    reply: SongComment,
    isFirstNode: Boolean = false,
    isLastNode: Boolean,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        // 树状分支导轨连接线 Canvas (使用 fillMaxHeight 随右侧卡片自适应延伸，增加分支小圆点节点，参考图四)
        Canvas(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight()
        ) {
            val strokeColor = Color.White.copy(alpha = 0.28f)
            val strokeWidthPx = 1.5.dp.toPx()
            val startX = 10.dp.toPx()
            val curveRadius = 8.dp.toPx()
            val branchY = 20.dp.toPx()
            val dotX = size.width - 4.dp.toPx()
            val topY = if (isFirstNode) -6.dp.toPx() else 0f

            // 竖向主干连接线：非末尾节点贯穿整个卡片高度，与下一个节点无缝咬合
            drawLine(
                color = strokeColor,
                start = Offset(startX, topY),
                end = Offset(startX, if (isLastNode) branchY else size.height),
                strokeWidth = strokeWidthPx
            )

            // 向右弯曲的分支路径 (├── / └──)
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

            // 分支节点小圆点标记 (类似图四的分支树节点)
            drawCircle(
                color = ConceptPrimary,
                radius = 3.5.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = Color(0xFF1B1E26),
                radius = 1.8.dp.toPx(),
                center = Offset(dotX, branchY)
            )
        }

        // 回复气泡卡片
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF222632)),
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 6.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // 回复者信息行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = reply.userName,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB0C4DE),
                            fontSize = 12.sp
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "IP: ${reply.ipLocation}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = reply.timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.White.copy(alpha = 0.35f),
                            fontSize = 10.sp
                        )
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // 点赞按钮与点赞数
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onLikeClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = "赞",
                            tint = if (reply.likeCount > 0) Color(0xFFFF5252).copy(alpha = 0.85f) else Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(12.dp)
                        )
                        if (reply.likeCount > 0) {
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "${reply.likeCount}",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // 回复按钮
                    Text(
                        text = "回复",
                        color = ConceptPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onReplyClick)
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 回复文字主体
                Row {
                    val targetUser = reply.replyUserName
                    if (!targetUser.isNullOrBlank()) {
                        Text(
                            text = "回复 @$targetUser: ",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = ConceptPrimary.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                    Text(
                        text = reply.content,
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color.White.copy(alpha = 0.88f),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    )
                }
            }
        }
    }
}
