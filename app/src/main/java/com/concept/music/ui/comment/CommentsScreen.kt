package com.concept.music.ui.comment

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.ui.zIndex
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

private val SmoothCommentSlideEasing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)

internal fun formatCommentLikeCount(count: Int): String {
    return when {
        count <= 0 -> "0"
        count < 1_000 -> count.toString()
        count < 10_000 -> String.format(java.util.Locale.US, "%.1fk", count / 1000.0)
        else -> String.format(java.util.Locale.US, "%.1fw", count / 10000.0)
    }
}

/**
 * 评论多级对话数据模型（用于超过四级评论后点击「查看对话」进入完整平铺对话界面）
 */
@Immutable
data class CommentConversationChain(
    val id: String,
    val title: String,
    val rootClusterComment: SongComment,
    val dialogueList: List<Pair<SongComment, Int>> // Pair<评论节点, 所在层级 1..N>
)

/**
 * 楼层内 1~4 级树状集群可视化渲染项
 */
@Immutable
internal sealed interface FloorTreeVisualItem {
    val key: String

    @Immutable
    data class ClusterCommentRow(
        override val key: String,
        val comment: SongComment,
        val level: Int, // 1..4
        val replyToUser: String?, // 2..4 级显示的被回复人
        val isFirstInSubBranch: Boolean,
        val isLastSibling: Boolean,
        val hasChildBranchBelow: Boolean,
        val activeParentTrunks: List<Boolean>, // 索引 0..(level-2) 表示外层第 1..(level-1) 级主干线是否继续向下延伸
        val directConversationChain: CommentConversationChain? = null
    ) : FloorTreeVisualItem

    @Immutable
    data class ViewDeepDialogueBadgeRow(
        override val key: String,
        val level: Int, // 固定在 4 级分支末端展示
        val hiddenDeepReplyCount: Int,
        val activeParentTrunks: List<Boolean>,
        val conversationChain: CommentConversationChain
    ) : FloorTreeVisualItem
}

internal data class FloorClusterTreeBuildResult(
    val visualItems: List<FloorTreeVisualItem>,
    val totalReplyCountInTree: Int
)

private class MutableCommentTreeNode(
    val comment: SongComment,
    val children: MutableList<MutableCommentTreeNode> = mutableListOf()
)

private fun normalizeForQuoteMatch(text: String): String {
    return KugouCommentEmojiParser.formatContent(text)
        .lowercase()
        .replace(Regex("""[\s\p{P}\p{S}]"""), "")
}

/**
 * 将楼层内的回复列表构建为「一级集群 -> 二级子集群 -> 三级分支 -> 四级分支」树状结构：
 * - 楼主评论下直接对楼主的回复为【一级评论 (level = 1)】，挂载于一级主分支线；
 * - 回复一级评论的为【二级评论 (level = 2)】，从一级评论分支延伸出二级主分支线；
 * - 回复二级评论的为【三级评论 (level = 3)】，回复三级的为【四级评论 (level = 4)】；
 * - 超过四级评论（5 级及以上）不再继续向内缩进分支线，而是在四级评论下方生成「查看对话」入口标识，
 *   点击进入独立的「查看对话」页面以常见评论列表样式完整浏览该多级对话。
 */
