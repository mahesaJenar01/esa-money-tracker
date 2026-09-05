package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OpeningBalanceDao {

    @Query("SELECT * FROM opening_balances")
    fun observeAll(): Flow<List<OpeningBalanceEntity>>

    @Query("SELECT * FROM opening_balances")
    suspend fun getAll(): List<OpeningBalanceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(balances: List<OpeningBalanceEntity>)
}
