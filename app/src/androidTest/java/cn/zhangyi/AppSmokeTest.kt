package cn.zhangyi

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.zhangyi.domain.LedgerData
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun reset() { runBlocking { (compose.activity.application as ZhangYiApplication).repository.update { LedgerData() } }; compose.waitForIdle() }
    @Test fun emptyLaunchCreateSubscriptionNavigateAndMarkPaid() {
        compose.waitUntil(15000) { compose.onAllNodesWithText("本月应付").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("新增订阅").onFirst().performClick()
        compose.onNodeWithText("名称 *").performTextInput("界面测试会员")
        compose.onNodeWithText("每周期金额 *").performTextInput("30.00")
        compose.onNodeWithText("保存订阅").performScrollTo().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("界面测试会员").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("标记已付").performScrollTo().performClick()
        compose.onNodeWithText("保存付款").performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("本月已付 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("本月已付 1").performScrollTo().performClick()
        compose.onNodeWithText("撤销已付").performScrollTo().performClick()
        compose.onAllNodesWithText("撤销已付").onLast().performClick()
        compose.waitUntil(15000) { compose.onAllNodesWithText("本月待付 1").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("日历").performClick()
        compose.onNodeWithText("回到今天").assertExists()
        compose.onNodeWithText("订阅").performClick()
        compose.onNodeWithText("搜索订阅名称").assertExists()
        compose.onNodeWithText("设置").performClick()
        compose.onNodeWithText("导出完整JSON备份").performScrollTo().assertExists()
    }
}