internal fun buildFloorClusterTree(
    rootComment: SongComment,
    rawReplies: List<SongComment>
): FloorClusterTreeBuildResult {
    if (rawReplies.isEmpty()) {
        return FloorClusterTreeBuildResult(emptyList(), 0)
    }

    val rootNormContent = normalizeForQuoteMatch(rootComment.content)
    val rootUserLower = rootComment.userName.trim().lowercase()

    // 1. 预处理并补齐可能存在于 //@用户A:内容A//@用户B:内容B 引用链中的中间节点，确保多级回复关系完整
    val allReplies = ArrayList<SongComment>(rawReplies.size + 8)
    val knownById = HashMap<String, SongComment>()
    val knownByNormContent = HashMap<String, SongComment>()

    rawReplies.forEach { r ->
        val clean = r.copy(content = KugouCommentEmojiParser.formatContent(r.content))
        allReplies.add(clean)
        knownById[clean.id] = clean
        val nc = normalizeForQuoteMatch(clean.content)
        if (nc.length >= 2) {
            knownByNormContent.putIfAbsent(nc, clean)
        }
    }

    // 检查是否有子回复通过 //@用户名:引用内容 指向某条尚未在当前分页中出现的上层评论（非楼主原评）
    val syntheticNodes = mutableListOf<SongComment>()
    for (r in allReplies) {
        val hops = r.replyChainHops
        if (hops.isNotEmpty()) {
            // 从最接近楼主的祖先往下检查
            var parentIdCursor: String? = rootComment.id
            var parentUserCursor: String? = rootComment.userName
            for (hopIdx in hops.indices.reversed()) {
                val hop = hops[hopIdx]
                val hopUser = hop.userName.trim()
                val hopCleanContent = KugouCommentEmojiParser.formatContent(hop.content)
                val hopNorm = normalizeForQuoteMatch(hopCleanContent)
                if (hopNorm.isBlank() || hopNorm == rootNormContent || hopUser.lowercase() == rootUserLower) {
                    continue
                }
                val matchedExisting = knownByNormContent[hopNorm]
                    ?: knownByNormContent.entries.firstOrNull { (k, _) ->
                        k.length >= 6 && hopNorm.length >= 6 && (k.contains(hopNorm) || hopNorm.contains(k))
                    }?.value
                if (matchedExisting != null) {
                    parentIdCursor = matchedExisting.id
                    parentUserCursor = matchedExisting.userName
                } else {
                    val synId = "syn_${hopUser}_${hopNorm.hashCode()}"
                    val existingSyn = knownById[synId]
                    if (existingSyn == null) {
                        val synComment = SongComment(
                            id = synId,
                            userId = "",
                            userName = hopUser,
                            userAvatar = "",
                            content = hopCleanContent,
                            timeFormatted = r.timeFormatted,
                            parentId = parentIdCursor,
                            rootId = rootComment.id,
                            replyTargetCommentId = parentIdCursor?.takeIf { it != rootComment.id },
                            replyUserName = parentUserCursor?.takeIf { it != rootComment.userName }
                        )
                        knownById[synId] = synComment
                        knownByNormContent[hopNorm] = synComment
                        syntheticNodes.add(synComment)
                        parentIdCursor = synId
                        parentUserCursor = hopUser
                    } else {
                        parentIdCursor = existingSyn.id
                        parentUserCursor = existingSyn.userName
                    }
                }
            }
        }
    }

    val combinedList = if (syntheticNodes.isEmpty()) allReplies else (syntheticNodes + allReplies)
    val nodeMap = LinkedHashMap<String, MutableCommentTreeNode>(combinedList.size)
    combinedList.forEach { c ->
        nodeMap[c.id] = MutableCommentTreeNode(c)
    }

    val level1Roots = mutableListOf<MutableCommentTreeNode>()

    // 2. 为每条回复匹配其直接父评论节点
    for ((idx, item) in combinedList.withIndex()) {
        val currentNode = nodeMap[item.id] ?: continue
        var matchedParentNode: MutableCommentTreeNode? = null

        // (a) 优先按显式 replyTargetCommentId / parentId 匹配
        val targetId = item.replyTargetCommentId ?: item.parentId?.takeIf { it != rootComment.id }
        if (!targetId.isNullOrBlank() && targetId != item.id) {
            matchedParentNode = nodeMap[targetId]
        }

        // (b) 按 //@用户名:被回复原文 的内容精准/模糊匹配父评论（解决用户改名如 Stephanie月月 -> 齐天洋洋 后的父子关联）
        if (matchedParentNode == null && !item.replyQuotedContent.isNullOrBlank()) {
            val quoteNorm = normalizeForQuoteMatch(item.replyQuotedContent)
            if (quoteNorm.isNotBlank() && quoteNorm != rootNormContent) {
                matchedParentNode = combinedList
                    .firstOrNull { candidate ->
                        candidate.id != item.id && run {
                            val candNorm = normalizeForQuoteMatch(candidate.content)
                            candNorm.isNotBlank() && (
                                candNorm == quoteNorm ||
                                    (candNorm.length >= 6 && quoteNorm.length >= 6 &&
                                        (candNorm.contains(quoteNorm) || quoteNorm.contains(candNorm)))
                                )
                        }
                    }
                    ?.let { nodeMap[it.id] }
            }
        }

        // (c) 按被回复人昵称 replyUserName 匹配（非楼主本人时）
        if (matchedParentNode == null && !item.replyUserName.isNullOrBlank()) {
            val replyUserLower = item.replyUserName.trim().lowercase()
            if (replyUserLower != rootUserLower) {
                val precedingMatch = combinedList.subList(0, idx).lastOrNull {
                    it.id != item.id && it.userName.trim().lowercase() == replyUserLower
                } ?: combinedList.firstOrNull {
                    it.id != item.id && it.userName.trim().lowercase() == replyUserLower
                }
                if (precedingMatch != null) {
                    matchedParentNode = nodeMap[precedingMatch.id]
                }
            }
        }

        // 防止环路
        if (matchedParentNode != null && matchedParentNode !== currentNode) {
            var cursor: MutableCommentTreeNode? = matchedParentNode
            var createsCycle = false
            var guard = 0
            while (cursor != null && guard < 30) {
                if (cursor === currentNode) {
                    createsCycle = true
                    break
                }
                cursor = nodeMap.values.firstOrNull { it.children.contains(cursor) }
                guard++
            }
            if (!createsCycle) {
                matchedParentNode.children.add(currentNode)
            } else {
                level1Roots.add(currentNode)
            }
        } else {
            level1Roots.add(currentNode)
        }
    }

    // 3. 递归遍历树，生成 1~4 级可视化节点列表，并将 > 4 级的深层回复收纳为「查看对话」入口
    val visualItems = mutableListOf<FloorTreeVisualItem>()

    fun collectAllDescendantsWithLevel(
        node: MutableCommentTreeNode,
        level: Int,
        out: MutableList<Pair<SongComment, Int>>
    ) {
        out.add(node.comment to level)
        node.children.forEach { child ->
            collectAllDescendantsWithLevel(child, level + 1, out)
        }
    }

    fun traverseCluster(
        node: MutableCommentTreeNode,
        level: Int,
        parentComment: SongComment?,
        isFirstSibling: Boolean,
        isLastSibling: Boolean,
        activeParentTrunks: List<Boolean>,
        topLevelClusterRoot: MutableCommentTreeNode
    ) {
        val cleanedComment = if (level == 1) {
            // 一级评论直接回复楼主，不显示多余的 "回复 @楼主"
            node.comment.copy(replyUserName = null)
        } else {
            val targetName = node.comment.replyUserName?.takeIf { it.isNotBlank() } ?: parentComment?.userName
            node.comment.copy(replyUserName = targetName)
        }

        val hasChildren = node.children.isNotEmpty()
        // 构建当前分支完整对话链（供超过4级或任意多级子集群点击“查看对话”时使用）
        val fullClusterDialogue = mutableListOf<Pair<SongComment, Int>>()
        collectAllDescendantsWithLevel(topLevelClusterRoot, 1, fullClusterDialogue)
        val conversationChain = if (fullClusterDialogue.size > 1) {
            CommentConversationChain(
                id = "conv_${topLevelClusterRoot.comment.id}_${node.comment.id}",
                title = "${topLevelClusterRoot.comment.userName} 的评论对话 (${fullClusterDialogue.size})",
                rootClusterComment = topLevelClusterRoot.comment,
                dialogueList = fullClusterDialogue
            )
        } else null

        visualItems.add(
            FloorTreeVisualItem.ClusterCommentRow(
                key = "tree_cmt_${node.comment.id}_lv$level",
                comment = cleanedComment,
                level = level,
                replyToUser = if (level >= 2) cleanedComment.replyUserName else null,
                isFirstInSubBranch = isFirstSibling,
                isLastSibling = isLastSibling,
                hasChildBranchBelow = hasChildren,
                activeParentTrunks = activeParentTrunks,
                directConversationChain = conversationChain
            )
        )

        if (!hasChildren) return

        val nextParentTrunks = activeParentTrunks + (!isLastSibling)

        if (level < 4) {
            // 1~3 级评论的子评论正常延伸出下一级子分支线（最多显示到 4 级）
            node.children.forEachIndexed { childIdx, childNode ->
                traverseCluster(
                    node = childNode,
                    level = level + 1,
                    parentComment = node.comment,
                    isFirstSibling = childIdx == 0,
                    isLastSibling = childIdx == node.children.lastIndex,
                    activeParentTrunks = nextParentTrunks,
                    topLevelClusterRoot = topLevelClusterRoot
                )
            }
        } else {
            // 已到达第 4 级评论，若还有第 5 级及更深层回复，不再缩进第 5 条分支线，而是显示「查看对话」入口标识
            val deepDescendants = mutableListOf<Pair<SongComment, Int>>()
            node.children.forEach { child ->
                collectAllDescendantsWithLevel(child, level + 1, deepDescendants)
            }
            if (deepDescendants.isNotEmpty() && conversationChain != null) {
                visualItems.add(
                    FloorTreeVisualItem.ViewDeepDialogueBadgeRow(
                        key = "tree_deep_dialogue_${node.comment.id}",
                        level = 4,
                        hiddenDeepReplyCount = deepDescendants.size,
                        activeParentTrunks = nextParentTrunks,
                        conversationChain = conversationChain
                    )
                )
            }
        }
    }

    level1Roots.forEachIndexed { idx, rootNode ->
        traverseCluster(
            node = rootNode,
            level = 1,
            parentComment = null,
            isFirstSibling = idx == 0,
            isLastSibling = idx == level1Roots.lastIndex,
            activeParentTrunks = emptyList(),
            topLevelClusterRoot = rootNode
        )
    }

    return FloorClusterTreeBuildResult(
        visualItems = visualItems,
        totalReplyCountInTree = combinedList.size
    )
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
    onSyncReplyCount: (commentId: String, actualCount: Int, latestReplies: List<SongComment>?) -> Unit = { _, _, _ -> },
    onPostComment: (content: String, replyToCommentId: String?, replyToUserName: String?, onResult: (Boolean, String) -> Unit) -> Unit = { _, _, _, cb -> cb(true, "评论已发布") },
    onLikeComment: (commentId: String) -> Unit = {}
) {
    // 独立楼中楼详情页目标根评论 ID（仅以 ID 驱动页面开关，杜绝因 replies 异步刷新导致切换动画重复触发卡顿）
    var activeFloorCommentId by remember { mutableStateOf<String?>(null) }
    var lastOpenedFloorComment by remember { mutableStateOf<SongComment?>(null) }
    // 超过四级评论点击「查看对话」进入的完整对话新页面
    var activeConversationChain by remember { mutableStateOf<CommentConversationChain?>(null) }
    var previewImageUrl by remember { mutableStateOf<String?>(null) }

    val activeFloorRootComment = remember(comments, activeFloorCommentId) {
        activeFloorCommentId?.let { id -> comments.find { it.id == id } }
    }
    LaunchedEffect(activeFloorRootComment) {
        if (activeFloorRootComment != null) {
            lastOpenedFloorComment = activeFloorRootComment
        }
    }

    BackHandler {
        when {
            previewImageUrl != null -> previewImageUrl = null
            activeConversationChain != null -> activeConversationChain = null
            activeFloorCommentId != null -> activeFloorCommentId = null
            else -> onBack()
        }
    }

    // 评论大图全屏预览弹窗
    previewImageUrl?.let { imgUrl ->
        CommentImagePreviewDialog(
            imageUrl = imgUrl,
            onDismiss = { previewImageUrl = null }
        )
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
    // 持久保持主评论列表滚动状态，从「查看全部回复」返回时 0ms 恢复原位不重排
    val listState = rememberLazyListState()

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

    // 主评论层视差缩放与压暗动画（当打开「查看全部回复」时平滑后退，返回时丝滑复原）
    val mainLayerScale by animateFloatAsState(
        targetValue = if (activeFloorCommentId != null) 0.955f else 1f,
        animationSpec = tween(durationMillis = 280, easing = SmoothCommentSlideEasing),
        label = "mainCommentLayerScale"
    )
    val mainLayerAlpha by animateFloatAsState(
        targetValue = if (activeFloorCommentId != null) 0.55f else 1f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "mainCommentLayerAlpha"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        // 第一层：主评论列表页（常驻底层，保持滚动状态与流畅视差）
        ConceptStageBackground(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = mainLayerScale
                    scaleY = mainLayerScale
                    alpha = mainLayerAlpha
                }
        ) {
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
                                val displayTotal = maxOf(totalCount, comments.size)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (song != null) {
                                            if (displayTotal > 0) "${song.title} (${formatCommentLikeCount(displayTotal)})" else song.title
                                        } else {
                                            if (displayTotal > 0) "歌曲评论 (${formatCommentLikeCount(displayTotal)})" else "歌曲评论"
                                        },
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
                                onPostComment(content, replyTarget?.first, replyTarget?.second) { ok, msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    if (ok) {
                                        commentInputText = ""
                                        replyTarget = null
                                        showEmojiBar = false
                                    }
                                }
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
                                            text = "最新评论",
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
                                itemsIndexed(
                                    items = comments,
                                    key = { idx, c -> "cmt_${c.id}_$idx" },
                                    contentType = { _, _ -> "root_comment_card" }
                                ) { _, comment ->
                                    LaunchedEffect(comment.id, comment.replyCount, comment.replies.size) {
                                        if (comment.replyCount > 0 && comment.replies.isEmpty()) {
                                            onLoadReplies(comment.id)
                                        }
                                    }

                                    RootCommentCard(
                                        comment = comment,
                                        isDark = isDark,
                                        onOpenAllRepliesFloor = {
                                            lastOpenedFloorComment = comment
                                            activeFloorCommentId = comment.id
                                            if (comment.replies.isEmpty()) {
                                                onLoadReplies(comment.id)
                                            }
                                        },
                                        onReplyRoot = {
                                            replyTarget = comment.id to comment.userName
                                        },
                                        onReplySub = { replyId, replyUserName ->
                                            replyTarget = replyId to replyUserName
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

        // 第二层：独立楼中楼全部回复详情页（1~4 级树状集群分支视图，丝滑右侧滑入/滑出过渡，不因数据异步更新而重播动画）
        AnimatedVisibility(
            visible = activeFloorCommentId != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 280, easing = SmoothCommentSlideEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 240, easing = SmoothCommentSlideEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing))
        ) {
            val floorComment = activeFloorRootComment ?: lastOpenedFloorComment
            if (floorComment != null) {
                CommentFloorDetailScreen(
                    song = song,
                    rootComment = floorComment,
                    currentUserAvatar = currentUserAvatar,
                    onBack = { activeFloorCommentId = null },
                    onOpenConversationChain = { chain -> activeConversationChain = chain },
                    onSyncReplyCount = { actualCnt, latestReplies ->
                        onSyncReplyCount(floorComment.id, actualCnt, latestReplies)
                    },
                    onPostReply = { content, replyToId, replyToUser, onResult ->
                        onPostComment(content, replyToId ?: floorComment.id, replyToUser ?: floorComment.userName, onResult)
                    },
                    onLikeComment = onLikeComment,
                    onPreviewImage = { previewImageUrl = it }
                )
            }
        }

        // 第三层：超过四级评论点击「查看对话」进入的完整多级评论对话界面（采用常见平铺评论设计，无分支线）
        AnimatedVisibility(
            visible = activeConversationChain != null,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(20f),
            enter = slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 270, easing = SmoothCommentSlideEasing)
            ) + fadeIn(animationSpec = tween(durationMillis = 190, easing = FastOutSlowInEasing)),
            exit = slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = 230, easing = SmoothCommentSlideEasing)
            ) + fadeOut(animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing))
        ) {
            val chain = activeConversationChain
            if (chain != null) {
                CommentConversationDetailScreen(
                    song = song,
                    conversation = chain,
                    currentUserAvatar = currentUserAvatar,
                    onBack = { activeConversationChain = null },
                    onPostReply = { content, replyToId, replyToUser, onResult ->
                        onPostComment(content, replyToId, replyToUser, onResult)
                    },
                    onLikeComment = onLikeComment,
                    onPreviewImage = { previewImageUrl = it }
                )
            }
        }
    }
}

