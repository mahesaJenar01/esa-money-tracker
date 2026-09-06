package com.esa.moneytracker.ui.entry

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

class EntryViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val form = MutableStateFlow(EntryUiState(nowStamp = LocalDateTime.now(zone)))

    val state: StateFlow<EntryUiState> =
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
                // One bank means there is nothing to decide, so it is chosen for
                // the user rather than asked about.
                bankId = current.bankId?.takeIf { id -> open.any { it.id == id } }
                    ?: open.firstOrNull()?.id,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = form.value,
        )

    fun chooseType(type: TransactionType) = form.update {
        it.copy(type = type, category = null, step = EntryStep.CATEGORY)
    }

    fun chooseCategory(category: Category) = form.update {
        it.copy(category = category, step = EntryStep.DETAILS)
    }

    fun choosePocket(pocket: Pocket) = form.update { it.copy(pocket = pocket) }

    fun chooseBank(bankId: String) = form.update { it.copy(bankId = bankId) }

    /** Accepts anything and keeps only the digits, so paste and IME quirks are safe. */
    fun onAmountChanged(raw: String) = form.update {
        val digits = raw.filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS)
        it.copy(amountDigits = digits)
    }

    fun onDescriptionChanged(value: String) = form.update {
        it.copy(description = value)
    }

    fun onOccurredAtChanged(value: LocalDateTime) = form.update { it.copy(occurredAt = value) }

    /** Back to the plain case: the note happened as it is being written. */
    fun resetOccurredAt() = form.update {
        it.copy(occurredAt = null, nowStamp = LocalDateTime.now(zone))
    }

    /**
     * Creates a bank without leaving the flow.
     *
     * Reaching the details step with no bank to pick would otherwise be a dead
     * end, and sending the user off to another screen mid-entry would lose what
     * they have typed.
     */
    fun addBank(name: String, color: BankColor, amount: Long, funding: BankFunding) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val bank = repository.addBank(name, color, amount, funding)
            form.update { it.copy(bankId = bank.id) }
        }
    }

    /** Returns true when there was a previous step to go back to. */
    fun back(): Boolean {
        val current = form.value
        val previous = when (current.step) {
            EntryStep.TYPE -> return false
            EntryStep.CATEGORY -> EntryStep.TYPE
            EntryStep.DETAILS -> EntryStep.CATEGORY
        }
        form.update { it.copy(step = previous, showErrors = false) }
        return true
    }

    fun submit() {
        val current = state.value
        val category = current.category
        val type = current.type
        if (type == null || category == null) return
        if (!current.canSubmit) {
            form.update { it.copy(showErrors = true) }
            return
        }

        form.update { it.copy(saving = true, showErrors = true) }
        viewModelScope.launch {
            repository.add(
                type = type,
                pocket = current.pocket,
                category = category,
                bankId = current.bankId,
                amount = current.amount,
                description = current.description,
                // Null when the time was never touched, which is what makes the
                // note say "this happened as I wrote it" instead of carrying a
                // backdating mark it did not earn.
                occurredAt = current.occurredAt?.atZone(zone)?.toInstant(),
            )
            form.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                EntryViewModel(app.repository)
            }
        }
    }
}
