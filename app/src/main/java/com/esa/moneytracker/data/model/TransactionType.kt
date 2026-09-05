package com.esa.moneytracker.data.model

/**
 * Income vs. expense.
 *
 * [id] is the value persisted in the database and written to exported files, so
 * it must stay stable even if the enum constant or the label is ever renamed.
 */
enum class TransactionType(val id: String, val label: String) {
    INCOME("income", "Pemasukan"),
    EXPENSE("expense", "Pengeluaran");

    companion object {
        fun fromId(id: String): TransactionType =
            entries.firstOrNull { it.id == id } ?: EXPENSE
    }
}
