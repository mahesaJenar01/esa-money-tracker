package com.esa.moneytracker.data.repository

import com.esa.moneytracker.data.attachment.AttachmentDraft
import com.esa.moneytracker.data.attachment.AttachmentImport
import com.esa.moneytracker.data.attachment.AttachmentStore
import com.esa.moneytracker.data.export.AttachmentExportRecord
import com.esa.moneytracker.data.export.BackupArchive
import com.esa.moneytracker.data.export.BackupDocument
import com.esa.moneytracker.data.export.BalanceCheckExportRecord
import com.esa.moneytracker.data.export.BankExportRecord
import com.esa.moneytracker.data.export.ImportResult
import com.esa.moneytracker.data.export.SubscriptionExportRecord
import com.esa.moneytracker.data.export.TransactionExportRecord
import com.esa.moneytracker.data.export.TransferExportRecord
import com.esa.moneytracker.data.local.AttachmentDao
import com.esa.moneytracker.data.local.BalanceCheckDao
import com.esa.moneytracker.data.local.BalanceCheckEntity
import com.esa.moneytracker.data.local.BalanceCheckItemEntity
import com.esa.moneytracker.data.local.BankDao
import com.esa.moneytracker.data.local.BankEntity
import com.esa.moneytracker.data.local.OpeningBalanceDao
import com.esa.moneytracker.data.local.OpeningBalanceEntity
import com.esa.moneytracker.data.local.SubscriptionDao
import com.esa.moneytracker.data.local.TransactionDao
import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.local.TransferDao
import com.esa.moneytracker.data.local.toDomain
import com.esa.moneytracker.data.local.toEntity
import com.esa.moneytracker.data.model.Attachment
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.BalanceCheckItem
import com.esa.moneytracker.data.model.Bank
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.OpeningBalances
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Subscription
import com.esa.moneytracker.data.model.SubscriptionUsage
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.Transfer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import android.net.Uri
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
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
    private val attachmentDao: AttachmentDao,
    private val subscriptionDao: SubscriptionDao,
    /** The files behind the lampiran; the database only ever holds their names. */
    private val attachments: AttachmentStore,
) {

    /** Holds the subscription catch-up to one run at a time. See [runDueSubscriptions]. */
    private val subscriptionRuns = Mutex()

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
        /** Set only by the subscription runner; a typed note never carries one. */
        subscriptionId: String? = null,
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
            subscriptionId = subscriptionId,
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

    /**
     * Clears out anything that has sat in the bin for more than 30 days.
     *
     * The pictures of a purged note go with it, but not from here: the note's
     * row is gone by then, so its lampiran are orphans and it is
     * [sweepAttachments] that recognises them as such. Keeping the two apart
     * means the same sweep also catches what a crash or an older version left
     * behind.
     */
    suspend fun purgeExpiredDeleted(now: Instant = Instant.now()): Int {
        val cutoff = now.minus(Duration.ofDays(TransactionEntity.RETENTION_DAYS)).toEpochMilli()
        return dao.purgeDeletedBefore(cutoff) +
            transferDao.purgeDeletedBefore(cutoff) +
            subscriptionDao.purgeDeletedBefore(cutoff)
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
            // A recurring bill points at the bank that pays it, so it follows
            // the money too — otherwise the plan would go on charging a bank
            // that no longer counts towards any balance.
            subscriptionDao.reassignBank(fromBankId = id, toBankId = closure.targetBankId)
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

    // ----------------------------------------------------------- subscriptions

    /** Every live plan — the ones that are running and the ones that are paused. */
    fun observeSubscriptions(): Flow<List<Subscription>> =
        subscriptionDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /**
     * Every plan with what it has actually cost so far and when it next bills.
     *
     * The charge count comes from the notes rather than from a figure kept on
     * the plan: a charge the user deleted stopped being money that left, and a
     * stored count would go on claiming it did.
     */
    fun observeSubscriptionUsage(
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<SubscriptionUsage>> =
        combine(
            subscriptionDao.observeAll(),
            dao.observeSubscriptionCharges(),
            bankDao.observeAll(),
        ) { rows, charges, banks ->
            val counts = charges.associateBy { it.subscriptionId }
            val openBanks = banks.filter { it.archivedAt == null }.map { it.id }.toSet()
            rows.map { row ->
                val subscription = row.toDomain()
                SubscriptionUsage(
                    subscription = subscription,
                    chargeCount = counts[subscription.id]?.charges ?: 0,
                    // Read off the watermark and nothing else, so this says
                    // exactly what the catch-up run will do next rather than a
                    // second opinion about it. In the ordinary case the run has
                    // just finished and the answer is in the future; when it is
                    // not, there really is a charge waiting to be written.
                    nextDue = if (!subscription.active) {
                        null
                    } else {
                        subscription.nextDueAfter(subscription.chargedThrough, zone)
                    },
                    bankMissing = subscription.pocket == Pocket.ONLINE &&
                        subscription.bankId !in openBanks,
                )
            }
        }

    suspend fun findSubscription(id: String): Subscription? =
        subscriptionDao.findById(id)?.toDomain()

    /**
     * Starts watching a recurring bill.
     *
     * The watermark starts at [now], which is the promise the feature makes: the
     * app records bills from the moment it is told about them and never invents
     * a backlog of ones that were already paid. A bill that came due earlier
     * today is therefore not written — it is history, and history is typed in
     * through Catat where it can be seen and checked.
     */
    suspend fun addSubscription(
        name: String,
        amount: Long,
        category: Category,
        pocket: Pocket,
        bankId: String?,
        cycle: BillingCycle,
        dayOfMonth: Int,
        dayOfWeek: DayOfWeek,
        timeOfDay: LocalTime,
        /** How many bills in all before it stops by itself; null for no end. */
        totalCharges: Int?,
        now: Instant = Instant.now(),
    ): Subscription? {
        if (amount <= 0L || name.isBlank()) return null
        if (totalCharges != null && totalCharges < 1) return null
        val subscription = Subscription(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            amount = amount,
            categoryId = category.id,
            pocket = pocket,
            bankId = bankId.takeIf { pocket == Pocket.ONLINE },
            cycle = cycle,
            dayOfMonth = dayOfMonth.coerceIn(Subscription.DAYS_OF_MONTH),
            dayOfWeek = dayOfWeek,
            timeOfDay = timeOfDay,
            chargedThrough = now,
            totalCharges = totalCharges?.coerceAtMost(Subscription.MAX_TOTAL_CHARGES),
            createdAt = now,
        )
        subscriptionDao.upsert(subscription.toEntity())
        return subscription
    }

    /**
     * Rewrites a plan in place.
     *
     * The watermark is left exactly where it is, so editing never re-charges a
     * bill that was already written and never skips one that was not. Changing
     * the billing day therefore takes effect from the next due moment after
     * whatever has already been settled — which is what the form previews
     * before the change is saved.
     *
     * Notes the plan has already written are untouched. They are records of
     * money that moved on a day; rewriting them because the price went up would
     * be rewriting history.
     *
     * [totalCharges] counts the bills already written, so it can never go below
     * them. The one time the watermark does move is when a finished plan is
     * given more bills: it starts again from [now], exactly like resuming, so
     * the months it sat finished are not billed all at once.
     */
    suspend fun updateSubscription(
        original: Subscription,
        name: String,
        amount: Long,
        category: Category,
        pocket: Pocket,
        bankId: String?,
        cycle: BillingCycle,
        dayOfMonth: Int,
        dayOfWeek: DayOfWeek,
        timeOfDay: LocalTime,
        totalCharges: Int?,
        now: Instant = Instant.now(),
    ): Subscription? = subscriptionRuns.withLock {
        if (amount <= 0L || name.isBlank()) return@withLock null
        // The form was filled in from a copy read when it opened; the catch-up
        // run may have written a bill since. Rewriting the row from that copy
        // would roll the watermark back and charge the same bill twice, so the
        // bookkeeping is taken from the row as it is now, under the same lock
        // the run holds.
        val current = subscriptionDao.findById(original.id)?.toDomain() ?: original
        val total = totalCharges
            ?.coerceIn(maxOf(1, current.chargesMade), Subscription.MAX_TOTAL_CHARGES)
        val reopened = current.finished && (total == null || total > current.chargesMade)
        val updated = current.copy(
            totalCharges = total,
            chargedThrough = if (reopened) now else current.chargedThrough,
            name = name.trim(),
            amount = amount,
            categoryId = category.id,
            pocket = pocket,
            bankId = bankId.takeIf { pocket == Pocket.ONLINE },
            cycle = cycle,
            dayOfMonth = dayOfMonth.coerceIn(Subscription.DAYS_OF_MONTH),
            dayOfWeek = dayOfWeek,
            timeOfDay = timeOfDay,
            updatedAt = now,
        )
        subscriptionDao.upsert(updated.toEntity())
        updated
    }

    /**
     * Stops a plan charging, without forgetting it.
     *
     * Pausing settles the watermark to [now] and resuming settles it again, so
     * the stretch in between is never billed. That is the only reading of
     * "pause" that makes sense: a subscription you stopped paying for a month
     * did not quietly run up a month of charges.
     */
    suspend fun pauseSubscription(id: String, now: Instant = Instant.now()) =
        subscriptionDao.pause(id, now.toEpochMilli(), now.toEpochMilli())

    suspend fun resumeSubscription(id: String, now: Instant = Instant.now()) =
        subscriptionDao.resume(id, now.toEpochMilli())

    /**
     * Moves a plan to the bin, and leaves every note it wrote standing.
     *
     * Same grammar as deleting a balance-check mark: those notes are claims
     * about money that actually left, so removing them would silently change
     * every balance since. Delete them one by one from Riwayat if they really
     * were wrong.
     */
    suspend fun deleteSubscription(id: String, now: Instant = Instant.now()) =
        subscriptionDao.softDelete(id, now.toEpochMilli())

    suspend fun restoreSubscription(id: String) = subscriptionDao.restore(id)

    /**
     * Writes down every bill that has come due since the app last looked.
     *
     * This is the whole of the automation, and it runs on launch and whenever
     * the app comes back to the foreground rather than from a background alarm.
     * The result is the same either way: a charge is dated to the moment it was
     * *due*, not to the moment the app noticed, so a bill on the 2nd lands on
     * the 2nd in Riwayat even if the phone was not opened until the 5th. An
     * alarm would only have made the row appear earlier, at the cost of a
     * scheduler, a permission and a battery argument.
     *
     * A plan whose bank has been closed is skipped rather than charged: notes in
     * a closed bank count towards no balance, so writing one would quietly drop
     * the money out of every figure in the app. Its watermark stays put, so the
     * bills it missed are written as soon as another bank is picked.
     */
    suspend fun runDueSubscriptions(
        now: Instant = Instant.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int = subscriptionRuns.withLock { writeDueSubscriptions(now, zone) }

    /**
     * The run itself, only ever entered one at a time.
     *
     * The lock is not decoration. A cold start calls the catch-up from
     * `Application.onCreate` and again from the activity's `onStart` a moment
     * later, and saving a plan calls it a third time — on a multi-threaded
     * dispatcher two of those can read the same watermark before either has
     * moved it, and write the same bill twice. Serialising the runs is what makes
     * "a charge is written exactly once" true rather than merely likely.
     */
    private suspend fun writeDueSubscriptions(now: Instant, zone: ZoneId): Int {
        val candidates = subscriptionDao.getDueCandidates()
        if (candidates.isEmpty()) return 0

        val openBanks = bankDao.getAll().filter { it.archivedAt == null }.map { it.id }.toSet()
        var written = 0

        candidates.forEach { row ->
            val subscription = row.toDomain()
            if (subscription.pocket == Pocket.ONLINE && subscription.bankId !in openBanks) {
                return@forEach
            }

            val due = subscription.dueBetween(subscription.chargedThrough, now, zone)
            if (due.isEmpty()) return@forEach

            val charges = due.map { moment ->
                TransactionEntity(
                    id = UUID.randomUUID().toString(),
                    type = TransactionType.EXPENSE.id,
                    pocket = subscription.pocket.id,
                    category = subscription.categoryId,
                    bank = subscription.bankId.takeIf { subscription.pocket == Pocket.ONLINE },
                    amount = subscription.amount,
                    description = subscription.name,
                    subscription = subscription.id,
                    occurredAt = moment.toEpochMilli(),
                    createdAt = now.toEpochMilli(),
                )
            }
            // The notes and the watermark in one transaction, and the watermark
            // moves only as far as what was actually written: the catch-up limit
            // can leave bills behind, and they are still owed on the next run.
            subscriptionDao.writeCharges(
                id = subscription.id,
                chargedThrough = due.last().toEpochMilli(),
                charges = charges,
            )
            written += charges.size
        }

        return written
    }

    // ------------------------------------------------------------ attachments

    /**
     * Every lampiran in the app, grouped by the note it belongs to.
     *
     * One flow for the whole table rather than a query per row: the rows are a
     * file name and a few numbers each, and the history list would otherwise
     * fire a database read for every note that scrolls past.
     */
    fun observeAttachments(): Flow<Map<String, List<Attachment>>> =
        attachmentDao.observeAll().map { rows ->
            rows.map { it.toDomain() }.groupBy { it.transactionId }
        }

    /** The lampiran of one note, for the edit page and the viewer. */
    fun observeAttachmentsFor(transactionId: String): Flow<List<Attachment>> =
        attachmentDao.observeFor(transactionId).map { rows -> rows.map { it.toDomain() } }

    suspend fun attachmentCountFor(transactionId: String): Int =
        attachmentDao.countFor(transactionId)

    /**
     * Turns a picked photo or PDF into pictures on disk, without attaching them
     * to anything yet.
     *
     * The Catat flow has to be able to do this before the note it is describing
     * exists, so the file comes first and the row follows at [attach]. A form
     * that is abandoned leaves the file behind; [sweepAttachments] takes it away
     * on the next launch.
     */
    suspend fun stageAttachment(uri: Uri, allowance: Int): AttachmentImport =
        attachments.stage(uri, allowance)

    /** The same, for a photo the camera has just written into the cache. */
    suspend fun stageCapture(capture: File): AttachmentImport =
        attachments.stageCapture(capture)

    /** Throws away staged pictures that were never attached to a note. */
    fun discardStaged(drafts: List<AttachmentDraft>) {
        drafts.forEach { attachments.remove(it.fileName) }
    }

    /**
     * Writes the rows that make staged pictures belong to a note.
     *
     * Called after the note itself is saved, so a lampiran can never point at a
     * note that does not exist. The files are already on disk by this point —
     * this only records which note claims them, and in what order.
     */
    suspend fun attach(
        transactionId: String,
        drafts: List<AttachmentDraft>,
        now: Instant = Instant.now(),
    ): List<Attachment> {
        if (drafts.isEmpty()) return emptyList()
        val start = attachmentDao.highestPosition(transactionId) + 1
        val rows = drafts.mapIndexed { index, draft ->
            Attachment(
                id = draft.id,
                transactionId = transactionId,
                source = draft.source,
                fileName = draft.fileName,
                label = draft.label,
                page = draft.page,
                width = draft.width,
                height = draft.height,
                sizeBytes = draft.sizeBytes,
                position = start + index,
                createdAt = now,
            )
        }
        attachmentDao.upsertAll(rows.map { it.toEntity() })
        return rows
    }

    /** Removes one lampiran, row and files together. */
    suspend fun detach(attachment: Attachment) {
        attachmentDao.delete(attachment.id)
        attachments.remove(attachment.fileName)
    }

    /**
     * Housekeeping: rows whose note is gone, and files no row claims.
     *
     * Run on launch rather than continuously, because the second half of it is
     * only safe when no form is open — a picture staged by a Catat form in
     * progress has no row yet and is exactly what this would delete.
     */
    suspend fun sweepAttachments(): Int {
        val orphans = attachmentDao.orphans()
        if (orphans.isNotEmpty()) {
            attachmentDao.deleteForTransactions(orphans.map { it.transactionId }.distinct())
        }
        val keep = attachmentDao.getAllOnce().map { it.fileName }.toSet()
        return attachments.sweep(keep)
    }

    /** Redraws any small copy that went missing — after an import, above all. */
    suspend fun ensureThumbnails() {
        attachmentDao.getAllOnce().forEach { attachments.ensureThumbnail(it.toDomain()) }
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
     * banks *are* included, because live notes still point at them, and so are
     * the balance checks: a restored history that had forgotten where it was
     * last reconciled would send the user back through every week of it.
     *
     * The lampiran are described here but their bytes are not. A note in the bin
     * takes its pictures out of the backup with it, for the same reason it takes
     * itself out.
     */
    suspend fun exportBackup(zone: ZoneId = ZoneId.systemDefault()): BackupDocument {
        val items = balanceCheckDao.getAllItems().groupBy { it.checkId }
        val bankNames = bankDao.getAll().associate { it.id to it.name }
        val live = dao.getAllOnce().map { it.id }.toSet()
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
            attachments = attachmentDao.getAllOnce()
                .filter { it.transactionId in live }
                .map { AttachmentExportRecord.from(it, zone) },
            subscriptions = subscriptionDao.getAllOnce().map {
                SubscriptionExportRecord.from(it, zone, bankNames)
            },
            zone = zone,
        )
    }

    /**
     * The pictures [document] refers to, ready to be written into an archive.
     *
     * Openers rather than bytes, so an export with thirty receipts in it never
     * holds thirty photos in memory at once.
     */
    fun archiveEntries(document: BackupDocument): List<BackupArchive.Entry> =
        document.attachments.map { record ->
            BackupArchive.Entry(record.file) { attachments.openForBackup(record.file) }
        }

    /** Writes one picture out of a backup archive into the app's own folder. */
    fun restoreAttachmentFile(fileName: String, input: java.io.InputStream): Boolean =
        attachments.writeFromBackup(fileName, input)

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
     *
     * Lampiran are the one thing that is not merged blindly: a row is only kept
     * when its picture is on this phone, because a note claiming a lampiran it
     * cannot show is worse than a note with none.
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

        // A lampiran row is only worth anything when the picture it names is
        // actually on this phone. That is true when the archive has just
        // unpacked it, and also when a plain .json backup is re-imported onto
        // the phone that made it — but never for a .json carried to a new phone,
        // where restoring the rows would put an unopenable lampiran on the note.
        val known = existing + entities.map { it.id }
        val restored = document.attachments
            .mapNotNull { it.toEntity() }
            .filter { it.transactionId in known && attachments.file(it.fileName).exists() }
        if (restored.isNotEmpty()) {
            attachmentDao.upsertAll(restored)
            // The small copies are never shipped — they are derived from the
            // pictures, so they are drawn again here rather than paid for twice.
            restored.forEach { attachments.ensureThumbnail(it.toDomain()) }
        }

        // Merged by id like the rest, watermark and all. Restoring a plan
        // without its watermark would make the next launch write every bill it
        // has ever had, on top of the very notes this file just put back.
        val subscriptions = document.subscriptions.mapNotNull { it.toEntity() }
        if (subscriptions.isNotEmpty()) subscriptionDao.upsertAll(subscriptions)

        val foldedInto = normaliseBanks()

        return ImportResult(
            added = added,
            updated = entities.size - added,
            skipped = skipped,
            openingBalanceApplied = opening != null,
            banksImported = banks.size,
            checksImported = checks.size,
            transfersImported = transfers.size,
            attachmentsImported = restored.size,
            subscriptionsImported = subscriptions.size,
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
