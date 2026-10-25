package com.concept.music.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.concept.music.MainActivity
import com.concept.music.R
import com.concept.music.data.vip.VipManager

object ConceptMusicWidgetUpdater {
    const val ACTION_WIDGET_TOGGLE_PLAY = "com.concept.music.ACTION_WIDGET_TOGGLE_PLAY"
    const val ACTION_WIDGET_NEXT = "com.concept.music.ACTION_WIDGET_NEXT"
    const val ACTION_WIDGET_PREV = "com.concept.music.ACTION_WIDGET_PREV"
    private const val PREFS_NAME = "concept_widget_state"

    private var cachedTitle: String = "ConceptMusic 酷狗概念版"
    private var cachedArtist: String = "点击开启流光无损听歌体验"
    private var cachedLyric: String = "KRC 逐字流光 · 每日自动领概念版 VIP"
    private var cachedPlaying: Boolean = false
    private var prefsLoaded: Boolean = false

    @Volatile
    var onWidgetCommand: ((String) -> Unit)? = null

    private fun ensureStateLoaded(context: Context) {
        if (prefsLoaded) return
        prefsLoaded = true
        runCatching {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            cachedTitle = sp.getString("title", cachedTitle) ?: cachedTitle
            cachedArtist = sp.getString("artist", cachedArtist) ?: cachedArtist
            cachedLyric = sp.getString("lyric", cachedLyric) ?: cachedLyric
        }
    }

    fun dispatchCommand(context: Context, action: String) {
        val handler = onWidgetCommand
        if (handler != null) {
            handler(action)
            return
        }
        val handledByService = when (action) {
            ACTION_WIDGET_TOGGLE_PLAY -> PlaybackService.toggleActivePlayerPlayPause()
            ACTION_WIDGET_NEXT -> PlaybackService.onSkipToNextIntercepted?.invoke() == true
            ACTION_WIDGET_PREV -> PlaybackService.onSkipToPreviousIntercepted?.invoke() == true
            else -> false
        }
        if (!handledByService) {
            runCatching {
                val appCtx = context.applicationContext
                val serviceIntent = Intent(appCtx, PlaybackService::class.java).apply {
                    this.action = action
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appCtx.startForegroundService(serviceIntent)
                } else {
                    appCtx.startService(serviceIntent)
                }
            }
        }
    }

    fun updateState(
        context: Context,
        songTitle: String? = null,
        artist: String? = null,
        lyricLine: String? = null,
        isPlaying: Boolean? = null
    ) {
        ensureStateLoaded(context)
        if (!songTitle.isNullOrBlank()) cachedTitle = songTitle
        if (!artist.isNullOrBlank()) cachedArtist = artist
        if (!lyricLine.isNullOrBlank()) cachedLyric = lyricLine
        if (isPlaying != null) cachedPlaying = isPlaying
        runCatching {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("title", cachedTitle)
                .putString("artist", cachedArtist)
                .putString("lyric", cachedLyric)
                .apply()
        }
        requestUpdate(context)
    }

    fun requestUpdate(context: Context) {
        runCatching {
            val appCtx = context.applicationContext
            ensureStateLoaded(appCtx)
            val manager = AppWidgetManager.getInstance(appCtx)
            val component = ComponentName(appCtx, ConceptMusicWidgetProvider::class.java)
            val ids = manager.getAppWidgetIds(component)
            if (ids != null && ids.isNotEmpty()) {
                ids.forEach { id ->
                    updateSingleWidget(appCtx, manager, id)
                }
            }
        }
    }

    internal fun updateSingleWidget(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int
    ) {
        ensureStateLoaded(context)
        val views = RemoteViews(context.packageName, R.layout.widget_concept_music)
        views.setTextViewText(R.id.widget_song_title, cachedTitle)
        views.setTextViewText(R.id.widget_song_artist, cachedArtist)
        views.setTextViewText(R.id.widget_lyric_line, cachedLyric)

        val vipActive = runCatching {
            VipManager.vip.state.value.snapshot.isVip ||
                VipGuardianManager.state.value.lastResultSummary.contains("已激活")
        }.getOrDefault(false)
        views.setTextViewText(
            R.id.widget_vip_badge,
            if (vipActive) "SVIP 已守护" else "概念版守护中"
        )

        views.setImageViewResource(
            R.id.widget_btn_play,
            if (cachedPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        )

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(
            context,
            100,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPending)

        val togglePending = buildBroadcastPendingIntent(context, ACTION_WIDGET_TOGGLE_PLAY, 101)
        val prevPending = buildBroadcastPendingIntent(context, ACTION_WIDGET_PREV, 102)
        val nextPending = buildBroadcastPendingIntent(context, ACTION_WIDGET_NEXT, 103)

        views.setOnClickPendingIntent(R.id.widget_btn_play, togglePending)
        views.setOnClickPendingIntent(R.id.widget_btn_prev, prevPending)
        views.setOnClickPendingIntent(R.id.widget_btn_next, nextPending)

        manager.updateAppWidget(appWidgetId, views)
    }

    private fun buildBroadcastPendingIntent(
        context: Context,
        action: String,
        requestCode: Int
    ): PendingIntent {
        // 使用显式组件 Intent，防止外部恶意应用伪造隐式广播指令
        val intent = Intent(context, ConceptMusicWidgetProvider::class.java).apply {
            this.action = action
            setPackage(context.packageName)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class ConceptMusicWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            ConceptMusicWidgetUpdater.updateSingleWidget(context, appWidgetManager, id)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        when (action) {
            ConceptMusicWidgetUpdater.ACTION_WIDGET_TOGGLE_PLAY,
            ConceptMusicWidgetUpdater.ACTION_WIDGET_NEXT,
            ConceptMusicWidgetUpdater.ACTION_WIDGET_PREV -> {
                ConceptMusicWidgetUpdater.dispatchCommand(context, action)
            }
        }
    }
}
