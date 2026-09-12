package com.esa.moneytracker.ui.subscriptions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.esa.moneytracker.MoneyTrackerApp
import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Subscription
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.onlinePocketOf
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Guards against a runaway amount and keeps the formatted value readable. */
private const val MAX_AMOUNT_DIGITS = 12

data class SubscriptionFormUiState(
    val loading: Boolean = true,
    /** The plan being edited is gone — deleted from the list behind this screen. */
    val missing: Boolean = false,
    val original: Subscription? = null,

    val name: String = "",
    /** Raw digits only — formatting to rupiah happens at the edges. */
    val amountDigits: String = "",
    val category: Category = Category.LANGGANAN,
    val pocket: Pocket = Pocket.ONLINE,
    val bankId: String? = null,
    val banks: List<BankBalance> = emptyList(),

    val cycle: BillingCycle = BillingCycle.MONTHLY,
    val dayOfMonth: Int = 1,
    val dayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val timeOfDay: LocalTime = Subscription.DEFAULT_TIME,

    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
    /** Captured when the screen opened, so the preview has a fixed "now". */
    val now: Instant = Instant.now(),
) {
    val editing: Boolean get() = original != null

    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    val categories: List<Category> get() = Category.of(TransactionType.EXPENSE)

    /** So a bank added from here rarely repeats a colour already in use. */
    val suggestedBankColor: BankColor
        get() = BankColor.suggestFor(banks.map { it.color })

    val nameError: String?
        get() = if (name.isBlank()) "Nama langganan wajib diisi" else null

    val amountError: String?
        get() = when {
            amountDigits.isBlank() -> "Nominal wajib diisi"
            amount <= 0L -> "Nominal harus lebih dari nol"
            else -> null
        }

    val bankError: String?
        get() = if (pocket == Pocket.ONLINE && bankId == null) "Pilih bank pembayarnya" else null

    /**
     * The plan exactly as it would be saved, for the preview.
     *
     * Built from the answers on screen rather than from the saved row, which is
     * what lets the form say when the next bill lands *before* the change is
     * committed — including when an edit moves it.
     */
    val preview: Subscription?
        get() {
            if (amountError != null || nameError != null) return null
            val base = original
            val settled = base?.chargedThrough ?: now
            return Subscription(
                id = base?.id ?: "preview",
                name = name.trim(),
                amount = amount,
                categoryId = category.id,
                pocket = pocket,
                bankId = bankId.takeIf { pocket == Pocket.ONLINE },
                cycle = cycle,
                dayOfMonth = dayOfMonth,
                dayOfWeek = dayOfWeek,
                timeOfDay = timeOfDay,
                chargedThrough = settled,
                createdAt = base?.createdAt ?: now,
            )
        }

    val hasChanges: Boolean
        get() {
            val source = original ?: return true
            return name.trim() != source.name ||
                amount != source.amount ||
                category.id != source.categoryId ||
                pocket != source.pocket ||
                bankId.takeIf { pocket == Pocket.ONLINE } != source.bankId ||
                cycle != source.cycle ||
                dayOfMonth != source.dayOfMonth ||
                dayOfWeek != source.dayOfWeek ||
                timeOfDay != source.timeOfDay
        }

    val canSubmit: Boolean
        get() = !saving && hasChanges &&
            nameError == null && amountError == null && bankError == null
}

/**
 * The form behind a recurring bill, for a new plan and for editing an old one.
 *
 * One view model for both: a subscription asks the same questions either way,
 * and the only difference is whether the answers start empty. What an edit does
 * *not* touch is the watermark — the plan keeps knowing which of its bills have
 * already been written, so changing the price or the day can neither re-charge
 * a month that was paid nor skip one that was not.
 */
