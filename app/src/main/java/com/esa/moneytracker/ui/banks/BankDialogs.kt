package com.esa.moneytracker.ui.banks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.BankColorPicker
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter

private const val MAX_AMOUNT_DIGITS = 12

private fun String.digitsOnly(): String =
    filter(Char::isDigit).trimStart('0').take(MAX_AMOUNT_DIGITS)

/**
 * Adding a bank.
 *
 * The question that matters is the last one. A balance the app has never seen is
 * new money and lifts the Online total; a balance it has already been counting
 * under another bank is only being re-labelled, and taking it off that bank is
 * the only way "the total stays the same" can actually be true rather than
 * wishful. That is why the source bank is a required answer and not a nicety.
 */
@Composable
fun AddBankDialog(
    others: List<BankBalance>,
    suggestedColor: BankColor,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: BankColor, amount: Long, funding: BankFunding) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var color by remember { mutableStateOf(suggestedColor) }
    var digits by remember { mutableStateOf("") }
    var additional by remember { mutableStateOf(true) }
    var sourceId by remember { mutableStateOf(others.firstOrNull()?.id) }
    var showErrors by remember { mutableStateOf(false) }

    val amount = digits.toLongOrNull() ?: 0L
    // With no other bank there is nothing to take the money from, and with no
    // money there is nothing to take — either way the question is not worth
    // asking, and "tambahan" is the only truthful answer.
    val askFunding = others.isNotEmpty() && amount > 0L
    val fundingReady = !askFunding || additional || sourceId != null
    val nameReady = name.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah bank") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NameField(
                    value = name,
                    onValueChange = { name = it },
                    isError = showErrors && !nameReady,
                )

                Spacer(Modifier.height(16.dp))
                FieldLabel("Warna")
                Spacer(Modifier.height(8.dp))
                BankColorPicker(selected = color, onSelect = { color = it })

                Spacer(Modifier.height(16.dp))
                FieldLabel("Saldo di bank ini")
                Spacer(Modifier.height(8.dp))
                AmountField(
                    digits = digits,
                    onDigitsChanged = { digits = it.digitsOnly() },
                    type = TransactionType.INCOME,
                )

                if (askFunding) {
                    Spacer(Modifier.height(8.dp))
                    FieldLabel("Saldo ini sudah termasuk total online sekarang?")
                    Spacer(Modifier.height(8.dp))

                    ChoiceRow(
                        selected = additional,
                        title = "Belum — ini tambahan",
                        subtitle = "Total online naik " + CurrencyFormatter.rupiah(amount) + ".",
                        onClick = { additional = true },
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(
                        selected = !additional,
                        title = "Sudah termasuk",
                        subtitle = "Total online tidak berubah — uangnya dipindahkan " +
                            "dari bank yang kamu pilih.",
                        onClick = { additional = false },
                    )

                    if (!additional) {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel("Selama ini tercatat di bank mana?")
                        Spacer(Modifier.height(8.dp))
                        others.forEach { bank ->
                            BankOptionRow(
                                bank = bank,
                                selected = sourceId == bank.id,
                                onClick = { sourceId = bank.id },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (showErrors && sourceId == null) {
                            Text(
                                text = "Pilih satu bank, kalau tidak totalnya jadi selisih.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!nameReady || !fundingReady) {
                        showErrors = true
                        return@TextButton
                    }
                    val funding = if (askFunding && !additional) {
                        BankFunding.MovedFrom(sourceId!!)
                    } else {
                        BankFunding.Additional
                    }
                    onConfirm(name, color, amount, funding)
                },
            ) { Text("Tambah") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** Renaming and recolouring; the balance is corrected somewhere else. */
@Composable
fun RenameBankDialog(
    bank: BankBalance,
    onDismiss: () -> Unit,
    onConfirm: (name: String, color: BankColor) -> Unit,
) {
    var name by remember { mutableStateOf(bank.name) }
    var color by remember { mutableStateOf(bank.color) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ubah bank") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                NameField(value = name, onValueChange = { name = it }, isError = name.isBlank())
                Spacer(Modifier.height(16.dp))
                FieldLabel("Warna")
                Spacer(Modifier.height(8.dp))
                BankColorPicker(selected = color, onSelect = { color = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onConfirm(name, color) },
            ) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/**
 * Saying what a bank really holds right now.
 *
 * The difference is kept as a correction rather than rewriting the opening
 * balance, so the history of the number survives the fix.
 */
@Composable
fun CorrectBalanceDialog(
    bank: BankBalance,
    onDismiss: () -> Unit,
    onConfirm: (newBalance: Long) -> Unit,
) {
    var digits by remember { mutableStateOf(bank.balance.coerceAtLeast(0L).toString()) }
    val target = digits.toLongOrNull() ?: 0L
    val delta = target - bank.balance

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Koreksi saldo " + bank.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Tercatat sekarang " + CurrencyFormatter.rupiah(bank.balance) +
                        ". Isi angka yang sebenarnya ada di bank ini.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                AmountField(
                    digits = digits,
                    onDigitsChanged = { digits = it.digitsOnly() },
                    type = TransactionType.INCOME,
                )
                if (delta != 0L) {
                    Text(
                        text = "Total online " + (if (delta > 0) "naik " else "turun ") +
                            CurrencyFormatter.rupiah(kotlin.math.abs(delta)) + ".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/**
 * Closing a bank.
 *
 * The money either left the picture or went somewhere else, and the app cannot
 * guess which. Answering "it moved" without saying where would leave the total
 * claiming money that is not in any bank, so the destination is required — and
 * the notes recorded in the closed bank follow the money there.
 */
@Composable
fun CloseBankDialog(
    bank: BankBalance,
    others: List<BankBalance>,
    onDismiss: () -> Unit,
    onConfirm: (BankClosure) -> Unit,
) {
    var moving by remember { mutableStateOf(others.isNotEmpty()) }
    var targetId by remember { mutableStateOf(others.firstOrNull()?.id) }
    var showErrors by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hapus " + bank.name + "?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Bank ini tercatat " + CurrencyFormatter.rupiah(bank.balance) +
                        (
                            if (bank.recordCount > 0) {
                                " dan menampung " + bank.recordCount + " catatan."
                            } else {
                                "."
                            }
                            ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))

                ChoiceRow(
                    selected = !moving,
                    title = "Kurangi dari total online",
                    subtitle = "Uangnya memang sudah tidak ada. Total online turun " +
                        CurrencyFormatter.rupiah(bank.balance) + ".",
                    onClick = { moving = false },
                )

                if (others.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(
                        selected = moving,
                        title = "Sudah dipindahkan ke bank lain",
                        subtitle = "Total online tetap. Saldo dan catatannya ikut " +
                            "ke bank yang kamu pilih.",
                        onClick = { moving = true },
                    )

                    if (moving) {
                        Spacer(Modifier.height(12.dp))
                        FieldLabel("Pindah ke mana?")
                        Spacer(Modifier.height(8.dp))
                        others.forEach { option ->
                            BankOptionRow(
                                bank = option,
                                selected = targetId == option.id,
                                onClick = { targetId = option.id },
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                        if (showErrors && targetId == null) {
                            Text(
                                text = "Wajib pilih satu, kalau tidak totalnya jadi selisih.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Catatan lama tetap ada di Riwayat dan tetap menyebut nama " +
                        "bank ini.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val target = targetId
                    if (moving && target == null) {
                        showErrors = true
                        return@TextButton
                    }
                    onConfirm(
                        if (moving && target != null) {
                            BankClosure.MoveTo(target)
                        } else {
                            BankClosure.DropBalance
                        },
                    )
                },
            ) { Text("Hapus bank") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}

/** The three things that can be done to one bank. */
@Composable
fun BankActionsDialog(
    bank: BankBalance,
    onRename: () -> Unit,
    onCorrect: () -> Unit,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BankDot(bank.color, size = 12.dp)
                Spacer(Modifier.width(10.dp))
                Text(bank.name)
            }
        },
        text = {
            Column {
                ActionRow(Icons.Rounded.Tune, "Koreksi saldo", CurrencyFormatter.rupiah(bank.balance), onCorrect)
                Spacer(Modifier.height(8.dp))
                ActionRow(Icons.Rounded.EditNote, "Ubah nama & warna", null, onRename)
                Spacer(Modifier.height(8.dp))
                ActionRow(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Hapus bank",
                    subtitle = "Tanya dulu ke mana saldonya",
                    onClick = onClose,
                    tint = MoneyTheme.colors.expense,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } },
    )
}

@Composable
private fun NameField(value: String, onValueChange: (String) -> Unit, isError: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Nama bank") },
        placeholder = { Text("Contoh: BCA, Dana, Jago") },
        singleLine = true,
        isError = isError,
        supportingText = { if (isError) Text("Nama wajib diisi.") },
        shape = RoundedCornerShape(16.dp),
    )
}

@Composable
private fun ChoiceRow(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                } else {
                    colors.surfaceElevated
                },
            )
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else colors.hairline,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = onClick, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BankOptionRow(
    bank: BankBalance,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.forBank(bank.color) else colors.hairline,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BankDot(bank.color)
        Spacer(Modifier.width(10.dp))
        Text(
            text = bank.name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = CurrencyFormatter.rupiah(bank.balance),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
