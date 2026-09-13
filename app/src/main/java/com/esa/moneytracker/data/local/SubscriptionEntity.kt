package com.esa.moneytracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Subscription
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime

/**
 * The on-disk shape of a recurring bill.
 *
 * Flat primitives like every other table here — no converters, no foreign keys —
 * so the file stays readable by anything that can open SQLite, and an export is
 * a straight copy of the columns.
 *
 * The two day columns are both always present even though only one of them is
 * used by any given row. Keeping the unused one rather than overloading a
 * single column means switching a plan from monthly to weekly and back
 * remembers both answers, and no reader has to know which meaning applies.
 */
@Entity(
    tableName = "subscriptions",
    indices = [
        Index("deleted_at"),
        Index("bank"),
    ],
)
data class SubscriptionEntity(
    /** Client-generated UUID: stable across export, backup and re-import. */
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    /** Whole rupiah, always positive. */
    @ColumnInfo(name = "amount")
    val amount: Long,

    /** [com.esa.moneytracker.data.model.Category.id] — always an expense one. */
    @ColumnInfo(name = "category")
    val category: String,

    /** [Pocket.id] */
    @ColumnInfo(name = "pocket")
    val pocket: String,

    /** [BankEntity.id], or null for cash. */
    @ColumnInfo(name = "bank")
    val bank: String? = null,

    /** [BillingCycle.id] */
    @ColumnInfo(name = "cycle")
    val cycle: String,

    /** 1..31; clamped to the month's length when the bill is written. */
    @ColumnInfo(name = "day_of_month")
    val dayOfMonth: Int,

    /** 1 (Monday) .. 7 (Sunday), matching [DayOfWeek.getValue]. */
    @ColumnInfo(name = "day_of_week")
    val dayOfWeek: Int,

    /** Minutes since midnight — one integer instead of a parsed clock string. */
    @ColumnInfo(name = "time_minutes")
    val timeMinutes: Int,

    /**
     * Epoch millis, UTC — every due moment on or before this has been charged.
     *
     * Set to the moment the plan was created, so the app never invents a
     * backlog of bills that were already paid before it was told about them.
     */
    @ColumnInfo(name = "charged_through")
    val chargedThrough: Long,

    /** Epoch millis, UTC, or null while the plan is running. */
    @ColumnInfo(name = "paused_at")
    val pausedAt: Long? = null,

    /** How many bills the plan writes before it stops by itself; null runs forever. */
    @ColumnInfo(name = "total_charges")
    val totalCharges: Int? = null,

    /**
     * Bills written so far. Moved in the same transaction as `charged_through`,
     * so the two can never disagree about what has been charged.
     */
    @ColumnInfo(name = "charges_made", defaultValue = "0")
    val chargesMade: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long? = null,

    /**
     * Epoch millis, UTC, or null while the plan is live.
     *
     * Deleting only sets this, exactly as it does for a note: the row keeps its
     * id so an undo puts it back unchanged, and the sweep clears it for good
     * after the same 30 days.
     */
    @ColumnInfo(name = "deleted_at")
    val deletedAt: Long? = null,
)

fun SubscriptionEntity.toDomain(): Subscription = Subscription(
    id = id,
    name = name,
    amount = amount,
    categoryId = category,
    pocket = Pocket.fromId(pocket),
    bankId = bank,
    cycle = BillingCycle.fromId(cycle),
    dayOfMonth = dayOfMonth.coerceIn(Subscription.DAYS_OF_MONTH),
    dayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7)),
    timeOfDay = LocalTime.ofSecondOfDay(timeMinutes.coerceIn(0, 24 * 60 - 1) * 60L),
    chargedThrough = Instant.ofEpochMilli(chargedThrough),
    pausedAt = pausedAt?.let(Instant::ofEpochMilli),
    totalCharges = totalCharges?.coerceIn(1, Subscription.MAX_TOTAL_CHARGES),
    chargesMade = chargesMade.coerceAtLeast(0),
    createdAt = Instant.ofEpochMilli(createdAt),
    updatedAt = updatedAt?.let(Instant::ofEpochMilli),
    deletedAt = deletedAt?.let(Instant::ofEpochMilli),
)

fun Subscription.toEntity(): SubscriptionEntity = SubscriptionEntity(
    id = id,
    name = name,
    amount = amount,
    category = categoryId,
    pocket = pocket.id,
    bank = bankId.takeIf { pocket == Pocket.ONLINE },
    cycle = cycle.id,
    dayOfMonth = dayOfMonth,
    dayOfWeek = dayOfWeek.value,
    timeMinutes = timeOfDay.hour * 60 + timeOfDay.minute,
    chargedThrough = chargedThrough.toEpochMilli(),
    pausedAt = pausedAt?.toEpochMilli(),
    totalCharges = totalCharges,
    chargesMade = chargesMade,
    createdAt = createdAt.toEpochMilli(),
    updatedAt = updatedAt?.toEpochMilli(),
    deletedAt = deletedAt?.toEpochMilli(),
)
