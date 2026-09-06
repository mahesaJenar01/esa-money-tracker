package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceCheckDao {

    @Query("SELECT * FROM balance_checks ORDER BY checked_at DESC")
    fun observeAll(): Flow<List<BalanceCheckEntity>>

    /**
     * Every line of every check, in one read.
     *
     * There are a handful of checks a year and a few lines each, so pairing them
     * up in memory costs nothing and saves a query per check — which is what a
     * relation would otherwise cost while the history list scrolls.
     */
    @Query("SELECT * FROM balance_check_items")
    fun observeAllItems(): Flow<List<BalanceCheckItemEntity>>

    @Query("SELECT * FROM balance_checks ORDER BY checked_at ASC")
    suspend fun getAll(): List<BalanceCheckEntity>

    @Query("SELECT * FROM balance_check_items")
    suspend fun getAllItems(): List<BalanceCheckItemEntity>

    @Query("SELECT id FROM balance_checks")
    suspend fun allIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(check: BalanceCheckEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(checks: List<BalanceCheckEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<BalanceCheckItemEntity>)

    @Query("DELETE FROM balance_checks WHERE id = :id")
    suspend fun delete(id: String)

    /** The lines go with the check; nothing else references them. */
    @Query("DELETE FROM balance_check_items WHERE check_id = :checkId")
    suspend fun deleteItemsOf(checkId: String)
}
