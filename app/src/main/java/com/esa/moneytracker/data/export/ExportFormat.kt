package com.esa.moneytracker.data.export

/**
 * Turns a snapshot of the app's data into a downloadable document body.
 *
 * These are pure functions with no Android dependencies, so the screen only has
 * to decide *where* the bytes go.
 *
 * The two formats answer different questions. [JSON] is the backup: it carries
 * the opening balances alongside every note and is the only format the importer
 * reads back. [CSV] is the spreadsheet: one row per note, nothing else, meant to
 * be opened somewhere other than this app.
 */
enum class ExportFormat(
    val id: String,
    val label: String,
    val extension: String,
    val mimeType: String,
    /** Whether a file in this format can be handed back to the importer. */
    val importable: Boolean,
) {
    JSON("json", "Cadangan", "json", "application/json", importable = true) {
        override fun render(document: BackupDocument): String = document.toJson()
    },
    CSV("csv", "Spreadsheet", "csv", "text/csv", importable = false) {
        override fun render(document: BackupDocument): String = buildString {
            appendLine(TransactionExportRecord.COLUMNS.joinToString(",") { escapeCsv(it) })
            document.transactions.forEach { record ->
                appendLine(record.values().joinToString(",") { escapeCsv(it) })
            }
        }
    };

    abstract fun render(document: BackupDocument): String

    fun fileName(stamp: String): String = "esa-money-tracker-$stamp.$extension"

    companion object {
        private fun escapeCsv(value: String): String =
            if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else {
                value
            }
    }
}
