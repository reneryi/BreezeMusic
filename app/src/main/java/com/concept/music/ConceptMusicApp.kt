package com.concept.music

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.concept.music.data.vip.VipManager
import com.concept.music.service.AmbientSoundManager
import com.concept.music.service.FocusPodManager
import com.concept.music.service.SystemLyricBridge
import com.concept.music.service.VipGuardianManager
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ConceptMusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        VipManager.initialize(this)
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

        val okHttpClient = OkHttpClient.Builder()
            .dispatcher(imageDispatcher)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://www.kugou.com/")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                chain.proceed(request)
            }
            .build()

        // 必须保持 allowHardware(false) + ARGB_8888 并剥离 Gainmap：
        // 酷狗封面图含有非标 AGTM Gainmap 元数据，若使用 HARDWARE 位图在 Android 14+ 旋转/缩放绘制会触发 libhwui 原生闪退
        return ImageLoader.Builder(this)
            .okHttpClient(okHttpClient)
            .allowHardware(false)
            .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888)
            .components {
                add(coil.intercept.Interceptor { chain ->
                    val result = chain.proceed(chain.request)
                    if (android.os.Build.VERSION.SDK_INT >= 34 && result is coil.request.SuccessResult) {
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
