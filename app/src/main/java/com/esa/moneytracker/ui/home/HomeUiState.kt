package com.esa.moneytracker.ui.home

import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.util.AnalyticsPeriod
import java.time.LocalDate

/** One day's worth of entries, as rendered in the history list. */
data class DayGroup(
    val date: LocalDate,
    val label: String,
    val net: Long,
    val items: List<Transaction>,
)

/** One row of the period's spending breakdown. */
data class CategorySlice(
    val category: Category?,
    val label: String,
    val amount: Long,
    /** 0f..1f, relative to the largest slice — used for the bar width. */
    val share: Float,
)

data class HomeUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val period: AnalyticsPeriod = AnalyticsPeriod.WEEKLY,
    val availablePeriods: List<AnalyticsPeriod> = AnalyticsPeriod.entries.toList(),

    val totalBalance: Long = 0,
    val onlineBalance: Long = 0,
    val cashBalance: Long = 0,
    /** How many open banks the online balance is spread across. */
    val bankCount: Int = 0,
    /** Bank id to name, so a history row can say where the money moved. */
    val bankNames: Map<String, String> = emptyMap(),

    val periodIncome: Long = 0,
    val periodExpense: Long = 0,
    val periodCount: Int = 0,
    val breakdown: List<CategorySlice> = emptyList(),

    val latest: Transaction? = null,

    /** This week only — older weeks live on the detailed Riwayat page. */
    val days: List<DayGroup> = emptyList(),
    val weekRangeLabel: String = "",
    /** True when there are notes outside this week, so the page is worth opening. */
    val hasOlderRecords: Boolean = false,
) {
    val periodNet: Long get() = periodIncome - periodExpense
    val isEmpty: Boolean get() = !loading && days.isEmpty()
    val periodRangeLabel: String get() = period.rangeLabel(today)
    val periodSubtitle: String get() = period.subtitle(today)
}
