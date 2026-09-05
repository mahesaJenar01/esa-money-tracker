package com.esa.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MoreHoriz
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
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * The gradient header: total balance up top, one tile per pocket underneath.
 *
 * It is the first thing on the screen and carries the brand, so it is drawn
 * full-bleed with its own decorative shapes rather than as a plain card.
 *
 * The Online tile opens: that figure is a sum of banks and the question it
 * invites — "which of them is that?" — deserves an answer one tap away.
 */
@Composable
fun BalanceHeader(
    total: Long,
    online: Long,
    cash: Long,
    modifier: Modifier = Modifier,
    bankCount: Int = 0,
    onOpenBanks: (() -> Unit)? = null,
    onOpenData: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(MoneyTheme.colors.balanceBrush()),
    ) {
        // Decorative bokeh — keeps the large flat area from feeling empty.
        Box(
            Modifier
                .size(220.dp)
                .offset(x = 210.dp, y = (-90).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f)),
        )
        Box(
            Modifier
                .size(150.dp)
                .offset(x = (-50).dp, y = 90.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f)),
        )

        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Total Saldo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    modifier = Modifier.weight(1f),
                )
                if (onOpenData != null) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.15f))
                            .clickable(onClick = onOpenData),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MoreHoriz,
                            contentDescription = "Data dan cadangan",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = CurrencyFormatter.rupiah(total),
                style = MaterialTheme.typography.displaySmall,
                color = Color.White,
            )

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PocketTile(
                    pocket = Pocket.ONLINE,
                    amount = online,
                    icon = iconFor(Pocket.ONLINE),
                    caption = when (bankCount) {
                        0 -> "Belum ada bank"
                        1 -> "1 bank"
                        else -> bankCount.toString() + " bank"
                    },
                    onClick = onOpenBanks,
                    modifier = Modifier.weight(1f),
                )
                PocketTile(
                    pocket = Pocket.CASH,
                    amount = cash,
                    icon = iconFor(Pocket.CASH),
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PocketTile(
    pocket: Pocket,
    amount: Long,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.13f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(15.dp),
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(
                text = pocket.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f),
            )
            if (onClick != null) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Lihat rincian " + pocket.label,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = CurrencyFormatter.rupiah(amount),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}
