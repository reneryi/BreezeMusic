package com.concept.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.VipGuardianManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ConceptMusicApp : Application(), ImageLoaderFactory {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 核心依赖轻量初始化（内存态与惰性 KeyStore），消除冷启动主线程阻塞
        VipManager.initialize(this)
        SystemLyricBridge.init(this)

        // 非首屏强依赖的后台守护与环境音合成器延后在 IO 线程错峰初始化，保障刚打开 App 时首屏动画满帧丝滑
        appScope.launch {
            AmbientSoundManager.init(this@ConceptMusicApp)
            delay(1500L)
            VipGuardianManager.init(this@ConceptMusicApp)
        }
    }

    override fun newImageLoader(): ImageLoader {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })

        val sslContext = SSLContext.getInstance("SSL").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val imageDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 10
        }

        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(imageDispatcher)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://www.kugou.com/")
                    .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
                    .build()
                chain.proceed(request)
            }
            .build()

        // 启用 GPU 硬件位图 (allowHardware = true)，将封面纹理直接驻留显存，消除进入新页面与滚动列表时的主线程位图上传卡顿
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .allowHardware(true)
            .components {
                add(coil.intercept.Interceptor { chain ->
                    val result = chain.proceed(chain.request)
                    if (android.os.Build.VERSION.SDK_INT >= 34 && result is coil.request.SuccessResult) {
                        val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bmp != null && bmp.config != android.graphics.Bitmap.Config.HARDWARE && bmp.hasGainmap()) {
                            runCatching { bmp.gainmap = null }
                        }
                    }
                    result
                })
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.28)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.06)
                    .build()
            }
            .crossfade(120)
            .respectCacheHeaders(false)
            .build()
    }
}
