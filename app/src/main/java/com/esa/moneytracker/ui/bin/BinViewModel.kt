package com.esa.moneytracker.ui.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant

/** One binned record, with the countdown that decides when it disappears. */
data class BinnedRecord(
    val transaction: Transaction,
    /** Whole days left before the record is deleted for good; 0 means today. */
    val daysLeft: Long,
)

data class BinUiState(
    val loading: Boolean = true,
    val records: List<BinnedRecord> = emptyList(),
) {
    val isEmpty: Boolean get() = !loading && records.isEmpty()
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
        repository.observeDeleted()
            .map { deleted ->
                val now = Instant.now()
                BinUiState(
                    loading = false,
                    records = deleted.map { BinnedRecord(it, daysLeftFor(it, now)) },
                )
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = BinUiState(),
            )

    init {
        // Opening the bin is a good moment to drop anything past its 30 days.
        viewModelScope.launch { repository.purgeExpiredDeleted() }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restore(id) }
    }

    private fun daysLeftFor(transaction: Transaction, now: Instant): Long {
        val deletedAt = transaction.deletedAt ?: return TransactionEntity.RETENTION_DAYS
        val elapsed = Duration.between(deletedAt, now).toDays()
        return (TransactionEntity.RETENTION_DAYS - elapsed).coerceAtLeast(0L)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                BinViewModel(app.repository)
            }
        }
    }
}
