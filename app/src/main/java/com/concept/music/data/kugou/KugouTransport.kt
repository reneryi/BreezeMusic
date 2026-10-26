package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.time.Clock
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 全局共享的 OkHttp 客户端连接池与调度器提供者（避免多实例连接池分裂与 SSL 握手重复开销）
 */
object NetworkClientProvider {
    val sharedConnectionPool = ConnectionPool(12, 5, TimeUnit.MINUTES)
    val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 16
    }

    val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(sharedConnectionPool)
            .dispatcher(sharedDispatcher)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

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
    suspend fun requestRawBytes(path: String, session: KugouSession? = null, query: Map<String, String> = emptyMap(),
        rawBody: String? = null, host: String = "https://gateway.kugou.com", method: String = "POST",
        headers: Map<String, String> = emptyMap(), sign: Boolean = true, includeDefaultParams: Boolean = true,
        device: DeviceIdentity? = null): ByteArray =
        callRaw(path, session, query, rawBody, host, method, headers, sign, includeDefaultParams, device)
}

class DirectKugouTransport(
    private val identity: () -> DeviceIdentity,
    private val client: OkHttpClient = NetworkClientProvider.baseClient,
    private val clock: Clock = Clock.systemUTC()
) : KugouTransport {

    companion object {
        private val CLEARTEXT_ALLOWED_HOSTS = setOf(
            "fs.android.kugou.com",
            "bssdlbig.kugou.com",
            "bssdl.kugou.com",
            "trackercdn.kugou.com",
            "mobilecdnbj.kugou.com",
            "userinfo.user.kugou.com",
            "relation.user.kugou.com",
            "usercenter.kugou.com",
            "update.user.kugou.com"
        )

        internal fun enforceHttpsForSensitiveHost(
            rawHost: String,
            session: KugouSession?,
            query: Map<String, String>,
            body: JsonObject? = null
        ): String {
            if (!rawHost.startsWith("http://", ignoreCase = true)) return rawHost
            val hostName = runCatching { rawHost.toHttpUrl().host.lowercase() }.getOrDefault("")
            // MockWebServer in local unit tests uses localhost / 127.0.0.1 / machine hostname without dots
            if (hostName == "localhost" || hostName == "127.0.0.1" || hostName == "::1" || !hostName.contains('.')) return rawHost
            val hasSensitiveParam = session != null ||
                "token" in query || "vip_token" in query || "mobile" in query ||
                body?.has("mobile") == true || body?.has("token") == true || body?.has("p3") == true
            return if (hasSensitiveParam && hostName !in CLEARTEXT_ALLOWED_HOSTS) {
                "https://" + rawHost.substring(7)
            } else if (hostName !in CLEARTEXT_ALLOWED_HOSTS) {
                "https://" + rawHost.substring(7)
            } else {
                rawHost
            }
        }
    }

    override suspend fun call(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?) =
        callAdvanced(path, session, query, body, host, method, web, headers, device, sign = true, includeDefaultParams = true)

    override suspend fun callAdvanced(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
        sign: Boolean, includeDefaultParams: Boolean): ApiResponse = withContext(Dispatchers.IO) {
        val isIdempotentOrTracker = method.equals("GET", ignoreCase = true) ||
            path.contains("priv_url") || path.contains("/url") || path.contains("privilege") ||
            path.contains("search") || path.contains("recommend")
        val maxAttempts = if (isIdempotentOrTracker) 2 else 1
        var lastError: IOException? = null

        for (attempt in 1..maxAttempts) {
            try {
                return@withContext executeSingleCall(
                    path = path,
                    session = session,
                    query = query,
                    body = body,
                    host = host,
                    method = method,
                    web = web,
                    headers = headers,
                    device = device,
                    sign = sign,
                    includeDefaultParams = includeDefaultParams
                )
            } catch (e: IOException) {
                lastError = e
                if (attempt < maxAttempts) {
                    delay(220L * attempt)
                }
            }
        }
        throw (lastError ?: IOException("网络连接失败，请检查网络后重试"))
    }

    private suspend fun executeSingleCall(
        path: String,
        session: KugouSession?,
        query: Map<String, String>,
        body: JsonObject?,
        host: String,
        method: String,
        web: Boolean,
        headers: Map<String, String>,
        device: DeviceIdentity?,
        sign: Boolean,
        includeDefaultParams: Boolean
    ): ApiResponse {
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
        val safeHost = enforceHttpsForSensitiveHost(host, session, params, body)
        val url = safeHost.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val isBodylessMethod = method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)
        val request = Request.Builder().url(url)
            .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .apply { if (includeDefaultParams) header("dfid", d.dfid).header("mid", d.mid).header("clienttime", params.getValue("clienttime")) }
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, if (isBodylessMethod) null else payload.toRequestBody(
                (headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value ?: "application/json; charset=utf-8").toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络连接失败，请检查网络后重试", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            val bodyString = it.body?.string().orEmpty()
                            val root = JsonParser.parseString(bodyString)
                            if (!root.isJsonObject) throw IOException("酷狗返回了无法识别的响应")
                            val result = ApiResponse(root.asJsonObject, it.header("ssa-code") ?: root.asJsonObject.text("ssaCode"), it.code)
                            if (continuation.isActive) continuation.resume(result)
                        } catch (e: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(IOException("酷狗响应解析失败，请稍后重试", e))
                        }
                    }
                }
            })
        }
    }

    override suspend fun callRaw(path: String, session: KugouSession?, query: Map<String, String>,
        rawBody: String?, host: String, method: String, headers: Map<String, String>,
        sign: Boolean, includeDefaultParams: Boolean, device: DeviceIdentity?): ByteArray =
        requestRawBytes(path, session, query, rawBody, host, method, headers, sign, includeDefaultParams, device)

    override suspend fun requestRawBytes(path: String, session: KugouSession?, query: Map<String, String>,
        rawBody: String?, host: String, method: String, headers: Map<String, String>,
        sign: Boolean, includeDefaultParams: Boolean, device: DeviceIdentity?): ByteArray = withContext(Dispatchers.IO) {
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
        val safeHost = enforceHttpsForSensitiveHost(host, session, params, null)
        val url = safeHost.toHttpUrl().newBuilder().addPathSegments(path.trimStart('/')).apply {
            params.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        val contentType = headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value ?: "text/plain; charset=utf-8"
        val isBodylessMethod = method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)
        val request = Request.Builder().url(url)
            .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .apply { if (includeDefaultParams) header("dfid", d.dfid).header("mid", d.mid).header("clienttime", params.getValue("clienttime")) }
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1")
            .header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .apply { headers.forEach { (key, value) -> header(key, value) } }
            .method(method, if (isBodylessMethod) null else payload.toRequestBody(contentType.toMediaType()))
            .build()
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("网络连接失败，请检查网络后重试", e))
                }
                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        try {
                            if (!it.isSuccessful) {
                                throw IOException("HTTP ${it.code} for $url")
                            }
                            val bytes = it.body?.bytes() ?: ByteArray(0)
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (e: Exception) {
                            val ioEx = e as? IOException ?: IOException("酷狗响应读取失败", e)
                            if (continuation.isActive) continuation.resumeWithException(ioEx)
                        }
                    }
                }
            })
        }
    }
}
