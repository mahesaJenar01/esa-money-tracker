package com.esa.moneytracker.ui.check

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.DateTimeField
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.RupiahVisualTransformation
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The weekly reconciliation: what the app thinks, next to what the bank says.
 *
 * A page rather than a dialog, because it asks the same question once per bank
 * and the answers have to be visible side by side — the whole value of doing it
 * in one sitting is seeing which pocket is the one that drifted.
 *
 * Every row can be left blank. A bank that was not counted is not part of the
 * check and gets no line in the record, which keeps the mark an honest account
 * of what was actually verified rather than a claim about everything.
 */
@Composable
fun BalanceCheckScreen(
    state: BalanceCheckUiState,
    onBack: () -> Unit,
    onBalanceChanged: (String, String) -> Unit,
    onClearBalance: (String) -> Unit,
    onRecordDifferenceChanged: (String, Boolean) -> Unit,
    onNoteChanged: (String) -> Unit,
    onCheckedAtChanged: (LocalDateTime) -> Unit,
    onResetCheckedAt: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = remember { LocalDate.now() }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            CheckTopBar(onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item("intro") { IntroCard() }

                item("when") {
                    Column {
                        FieldLabel("Kapan dicek")
                        Spacer(Modifier.height(8.dp))
                        DateTimeField(
                            value = state.checkedAt,
                            highlighted = !state.isNow,
                            headline = if (state.isNow) {
                                "Sekarang"
                            } else {
                                IndonesianDates.relativeDay(state.checkedAt.toLocalDate(), today)
                            },
                            note = "Penanda muncul di tanggal itu, bukan hari ini.",
                            resetLabel = "Sekarang saja",
                            onChange = onCheckedAtChanged,
                            onReset = onResetCheckedAt,
                        )
                    }
                }

                item("pockets-header") {
                    Spacer(Modifier.height(4.dp))
                    FieldLabel("Saldo asli tiap kantong")
                }

                items(state.rows, key = { it.id }) { row ->
                    CheckRowCard(
                        row = row,
                        onBalanceChanged = { onBalanceChanged(row.id, it) },
                        onClear = { onClearBalance(row.id) },
                        onRecordChanged = { onRecordDifferenceChanged(row.id, it) },
                    )
                }

                item("note") {
                    Column(Modifier.padding(top = 6.dp)) {
                        FieldLabel("Catatan pemeriksaan (opsional)")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = onNoteChanged,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("Contoh: cek rutin mingguan") },
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                                imeAction = ImeAction.Done,
                            ),
                            minLines = 2,
                            shape = RoundedCornerShape(18.dp),
                            colors = fieldColors(),
                        )
                    }
                }

                item("submit") {
                    Column(Modifier.padding(top = 6.dp)) {
                        SummaryCard(state)
                        Spacer(Modifier.height(16.dp))
                        SubmitButton(
                            label = submitLabel(state),
                            onClick = onSubmit,
                            enabled = state.canSubmit,
                            loading = state.busy,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CheckTopBar(onBack: () -> Unit) {
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
                text = "Cek saldo",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Cocokkan catatan dengan saldo asli",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IntroCard() {
    SoftCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Bookmark,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Menandai sampai mana yang sudah dicek",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Isi saldo asli tiap bank. Yang cocok ditandai cocok, yang " +
                "selisih bisa langsung dicatat jadi catatan supaya angkanya sama " +
                "lagi. Setelah disimpan, sebuah garis muncul di Riwayat: apa pun " +
                "yang ada di atas garis itu belum pernah dicocokkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Bank yang belum sempat dicek boleh dikosongkan.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CheckRowCard(
    row: CheckRow,
    onBalanceChanged: (String) -> Unit,
    onClear: () -> Unit,
    onRecordChanged: (Boolean) -> Unit,
) {
    val colors = MoneyTheme.colors
    val tint = when {
        !row.counted -> colors.muted
        row.matched -> colors.income
        else -> colors.expense
    }

    SoftCard(contentPadding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (row.color != null) {
                BankDot(row.color, size = 12.dp)
            } else {
                Box(
                    Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.pocketCash),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Menurut aplikasi " + CurrencyFormatter.rupiah(row.appBalance),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.counted) {
                IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Kosongkan " + row.label,
                        tint = colors.muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = row.digits,
            onValueChange = onBalanceChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Saldo asli") },
            placeholder = { Text("Rp 0") },
            visualTransformation = remember { RupiahVisualTransformation() },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next,
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.titleMedium,
            shape = RoundedCornerShape(18.dp),
            colors = fieldColors(),
        )

        if (row.counted) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (row.matched) "Cocok" else "Selisih",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = if (row.matched) {
                        "Tidak ada beda"
                    } else {
                        CurrencyFormatter.signedRupiah(row.difference)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = tint,
                )
            }
        }

        if (row.hasGap) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Catat selisihnya",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = gapExplanation(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Switch(checked = row.recordDifference, onCheckedChange = onRecordChanged)
            }
        }
    }
}

@Composable
private fun SummaryCard(state: BalanceCheckUiState) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = when (state.counted.size) {
                    0 -> "Belum ada yang diisi"
                    1 -> "1 kantong dicek"
                    else -> state.counted.size.toString() + " kantong dicek"
                },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    state.counted.isEmpty() ->
                        "Isi minimal satu saldo untuk bisa menandai."
                    state.gaps.isEmpty() ->
                        "Semuanya cocok. Tidak ada catatan baru yang dibuat."
                    state.willRecord == 0 ->
                        "Selisih dibiarkan terbuka, tidak ada catatan yang dibuat."
                    state.willRecord == 1 ->
                        "1 catatan selisih akan dibuat."
                    else ->
                        state.willRecord.toString() + " catatan selisih akan dibuat."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.gaps.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = CurrencyFormatter.signedRupiah(state.totalDifference),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.totalDifference >= 0) colors.income else colors.expense,
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MoneyTheme.colors.hairline,
    focusedContainerColor = MoneyTheme.colors.surfaceElevated,
    unfocusedContainerColor = MoneyTheme.colors.surfaceElevated,
)

/**
 * What writing the note actually claims.
 *
 * The direction matters and is easy to get backwards, so it is spelled out
 * rather than left to be inferred from a sign: more money in the bank than the
 * app knew about is income that was never recorded, and less is a spend.
 */
private fun gapExplanation(row: CheckRow): String = if (row.difference > 0L) {
    "Ada pemasukan yang belum tercatat. Dibuat catatan Pendapatan Lainnya."
} else {
    "Ada pengeluaran yang belum tercatat. Dibuat catatan Lainnya."
}

private fun submitLabel(state: BalanceCheckUiState): String = when {
    state.willRecord == 0 -> "Simpan penanda"
    state.willRecord == 1 -> "Simpan penanda & catat 1 selisih"
    else -> "Simpan penanda & catat " + state.willRecord + " selisih"
}
