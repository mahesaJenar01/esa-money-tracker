package com.esa.moneytracker.ui.subscriptions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.SubscriptionTotals
import com.esa.moneytracker.data.model.SubscriptionUsage
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SectionHeader
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Langganan: the bills that arrive on their own, and what they add up to.
 *
 * The summary at the top is the point of the screen. The rows underneath say
 * when each bill lands; the card says what all of them together cost every
 * month, which is the number worth knowing before the month starts rather than
 * after it has been spent.
 */
@Composable
fun SubscriptionsScreen(
    state: SubscriptionsUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Which row has its actions open. Only one at a time, so the list never
    // turns into a wall of buttons.
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding(),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Langganan", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TopBar(onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                item("totals") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        TotalsCard(state.totals)
                    }
                }

                if (state.hasStalled) {
                    item("stalled") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(12.dp))
                            StalledCard()
                        }
                    }
                }

                if (state.items.isNotEmpty()) {
                    item("list-header") {
                        Column(Modifier.padding(horizontal = 16.dp)) {
                            Spacer(Modifier.height(22.dp))
                            SectionHeader(
                                title = "Daftar langganan",
                                subtitle = subtitleFor(state.totals),
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                items(state.items, key = { it.id }) { usage ->
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                        SubscriptionRow(
                            usage = usage,
                            bankLabel = state.bankNames[usage.subscription.bankId],
                            bankColor = state.bankColors[usage.subscription.bankId],
                            today = state.today,
                            zone = zone,
                            expanded = expandedId == usage.id,
                            onToggle = {
                                expandedId = if (expandedId == usage.id) null else usage.id
                            },
                            onEdit = {
                                expandedId = null
                                onEdit(usage.id)
                            },
                            onPause = {
                                expandedId = null
                                onPause(usage.id)
                            },
                            onResume = {
                                expandedId = null
                                onResume(usage.id)
                            },
                            onDelete = {
                                expandedId = null
                                onDelete(usage.id)
                                scope.launch {
                                    val result = snackbarHostState.showSnackbar(
                                        message = "Langganan dihapus",
                                        actionLabel = "Urungkan",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        onRestore(usage.id)
                                    }
                                }
                            },
                        )
                    }
                }

                if (state.isEmpty) {
                    item("empty") { EmptyState(Modifier.padding(horizontal = 16.dp)) }
                }
            }
        }
    }
}

