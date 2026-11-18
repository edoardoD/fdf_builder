package manutenzioni.app.ui.features.operativita.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.features.operativita.*
import manutenzioni.app.ui.features.operativita.editor.components.AttivitaCard
import manutenzioni.app.ui.features.operativita.editor.sections.*
import manutenzioni.domain.model.*

@Composable
fun ImpiantoEditor(
    componentiStandard: List<ComponenteStandard> = emptyList(),
    impianto: Impianto,
    isNew: Boolean = impianto.codIntervento.isBlank(),
    isReadOnlyAdminFields: Boolean = false,
    catalogoApprovato: List<ComponenteApprovato> = emptyList(),
    onAggiungiComponenteAQuadro: ((QuadroBT, InterruttoreBT) -> Unit)? = null,
    onSostituisciProduttore: ((QuadroBT, String, VarianteProdotto) -> Unit)? = null,
    onAggiungiLampadaAEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onAggiungiLampadeAEmergenza: ((ImpiantoEmergenza, List<LampadaEmergenza>) -> Unit)? = null,
    onAggiornaLampadaInEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onAggiungiDispositiviAdAntincendio: ((RilevazioneAntincendio, List<DispositivoRilevazione>) -> Unit)? = null,
    onAggiornaDispositivoInAntincendio: ((RilevazioneAntincendio, DispositivoRilevazione) -> Unit)? = null,
    onExportStandaloneTechnicalSheet: ((Impianto) -> Unit)? = null,
    onSave: (Impianto, Boolean) -> Unit,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAggiungiComponenteDialog by remember { mutableStateOf(false) }
    var interruttoreInModifica by remember { mutableStateOf<InterruttoreBT?>(null) }
    var showAggiungiLampadaDialog by remember { mutableStateOf(false) }
    var lampadaInModifica by remember { mutableStateOf<LampadaEmergenza?>(null) }

    var codIntervento by remember(impianto) { mutableStateOf(impianto.codIntervento) }
    var nomeCompleto by remember(impianto) { mutableStateOf(impianto.nomeCompleto) }
    var premessa by remember(impianto) { mutableStateOf(impianto.premessa ?: "") }
    var noteSpecifiche by remember(impianto) { mutableStateOf(impianto.noteSpecifiche ?: "") }
    var attivitaList by remember(impianto) { mutableStateOf(impianto.listaAttivita) }

    var siglaQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.sigla ?: "") }
    var descrizioneQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.descrizioneQuadro ?: "") }
    var interruttoriQuadro by remember(impianto) { mutableStateOf((impianto as? QuadroBT)?.listaInterruttori ?: emptyList()) }

    var lampadeEmergenza by remember(impianto) { mutableStateOf((impianto as? ImpiantoEmergenza)?.listaLampade ?: emptyList()) }

    var propagaModifiche by remember { mutableStateOf(false) }
    var codInterventoError by remember { mutableStateOf(false) }
    var nomeCompletoError by remember { mutableStateOf(false) }

    // Polymorphic properties from Milestone 1 (0 string-sniffing heuristics)
    val isQuadro = impianto.isQuadro || (isNew && codIntervento.trim().equals("Q", ignoreCase = true))
    val isEmergenza = impianto.isEmergenza || (isNew && (codIntervento.trim().equals("EM", ignoreCase = true) || codIntervento.trim().equals("IS", ignoreCase = true)))
    val isQuadroMQT = impianto.isQuadroMqt || (isNew && codIntervento.trim().equals("QMT", ignoreCase = true))
    val isAntincendio = impianto.isAntincendio || (isNew && codIntervento.trim().equals("RI", ignoreCase = true))

    var tipoInterruttoreMQT by remember(impianto) {
        mutableStateOf((impianto as? QuadroMQT)?.tipoInterruttore ?: MqtSwitchType.SF6)
    }

    var dispositiviAntincendio by remember(impianto) {
        mutableStateOf((impianto as? RilevazioneAntincendio)?.listaDispositivi ?: emptyList())
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

    if (showAggiungiComponenteDialog && isQuadro) {
        AggiungiComponenteDialog(
            quadro = currentQuadro.copy(
                sigla = siglaQuadro.trim(),
                descrizioneQuadro = descrizioneQuadro.trim(),
                listaInterruttori = interruttoriQuadro
            ),
            onDismiss = { showAggiungiComponenteDialog = false },
            onConferma = { nuovoInterruttore ->
                val nuovaLista = interruttoriQuadro + nuovoInterruttore
                interruttoriQuadro = nuovaLista
                showAggiungiComponenteDialog = false
                onAggiungiComponenteAQuadro?.invoke(
                    currentQuadro.copy(listaInterruttori = nuovaLista),
                    nuovoInterruttore
                )
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
            onConferma = { interruttoreAggiornato ->
                val idx = interruttoriQuadro.indexOfFirst { it.id == interruttoreAggiornato.id }
                if (idx >= 0) {
                    val mList = interruttoriQuadro.toMutableList()
                    mList[idx] = interruttoreAggiornato
                    interruttoriQuadro = mList
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
                val nuovaLista = lampadeEmergenza + nuoveLampade
                lampadeEmergenza = nuovaLista
                showAggiungiLampadaDialog = false
                if (onAggiungiLampadeAEmergenza != null) {
                    onAggiungiLampadeAEmergenza.invoke(
                        currentEmergenza.copy(listaLampade = nuovaLista),
                        nuoveLampade
                    )
                } else if (nuoveLampade.size == 1) {
                    onAggiungiLampadaAEmergenza?.invoke(
                        currentEmergenza.copy(listaLampade = nuovaLista),
                        nuoveLampade.first()
                    )
                }
            }
        )
    }

    if (lampadaInModifica != null && isEmergenza) {
        ModificaLampadaDialog(
            impianto = currentEmergenza.copy(listaLampade = lampadeEmergenza),
            lampada = lampadaInModifica!!,
            onDismiss = { lampadaInModifica = null },
            onConferma = { lampadaAggiornata ->
                val idx = lampadeEmergenza.indexOfFirst { it.id == lampadaAggiornata.id }
                if (idx >= 0) {
                    val mList = lampadeEmergenza.toMutableList()
                    mList[idx] = lampadaAggiornata
                    lampadeEmergenza = mList
                }
                onAggiornaLampadaInEmergenza?.invoke(
                    currentEmergenza.copy(listaLampade = lampadeEmergenza),
                    lampadaAggiornata
                )
                lampadaInModifica = null
            }
        )
    }

    val currentAntincendio = (impianto as? RilevazioneAntincendio) ?: RilevazioneAntincendio(
        id = impianto.id,
        codIntervento = codIntervento.trim().ifBlank { "RI" },
        nomeCompleto = nomeCompleto.trim().ifBlank { "Rilevazione incendi" },
        premessa = premessa.ifBlank { null },
        listaAttivita = attivitaList,
        listaNormative = impianto.listaNormative,
        cantiereId = impianto.cantiereId,
        quantita = 1,
        noteSpecifiche = noteSpecifiche.ifBlank { null },
        listaDispositivi = dispositiviAntincendio
    )

    if (showAddDispositivoDialog && isAntincendio) {
        val editing = dispositivoToEdit
        if (editing == null) {
            MassiveDispositivoAntincendioDialog(
                dispositiviEsistenti = dispositiviAntincendio,
                onDismiss = { showAddDispositivoDialog = false },
                onConfirmBatch = { nuovi ->
                    val previousImpianto = currentAntincendio.copy(listaDispositivi = dispositiviAntincendio)
                    dispositiviAntincendio = dispositiviAntincendio + nuovi
                    showAddDispositivoDialog = false
                    onAggiungiDispositiviAdAntincendio?.invoke(
                        previousImpianto,
                        nuovi
                    )
                }
            )
        } else {
            AggiungiDispositivoDialog(
                dispositivoIniziale = editing,
                dispositiviEsistenti = dispositiviAntincendio,
                onDismiss = {
                    showAddDispositivoDialog = false
                    dispositivoToEdit = null
                },
                onConfirm = { dispSalvato ->
                    val idx = dispositiviAntincendio.indexOfFirst { it.id == dispSalvato.id }
                    if (idx >= 0) {
                        val mList = dispositiviAntincendio.toMutableList()
                        mList[idx] = dispSalvato
                        dispositiviAntincendio = mList
                    }
                    onAggiornaDispositivoInAntincendio?.invoke(
                        currentAntincendio.copy(listaDispositivi = dispositiviAntincendio),
                        dispSalvato
                    )
                    showAddDispositivoDialog = false
                    dispositivoToEdit = null
                }
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            GeneralEditorSection(
                codIntervento = codIntervento,
                onCodInterventoChange = {
                    codIntervento = it
                    codInterventoError = false
                },
                nomeCompleto = nomeCompleto,
                onNomeCompletoChange = {
                    nomeCompleto = it
                    nomeCompletoError = false
                },
                premessa = premessa,
                onPremessaChange = { premessa = it },
                siglaQuadro = siglaQuadro,
                onSiglaQuadroChange = { siglaQuadro = it },
                descrizioneQuadro = descrizioneQuadro,
                onDescrizioneQuadroChange = { descrizioneQuadro = it },
                isQuadro = isQuadro,
                isEmergenza = isEmergenza,
                isNew = isNew,
                isReadOnlyAdminFields = isReadOnlyAdminFields,
                codInterventoError = codInterventoError,
                nomeCompletoError = nomeCompletoError,
                propagaModifiche = propagaModifiche,
                onPropagaModificheChange = { propagaModifiche = it },
                onSaveClick = {
                    val hasError = codIntervento.isBlank() || nomeCompleto.isBlank()
                    codInterventoError = codIntervento.isBlank()
                    nomeCompletoError = nomeCompleto.isBlank()
                    if (!hasError) {
                        val savedCod = codIntervento.trim().uppercase()
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
                onCancelClick = onCancel
            )
        }

        if (isQuadroMQT) {
            item {
                QuadroMqtEditorSection(
                    tipoInterruttore = tipoInterruttoreMQT,
                    onTipoInterruttoreChange = { tipoInterruttoreMQT = it }
                )
            }
        }

        if (isQuadro) {
            item {
                QuadroBtEditorSection(
                    quadro = currentQuadro.copy(
                        sigla = siglaQuadro.trim(),
                        descrizioneQuadro = descrizioneQuadro.trim(),
                        listaInterruttori = interruttoriQuadro
                    ),
                    interruttori = interruttoriQuadro,
                    onInterruttoriChange = { interruttoriQuadro = it },
                    catalogoApprovato = catalogoApprovato,
                    onOpenAddDialog = { showAggiungiComponenteDialog = true },
                    onEditInterruttore = { interruttoreInModifica = it },
                    onSostituisciProduttore = onSostituisciProduttore,
                    onAggiungiComponenteAQuadro = onAggiungiComponenteAQuadro,
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentQuadro.copy(
                                sigla = siglaQuadro.trim(),
                                descrizioneQuadro = descrizioneQuadro.trim(),
                                listaInterruttori = interruttoriQuadro
                            )
                        )
                    }
                )
            }
        }

        if (isEmergenza) {
            item {
                EmergenzaEditorSection(
                    currentEmergenza = currentEmergenza,
                    lampade = lampadeEmergenza,
                    onLampadeChange = { lampadeEmergenza = it },
                    codIntervento = codIntervento,
                    onOpenAddDialog = { showAggiungiLampadaDialog = true },
                    onEditLampada = { lampadaInModifica = it },
                    onAggiungiLampadeAEmergenza = onAggiungiLampadeAEmergenza,
                    onAggiungiLampadaAEmergenza = onAggiungiLampadaAEmergenza,
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentEmergenza.copy(
                                codIntervento = codIntervento,
                                nomeCompleto = nomeCompleto,
                                listaLampade = lampadeEmergenza
                            )
                        )
                    }
                )
            }
        }

        if (isAntincendio) {
            item {
                AntincendioEditorSection(
                    dispositivi = dispositiviAntincendio,
                    onDispositiviChange = { dispositiviAntincendio = it },
                    onOpenAddDialog = {
                        dispositivoToEdit = null
                        showAddDispositivoDialog = true
                    },
                    onEditDispositivo = { disp ->
                        dispositivoToEdit = disp
                        showAddDispositivoDialog = true
                    },
                    onExportStandalonePdf = {
                        onExportStandaloneTechnicalSheet?.invoke(
                            currentAntincendio.copy(
                                codIntervento = codIntervento,
                                nomeCompleto = nomeCompleto,
                                listaDispositivi = dispositiviAntincendio
                            )
                        )
                    }
                )
            }
        }

        // Sezione Attività
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività di Manutenzione (${attivitaList.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colors.primary
                )
                Button(
                    onClick = {
                        val maxNum = attivitaList.maxOfOrNull { it.nAttivita } ?: 0
                        attivitaList = attivitaList + Attivita(
                            nAttivita = maxNum + 1,
                            tipoAttivita = "Nuova Attività",
                            descrizione = "",
                            frequenza = Periodo(TipoPeriodo.A, 1)
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aggiungi Attività", fontSize = 12.sp)
                }
            }
        }

        itemsIndexed(attivitaList) { index, att ->
            AttivitaCard(
                numeroAttivita = if (att.nAttivita > 0) att.nAttivita else index + 1,
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
