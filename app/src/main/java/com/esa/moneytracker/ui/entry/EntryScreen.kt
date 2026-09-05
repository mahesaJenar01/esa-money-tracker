package com.esa.moneytracker.ui.entry

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.esa.moneytracker.ui.components.OccurredAtField
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import java.time.LocalDateTime

@Composable
fun EntryScreen(
    state: EntryUiState,
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
            EntryTopBar(state = state, onBack = onBack)

            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    val offset = if (forward) 1 else -1
                    (slideInHorizontally(tween(260)) { it / 4 * offset } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutHorizontally(tween(260)) { -it / 4 * offset } + fadeOut(tween(160)),
                        )
                },
                label = "entry-step",
                modifier = Modifier.fillMaxSize(),
            ) { step ->
                when (step) {
                    EntryStep.TYPE -> TypeStep(onChooseType)
                    EntryStep.CATEGORY -> CategoryStep(state, onChooseCategory)
                    EntryStep.DETAILS -> DetailsStep(
                        state = state,
                        onChoosePocket = onChoosePocket,
                        onChooseBank = onChooseBank,
                        onAddBank = onAddBank,
                        onAmountChanged = onAmountChanged,
                        onDescriptionChanged = onDescriptionChanged,
                        onOccurredAtChanged = onOccurredAtChanged,
                        onResetOccurredAt = onResetOccurredAt,
                        onSubmit = onSubmit,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryTopBar(state: EntryUiState, onBack: () -> Unit) {
    val stepNumber = state.step.ordinal + 1
    val total = EntryStep.entries.size

    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
                    text = when (state.step) {
                        EntryStep.TYPE -> "Catat transaksi"
                        EntryStep.CATEGORY -> "Pilih kategori"
                        EntryStep.DETAILS -> "Detail transaksi"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Langkah " + stepNumber + " dari " + total,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // Step indicator — three segments that fill as the flow progresses.
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EntryStep.entries.forEach { step ->
                val done = step.ordinal <= state.step.ordinal
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (done) MaterialTheme.colorScheme.primary
                            else MoneyTheme.colors.hairline,
                        ),
                )
            }
        }
    }
}

@Composable
private fun TypeStep(onChoose: (TransactionType) -> Unit) {
    val colors = MoneyTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Mau mencatat apa?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        TypeCard(
            title = TransactionType.INCOME.label,
            subtitle = "Uang masuk — gaji atau pendapatan lain",
            icon = Icons.Rounded.ArrowDownward,
            tint = colors.income,
            container = colors.incomeContainer,
            onClick = { onChoose(TransactionType.INCOME) },
        )

        TypeCard(
            title = TransactionType.EXPENSE.label,
            subtitle = "Uang keluar — jajan, makan, belanja, dan lainnya",
            icon = Icons.Rounded.ArrowUpward,
            tint = colors.expense,
            container = colors.expenseContainer,
            onClick = { onChoose(TransactionType.EXPENSE) },
        )
    }
}

@Composable
private fun TypeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(container)
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = tint,
        )
    }
}

@Composable
private fun CategoryStep(state: EntryUiState, onChoose: (Category) -> Unit) {
    val colors = MoneyTheme.colors

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = state.type?.label.orEmpty() + " ini masuk kategori apa?",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        state.categories.forEach { category ->
            val tint = colors.forCategory(category)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceElevated)
                    .border(1.dp, colors.hairline, RoundedCornerShape(20.dp))
                    .clickable { onChoose(category) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = iconFor(category), tint = tint, size = 42.dp)
                Spacer(Modifier.width(14.dp))
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MoneyTheme.colors.muted,
                )
            }
        }
    }
}

@Composable
private fun DetailsStep(
    state: EntryUiState,
    onChoosePocket: (Pocket) -> Unit,
    onChooseBank: (String) -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onAmountChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onOccurredAtChanged: (LocalDateTime) -> Unit,
    onResetOccurredAt: () -> Unit,
    onSubmit: () -> Unit,
) {
    val colors = MoneyTheme.colors
    val amountFocus = remember { FocusRequester() }
    val tint = colors.forCategory(state.category)
    var addingBank by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { amountFocus.requestFocus() }

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
            // imePadding sits outside the scroll so the keyboard shortens the
            // viewport; inside it, it would only pad the content and leave the
            // button stranded under the keyboard.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 32.dp),
    ) {
        // Reminder of what is being recorded, so the choices above stay visible.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(colors.containerForType(state.type ?: TransactionType.EXPENSE))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = iconFor(state.category), tint = tint, size = 40.dp, containerAlpha = 0.22f)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = state.category?.label.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.type?.label.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onContainerForType(state.type ?: TransactionType.EXPENSE),
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        FieldLabel("Nominal")
        Spacer(Modifier.height(8.dp))
        AmountField(
            digits = state.amountDigits,
            onDigitsChanged = onAmountChanged,
            type = state.type ?: TransactionType.EXPENSE,
            modifier = Modifier.focusRequester(amountFocus),
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

        Spacer(Modifier.height(18.dp))

        FieldLabel("Kapan transaksinya terjadi?")
        Spacer(Modifier.height(8.dp))
        OccurredAtField(
            value = state.occurredAtOrNow,
            isNow = state.timeIsNow,
            onChange = onOccurredAtChanged,
            onResetToNow = onResetOccurredAt,
        )

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
            label = "Simpan " + CurrencyFormatter.rupiah(state.amount),
            onClick = onSubmit,
            loading = state.saving,
        )
    }
}
