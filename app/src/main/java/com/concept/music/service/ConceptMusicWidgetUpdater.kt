package com.concept.music.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.concept.music.MainActivity
import com.concept.music.R
import com.concept.music.data.vip.VipManager

object ConceptMusicWidgetUpdater {
    const val ACTION_WIDGET_TOGGLE_PLAY = "com.concept.music.ACTION_WIDGET_TOGGLE_PLAY"
    const val ACTION_WIDGET_NEXT = "com.concept.music.ACTION_WIDGET_NEXT"
    const val ACTION_WIDGET_PREV = "com.concept.music.ACTION_WIDGET_PREV"

    private var cachedTitle: String = "ConceptMusic 酷狗概念版"
    private var cachedArtist: String = "点击开启流光无损听歌体验"
    private var cachedLyric: String = "KRC 逐字流光 · 每日自动领概念版 VIP"
    private var cachedPlaying: Boolean = false

    var onWidgetCommand: ((String) -> Unit)? = null

    fun updateState(
        context: Context,
        songTitle: String? = null,
        artist: String? = null,
        lyricLine: String? = null,
        isPlaying: Boolean? = null
    ) {
        if (!songTitle.isNullOrBlank()) cachedTitle = songTitle
        if (!artist.isNullOrBlank()) cachedArtist = artist
        if (!lyricLine.isNullOrBlank()) cachedLyric = lyricLine
        if (isPlaying != null) cachedPlaying = isPlaying
        requestUpdate(context)
    }

    fun requestUpdate(context: Context) {
        runCatching {
            val appCtx = context.applicationContext
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
        val intent = Intent(context, ConceptMusicWidgetProvider::class.java).apply {
            this.action = action
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
                ConceptMusicWidgetUpdater.onWidgetCommand?.invoke(action)
            }
        }
    }
}
