package com.esa.moneytracker.ui.entry

import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import java.time.LocalDateTime

/** The three questions the entry flow asks, in order. */
enum class EntryStep {
    /** Pemasukan or pengeluaran? */
    TYPE,

    /** Which category? */
    CATEGORY,

    /** How much, from which pocket and bank, when, and what for? */
    DETAILS,
}

data class EntryUiState(
    val step: EntryStep = EntryStep.TYPE,
    val type: TransactionType? = null,
    val category: Category? = null,
    val pocket: Pocket = Pocket.ONLINE,

    /** The open banks, for the picker. */
    val banks: List<BankBalance> = emptyList(),
    val bankId: String? = null,

    /** Raw digits only — formatting to rupiah happens at the edges. */
    val amountDigits: String = "",
    val description: String = "",

    /**
     * When the money actually moved, or null for "right now".
     *
     * Null is not the same as today's date and time: a note saved with null gets
     * one single timestamp for both when it happened and when it was written,
     * which is what lets the app tell an ordinary note apart from one that was
     * remembered days later.
     */
    val occurredAt: LocalDateTime? = null,
    /** Captured when the flow opened, so "Sekarang" has something to show. */
    val nowStamp: LocalDateTime = LocalDateTime.now(),

    /** Errors stay hidden until the user has tried to submit. */
    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    val timeIsNow: Boolean get() = occurredAt == null

    val occurredAtOrNow: LocalDateTime get() = occurredAt ?: nowStamp

    val amountError: String?
        get() = when {
            amountDigits.isBlank() -> "Nominal wajib diisi"
            amount <= 0L -> "Nominal harus lebih dari nol"
            else -> null
        }

    val descriptionError: String?
        get() = if (description.isBlank()) "Keterangan wajib diisi" else null

    /**
     * Online money always belongs to a bank.
     *
     * Letting it through without one is what would make the bank breakdown stop
     * adding up to the Online total, so the flow asks — and offers to create one
     * on the spot when there is nothing to pick yet.
     */
    val bankError: String?
        get() = when {
            pocket != Pocket.ONLINE -> null
            banks.isEmpty() -> "Tambahkan dulu bank tempat uang online kamu"
            bankId == null -> "Pilih bank"
            else -> null
        }

    val canSubmit: Boolean
        get() = !saving && amountError == null && descriptionError == null &&
            bankError == null && category != null

    val categories: List<Category>
        get() = type?.let { Category.of(it) } ?: emptyList()

    /** The colour to offer a bank created from inside this flow. */
    val suggestedBankColor: BankColor get() = BankColor.suggestFor(banks.map { it.color })
}
