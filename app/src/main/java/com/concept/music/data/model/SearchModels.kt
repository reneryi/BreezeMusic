package com.concept.music.data.model

enum class SearchTab(val label: String) {
    COMPLEX("综合"),
    SONG("单曲"),
    ALBUM("专辑"),
    PLAYLIST("歌单")
}

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

data class PlaylistItem(
    val id: String,
    val name: String,
    val coverUrl: String,
    val playCount: Long = 0L,
    val trackCount: Int = 0,
    val creatorName: String = "",
    val intro: String = ""
)

data class ArtistItem(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val songCount: Int = 0,
    val albumCount: Int = 0,
    val intro: String = ""
)

data class ComplexSearchResult(
    val songs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val playlists: List<PlaylistItem> = emptyList(),
    val artists: List<ArtistItem> = emptyList()
)
