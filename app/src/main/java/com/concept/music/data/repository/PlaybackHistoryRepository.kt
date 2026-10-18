package com.concept.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.KugouSigner
import com.concept.music.data.model.FmChannel
import com.concept.music.data.model.Playlist
import com.concept.music.data.model.RankItem
import com.concept.music.data.model.AudioQuality
import com.concept.music.data.model.Song
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

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

/**
 * 发现页、歌单、榜单与电台曲目本地持久化秒开缓存 (Stale-While-Revalidate)
 * - 冷启动或切换模块时 0ms 同步返回上一次缓存的高质量快照，彻底消除各板块空白等待
 * - 后台静默拉取最新数据并原子更新内存与 SharedPreferences
 * - 在纯 JVM 单元测试环境中自适应内存降级，避免 Context 空指针
 */
class DiscoverLocalCache(context: Context? = null) {
    private val prefs: SharedPreferences? = try {
        context?.applicationContext?.getSharedPreferences("concept_discover_swr_cache_v2", Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    private val memorySongListMap = ConcurrentHashMap<String, List<Song>>()
    private val memoryRankList = ConcurrentHashMap<String, List<RankItem>>()
    private val memoryFmChannelList = ConcurrentHashMap<String, List<FmChannel>>()
    private val memoryPlaylistList = ConcurrentHashMap<String, List<Playlist>>()

    private val songListType = object : TypeToken<List<Song>>() {}.type
    private val rankListType = object : TypeToken<List<RankItem>>() {}.type
    private val fmChannelListType = object : TypeToken<List<FmChannel>>() {}.type
    private val playlistListType = object : TypeToken<List<Playlist>>() {}.type

    private fun sanitizeCachedSong(s: Song): Song {
        if (!s.isHiRes) return s
        val isVerifiedHiRes = s.hashHiRes.isNotBlank() &&
            s.fileSizeByte >= 38_000_000L &&
            (s.bitrateKbps <= 0 || s.bitrateKbps >= 1800) &&
            !s.hashHiRes.equals(s.hashFlac, ignoreCase = true) &&
            !s.hashHiRes.equals(s.hash320, ignoreCase = true) &&
            !s.hashHiRes.equals(s.hash128, ignoreCase = true)
        if (isVerifiedHiRes) return s
        val downgradeFlac = s.hashFlac.ifBlank { s.hashHiRes }
        val cleanedQualities = s.effectiveQualities.filter { it != AudioQuality.HI_RES }.let { q ->
            if (downgradeFlac.isNotBlank() && AudioQuality.LOSSLESS !in q) q + AudioQuality.LOSSLESS else q
        }.ifEmpty { listOf(AudioQuality.STANDARD) }
        return s.copy(
            hashHiRes = "",
            hashFlac = downgradeFlac,
            isHiRes = false,
            isLossless = downgradeFlac.isNotBlank() || s.isLossless,
            quality = if (downgradeFlac.isNotBlank() || s.isLossless) "flac" else "320",
            availableQualities = cleanedQualities
        )
    }

    private fun getSongList(key: String): List<Song> {
        memorySongListMap[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val sp = prefs ?: return emptyList()
            val raw = sp.getString(key, null) ?: return emptyList()
            val rawList: List<Song> = KugouSigner.gson.fromJson(raw, songListType) ?: emptyList()
            val list = rawList.map { sanitizeCachedSong(it) }
            if (list.isNotEmpty()) {
                memorySongListMap[key] = list
            }
            list
        }.getOrDefault(emptyList())
    }

    private fun saveSongList(key: String, songs: List<Song>, maxPersistCount: Int = 120) {
        if (songs.isEmpty()) return
        val trimmed = songs.take(maxPersistCount)
        memorySongListMap[key] = trimmed
        runCatching {
            prefs?.edit()?.putString(key, KugouSigner.gson.toJson(trimmed))?.apply()
        }
    }

    fun getDailyRecommend(userId: String?): List<Song> =
        getSongList("daily_rec_${userId ?: "guest"}")

    fun saveDailyRecommend(userId: String?, songs: List<Song>) =
        saveSongList("daily_rec_${userId ?: "guest"}", songs, maxPersistCount = 60)

    fun getPersonalFm(userId: String?): List<Song> =
        getSongList("personal_fm_${userId ?: "guest"}")

    fun savePersonalFm(userId: String?, songs: List<Song>) =
        saveSongList("personal_fm_${userId ?: "guest"}", songs, maxPersistCount = 40)

    fun getNewSongs(periodKey: String): List<Song> =
        getSongList("new_songs_${periodKey.uppercase()}")

    fun saveNewSongs(periodKey: String, songs: List<Song>) =
        saveSongList("new_songs_${periodKey.uppercase()}", songs, maxPersistCount = 120)

    fun getRanks(): List<RankItem> {
        memoryRankList["ranks_all"]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val sp = prefs ?: return emptyList()
            val raw = sp.getString("ranks_all", null) ?: return emptyList()
            val list: List<RankItem> = KugouSigner.gson.fromJson(raw, rankListType) ?: emptyList()
            if (list.isNotEmpty()) {
                memoryRankList["ranks_all"] = list
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveRanks(ranks: List<RankItem>) {
        if (ranks.isEmpty()) return
        memoryRankList["ranks_all"] = ranks
        runCatching {
            prefs?.edit()?.putString("ranks_all", KugouSigner.gson.toJson(ranks))?.apply()
        }
    }

    fun getFmChannels(): List<FmChannel> {
        memoryFmChannelList["fm_channels_all"]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val sp = prefs ?: return emptyList()
            val raw = sp.getString("fm_channels_all", null) ?: return emptyList()
            val list: List<FmChannel> = KugouSigner.gson.fromJson(raw, fmChannelListType) ?: emptyList()
            if (list.isNotEmpty()) {
                memoryFmChannelList["fm_channels_all"] = list
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveFmChannels(channels: List<FmChannel>) {
        if (channels.isEmpty()) return
        memoryFmChannelList["fm_channels_all"] = channels
        runCatching {
            prefs?.edit()?.putString("fm_channels_all", KugouSigner.gson.toJson(channels))?.apply()
        }
    }

    fun getUserPlaylists(userId: String?): List<Playlist> {
        val key = "user_playlists_${userId ?: "guest"}"
        memoryPlaylistList[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        return runCatching {
            val sp = prefs ?: return emptyList()
            val raw = sp.getString(key, null) ?: return emptyList()
            val list: List<Playlist> = KugouSigner.gson.fromJson(raw, playlistListType) ?: emptyList()
            if (list.isNotEmpty()) {
                memoryPlaylistList[key] = list
            }
            list
        }.getOrDefault(emptyList())
    }

    fun saveUserPlaylists(userId: String?, playlists: List<Playlist>) {
        if (playlists.isEmpty()) return
        val key = "user_playlists_${userId ?: "guest"}"
        memoryPlaylistList[key] = playlists
        runCatching {
            prefs?.edit()?.putString(key, KugouSigner.gson.toJson(playlists))?.apply()
        }
    }

    fun getLikedSongs(userId: String?): List<Song> =
        getSongList("liked_songs_${userId ?: "guest"}")

    fun saveLikedSongs(userId: String?, songs: List<Song>) =
        saveSongList("liked_songs_${userId ?: "guest"}", songs, maxPersistCount = 300)

    fun getPlaylistSongs(playlistId: String): List<Song> {
        if (playlistId.isBlank()) return emptyList()
        return getSongList("pl_tracks_$playlistId")
    }

    fun savePlaylistSongs(playlistId: String, songs: List<Song>) {
        if (playlistId.isBlank() || songs.isEmpty()) return
        saveSongList("pl_tracks_$playlistId", songs, maxPersistCount = 250)
    }

    fun getRankSongs(rankId: Int): List<Song> {
        if (rankId <= 0) return emptyList()
        return getSongList("rank_tracks_$rankId")
    }

    fun saveRankSongs(rankId: Int, songs: List<Song>) {
        if (rankId <= 0 || songs.isEmpty()) return
        saveSongList("rank_tracks_$rankId", songs, maxPersistCount = 150)
    }

    fun getFmChannelSongs(channelId: String): List<Song> {
        if (channelId.isBlank()) return emptyList()
        return getSongList("fm_tracks_$channelId")
    }

    fun saveFmChannelSongs(channelId: String, songs: List<Song>) {
        if (channelId.isBlank() || songs.isEmpty()) return
        saveSongList("fm_tracks_$channelId", songs, maxPersistCount = 60)
    }
}
