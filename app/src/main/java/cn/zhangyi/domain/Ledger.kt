package cn.zhangyi.domain

import java.time.LocalDate
import java.time.YearMonth

object Ledger {
    fun rules(data: LedgerData, subId: String): List<Rule> = data.rules.filter { it.subscriptionId == subId }.sortedBy { it.effectiveFrom }
    fun ruleAt(data: LedgerData, subId: String, due: String): Rule = rules(data, subId).let { rows -> rows.lastOrNull { it.effectiveFrom <= due } ?: rows.firstOrNull() ?: error("账单缺少适用规则") }
    fun defaultValues(data: LedgerData, bill: Bill): Values = ruleAt(data, bill.subscriptionId, bill.due).values
    fun values(data: LedgerData, bill: Bill): Values = bill.snapshot ?: bill.overrides.apply(defaultValues(data, bill))
    fun views(data: LedgerData): List<BillView> {
        val payments = data.payments.associateBy { it.billId }
        val rules = data.rules.groupBy { it.subscriptionId }.mapValues { it.value.sortedBy { r -> r.effectiveFrom } }
        return data.bills.filter { !it.retired }.map { b ->
            val base = rules.getValue(b.subscriptionId).last { it.effectiveFrom <= b.due }.values
            BillView(b, b.snapshot ?: b.overrides.apply(base), payments[b.id])
        }.sortedWith(compareBy({ it.bill.due }, { it.values.title }, { it.bill.id }))
    }
    fun totals(bills: List<BillView>): List<Totals> = bills.groupBy { it.values.currency }.toSortedMap().map { (currency, rows) ->
        fun sum(items: List<BillView>, f: (BillView) -> Long) = items.fold(0L) { n, b -> Math.addExact(n, f(b)) }
        val paid = rows.filter { it.payment != null }
        Totals(currency, sum(rows) { it.values.amount }, sum(paid) { it.payment!!.actualAmount }, sum(rows.filter { it.payment == null }) { it.values.amount }, sum(paid) { it.values.amount })
    }
    fun month(bills: List<BillView>, month: YearMonth): List<BillView> = bills.filter { YearMonth.from(date(it.bill.due)) == month }

    fun generate(data: LedgerData, through: LocalDate): LedgerData {
        val existing = data.bills.associateBy { it.id }.toMutableMap()
        val paidIds = data.payments.map { it.billId }.toSet()
        val paidDates = data.bills.filter { it.id in paidIds }.map { it.subscriptionId to it.due }.toSet()
        for (sub in data.subscriptions) {
            val versions = rules(data, sub.id)
            if (versions.isEmpty()) continue
            val segments = versions.filterIndexed { i, r -> i == 0 || r.scheduleId != versions[i - 1].scheduleId }
            segments.forEachIndexed { index, r ->
                val from = maxOf(date(sub.recordFrom), date(r.effectiveFrom))
                val end = minOf(through, sub.disabledAfter?.let(::date) ?: through, segments.getOrNull(index + 1)?.effectiveFrom?.let { date(it).minusDays(1) } ?: through)
                for (d in r.cycle.dates(date(r.anchor), from, end)) {
                    val id = "${sub.id}:${r.scheduleId}:$d"
                    if (id !in existing && (sub.id to d.toString()) !in paidDates) existing[id] = Bill(id, sub.id, r.scheduleId, d.toString())
                }
            }
        }
        require(existing.size <= 250_000) { "账单数量超出安全上限" }
        return data.copy(bills = existing.values.toList())
    }

    fun add(data: LedgerData, values: Values, cycle: Cycle, anchor: LocalDate, fromFirst: Boolean, enabled: Boolean, today: LocalDate): LedgerData {
        validateValues(values)
        val start = if (fromFirst) anchor else maxOf(anchor, today)
        val s = Subscription(recordFrom = start.toString(), disabledAfter = if (enabled) null else today.toString())
        val r = Rule(subscriptionId = s.id, effectiveFrom = minOf(anchor, start).toString(), values = values, cycle = cycle, anchor = anchor.toString())
        // A newly disabled subscription records no future bills; today's occurrence remains pending.
        return generate(data.copy(subscriptions = data.subscriptions + s, rules = data.rules + r), today.plusMonths(2).withDayOfMonth(1).minusDays(1))
    }

