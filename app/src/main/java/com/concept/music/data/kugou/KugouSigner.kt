package com.concept.music.data.kugou

import com.google.gson.GsonBuilder
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Protocol port of MakcRe/KuGouMusicApi (MIT), da5ccfd. See THIRD_PARTY_NOTICES. */
object KugouSigner {
    const val APP_ID = 3116
    const val CLIENT_VERSION = 11440
    private const val ANDROID_SALT = "LnT6xpN3khm36zse0QzvmgTZ3waWdRSA"
    private const val WEB_SALT = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
    private const val PUBLIC_KEY = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDECi0Np2UR87scwrvTr72L6oO01rBbbBPriSDFPxr3Z5syug0O24QyQO8bg27+0+4kBzTBTBOZ/WWU0WryL1JSXRTXLgFVxtzIY41Pe7lPOgsfTCn5kZcvKhYKJesKnnJDNr5/abvTGf+rHG3YRwsCHcQ08/q6ifSioBszvb3QiwIDAQAB"
    private const val PUBLIC_KEY_STANDARD = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"
    private const val PUBLIC_KEY_SIMULATE_OAEP = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAoW2+Ylo8ALePSQTP0xBFlFmEOHvBD9tS+s7DBlfKEu3RzzvZTaX1JtYbX4+AVUqj6ARz8IM+CKByqGFvbHN/W64XxNI+q7z36ajCL3VTJ2W5G9MCJitc6oGbire4NQfhaEq0nC+hxBWQvCbIFflA2ItrLUbSU7z1bHA/a+jlQm4OWvY+IKnTryOJTPuT1yNOVjbJ8wBLKy2DgQr9pPqWPmEQtGpR5IM9V8Kao6PaSdKYOWGbX3i2+RzIKhvZUxxtJwdVbqPlDPlW9h4/xIBc56Lgvr4aIl8nFtwbj4UJVUTFuGrs0tY9H/tXvZ22dUCKuGxW/gW7ZF+gXz6vHtYarQIDAQAB"
    private const val SIMULATE_AES_IV = "kugousecurity123"
    const val STANDARD_SALT = "OIlwieks28dk2k092lksi2UIkp"
    val gson = GsonBuilder()
        .disableHtmlEscaping()
        .registerTypeAdapterFactory(object : com.google.gson.TypeAdapterFactory {
            override fun <T : Any?> create(gson: com.google.gson.Gson, type: com.google.gson.reflect.TypeToken<T>): com.google.gson.TypeAdapter<T>? {
                val rawType = type.rawType
                val isSong = rawType == com.concept.music.data.model.Song::class.java
                val isPlaylist = rawType == com.concept.music.data.model.Playlist::class.java
                if (!isSong && !isPlaylist) return null
                val delegate = gson.getDelegateAdapter(this, type)
                return object : com.google.gson.TypeAdapter<T>() {
                    override fun write(out: com.google.gson.stream.JsonWriter, value: T?) {
                        delegate.write(out, value)
                    }

                    @Suppress("UNCHECKED_CAST")
                    override fun read(reader: com.google.gson.stream.JsonReader): T? {
                        val parsed = delegate.read(reader) ?: return null
                        return when (parsed) {
                            is com.concept.music.data.model.Song -> parsed.sanitizeFromJson() as T
                            is com.concept.music.data.model.Playlist -> parsed.sanitizeFromJson() as T
                            else -> parsed
                        }
                    }
                }
            }
        })
        .create()
    fun md5(text: String): String = hex(MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)))
    fun mid(guid: String): String = BigInteger(md5(guid), 16).toString()
    fun random(length: Int): String {
        val alphabet = "0123456789abcdefghijklmnopqrstuvwxyz"
        val random = SecureRandom()
        return buildString { repeat(length) { append(alphabet[random.nextInt(alphabet.length)]) } }
    }
    fun signature(params: Map<String, String>, body: String = "", web: Boolean = false): String {
        val salt = if (web) WEB_SALT else ANDROID_SALT
        val ordered = if (web) params.map { "${it.key}=${it.value}" }.sorted().joinToString("")
            else params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        return md5(salt + ordered + body + salt)
    }
    fun paramsKey(time: Long, extra: String = ""): String = md5("$APP_ID$ANDROID_SALT$CLIENT_VERSION$time$extra")
    fun paramsKeyStandard(time: Long, appId: Int = 1005, clientVer: Int = 20489, extra: String = ""): String =
        md5("$appId$STANDARD_SALT$clientVer$time$extra")
    fun signatureStandard(params: Map<String, String>, body: String = ""): String {
        val ordered = params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" }
        return md5(STANDARD_SALT + ordered + body + STANDARD_SALT)
    }
    data class Encrypted(val ciphertext: String, val key: String)
    fun encrypt(text: String, temporaryKey: String = random(16)): Encrypted {
        val key = md5(temporaryKey)
        return Encrypted(aes(text, key, key.takeLast(16)), temporaryKey)
    }
    fun aes(text: String, key: String, iv: String): String {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        return hex(cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
    fun decrypt(hex: String, temporaryKey: String): String {
        val key = md5(temporaryKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(key.takeLast(16).toByteArray(Charsets.UTF_8)))
        return cipher.doFinal(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).toString(Charsets.UTF_8)
    }
    fun rsa(text: String, useStandardKey: Boolean = false): String {
        val pubKeyB64 = if (useStandardKey) PUBLIC_KEY_STANDARD else PUBLIC_KEY
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyB64))) as RSAPublicKey
        val size = (key.modulus.bitLength() + 7) / 8
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= size) { "登录加密数据长度超限" }
        // Upstream pads on the RIGHT and uses raw modular exponentiation, not PKCS#1.
        return BigInteger(1, bytes.copyOf(size)).modPow(key.publicExponent, key.modulus).toString(16).padStart(size * 2, '0')
    }
    fun rsaPkcs1(payload: String, lite: Boolean = true): String {
        val pubKeyB64 = if (lite) PUBLIC_KEY else PUBLIC_KEY_STANDARD
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyB64)))
        val cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return hex(cipher.doFinal(payload.toByteArray(Charsets.UTF_8)))
    }
    fun playlistAesEncrypt(data: String): Pair<String, String> {
        val key = (1..6).map { ('a'..'z').random() }.joinToString("")
        val md = md5(key)
        val encKey = md.substring(0, 16)
        val iv = md.substring(16, 32)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(encKey.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        val encrypted = cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        return Pair(key, Base64.getEncoder().encodeToString(encrypted))
    }

    fun playlistAesDecrypt(key: String, base64Cipher: String): String {
        return playlistAesDecrypt(Base64.getDecoder().decode(base64Cipher), key)
    }

    fun playlistAesDecrypt(cipherBytes: ByteArray, key: String): String {
        val md = md5(key)
        val encKey = md.substring(0, 16)
        val iv = md.substring(16, 32)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey.toByteArray(Charsets.UTF_8), "AES"), IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        val decrypted = cipher.doFinal(cipherBytes)
        return String(decrypted, Charsets.UTF_8)
    }

    data class SimulatedSidEdt(val sid: String, val edt: String)

    /**
     * 完整移植自 MakcRe/KuGouMusicApi util/generate_simulate.js:
     * 生成模拟的行为指纹数据 EDT (AES-128-CBC, IV="kugousecurity123") 与 SID (RSA-OAEP SHA-256)。
     */
    fun generateSimulate(
        mid: String,
        userId: String,
        dfid: String = "-",
        webglHash: String = md5("kugou_webgl_${mid}_$userId")
    ): SimulatedSidEdt {
        val rnd = SecureRandom()
        fun ri(min: Int, max: Int): Int = min + rnd.nextInt((max - min + 1).coerceAtLeast(1))
        val sentinel = 0xFFFFFFFFL - rnd.nextInt(20).toLong()
        val key16 = md5(random(16)).substring(0, 16)

        val points = ri(30, 60)
        val sx = ri(200, 600).toDouble()
        val sy = ri(200, 500).toDouble()
        val ex = ri(500, 700).toDouble()
        val ey = ri(80, 150).toDouble()
        val c1x = sx + (ex - sx) * 0.3 + ri(-80, 80)
        val c1y = sy + (ey - sy) * 0.2 + ri(-60, 60)
        val c2x = sx + (ex - sx) * 0.7 + ri(-60, 60)
        val c2y = sy + (ey - sy) * 0.8 + ri(-40, 40)

        val entries = mutableListOf<String>()
        var ts = 0
        var ei = 0
        entries.add("5,0,0")
        entries.add("5,$sentinel,0")
        entries.add("5,0,0")
        entries.add("5,$sentinel,0")

        ts += ri(5, 20)
        entries.add("6,$ts,$ei,750,500")
        entries.add("6,$sentinel,$ei,750,500")
        ei++

        repeat(3) {
            ts += ri(80, 600)
            entries.add("5,$ts,$ei")
            entries.add("5,$sentinel,$ei")
            ei++
        }

        var si = 0
        for (i in 0..points) {
            val t = i.toDouble() / points.toDouble()
            val u = 1.0 - t
            val bx = u * u * u * sx + 3 * u * u * t * c1x + 3 * u * t * t * c2x + t * t * t * ex
            val by = u * u * u * sy + 3 * u * u * t * c1y + 3 * u * t * t * c2y + t * t * t * ey
            val jitter = maxOf(0.5, 3.0 - t * 2.5)
            val px = kotlin.math.round(bx + (rnd.nextDouble() - 0.5) * jitter).toInt()
            val py = kotlin.math.round(by + (rnd.nextDouble() - 0.5) * jitter).toInt()
            ts += ri(8, 50)
            entries.add("3,$ts,$si,$px,$py")
            entries.add("3,$sentinel,$si,$px,$py")
            if (i > 0 && i % 12 == 0) {
                ts += ri(20, 60)
                entries.add("5,$ts,$ei")
                entries.add("5,$sentinel,$ei")
                ei++
            }
            si = (si + 1) % 2
        }

        ts += ri(5, 30)
        val endXInt = kotlin.math.round(ex).toInt()
        val endYInt = kotlin.math.round(ey).toInt()
        entries.add("3,$ts,1,${endXInt + ri(-5, 5)},${endYInt + ri(-5, 5)}")
        entries.add("3,$sentinel,1,$endXInt,$endYInt")

        val safeMid = mid.ifBlank { "0" }
        val safeUid = userId.ifBlank { "0" }
        val safeDfid = if (dfid.isBlank() || dfid == "-") "0" else dfid
        val nowMs = System.currentTimeMillis()
        val sidPlaintext = "mid=$safeMid;userid=$safeUid;dfid=$safeDfid;webgl=$webglHash;webdriver=0;ts=$nowMs;data=${entries.joinToString(":")}"

        val aesCipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        aesCipher.init(
            Cipher.ENCRYPT_MODE,
            SecretKeySpec(key16.toByteArray(Charsets.UTF_8), "AES"),
            IvParameterSpec(SIMULATE_AES_IV.toByteArray(Charsets.UTF_8))
        )
        val edtBase64 = Base64.getEncoder().encodeToString(aesCipher.doFinal(sidPlaintext.toByteArray(Charsets.UTF_8)))

        val pubKey = KeyFactory.getInstance("RSA")
            .generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY_SIMULATE_OAEP)))
        val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        val oaepSpec = javax.crypto.spec.OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            java.security.spec.MGF1ParameterSpec.SHA256,
            javax.crypto.spec.PSource.PSpecified.DEFAULT
        )
        rsaCipher.init(Cipher.ENCRYPT_MODE, pubKey, oaepSpec)
        val sidBase64 = Base64.getEncoder().encodeToString(rsaCipher.doFinal(key16.toByteArray(Charsets.UTF_8)))

        return SimulatedSidEdt(sid = sidBase64, edt = edtBase64)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
