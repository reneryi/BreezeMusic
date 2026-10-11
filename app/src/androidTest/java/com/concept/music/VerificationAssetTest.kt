package com.concept.music

import android.annotation.SuppressLint
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import com.concept.music.data.kugou.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class VerificationAssetTest {
    @Test fun credentialsSurviveStoreRecreationAndRemainSeparated() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "test_${UUID.randomUUID()}_"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefix + name, mode)
        }
        try {
            val first = EncryptedSessionStore(context)
            val device = first.device()
            first.saveSession(KugouSession("123", "fixture-a", "t1-a", "vip-a"))
            first.saveSession(KugouSession("456", "fixture-b"))
            val next = EncryptedSessionStore(context)
            assertEquals(device, next.device())
            assertEquals("fixture-a", next.session("123")!!.token)
            assertEquals("t1-a", next.session("123")!!.t1)
            next.removeSession("123")
            assertNull(next.session("123"))
            assertEquals("fixture-b", next.session("456")!!.token)
            val ciphertext = context.getSharedPreferences("kugou_secrets_v2", Context.MODE_PRIVATE).getString("456", "")!!
            assertFalse(ciphertext.contains("fixture-b"))
        } finally {
            listOf("kugou_accounts_v2", "kugou_secrets_v2", "kugou_session").forEach { base.deleteSharedPreferences(prefix + it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test fun bundledWasmLoadsAndProducesVerificationEvidenceInAndroidWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var web: WebView
        val loaded = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
            web = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                    override fun onPageFinished(view: WebView, url: String) { loaded.countDown() }
                }
                loadUrl("https://appassets.androidplatform.net/assets/verification/index.html?type=32")
            }
        }
        try {
            assertTrue("Verification page did not load", loaded.await(20, TimeUnit.SECONDS))
            var result = ""
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (System.nanoTime() < deadline) {
                val latch = CountDownLatch(1); val value = AtomicReference<String>()
                instrumentation.runOnMainSync {
                    web.evaluateJavascript("(function(){try{return (!document.getElementById('start').disabled && evidence.get_sid().length>0 && evidence.get_edt().length>0)?'ready':document.getElementById('status').textContent;}catch(e){return String(e);}})()") {
                        value.set(it); latch.countDown()
                    }
                }
                assertTrue(latch.await(3, TimeUnit.SECONDS))
                result = value.get()
                if (result == "\"ready\"") break
                Thread.sleep(200)
            }
            assertEquals("WASM initialization failed: $result", "\"ready\"", result)
        } finally { instrumentation.runOnMainSync { web.destroy() } }
    }
}
