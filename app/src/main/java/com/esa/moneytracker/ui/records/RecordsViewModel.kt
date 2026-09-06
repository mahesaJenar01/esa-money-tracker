package com.esa.moneytracker.ui.records

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.repository.TransactionRepository
import com.esa.moneytracker.ui.home.DayGroup
import com.esa.moneytracker.ui.home.historyDays
import com.esa.moneytracker.util.WeekWindow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class RecordsUiState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val week: WeekWindow = WeekWindow.of(LocalDate.now()),

    val days: List<DayGroup> = emptyList(),
    val income: Long = 0,
    val expense: Long = 0,
    val count: Int = 0,

    /** How many notes are waiting in the bin, for the link at the top. */
    val binCount: Int = 0,

    /** Bank id to name, so a history row can say where the money moved. */
    val bankNames: Map<String, String> = emptyMap(),

    /** The most recent reconciliation, for the line under the title. */
    val lastCheck: BalanceCheck? = null,

    /** False at the oldest week that holds anything, so paging has an end. */
    val canGoOlder: Boolean = false,
    /** False on the current week — there is no future to walk into. */
    val canGoNewer: Boolean = false,
) {
    val net: Long get() = income - expense
    val title: String get() = week.relativeLabel(today)
    val rangeLabel: String get() = week.rangeLabel
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

/**
 * The full history, one week at a time.
 *
 * Paging by week rather than scrolling endlessly keeps every screen bounded and
 * makes "how did that week go" a question the page can actually answer, with a
 * per-week total at the top.
 */
class RecordsViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val week = MutableStateFlow(WeekWindow.of(LocalDate.now(zone)))

    val state: StateFlow<RecordsUiState> =
        combine(
            repository.observeAll(),
            repository.observeDeleted(),
            repository.observeBanks(),
            repository.observeBalanceChecks(),
            week,
        ) { transactions, deleted, banks, checks, window ->
            buildState(
                transactions = transactions,
                checks = checks,
                binCount = deleted.size,
                bankNames = banks.associate { it.id to it.name },
                window = window,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecordsUiState(),
        )

    fun showOlderWeek() {
        if (state.value.canGoOlder) week.value = week.value.previous()
    }

    fun showNewerWeek() {
        if (state.value.canGoNewer) week.value = week.value.next()
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    /** Removes a mark. Any note it wrote to close a gap is left alone. */
    fun deleteCheck(id: String) {
        viewModelScope.launch { repository.deleteBalanceCheck(id) }
    }

    private fun buildState(
        transactions: List<Transaction>,
        checks: List<BalanceCheck>,
        binCount: Int,
        bankNames: Map<String, String>,
        window: WeekWindow,
    ): RecordsUiState {
        val today = LocalDate.now(zone)
        val inWeek = transactions.filter { window.contains(it.dateIn(zone)) }
        val checksInWeek = checks.filter { window.contains(it.dateIn(zone)) }

        // A week holding nothing but a mark is still a week worth paging back
        // to, so the oldest thing on file is the older of the two.
        val oldest = minOf(
            transactions.minOfOrNull { it.dateIn(zone) } ?: LocalDate.MAX,
            checks.minOfOrNull { it.dateIn(zone) } ?: LocalDate.MAX,
        ).takeIf { it != LocalDate.MAX }

        return RecordsUiState(
            loading = false,
            today = today,
            week = window,
            days = historyDays(inWeek, checksInWeek, zone, today),
            income = inWeek.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            expense = inWeek.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount },
            count = inWeek.size,
            binCount = binCount,
            bankNames = bankNames,
            lastCheck = checks.maxByOrNull { it.checkedAt },
            canGoOlder = oldest != null && oldest.isBefore(window.start),
            canGoNewer = window.start.isBefore(WeekWindow.of(today).start),
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                RecordsViewModel(app.repository)
            }
        }
    }
}
