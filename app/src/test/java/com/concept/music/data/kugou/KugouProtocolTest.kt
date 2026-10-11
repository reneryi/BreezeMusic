package com.concept.music.data.kugou

import com.google.gson.JsonObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.Clock
import java.time.ZoneOffset

class KugouProtocolTest {
    @Test fun md5MidAndSignatureMatchUpstreamFixture() {
        assertEquals("104602365767877882724869659726014956567", KugouSigner.mid("fixture-guid"))
        val params = mapOf("mid" to "1234", "appid" to "3116", "clienttime" to "1770000000", "token" to "fixture-token", "userid" to "123")
        assertEquals("c2f83c3eeb8c6d72bfcdeedb0511c87e", KugouSigner.signature(params, "{\"mobile\":\"13800000000\",\"code\":\"123456\"}"))
        assertEquals("358d8457b5451893c970670a977256c4", KugouSigner.paramsKey(1770000000123L))
    }

    @Test fun fixedAesAndRawRsaAreDeterministic() {
        assertEquals("8270cd5eba410362918595d0cc6188dd", KugouSigner.aes("|1770000000123", "5e4ef500e9597fe004bd09a46d8add98", "04bd09a46d8add98"))
        assertEquals("73143dd1ae3d044b1eb18cb4178c7ead1c10607262e8afe75d954c2b4a5eb365b1913e1ab60fc516ad3154114c6eefe9b47a7b6895fb0f67ce7422b62f75255a574be399ac6cc2f39e5fb9a543c55523be17e52f4aecee6f8a497568d65f61559a204967195ab22dca1c6d27d85e2f7b0ae9262bb51808e2ce0d9190e5d5c998", KugouSigner.rsa("{\"clienttime_ms\":1770000000123,\"key\":\"0123456789abcdef\"}"))
    }

    @Test fun vipParsesTypesAndDateRepresentations() {
        val root = JsonObject().apply { add("busi_vip", com.google.gson.JsonArray().apply {
            add(JsonObject().apply { addProperty("product_type", "tvip"); addProperty("is_vip", 1); addProperty("vip_end_time", "2026-12-01 10:00:00") })
            add(JsonObject().apply { addProperty("product_type", "svip"); addProperty("is_vip", 0); addProperty("vip_end_time", "0") })
        }) }
        val snapshot = VipSnapshot.parse(root, java.time.Clock.fixed(Instant.parse("2026-10-04T00:00:00Z"), java.time.ZoneOffset.UTC))
        assertTrue(snapshot.active("tvip")); assertFalse(snapshot.active("svip"))
        assertEquals(Instant.parse("2026-12-01T02:00:00Z"), snapshot.benefits.first().expiresAt)
    }

    @Test fun responseDoesNotTreatMissingStatusAsSuccess() {
        val response = ApiResponse(JsonObject().apply { add("data", JsonObject()) })
        assertFalse(response.success)
        try { response.requireSuccess(); fail("missing status must fail") } catch (expected: ApiFailure) { assertNull(expected.code) }
    }
}
