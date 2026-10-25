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
    fun isDfidRegistered(): Boolean = true
}

class EncryptedSessionStore(context: Context) : SessionStore {
    companion object {
        private const val KEY_ALIAS = "concept_music_accounts"
    }

    private val metadata = context.getSharedPreferences("kugou_accounts_v2", Context.MODE_PRIVATE)
    private val secrets = context.getSharedPreferences("kugou_secrets_v2", Context.MODE_PRIVATE)
    private val gson = KugouSigner.gson

    @Volatile
    private var cachedAccounts: List<SavedAccount>? = null

    @Volatile
    private var cachedActiveId: String? = null

    @Volatile
    private var activeIdLoaded: Boolean = false

    @Volatile
    private var cachedDevice: DeviceIdentity? = null

    @Volatile
    private var dfidRegistered: Boolean = false

    @Volatile
    private var cachedSecretKey: SecretKey? = null

    private val sessionCache = ConcurrentHashMap<String, KugouSession>()

    @Synchronized
    private fun getOrCreateSecretKey(forceRecreate: Boolean = false): SecretKey? {
        if (!forceRecreate && cachedSecretKey != null) {
            return cachedSecretKey
        }
        return try {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (forceRecreate) {
                runCatching { store.deleteEntry(KEY_ALIAS) }
                secrets.edit().clear().apply()
                sessionCache.clear()
            }
            val existing = if (!forceRecreate) {
                runCatching { store.getKey(KEY_ALIAS, null) as? SecretKey }.getOrNull()
            } else null
            val resolved = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(
                    KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                    )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .build()
                )
                generateKey()
            }
            cachedSecretKey = resolved
            resolved
        } catch (_: Throwable) {
            if (!forceRecreate) {
                runCatching {
                    val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                    store.deleteEntry(KEY_ALIAS)
                    secrets.edit().clear().apply()
                    val rebuilt = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                        init(
                            KeyGenParameterSpec.Builder(
                                KEY_ALIAS,
                                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                            )
                                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                                .build()
                        )
                        generateKey()
                    }
                    cachedSecretKey = rebuilt
                    rebuilt
                }.getOrNull()
            } else {
                null
            }
        }
    }

    private fun synthesizeFallbackDfid(guid: String, dev: String): String {
        val raw = KugouSigner.md5("kugou_lite_dfid_${guid}_$dev").lowercase()
        return "2" + raw.take(23)
    }

    init {
        dfidRegistered = metadata.getBoolean("device.dfid_registered", false)
        if (!metadata.contains("guid")) {
            val guid = KugouSigner.md5(UUID.randomUUID().toString())
            val dev = KugouSigner.random(10).uppercase()
            val initialDfid = synthesizeFallbackDfid(guid, dev)
            metadata.edit()
                .putString("guid", guid)
                .putString("dev", dev)
                .putString("dfid", initialDfid)
                .putBoolean("device.dfid_registered", false)
                .apply()
            cachedDevice = DeviceIdentity(guid, KugouSigner.mid(guid), dev, dfid = initialDfid)
        } else {
            val guid = metadata.getString("guid", null).orEmpty().ifBlank { KugouSigner.md5(UUID.randomUUID().toString()) }
            val dev = metadata.getString("dev", null).orEmpty().ifBlank { KugouSigner.random(10).uppercase() }
            val existingDfid = metadata.getString("dfid", "-") ?: "-"
            val resolvedDfid = if (existingDfid.isBlank() || existingDfid == "-") {
                val fallbackDfid = synthesizeFallbackDfid(guid, dev)
                metadata.edit().putString("dfid", fallbackDfid).putBoolean("device.dfid_registered", false).apply()
                dfidRegistered = false
                fallbackDfid
            } else {
                existingDfid
            }
            cachedDevice = DeviceIdentity(guid, KugouSigner.mid(guid), dev, dfid = resolvedDfid)
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

    @Synchronized
    override fun accounts(): List<SavedAccount> {
        cachedAccounts?.let { return it }
        val loaded = runCatching {
            gson.fromJson<List<SavedAccount>>(metadata.getString("accounts", "[]"), object : TypeToken<List<SavedAccount>>() {}.type)
        }.getOrNull().orEmpty()
        cachedAccounts = loaded
        return loaded
    }

    @Synchronized
    override fun saveAccounts(accounts: List<SavedAccount>) {
        cachedAccounts = accounts.toList()
        metadata.edit().putString("accounts", gson.toJson(cachedAccounts)).apply()
    }

    @Synchronized
    override fun activeId(): String? {
        if (activeIdLoaded) return cachedActiveId
        val id = metadata.getString("active", null)
        cachedActiveId = id
        activeIdLoaded = true
        return id
    }

    @Synchronized
    override fun setActive(id: String?) {
        cachedActiveId = id
        activeIdLoaded = true
        metadata.edit().putString("active", id).apply()
    }

    @Synchronized
    override fun session(id: String): KugouSession? {
        sessionCache[id]?.let { return it }
        val encoded = secrets.getString(id, null) ?: return null
        val parts = encoded.split(":")
        if (parts.size != 2) return null
        val secretKey = getOrCreateSecretKey(forceRecreate = false) ?: return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])))
            cipher.updateAAD(id.toByteArray(Charsets.UTF_8))
            gson.fromJson(cipher.doFinal(Base64.getDecoder().decode(parts[1])).toString(Charsets.UTF_8), KugouSession::class.java)
                ?.takeIf { it.userId == id && it.token.isNotBlank() }
                ?.also { sessionCache[id] = it }
        } catch (_: Throwable) {
            // 密钥失效或密文损坏时自愈清理单条记录并尝试重建密钥
            secrets.edit().remove(id).apply()
            getOrCreateSecretKey(forceRecreate = true)
            null
        }
    }

    @Synchronized
    override fun saveSession(session: KugouSession) {
        sessionCache[session.userId] = session
        val encodedValue = fun(secretKey: SecretKey): String {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            cipher.updateAAD(session.userId.toByteArray(Charsets.UTF_8))
            val encrypted = cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
            return Base64.getEncoder().encodeToString(cipher.iv) + ":" + Base64.getEncoder().encodeToString(encrypted)
        }
        val key = getOrCreateSecretKey(forceRecreate = false)
        if (key != null) {
            val value = runCatching { encodedValue(key) }.getOrElse {
                val rebuiltKey = getOrCreateSecretKey(forceRecreate = true)
                rebuiltKey?.let { runCatching { encodedValue(it) }.getOrNull() }
            }
            if (value != null) {
                secrets.edit().putString(session.userId, value).apply()
            }
        }
    }

    @Synchronized
    override fun removeSession(id: String) {
        sessionCache.remove(id)
        secrets.edit().remove(id).apply()
    }

    @Synchronized
    override fun device(): DeviceIdentity {
        cachedDevice?.let { return it }
        val guid = metadata.getString("guid", null).orEmpty().ifBlank {
            KugouSigner.md5(UUID.randomUUID().toString()).also { metadata.edit().putString("guid", it).apply() }
        }
        val dev = metadata.getString("dev", null).orEmpty().ifBlank {
            KugouSigner.random(10).uppercase().also { metadata.edit().putString("dev", it).apply() }
        }
        val rawDfid = metadata.getString("dfid", "-") ?: "-"
        val dfid = if (rawDfid.isBlank() || rawDfid == "-") {
            synthesizeFallbackDfid(guid, dev).also { metadata.edit().putString("dfid", it).apply() }
        } else {
            rawDfid
        }
        return DeviceIdentity(guid, KugouSigner.mid(guid), dev, dfid = dfid).also {
            cachedDevice = it
        }
    }

    @Synchronized
    override fun saveDfid(dfid: String) {
        if (dfid.isNotBlank() && dfid != "-") {
            cachedDevice = device().copy(dfid = dfid)
            dfidRegistered = true
            metadata.edit().putString("dfid", dfid).putBoolean("device.dfid_registered", true).apply()
        }
    }

    @Synchronized
    override fun isDfidRegistered(): Boolean = dfidRegistered
}
