package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankColor
import java.time.Instant

/**
 * One place the Online money sits.
 *
 * Flat primitives only, like [TransactionEntity], so the table stays trivially
 * exportable and readable by anything that can open a SQLite file. There is no
 * foreign key on `transactions.bank` on purpose: a closed bank keeps its row and
 * an imported file may name a bank before the bank row is written.
 */
@Entity(
    tableName = "banks",
    indices = [Index("archived_at"), Index("position")],
)
data class BankEntity(
    /** Client-generated UUID: stable across export, backup and re-import. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** [BankColor.id] */
    @ColumnInfo(name = "color")
    val color: String,

    /** Whole rupiah held before the first note was recorded against this bank. */
    @ColumnInfo(name = "opening_balance")
    val openingBalance: Long,

    /**
     * Every manual correction to this bank's balance, summed. May be negative.
     *
     * Kept apart from [openingBalance] so a correction never rewrites history:
     * the opening amount stays what the user first said it was.
     */
    @ColumnInfo(name = "adjustment")
    val adjustment: Long,

    /** Display order in the bank list. */
    @ColumnInfo(name = "position")
    val position: Int,

    /** Epoch millis, UTC. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * Epoch millis, UTC, or null while the bank is open.
     *
     * A closed bank stops counting towards the Online balance but keeps its row,
     * so notes recorded against it can still say where they happened.
     */
    @ColumnInfo(name = "archived_at")
    val archivedAt: Long? = null,
) {
    companion object {
        /**
         * The bank every pre-bank install is folded into on upgrade.
         *
         * A fixed id rather than a fresh UUID: the same phone upgrading twice, or
         * a backup taken before and after the upgrade, must land on one bank
         * rather than two, and merging by id is what makes that true.
         */
        const val LEGACY_ID = "bank-online-default"
        const val LEGACY_NAME = "Online"
    }
}

fun BankEntity.toDomain(): Bank = Bank(
    id = id,
    name = name,
    color = BankColor.fromId(color),
    openingBalance = openingBalance,
    adjustment = adjustment,
    position = position,
    createdAt = Instant.ofEpochMilli(createdAt),
    archivedAt = archivedAt?.let(Instant::ofEpochMilli),
)

fun Bank.toEntity(): BankEntity = BankEntity(
    id = id,
    name = name,
    color = color.id,
    openingBalance = openingBalance,
    adjustment = adjustment,
    position = position,
    createdAt = createdAt.toEpochMilli(),
    archivedAt = archivedAt?.toEpochMilli(),
)
