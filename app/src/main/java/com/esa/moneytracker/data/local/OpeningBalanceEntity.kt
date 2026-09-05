package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One row per pocket: the money already in it before the first note was written.
 *
 * Its presence is also the app's "setup has been done" marker — an empty table
 * means a fresh install or wiped data, which is exactly when the opening
 * balance should be asked for again.
 */
@Entity(tableName = "opening_balances")
data class OpeningBalanceEntity(
    /** [com.esa.moneytracker.data.model.Pocket.id] */
    @PrimaryKey
    @ColumnInfo(name = "pocket")
    val pocket: String,

    /** Whole rupiah. */
    @ColumnInfo(name = "amount")
    val amount: Long,

    /** Epoch millis, UTC — when the balance was set. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
)