    fun pay(data: LedgerData, billId: String, paidOn: LocalDate, amount: Long): LedgerData {
        require(amount in 0..Money.MAX)
        val b = data.bills.single { it.id == billId }
        require(!b.retired)
        val frozen = values(data, b)
        return data.copy(bills = data.bills.map { if (it.id == billId) it.copy(snapshot = frozen) else it }, payments = data.payments.filterNot { it.billId == billId } + Payment(billId, paidOn.toString(), amount))
    }
    fun undo(data: LedgerData, billId: String): LedgerData {
        val b = data.bills.single { it.id == billId }
        val frozen = values(data, b)
        val sub = data.subscriptions.single { it.id == b.subscriptionId }
        val obsolete = (sub.disabledAfter != null && b.due > sub.disabledAfter) || ruleAt(data, b.subscriptionId, b.due).scheduleId != b.scheduleId
        return data.copy(payments = data.payments.filterNot { it.billId == billId }, bills = data.bills.map {
            if (it.id == billId) it.copy(snapshot = null, overrides = Fields.diff(defaultValues(data, b), frozen), retired = obsolete) else it
        })
    }
    fun editOne(data: LedgerData, billId: String, changed: Fields): LedgerData = data.copy(bills = data.bills.map {
        if (it.id != billId) it else it.copy(overrides = it.overrides.merge(changed), snapshot = it.snapshot?.let(changed::apply))
    })
    fun reset(data: LedgerData, billId: String): LedgerData = data.copy(bills = data.bills.map {
        if (it.id != billId) it else it.copy(overrides = Fields(), snapshot = it.snapshot?.let { frozen -> defaultValues(data, it).copy(currency = frozen.currency) })
    })

