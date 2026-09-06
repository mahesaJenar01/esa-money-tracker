package com.esa.moneytracker.data.export

/** What an import did, in the words the screen reports it with. */
data class ImportResult(
    val added: Int,
    val updated: Int,
    /** Rows that were unreadable and therefore left out. */
    val skipped: Int,
    val openingBalanceApplied: Boolean,
    val banksImported: Int = 0,
    /** Reconciliation marks restored, so the history knows where it was last checked. */
    val checksImported: Int = 0,
    /**
     * The bank a pre-bank file's Online balance was folded into, when that
     * happened, so the screen can say the data has been moved onto the new
     * system and name where it went.
     */
    val foldedIntoBank: String? = null,
) {
    val total: Int get() = added + updated
}
