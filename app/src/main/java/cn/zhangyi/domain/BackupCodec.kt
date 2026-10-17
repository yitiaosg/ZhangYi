package cn.zhangyi.domain

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDate

object BackupCodec {
    const val MAX_BYTES = 32 * 1024 * 1024
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false; explicitNulls = true }
    fun encode(data: LedgerData, settings: Preferences): String {
        val text = json.encodeToString(Backup(exportedAt = Instant.now().toString(), data = data, settings = settings))
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "账本超过32 MiB备份上限，无法导出可恢复文件" }
        return text
    }
    fun decode(text: String): Backup {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "备份不能超过32 MiB" }
        val root = json.parseToJsonElement(text).jsonObject
        require(root.keys == setOf("formatVersion", "appVersion", "exportedAt", "data", "settings")) { "备份头字段缺失或不受支持" }
        val payload = root.getValue("data").jsonObject
        require(payload.keys == setOf("subscriptions", "rules", "bills", "payments")) { "账本字段缺失" }
        require(root.getValue("settings").jsonObject.keys == setOf("theme", "dynamicColor", "currency")) { "设置字段缺失" }
        fun fields(key: String, expected: Set<String>) { payload.getValue(key).jsonArray.forEach { require(it.jsonObject.keys == expected) { "$key 记录字段缺失或不受支持" } } }
        fields("subscriptions", setOf("id", "recordFrom", "disabledAfter", "archived", "createdAt"))
        fields("rules", setOf("id", "subscriptionId", "effectiveFrom", "values", "cycle", "anchor", "scheduleId"))
        fields("bills", setOf("id", "subscriptionId", "scheduleId", "due", "overrides", "snapshot", "retired"))
        fields("payments", setOf("billId", "paidOn", "actualAmount"))
        fun valuesFields(element: JsonElement) { require(element.jsonObject.keys == setOf("title", "amount", "currency", "note", "color")) { "账单信息字段缺失" } }
        payload.getValue("rules").jsonArray.forEach { row ->
            valuesFields(row.jsonObject.getValue("values"))
            require(row.jsonObject.getValue("cycle").jsonObject.keys == setOf("unit", "every")) { "周期字段缺失" }
        }
        payload.getValue("bills").jsonArray.forEach { row ->
            require(row.jsonObject.getValue("overrides").jsonObject.keys == setOf("title", "amount", "note", "noteSet", "color")) { "单期设置字段缺失" }
            row.jsonObject.getValue("snapshot").takeIf { it != JsonNull }?.let(::valuesFields)
        }
        val backup = json.decodeFromJsonElement<Backup>(root)
        validate(backup)
        return backup
    }
    fun validate(b: Backup) {
        require(b.formatVersion == 1) { "备份版本不兼容，仅支持格式1" }
        require(b.appVersion.isNotBlank() && b.appVersion.length < 100)
        Instant.parse(b.exportedAt)
        require(b.settings.theme in listOf("SYSTEM", "LIGHT", "DARK")) { "主题设置无效" }
        require(b.settings.currency in Money.currencies) { "默认币种无效" }
        val d = b.data
        require(d.subscriptions.size <= 10000 && d.rules.size <= 100000 && d.bills.size <= 250000 && d.payments.size <= 250000) { "数据数量超出安全上限" }
        fun unique(ids: List<String>) { require(ids.size == ids.toSet().size && ids.none { it.isBlank() || it.length > 240 }) { "标识重复或无效" } }
        fun validDate(s: String): LocalDate = date(s).also { require(it.year in 1900..2400 && it.toString() == s) { "日期无效" } }
        unique(d.subscriptions.map { it.id }); unique(d.rules.map { it.id }); unique(d.bills.map { it.id }); unique(d.payments.map { it.billId })
        val subs = d.subscriptions.associateBy { it.id }
        val rules = d.rules.groupBy { it.subscriptionId }
        val bills = d.bills.associateBy { it.id }
        val payments = d.payments.associateBy { it.billId }
        d.subscriptions.forEach { s ->
            validDate(s.recordFrom); validDate(s.createdAt); s.disabledAfter?.let(::validDate)
            val versions = rules[s.id].orEmpty()
            require(versions.isNotEmpty()) { "订阅缺少默认规则" }
            unique(versions.map { it.effectiveFrom })
            require(versions.minOf { it.effectiveFrom } <= s.recordFrom) { "规则未覆盖记录起始日期" }
            // A schedule can have multiple price versions, but its anchor and interval are immutable.
            versions.groupBy { it.scheduleId }.forEach { (_, rows) -> require(rows.map { it.anchor to it.cycle }.distinct().size == 1) { "同一账期锚点冲突" } }
            val segments = versions.sortedBy { it.effectiveFrom }.map { it.scheduleId }.fold(mutableListOf<String>()) { acc, id -> acc.apply { if (lastOrNull() != id) add(id) } }
            require(segments.distinct().size == segments.size) { "账期版本顺序冲突" }
        }
        d.rules.forEach { r ->
            require(r.subscriptionId in subs) { "规则关联不存在" }
            require(r.scheduleId.isNotBlank() && r.scheduleId.length <= 100)
            validDate(r.effectiveFrom); validDate(r.anchor)
            require(r.cycle.every in 1..10000) { "周期必须为正整数且不超过10000" }
            Ledger.validateValues(r.values)
        }
        d.bills.forEach { bill ->
            val sub = subs[bill.subscriptionId] ?: error("账单订阅关联不存在")
            validDate(bill.due)
            require(bill.due >= sub.recordFrom) { "账单早于记录起始日期" }
            require(bill.id == "${bill.subscriptionId}:${bill.scheduleId}:${bill.due}") { "账单标识不匹配" }
            // Old retired schedule IDs can remain after a cycle change, preserving provenance.
            require(bill.scheduleId.isNotBlank() && bill.scheduleId.length <= 100)
            Ledger.validateValues(Ledger.defaultValues(d, bill))
            val p = bill.overrides
            p.title?.let { require(it.isNotBlank() && it.length <= 120) }
            p.amount?.let { require(it in 1..Money.MAX) }
            require(p.note.length <= 5000 && (p.noteSet || p.note.isEmpty()))
            p.color?.let { require(it in Palette.colors) }
            bill.snapshot?.let(Ledger::validateValues)
            require((bill.snapshot != null) == (bill.id in payments)) { "付款记录和信息快照不一致" }
            require(!bill.retired || bill.id !in payments) { "已付账单不能被替代" }
            if (!bill.retired && bill.id !in payments) {
                val r = Ledger.ruleAt(d, bill.subscriptionId, bill.due)
                require(r.scheduleId == bill.scheduleId) { "账单周期关联无效" }
                require(r.cycle.dates(date(r.anchor), date(bill.due), date(bill.due)).isNotEmpty()) { "扣款日期不符合周期" }
                require(sub.disabledAfter == null || bill.due <= sub.disabledAfter) { "停用后存在未付账单" }
            }
        }
        d.payments.forEach { p ->
            require(p.billId in bills) { "付款账单关联不存在" }
            validDate(p.paidOn)
            require(p.actualAmount in 0..Money.MAX) { "实际金额无效" }
        }
        // Ensure aggregates cannot overflow before committing an import.
        Ledger.totals(Ledger.views(d))
    }
}
