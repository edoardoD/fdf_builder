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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.domain.model.CantiereScadenzaInMese
import manutenzioni.domain.model.EventoManutenzione
import manutenzioni.domain.model.MeseScadenzaGlobaleGroup
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.ScadenzaImpiantoItem
import manutenzioni.domain.model.StatoEventoManutenzione

enum class VistaCalendarioTipo(val label: String) {
    GRIGLIA_SCHEDULER("Griglia Calendario (Scheduler)"),
    AGENDA_SCADENZARIO("Agenda Mensile / Scadenzario")
}

/**
 * Schermata di primo livello del Calendario Manutenzioni.
 *
 * Include:
 * 1. Griglia Calendario interattiva Mensile/Giornaliera per la pianificazione degli interventi (Scheduler Engine);
 * 2. Pannello dettaglio del giorno selezionato con lista cantieri e relative attività/modifiche da eseguire;
 * 3. Dialogo di creazione singola o massiva su più cantieri con generazione automatica di ricorrenze;
 * 4. Vista Agenda / Scadenzario multi-cantiere aggregata per mese.
 */
@Composable
fun CalendarioGlobaleScreen(
    state: ManutenzioniUiState,
    onOrizzonteChanged: (Int) -> Unit,
    onSelectMese: (String?) -> Unit,
    onApriCantiereDaCalendario: (CantiereScadenzaInMese) -> Unit,
    onExportRiepilogoCantierePdf: (CantiereScadenzaInMese) -> Unit,
    onDateSelected: (String?) -> Unit = {},
    onPreviousMonth: () -> Unit = {},
    onNextMonth: () -> Unit = {},
    onTodayClicked: () -> Unit = {},
    onPianificaInterventiBatch: (
        dataPianificata: String,
        cantieriIds: List<String>,
        impiantiIds: List<String>,
        impiantiCodici: List<String>,
        frequenza: Periodo,
        manutentore: String?,
        note: String?,
        generaRicorrenze: Boolean
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    onAvviaInterventoDaCalendario: (EventoManutenzione) -> Unit = {},
    onCompletaEventoManutenzione: (String) -> Unit = {},
    onEliminaEventoManutenzione: (String, Boolean) -> Unit = { _, _ -> }
) {
    var vistaCorrente by remember { mutableStateOf(VistaCalendarioTipo.GRIGLIA_SCHEDULER) }
    var showPianificaDialog by remember { mutableStateOf(false) }

    // Dialog di pianificazione intervento
    if (showPianificaDialog) {
        PianificaInterventoDialog(
            initialDate = state.dataCalendarioSelezionata,
            cantieriDisponibili = state.tuttiCantieriVisibili,
            clienti = state.clienti,
            tuttiImpianti = state.impianti,
            onDismiss = { showPianificaDialog = false },
            onPianifica = { data, cantieri, impIds, impCod, freq, man, note, ricorrenze ->
                onPianificaInterventiBatch(data, cantieri, impIds, impCod, freq, man, note, ricorrenze)
                showPianificaDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Barra Superiore: Titolo, Toggle Vista e Pulsante "+ Pianifica Intervento"
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(10.dp),
            backgroundColor = Color.White,
            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.padding(10.dp).size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Calendario Manutenzioni & Scheduler Ricorrenze",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = "Pianifica interventi per uno o più cantieri su una data specifica e gestisci le ricorrenze automatiche.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Switch tra vista Griglia Calendario e Agenda
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        VistaCalendarioTipo.values().forEach { tipo ->
                            val isSelected = vistaCorrente == tipo
                            Surface(
                                color = if (isSelected) Color.White else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                                elevation = if (isSelected) 1.dp else 0.dp,
                                modifier = Modifier.clickable { vistaCorrente = tipo }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (tipo == VistaCalendarioTipo.GRIGLIA_SCHEDULER) Icons.Default.DateRange else Icons.Default.List,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (isSelected) MaterialTheme.colors.primary else Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = tipo.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                    }

                    // Pulsante primario per aprire il dialog di pianificazione
                    Button(
                        onClick = { showPianificaDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Pianifica Intervento", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Sezione Contenuto principale
        when (vistaCorrente) {
            VistaCalendarioTipo.GRIGLIA_SCHEDULER -> {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Griglia Calendario (sinistra)
                    MonthlyCalendarGridView(
                        yearMonth = state.meseVisualizzatoCalendario,
                        selectedDate = state.dataCalendarioSelezionata,
                        eventiPerGiorno = state.mappaEventiPerGiorno,
                        onDateSelected = onDateSelected,
                        onPreviousMonth = onPreviousMonth,
                        onNextMonth = onNextMonth,
                        onTodayClicked = onTodayClicked,
                        modifier = Modifier.weight(1.35f)
                    )

                    // Drawer / Pannello Dettaglio del Giorno Selezionato (destra)
                    SelectedDayDetailPanel(
                        selectedDate = state.dataCalendarioSelezionata,
                        eventiDelGiorno = state.eventiGiornoSelezionato,
                        cantieri = state.tuttiCantieri,
                        clienti = state.clienti,
                        onPianificaClicked = { showPianificaDialog = true },
                        onAvviaIntervento = onAvviaInterventoDaCalendario,
                        onCompletaEvento = onCompletaEventoManutenzione,
                        onEliminaEvento = onEliminaEventoManutenzione,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }
            VistaCalendarioTipo.AGENDA_SCADENZARIO -> {
                AgendaScadenzarioView(
                    state = state,
                    onOrizzonteChanged = onOrizzonteChanged,
                    onSelectMese = onSelectMese,
                    onApriCantiereDaCalendario = onApriCantiereDaCalendario,
                    onExportRiepilogoCantierePdf = onExportRiepilogoCantierePdf
                )
            }
        }
    }
}

/**
 * Pannello laterale che mostra il dettaglio degli interventi pianificati per il giorno selezionato.
 */
@Composable
private fun SelectedDayDetailPanel(
    selectedDate: String?,
    eventiDelGiorno: List<EventoManutenzione>,
    cantieri: List<manutenzioni.domain.model.Cantiere>,
    clienti: List<manutenzioni.domain.model.Cliente>,
    onPianificaClicked: () -> Unit,
    onAvviaIntervento: (EventoManutenzione) -> Unit,
    onCompletaEvento: (String) -> Unit,
    onEliminaEvento: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val cantieriMap = remember(cantieri) { cantieri.associateBy { it.id } }
    val clientiMap = remember(clienti) { clienti.associateBy { it.id } }

    Card(
        modifier = modifier,
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header pannello giorno
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (selectedDate != null) "Interventi del $selectedDate" else "Seleziona un giorno",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "${eventiDelGiorno.size} cantieri con interventi programmati",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }

                if (selectedDate != null) {
                    OutlinedButton(
                        onClick = onPianificaClicked,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ Aggiungi", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Divider(color = Color(0xFFE2E8F0))

            if (selectedDate == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Clicca su un giorno del calendario per visualizzare o pianificare gli interventi di manutenzione.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B),
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else if (eventiDelGiorno.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(36.dp))
                        Text(
                            text = "Nessun intervento pianificato per il $selectedDate",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        Button(
                            onClick = onPianificaClicked,
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = MaterialTheme.colors.primary,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("Pianifica Intervento Ora", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(eventiDelGiorno, key = { it.id }) { evento ->
                        val cantiere = cantieriMap[evento.cantiereId]
                        val cliente = cantiere?.let { clientiMap[it.clienteId] } ?: clientiMap[evento.clienteId]
                        EventoGiornoCard(
                            evento = evento,
                            cantiereNome = cantiere?.nome ?: "Cantiere",
                            clienteNome = cliente?.nome ?: "Cliente",
                            cantiereIndirizzo = cantiere?.indirizzo,
                            onAvviaIntervento = { onAvviaIntervento(evento) },
                            onCompletaEvento = { onCompletaEvento(evento.id) },
                            onEliminaEvento = { eliminaFuturi -> onEliminaEvento(evento.id, eliminaFuturi) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EventoGiornoCard(
    evento: EventoManutenzione,
    cantiereNome: String,
    clienteNome: String,
    cantiereIndirizzo: String?,
    onAvviaIntervento: () -> Unit,
    onCompletaEvento: () -> Unit,
    onEliminaEvento: (Boolean) -> Unit
) {
    var expandedAttivita by remember { mutableStateOf(false) }

    val statusColor = when (evento.stato) {
        StatoEventoManutenzione.COMPLETATO -> Color(0xFF15803D)
        StatoEventoManutenzione.IN_CORSO -> Color(0xFFD97706)
        StatoEventoManutenzione.PIANIFICATO -> Color(0xFF1D4ED8)
        StatoEventoManutenzione.ANNULLATO -> Color(0xFF64748B)
    }
    val statusBg = when (evento.stato) {
        StatoEventoManutenzione.COMPLETATO -> Color(0xFFDCFCE7)
        StatoEventoManutenzione.IN_CORSO -> Color(0xFFFEF3C7)
        StatoEventoManutenzione.PIANIFICATO -> Color(0xFFDBEAFE)
        StatoEventoManutenzione.ANNULLATO -> Color(0xFFF1F5F9)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header Card: Cantiere, Cliente e Badge Stato
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$cantiereNome",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "$clienteNome${cantiereIndirizzo?.let { " • $it" } ?: ""}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }

                Surface(
                    color = statusBg,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = evento.stato.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Info Manutentore e Impianti
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val impiantiText = evento.impiantiPianificati.joinToString(", ") { "${it.codIntervento} (${it.periodicitaScelta.label()})" }
                Text(
                    text = "Impianti: ${impiantiText.ifEmpty { "Generale" }}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155)
                )

                if (!evento.nomeManutentore.isNullOrBlank()) {
                    Text(
                        text = "Tecnico: ${evento.nomeManutentore}",
                        fontSize = 10.sp,
                        color = Color(0xFF475569)
                    )
                }
            }

            // Dettaglio Attività espandibile
            val totalAttivita = evento.impiantiPianificati.sumOf { it.numeroAttivitaPreviste }
            if (totalAttivita > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { expandedAttivita = !expandedAttivita },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$totalAttivita verifiche/modifiche programmate",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colors.primary
                    )
                    Icon(
                        imageVector = if (expandedAttivita) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (expandedAttivita) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(4.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        evento.impiantiPianificati.forEach { imp ->
                            Text(
                                text = "• ${imp.codIntervento} - ${imp.descrizione}:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B)
                            )
                            imp.attivitaDescrizioni.forEach { desc ->
                                Text(
                                    text = "   $desc",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }

            Divider(color = Color(0xFFE2E8F0))

            // Azioni rapide sull'evento
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (evento.stato != StatoEventoManutenzione.COMPLETATO) {
                        IconButton(
                            onClick = onCompletaEvento,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = "Segna Completato", tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                        }
                    }
                    IconButton(
                        onClick = { onEliminaEvento(false) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                    }
                }

                Button(
                    onClick = onAvviaIntervento,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Esegui in Operatività →", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Vista alternativa: Agenda / Scadenzario con aggregazione multi-cantiere per mese.
 */
@Composable
private fun AgendaScadenzarioView(
    state: ManutenzioniUiState,
    onOrizzonteChanged: (Int) -> Unit,
    onSelectMese: (String?) -> Unit,
    onApriCantiereDaCalendario: (CantiereScadenzaInMese) -> Unit,
    onExportRiepilogoCantierePdf: (CantiereScadenzaInMese) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val orizzontiDisponibili = listOf(12 to "1 Anno (12M)", 24 to "2 Anni (24M)", 36 to "3 Anni (36M)", 60 to "5 Anni (60M)")

    val gruppiFiltrati = remember(state.calendarioGlobale, state.meseCalendarioSelezionato, searchQuery) {
        val base = if (state.meseCalendarioSelezionato != null) {
            state.calendarioGlobale.filter { it.annoMese == state.meseCalendarioSelezionato }
        } else {
            state.calendarioGlobale
        }
        val query = searchQuery.trim().lowercase()
        if (query.isEmpty()) {
            base
        } else {
            base.mapNotNull { group ->
                val matching = group.cantieriInScadenza.filter { item ->
                    item.cantiere.nome.lowercase().contains(query) ||
                        (item.cliente?.nome?.lowercase()?.contains(query) == true) ||
                        group.etichettaMese.lowercase().contains(query)
                }
                if (matching.isNotEmpty()) group.copy(cantieriInScadenza = matching) else null
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Barra filtro e orizzonte
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp,
            shape = RoundedCornerShape(8.dp),
            backgroundColor = Color.White
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Filtra cantiere, cliente o mese (es. Maggio)...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    modifier = Modifier.width(320.dp).height(42.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Orizzonte:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF475569))
                    orizzontiDisponibili.forEach { (mesi, label) ->
                        val isSelected = state.orizzonteScadenzeMesi == mesi
                        OutlinedButton(
                            onClick = { onOrizzonteChanged(mesi) },
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (isSelected) MaterialTheme.colors.primary else Color.White,
                                contentColor = if (isSelected) Color.White else Color(0xFF334155)
                            ),
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        if (gruppiFiltrati.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color.White,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Nessuna scadenza trovata per i criteri specificati.",
                    modifier = Modifier.padding(24.dp),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(gruppiFiltrati, key = { it.annoMese }) { group ->
                    MeseAgendaSectionCard(
                        group = group,
                        onApriCantiere = onApriCantiereDaCalendario,
                        onExportRiepilogoPdf = onExportRiepilogoCantierePdf
                    )
                }
            }
        }
    }
}

@Composable
private fun MeseAgendaSectionCard(
    group: MeseScadenzaGlobaleGroup,
    onApriCantiere: (CantiereScadenzaInMese) -> Unit,
    onExportRiepilogoPdf: (CantiereScadenzaInMese) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1E3A8A))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📅 ${group.etichettaMese.uppercase()}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Text(
                    text = "${group.totaleCantieri} cantieri • ${group.totaleImpianti} impianti",
                    fontSize = 11.sp,
                    color = Color(0xFFDBEAFE)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                group.cantieriInScadenza.forEach { item ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = Color(0xFFF8FAFC),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.cantiere.nome,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "${item.cliente?.nome ?: "Cliente"} • Data prevista: ${item.dataPrevista}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                                val dataRif = item.cantiere.effectiveDataIntervento
                                if (!dataRif.isNullOrBlank()) {
                                    Text(
                                        text = "Data intervento base: $dataRif",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1E3A8A)
                                    )
                                }
                                val dataContratto = item.cantiere.dataInizioContratto
                                if (!dataContratto.isNullOrBlank()) {
                                    Text(
                                        text = "Inizio contratto: $dataContratto",
                                        fontSize = 9.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (item.scadenzeImpianti.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        item.scadenzeImpianti.forEach { imp ->
                                            Surface(
                                                color = Color(0xFFE2E8F0),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "${imp.codIntervento} (${imp.frequenzeIncluse.joinToString { it.label() }})",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF334155),
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { onExportRiepilogoPdf(item) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Riepilogo PDF", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { onApriCantiere(item) },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = MaterialTheme.colors.primary,
                                        contentColor = Color.White
                                    ),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("Operatività →", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
