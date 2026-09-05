package com.esa.moneytracker.util

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The window the home screen summarises.
 *
 * Only [WEEKLY] is offered today. Adding `MONTHLY` or `YEARLY` means adding a
 * constant here with its own [startOf] / [endExclusiveOf] — the screen, the
 * selector and the view model all read the enum and need no changes.
 */
enum class AnalyticsPeriod(val id: String, val label: String) {

    WEEKLY("weekly", "Mingguan") {
        override fun startOf(today: LocalDate): LocalDate = today.with(DayOfWeek.MONDAY)
        override fun endExclusiveOf(today: LocalDate): LocalDate = startOf(today).plusWeeks(1)
        override fun subtitle(today: LocalDate): String = "Minggu ini"
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
