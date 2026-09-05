package com.esa.moneytracker.data.model

import java.time.Instant

/**
 * What each pocket already held when the app started tracking.
 *
 * Kept apart from the transaction list on purpose: it is a starting point, not
 * something that happened, so it lifts the balances without ever appearing in
 * Riwayat or in the weekly analytics.
 */
data class OpeningBalances(
    val online: Long = 0L,
    val cash: Long = 0L,
    val recordedAt: Instant? = null,
) {
    val total: Long get() = online + cash
}
