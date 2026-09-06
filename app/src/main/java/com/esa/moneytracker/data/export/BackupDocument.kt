package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.model.OpeningBalances
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the app knows, in one file.
 *
 * This is the shape written by an export and read back by an import, so it
 * carries the opening balances and the banks as well as the notes — restoring a
 * backup onto an empty phone has to reproduce the balances, not just the
 * history.
 *
 * Since format version 2 the Online opening balance lives in the banks, and
 * `opening_balance.online` is written as zero. A version 1 file has it the other
 * way round and no banks at all; the importer folds that figure into a single
 * bank so the restored total is the same number it was. Version 3 adds the
 * balance checks — the marks left in the history by a reconciliation.
 *
 * [formatVersion] is the promise made to older files: readers must accept any
 * version they understand and ignore fields they do not, which is why the JSON
 * parser is lenient about unknown keys.
 */
@Serializable
data class BackupDocument(
    @SerialName("app") val app: String = APP_ID,
    @SerialName("format_version") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("exported_at") val exportedAt: String = "",
    @SerialName("opening_balance") val openingBalance: OpeningBalanceRecord? = null,
    /**
     * The banks the Online money is split across.
     *
     * Absent in a format-version 1 file, which knew only a single Online
     * figure. The importer turns that figure into one bank rather than
     * refusing the file.
     */
    @SerialName("banks") val banks: List<BankExportRecord> = emptyList(),
    @SerialName("transactions") val transactions: List<TransactionExportRecord> = emptyList(),
    /**
     * Every reconciliation ever made, with the per-bank figures it recorded.
     *
     * Absent in a file written before format version 3, which simply means that
     * backup predates the mark. An empty list is never read as "the checks were
     * deleted": nothing is removed on import, only merged in.
     */
    @SerialName("balance_checks") val balanceChecks: List<BalanceCheckExportRecord> =
        emptyList(),
) {
    /** True for a file written before banks existed. */
    val preBanks: Boolean get() = formatVersion < 2 || banks.isEmpty()

    /** True when this really is one of our files rather than some other JSON. */
    val recognised: Boolean get() = app == APP_ID && formatVersion <= FORMAT_VERSION

    fun toJson(): String = json.encodeToString(this)

    companion object {
        const val APP_ID = "esa-money-tracker"
        const val FORMAT_VERSION = 3

        private val json = Json {
            prettyPrint = true
            encodeDefaults = true
            ignoreUnknownKeys = true
        }

        fun build(
            openingBalances: OpeningBalances,
            banks: List<BankExportRecord>,
            transactions: List<TransactionExportRecord>,
            balanceChecks: List<BalanceCheckExportRecord>,
            zone: ZoneId,
            now: Instant = Instant.now(),
        ): BackupDocument = BackupDocument(
            exportedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(now.atZone(zone)),
            openingBalance = OpeningBalanceRecord.from(openingBalances, zone),
            banks = banks,
            transactions = transactions,
            balanceChecks = balanceChecks,
        )

        /** Null when the text is not JSON at all; check [recognised] after that. */
        fun parse(text: String): BackupDocument? =
            runCatching { json.decodeFromString<BackupDocument>(text) }.getOrNull()
    }
}

@Serializable
data class OpeningBalanceRecord(
    @SerialName("online") val online: Long = 0L,
    @SerialName("cash") val cash: Long = 0L,
    @SerialName("recorded_iso") val recordedIso: String = "",
) {
    /** When the balance was originally set, so a restore keeps the real date. */
    val recordedAt: Instant?
        get() = runCatching {
            Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(recordedIso))
        }.getOrNull()

    companion object {
        fun from(balances: OpeningBalances, zone: ZoneId): OpeningBalanceRecord =
            OpeningBalanceRecord(
                online = balances.online,
                cash = balances.cash,
                recordedIso = balances.recordedAt
                    ?.let { DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(it.atZone(zone)) }
                    .orEmpty(),
            )
    }
}
