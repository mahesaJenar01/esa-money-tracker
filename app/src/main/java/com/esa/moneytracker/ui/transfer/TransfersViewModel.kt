package com.esa.moneytracker.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Transfer
import com.esa.moneytracker.data.repository.TransactionRepository
import com.esa.moneytracker.util.IndonesianDates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** One day's worth of moves. */
data class TransferDayGroup(
    val date: LocalDate,
    val label: String,
    /** What passed through that day. Not a gain or a loss — just volume. */
    val moved: Long,
    val items: List<Transfer>,
)

data class TransfersUiState(
    val loading: Boolean = true,
    val days: List<TransferDayGroup> = emptyList(),
    val count: Int = 0,
    /** Everything moved this month, as a sense of scale rather than a total. */
    val movedThisMonth: Long = 0,
    val bankNames: Map<String, String> = emptyMap(),
    val bankColors: Map<String, BankColor> = emptyMap(),
) {
    val isEmpty: Boolean get() = !loading && days.isEmpty()
}

/**
 * The Pindah Dana history, kept deliberately apart from Riwayat.
 *
 * Moving money between your own pockets is not something that happened to your
 * finances, so it does not belong in the list of things that did. Its own page
 * means Riwayat, "catatan terakhir" and the analytics stay a record of money
 * earned and money spent, and nothing has to remember to filter transfers out.
 */
class TransfersViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    val state: StateFlow<TransfersUiState> =
        combine(
            repository.observeTransfers(),
            repository.observeBanks(),
        ) { transfers, banks ->
            buildState(transfers, banks)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TransfersUiState(),
        )

    /** Moves one to the bin; the money goes straight back where it came from. */
    fun delete(id: String) {
        viewModelScope.launch { repository.deleteTransfer(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restoreTransfer(id) }
    }

    private fun buildState(transfers: List<Transfer>, banks: List<Bank>): TransfersUiState {
        val today = LocalDate.now(zone)
        val monthStart = today.withDayOfMonth(1)

        return TransfersUiState(
            loading = false,
            days = transfers
                .groupBy { it.dateIn(zone) }
                .toSortedMap(reverseOrder())
                .map { (date, items) ->
                    TransferDayGroup(
                        date = date,
                        label = IndonesianDates.relativeDay(date, today),
                        moved = items.sumOf { it.amount },
                        items = items.sortedByDescending { it.occurredAt },
                    )
                },
            count = transfers.size,
            movedThisMonth = transfers
                .filter { !it.dateIn(zone).isBefore(monthStart) }
                .sumOf { it.amount },
            bankNames = banks.associate { it.id to it.name },
            bankColors = banks.associate { it.id to it.color },
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                TransfersViewModel(app.repository)
            }
        }
    }
}
