package com.esa.moneytracker.ui.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.data.model.TransferEndpoint
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.DateTimeField
import com.esa.moneytracker.ui.components.DescriptionField
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.OccurredAtField
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.components.transferNotes
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pindah Dana: money leaving one of your pockets and arriving in another.
 *
 * A page of its own rather than a fourth step in Catat, because it asks nothing
 * that flow asks. There is no category, no income or expense, and nothing to
 * describe — only two ends and an amount. What it must never do is look like a
 * transaction, because it is not one: the total across every pocket is the same
 * rupiah before and after.
 */
@Composable
fun TransferScreen(
    state: TransferFormUiState,
    onChooseFrom: (String) -> Unit,
    onChooseTo: (String) -> Unit,
    onSwap: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onUseWholeBalance: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onOccurredAtChanged: (LocalDateTime) -> Unit,
    onResetOccurredAt: () -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TransferTopBar(editing = state.editing, onBack = onBack)

            when {
                state.loading -> Centered { CircularProgressIndicator() }

                state.missing -> Centered {
                    Text(
                        text = "Catatan pindah dana ini sudah tidak ada.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                state.endpoints.size < 2 -> Centered {
                    Text(
                        text = "Pindah dana perlu dua tempat. Tambahkan dulu bank di " +
                            "halaman Uang online, lalu kembali ke sini.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> TransferForm(
                    state = state,
                    onChooseFrom = onChooseFrom,
                    onChooseTo = onChooseTo,
                    onSwap = onSwap,
                    onAmountChanged = onAmountChanged,
                    onUseWholeBalance = onUseWholeBalance,
                    onNoteChanged = onNoteChanged,
                    onOccurredAtChanged = onOccurredAtChanged,
                    onResetOccurredAt = onResetOccurredAt,
                    onSubmit = onSubmit,
                    zone = zone,
                )
            }
        }
    }
}

@Composable
private fun TransferForm(
    state: TransferFormUiState,
    onChooseFrom: (String) -> Unit,
    onChooseTo: (String) -> Unit,
    onSwap: () -> Unit,
    onAmountChanged: (String) -> Unit,
    onUseWholeBalance: () -> Unit,
    onNoteChanged: (String) -> Unit,
    onOccurredAtChanged: (LocalDateTime) -> Unit,
    onResetOccurredAt: () -> Unit,
    onSubmit: () -> Unit,
    zone: ZoneId,
) {
    val colors = MoneyTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        state.original?.let { original ->
            HistoryCard(transferNotes(original, zone))
            Spacer(Modifier.height(20.dp))
        }

        FieldLabel("Dari mana?")
        Spacer(Modifier.height(8.dp))
        EndpointPicker(
            endpoints = state.endpoints,
            selectedKey = state.fromKey,
            onSelect = onChooseFrom,
        )
        BalanceHint(state.from)

        Spacer(Modifier.height(12.dp))
        SwapRow(enabled = state.fromKey != null || state.toKey != null, onSwap = onSwap)
        Spacer(Modifier.height(12.dp))

        FieldLabel("Ke mana?")
        Spacer(Modifier.height(8.dp))
        EndpointPicker(
            endpoints = state.destinations,
            selectedKey = state.toKey,
            onSelect = onChooseTo,
        )
        BalanceHint(state.to)

        if (state.showErrors && state.routeError != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = state.routeError.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(20.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Berapa yang pindah?")
            Spacer(Modifier.weight(1f))
            state.from?.takeIf { it.balance > 0L }?.let {
                Text(
                    text = "Semuanya",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onUseWholeBalance)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        AmountField(
            digits = state.amountDigits,
            onDigitsChanged = onAmountChanged,
            // Neutral green rather than expense red: nothing is being spent.
            type = TransactionType.INCOME,
            isError = state.showErrors && state.amountError != null,
            errorText = state.amountError,
        )

        state.from?.takeIf { state.exceedsSource }?.let { source ->
            Text(
                text = "Catatan: menurut aplikasi, " + source.label + " cuma punya " +
                    CurrencyFormatter.rupiah(source.balance) +
                    ". Tetap boleh disimpan kalau kamu yakin.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.gold,
            )
        }

        Spacer(Modifier.height(18.dp))

        FieldLabel("Kapan pindahnya?")
        Spacer(Modifier.height(8.dp))
        if (state.editing) {
            DateTimeField(
                value = state.occurredAtOrNow,
                highlighted = state.timeChanged,
                headline = if (state.timeChanged) "Waktu diubah" else "Waktu pindah dana",
                note = "Catatan akan pindah ke tanggal itu di riwayat pindah dana.",
                resetLabel = "Batalkan",
                onChange = onOccurredAtChanged,
                onReset = onResetOccurredAt,
            )
        } else {
            OccurredAtField(
                value = state.occurredAtOrNow,
                isNow = state.timeIsNow,
                onChange = onOccurredAtChanged,
                onResetToNow = onResetOccurredAt,
            )
        }

        Spacer(Modifier.height(18.dp))

        FieldLabel("Catatan")
        Spacer(Modifier.height(8.dp))
        DescriptionField(
            value = state.note,
            onValueChanged = onNoteChanged,
            placeholder = "Contoh: buat bayar kos bulan depan",
            helperText = "Boleh dikosongkan.",
        )

        Spacer(Modifier.height(24.dp))

        SubmitButton(
            label = when {
                state.editing && !state.hasChanges -> "Belum ada yang diubah"
                else -> "Simpan " + CurrencyFormatter.rupiah(state.amount)
            },
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.saving,
        )

        Spacer(Modifier.height(10.dp))

        Text(
            text = "Pindah dana tidak dihitung sebagai pemasukan maupun pengeluaran, " +
                "dan tidak muncul di Riwayat. Total saldomu tetap sama.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TransferTopBar(editing: Boolean, onBack: () -> Unit) {
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
                text = if (editing) "Ubah pindah dana" else "Pindah dana",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Antar bank, atau ke dan dari tunai",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun HistoryCard(notes: List<String>) {
    val colors = MoneyTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
            .padding(14.dp),
    ) {
        notes.forEach { note ->
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = "Menyimpan tidak memindahkan catatan ini — ia tetap di tanggalnya, " +
                "hanya ditandai pernah diubah.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EndpointPicker(
    endpoints: List<TransferEndpoint>,
    selectedKey: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        endpoints.forEach { endpoint ->
            EndpointChip(
                endpoint = endpoint,
                selected = endpoint.key == selectedKey,
                onClick = { onSelect(endpoint.key) },
            )
        }
    }
}

@Composable
private fun EndpointChip(
    endpoint: TransferEndpoint,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val tint = endpoint.color?.let { colors.forBank(it) } ?: colors.pocketCash

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) tint.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tint else colors.hairline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (endpoint.color != null) {
            BankDot(endpoint.color)
        } else {
            Box(
                Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.pocketCash),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = endpoint.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** What the chosen pocket currently holds, so the amount is an informed guess. */
@Composable
private fun BalanceHint(endpoint: TransferEndpoint?) {
    if (endpoint == null) return

    Spacer(Modifier.height(6.dp))
    Text(
        text = "Saldo " + endpoint.label + ": " + CurrencyFormatter.rupiah(endpoint.balance),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SwapRow(enabled: Boolean, onSwap: () -> Unit) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.hairline),
        )
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (enabled) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        colors.surfaceElevated
                    },
                )
                .clickable(enabled = enabled, onClick = onSwap)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.SwapVert,
                contentDescription = null,
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    colors.muted
                },
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Tukar",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    colors.muted
                },
            )
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}
