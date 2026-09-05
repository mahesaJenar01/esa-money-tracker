package com.esa.moneytracker.data.model

/**
 * The fixed set of categories, split by [TransactionType].
 *
 * [id] is the persisted / exported value. Adding a category later is a matter
 * of adding one constant here; nothing else in the app hard-codes the list.
 */
enum class Category(
    val id: String,
    val label: String,
    val type: TransactionType,
) {
    // Income
    GAJI("gaji", "Gaji", TransactionType.INCOME),
    PENDAPATAN_LAINNYA("pendapatan_lainnya", "Pendapatan Lainnya", TransactionType.INCOME),

    // Expense
    JAJAN("jajan", "Jajan", TransactionType.EXPENSE),
    OPERATIONAL("operational", "Operational", TransactionType.EXPENSE),
    MAKAN("makan", "Makan", TransactionType.EXPENSE),
    LANGGANAN("langganan", "Langganan", TransactionType.EXPENSE),
    KEBUTUHAN("kebutuhan", "Kebutuhan", TransactionType.EXPENSE),
    BELANJA("belanja", "Belanja", TransactionType.EXPENSE),
    LAINNYA("lainnya", "Lainnya", TransactionType.EXPENSE);

    companion object {
        fun of(type: TransactionType): List<Category> = entries.filter { it.type == type }

        fun fromId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}
