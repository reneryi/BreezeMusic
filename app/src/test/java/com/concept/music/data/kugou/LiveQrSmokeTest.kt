package com.concept.music.data.kugou

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Explicit opt-in: creates an unauthenticated QR only. Never sends SMS or claims benefits. */
class LiveQrSmokeTest {
    @Test fun createAndPollWithoutCredentials() = runBlocking {
        assumeTrue(System.getenv("KUGOU_LIVE_SMOKE") == "1")
        val store = MemoryStore()
        val guid = KugouSigner.md5(UUID.randomUUID().toString())
        val identity = DeviceIdentity(guid, KugouSigner.mid(guid), KugouSigner.random(10).uppercase())
        val direct = DirectKugouTransport({ identity })
        val auth = AuthRepository(direct, object : SessionStore by store { override fun device() = identity })
        val qr = auth.createQr()
        assertTrue(qr.key.isNotBlank())
        assertTrue(qr.imageUrl.startsWith("data:image/"))
        val status = auth.checkQr(qr.key).data.number("status")
        assertTrue("Expected unauthenticated QR waiting state, got $status", status == 1)
    }
}
