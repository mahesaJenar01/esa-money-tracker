package com.esa.moneytracker.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.ZoneId

/**
 * One entry in the history list.
 *
 * The category, where the money sat and the time lead, and the description
 * follows underneath. That order is deliberate: descriptions are long and
 * uneven — "masuk dari penjualan 10 mobil beserta aksesorisnya dari PT…" — and
 * leading with one turned the list into a wall of ragged text with the useful
 * part buried under it. The short, predictable facts go first and the sentence
 * gets a line of its own, clipped to one until the row is opened.
 */
@Composable
fun TransactionRow(
    transaction: Transaction,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
    /** The bank's name, when the note has one. Null falls back to the pocket. */
    bankLabel: String? = null,
    selected: Boolean = false,
    dimmed: Boolean = false,
    /** Opened rows show the whole description instead of clipping it. */
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MoneyTheme.colors
    val tint = colors.forCategory(transaction.category)
    val isIncome = transaction.type == TransactionType.INCOME

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.surfaceElevated else Color.Transparent)
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
            )
            .padding(horizontal = if (selected) 12.dp else 0.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(
            icon = iconFor(transaction.category),
            tint = if (dimmed) colors.muted else tint,
        )

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = transaction.categoryLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Dot()
                Text(
                    text = bankLabel ?: transaction.pocket.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (dimmed) colors.muted else colors.forPocket(transaction.pocket),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )
                Dot()
                Text(
                    text = IndonesianDates.time(transaction.dateTimeIn(zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                // Two quiet marks rather than words: the row says *that*
                // something was changed or backdated, and opening it says what.
                if (transaction.timeAdjusted) {
                    MarkerIcon(Icons.Rounded.History, "Dicatat menyusul")
                }
                if (transaction.edited) {
                    MarkerIcon(Icons.Rounded.EditNote, "Pernah diubah")
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = transaction.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Text(
            text = CurrencyFormatter.signedRupiah(transaction.signedAmount),
            style = MaterialTheme.typography.titleSmall,
            color = when {
                dimmed -> MaterialTheme.colorScheme.onSurfaceVariant
                isIncome -> colors.income
                else -> colors.expense
            },
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun MarkerIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
) {
    Icon(
        imageVector = icon,
        contentDescription = description,
        tint = MoneyTheme.colors.muted,
        modifier = Modifier.size(14.dp),
    )
}

@Composable
private fun Dot() {
    Box(
        Modifier
            .height(3.dp)
            .width(3.dp)
            .clip(CircleShape)
            .background(MoneyTheme.colors.muted),
    )
}
