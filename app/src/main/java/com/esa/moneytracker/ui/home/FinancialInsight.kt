package com.esa.moneytracker.ui.home

import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.earnedIncome
import com.esa.moneytracker.data.model.expenseContribution
import com.esa.moneytracker.util.CurrencyFormatter
import kotlin.math.absoluteValue

/** A compact fact about one transaction, safe for the UI and a future API payload. */
data class TransactionInsight(
    val description: String,
    val category: String,
    val amount: Long,
)

data class RepeatedExpenseInsight(
    val label: String,
    val count: Int,
    val total: Long,
)

data class BalanceDifferenceInsight(
    val checkCount: Int = 0,
    val mismatchCount: Int = 0,
    val totalAbsoluteDifference: Long = 0,
    val largestAbsoluteDifference: Long = 0,
)

/**
 * Structured facts shown by the local insight card today. The same shape is the
 * hand-off point for a remote AI implementation later; raw notes do not need to
 * leak into the Compose screen.
 */
data class FinancialInsight(
    val summary: String = "Belum cukup data untuk diringkas.",
    val largestExpense: TransactionInsight? = null,
    val largestIncome: TransactionInsight? = null,
    val repeatedExpense: RepeatedExpenseInsight? = null,
    val balanceDifferences: BalanceDifferenceInsight = BalanceDifferenceInsight(),
    val savingRatePercent: Int? = null,
    val suggestions: List<String> = emptyList(),
    val transactionCount: Int = 0,
)

/** Produces bounded, structured facts that can become input for the AI API later. */
fun interface FinancialInsightProvider {
    fun summarize(
        transactions: List<Transaction>,
        checks: List<BalanceCheck>,
    ): FinancialInsight
}

object LocalFinancialInsightProvider : FinancialInsightProvider {
    override fun summarize(
        transactions: List<Transaction>,
        checks: List<BalanceCheck>,
    ): FinancialInsight {
        val income = transactions.filter { it.type == TransactionType.INCOME && !it.isReturn }
        val returns = transactions.filter { it.isReturn }.groupBy { it.returnOf }
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
            .map { it.copy(amount = (it.amount - returns[it.id].orEmpty().sumOf { linked -> linked.amount }).coerceAtLeast(0)) }
            .filter { it.amount > 0 }
        val totalIncome = income.sumOf { it.amount }
        val totalExpense = transactions.sumOf { it.expenseContribution }
        val net = totalIncome - totalExpense

        val largestExpense = expenses.maxByOrNull { it.amount }?.toInsight()
        val largestIncome = income.maxByOrNull { it.amount }?.toInsight()
        val repeated = expenses
            .groupBy { it.repetitionKey() }
            .values
            .filter { it.size >= 2 }
            .map { group ->
                RepeatedExpenseInsight(
                    label = group.first().repetitionLabel(),
                    count = group.size,
                    total = group.sumOf { it.amount },
                )
            }
            .maxByOrNull { it.total }

        val mismatches = checks.filterNot { it.matched }
        val difference = BalanceDifferenceInsight(
            checkCount = checks.size,
            mismatchCount = mismatches.size,
            totalAbsoluteDifference = mismatches.sumOf { it.difference.absoluteValue },
            largestAbsoluteDifference = mismatches.maxOfOrNull { it.difference.absoluteValue } ?: 0,
        )
        val savingRate = if (totalIncome > 0 && net <= totalIncome) {
            ((net.toDouble() / totalIncome.toDouble()) * 100).toInt().coerceIn(-999, 100)
        } else {
            null
        }

        return FinancialInsight(
            summary = summaryOf(transactions.size, totalIncome, totalExpense, net),
            largestExpense = largestExpense,
            largestIncome = largestIncome,
            repeatedExpense = repeated,
            balanceDifferences = difference,
            savingRatePercent = savingRate,
            suggestions = suggestionsOf(
                totalIncome = totalIncome,
                totalExpense = totalExpense,
                savingRate = savingRate,
                largestExpense = largestExpense,
                repeated = repeated,
                difference = difference,
            ),
            transactionCount = transactions.size,
        )
    }

    private fun Transaction.toInsight() = TransactionInsight(
        description = description.ifBlank { categoryLabel },
        category = categoryLabel,
        amount = amount,
    )

    private fun Transaction.repetitionKey(): String {
        val normalized = description.trim().lowercase().replace(Regex("\\s+"), " ")
        return normalized.ifBlank { "category:$categoryId" }
    }

    private fun Transaction.repetitionLabel(): String = description.trim().ifBlank { categoryLabel }

    private fun summaryOf(count: Int, income: Long, expense: Long, net: Long): String {
        if (count == 0) {
            return "Belum ada transaksi pada periode ini. Tambahkan catatan agar AI bisa menemukan polanya."
        }
        val flow = "Dari $count transaksi, uang masuk ${CurrencyFormatter.rupiah(income)} " +
            "dan pengeluaran bersih ${CurrencyFormatter.rupiah(expense)}."
        return when {
            net > 0L -> "$flow Arus kasmu positif ${CurrencyFormatter.rupiah(net)}."
            net < 0L -> "$flow Pengeluaran lebih besar ${CurrencyFormatter.rupiah(-net)}."
            else -> "$flow Arus kasmu seimbang."
        }
    }

    private fun suggestionsOf(
        totalIncome: Long,
        totalExpense: Long,
        savingRate: Int?,
        largestExpense: TransactionInsight?,
        repeated: RepeatedExpenseInsight?,
        difference: BalanceDifferenceInsight,
    ): List<String> = buildList {
        when {
            totalIncome == 0L && totalExpense > 0L ->
                add("Catat pemasukan pada periode yang sama agar target tabungan bisa dihitung dengan akurat.")
            savingRate != null && savingRate < 0 ->
                add("Mulai dengan menekan pengeluaran hingga arus kas kembali positif, lalu sisihkan tabungan di awal periode.")
            savingRate != null && savingRate < 20 ->
                add("Coba sisihkan 20% pemasukan lebih dulu; saat ini perkiraan porsi tersimpanmu $savingRate%.")
            savingRate != null ->
                add("Porsi tersimpanmu sekitar $savingRate%. Pertahankan dengan memindahkan tabungan segera setelah pemasukan masuk.")
        }

        repeated?.let {
            add("Tinjau ‘${it.label}’ karena ${it.count} transaksi kecilnya sudah menjadi ${CurrencyFormatter.rupiah(it.total)}.")
        } ?: largestExpense?.let {
            add("Evaluasi pengeluaran terbesar ‘${it.description}’ (${CurrencyFormatter.rupiah(it.amount)}) sebelum periode berikutnya.")
        }

        if (difference.mismatchCount > 0) {
            add("Lakukan cek saldo lebih rutin; ${difference.mismatchCount} kali cek masih menemukan selisih.")
        }
    }.take(3)
}
