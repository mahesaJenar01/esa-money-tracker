package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.BalanceCheckItem
import java.time.Instant

/**
 * The on-disk shape of one reconciliation.
 *
 * Flat primitives only, like every other table here, and split in two rather
 * than stored as one blob: the per-bank lines are what make an old check worth
 * reading, and a JSON column would put them out of reach of a query.
 */
@Entity(
    tableName = "balance_checks",
    indices = [Index("checked_at")],
)
data class BalanceCheckEntity(
    /** Client-generated UUID: stable across export, backup and re-import. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /**
     * Epoch millis, UTC — when the balances were checked.
     *
     * This is the mark's position in the history, so it is compared against
     * `transactions.occurred_at` rather than against when the row was written.
     */
    @ColumnInfo(name = "checked_at")
    val checkedAt: Long,

    @ColumnInfo(name = "note")
    val note: String = "",

    /** Epoch millis, UTC — when the row was written. Never changes. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)

/**
 * One pocket inside one check.
 *
 * There is no foreign key on [checkId] on purpose, for the same reason
 * `transactions.bank` has none: an import may write the lines before the check
 * they belong to. Deleting a check therefore deletes its lines explicitly.
 */
@Entity(
    tableName = "balance_check_items",
    indices = [Index("check_id"), Index("bank")],
)
data class BalanceCheckItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** [BalanceCheckEntity.id] */
    @ColumnInfo(name = "check_id")
    val checkId: String,

    /** [BankEntity.id], or null for cash. */
    @ColumnInfo(name = "bank")
    val bank: String? = null,

    /** The bank's name as it read at the time, so an old check stays legible. */
    @ColumnInfo(name = "label")
    val label: String,

    @ColumnInfo(name = "app_balance")
    val appBalance: Long,

    @ColumnInfo(name = "real_balance")
    val realBalance: Long,

    /** [TransactionEntity.id] of the note written to close the gap, if any. */
    @ColumnInfo(name = "adjustment")
    val adjustment: String? = null,
)

fun BalanceCheckEntity.toDomain(items: List<BalanceCheckItem>): BalanceCheck = BalanceCheck(
    id = id,
    checkedAt = Instant.ofEpochMilli(checkedAt),
    note = note,
    createdAt = Instant.ofEpochMilli(createdAt),
    items = items,
)

fun BalanceCheckItemEntity.toDomain(): BalanceCheckItem = BalanceCheckItem(
    id = id,
    checkId = checkId,
    bankId = bank,
    label = label,
    appBalance = appBalance,
    realBalance = realBalance,
    adjustmentId = adjustment,
)
