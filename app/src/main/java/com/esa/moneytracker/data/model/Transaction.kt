package com.esa.moneytracker.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * A single money movement, as the UI sees it.
 *
 * [amount] is stored in whole rupiah as a [Long]; rupiah has no sub-unit in
 * everyday use and integers keep the arithmetic exact.
 *
 * Three timestamps, each answering a different question:
 *
 * - [occurredAt] is **when the money actually moved**. It is the only one the
 *   list sorts and groups by, and it is chosen by the user — a purchase on the
 *   1st entered on the 5th sits on the 1st.
 * - [createdAt] is when the note was first written. It never changes.
 * - [updatedAt] is when the note was last edited, or null if it never was.
 *   Editing deliberately leaves [occurredAt] alone, so a corrected note keeps
 *   its place in the history instead of jumping to today.
 */
data class Transaction(
    val id: String,
    val type: TransactionType,
    val pocket: Pocket,
    val category: Category?,
    val categoryId: String,
    /** Which bank the online money moved through; null for cash. */
    val bankId: String?,
    val amount: Long,
    val description: String,
    val occurredAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    /** Non-null once the record is in the bin; it leaves the bin after 30 days. */
    val deletedAt: Instant? = null,
) {
    val categoryLabel: String get() = category?.label ?: categoryId

    /** Positive for income, negative for expense — handy for running balances. */
    val signedAmount: Long
        get() = if (type == TransactionType.INCOME) amount else -amount

    /** True once the note has been rewritten at least once. */
    val edited: Boolean get() = updatedAt != null

    /**
     * True when the note was written about something that had already happened.
     *
     * An untouched note is written with one single [Instant] for both stamps, so
     * the difference is exactly zero and this is never a rounding accident. The
     * minute of slack is for records written before v3, whose two stamps could
     * land a millisecond apart by accident.
     *
     * Only backdating counts. A note dated forward keeps its place in the future
     * day it names, which is visible enough on its own without a mark that would
     * read as a mistake.
     */
    val timeAdjusted: Boolean
        get() = createdAt.toEpochMilli() - occurredAt.toEpochMilli() >= BACKDATE_SLACK_MILLIS

    companion object {
        /** Below this, a gap between the two stamps is noise rather than intent. */
        private const val BACKDATE_SLACK_MILLIS = 60_000L
    }

    fun dateTimeIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(occurredAt, zone)

    fun dateIn(zone: ZoneId): LocalDate = dateTimeIn(zone).toLocalDate()

    fun createdDateTimeIn(zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(createdAt, zone)

    fun updatedDateTimeIn(zone: ZoneId): LocalDateTime? =
        updatedAt?.let { LocalDateTime.ofInstant(it, zone) }

    fun deletedDateTimeIn(zone: ZoneId): LocalDateTime? =
        deletedAt?.let { LocalDateTime.ofInstant(it, zone) }
}
