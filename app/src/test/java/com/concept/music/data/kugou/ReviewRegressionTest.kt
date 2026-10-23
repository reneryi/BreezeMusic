package com.concept.music.data.kugou

import com.concept.music.ui.viewmodel.LibraryViewModel
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

internal class ScriptedTransport(val action: suspend (String, KugouSession?, JsonObject?) -> ApiResponse) : KugouTransport {
    override suspend fun call(path: String, session: KugouSession?, query: Map<String, String>, body: JsonObject?, host: String,
        method: String, web: Boolean, headers: Map<String, String>, device: DeviceIdentity?) = action(path, session, body)
}
internal fun response(text: String) = ApiResponse(JsonParser.parseString(text).asJsonObject)

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewRegressionTest {
    @Test fun switchDuringSlowRefreshStartsNewAccountAndDropsOldResponse() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val first = CompletableDeferred<Unit>()
        val transport = ScriptedTransport { path, session, _ ->
            when {
                path.endsWith("login_by_token") -> {
                    if (session!!.userId == "123") first.await()
                    response("""{"status":1,"data":{"userid":"${session.userId}","token":"new-${session.userId}"}}""")
                }
                path.endsWith("get_my_info") -> response("""{"status":1,"data":{"userid":"${session!!.userId}","nickname":"user-${session.userId}"}}""")
                path.endsWith("get_union_vip") -> response("""{"status":1,"data":{"busi_vip":[]}}""")
                else -> response("""{"status":1,"data":{"list":[]}}""")
            }
        }
        try {
            val store = MemoryStore(); val auth = AuthRepository(transport, store)
            val accounts = AccountRepository(store, auth, transport)
            val verification = VerificationCoordinator(transport)
            val vip = VipRepository(transport, accounts, verification)
            val vm = LibraryViewModel(accounts, auth, vip, verification)
            vm.onForeground(true); runCurrent()
            vm.selectAccount("456"); advanceUntilIdle()
            assertEquals("user-456", accounts.state.value.profile!!.nickname)
            assertTrue(accounts.state.value.profileFresh)
            first.complete(Unit); advanceUntilIdle()
            assertEquals("456", accounts.state.value.currentId)
            vm.onForeground(false)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun malformedVipIsUnknownInsteadOfNotVip() {
        assertThrows(IllegalStateException::class.java) { VipSnapshot.parse(JsonObject()) }
    }

    @Test fun nonVipWithNullBusiVipParsesCleanly() {
        val nonVipData = JsonObject().apply {
            addProperty("is_vip", 0)
            addProperty("vip_type", 0)
            add("busi_vip", JsonNull.INSTANCE)
        }
        val snapshot = VipSnapshot.parse(nonVipData)
        assertTrue(snapshot.checked)
        assertFalse(snapshot.isVip)
        assertFalse(snapshot.active("svip"))
        assertFalse(snapshot.active("tvip"))
    }

    @Test fun malformedHistoryNeverTriggersClaim() = runTest {
        val calls = mutableListOf<String>()
        val transport = ScriptedTransport { path, _, _ -> calls += path; response("""{"status":1,"data":{}}""") }
        val store = MemoryStore(); val accounts = AccountRepository(store, AuthRepository(transport, store), transport)
        val vip = VipRepository(transport, accounts, VerificationCoordinator(transport))
        assertEquals(ClaimOutcome.FAILED, vip.claim(accounts.context()!!).outcome)
        assertFalse(calls.any { it.contains("receive_vip_listen_song") })
    }

    @Test fun transportSignsExactlySentBodyAndKeepsBusinessErrors() = runTest {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(403).setHeader("ssa-code", "test-event")
            .setBody("""{"status":0,"error_code":20028,"error_msg":"验证"}"""))
        server.start()
        try {
            val client = DirectKugouTransport({ DeviceIdentity("g", "1234", "d") })
            val body = json("label" to "中文&<test>")
            val result = client.call("/test", KugouSession("123", "fixture"), body = body, method = "POST", host = server.url("/").toString())
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            val url = request.requestUrl!!
            val params = url.queryParameterNames.filter { it != "signature" }.associateWith { url.queryParameter(it)!! }
            val bytes = request.body.readUtf8()
            assertEquals(KugouSigner.gson.toJson(body), bytes)
            assertEquals(KugouSigner.signature(params, bytes), url.queryParameter("signature"))
            assertEquals(20028, result.code)
            assertEquals("test-event", result.ssaCode)
            assertFalse(result.success)
        } finally { server.shutdown() }
    }

    @Test fun verificationReplaysOnlyOnceAndPreservesSmsType() = runTest {
        val paths = mutableListOf<String>(); var count = 0
        val transport = ScriptedTransport { path, _, body ->
            paths += path
            if (path.endsWith("get_verify_info")) response("""{"status":1,"data":{"v_type":32}}""")
            else {
                assertEquals("123456", body!!.text("code")); assertEquals(32, body.number("v_type"))
                response("""{"status":1}""")
            }
        }
        val verification = VerificationCoordinator(transport)
        val job = async { runCatching { verification.execute(KugouSession("123", "fixture")) {
            count++; throw ApiFailure(20028, "验证", "event")
        } } }
        runCurrent()
        val id = verification.pending.value!!.id
        verification.complete("wrong-id", VerificationAnswer("123456", "sid", "edt"))
        assertFalse(job.isCompleted)
        verification.complete(id, VerificationAnswer("123456", "sid", "edt"))
        advanceUntilIdle()
        assertTrue(job.await().exceptionOrNull() is ApiFailure)
        assertEquals(2, count)
        assertEquals(2, paths.size)
        assertNull(verification.pending.value)
    }

    @Test fun qrRejectsInvalidAccountIdentity() = runTest {
        val store = MemoryStore()
        // QR carries a plain token; numeric identity validation is exercised without server access.
        val auth = AuthRepository(ScriptedTransport { _, _, _ -> error("no request") }, store)
        assertEquals("abc", auth.qrSession(json("userid" to "123", "token" to "abc")).session.token)
        assertThrows(IllegalStateException::class.java) { auth.qrSession(json("userid" to "bad", "token" to "abc")) }
    }

    @Test fun chinaMidnightAndEpochUnitsAgree() {
        assertEquals("2026-10-05", today(Clock.fixed(Instant.parse("2026-10-04T16:00:00Z"), ZoneOffset.UTC)))
        assertEquals(VipSnapshot.parseExpiry("1800000000"), VipSnapshot.parseExpiry("1800000000000"))
        assertNull(VipSnapshot.parseExpiry("invalid"))
    }
}
