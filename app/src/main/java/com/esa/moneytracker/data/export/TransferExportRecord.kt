package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.TransferEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one move between pockets.
 *
 * It carries the human labels next to the machine ids for the same reason every
 * other record here does: a backup should be readable without the app, and
 * `"BCA" -> "Tunai"` says what a pair of UUIDs never could.
 */
@Serializable
data class TransferExportRecord(
    @SerialName("id") val id: String,
    @SerialName("date") val date: String,
    @SerialName("time") val time: String,
    @SerialName("timestamp_iso") val timestampIso: String,
    @SerialName("created_iso") val createdIso: String = "",
    @SerialName("updated_iso") val updatedIso: String = "",
    /** Bank id the money left; empty means Tunai. */
    @SerialName("from_bank") val fromBank: String = "",
    @SerialName("from_label") val fromLabel: String = "",
    /** Bank id the money arrived in; empty means Tunai. */
    @SerialName("to_bank") val toBank: String = "",
    @SerialName("to_label") val toLabel: String = "",
    @SerialName("amount") val amount: Long,
    @SerialName("note") val note: String = "",
) {
    companion object {
        private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")

        fun from(
            entity: TransferEntity,
            zone: ZoneId,
            /** Bank id to name, so the file reads without the app. */
            bankNames: Map<String, String> = emptyMap(),
        ): TransferExportRecord {
            val local = Instant.ofEpochMilli(entity.occurredAt).atZone(zone)
            return TransferExportRecord(
                id = entity.id,
                date = DATE.format(local),
                time = TIME.format(local),
                timestampIso = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(local),
                createdIso = iso(entity.createdAt, zone),
                updatedIso = entity.updatedAt?.let { iso(it, zone) }.orEmpty(),
                fromBank = entity.fromBank.orEmpty(),
                fromLabel = label(entity.fromBank, bankNames),
                toBank = entity.toBank.orEmpty(),
                toLabel = label(entity.toBank, bankNames),
                amount = entity.amount,
                note = entity.note,
            )
        }

        private fun label(bankId: String?, bankNames: Map<String, String>): String =
            if (bankId == null) "Tunai" else bankNames[bankId] ?: bankId

        private fun iso(epochMillis: Long, zone: ZoneId): String =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

        private fun parse(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()
    }

    /**
     * The row as a database record, or null when it is not usable.
     *
     * Two ends that are both cash, or both the same bank, would be a move that
     * moves nothing; a negative amount is not a move either. None of them are
     * worth importing, so they are skipped rather than written.
     */
    fun toEntity(): TransferEntity? {
        if (id.isBlank() || amount <= 0L) return null
        val occurred = parse(timestampIso) ?: return null
        val from = fromBank.takeIf { it.isNotBlank() }
        val to = toBank.takeIf { it.isNotBlank() }
        if (from == to) return null
        return TransferEntity(
            id = id,
            fromBank = from,
            toBank = to,
            amount = amount,
            note = note,
            occurredAt = occurred.toEpochMilli(),
            createdAt = (parse(createdIso) ?: occurred).toEpochMilli(),
            updatedAt = parse(updatedIso)?.toEpochMilli(),
            deletedAt = null,
        )
    }
}
