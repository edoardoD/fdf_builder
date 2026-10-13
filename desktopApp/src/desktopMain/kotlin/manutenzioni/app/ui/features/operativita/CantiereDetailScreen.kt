package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.app.ui.ImpiantoEditor
import manutenzioni.app.ui.ImpiantoSelectionList
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.app.ui.theme.SlateBorder

@Composable
fun CantiereDetailScreen(
    state: ManutenzioniUiState,
    onBack: () -> Unit,
    onImpiantoSelectionChanged: (String, Boolean) -> Unit,
    onEditImpianto: (Impianto) -> Unit,
    onSaveImpianto: (Impianto) -> Unit,
    onFrequenzaPerImpiantoSelected: (String, Periodo) -> Unit,
    onGeneraPdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onCreateNewImpianto: (Impianto?) -> Unit,
    onDeleteImpianto: (String) -> Unit,
    onSearchComponenti: (String) -> Unit = {},
    onClearSearchComponenti: () -> Unit = {},
    onApprovaComponente: ((manutenzioni.domain.model.QuadroBT, manutenzioni.domain.model.ComponentCandidate, manutenzioni.domain.model.VarianteProdotto, Int, String) -> Unit)? = null,
    onAggiungiComponenteAQuadro: ((manutenzioni.domain.model.QuadroBT, manutenzioni.domain.model.InterruttoreBT) -> Unit)? = null,
    onSostituisciProduttore: ((manutenzioni.domain.model.QuadroBT, String, manutenzioni.domain.model.VarianteProdotto) -> Unit)? = null,
    onAggiungiLampadaAEmergenza: ((manutenzioni.domain.model.ImpiantoEmergenza, manutenzioni.domain.model.LampadaEmergenza) -> Unit)? = null,
    onAggiungiLampadeAEmergenza: ((manutenzioni.domain.model.ImpiantoEmergenza, List<manutenzioni.domain.model.LampadaEmergenza>) -> Unit)? = null,
    onAggiornaLampadaInEmergenza: ((manutenzioni.domain.model.ImpiantoEmergenza, manutenzioni.domain.model.LampadaEmergenza) -> Unit)? = null,
    onUpdateDatiIntervento: (String?, String?) -> Unit = { _, _ -> },
    onUpdateMqtSwitchType: ((String, manutenzioni.domain.model.MqtSwitchType) -> Unit)? = null,
    onAddImpiantiFromTemplates: (List<Impianto>) -> Unit = { templates -> templates.forEach { onCreateNewImpianto(it) } }
) {
    // Gestione visualizzazione ImpiantoEditor
    var impiantoInModifica by remember { mutableStateOf<Impianto?>(null) }
    var showNuovoImpiantoDialog by remember { mutableStateOf(false) }
    var showDeleteImpiantoDialog by remember { mutableStateOf(false) }
    var impiantoInEliminazione by remember { mutableStateOf<Impianto?>(null) }
    var showMassiveQuadroDialog by remember { mutableStateOf(false) }
    var selectedQuadroTemplate by remember { mutableStateOf<manutenzioni.domain.model.Impianto?>(null) }

    if (impiantoInModifica != null) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            Card(elevation = 8.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
                ImpiantoEditor(
                    impianto = impiantoInModifica!!,
                    componentiStandard = state.componentiStandard,
                    isReadOnlyAdminFields = true,
                    catalogoApprovato = state.catalogoApprovato,
                    candidateComponents = state.candidateComponents,
                    isSearchingComponenti = state.isSearchingComponenti,
                    onSearchComponenti = onSearchComponenti,
                    onClearSearchComponenti = onClearSearchComponenti,
                    onApprovaComponente = onApprovaComponente,
                    onAggiungiComponenteAQuadro = onAggiungiComponenteAQuadro,
                    onSostituisciProduttore = onSostituisciProduttore,
                    onAggiungiLampadaAEmergenza = onAggiungiLampadaAEmergenza,
                    onAggiungiLampadeAEmergenza = onAggiungiLampadeAEmergenza,
                    onAggiornaLampadaInEmergenza = onAggiornaLampadaInEmergenza,
                    onSave = { impianto, _ -> 
                        onSaveImpianto(impianto)
                        impiantoInModifica = null
                    },
                    onCancel = {
                        impiantoInModifica = null
                    }
                )
            }
        }
        return // Non disegniamo il resto se siamo nell'editor
    }

    if (showNuovoImpiantoDialog) {
        NuovoImpiantoDialog(
            impiantiGlobali = state.impiantiGlobali,
            onDismiss = { showNuovoImpiantoDialog = false },
            onConfirmMultiple = { templates ->
                // R6: i Quadri BT richiedono il wizard di creazione massiva (sigle QG, Q1, ...),
                // tutti gli altri template vengono aggiunti in blocco con un solo click.
                val (quadri, altri) = templates.partition { it.isQuadroBtTemplate() }
                if (altri.isNotEmpty()) onAddImpiantiFromTemplates(altri)
                showNuovoImpiantoDialog = false
                quadri.firstOrNull()?.let { quadroTemplate ->
                    selectedQuadroTemplate = quadroTemplate
                    showMassiveQuadroDialog = true
                }
            },
            onCreateEmpty = {
                onCreateNewImpianto(null)
                showNuovoImpiantoDialog = false
            }
        )
    }
    
    if (showDeleteImpiantoDialog && impiantoInEliminazione != null) {
        AlertDialog(
            onDismissRequest = { 
                showDeleteImpiantoDialog = false
                impiantoInEliminazione = null
            },
            title = { Text("Conferma eliminazione") },
            text = { Text("Vuoi davvero eliminare l'impianto '${impiantoInEliminazione?.nomeCompleto}' da questo cantiere?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteImpianto(impiantoInEliminazione!!.id)
                        showDeleteImpiantoDialog = false
                        impiantoInEliminazione = null
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.error, contentColor = Color.White)
                ) {
                    Text("Elimina")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showDeleteImpiantoDialog = false
                    impiantoInEliminazione = null
                }) {
                    Text("Annulla")
                }
            }
        )
    }

    if (showMassiveQuadroDialog) {
        MassiveQuadroCreationDialog(
            cantiereId = state.selectedCantiere?.id ?: "",
            templateQuadro = selectedQuadroTemplate,
            interruttoriDisponibili = state.componentiStandard.filter { it.tipo == manutenzioni.domain.model.TipoComponenteStandard.INTERRUTTORE_BT }.map { manutenzioni.domain.model.InterruttoreBT(nome = it.nome) },
            onDismiss = { showMassiveQuadroDialog = false },
            onSave = { nuoviQuadri ->
                nuoviQuadri.forEach { onSaveImpianto(it) }
                showMassiveQuadroDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Torna indietro")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = state.selectedCantiere?.nome ?: "Dettaglio Cantiere",
                        style = MaterialTheme.typography.h5,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = state.selectedCantiere?.indirizzo?.let { "📍 $it" } ?: "Gestione verifiche periodiche e generazione schede",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Banner / Card Dati Intervento e Manutentore
        var dataInizio by remember(state.selectedCantiere?.id, state.selectedCantiere?.dataInizioIntervento) {
            mutableStateOf(state.selectedCantiere?.dataInizioIntervento ?: "")
        }
        var nomeManutentore by remember(state.selectedCantiere?.id, state.selectedCantiere?.nomeManutentore) {
            mutableStateOf(state.selectedCantiere?.nomeManutentore ?: "")
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp,
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Engineering,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Dati Sessione Intervento",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "Storicizzazione automatica nel database",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = dataInizio,
                        onValueChange = {
                            dataInizio = it
                            onUpdateDatiIntervento(it, nomeManutentore)
                        },
                        label = { Text("Data Inizio Intervento") },
                        placeholder = { Text("GG/MM/AAAA (es. 08/10/2026)") },
                        leadingIcon = {
                            Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    OutlinedTextField(
                        value = nomeManutentore,
                        onValueChange = {
                            nomeManutentore = it
                            onUpdateDatiIntervento(dataInizio, it)
                        },
                        label = { Text("Nome Manutentore / Tecnico") },
                        placeholder = { Text("Nome e Cognome (es. Mario Rossi)") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = SlateBorder
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            // Colonna 1: Impianti del cantiere
            Column(modifier = Modifier.weight(2f).fillMaxHeight()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Attività di verifica in questo cantiere", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                    Button(onClick = { showNuovoImpiantoDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Aggiungi Attività di Verifica")
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ImpiantoSelectionList(
                    impianti = state.impiantiDelCantiere,
                    selectionState = state.impiantiSelezionati,
                    onSelectionChanged = onImpiantoSelectionChanged,
                    onEditImpianto = { impianto -> 
                        impiantoInModifica = impianto 
                        onEditImpianto(impianto)
                    },
                    onDeleteImpianto = { impianto ->
                        impiantoInEliminazione = impianto
                        showDeleteImpiantoDialog = true
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }

            // Colonna 2: Frequenza e Generazione
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 0.dp,
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("1. Scegli Frequenza per Modulo", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val impiantiAttiviIds = state.impiantiSelezionati.filter { it.value }.keys
                        val impiantiAttivi = state.impiantiDelCantiere.filter { it.id in impiantiAttiviIds }
                        
                        if (impiantiAttivi.isEmpty()) {
                            Text("Seleziona almeno un impianto dalla lista a sinistra.", color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                        } else {
                            impiantiAttivi.forEach { impianto ->
                                val frequenzeDisp = manutenzioni.domain.service.CrossPlantAttivitaResolver.resolveFrequenze(impianto, state.impiantiDelCantiere)
                                val currentFreq = state.frequenzePerImpianto[impianto.id]
                                val displayLabel = if (impianto is manutenzioni.domain.model.QuadroBT) "${impianto.codIntervento} - ${impianto.sigla}" else impianto.codIntervento
                                Text(text = displayLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.body2, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                                
                                if (impianto is manutenzioni.domain.model.QuadroMQT) {
                                    Text(
                                        text = "Tecnologia Interruttore MT:",
                                        style = MaterialTheme.typography.caption,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colors.primary,
                                        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        manutenzioni.domain.model.MqtSwitchType.values().forEach { st ->
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.clickable {
                                                    onUpdateMqtSwitchType?.invoke(impianto.id, st)
                                                }
                                            ) {
                                                RadioButton(
                                                    selected = (impianto.tipoInterruttore ?: manutenzioni.domain.model.MqtSwitchType.SF6) == st,
                                                    onClick = { onUpdateMqtSwitchType?.invoke(impianto.id, st) },
                                                    colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(Modifier.width(2.dp))
                                                Text(st.label, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }

                                if (frequenzeDisp.isEmpty()) {
                                    Text("Nessuna attività registrata per questo modulo.", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                                } else {
                                    frequenzeDisp.forEach { periodo ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = currentFreq == periodo,
                                                onClick = { onFrequenzaPerImpiantoSelected(impianto.id, periodo) },
                                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(text = periodo.label(), style = MaterialTheme.typography.body2)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 0.dp,
                    border = BorderStroke(1.dp, SlateBorder),
                    shape = RoundedCornerShape(8.dp),
                    backgroundColor = MaterialTheme.colors.surface
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("2. Generazione Schede di Verifica", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val impiantiAttiviIdsForGen = state.impiantiSelezionati.filter { it.value }.keys
                        val isEnabled = impiantiAttiviIdsForGen.isNotEmpty() && impiantiAttiviIdsForGen.all { id -> state.frequenzePerImpianto.containsKey(id) }
                        Button(
                            onClick = onGeneraPdf,
                            enabled = isEnabled && !state.isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White)
                        ) {
                            Text("Genera Schede di Verifica", fontWeight = FontWeight.Bold)
                        }
                        
                        if (state.pdfFile != null) {
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = onOpenPdf,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colors.primary)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Apri PDF Creato")
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Percorso: ${state.pdfFile.name}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                        }
                    }
                }
            }
        }
    }
}
