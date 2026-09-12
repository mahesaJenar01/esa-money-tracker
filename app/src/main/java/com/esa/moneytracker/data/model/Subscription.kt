package com.esa.moneytracker.data.model

import com.esa.moneytracker.util.IndonesianDates
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How often a subscription is billed. [id] is the persisted / exported value. */
enum class BillingCycle(val id: String, val label: String, val unit: String) {
    MONTHLY("monthly", "Bulanan", "bulan"),
    WEEKLY("weekly", "Mingguan", "minggu"),
    ;

    companion object {
        fun fromId(id: String): BillingCycle = entries.firstOrNull { it.id == id } ?: MONTHLY
    }
}

/**
 * A bill that arrives on its own, on a day you already know.
 *
 * This is **not** a record of money moving — it is the plan that says money
 * *will* move, and keeps saying it every month or every week. What it produces
 * is ordinary notes: when a due moment passes, the app writes a normal
 * [Transaction] for it, dated to the moment the bill was actually due, and that
 * note is editable and deletable like any other. No balance is ever worked out
 * from this table, which is what stops a plan and the history disagreeing.
 *
 * [chargedThrough] is the whole of the bookkeeping: every due moment on or
 * before it has already been dealt with, so a charge is written exactly once no
 * matter how often the app is opened. It starts at the moment the subscription
 * was created — the app only records bills from the day it was told about them,
 * never a backlog of ones that were paid before it knew.
 */
data class Subscription(
    val id: String,
    /** What the bill is for — becomes the description of every note it writes. */
    val name: String,
    /** Whole rupiah, always positive. A subscription is always an expense. */
    val amount: Long,
    val categoryId: String,
    val pocket: Pocket,
    /** Which bank pays it; null for cash. */
    val bankId: String?,
    val cycle: BillingCycle,
    /**
     * 1..31, used by [BillingCycle.MONTHLY].
     *
     * Clamped to the length of the month it lands in, so a bill on the 31st is
     * charged on the 28th of February rather than skipping that month.
     */
    val dayOfMonth: Int,
    /** Used by [BillingCycle.WEEKLY]. */
    val dayOfWeek: DayOfWeek,
    /** What time of day the bill lands. */
    val timeOfDay: LocalTime,
    /** Every due moment on or before this has already been written down. */
    val chargedThrough: Instant,
    /** Non-null while the plan is paused: no charge is written and none is owed. */
    val pausedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant? = null,
    /** Non-null once the plan is deleted; the notes it wrote are left alone. */
    val deletedAt: Instant? = null,
) {
    val category: Category? get() = Category.fromId(categoryId)

    val categoryLabel: String get() = category?.label ?: categoryId

    val paused: Boolean get() = pausedAt != null

    val edited: Boolean get() = updatedAt != null

    /** `"Setiap tanggal 2"` / `"Setiap hari Senin"` */
    val scheduleLabel: String
        get() = when (cycle) {
            BillingCycle.MONTHLY -> "Setiap tanggal " + dayOfMonth
            BillingCycle.WEEKLY -> "Setiap hari " + IndonesianDates.dayName(dayOfWeek)
        }

    /**
     * What this costs in a month, on average.
     *
     * A weekly bill has no exact monthly figure — there is no whole number of
     * weeks in a month — so it is converted at 52 weeks to 12 months, which is
     * the only honest yearly-average answer. A monthly bill is its own amount
     * and nothing about it is estimated.
     */
    val monthlyEquivalent: Long
        get() = when (cycle) {
            BillingCycle.MONTHLY -> amount
            BillingCycle.WEEKLY -> Math.round(amount * WEEKS_PER_YEAR / MONTHS_PER_YEAR)
        }

    /** The mirror image: what it costs in a week, monthly bills averaged out. */
    val weeklyEquivalent: Long
        get() = when (cycle) {
            BillingCycle.WEEKLY -> amount
            BillingCycle.MONTHLY -> Math.round(amount * MONTHS_PER_YEAR / WEEKS_PER_YEAR)
        }

    val yearlyEquivalent: Long
        get() = when (cycle) {
            BillingCycle.MONTHLY -> amount * MONTHS_PER_YEAR.toLong()
            BillingCycle.WEEKLY -> amount * WEEKS_PER_YEAR.toLong()
        }

    /**
     * Every date this is billed on, from [start] onwards, forever.
     *
     * Lazy on purpose: the callers all stop at a date of their own — the moment
     * now, the end of the month, the first one after the watermark — and none of
     * them wants to say in advance how many that is.
     */
    fun dueDatesFrom(start: LocalDate): Sequence<LocalDate> = when (cycle) {
        BillingCycle.MONTHLY -> {
            val first = YearMonth.from(start).let { month ->
                if (month.dueDate() < start) month.plusMonths(1) else month
            }
            generateSequence(first) { it.plusMonths(1) }.map { it.dueDate() }
        }

        BillingCycle.WEEKLY ->
            generateSequence(start.with(TemporalAdjusters.nextOrSame(dayOfWeek))) {
                it.plusWeeks(1)
            }
    }

    /** The billing dates that fall inside a window — a month, a week, a year. */
    fun dueDatesIn(from: LocalDate, toExclusive: LocalDate): List<LocalDate> =
        dueDatesFrom(from).takeWhile { it < toExclusive }.toList()

    /**
     * Every due moment strictly after [after], in order.
     *
     * Strictly, because [chargedThrough] names a moment that has already been
     * paid for: counting it again is exactly how a bill gets written twice.
     */
    fun dueMomentsAfter(after: Instant, zone: ZoneId): Sequence<Instant> =
        dueDatesFrom(after.atZone(zone).toLocalDate())
            .map { it.atTime(timeOfDay).atZone(zone).toInstant() }
            // The day `after` falls on can still hold a due moment later than it,
            // so the scan starts on that day and drops what is already behind.
            .dropWhile { !it.isAfter(after) }

    /** When the next bill lands, given everything is settled up to [after]. */
    fun nextDueAfter(after: Instant, zone: ZoneId): Instant =
        dueMomentsAfter(after, zone).first()

    /**
     * The bills that came due while nobody was looking.
     *
     * [limit] is a brake, not a rule: a plan left alone for years would
     * otherwise write hundreds of notes in one go. Whatever it leaves behind is
     * still owed and is written on the next run, because the watermark only ever
     * moves to the last charge actually made.
     */
    fun dueBetween(
        after: Instant,
        until: Instant,
        zone: ZoneId,
        limit: Int = MAX_CATCH_UP,
    ): List<Instant> =
        dueMomentsAfter(after, zone)
            .takeWhile { !it.isAfter(until) }
            .take(limit)
            .toList()

    private fun YearMonth.dueDate(): LocalDate = atDay(minOf(dayOfMonth, lengthOfMonth()))

    companion object {
        /** Days of the month a bill can be set to. */
        val DAYS_OF_MONTH: IntRange = 1..31

        /** The most charges one catch-up run will write for a single plan. */
        const val MAX_CATCH_UP = 36

        private const val WEEKS_PER_YEAR = 52.0
        private const val MONTHS_PER_YEAR = 12.0

        /** What a new plan defaults to — a time of day nobody has to think about. */
        val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)
    }
}

