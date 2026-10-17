@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.LocalDate

@Composable
fun PaymentDialog(view: BillView, today: LocalDate, busy: Boolean, dismiss: () -> Unit, save: (LocalDate, Long) -> Unit) {
    var amount by rememberSaveable(view.bill.id) { mutableStateOf(Money.input(view.payment?.actualAmount ?: view.values.amount, view.values.currency)) }
    var paidDate by rememberSaveable(view.bill.id) { mutableStateOf(view.payment?.paidOn ?: today.toString()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (view.payment == null) "确认付款" else "编辑付款记录") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("${view.values.title}\n到期：${view.bill.due}\n当期应付：${Money.format(view.values.amount, view.values.currency)}")
            OutlinedTextField(amount, { amount = it; error = null }, label = { Text("实际支付金额（${view.values.currency}）") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), supportingText = { Text("可为0；不会修改当期应付金额") })
            DateField("实际支付日期", date(paidDate), { paidDate = it.toString() })
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(enabled = !busy, onClick = {
        runCatching { require(date(paidDate) <= today) { "实际支付日期不能晚于今天" }; Money.parse(amount, view.values.currency, allowZero = true) }.onSuccess { save(date(paidDate), it) }.onFailure { error = it.message ?: "金额格式错误" }
    }) { Text("保存付款") } }, dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}

@Composable
fun BillDetail(data: LedgerData, views: List<BillView>, id: String, today: LocalDate, back: () -> Unit, edit: (String) -> Unit, editPayment: (BillView) -> Unit, pay: (BillView) -> Unit, change: ChangeAction) {
    val view = views.firstOrNull { it.bill.id == id }
    var reset by remember { mutableStateOf(false) }
    Scaffold(topBar = { PageBar("账单详情", back, actions = { if (view != null) TextButton(onClick = { edit(id) }) { Text("编辑本期") } }) }, contentWindowInsets = PageInsets) { padding ->
        if (view == null) EmptyState("账单已不存在", "可能已因停用或修改周期移除。", "返回", back)
        else Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { ColorDot(view.values.color); Text(view.values.title, style = MaterialTheme.typography.headlineSmall) }
            Text(Money.format(view.values.amount, view.values.currency), style = MaterialTheme.typography.headlineLarge)
            Text("到期日期：${view.bill.due}\n状态：${view.status(today)}", style = MaterialTheme.typography.bodyLarge)
            if (view.bill.overrides.any) {
                Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("本期存在单独修改", style = MaterialTheme.typography.titleSmall)
                    Text(buildList { if (view.bill.overrides.title != null) add("标题"); if (view.bill.overrides.amount != null) add("应付价格"); if (view.bill.overrides.noteSet) add("备注"); if (view.bill.overrides.color != null) add("颜色") }.joinToString("、"))
                    TextButton(onClick = { reset = true }) { Text("恢复该期默认值") }
                } }
            }
            if (view.payment != null) {
                SummaryCard("付款记录", listOf(Totals(view.values.currency, view.values.amount, view.payment.actualAmount, 0, view.values.amount)))
                Text("实际支付日期：${view.payment.paidOn}")
                Text("应付金额与实付金额独立保存；本月已付统计使用实际支付金额。", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { editPayment(view) }, modifier = Modifier.fillMaxWidth()) { Text("编辑付款记录") }
            }
            Text("备注", style = MaterialTheme.typography.titleSmall)
            Text(view.values.note.ifEmpty { "无备注" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { pay(view) }, modifier = Modifier.fillMaxWidth()) { Text(if (view.payment != null) "撤销已付" else "标记已付") }
        }
    }
    if (reset && view != null) {
        val base = Ledger.defaultValues(data, view.bill).copy(currency = view.values.currency)
        ConfirmDialog("恢复该期默认值", "标题：${view.values.title} → ${base.title}\n应付：${Money.format(view.values.amount, view.values.currency)} → ${Money.format(base.amount, base.currency)}\n备注：${view.values.note.ifEmpty { "（空）" }} → ${base.note.ifEmpty { "（空）" }}\n颜色：标签${Palette.colors.indexOf(view.values.color) + 1} → 标签${Palette.colors.indexOf(base.color) + 1}\n\n将清除本期全部覆盖设置。${if (view.payment != null) "此操作会更新该笔已付账单的信息快照，实际付款金额和日期保持不变。" else "其他账单不受影响。"}", "恢复默认值", { reset = false }) {
            change("本期已恢复默认值", { reset = false }) { Ledger.reset(it, id) }
        }
    }
}

