package com.mrjack.dressflow.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerField(
    label: String,
    value: String,            // formato "YYYY-MM-DD"
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val initialMillis = remember(value) {
        if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            try {
                val cal = Calendar.getInstance()
                val parts = value.split("-")
                cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 12, 0, 0)
                cal.timeInMillis
            } catch (_: Exception) { null }
        } else null
    }

    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

    val displayValue = if (value.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
        val p = value.split("-"); "${p[2]}/${p[1]}/${p[0]}"
    } else if (value.isBlank()) "" else value

    OutlinedTextField(
        value = displayValue,
        onValueChange = {},
        label = { Text(label) },
        readOnly = true,
        trailingIcon = {
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Default.CalendarToday, contentDescription = null)
            }
        },
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
    )

    if (showDialog) {
        DatePickerDialog(
            onDismissRequest = { showDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        val cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
                        cal.timeInMillis = millis
                        val y = cal.get(Calendar.YEAR)
                        val m = cal.get(Calendar.MONTH) + 1
                        val d = cal.get(Calendar.DAY_OF_MONTH)
                        onDateSelected("$y-${m.toString().padStart(2,'0')}-${d.toString().padStart(2,'0')}")
                    }
                    showDialog = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Cancelar") }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
