package com.esa.moneytracker.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A weekly reconciliation: the moment the balances the app had worked out were
 * held up against what the banks actually said.
 *
 * It is not a transaction and never touches a balance. Its only job is to sit in
 * the history at [checkedAt] and mark a line: everything **above** it has not
 * been checked against a bank yet, so a total that no longer matches is a
 * transaction somewhere in that stretch — forgotten, mistyped, or recorded
 * twice. Everything below it was true at the time it was written.
 *
 * The per-bank figures in [items] are the reason the mark is worth more than a
 * date. Weeks later it can still say what the app thought each bank held that
 * evening and what the bank itself said, which turns "something is off" into
 * "the gap opened in this bank, after this line".
 */
data class BalanceCheck(
    val id: String,
    /** When the check was made — the position of the mark in the history. */
    val checkedAt: Instant,
    /** Whatever the user wanted to remember about this particular check. */
    val note: String,
    val createdAt: Instant,
    val items: List<BalanceCheckItem> = emptyList(),
) {
    /** Everything the banks held over what the app had recorded, summed. */
    val difference: Long get() = items.sumOf { it.difference }

    /** True when every checked pocket agreed to the rupiah. */
    val matched: Boolean get() = items.all { it.matched }

    val mismatched: List<BalanceCheckItem> get() = items.filterNot { it.matched }

    /** How many gaps were closed by writing a note at the time. */
    val recordedCount: Int get() = items.count { it.adjustmentId != null }

    fun dateTimeIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(checkedAt, zone)

    fun dateIn(zone: ZoneId): LocalDate = dateTimeIn(zone).toLocalDate()
}

/**
 * One pocket as it stood during a check.
 *
 * [label] is a copy of the name rather than a lookup, because the whole point of
 * the record is that it stays readable: a bank renamed or closed months later
 * must not make an old check say "dicek: (bank tidak dikenal)".
 */
data class BalanceCheckItem(
    val id: String,
    val checkId: String,
    /** Which bank was counted, or null for Tunai — cash has no bank. */
    val bankId: String?,
    /** The pocket's name at the time of the check. */
    val label: String,
    /** What the app had worked out the pocket held. */
    val appBalance: Long,
    /** What the bank — or the wallet — actually said. */
    val realBalance: Long,
    /**
     * The note written to close the gap, when one was written.
     *
     * Null either because there was no gap, or because the user chose to look
     * for the missing transaction themselves rather than paper over it.
     */
    val adjustmentId: String? = null,
) {
    /** Positive when the pocket holds more than the app knew about. */
    val difference: Long get() = realBalance - appBalance

    val matched: Boolean get() = difference == 0L
}
