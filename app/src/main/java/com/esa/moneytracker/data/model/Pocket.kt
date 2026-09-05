package com.esa.moneytracker.data.model

/**
 * Where the money actually sits. [id] is the persisted / exported value.
 */
enum class Pocket(val id: String, val label: String, val description: String) {
    ONLINE("online", "Online", "E-wallet, rekening, m-banking"),
    CASH("cash", "Tunai", "Uang di dompet");

    companion object {
        fun fromId(id: String): Pocket =
            entries.firstOrNull { it.id == id } ?: CASH
    }
}
