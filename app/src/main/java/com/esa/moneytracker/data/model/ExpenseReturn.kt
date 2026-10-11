package com.esa.moneytracker.data.model

enum class ReturnKind(val id: String, val label: String) {
    REFUND("refund", "Refund"),
    REIMBURSEMENT("reimbursement", "Reimbursemen");

    companion object {
        fun fromId(id: String?): ReturnKind? = entries.firstOrNull { it.id == id }
    }
}

/** Returns reduce expenses on the day received, including returns of older purchases. */
val Transaction.earnedIncome: Long
    get() = if (type == TransactionType.INCOME && !isReturn) amount else 0L

val Transaction.expenseContribution: Long
    get() = when {
        isReturn -> -amount
        type == TransactionType.EXPENSE -> amount
        else -> 0L
    }

fun normalizeTags(values: List<String>): List<String> = values
    .map { it.trim().removePrefix("#").trim().replace(Regex("\\s+"), " ") }
    .filter { it.isNotBlank() }
    .distinctBy { it.lowercase(java.util.Locale.ROOT) }

fun parseTags(text: String): List<String> = normalizeTags(text.split(',', '\n'))

/** A tagged expense keeps its related returns in the group's net cost. */
fun transactionsForTag(transactions: List<Transaction>, tag: String): List<Transaction> {
    val tagged = transactions.filter { item -> item.tags.any { it.equals(tag, ignoreCase = true) } }.map { it.id }.toSet()
    return transactions.filter { it.id in tagged || (it.isReturn && it.returnOf in tagged) }
}

/** All linked totals come from the same snapshot, without storing derived amounts. */
fun resolveExpenseReturns(transactions: List<Transaction>): List<Transaction> {
    val originals = transactions.associateBy { it.id }
    val returned = transactions.filter { it.isReturn && it.deletedAt == null }
        .groupBy { it.returnOf }.mapValues { (_, items) -> items.sumOf { it.amount } }
    return transactions.map { item ->
        val expense = item.returnOf?.let(originals::get)
        item.copy(
            returnedAmount = returned[item.id] ?: 0L,
            originalExpenseDescription = expense?.description,
            category = expense?.category ?: item.category,
            categoryId = expense?.categoryId ?: item.categoryId,
        )
    }
}

/** Partial and multiple returns cannot turn a purchase into earnings. */
fun returnValidationError(
    expense: Transaction?,
    returns: List<Transaction>,
    amount: Long,
    occurredAt: java.time.Instant,
    excludingId: String? = null,
): String? {
    if (expense == null || expense.deletedAt != null || expense.type != TransactionType.EXPENSE || expense.isReturn) {
        return "Pengeluaran asal sudah tidak tersedia."
    }
    if (amount <= 0) return "Nominal harus lebih dari nol."
    if (occurredAt.isBefore(expense.occurredAt)) return "Tanggal pengembalian tidak boleh sebelum pengeluaran asal."
    var remaining = expense.amount
    for (item in returns.filter { it.returnOf == expense.id && it.id != excludingId && it.deletedAt == null }) {
        if (item.amount <= 0 || item.amount > remaining) return "Total pengembalian melebihi biaya pengeluaran asal."
        remaining -= item.amount
    }
    if (amount > remaining) return "Nominal melebihi sisa biaya yang bisa dikembalikan."
    return null
}

fun validateExpenseReturnGraph(transactions: List<Transaction>): String? {
    val originals = transactions.associateBy { it.id }
    for (item in transactions.filter { it.isReturn && it.deletedAt == null }) {
        if (item.type != TransactionType.INCOME || item.returnKind == null) return "Jenis pengembalian tidak valid."
        val error = returnValidationError(originals[item.returnOf], transactions, item.amount, item.occurredAt, item.id)
        if (error != null) return error
    }
    return null
}
