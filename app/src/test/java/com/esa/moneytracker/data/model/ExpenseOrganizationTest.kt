package com.esa.moneytracker.data.model

import com.esa.moneytracker.ui.home.LocalFinancialInsightProvider
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ExpenseOrganizationTest {
    private val at = Instant.parse("2026-10-01T05:00:00Z")
    private val travel = Category("custom_travel", "Transport", TransactionType.EXPENSE)
    private fun expense(amount: Long = 300_000L) = Transaction(
        id = "expense", type = TransactionType.EXPENSE, pocket = Pocket.CASH,
        category = travel, categoryId = travel.id, bankId = null, amount = amount,
        description = "Tiket kerja", tags = listOf("Kerja"), occurredAt = at, createdAt = at,
    )
    private fun returned(id: String, amount: Long, kind: ReturnKind = ReturnKind.REFUND) = expense().copy(
        id = id, type = TransactionType.INCOME, amount = amount, pocket = Pocket.ONLINE,
        bankId = "bank", returnOf = "expense", returnKind = kind,
        occurredAt = at.plusSeconds(86_400), description = kind.label,
    )

    @Test fun `partial returns reconcile net cost with actual balances and never become earnings`() {
        val records = resolveExpenseReturns(listOf(expense(), returned("refund", 50_000), returned("reimbursement", 100_000, ReturnKind.REIMBURSEMENT)))
        assertNull(validateExpenseReturnGraph(records))
        assertEquals(150_000L, records.first().netCost)
        assertEquals(0L, records.sumOf { it.earnedIncome })
        assertEquals(150_000L, records.sumOf { it.expenseContribution })
        assertEquals(-150_000L, records.sumOf { it.signedAmount })
        assertEquals(-300_000L, cashBalanceOf(0, records, emptyList()))
        assertEquals(150_000L, records.filter { it.pocket == Pocket.ONLINE }.sumOf { it.signedAmount })
        val insight = LocalFinancialInsightProvider.summarize(records, emptyList())
        assertNull(insight.largestIncome)
        assertEquals(150_000L, insight.largestExpense?.amount)
    }

    @Test fun `returns of earlier expenses reduce the receiving period without rewriting the original period`() {
        val original = expense()
        val later = returned("return", 300_000).copy(occurredAt = Instant.parse("2026-11-02T05:00:00Z"))
        assertEquals(300_000L, listOf(original).sumOf { it.expenseContribution })
        assertEquals(-300_000L, listOf(later).sumOf { it.expenseContribution })
        assertEquals(0L, later.earnedIncome)
        assertEquals(0L, resolveExpenseReturns(listOf(original, later)).first().netCost)
    }

    @Test fun `multiple returns and edited returns respect the remaining cost`() {
        val original = expense()
        val first = returned("first", 100_000)
        assertNull(returnValidationError(original, listOf(first), 200_000, first.occurredAt))
        assertNotNull(returnValidationError(original, listOf(first), 200_001, first.occurredAt))
        assertNull(returnValidationError(original, listOf(first), 300_000, first.occurredAt, excludingId = "first"))
        assertNotNull(validateExpenseReturnGraph(listOf(original.copy(amount = 90_000), first)))
    }

    @Test fun `deleted returns stop reducing cost and invalid restores are rejected`() {
        val original = expense()
        val first = returned("first", 250_000).copy(deletedAt = at.plusSeconds(172_800))
        val second = returned("second", 100_000)
        assertNull(validateExpenseReturnGraph(listOf(original, first, second)))
        assertEquals(200_000L, resolveExpenseReturns(listOf(original, first, second)).first().netCost)
        assertNotNull(validateExpenseReturnGraph(listOf(original, first.copy(deletedAt = null), second)))
        assertNotNull(validateExpenseReturnGraph(listOf(original.copy(deletedAt = at), second)))
    }

    @Test fun `self references missing parents early dates and malformed kinds are rejected`() {
        val original = expense()
        val valid = returned("return", 100_000)
        assertNotNull(validateExpenseReturnGraph(listOf(valid)))
        assertNotNull(validateExpenseReturnGraph(listOf(original, valid.copy(returnOf = valid.id))))
        assertNotNull(validateExpenseReturnGraph(listOf(original, valid.copy(occurredAt = at.minusSeconds(1)))))
        assertNotNull(validateExpenseReturnGraph(listOf(original, valid.copy(returnKind = null))))
        assertNotNull(validateExpenseReturnGraph(listOf(original, valid.copy(type = TransactionType.EXPENSE))))
    }

    @Test fun `refund caps do not overflow for hostile imported amounts`() {
        val huge = expense(Long.MAX_VALUE)
        val first = returned("first", Long.MAX_VALUE)
        assertNotNull(returnValidationError(huge, listOf(first, returned("second", Long.MAX_VALUE)), 1, first.occurredAt))
    }

    @Test fun `renamed and archived personal categories stay linked to every return`() {
        val renamed = travel.copy(label = "Perjalanan kerja", archived = true)
        val resolved = resolveExpenseReturns(listOf(expense().copy(category = renamed), returned("return", 50_000)))
        assertEquals("Perjalanan kerja", resolved.last().categoryLabel)
        assertEquals("Tiket kerja", resolved.last().originalExpenseDescription)
        assertEquals(travel.id, resolved.last().categoryId)
        assertTrue(resolved.last().category!!.archived)
    }

    @Test fun `tags trim deduplicate and preserve names with spaces`() {
        assertEquals(listOf("Kerja", "Liburan Bali", "Pribadi"), parseTags(" #Kerja, kerja, Liburan   Bali, , Pribadi\nKERJA"))
    }

    @Test fun `tag groups include returns even when the expense is tagged after reimbursement`() {
        val original = expense().copy(tags = listOf("Perjalanan kerja"))
        val refund = returned("return", 100_000).copy(tags = emptyList())
        val records = transactionsForTag(listOf(original, refund, expense().copy(id = "unrelated", tags = listOf("Pribadi"))), "perjalanan kerja")
        assertEquals(listOf("expense", "return"), records.map { it.id })
        assertEquals(200_000L, records.sumOf { it.expenseContribution })
    }
}
