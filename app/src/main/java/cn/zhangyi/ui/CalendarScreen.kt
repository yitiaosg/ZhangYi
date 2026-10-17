@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun CalendarScreen(views: List<BillView>, today: LocalDate, ensureMonth: (YearMonth) -> Unit, onBill: (BillView) -> Unit, onPay: (BillView) -> Unit) {
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    var selectedText by rememberSaveable { mutableStateOf(today.toString()) }
    val month = YearMonth.parse(monthText)
    val selected = date(selectedText)
    LaunchedEffect(month) { ensureMonth(month) }
    val monthly = remember(views, month) { Ledger.month(views, month) }
    val grouped = remember(monthly) { monthly.groupBy { it.bill.due } }
    val daily = grouped[selectedText].orEmpty()
    fun changeMonth(next: YearMonth) { if (next.year in 1900..2199) { monthText = next.toString(); selectedText = next.atDay(1).toString() } }
    Scaffold(topBar = { PageBar("账单日历", actions = { TextButton(onClick = { monthText = YearMonth.from(today).toString(); selectedText = today.toString() }) { Text("回到今天") } }) }, contentWindowInsets = PageInsets) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { changeMonth(month.minusMonths(1)) }, enabled = month > YearMonth.of(1900, 1)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "上个月") }
                    Text(month.format(MonthFormat), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { changeMonth(month.plusMonths(1)) }, enabled = month < YearMonth.of(2199, 12)) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "下个月") }
                }
                Row(Modifier.fillMaxWidth()) { listOf("一", "二", "三", "四", "五", "六", "日").forEach { Text(it, Modifier.weight(1f).wrapContentWidth(Alignment.CenterHorizontally).padding(vertical = 8.dp), style = MaterialTheme.typography.labelMedium) } }
                val offset = month.atDay(1).dayOfWeek.value - 1
                val count = ((offset + month.lengthOfMonth() + 6) / 7) * 7
                Column {
                    (0 until count step 7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (0..6).forEach { weekday ->
                                val number = week + weekday - offset + 1
                                if (number !in 1..month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(64.dp))
                                else {
                                    val day = month.atDay(number)
                                    val bills = grouped[day.toString()].orEmpty()
                                    val isSelected = day == selected
                                    Column(Modifier.weight(1f).heightIn(min = 64.dp).background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp)).clickable { selectedText = day.toString() }.padding(vertical = 7.dp).semantics { contentDescription = "$day，${if (day == today) "今天，" else ""}${bills.size}笔账单，已付${bills.count { it.payment != null }}，待付${bills.count { it.payment == null && date(it.bill.due) >= today }}，逾期${bills.count { it.payment == null && date(it.bill.due) < today }}" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(number.toString(), style = MaterialTheme.typography.bodyMedium, color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                                            bills.take(if (bills.size > 3) 2 else 3).forEach { ColorDot(it.values.color, Modifier.size(5.dp)) }
                                        }
                                        if (bills.size > 3) Text("+${bills.size - 2}", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item { SummaryCard("该月金额 · 按到期日期", Ledger.totals(monthly)) }
            item { Text(selected.format(DayFormat), style = MaterialTheme.typography.titleMedium) }
            if (daily.isEmpty()) item { EmptyState("当天没有账单", "选择有颜色标记的日期查看账单。") }
            items(daily, key = { it.bill.id }) { b -> BillCard(b, today, { onBill(b) }, { onPay(b) }) }
        }
    }
}
