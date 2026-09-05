package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
        ORDER BY occurred_at DESC, created_at DESC
        """
    )
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
          AND occurred_at >= :fromEpochMillis AND occurred_at < :toEpochMillis
        ORDER BY occurred_at DESC, created_at DESC
        """
    )
    fun observeBetween(fromEpochMillis: Long, toEpochMillis: Long): Flow<List<TransactionEntity>>

    /** The bin, most recently deleted first. */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
        """
    )
    fun observeDeleted(): Flow<List<TransactionEntity>>

    /** Every row ever written, bin included — the app's "has any data" signal. */
    @Query("SELECT COUNT(*) FROM transactions")
    fun observeRowCount(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun findById(id: String): TransactionEntity?

    /**
     * One-shot full read, oldest first.
     *
     * This is the seam the future export feature plugs into: it hands back every
     * live row in a deterministic order, ready to be serialised to CSV or JSON.
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE deleted_at IS NULL
        ORDER BY occurred_at ASC, created_at ASC
        """
    )
    suspend fun getAllOnce(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transaction: TransactionEntity)

    /** Bulk write for an import; a row already present is replaced by id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    /** Ids already on file, so an import can say what it added and what it replaced. */
    @Query("SELECT id FROM transactions")
    suspend fun allIds(): List<String>

    /** Moves a record to the bin; nothing is lost and its position is kept. */
    @Query("UPDATE transactions SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    /** Puts a binned record back exactly where it was. */
    @Query("UPDATE transactions SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    /**
     * Hands every note recorded against one bank to another one.
     *
     * Used when a bank is closed and its money is said to have moved: the notes
     * follow the money, so the receiving bank's balance ends up exactly where
     * the closed one's was and no history is rewritten beyond its address.
     */
    @Query("UPDATE transactions SET bank = :toBankId WHERE bank = :fromBankId")
    suspend fun reassignBank(fromBankId: String, toBankId: String): Int

    /**
     * Adopts every online note that names no bank.
     *
     * This is the upgrade path for data written before banks existed, and the
     * safety net after importing such a file: online money that belongs to no
     * bank would otherwise sit outside every balance.
     */
    @Query(
        """
        UPDATE transactions SET bank = :bankId
        WHERE pocket = 'online' AND (bank IS NULL OR bank = '')
        """
    )
    suspend fun adoptUnbankedOnline(bankId: String): Int

    /** Cash never has a bank; this clears one that an import may have carried in. */
    @Query("UPDATE transactions SET bank = NULL WHERE pocket = 'cash' AND bank IS NOT NULL")
    suspend fun clearBankOnCash(): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE pocket = 'online' AND (bank IS NULL OR bank = '')")
    suspend fun unbankedOnlineCount(): Int

    /** Drops binned records that ran out of their retention window. */
    @Query("DELETE FROM transactions WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int
}
