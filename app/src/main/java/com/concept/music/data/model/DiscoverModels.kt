package com.concept.music.data.model

/**
 * 酷狗官方排行榜数据模型
 */
data class RankItem(
    val rankId: Int,
    val rankName: String,
    val coverUrl: String,
    val updateFrequency: String = "每日更新",
    val topSongsPreview: List<String> = emptyList(),
    val rankType: Int = 1,
    val category: String = "官方热榜",
    val totalSongs: Int = 100
)

/**
 * 酷狗官方电台频道模型
 */
data class FmChannel(
    val id: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val desc: String = "",
    val category: String = "推荐电台",
    val fmType: Int = 2,
    val listenerCountText: String = "2.6w 人正在听"
) {
    val description: String get() = desc
    val listenerCount: Int
        get() = listenerCountText.filter { it.isDigit() }.toIntOrNull() ?: 2600
}

/**
 * 新歌速递分组/批次模型（支持今日最新、近3日、近5日及语种分类）
 */
data class NewSongPeriodGroup(
    val key: String,
    val label: String,
    val subtitle: String,
    val songs: List<Song>
)
