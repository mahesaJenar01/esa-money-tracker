package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.BalanceCheckEntity
import com.esa.moneytracker.data.local.BalanceCheckItemEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one balance check, lines included.
 *
 * Nested rather than flattened into two arrays, because a check without its
 * lines says almost nothing and the two halves are written and read as one.
 *
 * Absent from any file written before format version 3, which simply means the
 * backup predates the mark — never that the checks were lost.
 */
@Serializable
data class BalanceCheckExportRecord(
    @SerialName("id") val id: String,
    @SerialName("checked_iso") val checkedIso: String,
    @SerialName("created_iso") val createdIso: String = "",
    @SerialName("note") val note: String = "",
    @SerialName("items") val items: List<BalanceCheckItemExportRecord> = emptyList(),
) {
    companion object {
        fun from(
            entity: BalanceCheckEntity,
            items: List<BalanceCheckItemEntity>,
            zone: ZoneId,
        ): BalanceCheckExportRecord = BalanceCheckExportRecord(
            id = entity.id,
            checkedIso = iso(entity.checkedAt, zone),
            createdIso = iso(entity.createdAt, zone),
            note = entity.note,
            items = items.map { BalanceCheckItemExportRecord.from(it) },
        )

        internal fun iso(epochMillis: Long, zone: ZoneId): String =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

        internal fun parse(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()
    }

    /** The check as a database record, or null when it is not usable. */
    fun toEntity(): BalanceCheckEntity? {
        if (id.isBlank()) return null
        val checked = parse(checkedIso) ?: return null
        return BalanceCheckEntity(
            id = id,
            checkedAt = checked.toEpochMilli(),
            note = note,
            createdAt = (parse(createdIso) ?: checked).toEpochMilli(),
        )
    }

    /** The lines as database records, each one skipped if it cannot be read. */
    fun itemEntities(): List<BalanceCheckItemEntity> =
        items.mapNotNull { it.toEntity(id) }
}

/** One pocket inside one check. */
@Serializable
data class BalanceCheckItemExportRecord(
    @SerialName("id") val id: String,
    /** [com.esa.moneytracker.data.local.BankEntity.id]; empty for cash. */
    @SerialName("bank") val bank: String = "",
    @SerialName("label") val label: String = "",
    /** What the app had worked out the pocket held. */
    @SerialName("app_balance") val appBalance: Long = 0L,
    /** What the bank — or the wallet — actually said. */
    @SerialName("real_balance") val realBalance: Long = 0L,
    /** The note written to close the gap; empty when none was. */
    @SerialName("adjustment") val adjustment: String = "",
) {
    companion object {
        fun from(entity: BalanceCheckItemEntity): BalanceCheckItemExportRecord =
            BalanceCheckItemExportRecord(
                id = entity.id,
                bank = entity.bank.orEmpty(),
                label = entity.label,
                appBalance = entity.appBalance,
                realBalance = entity.realBalance,
                adjustment = entity.adjustment.orEmpty(),
            )
    }

    fun toEntity(checkId: String): BalanceCheckItemEntity? {
        if (id.isBlank()) return null
        return BalanceCheckItemEntity(
            id = id,
            checkId = checkId,
            bank = bank.takeIf { it.isNotBlank() },
            label = label,
            appBalance = appBalance,
            realBalance = realBalance,
            adjustment = adjustment.takeIf { it.isNotBlank() },
        )
    }
}
