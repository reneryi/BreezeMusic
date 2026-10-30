package com.concept.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import coil.size.Size
import com.concept.music.data.model.Song
import com.concept.music.service.AppPermissionManager
import com.concept.music.service.AppPermissionType
import com.concept.music.ui.theme.ConceptMotion
import com.concept.music.ui.theme.ConceptPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 高清封面 / 头像下载支持的四大范围（严格遵循需求：包含歌曲封面、专辑封面、歌单封面、歌手头像；不含榜单和电台封面）
 */
enum class ArtworkDownloadCategory(val label: String, val filePrefix: String) {
    SONG_COVER("歌曲封面", "SongCover"),
    ALBUM_COVER("专辑封面", "AlbumCover"),
    PLAYLIST_COVER("歌单封面", "PlaylistCover"),
    ARTIST_AVATAR("歌手头像", "ArtistAvatar")
}

sealed class ArtworkSaveResult {
    data class Success(val savedPathHint: String, val message: String) : ArtworkSaveResult()
    data class PermissionDisabled(val message: String) : ArtworkSaveResult()
    data class Failure(val message: String) : ArtworkSaveResult()
}

object HdArtworkSaver {

    /**
     * 将酷狗封面/头像 URL 升级为 1000x1000 高清原图地址，并构建多级回退候选队列
     */
    fun buildHdArtworkUrlCandidates(rawUrl: String): List<String> {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return emptyList()
        val httpsUrl = trimmed.replace("http://", "https://")
        val sizeSlotRegex = Regex("""/(100|120|150|165|200|240|300|400|480|720)/""")

        val hd1000 = when {
            httpsUrl.contains("{size}") -> httpsUrl.replace("{size}", "1000")
            sizeSlotRegex.containsMatchIn(httpsUrl) -> sizeSlotRegex.replaceFirst(httpsUrl, "/1000/")
            else -> httpsUrl
        }
        val hd480 = when {
            httpsUrl.contains("{size}") -> httpsUrl.replace("{size}", "480")
            sizeSlotRegex.containsMatchIn(httpsUrl) -> sizeSlotRegex.replaceFirst(httpsUrl, "/480/")
            else -> httpsUrl
        }
        val hd400 = when {
            httpsUrl.contains("{size}") -> httpsUrl.replace("{size}", "400")
            sizeSlotRegex.containsMatchIn(httpsUrl) -> sizeSlotRegex.replaceFirst(httpsUrl, "/400/")
            else -> httpsUrl
        }
        return listOf(hd1000, hd480, hd400, httpsUrl, trimmed)
            .map { it.replace("{size}", "400").trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()
    }

    fun resolveBestPreviewUrl(rawUrl: String): String {
        return buildHdArtworkUrlCandidates(rawUrl).firstOrNull() ?: rawUrl.trim()
    }

    /**
     * 同步到「设置 -> 权限管理 -> 存储读写与相册导出 (STORAGE_EXPORT)」，与 2K 歌词海报保持同一权限级别
     */
    fun isStorageExportAllowed(context: Context): Boolean {
        return AppPermissionManager.isPermissionEnabled(AppPermissionType.STORAGE_EXPORT)
    }

    suspend fun saveArtworkToGallery(
        context: Context,
        rawUrl: String,
        category: ArtworkDownloadCategory,
        title: String,
        subtitle: String = "",
        songForFallback: Song? = null
    ): ArtworkSaveResult = withContext(Dispatchers.IO) {
        if (!isStorageExportAllowed(context)) {
            return@withContext ArtworkSaveResult.PermissionDisabled(
                "已关闭「存储读写与相册导出」权限，请先在「设置 -> 权限管理」中开启后再保存${category.label}"
            )
        }

        // 若歌曲本身封面为空，尝试通过 MusicRepository 补齐封面 URL
        var effectiveUrl = rawUrl.trim()
        if (effectiveUrl.isBlank() && songForFallback != null) {
            effectiveUrl = runCatching {
                songForFallback.coverUrl
            }.getOrDefault("")
        }
        if (effectiveUrl.isBlank()) {
            return@withContext ArtworkSaveResult.Failure("暂无可用的${category.label}地址")
        }

        val candidates = buildHdArtworkUrlCandidates(effectiveUrl)
        var bitmap: Bitmap? = null

        // 1. 优先直连下载 1000x1000 高清原图字节流
        for (candidateUrl in candidates) {
            bitmap = downloadBitmapDirectly(candidateUrl)
            if (bitmap != null) break
        }

        // 2. 回退使用 Coil ImageLoader（兼容已缓存图像）
        if (bitmap == null) {
            for (candidateUrl in candidates) {
                bitmap = loadBitmapViaCoil(context, candidateUrl)
                if (bitmap != null) break
            }
        }

        val finalBitmap = bitmap
            ?: return@withContext ArtworkSaveResult.Failure("${category.label}下载失败，请检查网络或稍后重试")

        val safeTitle = sanitizeFileSegment(title.ifBlank { category.label }).take(36)
        val safeSub = sanitizeFileSegment(subtitle).take(24)
        val nameBody = if (safeSub.isNotBlank()) "${safeTitle}_${safeSub}" else safeTitle
        val fileName = "ConceptMusic_${category.filePrefix}_${nameBody}_${System.currentTimeMillis()}.png"

        return@withContext runCatching {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/ConceptMusic"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw IllegalStateException("无法创建系统相册写入条目")

            resolver.openOutputStream(uri)?.use { out ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("高清图像编码写入失败")
                }
                out.flush()
            } ?: throw IllegalStateException("无法打开相册输出流")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(uri, doneValues, null, null)
            }

            val widthPx = finalBitmap.width
            val heightPx = finalBitmap.height
            ArtworkSaveResult.Success(
                savedPathHint = "Pictures/ConceptMusic/$fileName",
                message = "高清${category.label} (${widthPx}×${heightPx}) 已保存至相册 Pictures/ConceptMusic"
            )
        }.getOrElse { e ->
            ArtworkSaveResult.Failure("保存${category.label}失败：${e.message ?: "存储写入异常"}")
        }
    }

    private fun sanitizeFileSegment(raw: String): String {
        return raw.replace(Regex("""[\\/:*?"<>|\s]+"""), "_").trim('_')
    }

    private fun downloadBitmapDirectly(urlStr: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 10000
                instanceFollowRedirects = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                )
            }
            if (conn.responseCode in 200..299) {
                val bytes = conn.inputStream.use { it.readBytes() }
                if (bytes.isNotEmpty()) {
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                } else null
            } else null
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private suspend fun loadBitmapViaCoil(context: Context, urlStr: String): Bitmap? {
        return runCatching {
            val req = ImageRequest.Builder(context)
                .data(urlStr)
                .size(Size.ORIGINAL)
                .allowHardware(false)
                .build()
            val res = context.imageLoader.execute(req)
            if (res is SuccessResult) {
                val drawable = res.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else null
            } else null
        }.getOrNull()
    }
}

