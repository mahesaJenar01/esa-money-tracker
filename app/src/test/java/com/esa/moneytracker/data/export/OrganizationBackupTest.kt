package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.local.toDomain
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.ReturnKind
import com.esa.moneytracker.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class OrganizationBackupTest {
    private val zone = ZoneId.of("Asia/Jakarta")
    private val category = Category("custom_transport", "Transport", TransactionType.EXPENSE, archived = true)
    private fun record() = TransactionExportRecord.from(TransactionEntity(
        id = "refund", type = "income", pocket = "cash", category = category.id,
        amount = 150_000, description = "Refund tiket", occurredAt = 1_790_810_000_000,
        createdAt = 1_790_810_000_000, tags = "[\"Kerja\",\"Liburan Bali\"]",
        returnOf = "expense", returnKind = ReturnKind.REFUND.id,
    ), zone, categories = Category.entries + category)

    @Test fun `backup round trip retains custom labels archive state tags and return links`() {
        val document = BackupDocument(
            categories = listOf(CategoryExportRecord(category.id, category.label, category.type.id, category.archived)),
            transactions = listOf(record()),
        )
        val restored = BackupDocument.parse(document.toJson())!!
        assertTrue(restored.recognised)
        assertEquals(7, restored.formatVersion)
        assertEquals(category, restored.categories.single().toEntity()!!.toDomain())
        val transaction = restored.transactions.single().toEntity()!!.toDomain(Category.entries + category)
        assertEquals("Transport", transaction.categoryLabel)
        assertEquals(listOf("Kerja", "Liburan Bali"), transaction.tags)
        assertEquals("expense", transaction.returnOf)
        assertEquals(ReturnKind.REFUND, transaction.returnKind)
        assertEquals("Refund", restored.transactions.single().typeLabel)
        assertEquals(TransactionExportRecord.COLUMNS.size, record().values().size)
    }

    @Test fun `old backups still parse with empty organization fields`() {
        val document = BackupDocument.parse("""{"app":"esa-money-tracker","format_version":6,"transactions":[]} """)!!
        assertTrue(document.recognised)
        assertTrue(document.categories.isEmpty())
        val oldRecord = record().copy(returnOf = "", returnKind = "", tags = emptyList())
        val transaction = oldRecord.toEntity()!!.toDomain()
        assertFalse(transaction.isReturn)
        assertTrue(transaction.tags.isEmpty())
    }

    @Test fun `malformed return records and built in category overrides are refused`() {
        assertNull(record().copy(returnOf = "refund").toEntity())
        assertNull(record().copy(returnKind = "unknown").toEntity())
        assertNull(record().copy(type = "expense").toEntity())
        assertNull(record().copy(returnOf = "").toEntity())
        assertNull(CategoryExportRecord("gaji", "Hacked", "expense").toEntity())
        assertNull(CategoryExportRecord("custom", "", "expense").toEntity())
    }

    @Test fun `subscription backups preserve a personal category`() {
        val plan = SubscriptionExportRecord(
            id = "subscription", name = "Transport bulanan", amount = 100_000,
            category = category.id, categoryLabel = category.label,
            pocket = "cash", pocketLabel = "Tunai", cycle = "monthly", dayOfMonth = 1,
            dayOfWeek = 1, time = "09:00", chargedThroughIso = "2026-10-01T09:00:00+07:00",
            createdIso = "2026-10-01T09:00:00+07:00",
        )
        assertEquals(category.id, plan.toEntity()!!.category)
    }
}
