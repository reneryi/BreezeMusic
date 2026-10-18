package com.concept.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class User(val userId: String = "-", val nickname: String = "未登录", val avatarUrl: String = "")
