package com.concept.music.ui.account

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.provider.MediaStore
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.concept.music.data.kugou.SavedAccount
import com.concept.music.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LoginScreen(viewModel: LibraryViewModel, onBack: () -> Unit) {
    val state by viewModel.login.collectAsState()
    LaunchedEffect(state.completed) { if (state.completed) onBack() }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().imePadding(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("返回") }; Text("登录酷狗账号", style = MaterialTheme.typography.headlineMedium) }
        item {
            TabRow(state.tab) {
                listOf("手机验证码", "酷狗扫码").forEachIndexed { index, label ->
                    Tab(state.tab == index, onClick = { viewModel.chooseTab(index) }, enabled = !state.busy, text = { Text(label) })
                }
            }
        }
        if (state.tab == 0) {
            item {
                OutlinedTextField(state.mobile, viewModel::setMobile, Modifier.fillMaxWidth(), enabled = !state.busy,
                    singleLine = true, label = { Text("中国大陆手机号") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            }
            item {
                OutlinedTextField(state.code, viewModel::setCode, Modifier.fillMaxWidth(), enabled = !state.busy,
                    singleLine = true, label = { Text("短信验证码") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = viewModel::sendCode, enabled = !state.busy && state.resendSeconds == 0 && state.mobile.length == 11) {
                        Text(if (state.resendSeconds > 0) "${state.resendSeconds} 秒后重发" else "发送验证码")
                    }
                    Button(onClick = { viewModel.phoneLogin() }, enabled = !state.busy && state.mobile.length == 11 && state.code.length >= 4) { Text("登录") }
                }
                Text("请使用已有酷狗账号；如未注册，请先在官方 App 注册。", style = MaterialTheme.typography.bodySmall)
            }
            items(state.candidates, key = { it.userId }) { candidate ->
                OutlinedCard(onClick = { if (!state.busy) viewModel.phoneLogin(candidate.userId) }, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (candidate.avatarUrl.isNotEmpty()) AsyncImage(candidate.avatarUrl, "头像", Modifier.size(40.dp))
                        Column { Text(candidate.nickname); Text("酷狗 ID：${candidate.userId}") }
                    }
                }
            }
        } else {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val bitmap = remember(state.qr?.imageUrl) { decodeQr(state.qr?.imageUrl) }
                    if (bitmap != null) Image(bitmap.asImageBitmap(), "酷狗登录二维码", Modifier.size(240.dp))
                    else if (state.qr != null) Text("二维码图片无法识别，请重新生成")
                    Text(state.qrStatus)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::createQr, enabled = !state.busy) { Text("重新生成") }
                        if (bitmap != null) SaveQrButton(bitmap)
                    }
                    Text("可保存二维码，在酷狗音乐 App 扫一扫中从相册识别；确认后返回本应用。", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
    }
}

internal fun decodeQr(data: String?): Bitmap? = runCatching {
    if (data == null || !data.startsWith("data:image/") || !data.contains(";base64,")) return null
    val bytes = Base64.decode(data.substringAfter(','), Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

@Composable
private fun SaveQrButton(bitmap: Bitmap) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val save = {
        scope.launch {
            busy = true
            try { withContext(Dispatchers.IO) { saveQr(context, bitmap) }; message = "二维码已保存到相册" }
            catch (e: Exception) { message = "保存失败：${e.message}" }
            finally { busy = false }
        }
        Unit
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) save() else message = "未获得相册保存权限，可使用验证码登录"
    }
    Column {
        Button(onClick = {
            if (!com.concept.music.service.AppPermissionManager.isPermissionEnabled(com.concept.music.service.AppPermissionType.STORAGE_EXPORT)) {
                message = "已在权限管理中关闭「相册图片写入与导出」权限，请先在设置中开启"
                return@Button
            }
            if (Build.VERSION.SDK_INT <= 28 && ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                permission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            else save()
        }, enabled = !busy) { Text("保存二维码") }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

private fun saveQr(context: Context, bitmap: Bitmap) {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "酷狗登录_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ConceptMusic")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建图片")
    try {
        resolver.openOutputStream(uri)?.use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } ?: error("无法写入图片")
        if (Build.VERSION.SDK_INT >= 29) resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
    } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
}

@Composable
fun AccountsScreen(viewModel: LibraryViewModel, onBack: () -> Unit, onLogin: () -> Unit) {
    val state by viewModel.accountState.collectAsState()
    var removing by remember { mutableStateOf<SavedAccount?>(null) }
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TextButton(onClick = onBack) { Text("返回") }; Text("账号管理", style = MaterialTheme.typography.headlineMedium) }
        item { Button(onClick = onLogin) { Text("添加账号") } }
        if (state.accounts.isEmpty()) item { Text("还没有保存的账号") }
        items(state.accounts, key = { it.userId }) { account ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (account.avatarUrl.isNotBlank()) AsyncImage(account.avatarUrl, "头像", Modifier.size(44.dp))
                        Column {
                            Text(account.nickname, style = MaterialTheme.typography.titleMedium)
                            Text("酷狗 ID：${account.userId}${if (account.userId == state.currentId) " · 当前账号" else ""}")
                        }
                    }
                    if (account.needsLogin) Text("登录已失效", color = MaterialTheme.colorScheme.error)
                    Row {
                        if (account.userId != state.currentId) TextButton(onClick = { viewModel.selectAccount(account.userId) }) { Text("切换") }
                        TextButton(onClick = onLogin) { Text("重新登录") }
                        TextButton(onClick = { removing = account }) { Text(if (account.userId == state.currentId) "退出账号" else "移除") }
                    }
                }
            }
        }
    }
    removing?.let { account -> AlertDialog(onDismissRequest = { removing = null }, title = { Text("移除账号？") },
        text = { Text("清除本机保存的 ${account.nickname} 的登录凭证和设置，其他账号保留。") },
        confirmButton = { TextButton(onClick = { viewModel.removeAccount(account.userId); removing = null }) { Text("移除") } },
        dismissButton = { TextButton(onClick = { removing = null }) { Text("取消") } }) }
}
