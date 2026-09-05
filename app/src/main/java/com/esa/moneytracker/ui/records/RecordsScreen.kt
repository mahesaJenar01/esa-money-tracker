package com.esa.moneytracker.ui.records

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteSweep
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.TransactionListItem
import com.esa.moneytracker.ui.home.DayGroup
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Riwayat in full: one week per screen, walked back a week at a time.
 */
@Composable
fun RecordsScreen(
    state: RecordsUiState,
    onBack: () -> Unit,
    onOlderWeek: () -> Unit,
    onNewerWeek: () -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit,
    onRestore: (String) -> Unit,
    onOpenBin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    // Stepping to another week makes the open row irrelevant.
    LaunchedEffect(state.week) { expandedId = null }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            RecordsTopBar(binCount = state.binCount, onBack = onBack, onOpenBin = onOpenBin)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                item("week") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        WeekPager(
                            title = state.title,
                            rangeLabel = state.rangeLabel,
                            canGoOlder = state.canGoOlder,
                            canGoNewer = state.canGoNewer,
                            onOlder = onOlderWeek,
                            onNewer = onNewerWeek,
                        )
                        Spacer(Modifier.height(12.dp))
                        WeekSummary(state)
                    }
                }

                state.days.forEach { day ->
                    item("day-" + day.date) { DayHeader(day) }
                    items(day.items, key = { it.id }) { transaction ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            TransactionListItem(
                                transaction = transaction,
                                zone = zone,
                                bankLabel = state.bankNames[transaction.bankId],
                                expanded = expandedId == transaction.id,
                                onToggle = {
                                    expandedId = if (expandedId == transaction.id) {
                                        null
                                    } else {
                                        transaction.id
                                    }
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

                if (state.isEmpty) {
                    item("empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp)
                                .padding(top = 48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.EventNote,
                                contentDescription = null,
                                tint = MoneyTheme.colors.muted,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Tidak ada catatan pada minggu ini",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordsTopBar(binCount: Int, onBack: () -> Unit, onOpenBin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
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
        Column(Modifier.weight(1f)) {
            Text(
                text = "Riwayat lengkap",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Telusuri minggu per minggu",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BinButton(count = binCount, onClick = onOpenBin)
    }
}

@Composable
private fun BinButton(count: Int, onClick: () -> Unit) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.surfaceElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteSweep,
            contentDescription = "Buka catatan terhapus",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        if (count > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WeekPager(
    title: String,
    rangeLabel: String,
    canGoOlder: Boolean,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(
            icon = Icons.Rounded.ChevronLeft,
            description = "Minggu sebelumnya",
            enabled = canGoOlder,
            onClick = onOlder,
        )
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = rangeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        StepButton(
            icon = Icons.Rounded.ChevronRight,
            description = "Minggu berikutnya",
            enabled = canGoNewer,
            onClick = onNewer,
        )
    }
}

@Composable
private fun StepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val tint = if (enabled) MaterialTheme.colorScheme.onSurface else colors.muted

    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.surfaceElevated else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun WeekSummary(state: RecordsUiState) {
    val colors = MoneyTheme.colors

    SoftCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SummaryCell(
                label = "Pemasukan",
                value = CurrencyFormatter.rupiah(state.income),
                tint = colors.income,
                modifier = Modifier.weight(1f),
            )
            SummaryCell(
                label = "Pengeluaran",
                value = CurrencyFormatter.rupiah(state.expense),
                tint = colors.expense,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.count.toString() + " catatan",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CurrencyFormatter.signedRupiah(state.net),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.net >= 0) colors.income else colors.expense,
            )
        }
    }
}

@Composable
private fun SummaryCell(
    label: String,
    value: String,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
