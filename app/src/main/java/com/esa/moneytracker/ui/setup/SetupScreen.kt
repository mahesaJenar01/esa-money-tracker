package com.esa.moneytracker.ui.setup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.export.ExportFormat
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.backup.DocumentIo
import com.esa.moneytracker.ui.backup.MessageBanner
import com.esa.moneytracker.ui.banks.AddBankDialog
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * The very first screen on an empty app: what is already in each pocket.
 *
 * The answer is not a transaction — it lifts the balances without ever showing
 * up in Riwayat or in the weekly analytics, so the first real note is still the
 * first note.
 *
 * Online money is asked for bank by bank rather than as one figure. That is the
 * shape the app keeps it in from here on, and asking for a lump sum would only
 * mean asking the user to break it apart again on the bank page.
 */
@Composable
fun SetupScreen(
    state: SetupUiState,
    onCashChanged: (String) -> Unit,
    onAddBank: (String, BankColor, Long) -> Unit,
    onRemoveBank: (Int) -> Unit,
    onSubmit: () -> Unit,
    onImport: (() -> String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors
    val context = LocalContext.current
    var addingBank by remember { mutableStateOf(false) }

    val openBackup = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) onImport { DocumentIo.readText(context, uri) }
    }

    if (addingBank) {
        AddBankDialog(
            // Nothing is on file yet, so every rupiah entered here is new money
            // and the "already counted somewhere else" question cannot arise.
            others = emptyList(),
            suggestedColor = state.suggestedBankColor,
            onDismiss = { addingBank = false },
            onConfirm = { name, color, amount, _ ->
                addingBank = false
                onAddBank(name, color, amount)
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 32.dp),
        ) {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Savings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Halo!",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Mulailah dengan salah satu dari dua cara: daftarkan bank yang " +
                    "kamu punya beserta saldo uang tunaimu, atau pulihkan berkas " +
                    "cadangan yang pernah kamu ekspor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            state.message?.let { message ->
                Spacer(Modifier.height(16.dp))
                MessageBanner(message)
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Isi saldo awal",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Angka ini jadi titik awal — ia tidak muncul di Riwayat dan " +
                    "tidak dihitung sebagai pemasukan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(28.dp))

            PocketLabel(Pocket.ONLINE, "Bank, e-wallet, rekening — daftarkan satu per satu")
            Spacer(Modifier.height(12.dp))

            state.banks.forEachIndexed { index, bank ->
                PendingBankRow(
                    name = bank.name,
                    color = bank.color,
                    amount = bank.amount,
                    onRemove = { onRemoveBank(index) },
                )
                Spacer(Modifier.height(8.dp))
            }

            AddBankButton(onClick = { addingBank = true }, hasBanks = state.banks.isNotEmpty())

            Spacer(Modifier.height(24.dp))

            PocketLabel(Pocket.CASH, Pocket.CASH.description)
            Spacer(Modifier.height(8.dp))
            AmountField(
                digits = state.cashDigits,
                onDigitsChanged = onCashChanged,
                type = TransactionType.INCOME,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Total saldo awal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = CurrencyFormatter.rupiah(state.total),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(24.dp))

            SubmitButton(
                label = "Mulai mencatat",
                onClick = onSubmit,
                loading = state.saving,
            )

            Spacer(Modifier.height(10.dp))

            // Leaving everything empty is a valid answer, so say so.
            Text(
                text = "Belum tahu angkanya? Biarkan kosong, saldo dimulai dari nol. " +
                    "Bank bisa ditambahkan kapan saja nanti.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(28.dp))

            OrDivider()

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Pulihkan dari cadangan",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Punya berkas .json hasil ekspor sebelumnya? Semua catatan, bank " +
                    "dan saldo awalnya dipulihkan, lalu aplikasi langsung terbuka " +
                    "dengan data itu. Berkas lama yang belum mengenal bank tetap " +
                    "diterima — isinya dikumpulkan ke satu bank bernama Online yang " +
                    "bisa kamu rapikan setelahnya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                    .clickable(enabled = !state.saving) {
                        openBackup.launch(arrayOf(ExportFormat.JSON.mimeType, "*/*"))
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(
                    icon = Icons.Rounded.CloudUpload,
                    tint = MaterialTheme.colorScheme.primary,
                    size = 40.dp,
                )
                Text(
                    text = "Pilih berkas cadangan",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 14.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingBankRow(
    name: String,
    color: BankColor,
    amount: Long,
    onRemove: () -> Unit,
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankDot(color, size = 12.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = CurrencyFormatter.rupiah(amount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Hapus " + name,
                tint = colors.muted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AddBankButton(onClick: () -> Unit, hasBanks: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (hasBanks) "Tambah bank lain" else "Tambah bank",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** A hairline with the word "atau" sitting in it. */
@Composable
private fun OrDivider() {
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
        Text(
            text = "atau",
            style = MaterialTheme.typography.labelMedium,
            color = colors.muted,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(colors.hairline),
        )
    }
}

@Composable
private fun PocketLabel(pocket: Pocket, description: String) {
    val tint = MoneyTheme.colors.forPocket(pocket)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconBadge(icon = iconFor(pocket), tint = tint, size = 32.dp)
        Column {
            FieldLabel("Saldo " + pocket.label)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
