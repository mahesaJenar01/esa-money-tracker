package com.esa.moneytracker.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.repository.NewBank
import com.esa.moneytracker.data.repository.TransactionRepository
import com.esa.moneytracker.ui.backup.BackupMessage
import com.esa.moneytracker.ui.backup.runImport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Guards against a runaway amount and keeps the formatted value readable. */
private const val MAX_AMOUNT_DIGITS = 12

/** Which screen the app should open on. */
enum class SetupGate {
    /** The database has not answered yet — show nothing rather than a flash. */
    UNKNOWN,

    /** Nothing has ever been recorded: ask for the starting balances. */
    NEEDED,

    /** Normal operation. */
    READY,
}

data class SetupUiState(
    val gate: SetupGate = SetupGate.UNKNOWN,
    val cashDigits: String = "",
    /** The banks being listed, in the order they were added. */
    val banks: List<NewBank> = emptyList(),
    val saving: Boolean = false,
    /** What the last import attempt did, when one was made. */
    val message: BackupMessage? = null,
) {
    val cash: Long get() = cashDigits.toLongOrNull() ?: 0L

    val online: Long get() = banks.sumOf { it.amount }

    val total: Long get() = online + cash

    val suggestedBankColor: BankColor get() = BankColor.suggestFor(banks.map { it.color })
}

/**
 * Decides whether the opening-balance question has to be asked, and answers it.
 *
 * The question is tied to the *data*, not to the install: it appears when there
 * is no opening balance and not a single record, which is true of a fresh
 * install or of wiped data, and false after an ordinary update or reinstall
 * over existing notes.
 *
 * Online money is asked for one bank at a time rather than as a single figure,
 * because that is the shape it is kept in from then on — asking for a lump sum
 * here would only mean asking the user to break it up again later.
 */
class SetupViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    private val form = MutableStateFlow(SetupUiState())

    val state: StateFlow<SetupUiState> =
        combine(repository.observeNeedsSetup(), form) { needsSetup, current ->
            current.copy(gate = if (needsSetup) SetupGate.NEEDED else SetupGate.READY)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SetupUiState(),
        )

    /**
     * The other way to start: restore a file exported earlier.
     *
     * A successful import writes the opening balance and the notes, which is
     * exactly the condition this screen is gating on — so the app moves on by
     * itself, with the restored data already in place.
     */
    fun importBackup(source: () -> String?) {
        if (form.value.saving) return
        form.update { it.copy(saving = true, message = null) }
        viewModelScope.launch {
            form.update { it.copy(saving = false, message = runImport(repository, source)) }
        }
    }

    fun onCashChanged(raw: String) = form.update { it.copy(cashDigits = raw.toDigits()) }

    fun addBank(name: String, color: BankColor, amount: Long) {
        if (name.isBlank()) return
        form.update { it.copy(banks = it.banks + NewBank(name.trim(), color, amount)) }
    }

    fun removeBank(index: Int) = form.update {
        if (index !in it.banks.indices) it else it.copy(banks = it.banks.filterIndexed { i, _ -> i != index })
    }

    /**
     * Writes the answer. Zero and no banks at all are perfectly good answers —
     * what matters is that the opening-balance rows exist, because that is what
     * stops the app asking again on every launch.
     */
    fun submit() {
        val current = form.value
        if (current.saving) return
        form.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.completeSetup(cash = current.cash, banks = current.banks)
            form.update { it.copy(saving = false) }
        }
    }

    private fun String.toDigits(): String =
        filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                SetupViewModel(app.repository)
            }
        }
    }
}
