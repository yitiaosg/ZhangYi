package cn.zhangyi.domain

import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Currency
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()
fun date(value: String): LocalDate = LocalDate.parse(value)

@Serializable
enum class PeriodUnit { DAY, WEEK, MONTH, YEAR }

@Serializable
data class Cycle(val unit: PeriodUnit = PeriodUnit.MONTH, val every: Int = 1) {
    fun label(): String = when {
        unit == PeriodUnit.MONTH && every == 3 -> "每季度"
        unit == PeriodUnit.MONTH && every == 6 -> "每半年"
        else -> "每${if (every == 1) "" else every}${when (unit) { PeriodUnit.DAY -> "天"; PeriodUnit.WEEK -> "周"; PeriodUnit.MONTH -> "月"; PeriodUnit.YEAR -> "年" }}"
    }
    fun occurrence(anchor: LocalDate, index: Long): LocalDate {
        require(every in 1..10000 && index >= 0)
        val n = Math.multiplyExact(every.toLong(), index)
        return when (unit) {
            PeriodUnit.DAY -> anchor.plusDays(n)
            PeriodUnit.WEEK -> anchor.plusWeeks(n)
            PeriodUnit.MONTH -> anchor.plusMonths(n)
            PeriodUnit.YEAR -> anchor.plusYears(n)
        }
    }
    // Always calculate from the ORIGINAL anchor, never from the previous occurrence.
    fun next(anchor: LocalDate, from: LocalDate): LocalDate {
        val distance = when (unit) {
            PeriodUnit.DAY -> ChronoUnit.DAYS.between(anchor, from)
            PeriodUnit.WEEK -> ChronoUnit.WEEKS.between(anchor, from)
            PeriodUnit.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(from))
            PeriodUnit.YEAR -> (from.year - anchor.year).toLong()
        }
        var i = (distance / every - 1).coerceAtLeast(0)
        var candidate = occurrence(anchor, i)
        while (candidate < from) candidate = occurrence(anchor, ++i)
        return candidate
    }
    fun dates(anchor: LocalDate, from: LocalDate, through: LocalDate): List<LocalDate> {
        if (through < from || through < anchor) return emptyList()
        val distance = when (unit) {
            PeriodUnit.DAY -> ChronoUnit.DAYS.between(anchor, from)
            PeriodUnit.WEEK -> ChronoUnit.WEEKS.between(anchor, from)
            PeriodUnit.MONTH -> ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(from))
            PeriodUnit.YEAR -> (from.year - anchor.year).toLong()
        }
        var i = (distance / every - 1).coerceAtLeast(0)
        return buildList {
            while (true) {
                val d = occurrence(anchor, i++)
                if (d > through) break
                if (d >= from) add(d)
                require(size <= 250_000) { "账单数量超出安全上限" }
            }
        }
    }
}

@Serializable
data class Values(val title: String, val amount: Long, val currency: String = "CNY", val note: String = "", val color: Int = 0)

// null = no override. noteSet distinguishes an explicitly cleared note from no override.
@Serializable
data class Fields(val title: String? = null, val amount: Long? = null, val note: String = "", val noteSet: Boolean = false, val color: Int? = null) {
    val any: Boolean get() = title != null || amount != null || noteSet || color != null
    fun apply(v: Values): Values = v.copy(title = title ?: v.title, amount = amount ?: v.amount, note = if (noteSet) note else v.note, color = color ?: v.color)
    fun merge(p: Fields): Fields = copy(title = p.title ?: title, amount = p.amount ?: amount, note = if (p.noteSet) p.note else note, noteSet = noteSet || p.noteSet, color = p.color ?: color)
    fun remove(p: Fields): Fields = copy(title = if (p.title != null) null else title, amount = if (p.amount != null) null else amount, note = if (p.noteSet) "" else note, noteSet = if (p.noteSet) false else noteSet, color = if (p.color != null) null else color)
    fun conflicts(p: Fields): Boolean = (title != null && p.title != null) || (amount != null && p.amount != null) || (noteSet && p.noteSet) || (color != null && p.color != null)
    companion object {
        fun diff(old: Values, next: Values) = Fields(next.title.takeIf { it != old.title }, next.amount.takeIf { it != old.amount }, if (next.note != old.note) next.note else "", next.note != old.note, next.color.takeIf { it != old.color })
    }
}

