package com.esa.moneytracker.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {

    /**
     * Every live plan, running ones first.
     *
     * Paused plans sit at the bottom rather than being hidden: a plan that is
     * not charging is still a decision the user made and will want to undo.
     */
    @Query(
        """
        SELECT * FROM subscriptions
        WHERE deleted_at IS NULL
        ORDER BY paused_at IS NOT NULL, amount DESC, name ASC
        """
    )
    fun observeAll(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun findById(id: String): SubscriptionEntity?

    /** What the catch-up run reads: everything that could owe a charge. */
    @Query("SELECT * FROM subscriptions WHERE deleted_at IS NULL AND paused_at IS NULL")
    suspend fun getDueCandidates(): List<SubscriptionEntity>

    @Query("SELECT * FROM subscriptions WHERE deleted_at IS NULL ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(subscription: SubscriptionEntity)

    /** Bulk write for an import; a row already present is replaced by id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(subscriptions: List<SubscriptionEntity>)

    @Query("SELECT id FROM subscriptions")
    suspend fun allIds(): List<String>

    /** Moves the watermark after a run, without touching anything else. */
    @Query("UPDATE subscriptions SET charged_through = :chargedThrough WHERE id = :id")
    suspend fun markChargedThrough(id: String, chargedThrough: Long)

    /**
     * Writes a plan's due charges and moves its watermark, both or neither.
     *
     * The two halves have to be one write. Charges written without the watermark
     * moving are charges the next run writes all over again, and a phone can be
     * killed between any two statements — so the notes and the record of having
     * written them go into the same transaction.
     *
     * That is why a subscription DAO inserts notes: not because the tables
     * belong together, but because this one guarantee does not survive being
     * split across two of them.
     */
    @Transaction
    suspend fun writeCharges(
        id: String,
        chargedThrough: Long,
        charges: List<TransactionEntity>,
    ) {
        insertCharges(charges)
        markChargedThrough(id, chargedThrough)
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCharges(charges: List<TransactionEntity>)

    /**
     * Pausing also settles the watermark up to now.
     *
     * Without that, resuming next month would write every bill that came due
     * while the plan was paused — and the whole point of pausing is that those
     * bills were not paid.
     */
    @Query(
        """
        UPDATE subscriptions
        SET paused_at = :pausedAt, charged_through = :chargedThrough
        WHERE id = :id
        """
    )
    suspend fun pause(id: String, pausedAt: Long, chargedThrough: Long)

    /** Resuming starts the clock again from now, for the same reason. */
    @Query(
        """
        UPDATE subscriptions
        SET paused_at = NULL, charged_through = :chargedThrough
        WHERE id = :id
        """
    )
    suspend fun resume(id: String, chargedThrough: Long)

    @Query("UPDATE subscriptions SET deleted_at = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE subscriptions SET deleted_at = NULL WHERE id = :id")
    suspend fun restore(id: String)

    /**
     * Follows the money when a bank is closed onto another one, exactly as the
     * notes and the transfers recorded there do.
     */
    @Query("UPDATE subscriptions SET bank = :toBankId WHERE bank = :fromBankId")
    suspend fun reassignBank(fromBankId: String, toBankId: String): Int

    @Query("DELETE FROM subscriptions WHERE deleted_at IS NOT NULL AND deleted_at < :cutoff")
    suspend fun purgeDeletedBefore(cutoff: Long): Int
}
