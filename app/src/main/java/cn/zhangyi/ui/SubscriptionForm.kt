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

typealias ChangeAction = (String, () -> Unit, (LedgerData) -> LedgerData) -> Unit

private fun preset(c: Cycle): String = when (c) { Cycle(PeriodUnit.WEEK, 1) -> "week"; Cycle(PeriodUnit.MONTH, 1) -> "month"; Cycle(PeriodUnit.MONTH, 3) -> "quarter"; Cycle(PeriodUnit.MONTH, 6) -> "half"; Cycle(PeriodUnit.YEAR, 1) -> "year"; else -> "custom" }

@Composable
fun SubscriptionForm(data: LedgerData, prefs: Preferences, id: String?, today: LocalDate, busy: Boolean, back: () -> Unit, change: ChangeAction) {
    val sub = data.subscriptions.firstOrNull { it.id == id }
    val initialRule = remember(id) { sub?.let { displayRule(data, it.id, today) } }
    val initial = remember(id) { initialRule?.values ?: Values("", 0, prefs.currency, color = Palette.choose(data, today)) }
    var title by rememberSaveable(id) { mutableStateOf(initial.title) }
    var amount by rememberSaveable(id) { mutableStateOf(if (id == null) "" else Money.input(initial.amount, initial.currency)) }
    var currency by rememberSaveable(id) { mutableStateOf(initial.currency) }
    var note by rememberSaveable(id) { mutableStateOf(initial.note) }
    var color by rememberSaveable(id) { mutableIntStateOf(initial.color) }
    var cycleType by rememberSaveable(id) { mutableStateOf(preset(initialRule?.cycle ?: Cycle())) }
    var customUnit by rememberSaveable(id) { mutableStateOf(initialRule?.cycle?.unit?.name ?: "MONTH") }
    var customCount by rememberSaveable(id) { mutableStateOf(initialRule?.cycle?.every?.toString() ?: "1") }
    var anchorText by rememberSaveable(id) { mutableStateOf(initialRule?.anchor ?: today.toString()) }
    var effectiveText by rememberSaveable(id) { mutableStateOf(today.toString()) }
    var enabled by rememberSaveable(id) { mutableStateOf(sub?.disabledAfter == null) }
    var stopText by rememberSaveable(id) { mutableStateOf(sub?.disabledAfter ?: today.toString()) }
    var fromFirst by rememberSaveable(id) { mutableStateOf(false) }
    var error by rememberSaveable(id) { mutableStateOf<String?>(null) }
    var confirmSave by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val cycle = when (cycleType) { "week" -> Cycle(PeriodUnit.WEEK); "month" -> Cycle(); "quarter" -> Cycle(PeriodUnit.MONTH, 3); "half" -> Cycle(PeriodUnit.MONTH, 6); "year" -> Cycle(PeriodUnit.YEAR); else -> Cycle(PeriodUnit.valueOf(customUnit), customCount.toIntOrNull() ?: 0) }
    val scheduleChanged = initialRule != null && (cycle != initialRule.cycle || anchorText != initialRule.anchor)
    val dirty = title != initial.title || amount != (if (id == null) "" else Money.input(initial.amount, initial.currency)) || note != initial.note || currency != initial.currency || color != initial.color || cycle != (initialRule?.cycle ?: Cycle()) || anchorText != (initialRule?.anchor ?: today.toString()) || enabled != (sub?.disabledAfter == null) || stopText != (sub?.disabledAfter ?: today.toString())
    val requestBack = { if (dirty) confirmExit = true else back() }
    BackHandler(dirty, requestBack)
    fun parsed(): Values {
        require(title.trim().isNotBlank()) { "请输入订阅名称" }
        require(cycle.every in 1..10000) { "自定义周期N必须为1至10000的整数" }
        if (scheduleChanged) require(date(effectiveText) >= today) { "新周期只能从今天或未来生效" }
        if (sub != null && enabled.not() && stopText != sub.disabledAfter) require(date(stopText) >= today) { "停用日期不能早于今天" }
        return Values(title.trim(), Money.parse(amount, currency), currency, note, color).also(Ledger::validateValues)
    }
    fun save() {
        val values = runCatching { parsed() }.getOrElse { error = it.message ?: "请检查输入"; return }
        change(if (id == null) "订阅已创建" else "默认规则已更新", back) { d ->
            if (id == null) Ledger.add(d, values, cycle, date(anchorText), fromFirst, enabled, today)
            else {
                var next = Ledger.editSubscription(d, id, Fields.diff(initial, values), currency, cycle, date(anchorText), if (scheduleChanged) date(effectiveText) else today, scheduleChanged)
                if (enabled && sub?.disabledAfter != null) next = Ledger.disable(next, id, null)
                else if (!enabled && (sub?.disabledAfter == null || sub.disabledAfter != stopText)) next = Ledger.disable(next, id, date(stopText))
                next
            }
        }
    }
    Scaffold(topBar = { PageBar(if (id == null) "新增订阅" else "修改默认规则", requestBack) }, contentWindowInsets = PageInsets) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (id != null) Text("默认规则从今天起生效。过去未付账单、所有已付记录和单期设置将保留。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(title, { title = it; error = null }, label = { Text("名称 *") }, singleLine = true, modifier = Modifier.fillMaxWidth(), isError = error != null && title.isBlank())
            OutlinedTextField(amount, { amount = it; error = null }, label = { Text("每周期金额 *") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth(), supportingText = { Text("精确到${Money.digits(currency)}位小数，必须大于0") })
            ChoiceField("币种", currency, Money.currencies.map { it to if (it == "CNY") "CNY 人民币" else it }) { currency = it }
            ChoiceField("扣款周期", cycleType, listOf("week" to "每周", "month" to "每月", "quarter" to "每季度", "half" to "每半年", "year" to "每年", "custom" to "自定义")) { cycleType = it }
            if (cycleType == "custom") {
                OutlinedTextField(customCount, { customCount = it }, label = { Text("每N个单位（1–10000）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                ChoiceField("周期单位", customUnit, listOf("DAY" to "天", "WEEK" to "周", "MONTH" to "个月", "YEAR" to "年")) { customUnit = it }
            }
            DateField(if (id == null) "首次扣款日期 *" else "周期锚点", date(anchorText), { anchorText = it.toString() })
            if (id == null && date(anchorText) < today) {
                Text("首次日期在过去，选择记录范围", style = MaterialTheme.typography.titleSmall)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = !fromFirst, onClick = { fromFirst = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("从今天起记录") }
                    SegmentedButton(selected = fromFirst, onClick = { fromFirst = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("从首次日期记录") }
                }
                Text(if (fromFirst) "将生成此前的账单，均视为未付，不假定已经付款。" else "不生成今天之前的账单，仍保留首次日期作为周期锚点。", style = MaterialTheme.typography.bodySmall)
            }
            if (scheduleChanged) {
                DateField("新周期生效日期", date(effectiveText), { effectiveText = it.toString() })
                Text("从生效日期起按新锚点重排未付账单。更早账单和全部已付记录保留；被替代的未来单期设置不转移到新日期。此次默认信息修改也从该日期生效。", style = MaterialTheme.typography.bodySmall)
            }
            ColorPicker(color) { color = it }
            OutlinedTextField(note, { note = it }, label = { Text("备注（可选）") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            ListItem(headlineContent = { Text("启用订阅") }, supportingContent = { Text("启用后生成周期账单，付款始终手动确认") }, trailingContent = { Switch(checked = enabled, onCheckedChange = { enabled = it }) })
            if (!enabled && id != null) {
                DateField("最后保留扣款日期", date(stopText), { stopText = it.toString() })
                Text("该日期之后停止生成新账单；此前账单和全部付款记录保留。", style = MaterialTheme.typography.bodySmall)
            }
            if (!enabled && id == null) Text("保存为停用状态：今天之后不生成账单。", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = { if (runCatching { parsed() }.onFailure { error = it.message ?: "请检查输入" }.isSuccess) { if (id == null) save() else confirmSave = true } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("保存订阅") }
            Spacer(Modifier.height(16.dp))
        }
    }
    if (confirmSave) {
        val since = if (scheduleChanged) effectiveText else today.toString()
        val paid = data.payments.map { it.billId }.toSet()
        val count = data.bills.count { it.subscriptionId == id && it.due >= since && it.id !in paid && !it.retired }
        ConfirmDialog(if (scheduleChanged) "确认新周期与锚点" else "更新默认规则", "生效日期：$since\n${if (scheduleChanged) "新周期：${cycle.label()}\n新锚点：$anchorText\n" else ""}当前范围内有 $count 笔未付账单；包含之后新生成的账单。\n已付账单和更早账单保持原样。${if (scheduleChanged) "未来原周期未付账单将被替代。" else "已有单期覆盖继续保留。"}", "保存", { confirmSave = false }) { confirmSave = false; save() }
    }
    if (confirmExit) ConfirmDialog("放弃未保存的修改？", "退出后，本次输入不会保存。", "放弃修改", { confirmExit = false }, back)
}