private fun subtitleFor(totals: SubscriptionTotals): String {
    val active = totals.activeCount.toString() + " aktif"
    return if (totals.pausedCount == 0) {
        active
    } else {
        active + " • " + totals.pausedCount + " dijeda"
    }
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Kembali",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text(
                text = "Langganan",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Tagihan rutin yang tercatat sendiri",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The accumulation.
 *
 * One figure up top because that is the question — "berapa pengeluaran pastiku
 * tiap bulan?" — and the per-week and per-year lines underneath because the same
 * money is easier to judge at a different scale.
 */
@Composable
private fun TotalsCard(totals: SubscriptionTotals) {
    val colors = MoneyTheme.colors

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Rounded.Autorenew, tint = colors.expense, size = 46.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Pengeluaran pasti tiap bulan",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = CurrencyFormatter.rupiah(totals.perMonth),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.expense,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        Row {
            Figure("Per minggu", CurrencyFormatter.rupiah(totals.perWeek), Modifier.weight(1f))
            Figure("Per tahun", CurrencyFormatter.rupiah(totals.perYear), Modifier.weight(1f))
        }

        if (!totals.isEmpty) {
            Spacer(Modifier.height(14.dp))
            Hairline()
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Bulan ini " + CurrencyFormatter.rupiah(totals.thisMonth) +
                    " jatuh tempo" +
                    if (totals.remainingThisMonth > 0L) {
                        ", " + CurrencyFormatter.rupiah(totals.remainingThisMonth) +
                            " di antaranya belum ditagih."
                    } else {
                        ", semuanya sudah tercatat."
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Said out loud, because a plan that has quietly stopped charging is a bug to the user. */
@Composable
private fun StalledCard() {
    val colors = MoneyTheme.colors

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = Icons.Rounded.WarningAmber, tint = colors.gold, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Ada langganan yang banknya sudah ditutup. Tagihannya berhenti " +
                    "dicatat sampai kamu pilih bank lain — begitu diganti, tagihan " +
                    "yang terlewat ikut masuk.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    usage: SubscriptionUsage,
    bankLabel: String?,
    bankColor: BankColor?,
    today: LocalDate,
    zone: ZoneId,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val subscription = usage.subscription
    val paused = subscription.paused
    val tint = if (paused) colors.muted else colors.forCategory(subscription.category)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(
                width = if (expanded) 2.dp else 1.dp,
                color = if (expanded) MaterialTheme.colorScheme.primary else colors.hairline,
                shape = RoundedCornerShape(20.dp),
            )
            .clickable(onClick = onToggle)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = iconFor(subscription.category), tint = tint, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = subscription.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (bankColor != null) {
                        BankDot(color = bankColor)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = subscription.scheduleLabel + " • " +
                            (bankLabel ?: subscription.pocket.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyFormatter.rupiah(subscription.amount),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (paused) colors.muted else colors.expense,
                )
                Text(
                    text = "per " + subscription.cycle.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        StatusLine(usage = usage, today = today, zone = zone)

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(12.dp))
                Hairline()
                Spacer(Modifier.height(12.dp))

                subscriptionNotes(usage, zone).forEach { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowAction(
                        icon = Icons.Rounded.EditNote,
                        label = "Ubah",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                    if (paused) {
                        RowAction(
                            icon = Icons.Rounded.PlayCircleOutline,
                            label = "Lanjutkan",
                            tint = colors.income,
                            onClick = onResume,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        RowAction(
                            icon = Icons.Rounded.PauseCircleOutline,
                            label = "Jeda",
                            tint = colors.gold,
                            onClick = onPause,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    RowAction(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "Hapus",
                        tint = colors.expense,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** The one line that says what happens next, in the words that fit the case. */
@Composable
private fun StatusLine(usage: SubscriptionUsage, today: LocalDate, zone: ZoneId) {
    val colors = MoneyTheme.colors
    val subscription = usage.subscription

    val (text, tint) = when {
        subscription.paused -> "Dijeda — tidak ada tagihan yang dicatat" to colors.muted

        usage.bankMissing ->
            "Bank pembayarnya sudah ditutup — pilih bank lain" to colors.gold

        usage.nextDue != null -> {
            val date = usage.nextDue.atZone(zone).toLocalDate()
            if (date.isBefore(today)) {
                // Only ever seen in the moment between a plan being changed and
                // the catch-up run noticing; saying so beats a date with no
                // explanation next to it.
                "Jatuh tempo " + IndonesianDates.dayAndDate(date) +
                    " — menunggu dicatat" to colors.gold
            } else {
                "Berikutnya " + IndonesianDates.dayAndDate(date) +
                    " • " + IndonesianDates.untilLabel(date, today) to
                    MaterialTheme.colorScheme.onSurfaceVariant
            }
        }

        else -> "Belum terjadwal" to colors.muted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = tint,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Everything the opened row can say about the plan itself, one line each. */
private fun subscriptionNotes(usage: SubscriptionUsage, zone: ZoneId): List<String> = buildList {
    val subscription = usage.subscription

    add("Dicatat jam " + IndonesianDates.time(subscription.timeOfDay) + " sebagai " +
        subscription.categoryLabel)

    if (subscription.cycle == BillingCycle.WEEKLY) {
        add(
            "Setara " + CurrencyFormatter.rupiah(subscription.monthlyEquivalent) +
                " per bulan (rata-rata 52 minggu setahun)",
        )
    }

    add(
        if (usage.chargeCount == 0) {
            "Belum pernah menagih"
        } else {
            "Sudah tercatat " + usage.chargeCount + " kali, total " +
                CurrencyFormatter.rupiah(usage.chargeCount * subscription.amount)
        },
    )

    val created = subscription.createdAt.atZone(zone).toLocalDate()
    add("Dibuat " + IndonesianDates.dayAndDate(created))
}

@Composable
private fun RowAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(
            icon = Icons.Rounded.Autorenew,
            tint = MaterialTheme.colorScheme.primary,
            size = 76.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Belum ada langganan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tagihan yang datang tiap bulan atau tiap minggu — YouTube, Claude, " +
                "kos, internet — cukup dicatat rencananya sekali di sini. Begitu " +
                "tanggalnya tiba, catatannya masuk sendiri ke Riwayat, dan totalnya " +
                "sudah ketahuan dari sekarang.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
