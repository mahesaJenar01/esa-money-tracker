package com.esa.moneytracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.Transfer
import com.esa.moneytracker.data.model.TransferKind
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.ZoneId

/**
 * One move in the Pindah Dana history, opening its own actions when tapped.
 *
 * The amount is written plainly, with no `+` or `-` and in no colour that means
 * gain or loss. Nothing was earned or spent here and the row must not imply
 * otherwise — the only thing that changed is which pocket holds the money.
 */
@Composable
fun TransferListItem(
    transfer: Transfer,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    /** Bank id to name, and to its colour, for the row's glyph and route. */
    bankNames: Map<String, String> = emptyMap(),
    bankColors: Map<String, BankColor> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MoneyTheme.colors

    Column(modifier.fillMaxWidth()) {
        TransferRow(
            transfer = transfer,
            bankNames = bankNames,
            bankColors = bankColors,
            zone = zone,
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
                transferNotes(transfer, zone).forEach { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TransferAction(
                        icon = Icons.Rounded.EditNote,
                        label = "Ubah",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onEdit,
                        modifier = Modifier.weight(1f),
                    )
                    TransferAction(
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

@Composable
fun TransferRow(
    transfer: Transfer,
    modifier: Modifier = Modifier,
    bankNames: Map<String, String> = emptyMap(),
    bankColors: Map<String, BankColor> = emptyMap(),
    zone: ZoneId = ZoneId.systemDefault(),
    selected: Boolean = false,
    dimmed: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colors = MoneyTheme.colors
    // Tinted by where the money landed, so the eye follows the destination.
    val tint = when {
        dimmed -> colors.muted
        transfer.toBankId == null -> colors.pocketCash
        else -> bankColors[transfer.toBankId]?.let { colors.forBank(it) } ?: colors.pocketOnline
    }
    val route = transfer.route { id -> bankNames[id] }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) colors.surfaceElevated else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (selected) 12.dp else 0.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(icon = iconFor(transfer.kind), tint = tint)

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = route,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (dimmed) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(weight = 1f, fill = false),
                )
                Dot()
                Text(
                    text = IndonesianDates.time(transfer.dateTimeIn(zone)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (transfer.timeAdjusted) {
                    MarkIcon(Icons.Rounded.History, "Dicatat menyusul")
                }
                if (transfer.edited) {
                    MarkIcon(Icons.Rounded.EditNote, "Pernah diubah")
                }
            }

            Spacer(Modifier.height(3.dp))

            Text(
                text = if (transfer.note.isBlank()) {
                    transfer.kind.label
                } else {
                    transfer.kind.label + " • " + transfer.note
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        // No sign and no income/expense colour: nothing was gained or lost.
        Text(
            text = CurrencyFormatter.rupiah(transfer.amount),
            style = MaterialTheme.typography.titleSmall,
            color = if (dimmed) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
        )
    }
}

/** Everything a move can say about its own history, one line each. */
fun transferNotes(transfer: Transfer, zone: ZoneId): List<String> = buildList {
    val created = transfer.createdDateTimeIn(zone)
    add(
        "Dibuat " + IndonesianDates.dayAndDate(created.toLocalDate()) +
            " • " + IndonesianDates.time(created),
    )

    if (transfer.timeAdjusted) {
        val occurred = transfer.dateTimeIn(zone)
        add(
            "Dicatat menyusul — pindahnya " +
                IndonesianDates.dayAndDate(occurred.toLocalDate()) +
                " • " + IndonesianDates.time(occurred),
        )
    }

    transfer.updatedDateTimeIn(zone)?.let { updated ->
        add(
            "Pernah diubah " + IndonesianDates.dayAndDate(updated.toLocalDate()) +
                " • " + IndonesianDates.time(updated) + ", posisinya tidak berpindah",
        )
    }
}

fun iconFor(kind: TransferKind): ImageVector = when (kind) {
    TransferKind.BETWEEN_BANKS -> Icons.Rounded.SwapHoriz
    TransferKind.DEPOSIT -> Icons.Rounded.Savings
    TransferKind.WITHDRAWAL -> Icons.Rounded.AccountBalanceWallet
}

@Composable
private fun MarkIcon(icon: ImageVector, description: String) {
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
            .size(3.dp)
            .clip(CircleShape)
            .background(MoneyTheme.colors.muted),
    )
}

@Composable
private fun TransferAction(
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
