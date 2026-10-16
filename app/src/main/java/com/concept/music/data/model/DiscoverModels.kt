package com.concept.music.data.model

data class RankItem(
    val rankId: Int,
    val rankName: String,
    val coverUrl: String,
    val updateFrequency: String = "每天更新",
    val topSongsPreview: List<String> = emptyList(),
    val rankType: Int = 0,
    val category: String = "官方热榜",
    val totalSongs: Int = 100
)

data class FmChannel(
    val id: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val desc: String = "",
    val category: String = "推荐电台",
    val fmType: Int = 2,
    val listenerCountText: String = "实时漫游",
    val listenerCount: Int = 0,
    val description: String = desc
)

data class YouthChannel(
    val id: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val desc: String = "",
    val songCount: Int = 0,
    val tag: String = "",
    val channelId: String = "",
    val bannerUrl: String = "",
    val subscriberCount: Int = 0,
    val isSubscribed: Boolean = false,
    val creatorName: String = "概念版主理人",
    val latestStoryPreview: String = "",
    val description: String = desc,
    val subscribeCount: Int = subscriberCount
)

/**
 * 概念版频道安利 (对应 /youth/api/amway/v2/index)
 */
data class YouthChannelAmway(
    val id: String,
    val channelId: String,
    val channelName: String,
    val title: String,
    val content: String,
    val coverUrl: String = "",
    val authorName: String = "概念安利官",
    val song: Song? = null
)

/**
 * 概念版频道音乐故事与音乐故事详情 (对应 /youth/api/channel/v1/channel_get_song_audit_passed & /youth/v2/post/get_song_detail)
 */
data class MusicStoryItem(
    val fileId: String,
    val channelId: String,
    val title: String,
    val storyContent: String,
    val authorName: String = "概念乐迷",
    val authorAvatar: String = "",
    val publishTime: String = "近期",
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val coverUrl: String = "",
    val song: Song? = null,
    val storyId: String = fileId,
    val content: String = storyContent
)

data class SceneCategory(
    val id: String,
    val label: String,
    val subtitle: String,
    val iconName: String = ""
)
