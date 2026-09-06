package com.esa.moneytracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * The one promise a transfer makes: money moves, none is created or destroyed.
 *
 * Everything else about the feature is a matter of taste. This is not — a bug
 * here would quietly invent or lose rupiah on the home screen, and the user
 * would have no way to tell which of their own records was wrong.
 */
class TransferBalanceTest {

    private val epoch = Instant.ofEpochMilli(1_000_000L)

    private fun bank(id: String, opening: Long) = Bank(
        id = id,
        name = id.uppercase(),
        color = BankColor.SKY,
        openingBalance = opening,
        adjustment = 0L,
        position = 0,
        createdAt = epoch,
    )

    private fun transfer(from: String?, to: String?, amount: Long) = Transfer(
        id = "t-" + from + "-" + to + "-" + amount,
        fromBankId = from,
        toBankId = to,
        amount = amount,
        note = "",
        occurredAt = epoch,
        createdAt = epoch,
    )

    private fun note(pocket: Pocket, bankId: String?, type: TransactionType, amount: Long) =
        Transaction(
            id = "n-" + bankId + "-" + amount,
            type = type,
            pocket = pocket,
            category = Category.LAINNYA,
            categoryId = Category.LAINNYA.id,
            bankId = bankId,
            amount = amount,
            description = "catatan",
            occurredAt = epoch,
            createdAt = epoch,
        )

    @Test
    fun `moving between banks leaves the online total alone`() {
        val banks = listOf(bank("bca", 1_000_000L), bank("jago", 200_000L))

        val before = onlinePocketOf(banks, emptyList(), emptyList())
        val after = onlinePocketOf(banks, emptyList(), listOf(transfer("bca", "jago", 300_000L)))

        assertEquals(1_200_000L, before.total)
        assertEquals("total tidak boleh berubah", before.total, after.total)
        assertEquals(700_000L, after.banks.single { it.id == "bca" }.balance)
        assertEquals(500_000L, after.banks.single { it.id == "jago" }.balance)
    }

    @Test
    fun `a deposit moves cash into the bank and nothing else`() {
        val banks = listOf(bank("bca", 1_000_000L))
        val openingCash = 800_000L
        val transfers = listOf(transfer(null, "bca", 500_000L))

        val onlineBefore = onlinePocketOf(banks, emptyList(), emptyList()).total
        val cashBefore = cashBalanceOf(openingCash, emptyList(), emptyList())

        val onlineAfter = onlinePocketOf(banks, emptyList(), transfers).total
        val cashAfter = cashBalanceOf(openingCash, emptyList(), transfers)

        assertEquals(1_500_000L, onlineAfter)
        assertEquals(300_000L, cashAfter)
        assertEquals(
            "saldo keseluruhan harus tetap",
            onlineBefore + cashBefore,
            onlineAfter + cashAfter,
        )
    }

    @Test
    fun `a withdrawal is the same row read backwards`() {
        val banks = listOf(bank("bca", 1_000_000L))
        val transfers = listOf(transfer("bca", null, 250_000L))

        assertEquals(750_000L, onlinePocketOf(banks, emptyList(), transfers).total)
        assertEquals(250_000L, cashBalanceOf(0L, emptyList(), transfers))
    }

    @Test
    fun `transfers ride alongside ordinary notes without disturbing them`() {
        val banks = listOf(bank("bca", 0L), bank("jago", 0L))
        val notes = listOf(
            note(Pocket.ONLINE, "bca", TransactionType.INCOME, 5_000_000L),
            note(Pocket.ONLINE, "bca", TransactionType.EXPENSE, 1_000_000L),
            note(Pocket.CASH, null, TransactionType.EXPENSE, 200_000L),
        )
        val transfers = listOf(transfer("bca", "jago", 1_500_000L))

        val online = onlinePocketOf(banks, notes, transfers)
        val cash = cashBalanceOf(1_000_000L, notes, transfers)

        // The notes still decide how much there is; the transfer only decides
        // where it sits.
        assertEquals(4_000_000L, online.total)
        assertEquals(2_500_000L, online.banks.single { it.id == "bca" }.balance)
        assertEquals(1_500_000L, online.banks.single { it.id == "jago" }.balance)
        assertEquals(800_000L, cash)

        // And the income and expense figures never saw the transfer at all.
        val income = notes.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
        val expense = notes.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        assertEquals(5_000_000L, income)
        assertEquals(1_200_000L, expense)
    }

    @Test
    fun `a closed bank stops counting, transfers included`() {
        val banks = listOf(
            bank("bca", 1_000_000L),
            bank("jago", 500_000L).copy(archivedAt = epoch),
        )
        val transfers = listOf(transfer("bca", "jago", 400_000L))

        val online = onlinePocketOf(banks, emptyList(), transfers)

        // The money left BCA and landed somewhere the app no longer counts,
        // which is exactly what closing a bank on "kurangi" means.
        assertEquals(600_000L, online.total)
        assertTrue(online.banks.none { it.id == "jago" })
    }

    @Test
    fun `a transfer counts once on each end and nowhere else`() {
        val move = transfer("bca", "jago", 100_000L)

        assertEquals(-100_000L, move.effectOn("bca"))
        assertEquals(100_000L, move.effectOn("jago"))
        assertEquals(0L, move.effectOn("seabank"))
        assertEquals(0L, move.effectOn(null))
        assertEquals(0L, move.effectOn("bca") + move.effectOn("jago"))
    }

    @Test
    fun `the three kinds are told apart by which end is cash`() {
        assertEquals(TransferKind.BETWEEN_BANKS, transfer("bca", "jago", 1L).kind)
        assertEquals(TransferKind.DEPOSIT, transfer(null, "bca", 1L).kind)
        assertEquals(TransferKind.WITHDRAWAL, transfer("bca", null, 1L).kind)
    }
}
