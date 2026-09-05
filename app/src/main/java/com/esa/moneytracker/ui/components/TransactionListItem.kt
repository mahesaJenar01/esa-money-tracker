package com.esa.moneytracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Transaction
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.IndonesianDates
import java.time.ZoneId

/**
 * A history row that opens when tapped.
 *
 * Opening it does three things at once: the description stops being clipped and
 * shows in full, the note says when it was written, backdated or last changed,
 * and **Ubah** and **Hapus** appear directly underneath. All three belong to the
 * same gesture because they answer the same question — "what is this entry,
 * really?" — and none of them is worth a screen of its own.
 */
@Composable
fun TransactionListItem(
    transaction: Transaction,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    bankLabel: String? = null,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MoneyTheme.colors

    Column(modifier.fillMaxWidth()) {
        TransactionRow(
            transaction = transaction,
            zone = zone,
            bankLabel = bankLabel,
            selected = expanded,
            expanded = expanded,
            onClick = onToggle,
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp)) {
                transactionNotes(transaction, zone).forEach { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    RowAction(
                        icon = Icons.Rounded.EditNote,
                        label = "Ubah",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                    RowAction(
                        icon = Icons.Rounded.DeleteOutline,
                        label = "Hapus",
                        tint = colors.expense,
                        onClick = onDelete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Everything the note can say about its own history, one line each.
 *
 * The two marks are independent and both can be true at once: a purchase from
 * the 1st entered on the 5th with the wrong amount, then corrected on the 6th,
 * has something to say on all three counts.
 */
fun transactionNotes(transaction: Transaction, zone: ZoneId): List<String> = buildList {
    val created = transaction.createdDateTimeIn(zone)
    add("Dibuat " + IndonesianDates.dayAndDate(created.toLocalDate()) + " • " + stamp(created))

    if (transaction.timeAdjusted) {
        val occurred = transaction.dateTimeIn(zone)
        add(
            "Dicatat menyusul — transaksinya " +
                IndonesianDates.dayAndDate(occurred.toLocalDate()) + " • " + stamp(occurred),
        )
    }

    transaction.updatedDateTimeIn(zone)?.let { updated ->
        add(
            "Pernah diubah " + IndonesianDates.dayAndDate(updated.toLocalDate()) +
                " • " + stamp(updated) + ", posisinya tidak berpindah",
        )
    }
}

private fun stamp(value: java.time.LocalDateTime): String = IndonesianDates.time(value)

@Composable
private fun RowAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}
