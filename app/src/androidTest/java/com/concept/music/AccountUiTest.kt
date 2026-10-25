package com.concept.music

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import org.junit.Rule
import org.junit.Test

/** Guest-state navigation; real SMS and QR authorization remain manual acceptance. */
class AccountUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun guestCanOpenPhoneLoginAndReturnToLibrary() {
        compose.onNodeWithText("我的").performClick()
        // 访客未登录态下，个人名片区显示“立即登录”，点击直接进入登录页
        compose.onNodeWithText("立即登录").performClick()
        compose.onNodeWithText("手机验证码").assertIsDisplayed()
        compose.onNodeWithText("酷狗扫码").assertIsDisplayed()
        compose.onNodeWithText("发送验证码").assertIsNotEnabled()
        compose.onNodeWithText("中国大陆手机号").performTextInput("13800000000")
        compose.onNodeWithText("发送验证码").assertIsEnabled()
        compose.onNodeWithText("短信验证码").performTextInput("123456")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("13800000000").assertExists()
        compose.onNodeWithText("123456").assertExists()
        compose.onNodeWithText("返回").performClick()
        compose.onNodeWithText("立即登录").assertIsDisplayed()
    }
}
