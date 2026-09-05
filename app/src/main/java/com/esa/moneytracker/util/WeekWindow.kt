package com.esa.moneytracker.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * One Monday-to-Sunday week — the unit Riwayat is paged by.
 *
 * The history shows a single week at a time; older notes are reached by
 * stepping back one window at a time rather than by scrolling forever.
 */
data class WeekWindow(val start: LocalDate) {

    val endExclusive: LocalDate get() = start.plusWeeks(1)

    val endInclusive: LocalDate get() = endExclusive.minusDays(1)

    fun contains(date: LocalDate): Boolean =
        !date.isBefore(start) && date.isBefore(endExclusive)

    fun previous(): WeekWindow = WeekWindow(start.minusWeeks(1))

    fun next(): WeekWindow = WeekWindow(start.plusWeeks(1))

    /** `"1 – 7 Sep 2026"` */
    val rangeLabel: String get() = IndonesianDates.dateRange(start, endInclusive)

    /** `"Minggu ini"` / `"Minggu lalu"` / `"3 minggu lalu"` */
    fun relativeLabel(today: LocalDate): String {
        val weeksBack = weeksBefore(today)
        return when {
            weeksBack == 0L -> "Minggu ini"
            weeksBack == 1L -> "Minggu lalu"
            weeksBack > 1L -> "$weeksBack minggu lalu"
            weeksBack == -1L -> "Minggu depan"
            else -> "${-weeksBack} minggu ke depan"
        }
    }

    /** How many whole weeks this window sits before the one holding [today]. */
    fun weeksBefore(today: LocalDate): Long =
        java.time.temporal.ChronoUnit.WEEKS.between(start, of(today).start)

    companion object {
        fun of(date: LocalDate): WeekWindow = WeekWindow(date.with(DayOfWeek.MONDAY))
    }
}
