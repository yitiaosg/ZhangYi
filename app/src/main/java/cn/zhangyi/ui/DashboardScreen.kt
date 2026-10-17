@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(views: List<BillView>, today: LocalDate, onAdd: () -> Unit, onBill: (BillView) -> Unit, onPay: (BillView) -> Unit) {
    var paidTab by rememberSaveable { mutableStateOf(false) }
    val month = YearMonth.from(today)
    val current = remember(views, month) { Ledger.month(views, month) }
    val priorOverdue = views.filter { it.payment == null && date(it.bill.due) < month.atDay(1) }
    var showOverdue by rememberSaveable { mutableStateOf(false) }
    val list = current.filter { (it.payment != null) == paidTab }
    Scaffold(topBar = { PageBar("账易", actions = { TextButton(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Text("新增") } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("新增订阅") }) }, contentWindowInsets = PageInsets) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(month.format(MonthFormat), style = MaterialTheme.typography.titleLarge) }
            item { SummaryCard("本月应付", Ledger.totals(current)) }
            item { Text("按账单到期月份统计；已付按实际支付金额。应付 = 已付账单应付 + 待付，币种分别汇总。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (priorOverdue.isNotEmpty()) {
                item {
                    OutlinedCard(onClick = { showOverdue = !showOverdue }) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("此前逾期待付 · ${priorOverdue.size} 笔", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                            Ledger.totals(priorOverdue).forEach { Text(Money.format(it.pending, it.currency)) }
                            Text(if (showOverdue) "收起此前账单" else "查看此前账单（不计入本月待付）", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (showOverdue) items(priorOverdue, key = { "prior:${it.bill.id}" }) { b -> BillCard(b, today, { onBill(b) }, { onPay(b) }) }
            }
            item { SummaryCard("${month.plusMonths(1).format(MonthFormat)}预计支出", Ledger.totals(Ledger.month(views, month.plusMonths(1))), compact = true) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !paidTab, onClick = { paidTab = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("本月待付 ${current.count { it.payment == null }}") }
                    SegmentedButton(selected = paidTab, onClick = { paidTab = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("本月已付 ${current.count { it.payment != null }}") }
                }
            }
            if (list.isEmpty()) item { EmptyState(if (paidTab) "本月暂无已付账单" else "本月没有待付账单", "到期后仍需手动确认付款。", if (views.isEmpty()) "新增第一笔订阅" else null, onAdd) }
            items(list, key = { it.bill.id }) { b -> BillCard(b, today, { onBill(b) }, { onPay(b) }) }
        }
    }
}
