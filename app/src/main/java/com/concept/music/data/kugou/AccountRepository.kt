package com.concept.music.data.kugou

import com.concept.music.data.vip.ListeningGradeManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock

class AccountRepository(private val store: SessionStore, private val auth: AuthRepository,
    private val transport: KugouTransport, private val clock: Clock = Clock.systemUTC()) {
    private var generation = 0L
    private val refreshMutex = Mutex()
    private val mutable = MutableStateFlow(AccountState(accounts = store.accounts(), currentId = store.activeId()))
    val state = mutable.asStateFlow()
    init { select(store.activeId()) }
    fun context(): AccountContext? = state.value.currentId?.let { id -> store.session(id)?.let { AccountContext(it, generation) } }
    fun isCurrent(context: AccountContext): Boolean = state.value.currentId == context.session.userId && generation == context.generation
    fun ensureCurrent(context: AccountContext) { if (!isCurrent(context)) throw CancellationException("账号已切换") }
    fun select(id: String?) {
        generation++
        store.setActive(id)
        val saved = store.accounts().find { it.userId == id }
        mutable.value = AccountState(store.accounts(), id, saved?.let { AccountProfile(it.userId, it.nickname, it.avatarUrl) },
            if (id == null) AuthState.GUEST else if (store.session(id) == null || saved?.needsLogin == true) AuthState.NEEDS_LOGIN else AuthState.RESTORING)
    }
    fun accept(result: LoginResult.Success) {
        store.saveSession(result.session)
        val list = store.accounts()
        val old = list.find { it.userId == result.session.userId }
        val saved = (old ?: SavedAccount(result.session.userId)).copy(nickname = result.profile.nickname,
            avatarUrl = result.profile.avatarUrl, needsLogin = false)
        store.saveAccounts(list.filterNot { it.userId == saved.userId } + saved)
        select(saved.userId)
    }
    fun remove(id: String) {
        store.removeSession(id)
        store.saveAccounts(store.accounts().filterNot { it.userId == id })
        if (id == state.value.currentId) select(null) else mutable.value = state.value.copy(accounts = store.accounts())
    }
    fun setAuto(enabled: Boolean) { updateCurrent { it.copy(autoClaim = enabled) } }
    fun completed(context: AccountContext, day: String) { ensureCurrent(context); updateCurrent { it.copy(completedDay = day) } }
    private fun updateCurrent(transform: (SavedAccount) -> SavedAccount) {
        store.saveAccounts(store.accounts().map { if (it.userId == state.value.currentId) transform(it) else it })
        mutable.value = state.value.copy(accounts = store.accounts())
    }
    suspend fun restore() {
        val context = context() ?: return
        if (state.value.auth == AuthState.NEEDS_LOGIN) return
        try {
            refreshSession(context)
            ensureCurrent(context)
            mutable.value = state.value.copy(auth = AuthState.READY, message = null)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            ensureCurrent(context)
            if (e is ApiFailure && e.invalidSession) invalidate(context)
            else mutable.value = state.value.copy(message = "会话刷新失败，可重试：${e.message}")
        }
    }
    suspend fun refreshSession(context: AccountContext): KugouSession = refreshMutex.withLock {
        ensureCurrent(context)
        val latest = store.session(context.session.userId) ?: throw ApiFailure(20010, "请重新登录")
        if (latest.token != context.session.token) return@withLock latest
        val refreshed = auth.refresh(latest)
        ensureCurrent(context)
        store.saveSession(refreshed.session)
        updateCurrent { it.copy(needsLogin = false) }
        refreshed.session
    }
    suspend fun <T> authenticated(context: AccountContext, block: suspend (KugouSession) -> T): T {
        ensureCurrent(context)
        val latest = store.session(context.session.userId) ?: throw ApiFailure(20010, "请重新登录")
        try { return block(latest).also { ensureCurrent(context) } }
        catch (e: ApiFailure) {
            if (!e.invalidSession) throw e
            val refreshed = try { refreshSession(context.copy(session = latest)) }
            catch (failure: ApiFailure) { if (failure.invalidSession) invalidate(context); throw failure }
            return try { block(refreshed).also { ensureCurrent(context) } }
            catch (failure: ApiFailure) { if (failure.invalidSession) invalidate(context); throw failure }
        }
    }
    private fun invalidate(context: AccountContext) {
        ensureCurrent(context)
        updateCurrent { it.copy(needsLogin = true) }
        mutable.value = state.value.copy(auth = AuthState.NEEDS_LOGIN, profileFresh = false, message = "登录已失效，请重新登录")
    }
    suspend fun refreshProfile(context: AccountContext) {
        try {
            val response = authenticated(context) { session ->
                val time = clock.instant().epochSecond
                transport.call("/v3/get_my_info", session, query = mapOf("plat" to "1"), method = "POST",
                    body = json("visit_time" to time, "usertype" to 1,
                        "p" to KugouSigner.rsa(KugouSigner.gson.toJson(json("token" to session.token, "clienttime" to time))).uppercase(),
                        "userid" to session.userId.toLong()), headers = mapOf("x-router" to "usercenter.kugou.com")).requireSuccess()
            }
            ensureCurrent(context)
            val data = response.data
            check(data.text("userid") == null || data.text("userid") == context.session.userId) { "账号资料不匹配" }
            val profile = AccountProfile(context.session.userId, data.text("nickname") ?: state.value.profile?.nickname ?: "酷狗用户", imageUrl(data.text("pic")))
            updateCurrent { it.copy(nickname = profile.nickname, avatarUrl = profile.avatarUrl, needsLogin = false) }
            mutable.value = state.value.copy(profile = profile, auth = AuthState.READY, profileFresh = true, message = null)

            // 同步 /v3/get_my_info (module/user_detail.js) 中的官方个人资料权威听歌时长（分钟）与等级
            val officialMinutes = listOfNotNull(
                data.text("r_duration")?.toLongOrNull(),
                data.text("duration")?.toLongOrNull(),
                data.text("listen_duration")?.toLongOrNull(),
                data.text("user_duration")?.toLongOrNull()
            ).maxOrNull() ?: 0L
            val officialLevel = listOfNotNull(
                data.number("user_grade"),
                data.number("p_grade"),
                data.number("grade")
            ).maxOrNull() ?: 0
            if (officialMinutes > 0L || officialLevel > 0) {
                ListeningGradeManager.onOfficialProfileSynced(context.session.userId, officialMinutes, officialLevel)
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ensureCurrent(context); mutable.value = state.value.copy(profileFresh = false, message = "资料尚未刷新：${e.message}") }
    }
}
