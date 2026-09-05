package com.esa.moneytracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TransactionEntity::class, OpeningBalanceEntity::class, BankEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun openingBalanceDao(): OpeningBalanceDao

    abstract fun bankDao(): BankDao

    companion object {
        /**
         * Fixed, unqualified name so the file is easy to locate for backup and
         * for the future "download my data" feature:
         * `context.getDatabasePath(MoneyDatabase.NAME)`.
         */
        const val NAME = "money_tracker.db"

        /**
         * v2 adds the bin (`transactions.deleted_at`) and the per-pocket opening
         * balance. Written by hand rather than falling back to a destructive
         * migration: this database holds the only copy of the user's records.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `deleted_at` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_deleted_at` " +
                        "ON `transactions` (`deleted_at`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `opening_balances` (" +
                        "`pocket` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`pocket`))"
                )
            }
        }

        /**
         * v3 splits the Online pocket into banks, and stops an edit from moving
         * a note's date — it records `updated_at` instead.
         *
         * The upgrade is deliberately not a question the user has to answer: an
         * install that already holds data is folded into one bank named
         * "Online", which takes the whole online opening balance and adopts
         * every online note ever written. Nothing changes on screen — the same
         * total, the same history — and the bank can be renamed, split or joined
         * by others afterwards.
         *
         * The online row of `opening_balances` is zeroed in the same breath,
         * because from here on the Online balance is the sum of the banks and
         * counting the old figure as well would double every rupiah.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `bank` TEXT")
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `updated_at` INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_bank` " +
                        "ON `transactions` (`bank`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `banks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`color` TEXT NOT NULL, " +
                        "`opening_balance` INTEGER NOT NULL, " +
                        "`adjustment` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`archived_at` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_banks_archived_at` " +
                        "ON `banks` (`archived_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_banks_position` " +
                        "ON `banks` (`position`)"
                )

                // Only for an install that actually holds something. A fresh
                // install never runs this migration at all, and one that was
                // updated before the opening balance was ever answered must
                // still be offered the first-run screen rather than a stray bank.
                db.execSQL(
                    "INSERT INTO `banks` " +
                        "(`id`, `name`, `color`, `opening_balance`, `adjustment`, " +
                        "`position`, `created_at`, `archived_at`) " +
                        "SELECT '${BankEntity.LEGACY_ID}', '${BankEntity.LEGACY_NAME}', 'sky', " +
                        "COALESCE((SELECT `amount` FROM `opening_balances` WHERE `pocket` = 'online'), 0), " +
                        "0, 0, " +
                        "COALESCE(" +
                        "(SELECT `created_at` FROM `opening_balances` WHERE `pocket` = 'online'), " +
                        "CAST(strftime('%s', 'now') AS INTEGER) * 1000), " +
                        "NULL " +
                        "WHERE EXISTS (SELECT 1 FROM `opening_balances`) " +
                        "   OR EXISTS (SELECT 1 FROM `transactions`)"
                )
                db.execSQL(
                    "UPDATE `transactions` SET `bank` = '${BankEntity.LEGACY_ID}' " +
                        "WHERE `pocket` = 'online' " +
                        "AND EXISTS (SELECT 1 FROM `banks` WHERE `id` = '${BankEntity.LEGACY_ID}')"
                )
                db.execSQL(
                    "UPDATE `opening_balances` SET `amount` = 0 WHERE `pocket` = 'online' " +
                        "AND EXISTS (SELECT 1 FROM `banks` WHERE `id` = '${BankEntity.LEGACY_ID}')"
                )

                // Until v3 an edit moved `occurred_at` to the moment of the edit,
                // which is the only way the two stamps could end up more than a
                // moment apart. Reading that gap as an edit stamp is what keeps
                // an old corrected note from being mislabelled as backdated, and
                // gives it the "pernah diubah" mark it always deserved.
                db.execSQL(
                    "UPDATE `transactions` SET `updated_at` = `occurred_at` " +
                        "WHERE `occurred_at` - `created_at` >= 60000"
                )
            }
        }

        @Volatile
        private var instance: MoneyDatabase? = null

        fun get(context: Context): MoneyDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    MoneyDatabase::class.java,
                    NAME,
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
    }
}
