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
    /** Moves between pockets restored, so the money is in the right banks. */
    val transfersImported: Int = 0,
    /**
     * Pictures restored, counting only those whose bytes were actually in the
     * archive: a row without its file would put a lampiran on a note that can
     * never be opened, so those are dropped rather than counted.
     */
    val attachmentsImported: Int = 0,
    /**
     * Recurring bills restored, each with the watermark saying how far it had
     * already charged — without which the next launch would bill them all again.
     */
    val subscriptionsImported: Int = 0,
    /**
     * The bank a pre-bank file's Online balance was folded into, when that
     * happened, so the screen can say the data has been moved onto the new
     * system and name where it went.
     */
    val foldedIntoBank: String? = null,
) {
    val total: Int get() = added + updated
}
