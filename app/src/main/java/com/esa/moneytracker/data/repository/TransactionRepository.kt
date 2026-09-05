package com.esa.moneytracker.data.repository

import com.esa.moneytracker.data.export.BackupDocument
import com.esa.moneytracker.data.export.BankExportRecord
import com.esa.moneytracker.data.export.ImportResult
import com.esa.moneytracker.data.export.TransactionExportRecord
import com.esa.moneytracker.data.local.BankDao
import com.esa.moneytracker.data.local.BankEntity
import com.esa.moneytracker.data.local.OpeningBalanceDao
import com.esa.moneytracker.data.local.OpeningBalanceEntity
import com.esa.moneytracker.data.local.TransactionDao
import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.local.toDomain
import com.esa.moneytracker.data.local.toEntity
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** A bank as the first-run screen collects it, before it has an id. */
data class NewBank(
    val name: String,
    val color: BankColor,
    val amount: Long,
)

class TransactionRepository(
    private val dao: TransactionDao,
    private val openingBalanceDao: OpeningBalanceDao,
    private val bankDao: BankDao,
) {

    fun observeAll(): Flow<List<Transaction>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** The bin: deleted records still inside their 30-day retention window. */
    fun observeDeleted(): Flow<List<Transaction>> =
        dao.observeDeleted().map { rows -> rows.map { it.toDomain() } }

    /**
     * Every bank, closed ones included.
     *
     * Closed banks are part of the answer because an old note still names the
     * bank it happened in; whether a bank counts towards the balance is decided
     * by [com.esa.moneytracker.data.model.onlinePocketOf], not here.
     */
    fun observeBanks(): Flow<List<Bank>> =
        bankDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    fun observeOpeningBalances(): Flow<OpeningBalances> =
        openingBalanceDao.observeAll().map { rows -> rows.toOpeningBalances() }

    /**
     * True only on a genuinely empty app: no opening balance has ever been set
     * *and* not a single record exists, deleted ones included.
     *
     * That is deliberately not the same as "first launch". Updating the app or
     * reinstalling over existing data leaves both tables in place, so the
     * question is asked once — on a fresh install, or after the data is wiped.
     */
    fun observeNeedsSetup(): Flow<Boolean> =
        combine(openingBalanceDao.observeAll(), dao.observeRowCount()) { balances, rows ->
            balances.isEmpty() && rows == 0
        }

    /**
     * Answers the first-run screen: how much cash there is, and which banks hold
     * the online money.
     *
     * Written together because they are one answer. Zero banks is a perfectly
     * good answer too — what matters is that the opening-balance rows exist,
     * because that is what stops the app asking again on every launch.
     */
    suspend fun completeSetup(
        cash: Long,
        banks: List<NewBank>,
        now: Instant = Instant.now(),
    ) {
        val millis = now.toEpochMilli()
        if (banks.isNotEmpty()) {
            bankDao.upsertAll(
                banks.mapIndexed { index, bank ->
                    BankEntity(
                        id = UUID.randomUUID().toString(),
                        name = bank.name.trim(),
                        color = bank.color.id,
                        openingBalance = bank.amount,
                        adjustment = 0L,
                        position = index,
                        createdAt = millis,
                    )
                },
            )
        }
        // Online is always zero from here on: the online money lives in the
        // banks, and counting it in both places would double every rupiah.
        setOpeningBalances(online = 0L, cash = cash, now = now)
    }

    suspend fun setOpeningBalances(online: Long, cash: Long, now: Instant = Instant.now()) {
        val millis = now.toEpochMilli()
        openingBalanceDao.upsertAll(
            listOf(
                OpeningBalanceEntity(Pocket.ONLINE.id, online, millis),
                OpeningBalanceEntity(Pocket.CASH.id, cash, millis),
            ),
        )
    }

    suspend fun find(id: String): Transaction? = dao.findById(id)?.toDomain()

    suspend fun findBank(id: String): Bank? = bankDao.findById(id)?.toDomain()

    suspend fun add(
        type: TransactionType,
        pocket: Pocket,
        category: Category,
        bankId: String?,
        amount: Long,
        description: String,
        /** Null means "right now", and is what makes an untouched note say so. */
        occurredAt: Instant? = null,
    ): Transaction {
        // One Instant for both stamps when the time was not chosen, so
        // Transaction.timeAdjusted is an exact comparison rather than a guess
        // about how long the form took to submit.
        val now = Instant.now()
        val transaction = Transaction(
            id = UUID.randomUUID().toString(),
            type = type,
            pocket = pocket,
            category = category,
            categoryId = category.id,
            bankId = bankId.takeIf { pocket == Pocket.ONLINE },
            amount = amount,
            description = description.trim(),
            occurredAt = occurredAt ?: now,
            createdAt = now,
        )
        dao.upsert(transaction.toEntity())
        return transaction
    }

    /**
     * Rewrites a record in place.
     *
     * [Transaction.occurredAt] is only touched when the user actually changed
     * it, so correcting a typo in last week's note leaves it exactly where it
     * sits in the history. What the edit does leave behind is
     * [Transaction.updatedAt], which is how a note can say it was changed
     * without having to move to say it.
     */
    suspend fun update(
        original: Transaction,
        type: TransactionType,
        pocket: Pocket,
        category: Category,
        bankId: String?,
        amount: Long,
        description: String,
        occurredAt: Instant = original.occurredAt,
        now: Instant = Instant.now(),
    ): Transaction {
        val updated = original.copy(
            type = type,
            pocket = pocket,
            category = category,
            categoryId = category.id,
            bankId = bankId.takeIf { pocket == Pocket.ONLINE },
            amount = amount,
            description = description.trim(),
            occurredAt = occurredAt,
            updatedAt = now,
        )
        dao.upsert(updated.toEntity())
        return updated
    }

    /** Moves a record to the bin. It keeps its id, dates and ordering. */
    suspend fun delete(id: String, now: Instant = Instant.now()) =
        dao.softDelete(id, now.toEpochMilli())

    /** Undo: the record reappears in the exact position it was deleted from. */
    suspend fun restore(id: String) = dao.restore(id)

    /** Clears out anything that has sat in the bin for more than 30 days. */
    suspend fun purgeExpiredDeleted(now: Instant = Instant.now()): Int =
        dao.purgeDeletedBefore(
            now.minus(Duration.ofDays(TransactionEntity.RETENTION_DAYS)).toEpochMilli(),
        )

    // ------------------------------------------------------------------ banks

    /**
     * Adds a bank holding [amount].
     *
     * [funding] is the answer to the question the screen asks, and it is the
     * only thing that decides whether the Online total moves.
     * [BankFunding.Additional] is money the app has never counted, so the total
     * goes up. [BankFunding.MovedFrom] is money it has already counted under
     * another bank, so the same rupiah come off that one and the total stays
     * exactly where it was.
     */
    suspend fun addBank(
        name: String,
        color: BankColor,
        amount: Long,
        funding: BankFunding,
        now: Instant = Instant.now(),
    ): Bank {
        val entity = BankEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            color = color.id,
            openingBalance = amount,
            adjustment = 0L,
            position = bankDao.highestPosition() + 1,
            createdAt = now.toEpochMilli(),
        )
        bankDao.upsert(entity)
        if (funding is BankFunding.MovedFrom) {
            bankDao.addAdjustment(funding.sourceBankId, -amount)
        }
        return entity.toDomain()
    }

    suspend fun renameBank(id: String, name: String, color: BankColor) {
        val bank = bankDao.findById(id) ?: return
        bankDao.upsert(bank.copy(name = name.trim(), color = color.id))
    }

    /**
     * Corrects what a bank is said to hold.
     *
     * Recorded as an adjustment rather than by rewriting the opening balance:
     * the opening figure stays what the user first said it was, and the
     * correction sits as a separate number on top of it.
     */
    suspend fun correctBankBalance(id: String, currentBalance: Long, newBalance: Long) {
        if (currentBalance == newBalance) return
        bankDao.addAdjustment(id, newBalance - currentBalance)
    }

    /**
     * Closes a bank.
     *
     * The row survives either way, so notes recorded there can still say where
     * they happened. What differs is where the money went.
     * [BankClosure.MoveTo] hands both the balance and the notes to another
     * bank, leaving the Online total untouched. [BankClosure.DropBalance] says
     * the money is simply gone: the closed bank and everything recorded in it
     * stop counting, and the Online total falls by exactly what it held.
     */
    suspend fun closeBank(id: String, closure: BankClosure, now: Instant = Instant.now()) {
        val bank = bankDao.findById(id) ?: return
        if (closure is BankClosure.MoveTo && closure.targetBankId != id) {
            dao.reassignBank(fromBankId = id, toBankId = closure.targetBankId)
            bankDao.addAdjustment(
                id = closure.targetBankId,
                delta = bank.openingBalance + bank.adjustment,
            )
        }
        bankDao.archive(id, now.toEpochMilli())
    }

    /**
     * Puts online money that belongs to no bank into one, and names the bank it
     * used — or null when there was nothing to do.
     *
     * Two things reach that state: an install upgraded from before banks
     * existed, and a backup file written by that version. Both are folded into a
     * single bank named "Online" — the same total, the same history, now
     * expressed in the way the rest of the app understands. Renaming it,
     * splitting it or adding others is an ordinary edit afterwards.
     */
    suspend fun normaliseBanks(now: Instant = Instant.now()): String? {
        dao.clearBankOnCash()

        val unbanked = dao.unbankedOnlineCount()
        val onlineRow = openingBalanceDao.getAll().firstOrNull { it.pocket == Pocket.ONLINE.id }
        val onlineOpening = onlineRow?.amount ?: 0L
        if (unbanked == 0 && onlineOpening == 0L) return null

        val existing = bankDao.getAll()
        val target = existing.firstOrNull { it.id == BankEntity.LEGACY_ID }
            ?: existing.firstOrNull { it.archivedAt == null }
            ?: BankEntity(
                id = BankEntity.LEGACY_ID,
                name = BankEntity.LEGACY_NAME,
                color = BankColor.DEFAULT.id,
                openingBalance = 0L,
                adjustment = 0L,
                position = bankDao.highestPosition() + 1,
                createdAt = now.toEpochMilli(),
            )

        if (onlineOpening != 0L && onlineRow != null) {
            bankDao.upsert(target.copy(openingBalance = target.openingBalance + onlineOpening))
            // The figure lives in the bank now; leaving it in place as well
            // would count the same rupiah twice on the home screen.
            openingBalanceDao.upsertAll(listOf(onlineRow.copy(amount = 0L)))
        } else if (existing.none { it.id == target.id }) {
            bankDao.upsert(target)
        }

        if (unbanked > 0) dao.adoptUnbankedOnline(target.id)
        return target.name
    }

    // ---------------------------------------------------------- export/import

    /**
     * Everything the app knows, oldest first, in export shape.
     *
     * The single entry point for exporting, so no caller ever has to reach into
     * Room or reshape domain objects itself.
     */
    suspend fun exportSnapshot(
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<TransactionExportRecord> {
        val names = bankDao.getAll().associate { it.id to it.name }
        return dao.getAllOnce().map { TransactionExportRecord.from(it, zone, names) }
    }

    /**
     * A complete backup: the opening balances, the banks, and every live note.
     *
     * Binned notes are left out on purpose — the bin is a 30-day safety net for
     * this phone, not part of the history worth carrying to another one. Closed
     * banks *are* included, because live notes still point at them.
     */
    suspend fun exportBackup(zone: ZoneId = ZoneId.systemDefault()): BackupDocument =
        BackupDocument.build(
            openingBalances = openingBalanceDao.getAll().toOpeningBalances(),
            banks = bankDao.getAll().map { BankExportRecord.from(it, zone) },
            transactions = exportSnapshot(zone),
            zone = zone,
        )

    /**
     * Writes a backup back into the database, merging by id.
     *
     * A note whose id is already here is replaced by the version in the file and
     * anything new is added, so importing the same file twice leaves the same
     * result rather than a doubled history. Banks merge the same way, by id.
     *
     * The opening balance in the file replaces the current one, because
     * restoring a backup that did not restore the starting balances would leave
     * every total wrong. A file written before banks existed carries its whole
     * online balance in that figure and names no bank on any note; both are
     * folded into a bank afterwards, so an old backup lands on the new system
     * holding exactly the total it left with.
     */
    suspend fun importBackup(document: BackupDocument): ImportResult {
        val banks = document.banks.mapNotNull { it.toEntity() }
        if (banks.isNotEmpty()) bankDao.upsertAll(banks)

        val entities = document.transactions.mapNotNull { it.toEntity() }
        val skipped = document.transactions.size - entities.size

        val existing = dao.allIds().toSet()
        val added = entities.count { it.id !in existing }

        if (entities.isNotEmpty()) dao.upsertAll(entities)

        val opening = document.openingBalance
        if (opening != null) {
            setOpeningBalances(
                online = opening.online,
                cash = opening.cash,
                // Keep the date the balance was first set, not the date of the
                // restore, so a round trip really does come back unchanged.
                now = opening.recordedAt ?: Instant.now(),
            )
        }

        val foldedInto = normaliseBanks()

        return ImportResult(
            added = added,
            updated = entities.size - added,
            skipped = skipped,
            openingBalanceApplied = opening != null,
            banksImported = banks.size,
            foldedIntoBank = foldedInto.takeIf { document.preBanks },
        )
    }
}

private fun List<OpeningBalanceEntity>.toOpeningBalances(): OpeningBalances {
    if (isEmpty()) return OpeningBalances()
    return OpeningBalances(
        online = firstOrNull { it.pocket == Pocket.ONLINE.id }?.amount ?: 0L,
        cash = firstOrNull { it.pocket == Pocket.CASH.id }?.amount ?: 0L,
        recordedAt = Instant.ofEpochMilli(minOf { it.createdAt }),
    )
}
