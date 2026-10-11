package com.concept.music.ui.account

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.concept.music.data.kugou.*
import com.google.gson.JsonParser

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun VerificationDialog(request: VerificationRequest, onAnswer: (VerificationAnswer?) -> Unit) {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onAnswer)
    var error by remember(request.id) { mutableStateOf<String?>(null) }
    val webView = remember(request.id) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && request.url.host != "appassets.androidplatform.net"
                override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                    if (request.isForMainFrame) error = "验证页面加载失败，请取消后重试"
                }
                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, failure: android.net.http.SslError) { handler.cancel() }
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this, "VerificationBridge", setOf("https://appassets.androidplatform.net")) { _, message, _, mainFrame, _ ->
                    if (mainFrame) {
                        val data = message.data.orEmpty()
                        if (data.length <= 100_000) runCatching {
                            val obj = JsonParser.parseString(data).asJsonObject
                            if (obj.has("error")) error = "验证码组件加载或验证失败，请取消后重试"
                            else {
                                val code = obj.get("code").asString
                                val sid = obj.get("sid").asString
                                val edt = obj.get("edt").asString
                                if (code.isNotBlank() && sid.isNotBlank() && edt.isNotBlank()) callback(VerificationAnswer(code, sid, edt))
                            }
                        }.onFailure { error = "验证结果无效，请重试" }
                    }
                }
                loadUrl("https://appassets.androidplatform.net/assets/verification/index.html?type=${request.type}&appid=${Uri.encode(request.txAppId)}")
            } else error = "请更新 Android System WebView 后再进行安全验证"
        }
    }
    DisposableEffect(webView) { onDispose { webView.stopLoading(); webView.destroy() } }
    Dialog(onDismissRequest = { callback(null) }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Text("酷狗安全验证", style = MaterialTheme.typography.titleLarge)
                Text(request.message, Modifier.padding(vertical = 8.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 460.dp))
                TextButton(onClick = { callback(null) }) { Text("取消验证") }
            }
        }
    }
}
