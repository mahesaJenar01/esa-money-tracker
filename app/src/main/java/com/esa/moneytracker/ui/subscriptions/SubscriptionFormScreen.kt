package com.esa.moneytracker.ui.subscriptions

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.EventRepeat
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
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.BillingCycle
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.Subscription
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.banks.AddBankDialog
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.BankPicker
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.PocketPicker
import com.esa.moneytracker.ui.components.SingleLineField
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.components.TimeOfDayField
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * The plan behind a recurring bill, in one page.
 *
 * Every answer it collects is a promise about the future, so the page ends with
 * the future it has just described: when the next bill lands, and what the plan
 * costs a month. A schedule that cannot be checked before it is saved is a
 * schedule that gets found out weeks later, in the history, by a charge on the
 * wrong day.
 */
@Composable
fun SubscriptionFormScreen(
    state: SubscriptionFormUiState,
    onNameChanged: (String) -> Unit,
    onAmountChanged: (String) -> Unit,
    onChooseCategory: (Category) -> Unit,
    onChoosePocket: (Pocket) -> Unit,
    onChooseBank: (String) -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onChooseCycle: (BillingCycle) -> Unit,
    onChooseDayOfMonth: (Int) -> Unit,
    onChooseDayOfWeek: (DayOfWeek) -> Unit,
    onTimeChanged: (LocalTime) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = remember { ZoneId.systemDefault() }
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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TopBar(editing = state.editing, onBack = onBack)

            if (state.missing) {
                MissingState()
                return@Column
            }

            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Nama langganan")
                    SingleLineField(
                        value = state.name,
                        onValueChanged = onNameChanged,
                        placeholder = "Contoh: YouTube Premium",
                        isError = state.showErrors && state.nameError != null,
                        errorText = state.nameError,
                        helperText = "Dipakai sebagai keterangan tiap catatan tagihannya.",
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("Nominal tiap tagihan")
                    AmountField(
                        digits = state.amountDigits,
                        onDigitsChanged = onAmountChanged,
                        type = TransactionType.EXPENSE,
                        isError = state.showErrors && state.amountError != null,
                        errorText = state.amountError,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Ditagih berapa sering?")
                    CyclePicker(selected = state.cycle, onSelect = onChooseCycle)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (state.cycle) {
                        BillingCycle.MONTHLY -> {
                            FieldLabel("Tanggal berapa?")
                            DayOfMonthPicker(
                                selected = state.dayOfMonth,
                                onSelect = onChooseDayOfMonth,
                            )
                            if (state.dayOfMonth > 28) {
                                Hint(
                                    "Bulan yang lebih pendek dipakai tanggal terakhirnya — " +
                                        "Februari jadi tanggal 28 atau 29.",
                                )
                            }
                        }

                        BillingCycle.WEEKLY -> {
                            FieldLabel("Hari apa?")
                            DayOfWeekPicker(
                                selected = state.dayOfWeek,
                                onSelect = onChooseDayOfWeek,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Jam berapa dicatat?")
                    TimeOfDayField(value = state.timeOfDay, onChange = onTimeChanged)
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Kategori")
                    CategoryPicker(
                        categories = state.categories,
                        selected = state.category,
                        onSelect = onChooseCategory,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FieldLabel("Dibayar dari mana?")
                    PocketPicker(selected = state.pocket, onSelect = onChoosePocket)

                    if (state.pocket == Pocket.ONLINE) {
                        BankPicker(
                            banks = state.banks,
                            selectedId = state.bankId,
                            onSelect = onChooseBank,
                            onAddBank = { addingBank = true },
                        )
                        val bankError = state.bankError
                        if (state.showErrors && bankError != null) {
                            Hint(bankError, error = true)
                        }
                    }
                }

                PreviewCard(state = state, zone = zone)

                SubmitButton(
                    label = if (state.editing) "Simpan perubahan" else "Mulai langganan",
                    onClick = onSubmit,
                    enabled = state.canSubmit,
                    loading = state.saving,
                )
            }
        }
    }
}

@Composable
private fun TopBar(editing: Boolean, onBack: () -> Unit) {
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
                text = if (editing) "Ubah langganan" else "Langganan baru",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = if (editing) {
                    "Tagihan yang sudah tercatat tidak ikut berubah"
                } else {
                    "Tagihannya dicatat sendiri saat tanggalnya tiba"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CyclePicker(selected: BillingCycle, onSelect: (BillingCycle) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BillingCycle.entries.forEach { cycle ->
            val isSelected = cycle == selected
            val tint = MaterialTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isSelected) {
                            tint.copy(alpha = 0.14f)
                        } else {
                            MoneyTheme.colors.surfaceElevated
                        },
                    )
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) tint else MoneyTheme.colors.hairline,
                        shape = RoundedCornerShape(18.dp),
                    )
                    .clickable { onSelect(cycle) }
                    .padding(14.dp),
            ) {
                Text(
                    text = cycle.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Tiap " + cycle.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Every day of the month, laid out as a grid of chips.
 *
 * A dropdown of 31 entries hides the one answer the user already knows, and a
 * date picker would ask for a year as well. The grid is the shape of the thing
 * being chosen — a calendar — and the right day is one tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfMonthPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Subscription.DAYS_OF_MONTH.forEach { day ->
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MoneyTheme.colors.surfaceElevated
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MoneyTheme.colors.hairline
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(day) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayOfWeekPicker(selected: DayOfWeek, onSelect: (DayOfWeek) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DayOfWeek.entries.forEach { day ->
            val isSelected = day == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MoneyTheme.colors.surfaceElevated
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MoneyTheme.colors.hairline
                        },
                        shape = RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelect(day) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = IndonesianDates.dayShort(day),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selected: Category,
    onSelect: (Category) -> Unit,
) {
    val colors = MoneyTheme.colors

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        categories.forEach { category ->
            val isSelected = category == selected
            val tint = colors.forCategory(category)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) tint.copy(alpha = 0.14f) else colors.surfaceElevated)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) tint else colors.hairline,
                        shape = RoundedCornerShape(16.dp),
                    )
                    .clickable { onSelect(category) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = iconFor(category),
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = category.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

/**
 * What the answers above actually mean, spelled out before they are saved.
 *
 * It reads the plan as it *would* be written rather than as it is stored, which
 * is what lets an edit say where the next bill moves to — the one thing about
 * changing a schedule that is genuinely hard to work out in your head.
 */
@Composable
private fun PreviewCard(state: SubscriptionFormUiState, zone: ZoneId) {
    val preview = state.preview
    val colors = MoneyTheme.colors

    SoftCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.EventRepeat,
                tint = MaterialTheme.colorScheme.primary,
                size = 44.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Tagihan berikutnya",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (preview == null) {
                    Text(
                        text = "Isi nama dan nominalnya dulu",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.muted,
                    )
                } else {
                    // The same moment the catch-up run would pick, so the card
                    // is a promise rather than a second opinion.
                    val moment = preview.nextDueAfter(preview.chargedThrough, zone)
                    val due = moment.atZone(zone).toLocalDate()
                    val immediate = !moment.isAfter(state.now)
                    Text(
                        text = IndonesianDates.dayAndDate(due) +
                            " • " + IndonesianDates.time(preview.timeOfDay),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (immediate) {
                            "Sudah lewat — langsung dicatat begitu disimpan • " +
                                CurrencyFormatter.rupiah(preview.amount)
                        } else {
                            IndonesianDates.untilLabel(due, LocalDate.now(zone)) +
                                " • " + CurrencyFormatter.rupiah(preview.amount)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (preview != null) {
            Spacer(Modifier.height(12.dp))
            Hairline()
            Spacer(Modifier.height(12.dp))
            Text(
                text = preview.scheduleLabel + ", terus berulang. Setara " +
                    CurrencyFormatter.rupiah(preview.monthlyEquivalent) + " per bulan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!state.editing) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Tagihan yang jatuh tempo sebelum hari ini tidak ikut dicatat — " +
                        "itu riwayat, bukan rencana.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.muted,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (error) MoneyTheme.colors.expense else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MissingState() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Langganan ini sudah dihapus.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
