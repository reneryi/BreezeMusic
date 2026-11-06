package com.concept.music.data.kugou

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

data class VerificationRequest(val id: String, val type: Int, val txAppId: String, val message: String)
data class VerificationAnswer(val code: String, val sid: String, val edt: String)

class VerificationCoordinator(private val transport: KugouTransport) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<VerificationRequest?>(null)
    val pending = mutable.asStateFlow()
    private var answer: CompletableDeferred<VerificationAnswer?>? = null
    fun complete(id: String, value: VerificationAnswer?) { if (mutable.value?.id == id) answer?.complete(value) }
    fun cancel() { answer?.cancel(); mutable.value = null }

    /**
     * 对齐 MakcRe/KuGouMusicApi module/sidedt.js + module/get_verify_info.js + module/verify_user_info.js：
     * 使用纯算法生成的模拟行为指纹 (sid / edt) 静默完成酷狗风控验证，无需弹出手动滑块验证窗。
     */
    suspend fun trySilentSimulateVerification(
        session: KugouSession?,
        eventId: String?,
        mid: String = "0",
        dfid: String = "-"
    ): Boolean {
        if (eventId.isNullOrBlank()) return false
        val uidStr = session?.userId?.ifBlank { "0" } ?: "0"
        val uidLong = uidStr.toLongOrNull() ?: 0L
        val sim = runCatching { KugouSigner.generateSimulate(mid = mid, userId = uidStr, dfid = dfid) }.getOrNull()
            ?: return false

        val verifyInfoData = suspendRunCatching {
            transport.call(
                path = "/verifyservice/v3/get_verify_info",
                session = session,
                host = "https://verifyservice.kugou.com",
                method = "POST",
                query = mapOf("clientver" to "11510"),
                body = json(
                    "edt" to sim.edt,
                    "eventid" to eventId,
                    "i" to "",
                    "platid" to 1,
                    "rtype" to 1,
                    "sid" to sim.sid,
                    "userid" to uidLong,
                    "version" to "1.0.21",
                    "wasm" to 1
                )
            ).data
        }.getOrNull()

        val reportedType = verifyInfoData?.number("v_type") ?: 93
        if (reportedType == 32) {
            // 32 为短信验证码，无法仅靠行为指纹静默完成
            return false
        }

        val candidateTypes = listOf(93, reportedType, 23).distinct()
        for (vType in candidateTypes) {
            val ok = suspendRunCatching {
                val encrypted = KugouSigner.encrypt("{}")
                val pk = KugouSigner.rsa(KugouSigner.gson.toJson(json("key" to encrypted.key))).uppercase()
                val resp = transport.call(
                    path = "/v4/verify_user_info",
                    session = session,
                    host = "https://verifyservice.kugou.com",
                    method = "POST",
                    query = mapOf("clientver" to "11510"),
                    body = json(
                        "edt" to sim.edt,
                        "eventid" to eventId,
                        "i" to "",
                        "params" to encrypted.ciphertext,
                        "pk" to pk,
                        "platid" to 1,
                        "sid" to sim.sid,
                        "userid" to uidLong,
                        "v_type" to vType,
                        "verifycode" to "",
                        "version" to "1.0.21",
                        "wasm" to 1
                    )
                )
                resp.success || resp.code == 0
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    suspend fun <T> execute(session: KugouSession?, interactive: Boolean = true, guard: () -> Unit = {}, action: suspend () -> T): T {
        try { return action() }
        catch (failure: ApiFailure) {
            if (failure.code != 20028 || failure.eventId.isNullOrBlank() || !interactive) throw failure
            mutex.withLock {
                guard()
                val sim = runCatching {
                    KugouSigner.generateSimulate(mid = "0", userId = session?.userId ?: "0", dfid = "-")
                }.getOrNull()
                val info = transport.call("/verifyservice/v3/get_verify_info", session, method = "POST", body = json(
                    "eventid" to failure.eventId, "userid" to (session?.userId?.toLong() ?: 0L), "platid" to 2,
                    "rtype" to 1, "wasm" to 1, "i" to "", "sid" to (sim?.sid ?: ""), "edt" to (sim?.edt ?: ""))).requireSuccess().data
                val type = info.number("v_type") ?: error("缺少验证类型")
                if (type != 32 && sim != null) {
                    // 非短信验证码（如 23 滑块 / 93 行为指纹）优先使用模拟 sid/edt 自动静默通过，免除用户手动弹窗验证
                    val silentPassed = suspendRunCatching {
                        val encrypted = KugouSigner.encrypt("{}")
                        transport.call(
                            "/v4/verify_user_info",
                            session,
                            host = "https://verifyservice.kugou.com",
                            method = "POST",
                            query = mapOf("clientver" to "11510"),
                            body = json(
                                "eventid" to failure.eventId,
                                "userid" to (session?.userId?.toLong() ?: 0L),
                                "platid" to 1,
                                "v_type" to 93,
                                "wasm" to 1,
                                "i" to "",
                                "sid" to sim.sid,
                                "edt" to sim.edt,
                                "verifycode" to "",
                                "version" to "1.0.21",
                                "pk" to KugouSigner.rsa(KugouSigner.gson.toJson(json("key" to encrypted.key))).uppercase(),
                                "params" to encrypted.ciphertext
                            )
                        ).requireSuccess()
                        true
                    }.getOrDefault(false)
                    if (silentPassed) {
                        guard()
                        return@withLock
                    }
                }
                if (type != 23 && type != 32) throw ApiFailure(20028, "暂不支持此验证类型（$type），请在官方 App 完成验证后重试")
                val appId = info.text("txappid").orEmpty()
                if (type == 23 && !appId.matches(Regex("[0-9]+"))) error("验证码参数无效")
                guard()
                val deferred = CompletableDeferred<VerificationAnswer?>()
                answer = deferred
                mutable.value = VerificationRequest(UUID.randomUUID().toString(), type, appId,
                    info.text("tips") ?: if (type == 32) "请输入酷狗安全验证短信中的验证码" else "请完成酷狗安全验证")
                val value = try { withTimeoutOrNull(180_000) { deferred.await() } }
                finally { mutable.value = null; answer = null; deferred.cancel() }
                guard()
                if (value == null) throw ApiFailure(20028, "验证已取消或超时，可重新操作")
                val encrypted = KugouSigner.encrypt(if (type == 32) KugouSigner.gson.toJson(json("code" to value.code)) else "{}")
                transport.call("/v4/verify_user_info", session, host = "https://verifyservice.kugou.com", method = "POST",
                    query = mapOf("clientver" to "11510"), body = json("eventid" to failure.eventId,
                        "userid" to (session?.userId?.toLong() ?: 0L), "platid" to 2, "v_type" to type, "wasm" to 1,
                        "i" to "", "sid" to value.sid, "edt" to value.edt,
                        (if (type == 32) "code" else "verifycode") to value.code,
                        "pk" to KugouSigner.rsa(KugouSigner.gson.toJson(json("key" to encrypted.key))),
                        "params" to encrypted.ciphertext)).requireSuccess()
                guard()
            }
            // Exactly one replay after human verification. A second challenge is surfaced to the UI.
            return action()
        }
    }
}
