package com.esa.moneytracker.ui.banks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExtendedFloatingActionButton
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
import com.esa.moneytracker.data.model.BankBalance
import com.esa.moneytracker.data.model.BankClosure
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.ui.components.BankDot
import com.esa.moneytracker.ui.components.Hairline
import com.esa.moneytracker.ui.components.IconBadge
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.CurrencyFormatter

/**
 * What the Online pocket is actually made of.
 *
 * A page rather than a panel folded into the home screen: the list has no fixed
 * length, every row leads to a decision that needs a dialog behind it, and the
 * balance header is already the busiest thing on the home screen.
 */
@Composable
fun BanksScreen(
    state: BanksUiState,
    onBack: () -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onRenameBank: (String, String, BankColor) -> Unit,
    onCorrectBalance: (String, Long, Long) -> Unit,
    onCloseBank: (String, String, BankClosure) -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialog by remember { mutableStateOf<BankDialog>(BankDialog.None) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { dialog = BankDialog.Add },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.navigationBarsPadding(),
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text("Tambah bank", style = MaterialTheme.typography.labelLarge) },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            BanksTopBar(onBack)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 110.dp),
            ) {
                item("total") {
                    Column(Modifier.padding(horizontal = 16.dp)) {
                        TotalCard(state)
                        state.message?.let { message ->
                            Spacer(Modifier.height(12.dp))
                            MessageRow(message, onDismissMessage)
                        }
                        if (state.showUpgradeHint) {
                            Spacer(Modifier.height(12.dp))
                            UpgradeHint()
                        }
                        Spacer(Modifier.height(18.dp))
                    }
                }

                items(state.banks, key = { it.id }) { bank ->
                    Box(Modifier.padding(horizontal = 16.dp)) {
                        BankRow(
                            bank = bank,
                            share = if (state.total > 0L) {
                                (bank.balance.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            },
                            onClick = { dialog = BankDialog.Actions(bank.id) },
                        )
                    }
                }

                if (state.pocket.hasUnassigned) {
                    item("unassigned") {
                        Box(Modifier.padding(horizontal = 16.dp)) {
                            UnassignedRow(state.pocket.unassigned, state.pocket.unassignedCount)
                        }
                    }
                }

                if (!state.loading && state.banks.isEmpty()) {
                    item("empty") { EmptyBanks(Modifier.padding(horizontal = 16.dp)) }
                }
            }
        }
    }

    // Dialogs are addressed by bank id rather than by object, so a balance that
    // changes underneath one cannot leave it showing a stale figure, and a bank
    // that disappears closes its dialog instead of freezing it.
    when (val current = dialog) {
        BankDialog.None -> Unit

        BankDialog.Add -> AddBankDialog(
            others = state.banks,
            suggestedColor = state.suggestedColor,
            onDismiss = { dialog = BankDialog.None },
            onConfirm = { name, color, amount, funding ->
                dialog = BankDialog.None
                onAddBank(name, color, amount, funding)
            },
        )

        is BankDialog.WithBank -> {
            val bank = state.banks.firstOrNull { it.id == current.bankId }
            val others = state.banks.filter { it.id != current.bankId }
            if (bank != null) {
                when (current) {
                    is BankDialog.Actions -> BankActionsDialog(
                        bank = bank,
                        onRename = { dialog = BankDialog.Rename(bank.id) },
                        onCorrect = { dialog = BankDialog.Correct(bank.id) },
                        onClose = { dialog = BankDialog.Close(bank.id) },
                        onDismiss = { dialog = BankDialog.None },
                    )

                    is BankDialog.Rename -> RenameBankDialog(
                        bank = bank,
                        onDismiss = { dialog = BankDialog.None },
                        onConfirm = { name, color ->
                            dialog = BankDialog.None
                            onRenameBank(bank.id, name, color)
                        },
                    )

                    is BankDialog.Correct -> CorrectBalanceDialog(
                        bank = bank,
                        onDismiss = { dialog = BankDialog.None },
                        onConfirm = { newBalance ->
                            dialog = BankDialog.None
                            onCorrectBalance(bank.id, bank.balance, newBalance)
                        },
                    )

                    is BankDialog.Close -> CloseBankDialog(
                        bank = bank,
                        others = others,
                        onDismiss = { dialog = BankDialog.None },
                        onConfirm = { closure ->
                            dialog = BankDialog.None
                            onCloseBank(bank.id, bank.name, closure)
                        },
                    )
                }
            }
        }
    }
}

