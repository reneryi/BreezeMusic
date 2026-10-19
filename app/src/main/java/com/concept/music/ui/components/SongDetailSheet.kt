package com.concept.music.ui.components

import com.concept.music.ui.theme.conceptSheetSpringEntrance

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.concept.music.data.vip.VipManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.SongDetailInfo
import com.concept.music.ui.theme.ConceptPrimary
import com.concept.music.ui.theme.LocalIsDarkTheme
import com.concept.music.ui.theme.VipGold
import com.concept.music.ui.theme.conceptElevatedSurfaceColor
import com.concept.music.ui.theme.conceptSheetContainerColor
import com.concept.music.ui.theme.softClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongDetailSheet(
    detail: SongDetailInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val isDark = LocalIsDarkTheme.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val liveDetail by produceState(
        initialValue = detail,
        key1 = detail.song.id,
        key2 = detail.song.actualPlayQuality
    ) {
        value = runCatching { VipManager.music.fetchSongDetail(detail.song) }.getOrDefault(detail)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        windowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = conceptSheetContainerColor(),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(42.dp)
                    .height(4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .conceptSheetSpringEntrance()
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    tint = ConceptPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "音乐技术参数与详情",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = conceptElevatedSurfaceColor(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = liveDetail.song.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${liveDetail.singerName} · ${liveDetail.albumName}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                shape = RoundedCornerShape(22.dp),
                color = conceptElevatedSurfaceColor(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    val isHiResActive = liveDetail.audioQuality.startsWith("Hi-Res")
                    DetailRow(label = "音质规格", value = liveDetail.audioQuality, isHighlight = isHiResActive, isDark = isDark)
                    DetailRow(label = "采样精度", value = liveDetail.sampleRate, isDark = isDark)
                    DetailRow(label = "音频码率", value = liveDetail.bitRate, isDark = isDark)
                    DetailRow(label = "文件大小", value = liveDetail.fileSize, isDark = isDark)
                    if (liveDetail.song.durationMs > 0L) {
                        DetailRow(label = "音频时长", value = liveDetail.song.durationFormatted, isDark = isDark)
                    }
                    DetailRow(label = "所属专辑", value = liveDetail.albumName, isDark = isDark)
                    if (liveDetail.publishTime.isNotBlank() && liveDetail.publishTime != "未知日期") {
                        DetailRow(label = "发行时间", value = liveDetail.publishTime, isDark = isDark)
                    }
                    DetailRow(label = "唱片公司", value = liveDetail.company, isDark = isDark)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .softClickable {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Song Hash", liveDetail.fileHash))
                                Toast.makeText(context, "音频 Hash 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                            }
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "音频特征码",
                            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (liveDetail.fileHash.length > 16) liveDetail.fileHash.take(16) + "..." else liveDetail.fileHash,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = ConceptPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = "复制",
                                tint = ConceptPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    isHighlight: Boolean = false,
    isDark: Boolean = true
) {
    val goldColor = if (isDark) VipGold else Color(0xFFC47F00)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.Normal,
                color = if (isHighlight) goldColor else MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
