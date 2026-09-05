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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EditCalendar
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.esa.moneytracker.ui.theme.MoneyTheme
import com.esa.moneytracker.util.IndonesianDates
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * When the money actually moved.
 *
 * Untouched, it means *now* and says so in one word. Once a date is picked the
 * card spells out what that changes: the note lands in the history on that day,
 * not today, and carries a mark saying it was written later. That is the whole
 * point of the field — a purchase remembered four days on belongs on the day it
 * happened, and the list should not quietly pretend it happened this morning.
 */
@Composable
fun OccurredAtField(
    value: LocalDateTime,
    isNow: Boolean,
    onChange: (LocalDateTime) -> Unit,
    onResetToNow: () -> Unit,
    modifier: Modifier = Modifier,
    today: LocalDate = LocalDate.now(),
) {
    DateTimeField(
        value = value,
        highlighted = !isNow,
        headline = if (isNow) "Sekarang" else IndonesianDates.relativeDay(value.toLocalDate(), today),
        note = "Catatan masuk ke tanggal itu di Riwayat, ditandai dicatat menyusul.",
        resetLabel = "Sekarang saja",
        onChange = onChange,
        onReset = onResetToNow,
        modifier = modifier,
    )
}

/**
 * The date-and-time card, with every word left to the caller.
 *
 * The entry flow and the edit page ask the same question about two different
 * defaults — "now" and "whatever this note already said" — so the mechanics of
 * picking live here and the wording lives with whoever is asking.
 */
@Composable
fun DateTimeField(
    value: LocalDateTime,
    highlighted: Boolean,
    headline: String,
    onChange: (LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    note: String? = null,
    resetLabel: String? = null,
    onReset: (() -> Unit)? = null,
) {
    val colors = MoneyTheme.colors
    var picking by remember { mutableStateOf(PickerStep.NONE) }

    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surfaceElevated)
                .border(
                    width = if (highlighted) 2.dp else 1.dp,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        colors.hairline
                    },
                    shape = RoundedCornerShape(18.dp),
                )
                .clickable { picking = PickerStep.DATE }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.primary else colors.muted,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = IndonesianDates.dayAndDate(value.toLocalDate()) +
                        " • " + IndonesianDates.time(value),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.EditCalendar,
                contentDescription = "Ubah waktu transaksi",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (highlighted && (note != null || resetLabel != null)) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (resetLabel != null && onReset != null) {
                    Text(
                        text = resetLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onReset)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    when (picking) {
        PickerStep.NONE -> Unit

        // Date first, then time — the day is what decides where the note lands,
        // and the clock is a detail on top of it.
        PickerStep.DATE -> DateDialog(
            initial = value.toLocalDate(),
            onDismiss = { picking = PickerStep.NONE },
            onPicked = { date ->
                onChange(LocalDateTime.of(date, value.toLocalTime()))
                picking = PickerStep.TIME
            },
        )

        PickerStep.TIME -> TimeDialog(
            initial = value.toLocalTime(),
            onDismiss = { picking = PickerStep.NONE },
            onPicked = { time ->
                onChange(LocalDateTime.of(value.toLocalDate(), time))
                picking = PickerStep.NONE
            },
        )
    }
}

private enum class PickerStep { NONE, DATE, TIME }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit,
) {
    // The picker speaks in UTC midnight millis, which is exactly what an epoch
    // day multiplied out is — no time zone enters the conversion in either
    // direction, so the day picked is the day returned.
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toEpochDay() * MILLIS_PER_DAY,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = state.selectedDateMillis
                    if (millis != null) {
                        onPicked(LocalDate.ofEpochDay(Math.floorDiv(millis, MILLIS_PER_DAY)))
                    } else {
                        onDismiss()
                    }
                },
            ) { Text("Lanjut ke jam") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    ) {
        DatePicker(state = state, title = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPicked: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jam berapa?") },
        text = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPicked(LocalTime.of(state.hour, state.minute)) },
            ) { Text("Simpan waktu") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        },
    )
}
