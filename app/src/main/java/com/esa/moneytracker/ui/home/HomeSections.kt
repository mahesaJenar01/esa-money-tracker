package com.esa.moneytracker.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.iconFor
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.AnalyticsPeriod
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * The analytics window picker. Only "Mingguan" exists today, but it is rendered
 * as a real menu so adding monthly and yearly later changes nothing here.
 */
@Composable
fun PeriodSelector(
    selected: AnalyticsPeriod,
    options: List<AnalyticsPeriod>,
    onSelect: (AnalyticsPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { expanded = true }
                .padding(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = "Ubah periode analitik",
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** Income / expense / net for the selected window, plus a spending breakdown. */
@Composable
fun AnalyticsCard(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors

    SoftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = Icons.Rounded.Insights,
                tint = MaterialTheme.colorScheme.primary,
                size = 38.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.periodSubtitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.periodRangeLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = state.periodCount.toString() + " catatan",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FlowStat(
                label = "Pemasukan",
                amount = state.periodIncome,
                icon = Icons.Rounded.ArrowDownward,
                tint = colors.income,
                container = colors.incomeContainer,
                modifier = Modifier.weight(1f),
            )
            FlowStat(
                label = "Pengeluaran",
                amount = state.periodExpense,
                icon = Icons.Rounded.ArrowUpward,
                tint = colors.expense,
                container = colors.expenseContainer,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Selisih",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = CurrencyFormatter.signedRupiah(state.periodNet),
                style = MaterialTheme.typography.titleMedium,
                color = if (state.periodNet >= 0) colors.income else colors.expense,
            )
        }

        if (state.breakdown.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Pengeluaran per kategori",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            state.breakdown.forEach { slice ->
                BreakdownBar(slice)
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/**
 * A concise, bounded explanation of the selected analytics window.
 *
 * The facts are produced through [FinancialInsightProvider]. Today the summary
 * is deterministic and stays on-device; the structured facts can be handed to
 * an API later without changing this card or sending an unbounded history.
 */
@Composable
fun AiInsightCard(
    state: HomeUiState,
    modifier: Modifier = Modifier,
) {
    val insight = state.financialInsight
    val colors = MoneyTheme.colors

    SoftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Ringkasan AI",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = state.periodSubtitle + " • analisis lokal sementara",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "BETA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
            )
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = insight.summary,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (insight.transactionCount > 0) {
            Spacer(Modifier.height(16.dp))

            insight.largestExpense?.let { expense ->
                InsightFact(
                    icon = Icons.Rounded.ArrowUpward,
                    iconTint = colors.expense,
                    iconContainer = colors.expenseContainer,
                    label = "Pengeluaran terbesar",
                    title = expense.description,
                    value = CurrencyFormatter.rupiah(expense.amount),
                )
            }

            insight.repeatedExpense?.let { repeated ->
                Spacer(Modifier.height(10.dp))
                InsightFact(
                    icon = Icons.Rounded.Repeat,
                    iconTint = colors.gold,
                    iconContainer = MaterialTheme.colorScheme.secondaryContainer,
                    label = "Sering dilakukan • ${repeated.count} kali",
                    title = repeated.label,
                    value = CurrencyFormatter.rupiah(repeated.total),
                )
            }

            insight.largestIncome?.let { income ->
                Spacer(Modifier.height(10.dp))
                InsightFact(
                    icon = Icons.Rounded.Payments,
                    iconTint = colors.income,
                    iconContainer = colors.incomeContainer,
                    label = "Pendapatan terbesar",
                    title = income.description,
                    value = CurrencyFormatter.rupiah(income.amount),
                )
            }

            val differences = insight.balanceDifferences
            Spacer(Modifier.height(10.dp))
            InsightFact(
                icon = Icons.Rounded.CheckCircle,
                iconTint = if (differences.mismatchCount == 0) colors.income else colors.expense,
                iconContainer = if (differences.mismatchCount == 0) {
                    colors.incomeContainer
                } else {
                    colors.expenseContainer
                },
                label = "Kecocokan saldo",
                title = when {
                    differences.checkCount == 0 -> "Belum ada cek saldo di periode ini"
                    differences.mismatchCount == 0 -> "Semua ${differences.checkCount} cek cocok"
                    else -> "${differences.mismatchCount} dari ${differences.checkCount} cek • " +
                        "terbesar ${CurrencyFormatter.rupiah(differences.largestAbsoluteDifference)}"
                },
                value = when {
                    differences.checkCount == 0 -> "—"
                    differences.mismatchCount == 0 -> "Tepat"
                    else -> "Total " + CurrencyFormatter.rupiah(differences.totalAbsoluteDifference)
                },
            )

            insight.savingRatePercent?.let { rate ->
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.incomeContainer)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Savings,
                        contentDescription = null,
                        tint = colors.income,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Perkiraan porsi tersimpan",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "$rate%",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (rate >= 0) colors.income else colors.expense,
                    )
                }
            }
        }

        if (insight.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    contentDescription = null,
                    tint = colors.gold,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Saran untukmu",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(8.dp))
            insight.suggestions.forEach { suggestion ->
                Row(Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = suggestion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun InsightFact(
    icon: ImageVector,
    iconTint: Color,
    iconContainer: Color,
    label: String,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MoneyTheme.colors.hairline, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FlowStat(
    label: String,
    amount: Long,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(container)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = CurrencyFormatter.rupiah(amount),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BreakdownBar(slice: CategorySlice) {
    val colors = MoneyTheme.colors
    val tint = colors.forCategory(slice.category)
    val fraction by animateFloatAsState(
        targetValue = slice.share.coerceIn(0f, 1f),
        label = "breakdown",
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon = iconFor(slice.category), tint = tint, size = 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row {
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = CurrencyFormatter.rupiah(slice.amount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.15f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(tint),
                )
            }
        }
    }
}
