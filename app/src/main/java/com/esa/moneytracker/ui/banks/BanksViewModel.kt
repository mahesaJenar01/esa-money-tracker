package com.esa.moneytracker.ui.banks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.local.BankEntity
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.OnlinePocket
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BanksUiState(
    val loading: Boolean = true,
    val pocket: OnlinePocket = OnlinePocket(),
    val busy: Boolean = false,
    /** The last thing that happened, shown as a banner and then dismissed. */
    val message: String? = null,
) {
    val banks get() = pocket.banks
    val total: Long get() = pocket.total

    /**
     * True while the only bank is still the one the upgrade created for itself.
     *
     * Used to offer the nudge to rename or split it. Renaming it or adding a
     * second bank makes this false, so the hint puts itself away once it has
     * been acted on and never needs a "seen" flag stored anywhere.
     */
    val showUpgradeHint: Boolean
        get() = banks.size == 1 &&
            banks[0].id == BankEntity.LEGACY_ID &&
            banks[0].name == BankEntity.LEGACY_NAME

    /** The colour to offer a new bank, so two banks rarely look alike. */
    val suggestedColor: BankColor get() = BankColor.suggestFor(banks.map { it.color })
}

/**
 * The bank page: what the Online pocket is made of, and every way to change it.
 *
 * Every figure here is derived — the balances come from the banks and the notes
 * recorded against them, never from a stored total — so the page cannot show a
 * breakdown that fails to add up to the number on the home screen.
 */
class BanksViewModel(
    private val repository: TransactionRepository,
) : ViewModel() {

    private val local = MutableStateFlow(BanksUiState())

    val state: StateFlow<BanksUiState> =
        combine(
            repository.observeBanks(),
            repository.observeAll(),
            local,
        ) { banks, transactions, current ->
            current.copy(loading = false, pocket = onlinePocketOf(banks, transactions))
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BanksUiState(),
        )

    fun dismissMessage() = local.update { it.copy(message = null) }

    /**
     * Adds a bank.
     *
     * [funding] is the answer to the question the dialog asks. Money the app has
     * never counted lifts the Online total; money it already counted somewhere
     * else is taken off that bank instead, and the total does not move at all.
     */
    fun addBank(name: String, color: BankColor, amount: Long, funding: BankFunding) {
        if (local.value.busy || name.isBlank()) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.addBank(name = name, color = color, amount = amount, funding = funding)
            local.update {
                it.copy(
                    busy = false,
                    message = when (funding) {
                        BankFunding.Additional -> name.trim() + " ditambahkan."
                        is BankFunding.MovedFrom ->
                            name.trim() + " ditambahkan, saldonya dipindahkan dari bank lain."
                    },
                )
            }
        }
    }

    fun renameBank(id: String, name: String, color: BankColor) {
        if (local.value.busy || name.isBlank()) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.renameBank(id, name, color)
            local.update { it.copy(busy = false, message = "Bank diperbarui.") }
        }
    }

    /** Says what a bank really holds; the difference is kept as a correction. */
    fun correctBalance(id: String, currentBalance: Long, newBalance: Long) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.correctBankBalance(id, currentBalance, newBalance)
            local.update { it.copy(busy = false, message = "Saldo bank disesuaikan.") }
        }
    }

    /**
     * Closes a bank the way the dialog was answered: the money moves to another
     * bank, or it is gone and the Online total falls by what the bank held.
     */
    fun closeBank(id: String, name: String, closure: BankClosure) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repository.closeBank(id, closure)
            local.update {
                it.copy(
                    busy = false,
                    message = when (closure) {
                        BankClosure.DropBalance ->
                            name + " dihapus, saldonya dikurangi dari total online."
                        is BankClosure.MoveTo ->
                            name + " dihapus, saldo dan catatannya dipindahkan."
                    },
                )
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                BanksViewModel(app.repository)
            }
        }
    }
}
