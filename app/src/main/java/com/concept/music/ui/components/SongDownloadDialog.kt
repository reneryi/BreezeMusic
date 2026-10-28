package com.concept.music.ui.components

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.MusicDownloadManager
import com.concept.music.ui.theme.*

/**
 * 单曲多音质下载选择弹窗（参考 lisalee23042605/kgmusicbot & MakcRe/KuGouMusicApi）：
 * 1. 自动异步查询 `/v2/get_res_privilege/lite` 补全该歌曲所有真实可用音质档位（Hi-Res / SQ 无损 FLAC / 高品 320K / 标准 128K）；
 * 2. 展示当前「下载保存路径」、「歌词保存路径」及「高清封面内嵌 / 歌词内嵌 / 独立 .lrc 保存」开关，支持在下载前直接微调；
 * 3. 自动检查并申请手机存储运行时权限 (`WRITE_EXTERNAL_STORAGE` / `READ_MEDIA_AUDIO`)。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongDownloadDialog(
    song: Song,
    onDismiss: () -> Unit,
    onOpenDownloadSettings: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val downloadConfig by MusicDownloadManager.config.collectAsState()
    var enrichedSong by remember(song.id, song.hash) { mutableStateOf(song) }
    var resolvingQualities by remember(song.id, song.hash) { mutableStateOf(true) }

    LaunchedEffect(song.id, song.hash) {
        resolvingQualities = true
        val fetched = runCatching {
            VipManager.music.enrichSongsPrivilegeLite(listOf(song)).firstOrNull()
        }.getOrNull()
        if (fetched != null) {
            enrichedSong = fetched
        }
        resolvingQualities = false
    }

    val availableQualities = remember(enrichedSong) {
        val list = enrichedSong.effectiveQualities
        if (list.isEmpty()) listOf(AudioQuality.STANDARD, AudioQuality.HIGH, AudioQuality.LOSSLESS, AudioQuality.HI_RES)
        else list.sortedByDescending { it.ordinal }
    }

    var selectedQuality by remember(availableQualities, downloadConfig.defaultDownloadQuality) {
        val pref = downloadConfig.defaultDownloadQuality
        mutableStateOf(
            if (pref in availableQualities) pref else availableQualities.firstOrNull() ?: AudioQuality.HIGH
        )
    }

    var pendingQualityAfterPerm by remember { mutableStateOf<AudioQuality?>(null) }
    val storagePermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val allGranted = resultMap.values.all { it }
        val targetQ = pendingQualityAfterPerm ?: selectedQuality
        pendingQualityAfterPerm = null
        if (allGranted || AppPermissionManager.hasMusicDownloadStoragePermission(context)) {
            MusicDownloadManager.enqueueDownload(context, enrichedSong, targetQ)
            onDismiss()
        } else {
            Toast.makeText(
                context,
                "未授予存储权限，将尝试通过系统 MediaStore 保存至公共音乐目录",
                Toast.LENGTH_SHORT
            ).show()
            MusicDownloadManager.enqueueDownload(context, enrichedSong, targetQ)
            onDismiss()
        }
    }

    val baseSheetBg = conceptSheetContainerColor()
    val textPrimary = MaterialTheme.colorScheme.onSurface
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val panelShape = RoundedCornerShape(26.dp)

    ConceptAnimatedOverlaySheet(
        onDismiss = onDismiss,
        isLandscape = true,
        scrimAlpha = 0.56f
    ) {
        Surface(
            shape = panelShape,
            color = baseSheetBg.copy(alpha = 0.98f),
            contentColor = textPrimary,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(max = 430.dp)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(ConceptPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "下载歌曲到本地",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.5.sp,
                                color = textPrimary
                            )
                            Text(
                                text = "${enrichedSong.artist} - ${enrichedSong.title}",
                                fontSize = 12.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = requestDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // 音质档位选择区
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "选择下载音质（无该档位时自动平滑降级）",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textSecondary
                        )
                        if (resolvingQualities) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = ConceptPrimary
                            )
                        }
                    }

                    listOf(
                        AudioQuality.HI_RES,
                        AudioQuality.LOSSLESS,
                        AudioQuality.HIGH,
                        AudioQuality.STANDARD
                    ).forEach { quality ->
                        val isAvailable = quality in availableQualities
                        val isSelected = selectedQuality == quality
                        val (titleText, descText, badgeColor) = when (quality) {
                            AudioQuality.HI_RES -> Triple(
                                "Hi-Res 高解析度无损",
                                if (enrichedSong.hashHiRes.isNotBlank()) "24bit / 96kHz FLAC · 概念版母带音质" else "优先请求 Hi-Res，若无则自动降级 SQ 无损",
                                VipGold
                            )
                            AudioQuality.LOSSLESS -> Triple(
                                "SQ 无损音质 (FLAC)",
                                if (enrichedSong.hashFlac.isNotBlank() || enrichedSong.isLossless) "16bit / 44.1kHz FLAC · CD 级无损" else "优先请求 FLAC 无损，若无则降级 320K",
                                ConceptPrimary
                            )
                            AudioQuality.HIGH -> Triple(
                                "HQ 高品音质 (320K)",
                                "320kbps MP3 · 高清压缩音质",
                                ConceptMintAccent
                            )
                            AudioQuality.STANDARD -> Triple(
                                "标准音质 (128K)",
                                "128kbps MP3/OGG · 省空间流畅音质",
                                textSecondary
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) ConceptPrimary.copy(alpha = 0.14f) else textPrimary.copy(alpha = 0.04f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                    selectedQuality = quality
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedQuality = quality },
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = titleText,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) ConceptPrimary else textPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        if (isAvailable) {
                                            Surface(
                                                shape = RoundedCornerShape(5.dp),
                                                color = badgeColor.copy(alpha = 0.16f)
                                            ) {
                                                Text(
                                                    text = quality.label,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = badgeColor,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = descText,
                                        fontSize = 11.sp,
                                        color = textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 元数据、高清封面内嵌与歌词保存快捷开关
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = textPrimary.copy(alpha = 0.045f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "元数据与歌词内嵌选项",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = ConceptPrimary
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = downloadConfig.embedHdCover,
                                onClick = { MusicDownloadManager.setEmbedHdCover(!downloadConfig.embedHdCover) },
                                label = { Text("内嵌高清封面", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                            FilterChip(
                                selected = downloadConfig.embedLyrics,
                                onClick = { MusicDownloadManager.setEmbedLyrics(!downloadConfig.embedLyrics) },
                                label = { Text("内嵌歌词", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Lyrics, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                            FilterChip(
                                selected = downloadConfig.saveStandaloneLrcFile,
                                onClick = { MusicDownloadManager.setSaveStandaloneLrcFile(!downloadConfig.saveStandaloneLrcFile) },
                                label = { Text("独立保存 .lrc", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Description, contentDescription = null, modifier = Modifier.size(14.dp))
                                }
                            )
                        }
                        Text(
                            text = "音频保存至：${downloadConfig.downloadDirectoryPath}" +
                                if (downloadConfig.saveStandaloneLrcFile) "\n歌词保存至：${downloadConfig.lyricDirectoryPath}" else "",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = textSecondary,
                            lineHeight = 14.5.sp
                        )
                    }
                }

                // 底部操作按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onOpenDownloadSettings != null) {
                        TextButton(
                            onClick = {
                                requestDismiss()
                                onOpenDownloadSettings()
                            }
                        ) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("路径设置", fontSize = 12.5.sp)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = requestDismiss) {
                            Text("取消")
                        }
                        Button(
                            onClick = {
                                MusicDownloadManager.enqueueDownload(
                                    context = context,
                                    song = enrichedSong,
                                    quality = selectedQuality,
                                    onPermissionRequired = { perms ->
                                        pendingQualityAfterPerm = selectedQuality
                                        storagePermLauncher.launch(perms)
                                    }
                                )
                                requestDismiss()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ConceptPrimary,
                                contentColor = Color.White
                            )
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(5.dp))
                            Text("立即下载", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