/**
 * 独立楼中楼评论详情页 (点击“查看全部 X 条回复”后进入的独立页面)：
 * 1. 采用 1~4 级树状集群分支线设计（一级主分支线 -> 二级子分支线 -> 三级分支 -> 四级分支）；
 * 2. 超过四级的深层回复显示「查看对话」标识，点击进入平铺对话页查看完整上下文；
 * 3. 自动分页拉取全部可用回复，并在加载完毕后将顶部/标题/底部数量与界面内实际评论数量严格对齐。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentFloorDetailScreen(
    song: Song?,
    rootComment: SongComment,
    currentUserAvatar: String = "",
    onBack: () -> Unit,
    onOpenConversationChain: (CommentConversationChain) -> Unit = {},
    onSyncReplyCount: (actualCount: Int, latestReplies: List<SongComment>) -> Unit = { _, _ -> },
    onPostReply: (content: String, replyToCommentId: String?, replyToUserName: String?, onResult: (Boolean, String) -> Unit) -> Unit,
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
        mutableStateOf(rootComment.replyCount > rootComment.replies.size || rootComment.replies.isEmpty())
    }
    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }

    LaunchedEffect(rootComment.replies) {
        if (rootComment.replies.isNotEmpty()) {
            val merged = (floorReplies + rootComment.replies).distinctBy { it.id }
            if (merged.size != floorReplies.size) {
                floorReplies = merged
            }
        }
    }

    fun fetchFloorReplies(page: Int) {
        if (song == null || floorLoading) return
        floorLoading = true
        scope.launch {
            val fetched = runCatching {
                VipManager.music.songCommentReplies(
                    song = song,
                    commentId = rootComment.id,
                    page = page,
                    pageSize = 30,
                    specialChildId = rootComment.specialChildId
                )
            }.getOrDefault(emptyList())
            val prevSize = floorReplies.size
            val merged = (if (page == 1) rootComment.replies + fetched else floorReplies + fetched).distinctBy { it.id }
            val addedCount = merged.size - prevSize
            floorReplies = merged
            floorPage = page
            val moreAvailable = fetched.isNotEmpty() && (addedCount > 0 || page == 1) &&
                merged.size < maxOf(rootComment.replyCount, merged.size + (if (fetched.size >= 15) 1 else 0))
            floorHasMore = moreAvailable
            floorLoading = false
        }
    }

    LaunchedEffect(rootComment.id) {
        fetchFloorReplies(1)
    }

    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }
    LaunchedEffect(shouldLoadMore, floorHasMore, floorLoading, floorReplies.size) {
        if (shouldLoadMore && floorHasMore && !floorLoading && floorReplies.isNotEmpty()) {
            fetchFloorReplies(floorPage + 1)
        }
    }

    // 构建 1~4 级树状分支集群
    val treeResult = remember(rootComment.id, rootComment.content, rootComment.userName, floorReplies) {
        buildFloorClusterTree(rootComment, floorReplies)
    }

    // 当服务端全部可用分页加载完毕后，将界面显示的全部评论数与实际加载到的树节点数严格同步，消除标题与列表条数不一致
    val actualLoadedCount = maxOf(floorReplies.size, treeResult.totalReplyCountInTree)
    val effectiveTotalCount = if (!floorHasMore && !floorLoading && actualLoadedCount > 0) {
        actualLoadedCount
    } else {
        maxOf(rootComment.replyCount, actualLoadedCount)
    }

    LaunchedEffect(floorHasMore, floorLoading, actualLoadedCount) {
        if (!floorHasMore && !floorLoading && actualLoadedCount > 0 && actualLoadedCount != rootComment.replyCount) {
            onSyncReplyCount(actualLoadedCount, floorReplies)
        }
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
                        Column {
                            Text(
                                text = "评论回复详情 ($effectiveTotalCount)",
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
                            val targetCommentId = replyTarget?.first ?: rootComment.id
                            val targetUserName = replyTarget?.second ?: rootComment.userName
                            onPostReply(content, targetCommentId, targetUserName) { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (ok) {
                                    inputText = ""
                                    replyTarget = null
                                    showEmojiBar = false
                                }
                            }
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
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // 1. 楼主原评置顶卡片
                item(key = "floor_root_${rootComment.id}") {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
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

                // 2. 全部回复标题栏
                item(key = "floor_section_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "全部回复 ($effectiveTotalCount)",
                            color = primaryText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "点击任意评论可挂载子分支回复",
                            color = subText.copy(alpha = 0.65f),
                            fontSize = 11.sp
                        )
                    }
                }

                // 3. 1~4 级多级集群分支树渲染
                itemsIndexed(
                    items = treeResult.visualItems,
                    key = { idx, item -> "${item.key}_$idx" },
                    contentType = { _, item ->
                        when (item) {
                            is FloorTreeVisualItem.ClusterCommentRow -> "cluster_comment_row"
                            is FloorTreeVisualItem.ViewDeepDialogueBadgeRow -> "deep_dialogue_badge_row"
                        }
                    }
                ) { _, visualItem ->
                    when (visualItem) {
                        is FloorTreeVisualItem.ClusterCommentRow -> {
                            MultiLevelClusterReplyNode(
                                item = visualItem,
                                onReplyClick = {
                                    replyTarget = visualItem.comment.id to visualItem.comment.userName
                                },
                                onLikeClick = { onLikeComment(visualItem.comment.id) },
                                onOpenConversation = visualItem.directConversationChain?.let { chain ->
                                    { onOpenConversationChain(chain) }
                                },
                                onPreviewImage = onPreviewImage
                            )
                        }
                        is FloorTreeVisualItem.ViewDeepDialogueBadgeRow -> {
                            MultiLevelDeepDialogueEntryNode(
                                item = visualItem,
                                onClick = { onOpenConversationChain(visualItem.conversationChain) }
                            )
                        }
                    }
                }

                item(key = "floor_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (floorLoading) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = ConceptPrimary,
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                                Text("正在同步楼层回复...", color = subText.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        } else if (treeResult.visualItems.isEmpty()) {
                            Text("暂无更多回复", color = subText.copy(alpha = 0.5f), fontSize = 12.sp)
                        } else if (!floorHasMore) {
                            Text(
                                text = "— 已显示全部 $effectiveTotalCount 条回复 —",
                                color = subText.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 超过四级评论点击「查看对话」进入的完整多级评论对话界面 (CommentConversationDetailScreen)：
 * 不再使用树状分支线显示，改为清爽的常见平铺评论列表设计，完整展示该集群下的全部多级互动对话。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentConversationDetailScreen(
    song: Song?,
    conversation: CommentConversationChain,
    currentUserAvatar: String,
    onBack: () -> Unit,
    onPostReply: (content: String, replyToCommentId: String?, replyToUserName: String?, onResult: (Boolean, String) -> Unit) -> Unit,
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

    var replyTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var inputText by remember { mutableStateOf("") }
    var showEmojiBar by remember { mutableStateOf(false) }

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
                                text = "查看对话 (${conversation.dialogueList.size})",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    color = primaryText,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = conversation.title,
                                color = subText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "返回楼层详情",
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
                    placeholderOverride = if (replyTarget != null) {
                        "回复 @${replyTarget?.second}..."
                    } else {
                        "回复对话发起人 @${conversation.rootClusterComment.userName}..."
                    },
                    onSend = {
                        val content = inputText.trim()
                        if (content.isNotBlank()) {
                            onPostReply(
                                content,
                                replyTarget?.first ?: conversation.rootClusterComment.id,
                                replyTarget?.second ?: conversation.rootClusterComment.userName
                            ) { ok, msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                if (ok) {
                                    inputText = ""
                                    replyTarget = null
                                    showEmojiBar = false
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(
                    items = conversation.dialogueList,
                    key = { idx, (cmt, _) -> "conv_item_${cmt.id}_$idx" }
                ) { idx, (cmt, _) ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = cardColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .softCardClickable(shape = RoundedCornerShape(16.dp)) {
                                replyTarget = cmt.id to cmt.userName
                            }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (cmt.userAvatar.isNotBlank()) {
                                    AsyncImage(
                                        model = cmt.userAvatar,
                                        contentDescription = null,
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
                                                contentDescription = null,
                                                tint = subText,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = cmt.userName,
                                            color = primaryText,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (idx == 0) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = ConceptPrimary.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = "层主",
                                                    color = ConceptPrimary,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = cmt.timeFormatted,
                                            color = subText.copy(alpha = 0.75f),
                                            fontSize = 11.sp
                                        )
                                        if (cmt.ipLocation.isNotBlank()) {
                                            Text(
                                                text = "IP属地：${cmt.ipLocation}",
                                                color = subText.copy(alpha = 0.7f),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .softClickable { onLikeComment(cmt.id) }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (cmt.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "点赞",
                                        tint = if (cmt.isLiked) Color(0xFFFF5252) else subText.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    if (cmt.likeCount > 0) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = formatCommentLikeCount(cmt.likeCount),
                                            color = if (cmt.isLiked) Color(0xFFFF5252) else subText,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val cleanBody = KugouCommentEmojiParser.formatContent(cmt.content)
                            val displayAnnotated = buildAnnotatedString {
                                if (idx > 0 && !cmt.replyUserName.isNullOrBlank()) {
                                    append("回复 ")
                                    withStyle(SpanStyle(color = ConceptPrimary, fontWeight = FontWeight.SemiBold)) {
                                        append("@${cmt.replyUserName}")
                                    }
                                    append("：")
                                }
                                append(cleanBody)
                            }
                            Text(
                                text = displayAnnotated,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = primaryText.copy(alpha = 0.94f),
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp
                                ),
                                modifier = Modifier.padding(start = 46.dp)
                            )

                            if (cmt.images.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                CommentImagesGrid(
                                    images = cmt.images,
                                    onImageClick = onPreviewImage,
                                    modifier = Modifier.padding(start = 46.dp)
                                )
                            }
                        }
                    }
                }

                item(key = "conv_footer") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "— 已展示完整对话 (${conversation.dialogueList.size} 条) —",
                            color = subText.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * 1~4 级多级集群分支评论节点 (MultiLevelClusterReplyNode)：
 * - Level 1 (一级评论): 连接在最左侧的一级主分支线上；
 * - Level 2 (二级评论): 从一级评论分支延伸出二级主分支线；
 * - Level 3 (三级评论): 从二级评论分支延伸出三级子分支线；
 * - Level 4 (四级评论): 从三级评论分支延伸出四级子分支线。
 */
