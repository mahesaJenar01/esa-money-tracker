package com.esa.moneytracker.ui.edit

import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import java.time.LocalDateTime

data class EditUiState(
    val loading: Boolean = true,
    /** The record is gone — deleted from another screen while this one was open. */
    val missing: Boolean = false,

    val original: Transaction? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val category: Category? = null,
    val pocket: Pocket = Pocket.ONLINE,

    val banks: List<BankBalance> = emptyList(),
    val bankId: String? = null,

    /** Raw digits only — formatting to rupiah happens at the edges. */
    val amountDigits: String = "",
    val description: String = "",

    /**
     * When the money moved, as this page currently has it.
     *
     * It starts as whatever the note already said and is only written back when
     * the user actually changes it, which is what keeps an edited note in its
     * place in the history instead of jumping to today.
     */
    val occurredAt: LocalDateTime? = null,
    val originalOccurredAt: LocalDateTime? = null,

    val showErrors: Boolean = false,
    val saving: Boolean = false,
    val saved: Boolean = false,
) {
    val amount: Long get() = amountDigits.toLongOrNull() ?: 0L

    val timeChanged: Boolean get() = occurredAt != null && occurredAt != originalOccurredAt

    val amountError: String?
        get() = when {
            amountDigits.isBlank() -> "Nominal wajib diisi"
            amount <= 0L -> "Nominal harus lebih dari nol"
            else -> null
        }

    val descriptionError: String?
        get() = if (description.isBlank()) "Keterangan wajib diisi" else null

    /** Online money always belongs to a bank; see the entry flow for why. */
    val bankError: String?
        get() = when {
            pocket != Pocket.ONLINE -> null
            banks.isEmpty() -> "Tambahkan dulu bank tempat uang online kamu"
            bankId == null -> "Pilih bank"
            else -> null
        }

    /**
     * Whether saving would actually write anything different.
     *
     * Compared against the same values the repository would store, trimming and
     * the cash-has-no-bank rule included, so opening a note and leaving it alone
     * cannot count as a change. Without this, a save that changed nothing still
     * stamped the note as edited and it said so in Riwayat for good.
     */
    val hasChanges: Boolean
        get() {
            val source = original ?: return false
            return type != source.type ||
                category != source.category ||
                pocket != source.pocket ||
                bankId.takeIf { pocket == Pocket.ONLINE } != source.bankId ||
                amount != source.amount ||
                description.trim() != source.description ||
                timeChanged
        }

    val canSubmit: Boolean
        get() = !saving && hasChanges && amountError == null && descriptionError == null &&
            bankError == null && category != null

    val categories: List<Category> get() = Category.of(type)

    val suggestedBankColor: BankColor get() = BankColor.suggestFor(banks.map { it.color })
}
