package manutenzioni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.*
import manutenzioni.app.data.ManutenzioniDatabase
import manutenzioni.app.ui.features.operativita.AggiungiComponenteDialog

/**
 * Editor universale per impianti — gestisce sia creazione che modifica.
 *
 * Permette di editare:
 * - Codice intervento, nome completo, premessa
 * - Lista attività con tipo, descrizione e frequenza
 *
 * Se l'impianto è nuovo (codIntervento blank), mostra un titolo adeguato
 * e valida i campi obbligatori prima del salvataggio.
 *
 * @param impianto L'impianto da editare (può essere vuoto per creazione)
 * @param isNew true se l'impianto è in fase di creazione (non ancora persistito)
 * @param onSave Callback per il salvataggio (validato)
 */
@Composable
fun ImpiantoEditor(
    componentiStandard: List<manutenzioni.domain.model.ComponenteStandard> = emptyList(), 
    impianto: Impianto,
    isNew: Boolean = impianto.codIntervento.isBlank(),
    isReadOnlyAdminFields: Boolean = false,
    catalogoApprovato: List<manutenzioni.domain.model.ComponenteApprovato> = emptyList(),
    candidateComponents: List<manutenzioni.domain.model.ComponentCandidate> = emptyList(),
    isSearchingComponenti: Boolean = false,
    onSearchComponenti: (String) -> Unit = {},
    onClearSearchComponenti: () -> Unit = {},
    onApprovaComponente: ((QuadroBT, ComponentCandidate, VarianteProdotto, Int, String) -> Unit)? = null,
    onSostituisciProduttore: ((QuadroBT, String, VarianteProdotto) -> Unit)? = null,
    onSave: (Impianto, Boolean) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAggiungiComponenteDialog by remember { mutableStateOf(false) }

    // Stato locale mutabile per l'editing
    var codIntervento by remember(impianto) { mutableStateOf(impianto.codIntervento) }
    var nomeCompleto by remember(impianto) { mutableStateOf(impianto.nomeCompleto) }
    var premessa by remember(impianto) { mutableStateOf(impianto.premessa ?: "") }
    var noteSpecifiche by remember(impianto) { mutableStateOf(impianto.noteSpecifiche ?: "") }
    var attivitaList by remember(impianto) { mutableStateOf(impianto.listaAttivita) }
    
    // Stato per QuadroBT
    var siglaQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.sigla ?: "") }
    var descrizioneQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.descrizioneQuadro ?: "") }
    var interruttoriQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.listaInterruttori ?: emptyList()) }

    // Stato per la propagazione globale
    var propagaModifiche by remember { mutableStateOf(false) }

    // Stato di validazione locale
    var codInterventoError by remember { mutableStateOf(false) }
    var nomeCompletoError by remember { mutableStateOf(false) }

    if (showAggiungiComponenteDialog && impianto is QuadroBT) {
        AggiungiComponenteDialog(
            quadro = impianto,
            candidateComponents = candidateComponents,
            isSearching = isSearchingComponenti,
            onSearch = onSearchComponenti,
            onClearSearch = onClearSearchComponenti,
            onDismiss = { showAggiungiComponenteDialog = false },
            onApprovaEAssegna = { candidate, variante, qta, circuito ->
                val nowIso = java.time.LocalDate.now().toString()
                val varConData = variante.copy(dataApprovazione = nowIso)
                val nuovoInterruttore = InterruttoreBT(
                    id = java.util.UUID.randomUUID().toString(),
                    nome = candidate.descrizioneStandard,
                    quantita = qta,
                    siglaCircuito = circuito.trim().ifBlank { null },
                    produttore = varConData.produttore,
                    codiceArticolo = varConData.codice,
                    etimClassId = candidate.etimClassId,
                    etimClassName = candidate.etimClassName,
                    caratteristicheTecniche = candidate.caratteristicheTecniche,
                    note = varConData.serie?.let { "Serie: $it" }
                )
                interruttoriQuadro = interruttoriQuadro + nuovoInterruttore
                onApprovaComponente?.invoke(impianto, candidate, varConData, qta, circuito)
                showAggiungiComponenteDialog = false
            },
            onAggiungiManuale = { interruttore ->
                interruttoriQuadro = interruttoriQuadro + interruttore
                showAggiungiComponenteDialog = false
            }
        )
    }

    LazyColumn(modifier = modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isNew) "Nuovo Impianto" else "Editor Impianto",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                if (isNew) {
                    Text(
                        text = "Compila i campi obbligatori e salva",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onCancel != null) {
                    OutlinedButton(
                        onClick = onCancel,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.error)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Annulla")
                    }
                }
                Button(
                    onClick = {
                        // Validazione locale
                        codInterventoError = codIntervento.isBlank()
                        nomeCompletoError = nomeCompleto.isBlank()
    
                        if (!codInterventoError && !nomeCompletoError) {
                            var updated = impianto.copyWithBasicParams(
                                codIntervento = codIntervento.trim(),
                                nomeCompleto = nomeCompleto.trim(),
                                premessa = premessa.ifBlank { null },
                                quantita = 1,
                                noteSpecifiche = noteSpecifiche.ifBlank { null },
                                listaAttivita = attivitaList
                            )
                            if (updated is QuadroBT) {
                                updated = updated.copy(
                                    sigla = siglaQuadro.trim(),
                                    descrizioneQuadro = descrizioneQuadro.trim(),
                                    listaInterruttori = interruttoriQuadro
                                )
                            }
                            onSave(updated, propagaModifiche)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isNew) "Crea Impianto" else "Salva")
                }
            }
        }
        }

        item {
        // Campi principali
        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codIntervento,
                    onValueChange = {
                        codIntervento = it
                        codInterventoError = false
                    },
                    label = { Text("Codice Intervento *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = codInterventoError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        errorBorderColor = Color(0xFFD32F2F)
                    ),
                    placeholder = { Text("es. GE, CAB, Q", fontSize = 12.sp) }
                )
                if (codInterventoError) {
                    Text(
                        text = "Il codice intervento è obbligatorio",
                        color = Color(0xFFD32F2F),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = nomeCompleto,
                    onValueChange = {
                        nomeCompleto = it
                        nomeCompletoError = false
                    },
                    label = { Text("Nome Completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nomeCompletoError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        errorBorderColor = Color(0xFFD32F2F)
                    ),
                    placeholder = { Text("es. Gruppo Elettrogeno", fontSize = 12.sp) }
                )
                if (nomeCompletoError) {
                    Text(
                        text = "Il nome completo è obbligatorio",
                        color = Color(0xFFD32F2F),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = premessa,
                    onValueChange = { premessa = it },
                    label = { Text("Premessa") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
                if (impianto is QuadroBT) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        OutlinedTextField(
                            value = siglaQuadro,
                            onValueChange = { siglaQuadro = it },
                            label = { Text("Sigla Quadro") },
                            modifier = Modifier.weight(0.3f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                        OutlinedTextField(
                            value = descrizioneQuadro,
                            onValueChange = { descrizioneQuadro = it },
                            label = { Text("Descrizione / Ubicazione") },
                            modifier = Modifier.weight(0.7f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                        )
                    }
                }
            }

            if (!isReadOnlyAdminFields && !isNew) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Checkbox(
                        checked = propagaModifiche,
                        onCheckedChange = { propagaModifiche = it }
                    )
                    Text("Propaga modifiche (attività e normative) a TUTTI i cantieri con codice $codIntervento", fontSize = 12.sp)
                }
            }
        }
        }
        if (impianto is QuadroBT) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Interruttori & Componenti (${interruttoriQuadro.size})", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Componenti associati a questo quadro con codifica ETIM", style = MaterialTheme.typography.caption, color = Color.Gray)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                // Dropdown per aggiungere componenti standard se presenti
                                val interruttoriStandard = componentiStandard.filter { it.tipo == manutenzioni.domain.model.TipoComponenteStandard.INTERRUTTORE_BT }
                                if (interruttoriStandard.isNotEmpty()) {
                                    var espandiDropdown by remember { mutableStateOf(false) }
                                    Box {
                                        OutlinedButton(
                                            onClick = { espandiDropdown = true },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Da Standard", fontSize = 12.sp)
                                        }
                                        DropdownMenu(
                                            expanded = espandiDropdown,
                                            onDismissRequest = { espandiDropdown = false }
                                        ) {
                                            interruttoriStandard.forEach { comp ->
                                                DropdownMenuItem(
                                                    onClick = {
                                                        interruttoriQuadro = interruttoriQuadro + InterruttoreBT(nome = comp.nome)
                                                        espandiDropdown = false
                                                    }
                                                ) {
                                                    Text(comp.nome, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }

                                Button(
                                    onClick = { showAggiungiComponenteDialog = true },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Cerca & Aggiungi (ETIM)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (interruttoriQuadro.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Nessun componente registrato per questo quadro. Clicca su 'Cerca & Aggiungi' per iniziare.", color = Color.Gray, fontSize = 12.sp)
                            }
                        } else {
                            interruttoriQuadro.forEach { interruttore ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    backgroundColor = Color(0xFFF8FAFC),
                                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Badge Quantità
                                        Surface(
                                            color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                            shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "${interruttore.quantita}x",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colors.primary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Spacer(Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (!interruttore.produttore.isNullOrBlank()) {
                                                    Text(
                                                        text = interruttore.produttore,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 13.sp,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                if (!interruttore.codiceArticolo.isNullOrBlank()) {
                                                    Text(
                                                        text = "• ${interruttore.codiceArticolo}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = Color(0xFF2563EB)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                }
                                                if (!interruttore.siglaCircuito.isNullOrBlank()) {
                                                    Surface(color = Color(0xFFFEF3C7), shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)) {
                                                        Text(
                                                            text = interruttore.siglaCircuito,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = Color(0xFFB45309),
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            Text(
                                                text = interruttore.nome,
                                                fontSize = 12.sp,
                                                color = Color(0xFF334155)
                                            )

                                            if (!interruttore.etimClassId.isNullOrBlank()) {
                                                Text(
                                                    text = "ETIM: ${interruttore.etimClassId}${if (!interruttore.etimClassName.isNullOrBlank()) " (${interruttore.etimClassName})" else ""}",
                                                    fontSize = 10.sp,
                                                    color = Color(0xFF64748B)
                                                )
                                            }
                                        }

                                        // Menu Sostituzione Marca / Equivalenti (se presente etimClassId)
                                        if (!interruttore.etimClassId.isNullOrBlank()) {
                                            val approvato = catalogoApprovato.firstOrNull { it.etimClassId == interruttore.etimClassId }
                                            val alternative = approvato?.variantiProduttore?.values?.filter { it.produttore != interruttore.produttore } ?: emptyList()

                                            if (alternative.isNotEmpty()) {
                                                var menuAlternativeExpanded by remember { mutableStateOf(false) }
                                                Box {
                                                    OutlinedButton(
                                                        onClick = { menuAlternativeExpanded = true },
                                                        modifier = Modifier.height(28.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                                    ) {
                                                        Text("🔄 Cambia Marca", fontSize = 10.sp)
                                                    }
                                                    DropdownMenu(
                                                        expanded = menuAlternativeExpanded,
                                                        onDismissRequest = { menuAlternativeExpanded = false }
                                                    ) {
                                                        Text(
                                                            text = "Alternative approvate per ETIM ${interruttore.etimClassId}:",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                                        )
                                                        Divider()
                                                        alternative.forEach { alt ->
                                                            DropdownMenuItem(
                                                                onClick = {
                                                                    val q = impianto as QuadroBT
                                                                    onSostituisciProduttore?.invoke(q, interruttore.id, alt)
                                                                    val idx = interruttoriQuadro.indexOfFirst { it.id == interruttore.id }
                                                                    if (idx >= 0) {
                                                                        val mList = interruttoriQuadro.toMutableList()
                                                                        mList[idx] = interruttore.copy(
                                                                            produttore = alt.produttore,
                                                                            codiceArticolo = alt.codice,
                                                                            note = alt.serie?.let { "Serie: $it" } ?: interruttore.note
                                                                        )
                                                                        interruttoriQuadro = mList
                                                                    }
                                                                    menuAlternativeExpanded = false
                                                                }
                                                            ) {
                                                                Column {
                                                                    Text("${alt.produttore} — ${alt.codice}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                                    if (alt.prezzoListino != null) {
                                                                        Text("Listino indicativo: ${alt.prezzoListino} €", fontSize = 10.sp, color = Color.Gray)
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                        }

                                        IconButton(
                                            onClick = {
                                                interruttoriQuadro = interruttoriQuadro - interruttore
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp), tint = Color(0xFFDC2626))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
        // Lista attività
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attività (${attivitaList.size})",
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {
                val newNum = (attivitaList.maxOfOrNull { it.nAttivita } ?: 0) + 1
                attivitaList = attivitaList + Attivita(
                    nAttivita = newNum,
                    tipoAttivita = "Controllo visivo",
                    descrizione = "",
                    frequenza = Periodo(TipoPeriodo.M, 1)
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Aggiungi attività")
            }
        }
        }

        itemsIndexed(attivitaList, key = { idx, att -> "${att.nAttivita}_$idx" }) { index, att ->
                AttivitaCard(
                    attivita = att,
                    onUpdate = { updated ->
                        attivitaList = attivitaList.toMutableList().also { it[index] = updated }
                    },
                    onDelete = {
                        attivitaList = attivitaList.toMutableList().also { it.removeAt(index) }
                    }
                )
            }
    }
}

/**
 * Card per una singola attività nell'editor
 */
@Composable
private fun AttivitaCard(
    attivita: Attivita,
    onUpdate: (Attivita) -> Unit,
    onDelete: () -> Unit
) {
    var tipoAttivita by remember(attivita) { mutableStateOf(attivita.tipoAttivita ?: "") }
    var descrizione by remember(attivita) { mutableStateOf(attivita.descrizione ?: "") }
    var tipoPeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.tipo) }
    var valorePeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.valore.toString()) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header riga con numero e bottone elimina
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività #${attivita.nAttivita}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tipoAttivita,
                    onValueChange = {
                        tipoAttivita = it
                        onUpdate(attivita.copy(tipoAttivita = it))
                    },
                    label = { Text("Tipo", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                // Frequenza: tipo (M/A)
                Column(modifier = Modifier.width(80.dp)) {
                    Text("Periodo", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.M,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.M
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.M, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("M", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.A,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.A
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.A, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("A", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = valorePeriodo,
                    onValueChange = {
                        valorePeriodo = it
                        val v = it.toIntOrNull() ?: 1
                        onUpdate(attivita.copy(frequenza = Periodo(tipoPeriodo, v)))
                    },
                    label = { Text("Val.", fontSize = 11.sp) },
                    modifier = Modifier.width(60.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            OutlinedTextField(
                value = descrizione,
                onValueChange = {
                    descrizione = it
                    onUpdate(attivita.copy(descrizione = it))
                },
                label = { Text("Descrizione", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
        }
    }
}

