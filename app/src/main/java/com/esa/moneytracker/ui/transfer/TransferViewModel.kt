package com.esa.moneytracker.ui.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transfer
import com.esa.moneytracker.data.model.TransferEndpoint
import com.esa.moneytracker.data.model.cashBalanceOf
import com.esa.moneytracker.data.model.onlinePocketOf
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

/** The key [TransferEndpoint] uses for the wallet, since cash has no bank id. */
const val CASH_KEY = "cash"

data class TransferFormUiState(
    val loading: Boolean = true,
    /** The transfer being edited is gone — deleted from another screen. */
    val missing: Boolean = false,
    val original: Transfer? = null,

    /** Every pocket the money can start or end at: the open banks, plus Tunai. */
    val endpoints: List<TransferEndpoint> = emptyList(),
    val fromKey: String? = null,
    val toKey: String? = null,

    /** Raw digits only — formatting to rupiah happens at the edges. */
    val amountDigits: String = "",
    val note: String = "",

    val occurredAt: LocalDateTime? = null,
    val originalOccurredAt: LocalDateTime? = null,
    /** Captured when the screen opened, so "Sekarang" has something to show. */
    val nowStamp: LocalDateTime = LocalDateTime.now(),

    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val editing: Boolean get() = original != null

    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    val from: TransferEndpoint? get() = endpoints.firstOrNull { it.key == fromKey }
    val to: TransferEndpoint? get() = endpoints.firstOrNull { it.key == toKey }

    /** Where the money can go, once the source is known: everywhere else. */
    val destinations: List<TransferEndpoint> get() = endpoints.filter { it.key != fromKey }

    val timeIsNow: Boolean get() = !editing && occurredAt == null

    val timeChanged: Boolean get() = editing && occurredAt != originalOccurredAt

    val occurredAtOrNow: LocalDateTime get() = occurredAt ?: nowStamp

    val amountError: String?
        get() = when {
            amountDigits.isBlank() -> "Nominal wajib diisi"
            amount <= 0L -> "Nominal harus lebih dari nol"
            else -> null
        }

    val routeError: String?
        get() = when {
            fromKey == null -> "Pilih asal dananya"
            toKey == null -> "Pilih tujuannya"
            fromKey == toKey -> "Asal dan tujuan tidak boleh sama"
            else -> null
        }

    /**
     * A warning, never a block.
     *
     * The app's idea of a balance is only as complete as what has been recorded,
     * so refusing a move it thinks you cannot afford would be the app arguing
     * with the bank. It says the number and lets the user decide.
     */
    val exceedsSource: Boolean
        get() = from?.let { amount > it.balance } == true

    val hasChanges: Boolean
        get() {
            val source = original ?: return true
            return fromKey != keyOf(source.fromBankId) ||
                toKey != keyOf(source.toBankId) ||
                amount != source.amount ||
                note.trim() != source.note ||
                timeChanged
        }

    val canSubmit: Boolean
        get() = !saving && hasChanges && amountError == null && routeError == null

    /** The bank id behind a picker key; null is Tunai, and is a real answer. */
    fun bankIdOf(key: String?): String? = key.takeIf { it != CASH_KEY }
}

/** Cash is stored as a null bank; the picker needs a string it can compare. */
fun keyOf(bankId: String?): String = bankId ?: CASH_KEY

/**
 * The form behind Pindah Dana, for a new move and for editing an old one.
 *
 * One view model for both because a transfer is the same four questions either
 * way — from, to, how much, when — and the only difference is whether the
 * answers start empty.
 */
