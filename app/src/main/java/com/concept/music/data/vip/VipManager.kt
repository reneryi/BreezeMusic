package com.concept.music.data.vip

import android.content.Context
import com.concept.music.data.kugou.*
import com.concept.music.data.repository.PlaybackHistoryRepository

/** Application-scoped dependencies. Account actions are owned by LibraryViewModel. */
object VipManager {
    lateinit var accounts: AccountRepository; private set
    lateinit var auth: AuthRepository; private set
    lateinit var vip: VipRepository; private set
    lateinit var verification: VerificationCoordinator; private set
    lateinit var music: MusicApiRepository; private set
    lateinit var lyric: LyricRepository; private set
    lateinit var history: PlaybackHistoryRepository; private set

    fun initialize(context: Context) {
        if (::accounts.isInitialized) return
        val store = EncryptedSessionStore(context.applicationContext)
        val transport = DirectKugouTransport(store::device)
        auth = AuthRepository(transport, store)
        accounts = AccountRepository(store, auth, transport)
        verification = VerificationCoordinator(transport)
        vip = VipRepository(transport, accounts, verification)
        music = MusicApiRepository(transport, accounts, store::device)
        lyric = LyricRepository(transport, store::device)
        history = PlaybackHistoryRepository(context.applicationContext)
    }
}
