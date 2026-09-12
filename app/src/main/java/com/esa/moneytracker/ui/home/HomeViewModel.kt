package com.esa.moneytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.SubscriptionUsage
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.Transfer
import com.esa.moneytracker.data.model.cashBalanceOf
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.model.subscriptionTotalsOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The four lists that hang off the history, carried as one.
 *
 * `combine` takes five flows and the home screen needs eight, so these travel
 * together — they are all read from the same notes and none of them is useful
 * without the others.
 */
private data class HomeInputs(
    val transactions: List<Transaction>,
    val transfers: List<Transfer>,
    val attachments: Map<String, List<Attachment>>,
    val subscriptions: List<SubscriptionUsage>,
)

class HomeViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val selectedPeriod = MutableStateFlow(AnalyticsPeriod.WEEKLY)

    val state: StateFlow<HomeUiState> =
        combine(
            // Grouped up because `combine` takes five flows and this needs
            // eight. Notes, transfers and lampiran all hang off the same list:
            // every balance on this screen is worked out from the first two, the
            // third is what the rows draw, and the plans are what says how much
            // of the balance is already spoken for.
            combine(
                repository.observeAll(),
                repository.observeTransfers(),
                repository.observeAttachments(),
                repository.observeSubscriptionUsage(zone),
            ) { transactions, transfers, attachments, subscriptions ->
                HomeInputs(transactions, transfers, attachments, subscriptions)
            },
            repository.observeOpeningBalances(),
            repository.observeBanks(),
            repository.observeBalanceChecks(),
            selectedPeriod,
        ) { inputs, opening, banks, checks, period ->
            buildState(inputs, opening, banks, checks, period)
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

    /** Removes a mark. Any note it wrote to close a gap is left alone. */
    fun deleteCheck(id: String) {
        viewModelScope.launch { repository.deleteBalanceCheck(id) }
    }

    private fun buildState(
        inputs: HomeInputs,
        opening: OpeningBalances,
        banks: List<Bank>,
        checks: List<BalanceCheck>,
        period: AnalyticsPeriod,
    ): HomeUiState {
        val (transactions, transfers, attachments, subscriptions) = inputs
        val today = LocalDate.now(zone)

        // Online is never a figure of its own: it is whatever the open banks add
        // up to, so the tile and the bank page can never disagree. Cash is still
        // the opening amount plus every rupiah recorded against it.
        val onlinePocket = onlinePocketOf(banks, transactions, transfers)
        val online = onlinePocket.total
        val cash = cashBalanceOf(opening.cash, transactions, transfers)

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
        val checksThisWeek = checks.filter { week.contains(it.dateIn(zone)) }

        return HomeUiState(
            loading = false,
            today = today,
            period = period,
            totalBalance = online + cash,
            onlineBalance = online,
            cashBalance = cash,
            bankCount = onlinePocket.banks.size,
            bankNames = banks.associate { it.id to it.name },
            attachments = attachments,
            periodIncome = periodIncome,
            periodExpense = periodExpense,
            periodCount = inPeriod.size,
            breakdown = breakdown,
            latest = transactions.firstOrNull(),
            subscriptions = subscriptionTotalsOf(
                subscriptions = subscriptions.map { it.subscription },
                today = today,
                now = Instant.now(),
                zone = zone,
            ),
            // The one that lands first, so the card can name it rather than
            // leaving the user to open the page and work it out.
            nextBill = subscriptions
                .filter { it.nextDue != null && !it.subscription.paused }
                .minByOrNull { it.nextDue!! },
            days = historyDays(thisWeek, checksThisWeek, zone, today),
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

/**
 * Newest day first, newest entry first inside each day.
 *
 * Notes and balance-check marks are interleaved on one clock, and a mark wins a
 * tie. That matters: closing a gap writes its note at the exact instant of the
 * check, and the note belongs to the stretch that was just verified — below the
 * line — not to the unchecked stretch that starts above it.
 */
fun historyDays(
    transactions: List<Transaction>,
    checks: List<BalanceCheck>,
    zone: ZoneId,
    today: LocalDate,
): List<DayGroup> {
    val entries: List<HistoryEntry> =
        transactions.map(HistoryEntry::Record) + checks.map(HistoryEntry::Mark)

    return entries
        .groupBy { LocalDateTime.ofInstant(it.at, zone).toLocalDate() }
        .toSortedMap(reverseOrder())
        .map { (date, items) ->
            DayGroup(
                date = date,
                label = IndonesianDates.relativeDay(date, today),
                net = items.filterIsInstance<HistoryEntry.Record>()
                    .sumOf { it.transaction.signedAmount },
                items = items.sortedWith(
                    compareByDescending<HistoryEntry> { it.at }
                        .thenByDescending { it is HistoryEntry.Mark },
                ),
            )
        }
}
