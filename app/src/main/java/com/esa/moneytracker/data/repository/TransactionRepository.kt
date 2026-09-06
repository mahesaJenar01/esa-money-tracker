package com.esa.moneytracker.data.repository

import com.esa.moneytracker.data.export.BackupDocument
import com.esa.moneytracker.data.export.BalanceCheckExportRecord
import com.esa.moneytracker.data.export.BankExportRecord
import com.esa.moneytracker.data.export.ImportResult
import com.esa.moneytracker.data.export.TransactionExportRecord
import com.esa.moneytracker.data.export.TransferExportRecord
import com.esa.moneytracker.data.local.BalanceCheckDao
import com.esa.moneytracker.data.local.BalanceCheckEntity
import com.esa.moneytracker.data.local.BalanceCheckItemEntity
import com.esa.moneytracker.data.local.BankDao
import com.esa.moneytracker.data.local.BankEntity
import com.esa.moneytracker.data.local.OpeningBalanceDao
import com.esa.moneytracker.data.local.OpeningBalanceEntity
import com.esa.moneytracker.data.local.TransactionDao
import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.local.TransferDao
import com.esa.moneytracker.data.local.toDomain
import com.esa.moneytracker.data.local.toEntity
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.BalanceCheckItem
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

/** A bank as the first-run screen collects it, before it has an id. */
data class NewBank(
    val name: String,
    val color: BankColor,
    val amount: Long,
)

/**
 * One line of a reconciliation, as the check screen collects it.
 *
 * Only pockets the user actually counted are handed over — a bank left blank is
 * simply not part of that check, which is a different thing from a bank that was
 * counted and agreed.
 */
data class BalanceCheckEntry(
    /** Which bank was counted, or null for Tunai. */
    val bankId: String?,
    val label: String,
    /** What the app had worked out the pocket held, at the moment of the check. */
    val appBalance: Long,
    /** What the bank — or the wallet — actually said. */
    val realBalance: Long,
    /**
     * Whether a gap should be written into the history as an ordinary note.
     *
     * True is the usual answer and is what makes the two figures agree again.
     * False leaves the gap standing, for when the missing transaction is worth
     * hunting down before it is papered over.
     */
    val recordDifference: Boolean = true,
) {
    val difference: Long get() = realBalance - appBalance
}

