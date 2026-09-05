package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BankDao {

    /**
     * Every bank, closed ones included, in display order.
     *
     * Closed banks come back too because an old note still names the bank it
     * happened in; filtering them out is the balance calculation's job, not the
     * lookup's.
     */
    @Query("SELECT * FROM banks ORDER BY position ASC, created_at ASC")
    fun observeAll(): Flow<List<BankEntity>>

    @Query("SELECT * FROM banks ORDER BY position ASC, created_at ASC")
    suspend fun getAll(): List<BankEntity>

    @Query("SELECT * FROM banks WHERE id = :id")
    suspend fun findById(id: String): BankEntity?

    @Query("SELECT COUNT(*) FROM banks")
    suspend fun count(): Int

    @Query("SELECT COALESCE(MAX(position), -1) FROM banks")
    suspend fun highestPosition(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bank: BankEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(banks: List<BankEntity>)

    /** Closing a bank keeps the row; only this column changes. */
    @Query("UPDATE banks SET archived_at = :archivedAt WHERE id = :id")
    suspend fun archive(id: String, archivedAt: Long)

    @Query("UPDATE banks SET adjustment = adjustment + :delta WHERE id = :id")
    suspend fun addAdjustment(id: String, delta: Long)
}
