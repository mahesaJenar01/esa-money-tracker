package com.esa.moneytracker

import android.app.Application
import com.esa.moneytracker.data.attachment.AttachmentStore
import com.esa.moneytracker.data.local.MoneyDatabase
import com.esa.moneytracker.data.repository.TransactionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The app has exactly one dependency graph and it fits in three lines, so a
 * plain service locator beats pulling in a DI framework.
 */
class MoneyTrackerApp : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: TransactionRepository by lazy {
        val database = MoneyDatabase.get(this)
        TransactionRepository(
            database.transactionDao(),
            database.openingBalanceDao(),
            database.bankDao(),
            database.balanceCheckDao(),
            database.transferDao(),
            database.attachmentDao(),
            database.subscriptionDao(),
            AttachmentStore(this),
        )
    }

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            // Emptying the bin is the app's own housekeeping, so it happens on
            // launch rather than waiting for the bin screen to be opened.
            repository.purgeExpiredDeleted()
            // The v3 migration already folds a pre-bank install into one bank.
            // Running the same check here catches what a migration cannot: data
            // that arrived from an old backup file, or an online note that was
            // somehow left without a bank.
            repository.normaliseBanks()
            // Pictures with no note behind them: what an abandoned Catat form
            // leaves staged on disk, and what a note purged from the bin leaves
            // in the table. Launch is the one moment this is safe — no form is
            // open, so nothing is half-written.
            repository.sweepAttachments()
        }
        catchUpSubscriptions()
    }

    /**
     * Writes down every recurring bill that has come due since the app last
     * looked.
     *
     * Called here and again from [MainActivity] every time the app comes back to
     * the foreground, because a process can stay alive for days and `onCreate`
     * would then only run once a week. Both calls are cheap and neither can
     * write a charge twice — the plan's own watermark decides that, not the
     * caller.
     *
     * Each charge is dated to the moment the bill was *due*, so opening the app
     * on the 5th puts a bill from the 2nd on the 2nd rather than pretending it
     * is today. That is what makes a background alarm unnecessary: an alarm
     * would only have made the row appear sooner, at the cost of a scheduler, a
     * permission and a battery argument.
     */
    fun catchUpSubscriptions() {
        scope.launch { repository.runDueSubscriptions() }
    }
}
