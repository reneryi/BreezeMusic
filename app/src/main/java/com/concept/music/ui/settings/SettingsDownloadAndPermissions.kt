package com.concept.music.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.concept.music.BuildConfig
import com.concept.music.data.kugou.NetworkClientProvider
import com.concept.music.data.model.AudioQuality
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionState
import com.concept.music.service.AppPermissionType
import com.concept.music.service.AudioCacheLimit
import com.concept.music.service.AudioCacheManager
import com.concept.music.service.AudioEffectManager
import com.concept.music.service.AudioFocusMode
import com.concept.music.service.DetailedCacheStats
import com.concept.music.service.HeadsetClickSensitivity
import com.concept.music.service.MusicDownloadManager
import com.concept.music.service.DownloadTaskStatus
import com.concept.music.ui.theme.*
import com.concept.music.ui.viewmodel.CoverStyle
import com.concept.music.ui.viewmodel.LyricAlignment
import com.concept.music.ui.viewmodel.MusicPlayerViewModel
import com.concept.music.ui.viewmodel.PlayerBackgroundStyle
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 独立「下载」二级页面（仅在权限管理中手动开启「音乐下载与歌词保存」后可见）：
 * 采用清晰、整洁的模块化四卡片分组设计，并将「最近下载记录」独立为二级子页面进入。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadSettingsSectionContent(
    context: Context,
    refreshTick: Int,
    onOpenDownloadHistory: () -> Unit
) {
    val config by MusicDownloadManager.config.collectAsState()
    val tasks by MusicDownloadManager.tasks.collectAsState()

    val storageGrantStatus = remember(refreshTick) {
        AppPermissionManager.querySystemGrantStatus(context, AppPermissionType.STORAGE_EXPORT)
    }
    val allFilesGranted = remember(refreshTick) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()
    }

    var showCustomDownloadPathDialog by remember { mutableStateOf(false) }
    var customDownloadPathInput by remember(config.downloadDirectoryPath) {
        mutableStateOf(config.downloadDirectoryPath)
    }

    var showCustomLyricPathDialog by remember { mutableStateOf(false) }
    var customLyricPathInput by remember(config.lyricDirectoryPath) {
        mutableStateOf(config.lyricDirectoryPath)
    }

    val downloadDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val resolvedPath = MusicDownloadManager.resolveTreeUriToFilePath(context, treeUri)
            if (!resolvedPath.isNullOrBlank()) {
                MusicDownloadManager.setDownloadDirectoryPath(resolvedPath)
                Toast.makeText(context, "音乐保存路径已更新：$resolvedPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "所选目录无法映射为本地路径，请使用手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val lyricDirPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val resolvedPath = MusicDownloadManager.resolveTreeUriToFilePath(context, treeUri)
            if (!resolvedPath.isNullOrBlank()) {
                MusicDownloadManager.setLyricDirectoryPath(resolvedPath)
                Toast.makeText(context, "歌词保存路径已更新：$resolvedPath", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "所选目录无法映射为本地路径，请使用手动输入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val completedCount = remember(tasks) {
        tasks.count { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.SKIPPED_EXISTING }
    }
    val activeCount = remember(tasks) {
        tasks.count {
            it.status == DownloadTaskStatus.QUEUED ||
                it.status == DownloadTaskStatus.RESOLVING ||
                it.status == DownloadTaskStatus.DOWNLOADING ||
                it.status == DownloadTaskStatus.EMBEDDING
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 卡片 1：存储权限授权（直跳系统权限页）与「最近下载记录」二级菜单入口
        SettingsSectionCard(
            title = "存储授权与下载记录",
            icon = Icons.Rounded.Download
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (allFilesGranted || storageGrantStatus.isSystemGranted) {
                                    ConceptMintAccent.copy(alpha = 0.14f)
                                } else {
                                    ConceptAmberAccent.copy(alpha = 0.14f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (allFilesGranted || storageGrantStatus.isSystemGranted) {
                                Icons.Rounded.VerifiedUser
                            } else {
                                Icons.Rounded.FolderOff
                            },
                            contentDescription = null,
                            tint = if (allFilesGranted || storageGrantStatus.isSystemGranted) ConceptMintAccent else ConceptAmberAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "手机存储读写权限",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = storageGrantStatus.statusLabel,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (allFilesGranted) ConceptMintAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.8.sp
                        )
                    }
                }

                ConceptTonalActionButton(
                    text = "存储权限设置",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    accentColor = ConceptPrimary,
                    onClick = {
                        AppPermissionManager.openSystemPermissionSetting(context, AppPermissionType.STORAGE_EXPORT)
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // 二级菜单入口：最近下载记录
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                modifier = Modifier
                    .fillMaxWidth()
                    .softCardClickable(shape = RoundedCornerShape(16.dp), onClick = onOpenDownloadHistory)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
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
                                .clip(RoundedCornerShape(11.dp))
                                .background(ConceptPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.History,
                                contentDescription = null,
                                tint = ConceptPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "最近下载记录",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    activeCount > 0 -> "正在下载 $activeCount 首 · 已完成 $completedCount 首"
                                    tasks.isNotEmpty() -> "共 ${tasks.size} 条记录 · 已完成 $completedCount 首"
                                    else -> "查看下载队列、内嵌状态与本地文件位置"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.8.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = "进入最近下载记录",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // 卡片 2：默认下载音质档位（整洁的 2x2 对称网格卡片）
        SettingsSectionCard(
            title = "默认下载音质",
            icon = Icons.Rounded.HighQuality
        ) {
            Text(
                text = "优先按所选音质下载，若目标歌曲无该档位将按 Hi-Res → SQ → 320K → 128K 自动平滑降级",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.8.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            val qualityRows = listOf(
                listOf(
                    Triple(AudioQuality.HI_RES, "Hi-Res 高解析", "24bit 无损 FLAC"),
                    Triple(AudioQuality.LOSSLESS, "SQ 无损音质", "16bit CD级 FLAC")
                ),
                listOf(
                    Triple(AudioQuality.HIGH, "HQ 高品 320K", "320kbps MP3"),
                    Triple(AudioQuality.STANDARD, "标准 128K", "128kbps MP3/OGG")
                )
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                qualityRows.forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (qOption, qTitle, qSpec) ->
                            val isSelected = config.defaultDownloadQuality == qOption
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) {
                                    ConceptMintAccent.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .softCardClickable(shape = RoundedCornerShape(14.dp)) {
                                        MusicDownloadManager.setDefaultDownloadQuality(qOption)
                                        Toast.makeText(context, "默认下载音质已设为 ${qOption.label}", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = qTitle,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSelected) ConceptMintAccent else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 13.5.sp
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = ConceptMintAccent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = qSpec,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 卡片 3：自定义保存路径设置（音乐下载路径 + 独立 .lrc 歌词保存路径）
        SettingsSectionCard(
            title = "保存路径设置",
            icon = Icons.Rounded.Folder
        ) {
            // 1. 音乐下载保存路径
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "音乐保存路径",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { runCatching { downloadDirPickerLauncher.launch(null) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择文件夹", fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = {
                            customDownloadPathInput = config.downloadDirectoryPath
                            showCustomDownloadPathDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动输入", fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = ConceptAmberAccent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = config.downloadDirectoryPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // 2. 独立 .lrc 歌词保存路径
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "歌词保存路径 (.lrc)",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { runCatching { lyricDirPickerLauncher.launch(null) } },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("选择文件夹", fontSize = 11.5.sp)
                    }
                    TextButton(
                        onClick = {
                            customLyricPathInput = config.lyricDirectoryPath
                            showCustomLyricPathDialog = true
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("手动输入", fontSize = 11.5.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Lyrics,
                        contentDescription = null,
                        tint = ConceptCyanAccent,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = config.lyricDirectoryPath,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 11.8.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        MusicDownloadManager.setLyricDirectoryPath(config.downloadDirectoryPath)
                        Toast.makeText(context, "已将歌词路径设为与音乐目录相同", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("歌词与音乐同目录", fontSize = 11.5.sp, color = ConceptCyanAccent)
                }
                TextButton(
                    onClick = {
                        MusicDownloadManager.resetDirectoriesToDefault()
                        Toast.makeText(context, "已恢复默认下载与歌词目录", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text("恢复默认路径", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // 卡片 4：音频标签内嵌与歌词导出选项
        SettingsSectionCard(
            title = "音频内嵌与歌词选项",
            icon = Icons.Rounded.Lyrics
        ) {
            SettingToggleRow(
                title = "内嵌高清封面与歌曲信息",
                subtitle = "自动写入 1000P 高清专辑封面、歌名、歌手、专辑与发行年份标签",
                checked = config.embedHdCover,
                onCheckedChange = { MusicDownloadManager.setEmbedHdCover(it) },
                thumbColor = ConceptPrimary
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "内嵌歌词到音频文件",
                subtitle = "自动将时间轴歌词写入 FLAC (LYRICS) 与 MP3 (USLT) 内嵌标签",
                checked = config.embedLyrics,
                onCheckedChange = { MusicDownloadManager.setEmbedLyrics(it) },
                thumbColor = ConceptCyanAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "独立保存 .lrc 歌词文件",
                subtitle = "下载歌曲时同步在「歌词保存路径」下生成同名 .lrc 文件",
                checked = config.saveStandaloneLrcFile,
                onCheckedChange = { MusicDownloadManager.setSaveStandaloneLrcFile(it) },
                thumbColor = ConceptMintAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "歌词包含双语翻译",
                subtitle = "外语歌曲存在中文译文时，在内嵌与导出歌词中附加双语行",
                checked = config.includeTranslationInLyric,
                onCheckedChange = { MusicDownloadManager.setIncludeTranslationInLyric(it) },
                thumbColor = ConceptVioletAccent
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
            )

            SettingToggleRow(
                title = "跳过已存在的同名歌曲",
                subtitle = "目标路径下已存在同名音频文件时自动跳过，避免重复下载",
                checked = config.skipExistingFiles,
                onCheckedChange = { MusicDownloadManager.setSkipExistingFiles(it) },
                thumbColor = ConceptAmberAccent
            )
        }
    }

    if (showCustomDownloadPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDownloadPathDialog = false },
            title = { Text("自定义音乐保存路径", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入手机存储内的目录路径（不存在将自动创建）：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customDownloadPathInput,
                        onValueChange = { customDownloadPathInput = it },
                        singleLine = true,
                        label = { Text("音乐保存目录路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "/storage/emulated/0/Music/BreezeMusic",
                            "/storage/emulated/0/Download/BreezeMusic",
                            "/storage/emulated/0/Music"
                        ).forEach { preset ->
                            ConceptBorderlessChoicePill(
                                selected = customDownloadPathInput == preset,
                                label = preset.removePrefix("/storage/emulated/0/"),
                                accentColor = ConceptCyanAccent,
                                onClick = { customDownloadPathInput = preset }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customDownloadPathInput.trim()
                        if (trimmed.isNotEmpty()) {
                            MusicDownloadManager.setDownloadDirectoryPath(trimmed)
                            Toast.makeText(context, "音乐保存路径已更新", Toast.LENGTH_SHORT).show()
                        }
                        showCustomDownloadPathDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDownloadPathDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCustomLyricPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomLyricPathDialog = false },
            title = { Text("自定义歌词保存路径 (.lrc)", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "请输入独立 .lrc 歌词文件的保存目录路径：",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = customLyricPathInput,
                        onValueChange = { customLyricPathInput = it },
                        singleLine = true,
                        label = { Text("歌词保存目录路径") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "/storage/emulated/0/Music/BreezeMusic/Lyrics",
                            "/storage/emulated/0/Download/BreezeMusic/Lyrics",
                            config.downloadDirectoryPath
                        ).distinct().forEach { preset ->
                            ConceptBorderlessChoicePill(
                                selected = customLyricPathInput == preset,
                                label = preset.removePrefix("/storage/emulated/0/"),
                                accentColor = ConceptCyanAccent,
                                onClick = { customLyricPathInput = preset }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customLyricPathInput.trim()
                        if (trimmed.isNotEmpty()) {
                            MusicDownloadManager.setLyricDirectoryPath(trimmed)
                            Toast.makeText(context, "歌词保存路径已更新", Toast.LENGTH_SHORT).show()
                        }
                        showCustomLyricPathDialog = false
                    }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomLyricPathDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

/**
 * 「最近下载记录」独立二级子页面：
 * 展示全部下载任务状态、进度、已内嵌标签详情与保存文件路径，支持一键清理已完成记录。
 */
@Composable
internal fun DownloadHistorySubPageContent() {
    val tasks by MusicDownloadManager.tasks.collectAsState()
    val completedCount = remember(tasks) {
        tasks.count { it.status == DownloadTaskStatus.COMPLETED || it.status == DownloadTaskStatus.SKIPPED_EXISTING }
    }
    val activeCount = remember(tasks) {
        tasks.count {
            it.status == DownloadTaskStatus.QUEUED ||
                it.status == DownloadTaskStatus.RESOLVING ||
                it.status == DownloadTaskStatus.DOWNLOADING ||
                it.status == DownloadTaskStatus.EMBEDDING
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(
            title = "下载队列概览",
            icon = Icons.Rounded.History
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "共 ${tasks.size} 条下载记录",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "已完成 $completedCount 首 · 进行中 $activeCount 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (tasks.isNotEmpty()) {
                    ConceptTonalActionButton(
                        text = "清理已完成",
                        icon = Icons.Rounded.CleaningServices,
                        accentColor = ConceptPrimary,
                        onClick = { MusicDownloadManager.clearCompletedTasks() }
                    )
                }
            }
        }

        if (tasks.isEmpty()) {
            SettingsSectionCard(
                title = "暂无记录",
                icon = Icons.Rounded.DownloadDone
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "暂无最近下载记录",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "在歌曲更多菜单或播放页点击「下载歌曲」即可将音频与歌词保存至本地",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontSize = 11.5.sp
                    )
                }
            }
        } else {
            tasks.forEach { task ->
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = conceptFloatingCardColor(),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = task.song.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = task.song.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontSize = 11.8.sp
                                )
                            }
                            val statusColor = when (task.status) {
                                DownloadTaskStatus.COMPLETED, DownloadTaskStatus.SKIPPED_EXISTING -> ConceptMintAccent
                                DownloadTaskStatus.FAILED -> ConceptRoseAccent
                                else -> ConceptPrimary
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = statusColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = "${task.actualQuality.shortBadge} · ${task.status.label}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        if (task.status == DownloadTaskStatus.DOWNLOADING ||
                            task.status == DownloadTaskStatus.EMBEDDING ||
                            task.status == DownloadTaskStatus.RESOLVING
                        ) {
                            LinearProgressIndicator(
                                progress = { task.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape),
                                color = ConceptPrimary
                            )
                        }

                        if (task.status == DownloadTaskStatus.COMPLETED || task.status == DownloadTaskStatus.SKIPPED_EXISTING) {
                            val tagsSummary = buildList {
                                if (task.embeddedMetadata) add("歌曲信息已内嵌")
                                if (task.embeddedCover) add("高清封面已内嵌")
                                if (task.embeddedLyric) add("歌词已内嵌")
                                if (task.savedLrcPath.isNotBlank()) add("独立 .lrc 已保存")
                            }.joinToString(" · ").ifBlank { "文件已保存" }
                            Text(
                                text = tagsSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = ConceptMintAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (task.savedAudioPath.isNotBlank()) {
                                Text(
                                    text = task.savedAudioPath,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.5.sp,
                                    lineHeight = 14.sp
                                )
                            }
                        } else if (task.status == DownloadTaskStatus.FAILED && !task.errorMessage.isNullOrBlank()) {
                            Text(
                                text = task.errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = ConceptRoseAccent,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 存储与缓存二级页面完整内容：
 * 1. 总空间占用与多维构成比例条
 * 2. 流媒体音频缓存上限档位调节（512MB ~ 10GB）
 * 3. 四大实际缓存分类明细（音频流、封面/评论图片、歌单SWR快照、KRC歌词与WebView临时缓存）及各自独立清理按钮
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun StorageAndCacheSectionContent(
    context: Context,
    totalBytes: Long,
    stats: DetailedCacheStats,
    cacheLimit: AudioCacheLimit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SettingsSectionCard(
            title = "存储与缓存总览",
            icon = Icons.Rounded.Storage
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "当前总缓存占用",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        text = "包含在线音频流、封面图片、歌单秒开快照与歌词缓存",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = AudioCacheManager.formatSize(totalBytes),
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = VipGold,
                        fontWeight = FontWeight.ExtraBold
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 四色分项缓存构成比例条
            val denom = totalBytes.coerceAtLeast(1L).toFloat()
            val audioFrac = (stats.audioBytes / denom).coerceIn(0f, 1f)
            val imgFrac = (stats.totalImageBytes / denom).coerceIn(0f, 1f)
            val swrFrac = (stats.swrMetadataBytes / denom).coerceIn(0f, 1f)
            val otherFrac = (stats.otherTempBytes / denom).coerceIn(0f, 1f)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (totalBytes > 0L) {
                    if (audioFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(audioFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptAmberAccent)
                        )
                    }
                    if (imgFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(imgFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptPrimary)
                        )
                    }
                    if (swrFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(swrFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptMintAccent)
                        )
                    }
                    if (otherFrac > 0.005f) {
                        Box(
                            modifier = Modifier
                                .weight(otherFrac.coerceAtLeast(0.02f))
                                .fillMaxHeight()
                                .background(ConceptVioletAccent)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CacheLegendDot("音频 ${AudioCacheManager.formatSize(stats.audioBytes)}", ConceptAmberAccent)
                CacheLegendDot("图片 ${AudioCacheManager.formatSize(stats.totalImageBytes)}", ConceptPrimary)
                CacheLegendDot("歌单快照 ${AudioCacheManager.formatSize(stats.swrMetadataBytes)}", ConceptMintAccent)
                CacheLegendDot("歌词/临时 ${AudioCacheManager.formatSize(stats.otherTempBytes)}", ConceptVioletAccent)
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "流媒体音频缓存容量上限",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = "${AudioCacheManager.formatSize(stats.audioBytes)} / ${cacheLimit.label}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = ConceptAmberAccent,
                        fontWeight = FontWeight.Bold
                    )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "超出设定上限时，播放器将按 LRU（最久未播放）策略自动淘汰旧音频缓存",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AudioCacheLimit.entries.forEach { limitOption ->
                    ConceptBorderlessChoicePill(
                        selected = cacheLimit == limitOption,
                        label = limitOption.label,
                        accentColor = ConceptAmberAccent,
                        onClick = {
                            AudioCacheManager.setCacheLimit(context, limitOption)
                            Toast.makeText(context, "音频缓存上限已设为 ${limitOption.label}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                var cacheRefreshSpin by remember { mutableIntStateOf(0) }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ConceptPrimary.copy(alpha = 0.14f),
                    modifier = Modifier
                        .height(38.dp)
                        .softCardClickable(shape = RoundedCornerShape(12.dp)) {
                            cacheRefreshSpin += 1
                            AudioCacheManager.refreshCacheSize(context)
                            Toast.makeText(context, "已刷新各项缓存统计", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConceptFluidRefreshIcon(
                            tint = ConceptPrimary,
                            spinTrigger = cacheRefreshSpin,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("刷新统计", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ConceptPrimary)
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        AudioCacheManager.clearCache(context) {
                            Toast.makeText(context, "已清理全部缓存数据", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ConceptPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("一键清理全部", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        SettingsSectionCard(
            title = "分项缓存明细与独立管理",
            icon = Icons.Rounded.PieChart
        ) {
            CacheCategoryDetailRow(
                title = "流媒体音频缓存",
                badgeText = "共 ${stats.audioTrackCount} 首音频片段",
                sizeText = AudioCacheManager.formatSize(stats.audioBytes),
                description = "在线播放时自动保存的 Hi-Res / SQ 无损与高品音频数据块，再次播放同一歌曲时免缓冲且不消耗流量。",
                accentColor = ConceptAmberAccent,
                icon = Icons.Rounded.LibraryMusic,
                actionLabel = "清理音频",
                onClearClick = {
                    AudioCacheManager.clearAudioCacheOnly(context) {
                        Toast.makeText(context, "已清理流媒体音频缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "封面与评论图片缓存",
                badgeText = "磁盘 ${AudioCacheManager.formatSize(stats.imageDiskBytes)} · 内存 ${AudioCacheManager.formatSize(stats.imageMemoryBytes)}",
                sizeText = AudioCacheManager.formatSize(stats.totalImageBytes),
                description = "包含专辑高清封面、歌词海报底图、歌手写真、歌单封面及评论区配图缓存。",
                accentColor = ConceptPrimary,
                icon = Icons.Rounded.Image,
                actionLabel = "清理图片",
                onClearClick = {
                    AudioCacheManager.clearImageCacheOnly(context) {
                        Toast.makeText(context, "已清理封面与评论图片缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "歌单与发现页本地快照",
                badgeText = "${stats.swrSnapshotCount} 份歌单离线快照",
                sizeText = AudioCacheManager.formatSize(stats.swrMetadataBytes),
                description = "每日推荐、私人 FM、新歌速递、排行榜及个人收藏歌单的启动加速缓存，清理后联网会自动重新同步，不影响云端歌单。",
                accentColor = ConceptMintAccent,
                icon = Icons.Rounded.FlashOn,
                actionLabel = "清理快照",
                onClearClick = {
                    AudioCacheManager.clearSwrMetadataCacheOnly(context) {
                        Toast.makeText(context, "已清理歌单与发现页本地快照", Toast.LENGTH_SHORT).show()
                    }
                }
            )

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )

            CacheCategoryDetailRow(
                title = "逐字歌词与音源直链缓存",
                badgeText = "歌词 ${stats.lyricMemoryCount} 首 · 音源元数据 ${stats.runtimeMetaCount} 条",
                sizeText = AudioCacheManager.formatSize(stats.otherTempBytes),
                description = "包含已解析的 KRC 逐字双语歌词、临时音源链接、专辑发行信息及安全验证临时文件。",
                accentColor = ConceptVioletAccent,
                icon = Icons.Rounded.Lyrics,
                actionLabel = "清理临时",
                onClearClick = {
                    AudioCacheManager.clearLyricAndTempCacheOnly(context) {
                        Toast.makeText(context, "已清理歌词、音源直链与临时缓存", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun CacheLegendDot(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun CacheCategoryDetailRow(
    title: String,
    badgeText: String,
    sizeText: String,
    description: String,
    accentColor: Color,
    icon: ImageVector,
    actionLabel: String,
    onClearClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = accentColor.copy(alpha = 0.14f)
                        ) {
                            Text(
                                text = sizeText,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            ConceptTonalActionButton(
                text = actionLabel,
                icon = Icons.Rounded.CleaningServices,
                accentColor = accentColor,
                onClick = onClearClick
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.5.sp,
            lineHeight = 16.5.sp
        )
    }
}


/**
 * 权限管理二级页面（大气、高可读性的权限公开透明说明与自主开关中心）：
 * 1. 顶部全景概览横幅：直观呈现已开启权限数量、最小化隐私承诺及「系统权限设置 / 一键全部开启」快捷入口；
 * 2. 7 大核心权限独立卡片：清晰公开每项权限的 Android 系统标识、获取用途、隐私保护承诺，以及关闭后的具体功能影响；
 * 3. 独立自主开关：用户可随时开启或关闭任意一项权限，关闭后立即拦截或停用对应功能。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PermissionsSectionContent(
    context: Context,
    permissionState: AppPermissionState,
    refreshTick: Int,
    onTogglePermission: (AppPermissionType, Boolean) -> Unit,
    onEnableAll: () -> Unit
) {
    val enabledCount = permissionState.enabledCount
    val totalCount = permissionState.totalCount
    val restrictedCount = (totalCount - enabledCount).coerceAtLeast(0)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // 1. 顶部大气概览 Hero 卡片
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = conceptFloatingCardColor(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    ConceptMintAccent.copy(alpha = 0.16f),
                                    ConceptPrimary.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 20.dp, vertical = 20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(15.dp))
                                        .background(
                                            Brush.linearGradient(
                                                listOf(ConceptMintAccent, ConceptPrimary)
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VerifiedUser,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "应用权限公开与自主管控",
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 17.5.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (restrictedCount == 0) {
                                            "当前已启用全部 $totalCount 项权限 · 所有功能正常可用"
                                        } else {
                                            "已启用 $enabledCount / $totalCount 项 · 已自主关闭 $restrictedCount 项权限"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (restrictedCount == 0) ConceptMintAccent else ConceptAmberAccent
                                    )
                                }
                            }
                        }

                        Text(
                            text = "微风音乐遵循「最小化按需获取」与「纯端侧透明直连」原则。下方完整公开项目中声明与使用的每项权限用途，您可随时自主开关；关闭某项权限后，对应功能将立即停用，不影响其他无关模块正常运行。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            lineHeight = 20.sp
                        )

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                "零私服纯端侧直连",
                                "用途透明公开",
                                "支持独立自主关停",
                                "绝不读取私人相册与通讯录"
                            ).forEach { badge ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ConceptMintAccent.copy(alpha = 0.12f)
                                ) {
                                    Text(
                                        text = badge,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = ConceptMintAccent,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ConceptTonalActionButton(
                                text = "系统应用权限页",
                                icon = Icons.Rounded.SettingsApplications,
                                accentColor = ConceptPrimary,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp),
                                onClick = { AppPermissionManager.openAppDetailsSettings(context) }
                            )

                            if (restrictedCount > 0) {
                                Button(
                                    onClick = onEnableAll,
                                    shape = RoundedCornerShape(13.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ConceptMintAccent,
                                        contentColor = Color(0xFF061310)
                                    ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DoneAll,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "恢复开启全部",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. 逐项权限公开说明与独立开关卡片
        AppPermissionType.entries.forEach { permType ->
            val isEnabled = permissionState.isEnabled(permType)
            val systemGrant = remember(permType, refreshTick, isEnabled) {
                AppPermissionManager.querySystemGrantStatus(context, permType)
            }
            PermissionDisclosureCard(
                type = permType,
                enabled = isEnabled,
                systemGrant = systemGrant,
                onCheckedChange = { onTogglePermission(permType, it) },
                onOpenSystemSetting = {
                    AppPermissionManager.openSystemPermissionSetting(context, permType)
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionDisclosureCard(
    type: AppPermissionType,
    enabled: Boolean,
    systemGrant: com.concept.music.service.SystemPermissionGrantStatus,
    onCheckedChange: (Boolean) -> Unit,
    onOpenSystemSetting: () -> Unit
) {
    val (icon, accentColor) = when (type) {
        AppPermissionType.NETWORK_ACCESS -> Icons.Rounded.Public to ConceptPrimary
        AppPermissionType.NOTIFICATION_MEDIA -> Icons.Rounded.NotificationsActive to ConceptCyanAccent
        AppPermissionType.SYSTEM_OVERLAY -> Icons.Rounded.PictureInPictureAlt to ConceptVioletAccent
        AppPermissionType.STORAGE_EXPORT -> Icons.Rounded.PhotoLibrary to ConceptAmberAccent
        AppPermissionType.MUSIC_DOWNLOAD -> Icons.Rounded.Download to ConceptCyanAccent
        AppPermissionType.BACKGROUND_GUARDIAN -> Icons.Rounded.BatteryChargingFull to ConceptMintAccent
        AppPermissionType.MOTION_ORIENTATION -> Icons.Rounded.ScreenRotation to ConceptCoralAccent
        AppPermissionType.AUDIO_HARDWARE -> Icons.Rounded.Headphones to ConceptRoseAccent
    }
    var detailsExpanded by rememberSaveable(type.name) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = conceptFloatingCardColor(),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .softCardClickable(
                shape = RoundedCornerShape(22.dp),
                onClick = { detailsExpanded = !detailsExpanded }
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. 卡片头部：色阶图标 + 权限标题 + 状态胶囊 + 独立开关
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(
                                if (enabled) accentColor.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = type.title,
                            tint = if (enabled) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = type.title,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = type.badge,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (enabled && systemGrant.isSystemGranted) {
                                    ConceptMintAccent.copy(alpha = 0.14f)
                                } else {
                                    ConceptAmberAccent.copy(alpha = 0.15f)
                                }
                            ) {
                                Text(
                                    text = if (!enabled) "应用内已关闭" else systemGrant.statusLabel,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (enabled && systemGrant.isSystemGranted) {
                                        ConceptMintAccent
                                    } else {
                                        ConceptAmberAccent
                                    },
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                Switch(
                    checked = enabled,
                    onCheckedChange = onCheckedChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = accentColor
                    )
                )
            }

            // 2. 一句话精简摘要（或关闭时的停用提示）+ 右侧展开详情/系统设置无框胶囊
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (enabled) type.purposeDescription else "已停用：${type.disabledImpactDescription}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ConceptAmberAccent,
                    fontSize = 12.sp,
                    maxLines = if (detailsExpanded) 4 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (systemGrant.actionButtonLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.13f),
                            modifier = Modifier.softCardClickable(
                                shape = RoundedCornerShape(8.dp),
                                onClick = onOpenSystemSetting
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = systemGrant.actionButtonLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        modifier = Modifier.softCardClickable(
                            shape = RoundedCornerShape(8.dp),
                            onClick = { detailsExpanded = !detailsExpanded }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (detailsExpanded) "收起" else "详情",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Icon(
                                imageVector = if (detailsExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            // 3. 按需展开的技术标识、完整隐私承诺与关闭影响详情（避免默认满屏堆砌长文）
            AnimatedVisibility(
                visible = detailsExpanded,
                enter = expandVertically(animationSpec = ConceptMotion.spatialPageSpring()) + fadeIn(tween(180)),
                exit = shrinkVertically(animationSpec = tween(200)) + fadeOut(tween(150))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.62f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = type.manifestPermissionName,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.52f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "隐私与数据边界：${type.privacyCommitment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp
                            )
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f)
                            )
                            Text(
                                text = "关闭影响：${type.disabledImpactDescription}",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else ConceptAmberAccent,
                                fontSize = 12.sp,
                                lineHeight = 17.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