@Composable
private fun MultiLevelClusterReplyNode(
    item: FloorTreeVisualItem.ClusterCommentRow,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onOpenConversation: (() -> Unit)? = null,
    onPreviewImage: (String) -> Unit
) {
    val reply = item.comment
    val level = item.level.coerceIn(1, 4)
    val primaryText = MaterialTheme.colorScheme.onSurface
    val subText = MaterialTheme.colorScheme.onSurfaceVariant
    val elevatedColor = conceptElevatedSurfaceColor()
    val cardBg = conceptFloatingCardColor()

    val levelColors = remember {
        listOf(
            ConceptPrimary,         // 一级主分支：主题青蓝
            Color(0xFF38BDF8),      // 二级子集群：天青蓝
            Color(0xFFA78BFA),      // 三级子集群：星云紫
            Color(0xFFFBBF24)       // 四级深层分支：流光金
        )
    }
    val branchAccent = levelColors[(level - 1).coerceIn(0, levelColors.lastIndex)]
    val stepIndentDp = 20.dp
    val canvasWidth = 30.dp + stepIndentDp * (level - 1)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.Top
    ) {
        Canvas(
            modifier = Modifier
                .width(canvasWidth)
                .fillMaxHeight()
        ) {
            val strokeWidthPx = 1.6.dp.toPx()
            val baseStartX = 8.dp.toPx()
            val stepPx = stepIndentDp.toPx()
            val curveRadius = 8.dp.toPx()
            val branchY = 20.dp.toPx()

            // 1. 绘制外层各祖先集群 (1 .. level-1) 仍在向下延续的垂直主分支线
            for (d in 1 until level) {
                val keepDrawing = item.activeParentTrunks.getOrNull(d - 1) ?: false
                if (keepDrawing) {
                    val parentX = baseStartX + (d - 1) * stepPx
                    val parentColor = levelColors[(d - 1).coerceIn(0, levelColors.lastIndex)].copy(alpha = 0.30f)
                    drawLine(
                        color = parentColor,
                        start = Offset(parentX, 0f),
                        end = Offset(parentX, size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 2. 绘制当前层级 (level) 的分支线与贝塞尔弯钩
            val curTrunkX = baseStartX + (level - 1) * stepPx
            val dotX = size.width - 5.dp.toPx()
            val spineColor = branchAccent.copy(alpha = 0.42f)
            val continueOwnSpine = !item.isLastSibling

            drawLine(
                color = spineColor,
                start = Offset(curTrunkX, 0f),
                end = Offset(curTrunkX, if (continueOwnSpine) size.height else branchY - curveRadius),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            if (level == 1 && item.isFirstInSubBranch) {
                drawCircle(
                    color = branchAccent.copy(alpha = 0.6f),
                    radius = 2.4.dp.toPx(),
                    center = Offset(curTrunkX, 2.dp.toPx())
                )
            }

            val branchPath = Path().apply {
                moveTo(curTrunkX, branchY - curveRadius)
                quadraticBezierTo(curTrunkX, branchY, curTrunkX + curveRadius, branchY)
                lineTo(dotX, branchY)
            }
            drawPath(
                path = branchPath,
                brush = Brush.horizontalGradient(
                    colors = listOf(spineColor, branchAccent.copy(alpha = 0.82f))
                ),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )

            // 3. 分支发光节点
            drawCircle(
                color = branchAccent.copy(alpha = 0.22f),
                radius = 5.2.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = branchAccent,
                radius = 3.2.dp.toPx(),
                center = Offset(dotX, branchY)
            )
            drawCircle(
                color = cardBg,
                radius = 1.4.dp.toPx(),
                center = Offset(dotX, branchY)
            )

            // 4. 若当前节点下方挂载了子集群分支（level + 1），从当前分支点下方引出子集群起始竖线连接到底部
            if (item.hasChildBranchBelow && level < 4) {
                val childTrunkX = baseStartX + level * stepPx
                val childColor = levelColors[level.coerceIn(0, levelColors.lastIndex)].copy(alpha = 0.40f)
                drawLine(
                    color = childColor,
                    start = Offset(childTrunkX, branchY + 4.dp.toPx()),
                    end = Offset(childTrunkX, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = elevatedColor,
            modifier = Modifier
                .weight(1f)
                .padding(top = 3.dp, bottom = 5.dp)
                .border(
                    width = 0.6.dp,
                    color = branchAccent.copy(alpha = if (level == 1) 0.12f else 0.22f),
                    shape = RoundedCornerShape(14.dp)
                )
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
                            color = branchAccent,
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (reply.ipLocation.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "IP:${reply.ipLocation}",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = subText.copy(alpha = 0.7f),
                                fontSize = 9.5.sp
                            ),
                            maxLines = 1
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = reply.timeFormatted,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = subText.copy(alpha = 0.62f),
                            fontSize = 9.5.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    val replyLikeScale by animateFloatAsState(
                        targetValue = if (reply.isLiked) 1.18f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "replyLikeScale"
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
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer {
                                    scaleX = replyLikeScale
                                    scaleY = replyLikeScale
                                }
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

                val cleanBodyText = KugouCommentEmojiParser.formatContent(reply.content)
                val annotatedContent = buildAnnotatedString {
                    if (level >= 2 && !item.replyToUser.isNullOrBlank()) {
                        append("回复 ")
                        withStyle(SpanStyle(color = branchAccent, fontWeight = FontWeight.SemiBold)) {
                            append("@${item.replyToUser}")
                        }
                        append("：")
                    }
                    append(cleanBodyText)
                }

                Text(
                    text = annotatedContent,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = primaryText.copy(alpha = 0.93f),
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

                // 四级评论若有对话上下文，支持一键「查看对话」
                if (level == 4 && onOpenConversation != null && !item.hasChildBranchBelow) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .softClickable(onClick = onOpenConversation)
                            .padding(vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forum,
                            contentDescription = null,
                            tint = branchAccent,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "查看对话 >",
                            color = branchAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 超过四级评论后的「查看对话」分支入口节点 (MultiLevelDeepDialogueEntryNode)：
 * 当评论回复深度超过 4 级时，为保证手机小屏幕阅读体验，不再继续向右缩进分支线，
 * 而是展示此标识按钮，点击后进入平铺式完整多级对话界面。
 */
@Composable
private fun MultiLevelDeepDialogueEntryNode(
    item: FloorTreeVisualItem.ViewDeepDialogueBadgeRow,
    onClick: () -> Unit
) {
    val level = item.level.coerceIn(1, 4)
    val stepIndentDp = 20.dp
    val canvasWidth = 30.dp + stepIndentDp * (level - 1)
    val levelColors = remember {
        listOf(
            ConceptPrimary,
            Color(0xFF38BDF8),
            Color(0xFFA78BFA),
            Color(0xFFFBBF24)
        )
    }
    val badgeAccent = Color(0xFFFBBF24)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(canvasWidth)
                .height(34.dp)
        ) {
            val strokeWidthPx = 1.6.dp.toPx()
            val baseStartX = 8.dp.toPx()
            val stepPx = stepIndentDp.toPx()
            val curveRadius = 8.dp.toPx()
            val branchY = 17.dp.toPx()

            for (d in 1 until level) {
                val keepDrawing = item.activeParentTrunks.getOrNull(d - 1) ?: false
                if (keepDrawing) {
                    val parentX = baseStartX + (d - 1) * stepPx
                    val parentColor = levelColors[(d - 1).coerceIn(0, levelColors.lastIndex)].copy(alpha = 0.30f)
                    drawLine(
                        color = parentColor,
                        start = Offset(parentX, 0f),
                        end = Offset(parentX, size.height),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )
                }
            }

            val curTrunkX = baseStartX + (level - 1) * stepPx
            val dotX = size.width - 5.dp.toPx()
            val branchPath = Path().apply {
                moveTo(curTrunkX, 0f)
                lineTo(curTrunkX, branchY - curveRadius)
                quadraticBezierTo(curTrunkX, branchY, curTrunkX + curveRadius, branchY)
                lineTo(dotX, branchY)
            }
            drawPath(
                path = branchPath,
                color = badgeAccent.copy(alpha = 0.55f),
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
            )
            drawCircle(
                color = badgeAccent,
                radius = 2.8.dp.toPx(),
                center = Offset(dotX, branchY)
            )
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = badgeAccent.copy(alpha = 0.14f),
            modifier = Modifier
                .padding(vertical = 3.dp)
                .border(0.8.dp, badgeAccent.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
                .softCardClickable(shape = RoundedCornerShape(12.dp), onClick = onClick)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Forum,
                    contentDescription = null,
                    tint = badgeAccent,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "查看对话 (还有 ${item.hiddenDeepReplyCount} 条深层回复)",
                    color = badgeAccent,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = badgeAccent,
                    modifier = Modifier.size(15.dp)
                )
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

            // 主评论卡片下方默认预览前 3 条回复（严格限定为直接回复层主本人的 1 级评论，绝不展示 2 级及往后的评论）
            if (comment.replies.isNotEmpty() || totalRepliesCount > 0) {
                val level1Replies = remember(comment.id, comment.userName, comment.content, comment.replies) {
                    comment.replies.filter { isLevel1DirectReply(rootComment = comment, reply = it) }
                }
                val visibleReplies = remember(level1Replies) { level1Replies.take(3) }
                val hasMoreReplies = totalRepliesCount > visibleReplies.size || comment.replies.size > visibleReplies.size

                if (visibleReplies.isNotEmpty() || hasMoreReplies) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Column(modifier = Modifier.padding(start = 18.dp)) {
                        visibleReplies.forEachIndexed { index, reply ->
                            val isFirst = index == 0
                            val isLast = index == visibleReplies.lastIndex
                            // 1 级评论直接回复层主，统一剥离 replyUserName，不加任何 "回复 @某某"
                            val displayReply = reply.copy(replyUserName = null)
                            TreeBranchReplyNode(
                                reply = displayReply,
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
                                totalCount = maxOf(totalRepliesCount, comment.replies.size),
                                onClick = onOpenAllRepliesFloor
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 严格判定一条子回复是否为直接回复层主本人的「1级评论」：
 * - 不包含多级 //@ 引用链 (replyChainHops 为空)
 * - replyTargetCommentId 与 parentId 未指向除层主以外的其他子评论
 * - replyUserName 为空或等于层主昵称（不指向其他层内评论者，如“回复 @某某”）
 * - 引用文本 replyQuotedContent 为空或仅对应层主原评
 */
internal fun isLevel1DirectReply(rootComment: SongComment, reply: SongComment): Boolean {
    if (reply.replyChainHops.isNotEmpty()) return false
    val rootId = rootComment.id.trim()
    val targetId = reply.replyTargetCommentId?.trim().orEmpty()
    if (targetId.isNotBlank() && targetId != "0" && targetId != rootId) return false
    val pId = reply.parentId?.trim().orEmpty()
    if (pId.isNotBlank() && pId != "0" && pId != rootId) return false

    val rootUserClean = rootComment.userName.trim().removePrefix("@")
    val replyUserClean = reply.replyUserName?.trim()?.removePrefix("@").orEmpty()
    if (replyUserClean.isNotBlank() && !replyUserClean.equals(rootUserClean, ignoreCase = true)) {
        return false
    }

    if (!reply.replyQuotedContent.isNullOrBlank()) {
        val rootNorm = rootComment.content.replace(Regex("""\s+"""), "")
        val quoteNorm = reply.replyQuotedContent.replace(Regex("""\s+"""), "")
        if (quoteNorm.isNotBlank() && rootNorm.isNotBlank() && !rootNorm.contains(quoteNorm) && !quoteNorm.contains(rootNorm)) {
            return false
        }
    }

    val contentTrimmed = reply.content.trim()
    val replyPrefixMatch = Regex("""^回复\s*@?([^:：]+)[:：]""").find(contentTrimmed)
    if (replyPrefixMatch != null) {
        val targetNameInText = replyPrefixMatch.groupValues[1].trim()
        if (targetNameInText.isNotBlank() && !targetNameInText.equals(rootUserClean, ignoreCase = true)) {
            return false
        }
    }

    return true
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

        val likeScale by animateFloatAsState(
            targetValue = if (comment.isLiked) 1.18f else 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "rootLikeScale"
        )
        val likeTint by animateColorAsState(
            targetValue = if (comment.isLiked) Color(0xFFFF5252) else subText.copy(alpha = 0.7f),
            animationSpec = tween(200),
            label = "rootLikeTint"
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .softClickable(onClick = onLikeClick)
                .padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = "点赞",
                tint = likeTint,
                modifier = Modifier
                    .size(16.dp)
                    .graphicsLayer {
                        scaleX = likeScale
                        scaleY = likeScale
                    }
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
 * 主评论卡片内嵌前 3 条一级回复分支节点 (TreeBranchReplyNode)
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

            drawLine(
                color = spineColor,
                start = Offset(startX, topY),
                end = Offset(startX, if (continueSpine) size.height + 4.dp.toPx() else branchY - curveRadius),
                strokeWidth = strokeWidthPx,
                cap = StrokeCap.Round
            )

            if (isFirstNode) {
                drawCircle(
                    color = branchAccent.copy(alpha = 0.55f),
                    radius = 2.4.dp.toPx(),
                    center = Offset(startX, 0f)
                )
            }

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

                    val replyLikeScale by animateFloatAsState(
                        targetValue = if (reply.isLiked) 1.18f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "replyLikeScale"
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
                            modifier = Modifier
                                .size(13.dp)
                                .graphicsLayer {
                                    scaleX = replyLikeScale
                                    scaleY = replyLikeScale
                                }
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
 * 分支连线末端节点：「查看全部 X 条回复 >」（点击平滑进入独立楼中楼页面）
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
 * 评论配图缩略图网格
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
