package com.esa.moneytracker.data.export

import com.esa.moneytracker.data.local.SubscriptionEntity
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Subscription
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The wire shape of one recurring bill.
 *
 * `charged_through` travels with it, and that is the field that matters most on
 * the way back in. Restoring a plan without it would make the app think none of
 * its bills had ever been written, and the next launch would charge every one of
 * them again on top of the notes the same backup just restored.
 *
 * Like every other record here it carries human labels beside the machine ids,
 * so the file says "Langganan • BCA • setiap tanggal 2" rather than three UUIDs.
 */
@Serializable
data class SubscriptionExportRecord(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    @SerialName("amount") val amount: Long,
    @SerialName("category") val category: String,
    @SerialName("category_label") val categoryLabel: String = "",
    @SerialName("pocket") val pocket: String,
    @SerialName("pocket_label") val pocketLabel: String = "",
    /** Bank id that pays it; empty means Tunai. */
    @SerialName("bank") val bank: String = "",
    @SerialName("bank_label") val bankLabel: String = "",
    /** [BillingCycle.id] */
    @SerialName("cycle") val cycle: String,
    @SerialName("day_of_month") val dayOfMonth: Int = 1,
    /** 1 (Monday) .. 7 (Sunday). */
    @SerialName("day_of_week") val dayOfWeek: Int = 1,
    @SerialName("time") val time: String = "09:00",
    @SerialName("charged_through_iso") val chargedThroughIso: String = "",
    @SerialName("paused_iso") val pausedIso: String = "",
    /**
     * How many bills in all before the plan stops by itself; null runs until
     * paused. Absent from files written before plans could end, which reads as
     * null — exactly what every plan in those files was.
     */
    @SerialName("total_charges") val totalCharges: Int? = null,
    /** Bills written so far; travels with the watermark for the same reason. */
    @SerialName("charges_made") val chargesMade: Int = 0,
    @SerialName("created_iso") val createdIso: String = "",
    @SerialName("updated_iso") val updatedIso: String = "",
) {
    /**
     * The row as a database record, or null when it is not usable.
     *
     * A plan with no name, no money or no start date says nothing worth keeping,
     * and a nonsense day would put its bills on a date that does not exist.
     */
    fun toEntity(): SubscriptionEntity? {
        if (id.isBlank() || name.isBlank() || amount <= 0L) return null
        val created = parse(createdIso) ?: return null
        val minutes = parseMinutes(time) ?: return null
        return SubscriptionEntity(
            id = id,
            name = name,
            amount = amount,
            category = Category.fromId(category)?.id ?: Category.LANGGANAN.id,
            pocket = Pocket.fromId(pocket).id,
            bank = bank.takeIf { it.isNotBlank() },
            cycle = BillingCycle.fromId(cycle).id,
            dayOfMonth = dayOfMonth.coerceIn(Subscription.DAYS_OF_MONTH),
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            timeMinutes = minutes,
            // Falling back to the creation stamp keeps the promise the watermark
            // makes: a plan never charges for a stretch older than itself.
            chargedThrough = (parse(chargedThroughIso) ?: created).toEpochMilli(),
            pausedAt = parse(pausedIso)?.toEpochMilli(),
            totalCharges = totalCharges?.takeIf { it > 0 }
                ?.coerceAtMost(Subscription.MAX_TOTAL_CHARGES),
            chargesMade = chargesMade.coerceAtLeast(0),
            createdAt = created.toEpochMilli(),
            updatedAt = parse(updatedIso)?.toEpochMilli(),
            deletedAt = null,
        )
    }

    companion object {
        fun from(
            entity: SubscriptionEntity,
            zone: ZoneId,
            /** Bank id to name, so the file reads without the app. */
            bankNames: Map<String, String> = emptyMap(),
        ): SubscriptionExportRecord = SubscriptionExportRecord(
            id = entity.id,
            name = entity.name,
            amount = entity.amount,
            category = entity.category,
            categoryLabel = Category.fromId(entity.category)?.label ?: entity.category,
            pocket = entity.pocket,
            pocketLabel = Pocket.fromId(entity.pocket).label,
            bank = entity.bank.orEmpty(),
            bankLabel = entity.bank?.let { bankNames[it] ?: it }.orEmpty(),
            cycle = entity.cycle,
            dayOfMonth = entity.dayOfMonth,
            dayOfWeek = entity.dayOfWeek,
            time = "%02d:%02d".format(entity.timeMinutes / 60, entity.timeMinutes % 60),
            chargedThroughIso = iso(entity.chargedThrough, zone),
            pausedIso = entity.pausedAt?.let { iso(it, zone) }.orEmpty(),
            totalCharges = entity.totalCharges,
            chargesMade = entity.chargesMade,
            createdIso = iso(entity.createdAt, zone),
            updatedIso = entity.updatedAt?.let { iso(it, zone) }.orEmpty(),
        )

        private fun iso(epochMillis: Long, zone: ZoneId): String =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
                .format(Instant.ofEpochMilli(epochMillis).atZone(zone))

        private fun parse(value: String): Instant? =
            runCatching { Instant.from(DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(value)) }
                .getOrNull()

        /** `"09:30"` to minutes since midnight, or null when it is not a clock. */
        private fun parseMinutes(value: String): Int? {
            val parts = value.split(":")
            if (parts.size != 2) return null
            val hour = parts[0].toIntOrNull() ?: return null
            val minute = parts[1].toIntOrNull() ?: return null
            if (hour !in 0..23 || minute !in 0..59) return null
            return hour * 60 + minute
        }
    }
}