    fun forwardInfo(data: LedgerData, billId: String, changed: Fields, today: LocalDate): Pair<Int, Int> {
        val bill = data.bills.single { it.id == billId }
        require(!bill.retired && date(bill.due) > today && data.payments.none { it.billId == billId }) { "仅未来未付账单可向后应用" }
        val paid = data.payments.map { it.billId }.toSet()
        val targets = data.bills.filter { it.subscriptionId == bill.subscriptionId && it.due >= bill.due && it.id !in paid && !it.retired }
        return targets.size to targets.count { it.id != billId && it.overrides.conflicts(changed) }
    }
    fun forwardAffectedCount(data: LedgerData, billId: String, changed: Fields, overwrite: Boolean, today: LocalDate): Int {
        forwardInfo(data, billId, changed, today)
        val selected = data.bills.single { it.id == billId }
        val paid = data.payments.map { it.billId }.toSet()
        return data.bills.count { b ->
            if (b.subscriptionId != selected.subscriptionId || b.due < selected.due || b.retired || b.id in paid) false
            else {
                val base = changed.apply(defaultValues(data, b))
                val overrides = if (b.id == billId || overwrite) b.overrides.remove(changed) else b.overrides
                overrides.apply(base) != values(data, b)
            }
        }
    }
    fun forward(data: LedgerData, billId: String, changed: Fields, overwrite: Boolean, today: LocalDate): LedgerData {
        forwardInfo(data, billId, changed, today)
        val bill = data.bills.single { it.id == billId }
        var next = defaults(data, bill.subscriptionId, bill.due, changed)
        val paid = next.payments.map { it.billId }.toSet()
        next = next.copy(bills = next.bills.map { b ->
            if (b.subscriptionId != bill.subscriptionId || b.due < bill.due || b.id in paid || b.retired) b
            else if (b.id == billId) b.copy(overrides = b.overrides.remove(changed))
            else if (overwrite) b.copy(overrides = b.overrides.remove(changed)) else b
        })
        return next
    }
    // Apply ONLY the actual changed fields. This deliberately preserves unrelated future defaults.
    fun defaults(data: LedgerData, subId: String, effective: String, changed: Fields): LedgerData {
        if (!changed.any) return data
        val base = ruleAt(data, subId, effective)
        var versions = data.rules.map { if (it.subscriptionId == subId && it.effectiveFrom >= effective) it.copy(values = changed.apply(it.values)) else it }
        if (versions.none { it.subscriptionId == subId && it.effectiveFrom == effective }) versions = versions + base.copy(id = newId(), effectiveFrom = effective, values = changed.apply(base.values))
        return data.copy(rules = versions)
    }
    fun editSubscription(data: LedgerData, subId: String, changed: Fields, currency: String, cycle: Cycle, anchor: LocalDate, effective: LocalDate, changeSchedule: Boolean): LedgerData {
        var next = defaults(data, subId, effective.toString(), changed)
        // Currency changes are handled as a dated default field; frozen payments stay in their original currency.
        val current = ruleAt(next, subId, effective.toString())
        if (currency != current.values.currency) {
            var versions = next.rules.map { if (it.subscriptionId == subId && it.effectiveFrom >= effective.toString()) it.copy(values = it.values.copy(currency = currency)) else it }
            if (versions.none { it.subscriptionId == subId && it.effectiveFrom == effective.toString() }) versions += current.copy(id = newId(), effectiveFrom = effective.toString(), values = current.values.copy(currency = currency))
            next = next.copy(rules = versions)
        }
        if (changeSchedule) {
            val at = ruleAt(next, subId, effective.toString())
            val sid = newId()
            var versions = next.rules.map { if (it.subscriptionId == subId && it.effectiveFrom >= effective.toString()) it.copy(cycle = cycle, anchor = anchor.toString(), scheduleId = sid) else it }
            if (versions.none { it.subscriptionId == subId && it.effectiveFrom == effective.toString() }) versions += at.copy(id = newId(), effectiveFrom = effective.toString(), cycle = cycle, anchor = anchor.toString(), scheduleId = sid)
            val paid = next.payments.map { it.billId }.toSet()
            next = next.copy(subscriptions = next.subscriptions.map { if (it.id == subId) it.copy(recordFrom = minOf(it.recordFrom, effective.toString())) else it }, rules = versions, bills = next.bills.map { if (it.subscriptionId == subId && it.due >= effective.toString() && it.id !in paid) it.copy(retired = true) else it })
        }
        return next
    }
    fun disable(data: LedgerData, subId: String, after: LocalDate?): LedgerData {
        val paid = data.payments.map { it.billId }.toSet()
        return data.copy(subscriptions = data.subscriptions.map { if (it.id == subId) it.copy(disabledAfter = after?.toString()) else it },
            // Remove only unpaid occurrences beyond the chosen stop date; previous history remains.
            bills = data.bills.filterNot { it.subscriptionId == subId && after != null && it.due > after.toString() && it.id !in paid })
    }
    fun archive(data: LedgerData, subId: String, value: Boolean): LedgerData = data.copy(subscriptions = data.subscriptions.map { if (it.id == subId) it.copy(archived = value) else it })
    fun delete(data: LedgerData, subId: String): LedgerData {
        val ids = data.bills.filter { it.subscriptionId == subId }.map { it.id }.toSet()
        return data.copy(subscriptions = data.subscriptions.filterNot { it.id == subId }, rules = data.rules.filterNot { it.subscriptionId == subId }, bills = data.bills.filterNot { it.subscriptionId == subId }, payments = data.payments.filterNot { it.billId in ids })
    }
    fun validateValues(v: Values) {
        require(v.title.isNotBlank() && v.title.length <= 120) { "名称必填，最多120字" }
        require(v.amount in 1..Money.MAX) { "应付金额必须大于0" }
        require(v.currency in Money.currencies) { "不支持的币种" }
        require(v.note.length <= 5000) { "备注最多5000字" }
        require(v.color in Palette.colors) { "无效标签颜色" }
    }
}
