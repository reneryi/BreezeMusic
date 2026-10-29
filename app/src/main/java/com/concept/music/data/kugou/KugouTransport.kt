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
            "tracker.kugou.com",
            "mobilecdnbj.kugou.com",
            "login.user.kugou.com",
            "loginserviceretry.kugou.com",
            "userinfo.user.kugou.com",
            "relation.user.kugou.com",
            "usercenter.kugou.com",
            "update.user.kugou.com",
            "wwwapi.kugou.com",
            "m.kugou.com"
        )

        internal fun enforceHttpsForSensitiveHost(
            rawHost: String,
            session: KugouSession?,
            query: Map<String, String>,
            body: JsonObject? = null
        ): String {
            val hostName = runCatching { rawHost.toHttpUrl().host.lowercase() }.getOrDefault("")
            // MockWebServer in local unit tests uses localhost / 127.0.0.1 / machine hostname without dots
            if (hostName == "localhost" || hostName == "127.0.0.1" || hostName == "::1" || !hostName.contains('.')) return rawHost
            // 二级子域 login.user.kugou.com 在酷狗服务端仅部署了 *.kugou.com 一级通配符证书，
            // Android OkHostnameVerifier 遵循 RFC 6125 会对 https://login.user.kugou.com 抛出 SSLPeerUnverifiedException，
            // 对齐上游 MakcRe/KuGouMusicApi 统一使用 http://login.user.kugou.com 或网关转发
            if (hostName == "login.user.kugou.com" && rawHost.startsWith("https://", ignoreCase = true)) {
                return "http://" + rawHost.substring(8)
            }
            if (!rawHost.startsWith("http://", ignoreCase = true)) return rawHost
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

        /**
         * 针对国内移动/联通/电信/广电不同运营商网络下的 DNS 污染、二级子域 TLS 证书不匹配或网关偶发超时，
         * 构建多通道自动容灾候选端点列表（本地 MockWebServer 单测地址保持单次请求不变）。
         */
        internal fun buildCandidateEndpoints(
            rawHost: String,
            baseHeaders: Map<String, String>
        ): List<Pair<String, Map<String, String>>> {
            val hostName = runCatching { rawHost.toHttpUrl().host.lowercase() }.getOrDefault("")
            if (hostName.isBlank() || hostName == "localhost" || hostName == "127.0.0.1" || hostName == "::1" || !hostName.contains('.')) {
                return listOf(rawHost to baseHeaders)
            }
            val loginRouterHeaders = LinkedHashMap(baseHeaders).apply {
                put("x-router", "login.user.kugou.com")
            }
            return when (hostName) {
                "login.user.kugou.com" -> listOf(
                    "http://login.user.kugou.com" to baseHeaders,
                    "https://gateway.kugou.com" to loginRouterHeaders,
                    "https://login-user.kugou.com" to baseHeaders
                )
                "loginserviceretry.kugou.com" -> listOf(
                    rawHost to baseHeaders,
                    "https://gateway.kugou.com" to loginRouterHeaders,
                    "https://login-user.kugou.com" to baseHeaders,
                    "http://login.user.kugou.com" to baseHeaders
                )
                "login-user.kugou.com" -> listOf(
                    rawHost to baseHeaders,
                    "https://gateway.kugou.com" to loginRouterHeaders,
                    "http://login.user.kugou.com" to baseHeaders
                )
                "gateway.kugou.com" -> listOf(
                    rawHost to baseHeaders,
                    "https://gatewayretry.kugou.com" to baseHeaders
                )
                "tracker.kugou.com" -> listOf(
                    rawHost to baseHeaders,
                    "http://tracker.kugou.com" to baseHeaders,
                    "https://gateway.kugou.com" to LinkedHashMap(baseHeaders).apply { put("x-router", "tracker.kugou.com") }
                )
                else -> listOf(rawHost to baseHeaders)
            }
        }
    }

    override suspend fun call(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?) =
        callAdvanced(path, session, query, body, host, method, web, headers, device, sign = true, includeDefaultParams = true)

    override suspend fun callAdvanced(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?,
        sign: Boolean, includeDefaultParams: Boolean): ApiResponse = withContext(Dispatchers.IO) {
        val candidateEndpoints = buildCandidateEndpoints(host, headers)
        var lastError: IOException? = null

        for ((index, endpoint) in candidateEndpoints.withIndex()) {
            val (candidateHost, candidateHeaders) = endpoint
            try {
                return@withContext executeSingleCall(
                    path = path,
                    session = session,
                    query = query,
                    body = body,
                    host = candidateHost,
                    method = method,
                    web = web,
                    headers = candidateHeaders,
                    device = device,
                    sign = sign,
                    includeDefaultParams = includeDefaultParams
                )
            } catch (e: IOException) {
                lastError = e
                if (index < candidateEndpoints.lastIndex) {
                    delay(150L * (index + 1))
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
        if (!com.concept.music.service.AppPermissionManager.isPermissionEnabled(com.concept.music.service.AppPermissionType.NETWORK_ACCESS)) {
            throw IOException("已关闭「网络访问与网络状态检测」权限，无法加载在线数据（可在设置-权限管理中重新开启）")
        }
        val d = device ?: identity()
        val params = linkedMapOf<String, String>()
        if (includeDefaultParams) {
            if (web) {
                params.putAll(mapOf(
                    "dfid" to d.dfid,
                    "mid" to d.mid,
                    "uuid" to "-",
                    "appid" to "3116",
                    "srcappid" to "2919",
                    "clientver" to "1000",
                    "clienttime" to clock.instant().epochSecond.toString(),
                    "platid" to "4"
                ))
            } else {
                params.putAll(mapOf(
                    "dfid" to d.dfid,
                    "mid" to d.mid,
                    "uuid" to "-",
                    "appid" to "3116",
                    "clientver" to "11440",
                    "clienttime" to clock.instant().epochSecond.toString()
                ))
            }
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
        if (!com.concept.music.service.AppPermissionManager.isPermissionEnabled(com.concept.music.service.AppPermissionType.NETWORK_ACCESS)) {
            throw IOException("已关闭「网络访问与网络状态检测」权限，无法加载在线数据（可在设置-权限管理中重新开启）")
        }
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