class TransferViewModel(
    private val repository: TransactionRepository,
    private val transferId: String?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val form = MutableStateFlow(
        TransferFormUiState(
            loading = transferId != null,
            nowStamp = LocalDateTime.now(zone),
        ),
    )

    val state: StateFlow<TransferFormUiState> =
        combine(
            form,
            repository.observeBanks(),
            repository.observeAll(),
            repository.observeTransfers(),
            repository.observeOpeningBalances(),
        ) { current, banks, transactions, transfers, opening ->
            val online = onlinePocketOf(banks, transactions, transfers)
            val endpoints = online.banks.map { bank ->
                TransferEndpoint(
                    bankId = bank.id,
                    label = bank.name,
                    balance = bank.balance,
                    color = bank.color,
                )
            } + TransferEndpoint(
                bankId = null,
                label = Pocket.CASH.label,
                balance = cashBalanceOf(opening.cash, transactions, transfers),
                color = null,
            )

            current.copy(
                endpoints = endpoints,
                // A pocket that disappeared while the form was open stops being
                // a valid answer, rather than silently sending money nowhere.
                fromKey = current.fromKey?.takeIf { key -> endpoints.any { it.key == key } },
                toKey = current.toKey?.takeIf { key -> endpoints.any { it.key == key } },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = form.value,
        )

    init {
        if (transferId != null) {
            viewModelScope.launch {
                val transfer = repository.findTransfer(transferId)
                if (transfer == null) {
                    form.update { it.copy(loading = false, missing = true) }
                    return@launch
                }
                val occurred = transfer.dateTimeIn(zone)
                form.update {
                    it.copy(
                        loading = false,
                        original = transfer,
                        fromKey = keyOf(transfer.fromBankId),
                        toKey = keyOf(transfer.toBankId),
                        amountDigits = transfer.amount.toString(),
                        note = transfer.note,
                        occurredAt = occurred,
                        originalOccurredAt = occurred,
                    )
                }
            }
        }
    }

    fun chooseFrom(key: String) = form.update {
        // Picking the destination as the source would leave an impossible pair
        // on screen, so the destination steps aside instead.
        it.copy(fromKey = key, toKey = it.toKey.takeIf { to -> to != key })
    }

    fun chooseTo(key: String) = form.update { it.copy(toKey = key) }

    /** Swaps the two ends — the mistake this form invites most. */
    fun swapEnds() = form.update { it.copy(fromKey = it.toKey, toKey = it.fromKey) }

    /** Accepts anything and keeps only the digits, so paste and IME quirks are safe. */
    fun onAmountChanged(raw: String) = form.update {
        it.copy(amountDigits = raw.filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS))
    }

    /** Fills the amount with everything the source holds. */
    fun useWholeBalance() = form.update { current ->
        val balance = current.from?.balance ?: return@update current
        if (balance <= 0L) current else current.copy(amountDigits = balance.toString())
    }

    fun onNoteChanged(value: String) = form.update { it.copy(note = value) }

    fun onOccurredAtChanged(value: LocalDateTime) = form.update { it.copy(occurredAt = value) }

    fun resetOccurredAt() = form.update {
        if (it.editing) {
            it.copy(occurredAt = it.originalOccurredAt)
        } else {
            it.copy(occurredAt = null, nowStamp = LocalDateTime.now(zone))
        }
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) {
            form.update { it.copy(showErrors = true) }
            return
        }

        form.update { it.copy(saving = true, showErrors = true) }
        viewModelScope.launch {
            val fromBank = current.bankIdOf(current.fromKey)
            val toBank = current.bankIdOf(current.toKey)
            val original = current.original
            if (original == null) {
                repository.addTransfer(
                    fromBankId = fromBank,
                    toBankId = toBank,
                    amount = current.amount,
                    note = current.note,
                    occurredAt = current.occurredAt?.atZone(zone)?.toInstant(),
                )
            } else {
                repository.updateTransfer(
                    original = original,
                    fromBankId = fromBank,
                    toBankId = toBank,
                    amount = current.amount,
                    note = current.note,
                    occurredAt = current.occurredAt?.atZone(zone)?.toInstant()
                        ?: original.occurredAt,
                )
            }
            form.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(transferId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                TransferViewModel(app.repository, transferId)
            }
        }
    }
}
