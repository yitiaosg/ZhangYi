@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.LocalDate

@Composable
fun SubscriptionDetail(data: LedgerData, views: List<BillView>, id: String, today: LocalDate, back: () -> Unit, edit: (String) -> Unit, onBill: (BillView) -> Unit, change: ChangeAction) {
    val sub = data.subscriptions.firstOrNull { it.id == id }
    var stop by remember { mutableStateOf(false) }
    var stopDate by rememberSaveable { mutableStateOf(today.toString()) }
    var archive by remember { mutableStateOf(false) }
    var delete by remember { mutableStateOf(false) }
    var resume by remember { mutableStateOf(false) }
    var history by rememberSaveable { mutableStateOf(true) }
    Scaffold(topBar = { PageBar("订阅详情", back, actions = { if (sub != null) TextButton(onClick = { edit(id) }) { Text("默认规则") } }) }, contentWindowInsets = PageInsets) { padding ->
        if (sub == null) Box(Modifier.padding(padding)) { EmptyState("订阅已不存在", "相关记录可能已被删除。", "返回", back) }
        else {
            val r = displayRule(data, id, today)
            val bills = views.filter { it.bill.subscriptionId == id }.filter { if (history) date(it.bill.due) <= today || it.payment != null else date(it.bill.due) > today && it.payment == null }.let { if (history) it.sortedByDescending { b -> b.bill.due } else it }
            val futureRules = Ledger.rules(data, id).filter { it.effectiveFrom > today.toString() }
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ColorDot(r.values.color); Text(r.values.title, style = MaterialTheme.typography.headlineSmall) } }
                item { Text("${Money.format(r.values.amount, r.values.currency)} · ${r.cycle.label()}", style = MaterialTheme.typography.titleLarge) }
                item { Text("状态：${subStatus(sub, today)}\n周期锚点：${r.anchor}\n开始记录：${sub.recordFrom}\n下次扣款：${nextDue(data, sub, today)?.toString() ?: "暂无"}", style = MaterialTheme.typography.bodyLarge) }
                item { Text("备注", style = MaterialTheme.typography.titleSmall); Spacer(Modifier.height(6.dp)); Text(r.values.note.ifEmpty { "无备注" }, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (futureRules.isNotEmpty()) item {
                    OutlinedCard { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("已安排的未来规则", style = MaterialTheme.typography.titleSmall)
                        futureRules.forEach { Text("${it.effectiveFrom}起 · ${it.values.title}\n${Money.format(it.values.amount, it.values.currency)} · ${it.cycle.label()}\n锚点 ${it.anchor}", style = MaterialTheme.typography.bodyMedium) }
                    } }
                }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (sub.disabledAfter == null) OutlinedButton(onClick = { stop = true }) { Text("停用") }
                        else { OutlinedButton(onClick = { resume = true }) { Text("重新启用") }; TextButton(onClick = { stopDate = maxOf(today, date(sub.disabledAfter)).toString(); stop = true }) { Text("修改停用日期") } }
                        OutlinedButton(onClick = { archive = true }) { Text(if (sub.archived) "取消归档" else "归档") }
                        TextButton(onClick = { delete = true }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("彻底删除") }
                    }
                }
                item { HorizontalDivider() }
                item {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = history, onClick = { history = true }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("历史账单") }
                        SegmentedButton(selected = !history, onClick = { history = false }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("未来账单") }
                    }
                }
                if (bills.isEmpty()) item { EmptyState(if (history) "暂无历史账单" else "暂无已生成的未来账单", "查看其他月份时会按需生成账单。") }
                items(bills, key = { it.bill.id }) { b -> BillCard(b, today, { onBill(b) }) }
            }
        }
    }
    if (sub == null) return
    if (stop) {
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(onDismissRequest = { stop = false }, title = { Text("设置停用日期") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                DateField("最后保留扣款日期", date(stopDate), { stopDate = it.toString() })
                Text("$stopDate 当天及以前的账单保留。之后不再生成新账单，已有的未来未付账单将移除；所有付款记录保留。")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        }, confirmButton = { TextButton(onClick = {
            if (date(stopDate) < today) error = "停用日期不能早于今天"
            else change("已设置停用日期", { stop = false }) { Ledger.disable(it, id, date(stopDate)) }
        }) { Text("确认停用") } }, dismissButton = { TextButton(onClick = { stop = false }) { Text("取消") } })
    }
    if (resume) ConfirmDialog("重新启用订阅", "将恢复从记录起始日期计算的周期账单，包括停用期间的账单，均不假定已付。原付款记录保留。", "启用", { resume = false }) { change("订阅已启用", { resume = false }) { Ledger.disable(it, id, null) } }
    if (archive) ConfirmDialog(if (sub.archived) "取消归档" else "归档订阅", if (sub.archived) "订阅将重新显示在列表中。" else "归档仅收起订阅，保留全部历史。归档不会停止扣款账单；如不再使用，请先设置停用日期。", if (sub.archived) "取消归档" else "归档", { archive = false }) { change("归档状态已更新", { archive = false }) { Ledger.archive(it, id, !sub.archived) } }
    if (delete) ConfirmDialog("彻底删除订阅？", "会同时永久删除该订阅、全部默认规则、历史账单、单期修改和付款记录。此操作无法撤销。建议先在设置中导出备份。", "删除全部相关记录", { delete = false }) { change("订阅及相关历史已删除", back) { Ledger.delete(it, id) } }
}
