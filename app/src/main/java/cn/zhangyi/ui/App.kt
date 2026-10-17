@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package cn.zhangyi.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import cn.zhangyi.domain.*
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun ZhangYiApp(vm: LedgerViewModel) {
    val data by vm.data.collectAsStateWithLifecycle()
    val prefs by vm.settings.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val fatal by vm.fatalError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) { today = LocalDate.now(); vm.refresh() } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); val now = LocalDate.now(); if (today != now) { today = now; vm.refresh() } } }
    val views = remember(data) { Ledger.views(data) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "dashboard"
    val roots = listOf("dashboard", "calendar", "subscriptions", "settings")
    var payId by remember { mutableStateOf<String?>(null) }
    var undoId by remember { mutableStateOf<String?>(null) }
    val onPay: (BillView) -> Unit = { if (it.payment == null) payId = it.bill.id else undoId = it.bill.id }
    val onBill: (BillView) -> Unit = { nav.navigate("bill/${it.bill.id}") }
    ZhangYiTheme(prefs) {
        Scaffold(contentWindowInsets = WindowInsets(0), snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            if (route in roots) NavigationBar {
                val labels = listOf("看板", "日历", "订阅", "设置")
                val icons = listOf(Icons.Outlined.Home, Icons.Outlined.DateRange, Icons.AutoMirrored.Outlined.List, Icons.Outlined.Settings)
                roots.forEachIndexed { i, r -> NavigationBarItem(selected = route == r, onClick = {
                    nav.navigate(r) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true }
                }, icon = { Icon(icons[i], labels[i]) }, label = { Text(labels[i]) }) }
            }
        }) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (!ready || fatal != null) {
                    Scaffold(topBar = { PageBar("账易") }, contentWindowInsets = PageInsets) { inset ->
                        Column(Modifier.padding(inset)) {
                            if (fatal == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); EmptyState("正在读取本地账本", "数据仅保存在设备上") }
                            else EmptyState("账本暂时无法打开", fatal ?: "", "重试", vm::refresh)
                        }
                    }
                } else NavHost(nav, startDestination = "dashboard") {
                    composable("dashboard") { DashboardScreen(views, today, { nav.navigate("new") }, onBill, onPay) }
                    composable("calendar") { CalendarScreen(views, today, vm::ensureMonth, onBill, onPay) }
                    composable("subscriptions") { SubscriptionsScreen(data, views, today, { nav.navigate("new") }, { nav.navigate("sub/$it") }) }
                    composable("settings") { SettingsScreen(vm, prefs, data) }
                    composable("new") { SubscriptionForm(data, prefs, null, today, busy, { nav.popBackStack() }, vm::change) }
                    composable("edit/{id}") { e -> SubscriptionForm(data, prefs, e.arguments?.getString("id"), today, busy, { nav.popBackStack() }, vm::change) }
                    composable("sub/{id}") { e -> SubscriptionDetail(data, views, e.arguments?.getString("id") ?: "", today, { nav.popBackStack() }, { nav.navigate("edit/$it") }, onBill, vm::change) }
                    composable("bill/{id}") { e -> BillDetail(data, views, e.arguments?.getString("id") ?: "", today, { nav.popBackStack() }, { nav.navigate("billEdit/$it") }, { payId = it.bill.id }, onPay, vm::change) }
                    composable("billEdit/{id}") { e -> BillEditScreen(data, views, e.arguments?.getString("id") ?: "", today, busy, { nav.popBackStack() }, vm::change) }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
        views.firstOrNull { it.bill.id == payId }?.let { bill ->
            PaymentDialog(bill, today, busy, { payId = null }) { paidOn, amount ->
                vm.change(if (bill.payment == null) "已标记付款" else "付款记录已更新", { payId = null }) { Ledger.pay(it, bill.bill.id, paidOn, amount) }
            }
        }
        views.firstOrNull { it.bill.id == undoId }?.let { bill ->
            ConfirmDialog("撤销已付", "撤销“${bill.values.title}”的付款记录？该期账单将恢复为待付或逾期；若此期已因停用或周期变更失效，将同时收起。", "撤销已付", { undoId = null }) {
                vm.change("已撤销付款", { undoId = null }) { Ledger.undo(it, bill.bill.id) }
            }
        }
    }
}
