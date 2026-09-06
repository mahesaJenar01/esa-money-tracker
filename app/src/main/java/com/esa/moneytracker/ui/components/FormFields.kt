package com.esa.moneytracker.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.data.model.Pocket
import com.esa.moneytracker.data.model.TransactionType
import com.esa.moneytracker.ui.theme.MoneyTheme

/*
 * The inputs a transaction needs — nominal, kantong, keterangan — plus the
 * button that ends the form.
 *
 * They live here rather than inside one screen because the entry flow and the
 * edit page ask for exactly the same things and have to look and behave
 * identically.
 */

@Composable
private fun moneyFieldColors(): TextFieldColors {
    val colors = MoneyTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = colors.hairline,
        focusedContainerColor = colors.surfaceElevated,
        unfocusedContainerColor = colors.surfaceElevated,
    )
}

@Composable
fun FieldLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Digits in, rupiah on screen. */
@Composable
fun AmountField(
    digits: String,
    onDigitsChanged: (String) -> Unit,
    type: TransactionType,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    imeAction: ImeAction = ImeAction.Next,
) {
    val colors = MoneyTheme.colors

    OutlinedTextField(
        value = digits,
        onValueChange = onDigitsChanged,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = "Rp 0",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.muted,
            )
        },
        textStyle = MaterialTheme.typography.headlineMedium.copy(color = colors.forType(type)),
        visualTransformation = remember { RupiahVisualTransformation() },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = imeAction,
        ),
        singleLine = true,
        isError = isError,
        supportingText = {
            if (isError && errorText != null) {
                Text(errorText)
            } else {
                Text("Hanya angka. Otomatis diformat ke rupiah.")
            }
        },
        shape = RoundedCornerShape(18.dp),
        colors = moneyFieldColors(),
    )
}

@Composable
fun DescriptionField(
    value: String,
    onValueChanged: (String) -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    errorText: String? = null,
    placeholder: String = "Contoh: makan siang di kantin",
    /** What the field says when it is not complaining. */
    helperText: String = "Wajib diisi.",
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChanged,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Done,
        ),
        minLines = 2,
        isError = isError,
        supportingText = {
            if (isError && errorText != null) Text(errorText) else Text(helperText)
        },
        shape = RoundedCornerShape(18.dp),
        colors = moneyFieldColors(),
    )
}

@Composable
fun PocketPicker(
    selected: Pocket,
    onSelect: (Pocket) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Pocket.entries.forEach { pocket ->
            PocketOption(
                pocket = pocket,
                selected = selected == pocket,
                onClick = { onSelect(pocket) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PocketOption(
    pocket: Pocket,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MoneyTheme.colors
    val tint = colors.forPocket(pocket)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) tint.copy(alpha = 0.14f) else colors.surfaceElevated)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tint else colors.hairline,
                shape = RoundedCornerShape(18.dp),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = iconFor(pocket),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = pocket.label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = pocket.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The full-width primary button that ends a form.
 *
 * Nothing here applies a window inset: padding added *after* a fixed height is
 * subtracted from that height instead of sitting outside it, which is what used
 * to collapse this button into a bare green line whenever the keyboard was
 * closed and the navigation bar inset was non-zero. Screens give the button its
 * breathing room through their own bottom padding.
 */
@Composable
fun SubmitButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
