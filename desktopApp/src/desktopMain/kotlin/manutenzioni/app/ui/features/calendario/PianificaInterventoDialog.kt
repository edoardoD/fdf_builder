package manutenzioni.app.ui.features.calendario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo

private val PERIODICITA_OPZIONI = listOf(
    Periodo(TipoPeriodo.M, 1),
    Periodo(TipoPeriodo.M, 3),
    Periodo(TipoPeriodo.M, 6),
    Periodo(TipoPeriodo.A, 1),
    Periodo(TipoPeriodo.A, 2),
    Periodo(TipoPeriodo.A, 5)
)

private val CODICI_IMPIANTI_COMUNI = listOf(
    "EM" to "Illuminazione di Emergenza (EM)",
    "Q" to "Quadri Elettrici BT (Q)",
    "RI" to "Rilevazione Incendi (RI)",
    "QMT" to "Quadri Media Tensione (QMT)",
    "GE" to "Gruppi Elettrogeni (GE)",
    "PEM" to "Pulsanti Emergenza (PEM)",
    "RIG" to "Rilevazione Gas (RIG)"
)

/**
 * Modale di dialogo per la pianificazione di un intervento di manutenzione su data specifica,
 * supportando la selezione singola o multipla di cantieri e la generazione di ricorrenze automatiche.
 */
