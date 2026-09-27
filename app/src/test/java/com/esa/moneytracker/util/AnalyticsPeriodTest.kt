package com.esa.moneytracker.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AnalyticsPeriodTest {

    @Test
    fun `monthly report follows the calendar month`() {
        val today = LocalDate.of(2026, 9, 28)

        assertEquals(LocalDate.of(2026, 9, 1), AnalyticsPeriod.MONTHLY.startOf(today))
        assertEquals(LocalDate.of(2026, 10, 1), AnalyticsPeriod.MONTHLY.endExclusiveOf(today))
        assertTrue(AnalyticsPeriod.MONTHLY.contains(LocalDate.of(2026, 9, 1), today))
        assertTrue(AnalyticsPeriod.MONTHLY.contains(LocalDate.of(2026, 9, 30), today))
        assertFalse(AnalyticsPeriod.MONTHLY.contains(LocalDate.of(2026, 10, 1), today))
    }
}
