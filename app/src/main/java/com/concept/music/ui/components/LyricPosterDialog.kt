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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.concept.music.ui.theme.ConceptCyanAccent
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.softCardClickable
import com.concept.music.ui.theme.softClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 推翻重构：「高定歌词海报工坊 (Lyrics Poster Studio)」
 * 提供 5 款极具设计感的海报视觉模板：
 * 1. 黑胶典藏馆 (VINYL_ARCHIVE)：半露黑胶唱盘 + 专辑封套 + 金句排版
 * 2. 先锋音乐画报 (EDITORIAL_MAGAZINE)：全画幅大片构图 + 杂志刊头 + 巨型引号金句
 * 3. 极简艺术留白 (MINIMAL_GALLERY)：美术馆级纯净留白 + 声波条码 + 现代主义字型
 * 4. 复古胶片拍立得 (RETRO_POLAROID)：温暖胶片相纸 + 日期印记 + 叙事排版
 * 5. 极光毛玻璃 (AURORA_GLASS)：深海流光渐变 + 磨砂玻璃卡片
 * 支持最多 6 句歌词自选、居中/居左对齐切换、译文/注音开关、声波条码开关、高清导出相册与一键系统分享。
 */
enum class PosterTemplateStyle(
    val label: String,
    val tag: String,
    val bgColors: IntArray,
    val cardColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val accentColor: Int
) {
    VINYL_ARCHIVE(
        label = "黑胶典藏",
        tag = "VINYL ARCHIVE",
        bgColors = intArrayOf(0xFF080C14.toInt(), 0xFF132238.toInt(), 0xFF0A0F1A.toInt()),
        cardColor = 0xE6101827.toInt(),
        primaryTextColor = 0xFFF8FAFC.toInt(),
        secondaryTextColor = 0xFF94A3B8.toInt(),
        accentColor = 0xFFE5A93B.toInt()
    ),
    EDITORIAL_MAGAZINE(
        label = "先锋画报",
        tag = "CONCEPT EDITORIAL",
        bgColors = intArrayOf(0xFF16121E.toInt(), 0xFF2A1B3D.toInt(), 0xFF0E0B14.toInt()),
        cardColor = 0xDF181424.toInt(),
        primaryTextColor = 0xFFFFFFFF.toInt(),
        secondaryTextColor = 0xFFCBD5E1.toInt(),
        accentColor = 0xFFFF6B9D.toInt()
    ),
    MINIMAL_GALLERY(
        label = "极简画廊",
        tag = "GALLERY EDITION",
        bgColors = intArrayOf(0xFFF4F1EA.toInt(), 0xFFEBE6DC.toInt(), 0xFFF7F5F0.toInt()),
        cardColor = 0xFFFFFFFF.toInt(),
        primaryTextColor = 0xFF18181B.toInt(),
        secondaryTextColor = 0xFF71717A.toInt(),
        accentColor = 0xFF1D4ED8.toInt()
    ),
    RETRO_POLAROID(
        label = "复古拍立得",
        tag = "INSTANT FILM",
        bgColors = intArrayOf(0xFF1E1B18.toInt(), 0xFF2C2520.toInt(), 0xFF171412.toInt()),
        cardColor = 0xFFF9F6F0.toInt(),
        primaryTextColor = 0xFF1C1917.toInt(),
        secondaryTextColor = 0xFF57534E.toInt(),
        accentColor = 0xFFD97706.toInt()
    ),
    AURORA_GLASS(
        label = "极光毛玻璃",
        tag = "AURORA GLASS",
        bgColors = intArrayOf(0xFF071A24.toInt(), 0xFF0F3142.toInt(), 0xFF131B36.toInt()),
        cardColor = 0xCC112233.toInt(),
        primaryTextColor = 0xFFF0FDFA.toInt(),
        secondaryTextColor = 0xFF99F6E4.toInt(),
        accentColor = 0xFF2CE5B5.toInt()
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
        runCatching { LocalDate.now().toString().replace("-", ".") }.getOrDefault("2026.10.20")
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
                .background(Color(0xF2080C14))
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                // 1. 顶部工作台标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "关闭",
                                tint = Color.White
                            )
                        }
                        Column {
                            Text(
                                text = "歌词海报工坊",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "选择歌词与模板导出分享",
                                color = Color.White.copy(alpha = 0.58f),
                                fontSize = 11.sp
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
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color.White.copy(alpha = 0.12f),
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Share, contentDescription = "分享", modifier = Modifier.size(15.dp))
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
                                        if (uri != null) "高清海报已保存至系统相册 (Pictures/ConceptMusic)" else "保存海报失败",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    if (uri != null) onDismiss()
                                }
                            },
                            enabled = selectedLines.isNotEmpty() && !saving,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = "保存", modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (saving) "渲染中..." else "保存相册", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. 实时海报高定卡片预览区
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
                        .weight(0.52f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 3. 五大模板选择器 + 排版细节工具栏
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
                            color = if (isSelected) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.07f),
                            modifier = Modifier
                                .border(
                                    width = if (isSelected) 1.2.dp else 0.dp,
                                    color = if (isSelected) accent else Color.Transparent,
                                    shape = RoundedCornerShape(14.dp)
                                )
                                .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                    template = style
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(accent)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = style.label,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.78f),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
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
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PosterMiniToggleChip(
                            label = if (centerAlign) "居中排版" else "左对齐",
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
                    shape = RoundedCornerShape(18.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.48f)
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
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (checked) ConceptPrimary.copy(alpha = 0.18f) else Color.Transparent,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .softCardClickable(shape = RoundedCornerShape(12.dp)) {
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
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (checked) ConceptPrimary else Color.White.copy(alpha = 0.35f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = line.text,
                                                color = if (checked) Color.White else Color.White.copy(alpha = 0.78f),
                                                fontSize = 13.5.sp,
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
    val bgBrush = Brush.linearGradient(
        colors = template.bgColors.map { Color(it) }
    )
    val cardColor = Color(template.cardColor)
    val textPrimary = Color(template.primaryTextColor)
    val textSecondary = Color(template.secondaryTextColor)
    val accent = Color(template.accentColor)
    val align = if (centerAlign) Alignment.CenterHorizontally else Alignment.Start
    val textAlign = if (centerAlign) TextAlign.Center else TextAlign.Start

    Box(
        modifier = modifier
            .shadow(20.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(bgBrush)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = cardColor,
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = 1.dp,
                    color = accent.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(20.dp)
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // 顶部：封面 + 黑胶/画报头标
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (template == PosterTemplateStyle.VINYL_ARCHIVE) {
                                // 半露黑胶盘装饰
                                Box(
                                    modifier = Modifier
                                        .padding(start = 18.dp)
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF141820))
                                        .border(1.dp, accent.copy(alpha = 0.45f), CircleShape)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(if (template == PosterTemplateStyle.MINIMAL_GALLERY) 6.dp else 12.dp))
                                    .background(accent.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (song.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = song.coverUrl,
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(Icons.Rounded.Album, contentDescription = null, tint = accent)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                color = textPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${song.artist} · ${song.album.ifBlank { "概念典藏" }}",
                                color = textSecondary,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = template.tag,
                            color = accent,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                        Text(
                            text = dateStr,
                            color = textSecondary.copy(alpha = 0.75f),
                            fontSize = 9.sp
                        )
                    }
                }

                // 中部：勾选歌词金句展示
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 8.dp),
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
                        selectedLines.take(5).forEach { ln ->
                            Column(
                                horizontalAlignment = align,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Text(
                                    text = ln.text,
                                    color = textPrimary,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = if (selectedLines.size <= 2) 17.sp else 14.5.sp,
                                    lineHeight = 20.sp,
                                    textAlign = textAlign,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (includeTranslation && !ln.translation.isNullOrBlank()) {
                                    Text(
                                        text = ln.translation,
                                        color = textSecondary,
                                        fontSize = 11.sp,
                                        textAlign = textAlign,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                // 底部：声波条码与概念版印记
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BREEZE MUSIC · 酷狗概念版高定歌词卡",
                        color = accent,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )

                    if (showWaveform) {
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
                                        .background(accent.copy(alpha = 0.75f))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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

    // 尝试加载高清封面位图
    val coverBitmap: Bitmap? = if (song.coverUrl.isNotBlank()) {
        runCatching {
            val req = ImageRequest.Builder(context)
                .data(song.coverUrl)
                .size(360, 360)
                .allowHardware(false)
                .build()
            val res = context.imageLoader.execute(req)
            ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    } else null

    // 1. 绘制渐变背景
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            template.bgColors,
            floatArrayOf(0f, 0.52f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // 2. 绘制主海报卡片容器
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.cardColor
    }
    val cardRect = RectF(68f, 84f, width - 68f, height - 84f)
    canvas.drawRoundRect(cardRect, 56f, 56f, cardPaint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.accentColor
        alpha = 75
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(cardRect, 56f, 56f, borderPaint)

    // 3. 顶部封面与曲目元数据
    val coverRect = RectF(132f, 152f, 292f, 312f)
    if (template == PosterTemplateStyle.VINYL_ARCHIVE) {
        val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF10141D.toInt() }
        canvas.drawCircle(256f, 232f, 76f, vinylPaint)
        val groovePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.accentColor
            alpha = 90
            style = Paint.Style.STROKE
            strokeWidth = 2.5f
        }
        canvas.drawCircle(256f, 232f, 54f, groovePaint)
    }
    if (coverBitmap != null) {
        canvas.save()
        val clipPath = Path().apply {
            addRoundRect(coverRect, 28f, 28f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
        canvas.drawBitmap(coverBitmap, null, coverRect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        canvas.restore()
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = template.accentColor; alpha = 60 }
        canvas.drawRoundRect(coverRect, 28f, 28f, placeholderPaint)
    }

    val titleX = if (template == PosterTemplateStyle.VINYL_ARCHIVE) 364f else 326f
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.primaryTextColor
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.secondaryTextColor
        textSize = 30f
    }
    val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.accentColor
        textSize = 22f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText(song.title.take(14), titleX, 214f, titlePaint)
    canvas.drawText("${song.artist} · ${song.album.ifBlank { "概念典藏" }}".take(20), titleX, 262f, subPaint)
    canvas.drawText("${template.tag} // $dateStr", titleX, 300f, tagPaint)

    // 分割线
    val divPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.secondaryTextColor
        alpha = 45
        strokeWidth = 2f
    }
    canvas.drawLine(132f, 360f, width - 132f, 360f, divPaint)

    // 4. 绘制歌词金句主体
    val lyricMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.primaryTextColor
        textSize = if (lines.size <= 3) 50f else 42f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    val lyricTransPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.secondaryTextColor
        textSize = 30f
        textAlign = if (centerAlign) Paint.Align.CENTER else Paint.Align.LEFT
    }
    val anchorX = if (centerAlign) width / 2f else 136f

    var cursorY = 465f
    lines.take(6).forEach { ln ->
        canvas.drawText(ln.text.take(22), anchorX, cursorY, lyricMainPaint)
        cursorY += if (lines.size <= 3) 64f else 54f
        if (includeTranslation && !ln.translation.isNullOrBlank()) {
            canvas.drawText(ln.translation.take(28), anchorX, cursorY, lyricTransPaint)
            cursorY += 46f
        }
        cursorY += 36f
    }

    // 5. 底部声波条码与品牌印记
    val footerY = height - 148f
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = template.accentColor
        textSize = 26f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("BREEZE MUSIC · 酷狗概念版高定歌词典藏", 136f, footerY, footerPaint)

    if (showWaveform) {
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.accentColor
            alpha = 190
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

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BreezePoster_${System.currentTimeMillis()}.png")
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
