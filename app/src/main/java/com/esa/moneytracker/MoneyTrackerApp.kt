package com.esa.moneytracker

import android.app.Application
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
        }
    }
}
