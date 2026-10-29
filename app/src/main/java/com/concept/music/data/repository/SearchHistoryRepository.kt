package com.concept.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.KugouSigner
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 搜索历史记录持久化存储仓库
 * - 线程安全读写并返回不可变快照；
 * - 惰性初始化 SharedPreferences，避免冷启动在主线程抢占磁盘 IO；
 * - 使用单线程串行 IO 调度器保序落盘。
 */
class SearchHistoryRepository(context: Context? = null) {
    private val appCtx: Context? = try { context?.applicationContext } catch (_: Exception) { null }
    private val prefs: SharedPreferences? by lazy {
        try {
            appCtx?.getSharedPreferences("search_history", Context.MODE_PRIVATE)
        } catch (_: Exception) {
            null
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private var memoryCache = mutableListOf<String>()
    private var loadedFromDisk = false

    @Synchronized
    fun get(): List<String> {
        if (loadedFromDisk) return memoryCache.toList()
        return runCatching {
            val sp = prefs ?: run {
                loadedFromDisk = true
                return memoryCache.toList()
            }
            val json = sp.getString("history_keywords", "[]") ?: "[]"
            val list: List<String> = if (json.isBlank() || json == "[]") {
                emptyList()
            } else {
                KugouSigner.gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
            }
            memoryCache = list.toMutableList()
            loadedFromDisk = true
            memoryCache.toList()
        }.getOrDefault(memoryCache.toList())
    }

    @Synchronized
    fun save(items: List<String>) {
        val snapshot = items.toList()
        memoryCache = snapshot.toMutableList()
        loadedFromDisk = true
        ioScope.launch {
            runCatching {
                prefs?.edit()?.putString("history_keywords", KugouSigner.gson.toJson(snapshot))?.apply()
            }
        }
    }

    @Synchronized
    fun clear() {
        memoryCache.clear()
        loadedFromDisk = true
        ioScope.launch {
            runCatching {
                prefs?.edit()?.remove("history_keywords")?.apply()
            }
        }
    }
}
