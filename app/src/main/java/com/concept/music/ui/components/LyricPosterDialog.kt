package com.concept.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.LyricLine
import com.concept.music.data.model.Song
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.VipGold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * [创新5] 「歌词金句黑胶海报生成器 (Lyric Vinyl Poster Card)」
 * 支持勾选 1~4 句双语/音译歌词，一键切换「幽邃微蓝黑胶卡」与「水彩浸润漫游卡」并导出高清海报至系统相册
 */
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
            setOfNotNull(idx, (idx + 1).takeIf { it in lines.indices })
        }
    }
    var selectedIndices by remember { mutableStateOf(initialSelected) }
    var posterStyle by remember { mutableIntStateOf(0) } // 0: 幽邃微蓝黑胶卡, 1: 水彩浸润漫游卡
    var includeTranslation by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }

    val selectedLines = remember(selectedIndices, lines) {
        selectedIndices.sorted().mapNotNull { lines.getOrNull(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("黑胶金句歌词海报", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = posterStyle == 0,
                        onClick = { posterStyle = 0 },
                        label = { Text("幽邃微蓝", fontSize = 11.sp) }
                    )
                    FilterChip(
                        selected = posterStyle == 1,
                        onClick = { posterStyle = 1 },
                        label = { Text("水彩浸润", fontSize = 11.sp) }
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 实时海报卡片预览
                val isDarkCard = posterStyle == 0
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isDarkCard) {
                                Brush.linearGradient(
                                    listOf(Color(0xFF0A0F1D), Color(0xFF13233F), Color(0xFF0D1424))
                                )
                            } else {
                                Brush.linearGradient(
                                    listOf(Color(0xFFEAF3FF), Color(0xFFDFF2F9), Color(0xFFF2ECFF))
                                )
                            }
                        )
                        .border(
                            1.dp,
                            if (isDarkCard) Color.White.copy(alpha = 0.14f) else ConceptPrimary.copy(alpha = 0.28f),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Album,
                                contentDescription = null,
                                tint = if (isDarkCard) ConceptPrimary else Color(0xFF1A66CC),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = if (isDarkCard) Color.White else Color(0xFF0F1D33),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = song.artist,
                                    fontSize = 11.sp,
                                    color = if (isDarkCard) Color.White.copy(alpha = 0.6f) else Color(0xFF4A5C75),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        HorizontalDivider(
                            color = if (isDarkCard) Color.White.copy(alpha = 0.1f) else Color(0xFF1A66CC).copy(alpha = 0.15f)
                        )

                        if (selectedLines.isEmpty()) {
                            Text(
                                text = "请在下方勾选 1~4 句心仪歌词",
                                fontSize = 13.sp,
                                color = if (isDarkCard) Color.White.copy(alpha = 0.5f) else Color(0xFF4A5C75)
                            )
                        } else {
                            selectedLines.forEach { ln ->
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    if (includeTranslation && !ln.phonetic.isNullOrBlank()) {
                                        Text(
                                            text = ln.phonetic,
                                            fontSize = 10.sp,
                                            color = if (isDarkCard) ConceptPrimary.copy(alpha = 0.8f) else Color(0xFF2B7FFF)
                                        )
                                    }
                                    Text(
                                        text = ln.text,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = if (isDarkCard) Color.White else Color(0xFF0F1D33)
                                    )
                                    if (includeTranslation && !ln.translation.isNullOrBlank()) {
                                        Text(
                                            text = ln.translation,
                                            fontSize = 11.sp,
                                            color = if (isDarkCard) Color.White.copy(alpha = 0.68f) else Color(0xFF4A5C75)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "BreezeMusic · 酷狗概念版黑胶典藏",
                            fontSize = 10.sp,
                            color = if (isDarkCard) VipGold.copy(alpha = 0.85f) else Color(0xFF1A66CC).copy(alpha = 0.75f)
                        )
                    }
                }

                // 歌词行多选列表
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "勾选歌词金句 (${selectedIndices.size}/4)",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("含译文/注音", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = includeTranslation,
                            onCheckedChange = { includeTranslation = it }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                ) {
                    itemsIndexed(lines) { idx, line ->
                        val checked = idx in selectedIndices
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    selectedIndices = if (checked) {
                                        selectedIndices - idx
                                    } else if (selectedIndices.size < 4) {
                                        selectedIndices + idx
                                    } else {
                                        selectedIndices
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (checked) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (checked) ConceptPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = line.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (checked) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!line.translation.isNullOrBlank()) {
                                    Text(
                                        text = line.translation,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedLines.isEmpty()) return@Button
                    saving = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                saveLyricPosterBitmap(
                                    context = context,
                                    song = song,
                                    lines = selectedLines,
                                    isDarkStyle = posterStyle == 0,
                                    includeTranslation = includeTranslation
                                )
                            }.getOrDefault(false)
                        }
                        saving = false
                        Toast.makeText(
                            context,
                            if (ok) "歌词黑胶海报已保存至系统相册 (Pictures/ConceptMusic)" else "保存海报失败",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (ok) onDismiss()
                    }
                },
                enabled = selectedLines.isNotEmpty() && !saving,
                colors = ButtonDefaults.buttonColors(containerColor = ConceptPrimary)
            ) {
                Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (saving) "渲染中..." else "保存海报", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

private fun saveLyricPosterBitmap(
    context: Context,
    song: Song,
    lines: List<LyricLine>,
    isDarkStyle: Boolean,
    includeTranslation: Boolean
): Boolean {
    val width = 1080
    val height = 1480
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // 1. 背景绘制（幽邃微蓝 vs 水彩浸润）
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            if (isDarkStyle) intArrayOf(0xFF0A0E17.toInt(), 0xFF142440.toInt(), 0xFF0B1220.toInt())
            else intArrayOf(0xFFF2F8FF.toInt(), 0xFFDCEEFB.toInt(), 0xFFEDE7FA.toInt()),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // 2. 内嵌黑胶卡片框
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xCC121A2B.toInt() else 0xE6FFFFFF.toInt()
    }
    val cardRect = RectF(72f, 96f, width - 72f, height - 96f)
    canvas.drawRoundRect(cardRect, 56f, 56f, cardPaint)

    // 3. 顶部黑胶纹理圆环与标题
    val vinylPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xFF2B8CFF.toInt() else 0xFF1B66CC.toInt()
    }
    canvas.drawCircle(164f, 210f, 36f, vinylPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xFFFFFFFF.toInt() else 0xFF0F1D33.toInt()
        textSize = 46f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xB3FFFFFF.toInt() else 0xFF4A5C75.toInt()
        textSize = 32f
    }
    canvas.drawText(song.title.take(18), 228f, 200f, titlePaint)
    canvas.drawText("${song.artist} · ${song.album.ifBlank { "概念版精选" }}".take(24), 228f, 248f, artistPaint)

    // 4. 歌词金句主视觉排版
    val lyricMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xFFF2F6FC.toInt() else 0xFF101E34.toInt()
        textSize = 48f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val lyricSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xFF7AB8FF.toInt() else 0xFF2B7FFF.toInt()
        textSize = 32f
    }

    var cursorY = 400f
    for (ln in lines) {
        if (includeTranslation && !ln.phonetic.isNullOrBlank()) {
            canvas.drawText(ln.phonetic.take(34), 132f, cursorY, lyricSubPaint)
            cursorY += 48f
        }
        canvas.drawText(ln.text.take(22), 132f, cursorY, lyricMainPaint)
        cursorY += 64f
        if (includeTranslation && !ln.translation.isNullOrBlank()) {
            canvas.drawText(ln.translation.take(28), 132f, cursorY, artistPaint)
            cursorY += 56f
        }
        cursorY += 38f
    }

    // 5. 底部落款印记
    val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = if (isDarkStyle) 0xFFE5A93B.toInt() else 0xFF1B66CC.toInt()
        textSize = 28f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    canvas.drawText("BreezeMusic · 酷狗概念版黑胶歌词典藏", 132f, height - 160f, footerPaint)

    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "BreezeLyric_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ConceptMusic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
    return try {
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        } ?: false
        if (Build.VERSION.SDK_INT >= 29) {
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        }
        true
    } catch (_: Exception) {
        resolver.delete(uri, null, null)
        false
    }
}
