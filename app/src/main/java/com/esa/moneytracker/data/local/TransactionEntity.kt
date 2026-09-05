package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import java.time.Instant

/**
 * The on-disk shape of a transaction.
 *
 * Deliberately flat and made of primitives only — no type converters, no nested
 * objects, no foreign keys. That keeps the table trivially dumpable to CSV or
 * JSON when the export feature lands, and readable by anything that can open a
 * SQLite file.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index("occurred_at"),
        Index("type"),
        Index("pocket"),
        Index("bank"),
        Index("deleted_at"),
    ],
)
data class TransactionEntity(
    /** Client-generated UUID: stable across export, backup and re-import. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** [TransactionType.id] */
    @ColumnInfo(name = "type")
    val type: String,

    /** [Pocket.id] */
    @ColumnInfo(name = "pocket")
    val pocket: String,

    /** [Category.id] */
    @ColumnInfo(name = "category")
    val category: String,

    /**
     * [BankEntity.id], or null for cash.
     *
     * Nullable rather than required because the Tunai pocket has no bank, and
     * because a file imported from an older version names none.
     */
    @ColumnInfo(name = "bank")
    val bank: String? = null,

    /** Whole rupiah, always positive; the sign lives in [type]. */
    @ColumnInfo(name = "amount")
    val amount: Long,

    @ColumnInfo(name = "description")
    val description: String,

    /**
     * Epoch millis, UTC — when the money actually moved.
     *
     * Chosen by the user and never touched by an edit, so a note fixed today
     * keeps the place in the history that it was recorded against.
     */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Epoch millis, UTC — when the row was first written. Never changes. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Epoch millis, UTC, or null while the note has never been edited. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    /**
     * Epoch millis, UTC, or null while the record is live.
     *
     * Deleting only sets this: the row keeps its id, its dates and its place in
     * the ordering, so restoring it puts it back exactly where it was. Rows are
     * removed for good once they have been in the bin for [RETENTION_DAYS] days.
     */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
) {
    companion object {
        /** How long a deleted record stays recoverable. */
        const val RETENTION_DAYS = 30L
    }
}

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = TransactionType.fromId(type),
    pocket = Pocket.fromId(pocket),
    category = Category.fromId(category),
    categoryId = category,
    bankId = bank,
    amount = amount,
    description = description,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun Transaction.toEntity(): TransactionEntity =
    TransactionEntity(
        id = id,
        type = type.id,
        pocket = pocket.id,
        category = categoryId,
        bank = bankId,
        amount = amount,
        description = description,
        occurredAt = occurredAt.toEpochMilli(),
        createdAt = createdAt.toEpochMilli(),
        updatedAt = updatedAt?.toEpochMilli(),
        deletedAt = deletedAt?.toEpochMilli(),
    )
