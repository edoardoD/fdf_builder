package manutenzioni.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import manutenzioni.domain.service.DateValidationUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val NOMI_MESI_IT = listOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
)

private val GIORNI_SETTIMANA_IT = listOf("L", "M", "M", "G", "V", "S", "D")

/**
 * Campo di input per date con maschera automatica formato italiano (GG/MM/AAAA),
 * validazione reattiva e selettore DatePicker visuale integrato.
 */
@Composable
fun ItalianDateInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "GG/MM/AAAA",
    isRequired: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    enabled: Boolean = true
) {
    var showDatePicker by remember { mutableStateOf(false) }

    val cleanValue = value.trim()
    val isInvalidDate = cleanValue.length == 10 && !DateValidationUtils.isValidItalianDate(cleanValue)
    val isIncomplete = cleanValue.isNotEmpty() && cleanValue.length < 10
    val hasError = isError || isInvalidDate || (isRequired && cleanValue.isEmpty())

    val displayError = when {
        errorMessage != null -> errorMessage
        isInvalidDate -> "Data non valida (GG/MM/AAAA)"
        isIncomplete -> "Data incompleta (GG/MM/AAAA)"
        isRequired && cleanValue.isEmpty() -> "Campo obbligatorio"
        else -> null
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                val formatted = if (input.length < value.length) {
                    if (input.endsWith("/")) input.dropLast(1) else input
                } else {
                    DateValidationUtils.formatItalianDateInput(input)
                }
                onValueChange(formatted)
            },
            label = {
                Text(
                    text = if (isRequired) "$label *" else label,
                    fontSize = 11.sp
                )
            },
            placeholder = { Text(placeholder, fontSize = 11.sp, color = Color.Gray) },
            singleLine = true,
            enabled = enabled,
            isError = hasError,
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            trailingIcon = {
                IconButton(
                    onClick = { if (enabled) showDatePicker = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = "Seleziona data da calendario",
                        tint = if (hasError) Color(0xFFD32F2F) else MaterialTheme.colors.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            },
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = MaterialTheme.colors.primary,
                unfocusedBorderColor = Color(0xFFCBD5E1),
                errorBorderColor = Color(0xFFD32F2F)
            )
        )

        if (displayError != null) {
            Text(
                text = displayError,
                color = Color(0xFFD32F2F),
                fontSize = 10.sp,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp)
            )
        }
    }

    if (showDatePicker) {
        ItalianDatePickerDialog(
            initialDate = DateValidationUtils.parseItalianDate(cleanValue) ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onDateSelected = { date ->
                onValueChange(DateValidationUtils.formatToItalianDate(date))
                showDatePicker = false
            },
            onClear = if (!isRequired) {
                {
                    onValueChange("")
                    showDatePicker = false
                }
            } else null
        )
    }
}

/**
 * Modale dialog con calendario desktop mensile per la selezione rapida della data.
 */
@Composable
private fun ItalianDatePickerDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onClear: (() -> Unit)? = null
) {
    var currentYearMonth by remember { mutableStateOf(YearMonth.of(initialDate.year, initialDate.monthValue)) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(320.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color.White,
            elevation = 8.dp,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header del selettore
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${NOMI_MESI_IT[currentYearMonth.monthValue - 1]} ${currentYearMonth.year}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = DateValidationUtils.formatToItalianDate(selectedDate),
                            fontSize = 11.sp,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Mese precedente", modifier = Modifier.size(20.dp))
                        }
                        IconButton(
                            onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Mese successivo", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Intestazione giorni settimana
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GIORNI_SETTIMANA_IT.forEach { dayName ->
                        Text(
                            text = dayName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(36.dp)
                        )
                    }
                }

                // Griglia dei giorni
                val firstOfMonth = currentYearMonth.atDay(1)
                val daysInMonth = currentYearMonth.lengthOfMonth()
                val startDayOffset = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7

                val totalCells = startDayOffset + daysInMonth
                val totalRows = (totalCells + 6) / 7

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (row in 0 until totalRows) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0..6) {
                                val cellIndex = row * 7 + col
                                val dayNum = cellIndex - startDayOffset + 1
                                if (dayNum in 1..daysInMonth) {
                                    val cellDate = currentYearMonth.atDay(dayNum)
                                    val isSelected = cellDate == selectedDate
                                    val isToday = cellDate == LocalDate.now()

                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .background(
                                                color = when {
                                                    isSelected -> MaterialTheme.colors.primary
                                                    isToday -> Color(0xFFEFF6FF)
                                                    else -> Color.Transparent
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedDate = cellDate
                                                onDateSelected(cellDate)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = when {
                                                isSelected -> Color.White
                                                isToday -> MaterialTheme.colors.primary
                                                else -> Color(0xFF0F172A)
                                            }
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.size(36.dp))
                                }
                            }
                        }
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Azioni rapide: Oggi, Pulisci, Annulla
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            val today = LocalDate.now()
                            currentYearMonth = YearMonth.of(today.year, today.monthValue)
                            selectedDate = today
                            onDateSelected(today)
                        },
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Oggi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (onClear != null) {
                            TextButton(
                                onClick = onClear,
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("Pulisci", fontSize = 11.sp, color = Color(0xFF64748B))
                            }
                        }
                        TextButton(
                            onClick = onDismiss,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text("Annulla", fontSize = 11.sp, color = Color(0xFFEF4444))
                        }
                    }
                }
            }
        }
    }
}
