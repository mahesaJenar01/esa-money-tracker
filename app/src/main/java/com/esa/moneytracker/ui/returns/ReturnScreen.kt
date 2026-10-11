package com.esa.moneytracker.ui.returns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.BankColor
import com.esa.moneytracker.data.model.BankFunding
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.ReturnKind
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.banks.AddBankDialog
import com.esa.moneytracker.ui.components.AmountField
import com.esa.moneytracker.ui.components.BankPicker
import com.esa.moneytracker.ui.components.DescriptionField
import com.esa.moneytracker.ui.components.FieldLabel
import com.esa.moneytracker.ui.components.OccurredAtField
import com.esa.moneytracker.ui.components.PocketPicker
import com.esa.moneytracker.ui.components.SoftCard
import com.esa.moneytracker.ui.components.SubmitButton
import com.esa.moneytracker.ui.components.TagsField
import com.esa.moneytracker.util.CurrencyFormatter
import com.esa.moneytracker.util.IndonesianDates
import java.time.LocalDateTime

@Composable
fun ReturnScreen(
    state: ReturnUiState,
    onBack: () -> Unit,
    onKind: (ReturnKind) -> Unit,
    onAmount: (String) -> Unit,
    onPocket: (Pocket) -> Unit,
    onBank: (String) -> Unit,
    onAddBank: (String, BankColor, Long, BankFunding) -> Unit,
    onDescription: (String) -> Unit,
    onTags: (String) -> Unit,
    onDate: (LocalDateTime) -> Unit,
    onResetDate: () -> Unit,
    onSubmit: () -> Unit,
) {
    var addingBank by remember { mutableStateOf(false) }
    if (addingBank) AddBankDialog(
        others = state.banks, suggestedColor = BankColor.suggestFor(state.banks.map { it.color }),
        onDismiss = { addingBank = false },
        onConfirm = { name, color, amount, funding -> addingBank = false; onAddBank(name, color, amount, funding) },
    )
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
                Text("Catat pengembalian", style = MaterialTheme.typography.titleLarge)
            }
            when {
                state.loading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.expense == null || state.expense.canReceiveReturn != true -> Text("Pengeluaran ini tidak tersedia atau sudah dikembalikan seluruhnya.", Modifier.padding(16.dp))
                else -> Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val expense = state.expense
                    SoftCard {
                        Text(expense.description, style = MaterialTheme.typography.titleMedium)
                        Text(expense.categoryLabel + " • " + IndonesianDates.dayAndDate(expense.dateIn(java.time.ZoneId.systemDefault())))
                        Text("Biaya awal: " + CurrencyFormatter.rupiah(expense.amount))
                        Text("Sudah kembali: " + CurrencyFormatter.rupiah(expense.returnedAmount))
                        Text("Sisa biaya: " + CurrencyFormatter.rupiah(expense.netCost))
                    }
                    Text("Catat setelah uang diterima. Saldo bertambah dan pengeluaran bersih berkurang pada tanggal penerimaan; pemasukan tidak bertambah.", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReturnKind.entries.forEach { kind -> FilterChip(selected = state.kind == kind, onClick = { onKind(kind) }, label = { Text(kind.label) }) }
                    }
                    AmountField(state.amountDigits, onAmount, TransactionType.INCOME,
                        isError = state.showErrors && state.amountError != null, errorText = state.amountError)
                    FieldLabel("Uang diterima di mana?")
                    PocketPicker(state.pocket, onPocket)
                    if (state.pocket == Pocket.ONLINE) {
                        BankPicker(state.banks, state.bankId, onBank, onAddBank = { addingBank = true })
                        if (state.showErrors && state.bankError != null) Text(state.bankError.orEmpty(), color = MaterialTheme.colorScheme.error)
                    }
                    FieldLabel("Kapan uang diterima?")
                    OccurredAtField(state.occurredAt ?: state.nowStamp, state.occurredAt == null, onDate, onResetDate)
                    DescriptionField(state.description, onDescription, isError = state.showErrors && state.description.isBlank(), errorText = "Keterangan wajib diisi.")
                    TagsField(state.tagsText, onTags, state.knownTags)
                    if (state.message != null) Text(state.message, color = MaterialTheme.colorScheme.error)
                    SubmitButton("Simpan " + state.kind.label, onSubmit, loading = state.saving, enabled = !state.saving)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
