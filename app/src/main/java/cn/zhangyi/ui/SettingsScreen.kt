@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cn.zhangyi.BuildConfig
import cn.zhangyi.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun SettingsScreen(vm: LedgerViewModel, prefs: Preferences, data: LedgerData) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportWarning by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Backup?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var processing by remember { mutableStateOf(false) }
    var restoreConfirm by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            processing = true
            runCatching {
                val text = vm.export()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(text) } ?: error("无法写入所选文件") }
            }.onSuccess { vm.tell("备份已导出，请妥善保管") }.onFailure { error = "导出失败：${it.message}" }
            processing = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            processing = true
            runCatching {
                withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                        val output = java.io.ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= BackupCodec.MAX_BYTES) { "备份不能超过32 MiB" }; output.write(buffer, 0, count) }
                        output.toByteArray()
                    } ?: error("无法读取所选文件")
                    // Decode UTF-8 strictly; malformed byte sequences are rejected rather than silently replaced.
                    val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    BackupCodec.decode(decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString())
                }
            }.onSuccess { preview = it }.onFailure { error = "备份校验失败，现有数据未改变。\n${it.message?.take(250)}" }
            processing = false
        }
    }
    fun launchExport() { exportLauncher.launch("账易-backup-${LocalDate.now()}.json") }
    Scaffold(topBar = { PageBar("设置") }, contentWindowInsets = PageInsets) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("外观", style = MaterialTheme.typography.titleMedium)
            ChoiceField("主题", prefs.theme, listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色")) { vm.setPreferences(prefs.copy(theme = it)) }
            ListItem(headlineContent = { Text("动态配色") }, supportingContent = { Text(if (Build.VERSION.SDK_INT >= 31) "使用系统壁纸的 Material You 颜色" else "Android 12及以上生效，此设备使用默认配色") }, trailingContent = { Switch(prefs.dynamicColor, { vm.setPreferences(prefs.copy(dynamicColor = it)) }) })
            HorizontalDivider()
            Text("记账默认值", style = MaterialTheme.typography.titleMedium)
            ChoiceField("默认币种", prefs.currency, Money.currencies.map { it to if (it == "CNY") "CNY 人民币" else it }) { vm.setPreferences(prefs.copy(currency = it)) }
            Text("仅用于新增订阅。不同币种分别统计，不进行汇率换算。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text("数据与备份", style = MaterialTheme.typography.titleMedium)
            Text("本地账本：${data.subscriptions.size}个订阅 · ${data.bills.count { !it.retired }}笔账单 · ${data.payments.size}条付款记录")
            OutlinedButton(enabled = !processing, onClick = { exportWarning = true }, modifier = Modifier.fillMaxWidth()) { Text("导出完整JSON备份") }
            OutlinedButton(enabled = !processing, onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, modifier = Modifier.fillMaxWidth()) { Text("导入备份并完整恢复") }
            Text("导出的JSON包含账单信息且未加密，请存放在可信位置。恢复会完整替换现有数据，不进行合并。卸载应用会移除本地数据，请自行保留备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (processing) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在处理本地文件…") }
            HorizontalDivider()
            Text("关于账易", style = MaterialTheme.typography.titleMedium)
            Text("账易 ${BuildConfig.VERSION_NAME}\n简单、清爽的订阅账单管理\n\n离线使用，无账号、广告、付费订阅和云同步。不会连接银行卡或支付平台。\n\n付款由你手动确认，到期不会自动变成已付。", style = MaterialTheme.typography.bodyMedium)
            Text("Android 8.0及以上 · 本地日期账期 · 备份格式1", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
        }
    }
    if (exportWarning) ConfirmDialog("导出未加密备份", "JSON将包含完整订阅、账单、付款记录、单期修改和设置。请妥善保管，仅向可信位置导出。", "选择保存位置", { exportWarning = false }) { exportWarning = false; launchExport() }
    preview?.let { backup ->
        if (!restoreConfirm) AlertDialog(onDismissRequest = { if (!processing) preview = null }, title = { Text("备份校验通过") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("导出时间：${backup.exportedAt}\n应用版本：${backup.appVersion}\n格式版本：${backup.formatVersion}\n\n${backup.data.subscriptions.size}个订阅\n${backup.data.rules.size}个规则版本\n${backup.data.bills.size}笔账单（含被替代记录）\n${backup.data.bills.count { it.overrides.any }}笔单期设置\n${backup.data.payments.size}条付款与信息快照\n币种：${backup.data.rules.map { it.values.currency }.distinct().joinToString()}")
                Text("恢复将替换当前全部数据和设置。建议先导出当前账本；导出JSON未加密。", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { launchExport() }, enabled = !processing) { Text("先导出当前数据") }
            }
        }, confirmButton = { TextButton(onClick = { restoreConfirm = true }, enabled = !processing) { Text("继续完整恢复") } }, dismissButton = { TextButton(onClick = { preview = null }, enabled = !processing) { Text("取消") } })
        else AlertDialog(onDismissRequest = { if (!processing) restoreConfirm = false }, title = { Text("确认替换全部数据？") }, text = { Text("现有${data.subscriptions.size}个订阅和${data.payments.size}条付款记录将被备份内容完整替换。此操作无法撤销。") }, confirmButton = {
            TextButton(enabled = !processing, onClick = { scope.launch { processing = true; if (vm.restore(backup)) { preview = null; restoreConfirm = false }; processing = false } }) { Text(if (processing) "正在恢复…" else "确认替换并恢复") }
        }, dismissButton = { TextButton(onClick = { restoreConfirm = false }, enabled = !processing) { Text("返回") } })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { error = null }, title = { Text("操作未完成") }, text = { Text(message) }, confirmButton = { TextButton(onClick = { error = null }) { Text("知道了") } }) }
}
