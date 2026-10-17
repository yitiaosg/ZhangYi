package cn.zhangyi

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cn.zhangyi.domain.*
import cn.zhangyi.ui.CalendarScreen
import cn.zhangyi.ui.ZhangYiTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class CalendarUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun crowdedDayHasBoundedMarkersAndEveryBillAtLargeFontInDarkTheme() {
        val today = LocalDate.now()
        var d = LedgerData()
        repeat(5) { i -> d = Ledger.add(d, Values("同日$i", 1000 + i.toLong(), color = Palette.colors[i]), Cycle(), today, true, true, today) }
        val views = Ledger.views(d)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.5f)) {
                ZhangYiTheme(Preferences("DARK", false)) { CalendarScreen(views, today, {}, {}, {}) }
            }
        }
        compose.onNodeWithText("+3").assertExists()
        compose.onNodeWithContentDescription("$today，今天，5笔账单，已付0，待付5，逾期0").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("同日4"))
        compose.onNodeWithText("同日4").assertIsDisplayed()
        compose.onAllNodesWithText("待付").onLast().assertExists()
    }
}
