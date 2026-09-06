package com.esa.moneytracker.ui.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.Transfer
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/**
 * One thing waiting in the bin, with the countdown that decides when it goes.
 *
 * Both kinds live here for one reason: a deleted transfer moves money back just
 * as surely as a deleted note does, so it has to be as recoverable. A bin that
 * held only one of them would leave the other quietly unrecoverable the moment
 * its undo snackbar faded.
 */
sealed interface BinnedItem {
    /** Unique across both kinds, so a LazyColumn key never collides. */
    val key: String

    /** The row's own id, for restoring it. */
    val id: String

    /** When it was thrown away, which is what the list is ordered by. */
    val deletedAt: Instant?

    /** Whole days left before it is deleted for good; 0 means today. */
    val daysLeft: Long

    data class Note(
        val transaction: Transaction,
        override val daysLeft: Long,
    ) : BinnedItem {
        override val key: String get() = "note-" + transaction.id
        override val id: String get() = transaction.id
        override val deletedAt: Instant? get() = transaction.deletedAt
    }

    data class Move(
        val transfer: Transfer,
        override val daysLeft: Long,
    ) : BinnedItem {
        override val key: String get() = "transfer-" + transfer.id
        override val id: String get() = transfer.id
        override val deletedAt: Instant? get() = transfer.deletedAt
    }
}

data class BinUiState(
    val loading: Boolean = true,
    val items: List<BinnedItem> = emptyList(),
    val bankNames: Map<String, String> = emptyMap(),
    val bankColors: Map<String, BankColor> = emptyMap(),
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()
}

/**
 * The bin: everything deleted in the last 30 days, newest first.
 *
 * Restoring is not a re-insert — the row never left the table, so it reappears
 * in the exact position, with the same dates, that it had when it was deleted.
 */
class BinViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    val state: StateFlow<BinUiState> =
        combine(
            repository.observeDeleted(),
            repository.observeDeletedTransfers(),
            repository.observeBanks(),
        ) { notes, transfers, banks ->
            val now = Instant.now()
            BinUiState(
                loading = false,
                items = (
                    notes.map { BinnedItem.Note(it, daysLeftFor(it.deletedAt, now)) } +
                        transfers.map { BinnedItem.Move(it, daysLeftFor(it.deletedAt, now)) }
                    ).sortedByDescending { it.deletedAt },
                bankNames = banks.associate { it.id to it.name },
                bankColors = banks.associate { it.id to it.color },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BinUiState(),
        )

    init {
        // Opening the bin is a good moment to drop anything past its 30 days.
        viewModelScope.launch { repository.purgeExpiredDeleted() }
    }

    fun restore(item: BinnedItem) {
        viewModelScope.launch {
            when (item) {
                is BinnedItem.Note -> repository.restore(item.id)
                is BinnedItem.Move -> repository.restoreTransfer(item.id)
            }
        }
    }

    private fun daysLeftFor(deletedAt: Instant?, now: Instant): Long {
        if (deletedAt == null) return TransactionEntity.RETENTION_DAYS
        val elapsed = Duration.between(deletedAt, now).toDays()
        return (TransactionEntity.RETENTION_DAYS - elapsed).coerceAtLeast(0L)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                BinViewModel(app.repository)
            }
        }
    }
}
