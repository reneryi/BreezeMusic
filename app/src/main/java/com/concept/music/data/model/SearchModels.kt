package com.concept.music.data.model

import androidx.compose.runtime.Immutable

enum class SearchTab(val label: String) {
    COMPLEX("综合"),
    SONG("单曲"),
    ARTIST("歌手"),
    ALBUM("专辑"),
    PLAYLIST("歌单")
}

/**
 * 综合搜索意图识别枚举
 * - SONG: 搜索具体歌曲名（如“勋章”），置顶该歌曲原版及翻唱/Live版本，同时展示原唱歌手、同名/关联专辑与歌单
 * - ARTIST: 搜索歌手名（如“邓紫棋”、“G.E.M.邓紫棋”、“鹿晗”），置顶歌手数字名片，依次展示代表单曲、热门专辑与精选歌单
 * - ALBUM: 搜索专辑名（如“范特西”、“摩天动物园”、“新的心跳”），优先置顶匹配专辑，依次展示专辑收录单曲、创作者与相关歌单
 * - PLAYLIST: 搜索主题歌单，优先置顶精选歌单与相关曲目
 */
enum class SearchIntent {
    SONG,
    ARTIST,
    ALBUM,
    PLAYLIST
}

@Immutable
data class AlbumItem(
    val id: String,
    val name: String,
    val artist: String,
    val coverUrl: String,
    val songCount: Int = 0,
    val publishDate: String = "",
    val intro: String = "",
    val company: String = ""
)

@Immutable
data class PlaylistItem(
    val id: String,
    val name: String,
    val coverUrl: String,
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val creatorName: String = "",
    val intro: String = ""
)

@Immutable
data class ArtistItem(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val intro: String = ""
)

@Immutable
data class ComplexSearchResult(
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList(),
    val intent: SearchIntent = SearchIntent.SONG,
    val primaryArtist: ArtistItem? = null
)
