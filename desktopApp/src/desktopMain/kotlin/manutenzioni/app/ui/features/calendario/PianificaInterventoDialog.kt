package manutenzioni.app.ui.features.calendario

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
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
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.RilevazioneGas
import manutenzioni.domain.model.TipoPeriodo

private val PERIODICITA_OPZIONI = listOf(
    Periodo(TipoPeriodo.M, 1),
    Periodo(TipoPeriodo.M, 3),
    Periodo(TipoPeriodo.M, 6),
    Periodo(TipoPeriodo.A, 1),
    Periodo(TipoPeriodo.A, 2),
    Periodo(TipoPeriodo.A, 5)
)

/**
 * Modale di dialogo per la pianificazione di un intervento di manutenzione su data specifica.
 *
 * Flusso gerarchico:
 * 1. Data e Manutentore
 * 2. Selezione Cantieri (multipla con conteggio impianti per cantiere)
 * 3. Selezione Impianti Coinvolti (risolti dinamicamente in base ai cantieri scelti)
 * 4. Frequenza Manutentiva
 * 5. Ricorrenze & Note
 */
@Composable
fun PianificaInterventoDialog(
    initialDate: String?,
    cantieriDisponibili: List<Cantiere>,
    clienti: List<Cliente>,
    tuttiImpianti: List<Impianto> = emptyList(),
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

    // Conteggio impianti per cantiere
    val conteggioImpiantiPerCantiere = remember(tuttiImpianti) {
        tuttiImpianti.filter { !it.cantiereId.isNullOrBlank() }
            .groupBy { it.cantiereId!! }
            .mapValues { it.value.size }
    }

    // Impianti effettivamente presenti nei cantieri selezionati
    val impiantiDeiCantieriSelezionati = remember(selectedCantieriIds, tuttiImpianti) {
        if (selectedCantieriIds.isEmpty()) emptyList()
        else tuttiImpianti.filter { it.cantiereId in selectedCantieriIds }
    }

    // Raggruppamento per codice impianto presente nei cantieri selezionati
    val tipologieImpiantiDisponibili = remember(impiantiDeiCantieriSelezionati) {
        impiantiDeiCantieriSelezionati
            .groupBy { it.codIntervento.uppercase() }
            .map { (cod, list) ->
                val first = list.first()
                val nomeDesc = when (first) {
                    is QuadroBT -> "Quadri BT"
                    is ImpiantoEmergenza -> "Emergenza"
                    is RilevazioneAntincendio -> "Antincendio"
                    is RilevazioneGas -> "Gas"
                    is QuadroMQT -> "Quadri MT"
                    else -> first.nomeCompleto.take(20)
                }
                Triple(cod, nomeDesc, list.size)
            }
            .sortedBy { it.first }
    }

    // Allinea la selezione impianti se la lista dei cantieri cambia
    LaunchedEffect(tipologieImpiantiDisponibili) {
        val availableCodes = tipologieImpiantiDisponibili.map { it.first }.toSet()
        val validSelected = selectedImpiantiCodici.filter { it in availableCodes }.toSet()
        if (validSelected != selectedImpiantiCodici) {
            selectedImpiantiCodici = validSelected
            if (validSelected.isEmpty()) allImpiantiSelected = true
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.width(720.dp).fillMaxHeight(0.94f),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
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
                                text = "Seleziona i cantieri e gli impianti per programmare le verifiche e le scadenze ricorrenti.",
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

                // Form con scroll interno
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

                    // 2. Selezione Cantieri (PRIMO STEP OPERATIVO)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "1. Cantieri di Intervento (${selectedCantieriIds.size} selezionati):",
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
                            modifier = Modifier.fillMaxWidth().height(150.dp),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            color = Color(0xFFF8FAFC)
                        ) {
                            if (cantieriFiltrati.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text("Nessun cantiere trovato.", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().padding(4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    items(cantieriFiltrati, key = { it.id }) { cantiere ->
                                        val isSelected = cantiere.id in selectedCantieriIds
                                        val clienteNome = clientiMap[cantiere.clienteId]?.nome ?: "Cliente"
                                        val nImpianti = conteggioImpiantiPerCantiere[cantiere.id] ?: 0

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
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = cantiere.nome,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Text(
                                                    text = clienteNome,
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }

                                            Surface(
                                                color = if (isSelected) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "$nImpianti impianti",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF1E40AF) else Color(0xFF475569),
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 3. Impianti Coinvolti (DINAMICO SUI CANTIERI SELEZIONATI)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "2. Impianti Coinvolti nell'Intervento:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )

                        if (selectedCantieriIds.isEmpty()) {
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Seleziona almeno un cantiere sopra per visualizzare e filtrare gli impianti presenti.",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }
                        } else if (tipologieImpiantiDisponibili.isEmpty()) {
                            Surface(
                                color = Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier.fillMaxWidth().padding(8.dp)
                            ) {
                                Text(
                                    text = "Nessun impianto censito nei cantieri selezionati.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Opzione "Tutti gli impianti"
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
                                        text = "Tutti gli impianti (${impiantiDeiCantieriSelezionati.size})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (allImpiantiSelected) Color.White else Color(0xFF334155),
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }

                                // Chip dinamici per tutti i tipi d'impianto presenti nei cantieri selezionati
                                tipologieImpiantiDisponibili.forEach { (cod, nomeDesc, count) ->
                                    val isChecked = !allImpiantiSelected && cod in selectedImpiantiCodici
                                    Surface(
                                        color = if (isChecked) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                        shape = RoundedCornerShape(14.dp),
                                        border = BorderStroke(1.dp, if (isChecked) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                        modifier = Modifier.clickable {
                                            allImpiantiSelected = false
                                            selectedImpiantiCodici = if (isChecked) {
                                                selectedImpiantiCodici - cod
                                            } else {
                                                selectedImpiantiCodici + cod
                                            }
                                            if (selectedImpiantiCodici.isEmpty()) {
                                                allImpiantiSelected = true
                                            }
                                        }
                                    ) {
                                        Text(
                                            text = "$cod • $nomeDesc ($count)",
                                            fontSize = 11.sp,
                                            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isChecked) Color.White else Color(0xFF1E293B),
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Frequenza Manutentiva
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "3. Frequenza Manutentiva:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF334155)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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

                    // 5. Opzione Ricorrenze Automatiche & Note
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Genera automaticamente le ricorrenze successive",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            Text(
                                text = "Calcola e pianifica gli appuntamenti futuri (es. ogni ${selectedFrequenza.label()}) sul calendario per i prossimi 24 mesi.",
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

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note operative per l'intervento (opzionale)", fontSize = 11.sp) },
                        placeholder = { Text("es. Portare strumentazione per misura differenziali", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }

                Divider(color = Color(0xFFE2E8F0))

                // Footer con Bottoni di Conferma / Annulla
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "${selectedCantieriIds.size} cantieri selezionati",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selectedCantieriIds.isNotEmpty()) Color(0xFF15803D) else Color(0xFFEF4444)
                        )
                        if (selectedCantieriIds.isNotEmpty()) {
                            val impiantiSummary = if (allImpiantiSelected) {
                                "Tutti gli impianti (${impiantiDeiCantieriSelezionati.size})"
                            } else {
                                "${selectedImpiantiCodici.size} tipologie (${selectedImpiantiCodici.joinToString()})"
                            }
                            Text(
                                text = impiantiSummary,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    }

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