class SubscriptionFormViewModel(
    private val repository: TransactionRepository,
    private val subscriptionId: String?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val form = MutableStateFlow(
        SubscriptionFormUiState(
            loading = subscriptionId != null,
            // A new plan starts on today's date and this weekday, which is
            // almost always what a bill signed up for today actually does.
            dayOfMonth = LocalDate.now(zone).dayOfMonth,
            dayOfWeek = LocalDate.now(zone).dayOfWeek,
        ),
    )

    val state: StateFlow<SubscriptionFormUiState> =
        combine(
            form,
            repository.observeBanks(),
            repository.observeAll(),
            repository.observeTransfers(),
        ) { current, banks, transactions, transfers ->
            val open = onlinePocketOf(banks, transactions, transfers).banks
            current.copy(
                banks = open,
                // With exactly one bank there is nothing to decide, so the
                // question answers itself rather than blocking the form.
                bankId = current.bankId?.takeIf { id -> open.any { it.id == id } }
                    ?: open.singleOrNull()?.id,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = form.value,
        )

    init {
        if (subscriptionId != null) {
            viewModelScope.launch {
                val subscription = repository.findSubscription(subscriptionId)
                if (subscription == null) {
                    form.update { it.copy(loading = false, missing = true) }
                    return@launch
                }
                form.update {
                    it.copy(
                        loading = false,
                        original = subscription,
                        name = subscription.name,
                        amountDigits = subscription.amount.toString(),
                        category = subscription.category ?: Category.LANGGANAN,
                        pocket = subscription.pocket,
                        bankId = subscription.bankId,
                        cycle = subscription.cycle,
                        dayOfMonth = subscription.dayOfMonth,
                        dayOfWeek = subscription.dayOfWeek,
                        timeOfDay = subscription.timeOfDay,
                    )
                }
            }
        }
    }

    fun onNameChanged(value: String) = form.update { it.copy(name = value) }

    /** Accepts anything and keeps only the digits, so paste and IME quirks are safe. */
    fun onAmountChanged(raw: String) = form.update {
        it.copy(amountDigits = raw.filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS))
    }

    fun chooseCategory(category: Category) = form.update { it.copy(category = category) }

    fun choosePocket(pocket: Pocket) = form.update {
        it.copy(pocket = pocket, bankId = if (pocket == Pocket.CASH) null else it.bankId)
    }

    fun chooseBank(id: String) = form.update { it.copy(bankId = id, pocket = Pocket.ONLINE) }

    /** Adds a bank from inside the form, so an empty list is never a dead end. */
    fun addBank(name: String, color: BankColor, amount: Long, funding: BankFunding) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val bank = repository.addBank(name, color, amount, funding)
            form.update { it.copy(bankId = bank.id, pocket = Pocket.ONLINE) }
        }
    }

    fun chooseCycle(cycle: BillingCycle) = form.update { it.copy(cycle = cycle) }

    fun chooseDayOfMonth(day: Int) = form.update { it.copy(dayOfMonth = day) }

    fun chooseDayOfWeek(day: DayOfWeek) = form.update { it.copy(dayOfWeek = day) }

    fun onTimeChanged(time: LocalTime) = form.update { it.copy(timeOfDay = time) }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) {
            form.update { it.copy(showErrors = true) }
            return
        }

        form.update { it.copy(saving = true, showErrors = true) }
        viewModelScope.launch {
            val original = current.original
            if (original == null) {
                repository.addSubscription(
                    name = current.name,
                    amount = current.amount,
                    category = current.category,
                    pocket = current.pocket,
                    bankId = current.bankId,
                    cycle = current.cycle,
                    dayOfMonth = current.dayOfMonth,
                    dayOfWeek = current.dayOfWeek,
                    timeOfDay = current.timeOfDay,
                )
            } else {
                repository.updateSubscription(
                    original = original,
                    name = current.name,
                    amount = current.amount,
                    category = current.category,
                    pocket = current.pocket,
                    bankId = current.bankId,
                    cycle = current.cycle,
                    dayOfMonth = current.dayOfMonth,
                    dayOfWeek = current.dayOfWeek,
                    timeOfDay = current.timeOfDay,
                )
            }
            // Saving can move a bill into the past — an edit that pulls the
            // billing day backwards does exactly that. Running the catch-up here
            // means the charge it owes is written now, rather than lurking until
            // the next time the app is opened.
            repository.runDueSubscriptions()
            form.update { it.copy(saving = false, saved = true) }
        }
    }

    companion object {
        fun factory(subscriptionId: String?): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app =
                    this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MoneyTrackerApp
                SubscriptionFormViewModel(app.repository, subscriptionId)
            }
        }
    }
}
