package com.concept.music.data.kugou

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import java.io.IOException
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

class VipRepository(private val transport: KugouTransport, private val accounts: AccountRepository,
    private val verification: VerificationCoordinator, private val clock: Clock = Clock.systemUTC(),
    private val settleDelayMs: Long = 1_000, private val onVipActivated: () -> Unit = {}) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val mutable = MutableStateFlow(VipUiState())
    val state = mutable.asStateFlow()
    fun clear() { mutable.value = VipUiState() }
    private suspend fun snapshot(context: AccountContext): VipSnapshot = accounts.authenticated(context) {
        VipSnapshot.parse(
            transport.call(
                path = "/v1/get_union_vip",
                session = it,
                host = "https://kugouvip.kugou.com",
                query = mapOf(
                    "busi_type" to "concept",
                    "opt_product_types" to "dvip,qvip",
                    "product_type" to "svip"
                )
            ).requireSuccess().data,
            clock
        )
    }
    private suspend fun records(context: AccountContext): List<VipDayRecord> = accounts.authenticated(context) { session ->
        val data = transport.call("/youth/v1/activity/get_month_vip_record", session, query = mapOf("latest_limit" to "100"))
            .requireSuccess().data
        check(data.get("list")?.isJsonArray == true) { "领取响应缺少记录列表，无法判断是否已领取" }
        data.objects("list").map {
                VipDayRecord(it.text("day").orEmpty(), it.number("receive_vip") == 1, it.text("vip_type").orEmpty())
            }
    }
    suspend fun refresh(context: AccountContext) {
        try {
            val vip = snapshot(context)
            accounts.ensureCurrent(context)
            mutable.update { it.copy(snapshot = vip, fresh = true, error = null) }
            if (vip.isVip) onVipActivated()
            val history = records(context)
            accounts.ensureCurrent(context)
            mutable.update { it.copy(records = history) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { accounts.ensureCurrent(context); mutable.update { it.copy(fresh = false, error = "权益尚未完整刷新：${e.message}") } }
    }
    private fun confirmed(day: String, vip: VipSnapshot, records: List<VipDayRecord>): Boolean =
        records.any { it.day == day && it.received && it.type.lowercase() == "svip" } && vip.active("svip")

    private suspend fun reportAdPlayForVip(context: AccountContext) {
        val nowMs = clock.millis()
        accounts.authenticated(context) { session ->
            accounts.ensureCurrent(context)
            transport.call(
                path = "/youth/v1/ad/play_report",
                session = session,
                method = "POST",
                body = json(
                    "ad_id" to 12307537187L,
                    "play_end" to nowMs,
                    "play_start" to (nowMs - 30_000L)
                )
            )
        }
    }

    suspend fun claim(context: AccountContext, automatic: Boolean = false): VipClaimResult {
        val lock = locks.computeIfAbsent(context.session.userId) { Mutex() }
        if (!lock.tryLock()) return VipClaimResult(ClaimOutcome.PENDING, "领取正在进行，请稍候")
        try {
            accounts.ensureCurrent(context)
            mutable.update { it.copy(busy = true, result = null, error = null) }
            val day = today(clock)
            var history = records(context)
            var vip = snapshot(context)
            if (confirmed(day, vip, history)) {
                accounts.completed(context, day)
                mutable.update { it.copy(snapshot = vip, records = history, fresh = true) }
                onVipActivated()
                return publish(context, ClaimOutcome.ALREADY_DONE, "今日已完成，概念版权益有效")
            }
            var received = history.any { it.day == day && it.received }
            if (!received) {
                val claim = try {
                    mutate(
                        context = context,
                        automatic = automatic,
                        path = "/youth/v1/recharge/receive_vip_listen_song",
                        query = mapOf("source_id" to "90139", "receive_day" to day),
                        alreadyCodes = setOf(131001)
                    )
                } catch (e: ApiFailure) {
                    if (e.code == 20028 || e.invalidSession) throw e
                    // 备选激励广告通道 (/youth/v1/ad/play_report)，使用 suspendRunCatching 确保不吞没 CancellationException
                    suspendRunCatching { reportAdPlayForVip(context) }
                    mutate(
                        context = context,
                        automatic = automatic,
                        path = "/youth/v1/recharge/receive_vip_listen_song",
                        query = mapOf("source_id" to "90139", "receive_day" to day),
                        alreadyCodes = setOf(131001)
                    )
                }
                received = claim.success || claim.code == 131001
            }
            var upgradeFailure: ApiFailure? = null
            if (received) {
                try { mutate(context, automatic, "/youth/v1/listen_song/upgrade_vip_reward",
                    mapOf("kugouid" to context.session.userId, "ad_type" to "1"), setOf(20030, 131001)) }
                catch (e: ApiFailure) {
                    if (e.code == 20028 || e.invalidSession) throw e
                    upgradeFailure = e
                }
            }
            repeat(3) { attempt ->
                if (attempt > 0) delay(settleDelayMs)
                history = records(context)
                vip = snapshot(context)
                accounts.ensureCurrent(context)
                mutable.update { it.copy(snapshot = vip, records = history, fresh = true) }
                if (confirmed(day, vip, history)) {
                    accounts.completed(context, day)
                    onVipActivated()
                    return publish(context, ClaimOutcome.SUCCESS, "领取并升级成功，已核实今日记录与概念版权益")
                }
            }
            if (vip.isVip) onVipActivated()
            return if (upgradeFailure != null) publish(context, ClaimOutcome.UPGRADE_FAILED, "已领取畅听权益，但升级失败：${upgradeFailure.message}")
            else publish(context, ClaimOutcome.PENDING, "请求已处理，权益尚未同步确认，请稍后刷新")
        } catch (e: CancellationException) { throw e }
        catch (e: IOException) {
            refresh(context)
            val day = today(clock)
            if (state.value.fresh && confirmed(day, state.value.snapshot, state.value.records)) {
                accounts.completed(context, day)
                onVipActivated()
                return publish(context, ClaimOutcome.SUCCESS, "已通过服务端记录确认今日权益")
            }
            return publish(context, ClaimOutcome.PENDING, "网络中断，领取结果待确认；请先刷新状态")
        } catch (e: ApiFailure) {
            refresh(context)
            return if (e.code == 20028) publish(context, ClaimOutcome.NEEDS_VERIFICATION,
                if (automatic) "自动领取需要安全验证，请点击领取并完成验证" else "领取需要验证：${e.message}")
            else publish(context, ClaimOutcome.FAILED, "领取失败：$e")
        } catch (e: Exception) {
            return publish(context, ClaimOutcome.FAILED, "领取失败：${e.message}")
        } finally {
            if (accounts.isCurrent(context)) mutable.update { it.copy(busy = false) }
            lock.unlock()
        }
    }
    private suspend fun mutate(context: AccountContext, automatic: Boolean, path: String,
        query: Map<String, String>, alreadyCodes: Set<Int>): ApiResponse = accounts.authenticated(context) { session ->
        verification.execute(session, interactive = !automatic, guard = { accounts.ensureCurrent(context) }) {
            accounts.ensureCurrent(context)
            transport.call(path, session, query = query, method = "POST",
                headers = if (path.endsWith("receive_vip_listen_song")) mapOf("content-type" to "application/x-www-form-urlencoded") else emptyMap())
                .also { if (!it.success && it.code !in alreadyCodes) it.requireSuccess() }
        }
    }
    private fun publish(context: AccountContext, outcome: ClaimOutcome, message: String): VipClaimResult {
        accounts.ensureCurrent(context)
        return VipClaimResult(outcome, message).also { res -> mutable.update { it.copy(result = res) } }
    }
}
