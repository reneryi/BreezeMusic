package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: String,
    val title: String,
    val coverUrl: String = "",
    val trackCount: Int = 0,
    val playCount: String = "",
    val description: String = "",
    val creatorName: String = "",
    val isCreatedByMe: Boolean = true,
    val isFavorite: Boolean = false,
    val isLiked: Boolean = false,
    val listId: Long = 0L,
    val globalCollectionId: String = "",
    val totalVersion: Long = 0L,
    val listCreateUserid: String = "",
    val listCreateListid: String = "",
    val listCreateGid: String = ""
) {
    val songCount: Int get() = trackCount

    /**
     * 提取歌单所有可能匹配的身份标识集合（兼容 specialId、global_collection_id、list_create_gid、list_create_listid 以及 GID 内嵌的原始 listId）
     */
    fun candidateIdentityKeys(): Set<String> {
        val result = LinkedHashSet<String>()
        fun addKey(raw: String?) {
            val clean = raw?.trim().orEmpty()
            if (clean.isEmpty() || clean == "0") return
            result.add(clean.lowercase())
            if (clean.startsWith("collection_", ignoreCase = true)) {
                val parts = clean.split("_")
                // collection_3_{createUserid}_{createListid}_{...}
                if (parts.size >= 4) {
                    val embeddedListId = parts[3].trim()
                    if (embeddedListId.isNotEmpty() && embeddedListId != "0" && embeddedListId.all { it.isDigit() }) {
                        result.add(embeddedListId)
                    }
                }
            }
        }
        addKey(id)
        addKey(globalCollectionId)
        addKey(listCreateGid)
        addKey(listCreateListid)
        if (listId > 0L) {
            result.add(listId.toString())
        }
        return result
    }

    fun matchesIdentifier(rawId: String?): Boolean {
        val clean = rawId?.trim().orEmpty()
        if (clean.isEmpty() || clean == "0") return false
        val targetKeys = LinkedHashSet<String>()
        targetKeys.add(clean.lowercase())
        if (clean.startsWith("collection_", ignoreCase = true)) {
            val parts = clean.split("_")
            if (parts.size >= 4) {
                val embedded = parts[3].trim()
                if (embedded.isNotEmpty() && embedded != "0" && embedded.all { it.isDigit() }) {
                    targetKeys.add(embedded)
                }
            }
        }
        val myKeys = candidateIdentityKeys()
        return targetKeys.any { it in myKeys }
    }

    fun isSamePlaylistAs(other: Playlist?): Boolean {
        if (other == null) return false
        if (this.listId > 0L && other.listId > 0L && this.listId == other.listId) return true
        val myKeys = candidateIdentityKeys()
        val otherKeys = other.candidateIdentityKeys()
        if (myKeys.any { it in otherKeys }) return true
        val t1 = title.trim()
        val t2 = other.title.trim()
        val isGenericTitle = t1.isEmpty() || t1 == "精选歌单" || t1 == "未命名歌单" || t1 == "我喜欢" || t1 == "我喜欢的音乐"
        if (!isGenericTitle && !this.isCreatedByMe && !other.isCreatedByMe && t1.equals(t2, ignoreCase = true)) {
            return true
        }
        return false
    }

    @Suppress("USELESS_ELVIS")
    fun sanitizeFromJson(): Playlist = Playlist(
        id = (id as String?) ?: "",
        title = (title as String?) ?: "",
        coverUrl = (coverUrl as String?) ?: "",
        trackCount = trackCount,
        playCount = (playCount as String?) ?: "",
        description = (description as String?) ?: "",
        creatorName = (creatorName as String?) ?: "",
        isCreatedByMe = isCreatedByMe,
        isFavorite = isFavorite,
        isLiked = isLiked,
        listId = listId,
        globalCollectionId = (globalCollectionId as String?) ?: "",
        totalVersion = totalVersion,
        listCreateUserid = (listCreateUserid as String?) ?: "",
        listCreateListid = (listCreateListid as String?) ?: "",
        listCreateGid = (listCreateGid as String?) ?: ""
    )
}
