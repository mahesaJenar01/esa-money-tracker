package com.esa.moneytracker.ui.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

/** Guards against a runaway amount and keeps the formatted value readable. */
private const val MAX_AMOUNT_DIGITS = 12

/**
 * Edits one existing record.
 *
 * Everything the entry flow asks over three steps is on a single page here —
 * the record already exists, so there is nothing to walk the user through.
 *
 * Saving does **not** move the note. Its date is only rewritten when the user
 * changes it deliberately, so fixing a typo in a note from the 1st leaves it on
 * the 1st, carrying a mark that says it was corrected.
 */
class EditViewModel(
    private val repository: TransactionRepository,
    private val transactionId: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val form = MutableStateFlow(EditUiState())

    val state: StateFlow<EditUiState> =
        combine(
            form,
            repository.observeBanks(),
            repository.observeAll(),
            repository.observeTransfers(),
        ) { current, banks, transactions, transfers ->
            // Balances, not bare names: the picker is also where a bank gets
            // created, and that dialog has to say what the other banks hold.
            val open = onlinePocketOf(banks, transactions, transfers).banks
            current.copy(
                banks = open,
                bankId = current.bankId?.takeIf { id -> open.any { it.id == id } }
                    ?: open.firstOrNull()?.id,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = form.value,
        )

    init {
        viewModelScope.launch {
            val transaction = repository.find(transactionId)
            if (transaction == null) {
                form.update { it.copy(loading = false, missing = true) }
                return@launch
            }
            val occurred = transaction.dateTimeIn(zone)
            form.update {
                it.copy(
                    loading = false,
                    original = transaction,
                    type = transaction.type,
                    category = transaction.category,
                    pocket = transaction.pocket,
                    bankId = transaction.bankId,
                    amountDigits = transaction.amount.toString(),
                    description = transaction.description,
                    occurredAt = occurred,
                    originalOccurredAt = occurred,
                )
            }
        }
    }

    /**
     * Switching income to expense (or back) invalidates the category, so the
     * page falls back to no selection and asks for one again.
     */
    fun chooseType(type: TransactionType) = form.update {
        if (it.type == type) {
            it
        } else {
            it.copy(type = type, category = null)
        }
    }

    fun chooseCategory(category: Category) = form.update { it.copy(category = category) }

    fun choosePocket(pocket: Pocket) = form.update { it.copy(pocket = pocket) }

    fun chooseBank(bankId: String) = form.update { it.copy(bankId = bankId) }

    /** Accepts anything and keeps only the digits, so paste and IME quirks are safe. */
    fun onAmountChanged(raw: String) = form.update {
        val digits = raw.filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS)
        it.copy(amountDigits = digits)
    }

    fun onDescriptionChanged(value: String) = form.update { it.copy(description = value) }

    fun onOccurredAtChanged(value: LocalDateTime) = form.update { it.copy(occurredAt = value) }

    /** Puts the note's own date back, undoing a change made on this page. */
    fun resetOccurredAt() = form.update { it.copy(occurredAt = it.originalOccurredAt) }

    fun addBank(name: String, color: BankColor, amount: Long, funding: BankFunding) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val bank = repository.addBank(name, color, amount, funding)
            form.update { it.copy(bankId = bank.id) }
        }
    }

    fun submit() {
        val current = state.value
        val original = current.original ?: return
        val category = current.category
        // Nothing to write. Saving anyway would stamp the note as edited and it
        // would say so in Riwayat from then on, for a change that never happened.
        if (!current.hasChanges) return
        if (category == null || !current.canSubmit) {
            form.update { it.copy(showErrors = true) }
            return
        }

        form.update { it.copy(saving = true, showErrors = true) }
        viewModelScope.launch {
            repository.update(
                original = original,
                type = current.type,
                pocket = current.pocket,
                category = category,
                bankId = current.bankId,
                amount = current.amount,
                description = current.description,
                // Only what the user actually touched. Handing back the same
                // instant is what keeps the note where it is in the history.
                occurredAt = current.occurredAt?.atZone(zone)?.toInstant()
                    ?: original.occurredAt,
            )
            form.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(transactionId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                EditViewModel(app.repository, transactionId)
            }
        }
    }
}