/** A plan together with how many notes it has actually written. */
data class SubscriptionUsage(
    val subscription: Subscription,
    val chargeCount: Int,
    /** When the next bill lands, or null while the plan is paused. */
    val nextDue: Instant?,
    /**
     * True when the bank this is paid from has been closed.
     *
     * The plan stops charging until another bank is picked, rather than writing
     * notes into a bank that no longer counts towards any balance — that would
     * quietly drop the money out of every figure in the app.
     */
    val bankMissing: Boolean = false,
) {
    val id: String get() = subscription.id
}

/**
 * What a set of plans costs, per month and per week.
 *
 * The monthly figure is the one the feature exists for: two bills on different
 * days of the month are still one number of rupiah leaving every month, and
 * that number is worth knowing before the month starts rather than after it.
 */
data class SubscriptionTotals(
    val perMonth: Long = 0,
    val perWeek: Long = 0,
    val perYear: Long = 0,
    /** Bills that actually fall inside this calendar month, whenever they are. */
    val thisMonth: Long = 0,
    /** The part of [thisMonth] that has not come due yet. */
    val remainingThisMonth: Long = 0,
    val activeCount: Int = 0,
    val pausedCount: Int = 0,
) {
    val isEmpty: Boolean get() = activeCount == 0 && pausedCount == 0
}

/**
 * Adds up what the active plans cost.
 *
 * Paused plans are left out of every figure: a plan that is not charging is not
 * money leaving, and counting it would make the total a wish rather than a
 * forecast.
 */
fun subscriptionTotalsOf(
    subscriptions: List<Subscription>,
    today: LocalDate,
    now: Instant,
    zone: ZoneId,
): SubscriptionTotals {
    val active = subscriptions.filterNot { it.paused }
    val monthStart = today.withDayOfMonth(1)
    val monthEnd = monthStart.plusMonths(1)

    var thisMonth = 0L
    var remaining = 0L
    active.forEach { subscription ->
        subscription.dueDatesIn(monthStart, monthEnd).forEach { date ->
            thisMonth += subscription.amount
            val moment = date.atTime(subscription.timeOfDay).atZone(zone).toInstant()
            if (moment.isAfter(now)) remaining += subscription.amount
        }
    }

    return SubscriptionTotals(
        perMonth = active.sumOf { it.monthlyEquivalent },
        perWeek = active.sumOf { it.weeklyEquivalent },
        perYear = active.sumOf { it.yearlyEquivalent },
        thisMonth = thisMonth,
        remainingThisMonth = remaining,
        activeCount = active.size,
        pausedCount = subscriptions.size - active.size,
    )
}
