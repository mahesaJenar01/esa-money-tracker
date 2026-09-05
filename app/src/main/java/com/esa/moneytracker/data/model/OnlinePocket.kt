package com.esa.moneytracker.data.model

/**
 * The Online pocket, broken down by bank.
 *
 * The whole point of the breakdown is that it adds up: [total] is the number the
 * home screen shows for Online, and it is nothing more than the banks plus
 * whatever could not be attributed to one. There is no second stored figure that
 * could drift away from it.
 */
data class OnlinePocket(
    val banks: List<BankBalance> = emptyList(),
    /**
     * Online money recorded against no bank at all.
     *
     * Normally zero — every online note is given a bank when it is written, and
     * upgrades and imports adopt the ones that never had one. It is counted
     * anyway rather than dropped, because losing rupiah quietly is worse than
     * showing a row that says they are homeless.
     */
    val unassigned: Long = 0L,
    val unassignedCount: Int = 0,
) {
    val total: Long get() = banks.sumOf { it.balance } + unassigned

    val hasUnassigned: Boolean get() = unassignedCount > 0 || unassigned != 0L
}

/**
 * Works out what each open bank holds, and what the Online pocket holds in all.
 *
 * A closed bank is left out on both counts: closing one is how the app is told
 * the money is no longer there, so its balance *and* the notes recorded against
 * it stop counting. Those notes stay visible in Riwayat — history is never
 * rewritten — they simply no longer add up to a balance that does not exist.
 */
fun onlinePocketOf(banks: List<Bank>, transactions: List<Transaction>): OnlinePocket {
    val open = banks.filterNot { it.archived }
    val byBank = transactions
        .filter { it.pocket == Pocket.ONLINE }
        .groupBy { it.bankId }

    val balances = open.map { bank ->
        val notes = byBank[bank.id].orEmpty()
        BankBalance(
            bank = bank,
            balance = bank.baseBalance + notes.sumOf { it.signedAmount },
            recordCount = notes.size,
        )
    }

    val homeless = byBank[null].orEmpty()
    return OnlinePocket(
        banks = balances,
        unassigned = homeless.sumOf { it.signedAmount },
        unassignedCount = homeless.size,
    )
}
