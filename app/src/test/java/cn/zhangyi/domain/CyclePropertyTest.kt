package cn.zhangyi.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CyclePropertyTest {
    @Test fun allMonthEndsStayAnchoredAcrossTwentyYears() {
        for (year in 2000..2020) for (day in 28..31) {
            val anchor = LocalDate.of(year, 1, day)
            val cycle = Cycle()
            val dates = cycle.dates(anchor, anchor, anchor.plusYears(20))
            dates.forEachIndexed { i, actual ->
                val target = java.time.YearMonth.from(anchor).plusMonths(i.toLong())
                assertEquals(target.atDay(minOf(day, target.lengthOfMonth())), actual)
            }
            assertEquals(dates.size, dates.distinct().size)
        }
    }
    @Test fun rangedGenerationEqualsSlicingFullGeneration() {
        for (unit in PeriodUnit.entries) for (every in listOf(1, 2, 3, 7, 13)) {
            val c = Cycle(unit, every)
            val anchor = LocalDate.of(2020, 2, 29)
            val from = LocalDate.of(2026, 3, 1)
            val to = LocalDate.of(2040, 12, 31)
            assertEquals(c.dates(anchor, anchor, to).filter { it >= from }, c.dates(anchor, from, to))
        }
    }
}
