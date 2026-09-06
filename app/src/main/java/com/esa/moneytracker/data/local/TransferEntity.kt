package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.Transfer
import java.time.Instant

/**
 * The on-disk shape of a move between two of your own pockets.
 *
 * A table of its own rather than a third `transactions.type`, because the two
 * are not the same kind of thing. A transaction changes how much money exists;
 * a transfer only changes where it sits. Keeping them apart means every income
 * and expense figure in the app excludes transfers by construction instead of
 * by remembering to filter them out.
 *
 * Null in either bank column means Tunai. Both null would be cash to cash,
 * which is not a move at all; the repository refuses to write one.
 */
@Entity(
    tableName = "transfers",
    indices = [
        Index("occurred_at"),
        Index("from_bank"),
        Index("to_bank"),
        Index("deleted_at"),
    ],
)
data class TransferEntity(
    /** Client-generated UUID: stable across export, backup and re-import. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    /** [BankEntity.id] the money left, or null for cash. */
    @ColumnInfo(name = "from_bank")
    val fromBank: String? = null,

    /** [BankEntity.id] the money arrived in, or null for cash. */
    @ColumnInfo(name = "to_bank")
    val toBank: String? = null,

    /** Whole rupiah, always positive. */
    @ColumnInfo(name = "amount")
    val amount: Long,

    /** Optional; a transfer usually explains itself. */
    @ColumnInfo(name = "note")
    val note: String = "",

    /** Epoch millis, UTC — when the money actually moved. */
    @ColumnInfo(name = "occurred_at")
    val occurredAt: Long,

    /** Epoch millis, UTC — when the row was written. Never changes. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** Epoch millis, UTC, or null while it has never been edited. */
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    /** Epoch millis, UTC, or null while the transfer is live. */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)

fun TransferEntity.toDomain(): Transfer = Transfer(
    id = id,
    fromBankId = fromBank,
    toBankId = toBank,
    amount = amount,
    note = note,
    occurredAt = Instant.ofEpochMilli(occurredAt),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun Transfer.toEntity(): TransferEntity = TransferEntity(
    id = id,
    fromBank = fromBankId,
    toBank = toBankId,
    amount = amount,
    note = note,
    occurredAt = occurredAt.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt?.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
)
