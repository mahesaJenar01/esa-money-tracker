package com.esa.moneytracker.data.model

import java.time.Instant

/**
 * The palette a bank can be given.
 *
 * [id] is the persisted / exported value; the actual [androidx.compose.ui.graphics.Color]
 * lives in the theme, because it differs between light and dark mode.
 */
enum class BankColor(val id: String, val label: String) {
    EMERALD("emerald", "Hijau"),
    SKY("sky", "Biru"),
    VIOLET("violet", "Ungu"),
    ROSE("rose", "Merah muda"),
    CORAL("coral", "Merah"),
    AMBER("amber", "Oranye"),
    TEAL("teal", "Tosca"),
    GOLD("gold", "Emas"),
    SLATE("slate", "Abu-abu");

    companion object {
        val DEFAULT = SKY

        fun fromId(id: String): BankColor = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** The colour to suggest next, so a new bank rarely repeats one already used. */
        fun suggestFor(used: Collection<BankColor>): BankColor =
            entries.firstOrNull { it !in used } ?: entries[used.size % entries.size]
    }
}

/**
 * One place the online money actually sits — a bank, an e-wallet, an account.
 *
 * The money in a bank is never a single stored number. It is
 * [openingBalance] (what was in it before the app started watching) plus
 * [adjustment] (every manual correction since) plus every transaction recorded
 * against it. That is what keeps the sum of the banks equal to the Online
 * pocket at all times instead of drifting apart.
 *
 * Closing a bank sets [archivedAt] rather than deleting the row: records that
 * happened there keep their name, and the bank simply stops counting towards
 * the balance.
 */
data class Bank(
    val id: String,
    val name: String,
    val color: BankColor,
    /** Whole rupiah in the bank before the first note was written against it. */
    val openingBalance: Long,
    /** Every manual correction, summed. Can be negative. */
    val adjustment: Long,
    val position: Int,
    val createdAt: Instant,
    val archivedAt: Instant? = null,
) {
    val archived: Boolean get() = archivedAt != null

    /** What the bank holds before any transaction is counted. */
    val baseBalance: Long get() = openingBalance + adjustment
}

/** A bank together with what it currently holds. */
data class BankBalance(
    val bank: Bank,
    val balance: Long,
    /** How many live notes were recorded against it. */
    val recordCount: Int,
) {
    val id: String get() = bank.id
    val name: String get() = bank.name
    val color: BankColor get() = bank.color
}

/**
 * Where the money for a newly added bank comes from.
 *
 * The distinction is the whole point of the question asked when a bank is
 * added: money the app has never seen lifts the Online total, money that was
 * already counted somewhere else only moves.
 */
sealed interface BankFunding {
    /** New money — the Online total goes up by the amount. */
    data object Additional : BankFunding

    /** Already counted in [sourceBankId] — the Online total stays put. */
    data class MovedFrom(val sourceBankId: String) : BankFunding
}

/** What should happen to the money when a bank is closed. */
sealed interface BankClosure {
    /** The money is gone. The Online total drops by what the bank held. */
    data object DropBalance : BankClosure

    /** The money was moved; the target bank absorbs the balance and the notes. */
    data class MoveTo(val targetBankId: String) : BankClosure
}
