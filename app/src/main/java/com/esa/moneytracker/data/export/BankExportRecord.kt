package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.BankEntity
import com.esa.moneytracker.data.model.BankColor
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one bank.
 *
 * It carries the two figures a balance is built from — the opening amount and
 * the corrections since — rather than the balance itself, because the balance
 * is derived from the notes in the same file and storing it as well would let
 * the two disagree after a partial merge.
 */
@Serializable
data class BankExportRecord(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("color") val color: String = BankColor.DEFAULT.id,
    @SerialName("opening_balance") val openingBalance: Long = 0L,
    @SerialName("adjustment") val adjustment: Long = 0L,
    @SerialName("position") val position: Int = 0,
    @SerialName("created_iso") val createdIso: String = "",
    /** Set once the bank has been closed; it keeps its notes but stops counting. */
    @SerialName("archived_iso") val archivedIso: String = "",
) {
    companion object {
        fun from(entity: BankEntity, zone: ZoneId): BankExportRecord = BankExportRecord(
            id = entity.id,
            name = entity.name,
            color = entity.color,
            openingBalance = entity.openingBalance,
            adjustment = entity.adjustment,
            position = entity.position,
            createdIso = iso(entity.createdAt, zone),
            archivedIso = entity.archivedAt?.let { iso(it, zone) }.orEmpty(),
        )

        private fun iso(epochMillis: Long, zone: ZoneId): String =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

        private fun parse(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()
    }

    /** The row as a database record, or null when it is not usable. */
    fun toEntity(): BankEntity? {
        if (id.isBlank() || name.isBlank()) return null
        return BankEntity(
            id = id,
            name = name.trim(),
            color = BankColor.fromId(color).id,
            openingBalance = openingBalance,
            adjustment = adjustment,
            position = position,
            createdAt = (parse(createdIso) ?: Instant.now()).toEpochMilli(),
            archivedAt = parse(archivedIso)?.toEpochMilli(),
        )
    }
}
