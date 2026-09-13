package com.esa.moneytracker.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.SubscriptionTotals
import com.esa.moneytracker.data.model.SubscriptionUsage
import com.esa.moneytracker.data.model.subscriptionTotalsOf
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SubscriptionsUiState(
    val loading: Boolean = true,
    val items: List<SubscriptionUsage> = emptyList(),
    val totals: SubscriptionTotals = SubscriptionTotals(),
    val bankNames: Map<String, String> = emptyMap(),
    val bankColors: Map<String, BankColor> = emptyMap(),
    val today: LocalDate = LocalDate.now(),
) {
    val isEmpty: Boolean get() = !loading && items.isEmpty()

    /** True when at least one plan is stuck because its bank was closed. */
    val hasStalled: Boolean get() = items.any { it.bankMissing && it.subscription.active }
}

/**
 * Langganan: every bill that arrives on its own, and what they add up to.
 *
 * The accumulation is the reason this screen is not just a list. Two bills on
 * the 2nd and the 4th are still one figure leaving every month, and knowing that
 * figure before the month starts is worth more than seeing the two rows after it
 * has ended.
 */
class SubscriptionsViewModel(
    private val repository: TransactionRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    val state: StateFlow<SubscriptionsUiState> =
        combine(
            repository.observeSubscriptionUsage(zone),
            repository.observeBanks(),
        ) { items, banks ->
            buildState(items, banks)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SubscriptionsUiState(),
        )

    /** Stops a plan charging without forgetting it. Nothing is billed while paused. */
    fun pause(id: String) {
        viewModelScope.launch { repository.pauseSubscription(id) }
    }

    fun resume(id: String) {
        viewModelScope.launch { repository.resumeSubscription(id) }
    }

    /** Deletes the plan; every note it has already written stays where it is. */
    fun delete(id: String) {
        viewModelScope.launch { repository.deleteSubscription(id) }
    }

    fun restore(id: String) {
        viewModelScope.launch { repository.restoreSubscription(id) }
    }

    private fun buildState(
        items: List<SubscriptionUsage>,
        banks: List<Bank>,
    ): SubscriptionsUiState {
        val today = LocalDate.now(zone)
        return SubscriptionsUiState(
            loading = false,
            items = items,
            totals = subscriptionTotalsOf(
                subscriptions = items.map { it.subscription },
                today = today,
                now = Instant.now(),
                zone = zone,
            ),
            bankNames = banks.associate { it.id to it.name },
            bankColors = banks.associate { it.id to it.color },
            today = today,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                SubscriptionsViewModel(app.repository)
            }
        }
    }
}
