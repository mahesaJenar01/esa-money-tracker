package com.esa.moneytracker.ui.check

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.repository.BalanceCheckEntry
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

/** Same ceiling the entry form uses, for the same reason: no accidental novels. */
private const val MAX_BALANCE_DIGITS = 12

/**
 * One pocket to count, as the form asks about it.
 *
 * [digits] empty means the pocket has not been counted at all, which is a
 * different answer from "counted and it agrees" — a bank left blank is simply
 * not part of this check and gets no line in the record.
 */
data class CheckRow(
    /** The bank id, or [CASH] for Tunai. Also the row's identity in the form. */
    val id: String,
    val bankId: String?,
    val label: String,
    /** Null for cash, which is not a bank and has no colour of its own. */
    val color: BankColor?,
    /** What the app has worked out this pocket holds, right now. */
    val appBalance: Long,
    /** Raw digits as typed; rupiah formatting is the field's business. */
    val digits: String = "",
    val recordDifference: Boolean = true,
) {
    val counted: Boolean get() = digits.isNotEmpty()

    val realBalance: Long get() = digits.toLongOrNull() ?: 0L

    val difference: Long get() = realBalance - appBalance

    val matched: Boolean get() = counted && difference == 0L

    val hasGap: Boolean get() = counted && difference != 0L

    companion object {
        const val CASH = "cash"
    }
}

data class BalanceCheckUiState(
    val loading: Boolean = true,
    val rows: List<CheckRow> = emptyList(),
    val note: String = "",
    /** When the check was made. Untouched, it means "now". */
    val checkedAt: LocalDateTime = LocalDateTime.now(),
    val isNow: Boolean = true,
    val busy: Boolean = false,
    val saved: Boolean = false,
) {
    val counted: List<CheckRow> get() = rows.filter { it.counted }

    val gaps: List<CheckRow> get() = rows.filter { it.hasGap }

    /** How many gaps will be written into the history when this is saved. */
    val willRecord: Int get() = gaps.count { it.recordDifference }

    val totalDifference: Long get() = counted.sumOf { it.difference }

    /**
     * A check that counted nothing is not a check.
     *
     * The mark is a claim that the balances were verified; without a single
     * figure behind it there would be nothing to verify and nothing to read
     * later, so the button stays out of reach until one pocket is filled in.
     */
    val canSubmit: Boolean get() = counted.isNotEmpty() && !busy
}

/**
 * The weekly reconciliation, as a form.
 *
 * The figures on the left are live — they are recomputed from the banks and the
 * notes on every emission, exactly like the bank page — while what the user
 * types is held apart and keyed by row. That is what lets a note added in
 * another screen, or a correction made a moment ago, show up here without
 * throwing away half-typed input.
 */
class BalanceCheckViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    /** What the user has typed, by row id. Survives every data refresh. */
    private data class Input(val digits: String = "", val record: Boolean = true)

    private val inputs = MutableStateFlow<Map<String, Input>>(emptyMap())
    private val form = MutableStateFlow(BalanceCheckUiState())

    val state: StateFlow<BalanceCheckUiState> =
        combine(
            repository.observeBanks(),
            repository.observeAll(),
            repository.observeOpeningBalances(),
            inputs,
            form,
        ) { banks, transactions, opening, typed, current ->
            current.copy(loading = false, rows = rowsOf(banks, transactions, opening, typed))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BalanceCheckUiState(),
        )

    /**
     * A typed balance.
     *
     * Leading zeros are stripped, but a balance of exactly zero survives as
     * `"0"` rather than collapsing to an empty field — an emptied account is a
     * real answer, and "not counted" has to stay distinguishable from it.
     */
    fun onBalanceChanged(rowId: String, digits: String) {
        val typed = digits.filter(Char::isDigit).take(MAX_BALANCE_DIGITS)
        val cleaned = typed.trimStart('0').ifEmpty { if (typed.isEmpty()) "" else "0" }
        inputs.update { current ->
            val existing = current[rowId] ?: Input()
            current + (rowId to existing.copy(digits = cleaned))
        }
    }

    /** Clears a row, taking it back out of the check entirely. */
    fun onClearBalance(rowId: String) {
        inputs.update { current ->
            val existing = current[rowId] ?: Input()
            current + (rowId to existing.copy(digits = ""))
        }
    }

    fun onRecordDifferenceChanged(rowId: String, record: Boolean) {
        inputs.update { current ->
            val existing = current[rowId] ?: Input()
            current + (rowId to existing.copy(record = record))
        }
    }

    fun onNoteChanged(note: String) = form.update { it.copy(note = note) }

    fun onCheckedAtChanged(value: LocalDateTime) =
        form.update { it.copy(checkedAt = value, isNow = false) }

    fun resetCheckedAt() =
        form.update { it.copy(checkedAt = LocalDateTime.now(zone), isNow = true) }

    /**
     * Writes the mark, and the notes that close whatever gaps were found.
     *
     * The figures are taken from [state] rather than re-read, so what is saved
     * is exactly what the screen was showing when the button was pressed.
     */
    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        form.update { it.copy(busy = true) }

        viewModelScope.launch {
            repository.saveBalanceCheck(
                entries = current.counted.map { row ->
                    BalanceCheckEntry(
                        bankId = row.bankId,
                        label = row.label,
                        appBalance = row.appBalance,
                        realBalance = row.realBalance,
                        recordDifference = row.recordDifference,
                    )
                },
                note = current.note,
                checkedAt = if (current.isNow) {
                    null
                } else {
                    current.checkedAt.atZone(zone).toInstant()
                },
            )
            form.update { it.copy(busy = false, saved = true) }
        }
    }

    /**
     * Every pocket worth counting: the open banks, then cash.
     *
     * Cash is asked about last and can be skipped like any other row. It is
     * offered at all because the point of the mark is that the app agrees with
     * the world, and a wallet is as capable of drifting as a bank is.
     */
    private fun rowsOf(
        banks: List<Bank>,
        transactions: List<Transaction>,
        opening: OpeningBalances,
        typed: Map<String, Input>,
    ): List<CheckRow> {
        val online = onlinePocketOf(banks, transactions).banks.map { bank ->
            val input = typed[bank.id] ?: Input()
            CheckRow(
                id = bank.id,
                bankId = bank.id,
                label = bank.name,
                color = bank.color,
                appBalance = bank.balance,
                digits = input.digits,
                recordDifference = input.record,
            )
        }

        val cashInput = typed[CheckRow.CASH] ?: Input()
        val cash = CheckRow(
            id = CheckRow.CASH,
            bankId = null,
            label = Pocket.CASH.label,
            color = null,
            appBalance = opening.cash + transactions
                .filter { it.pocket == Pocket.CASH }
                .sumOf { it.signedAmount },
            digits = cashInput.digits,
            recordDifference = cashInput.record,
        )

        return online + cash
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                BalanceCheckViewModel(app.repository)
            }
        }
    }
}
