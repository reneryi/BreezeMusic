package com.concept.music.data.kugou

import com.google.gson.JsonObject
import java.time.*
import java.time.format.DateTimeFormatter

internal fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
internal fun JsonObject.number(key: String): Int? = text(key)?.toIntOrNull()
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.objects(key: String): List<JsonObject> = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    ?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()
internal fun json(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
    values.forEach { (key, value) -> when (value) {
        null -> Unit
        is Number -> addProperty(key, value)
        is Boolean -> addProperty(key, value)
        else -> addProperty(key, value.toString())
    } }
}

val CHINA_ZONE: ZoneId = ZoneId.of("Asia/Shanghai")
fun today(clock: Clock = Clock.systemUTC()): String = LocalDate.now(clock.withZone(CHINA_ZONE)).toString()

data class KugouSession(val userId: String, val token: String, val t1: String? = null, val vipToken: String? = null)
data class DeviceIdentity(val guid: String, val mid: String, val dev: String, val mac: String = "02:00:00:00:00:00", val dfid: String = "-")
data class AccountProfile(val userId: String, val nickname: String = "酷狗用户", val avatarUrl: String = "")
data class SavedAccount(val userId: String, val nickname: String = "酷狗用户", val avatarUrl: String = "",
    val autoClaim: Boolean = false, val completedDay: String? = null, val needsLogin: Boolean = false)
data class AccountContext(val session: KugouSession, val generation: Long)
data class KugouQrCode(val key: String, val imageUrl: String, val expiresAt: Long)
data class LoginCandidate(val userId: String, val nickname: String, val avatarUrl: String)
sealed interface LoginResult {
    data class Success(val session: KugouSession, val profile: AccountProfile) : LoginResult
    data class ChooseAccount(val accounts: List<LoginCandidate>) : LoginResult
}
enum class AuthState { GUEST, RESTORING, READY, NEEDS_LOGIN }
data class AccountState(val accounts: List<SavedAccount> = emptyList(), val currentId: String? = null,
    val profile: AccountProfile? = null, val auth: AuthState = AuthState.GUEST, val profileFresh: Boolean = false,
    val message: String? = null) {
    val current: SavedAccount? get() = accounts.find { it.userId == currentId }
}

data class VipBenefit(val type: String, val active: Boolean, val expiresAt: Instant?, val rawExpiry: String?) {
    val label: String get() = if (type == "svip") "概念版权益" else "畅听 VIP"
    fun display(): String = if (!active) "$label：未开通" else "$label：有效 · " +
        (expiresAt?.atZone(CHINA_ZONE)?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) ?: "到期时间未知")
}
data class VipSnapshot(val benefits: List<VipBenefit> = emptyList(), val checked: Boolean = false) {
    val isVip: Boolean get() = benefits.any { it.active }
    fun active(type: String): Boolean = benefits.any { it.type == type && it.active }
    companion object {
        fun parse(data: JsonObject, clock: Clock = Clock.systemUTC()): VipSnapshot {
            check(data.get("busi_vip")?.isJsonArray == true) { "会员响应缺少权益列表，无法确认会员状态" }
            val items = data.objects("busi_vip")
            return VipSnapshot(listOf("tvip", "svip").map { type ->
                val matching = items.filter { it.text("product_type") == type }
                val item = matching.firstOrNull { it.number("is_vip") == 1 } ?: matching.firstOrNull()
                val raw = item?.text("vip_end_time")
                val expiry = parseExpiry(raw)
                VipBenefit(type, item?.number("is_vip") == 1 && (expiry == null || expiry.isAfter(clock.instant())), expiry, raw)
            }, checked = true)
        }
        fun parseExpiry(value: String?): Instant? {
            if (value.isNullOrBlank()) return null
            value.toLongOrNull()?.let { n -> return runCatching {
                if (n > 10_000_000_000L) Instant.ofEpochMilli(n) else Instant.ofEpochSecond(n)
            }.getOrNull() }
            return runCatching { Instant.parse(value) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value.replace(' ', 'T')).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(value.replace(' ', 'T')).atZone(CHINA_ZONE).toInstant() }.getOrNull()
                ?: runCatching { LocalDate.parse(value).plusDays(1).atStartOfDay(CHINA_ZONE).toInstant() }.getOrNull()
        }
    }
}
data class VipDayRecord(val day: String, val received: Boolean, val type: String)
enum class ClaimOutcome { SUCCESS, ALREADY_DONE, UPGRADE_FAILED, PENDING, NEEDS_VERIFICATION, FAILED }
data class VipClaimResult(val outcome: ClaimOutcome, val message: String)
data class VipUiState(val snapshot: VipSnapshot = VipSnapshot(), val records: List<VipDayRecord> = emptyList(),
    val fresh: Boolean = false, val busy: Boolean = false, val result: VipClaimResult? = null, val error: String? = null)

data class ApiResponse(val body: JsonObject, val ssaCode: String? = null, val httpCode: Int = 200) {
    val data: JsonObject get() = body.obj("data") ?: body
    val code: Int? get() = body.number("error_code") ?: body.number("err_code") ?: body.number("errcode")
    val status: Int? get() = body.number("status")
    val success: Boolean get() {
        if (httpCode !in 200..299) return false
        if (code != null && code != 0) return false
        return status == 1
    }
    fun requireSuccess(): ApiResponse {
        if (!success) throw ApiFailure(code, body.text("error_msg") ?: body.text("msg") ?: body.text("message") ?: "酷狗接口未确认成功", ssaCode)
        return this
    }
}
class ApiFailure(val code: Int?, message: String, val eventId: String? = null) : Exception(message) {
    val invalidSession: Boolean get() = code == 20010
    override fun toString(): String = "${message ?: "请求失败"}${code?.let { "（$it）" }.orEmpty()}"
}
