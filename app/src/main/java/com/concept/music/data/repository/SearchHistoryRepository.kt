package com.concept.music.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.concept.music.data.kugou.KugouSigner
import com.google.gson.reflect.TypeToken

/**
 * 搜索历史记录持久化存储仓库
 * - 在真实设备上持久化至 SharedPreferences
 * - 在纯 JVM 单元测试环境中自适应内存降级，避免 Context 空指针
 */
class SearchHistoryRepository(context: Context? = null) {
    private val prefs: SharedPreferences? = try {
        context?.applicationContext?.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    } catch (_: Exception) {
        null
    }

    private var memoryCache = mutableListOf<String>()

    fun get(): List<String> = runCatching {
        val sp = prefs ?: return memoryCache
        val json = sp.getString("history_keywords", "[]") ?: "[]"
        val list: List<String> = KugouSigner.gson.fromJson(json, object : TypeToken<List<String>>() {}.type) ?: emptyList()
        memoryCache = list.toMutableList()
        list
    }.getOrDefault(memoryCache)

    fun save(items: List<String>) {
        memoryCache = items.toMutableList()
        runCatching {
            prefs?.edit()?.putString("history_keywords", KugouSigner.gson.toJson(items))?.apply()
        }
    }

    fun clear() {
        memoryCache.clear()
        runCatching {
            prefs?.edit()?.remove("history_keywords")?.apply()
        }
    }
}
