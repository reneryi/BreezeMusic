package com.concept.music.data.model

data class RankItem(
    val rankId: Int,
    val rankName: String,
    val coverUrl: String,
    val updateFrequency: String = "每天更新",
    val topSongsPreview: List<String> = emptyList(),
    val rankType: Int = 0
)

data class FmChannel(
    val id: String,
    val name: String,
    val coverUrl: String,
    val desc: String = ""
)

data class YouthChannel(
    val id: String,
    val name: String,
    val coverUrl: String,
    val desc: String = "",
    val songCount: Int = 0,
    val tag: String = ""
)

data class SceneCategory(
    val id: String,
    val label: String,
    val subtitle: String,
    val iconName: String = ""
)
