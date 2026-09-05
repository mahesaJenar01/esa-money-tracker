package com.esa.moneytracker.util

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Indonesian date labels, spelled out here rather than relying on the device
 * locale so the app reads the same on every phone.
 */
object IndonesianDates {

    private val monthsShort = arrayOf(
        "Jan", "Feb", "Mar", "Apr", "Mei", "Jun",
        "Jul", "Agu", "Sep", "Okt", "Nov", "Des",
    )

    private val monthsLong = arrayOf(
        "Januari", "Februari", "Maret", "April", "Mei", "Juni",
        "Juli", "Agustus", "September", "Oktober", "November", "Desember",
    )

    /** Index 0 == Monday, matching [java.time.DayOfWeek.getValue] minus one. */
    private val daysShort = arrayOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")

    fun monthShort(date: LocalDate): String = monthsShort[date.monthValue - 1]

    fun monthLong(date: LocalDate): String = monthsLong[date.monthValue - 1]

    fun dayShort(date: LocalDate): String = daysShort[date.dayOfWeek.value - 1]

    /** `"1 Sep 2026"` */
    fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth} ${monthShort(date)} ${date.year}"

    /** `"Sen, 1 Sep 2026"` */
    fun dayAndDate(date: LocalDate): String =
        "${dayShort(date)}, ${shortDate(date)}"

    /** `"Hari Ini"` / `"Kemarin"` / `"Sen, 1 Sep 2026"` */
    fun relativeDay(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hari Ini"
        today.minusDays(1) -> "Kemarin"
        else -> dayAndDate(date)
    }

    /** `"14:35"` */
    fun time(dateTime: LocalDateTime): String =
        "%02d:%02d".format(dateTime.hour, dateTime.minute)

    /** `"1 – 7 Sep 2026"`, collapsing the repeated month or year where possible. */
    fun dateRange(start: LocalDate, endInclusive: LocalDate): String = when {
        start.year != endInclusive.year ->
            "${shortDate(start)} – ${shortDate(endInclusive)}"
        start.monthValue != endInclusive.monthValue ->
            "${start.dayOfMonth} ${monthShort(start)} – ${shortDate(endInclusive)}"
        else ->
            "${start.dayOfMonth} – ${shortDate(endInclusive)}"
    }
}
