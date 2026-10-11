package com.esa.moneytracker.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.normalizeTags
import com.esa.moneytracker.data.model.parseTags

@Composable
fun TagsField(value: String, onChange: (String) -> Unit, suggestions: List<String>) {
    val selected = parseTags(value)
    Column {
        OutlinedTextField(
            value = value, onValueChange = onChange, modifier = Modifier.fillMaxWidth(),
            label = { Text("Tag (opsional)") },
            placeholder = { Text("Kerja, Pribadi, Liburan Bali") },
            supportingText = { Text("Pisahkan dengan koma. Satu catatan boleh punya beberapa tag.") },
        )
        if (suggestions.isNotEmpty()) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(12).forEach { tag ->
                    val active = selected.any { it.equals(tag, ignoreCase = true) }
                    FilterChip(
                        selected = active,
                        onClick = {
                            val tags = if (active) selected.filterNot { it.equals(tag, ignoreCase = true) } else normalizeTags(selected + tag)
                            onChange(tags.joinToString(", "))
                        }, label = { Text(tag) },
                    )
                }
            }
        }
    }
}
