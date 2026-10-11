package com.esa.moneytracker.ui.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Category
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.components.SoftCard

@Composable
fun CategoriesScreen(
    state: CategoriesUiState,
    onBack: () -> Unit,
    onSave: (String, TransactionType, String?) -> Unit,
    onArchive: (Category) -> Unit,
    onDismissError: () -> Unit,
) {
    var typeId by rememberSaveable { mutableStateOf(TransactionType.EXPENSE.id) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    val type = TransactionType.fromId(typeId)
    LaunchedEffect(state.revision) { if (state.revision > 0) editing = false }

    if (editing) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) { editing = false; onDismissError() } },
            title = { Text(if (editingId == null) "Tambah kategori " + type.label.lowercase() else "Ubah nama kategori") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it; onDismissError() }, label = { Text("Nama kategori") }, singleLine = true)
                    Text(state.error ?: "Maksimal 50 karakter. Nama yang sama tidak bisa dipakai dua kali untuk jenis ini.",
                        color = if (state.error == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = { TextButton(onClick = { onSave(name, type, editingId) }, enabled = !state.busy && name.isNotBlank() && name.trim().length <= 50) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { editing = false; onDismissError() }, enabled = !state.busy) { Text("Batal") } },
        )
    }

    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
                Text("Kategori", style = MaterialTheme.typography.titleLarge)
            }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Kategori menjelaskan jenis uang masuk atau keluar. Tag menghubungkan catatan lintas kategori, misalnya Kerja atau Liburan Bali.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TransactionType.entries.forEach { option ->
                            FilterChip(selected = type == option, onClick = { typeId = option.id }, label = { Text(option.label) })
                        }
                    }
                    Button(onClick = { editingId = null; name = ""; onDismissError(); editing = true }, enabled = !state.busy) { Text("Tambah kategori") }
                    if (!editing && state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                items(state.categories.filter { it.type == type }, key = { it.id }) { category ->
                    SoftCard {
                        Text(category.label, style = MaterialTheme.typography.titleMedium)
                        Text(if (!category.custom) "Kategori bawaan" else if (category.archived) "Diarsipkan — tetap ada di catatan lama" else "Kategori pribadi", style = MaterialTheme.typography.bodySmall)
                        if (category.custom) {
                            Row {
                                TextButton(onClick = { editingId = category.id; name = category.label; onDismissError(); editing = true }, enabled = !state.busy) { Text("Ubah nama") }
                                TextButton(onClick = { onArchive(category) }, enabled = !state.busy) { Text(if (category.archived) "Aktifkan" else "Arsipkan") }
                            }
                        }
                    }
                }
            }
        }
    }
}