/** Which dialog is open, addressed by bank id so it survives a data refresh. */
private sealed interface BankDialog {
    data object None : BankDialog
    data object Add : BankDialog

    sealed interface WithBank : BankDialog {
        val bankId: String
    }

    data class Actions(override val bankId: String) : WithBank
    data class Rename(override val bankId: String) : WithBank
    data class Correct(override val bankId: String) : WithBank
    data class Close(override val bankId: String) : WithBank
}

@Composable
private fun BanksTopBar(onBack: () -> Unit) {
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
                text = "Uang online",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "Bank, e-wallet, dan rekening",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TotalCard(state: BanksUiState) {
    SoftCard {
        Text(
            text = "Total online",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = CurrencyFormatter.rupiah(state.total),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Hairline()
        Spacer(Modifier.height(12.dp))
        Text(
            text = when (state.banks.size) {
                0 -> "Belum ada bank terdaftar"
                1 -> "Tersimpan di 1 bank"
                else -> "Tersebar di " + state.banks.size + " bank"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MessageRow(message: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

/**
 * Shown only while the bank the upgrade made for itself is still untouched.
 *
 * Renaming it or adding a second bank makes it stop being true, so the nudge
 * disappears the moment it has been acted on without anything having to
 * remember that it was seen.
 */
@Composable
private fun UpgradeHint() {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconBadge(
            icon = Icons.Rounded.AccountBalance,
            tint = MaterialTheme.colorScheme.primary,
            size = 38.dp,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Saldo online lamamu ada di sini",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Semuanya dikumpulkan ke satu bank bernama Online, lengkap " +
                    "dengan riwayatnya. Ganti namanya sesuai bank aslimu, lalu " +
                    "tambahkan bank lain yang kamu punya.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BankRow(bank: BankBalance, share: Float, onClick: () -> Unit) {
    val colors = MoneyTheme.colors
    val tint = colors.forBank(bank.color)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BankDot(bank.color, size = 12.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = bank.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when (bank.recordCount) {
                        0 -> "Belum ada catatan"
                        1 -> "1 catatan"
                        else -> bank.recordCount.toString() + " catatan"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = CurrencyFormatter.rupiah(bank.balance),
                style = MaterialTheme.typography.titleSmall,
                color = if (bank.balance < 0L) colors.expense else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = "Kelola " + bank.name,
                tint = colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(10.dp))

        // How much of the online money sits here, at a glance.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(colors.hairline),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share)
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(tint),
            )
        }
    }
}

@Composable
private fun UnassignedRow(amount: Long, count: Int) {
    val colors = MoneyTheme.colors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceElevated)
            .border(1.dp, colors.hairline, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Belum masuk bank manapun",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = count.toString() + " catatan online tanpa bank. Ubah catatannya " +
                    "untuk memilih bank.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = CurrencyFormatter.rupiah(amount),
            style = MaterialTheme.typography.titleSmall,
            color = colors.muted,
        )
    }
}

@Composable
private fun EmptyBanks(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        IconBadge(
            icon = Icons.Rounded.AccountBalance,
            tint = MaterialTheme.colorScheme.primary,
            size = 76.dp,
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "Belum ada bank",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tambahkan bank, e-wallet, atau rekening yang kamu punya beserta " +
                "saldonya. Semua uang online kamu dihitung dari sini.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}