@Composable
fun BillEditScreen(data: LedgerData, views: List<BillView>, id: String, today: LocalDate, busy: Boolean, back: () -> Unit, change: ChangeAction) {
    val view = views.firstOrNull { it.bill.id == id }
    if (view == null) { Scaffold(topBar = { PageBar("编辑本期账单", back) }) { padding -> Box(Modifier.padding(padding)) { EmptyState("找不到这笔账单", "返回后重试") } }; return }
    val initial = remember(id) { view.values }
    var title by rememberSaveable(id) { mutableStateOf(initial.title) }
    var amount by rememberSaveable(id) { mutableStateOf(Money.input(initial.amount, initial.currency)) }
    var note by rememberSaveable(id) { mutableStateOf(initial.note) }
    var color by rememberSaveable(id) { mutableIntStateOf(initial.color) }
    var forward by rememberSaveable(id) { mutableStateOf(false) }
    var overwrite by rememberSaveable(id) { mutableStateOf(false) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf(false) }
    var exit by remember { mutableStateOf(false) }
    val allowForward = view.payment == null && date(view.bill.due) > today
    val changed = Fields.diff(initial, initial.copy(title = title.trim(), amount = runCatching { Money.parse(amount, initial.currency) }.getOrDefault(initial.amount), note = note, color = color))
    val dirty = changed.any || amount != Money.input(initial.amount, initial.currency)
    val requestBack = { if (dirty) exit = true else back() }
    BackHandler(dirty, requestBack)
    val info = if (allowForward) Ledger.forwardInfo(data, id, changed, today) else (1 to 0)
    val affected = if (allowForward) Ledger.forwardAffectedCount(data, id, changed, overwrite, today) else 1
    Scaffold(topBar = { PageBar("编辑本期账单", requestBack) }, contentWindowInsets = PageInsets) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("到期：${view.bill.due} · ${view.status(today)}\n本期编辑不会改变扣款日期或周期锚点。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it }, label = { Text("本期标题 *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(amount, { amount = it }, label = { Text("当期应付金额（${initial.currency}）*") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            if (view.payment != null) Text("实付 ${Money.format(view.payment.actualAmount, initial.currency)} 将保持不变。实际金额请在付款记录中修改。", style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(note, { note = it }, label = { Text("本期备注（可清空）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            ColorPicker(color) { color = it }
            HorizontalDivider()
            Text("应用范围", style = MaterialTheme.typography.titleMedium)
            ListItem(headlineContent = { Text("仅应用当前账单") }, supportingContent = { Text("修改1笔，保留订阅默认规则") }, leadingContent = { RadioButton(selected = !forward || !allowForward, onClick = { forward = false }) }, modifier = Modifier.fillMaxWidth())
            if (allowForward) {
                ListItem(headlineContent = { Text("应用当前及此后所有账单") }, supportingContent = { Text("从${view.bill.due}起，范围${info.first}笔，实际改变${affected}笔已生成账单；包含之后新生成的账单") }, leadingContent = { RadioButton(selected = forward, onClick = { forward = true }) })
                if (forward) {
                    Text("仅向后应用本次实际修改的字段。更早账单、已付账单及未修改的字段保持原样。", style = MaterialTheme.typography.bodySmall)
                    if (info.second > 0) {
                        Text("后续${info.second}笔账单含冲突的单期设置", color = MaterialTheme.colorScheme.error)
                        ChoiceField("冲突处理", if (overwrite) "overwrite" else "keep", listOf("keep" to "保留单期设置（默认）", "overwrite" to "覆盖后续单期设置")) { overwrite = it == "overwrite" }
                    }
                }
            } else Text("到期日在今天或过去，或已经支付的账单，只能修改本期。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = dirty && !busy, onClick = {
                runCatching { Ledger.validateValues(initial.copy(title = title.trim(), amount = Money.parse(amount, initial.currency), note = note, color = color)) }.onSuccess { if (changed.any) confirm = true else error = "没有实际修改" }.onFailure { error = it.message ?: "请检查输入" }
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("保存修改") }
        }
    }
    if (confirm) {
        val fields = buildList { if (changed.title != null) add("标题"); if (changed.amount != null) add("应付价格"); if (changed.noteSet) add("备注"); if (changed.color != null) add("颜色") }.joinToString("、")
        ConfirmDialog("确认应用范围", "修改字段：$fields\n${if (forward && allowForward) "从${view.bill.due}起，范围内${info.first}笔已生成的未付账单，实际改变${affected}笔，包含之后新生成的账单。\n${if (overwrite) "覆盖后续冲突字段的单期设置。" else "保留后续已有单期设置。"}" else "仅应用当前账单（1笔）。"}${if (view.payment != null) "\n实际支付金额和日期保持不变。" else ""}", "保存", { confirm = false }) {
            change("账单修改已保存", back) { if (forward && allowForward) Ledger.forward(it, id, changed, overwrite, today) else Ledger.editOne(it, id, changed) }
        }
    }
    if (exit) ConfirmDialog("放弃未保存的修改？", "本次输入将不会保存。", "放弃修改", { exit = false }, back)
}
