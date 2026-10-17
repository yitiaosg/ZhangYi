package cn.zhangyi.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class LedgerTest {
    private val today = date("2026-01-01")
    private val values = Values("视频会员", 3000, "CNY", "默认备注", Palette.colors.first())
    private fun ledger(anchor: String = "2026-01-01", fromFirst: Boolean = true, cycle: Cycle = Cycle(), at: LocalDate = today): LedgerData = Ledger.generate(Ledger.add(LedgerData(), values, cycle, date(anchor), fromFirst, true, at), date("2026-06-30"))
    private fun bill(d: LedgerData, due: String): Bill = d.bills.single { it.due == due && !it.retired }
    private fun value(d: LedgerData, due: String): Values = Ledger.values(d, bill(d, due))
    private fun expectFailure(action: () -> Unit) { try { action(); fail("Should reject invalid input") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }

    @Test fun monthEndNeverDrifts() {
        val dates = Cycle().dates(date("2026-01-31"), date("2026-01-01"), date("2026-05-31"))
        assertEquals(listOf("2026-01-31", "2026-02-28", "2026-03-31", "2026-04-30", "2026-05-31"), dates.map { it.toString() })
    }
    @Test fun leapYearRestoresFebruary29() {
        assertEquals(listOf("2024-02-29", "2025-02-28", "2026-02-28", "2027-02-28", "2028-02-29"), Cycle(PeriodUnit.YEAR).dates(date("2024-02-29"), date("2024-01-01"), date("2028-12-31")).map { it.toString() })
    }
    @Test fun quarterAndHalfYearUseOriginalAnchor() {
        assertEquals(date("2027-01-31"), Cycle(PeriodUnit.MONTH, 3).occurrence(date("2026-01-31"), 4))
        assertEquals(date("2027-02-28"), Cycle(PeriodUnit.MONTH, 6).occurrence(date("2024-08-31"), 5))
    }
    @Test fun weeklyCrossesYearAndMonthsByActualDate() {
        val d = Cycle(PeriodUnit.WEEK).dates(date("2025-12-29"), date("2025-12-01"), date("2026-02-01"))
        assertEquals(listOf("2025-12-29", "2026-01-05", "2026-01-12", "2026-01-19", "2026-01-26"), d.map { it.toString() })
    }
    @Test fun customPeriodsAndEfficientNextAgree() {
        for (c in listOf(Cycle(PeriodUnit.DAY, 10), Cycle(PeriodUnit.WEEK, 3), Cycle(PeriodUnit.MONTH, 5), Cycle(PeriodUnit.YEAR, 2))) {
            val anchor = date("2024-02-29")
            val from = date("2026-01-01")
            assertEquals(c.dates(anchor, from, date("2040-01-01")).first(), c.next(anchor, from))
        }
    }
    @Test fun localDateHasNoTimezoneConversion() {
        val before = java.util.TimeZone.getDefault()
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Pacific/Kiritimati"))
            val a = ledger("2025-12-31")
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Los_Angeles"))
            assertEquals(a.bills.map { it.due }, ledger("2025-12-31").bills.map { it.due })
        } finally { java.util.TimeZone.setDefault(before) }
    }
    @Test fun pastAnchorFromTodayKeepsOriginalSchedule() {
        val d = ledger("2025-10-31", fromFirst = false, at = date("2026-02-10"))
        assertEquals("2026-02-28", d.bills.minOf { it.due })
        assertEquals(3000L, value(d, "2026-03-31").amount)
        assertTrue(d.payments.isEmpty())
    }
    @Test fun pastAnchorFromFirstCreatesUnpaidHistory() {
        val d = ledger("2025-10-31", fromFirst = true, at = date("2026-02-10"))
        assertEquals("2025-10-31", d.bills.minOf { it.due })
        assertTrue(d.payments.isEmpty())
        assertEquals("逾期", Ledger.views(d).first().status(date("2026-02-10")))
    }
    @Test fun todayIsPendingTomorrowIsOverdue() {
        val b = Ledger.views(ledger()).first()
        assertEquals("待付", b.status(today))
        assertEquals("逾期", b.status(today.plusDays(1)))
        assertNull(b.payment)
    }
    @Test fun onePeriodDiscountDoesNotChangeNext() {
        var d = ledger()
        d = Ledger.editOne(d, bill(d, "2026-01-01").id, Fields(amount = 1000))
        assertEquals(1000L, value(d, "2026-01-01").amount)
        assertEquals(3000L, value(d, "2026-02-01").amount)
        assertEquals(3000L, d.rules.first().values.amount)
    }
    @Test fun titleNoteAndColorOverridesAreIndependent() {
        var d = ledger()
        d = Ledger.editOne(d, bill(d, "2026-02-01").id, Fields(title = "特殊标题", note = "", noteSet = true, color = Palette.colors[2]))
        assertEquals("特殊标题", value(d, "2026-02-01").title)
        assertEquals("", value(d, "2026-02-01").note)
        assertEquals(Palette.colors[2], value(d, "2026-02-01").color)
        assertEquals(values, value(d, "2026-03-01"))
    }
    @Test fun forwardStartsAtSelectedDueNotEditDay() {
        var d = ledger()
        d = Ledger.forward(d, bill(d, "2026-03-01").id, Fields(amount = 4000), false, today)
        assertEquals(3000L, value(d, "2026-02-01").amount)
        assertEquals(4000L, value(d, "2026-03-01").amount)
        assertEquals(4000L, value(d, "2026-06-01").amount)
        d = Ledger.generate(d, date("2027-01-31"))
        assertEquals(4000L, value(d, "2027-01-01").amount)
    }
    @Test fun forwardDoesNotCopyUnchangedSpecialFields() {
        var d = ledger()
        val id = bill(d, "2026-02-01").id
        d = Ledger.editOne(d, id, Fields(title = "首发", note = "特例", noteSet = true, color = Palette.colors[4]))
        d = Ledger.forward(d, id, Fields(amount = 4000), false, today)
        assertEquals("首发", value(d, "2026-02-01").title)
        assertEquals(values.copy(amount = 4000), value(d, "2026-03-01"))
    }
    @Test fun laterDiscountIsKeptUnlessExplicitlyOverwritten() {
        var d = ledger()
        d = Ledger.editOne(d, bill(d, "2026-04-01").id, Fields(amount = 1000, note = "优惠", noteSet = true))
        val selected = bill(d, "2026-02-01").id
        assertEquals(5 to 1, Ledger.forwardInfo(d, selected, Fields(amount = 4000), today))
        val kept = Ledger.forward(d, selected, Fields(amount = 4000), false, today)
        assertEquals(1000L, value(kept, "2026-04-01").amount)
        val replaced = Ledger.forward(d, selected, Fields(amount = 4000), true, today)
        assertEquals(4000L, value(replaced, "2026-04-01").amount)
        assertEquals("优惠", value(replaced, "2026-04-01").note)
    }
    @Test fun defaultsProtectPaidPastPendingAndOverrides() {
        var d = ledger()
        d = Ledger.pay(d, bill(d, "2026-04-01").id, today, 2500)
        d = Ledger.editOne(d, bill(d, "2026-05-01").id, Fields(amount = 1000))
        d = Ledger.defaults(d, d.subscriptions.first().id, "2026-03-01", Fields(amount = 4000, title = "新版"))
        assertEquals(values, value(d, "2026-01-01"))
        assertEquals(values, value(d, "2026-04-01"))
        assertEquals(1000L, value(d, "2026-05-01").amount)
        assertEquals("新版", value(d, "2026-05-01").title)
        assertEquals(4000L, value(d, "2026-06-01").amount)
    }
    @Test fun editingPaidDueDoesNotChangeActualAndResetRequiresSeparateAction() {
        var d = ledger()
        val id = bill(d, "2026-01-01").id
        d = Ledger.pay(d, id, today, 2500)
        d = Ledger.editOne(d, id, Fields(amount = 2000))
        assertEquals(2000L, value(d, "2026-01-01").amount)
        assertEquals(2500L, d.payments.single().actualAmount)
        val t = Ledger.totals(Ledger.month(Ledger.views(d), YearMonth.of(2026, 1))).single()
        assertEquals(2500L, t.paid); assertEquals(500L, t.difference)
        d = Ledger.reset(d, id)
        assertEquals(3000L, value(d, "2026-01-01").amount)
        assertEquals(2500L, d.payments.single().actualAmount)
    }
    @Test fun paymentEditAndUndoAreIdempotent() {
        var d = ledger()
        val id = bill(d, "2026-01-01").id
        d = Ledger.pay(d, id, today, 3000)
        d = Ledger.pay(d, id, today.plusDays(1), 2700)
        assertEquals(1, d.payments.size)
        assertEquals("2026-01-02", d.payments.single().paidOn)
        d = Ledger.undo(d, id)
        assertTrue(d.payments.isEmpty())
        assertNull(bill(d, "2026-01-01").snapshot)
        assertEquals("逾期", Ledger.views(d).first().status(today.plusDays(1)))
    }
    @Test fun forwardRejectedForTodayPastAndPaid() {
        var d = ledger("2025-12-01")
        for (due in listOf("2025-12-01", "2026-01-01")) expectFailure { Ledger.forward(d, bill(d, due).id, Fields(amount = 4000), false, today) }
        val id = bill(d, "2026-02-01").id
        d = Ledger.pay(d, id, today, 3000)
        expectFailure { Ledger.forward(d, id, Fields(amount = 4000), false, today) }
    }
    @Test fun futureDefaultsDoNotOverwriteUnrelatedFutureFields() {
        var d = ledger()
        val sub = d.subscriptions.single().id
        d = Ledger.defaults(d, sub, "2026-04-01", Fields(title = "四月标题"))
        d = Ledger.forward(d, bill(d, "2026-02-01").id, Fields(amount = 4000), false, today)
        assertEquals("四月标题", value(d, "2026-04-01").title)
        assertEquals(4000L, value(d, "2026-04-01").amount)
    }
    @Test fun stopDateKeepsThatDayAndEveryPayment() {
        var d = ledger()
        d = Ledger.pay(d, bill(d, "2026-05-01").id, today, 3000)
        d = Ledger.disable(d, d.subscriptions.first().id, date("2026-03-01"))
        d = Ledger.generate(d, date("2027-12-31"))
        assertNotNull(bill(d, "2026-03-01"))
        assertFalse(d.bills.any { it.due == "2026-04-01" })
        assertNotNull(bill(d, "2026-05-01"))
        assertEquals(1, d.payments.size)
    }
    @Test fun changingCycleKeepsHistoryAndPrepaidBills() {
        var d = ledger()
        val sub = d.subscriptions.single().id
        d = Ledger.pay(d, bill(d, "2026-04-01").id, today, 3000)
        d = Ledger.editSubscription(d, sub, Fields(), "CNY", Cycle(PeriodUnit.WEEK, 2), date("2026-03-10"), date("2026-03-01"), true)
        d = Ledger.generate(d, date("2026-06-30"))
        assertEquals(values, value(d, "2026-02-01"))
        assertNotNull(bill(d, "2026-03-10"))
        assertNotNull(bill(d, "2026-03-24"))
        assertNotNull(bill(d, "2026-04-01"))
        assertFalse(Ledger.views(d).any { it.bill.due == "2026-03-01" })
        BackupCodec.decode(BackupCodec.encode(d, Preferences()))
    }
    @Test fun editingBeforeFirstAnchorIsSupported() {
        var d = Ledger.add(LedgerData(), values, Cycle(), date("2026-05-01"), false, true, today)
        d = Ledger.editSubscription(d, d.subscriptions.single().id, Fields(amount = 4000), "CNY", Cycle(), date("2026-05-01"), today, false)
        d = Ledger.generate(d, date("2026-06-30"))
        assertEquals(4000L, value(d, "2026-05-01").amount)
    }
    @Test fun currenciesAreNeverAddedTogether() {
        var d = ledger()
        d = Ledger.add(d, values.copy(title = "USD", currency = "USD", amount = 500), Cycle(), today, true, true, today)
        val t = Ledger.totals(Ledger.month(Ledger.views(d), YearMonth.from(today)))
        assertEquals(listOf("CNY", "USD"), t.map { it.currency })
        assertEquals(listOf(3000L, 500L), t.map { it.due })
    }
    @Test fun annualForecastIsNotDividedBy12() {
        val d = ledger("2026-02-01", cycle = Cycle(PeriodUnit.YEAR))
        assertTrue(Ledger.month(Ledger.views(d), YearMonth.of(2026, 1)).isEmpty())
        assertEquals(3000L, Ledger.totals(Ledger.month(Ledger.views(d), YearMonth.of(2026, 2))).single().due)
        assertTrue(Ledger.month(Ledger.views(d), YearMonth.of(2026, 3)).isEmpty())
    }
    @Test fun repeatedRefreshDoesNotDuplicateSameDayBills() {
        var d = ledger()
        d = Ledger.add(d, values.copy(title = "另一笔"), Cycle(), today, true, true, today)
        val once = Ledger.generate(d, date("2027-01-31"))
        var again = once
        repeat(5) { again = Ledger.generate(again, date("2027-01-31")) }
        assertEquals(once, again)
        assertEquals(2, Ledger.views(again).count { it.bill.due == "2026-01-01" })
        assertEquals(again.bills.size, again.bills.map { it.id }.distinct().size)
    }
    @Test fun backupRoundtripIncludesFutureRulesExceptionsPaymentsAndSettings() {
        var d = ledger()
        d = Ledger.editOne(d, bill(d, "2026-01-01").id, Fields(amount = 1000, noteSet = true, note = ""))
        d = Ledger.pay(d, bill(d, "2026-01-01").id, today, 900)
        d = Ledger.forward(d, bill(d, "2026-03-01").id, Fields(amount = 4000), false, today)
        val prefs = Preferences("DARK", false, "USD")
        val result = BackupCodec.decode(BackupCodec.encode(d, prefs))
        assertEquals(d, result.data); assertEquals(prefs, result.settings)
        assertEquals(Ledger.views(d), Ledger.views(result.data))
        assertEquals(Ledger.generate(d, date("2027-02-01")), Ledger.generate(result.data, date("2027-02-01")))
    }
    @Test fun corruptBackupVersionRelationsSnapshotsAndAmountsRejected() {
        val d = ledger()
        val raw = BackupCodec.encode(d, Preferences())
        expectFailure { BackupCodec.decode("{broken") }
        expectFailure { BackupCodec.decode(raw.replace("\"formatVersion\": 1", "\"formatVersion\": 999")) }
        expectFailure { BackupCodec.decode(BackupCodec.encode(d.copy(payments = listOf(Payment("missing", today.toString(), 1))), Preferences())) }
        expectFailure { BackupCodec.decode(BackupCodec.encode(d.copy(bills = d.bills + d.bills.first()), Preferences())) }
        expectFailure { BackupCodec.decode(BackupCodec.encode(d.copy(rules = d.rules.map { it.copy(values = it.values.copy(amount = -1)) }), Preferences())) }
        expectFailure { BackupCodec.decode(BackupCodec.encode(d.copy(bills = d.bills.map { it.copy(snapshot = values) }), Preferences())) }
    }
    @Test fun moneyUsesExactMinorUnits() {
        assertEquals(101L, Money.parse("1.01", "CNY"))
        assertEquals(1L, Money.parse("1", "JPY"))
        assertEquals(0L, Money.parse("0", "CNY", true))
        assertEquals("1.01", Money.input(101, "CNY"))
        for (text in listOf("0", "-1", "NaN", "1e5", "1,000")) expectFailure { Money.parse(text, "CNY") }
        expectFailure { Money.parse("0.001", "CNY") }
    }
    @Test fun palettePrefersUnusedThenLeastUsedAndNotMostRecent() {
        var d = LedgerData()
        repeat(Palette.colors.size) { i ->
            val c = Palette.choose(d, today)
            assertEquals(Palette.colors[i], c)
            d = Ledger.add(d, values.copy(color = c), Cycle(), today, true, true, today)
        }
        assertEquals(Palette.colors.first(), Palette.choose(d, today))
    }
    @Test fun restoreDefaultRemovesExplicitBlankNoteOverride() {
        var d = ledger()
        val id = bill(d, "2026-02-01").id
        d = Ledger.editOne(d, id, Fields(note = "", noteSet = true))
        d = Ledger.defaults(d, d.subscriptions.first().id, today.toString(), Fields(note = "新的默认备注", noteSet = true))
        assertEquals("", value(d, "2026-02-01").note)
        d = Ledger.reset(d, id)
        assertEquals("新的默认备注", value(d, "2026-02-01").note)
        assertFalse(bill(d, "2026-02-01").overrides.any)
    }
    @Test fun deletingSubscriptionRemovesOnlyItsRelationships() {
        var d = ledger()
        val first = d.subscriptions.single().id
        d = Ledger.pay(d, bill(d, "2026-01-01").id, today, 3000)
        d = Ledger.add(d, values, Cycle(), today, true, true, today)
        d = Ledger.delete(d, first)
        assertEquals(1, d.subscriptions.size)
        assertTrue(d.payments.isEmpty())
        assertTrue(d.bills.none { it.subscriptionId == first })
    }
    @Test fun undoingPrepaidObsoleteBillRetiresItWithoutBreakingBackup() {
        var d = ledger()
        val id = bill(d, "2026-05-01").id
        d = Ledger.pay(d, id, today, 3000)
        d = Ledger.disable(d, d.subscriptions.single().id, date("2026-03-01"))
        d = Ledger.undo(d, id)
        assertTrue(d.bills.single { it.id == id }.retired)
        assertTrue(d.payments.isEmpty())
        BackupCodec.decode(BackupCodec.encode(d, Preferences()))
    }
    @Test fun paidCurrencyIsFrozenEvenWhenRestoringDefaults() {
        var d = ledger()
        val id = bill(d, "2026-03-01").id
        d = Ledger.pay(d, id, today, 3000)
        d = Ledger.editSubscription(d, d.subscriptions.single().id, Fields(amount = 4000), "USD", Cycle(), today, today, false)
        d = Ledger.editOne(d, id, Fields(title = "已付编辑"))
        d = Ledger.reset(d, id)
        assertEquals("CNY", value(d, "2026-03-01").currency)
        assertEquals(3000L, d.payments.single().actualAmount)
    }
    @Test fun missingBackupHeaderAndRecordFieldsAreRejected() {
        val raw = BackupCodec.encode(ledger(), Preferences())
        expectFailure { BackupCodec.decode(raw.replace("  \"formatVersion\": 1,\n", "")) }
        expectFailure { BackupCodec.decode(raw.replace("      \"archived\": false,\n", "")) }
    }
    @Test fun affectedCountExcludesFullyProtectedLaterDiscount() {
        var d = ledger()
        d = Ledger.editOne(d, bill(d, "2026-04-01").id, Fields(amount = 1000))
        val selected = bill(d, "2026-02-01").id
        assertEquals(4, Ledger.forwardAffectedCount(d, selected, Fields(amount = 4000), false, today))
        assertEquals(5, Ledger.forwardAffectedCount(d, selected, Fields(amount = 4000), true, today))
    }

    @Test fun cycleChangesDoNotCreateAnotherBillOnAPrepaidDate() {
        var d = ledger()
        d = Ledger.pay(d, bill(d, "2026-04-01").id, today, 3000)
        d = Ledger.editSubscription(d, d.subscriptions.single().id, Fields(), "CNY", Cycle(), date("2026-03-01"), date("2026-03-01"), true)
        d = Ledger.generate(d, date("2026-06-30"))
        assertEquals(1, Ledger.views(d).count { it.bill.due == "2026-04-01" })
        assertNotNull(Ledger.views(d).single { it.bill.due == "2026-04-01" }.payment)
    }

    @Test fun futureSubscriptionCanBeRescheduledBeforeItsOriginalFirstDate() {
        var d = Ledger.add(LedgerData(), values, Cycle(), date("2026-05-01"), false, true, today)
        d = Ledger.editSubscription(d, d.subscriptions.single().id, Fields(), "CNY", Cycle(), date("2026-03-01"), date("2026-03-01"), true)
        d = Ledger.generate(d, date("2026-06-30"))
        assertEquals("2026-03-01", d.subscriptions.single().recordFrom)
        assertEquals(values, value(d, "2026-03-01"))
        BackupCodec.decode(BackupCodec.encode(d, Preferences()))
    }

}
