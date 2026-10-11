package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface KugouTransport {
    suspend fun call(path: String, session: KugouSession? = null, query: Map<String, String> = emptyMap(),
        body: JsonObject? = null, host: String = "https://gateway.kugou.com", method: String = "GET",
        web: Boolean = false, headers: Map<String, String> = emptyMap(), device: DeviceIdentity? = null): ApiResponse
    suspend fun callAdvanced(path: String, session: KugouSession? = null, query: Map<String, String> = emptyMap(),
        body: JsonObject? = null, host: String = "https://gateway.kugou.com", method: String = "GET",
        web: Boolean = false, headers: Map<String, String> = emptyMap(), device: DeviceIdentity? = null,
        sign: Boolean = true, includeDefaultParams: Boolean = true): ApiResponse =
        call(path, session, query, body, host, method, web, headers, device)
    suspend fun callRaw(path: String, session: KugouSession? = null, query: Map<String, String> = emptyMap(),
        rawBody: String? = null, host: String = "https://gateway.kugou.com", method: String = "POST",
        headers: Map<String, String> = emptyMap(), sign: Boolean = true, includeDefaultParams: Boolean = true,
        device: DeviceIdentity? = null): ByteArray = ByteArray(0)
}

class DirectKugouTransport(
    private val identity: () -> DeviceIdentity,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build(),
    private val clock: Clock = Clock.systemUTC()
) : KugouTransport {
    override suspend fun call(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?) =
        callAdvanced(path, session, query, body, host, method, web, headers, device, sign = true, includeDefaultParams = true)

    override suspend fun callAdvanced(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
        sign: Boolean, includeDefaultParams: Boolean): ApiResponse {
        val d = device ?: identity()
        val params = linkedMapOf<String, String>()
        if (includeDefaultParams) {
            params.putAll(mapOf("dfid" to d.dfid, "mid" to d.mid, "uuid" to "-", "appid" to "3116",
                "clientver" to "11440", "clienttime" to clock.instant().epochSecond.toString()))
            session?.let { params["token"] = it.token; params["userid"] = it.userId }
        }
        params.putAll(query)
        val payload = body?.let(KugouSigner.gson::toJson).orEmpty()
        if (sign) params["signature"] = KugouSigner.signature(params, payload, web)
        val url = host.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val request = Request.Builder().url(url)
            .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .apply { if (includeDefaultParams) header("dfid", d.dfid).header("mid", d.mid).header("clienttime", params.getValue("clienttime")) }
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, if (method == "GET") null else payload.toRequestBody(
                (headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value ?: "application/json; charset=utf-8").toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络连接失败，请检查网络后重试"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val bodyString = it.body?.string().orEmpty()
                            runCatching { android.util.Log.d("KugouTransport", "[$method] $path (HTTP ${it.code}): $bodyString") }
                            val root = JsonParser.parseString(bodyString)
                            if (!root.isJsonObject) throw IOException("酷狗返回了无法识别的响应")
                            val result = ApiResponse(root.asJsonObject, it.header("ssa-code") ?: root.asJsonObject.text("ssaCode"), it.code)
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            runCatching { android.util.Log.e("KugouTransport", "Failed to parse $path: ${e.message}") }
                            if (continuation.isActive) continuation.resumeWithException(IOException("酷狗响应解析失败，请稍后重试"))
                        }
                    }
                }
            })
        }
    }

    override suspend fun callRaw(path: String, session: KugouSession?, query: Map<String, String>,
        rawBody: String?, host: String, method: String, headers: Map<String, String>,
        sign: Boolean, includeDefaultParams: Boolean, device: DeviceIdentity?): ByteArray {
        val d = device ?: identity()
        val params = linkedMapOf<String, String>()
        if (includeDefaultParams) {
            params.putAll(mapOf("dfid" to d.dfid, "mid" to d.mid, "uuid" to "-", "appid" to "3116",
                "clientver" to "11440", "clienttime" to clock.instant().epochSecond.toString()))
            session?.let { params["token"] = it.token; params["userid"] = it.userId }
        }
        params.putAll(query)
        val payload = rawBody.orEmpty()
        if (sign) params["signature"] = KugouSigner.signature(params, payload, false)
        val url = host.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val contentType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value ?: "text/plain; charset=utf-8"
        val request = Request.Builder().url(url)
            .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .apply { if (includeDefaultParams) header("dfid", d.dfid).header("mid", d.mid).header("clienttime", params.getValue("clienttime")) }
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, if (method == "GET") null else payload.toRequestBody(contentType.toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络连接失败，请检查网络后重试"))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val bytes = it.body?.bytes() ?: ByteArray(0)
                            runCatching { android.util.Log.d("KugouTransport", "[$method] $path (HTTP ${it.code}): ${bytes.size} bytes") }
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(IOException("酷狗响应读取失败"))
                        }
                    }
                }
            })
        }
    }
}
