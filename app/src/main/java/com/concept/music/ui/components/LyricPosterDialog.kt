package com.concept.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 深度重构：「高定歌词海报工坊 (Lyrics Poster Studio)」
 * 彻底重塑 5 款构图、排版、装饰元素与视觉气质截然不同的独立海报设计：
 * 1. 黑胶典藏 (VINYL_ARCHIVE)：暗调录音室 + 大尺寸探出式同心纹黑胶唱盘 + 鎏金转速铭牌 + 左侧金句引线
 * 2. 先锋画报 (EDITORIAL_MAGAZINE)：全画幅沉浸封面背景 + 时尚杂志大刊头 + 巨型半透明引号 + 杂志社论排版
 * 3. 东方雅韵 (MINIMAL_GALLERY)：温润宣纸底纹 + 古典外粗内细双线画框 + 圆相封面 + 衬线宋刻体 + 朱砂方印「微风」
 * 4. 复古拍立得 (RETRO_POLAROID)：经典宽下巴相纸 + 大画幅方形取景窗 + 胶片橙色日期戳 ('26 10 22) + 手写感签注
 * 5. 赛博流光 (AURORA_GLASS)：未来感霓虹终端 + 四角机能取景框 + 首句荧光高亮胶囊 + 模拟播放时间轴进度条
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
        runCatching { LocalDate.now().toString().replace("-", ".") }.getOrDefault("2026.10.22")
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
                // 1. 顶部工作台标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "高定歌词海报工坊",
                                color = Color.White,
                                fontSize = 16.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "五款独立艺术构图 · 高清渲染导出",
                                color = Color.White.copy(alpha = 0.56f),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                if (selectedLines.isEmpty()) return@FilledTonalButton
                                saving = true
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) {
                                        runCatching {
                                            renderAndSavePosterToGallery(
                                                context = context,
                                                song = song,
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
                            },
                            enabled = selectedLines.isNotEmpty() && !saving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 11.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "分享", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("分享", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (selectedLines.isEmpty()) return@Button
                                saving = true
                                scope.launch {
                                    val uri = withContext(Dispatchers.IO) {
                                        runCatching {
                                            renderAndSavePosterToGallery(
                                                context = context,
                                                song = song,
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
                                        if (uri != null) "高清海报已保存至相册 (Pictures/ConceptMusic)" else "保存海报失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (uri != null) onDismiss()
                                }
                            },
                            enabled = selectedLines.isNotEmpty() && !saving,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(template.accentColor)),
                            contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Download,
                                contentDescription = "保存",
                                tint = if (template == PosterTemplateStyle.AURORA_GLASS || template == PosterTemplateStyle.VINYL_ARCHIVE) Color(0xFF0A0F18) else Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (saving) "渲染中..." else "保存海报",
                                color = if (template == PosterTemplateStyle.AURORA_GLASS || template == PosterTemplateStyle.VINYL_ARCHIVE) Color(0xFF0A0F18) else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 2. 实时海报高定卡片预览区（五款截然不同的构图）
                PosterLivePreviewCard(
                    song = song,
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
            color = if (active) ConceptPrimary else Color.White.copy(alpha = 0.65f),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
                                    PosterCoverImage(song = song, accent = accent)
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
                                    fontSize = 15.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} · 《${song.album.ifBlank { "概念典藏" }}》",
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
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
                    if (song.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
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
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(accent.copy(alpha = 0.25f))
                                    ) {
                                        PosterCoverImage(song = song, accent = accent)
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = song.title,
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "BY ${song.artist.uppercase()}",
                                            color = accent,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (showWaveform) {
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
                        // 顶部：东方美学圆相封面 + 竖排意境引题
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
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
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Serif,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist}  ·  ${song.album.ifBlank { "雅集" }}",
                                    color = textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Serif,
                                    maxLines = 1,
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
                                PosterCoverImage(song = song, accent = accent)
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
                                    text = "$dateStr · 概念版典藏",
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
                            PosterCoverImage(song = song, accent = accent)
                            // 胶片暗角遮罩
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.45f)
                                            )
                                        )
                                    )
                            )
                            // 左下角曲目胶片字幕
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = song.title,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    color = Color.White.copy(alpha = 0.82f),
                                    fontSize = 10.5.sp,
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
                                text = "INSTANT FILM · 《${song.album.ifBlank { song.title }}》",
                                color = textSecondary,
                                fontSize = 9.5.sp,
                                fontStyle = FontStyle.Italic,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            if (showWaveform) {
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
                        // 左上
                        drawLine(c, Offset(0f, 0f), Offset(len, 0f), stroke)
                        drawLine(c, Offset(0f, 0f), Offset(0f, len), stroke)
                        // 右上
                        drawLine(c, Offset(size.width - len, 0f), Offset(size.width, 0f), stroke)
                        drawLine(c, Offset(size.width, 0f), Offset(size.width, len), stroke)
                        // 左下
                        drawLine(c, Offset(0f, size.height - len), Offset(0f, size.height), stroke)
                        drawLine(c, Offset(0f, size.height), Offset(len, size.height), stroke)
                        // 右下
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
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.5.dp, accent, RoundedCornerShape(12.dp))
                                ) {
                                    PosterCoverImage(song = song, accent = accent)
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        color = textPrimary,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Black,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${song.artist} // HI-RES STREAM",
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
                                selectedLines.take(5).forEachIndexed { idx, ln ->
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
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
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
                                                color = textSecondary.copy(alpha = 0.8f),
                                                fontSize = 10.5.sp,
                                                textAlign = textAlign,
                                                maxLines = 1,
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
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterCoverImage(song: Song, accent: Color) {
    if (song.coverUrl.isNotBlank()) {
        AsyncImage(
            model = song.coverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
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
                            maxLines = 1,
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
 * 高清 1080x1560 海报离屏渲染引擎：严格为五大模板各自绘制独立构图与专属细节
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
    val width = 1080
    val height = 1560
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 加载高清封面位图
    val coverBitmap: Bitmap? = if (song.coverUrl.isNotBlank()) {
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(song.coverUrl)
                .size(640, 640)
                .allowHardware(false)
                .build()
            val res = context.imageLoader.execute(req)
            ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    } else null

    // 1. 底层背景
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            template.bgColors,
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    val cardRect = RectF(64f, 76f, width - 64f, height - 76f)
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.cardColor }

    when (template) {
        PosterTemplateStyle.VINYL_ARCHIVE -> {
            canvas.drawRoundRect(cardRect, 52f, 52f, cardPaint)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                alpha = 95
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRoundRect(cardRect, 52f, 52f, borderPaint)

            // 探出式黑胶唱盘 + 专辑封套
            val discCenterX = 330f
            val discCenterY = 250f
            val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0A0D14.toInt() }
            canvas.drawCircle(discCenterX, discCenterY, 102f, vinylPaint)
            val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                alpha = 110
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
            }
            canvas.drawCircle(discCenterX, discCenterY, 82f, groovePaint)
            canvas.drawCircle(discCenterX, discCenterY, 62f, groovePaint)
            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.accentColor }
            canvas.drawCircle(discCenterX, discCenterY, 30f, labelPaint)

            val sleeveRect = RectF(132f, 146f, 340f, 354f)
            drawClippedCover(canvas, coverBitmap, sleeveRect, 26f, template.accentColor)

            // 右侧标题
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                textSize = 24f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.primaryTextColor
                textSize = 48f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.secondaryTextColor
                textSize = 30f
            }
            canvas.drawText("MASTER VINYL · 45 RPM", 468f, 200f, tagPaint)
            canvas.drawText(song.title.take(11), 468f, 264f, titlePaint)
            canvas.drawText("${song.artist} · 《${song.album.ifBlank { "概念典藏" }}》".take(16), 468f, 316f, subPaint)

            // 鎏金分割线
            val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                alpha = 90
                strokeWidth = 2.5f
            }
            canvas.drawLine(132f, 412f, width - 132f, 412f, divPaint)

            // 左侧金色引线 + 歌词
            if (!centerAlign) {
                val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = template.accentColor
                    strokeWidth = 8f
                    strokeCap = Paint.Cap.ROUND
                }
                canvas.drawLine(140f, 480f, 140f, 1180f, barPaint)
            }
            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 176f,
                startY = 530f,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )
            drawCanvasFooter(canvas, width, height, "CAT. NO. BM-${song.id.take(6).uppercase()} · HIGH FIDELITY", template.accentColor, showWaveform)
        }

        PosterTemplateStyle.EDITORIAL_MAGAZINE -> {
            // 全画幅背景封面
            canvas.save()
            val clip = Path().apply { addRoundRect(cardRect, 52f, 52f, Path.Direction.CW) }
            canvas.clipPath(clip)
            if (coverBitmap != null) {
                canvas.drawBitmap(coverBitmap, null, cardRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            }
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, cardRect.top, 0f, cardRect.bottom,
                    intArrayOf(0xB80E0816.toInt(), 0xD8140C20.toInt(), 0xF50B0612.toInt()),
                    floatArrayOf(0f, 0.5f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(cardRect, scrimPaint)

            // 巨型半透明引号
            val quotePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                alpha = 55
                textSize = 280f
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            }
            canvas.drawText("“", 118f, 480f, quotePaint)

            // 杂志大刊头
            val mastheadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.primaryTextColor
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subMastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )

            // 底部杂志社论署名栏
            val bottomBox = RectF(124f, height - 280f, width - 124f, height - 130f)
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
            canvas.drawRoundRect(bottomBox, 32f, 32f, boxPaint)
            val miniCoverRect = RectF(152f, height - 256f, 254f, height - 154f)
            drawClippedCover(canvas, coverBitmap, miniCoverRect, 20f, template.accentColor)

            val bTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.primaryTextColor
                textSize = 38f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val bArtistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                textSize = 26f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText(song.title.take(15), 284f, height - 210f, bTitlePaint)
            canvas.drawText("BY ${song.artist.uppercase()}".take(22), 284f, height - 168f, bArtistPaint)
            canvas.restore()
        }

        PosterTemplateStyle.MINIMAL_GALLERY -> {
            // 宣纸外粗内细双线古典画框
            canvas.drawRoundRect(cardRect, 36f, 36f, cardPaint)
            val outerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF44403C.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 5f
            }
            val innerStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA8A29E.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            canvas.drawRoundRect(cardRect, 36f, 36f, outerStroke)
            val innerRect = RectF(cardRect.left + 18f, cardRect.top + 18f, cardRect.right - 18f, cardRect.bottom - 18f)
            canvas.drawRoundRect(innerRect, 26f, 26f, innerStroke)

            val serifBold = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            val serifNormal = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

            val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.secondaryTextColor
                textSize = 26f
                typeface = serifNormal
            }
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.primaryTextColor
                textSize = 50f
                typeface = serifBold
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.secondaryTextColor
                textSize = 30f
                typeface = serifNormal
            }
            canvas.drawText("「 岁 月 留 声 · 词 境 」", 136f, 190f, headerPaint)
            canvas.drawText(song.title.take(13), 136f, 262f, titlePaint)
            canvas.drawText("${song.artist} · 《${song.album.ifBlank { "雅集" }}》".take(18), 136f, 314f, subPaint)

            // 右上圆相封面
            val circleRect = RectF(width - 310f, 156f, width - 142f, 324f)
            drawClippedCover(canvas, coverBitmap, circleRect, 84f, template.accentColor)
            val circleBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF292524.toInt()
                style = Paint.Style.STROKE
                strokeWidth = 4f
            }
            canvas.drawOval(circleRect, circleBorder)

            val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFD6D3D1.toInt()
                strokeWidth = 2f
            }
            canvas.drawLine(136f, 376f, width - 136f, 376f, linePaint)

            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 136f,
                startY = 485f,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = serifBold
            )

            // 朱砂红印章
            val sealRect = RectF(136f, height - 196f, 200f, height - 132f)
            val sealPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.accentColor }
            canvas.drawRoundRect(sealRect, 10f, 10f, sealPaint)
            val sealTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                textSize = 26f
                typeface = serifBold
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("微风", sealRect.centerX(), sealRect.centerY() + 10f, sealTextPaint)

            val footTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.secondaryTextColor
                textSize = 28f
                typeface = serifNormal
            }
            canvas.drawText("$dateStr · 酷狗概念版典藏", 222f, height - 154f, footTextPaint)
        }

        PosterTemplateStyle.RETRO_POLAROID -> {
            // 经典拍立得相纸
            val polaroidRect = RectF(88f, 92f, width - 88f, height - 92f)
            canvas.drawRoundRect(polaroidRect, 28f, 28f, cardPaint)

            // 大画幅方形取景窗
            val photoRect = RectF(140f, 144f, width - 140f, 770f)
            drawClippedCover(canvas, coverBitmap, photoRect, 16f, template.accentColor)

            // 胶片橙色日期戳
            val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFF8A2B.toInt()
                textSize = 34f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                textAlign = Paint.Align.RIGHT
            }
            canvas.drawText("'${dateStr.replace(".", " ")}", photoRect.right - 28f, photoRect.bottom - 28f, stampPaint)

            // 相纸下巴区歌词
            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 148f,
                startY = 860f,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
            )

            drawCanvasFooter(
                canvas = canvas,
                width = width,
                height = height,
                text = "INSTANT FILM · ${song.title} — ${song.artist}",
                accentColor = template.accentColor,
                showWaveform = showWaveform
            )
        }

        PosterTemplateStyle.AURORA_GLASS -> {
            canvas.drawRoundRect(cardRect, 52f, 52f, cardPaint)
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.primaryTextColor
                textSize = 46f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                textSize = 26f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            }
            canvas.drawText(song.title.take(14), 308f, 216f, titlePaint)
            canvas.drawText("${song.artist} // 24-BIT AUDIO STREAM".take(26), 308f, 268f, subPaint)

            // 首句荧光高亮底框
            if (lines.isNotEmpty()) {
                val hlRect = RectF(124f, 410f, width - 124f, if (includeTranslation && !lines[0].translation.isNullOrBlank()) 545f else 495f)
                val hlFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = template.accentColor
                    alpha = 38
                }
                canvas.drawRoundRect(hlRect, 24f, 24f, hlFill)
            }

            drawCanvasLyrics(
                canvas = canvas,
                lines = lines,
                includeTranslation = includeTranslation,
                centerAlign = centerAlign,
                startX = if (centerAlign) width / 2f else 148f,
                startY = 472f,
                primaryColor = template.primaryTextColor,
                secondaryColor = template.secondaryTextColor,
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            )

            // 底部模拟时间轴进度条
            val barY = height - 210f
            val trackBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x33FFFFFF
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            val trackFg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.accentColor
                strokeWidth = 8f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(240f, barY, width - 240f, barY, trackBg)
            canvas.drawLine(240f, barY, 640f, barY, trackFg)
            canvas.drawCircle(640f, barY, 12f, trackFg)

            drawCanvasFooter(canvas, width, height, "CYBER AUDIO // BREEZE MUSIC · $dateStr", template.accentColor, showWaveform)
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
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
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
    }
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
        canvas.drawBitmap(coverBitmap, null, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fallbackColor
            alpha = 65
        }
        canvas.drawRoundRect(rect, cornerRadius, cornerRadius, placeholderPaint)
    }
}

