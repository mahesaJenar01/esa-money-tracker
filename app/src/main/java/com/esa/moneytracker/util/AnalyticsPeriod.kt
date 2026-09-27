package com.esa.moneytracker.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The window the home screen summarises.
 *
 * This window only controls analytics. Riwayat deliberately keeps its own
 * Monday-to-Sunday paging even when [MONTHLY] is selected here, so a longer
 * report never turns the home screen into an endless list.
 */
enum class AnalyticsPeriod(val id: String, val label: String) {

    WEEKLY("weekly", "Mingguan") {
        override fun startOf(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY)
        override fun endExclusiveOf(today: LocalDate): LocalDate = startOf(today).plusWeeks(1)
        override fun subtitle(today: LocalDate): String = "Minggu ini"
    },
    MONTHLY("monthly", "Bulanan") {
        override fun startOf(today: LocalDate): LocalDate = today.withDayOfMonth(1)
        override fun endExclusiveOf(today: LocalDate): LocalDate = startOf(today).plusMonths(1)
        override fun subtitle(today: LocalDate): String = "Bulan ini"
    },
    ;

    abstract fun startOf(today: LocalDate): LocalDate

    abstract fun endExclusiveOf(today: LocalDate): LocalDate

    /** Short human label for the period, e.g. `"Minggu ini"`. */
    abstract fun subtitle(today: LocalDate): String

    fun endInclusiveOf(today: LocalDate): LocalDate = endExclusiveOf(today).minusDays(1)

    fun rangeLabel(today: LocalDate): String =
        IndonesianDates.dateRange(startOf(today), endInclusiveOf(today))

    fun contains(date: LocalDate, today: LocalDate): Boolean =
        !date.isBefore(startOf(today)) && date.isBefore(endExclusiveOf(today))
}
