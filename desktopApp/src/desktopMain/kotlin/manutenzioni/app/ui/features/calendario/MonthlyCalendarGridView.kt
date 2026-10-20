package manutenzioni.app.ui.features.calendario

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.EventoManutenzione
import manutenzioni.domain.model.StatoEventoManutenzione
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

private val NOMI_MESI_IT = listOf(
    "Gennaio", "Febbraio", "Marzo", "Aprile", "Maggio", "Giugno",
    "Luglio", "Agosto", "Settembre", "Ottobre", "Novembre", "Dicembre"
)

private val GIORNI_SETTIMANA = listOf("LUN", "MAR", "MER", "GIO", "VEN", "SAB", "DOM")

/**
 * Griglia mensile interattiva del calendario (7 colonne, Lunedì - Domenica).
 * Visualizza per ogni giorno gli interventi di manutenzione pianificati con badge di stato e cantiere.
 */
@Composable
fun MonthlyCalendarGridView(
    yearMonth: YearMonth,
    selectedDate: String?,
    eventiPerGiorno: Map<String, List<EventoManutenzione>>,
    onDateSelected: (String) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTodayClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val nomeMese = NOMI_MESI_IT[(yearMonth.monthValue - 1).coerceIn(0, 11)]

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header di navigazione mese
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "$nomeMese ${yearMonth.year}".uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    val totalEvents = eventiPerGiorno.values.sumOf { it.size }
                    if (totalEvents > 0) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFBFDBFE))
                        ) {
                            Text(
                                text = "$totalEvents interventi",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1D4ED8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onTodayClicked,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Oggi", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    IconButton(onClick = onPreviousMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Mese Precedente")
                    }
                    IconButton(onClick = onNextMonth, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Mese Successivo")
                    }
                }
            }

            // Intestazione giorni della settimana (LUN - DOM)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC), RoundedCornerShape(6.dp))
                    .padding(vertical = 8.dp)
            ) {
                GIORNI_SETTIMANA.forEach { giorno ->
                    Text(
                        text = giorno,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Griglia giorni
            val firstDayOfMonth = yearMonth.atDay(1)
            val dayOfWeekValue = when (firstDayOfMonth.dayOfWeek) {
                DayOfWeek.MONDAY -> 1
                DayOfWeek.TUESDAY -> 2
                DayOfWeek.WEDNESDAY -> 3
                DayOfWeek.THURSDAY -> 4
                DayOfWeek.FRIDAY -> 5
                DayOfWeek.SATURDAY -> 6
                DayOfWeek.SUNDAY -> 7
            }
            val daysInMonth = yearMonth.lengthOfMonth()
            val totalCells = ((dayOfWeekValue - 1 + daysInMonth + 6) / 7) * 7

            val rows = totalCells / 7
            for (row in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(88.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (col in 0 until 7) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - (dayOfWeekValue - 1) + 1

                        if (dayNumber in 1..daysInMonth) {
                            val currentDate = yearMonth.atDay(dayNumber)
                            val dateString = "%02d/%02d/%04d".format(currentDate.dayOfMonth, currentDate.monthValue, currentDate.year)
                            val isSelected = selectedDate != null && selectedDate.trim() == dateString
                            val isToday = currentDate == today
                            val eventiDelGiorno = eventiPerGiorno[dateString].orEmpty()

                            DayGridCell(
                                dayNumber = dayNumber,
                                dateString = dateString,
                                isToday = isToday,
                                isSelected = isSelected,
                                eventi = eventiDelGiorno,
                                onCellClick = { onDateSelected(dateString) },
                                modifier = Modifier.weight(1f).fillMaxHeight()
                            )
                        } else {
                            // Cella vuota per giorni di mesi adiacenti
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFFFAFAFA), RoundedCornerShape(6.dp))
                            )
                        }
                    }
                }
                if (row < rows - 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

@Composable
private fun DayGridCell(
    dayNumber: Int,
    dateString: String,
    isToday: Boolean,
    isSelected: Boolean,
    eventi: List<EventoManutenzione>,
    onCellClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isSelected -> Color(0xFFEFF6FF)
        isToday -> Color(0xFFFEF3C7)
        eventi.isNotEmpty() -> Color(0xFFFFFFFF)
        else -> Color(0xFFFCFCFD)
    }
    val borderColor = when {
        isSelected -> Color(0xFF3366FF)
        isToday -> Color(0xFFF59E0B)
        eventi.isNotEmpty() -> Color(0xFFCBD5E1)
        else -> Color(0xFFE2E8F0)
    }

    Surface(
        modifier = modifier.clickable { onCellClick() },
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(if (isSelected || isToday) 1.5.dp else 1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayNumber.toString(),
                    fontSize = 11.sp,
                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = when {
                        isSelected -> Color(0xFF1D4ED8)
                        isToday -> Color(0xFFB45309)
                        else -> Color(0xFF334155)
                    }
                )

                if (eventi.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(0xFF3366FF), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = eventi.size.toString(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            // Badge sintetici degli interventi
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                eventi.take(2).forEach { ev ->
                    val impiantiLabel = ev.impiantiPianificati.joinToString("/") { it.codIntervento }
                        .ifEmpty { "Intervento" }
                    val badgeColor = when (ev.stato) {
                        StatoEventoManutenzione.COMPLETATO -> Color(0xFF15803D)
                        StatoEventoManutenzione.IN_CORSO -> Color(0xFFD97706)
                        StatoEventoManutenzione.PIANIFICATO -> Color(0xFF1D4ED8)
                        StatoEventoManutenzione.ANNULLATO -> Color(0xFF64748B)
                    }
                    val badgeBg = when (ev.stato) {
                        StatoEventoManutenzione.COMPLETATO -> Color(0xFFDCFCE7)
                        StatoEventoManutenzione.IN_CORSO -> Color(0xFFFEF3C7)
                        StatoEventoManutenzione.PIANIFICATO -> Color(0xFFDBEAFE)
                        StatoEventoManutenzione.ANNULLATO -> Color(0xFFF1F5F9)
                    }

                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "$impiantiLabel",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                if (eventi.size > 2) {
                    Text(
                        text = "+${eventi.size - 2} altri",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
            }
        }
    }
}
