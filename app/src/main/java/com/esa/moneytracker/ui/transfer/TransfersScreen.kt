package com.esa.moneytracker.ui.transfer

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.SwapHoriz
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.TransferListItem
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Riwayat pindah dana: every move between your own pockets, and nothing else.
 *
 * Separate from Riwayat on purpose. None of these rows is money earned or
 * spent, so putting them in the same list would mean every figure on the home
 * screen had to explain why the numbers below it do not add up to the ones
 * above.
 */
@Composable
fun TransfersScreen(
    state: TransfersUiState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
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
                text = { Text("Pindah dana", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TransfersTopBar(onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                item("summary") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        SummaryCard(state)
                        Spacer(Modifier.height(6.dp))
                    }
                }

                state.days.forEach { day ->
                    item("day-" + day.date) { DayHeader(day) }
                    items(day.items, key = { it.id }) { transfer ->
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            TransferListItem(
                                transfer = transfer,
                                bankNames = state.bankNames,
                                bankColors = state.bankColors,
                                zone = zone,
                                expanded = expandedId == transfer.id,
                                onToggle = {
                                    expandedId = if (expandedId == transfer.id) {
                                        null
                                    } else {
                                        transfer.id
                                    }
                                },
                                onEdit = {
                                    expandedId = null
                                    onEdit(transfer.id)
                                },
                                onDelete = {
                                    expandedId = null
                                    onDelete(transfer.id)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Pindah dana dihapus",
                                            actionLabel = "Urungkan",
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Long,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            onRestore(transfer.id)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }

                if (state.isEmpty) {
                    item("empty") { EmptyState(Modifier.padding(horizontal = 16.dp)) }
                }
            }
        }
    }
}

@Composable
private fun TransfersTopBar(onBack: () -> Unit) {
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
                text = "Pindah dana",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Terpisah dari Riwayat pemasukan dan pengeluaran",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryCard(state: TransfersUiState) {
    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.SwapHoriz,
                tint = MaterialTheme.colorScheme.primary,
                size = 44.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Dipindahkan bulan ini",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = CurrencyFormatter.rupiah(state.movedThisMonth),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Angka ini cuma seberapa sering uangmu berpindah tempat — bukan " +
                "pemasukan, bukan pengeluaran, dan tidak mengubah total saldo.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayHeader(day: TransferDayGroup) {
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
            text = CurrencyFormatter.rupiah(day.moved),
            style = MaterialTheme.typography.labelMedium,
            color = MoneyTheme.colors.muted,
        )
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
            icon = Icons.Rounded.SwapHoriz,
            tint = MaterialTheme.colorScheme.primary,
            size = 76.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Belum ada pindah dana",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Kalau uang di satu bank kurang dan di bank lain berlebih, atau " +
                "tunai di dompet mau disetor, catat perpindahannya di sini. " +
                "Saldo tiap tempat ikut menyesuaikan, totalnya tetap.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
