package com.esa.moneytracker.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * What a move of money between two of your own pockets is called.
 *
 * The three cases read differently in Indonesian even though the arithmetic is
 * identical, and the history is much easier to scan when it uses the words the
 * bank does.
 */
enum class TransferKind(val label: String) {
    /** Bank to bank. */
    BETWEEN_BANKS("Pindah antar bank"),

    /** Cash into a bank. */
    DEPOSIT("Setor tunai"),

    /** Cash out of a bank. */
    WITHDRAWAL("Tarik tunai"),
}

/**
 * Money moved from one of your pockets to another.
 *
 * Deliberately **not** a [Transaction]. Nothing here is earned or spent: the
 * total across every pocket is exactly the same before and after, so a transfer
 * must never reach the income figure, the expense figure, the category
 * breakdown, or the list of notes. It lives in its own table and its own
 * history for that reason — mixing the two would mean every total in the app
 * had to remember to exclude it, and one day one of them would forget.
 *
 * A null bank id means Tunai, which is also why both ends cannot be null: cash
 * to cash is not a move, it is nothing happening.
 */
data class Transfer(
    val id: String,
    /** The bank the money left, or null for Tunai. */
    val fromBankId: String?,
    /** The bank the money arrived in, or null for Tunai. */
    val toBankId: String?,
    /** Whole rupiah, always positive; the direction lives in the two ends. */
    val amount: Long,
    /** Optional — unlike a note, a transfer usually explains itself. */
    val note: String,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    /** Non-null once the transfer is in the bin; it leaves after 30 days. */
    val deletedAt: Instant? = null,
) {
    val fromPocket: Pocket get() = if (fromBankId == null) Pocket.CASH else Pocket.ONLINE

    val toPocket: Pocket get() = if (toBankId == null) Pocket.CASH else Pocket.ONLINE

    val kind: TransferKind
        get() = when {
            fromBankId == null -> TransferKind.DEPOSIT
            toBankId == null -> TransferKind.WITHDRAWAL
            else -> TransferKind.BETWEEN_BANKS
        }

    /** True once the transfer has been rewritten at least once. */
    val edited: Boolean get() = updatedAt != null

    /** True when the time was set by hand rather than taken as "now". */
    val timeAdjusted: Boolean
        get() = createdAt.toEpochMilli() - occurredAt.toEpochMilli() >= BACKDATE_SLACK_MILLIS

    /** What this transfer does to one pocket's balance: out, in, or nothing. */
    fun effectOn(bankId: String?): Long = when {
        fromBankId == bankId && toBankId == bankId -> 0L
        fromBankId == bankId -> -amount
        toBankId == bankId -> amount
        else -> 0L
    }

    fun dateTimeIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(occurredAt, zone)

    fun dateIn(zone: ZoneId): LocalDate = dateTimeIn(zone).toLocalDate()

    fun createdDateTimeIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(createdAt, zone)

    fun updatedDateTimeIn(zone: ZoneId): LocalDateTime? =
        updatedAt?.let { LocalDateTime.ofInstant(it, zone) }

    fun deletedDateTimeIn(zone: ZoneId): LocalDateTime? =
        deletedAt?.let { LocalDateTime.ofInstant(it, zone) }

    /** `"BCA → Tunai"`, given a way to name a bank. */
    fun route(bankName: (String) -> String?): String =
        endpointLabel(fromBankId, bankName) + " → " + endpointLabel(toBankId, bankName)

    companion object {
        /** Matches [Transaction]: below a minute, a gap is noise, not intent. */
        private const val BACKDATE_SLACK_MILLIS = 60_000L

        /** A bank's name, Tunai for cash, and something honest for a gap. */
        fun endpointLabel(bankId: String?, bankName: (String) -> String?): String =
            if (bankId == null) Pocket.CASH.label else bankName(bankId) ?: "Bank terhapus"
    }
}

/**
 * One pocket a transfer can start or end at, as the pickers offer them.
 *
 * Cash is one of the choices rather than a separate switch, because to the user
 * "where is this money now" has one answer and Tunai is one of the places.
 */
data class TransferEndpoint(
    /** Null for Tunai. */
    val bankId: String?,
    val label: String,
    val balance: Long,
    val color: BankColor?,
) {
    val isCash: Boolean get() = bankId == null

    /** Stable across recompositions and safe as a list key. */
    val key: String get() = bankId ?: "cash"
}
