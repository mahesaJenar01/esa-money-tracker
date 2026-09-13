package com.esa.moneytracker.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        TransactionEntity::class,
        OpeningBalanceEntity::class,
        BankEntity::class,
        BalanceCheckEntity::class,
        BalanceCheckItemEntity::class,
        TransferEntity::class,
        AttachmentEntity::class,
        SubscriptionEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class MoneyDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao

    abstract fun openingBalanceDao(): OpeningBalanceDao

    abstract fun bankDao(): BankDao

    abstract fun balanceCheckDao(): BalanceCheckDao

    abstract fun transferDao(): TransferDao

    abstract fun attachmentDao(): AttachmentDao

    abstract fun subscriptionDao(): SubscriptionDao

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

        /**
         * v4 adds the reconciliation mark: the record of a balance check, and
         * one line per pocket that was counted during it.
         *
         * Nothing existing is touched and nothing is backfilled. A mark is a
         * statement that somebody sat down and compared the app against a bank,
         * and there is no honest way to invent one for a week nobody checked —
         * an install upgrading to v4 simply has no marks until the first check
         * is made.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `balance_checks` (" +
                        "`id` TEXT NOT NULL, " +
                        "`checked_at` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_checks_checked_at` " +
                        "ON `balance_checks` (`checked_at`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `balance_check_items` (" +
                        "`id` TEXT NOT NULL, " +
                        "`check_id` TEXT NOT NULL, " +
                        "`bank` TEXT, " +
                        "`label` TEXT NOT NULL, " +
                        "`app_balance` INTEGER NOT NULL, " +
                        "`real_balance` INTEGER NOT NULL, " +
                        "`adjustment` TEXT, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_check_items_check_id` " +
                        "ON `balance_check_items` (`check_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_balance_check_items_bank` " +
                        "ON `balance_check_items` (`bank`)"
                )
            }
        }

        /**
         * v5 adds moving money between your own pockets.
         *
         * A table of its own rather than another `transactions.type`: a transfer
         * changes where money sits, not how much there is, and keeping the two
         * apart is what stops it ever reaching an income or expense total.
         *
         * Purely additive, so nothing existing has to be rewritten — an install
         * that upgrades has no transfers yet, and every balance stays exactly
         * what it was.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `transfers` (" +
                        "`id` TEXT NOT NULL, " +
                        "`from_bank` TEXT, " +
                        "`to_bank` TEXT, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`note` TEXT NOT NULL, " +
                        "`occurred_at` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER, " +
                        "`deleted_at` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transfers_occurred_at` " +
                        "ON `transfers` (`occurred_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transfers_from_bank` " +
                        "ON `transfers` (`from_bank`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transfers_to_bank` " +
                        "ON `transfers` (`to_bank`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transfers_deleted_at` " +
                        "ON `transfers` (`deleted_at`)"
                )
            }
        }

        /**
         * v6 lets a note carry pictures — a struk, an invoice, a transfer
         * receipt.
         *
         * Only the description of each picture lives here. The bytes sit in the
         * app's own `attachments` folder under the file named by `file_name`,
         * because a table that held a few hundred kilobytes per row would be
         * read into memory by every query that only ever wanted a rupiah figure.
         *
         * Additive and empty on arrival: an install that upgrades has no
         * lampiran yet, and every note it already holds is untouched.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` TEXT NOT NULL, " +
                        "`transaction_id` TEXT NOT NULL, " +
                        "`source` TEXT NOT NULL, " +
                        "`file_name` TEXT NOT NULL, " +
                        "`label` TEXT NOT NULL, " +
                        "`page` INTEGER, " +
                        "`width` INTEGER NOT NULL, " +
                        "`height` INTEGER NOT NULL, " +
                        "`size_bytes` INTEGER NOT NULL, " +
                        "`position` INTEGER NOT NULL, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachments_transaction_id` " +
                        "ON `attachments` (`transaction_id`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_attachments_position` " +
                        "ON `attachments` (`position`)"
                )
            }
        }

        /**
         * v7 adds the recurring bills — the plans behind Langganan.
         *
         * The table holds plans, never money. What moves money is the ordinary
         * note the app writes when a due moment passes, and `charged_through` is
         * the single column that keeps it from writing the same one twice.
         *
         * `transactions.subscription` is the other half: a note that says which
         * plan produced it. Null on every row that already exists, which is
         * exactly right — they were all typed by hand.
         *
         * Purely additive. An install that upgrades has no plans yet and every
         * balance stays what it was.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `transactions` ADD COLUMN `subscription` TEXT")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_subscription` " +
                        "ON `transactions` (`subscription`)"
                )

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `subscriptions` (" +
                        "`id` TEXT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`amount` INTEGER NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`pocket` TEXT NOT NULL, " +
                        "`bank` TEXT, " +
                        "`cycle` TEXT NOT NULL, " +
                        "`day_of_month` INTEGER NOT NULL, " +
                        "`day_of_week` INTEGER NOT NULL, " +
                        "`time_minutes` INTEGER NOT NULL, " +
                        "`charged_through` INTEGER NOT NULL, " +
                        "`paused_at` INTEGER, " +
                        "`created_at` INTEGER NOT NULL, " +
                        "`updated_at` INTEGER, " +
                        "`deleted_at` INTEGER, " +
                        "PRIMARY KEY(`id`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subscriptions_deleted_at` " +
                        "ON `subscriptions` (`deleted_at`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_subscriptions_bank` " +
                        "ON `subscriptions` (`bank`)"
                )
            }
        }

        /**
         * v8 lets a plan end by itself: a cicilan with five payments left writes
         * five bills and stops.
         *
         * `total_charges` is null for every existing plan, which keeps each of
         * them running exactly as before. `charges_made` is backfilled from the
         * notes each plan has already written — bin included, since a charge
         * that was deleted was still a charge — so a plan that is later given an
         * end counts the bills it already wrote rather than starting from zero.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `subscriptions` ADD COLUMN `total_charges` INTEGER")
                db.execSQL(
                    "ALTER TABLE `subscriptions` ADD COLUMN `charges_made` INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE `subscriptions` SET `charges_made` = " +
                        "(SELECT COUNT(*) FROM `transactions` " +
                        "WHERE `transactions`.`subscription` = `subscriptions`.`id`)"
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
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    .build()
                    .also { instance = it }
            }
    }
}