@Composable
fun PianificaInterventoDialog(
    initialDate: String?,
    cantieriDisponibili: List<Cantiere>,
    clienti: List<Cliente>,
    onDismiss: () -> Unit,
    onPianifica: (
        dataPianificata: String,
        cantieriIds: List<String>,
        impiantiCodici: List<String>,
        frequenza: Periodo,
        manutentore: String?,
        note: String?,
        generaRicorrenze: Boolean
    ) -> Unit
) {
    val todayFormatted = remember {
        val now = java.time.LocalDate.now()
        "%02d/%02d/%04d".format(now.dayOfMonth, now.monthValue, now.year)
    }
    var dataPianificata by remember { mutableStateOf(initialDate?.takeIf { it.isNotBlank() } ?: todayFormatted) }
    var selectedCantieriIds by remember { mutableStateOf(setOf<String>()) }
    var searchCantiereQuery by remember { mutableStateOf("") }

    var selectedImpiantiCodici by remember { mutableStateOf(setOf<String>()) }
    var allImpiantiSelected by remember { mutableStateOf(true) }

    var selectedFrequenza by remember { mutableStateOf(Periodo(TipoPeriodo.M, 6)) }
    var manutentore by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var generaRicorrenze by remember { mutableStateOf(true) }

    val clientiMap = remember(clienti) { clienti.associateBy { it.id } }

    val cantieriFiltrati = remember(cantieriDisponibili, searchCantiereQuery, clientiMap) {
        val q = searchCantiereQuery.trim().lowercase()
        if (q.isEmpty()) cantieriDisponibili
        else cantieriDisponibili.filter { c ->
            c.nome.lowercase().contains(q) || (clientiMap[c.clienteId]?.nome?.lowercase()?.contains(q) == true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(680.dp).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header del Dialog
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            color = Color(0xFFEFF6FF),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(
                                Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(8.dp).size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Pianifica Intervento di Manutenzione",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = "Seleziona uno o più cantieri per programmare le verifiche e le scadenze ricorrenti.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Chiudi", tint = Color(0xFF64748B))
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Form scrollabile
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 1. Data e Manutentore
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = dataPianificata,
                            onValueChange = { dataPianificata = it },
                            label = { Text("Data Pianificata (dd/MM/yyyy)", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        )

                        OutlinedTextField(
                            value = manutentore,
                            onValueChange = { manutentore = it },
                            label = { Text("Manutentore Incaricato (opzionale)", fontSize = 11.sp) },
                            placeholder = { Text("es. Ing. Rossi", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                        )
                    }

                    // 2. Frequenza Manutentiva
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Frequenza Manutentiva:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            PERIODICITA_OPZIONI.forEach { freq ->
                                val isSelected = selectedFrequenza == freq
                                Surface(
                                    color = if (isSelected) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                    modifier = Modifier.clickable { selectedFrequenza = freq }
                                ) {
                                    Text(
                                        text = freq.label(),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else Color(0xFF1E293B),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Tipologia Impianti da manutenere
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Impianti Coinvolti nell'Intervento:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (allImpiantiSelected) Color(0xFF1E3A8A) else Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, if (allImpiantiSelected) Color(0xFF1E3A8A) else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable {
                                    allImpiantiSelected = true
                                    selectedImpiantiCodici = emptySet()
                                }
                            ) {
                                Text(
                                    text = "Tutti gli impianti del cantiere",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (allImpiantiSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }

                            CODICI_IMPIANTI_COMUNI.take(4).forEach { (cod, label) ->
                                val isChecked = !allImpiantiSelected && cod in selectedImpiantiCodici
                                Surface(
                                    color = if (isChecked) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                    modifier = Modifier.clickable {
                                        allImpiantiSelected = false
                                        selectedImpiantiCodici = if (isChecked) selectedImpiantiCodici - cod else selectedImpiantiCodici + cod
                                        if (selectedImpiantiCodici.isEmpty()) allImpiantiSelected = true
                                    }
                                ) {
                                    Text(
                                        text = cod,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isChecked) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 4. Selezione Cantieri (Multipla)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cantieri di Intervento (${selectedCantieriIds.size} selezionati):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF334155)
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(
                                    onClick = { selectedCantieriIds = cantieriDisponibili.map { it.id }.toSet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Seleziona Tutti", fontSize = 10.sp)
                                }
                                TextButton(
                                    onClick = { selectedCantieriIds = emptySet() },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("Deseleziona", fontSize = 10.sp)
                                }
                            }
                        }

                        OutlinedTextField(
                            value = searchCantiereQuery,
                            onValueChange = { searchCantiereQuery = it },
                            placeholder = { Text("Filtra cantiere o cliente...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            color = Color(0xFFF8FAFC)
                        ) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(cantieriFiltrati, key = { it.id }) { cantiere ->
                                    val isSelected = cantiere.id in selectedCantieriIds
                                    val clienteNome = clientiMap[cantiere.clienteId]?.nome ?: "Cliente"

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelected) Color(0xFFEFF6FF) else Color.Transparent,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .clickable {
                                                selectedCantieriIds = if (isSelected) {
                                                    selectedCantieriIds - cantiere.id
                                                } else {
                                                    selectedCantieriIds + cantiere.id
                                                }
                                            }
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = { checked ->
                                                selectedCantieriIds = if (checked) {
                                                    selectedCantieriIds + cantiere.id
                                                } else {
                                                    selectedCantieriIds - cantiere.id
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = cantiere.nome,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = Color(0xFF0F172A)
                                        )
                                        Text(
                                            text = "($clienteNome)",
                                            fontSize = 10.sp,
                                            color = Color(0xFF64748B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 5. Opzione Ricorrenze Automatiche
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Genera automaticamente le ricorrenze successive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Calcola e pianifica gli appuntamenti futuri (es. ogni ${selectedFrequenza.label()}) sul calendario.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        Switch(
                            checked = generaRicorrenze,
                            onCheckedChange = { generaRicorrenze = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colors.primary)
                        )
                    }
                }

                Divider(color = Color(0xFFE2E8F0))

                // Bottoni di Conferma / Annulla
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedCantieriIds.size} cantieri selezionati",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (selectedCantieriIds.isNotEmpty()) Color(0xFF15803D) else Color(0xFFEF4444)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("Annulla", fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                onPianifica(
                                    dataPianificata,
                                    selectedCantieriIds.toList(),
                                    if (allImpiantiSelected) emptyList() else selectedImpiantiCodici.toList(),
                                    selectedFrequenza,
                                    manutentore,
                                    note,
                                    generaRicorrenze
                                )
                                onDismiss()
                            },
                            enabled = selectedCantieriIds.isNotEmpty() && dataPianificata.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Pianifica Intervento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
