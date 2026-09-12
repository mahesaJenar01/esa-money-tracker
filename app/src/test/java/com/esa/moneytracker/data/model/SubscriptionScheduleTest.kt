package com.esa.moneytracker.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The two promises a subscription makes: a bill lands on the day it says, and it
 * lands exactly once.
 *
 * Both are worth a test for the same reason the transfer arithmetic is. A plan
 * that charges twice invents money leaving the account, and one that skips a
 * month hides money that left — and in either case the user has no way to tell
 * which of their own records is the wrong one.
 */
class SubscriptionScheduleTest {

    private val zone: ZoneId = ZoneId.of("Asia/Jakarta")

    private fun at(date: String, time: String = "09:00"): Instant =
        LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))
            .atZone(zone)
            .toInstant()

    private fun monthly(
        day: Int,
        amount: Long = 100_000L,
        chargedThrough: Instant,
    ) = Subscription(
        id = "sub-monthly-" + day,
        name = "YouTube",
        amount = amount,
        categoryId = Category.LANGGANAN.id,
        pocket = Pocket.ONLINE,
        bankId = "bca",
        cycle = BillingCycle.MONTHLY,
        dayOfMonth = day,
        dayOfWeek = DayOfWeek.MONDAY,
        timeOfDay = LocalTime.of(9, 0),
        chargedThrough = chargedThrough,
        createdAt = chargedThrough,
    )

    private fun weekly(
        day: DayOfWeek,
        amount: Long = 30_000L,
        chargedThrough: Instant,
    ) = Subscription(
        id = "sub-weekly",
        name = "Kopi mingguan",
        amount = amount,
        categoryId = Category.LANGGANAN.id,
        pocket = Pocket.CASH,
        bankId = null,
        cycle = BillingCycle.WEEKLY,
        dayOfMonth = 1,
        dayOfWeek = day,
        timeOfDay = LocalTime.of(9, 0),
        chargedThrough = chargedThrough,
        createdAt = chargedThrough,
    )

    @Test
    fun `a monthly bill lands on its day, once a month`() {
        val subscription = monthly(day = 2, chargedThrough = at("2026-09-01"))

        val due = subscription.dueBetween(
            after = subscription.chargedThrough,
            until = at("2026-12-05"),
            zone = zone,
        )

        assertEquals(
            listOf(at("2026-09-02"), at("2026-10-02"), at("2026-11-02"), at("2026-12-02")),
            due,
        )
    }

    @Test
    fun `the watermark is what stops a bill being written twice`() {
        val subscription = monthly(day = 2, chargedThrough = at("2026-09-01"))

        val first = subscription.dueBetween(subscription.chargedThrough, at("2026-09-20"), zone)
        assertEquals(listOf(at("2026-09-02")), first)

        // What the runner does next: the watermark moves to the last charge
        // written, and the same window then owes nothing at all.
        val settled = subscription.copy(chargedThrough = first.last())
        assertTrue(settled.dueBetween(settled.chargedThrough, at("2026-09-20"), zone).isEmpty())
    }

    @Test
    fun `a bill on the 31st falls back to the last day of a shorter month`() {
        val subscription = monthly(day = 31, chargedThrough = at("2026-01-01"))

        val due = subscription.dueBetween(
            after = subscription.chargedThrough,
            until = at("2026-04-30"),
            zone = zone,
        )

        // 2026 is not a leap year, so February gives up its 28th rather than
        // skipping the month entirely.
        assertEquals(
            listOf(at("2026-01-31"), at("2026-02-28"), at("2026-03-31"), at("2026-04-30")),
            due,
        )
    }

    @Test
    fun `a plan never charges for the stretch before it existed`() {
        // Created on the 13th, billed on the 2nd: the 2nd of this month was paid
        // before the app was told about it, so the next bill is next month's.
        val created = at("2026-09-13", "20:30")
        val subscription = monthly(day = 2, chargedThrough = created)

        assertTrue(subscription.dueBetween(created, at("2026-09-30"), zone).isEmpty())
        assertEquals(at("2026-10-02"), subscription.nextDueAfter(created, zone))
    }

    @Test
    fun `a weekly bill lands on its weekday`() {
        // 7 September 2026 is a Monday.
        val subscription = weekly(DayOfWeek.MONDAY, chargedThrough = at("2026-09-05"))

        val due = subscription.dueBetween(
            after = subscription.chargedThrough,
            until = at("2026-09-25"),
            zone = zone,
        )

        assertEquals(listOf(at("2026-09-07"), at("2026-09-14"), at("2026-09-21")), due)
        assertTrue(due.all { it.atZone(zone).dayOfWeek == DayOfWeek.MONDAY })
    }

    @Test
    fun `a due moment earlier the same day is not charged again`() {
        // The watermark sits at 09:00 on the billing day itself; the next bill is
        // a month later, not the one that has just been written.
        val subscription = monthly(day = 2, chargedThrough = at("2026-09-02", "09:00"))

        assertEquals(
            at("2026-10-02"),
            subscription.nextDueAfter(subscription.chargedThrough, zone),
        )
    }

    @Test
    fun `the catch-up limit leaves the rest owed rather than dropping it`() {
        val subscription = weekly(DayOfWeek.MONDAY, chargedThrough = at("2026-01-01"))

        val capped = subscription.dueBetween(
            after = subscription.chargedThrough,
            until = at("2026-12-31"),
            zone = zone,
            limit = 5,
        )
        assertEquals(5, capped.size)

        // Moving the watermark to what was actually written is what makes the
        // next run pick up exactly where this one stopped.
        val next = subscription.copy(chargedThrough = capped.last())
            .dueBetween(capped.last(), at("2026-12-31"), zone, limit = 5)
        assertEquals(capped.last().plusSeconds(7 * 24 * 3600), next.first())
    }

    @Test
    fun `the monthly total is what every plan costs together`() {
        val today = LocalDate.of(2026, 9, 13)
        val now = at("2026-09-13", "20:30")

        // The example from the brief: YouTube on the 2nd at 100k, Claude on the
        // 4th at 400k. Different days, one figure — half a million a month.
        val youtube = monthly(day = 2, amount = 100_000L, chargedThrough = now)
        val claude = monthly(day = 4, amount = 400_000L, chargedThrough = now)
            .copy(id = "sub-claude", name = "Claude AI")

        val totals = subscriptionTotalsOf(listOf(youtube, claude), today, now, zone)

        assertEquals(500_000L, totals.perMonth)
        assertEquals(6_000_000L, totals.perYear)
        assertEquals(2, totals.activeCount)
        // Both bills fell earlier this month, so nothing is still to come.
        assertEquals(500_000L, totals.thisMonth)
        assertEquals(0L, totals.remainingThisMonth)
    }

    @Test
    fun `a weekly plan is averaged into the monthly total, not multiplied by four`() {
        val today = LocalDate.of(2026, 9, 13)
        val now = at("2026-09-13", "20:30")
        val subscription = weekly(DayOfWeek.MONDAY, amount = 30_000L, chargedThrough = now)

        val totals = subscriptionTotalsOf(listOf(subscription), today, now, zone)

        // 30k a week is 52 weeks a year over 12 months, which is 130k a month —
        // not the 120k that "four weeks in a month" would claim.
        assertEquals(130_000L, totals.perMonth)
        assertEquals(30_000L, totals.perWeek)
        assertEquals(1_560_000L, totals.perYear)
    }

    @Test
    fun `a paused plan costs nothing until it is resumed`() {
        val today = LocalDate.of(2026, 9, 13)
        val now = at("2026-09-13", "20:30")
        val running = monthly(day = 2, amount = 100_000L, chargedThrough = now)
        val paused = monthly(day = 4, amount = 400_000L, chargedThrough = now)
            .copy(id = "sub-paused", pausedAt = now)

        val totals = subscriptionTotalsOf(listOf(running, paused), today, now, zone)

        assertEquals(100_000L, totals.perMonth)
        assertEquals(1, totals.activeCount)
        assertEquals(1, totals.pausedCount)
    }
}
