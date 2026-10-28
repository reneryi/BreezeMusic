package com.concept.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.concept.music.data.model.LyricData
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.max

/**
 * 深度重构：「高定歌词海报工坊 (Lyrics Poster Studio)」
 * 1. 黑胶典藏 (VINYL_ARCHIVE)：暗调录音室 + 大尺寸探出式同心纹黑胶唱盘 + 鎏金转速铭牌 + 左侧金句引线
 * 2. 先锋画报 (EDITORIAL_MAGAZINE)：全画幅沉浸封面背景 + 时尚杂志大刊头 + 巨型半透明引号 + 杂志社论排版
 * 3. 东方雅韵 (MINIMAL_GALLERY)：温润宣纸底纹 + 古典外粗内细双线画框 + 圆相封面 + 衬线宋刻体 + 朱砂方印「微风」
 * 4. 复古拍立得 (RETRO_POLAROID)：经典宽下巴相纸 + 大画幅方形取景窗 + 胶片橙色日期戳 ('26 10 27) + 手写感签注
 * 5. 赛博流光 (AURORA_GLASS)：未来感霓虹终端 + 四角机能取景框 + 首句荧光高亮胶囊 + 模拟播放时间轴进度条
 *
 * 超清无损优化：
 * - 导出分辨率升级至 2160 × 3120（2K+ 超采样无损 PNG，支持多行长歌词动态延展高度，拒绝模糊与挤压）；
 * - 封面图自动升级为 1000px+ 高清原图源并开启双线性抗锯齿采样；
 * - 彻底移除所有硬编码 `.take(N)` 截断，改用像素宽度自适应字号缩放与多行智能折行算法，确保歌名、歌手、专辑名与底栏铭牌完整呈现；
 * - 自动从缓存与酷狗接口解析真实专辑名称，仅当歌曲确无所属专辑时才隐藏专辑书名号。
 */
enum class PosterTemplateStyle(
    val label: String,
    val subtitle: String,
    val tag: String,
    val bgColors: IntArray,
    val cardColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentColor: Int
) {
    VINYL_ARCHIVE(
        label = "黑胶典藏",
        subtitle = "唱盘探出·录音室鎏金",
        tag = "SIDE A · 45 RPM",
        bgColors = intArrayOf(0xFF070A12.toInt(), 0xFF121C2D.toInt(), 0xFF090D16.toInt()),
        cardColor = 0xF2101726.toInt(),
        primaryTextColor = 0xFFF8FAFC.toInt(),
        secondaryTextColor = 0xFF94A3B8.toInt(),
        accentColor = 0xFFE5A93B.toInt()
    ),
    EDITORIAL_MAGAZINE(
        label = "先锋画报",
        subtitle = "全画幅封面·杂志大刊头",
        tag = "CONCEPT // ISSUE",
        bgColors = intArrayOf(0xFF180E24.toInt(), 0xFF2D163E.toInt(), 0xFF0D0814.toInt()),
        cardColor = 0xEA161022.toInt(),
        primaryTextColor = 0xFFFFFFFF.toInt(),
        secondaryTextColor = 0xFFE2E8F0.toInt(),
        accentColor = 0xFFFF4D8D.toInt()
    ),
    MINIMAL_GALLERY(
        label = "东方雅韵",
        subtitle = "宣纸双线框·朱砂红印",
        tag = "BREEZE GALLERY",
        bgColors = intArrayOf(0xFFE8E2D5.toInt(), 0xFFDED6C6.toInt(), 0xFFECE7DC.toInt()),
        cardColor = 0xFFF6F2E9.toInt(),
        primaryTextColor = 0xFF1C1917.toInt(),
        secondaryTextColor = 0xFF57534E.toInt(),
        accentColor = 0xFFC83E31.toInt()
    ),
    RETRO_POLAROID(
        label = "复古拍立得",
        subtitle = "胶片取景窗·宽幅相纸",
        tag = "INSTANT FILM 400",
        bgColors = intArrayOf(0xFF1A1614.toInt(), 0xFF2B231E.toInt(), 0xFF14110F.toInt()),
        cardColor = 0xFFFAF6EE.toInt(),
        primaryTextColor = 0xFF1F1C18.toInt(),
        secondaryTextColor = 0xFF6B635B.toInt(),
        accentColor = 0xFFE06D1A.toInt()
    ),
    AURORA_GLASS(
        label = "赛博流光",
        subtitle = "霓虹机能框·音轨时间轴",
        tag = "CYBER AUDIO // 24BIT",
        bgColors = intArrayOf(0xFF040D14.toInt(), 0xFF0A2233.toInt(), 0xFF091326.toInt()),
        cardColor = 0xE60B1926.toInt(),
        primaryTextColor = 0xFFF0FDFA.toInt(),
        secondaryTextColor = 0xFF99F6E4.toInt(),
        accentColor = 0xFF00F5D4.toInt()
    )
}

internal const val POSTER_EXPORT_SCALE = 2f
internal const val POSTER_BASE_LOGICAL_WIDTH = 1080
internal const val POSTER_BASE_LOGICAL_HEIGHT = 1560

private val KUGOU_THUMB_SIZE_REGEX = Regex("""/(100|120|150|200|240|300|400|480)/""")

/**
 * 将酷狗缩略图 URL 升级为高分辨率大图 URL（1000x1000），保障 2K 海报导出时封面纤毫毕现。
 */
internal fun upgradePosterCoverUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    if (trimmed.isEmpty()) return ""
    var upgraded = trimmed.replace("{size}", "1000")
    if (KUGOU_THUMB_SIZE_REGEX.containsMatchIn(upgraded)) {
        upgraded = KUGOU_THUMB_SIZE_REGEX.replace(upgraded, "/1000/")
    }
    return upgraded
}

/**
 * 提取真实有效的专辑名（过滤 "未知专辑" 等占位词）。
 */
internal fun sanitizeAlbumName(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.equals("未知专辑", ignoreCase = true) ||
        trimmed.equals("单曲", ignoreCase = true) ||
        trimmed.equals("单曲发行", ignoreCase = true) ||
        trimmed == "0"
    ) {
        return ""
    }
    return trimmed
}

internal fun resolveCleanPosterAlbumName(song: Song, resolvedAlbumOverride: String = ""): String {
    val candidates = listOf(
        resolvedAlbumOverride,
        song.album,
        runCatching { VipManager.music.peekCachedAlbumName(song) }.getOrDefault("")
    )
    for (raw in candidates) {
        val cleaned = sanitizeAlbumName(raw)
        if (cleaned.isNotBlank()) return cleaned
    }
    return ""
}

/**
 * 构建「歌手 · 《专辑》」副标题文本：若歌曲确实无专辑，则只展示歌手或回退标签，绝不伪造或截断。
 */
internal fun formatPosterArtistAlbumSubtitle(
    artist: String,
    albumName: String,
    fallbackTagWhenNoAlbum: String = ""
): String {
    val safeArtist = artist.trim().ifBlank { "未知歌手" }
    val cleanAlbum = sanitizeAlbumName(albumName)
    return when {
        cleanAlbum.isNotBlank() -> "$safeArtist · 《$cleanAlbum》"
        fallbackTagWhenNoAlbum.isNotBlank() -> "$safeArtist · $fallbackTagWhenNoAlbum"
        else -> safeArtist
    }
}

@Composable
fun LyricPosterDialog(
    song: Song,
    lyricData: LyricData,
    currentLyricIndex: Int,
    onDismiss: () -> Unit
) {
    LyricPosterDialog(
        song = song,
        lines = lyricData.lines,
        currentIndex = currentLyricIndex,
        onDismiss = onDismiss
    )
}

