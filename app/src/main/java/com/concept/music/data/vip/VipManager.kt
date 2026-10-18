package com.concept.music.data.vip

import android.content.Context
import com.concept.music.data.kugou.*
import com.concept.music.data.repository.DiscoverLocalCache
import com.concept.music.data.repository.PlaybackHistoryRepository
import com.concept.music.data.repository.SearchHistoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Application-scoped dependencies. Account actions are owned by LibraryViewModel. */
object VipManager {
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var accounts: AccountRepository; private set
    lateinit var auth: AuthRepository; private set
    lateinit var vip: VipRepository; private set
    lateinit var verification: VerificationCoordinator; private set
    lateinit var music: MusicApiRepository; private set
    lateinit var lyric: LyricRepository; private set
    lateinit var history: PlaybackHistoryRepository; private set
    var searchHistoryRepo: SearchHistoryRepository = SearchHistoryRepository(null); private set
    var discoverCache: DiscoverLocalCache = DiscoverLocalCache(null); private set
    lateinit var device: () -> DeviceIdentity; private set

    fun initialize(context: Context) {
        if (::accounts.isInitialized) return
        val appCtx = context.applicationContext
        val store = EncryptedSessionStore(appCtx)
        val transport = DirectKugouTransport(store::device)
        device = store::device
        auth = AuthRepository(transport, store)
        accounts = AccountRepository(store, auth, transport)
        verification = VerificationCoordinator(transport)
        music = MusicApiRepository(transport, accounts, store::device)
        vip = VipRepository(
            transport = transport,
            accounts = accounts,
            verification = verification,
            onVipActivated = { music.clearPlaybackCache() }
        )
        lyric = LyricRepository(transport, store::device)
        history = PlaybackHistoryRepository(appCtx)
        searchHistoryRepo = SearchHistoryRepository(appCtx)
        discoverCache = DiscoverLocalCache(appCtx)
        ListeningGradeManager.initialize(appCtx)

        // [P0-1] 启动时若本地尚未注册 dfid ("-")，在后台静默向酷狗概念版网关申请持久化 dfid
        if (store.device().dfid == "-") {
            initScope.launch {
                runCatching { auth.registerDevice() }
            }
        }
    }

    internal fun initializeForTest(
        accounts: AccountRepository,
        auth: AuthRepository,
        vip: VipRepository,
        verification: VerificationCoordinator,
        music: MusicApiRepository,
        lyric: LyricRepository,
        history: PlaybackHistoryRepository,
        device: () -> DeviceIdentity = { DeviceIdentity("00000000-0000-0000-0000-000000000000", "0", "Android") }
    ) {
        this.accounts = accounts
        this.auth = auth
        this.vip = vip
        this.verification = verification
        this.music = music
        this.lyric = lyric
        this.history = history
        this.device = device
    }
}
