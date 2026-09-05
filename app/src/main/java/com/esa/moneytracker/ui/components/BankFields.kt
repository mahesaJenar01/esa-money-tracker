package com.esa.moneytracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.ui.theme.MoneyTheme

/*
 * The pieces every bank-shaped question is built from: a coloured dot, a row of
 * bank chips, and the palette a bank is named with.
 *
 * They live here rather than in one screen because the same three questions get
 * asked in the entry flow, on the edit page, on the bank page and in the
 * first-run screen, and they have to look and behave identically in all four.
 */

/** The coloured dot that stands for a bank wherever its name appears. */
@Composable
fun BankDot(color: BankColor, modifier: Modifier = Modifier, size: Dp = 10.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MoneyTheme.colors.forBank(color)),
    )
}

/**
 * Which bank the money moved through.
 *
 * With a single bank there is nothing to decide, so the chip is shown selected
 * and the question costs the user nothing. [onAddBank] is what turns an empty
 * list into a way forward rather than a dead end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankPicker(
    banks: List<BankBalance>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    onAddBank: (() -> Unit)? = null,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        banks.forEach { bank ->
            BankChip(
                name = bank.name,
                color = bank.color,
                selected = bank.id == selectedId,
                onClick = { onSelect(bank.id) },
            )
        }
        if (onAddBank != null) {
            AddBankChip(onClick = onAddBank, hasBanks = banks.isNotEmpty())
        }
    }
}

@Composable
fun BankChip(
    name: String,
    color: BankColor,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors
    val tint = colors.forBank(color)

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
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankDot(color)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AddBankChip(onClick: () -> Unit, hasBanks: Boolean) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (hasBanks) "Bank lain" else "Tambah bank",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/** The palette a bank is given when it is created or renamed. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BankColorPicker(
    selected: BankColor,
    onSelect: (BankColor) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BankColor.entries.forEach { option ->
            val tint = MoneyTheme.colors.forBank(option)
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint)
                    .border(
                        width = if (option == selected) 3.dp else 0.dp,
                        color = if (option == selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            Color.Transparent
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                if (option == selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = option.label,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