@Composable
fun LyricPosterDialog(
    song: Song,
    lines: List<LyricLine>,
    currentIndex: Int,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 自动解析真实专辑名（如果当前 Song 对象上 album 为空）
    var resolvedAlbumName by remember(song.id, song.hash, song.albumId, song.album) {
        mutableStateOf(resolveCleanPosterAlbumName(song))
    }
    LaunchedEffect(song.id, song.hash, song.albumId, song.album) {
        if (resolvedAlbumName.isBlank()) {
            val meta = withContext(Dispatchers.IO) {
                runCatching { VipManager.music.resolveSongAlbumMeta(song) }.getOrNull()
            }
            val fetchedName = sanitizeAlbumName(meta?.first.orEmpty())
            if (fetchedName.isNotBlank()) {
                resolvedAlbumName = fetchedName
            }
        }
    }

    val effectiveSong = remember(song, resolvedAlbumName) {
        if (resolvedAlbumName.isNotBlank() && song.album != resolvedAlbumName) {
            song.copy(album = resolvedAlbumName)
        } else {
            song
        }
    }

    val initialSelected = remember(lines, currentIndex) {
        if (lines.isEmpty()) emptySet()
        else {
            val idx = currentIndex.coerceIn(0, lines.lastIndex)
            setOfNotNull(
                idx,
                (idx + 1).takeIf { it in lines.indices },
                (idx + 2).takeIf { it in lines.indices }
            )
        }
    }
    var selectedIndices by remember { mutableStateOf(initialSelected) }
    var template by remember { mutableStateOf(PosterTemplateStyle.VINYL_ARCHIVE) }
    var includeTranslation by remember { mutableStateOf(true) }
    var centerAlign by remember { mutableStateOf(false) }
    var showWaveformCode by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val selectedLines = remember(selectedIndices, lines) {
        selectedIndices.sorted().mapNotNull { lines.getOrNull(it) }
    }

    val dateStr = remember {
        runCatching { LocalDate.now().toString().replace("-", ".") }.getOrDefault("2026.10.27")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF5070A10))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                // 1. 顶部工作台标题栏（左标题自适应留白 + 右侧紧凑等高双胶囊操作按钮）
                val actionButtonsEnabled = selectedLines.isNotEmpty() && !saving
                val primaryBtnTextColor = if (template == PosterTemplateStyle.AURORA_GLASS || template == PosterTemplateStyle.VINYL_ARCHIVE) {
                    Color(0xFF0A0F18)
                } else {
                    Color.White
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier
                                .size(34.dp)
                                .softClickable(onClick = onDismiss)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "关闭",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "歌词海报",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = Color.White.copy(alpha = if (actionButtonsEnabled) 0.11f else 0.05f),
                            modifier = Modifier
                                .height(34.dp)
                                .border(
                                    width = 0.8.dp,
                                    color = Color.White.copy(alpha = if (actionButtonsEnabled) 0.18f else 0.08f),
                                    shape = RoundedCornerShape(11.dp)
                                )
                                .softCardClickable(
                                    enabled = actionButtonsEnabled,
                                    shape = RoundedCornerShape(11.dp)
                                ) {
                                    if (selectedLines.isEmpty() || saving) return@softCardClickable
                                    if (!com.concept.music.service.AppPermissionManager.isPermissionEnabled(
                                            com.concept.music.service.AppPermissionType.STORAGE_EXPORT
                                        )
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "已在权限管理中关闭「相册图片写入与导出」权限，请先在设置-权限管理中开启",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@softCardClickable
                                    }
                                    saving = true
                                    scope.launch {
                                        val uri = withContext(Dispatchers.IO) {
                                            runCatching {
                                                renderAndSavePosterToGallery(
                                                    context = context,
                                                    song = effectiveSong,
                                                    lines = selectedLines,
                                                    template = template,
                                                    includeTranslation = includeTranslation,
                                                    centerAlign = centerAlign,
                                                    showWaveform = showWaveformCode,
                                                    dateStr = dateStr
                                                )
                                            }.getOrNull()
                                        }
                                        saving = false
                                        if (uri != null) {
                                            runCatching {
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "image/png"
                                                    putExtra(Intent.EXTRA_STREAM, uri)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "分享歌词海报"))
                                            }
                                        } else {
                                            Toast.makeText(context, "生成分享海报失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Share,
                                    contentDescription = "分享",
                                    tint = Color.White.copy(alpha = if (actionButtonsEnabled) 0.95f else 0.4f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "分享",
                                    color = Color.White.copy(alpha = if (actionButtonsEnabled) 0.95f else 0.4f),
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = Color(template.accentColor).copy(alpha = if (actionButtonsEnabled) 1f else 0.45f),
                            modifier = Modifier
                                .height(34.dp)
                                .softCardClickable(
                                    enabled = actionButtonsEnabled,
                                    shape = RoundedCornerShape(11.dp)
                                ) {
                                    if (selectedLines.isEmpty() || saving) return@softCardClickable
                                    if (!com.concept.music.service.AppPermissionManager.isPermissionEnabled(
                                            com.concept.music.service.AppPermissionType.STORAGE_EXPORT
                                        )
                                    ) {
                                        Toast.makeText(
                                            context,
                                            "已在权限管理中关闭「相册图片写入与导出」权限，请先在设置-权限管理中开启",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@softCardClickable
                                    }
                                    saving = true
                                    scope.launch {
                                        val uri = withContext(Dispatchers.IO) {
                                            runCatching {
                                                renderAndSavePosterToGallery(
                                                    context = context,
                                                    song = effectiveSong,
                                                    lines = selectedLines,
                                                    template = template,
                                                    includeTranslation = includeTranslation,
                                                    centerAlign = centerAlign,
                                                    showWaveform = showWaveformCode,
                                                    dateStr = dateStr
                                                )
                                            }.getOrNull()
                                        }
                                        saving = false
                                        Toast.makeText(
                                            context,
                                            if (uri != null) "海报已保存至相册 (Pictures/ConceptMusic)" else "保存海报失败",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        if (uri != null) onDismiss()
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Download,
                                    contentDescription = "保存海报",
                                    tint = primaryBtnTextColor,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (saving) "渲染中..." else "保存海报",
                                    color = primaryBtnTextColor,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. 实时海报高定卡片预览区（五款截然不同的构图）
                PosterLivePreviewCard(
                    song = effectiveSong,
                    selectedLines = selectedLines,
                    template = template,
                    includeTranslation = includeTranslation,
                    centerAlign = centerAlign,
                    showWaveform = showWaveformCode,
                    dateStr = dateStr,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.55f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. 五大风格差异化模板选择器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PosterTemplateStyle.entries.forEach { style ->
                        val isSelected = template == style
                        val accent = Color(style.accentColor)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) accent.copy(alpha = 0.20f) else Color.White.copy(alpha = 0.06f),
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 1.3.dp else 0.5.dp,
                                    color = if (isSelected) accent else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                    template = style
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = style.label,
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.82f),
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = style.subtitle,
                                    color = if (isSelected) accent else Color.White.copy(alpha = 0.45f),
                                    fontSize = 9.5.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 排版微调开关条
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "勾选歌词金句 (${selectedIndices.size}/6)",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosterMiniToggleChip(
                            label = if (centerAlign) "居中对齐" else "居左对齐",
                            active = centerAlign,
                            onClick = { centerAlign = !centerAlign }
                        )
                        PosterMiniToggleChip(
                            label = "双语译文",
                            active = includeTranslation,
                            onClick = { includeTranslation = !includeTranslation }
                        )
                        PosterMiniToggleChip(
                            label = "声波印记",
                            active = showWaveformCode,
                            onClick = { showWaveformCode = !showWaveformCode }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // 4. 歌词行多选列表
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.45f)
                ) {
                    if (lines.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("当前曲目暂无可选歌词", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            itemsIndexed(lines) { idx, line ->
                                val checked = idx in selectedIndices
                                val accent = Color(template.accentColor)
                                Surface(
                                    shape = RoundedCornerShape(11.dp),
                                    color = if (checked) accent.copy(alpha = 0.16f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softCardClickable(shape = RoundedCornerShape(11.dp)) {
                                            selectedIndices = if (checked) {
                                                selectedIndices - idx
                                            } else if (selectedIndices.size < 6) {
                                                selectedIndices + idx
                                            } else {
                                                Toast.makeText(context, "最多可勾选 6 句歌词", Toast.LENGTH_SHORT).show()
                                                selectedIndices
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (checked) accent else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(17.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = line.text,
                                                color = if (checked) Color.White else Color.White.copy(alpha = 0.78f),
                                                fontSize = 13.sp,
                                                fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!line.translation.isNullOrBlank()) {
                                                Text(
                                                    text = line.translation,
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterMiniToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(9.dp),
        color = if (active) ConceptPrimary.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.08f),
        modifier = Modifier.softClickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = if (active) ConceptPrimary else Color.White.copy(alpha = 0.72f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
        )
    }
}

@Composable
private fun PosterLivePreviewCard(
    song: Song,
    selectedLines: List<LyricLine>,
    template: PosterTemplateStyle,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    showWaveform: Boolean,
    dateStr: String,
    modifier: Modifier = Modifier
) {
    val bgBrush = Brush.linearGradient(colors = template.bgColors.map { Color(it) })
    val cardColor = Color(template.cardColor)
    val textPrimary = Color(template.primaryTextColor)
    val textSecondary = Color(template.secondaryTextColor)
    val accent = Color(template.accentColor)
    val align = if (centerAlign) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centerAlign) TextAlign.Center else TextAlign.Start
    val cleanAlbum = remember(song.album) { sanitizeAlbumName(song.album) }
    val highResCoverUrl = remember(song.coverUrl) { upgradePosterCoverUrl(song.coverUrl) }
    val titlePreviewSize = remember(song.title) {
        when {
            song.title.length > 26 -> 12.5.sp
            song.title.length > 18 -> 13.5.sp
            else -> 15.sp
        }
    }

    Box(
        modifier = modifier
            .shadow(22.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        when (template) {
            // =====================================================================
            // 模板 1：黑胶典藏 (VINYL_ARCHIVE) — 探出式黑胶唱盘 + 鎏金录音室铭牌 + 金色左引线
            // =====================================================================
            PosterTemplateStyle.VINYL_ARCHIVE -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 顶部：左侧正方形封套 + 右侧半露出黑胶沟槽圆盘 + 曲目主信息
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .height(66.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                // 右侧探出的仿真黑胶盘
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF0A0D14))
                                        .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(0.8.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(accent)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF0A0D14))
                                    )
                                }
                                // 左侧专辑封套压在黑胶盘之上
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .shadow(8.dp, RoundedCornerShape(10.dp))
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFF1E293B))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                                ) {
                                    PosterCoverImage(coverUrl = highResCoverUrl, fallbackTitle = song.title, accent = accent)
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = accent.copy(alpha = 0.18f)
                                ) {
                                    Text(
                                        text = "MASTER VINYL · 45 RPM",
                                        color = accent,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.8.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = song.title,
                                    color = textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = titlePreviewSize,
                                    lineHeight = (titlePreviewSize.value * 1.18f).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum),
                                    color = textSecondary,
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // 鎏金刻度分割线
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(accent.copy(alpha = 0.35f))
                            )
                            Text(
                                text = " ✦ SIDE A · $dateStr ✦ ",
                                color = accent,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(1.dp)
                                    .background(accent.copy(alpha = 0.35f))
                            )
                        }

                        // 中部：带左侧鎏金竖向引线的歌词区
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!centerAlign) {
                                Box(
                                    modifier = Modifier
                                        .width(3.dp)
                                        .fillMaxHeight(0.76f)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(accent, accent.copy(alpha = 0.15f))
                                            )
                                        )
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            PosterLyricsBlock(
                                selectedLines = selectedLines,
                                includeTranslation = includeTranslation,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                align = align,
                                textAlign = textAlign,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // 底部：黑胶矩阵编号与声波条码
                        PosterFooterBar(
                            leftText = "CAT. NO. BM-${song.id.take(6).uppercase()} · HIGH FIDELITY",
                            accent = accent,
                            showWaveform = showWaveform
                        )
                    }
                }
            }

            // =====================================================================
            // 模板 2：先锋画报 (EDITORIAL_MAGAZINE) — 全画幅封面背景 + 杂志刊头 + 巨型引号
            // =====================================================================
            PosterTemplateStyle.EDITORIAL_MAGAZINE -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                ) {
                    // 全画幅背景封面 + 戏剧性暗调渐变遮罩
                    if (highResCoverUrl.isNotBlank()) {
                        AsyncImage(
                            model = highResCoverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            filterQuality = FilterQuality.High,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xB80E0816),
                                        Color(0xD8140C20),
                                        Color(0xF50B0612)
                                    )
                                )
                            )
                    )

                    // 背景巨型装饰引号
                    Text(
                        text = "“",
                        color = accent.copy(alpha = 0.22f),
                        fontSize = 110.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Serif,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 14.dp, top = 36.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 杂志大刊头 (Masthead)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column {
                                Text(
                                    text = "CONCEPT PICTORIAL",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.8.sp
                                )
                                Text(
                                    text = "MUSIC & LYRIC EDITORIAL // $dateStr",
                                    color = Color.White.copy(alpha = 0.65f),
                                    fontSize = 8.5.sp,
                                    letterSpacing = 0.6.sp
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent
                            ) {
                                Text(
                                    text = "ISSUE #10",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 中部：先锋大字报歌词
                        PosterLyricsBlock(
                            selectedLines = selectedLines,
                            includeTranslation = includeTranslation,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            align = align,
                            textAlign = textAlign,
                            fontSizeBoost = 1.5f,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 6.dp)
                        )

                        // 底部：杂志社论署名卡片 + 封面小缩略图
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White.copy(alpha = 0.10f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.6.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accent.copy(alpha = 0.25f))
                                    ) {
                                        PosterCoverImage(coverUrl = highResCoverUrl, fallbackTitle = song.title, accent = accent)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            lineHeight = 15.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (cleanAlbum.isNotBlank()) {
                                                "BY ${song.artist.uppercase()} · 《$cleanAlbum》"
                                            } else {
                                                "BY ${song.artist.uppercase()}"
                                            },
                                            color = accent,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (showWaveform) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PosterWaveformBars(accent = accent)
                                }
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 模板 3：东方雅韵 (MINIMAL_GALLERY) — 宣纸双线框 + 水墨圆相 + 宋刻衬线 + 朱砂印章
            // =====================================================================
            PosterTemplateStyle.MINIMAL_GALLERY -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .border(2.dp, Color(0xFF44403C), RoundedCornerShape(16.dp))
                        .padding(5.dp)
                        .border(0.8.dp, Color(0xFFA8A29E), RoundedCornerShape(12.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 顶部：东方美学圆相封面 + 意境引题
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 10.dp)
                            ) {
                                Text(
                                    text = "「 岁 月 留 声 · 词 境 」",
                                    color = textSecondary,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = song.title,
                                    color = textPrimary,
                                    fontSize = titlePreviewSize,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = (titlePreviewSize.value * 1.18f).sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum),
                                    color = textSecondary,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    lineHeight = 13.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // 圆相水墨边框封面
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, Color(0xFF292524), CircleShape)
                                    .padding(3.dp)
                                    .clip(CircleShape)
                            ) {
                                PosterCoverImage(coverUrl = highResCoverUrl, fallbackTitle = song.title, accent = accent)
                            }
                        }

                        // 水墨细线
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.8.dp)
                                .background(Color(0xFFD6D3D1))
                        )

                        // 中部：衬线体诗意歌词排版
                        PosterLyricsBlock(
                            selectedLines = selectedLines,
                            includeTranslation = includeTranslation,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            align = align,
                            textAlign = textAlign,
                            useSerif = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 6.dp)
                        )

                        // 底部：朱砂红印章「微风」+ 宣纸题跋
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // 朱砂方印
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(accent)
                                        .border(1.dp, Color(0xFFF6F2E9), RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "微风",
                                        color = Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        fontFamily = FontFamily.Serif
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "$dateStr · 酷狗概念版典藏",
                                    color = textSecondary,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Serif
                                )
                            }

                            if (showWaveform) {
                                PosterWaveformBars(accent = accent)
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 模板 4：复古拍立得 (RETRO_POLAROID) — 经典相纸宽下巴 + 橙色胶片日期戳
            // =====================================================================
            PosterTemplateStyle.RETRO_POLAROID -> {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = cardColor,
                    modifier = Modifier
                        .fillMaxSize()
                        .shadow(16.dp, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 上半部：拍立得胶片显影窗（带复古暗角与右下角橙黄胶片日期戳）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.46f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF181512))
                                .border(1.dp, Color(0xFFD6CFC2), RoundedCornerShape(6.dp))
                        ) {
                            PosterCoverImage(coverUrl = highResCoverUrl, fallbackTitle = song.title, accent = accent)
                            // 胶片暗角遮罩
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.56f)
                                            )
                                        )
                                    )
                            )
                            // 左下角曲目胶片字幕
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = 10.dp, end = 86.dp, bottom = 10.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    lineHeight = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum),
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            // 右下角经典胶片相机橙色 LED 日期印记
                            Text(
                                text = "'${dateStr.takeLast(8).replace(".", " ")}",
                                color = Color(0xFFFF8A2B),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                            )
                        }

                        // 下半部：拍立得宽幅相纸留白手写感金句区
                        PosterLyricsBlock(
                            selectedLines = selectedLines,
                            includeTranslation = includeTranslation,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            align = align,
                            textAlign = textAlign,
                            italicStyle = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(0.44f)
                                .padding(vertical = 4.dp)
                        )

                        // 底部：胶片编码与手写风签名栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cleanAlbum.isNotBlank()) {
                                    "INSTANT FILM · 《$cleanAlbum》 · ${song.artist}"
                                } else {
                                    "INSTANT FILM · ${song.title} — ${song.artist}"
                                },
                                color = textSecondary,
                                fontSize = 9.5.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (showWaveform) {
                                Spacer(modifier = Modifier.width(6.dp))
                                PosterWaveformBars(accent = accent)
                            }
                        }
                    }
                }
            }

            // =====================================================================
            // 模板 5：赛博流光 (AURORA_GLASS) — 霓虹机能四角框 + 首句荧光胶囊 + 播放时间轴
            // =====================================================================
            PosterTemplateStyle.AURORA_GLASS -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor)
                        .border(
                            width = 1.2.dp,
                            brush = Brush.linearGradient(
                                listOf(accent, Color(0xFF3898FF), accent.copy(alpha = 0.3f))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                ) {
                    // 机能风四角 L 型准星装饰线
                    ComposeCanvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                        val len = 18.dp.toPx()
                        val stroke = 2.dp.toPx()
                        val c = accent
                        drawLine(c, Offset(0f, 0f), Offset(len, 0f), stroke)
                        drawLine(c, Offset(0f, 0f), Offset(0f, len), stroke)
                        drawLine(c, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke)
                        drawLine(c, Offset(size.width, 0f), Offset(size.width, len), stroke)
                        drawLine(c, Offset(0f, size.height - len), Offset(0f, size.height), stroke)
                        drawLine(c, Offset(0f, size.height), Offset(len, size.height), stroke)
                        drawLine(c, Offset(size.width - len, size.height), Offset(size.width, size.height), stroke)
                        drawLine(c, Offset(size.width, size.height - len), Offset(size.width, size.height), stroke)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // 顶部：赛博音频终端 HUD 栏
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, accent, RoundedCornerShape(12.dp))
                                ) {
                                    PosterCoverImage(coverUrl = highResCoverUrl, fallbackTitle = song.title, accent = accent)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Black,
                                        lineHeight = 16.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = formatPosterArtistAlbumSubtitle(
                                            artist = song.artist,
                                            albumName = cleanAlbum,
                                            fallbackTagWhenNoAlbum = "24-BIT HI-RES"
                                        ),
                                        color = accent,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.border(0.8.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        // 中部：首句带赛博霓虹高亮胶囊背景
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 6.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = align
                        ) {
                            if (selectedLines.isEmpty()) {
                                Text("请在下方勾选 1~6 句歌词", color = textSecondary, fontSize = 13.sp)
                            } else {
                                selectedLines.take(6).forEachIndexed { idx, ln ->
                                    val isHighlight = idx == 0
                                    Column(
                                        horizontalAlignment = align,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.5.dp)
                                            .then(
                                                if (isHighlight) {
                                                    Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(accent.copy(alpha = 0.16f))
                                                        .border(0.8.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 8.dp, vertical = 5.dp)
                                                } else Modifier
                                            )
                                    ) {
                                        Text(
                                            text = ln.text,
                                            color = if (isHighlight) accent else textPrimary,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = if (selectedLines.size <= 2) 16.sp else 13.5.sp,
                                            lineHeight = 19.sp,
                                            textAlign = textAlign,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (includeTranslation && !ln.translation.isNullOrBlank()) {
                                            Text(
                                                text = ln.translation,
                                                color = textSecondary.copy(alpha = 0.85f),
                                                fontSize = 10.5.sp,
                                                textAlign = textAlign,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // 底部：模拟播放器进度条 + 频谱脉冲
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "01:28",
                                    color = accent,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(3.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.16f))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.62f)
                                            .fillMaxHeight()
                                            .background(accent)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = dateStr,
                                    color = textSecondary.copy(alpha = 0.75f),
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                if (showWaveform) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    PosterWaveformBars(accent = accent)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCoverImage(
    coverUrl: String,
    fallbackTitle: String,
    accent: Color
) {
    if (coverUrl.isNotBlank()) {
        AsyncImage(
            model = coverUrl,
            contentDescription = fallbackTitle,
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.High,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Album, contentDescription = null, tint = accent)
        }
    }
}

@Composable
private fun PosterLyricsBlock(
    selectedLines: List<LyricLine>,
    includeTranslation: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    align: Alignment.Horizontal,
    textAlign: TextAlign,
    useSerif: Boolean = false,
    italicStyle: Boolean = false,
    fontSizeBoost: Float = 0f,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = align
    ) {
        if (selectedLines.isEmpty()) {
            Text(
                text = "请在下方列表勾选 1~6 句心仪歌词",
                color = textSecondary,
                fontSize = 13.sp
            )
        } else {
            val baseSize = (if (selectedLines.size <= 2) 16.5f else if (selectedLines.size <= 4) 14.5f else 13f) + fontSizeBoost
            selectedLines.take(6).forEach { ln ->
                Column(
                    horizontalAlignment = align,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.5.dp)
                ) {
                    Text(
                        text = ln.text,
                        color = textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                        fontStyle = if (italicStyle) FontStyle.Italic else FontStyle.Normal,
                        fontSize = baseSize.sp,
                        lineHeight = (baseSize * 1.32f).sp,
                        textAlign = textAlign,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (includeTranslation && !ln.translation.isNullOrBlank()) {
                        Text(
                            text = ln.translation,
                            color = textSecondary,
                            fontFamily = if (useSerif) FontFamily.Serif else FontFamily.Default,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            textAlign = textAlign,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterFooterBar(
    leftText: String,
    accent: Color,
    showWaveform: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = leftText,
            color = accent,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (showWaveform) {
            PosterWaveformBars(accent = accent)
        }
    }
}

@Composable
private fun PosterWaveformBars(accent: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        val heights = listOf(6, 12, 8, 15, 10, 14, 7, 13, 9, 5)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(h.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.8f))
            )
        }
    }
}

/**
 * 2K 超清 (2160 × 3120+) 海报离屏渲染引擎：
 * - 采用 2.0x 矢量超采样矩阵 (`POSTER_EXPORT_SCALE = 2f`)，基础分辨率达 2160 × 3120；
 * - 若选中歌词折行较多，自动动态延展逻辑画布高度，杜绝歌词与底栏重叠；
 * - 封面请求自动升级为 1000px 高清源并以 1600×1600 尺寸解码；
 * - 歌名、歌手、专辑名与页脚标语全部采用像素宽度自适应字号缩放与智能多行折行，零截断呈现。
 */
private suspend fun renderAndSavePosterToGallery(
    context: Context,
    song: Song,
    lines: List<LyricLine>,
    template: PosterTemplateStyle,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    showWaveform: Boolean,
    dateStr: String
): Uri? {
    val cleanAlbum = resolveCleanPosterAlbumName(song)
    val logicalWidth = POSTER_BASE_LOGICAL_WIDTH

    // 预估歌词总高度，若歌词较长则自动增高海报画布
    val dummyTypeface = if (template == PosterTemplateStyle.MINIMAL_GALLERY) {
        Typeface.create(Typeface.SERIF, Typeface.BOLD)
    } else if (template == PosterTemplateStyle.RETRO_POLAROID) {
        Typeface.create(Typeface.SERIF, Typeface.ITALIC)
    } else {
        Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val lyricsStartX = when (template) {
        PosterTemplateStyle.VINYL_ARCHIVE -> if (centerAlign) logicalWidth / 2f else 176f
        PosterTemplateStyle.EDITORIAL_MAGAZINE -> if (centerAlign) logicalWidth / 2f else 136f
        PosterTemplateStyle.MINIMAL_GALLERY -> if (centerAlign) logicalWidth / 2f else 136f
        PosterTemplateStyle.RETRO_POLAROID -> if (centerAlign) logicalWidth / 2f else 148f
        PosterTemplateStyle.AURORA_GLASS -> if (centerAlign) logicalWidth / 2f else 148f
    }
    val lyricsStartY = when (template) {
        PosterTemplateStyle.VINYL_ARCHIVE -> 530f
        PosterTemplateStyle.EDITORIAL_MAGAZINE -> 470f
        PosterTemplateStyle.MINIMAL_GALLERY -> 495f
        PosterTemplateStyle.RETRO_POLAROID -> 865f
        PosterTemplateStyle.AURORA_GLASS -> 478f
    }
    val bottomReservedHeight = when (template) {
        PosterTemplateStyle.EDITORIAL_MAGAZINE -> 320f
        PosterTemplateStyle.AURORA_GLASS -> 270f
        else -> 220f
    }
    val measuredLyricsHeight = measureCanvasLyricsTotalHeight(
        lines = lines,
        includeTranslation = includeTranslation,
        centerAlign = centerAlign,
        startX = lyricsStartX,
        logicalWidth = logicalWidth,
        typeface = dummyTypeface
    )
    val logicalHeight = max(
        POSTER_BASE_LOGICAL_HEIGHT,
        (lyricsStartY + measuredLyricsHeight + bottomReservedHeight).toInt()
    )

    val bitmapWidth = (logicalWidth * POSTER_EXPORT_SCALE).toInt()
    val bitmapHeight = (logicalHeight * POSTER_EXPORT_SCALE).toInt()
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.scale(POSTER_EXPORT_SCALE, POSTER_EXPORT_SCALE)

    // 加载高分辨率封面位图（优先尝试升级后的 1000px 高清封面，若失败则回退原 coverUrl）
    val coverBitmap: Bitmap? = if (song.coverUrl.isNotBlank()) {
        val highResUrl = upgradePosterCoverUrl(song.coverUrl)
        loadPosterCoverBitmap(context, highResUrl)
            ?: if (highResUrl != song.coverUrl) loadPosterCoverBitmap(context, song.coverUrl) else null
    } else null

    val width = logicalWidth
    val height = logicalHeight

    // 1. 底层背景
    val bgPaint = newHiResPaint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            template.bgColors,
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val cardRect = RectF(64f, 76f, width - 64f, height - 76f)
    val cardPaint = newHiResPaint().apply { color = template.cardColor }

    when (template) {
        PosterTemplateStyle.VINYL_ARCHIVE -> {
            canvas.drawRoundRect(cardRect, 52f, 52f, cardPaint)
            val borderPaint = newHiResPaint().apply {
                color = template.accentColor
                alpha = 95
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(cardRect, 52f, 52f, borderPaint)

            // 探出式黑胶唱盘 + 专辑封套
            val discCenterX = 330f
            val discCenterY = 250f
            val vinylPaint = newHiResPaint().apply { color = 0xFF0A0D14.toInt() }
            canvas.drawCircle(discCenterX, discCenterY, 102f, vinylPaint)
            val groovePaint = newHiResPaint().apply {
                color = template.accentColor
                alpha = 110
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawCircle(discCenterX, discCenterY, 82f, groovePaint)
            canvas.drawCircle(discCenterX, discCenterY, 62f, groovePaint)
            val labelPaint = newHiResPaint().apply { color = template.accentColor }
            canvas.drawCircle(discCenterX, discCenterY, 30f, labelPaint)

            val sleeveRect = RectF(132f, 146f, 340f, 354f)
            drawClippedCover(canvas, coverBitmap, sleeveRect, 26f, template.accentColor)

            // 右侧标题与完整「歌手 · 《专辑》」自适应排版（可用宽度 468..964 = 496px）
            val rightTextX = 468f
            val rightMaxWidth = (cardRect.right - 48f) - rightTextX
            val tagPaint = newHiResPaint().apply {
                color = template.accentColor
                textSize = 23f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("MASTER VINYL · 45 RPM", rightTextX, 186f, tagPaint)

            val titlePaint = newHiResPaint().apply {
                color = template.primaryTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleEndY = drawFittedTextBlock(
                canvas = canvas,
                text = song.title,
                x = rightTextX,
                firstBaselineY = 244f,
                maxWidth = rightMaxWidth,
                baseTextSize = 46f,
                minTextSize = 26f,
                maxLines = 2,
                paint = titlePaint,
                lineSpacingFactor = 1.18f
            )

            val subPaint = newHiResPaint().apply {
                color = template.secondaryTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val artistAlbumSub = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum)
            drawFittedTextBlock(
                canvas = canvas,
                text = artistAlbumSub,
                x = rightTextX,
                firstBaselineY = titleEndY + 42f,
                maxWidth = rightMaxWidth,
                baseTextSize = 29f,
                minTextSize = 19f,
                maxLines = 2,
                paint = subPaint,
                lineSpacingFactor = 1.20f
            )

            // 鎏金分割线
            val divPaint = newHiResPaint().apply {
                color = template.accentColor
                alpha = 90
                strokeWidth = 2.5f
            }
            canvas.drawLine(132f, 412f, width - 132f, 412f, divPaint)

            // 左侧金色引线 + 歌词
            val lyricsEndY = drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 176f,
                startY = 530f,
                logicalWidth = width,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )
            if (!centerAlign) {
                val barPaint = newHiResPaint().apply {
                    color = template.accentColor
                    strokeWidth = 8f
                    strokeCap = Paint.Cap.ROUND
                }
                val barBottom = max(920f, (lyricsEndY - 24f).coerceAtMost(height - 220f))
                canvas.drawLine(140f, 485f, 140f, barBottom, barPaint)
            }
            drawCanvasFooter(
                canvas = canvas,
                width = width,
                height = height,
                text = "CAT. NO. BM-${song.id.take(6).uppercase()} · HIGH FIDELITY",
                accentColor = template.accentColor,
                showWaveform = showWaveform
            )
        }

        PosterTemplateStyle.EDITORIAL_MAGAZINE -> {
            // 全画幅背景封面
            canvas.save()
            val clip = Path().apply { addRoundRect(cardRect, 52f, 52f, Path.Direction.CW) }
            canvas.clipPath(clip)
            if (coverBitmap != null) {
                drawCenterCropBitmap(canvas, coverBitmap, cardRect)
            }
            val scrimPaint = newHiResPaint().apply {
                shader = LinearGradient(
                    0f, cardRect.top, 0f, cardRect.bottom,
                    intArrayOf(0xB80E0816.toInt(), 0xD8140C20.toInt(), 0xF50B0612.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(cardRect, scrimPaint)

            // 巨型半透明引号
            val quotePaint = newHiResPaint().apply {
                color = template.accentColor
                alpha = 55
                textSize = 280f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("“", 118f, 480f, quotePaint)

            // 杂志大刊头
            val mastheadPaint = newHiResPaint().apply {
                color = template.primaryTextColor
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subMastPaint = newHiResPaint().apply {
                color = template.secondaryTextColor
                textSize = 24f
            }
            canvas.drawText("CONCEPT PICTORIAL", 132f, 188f, mastheadPaint)
            canvas.drawText("MUSIC & LYRIC EDITORIAL // $dateStr", 132f, 230f, subMastPaint)

            // 歌词
            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 136f,
                startY = 470f,
                logicalWidth = width,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )

            // 底部杂志社论署名栏（自适应歌名与歌手/专辑，绝不截断）
            val bottomBox = RectF(124f, height - 292f, width - 124f, height - 126f)
            val boxPaint = newHiResPaint().apply { color = 0x33FFFFFF }
            canvas.drawRoundRect(bottomBox, 32f, 32f, boxPaint)
            val miniCoverRect = RectF(152f, height - 260f, 254f, height - 158f)
            drawClippedCover(canvas, coverBitmap, miniCoverRect, 20f, template.accentColor)

            val textRightLimit = if (showWaveform) (bottomBox.right - 185f) else (bottomBox.right - 32f)
            val bottomTextMaxWidth = (textRightLimit - 280f).coerceAtLeast(320f)
            val bTitlePaint = newHiResPaint().apply {
                color = template.primaryTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleEndY = drawFittedTextBlock(
                canvas = canvas,
                text = song.title,
                x = 280f,
                firstBaselineY = height - 216f,
                maxWidth = bottomTextMaxWidth,
                baseTextSize = 36f,
                minTextSize = 22f,
                maxLines = 2,
                paint = bTitlePaint,
                lineSpacingFactor = 1.15f
            )

            val bArtistPaint = newHiResPaint().apply {
                color = template.accentColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val editorialSub = if (cleanAlbum.isNotBlank()) {
                "BY ${song.artist.uppercase()} · 《$cleanAlbum》"
            } else {
                "BY ${song.artist.uppercase()}"
            }
            drawFittedTextBlock(
                canvas = canvas,
                text = editorialSub,
                x = 280f,
                firstBaselineY = titleEndY + 34f,
                maxWidth = bottomTextMaxWidth,
                baseTextSize = 24f,
                minTextSize = 16f,
                maxLines = 2,
                paint = bArtistPaint,
                lineSpacingFactor = 1.15f
            )

            if (showWaveform) {
                drawCanvasWaveformBars(
                    canvas = canvas,
                    startX = bottomBox.right - 165f,
                    centerY = bottomBox.centerY(),
                    accentColor = template.accentColor
                )
            }
            canvas.restore()
        }

        PosterTemplateStyle.MINIMAL_GALLERY -> {
            // 宣纸外粗内细双线古典画框
            canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)
            val outerStroke = newHiResPaint().apply {
                color = 0xFF44403C.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val innerStroke = newHiResPaint().apply {
                color = 0xFFA8A29E.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(cardRect, 36f, 36f, outerStroke)
            val innerRect = RectF(cardRect.left + 18f, cardRect.top + 18f, cardRect.right - 18f, cardRect.bottom - 18f)
            canvas.drawRoundRect(innerRect, 26f, 26f, innerStroke)

            val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            val serifNormal = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

            // 右上圆相封面
            val circleRect = RectF(width - 310f, 156f, width - 142f, 324f)
            drawClippedCover(canvas, coverBitmap, circleRect, 84f, template.accentColor)
            val circleBorder = newHiResPaint().apply {
                color = 0xFF292524.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawOval(circleRect, circleBorder)

            val headerMaxWidth = (circleRect.left - 32f) - 136f
            val headerPaint = newHiResPaint().apply {
                color = template.secondaryTextColor
                textSize = 25f
                typeface = serifNormal
            }
            canvas.drawText("「 岁 月 留 声 · 词 境 」", 136f, 184f, headerPaint)

            val titlePaint = newHiResPaint().apply {
                color = template.primaryTextColor
                typeface = serifBold
            }
            val titleEndY = drawFittedTextBlock(
                canvas = canvas,
                text = song.title,
                x = 136f,
                firstBaselineY = 246f,
                maxWidth = headerMaxWidth,
                baseTextSize = 48f,
                minTextSize = 26f,
                maxLines = 2,
                paint = titlePaint,
                lineSpacingFactor = 1.16f
            )

            val subPaint = newHiResPaint().apply {
                color = template.secondaryTextColor
                typeface = serifNormal
            }
            val minimalSub = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum)
            val subEndY = drawFittedTextBlock(
                canvas = canvas,
                text = minimalSub,
                x = 136f,
                firstBaselineY = titleEndY + 42f,
                maxWidth = headerMaxWidth,
                baseTextSize = 29f,
                minTextSize = 19f,
                maxLines = 2,
                paint = subPaint,
                lineSpacingFactor = 1.18f
            )

            val dividerY = max(384f, subEndY + 34f)
            val linePaint = newHiResPaint().apply {
                color = 0xFFD6D3D1.toInt()
                strokeWidth = 2f
            }
            canvas.drawLine(136f, dividerY, width - 136f, dividerY, linePaint)

            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 136f,
                startY = dividerY + 105f,
                logicalWidth = width,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = serifBold
            )

            // 朱砂红印章
            val sealRect = RectF(136f, height - 196f, 200f, height - 132f)
            val sealPaint = newHiResPaint().apply { color = template.accentColor }
            canvas.drawRoundRect(sealRect, 10f, 10f, sealPaint)
            val sealTextPaint = newHiResPaint().apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 26f
                typeface = serifBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("微风", sealRect.centerX(), sealRect.centerY() + 10f, sealTextPaint)

            val footTextPaint = newHiResPaint().apply {
                color = template.secondaryTextColor
                textSize = 27f
                typeface = serifNormal
            }
            canvas.drawText("$dateStr · 酷狗概念版典藏", 222f, height - 154f, footTextPaint)
            if (showWaveform) {
                drawCanvasWaveformBars(
                    canvas = canvas,
                    startX = width - 290f,
                    centerY = height - 164f,
                    accentColor = template.accentColor
                )
            }
        }

        PosterTemplateStyle.RETRO_POLAROID -> {
            // 经典拍立得相纸
            val polaroidRect = RectF(88f, 92f, width - 88f, height - 92f)
            canvas.drawRoundRect(polaroidRect, 28f, 28f, cardPaint)

            // 大画幅方形取景窗
            val photoRect = RectF(140f, 144f, width - 140f, 770f)
            drawClippedCover(canvas, coverBitmap, photoRect, 16f, template.accentColor)

            // 底部暗角渐变遮罩，保证白色叠字清晰可读
            val photoScrim = newHiResPaint().apply {
                shader = LinearGradient(
                    0f, photoRect.bottom - 220f, 0f, photoRect.bottom,
                    intArrayOf(0x00000000, 0xAA000000.toInt()),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.save()
            val photoClip = Path().apply { addRoundRect(photoRect, 16f, 16f, Path.Direction.CW) }
            canvas.clipPath(photoClip)
            canvas.drawRect(photoRect.left, photoRect.bottom - 220f, photoRect.right, photoRect.bottom, photoScrim)
            canvas.restore()

            val stampText = "'${dateStr.takeLast(8).replace(".", " ")}"
            val stampPaint = newHiResPaint().apply {
                color = 0xFFFF8A2B.toInt()
                textSize = 32f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
                setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
            }
            canvas.drawText(stampText, photoRect.right - 28f, photoRect.bottom - 28f, stampPaint)

            val stampWidth = stampPaint.measureText(stampText)
            val photoTextMaxWidth = (photoRect.width() - 56f - stampWidth - 24f).coerceAtLeast(320f)

            val photoArtistPaint = newHiResPaint().apply {
                color = 0xEEFFFFFF.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                setShadowLayer(6f, 0f, 2f, 0x99000000.toInt())
            }
            val polaroidSub = formatPosterArtistAlbumSubtitle(song.artist, cleanAlbum)
            drawFittedTextBlock(
                canvas = canvas,
                text = polaroidSub,
                x = photoRect.left + 28f,
                firstBaselineY = photoRect.bottom - 28f,
                maxWidth = photoTextMaxWidth,
                baseTextSize = 25f,
                minTextSize = 17f,
                maxLines = 1,
                paint = photoArtistPaint
            )

            val photoTitlePaint = newHiResPaint().apply {
                color = 0xFFFFFFFF.toInt()
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                setShadowLayer(8f, 0f, 3f, 0x99000000.toInt())
            }
            drawFittedTextBlock(
                canvas = canvas,
                text = song.title,
                x = photoRect.left + 28f,
                firstBaselineY = photoRect.bottom - 68f,
                maxWidth = photoTextMaxWidth,
                baseTextSize = 38f,
                minTextSize = 22f,
                maxLines = 1,
                paint = photoTitlePaint
            )

            // 相纸下巴区歌词
            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 148f,
                startY = 865f,
                logicalWidth = width,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            )

            val polaroidFooter = if (cleanAlbum.isNotBlank()) {
                "INSTANT FILM · 《$cleanAlbum》 — ${song.artist}"
            } else {
                "INSTANT FILM · ${song.title} — ${song.artist}"
            }
            drawCanvasFooter(
                canvas = canvas,
                width = width,
                height = height,
                text = polaroidFooter,
                accentColor = template.accentColor,
                showWaveform = showWaveform,
                leftMargin = 148f,
                rightMargin = 148f
            )
        }

        PosterTemplateStyle.AURORA_GLASS -> {
            canvas.drawRoundRect(cardRect, 52f, 52f, cardPaint)
            val borderPaint = newHiResPaint().apply {
                color = template.accentColor
                alpha = 150
                style = Paint.Style.STROKE
                strokeWidth = 3.5f
            }
            canvas.drawRoundRect(cardRect, 52f, 52f, borderPaint)

            // 顶部赛博 HUD
            val hudCoverRect = RectF(132f, 152f, 276f, 296f)
            drawClippedCover(canvas, coverBitmap, hudCoverRect, 28f, template.accentColor)
            canvas.drawRoundRect(hudCoverRect, 28f, 28f, borderPaint)

            val hudMaxWidth = (cardRect.right - 52f) - 308f
            val titlePaint = newHiResPaint().apply {
                color = template.primaryTextColor
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titleEndY = drawFittedTextBlock(
                canvas = canvas,
                text = song.title,
                x = 308f,
                firstBaselineY = 208f,
                maxWidth = hudMaxWidth,
                baseTextSize = 44f,
                minTextSize = 25f,
                maxLines = 2,
                paint = titlePaint,
                lineSpacingFactor = 1.15f
            )

            val subPaint = newHiResPaint().apply {
                color = template.accentColor
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            val auroraSub = if (cleanAlbum.isNotBlank()) {
                "${song.artist} · 《$cleanAlbum》 // 24-BIT AUDIO"
            } else {
                "${song.artist} // 24-BIT AUDIO STREAM"
            }
            drawFittedTextBlock(
                canvas = canvas,
                text = auroraSub,
                x = 308f,
                firstBaselineY = titleEndY + 40f,
                maxWidth = hudMaxWidth,
                baseTextSize = 25f,
                minTextSize = 16.5f,
                maxLines = 2,
                paint = subPaint,
                lineSpacingFactor = 1.18f
            )

            // 首句荧光高亮底框：根据第 1 句歌词实际折行高度动态计算底框尺寸，确保主词与译文完全包裹在内
            val auroraLyricsStartX = if (centerAlign) width / 2f else 148f
            val auroraLyricsStartY = 478f
            val auroraBoldTypeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            if (lines.isNotEmpty()) {
                val firstBlockBottomY = measureFirstLyricHighlightBottomY(
                    firstLine = lines[0],
                    totalSelectedCount = lines.size,
                    includeTranslation = includeTranslation,
                    centerAlign = centerAlign,
                    startX = auroraLyricsStartX,
                    startY = auroraLyricsStartY,
                    logicalWidth = width,
                    typeface = auroraBoldTypeface
                )
                val hlRect = RectF(124f, auroraLyricsStartY - 62f, width - 124f, firstBlockBottomY)
                val hlFill = newHiResPaint().apply {
                    color = template.accentColor
                    alpha = 38
                }
                val hlStroke = newHiResPaint().apply {
                    color = template.accentColor
                    alpha = 110
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRoundRect(hlRect, 24f, 24f, hlFill)
                canvas.drawRoundRect(hlRect, 24f, 24f, hlStroke)
            }

            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = auroraLyricsStartX,
                startY = auroraLyricsStartY,
                logicalWidth = width,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = auroraBoldTypeface,
                firstLineHighlightColor = template.accentColor
            )

            // 底部模拟时间轴进度条
            val barY = height - 210f
            val trackBg = newHiResPaint().apply {
                color = 0x33FFFFFF
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            val trackFg = newHiResPaint().apply {
                color = template.accentColor
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(240f, barY, width - 240f, barY, trackBg)
            canvas.drawLine(240f, barY, 640f, barY, trackFg)
            canvas.drawCircle(640f, barY, 12f, trackFg)

            drawCanvasFooter(
                canvas = canvas,
                width = width,
                height = height,
                text = "CYBER AUDIO // BREEZE MUSIC · $dateStr",
                accentColor = template.accentColor,
                showWaveform = showWaveform
            )
        }
    }

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BreezePoster_${template.name}_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ConceptMusic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: run {
        if (!bitmap.isRecycled) bitmap.recycle()
        return null
    }
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        uri
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        null
    } finally {
        if (!bitmap.isRecycled) {
            bitmap.recycle()
        }
    }
}

private fun newHiResPaint(): Paint = Paint(
    Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.FILTER_BITMAP_FLAG
)

private suspend fun loadPosterCoverBitmap(context: Context, url: String): Bitmap? {
    if (url.isBlank()) return null
    return runCatching {
        val req = ImageRequest.Builder(context)
            .data(url)
            .size(1600, 1600)
            .allowHardware(false)
            .build()
        val res = context.imageLoader.execute(req)
        ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
    }.getOrNull()
}

private fun drawCenterCropBitmap(canvas: Canvas, bitmap: Bitmap, dstRect: RectF) {
    val bw = bitmap.width.toFloat().coerceAtLeast(1f)
    val bh = bitmap.height.toFloat().coerceAtLeast(1f)
    val dw = dstRect.width()
    val dh = dstRect.height()
    val scale = max(dw / bw, dh / bh)
    val scaledW = bw * scale
    val scaledH = bh * scale
    val left = dstRect.left + (dw - scaledW) * 0.5f
    val top = dstRect.top + (dh - scaledH) * 0.5f
    val target = RectF(left, top, left + scaledW, top + scaledH)
    canvas.drawBitmap(bitmap, null, target, newHiResPaint())
}

private fun drawClippedCover(
    canvas: Canvas,
    coverBitmap: Bitmap?,
    rect: RectF,
    cornerRadius: Float,
    fallbackColor: Int
) {
    if (coverBitmap != null) {
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(rect, cornerRadius, cornerRadius, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        drawCenterCropBitmap(canvas, coverBitmap, rect)
        canvas.restore()
    } else {
        val placeholderPaint = newHiResPaint().apply {
            color = fallbackColor
            alpha = 65
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, placeholderPaint)
    }
}

/**
 * 自适应宽度文本排版：
 * 1. 先尝试从 `baseTextSize` 平滑缩小至 `minTextSize`，若单行能完整放下则单行绘制；
 * 2. 若在 `minTextSize` 下仍超出 `maxWidth` 且 `maxLines > 1`，则自动在较大可读字号下拆分为多行，
 *    若拆分行数超过 `maxLines` 则继续微调字号，直到全部文本完整放入 `maxLines` 行内，彻底杜绝截断缺字。
 * @return 最后一行的基线 Y 坐标
 */
internal fun drawFittedTextBlock(
    canvas: Canvas?,
    text: String,
    x: Float,
    firstBaselineY: Float,
    maxWidth: Float,
    baseTextSize: Float,
    minTextSize: Float,
    maxLines: Int = 2,
    paint: Paint,
    lineSpacingFactor: Float = 1.18f
): Float {
    val cleaned = text.trim()
    if (cleaned.isEmpty()) return firstBaselineY
    val safeWidth = maxWidth.coerceAtLeast(160f)

    // 1. 尝试单行自适应字号
    var candidateSize = baseTextSize
    val singleLineFloor = if (maxLines <= 1) minTextSize.coerceAtMost(13f) else minTextSize
    while (candidateSize >= singleLineFloor) {
        paint.textSize = candidateSize
        if (paint.measureText(cleaned) <= safeWidth) {
            canvas?.drawText(cleaned, x, firstBaselineY, paint)
            return firstBaselineY
        }
        candidateSize -= 1.0f
    }

    if (maxLines <= 1) {
        // 即使极长单行文本，也通过缩放使其完整放入 maxWidth
        val measured = paint.measureText(cleaned).coerceAtLeast(1f)
        if (measured > safeWidth) {
            paint.textSize = (paint.textSize * (safeWidth / measured)).coerceAtLeast(11f)
        }
        canvas?.drawText(cleaned, x, firstBaselineY, paint)
        return firstBaselineY
    }

    // 2. 多行自适应折行：从较舒适的字号开始尝试，确保拆分后总行数 <= maxLines
    var multiLineSize = (baseTextSize * 0.84f).coerceAtLeast(minTextSize)
    var wrappedLines = breakTextIntoLines(cleaned, paint.apply { textSize = multiLineSize }, safeWidth)
    while (wrappedLines.size > maxLines && multiLineSize > 14f) {
        multiLineSize -= 1.0f
        paint.textSize = multiLineSize
        wrappedLines = breakTextIntoLines(cleaned, paint, safeWidth)
    }
    if (wrappedLines.size > maxLines) {
        // 若仍超出 maxLines，将剩余文本合并到最后一行并按宽度缩放字号，保证零丢失
        val head = wrappedLines.take(maxLines - 1)
        val tail = wrappedLines.drop(maxLines - 1).joinToString(" ")
        wrappedLines = head + tail
        val tailWidth = paint.measureText(tail)
        if (tailWidth > safeWidth) {
            paint.textSize = (paint.textSize * (safeWidth / tailWidth)).coerceAtLeast(12f)
        }
    }

    var currentY = firstBaselineY
    val stepY = paint.textSize * lineSpacingFactor
    wrappedLines.forEachIndexed { idx, lineStr ->
        if (idx > 0) currentY += stepY
        canvas?.drawText(lineStr, x, currentY, paint)
    }
    return currentY
}

internal fun breakTextIntoLines(text: String, paint: Paint, maxWidth: Float): List<String> {
    val cleaned = text.trim()
    if (cleaned.isEmpty()) return emptyList()
    val result = mutableListOf<String>()
    var offset = 0
    val len = cleaned.length
    val safeMaxWidth = maxWidth.coerceAtLeast(180f)
    while (offset < len) {
        val count = paint.breakText(cleaned, offset, len, true, safeMaxWidth, null)
        if (count <= 0) break
        var end = offset + count
        if (end < len && cleaned[end - 1].isLetterOrDigit() && cleaned[end].isLetterOrDigit()) {
            val lastSpace = cleaned.lastIndexOf(' ', end - 1)
            if (lastSpace > offset) {
                end = lastSpace + 1
            }
        }
        val segment = cleaned.substring(offset, end).trim()
        if (segment.isNotEmpty()) {
            result.add(segment)
        }
        offset = end
        while (offset < len && cleaned[offset] == ' ') {
            offset++
        }
    }
    return result
}

internal fun measureFirstLyricHighlightBottomY(
    firstLine: LyricLine,
    totalSelectedCount: Int,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    startX: Float,
    startY: Float,
    logicalWidth: Int,
    typeface: Typeface
): Float {
    val lyricMainPaint = newHiResPaint().apply {
        textSize = if (totalSelectedCount <= 3) 48f else 40f
        this.typeface = typeface
    }
    val lyricTransPaint = newHiResPaint().apply {
        textSize = 28f
    }
    val availableWidth = if (centerAlign) (logicalWidth - 280f) else (logicalWidth - startX * 2f)
    val mainLineStep = if (totalSelectedCount <= 3) 60f else 50f
    val transLineStep = 38f
    var cursorY = startY
    val wrappedMain = breakTextIntoLines(firstLine.text, lyricMainPaint, availableWidth)
    cursorY += wrappedMain.size.coerceAtLeast(1) * mainLineStep
    if (includeTranslation && !firstLine.translation.isNullOrBlank()) {
        val wrappedTrans = breakTextIntoLines(firstLine.translation, lyricTransPaint, availableWidth)
        cursorY += wrappedTrans.size * transLineStep + 6f
    }
    return cursorY - 12f
}

private fun measureCanvasLyricsTotalHeight(
    lines: List<LyricLine>,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    startX: Float,
    logicalWidth: Int,
    typeface: Typeface
): Float {
    if (lines.isEmpty()) return 200f
    val lyricMainPaint = newHiResPaint().apply {
        textSize = if (lines.size <= 3) 48f else 40f
        this.typeface = typeface
    }
    val lyricTransPaint = newHiResPaint().apply {
        textSize = 28f
    }
    val availableWidth = if (centerAlign) (logicalWidth - 280f) else (logicalWidth - startX * 2f)
    val mainLineStep = if (lines.size <= 3) 60f else 50f
    val transLineStep = 38f
    var total = 0f
    lines.take(6).forEach { ln ->
        val wrappedMain = breakTextIntoLines(ln.text, lyricMainPaint, availableWidth)
        total += wrappedMain.size.coerceAtLeast(1) * mainLineStep
        if (includeTranslation && !ln.translation.isNullOrBlank()) {
            val wrappedTrans = breakTextIntoLines(ln.translation, lyricTransPaint, availableWidth)
            total += wrappedTrans.size * transLineStep + 6f
        }
        total += 26f
    }
    return total
}

private fun drawCanvasLyrics(
    canvas: Canvas,
    lines: List<LyricLine>,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    startX: Float,
    startY: Float,
    logicalWidth: Int,
    primaryColor: Int,
    secondaryColor: Int,
    typeface: Typeface,
    firstLineHighlightColor: Int? = null
): Float {
    val lyricMainPaint = newHiResPaint().apply {
        color = primaryColor
        textSize = if (lines.size <= 3) 48f else 40f
        this.typeface = typeface
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    val lyricTransPaint = newHiResPaint().apply {
        color = secondaryColor
        textSize = 28f
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    val availableWidth = if (centerAlign) (logicalWidth - 280f) else (logicalWidth - startX * 2f)
    val mainLineStep = if (lines.size <= 3) 60f else 50f
    val transLineStep = 38f
    var cursorY = startY
    lines.take(6).forEachIndexed { idx, ln ->
        lyricMainPaint.color = if (idx == 0 && firstLineHighlightColor != null) firstLineHighlightColor else primaryColor
        val wrappedMain = breakTextIntoLines(ln.text, lyricMainPaint, availableWidth)
        wrappedMain.forEach { subLine ->
            canvas.drawText(subLine, startX, cursorY, lyricMainPaint)
            cursorY += mainLineStep
        }
        if (includeTranslation && !ln.translation.isNullOrBlank()) {
            val wrappedTrans = breakTextIntoLines(ln.translation, lyricTransPaint, availableWidth)
            wrappedTrans.forEach { subTrans ->
                canvas.drawText(subTrans, startX, cursorY, lyricTransPaint)
                cursorY += transLineStep
            }
            cursorY += 6f
        }
        cursorY += 26f
    }
    return cursorY
}

private fun drawCanvasWaveformBars(
    canvas: Canvas,
    startX: Float,
    centerY: Float,
    accentColor: Int
) {
    val barPaint = newHiResPaint().apply {
        color = accentColor
        alpha = 205
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }
    val waveHeights = floatArrayOf(18f, 36f, 24f, 44f, 28f, 40f, 20f, 34f, 26f, 16f)
    var bx = startX
    for (wh in waveHeights) {
        canvas.drawLine(bx, centerY - wh / 2f, bx, centerY + wh / 2f, barPaint)
        bx += 15f
    }
}

private fun drawCanvasFooter(
    canvas: Canvas,
    width: Int,
    height: Int,
    text: String,
    accentColor: Int,
    showWaveform: Boolean,
    leftMargin: Float = 136f,
    rightMargin: Float = 136f
) {
    val footerY = height - 138f
    val waveformStartX = width - rightMargin - 150f
    val maxFooterTextWidth = if (showWaveform) {
        (waveformStartX - leftMargin - 24f).coerceAtLeast(360f)
    } else {
        (width - leftMargin - rightMargin).coerceAtLeast(480f)
    }
    val footerPaint = newHiResPaint().apply {
        color = accentColor
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    drawFittedTextBlock(
        canvas = canvas,
        text = text,
        x = leftMargin,
        firstBaselineY = footerY,
        maxWidth = maxFooterTextWidth,
        baseTextSize = 25f,
        minTextSize = 14f,
        maxLines = 1,
        paint = footerPaint
    )

    if (showWaveform) {
        drawCanvasWaveformBars(
            canvas = canvas,
            startX = waveformStartX,
            centerY = footerY - 10f,
            accentColor = accentColor
        )
    }
}
