package com.concept.music.data.kugou

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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

    @Volatile
    private var cachedAccounts: List<SavedAccount>? = null

    @Volatile
    private var cachedActiveId: String? = null
    private var activeIdLoaded: Boolean = false

    @Volatile
    private var cachedDevice: DeviceIdentity? = null

    private val sessionCache = ConcurrentHashMap<String, KugouSession>()

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
            val dev = KugouSigner.random(10).uppercase()
            metadata.edit().putString("guid", guid).putString("dev", dev).apply()
            cachedDevice = DeviceIdentity(guid, KugouSigner.mid(guid), dev, dfid = "-")
        }
        val old = context.getSharedPreferences("kugou_session", Context.MODE_PRIVATE)
        val id = old.getString("userId", null)
        val token = old.getString("token", null)
        if (!id.isNullOrBlank() && !token.isNullOrBlank()) {
            if (session(id) == null) saveSession(KugouSession(id, token, old.getString("t1", null)))
            if (accounts().none { it.userId == id }) saveAccounts(accounts() + SavedAccount(id))
            if (activeId() == null) setActive(id)
            old.edit().clear().apply()
        }
    }

    override fun accounts(): List<SavedAccount> {
        cachedAccounts?.let { return it }
        val loaded = runCatching {
            gson.fromJson<List<SavedAccount>>(metadata.getString("accounts", "[]"), object : TypeToken<List<SavedAccount>>() {}.type)
        }.getOrDefault(emptyList())
        cachedAccounts = loaded
        return loaded
    }

    override fun saveAccounts(accounts: List<SavedAccount>) {
        cachedAccounts = accounts
        metadata.edit().putString("accounts", gson.toJson(accounts)).apply()
    }

    override fun activeId(): String? {
        if (activeIdLoaded) return cachedActiveId
        val id = metadata.getString("active", null)
        cachedActiveId = id
        activeIdLoaded = true
        return id
    }

    override fun setActive(id: String?) {
        cachedActiveId = id
        activeIdLoaded = true
        metadata.edit().putString("active", id).apply()
    }

    override fun session(id: String): KugouSession? {
        sessionCache[id]?.let { return it }
        return runCatching {
            val encoded = secrets.getString(id, null) ?: return null
            val parts = encoded.split(":")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            gson.fromJson(cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8), KugouSession::class.java)
                ?.takeIf { it.userId == id && it.token.isNotBlank() }
                ?.also { sessionCache[id] = it }
        }.getOrNull()
    }

    override fun saveSession(session: KugouSession) {
        sessionCache[session.userId] = session
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(session.userId.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
        val value = Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(encrypted)
        check(secrets.edit().putString(session.userId, value).commit()) { "无法保存登录凭证" }
    }

    override fun removeSession(id: String) {
        sessionCache.remove(id)
        secrets.edit().remove(id).apply()
    }

    override fun device(): DeviceIdentity {
        cachedDevice?.let { return it }
        val guid = metadata.getString("guid", null) ?: KugouSigner.md5(UUID.randomUUID().toString()).also {
            metadata.edit().putString("guid", it).apply()
        }
        val dev = metadata.getString("dev", null) ?: KugouSigner.random(10).uppercase().also {
            metadata.edit().putString("dev", it).apply()
        }
        val dfid = metadata.getString("dfid", "-") ?: "-"
        return DeviceIdentity(guid, KugouSigner.mid(guid), dev, dfid = dfid).also {
            cachedDevice = it
        }
    }

    override fun saveDfid(dfid: String) {
        if (dfid.isNotBlank() && dfid != "-") {
            cachedDevice = device().copy(dfid = dfid)
            metadata.edit().putString("dfid", dfid).apply()
        }
    }
}
