package com.concept.music.data.kugou

import android.util.Log
import com.concept.music.data.model.Song
import com.concept.music.data.vip.VipManager
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 酷狗官方 CSCC 播放上报引擎 (d.kugou.com)
 * 纯 Kotlin 端侧实现三段式真实播放事件上报 (qrydid -> gen -> post)。
 * 酷狗官方服务器以此为准记入用户真实听歌时长与等级，不被判定为异常刷量。
 */
object KugouCsccReporter {
    private const val TAG = "KugouCSCC"
    private const val CSCC_HOST = "https://d.kugou.com"
    private const val CSCC_VERSION = "10597"
    private const val CSCC_APPKEY = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    private const val QRYDID_SALT = "pbKC7zn{4U*ydo2M1Rir"

    // Lite 客户端公钥
    private const val PUBLIC_KEY_LITE = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"

    private val client = NetworkClientProvider.baseClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(serialDispatcher + SupervisorJob())

    private data class CsccSession(
        val cookie: String,
        val clientTime: Long,
        val serverStr: String,
        val field2: String,
        val expireTime: Long
    )

    @Volatile
    private var cachedSession: CsccSession? = null
    @Volatile
    internal var currentPlayingSongId: String? = null
    @Volatile
    internal var songStartTimeMs: Long = 0L
    @Volatile
    private var lastPostedSec: Long = 0L
    @Volatile
    private var lastPostWallMs: Long = 0L

    internal var eventObserverForTest: ((eventType: String, song: Song, durationMs: Long, state: String) -> Unit)? = null

    /**
     * 歌曲开始播放时上报 start 事件
     */
    fun onSongStart(song: Song) {
        val songId = song.id
        currentPlayingSongId = songId
        songStartTimeMs = System.currentTimeMillis()
        eventObserverForTest?.invoke("start", song, 0L, "开始播放")
        if (eventObserverForTest != null) return

        scope.launch {
            val mixSongId = resolveMixSongId(song)
            if (mixSongId > 0L) {
                runCatching {
                    sendEvent(mixSongId = mixSongId, isStart = true, durationMs = 0L, state = "开始播放")
                }.onFailure { Log.e(TAG, "CSCC start 上报异常: ${it.message}") }
            }
        }
    }

    /**
     * 歌曲切歌、暂停或结束时上报 end 事件 (真实播放时长大于 10 秒才计入)
     */
    fun onSongEnd(song: Song, playedDurationMs: Long = 0L, state: String = "完整播放") {
        val startSnapshot = songStartTimeMs
        val actualPlayedMs = if (playedDurationMs > 0L) {
            playedDurationMs
        } else if (startSnapshot > 0L) {
            (System.currentTimeMillis() - startSnapshot).coerceAtLeast(0L)
        } else {
            0L
        }
        if (currentPlayingSongId == song.id) {
            currentPlayingSongId = null
            songStartTimeMs = 0L
        }

        // 听歌小于 10 秒视为试听或误点，不发送 end 事件
        if (actualPlayedMs < 10000L) {
            Log.d(TAG, "跳过 CSCC end 事件，播放时长不足 10s ($actualPlayedMs ms)")
            return
        }

        eventObserverForTest?.invoke("end", song, actualPlayedMs, state)
        if (eventObserverForTest != null) return

        scope.launch {
            val mixSongId = resolveMixSongId(song)
            if (mixSongId > 0L) {
                runCatching {
                    val settled = sendEvent(
                        mixSongId = mixSongId,
                        isStart = false,
                        durationMs = actualPlayedMs,
                        state = state
                    )
                    if (settled) {
                        Log.d(TAG, "CSCC end 上报成功: ${song.title}, 时长=${actualPlayedMs / 1000}秒, state=$state")
                        com.concept.music.data.vip.ListeningGradeManager.scheduleCloudSyncAfterCscc(actualPlayedMs / 1000L)
                    }
                }.onFailure { Log.e(TAG, "CSCC end 上报异常: ${it.message}") }
            }
        }
    }

