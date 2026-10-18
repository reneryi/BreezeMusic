package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class SongComment(
    val id: String,
    val userId: String,
    val userName: String,
    val userAvatar: String,
    val content: String,
    val timeFormatted: String,
    val likeCount: Int = 0,
    val isLiked: Boolean = false,
    val isHot: Boolean = false,
    val vipLevel: Int = 0,
    val ipLocation: String = "",
    val parentId: String? = null,
    val rootId: String? = null,
    val replyUserName: String? = null,
    val replyCount: Int = 0,
    val replies: List<SongComment> = emptyList()
)

@Immutable
data class SongDetailInfo(
    val song: Song,
    val albumName: String = "",
    val singerName: String = "",
    val publishTime: String = "",
    val company: String = "",
    val audioQuality: String = "SQ 无损 (FLAC)",
    val sampleRate: String = "44.1 kHz / 16 bit",
    val bitRate: String = "920 kbps",
    val fileSize: String = "28.5 MB",
    val fileHash: String = ""
)
