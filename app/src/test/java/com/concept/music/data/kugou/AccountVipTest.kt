package com.concept.music.data.kugou

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

internal class MemoryStore : SessionStore {
    var rows = listOf(SavedAccount("123"), SavedAccount("456"))
    var activeKey: String? = "123"
    val sessions = mutableMapOf("123" to KugouSession("123", "a"), "456" to KugouSession("456", "b"))
    override fun accounts() = rows
    override fun saveAccounts(accounts: List<SavedAccount>) { rows = accounts }
    override fun activeId() = activeKey
    override fun setActive(id: String?) { activeKey = id }
    override fun session(id: String) = sessions[id]
    override fun saveSession(session: KugouSession) { sessions[session.userId] = session }
    override fun removeSession(id: String) { sessions.remove(id) }
    override fun device() = DeviceIdentity("fixture", "1", "device")
    override fun saveDfid(dfid: String) = Unit
}
private class FakeTransport : KugouTransport {
    var upgraded = false
    var upgradeFails = false
    var challenge = false
    var failFirstClaimUntilAdReport = false
    var adReported = false
    val paths = mutableListOf<String>()
    val unionVipQueries = mutableListOf<Map<String, String>>()
    override suspend fun call(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?,
        host: String, method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?): ApiResponse {
        paths += path
        val payload = when {
            path.endsWith("get_month_vip_record") -> {
                val hasRec = !failFirstClaimUntilAdReport || adReported
                """{"status":1,"data":{"list":${if (hasRec) """[{"day":"2026-10-04","receive_vip":1,"vip_type":"${if(upgraded) "svip" else "tvip"}"}]""" else "[]"}}}"""
            }
            path.endsWith("get_union_vip") -> {
                unionVipQueries += query
                """{"status":1,"data":{"busi_vip":[{"product_type":"svip","is_vip":${if(upgraded) 1 else 0},"vip_end_time":"2026-10-05 23:00:00"}]}}"""
            }
            path.endsWith("receive_vip_listen_song") -> {
                if (failFirstClaimUntilAdReport && !adReported) {
                    """{"status":0,"error_code":130001,"error_msg":"请先完成激励任务"}"""
                } else {
                    """{"status":1,"error_code":0}"""
                }
            }
            path.endsWith("ad/play_report") -> {
                adReported = true
                """{"status":1,"error_code":0}"""
            }
            path.endsWith("upgrade_vip_reward") -> {
                if (challenge) return ApiResponse(json("status" to 0, "error_code" to 20028), "event")
                if (upgradeFails) """{"status":0,"error_code":999,"error_msg":"升级被拒绝"}"""
                else { upgraded = true; """{"status":1,"error_code":0}""" }
            }
            else -> error("Unexpected request $path")
        }
        return ApiResponse(JsonParser.parseString(payload).asJsonObject)
    }
}
class AccountVipTest {
    private val clock = Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), ZoneOffset.UTC)
    @Test fun alreadyClaimedListeningVipStillUpgradesAndNeverReportsListenSong() = runTest {
        val store = MemoryStore(); val transport = FakeTransport()
        val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        var activatedCount = 0
        val vip = VipRepository(transport, accounts, VerificationCoordinator(transport), clock, 0, onVipActivated = { activatedCount++ })
        assertEquals(ClaimOutcome.SUCCESS, vip.claim(accounts.context()!!).outcome)
        assertTrue(transport.paths.any { it.endsWith("upgrade_vip_reward") })
        assertFalse(transport.paths.any { it.endsWith("receive_vip_listen_song") })
        assertFalse("严禁调用 listen_song 污染用户听歌时长", transport.paths.any { it.contains("report/listen_song") })
        assertEquals("concept", transport.unionVipQueries.first()["busi_type"])
        assertEquals("dvip,qvip", transport.unionVipQueries.first()["opt_product_types"])
        assertEquals("svip", transport.unionVipQueries.first()["product_type"])
        assertTrue(activatedCount > 0)
        assertEquals("2026-10-04", store.rows.first().completedDay)
        assertEquals(ClaimOutcome.ALREADY_DONE, vip.claim(accounts.context()!!).outcome)
    }
    @Test fun claimRetriesWithAdPlayReportWithoutListenSongWhenInitialClaimFails() = runTest {
        val store = MemoryStore()
        val transport = FakeTransport().apply { failFirstClaimUntilAdReport = true }
        val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        val vip = VipRepository(transport, accounts, VerificationCoordinator(transport), clock, 0)
        assertEquals(ClaimOutcome.SUCCESS, vip.claim(accounts.context()!!).outcome)
        assertTrue(transport.adReported)
        assertFalse(transport.paths.any { it.contains("report/listen_song") })
    }
    @Test fun upgradeFailureCannotMarkDayCompleted() = runTest {
        val store = MemoryStore(); val transport = FakeTransport().apply { upgradeFails = true }
        val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        val vip = VipRepository(transport, accounts, VerificationCoordinator(transport), clock, 0)
        assertEquals(ClaimOutcome.UPGRADE_FAILED, vip.claim(accounts.context()!!).outcome)
        assertNull(store.rows.first().completedDay)
        assertFalse(vip.state.value.busy)
    }
    @Test fun autoChallengeReturnsActionWithoutOpeningDialog() = runTest {
        val store = MemoryStore(); val transport = FakeTransport().apply { challenge = true }
        val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        val verification = VerificationCoordinator(transport)
        val vip = VipRepository(transport, accounts, verification, clock, 0)
        assertEquals(ClaimOutcome.NEEDS_VERIFICATION, vip.claim(accounts.context()!!, true).outcome)
        assertNull(verification.pending.value)
    }
    @Test fun switchedAccountRejectsOldContextAndPreferencesStayIsolated() {
        val store = MemoryStore(); val transport = FakeTransport()
        val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        val old = accounts.context()!!
        accounts.setAuto(true); accounts.select("456")
        assertFalse(accounts.state.value.current!!.autoClaim)
        try { accounts.ensureCurrent(old); fail("stale context") } catch (_: CancellationException) { }
        accounts.remove("456")
        assertNull(accounts.state.value.currentId)
        assertTrue(store.rows.single().autoClaim)
        assertNotNull(store.session("123"))
    }
}