    /**
     * 解析真实的数字音频 ID (mxid / mixsongid)
     */
    suspend fun resolveMixSongId(song: Song): Long = withContext(serialDispatcher) {
        if (song.albumAudioId > 0L) return@withContext song.albumAudioId
        if (song.audioId > 0L) return@withContext song.audioId
        val parsedMixId = song.mixSongId.toLongOrNull()
        if (parsedMixId != null && parsedMixId > 0L) return@withContext parsedMixId
        val parsedId = song.id.toLongOrNull()
        if (parsedId != null && parsedId > 0L) return@withContext parsedId

        // 若为 Hash，向酷狗快速反查真实 audio_id
        if (song.hash.isNotBlank()) {
            return@withContext runCatching {
                val url = "https://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=${song.hash}".toHttpUrl()
                val req = Request.Builder().url(url).build()
                client.newCall(req).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    val json = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
                    json.get("audio_id")?.asLong ?: json.get("songid")?.asLong ?: 0L
                }
            }.getOrDefault(0L)
        }
        0L
    }

    private suspend fun getOrCreateSession(device: DeviceIdentity): CsccSession = withContext(serialDispatcher) {
        val now = System.currentTimeMillis()
        val session = cachedSession
        if (session != null && session.expireTime > now) {
            return@withContext session
        }

        // 1. POST /v3/qrydid
        val nowSec = (now / 1000).toString()
        val didData = "{\"machine\":\"${device.dev}\",\"mid\":\"${device.mid}\",\"uuid\":\"${device.guid}\",\"wh\":[1080,2400]}"
        val didSign = md5("_t${nowSec}appidand02${QRYDID_SALT}${didData}")
        val qryUrl = "$CSCC_HOST/v3/qrydid".toHttpUrl().newBuilder()
            .addQueryParameter("appid", "and02")
            .addQueryParameter("_t", nowSec)
            .addQueryParameter("sign", didSign)
            .build()

        val qryReq = Request.Builder()
            .url(qryUrl)
            .post(didData.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val deviceId = client.newCall(qryReq).execute().use { resp ->
            val json = com.google.gson.JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            json.getAsJsonObject("data")?.get("deviceid")?.asString
                ?: throw IllegalStateException("CSCC 未返回有效 deviceid")
        }

        // 2. GET /v2/gen (crypto.randomBytes(16).toString('hex') -> 32 个十六进制字符 = 32 字节 AES-256 密钥)
        val field2 = randomHex(32)
        val sPlain = "${device.guid}\t$field2\t$now\t$deviceId"
        val sEncrypted = rsaEncryptPkcs1(sPlain, PUBLIC_KEY_LITE)
        val sBase64 = Base64.getEncoder().encodeToString(sEncrypted)

        val genTimeSec = (System.currentTimeMillis() / 1000).toString()
        val genSign = md5("_t${genTimeSec}appid3116s${sBase64}${CSCC_APPKEY}")
        val genUrl = "$CSCC_HOST/v2/gen".toHttpUrl().newBuilder()
            .addQueryParameter("s", sBase64)
            .addQueryParameter("appid", "3116")
            .addQueryParameter("_t", genTimeSec)
            .addQueryParameter("sign", genSign)
            .build()

        val genReq = Request.Builder().url(genUrl).build()
        val newSession = client.newCall(genReq).execute().use { resp ->
            val json = com.google.gson.JsonParser.parseString(resp.body?.string().orEmpty()).asJsonObject
            val encDataB64 = json.get("data")?.asString ?: throw IllegalStateException("CSCC gen 响应数据无效")
            val encDataBytes = Base64.getDecoder().decode(encDataB64)
            val iv = device.guid.take(16).toByteArray(Charsets.UTF_8)
            val key = field2.toByteArray(Charsets.UTF_8)
            val decBytes = aesCbcDecrypt(encDataBytes, key, iv)
            val decJson = com.google.gson.JsonParser.parseString(String(decBytes, Charsets.UTF_8)).asJsonObject

            CsccSession(
                cookie = decJson.get("cookie").asString,
                clientTime = decJson.get("clienttime").asLong,
                serverStr = decJson.get("serverstr").asString,
                field2 = field2,
                expireTime = now + 25 * 60 * 1000L
            )
        }

        cachedSession = newSession
        newSession
    }

    private fun nextPacketEpochSec(): Long {
        val rawSec = System.currentTimeMillis() / 1000L
        val nextSec = if (rawSec <= lastPostedSec) lastPostedSec + 1L else rawSec
        lastPostedSec = nextSec
        return nextSec
    }

    private suspend fun postPacketWithRetry(
        device: DeviceIdentity,
        userId: String,
        businessId: Int,
        fieldsBuilder: (nowMs: Long, nowSec: Long) -> String
    ): Boolean {
        val elapsedSinceLast = System.currentTimeMillis() - lastPostWallMs
        if (elapsedSinceLast in 0L..349L) {
            delay(350L - elapsedSinceLast)
        }
        val session1 = getOrCreateSession(device)
        val sec1 = nextPacketEpochSec()
        val ms1 = sec1 * 1000L + (System.currentTimeMillis() % 1000L)
        lastPostWallMs = System.currentTimeMillis()
        if (postPacket(session1, device, userId, sec1, businessId, fieldsBuilder(ms1, sec1))) {
            return true
        }
        // 会话过期或被拒绝时自动清理缓存会话并重试一次
        cachedSession = null
        delay(450L)
        val session2 = getOrCreateSession(device)
        val sec2 = nextPacketEpochSec()
        val ms2 = sec2 * 1000L + (System.currentTimeMillis() % 1000L)
        lastPostWallMs = System.currentTimeMillis()
        return postPacket(session2, device, userId, sec2, businessId, fieldsBuilder(ms2, sec2))
    }

    private suspend fun sendEvent(
        mixSongId: Long,
        isStart: Boolean,
        durationMs: Long,
        state: String = "完整播放"
    ): Boolean = withContext(serialDispatcher) {
        val sessionContext = VipManager.accounts.context() ?: return@withContext false
        val session = sessionContext.session
        val device = VipManager.device()
        val userId = session.userId
        val durationSec = (durationMs / 1000L).coerceAtLeast(10L)

        if (isStart) {
            postPacketWithRetry(device, userId, 10048) { nowMs, _ ->
                "type_id=20431&action=play&fo3=3878,4320,4326,5747,5749,7229,7289,7863&spt=0&sty=手动&mixsongid=$mixSongId&source=46&ivar1=1&fo=/专辑播放页&mid=${device.mid}&uuid=${device.guid}&ss1=1&ss2=1&sys=14&mod=${device.dev}&channelid=18&ip=0.0.0.0&net=1&ver=$CSCC_VERSION&gitversion=7aa8a76&time=$nowMs&userid=$userId&ss3=${md5(UUID.randomUUID().toString())}"
            }
        } else {
            // 1. 核心播放结算事件 (10048 / type_id=4)，酷狗云端仅对 state=完整播放 累加 d_sec
            val settled10048 = postPacketWithRetry(device, userId, 10048) { nowMs, _ ->
                "type_id=4&action=play&fo3=3878,4320,4326,5747,5749,7229,7289,7863&duration=$durationMs&svar3=0&svar2=0&fo=我的音乐/主态/自建歌单/RU&sty=手动&state=完整播放&mixsongid=$mixSongId&source=46&ivar3=0&type=1&fs=1.0&ivar1=1&mid=${device.mid}&uuid=${device.guid}&ss1=1&ss2=1&sys=14&mod=${device.dev}&channelid=18&ip=0.0.0.0&net=1&ver=$CSCC_VERSION&gitversion=7aa8a76&time=$nowMs&userid=$userId&ss3=${md5(UUID.randomUUID().toString())}"
            }

            // 2. 对齐 KuGouMusicApi (user_listen_report.js)：上报 30025 单曲听歌明细与 12904 听歌时长心跳
            val detailState = if (state == "完整播放") "播放完成" else "切换歌曲"
            runCatching {
                postPacketWithRetry(device, userId, 30025) { nowMs, _ ->
                    "type_id=30025&time=$nowMs&mid=${device.mid}&uuid=${device.guid}&Check=0&net=1&ver=$CSCC_VERSION&channelid=18&mod=${device.dev}&sys=14&userid=$userId&gitversion=8767fd9&ip=0.0.0.0&action=play&ivar1=1&ivar3=0&svar1=音频&svar2=0&svar3=0&svar6=正式播放&svar8=-1&svar9=否&svar10=未知&svar12=否&svar16=默认音质&svar20=音乐&svar21=否&svar22=无&svar24=2&svar28=-999&svar34=手动切歌&svar38=原始&svar39=关&svar40=0&svar41=主包&svar42=1&svar43=0&svar44=1&svar47=无效果&svar60=普通歌曲&svar61=关闭&svar66=0&svar81=否&svar86=0&svar93=0&svar102=${nowMs - durationMs}&svar103=$nowMs&svar106=1&svar128=否&svar133=已登录&svar138=长按&bvar1=$durationSec&bvar2=1&bvar5=$durationSec&bvar9=$durationSec&duration=$durationMs&mxid=$mixSongId&source=2&sty=手动&state=$detailState&play_Check=1&fo=搜索/搜索结果页&fo3=3149,3366,4455,6377&fs=1.0&ss1=1&ss2=1&ss3=${md5(UUID.randomUUID().toString())}"
                }
                postPacketWithRetry(device, userId, 12904) { nowMs, nowSec ->
                    "type_id=12904&time=$nowMs&mid=${device.mid}&uuid=${device.guid}&Check=0&net=1&ver=$CSCC_VERSION&channelid=18&mod=${device.dev}&sys=14&userid=$userId&gitversion=8767fd9&ip=0.0.0.0&ivar1=$durationSec&ivar2=1&ivar4=0&ivar5=${nowSec - durationSec}&ivar6=$nowSec&ivar7=$durationSec&ivar9=1&svar1=音频&svar3=音乐&svar4=概念版&svar5=手动播放&svar7=否&svar8=前台&svar9=主应用&svar10=扬声器&svar14=否&svar19=未知&mxid=$mixSongId&source=2&fo=搜索/搜索结果页&fo3=3149,3366,4455,6377&ss1=1&ss2=1&ss3=${md5(UUID.randomUUID().toString())}"
                }
            }
            settled10048
        }
    }

    private fun postPacket(
        csccSession: CsccSession,
        device: DeviceIdentity,
        userId: String,
        nowSec: Long,
        businessId: Int,
        fields: String
    ): Boolean {
        val eventBuffer = fields.toByteArray(Charsets.UTF_8)
        val deflatedEvent = deflate(eventBuffer)

        val line1 = "4\t1\t${device.guid}\t0\t3116\t$CSCC_VERSION\t18\t14\t${device.dev}\t${device.guid}\t${device.mid}\t0\t000000000000000000000000000000000000"
        val line2 = "${eventBuffer.size}\t$businessId\t0\t$nowSec\t1\t$userId\t0\t0"
        val headerBytes = "$line1\r\n$line2\r\n".toByteArray(Charsets.UTF_8)
        val plain = headerBytes + deflatedEvent
        val deflatedPlain = deflate(plain)

        val aesKeyStr = md5("${device.guid}${csccSession.clientTime}${csccSession.field2}${csccSession.serverStr}")
        val aesKey = aesKeyStr.toByteArray(Charsets.UTF_8)
        val iv = device.guid.take(16).toByteArray(Charsets.UTF_8)
        val encrypted = aesCbcEncrypt(deflatedPlain, aesKey, iv)

        val queryParams = sortedMapOf(
            "appid" to "3116",
            "cookie" to csccSession.cookie,
            "length" to plain.size.toString(),
            "_t" to nowSec.toString()
        )
        val sortedString = queryParams.entries.joinToString("") { "${it.key}${it.value}" }
        val sign = md5(sortedString.toByteArray(Charsets.UTF_8) + CSCC_APPKEY.toByteArray(Charsets.UTF_8) + encrypted)

        val postUrl = "$CSCC_HOST/v2/post".toHttpUrl().newBuilder().apply {
            queryParams.forEach { (k, v) -> addQueryParameter(k, v) }
            addQueryParameter("sign", sign)
        }.build()

        val postReq = Request.Builder()
            .url(postUrl)
            .header("User-Agent", "Android14-1070-$CSCC_VERSION-18-0-CsccPost-wifi")
            .header("KG-Rec", "1")
            .header("Content-Type", "application/octet-stream")
            .post(encrypted.toRequestBody("application/octet-stream".toMediaType()))
            .build()

        return client.newCall(postReq).execute().use { resp ->
            val bodyStr = resp.body?.string().orEmpty()
            val ok = runCatching {
                val obj = com.google.gson.JsonParser.parseString(bodyStr).asJsonObject
                val status = obj.get("status")?.asInt ?: if (resp.isSuccessful) 1 else 0
                val errcode = obj.get("errcode")?.asInt ?: 0
                status == 1 && errcode == 0
            }.getOrDefault(resp.isSuccessful)
            Log.d(TAG, "CSCC businessId=$businessId 响应码: ${resp.code}, body=$bodyStr")
            ok
        }
    }

    private fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        return try {
            deflater.setInput(data)
            deflater.finish()
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buffer)
                if (count <= 0 && deflater.needsInput()) break
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun aesCbcEncrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun aesCbcDecrypt(data: ByteArray, key: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val keySpec = SecretKeySpec(key, "AES")
        val ivSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec)
        return cipher.doFinal(data)
    }

    private fun rsaEncryptPkcs1(plainText: String, publicKeyB64: String): ByteArray {
        val keyBytes = Base64.getDecoder().decode(publicKeyB64)
        val spec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        val key = keyFactory.generatePublic(spec)
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
    }

    private fun randomHex(len: Int): String {
        val random = SecureRandom()
        val bytes = ByteArray(len / 2)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun md5(text: String): String = md5(text.toByteArray(Charsets.UTF_8))

    private fun md5(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