private fun drawCanvasLyrics(
    canvas: Canvas,
    lines: List<LyricLine>,
    includeTranslation: Boolean,
    centerAlign: Boolean,
    startX: Float,
    startY: Float,
    primaryColor: Int,
    secondaryColor: Int,
    typeface: Typeface
) {
    val lyricMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        textSize = if (lines.size <= 3) 48f else 40f
        this.typeface = typeface
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    val lyricTransPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryColor
        textSize = 28f
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    var cursorY = startY
    lines.take(6).forEach { ln ->
        canvas.drawText(ln.text.take(22), startX, cursorY, lyricMainPaint)
        cursorY += if (lines.size <= 3) 62f else 52f
        if (includeTranslation && !ln.translation.isNullOrBlank()) {
            canvas.drawText(ln.translation.take(28), startX, cursorY, lyricTransPaint)
            cursorY += 42f
        }
        cursorY += 30f
    }
}

private fun drawCanvasFooter(
    canvas: Canvas,
    width: Int,
    height: Int,
    text: String,
    accentColor: Int,
    showWaveform: Boolean
) {
    val footerY = height - 138f
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accentColor
        textSize = 25f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(text.take(36), 136f, footerY, footerPaint)

    if (showWaveform) {
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            alpha = 200
            strokeWidth = 6f
            strokeCap = Paint.Cap.ROUND
        }
        val waveHeights = floatArrayOf(18f, 36f, 24f, 44f, 28f, 40f, 20f, 34f, 26f, 16f)
        var bx = width - 290f
        for (wh in waveHeights) {
            canvas.drawLine(bx, footerY - 10f - wh / 2f, bx, footerY - 10f + wh / 2f, barPaint)
            bx += 15f
        }
    }
}
