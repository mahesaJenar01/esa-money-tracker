package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query(
        """
        SELECT * FROM transfers
        WHERE deleted_at IS NULL
        ORDER BY occurred_at DESC, created_at DESC
        """
    )
    fun observeAll(): Flow<List<TransferEntity>>

    /** The bin, most recently deleted first. */
    @Query(
        """
        SELECT * FROM transfers
        WHERE deleted_at IS NOT NULL
        ORDER BY deleted_at DESC
        """
    )
    fun observeDeleted(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun findById(id: String): TransferEntity?

    @Query(
        """
        SELECT * FROM transfers
        WHERE deleted_at IS NULL
        ORDER BY occurred_at ASC, created_at ASC
        """
    )
    suspend fun getAllOnce(): List<TransferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(transfer: TransferEntity)

    /** Bulk write for an import; a row already present is replaced by id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(transfers: List<TransferEntity>)

    @Query("SELECT id FROM transfers")
    suspend fun allIds(): List<String>

    /** Moves a transfer to the bin. It keeps its id, dates and ordering. */
    @Query("UPDATE transfers SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE transfers SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    @Query("DELETE FROM transfers WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int

    /**
     * Follows the money when a bank is closed and its balance is said to have
     * moved, exactly as the notes recorded against it do.
     */
    @Query("UPDATE transfers SET from_bank = :toBankId WHERE from_bank = :fromBankId")
    suspend fun reassignSource(fromBankId: String, toBankId: String): Int

    @Query("UPDATE transfers SET to_bank = :toBankId WHERE to_bank = :fromBankId")
    suspend fun reassignDestination(fromBankId: String, toBankId: String): Int

    /**
     * Drops transfers that would move money to and from the same bank.
     *
     * Only ever produced by closing a bank onto one it had already exchanged
     * money with: the two ends collapse into one and the row stops meaning
     * anything. Leaving it would put a "BCA → BCA" line in the history forever.
     */
    @Query("DELETE FROM transfers WHERE from_bank IS NOT NULL AND from_bank = to_bank")
    suspend fun dropSelfTransfers(): Int
}
