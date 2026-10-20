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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.state.ToggleableState
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
    onFrequenzaPerImpiantoToggled: (String, Periodo) -> Unit = onFrequenzaPerImpiantoSelected,
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
    onExportStandaloneTechnicalSheet: ((manutenzioni.domain.model.Impianto) -> Unit)? = null,
    onExportCantiereInterruttoriPdf: () -> Unit = {},
    onUpdateDatiIntervento: (String?, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onFrequenzaForImpiantiToggled: ((List<String>, Periodo) -> Unit)? = null,
    onImpiantiSelectionChanged: ((List<String>, Boolean) -> Unit)? = null,
    onSelectFirmaManutentore: () -> Unit = {},
    onRemoveFirmaManutentore: () -> Unit = {},
    onUpdateMqtSwitchType: ((String, manutenzioni.domain.model.MqtSwitchType) -> Unit)? = null,
    onUpdateEmergenzaSiglaDocumentale: ((String, manutenzioni.domain.model.EmergenzaSiglaDoc) -> Unit)? = null,
    onAddImpiantiFromTemplates: (List<Impianto>) -> Unit = { templates -> templates.forEach { onCreateNewImpianto(it) } }
) {
    // Gestione visualizzazione ImpiantoEditor
    var impiantoInModifica by remember { mutableStateOf<Impianto?>(null) }
    var showNuovoImpiantoDialog by remember { mutableStateOf(false) }
    var showDeleteImpiantoDialog by remember { mutableStateOf(false) }
    var impiantoInEliminazione by remember { mutableStateOf<Impianto?>(null) }
    var showMassiveQuadroDialog by remember { mutableStateOf(false) }
    var selectedQuadroTemplate by remember { mutableStateOf<manutenzioni.domain.model.Impianto?>(null) }
    var selectedCantiereTab by remember { mutableStateOf(0) }

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
                    onExportStandaloneTechnicalSheet = onExportStandaloneTechnicalSheet,
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

        Spacer(modifier = Modifier.height(16.dp))

        // Banner / Card Dati Intervento e Manutentore
        var dataInizioContratto by remember(state.selectedCantiere?.id, state.selectedCantiere?.dataInizioContratto) {
            mutableStateOf(state.selectedCantiere?.dataInizioContratto ?: "")
        }
        var dataInterventoPrecedente by remember(state.selectedCantiere?.id, state.selectedCantiere?.dataInterventoPrecedente) {
            mutableStateOf(state.selectedCantiere?.dataInterventoPrecedente ?: "")
        }
        var dataIntervento by remember(state.selectedCantiere?.id, state.selectedCantiere?.dataIntervento, state.selectedCantiere?.dataInizioIntervento) {
            mutableStateOf(state.selectedCantiere?.dataIntervento ?: state.selectedCantiere?.dataInizioIntervento ?: "")
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
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = dataInizioContratto,
                        onValueChange = {
                            dataInizioContratto = it
                            onUpdateDatiIntervento(it, dataIntervento, dataInterventoPrecedente, nomeManutentore)
                        },
                        label = { Text("Data inizio contratto") },
                        placeholder = { Text("GG/MM/AAAA") },
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
                        value = dataInterventoPrecedente,
                        onValueChange = {
                            dataInterventoPrecedente = it
                            onUpdateDatiIntervento(dataInizioContratto, dataIntervento, it, nomeManutentore)
                        },
                        label = { Text("Data intervento precedente") },
                        placeholder = { Text("GG/MM/AAAA") },
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
                        value = dataIntervento,
                        onValueChange = {
                            dataIntervento = it
                            onUpdateDatiIntervento(dataInizioContratto, it, dataInterventoPrecedente, nomeManutentore)
                        },
                        label = { Text("Data intervento") },
                        placeholder = { Text("GG/MM/AAAA") },
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
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = nomeManutentore,
                        onValueChange = {
                            nomeManutentore = it
                            onUpdateDatiIntervento(dataInizioContratto, dataIntervento, dataInterventoPrecedente, it)
                        },
                        label = { Text("Nome Manutentore / Tecnico") },
                        placeholder = { Text("Nome e Cognome (es. Mario Rossi)") },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colors.primary)
                        },
                        singleLine = true,
                        modifier = Modifier.weight(1.2f),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = MaterialTheme.colors.primary,
                            unfocusedBorderColor = SlateBorder
                        )
                    )

                    val hasFirma = !state.selectedCantiere?.firmaManutentore.isNullOrBlank()
                    Column(
                        modifier = Modifier.weight(0.8f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = onSelectFirmaManutentore,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = if (hasFirma) Color(0xFF2E7D32) else MaterialTheme.colors.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (hasFirma) Icons.Default.Check else Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (hasFirma) "Firma Caricata (Cambia)" else "Carica Firma Tecnico",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            if (hasFirma) {
                                TextButton(
                                    onClick = onRemoveFirmaManutentore,
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colors.error)
                                ) {
                                    Text("Rimuovi", fontSize = 11.sp)
                                }
                            }
                        }
                        Text(
                            text = if (hasFirma) "✓ Immagine firma pronta per il cartiglio PDF" else "Nessuna firma digitale associata (opzionale)",
                            fontSize = 10.sp,
                            color = if (hasFirma) Color(0xFF2E7D32) else MaterialTheme.colors.secondary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // TabRow di Cantiere: 0 = "Generazione schede di Verifica", 1 = "Elenco interruttori" (R11, R12)
        val quadriDelCantiere = state.impiantiDelCantiere.filterIsInstance<manutenzioni.domain.model.QuadroBT>()
        val totInterruttori = quadriDelCantiere.sumOf { q -> q.listaInterruttori.sumOf { it.quantita.coerceAtLeast(1) } }

        TabRow(
            selectedTabIndex = selectedCantiereTab,
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedCantiereTab == 0,
                onClick = { selectedCantiereTab = 0 },
                text = {
                    Text(
                        "Generazione schede di Verifica",
                        fontWeight = if (selectedCantiereTab == 0) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = selectedCantiereTab == 1,
                onClick = { selectedCantiereTab = 1 },
                text = {
                    Text(
                        "Elenco interruttori ($totInterruttori)",
                        fontWeight = if (selectedCantiereTab == 1) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedCantiereTab == 1) {
            CantiereElencoInterruttoriTab(
                quadri = quadriDelCantiere,
                isLoading = state.isLoading,
                onExportPdf = onExportCantiereInterruttoriPdf,
                onEditQuadro = { quadro ->
                    impiantoInModifica = quadro
                    onEditImpianto(quadro)
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        } else {
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
                        onBulkSelectionChanged = onImpiantiSelectionChanged,
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
                            Text("1. Scegli Frequenze per Modulo", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Puoi spuntare più periodicità per lo stesso impianto (es. 6 Mesi + 1 Anno)",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.secondary
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            val impiantiAttiviIds = state.impiantiSelezionati.filter { it.value }.keys
                            val impiantiAttivi = state.impiantiDelCantiere.filter { it.id in impiantiAttiviIds }
                            
                            if (impiantiAttivi.isEmpty()) {
                                Text("Seleziona almeno un impianto dalla lista a sinistra.", color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                            } else {
                                val quadriAttivi = impiantiAttivi.filterIsInstance<manutenzioni.domain.model.QuadroBT>()
                                val altriImpiantiAttivi = impiantiAttivi.filter { it !is manutenzioni.domain.model.QuadroBT }

                                if (quadriAttivi.isNotEmpty()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                        elevation = 0.dp,
                                        border = BorderStroke(1.dp, SlateBorder),
                                        shape = RoundedCornerShape(8.dp),
                                        backgroundColor = MaterialTheme.colors.surface
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = "Quadri Elettrici selezionati (${quadriAttivi.size})",
                                                fontWeight = FontWeight.Bold,
                                                style = MaterialTheme.typography.subtitle2,
                                                color = MaterialTheme.colors.primary
                                            )
                                            Text(
                                                text = "Imposta le periodicità per tutti i quadri attivi",
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.secondary
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))

                                            val frequenzeDisponibiliQuadri = quadriAttivi
                                                .flatMap { manutenzioni.domain.service.CrossPlantAttivitaResolver.resolveFrequenze(it, state.impiantiDelCantiere) }
                                                .distinct()
                                                .sortedBy { it.inMesi() }

                                            if (frequenzeDisponibiliQuadri.isEmpty()) {
                                                Text("Nessuna attività registrata per i quadri selezionati.", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                                            } else {
                                                frequenzeDisponibiliQuadri.forEach { periodo ->
                                                    val matchingCount = quadriAttivi.count { quadro ->
                                                        val selectedFreqs = state.frequenzeSelezionatePerImpianto[quadro.id]
                                                            ?: state.frequenzePerImpianto[quadro.id]?.let { setOf(it) }
                                                            ?: emptySet()
                                                        periodo in selectedFreqs
                                                    }
                                                    val triState = when {
                                                        matchingCount == 0 -> ToggleableState.Off
                                                        matchingCount == quadriAttivi.size -> ToggleableState.On
                                                        else -> ToggleableState.Indeterminate
                                                    }

                                                    val onToggleBulkFreq = {
                                                        if (onFrequenzaForImpiantiToggled != null) {
                                                            onFrequenzaForImpiantiToggled(quadriAttivi.map { it.id }, periodo)
                                                        } else {
                                                            quadriAttivi.forEach { onFrequenzaPerImpiantoToggled(it.id, periodo) }
                                                        }
                                                    }

                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clickable { onToggleBulkFreq() }
                                                            .padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        TriStateCheckbox(
                                                            state = triState,
                                                            onClick = onToggleBulkFreq,
                                                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = periodo.label(),
                                                            style = MaterialTheme.typography.body2
                                                        )
                                                        if (matchingCount > 0 && matchingCount < quadriAttivi.size) {
                                                            Spacer(modifier = Modifier.width(6.dp))
                                                            Text(
                                                                text = "($matchingCount/${quadriAttivi.size})",
                                                                style = MaterialTheme.typography.caption,
                                                                color = MaterialTheme.colors.secondary
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Divider()

                                            var expandPersonalizzaQuadri by remember { mutableStateOf(false) }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandPersonalizzaQuadri = !expandPersonalizzaQuadri }
                                                    .padding(top = 8.dp, bottom = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Personalizza singoli quadri (${quadriAttivi.size})",
                                                    style = MaterialTheme.typography.caption,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colors.primary
                                                )
                                                Icon(
                                                    imageVector = if (expandPersonalizzaQuadri) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colors.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            if (expandPersonalizzaQuadri) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(start = 12.dp, top = 4.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    quadriAttivi.forEach { quadro ->
                                                        val frequenzeDisp = manutenzioni.domain.service.CrossPlantAttivitaResolver.resolveFrequenze(quadro, state.impiantiDelCantiere)
                                                        val selectedFreqs = state.frequenzeSelezionatePerImpianto[quadro.id]
                                                            ?: state.frequenzePerImpianto[quadro.id]?.let { setOf(it) }
                                                            ?: emptySet()
                                                        val displayLabel = if (quadro.sigla.isNotBlank()) "${quadro.codIntervento} - ${quadro.sigla}" else quadro.nomeCompleto
                                                        Text(text = displayLabel, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.caption, modifier = Modifier.padding(top = 4.dp))
                                                        
                                                        frequenzeDisp.forEach { periodo ->
                                                            val isChecked = periodo in selectedFreqs
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .clickable { onFrequenzaPerImpiantoToggled(quadro.id, periodo) }
                                                                    .padding(vertical = 1.dp),
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Checkbox(
                                                                    checked = isChecked,
                                                                    onCheckedChange = { onFrequenzaPerImpiantoToggled(quadro.id, periodo) },
                                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                                                )
                                                                Spacer(modifier = Modifier.width(6.dp))
                                                                Text(
                                                                    text = periodo.label(),
                                                                    style = MaterialTheme.typography.caption
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                altriImpiantiAttivi.forEach { impianto ->
                                    val frequenzeDisp = manutenzioni.domain.service.CrossPlantAttivitaResolver.resolveFrequenze(impianto, state.impiantiDelCantiere)
                                    val selectedFreqs = state.frequenzeSelezionatePerImpianto[impianto.id]
                                        ?: state.frequenzePerImpianto[impianto.id]?.let { setOf(it) }
                                        ?: emptySet()
                                    val displayLabel = if (impianto is manutenzioni.domain.model.QuadroBT && impianto.sigla.isNotBlank()) "${impianto.codIntervento} - ${impianto.sigla}" else impianto.codIntervento
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

                                    if (impianto is manutenzioni.domain.model.ImpiantoEmergenza) {
                                        Text(
                                            text = "Sigla Documentale:",
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
                                            manutenzioni.domain.model.EmergenzaSiglaDoc.values().forEach { sigla ->
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.clickable {
                                                        onUpdateEmergenzaSiglaDocumentale?.invoke(impianto.id, sigla)
                                                    }
                                                ) {
                                                    RadioButton(
                                                        selected = impianto.siglaDocumentale == sigla,
                                                        onClick = { onUpdateEmergenzaSiglaDocumentale?.invoke(impianto.id, sigla) },
                                                        colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colors.primary),
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(Modifier.width(2.dp))
                                                    Text(sigla.displayLabel, fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }

                                    if (frequenzeDisp.isEmpty()) {
                                        Text("Nessuna attività registrata per questo modulo.", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.error)
                                    } else {
                                        frequenzeDisp.forEach { periodo ->
                                            val isChecked = periodo in selectedFreqs
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onFrequenzaPerImpiantoToggled(impianto.id, periodo) }
                                                    .padding(vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isChecked,
                                                    onCheckedChange = { onFrequenzaPerImpiantoToggled(impianto.id, periodo) },
                                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = periodo.label(),
                                                    style = MaterialTheme.typography.body2
                                                )
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
                            val isEnabled = impiantiAttiviIdsForGen.isNotEmpty() && impiantiAttiviIdsForGen.all { id ->
                                !state.frequenzeSelezionatePerImpianto[id].isNullOrEmpty() || state.frequenzePerImpianto.containsKey(id)
                            }
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
}

/**
 * Tab dedicata in Cantiere per visualizzare tutti gli interruttori differenziali
 * aggregati dai vari Quadri BT del cantiere e stamparne il PDF unico (R11).
 */
@Composable
private fun CantiereElencoInterruttoriTab(
    quadri: List<manutenzioni.domain.model.QuadroBT>,
    isLoading: Boolean,
    onExportPdf: () -> Unit,
    onEditQuadro: (manutenzioni.domain.model.QuadroBT) -> Unit,
    modifier: Modifier = Modifier
) {
    val provider = remember { manutenzioni.app.service.attachments.QuadroBtAttachmentProvider() }
    val quadriConInterruttori = quadri.filter { it.listaInterruttori.isNotEmpty() }
    val totRigheSrotolate = quadriConInterruttori.sumOf { q -> q.listaInterruttori.sumOf { it.quantita.coerceAtLeast(1) } }

    Card(
        modifier = modifier,
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Elenco Interruttori Differenziali del Cantiere",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${quadri.size} quadri elettrici nel cantiere • $totRigheSrotolate interruttori registrati",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
                Button(
                    onClick = onExportPdf,
                    enabled = totRigheSrotolate > 0 && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Stampa PDF Elenco Interruttori", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (quadriConInterruttori.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nessun interruttore registrato nei quadri di questo cantiere.",
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Apri un Quadro Elettrico (Q) dalla tab 'Generazione schede di Verifica' per aggiungere gli interruttori.",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            } else {
                // Header tabella
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Quadro", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Sigla Circuito", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Componente / Articolo", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2.2f))
                    Text("Taratura Diff.", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    Text("Q.tà", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                    Text("Azioni", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                }
                Divider(color = SlateBorder)

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quadriConInterruttori.forEach { quadro ->
                        val siglaQuadro = if (quadro.sigla.isNotBlank()) quadro.sigla else quadro.codIntervento
                        quadro.listaInterruttori.forEachIndexed { idx, interruttore ->
                            val datiComp = buildString {
                                if (!interruttore.produttore.isNullOrBlank()) append(interruttore.produttore).append(" ")
                                if (!interruttore.codiceArticolo.isNullOrBlank()) append(interruttore.codiceArticolo).append(" — ")
                                append(interruttore.nome)
                            }
                            val taratura = provider.extractTaratura(interruttore).ifBlank { "—" }
                            val siglaCircuito = interruttore.siglaCircuito?.takeIf { it.isNotBlank() } ?: "QF${idx + 1}"

                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(siglaQuadro, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(siglaCircuito, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                Text(datiComp, fontSize = 12.sp, modifier = Modifier.weight(2.2f))
                                Text(taratura, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                                Text("${interruttore.quantita}", fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                                TextButton(
                                    onClick = { onEditQuadro(quadro) },
                                    modifier = Modifier.weight(0.8f)
                                ) {
                                    Text("Modifica", fontSize = 11.sp)
                                }
                            }
                            Divider(color = SlateBorder.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}
