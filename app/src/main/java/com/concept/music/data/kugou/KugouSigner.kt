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

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
}
