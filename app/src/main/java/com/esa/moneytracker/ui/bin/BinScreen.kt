package com.esa.moneytracker.ui.bin

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.components.TransactionRow
import com.esa.moneytracker.ui.components.TransferRow
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.IndonesianDates
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Catatan terhapus: the 30-day bin.
 *
 * Every row can be put back, and each one says how long it has left, so nothing
 * disappears without warning.
 */
@Composable
fun BinScreen(
    state: BinUiState,
    onBack: () -> Unit,
    onRestore: (BinnedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

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
                Column {
                    Text(
                        text = "Catatan terhapus",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Disimpan 30 hari, lalu hilang permanen",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                items(state.items, key = { it.key }) { item ->
                    BinRow(
                        item = item,
                        state = state,
                        zone = zone,
                        onRestore = {
                            onRestore(item)
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    message = "Catatan dipulihkan",
                                    withDismissAction = true,
                                )
                            }
                        },
                    )
                }

                if (state.isEmpty) {
                    item("empty") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp)
                                .padding(top = 56.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MoneyTheme.colors.muted,
                                modifier = Modifier.size(40.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Tidak ada catatan terhapus",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Catatan yang kamu hapus akan muncul di sini " +
                                    "dan bisa dipulihkan dalam 30 hari.",
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
private fun BinRow(
    item: BinnedItem,
    state: BinUiState,
    zone: ZoneId,
    onRestore: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val deleted = item.deletedAt?.let { java.time.LocalDateTime.ofInstant(it, zone) }

    Column(Modifier.padding(bottom = 8.dp)) {
        when (item) {
            is BinnedItem.Note -> TransactionRow(
                transaction = item.transaction,
                zone = zone,
                bankLabel = item.transaction.bankId?.let { state.bankNames[it] },
                dimmed = true,
            )

            is BinnedItem.Move -> TransferRow(
                transfer = item.transfer,
                bankNames = state.bankNames,
                bankColors = state.bankColors,
                zone = zone,
                dimmed = true,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (deleted != null) {
                    Text(
                        text = "Dihapus " + IndonesianDates.dayAndDate(deleted.toLocalDate()) +
                            " • " + IndonesianDates.time(deleted),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = when (item.daysLeft) {
                        0L -> "Hilang permanen hari ini"
                        1L -> "Sisa 1 hari"
                        else -> "Sisa " + item.daysLeft + " hari"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.daysLeft <= 3L) colors.expense else colors.muted,
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                    .clickable(onClick = onRestore)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Undo,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Pulihkan",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}
