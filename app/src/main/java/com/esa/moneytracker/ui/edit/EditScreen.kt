package com.esa.moneytracker.ui.edit

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.HistoryToggleOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.DescriptionField
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.PocketPicker
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.ui.banks.AddBankDialog
import com.esa.moneytracker.ui.components.BankPicker
import com.esa.moneytracker.ui.components.DateTimeField
import com.esa.moneytracker.ui.components.transactionNotes
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * The edit page: one screen holding everything about a record that can change.
 *
 * Saving rewrites the record in place and moves its date to now, which is why
 * the original creation stamp is spelled out at the top — the note keeps its
 * history even though the list shows the newer date.
 */
@Composable
fun EditScreen(
    state: EditUiState,
    onChooseType: (TransactionType) -> Unit,
    onChooseCategory: (Category) -> Unit,
    onChoosePocket: (Pocket) -> Unit,
    onChooseBank: (String) -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onAmountChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
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
            EditTopBar(onBack)

            when {
                state.loading -> Centered { CircularProgressIndicator() }

                state.missing -> Centered {
                    Text(
                        text = "Catatan ini sudah tidak ada.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> EditForm(
                    state = state,
                    onChooseType = onChooseType,
                    onChooseCategory = onChooseCategory,
                    onChoosePocket = onChoosePocket,
                    onChooseBank = onChooseBank,
                    onAddBank = onAddBank,
                    onAmountChanged = onAmountChanged,
                    onDescriptionChanged = onDescriptionChanged,
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
private fun EditTopBar(onBack: () -> Unit) {
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
                text = "Ubah catatan",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Nominal, kategori, dan keterangan",
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
private fun EditForm(
    state: EditUiState,
    onChooseType: (TransactionType) -> Unit,
    onChooseCategory: (Category) -> Unit,
    onChoosePocket: (Pocket) -> Unit,
    onChooseBank: (String) -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onAmountChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onOccurredAtChanged: (LocalDateTime) -> Unit,
    onResetOccurredAt: () -> Unit,
    onSubmit: () -> Unit,
    zone: ZoneId,
) {
    val colors = MoneyTheme.colors
    var addingBank by remember { mutableStateOf(false) }

    if (addingBank) {
        AddBankDialog(
            others = state.banks,
            suggestedColor = state.suggestedBankColor,
            onDismiss = { addingBank = false },
            onConfirm = { name, color, amount, funding ->
                addingBank = false
                onAddBank(name, color, amount, funding)
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        state.original?.let { original ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.HistoryToggleOff,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    transactionNotes(original, zone).forEach { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        text = "Menyimpan tidak memindahkan catatan ini — ia tetap di " +
                            "tanggalnya, hanya ditandai pernah diubah.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        FieldLabel("Jenis")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeChip(
                type = TransactionType.INCOME,
                selected = state.type == TransactionType.INCOME,
                onClick = { onChooseType(TransactionType.INCOME) },
                modifier = Modifier.weight(1f),
            )
            TypeChip(
                type = TransactionType.EXPENSE,
                selected = state.type == TransactionType.EXPENSE,
                onClick = { onChooseType(TransactionType.EXPENSE) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(18.dp))

        FieldLabel("Kategori")
        Spacer(Modifier.height(8.dp))
        state.categories.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { category ->
                    CategoryChip(
                        category = category,
                        selected = state.category == category,
                        onClick = { onChooseCategory(category) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a lone chip on the last row half-width like the others.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (state.showErrors && state.category == null) {
            Text(
                text = "Pilih satu kategori",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(8.dp))

        FieldLabel("Nominal")
        Spacer(Modifier.height(8.dp))
        AmountField(
            digits = state.amountDigits,
            onDigitsChanged = onAmountChanged,
            type = state.type,
            isError = state.showErrors && state.amountError != null,
            errorText = state.amountError,
        )

        Spacer(Modifier.height(18.dp))

        FieldLabel("Dari kantong mana?")
        Spacer(Modifier.height(8.dp))
        PocketPicker(selected = state.pocket, onSelect = onChoosePocket)

        if (state.pocket == Pocket.ONLINE) {
            Spacer(Modifier.height(16.dp))
            FieldLabel("Bank yang mana?")
            Spacer(Modifier.height(8.dp))
            BankPicker(
                banks = state.banks,
                selectedId = state.bankId,
                onSelect = onChooseBank,
                onAddBank = { addingBank = true },
            )
            if (state.showErrors && state.bankError != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = state.bankError.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        state.occurredAt?.let { occurredAt ->
            Spacer(Modifier.height(18.dp))
            FieldLabel("Kapan transaksinya terjadi?")
            Spacer(Modifier.height(8.dp))
            DateTimeField(
                value = occurredAt,
                highlighted = state.timeChanged,
                headline = if (state.timeChanged) "Waktu diubah" else "Waktu transaksi",
                note = "Catatan akan pindah ke tanggal itu di Riwayat.",
                resetLabel = "Batalkan",
                onChange = onOccurredAtChanged,
                onReset = onResetOccurredAt,
            )
        }

        Spacer(Modifier.height(18.dp))

        FieldLabel("Keterangan")
        Spacer(Modifier.height(8.dp))
        DescriptionField(
            value = state.description,
            onValueChanged = onDescriptionChanged,
            isError = state.showErrors && state.descriptionError != null,
            errorText = state.descriptionError,
        )

        Spacer(Modifier.height(24.dp))

        SubmitButton(
            label = if (state.hasChanges) {
                "Simpan " + CurrencyFormatter.rupiah(state.amount)
            } else {
                "Belum ada yang diubah"
            },
            onClick = onSubmit,
            enabled = state.canSubmit,
            loading = state.saving,
        )
    }
}

@Composable
private fun TypeChip(
    type: TransactionType,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors
    val tint = colors.forType(type)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) tint.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tint else colors.hairline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (type == TransactionType.INCOME) {
                Icons.Rounded.ArrowDownward
            } else {
                Icons.Rounded.ArrowUpward
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = type.label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun CategoryChip(
    category: Category,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors
    val tint = colors.forCategory(category)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) tint.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tint else colors.hairline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = iconFor(category), tint = tint, size = 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            text = category.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
