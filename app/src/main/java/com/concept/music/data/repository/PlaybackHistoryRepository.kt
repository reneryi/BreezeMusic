package com.concept.music.data.repository

import android.content.Context
import com.concept.music.data.model.Song
import com.concept.music.data.kugou.KugouSigner
import com.google.gson.reflect.TypeToken

class PlaybackHistoryRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("play_history", Context.MODE_PRIVATE)
    fun get(userId: String?): List<Song> = runCatching {
        val key = "songs_${userId ?: "guest"}"
        KugouSigner.gson.fromJson<List<Song>>(prefs.getString(key, "[]"), object : TypeToken<List<Song>>() {}.type) ?: emptyList()
    }.getOrDefault(emptyList())
    fun add(userId: String?, song: Song) {
        val list = get(userId).filterNot { it.id == song.id }.toMutableList()
        list.add(0, song)
        prefs.edit().putString("songs_${userId ?: "guest"}", KugouSigner.gson.toJson(list.take(100))).apply()
    }
    fun clear(userId: String?) { prefs.edit().remove("songs_${userId ?: "guest"}").apply() }
}