@Serializable
data class Subscription(val id: String = newId(), val recordFrom: String, val disabledAfter: String? = null, val archived: Boolean = false, val createdAt: String = LocalDate.now().toString())

@Serializable
data class Rule(val id: String = newId(), val subscriptionId: String, val effectiveFrom: String, val values: Values, val cycle: Cycle, val anchor: String, val scheduleId: String = newId())

@Serializable
data class Bill(val id: String, val subscriptionId: String, val scheduleId: String, val due: String, val overrides: Fields = Fields(), val snapshot: Values? = null, val retired: Boolean = false)

@Serializable
data class Payment(val billId: String, val paidOn: String, val actualAmount: Long)

@Serializable
data class Preferences(val theme: String = "SYSTEM", val dynamicColor: Boolean = true, val currency: String = "CNY")

@Serializable
data class LedgerData(val subscriptions: List<Subscription> = emptyList(), val rules: List<Rule> = emptyList(), val bills: List<Bill> = emptyList(), val payments: List<Payment> = emptyList())

@Serializable
data class Backup(val formatVersion: Int = 1, val appVersion: String = "1.0.0", val exportedAt: String, val data: LedgerData, val settings: Preferences)

data class BillView(val bill: Bill, val values: Values, val payment: Payment?) {
    fun status(today: LocalDate): String = if (payment != null) "已付" else if (date(bill.due) < today) "逾期" else "待付"
}

data class Totals(val currency: String, val due: Long, val paid: Long, val pending: Long, val paidDue: Long) {
    val difference: Long get() = Math.subtractExact(paid, paidDue)
}

object Money {
    val currencies = listOf("CNY", "USD", "EUR", "GBP", "JPY", "HKD", "TWD", "KRW", "SGD", "AUD", "CAD", "CHF")
    const val MAX = 1_000_000_000_000L
    fun digits(currency: String): Int = Currency.getInstance(currency).defaultFractionDigits.coerceAtLeast(0)
    fun parse(text: String, currency: String, allowZero: Boolean = false): Long {
        require(Regex("\\d{1,14}(\\.\\d{1,3})?").matches(text.trim())) { "请输入有效金额，不使用逗号或指数" }
        val parsed = BigDecimal(text.trim())
        require(parsed.stripTrailingZeros().scale() <= digits(currency)) { "$currency 最多支持${digits(currency)}位小数" }
        val n = parsed.setScale(digits(currency), RoundingMode.UNNECESSARY).movePointRight(digits(currency)).longValueExact()
        require(n in (if (allowZero) 0L else 1L)..MAX) { "金额必须${if (allowZero) "不小于0" else "大于0"}且不超过安全上限" }
        return n
    }
    fun input(amount: Long, currency: String): String = BigDecimal.valueOf(amount, digits(currency)).toPlainString()
    fun format(amount: Long, currency: String): String = "$currency ${input(amount, currency)}"
}

object Palette {
    // Labels only; surfaces and controls use the Material theme. No text is placed on these dots.
    val colors = listOf(0xFF407B67, 0xFF487AA1, 0xFF9761A6, 0xFFB07832, 0xFFB85E6B, 0xFF498A91, 0xFF7D8140, 0xFF826AB1, 0xFFB16B48, 0xFF617C8A).map { it.toInt() }
    fun choose(data: LedgerData, today: LocalDate): Int {
        val active = data.subscriptions.filter { !it.archived && (it.disabledAfter == null || date(it.disabledAfter) >= today) }
        val used = active.sortedBy { it.createdAt }.mapNotNull { s -> data.rules.filter { it.subscriptionId == s.id }.maxByOrNull { it.effectiveFrom }?.values?.color }
        val counts = colors.associateWith { c -> used.count { it == c } }
        return colors.firstOrNull { counts[it] == 0 } ?: colors.filter { it != used.lastOrNull() }.minBy { counts[it] ?: 0 }
    }
}
