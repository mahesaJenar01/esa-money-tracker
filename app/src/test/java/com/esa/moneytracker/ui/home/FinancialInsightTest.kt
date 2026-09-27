package com.esa.moneytracker.ui.home

import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.BalanceCheckItem
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class FinancialInsightTest {

    private val now = Instant.parse("2026-09-28T10:00:00Z")

    private fun transaction(
        id: String,
        type: TransactionType,
        amount: Long,
        description: String,
        category: Category,
    ) = Transaction(
        id = id,
        type = type,
        pocket = Pocket.CASH,
        category = category,
        categoryId = category.id,
        bankId = null,
        amount = amount,
        description = description,
        occurredAt = now,
        createdAt = now,
    )

    private fun check(id: String, app: Long, real: Long) = BalanceCheck(
        id = id,
        checkedAt = now,
        note = "",
        createdAt = now,
        items = listOf(
            BalanceCheckItem(
                id = "item-$id",
                checkId = id,
                bankId = null,
                label = "Tunai",
                appBalance = app,
                realBalance = real,
            ),
        ),
    )

    @Test
    fun `local insight finds largest repeated and mismatched activity`() {
        val insight = LocalFinancialInsightProvider.summarize(
            transactions = listOf(
                transaction("salary", TransactionType.INCOME, 1_000_000, "Gaji", Category.GAJI),
                transaction("coffee-1", TransactionType.EXPENSE, 50_000, "Kopi", Category.JAJAN),
                transaction("coffee-2", TransactionType.EXPENSE, 100_000, "  kopi  ", Category.JAJAN),
                transaction("rent", TransactionType.EXPENSE, 300_000, "Sewa", Category.KEBUTUHAN),
            ),
            checks = listOf(check("match", 100_000, 100_000), check("gap", 100_000, 90_000)),
        )

        assertEquals("Sewa", insight.largestExpense?.description)
        assertEquals("Gaji", insight.largestIncome?.description)
        assertEquals(2, insight.repeatedExpense?.count)
        assertEquals(150_000L, insight.repeatedExpense?.total)
        assertEquals(1, insight.balanceDifferences.mismatchCount)
        assertEquals(10_000L, insight.balanceDifferences.totalAbsoluteDifference)
        assertEquals(55, insight.savingRatePercent)
        assertTrue(insight.suggestions.isNotEmpty())
    }

    @Test
    fun `empty period stays useful without inventing facts`() {
        val insight = LocalFinancialInsightProvider.summarize(emptyList(), emptyList())

        assertEquals(0, insight.transactionCount)
        assertEquals(null, insight.largestExpense)
        assertTrue(insight.summary.contains("Belum ada transaksi"))
    }
}
