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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.PriorityHigh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.esa.moneytracker.data.model.BalanceCheck
import com.esa.moneytracker.data.model.BalanceCheckItem
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.ZoneId

/**
 * The line drawn across the history where the balances were last checked.
 *
 * It is deliberately not shaped like a note. A note is a card in the column; a
 * check is a rule *across* the column, because what it says is about everything
 * around it rather than about itself: below the line the app and the banks
 * agreed, above it nothing has been verified yet. When a total stops matching,
 * the missing transaction is somewhere in the stretch above the newest line.
 *
 * Opening it turns the line into the evidence — what the app thought each bank
 * held that evening and what the bank itself said — which is what makes an old
 * mark worth keeping rather than just a date.
 */
@Composable
fun BalanceCheckMark(
    check: BalanceCheck,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    zone: ZoneId = ZoneId.systemDefault(),
) {
    val colors = MoneyTheme.colors
    val matched = check.matched
    val tint = if (matched) colors.income else colors.expense
    var confirming by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Rule(Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (matched) {
                        Icons.Rounded.Bookmark
                    } else {
                        Icons.Rounded.PriorityHigh
                    },
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Saldo dicek • " + IndonesianDates.time(check.dateTimeIn(zone)),
                    style = MaterialTheme.typography.labelMedium,
                    color = tint,
                )
            }
            Rule(Modifier.weight(1f))
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = summaryOf(check),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(Modifier.padding(top = 10.dp)) {
                SoftCard(contentPadding = PaddingValues(16.dp)) {
                    Text(
                        text = "Pemeriksaan saldo",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = IndonesianDates.dayAndDate(check.dateIn(zone)) + " • " +
                            IndonesianDates.time(check.dateTimeIn(zone)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (check.note.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = check.note,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Hairline()

                    check.items.forEach { item -> CheckedItemRow(item) }

                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = footnoteOf(check),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))
                    DangerAction(label = "Hapus penanda", onClick = { confirming = true })
                }
            }
        }
    }

    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Hapus penanda ini?") },
            text = {
                Text(
                    "Hanya penandanya yang hilang. Catatan selisih yang terlanjur " +
                        "dibuat tetap ada di Riwayat, karena itu catatan tentang uang " +
                        "yang benar-benar berpindah.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onDelete()
                    },
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Batal") }
            },
        )
    }
}

@Composable
private fun Rule(modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(1.dp)
            .background(MoneyTheme.colors.hairline),
    )
}

@Composable
private fun CheckedItemRow(item: BalanceCheckItem) {
    val colors = MoneyTheme.colors
    val tint = if (item.matched) colors.income else colors.expense

    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (item.matched) {
                    "Cocok"
                } else {
                    CurrencyFormatter.signedRupiah(item.difference)
                },
                style = MaterialTheme.typography.labelLarge,
                color = tint,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "Aplikasi " + CurrencyFormatter.rupiah(item.appBalance) +
                " • bank " + CurrencyFormatter.rupiah(item.realBalance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.adjustmentId != null) {
            Text(
                text = "Selisihnya sudah ditulis jadi catatan.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DangerAction(label: String, onClick: () -> Unit) {
    val tint = MoneyTheme.colors.expense

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

/** The one line the collapsed mark gets to say for itself. */
private fun summaryOf(check: BalanceCheck): String {
    val counted = check.items.size
    if (counted == 0) return "Ditandai tanpa mencocokkan saldo apa pun"

    val off = check.mismatched
    return when {
        off.isEmpty() -> counted.toString() + " kantong dicek, semuanya cocok"
        off.size == 1 ->
            off[0].label + " selisih " + CurrencyFormatter.signedRupiah(off[0].difference)
        else ->
            off.size.toString() + " dari " + counted + " kantong selisih, total " +
                CurrencyFormatter.signedRupiah(check.difference)
    }
}

/** What the mark means for everything drawn above it. */
private fun footnoteOf(check: BalanceCheck): String = when {
    check.items.isEmpty() ->
        "Catatan di atas garis ini belum pernah dicocokkan dengan bank."

    check.matched ->
        "Sampai garis ini catatan dan bank sudah cocok. Kalau sekarang selisih, " +
            "transaksinya ada di antara garis ini dan hari ini."

    check.recordedCount > 0 ->
        "Selisihnya sudah ditutup dengan catatan, jadi dari garis ini ke bawah " +
            "angkanya cocok. Kalau sekarang selisih lagi, cari di atas garis ini."

    else ->
        "Selisihnya dibiarkan terbuka waktu itu — transaksi yang hilang belum " +
            "ketemu dan belum dicatat."
}
