package com.esa.moneytracker.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.ui.components.BalanceCheckMark
import com.esa.moneytracker.ui.components.BalanceHeader
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SectionHeader
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.TransactionListItem
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.AnalyticsPeriod
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import kotlinx.coroutines.launch
import java.time.ZoneId

@Composable
fun HomeScreen(
    state: HomeUiState,
    onSelectPeriod: (AnalyticsPeriod) -> Unit,
    onAddClick: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onDeleteCheck: (String) -> Unit,
    onOpenRecords: () -> Unit,
    onOpenBanks: () -> Unit,
    onOpenData: () -> Unit,
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
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding(),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Catat", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding()),
            contentPadding = PaddingValues(bottom = 110.dp),
        ) {
            item("balance") {
                BalanceHeader(
                    total = state.totalBalance,
                    online = state.onlineBalance,
                    cash = state.cashBalance,
                    bankCount = state.bankCount,
                    onOpenBanks = onOpenBanks,
                    onOpenData = onOpenData,
                )
            }

            item("analytics") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(20.dp))
                    SectionHeader(
                        title = "Analitik",
                        subtitle = "Ringkasan periode terpilih",
                        trailing = {
                            PeriodSelector(
                                selected = state.period,
                                options = state.availablePeriods,
                                onSelect = onSelectPeriod,
                            )
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    AnalyticsCard(state)
                }
            }

            state.latest?.let { latest ->
                item("latest") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(22.dp))
                        SectionHeader(title = "Catatan terakhir")
                        Spacer(Modifier.height(12.dp))
                        LatestCard(latest, zone, state.bankNames[latest.bankId])
                    }
                }
            }

            item("history-header") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Spacer(Modifier.height(22.dp))
                    SectionHeader(
                        title = "Riwayat",
                        subtitle = "Minggu ini • " + state.weekRangeLabel,
                        trailing = { OpenRecordsButton(onClick = onOpenRecords) },
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            state.days.forEach { day ->
                item("day-" + day.date) {
                    DayHeader(day)
                }
                items(day.items, key = { it.key }) { entry ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        when (entry) {
                            is HistoryEntry.Mark -> BalanceCheckMark(
                                check = entry.check,
                                zone = zone,
                                expanded = expandedId == entry.key,
                                onToggle = {
                                    expandedId = if (expandedId == entry.key) null else entry.key
                                },
                                onDelete = {
                                    expandedId = null
                                    onDeleteCheck(entry.check.id)
                                },
                            )

                            is HistoryEntry.Record -> {
                                val transaction = entry.transaction
                                TransactionListItem(
                                    transaction = transaction,
                                    zone = zone,
                                    bankLabel = state.bankNames[transaction.bankId],
                                    expanded = expandedId == entry.key,
                                    onToggle = {
                                        expandedId =
                                            if (expandedId == entry.key) null else entry.key
                                    },
                                    onEdit = {
                                        expandedId = null
                                        onEdit(transaction.id)
                                    },
                                    onDelete = {
                                        expandedId = null
                                        onDelete(transaction.id)
                                        scope.launch {
                                            val result = snackbarHostState.showSnackbar(
                                                message = "Catatan dihapus",
                                                actionLabel = "Urungkan",
                                                withDismissAction = true,
                                                duration = SnackbarDuration.Long,
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                onRestore(transaction.id)
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (state.isEmpty) {
                item("empty") {
                    EmptyState(
                        hasOlderRecords = state.hasOlderRecords,
                        onOpenRecords = onOpenRecords,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** Takes the user to the full, week-by-week history. */
@Composable
private fun OpenRecordsButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Semua",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = "Buka riwayat lengkap",
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LatestCard(transaction: Transaction, zone: ZoneId, bankLabel: String?) {
    val colors = MoneyTheme.colors
    val tint = colors.forCategory(transaction.category)

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = iconFor(transaction.category), tint = tint, size = 52.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = transaction.categoryLabel + " • " +
                        (bankLabel ?: transaction.pocket.label),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = transaction.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Hairline()
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = IndonesianDates.dayAndDate(transaction.dateIn(zone)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Pukul " + IndonesianDates.time(transaction.dateTimeIn(zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = CurrencyFormatter.signedRupiah(transaction.signedAmount),
                style = MaterialTheme.typography.headlineSmall,
                color = colors.forType(transaction.type),
            )
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = day.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = CurrencyFormatter.signedRupiah(day.net),
            style = MaterialTheme.typography.labelMedium,
            color = if (day.net >= 0) MoneyTheme.colors.income else MoneyTheme.colors.expense,
        )
    }
}

@Composable
private fun EmptyState(
    hasOlderRecords: Boolean,
    onOpenRecords: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (hasOlderRecords) "Belum ada catatan minggu ini" else "Belum ada catatan",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (hasOlderRecords) {
                "Catatan minggu-minggu sebelumnya ada di riwayat lengkap."
            } else {
                "Tekan tombol Catat untuk menambahkan pemasukan atau pengeluaran pertamamu."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (hasOlderRecords) {
            Spacer(Modifier.height(14.dp))
            OpenRecordsButton(onClick = onOpenRecords)
        }
    }
}
