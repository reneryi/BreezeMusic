package com.concept.music.data.kugou

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface SessionStore {
    fun accounts(): List<SavedAccount>
    fun saveAccounts(accounts: List<SavedAccount>)
    fun activeId(): String?
    fun setActive(id: String?)
    fun session(id: String): KugouSession?
    fun saveSession(session: KugouSession)
    fun removeSession(id: String)
    fun device(): DeviceIdentity
    fun saveDfid(dfid: String)
}

class EncryptedSessionStore(context: Context) : SessionStore {
    private val metadata = context.getSharedPreferences("kugou_accounts_v2", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("kugou_secrets_v2", Context.MODE_PRIVATE)
    private val gson = KugouSigner.gson
    private val key: SecretKey by lazy {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("concept_music_accounts", null) as? SecretKey) ?: KeyGenerator.getInstance("AES", "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder("concept_music_accounts", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    init {
        if (!metadata.contains("guid")) {
            val guid = KugouSigner.md5(UUID.randomUUID().toString())
            check(metadata.edit().putString("guid", guid).putString("dev", KugouSigner.random(10).uppercase()).commit())
        }
        val old = context.getSharedPreferences("kugou_session", Context.MODE_PRIVATE)
        val id = old.getString("userId", null)
        val token = old.getString("token", null)
        if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
            // Retain the legacy copy if any durable write fails. Migration is idempotent.
            if (session(id) == null) saveSession(KugouSession(id, token, old.getString("t1", null)))
            if (accounts().none { it.userId == id }) saveAccounts(accounts() + SavedAccount(id))
            if (activeId() == null) setActive(id)
            check(old.edit().clear().commit())
        }
    }
    override fun accounts(): List<SavedAccount> = runCatching {
        gson.fromJson<List<SavedAccount>>(metadata.getString("accounts", "[]"), object : TypeToken<List<SavedAccount>>() {}.type)
    }.getOrDefault(emptyList())
    override fun saveAccounts(accounts: List<SavedAccount>) { check(metadata.edit().putString("accounts", gson.toJson(accounts)).commit()) }
    override fun activeId(): String? = metadata.getString("active", null)
    override fun setActive(id: String?) { check(metadata.edit().putString("active", id).commit()) }
    override fun session(id: String): KugouSession? = runCatching {
        val encoded = secrets.getString(id, null) ?: return null
        val parts = encoded.split(":")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
        cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
        gson.fromJson(cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8), KugouSession::class.java)
            ?.takeIf { it.userId == id && it.token.isNotBlank() }
    }.getOrNull()
    override fun saveSession(session: KugouSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(session.userId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
        val value = Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(encrypted)
        check(secrets.edit().putString(session.userId, value).commit()) { "无法保存登录凭证" }
    }
    override fun removeSession(id: String) { check(secrets.edit().remove(id).commit()) }
    override fun device(): DeviceIdentity {
        val guid = metadata.getString("guid", null)!!
        return DeviceIdentity(guid, KugouSigner.mid(guid), metadata.getString("dev", null)!!,
            dfid = metadata.getString("dfid", "-") ?: "-")
    }
    override fun saveDfid(dfid: String) { if (dfid.isNotBlank() && dfid != "-") metadata.edit().putString("dfid", dfid).apply() }
}
