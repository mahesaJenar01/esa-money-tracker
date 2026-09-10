package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AttachmentDao {

    /**
     * Every lampiran in the app, ordered the way a note wants to show them.
     *
     * Read whole rather than a query per note: the rows are tiny — a file name
     * and a few numbers — and the history list would otherwise fire one query
     * per row while it scrolls.
     */
    @Query("SELECT * FROM attachments ORDER BY transaction_id, position, created_at")
    fun observeAll(): Flow<List<AttachmentEntity>>

    @Query(
        """
        SELECT * FROM attachments
        WHERE transaction_id = :transactionId
        ORDER BY position, created_at
        """
    )
    fun observeFor(transactionId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments ORDER BY transaction_id, position, created_at")
    suspend fun getAllOnce(): List<AttachmentEntity>

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun findById(id: String): AttachmentEntity?

    /** Where the next picture goes when one is added to a note that has some. */
    @Query("SELECT COALESCE(MAX(position), -1) FROM attachments WHERE transaction_id = :transactionId")
    suspend fun highestPosition(transactionId: String): Int

    @Query("SELECT COUNT(*) FROM attachments WHERE transaction_id = :transactionId")
    suspend fun countFor(transactionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(attachments: List<AttachmentEntity>)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: String)

    /** The rows a note is about to lose, so their files can go with them. */
    @Query("SELECT * FROM attachments WHERE transaction_id IN (:transactionIds)")
    suspend fun forTransactions(transactionIds: List<String>): List<AttachmentEntity>

    @Query("DELETE FROM attachments WHERE transaction_id IN (:transactionIds)")
    suspend fun deleteForTransactions(transactionIds: List<String>)

    /**
     * Rows whose note no longer exists at all.
     *
     * Reachable after a note is purged from the bin by an older version, and
     * after an import that carried lampiran for a note the file never had.
     */
    @Query("SELECT * FROM attachments WHERE transaction_id NOT IN (SELECT id FROM transactions)")
    suspend fun orphans(): List<AttachmentEntity>
}
