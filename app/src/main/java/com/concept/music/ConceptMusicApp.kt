package com.concept.music

import android.app.Application
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.concept.music.data.kugou.NetworkClientProvider
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.FocusPodManager
import com.concept.music.service.MusicDownloadManager
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.VipGuardianManager
import com.concept.music.ui.theme.ThemeManager
import okhttp3.Dispatcher
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class ConceptMusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.init(this)
        com.concept.music.service.AppPermissionManager.init(this)
        VipManager.initialize(this)
        MusicDownloadManager.init(this)
        com.concept.music.service.AudioEffectManager.init(this)
        AmbientSoundManager.init(this)
        FocusPodManager.init(this)
        SystemLyricBridge.init(this)
        VipGuardianManager.init(this)
    }

    override fun newImageLoader(): ImageLoader {
        val imageDispatcher = Dispatcher().apply {
            maxRequests = 32
            maxRequestsPerHost = 10
        }

        // 复用全局共享连接池，减少 TLS 握手与 Socket 创建开销；
        // 对酷狗静态图片 CDN (如 webimg.bssdl.kugou.com 等无多级通配符证书的子域) 提供 HTTPS -> HTTP 降级容错
        val okHttpClient = NetworkClientProvider.baseClient.newBuilder()
            .dispatcher(imageDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://www.kugou.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                try {
                    chain.proceed(request)
                } catch (sslEx: SSLException) {
                    val url = request.url
                    val host = url.host.lowercase()
                    val isPublicImageCdn = (host.endsWith(".kugou.com") || host.endsWith(".kgimg.com")) &&
                        host !in setOf(
                            "gateway.kugou.com",
                            "gatewayretry.kugou.com",
                            "login.user.kugou.com",
                            "userservice.kugou.com",
                            "vip.kugou.com",
                            "tracker.kugou.com"
                        )
                    if (url.isHttps && isPublicImageCdn) {
                        val fallbackUrl = url.newBuilder().scheme("http").build()
                        chain.proceed(request.newBuilder().url(fallbackUrl).build())
                    } else {
                        throw sslEx
                    }
                }
            }
            .build()

        // 必须保持 allowHardware(false) + ARGB_8888 并剥离 Gainmap：
        // 酷狗封面图含有非标 AGTM Gainmap 元数据，若使用 HARDWARE 位图在 Android 14+ 旋转/缩放绘制会触发 libhwui 原生闪退
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .allowHardware(false)
            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                add(coil.intercept.Interceptor { chain ->
                    val result = chain.proceed(chain.request)
                    if (Build.VERSION.SDK_INT >= 34 && result is coil.request.SuccessResult) {
                        val bmp = (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                        if (bmp != null && bmp.hasGainmap()) {
                            runCatching { bmp.gainmap = null }
                        }
                    }
                    result
                })
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.05)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()
    }
}
