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

    val hasUnassigned: Boolean get() = unassignedCount != 0 || unassigned != 0L
}

/**
 * Works out what each open bank holds, and what the Online pocket holds in all.
 *
 * Two things move a bank's balance and both are counted here: notes recorded
 * against it, and transfers in or out of it. A transfer is not income or
 * expense — it leaves the app's totals alone — but it certainly moves money
 * between banks, and a breakdown that ignored it would stop adding up the first
 * time money was shifted.
 *
 * A closed bank is left out on every count: closing one is how the app is told
 * the money is no longer there, so its balance, its notes *and* its transfers
 * stop counting. Those notes stay visible in Riwayat — history is never
 * rewritten — they simply no longer add up to a balance that does not exist.
 */
fun onlinePocketOf(
    banks: List<Bank>,
    transactions: List<Transaction>,
    transfers: List<Transfer> = emptyList(),
): OnlinePocket {
    val open = banks.filterNot { it.archived }
    val byBank = transactions
        .filter { it.pocket == Pocket.ONLINE }
        .groupBy { it.bankId }

    val balances = open.map { bank ->
        val notes = byBank[bank.id].orEmpty()
        BankBalance(
            bank = bank,
            balance = bank.baseBalance +
                notes.sumOf { it.signedAmount } +
                transfers.sumOf { it.effectOn(bank.id) },
            recordCount = notes.size,
            transferCount = transfers.count { it.touches(bank.id) },
        )
    }

    val homeless = byBank[null].orEmpty()
    return OnlinePocket(
        banks = balances,
        unassigned = homeless.sumOf { it.signedAmount },
        unassignedCount = homeless.size,
    )
}

/**
 * What the wallet holds: the opening figure, every cash note, and every rupiah
 * carried in or out of it by a transfer.
 *
 * Kept beside [onlinePocketOf] so the two pockets are worked out the same way
 * and in one place. A deposit taking money out of the wallet and a withdrawal
 * putting it back are the same row read from opposite ends.
 */
fun cashBalanceOf(
    openingCash: Long,
    transactions: List<Transaction>,
    transfers: List<Transfer> = emptyList(),
): Long = openingCash +
    transactions.filter { it.pocket == Pocket.CASH }.sumOf { it.signedAmount } +
    transfers.sumOf { it.effectOn(null) }

/** True when either end of the transfer is this pocket. */
private fun Transfer.touches(bankId: String?): Boolean =
    fromBankId == bankId || toBankId == bankId
