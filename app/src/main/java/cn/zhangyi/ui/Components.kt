@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cn.zhangyi.domain.*
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

val MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)
val PageInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)

@Composable
fun PageBar(title: String, back: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title) }, navigationIcon = { if (back != null) IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") } }, actions = actions)
}
@Composable
fun EmptyState(title: String, detail: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) FilledTonalButton(onClick = onAction) { Text(action) }
    }
}
@Composable
fun ColorDot(color: Int, modifier: Modifier = Modifier) { Box(modifier.size(10.dp).background(Color(color), CircleShape)) }

@Composable
fun BillCard(view: BillView, today: LocalDate, onClick: () -> Unit, onPay: (() -> Unit)? = null) {
    OutlinedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorDot(view.values.color)
                Text(view.values.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            }
            Text(Money.format(view.values.amount, view.values.currency), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            FlowRow(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(view.bill.due, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(view.status(today), style = MaterialTheme.typography.labelLarge, color = if (view.status(today) == "逾期") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                if (view.bill.overrides.any) Text("单独修改", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (onPay != null) TextButton(onClick = onPay) { Text(if (view.payment != null) "撤销已付" else "标记已付") }
            }
            if (view.payment != null && view.payment.actualAmount != view.values.amount) Text("实付 ${Money.format(view.payment.actualAmount, view.values.currency)} · 差额 ${Money.input(view.payment.actualAmount - view.values.amount, view.values.currency)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
@Composable
fun SummaryCard(title: String, rows: List<Totals>, compact: Boolean = false) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (rows.isEmpty()) Text("暂无账单", color = MaterialTheme.colorScheme.onSurfaceVariant)
            rows.forEach { t ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(Money.format(t.due, t.currency), style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (!compact) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column { Text("已付（实付）", style = MaterialTheme.typography.labelMedium); Text(Money.format(t.paid, t.currency)) }
                            Column { Text("待付（应付）", style = MaterialTheme.typography.labelMedium); Text(Money.format(t.pending, t.currency)) }
                        }
                        if (t.difference != 0L) Text("已付账单实付与应付差额：${Money.format(t.difference, t.currency)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun DateField(label: String, value: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier.fillMaxWidth().heightIn(min = 56.dp)) {
        Icon(Icons.Outlined.DateRange, null)
        Spacer(Modifier.width(12.dp))
        Text("$label：$value", Modifier.weight(1f))
    }
    if (open) {
        // Material date picker values are UTC midnight; convert explicitly to calendar dates.
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), yearRange = 1900..2199)
        DatePickerDialog(onDismissRequest = { open = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }; open = false }, enabled = state.selectedDateMillis != null) { Text("确定") }
        }, dismissButton = { TextButton(onClick = { open = false }) { Text("取消") } }) { DatePicker(state = state) }
    }
}

@Composable
fun ChoiceField(label: String, value: String, options: List<Pair<String, String>>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(value = options.firstOrNull { it.first == value }?.second ?: value, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }, modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable))
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onChange(key); expanded = false }) }
        }
    }
}
@Composable
fun ColorPicker(selected: Int, onChange: (Int) -> Unit) {
    Text("颜色标签", style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Palette.colors.forEachIndexed { i, c ->
            IconToggleButton(checked = c == selected, onCheckedChange = { onChange(c) }, modifier = Modifier.semantics { contentDescription = "颜色标签${i + 1}"; stateDescription = if (c == selected) "已选择" else "未选择" }) {
                Box(Modifier.size(32.dp).background(Color(c), CircleShape), contentAlignment = Alignment.Center) {
                    if (c == selected) Icon(Icons.Outlined.CheckCircle, null, tint = Color.White, modifier = Modifier.size(24.dp))

                }
            }
        }
    }
}
@Composable
fun ConfirmDialog(title: String, message: String, confirm: String = "确定", onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message, Modifier.verticalScroll(rememberScrollState())) }, confirmButton = { TextButton(onClick = onConfirm) { Text(confirm) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
