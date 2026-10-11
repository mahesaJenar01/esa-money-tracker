package com.esa.moneytracker.ui.returns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.ReturnKind
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.normalizeTags
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.model.parseTags
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.ZoneId

data class ReturnUiState(
    val loading: Boolean = true,
    val expense: Transaction? = null,
    val kind: ReturnKind = ReturnKind.REFUND,
    val amountDigits: String = "",
    val pocket: Pocket = Pocket.ONLINE,
    val banks: List<BankBalance> = emptyList(),
    val bankId: String? = null,
    val description: String = "",
    val tagsText: String = "",
    val knownTags: List<String> = emptyList(),
    val occurredAt: LocalDateTime? = null,
    val nowStamp: LocalDateTime = LocalDateTime.now(),
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val message: String? = null,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L
    val amountError: String? get() = when {
        amount <= 0 -> "Isi nominal lebih dari nol."
        amount > (expense?.netCost ?: 0) -> "Nominal melebihi sisa biaya yang bisa dikembalikan."
        else -> null
    }
    val bankError: String? get() = if (pocket == Pocket.ONLINE && banks.none { it.id == bankId }) "Pilih atau tambahkan bank penerima." else null
    val canSubmit: Boolean get() = !loading && !saving && expense?.canReceiveReturn == true && amountError == null && bankError == null && description.isNotBlank()
}

class ReturnViewModel(
    private val repository: TransactionRepository,
    private val expenseId: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {
    private val form = MutableStateFlow(ReturnUiState(nowStamp = LocalDateTime.now(zone)))
    val state = combine(form, repository.observeAll(), repository.observeBanks(), repository.observeTransfers()) { current, transactions, banks, transfers ->
        val open = onlinePocketOf(banks, transactions, transfers).banks
        current.copy(
            expense = transactions.firstOrNull { it.id == expenseId }, banks = open,
            bankId = current.bankId?.takeIf { id -> open.any { it.id == id } } ?: open.firstOrNull()?.id,
            knownTags = normalizeTags(transactions.flatMap { it.tags }),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), form.value)

    init {
        viewModelScope.launch {
            val expense = repository.find(expenseId)?.takeIf { it.deletedAt == null }
            form.update { it.copy(
                loading = false, expense = expense, pocket = expense?.pocket ?: Pocket.ONLINE,
                bankId = expense?.bankId, description = expense?.let { "Refund: " + it.description }.orEmpty(),
                tagsText = expense?.tags?.joinToString(", ").orEmpty(),
            ) }
        }
    }

    fun chooseKind(kind: ReturnKind) = form.update { current ->
        val expense = state.value.expense
        val defaultDescription = expense?.let { current.kind.label + ": " + it.description }
        current.copy(kind = kind, description = if (expense != null && current.description == defaultDescription) kind.label + ": " + expense.description else current.description)
    }
    fun onAmountChanged(raw: String) = form.update { it.copy(amountDigits = raw.filter(Char::isDigit).trimStart('0').take(12), message = null) }
    fun choosePocket(pocket: Pocket) = form.update { it.copy(pocket = pocket) }
    fun chooseBank(id: String) = form.update { it.copy(bankId = id) }
    fun onDescriptionChanged(text: String) = form.update { it.copy(description = text) }
    fun onTagsChanged(text: String) = form.update { it.copy(tagsText = text) }
    fun onOccurredAtChanged(value: LocalDateTime) = form.update { it.copy(occurredAt = value, message = null) }
    fun resetOccurredAt() = form.update { it.copy(occurredAt = null, nowStamp = LocalDateTime.now(zone)) }
    fun addBank(name: String, color: BankColor, amount: Long, funding: BankFunding) {
        viewModelScope.launch { val bank = repository.addBank(name, color, amount, funding); form.update { it.copy(bankId = bank.id) } }
    }

    fun submit() {
        if (form.value.saving || form.value.saved) return
        val current = state.value
        if (!current.canSubmit) { form.update { it.copy(showErrors = true) }; return }
        form.update { it.copy(saving = true, message = null, showErrors = true) }
        viewModelScope.launch {
            try {
                repository.addReturn(expenseId, current.kind, current.pocket, current.bankId, current.amount,
                    current.description, current.occurredAt?.atZone(zone)?.toInstant(), parseTags(current.tagsText))
                form.update { it.copy(saving = false, saved = true) }
            } catch (error: IllegalArgumentException) {
                form.update { it.copy(saving = false, message = error.message) }
            }
        }
    }

    companion object {
        fun factory(expenseId: String): ViewModelProvider.Factory = viewModelFactory {
            initializer { ReturnViewModel((this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp).repository, expenseId) }
        }
    }
}
