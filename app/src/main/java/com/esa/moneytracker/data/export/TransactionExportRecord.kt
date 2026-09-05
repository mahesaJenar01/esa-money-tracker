package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.TransactionEntity
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one transaction when the data leaves the app.
 *
 * Kept separate from [TransactionEntity] on purpose: the database schema is free
 * to change, while this stays a stable, self-describing contract. Every field is
 * a plain string or number, and each row carries both the machine id and the
 * human label so an exported file is readable without the app.
 *
 * It is also the *import* shape — [toEntity] turns a row straight back into a
 * database record, which is why the timestamps are written in a form that can be
 * parsed exactly, not only read.
 */
@Serializable
data class TransactionExportRecord(
    @SerialName("id") val id: String,
    @SerialName("date") val date: String,
    @SerialName("time") val time: String,
    @SerialName("timestamp_iso") val timestampIso: String,
    /** When the note was first written; survives an edit, unlike the timestamp. */
    @SerialName("created_iso") val createdIso: String = "",
    /** When the note was last rewritten; empty when it never was. */
    @SerialName("updated_iso") val updatedIso: String = "",
    @SerialName("type") val type: String,
    @SerialName("type_label") val typeLabel: String,
    @SerialName("pocket") val pocket: String,
    @SerialName("pocket_label") val pocketLabel: String,
    /** [com.esa.moneytracker.data.local.BankEntity.id]; empty for cash. */
    @SerialName("bank") val bank: String = "",
    @SerialName("bank_label") val bankLabel: String = "",
    @SerialName("category") val category: String,
    @SerialName("category_label") val categoryLabel: String,
    @SerialName("amount") val amount: Long,
    @SerialName("signed_amount") val signedAmount: Long,
    @SerialName("description") val description: String,
) {
    companion object {
        /** Column order for a tabular (CSV / spreadsheet) export. */
        val COLUMNS: List<String> = listOf(
            "id", "date", "time", "timestamp_iso", "created_iso", "updated_iso",
            "type", "type_label", "pocket", "pocket_label", "bank", "bank_label",
            "category", "category_label", "amount", "signed_amount", "description",
        )

        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun from(
            entity: TransactionEntity,
            zone: ZoneId,
            /** Bank id to name, so the file reads without the app. */
            bankNames: Map<String, String> = emptyMap(),
        ): TransactionExportRecord {
            val local = Instant.ofEpochMilli(entity.occurredAt).atZone(zone)
            val type = TransactionType.fromId(entity.type)
            val pocket = Pocket.fromId(entity.pocket)
            val category = Category.fromId(entity.category)
            return TransactionExportRecord(
                id = entity.id,
                date = DATE.format(local),
                time = TIME.format(local),
                timestampIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local),
                createdIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME
                    .format(Instant.ofEpochMilli(entity.createdAt).atZone(zone)),
                updatedIso = entity.updatedAt
                    ?.let {
                        DateTimeFormatter.ISO_OFFSET_DATE_TIME
                            .format(Instant.ofEpochMilli(it).atZone(zone))
                    }
                    .orEmpty(),
                type = type.id,
                typeLabel = type.label,
                pocket = pocket.id,
                pocketLabel = pocket.label,
                bank = entity.bank.orEmpty(),
                bankLabel = entity.bank?.let { bankNames[it] }.orEmpty(),
                category = entity.category,
                categoryLabel = category?.label ?: entity.category,
                amount = entity.amount,
                signedAmount = if (type == TransactionType.INCOME) entity.amount else -entity.amount,
                description = entity.description,
            )
        }

        private fun parseInstant(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()
    }

    /** Values in [COLUMNS] order. */
    fun values(): List<String> = listOf(
        id, date, time, timestampIso, createdIso, updatedIso,
        type, typeLabel, pocket, pocketLabel, bank, bankLabel,
        category, categoryLabel, amount.toString(), signedAmount.toString(), description,
    )

    /**
     * The row as a database record, or null when it is not usable.
     *
     * An imported file is not trusted: a row without an id, without a readable
     * timestamp, or with a negative amount is skipped rather than written, so one
     * bad line can never poison the whole import.
     */
    fun toEntity(): TransactionEntity? {
        if (id.isBlank() || amount < 0L) return null
        val occurred = parseInstant(timestampIso) ?: return null
        val created = parseInstant(createdIso) ?: occurred
        val resolvedPocket = Pocket.fromId(pocket)
        return TransactionEntity(
            id = id,
            type = TransactionType.fromId(type).id,
            pocket = resolvedPocket.id,
            category = category,
            // Cash never names a bank; an older file names none at all, and the
            // import fills those in afterwards.
            bank = bank.takeIf { it.isNotBlank() && resolvedPocket == Pocket.ONLINE },
            amount = amount,
            description = description,
            occurredAt = occurred.toEpochMilli(),
            createdAt = created.toEpochMilli(),
            updatedAt = parseInstant(updatedIso)?.toEpochMilli(),
            deletedAt = null,
        )
    }
}
