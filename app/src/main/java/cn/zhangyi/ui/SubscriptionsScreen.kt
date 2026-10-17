@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.LocalDate

fun displayRule(data: LedgerData, id: String, today: LocalDate): Rule = Ledger.rules(data, id).let { rows -> rows.lastOrNull { it.effectiveFrom <= today.toString() } ?: rows.first() }
fun subStatus(s: Subscription, today: LocalDate): String = when { s.archived -> "已归档"; s.disabledAfter == null -> "启用"; date(s.disabledAfter) < today -> "已停用"; else -> "将于${s.disabledAfter}后停用" }
fun nextDue(data: LedgerData, s: Subscription, today: LocalDate): LocalDate? {
    val end = minOf(today.plusYears(101), s.disabledAfter?.let(::date) ?: today.plusYears(101))
    val versions = Ledger.rules(data, s.id)
    val segments = versions.filterIndexed { i, r -> i == 0 || r.scheduleId != versions[i - 1].scheduleId }
    return segments.mapIndexedNotNull { i, r ->
        val from = maxOf(today, date(s.recordFrom), date(r.effectiveFrom))
        val through = minOf(end, segments.getOrNull(i + 1)?.effectiveFrom?.let { date(it).minusDays(1) } ?: end)
        r.cycle.next(date(r.anchor), from).takeIf { it <= through }
    }.minOrNull()
}
@Composable
fun SubscriptionsScreen(data: LedgerData, views: List<BillView>, today: LocalDate, onAdd: () -> Unit, onDetail: (String) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var archived by rememberSaveable { mutableStateOf(false) }
    val list = remember(data, search, archived, today) { data.subscriptions.filter { s -> s.archived == archived && displayRule(data, s.id, today).values.title.contains(search, true) }.sortedWith(compareBy({ it.disabledAfter != null && date(it.disabledAfter) < today }, { displayRule(data, it.id, today).values.title })) }
    Scaffold(topBar = { PageBar("订阅", actions = { TextButton(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Text("新增") } }) }, floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, text = { Text("新增订阅") }, icon = { Icon(Icons.Outlined.Add, null) }) }, contentWindowInsets = PageInsets) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("搜索订阅名称") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { FilterChip(selected = !archived, onClick = { archived = false }, label = { Text("全部订阅") }); FilterChip(selected = archived, onClick = { archived = true }, label = { Text("已归档") }) } }
            if (list.isEmpty()) item { EmptyState(if (search.isNotEmpty()) "没有匹配的订阅" else if (archived) "暂无归档订阅" else "让每笔订阅都心中有数", "记录订阅后，即可查看每月账单。", if (!archived && search.isEmpty()) "新增订阅" else null, onAdd) }
            items(list, key = { it.id }) { s ->
                val r = displayRule(data, s.id, today)
                val next = remember(data, s, today) { nextDue(data, s, today) }
                OutlinedCard(onClick = { onDetail(s.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { ColorDot(r.values.color); Text(r.values.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium); Text(subStatus(s, today), style = MaterialTheme.typography.labelMedium) }
                        Text("${Money.format(r.values.amount, r.values.currency)} / ${r.cycle.label()}", style = MaterialTheme.typography.bodyLarge)
                        Text("下次扣款：${next?.toString() ?: "已停止或超出预览范围"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
