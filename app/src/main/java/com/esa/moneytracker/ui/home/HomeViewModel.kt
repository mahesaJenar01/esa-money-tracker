package com.esa.moneytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.repository.TransactionRepository
import com.esa.moneytracker.util.AnalyticsPeriod
import com.esa.moneytracker.util.IndonesianDates
import com.esa.moneytracker.util.WeekWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

class HomeViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(AnalyticsPeriod.WEEKLY)

    val state: StateFlow<HomeUiState> =
        combine(
            repository.observeAll(),
            repository.observeOpeningBalances(),
            repository.observeBanks(),
            selectedPeriod,
        ) { transactions, opening, banks, period ->
            buildState(transactions, opening, banks, period)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun selectPeriod(period: AnalyticsPeriod) {
        selectedPeriod.value = period
    }

    /** Moves a record to the bin. [restore] puts it back where it was. */
    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    private fun buildState(
        transactions: List<Transaction>,
        opening: OpeningBalances,
        banks: List<Bank>,
        period: AnalyticsPeriod,
    ): HomeUiState {
        val today = LocalDate.now(zone)

        // Online is never a figure of its own: it is whatever the open banks add
        // up to, so the tile and the bank page can never disagree. Cash is still
        // the opening amount plus every rupiah recorded against it.
        val onlinePocket = onlinePocketOf(banks, transactions)
        val online = onlinePocket.total
        val cash = opening.cash + transactions
            .filter { it.pocket == Pocket.CASH }
            .sumOf { it.signedAmount }

        val inPeriod = transactions.filter { period.contains(it.dateIn(zone), today) }
        val periodIncome = inPeriod.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val periodExpense = inPeriod.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

        // The breakdown answers "where did my money go this week", so it only
        // covers expenses; income has just two categories and its own total.
        val expenseByCategory = inPeriod
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .map { (category, items) ->
                Triple(category, items.first().categoryLabel, items.sumOf { it.amount })
            }
            .sortedByDescending { it.third }
        val largest = expenseByCategory.firstOrNull()?.third ?: 0L
        val breakdown = expenseByCategory.map { (category, label, amount) ->
            CategorySlice(
                category = category,
                label = label,
                amount = amount,
                share = if (largest == 0L) 0f else amount.toFloat() / largest.toFloat(),
            )
        }

        // Riwayat here is this week only. Anything older is reached a week at a
        // time on the detailed page, so the home screen stays a summary.
        val week = WeekWindow.of(today)
        val thisWeek = transactions.filter { week.contains(it.dateIn(zone)) }

        return HomeUiState(
            loading = false,
            today = today,
            period = period,
            totalBalance = online + cash,
            onlineBalance = online,
            cashBalance = cash,
            bankCount = onlinePocket.banks.size,
            bankNames = banks.associate { it.id to it.name },
            periodIncome = periodIncome,
            periodExpense = periodExpense,
            periodCount = inPeriod.size,
            breakdown = breakdown,
            latest = transactions.firstOrNull(),
            days = thisWeek.groupIntoDays(zone, today),
            weekRangeLabel = week.rangeLabel,
            hasOlderRecords = thisWeek.size < transactions.size,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                HomeViewModel(app.repository)
            }
        }
    }
}

/** Newest day first, newest note first inside each day. */
fun List<Transaction>.groupIntoDays(zone: ZoneId, today: LocalDate): List<DayGroup> =
    groupBy { it.dateIn(zone) }
        .toSortedMap(reverseOrder())
        .map { (date, items) ->
            DayGroup(
                date = date,
                label = IndonesianDates.relativeDay(date, today),
                net = items.sumOf { it.signedAmount },
                items = items.sortedByDescending { it.occurredAt },
            )
        }