/**
 * 沉浸式高清封面 / 歌手头像预览与保存弹窗
 *
 * @param iconOnlySaveButton
 *  - 当在播放页长按黑胶/方形封面触发时，传入 `true`：仅展示一个悬浮的下载保存图标按钮（不包含任何文字说明）
 *  - 当在专辑详情、歌单详情、歌手主页点击封面/头像触发时，传入 `false`：展示带「保存高清封面/头像」的胶囊按钮
 */
@Composable
fun HdArtworkPreviewDialog(
    artworkUrl: String,
    title: String,
    subtitle: String = "",
    category: ArtworkDownloadCategory,
    songForFallback: Song? = null,
    iconOnlySaveButton: Boolean = false,
    accentColor: Color = ConceptPrimary,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isSaving by remember { mutableStateOf(false) }

    var resolvedCoverUrl by remember(artworkUrl, songForFallback?.id) {
        mutableStateOf(HdArtworkSaver.resolveBestPreviewUrl(artworkUrl))
    }

    LaunchedEffect(artworkUrl, songForFallback?.id) {
        if (resolvedCoverUrl.isBlank() && songForFallback != null) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching {
                    songForFallback.coverUrl
                }.getOrDefault("")
            }
            if (fetched.isNotBlank()) {
                resolvedCoverUrl = HdArtworkSaver.resolveBestPreviewUrl(fetched)
            }
        }
    }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffsetX by remember { mutableFloatStateOf(0f) }
    var panOffsetY by remember { mutableFloatStateOf(0f) }
    val animatedScale by animateFloatAsState(
        targetValue = zoomScale,
        animationSpec = ConceptMotion.gentleBounceSpring(),
        label = "artworkPreviewScale"
    )

    val triggerSaveAction: () -> Unit = {
        if (!isSaving) {
            isSaving = true
            coroutineScope.launch {
                val result = HdArtworkSaver.saveArtworkToGallery(
                    context = context,
                    rawUrl = resolvedCoverUrl.ifBlank { artworkUrl },
                    category = category,
                    title = title,
                    subtitle = subtitle,
                    songForFallback = songForFallback
                )
                isSaving = false
                when (result) {
                    is ArtworkSaveResult.Success -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                    is ArtworkSaveResult.PermissionDisabled -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    }
                    is ArtworkSaveResult.Failure -> {
                        Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEB070A12),
                            Color(0xF50B101C),
                            Color(0xFA06080E)
                        )
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .systemBarsPadding()
        ) {
            val isLandscape = maxWidth > maxHeight
            val previewSide = if (isLandscape) {
                (maxHeight * 0.72f).coerceIn(220.dp, 480.dp)
            } else {
                (maxWidth * 0.84f).coerceIn(240.dp, 460.dp)
            }

            // 顶部关闭按钮（纯图标）
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.10f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 16.dp, end = 20.dp)
                    .size(42.dp)
                    .border(0.8.dp, Color.White.copy(alpha = 0.14f), CircleShape)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "关闭预览",
                        tint = Color.White.copy(alpha = 0.92f),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // 中央高清封面 / 歌手头像主体（支持双指缩放与双击放大还原）
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val cardShape = if (category == ArtworkDownloadCategory.ARTIST_AVATAR) {
                    RoundedCornerShape(36.dp)
                } else {
                    RoundedCornerShape(26.dp)
                }

                Surface(
                    shape = cardShape,
                    color = Color(0xFF141B29),
                    shadowElevation = 28.dp,
                    modifier = Modifier
                        .size(previewSide)
                        .graphicsLayer {
                            scaleX = animatedScale
                            scaleY = animatedScale
                            translationX = panOffsetX
                            translationY = panOffsetY
                        }
                        .border(1.dp, Color.White.copy(alpha = 0.16f), cardShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (zoomScale > 1.05f) {
                                        zoomScale = 1f
                                        panOffsetX = 0f
                                        panOffsetY = 0f
                                    } else {
                                        zoomScale = 2.0f
                                    }
                                },
                                onTap = { /* 阻止点击封面本体时误触背景关闭 */ }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (zoomScale * zoom).coerceIn(1f, 3.2f)
                                zoomScale = newScale
                                if (newScale > 1.02f) {
                                    val maxOffset = (newScale - 1f) * 280f
                                    panOffsetX = (panOffsetX + pan.x).coerceIn(-maxOffset, maxOffset)
                                    panOffsetY = (panOffsetY + pan.y).coerceIn(-maxOffset, maxOffset)
                                } else {
                                    panOffsetX = 0f
                                    panOffsetY = 0f
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resolvedCoverUrl.ifBlank { artworkUrl })
                            .size(Size.ORIGINAL)
                            .crossfade(200)
                            .build(),
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(cardShape)
                    )
                }

                if (!iconOnlySaveButton && title.isNotBlank()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (subtitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.68f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 底部保存操作区
            if (iconOnlySaveButton) {
                // 播放页长按黑胶/方形封面：严格仅展示一个下载保存图标按钮（不含任何文字说明）
                Surface(
                    onClick = triggerSaveAction,
                    shape = CircleShape,
                    color = accentColor.copy(alpha = 0.90f),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 28.dp, end = 28.dp)
                        .size(54.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.24f), CircleShape)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.2.dp,
                                modifier = Modifier.size(22.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(25.dp)
                            )
                        }
                    }
                }
            } else {
                // 专辑详情 / 歌单详情 / 歌手主页点击封面或头像预览：底部居中保存按钮
                Surface(
                    onClick = triggerSaveAction,
                    shape = RoundedCornerShape(999.dp),
                    color = accentColor.copy(alpha = 0.88f),
                    shadowElevation = 12.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 28.dp)
                        .height(48.dp)
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = "保存${category.label}",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (isSaving) "正在保存..." else "保存高清${category.label}",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            ),
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
