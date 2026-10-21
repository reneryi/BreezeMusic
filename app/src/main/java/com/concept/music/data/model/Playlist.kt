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
}
