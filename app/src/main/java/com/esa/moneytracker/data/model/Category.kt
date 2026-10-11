package com.esa.moneytracker.data.model

/**
 * Built-in and personal categories, split by [TransactionType].
 *
 * [id] is stable across renaming, archiving, and backup. Personal definitions
 * are stored separately; all pickers receive the combined list.
 */
data class Category(
    val id: String,
    val label: String,
    val type: TransactionType,
    val archived: Boolean = false,
) {
    val custom: Boolean get() = entries.none { it.id == id }

    companion object {
        val GAJI = Category("gaji", "Gaji", TransactionType.INCOME)
        val PENDAPATAN_LAINNYA = Category("pendapatan_lainnya", "Pendapatan Lainnya", TransactionType.INCOME)
        val JAJAN = Category("jajan", "Jajan", TransactionType.EXPENSE)
        val OPERATIONAL = Category("operational", "Operational", TransactionType.EXPENSE)
        val MAKAN = Category("makan", "Makan", TransactionType.EXPENSE)
        val LANGGANAN = Category("langganan", "Langganan", TransactionType.EXPENSE)
        val KEBUTUHAN = Category("kebutuhan", "Kebutuhan", TransactionType.EXPENSE)
        val BELANJA = Category("belanja", "Belanja", TransactionType.EXPENSE)
        val LAINNYA = Category("lainnya", "Lainnya", TransactionType.EXPENSE)
        val entries = listOf(GAJI, PENDAPATAN_LAINNYA, JAJAN, OPERATIONAL, MAKAN, LANGGANAN, KEBUTUHAN, BELANJA, LAINNYA)

        fun of(type: TransactionType): List<Category> = entries.filter { it.type == type }

        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
