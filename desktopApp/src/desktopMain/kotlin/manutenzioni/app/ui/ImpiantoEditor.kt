package manutenzioni.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import manutenzioni.app.ui.components.TechnicalAttachmentSection
import manutenzioni.app.ui.components.SiglaCloneHelper
import manutenzioni.app.ui.features.operativita.AggiungiComponenteDialog
import manutenzioni.app.ui.features.operativita.ModificaComponenteDialog
import manutenzioni.app.ui.features.operativita.AggiungiLampadaDialog
import manutenzioni.app.ui.features.operativita.ModificaLampadaDialog
import manutenzioni.app.ui.features.operativita.AggiungiDispositivoDialog

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
    onAggiungiComponenteAQuadro: ((QuadroBT, InterruttoreBT) -> Unit)? = null,
    onSostituisciProduttore: ((QuadroBT, String, VarianteProdotto) -> Unit)? = null,
    onAggiungiLampadaAEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onAggiungiLampadeAEmergenza: ((ImpiantoEmergenza, List<LampadaEmergenza>) -> Unit)? = null,
    onAggiornaLampadaInEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onExportStandaloneTechnicalSheet: ((Impianto) -> Unit)? = null,
    onSave: (Impianto, Boolean) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAggiungiComponenteDialog by remember { mutableStateOf(false) }
    var interruttoreInModifica by remember { mutableStateOf<InterruttoreBT?>(null) }
    var showAggiungiLampadaDialog by remember { mutableStateOf(false) }
    var lampadaInModifica by remember { mutableStateOf<LampadaEmergenza?>(null) }

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

    // Stato per ImpiantoEmergenza
    var lampadeEmergenza by remember(impianto) { mutableStateOf((impianto as? ImpiantoEmergenza)?.listaLampade ?: emptyList<LampadaEmergenza>()) }

    // Stato per la propagazione globale
    var propagaModifiche by remember { mutableStateOf(false) }

    // Stato di validazione locale
    var codInterventoError by remember { mutableStateOf(false) }
    var nomeCompletoError by remember { mutableStateOf(false) }

    // Determinazione dinamica della tipologia di impianto (anche se originariamente ImpiantoStandard)
    val isQuadro = impianto is QuadroBT || codIntervento.trim().equals("Q", ignoreCase = true) || (codIntervento.trim().startsWith("Q", ignoreCase = true) && !codIntervento.trim().equals("QMT", ignoreCase = true))
    val isEmergenza = impianto is ImpiantoEmergenza || codIntervento.trim().equals("EM", ignoreCase = true) || codIntervento.trim().equals("IS", ignoreCase = true)
    val isQuadroMQT = impianto is QuadroMQT || codIntervento.trim().equals("QMT", ignoreCase = true)
    val isAntincendio = impianto is RilevazioneAntincendio || codIntervento.trim().equals("RI", ignoreCase = true)

    var tipoInterruttoreMQT by remember(impianto) {
        mutableStateOf((impianto as? QuadroMQT)?.tipoInterruttore ?: MqtSwitchType.SF6)
    }

    // Stato per RilevazioneAntincendio (R18 e R19)
    var categorieRivelatoriState by remember(impianto) {
        mutableStateOf((impianto as? RilevazioneAntincendio)?.categorieRivelatori ?: emptyMap())
    }
    var dispositiviAntincendio by remember(impianto) {
        mutableStateOf((impianto as? RilevazioneAntincendio)?.listaDispositivi ?: emptyList<DispositivoRilevazione>())
    }
    var showAddDispositivoDialog by remember { mutableStateOf(false) }
    var dispositivoToEdit by remember { mutableStateOf<DispositivoRilevazione?>(null) }

    val currentQuadro = (impianto as? QuadroBT) ?: QuadroBT(
        id = impianto.id,
        codIntervento = codIntervento.trim().ifBlank { "Q" },
        nomeCompleto = nomeCompleto.trim().ifBlank { "Quadro" },
        premessa = premessa.ifBlank { null },
        listaAttivita = attivitaList,
        listaNormative = impianto.listaNormative,
        cantiereId = impianto.cantiereId,
        quantita = 1,
        noteSpecifiche = noteSpecifiche.ifBlank { null },
        sigla = siglaQuadro.trim(),
        descrizioneQuadro = descrizioneQuadro.trim(),
        listaInterruttori = interruttoriQuadro
    )

    val currentEmergenza = (impianto as? ImpiantoEmergenza) ?: ImpiantoEmergenza(
        id = impianto.id,
        codIntervento = codIntervento.trim().ifBlank { "EM" },
        nomeCompleto = nomeCompleto.trim().ifBlank { "Illuminazione emergenza" },
        premessa = premessa.ifBlank { null },
        listaAttivita = attivitaList,
        listaNormative = impianto.listaNormative,
        cantiereId = impianto.cantiereId,
        quantita = 1,
        noteSpecifiche = noteSpecifiche.ifBlank { null },
        listaLampade = lampadeEmergenza
    )

    val currentAntincendio = (impianto as? RilevazioneAntincendio) ?: RilevazioneAntincendio(
        id = impianto.id,
        codIntervento = codIntervento.trim().ifBlank { "RI" },
        nomeCompleto = nomeCompleto.trim().ifBlank { "Rilevazione Incendi" },
        premessa = premessa.ifBlank { null },
        listaAttivita = attivitaList,
        listaNormative = impianto.listaNormative,
        cantiereId = impianto.cantiereId,
        quantita = 1,
        noteSpecifiche = noteSpecifiche.ifBlank { null },
        categorieRivelatori = categorieRivelatoriState,
        listaDispositivi = dispositiviAntincendio
    )

    if (showAggiungiComponenteDialog && isQuadro) {
        AggiungiComponenteDialog(
            quadro = currentQuadro.copy(
                sigla = siglaQuadro.trim(),
                descrizioneQuadro = descrizioneQuadro.trim(),
                listaInterruttori = interruttoriQuadro
            ),
            onDismiss = { showAggiungiComponenteDialog = false },
            onConferma = { interruttore ->
                interruttoriQuadro = interruttoriQuadro + interruttore
                onAggiungiComponenteAQuadro?.invoke(currentQuadro, interruttore)
                showAggiungiComponenteDialog = false
            }
        )
    }

    if (interruttoreInModifica != null && isQuadro) {
        ModificaComponenteDialog(
            quadro = currentQuadro.copy(
                sigla = siglaQuadro.trim(),
                descrizioneQuadro = descrizioneQuadro.trim(),
                listaInterruttori = interruttoriQuadro
            ),
            interruttore = interruttoreInModifica!!,
            onDismiss = { interruttoreInModifica = null },
            onConferma = { modificato ->
                val idx = interruttoriQuadro.indexOfFirst { it.id == modificato.id }
                if (idx >= 0) {
                    val mList = interruttoriQuadro.toMutableList()
                    mList[idx] = modificato
                    interruttoriQuadro = mList
                    onAggiungiComponenteAQuadro?.invoke(currentQuadro.copy(listaInterruttori = mList), modificato)
                }
                interruttoreInModifica = null
            }
        )
    }

    if (showAggiungiLampadaDialog && isEmergenza) {
        AggiungiLampadaDialog(
            impianto = currentEmergenza.copy(listaLampade = lampadeEmergenza),
            onDismiss = { showAggiungiLampadaDialog = false },
            onConferma = { nuoveLampade ->
                val aggiornata = lampadeEmergenza + nuoveLampade
                lampadeEmergenza = aggiornata
                if (onAggiungiLampadeAEmergenza != null) {
                    onAggiungiLampadeAEmergenza.invoke(currentEmergenza.copy(listaLampade = aggiornata), nuoveLampade)
                } else if (onAggiungiLampadaAEmergenza != null) {
                    nuoveLampade.forEach { lampada ->
                        onAggiungiLampadaAEmergenza.invoke(currentEmergenza.copy(listaLampade = aggiornata), lampada)
                    }
                }
                showAggiungiLampadaDialog = false
            }
        )
    }

    if (lampadaInModifica != null && isEmergenza) {
        ModificaLampadaDialog(
            impianto = currentEmergenza.copy(listaLampade = lampadeEmergenza),
            lampada = lampadaInModifica!!,
            onDismiss = { lampadaInModifica = null },
            onConferma = { aggiornata ->
                val nuovaLista = lampadeEmergenza.map { if (it.id == aggiornata.id) aggiornata else it }
                lampadeEmergenza = nuovaLista
                onAggiornaLampadaInEmergenza?.invoke(currentEmergenza.copy(listaLampade = nuovaLista), aggiornata)
                lampadaInModifica = null
            }
        )
    }

    if (showAddDispositivoDialog && isAntincendio) {
        val nextLoop = dispositivoToEdit?.loop ?: 1
        val nextIndirizzo = if (dispositivoToEdit != null) {
            dispositivoToEdit!!.indirizzo
        } else {
            val existingInLoop = dispositiviAntincendio.filter { it.loop == nextLoop }.map { it.indirizzo }
            SiglaCloneHelper.findNextSiglaForClone("1", existingInLoop, defaultPrefix = "1")
        }

        AggiungiDispositivoDialog(
            dispositivoIniziale = dispositivoToEdit,
            defaultLoop = nextLoop,
            suggestedIndirizzo = nextIndirizzo,
            onDismiss = {
                showAddDispositivoDialog = false
                dispositivoToEdit = null
            },
            onConfirm = { disp ->
                if (dispositivoToEdit != null) {
                    dispositiviAntincendio = dispositiviAntincendio.map {
                        if (it.id == disp.id) disp else it
                    }
                } else {
                    dispositiviAntincendio = dispositiviAntincendio + disp
                }
                showAddDispositivoDialog = false
                dispositivoToEdit = null
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
                            val savedCod = codIntervento.trim()
                            val savedNome = nomeCompleto.trim()
                            val savedPremessa = premessa.ifBlank { null }
                            val savedNote = noteSpecifiche.ifBlank { null }

                            val updated: Impianto = when {
                                isQuadro -> {
                                    QuadroBT(
                                        id = impianto.id,
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        listaAttivita = attivitaList,
                                        listaNormative = impianto.listaNormative,
                                        cantiereId = impianto.cantiereId,
                                        quantita = 1,
                                        noteSpecifiche = savedNote,
                                        sigla = siglaQuadro.trim(),
                                        descrizioneQuadro = descrizioneQuadro.trim(),
                                        listaInterruttori = interruttoriQuadro
                                    )
                                }
                                isEmergenza -> {
                                    ImpiantoEmergenza(
                                        id = impianto.id,
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        listaAttivita = attivitaList,
                                        listaNormative = impianto.listaNormative,
                                        cantiereId = impianto.cantiereId,
                                        quantita = 1,
                                        noteSpecifiche = savedNote,
                                        listaLampade = lampadeEmergenza
                                    )
                                }
                                isQuadroMQT -> {
                                    val baseMqt = (impianto as? QuadroMQT) ?: QuadroMQT(
                                        id = impianto.id,
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        listaAttivita = attivitaList,
                                        listaNormative = impianto.listaNormative,
                                        cantiereId = impianto.cantiereId,
                                        quantita = 1,
                                        noteSpecifiche = savedNote
                                    )
                                    baseMqt.copy(
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        quantita = 1,
                                        noteSpecifiche = savedNote,
                                        listaAttivita = attivitaList
                                    ).withTipoInterruttore(tipoInterruttoreMQT)
                                }
                                isAntincendio -> {
                                    val baseRi = (impianto as? RilevazioneAntincendio) ?: RilevazioneAntincendio(
                                        id = impianto.id,
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        listaAttivita = attivitaList,
                                        listaNormative = impianto.listaNormative,
                                        cantiereId = impianto.cantiereId,
                                        quantita = 1,
                                        noteSpecifiche = savedNote
                                    )
                                    baseRi.copy(
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        quantita = 1,
                                        noteSpecifiche = savedNote,
                                        listaAttivita = attivitaList,
                                        categorieRivelatori = categorieRivelatoriState,
                                        listaDispositivi = dispositiviAntincendio
                                    )
                                }
                                else -> {
                                    impianto.copyWithBasicParams(
                                        codIntervento = savedCod,
                                        nomeCompleto = savedNome,
                                        premessa = savedPremessa,
                                        quantita = 1,
                                        noteSpecifiche = savedNote,
                                        listaAttivita = attivitaList
                                    )
                                }
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
                if (isQuadro) {
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
                if (isEmergenza) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("Sigla Documentale:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                codIntervento = "EM"
                                if (nomeCompleto.isBlank() || nomeCompleto.equals("Illuminazione di sicurezza", ignoreCase = true)) {
                                    nomeCompleto = "Illuminazione di emergenza"
                                }
                            }
                        ) {
                            RadioButton(
                                selected = codIntervento.equals("EM", ignoreCase = true),
                                onClick = {
                                    codIntervento = "EM"
                                    if (nomeCompleto.isBlank() || nomeCompleto.equals("Illuminazione di sicurezza", ignoreCase = true)) {
                                        nomeCompleto = "Illuminazione di emergenza"
                                    }
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("EM (Emergenza)", fontSize = 13.sp)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                codIntervento = "IS"
                                if (nomeCompleto.isBlank() || nomeCompleto.equals("Illuminazione di emergenza", ignoreCase = true)) {
                                    nomeCompleto = "Illuminazione di sicurezza"
                                }
                            }
                        ) {
                            RadioButton(
                                selected = codIntervento.equals("IS", ignoreCase = true),
                                onClick = {
                                    codIntervento = "IS"
                                    if (nomeCompleto.isBlank() || nomeCompleto.equals("Illuminazione di emergenza", ignoreCase = true)) {
                                        nomeCompleto = "Illuminazione di sicurezza"
                                    }
                                }
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("IS (Sicurezza)", fontSize = 13.sp)
                        }
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
        if (isQuadroMQT) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Tipologia Interruttore Media Tensione (MT)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "Seleziona la tecnologia dell'interruttore (SF6, ISV, IVOR). Le attività associate adegueranno automaticamente la colonna Zona/Impianto nel PDF.",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            MqtSwitchType.values().forEach { switchType ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable { tipoInterruttoreMQT = switchType }
                                        .padding(vertical = 4.dp, horizontal = 4.dp)
                                ) {
                                    RadioButton(
                                        selected = tipoInterruttoreMQT == switchType,
                                        onClick = { tipoInterruttoreMQT = switchType },
                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = switchType.label,
                                        fontWeight = if (tipoInterruttoreMQT == switchType) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        if (isQuadro) {
            item {
                TechnicalAttachmentSection(
                    title = "Interruttori Differenziali e Componenti",
                    subtitle = "Componenti associati a questo quadro con verifiche strumentali (Foglio 2)",
                    items = interruttoriQuadro,
                    itemTitle = { interruttore ->
                        listOfNotNull(interruttore.produttore, interruttore.codiceArticolo).joinToString(" ").ifBlank { interruttore.nome }
                    },
                    itemSubtitle = { interruttore ->
                        if (interruttore.nome.isNotBlank() && interruttore.nome != interruttore.codiceArticolo) interruttore.nome else ""
                    },
                    badgeText = { interruttore ->
                        interruttore.siglaCircuito?.ifBlank { "${interruttore.quantita}x" } ?: "${interruttore.quantita}x"
                    },
                    extraBadges = { interruttore ->
                        if (interruttore.quantita > 1 && !interruttore.siglaCircuito.isNullOrBlank()) {
                            Surface(
                                color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "${interruttore.quantita}x",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        val taratura = interruttore.caratteristicheTecniche["taratura"]
                            ?: interruttore.caratteristicheTecniche["Idn"]
                            ?: interruttore.caratteristicheTecniche["IΔn"]
                            ?: interruttore.caratteristicheTecniche["taraturaDiff"]
                        if (!taratura.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "⚡ $taratura",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        if (!interruttore.note.isNullOrBlank()) {
                            Text(
                                text = interruttore.note,
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                        if (!interruttore.etimClassId.isNullOrBlank()) {
                            Text(
                                text = "ETIM: ${interruttore.etimClassId}${if (!interruttore.etimClassName.isNullOrBlank()) " (${interruttore.etimClassName})" else ""}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                        }
                    },
                    customActions = { interruttore ->
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
                                                    val q = (impianto as? QuadroBT) ?: currentQuadro
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
                                Spacer(Modifier.width(6.dp))
                            }
                        }
                    },
                    onAddItem = { showAggiungiComponenteDialog = true },
                    onEditItem = { interruttoreInModifica = it },
                    onCloneItem = { interruttore ->
                        val existingSigle = interruttoriQuadro.mapNotNull { it.siglaCircuito }
                        val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                            baseSigla = interruttore.siglaCircuito ?: "QA1",
                            existingSigle = existingSigle,
                            defaultPrefix = "QA"
                        )
                        val duplicato = interruttore.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            siglaCircuito = nextSigla
                        )
                        val nuovaLista = interruttoriQuadro + duplicato
                        interruttoriQuadro = nuovaLista
                        onAggiungiComponenteAQuadro?.invoke(currentQuadro.copy(listaInterruttori = nuovaLista), duplicato)
                    },
                    onDeleteItem = { interruttore ->
                        interruttoriQuadro = interruttoriQuadro - interruttore
                    },
                    onClearAll = { interruttoriQuadro = emptyList() },
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentQuadro.copy(
                                sigla = siglaQuadro.trim(),
                                descrizioneQuadro = descrizioneQuadro.trim(),
                                listaInterruttori = interruttoriQuadro
                            )
                        )
                    },
                    addButtonLabel = "Aggiungi Componente",
                    emptyMessage = "Nessun componente registrato per questo quadro. Clicca su 'Aggiungi Componente' per iniziare."
                )
            }
        }

        // Sezione Lampade di Emergenza / Sicurezza (ImpiantoEmergenza: EM e IS - R7, R14, R16)
        if (isEmergenza) {
            item {
                val isSicurezza = codIntervento.equals("IS", ignoreCase = true)
                val attachmentTitle = if (isSicurezza) {
                    "Registro Lampade di Sicurezza (UNI 11222)"
                } else {
                    "Registro Lampade di Emergenza (UNI 11222)"
                }
                TechnicalAttachmentSection(
                    title = attachmentTitle,
                    subtitle = "Apparecchi di illuminazione installati per verifiche e prove di autonomia (Foglio 2)",
                    items = lampadeEmergenza,
                    itemTitle = { lampada ->
                        listOfNotNull(lampada.produttore, lampada.modello).joinToString(" ").ifBlank { lampada.sigla }
                    },
                    itemSubtitle = { lampada ->
                        if (!lampada.posizione.isNullOrBlank()) "📍 ${lampada.posizione}" else ""
                    },
                    badgeText = { lampada ->
                        lampada.sigla.ifBlank { "—" }
                    },
                    extraBadges = { lampada ->
                        Surface(
                            color = Color(0xFFE8F5E9),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "⏱ ${lampada.autonomia}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (!lampada.dataUltimoCambioBatteria.isNullOrBlank()) {
                            Surface(
                                color = Color(0xFFFFF3E0),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "🔋 ${lampada.dataUltimoCambioBatteria}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE65100),
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    },
                    onAddItem = { showAggiungiLampadaDialog = true },
                    onEditItem = { lampadaInModifica = it },
                    onCloneItem = { lampada ->
                        val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                            baseSigla = lampada.sigla,
                            existingSigle = lampadeEmergenza.map { it.sigla },
                            defaultPrefix = codIntervento
                        )
                        val duplicata = lampada.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            sigla = nextSigla
                        )
                        val nuovaLista = lampadeEmergenza + duplicata
                        lampadeEmergenza = nuovaLista
                        if (onAggiungiLampadeAEmergenza != null) {
                            onAggiungiLampadeAEmergenza.invoke(
                                currentEmergenza.copy(
                                    codIntervento = codIntervento,
                                    nomeCompleto = nomeCompleto,
                                    listaLampade = nuovaLista
                                ),
                                listOf(duplicata)
                            )
                        } else {
                            onAggiungiLampadaAEmergenza?.invoke(
                                currentEmergenza.copy(
                                    codIntervento = codIntervento,
                                    nomeCompleto = nomeCompleto,
                                    listaLampade = nuovaLista
                                ),
                                duplicata
                            )
                        }
                    },
                    onDeleteItem = { lampada ->
                        lampadeEmergenza = lampadeEmergenza - lampada
                    },
                    onClearAll = { lampadeEmergenza = emptyList() },
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentEmergenza.copy(
                                codIntervento = codIntervento,
                                nomeCompleto = nomeCompleto,
                                listaLampade = lampadeEmergenza
                            )
                        )
                    },
                    addButtonLabel = "Aggiungi Lampade",
                    emptyMessage = "Nessuna lampada registrata. Clicca 'Aggiungi Lampade' per iniziare."
                )
            }
        }

        // Sezione Rilevazione Incendi (RilevazioneAntincendio: Checkbox Componenti & Registro Loop - R18, R19)
        if (isAntincendio) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Dispositivi di Rivelazione Installati (Verifiche Semestrali al 50%)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colors.primary
                        )
                        Text(
                            text = "Seleziona le tipologie di dispositivi presenti nell'impianto. In fase di stampa a 6 mesi verranno iniettate automaticamente le prove di funzionamento al 50% conformi alla normativa.",
                            style = MaterialTheme.typography.caption,
                            color = Color.Gray
                        )

                        // 7 Checkbox categorie
                        val categorie = CategoriaRivelatore.values()
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categorie.forEach { cat ->
                                val isChecked = categorieRivelatoriState[cat.key] == true
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val mut = categorieRivelatoriState.toMutableMap()
                                            if (!isChecked) mut[cat.key] = true else mut.remove(cat.key)
                                            categorieRivelatoriState = mut
                                        }
                                        .padding(vertical = 4.dp, horizontal = 2.dp)
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            val mut = categorieRivelatoriState.toMutableMap()
                                            if (checked) mut[cat.key] = true else mut.remove(cat.key)
                                            categorieRivelatoriState = mut
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = cat.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (isChecked) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                TechnicalAttachmentSection(
                    title = "Registro Componenti a Loop (ELENCO RIL)",
                    subtitle = "Sensori, rivelatori e moduli suddivisi per Loop (1..4) per verifiche strumentali (Foglio 2)",
                    items = dispositiviAntincendio,
                    itemTitle = { disp ->
                        disp.descrizione.ifBlank { "Dispositivo ${disp.indirizzo}" }
                    },
                    itemSubtitle = { disp ->
                        val parts = listOfNotNull(
                            disp.installazione?.let { "Installazione: $it" },
                            disp.zona?.let { "Zona: $it" },
                            disp.dataInstallazione?.let { "Data: $it" }
                        )
                        parts.joinToString(" • ")
                    },
                    badgeText = { disp ->
                        if (disp.indirizzo.startsWith("L", ignoreCase = true) || disp.indirizzo.contains(".")) {
                            disp.indirizzo
                        } else {
                            "L${disp.loop}.${disp.indirizzo}"
                        }
                    },
                    extraBadges = { disp ->
                        Surface(
                            color = MaterialTheme.colors.primary.copy(alpha = 0.12f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = "LOOP ${disp.loop}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = disp.tipo,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    },
                    onAddItem = {
                        dispositivoToEdit = null
                        showAddDispositivoDialog = true
                    },
                    onEditItem = { disp ->
                        dispositivoToEdit = disp
                        showAddDispositivoDialog = true
                    },
                    onCloneItem = { disp ->
                        val existingSigle = dispositiviAntincendio.filter { it.loop == disp.loop }.map { it.indirizzo }
                        val nextIndirizzo = SiglaCloneHelper.findNextSiglaForClone(
                            baseSigla = disp.indirizzo,
                            existingSigle = existingSigle,
                            defaultPrefix = "1"
                        )
                        val duplicato = disp.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            indirizzo = nextIndirizzo
                        )
                        dispositiviAntincendio = dispositiviAntincendio + duplicato
                    },
                    onDeleteItem = { disp ->
                        dispositiviAntincendio = dispositiviAntincendio - disp
                    },
                    onClearAll = { dispositiviAntincendio = emptyList() },
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentAntincendio.copy(
                                codIntervento = codIntervento,
                                nomeCompleto = nomeCompleto,
                                categorieRivelatori = categorieRivelatoriState,
                                listaDispositivi = dispositiviAntincendio
                            )
                        )
                    },
                    addButtonLabel = "Aggiungi al Loop",
                    emptyMessage = "Nessun sensore o modulo registrato. Clicca 'Aggiungi al Loop' per popolare il registro."
                )
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
    var zona by remember(attivita) { mutableStateOf(attivita.zona ?: "") }
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

                OutlinedTextField(
                    value = zona,
                    onValueChange = {
                        zona = it
                        onUpdate(attivita.copy(zona = it.ifBlank { null }))
                    },
                    label = { Text("Zona/Componente", fontSize = 11.sp) },
                    modifier = Modifier.weight(1.2f),
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

