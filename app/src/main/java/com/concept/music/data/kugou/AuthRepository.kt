package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.time.Clock
import java.util.Base64

class AuthRepository(private val transport: KugouTransport, private val store: SessionStore,
    private val clock: Clock = Clock.systemUTC()) {

    /**
     * [PROTO-02] 概念版设备注册 (对齐 MakcRe/KuGouMusicApi register_dev.js)
     * 首选通道请求 https://userservice.kugou.com/risk/v2/r_register_dev (playlistAesEncrypt + rsaPkcs1 + playlistAesDecrypt)，
     * 回退兼容 /v1/register_dev 与 /risk/v1/r_register_dev，当远端风控接口不可达时使用设备特征指纹生成合规 24 位 DFID。
     */
    suspend fun registerDevice(forceRefresh: Boolean = false): String? {
        val current = store.device()
        if (!forceRefresh && store.isDfidRegistered() && current.dfid.isNotBlank() && current.dfid != "-") {
            return current.dfid
        }
        val nowSec = clock.instant().epochSecond
        val activeUserId = store.activeId() ?: "0"
        val activeSession = store.activeId()?.let { store.session(it) }
        val activeToken = activeSession?.token.orEmpty()

        // 1. 首选通道：对齐上游 register_dev.js -> https://userservice.kugou.com/risk/v2/r_register_dev
        val riskV2Dfid = runCatching {
            val dataPlain = KugouSigner.gson.toJson(
                json(
                    "mid" to current.mid,
                    "uuid" to current.guid,
                    "appid" to "1014",
                    "userid" to activeUserId
                )
            )
            val (aesKey, aesCipher) = KugouSigner.playlistAesEncrypt(dataPlain)
            val pInner = KugouSigner.gson.toJson(
                json(
                    "aes" to aesKey,
                    "uid" to activeUserId,
                    "token" to activeToken
                )
            )
            val pRsa = KugouSigner.rsaPkcs1(pInner, lite = true).uppercase()
            val rawBytes = transport.requestRawBytes(
                path = "/risk/v2/r_register_dev",
                host = "https://userservice.kugou.com",
                method = "POST",
                session = activeSession,
                query = mapOf(
                    "part" to "1",
                    "plat" to "1",
                    "p" to pRsa
                ),
                rawBody = aesCipher,
                sign = true,
                includeDefaultParams = true
            )
            if (rawBytes.isNotEmpty()) {
                val utf8Text = String(rawBytes, Charsets.UTF_8).trim()
                val parsedObj = if (utf8Text.startsWith("{")) {
                    runCatching { JsonParser.parseString(utf8Text).asJsonObject }.getOrNull()
                } else {
                    val decrypted = runCatching { KugouSigner.playlistAesDecrypt(rawBytes, aesKey) }
                        .getOrElse {
                            runCatching { KugouSigner.playlistAesDecrypt(aesKey, utf8Text) }.getOrNull()
                        }
                    decrypted?.let { runCatching { JsonParser.parseString(it).asJsonObject }.getOrNull() }
                }
                val dataObj = parsedObj?.obj("data") ?: parsedObj
                dataObj?.text("dfid")?.takeIf { it.isNotBlank() && it != "-" }
            } else {
                null
            }
        }.getOrNull()

        val pPlain = KugouSigner.gson.toJson(json("uuid" to current.guid, "plat" to 1, "clienttime" to nowSec))
        val pEncrypted = runCatching { KugouSigner.rsa(pPlain).uppercase() }.getOrDefault("")
        val payloadJson = KugouSigner.gson.toJson(
            json(
                "mid" to current.mid,
                "uuid" to current.guid,
                "appid" to KugouSigner.APP_ID.toString(),
                "userid" to activeUserId
            )
        )
        val encodedPayload = Base64.getEncoder().encodeToString(payloadJson.toByteArray(Charsets.UTF_8))

        val remoteDfid = riskV2Dfid ?: runCatching {
            val resp = transport.callAdvanced(
                path = "/v1/register_dev",
                host = "https://usersdfid.kugou.com",
                method = "POST",
                query = mapOf(
                    "plat" to "1",
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "clienttime" to nowSec.toString(),
                    "appid" to KugouSigner.APP_ID.toString(),
                    "mid" to current.mid,
                    "uuid" to current.guid,
                    "userid" to activeUserId,
                    "p" to pEncrypted
                ),
                body = json("data" to encodedPayload),
                sign = true,
                includeDefaultParams = false
            )
            (resp.data.text("dfid") ?: resp.data.obj("data")?.text("dfid"))?.takeIf { it.isNotBlank() && it != "-" }
        }.getOrNull() ?: runCatching {
            val resp = transport.callAdvanced(
                path = "/risk/v1/r_register_dev",
                host = "https://usersdfid.kugou.com",
                method = "POST",
                query = mapOf(
                    "plat" to "1",
                    "clientver" to KugouSigner.CLIENT_VERSION.toString(),
                    "clienttime" to nowSec.toString(),
                    "appid" to KugouSigner.APP_ID.toString(),
                    "mid" to current.mid,
                    "uuid" to current.guid,
                    "userid" to activeUserId,
                    "p" to pEncrypted
                ),
                sign = true,
                includeDefaultParams = false
            )
            (resp.data.text("dfid") ?: resp.data.obj("data")?.text("dfid"))?.takeIf { it.isNotBlank() && it != "-" }
        }.getOrNull()

        if (!remoteDfid.isNullOrBlank() && remoteDfid != "-") {
            store.saveDfid(remoteDfid)
            return remoteDfid
        }

        val salt = if (forceRefresh) nowSec.toString() else "v1"
        val fallbackDfid = "2" + KugouSigner.md5("kugou_lite_${current.guid}_${current.mid}_$salt").lowercase().take(23)
        if (current.dfid.isBlank() || current.dfid == "-") {
            store.saveDfid(fallbackDfid)
        }
        return current.dfid.takeIf { it.isNotBlank() && it != "-" } ?: fallbackDfid
    }

    suspend fun sendCode(mobile: String): ApiResponse {
        require(mobile.matches(Regex("1[3-9][0-9]{9}"))) { "请输入有效的中国大陆手机号" }
        // The upstream captcha module deliberately uses the default dfid '-'.
        return transport.call("/v7/send_mobile_code", host = "http://login.user.kugou.com", method = "POST",
            body = json("businessid" to 5, "mobile" to mobile, "plat" to 3), device = store.device().copy(dfid = "-"))
    }
    suspend fun phone(mobile: String, code: String, userId: String? = null): LoginResult {
        require(mobile.matches(Regex("1[3-9][0-9]{9}"))) { "请输入有效的中国大陆手机号" }
        require(code.matches(Regex("[0-9]{4,8}"))) { "请输入短信验证码" }
        val now = clock.millis()
        val d = store.device()
        val encrypted = KugouSigner.encrypt(KugouSigner.gson.toJson(json("mobile" to mobile, "code" to code)))
        val body = json("plat" to 1, "support_multi" to 1, "t1" to t1(null, now), "t2" to t2(d, now),
            "clienttime_ms" to now, "mobile" to (mobile.take(2) + "*****" + mobile.takeLast(1)),
            "key" to KugouSigner.paramsKey(now),
            "pk" to KugouSigner.rsa(KugouSigner.gson.toJson(json("clienttime_ms" to now, "key" to encrypted.key))).uppercase(),
            "params" to encrypted.ciphertext, "userid" to userId, "dfid" to d.dfid, "dev" to d.dev, "gitversion" to "5f0b7c4")
        val response = transport.call("/v7/login_by_verifycode", host = "https://loginserviceretry.kugou.com", method = "POST", body = body,
            headers = mapOf("support-calm" to "1", "User-Agent" to "Android16-1070-11440-130-0-LOGIN-wifi"))
        val data = response.data
        val candidates = listOf("info_list", "user_list", "userList", "lists", "list").asSequence()
            .map { data.objects(it) }.firstOrNull { it.isNotEmpty() }.orEmpty().mapNotNull { item ->
                val id = item.text("userid") ?: item.text("userId") ?: return@mapNotNull null
                LoginCandidate(id, item.text("nickname") ?: item.text("username") ?: "账号 $id", imageUrl(item.text("pic") ?: item.text("avatar")))
            }
        if (!response.success && candidates.isNotEmpty() && response.code == 34175)
            return LoginResult.ChooseAccount(candidates)
        if (response.success && data.text("token").isNullOrBlank() && data.text("secu_params").isNullOrBlank() && candidates.isNotEmpty())
            return LoginResult.ChooseAccount(candidates)
        response.requireSuccess()
        return decodeLogin(data, encrypted.key)
    }
    suspend fun createQr(): KugouQrCode {
        val r = transport.call("/v2/qrcode", host = "https://login-user.kugou.com", web = true,
            query = mapOf("appid" to "1001", "type" to "1", "plat" to "4", "qrcode_txt" to
                "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=3116&", "srcappid" to "2919")).requireSuccess()
        val d = r.data
        val ttl = d.number("expire") ?: d.number("expires_in") ?: 120
        return KugouQrCode(d.text("qrcode") ?: error("二维码密钥缺失"), d.text("qrcode_img") ?: error("二维码图片缺失"),
            clock.millis() + ttl.coerceIn(1, 600) * 1000L)
    }
    suspend fun checkQr(key: String): ApiResponse = transport.call("/v2/get_userinfo_qrcode",
        host = "https://login-user.kugou.com", web = true,
        query = mapOf("plat" to "4", "appid" to "3116", "srcappid" to "2919", "qrcode" to key, "dev" to store.device().dev)).requireSuccess()
    fun qrSession(data: JsonObject): LoginResult.Success = decodeLogin(data, null)
    suspend fun refresh(session: KugouSession): LoginResult.Success {
        val now = clock.millis()
        val encrypted = KugouSigner.encrypt("{}")
        val p3 = KugouSigner.aes(KugouSigner.gson.toJson(json("clienttime" to now / 1000, "token" to session.token)),
            "c24f74ca2820225badc01946dba4fdf7", "adc01946dba4fdf7")
        val d = store.device()
        val response = transport.call("/v5/login_by_token", session, host = "http://login.user.kugou.com", method = "POST",
            body = json("dfid" to d.dfid, "p3" to p3, "plat" to 1, "t1" to t1(session.t1, now), "t2" to t2(d, now),
                "t3" to "MCwwLDAsMCwwLDAsMCwwLDA=", "pk" to KugouSigner.rsa(KugouSigner.gson.toJson(json("clienttime_ms" to now, "key" to encrypted.key))),
                "params" to encrypted.ciphertext, "userid" to session.userId, "clienttime_ms" to now, "dev" to d.dev)).requireSuccess()
        val decoded = decodeLogin(response.data, encrypted.key, session)
        check(decoded.session.userId == session.userId) { "刷新返回的账号不匹配，请重新登录" }
        return decoded
    }
    private fun decodeLogin(data: JsonObject, key: String?, previous: KugouSession? = null): LoginResult.Success {
        val merged = data.deepCopy()
        if (key != null) data.text("secu_params")?.let { ciphertext ->
            val plain = KugouSigner.decrypt(ciphertext, key)
            val parsed = runCatching { JsonParser.parseString(plain) }.getOrNull()
            if (parsed?.isJsonObject == true) parsed.asJsonObject.entrySet().forEach { merged.add(it.key, it.value) }
            else merged.addProperty("token", if (parsed?.isJsonPrimitive == true && parsed.asJsonPrimitive.isString) parsed.asString else plain)
        }
        fun value(name: String): String? = merged.text(name)?.takeIf { it.isNotBlank() && it != "undefined" && it != "null" }
        val id = value("userid") ?: previous?.userId ?: error("登录响应缺少账号 ID")
        val token = value("token") ?: error("登录响应缺少凭证")
        check(id.toLongOrNull()?.let { it > 0 } == true) { "登录返回了无效账号" }
        merged.text("dfid")?.let(store::saveDfid)
        val vipToken = value("vip_token") ?: value("viptoken") ?: previous?.vipToken
        return LoginResult.Success(KugouSession(id, token, value("t1") ?: previous?.t1, vipToken),
            AccountProfile(id, value("nickname") ?: "酷狗用户", imageUrl(value("pic") ?: value("avatar"))))
    }
    private fun t1(previous: String?, now: Long): String = KugouSigner.aes("${previous.orEmpty()}|$now", "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98")
    private fun t2(d: DeviceIdentity, now: Long): String = KugouSigner.aes("${d.guid}|0f607264fc6318a92b9e13c65db7cd3c|${d.mac}|${d.dev}|$now",
        "fd14b35e3f81af3817a20ae7adae7020", "17a20ae7adae7020")
}
fun imageUrl(raw: String?): String {
    if (raw.isNullOrBlank()) return ""
    var url = raw.replace("{size}", "400").replace("{param}", "400")
    if (url.startsWith("//")) url = "https:$url"
    else if (url.startsWith("http://")) url = "https://" + url.removePrefix("http://")
    return url
}