class TransactionRepository(
    private val dao: TransactionDao,
    private val openingBalanceDao: OpeningBalanceDao,
    private val bankDao: BankDao,
    private val balanceCheckDao: BalanceCheckDao,
    private val transferDao: TransferDao,
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

    /**
     * Every reconciliation, newest first, with its per-bank lines attached.
     *
     * The lines are read in one go and paired up here rather than through a
     * Room relation: there are a handful of checks a year and a few lines each,
     * so the join costs nothing in memory and saves a query per mark while the
     * history list scrolls.
     */
    fun observeBalanceChecks(): Flow<List<BalanceCheck>> =
        combine(
            balanceCheckDao.observeAll(),
            balanceCheckDao.observeAllItems(),
        ) { checks, items ->
            val byCheck = items.groupBy { it.checkId }
            checks.map { check ->
                check.toDomain(byCheck[check.id].orEmpty().map { it.toDomain() })
            }
        }

    /**
     * Every live move between your own pockets, newest first.
     *
     * Kept apart from [observeAll] on purpose. A transfer earns and spends
     * nothing, so it has no business in the list of notes, in the analytics, or
     * in "catatan terakhir" — but it decides which bank holds the money, so the
     * balance calculation cannot do without it.
     */
    fun observeTransfers(): Flow<List<Transfer>> =
        transferDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** The bin, transfers half: deleted moves still inside their 30 days. */
    fun observeDeletedTransfers(): Flow<List<Transfer>> =
        transferDao.observeDeleted().map { rows -> rows.map { it.toDomain() } }

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
    suspend fun purgeExpiredDeleted(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(Duration.ofDays(TransactionEntity.RETENTION_DAYS)).toEpochMilli()
        return dao.purgeDeletedBefore(cutoff) + transferDao.purgeDeletedBefore(cutoff)
    }

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
            // Transfers follow the money for the same reason the notes do. A
            // move between the closed bank and its destination collapses into a
            // row pointing at itself, which is worth nothing and is dropped —
            // the two ends cancelled out, so no balance changes by removing it.
            transferDao.reassignSource(fromBankId = id, toBankId = closure.targetBankId)
            transferDao.reassignDestination(fromBankId = id, toBankId = closure.targetBankId)
            transferDao.dropSelfTransfers()
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

    // ---------------------------------------------------------------- checks

    /**
     * Writes down a reconciliation, and closes the gaps it found.
     *
     * The mark itself never moves a rupiah. What can move one is [entries]: a
     * line whose two figures disagree and that asked for it becomes an ordinary
     * note — income when the pocket holds more than the app knew about, an
     * expense when it holds less — which is exactly what makes the app agree
     * with the bank again from that moment on.
     *
     * Those notes are dated to the check itself, so they land *below* the mark
     * in the history. That is the whole grammar of the thing: everything above
     * the mark is unverified, and a note explaining a gap belongs to the stretch
     * that was just verified, not to the one that follows it.
     *
     * The note is a real, editable record with a category and a description. It
     * is deliberately not a bank correction: a correction hides in the bank's
     * arithmetic, whereas a forgotten transaction is history and belongs in
     * Riwayat where it can be found, edited, or deleted once it is remembered.
     */
    suspend fun saveBalanceCheck(
        entries: List<BalanceCheckEntry>,
        note: String = "",
        /** Null means "right now", which is what an untouched form says. */
        checkedAt: Instant? = null,
        now: Instant = Instant.now(),
    ): BalanceCheck {
        val at = checkedAt ?: now
        val checkId = UUID.randomUUID().toString()

        val items = entries.map { entry ->
            val adjustment = if (entry.recordDifference && entry.difference != 0L) {
                val income = entry.difference > 0L
                add(
                    type = if (income) TransactionType.INCOME else TransactionType.EXPENSE,
                    pocket = if (entry.bankId == null) Pocket.CASH else Pocket.ONLINE,
                    category = if (income) Category.PENDAPATAN_LAINNYA else Category.LAINNYA,
                    bankId = entry.bankId,
                    amount = abs(entry.difference),
                    description = "Selisih saldo " + entry.label,
                    occurredAt = at,
                ).id
            } else {
                null
            }

            BalanceCheckItemEntity(
                id = UUID.randomUUID().toString(),
                checkId = checkId,
                bank = entry.bankId,
                label = entry.label,
                appBalance = entry.appBalance,
                realBalance = entry.realBalance,
                adjustment = adjustment,
            )
        }

        val check = BalanceCheckEntity(
            id = checkId,
            checkedAt = at.toEpochMilli(),
            note = note.trim(),
            createdAt = now.toEpochMilli(),
        )
        balanceCheckDao.upsert(check)
        balanceCheckDao.upsertItems(items)

        return check.toDomain(items.map { it.toDomain() })
    }

    /**
     * Removes a mark.
     *
     * Only the mark. Any note it wrote to close a gap stays exactly where it is,
     * because that note is a claim about money that moved, not about the check —
     * deleting it would quietly change every balance since. It can be deleted on
     * its own from Riwayat if it really was wrong.
     */
    suspend fun deleteBalanceCheck(id: String) {
        balanceCheckDao.deleteItemsOf(id)
        balanceCheckDao.delete(id)
    }

    // -------------------------------------------------------------- transfers

    suspend fun findTransfer(id: String): Transfer? = transferDao.findById(id)?.toDomain()

    /**
     * Records money moved from one of your pockets to another.
     *
     * Returns null rather than writing nonsense: a move needs two different
     * ends and a positive amount. Nothing here touches income or expense — the
     * two ends cancel out, so the total across every pocket is the same rupiah
     * before and after.
     */
    suspend fun addTransfer(
        fromBankId: String?,
        toBankId: String?,
        amount: Long,
        note: String,
        /** Null means "right now", and is what makes an untouched move say so. */
        occurredAt: Instant? = null,
    ): Transfer? {
        if (fromBankId == toBankId || amount <= 0L) return null
        // One Instant for both stamps when the time was not chosen, so
        // Transfer.timeAdjusted is an exact comparison rather than a guess.
        val now = Instant.now()
        val transfer = Transfer(
            id = UUID.randomUUID().toString(),
            fromBankId = fromBankId,
            toBankId = toBankId,
            amount = amount,
            note = note.trim(),
            occurredAt = occurredAt ?: now,
            createdAt = now,
        )
        transferDao.upsert(transfer.toEntity())
        return transfer
    }

    /**
     * Rewrites a transfer in place, leaving it where it sits in the history.
     *
     * Same rule as a note: [Transfer.occurredAt] only moves when the user moves
     * it, and the edit leaves [Transfer.updatedAt] behind instead.
     */
    suspend fun updateTransfer(
        original: Transfer,
        fromBankId: String?,
        toBankId: String?,
        amount: Long,
        note: String,
        occurredAt: Instant = original.occurredAt,
        now: Instant = Instant.now(),
    ): Transfer? {
        if (fromBankId == toBankId || amount <= 0L) return null
        val updated = original.copy(
            fromBankId = fromBankId,
            toBankId = toBankId,
            amount = amount,
            note = note.trim(),
            occurredAt = occurredAt,
            updatedAt = now,
        )
        transferDao.upsert(updated.toEntity())
        return updated
    }

    /** Moves a transfer to the bin; the money goes back where it came from. */
    suspend fun deleteTransfer(id: String, now: Instant = Instant.now()) =
        transferDao.softDelete(id, now.toEpochMilli())

    suspend fun restoreTransfer(id: String) = transferDao.restore(id)

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
     * banks *are* included, because live notes still point at them, and so are
     * the balance checks: a restored history that had forgotten where it was
     * last reconciled would send the user back through every week of it.
     */
    suspend fun exportBackup(zone: ZoneId = ZoneId.systemDefault()): BackupDocument {
        val items = balanceCheckDao.getAllItems().groupBy { it.checkId }
        val bankNames = bankDao.getAll().associate { it.id to it.name }
        return BackupDocument.build(
            openingBalances = openingBalanceDao.getAll().toOpeningBalances(),
            banks = bankDao.getAll().map { BankExportRecord.from(it, zone) },
            transactions = exportSnapshot(zone),
            balanceChecks = balanceCheckDao.getAll().map { check ->
                BalanceCheckExportRecord.from(check, items[check.id].orEmpty(), zone)
            },
            transfers = transferDao.getAllOnce().map {
                TransferExportRecord.from(it, zone, bankNames)
            },
            zone = zone,
        )
    }

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

        // A check's lines are replaced wholesale rather than merged one by one:
        // they are read as a set, and a merge could otherwise leave a line from
        // an older version of the same check standing beside the new ones.
        val checks = document.balanceChecks.mapNotNull { record ->
            record.toEntity()?.let { it to record.itemEntities() }
        }
        if (checks.isNotEmpty()) {
            balanceCheckDao.upsertAll(checks.map { it.first })
            checks.forEach { (check, lines) ->
                balanceCheckDao.deleteItemsOf(check.id)
                if (lines.isNotEmpty()) balanceCheckDao.upsertItems(lines)
            }
        }

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

        // Merged by id like everything else. A file older than format version 4
        // simply has none, which is not the same as "they were deleted": an
        // import only ever adds and replaces.
        val transfers = document.transfers.mapNotNull { it.toEntity() }
        if (transfers.isNotEmpty()) transferDao.upsertAll(transfers)

        val foldedInto = normaliseBanks()

        return ImportResult(
            added = added,
            updated = entities.size - added,
            skipped = skipped,
            openingBalanceApplied = opening != null,
            banksImported = banks.size,
            checksImported = checks.size,
            transfersImported = transfers.size,
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
