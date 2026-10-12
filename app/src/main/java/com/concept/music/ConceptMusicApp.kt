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
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

class ConceptMusicApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        VipManager.initialize(this)
        AmbientSoundManager.init(this)
        SystemLyricBridge.init(this)
        VipGuardianManager.init(this)
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

        val okHttpClient = OkHttpClient.Builder()
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Referer", "https://www.kugou.com/")
                    .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
                    .build()
                chain.proceed(request)
            }
            .build()

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

