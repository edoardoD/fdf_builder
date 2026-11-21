package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.app.ui.features.operativita.components.CantiereActionBar
import manutenzioni.app.ui.features.operativita.components.CantiereSessionHeaderCard
import manutenzioni.app.ui.features.operativita.editor.ImpiantoEditor
import manutenzioni.app.ui.features.operativita.tabs.CantiereElencoInterruttoriTab
import manutenzioni.app.ui.features.operativita.tabs.CantiereImpiantiTab
import manutenzioni.domain.model.*

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
    onAggiungiComponenteAQuadro: ((QuadroBT, InterruttoreBT) -> Unit)? = null,
    onSostituisciProduttore: ((QuadroBT, String, VarianteProdotto) -> Unit)? = null,
    onAggiungiLampadaAEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onAggiungiLampadeAEmergenza: ((ImpiantoEmergenza, List<LampadaEmergenza>) -> Unit)? = null,
    onAggiornaLampadaInEmergenza: ((ImpiantoEmergenza, LampadaEmergenza) -> Unit)? = null,
    onAggiungiDispositiviAdAntincendio: ((RilevazioneAntincendio, List<DispositivoRilevazione>) -> Unit)? = null,
    onAggiornaDispositivoInAntincendio: ((RilevazioneAntincendio, DispositivoRilevazione) -> Unit)? = null,
    onDismissAntincendioLoopTrigger: () -> Unit = {},
    onExportStandaloneTechnicalSheet: ((Impianto) -> Unit)? = null,
    onExportCantiereInterruttoriPdf: () -> Unit = {},
    onUpdateDatiIntervento: (String?, String?, String?, String?) -> Unit = { _, _, _, _ -> },
    onFrequenzaForImpiantiToggled: ((List<String>, Periodo) -> Unit)? = null,
    onImpiantiSelectionChanged: ((List<String>, Boolean) -> Unit)? = null,
    onSelectFirmaManutentore: () -> Unit = {},
    onRemoveFirmaManutentore: () -> Unit = {},
    onUpdateMqtSwitchType: ((String, MqtSwitchType) -> Unit)? = null,
    onUpdateEmergenzaSiglaDocumentale: ((String, EmergenzaSiglaDoc) -> Unit)? = null,
    onAddImpiantiFromTemplates: (List<Impianto>) -> Unit = { templates -> templates.forEach { onCreateNewImpianto(it) } },
    onOpenSessionePdfFile: (String) -> Unit = {},
    onRestoreSessioneVerifica: (SessioneVerifica) -> Unit = {},
    onDeleteSessioneVerifica: (String) -> Unit = {}
) {
    var impiantoInModifica by remember { mutableStateOf<Impianto?>(null) }
    var showNuovoImpiantoDialog by remember { mutableStateOf(false) }
    var showDeleteImpiantoDialog by remember { mutableStateOf(false) }
    var impiantoInEliminazione by remember { mutableStateOf<Impianto?>(null) }
    var showMassiveQuadroDialog by remember { mutableStateOf(false) }
    var selectedQuadroTemplate by remember { mutableStateOf<Impianto?>(null) }
    var selectedCantiereTab by remember { mutableStateOf(0) }

    if (impiantoInModifica != null) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp)) {
            Card(elevation = 8.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
                ImpiantoEditor(
                    impianto = impiantoInModifica!!,
                    componentiStandard = state.componentiStandard,
                    isReadOnlyAdminFields = true,
                    catalogoApprovato = state.catalogoApprovato,
                    onAggiungiComponenteAQuadro = onAggiungiComponenteAQuadro,
                    onSostituisciProduttore = onSostituisciProduttore,
                    onAggiungiLampadaAEmergenza = onAggiungiLampadaAEmergenza,
                    onAggiungiLampadeAEmergenza = onAggiungiLampadeAEmergenza,
                    onAggiornaLampadaInEmergenza = onAggiornaLampadaInEmergenza,
                    onAggiungiDispositiviAdAntincendio = onAggiungiDispositiviAdAntincendio,
                    onAggiornaDispositivoInAntincendio = onAggiornaDispositivoInAntincendio,
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
        return
    }

    if (showNuovoImpiantoDialog) {
        NuovoImpiantoDialog(
            impiantiGlobali = state.impiantiGlobali,
            onDismiss = { showNuovoImpiantoDialog = false },
            onConfirmMultiple = { templates ->
                val (quadri, altri) = templates.partition { it.requiresMassiveCreation || it.isQuadro }
                if (altri.isNotEmpty()) onAddImpiantiFromTemplates(altri)
                showNuovoImpiantoDialog = false
                quadri.firstOrNull()?.let { quadroTemplate ->
                    selectedQuadroTemplate = quadroTemplate
                    showMassiveQuadroDialog = true
                }
            },
            onCreateEmpty = {
                showNuovoImpiantoDialog = false
                impiantoInModifica = ImpiantoStandard(
                    codIntervento = "",
                    nomeCompleto = "",
                    cantiereId = state.selectedCantiere?.id
                )
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
            interruttoriDisponibili = state.componentiStandard.filter { it.tipo == TipoComponenteStandard.INTERRUTTORE_BT }.map { InterruttoreBT(nome = it.nome) },
            onDismiss = { showMassiveQuadroDialog = false },
            onSave = { nuoviQuadri ->
                nuoviQuadri.forEach { onSaveImpianto(it) }
                showMassiveQuadroDialog = false
            }
        )
    }

    val pendingRiTrigger = state.pendingAntincendioLoopTrigger
        ?.takeIf { it.cantiereId == state.selectedCantiere?.id }
    if (!showMassiveQuadroDialog && pendingRiTrigger != null) {
        MassiveDispositivoAntincendioDialog(
            dispositiviEsistenti = pendingRiTrigger.listaDispositivi,
            cantiereNome = state.selectedCantiere?.nome,
            onDismiss = { onDismissAntincendioLoopTrigger() },
            onConfirmBatch = { nuovi ->
                if (onAggiungiDispositiviAdAntincendio != null) {
                    onAggiungiDispositiviAdAntincendio.invoke(pendingRiTrigger, nuovi)
                } else {
                    onSaveImpianto(pendingRiTrigger.copy(listaDispositivi = pendingRiTrigger.listaDispositivi + nuovi))
                }
                onDismissAntincendioLoopTrigger()
            }
        )
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableHeight = maxHeight
        val screenScrollState = rememberScrollState()
        var topSectionHeightPx by remember { mutableStateOf(0) }
        val density = LocalDensity.current
        val topSectionHeightDp = with(density) { topSectionHeightPx.toDp() }
        val effectiveTopSpace = if (topSectionHeightDp > 0.dp) topSectionHeightDp else 390.dp
        val tabContentHeight = maxOf(580.dp, availableHeight - effectiveTopSpace - 48.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(screenScrollState)
                    .padding(start = 24.dp, top = 24.dp, end = 32.dp, bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coordinates ->
                            topSectionHeightPx = coordinates.size.height
                        }
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

                    val cantiere = state.selectedCantiere
                    CantiereSessionHeaderCard(
                        dataInizioContratto = cantiere?.dataInizioContratto.orEmpty(),
                        dataInterventoPrecedente = cantiere?.dataInterventoPrecedente.orEmpty(),
                        dataIntervento = cantiere?.dataIntervento.orEmpty(),
                        nomeManutentore = cantiere?.nomeManutentore.orEmpty(),
                        hasFirmaManutentore = !cantiere?.firmaManutentore.isNullOrBlank(),
                        onDataInizioContrattoChange = {
                            onUpdateDatiIntervento(it, cantiere?.dataIntervento, cantiere?.dataInterventoPrecedente, cantiere?.nomeManutentore)
                        },
                        onDataInterventoPrecedenteChange = {
                            onUpdateDatiIntervento(cantiere?.dataInizioContratto, cantiere?.dataIntervento, it, cantiere?.nomeManutentore)
                        },
                        onDataInterventoChange = {
                            onUpdateDatiIntervento(cantiere?.dataInizioContratto, it, cantiere?.dataInterventoPrecedente, cantiere?.nomeManutentore)
                        },
                        onNomeManutentoreChange = {
                            onUpdateDatiIntervento(cantiere?.dataInizioContratto, cantiere?.dataIntervento, cantiere?.dataInterventoPrecedente, it)
                        },
                        onSelectFirmaManutentore = onSelectFirmaManutentore,
                        onRemoveFirmaManutentore = onRemoveFirmaManutentore
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    val quadriDelCantiere = state.impiantiDelCantiere.filterIsInstance<QuadroBT>()
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
                        Tab(
                            selected = selectedCantiereTab == 2,
                            onClick = { selectedCantiereTab = 2 },
                            text = {
                                Text(
                                    "Storico Verifiche (${state.sessioniVerificaCantiere.size})",
                                    fontWeight = if (selectedCantiereTab == 2) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                val quadriDelCantiere = state.impiantiDelCantiere.filterIsInstance<QuadroBT>()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(tabContentHeight)
                ) {
                    when (selectedCantiereTab) {
                        1 -> {
                            CantiereElencoInterruttoriTab(
                                quadri = quadriDelCantiere,
                                isLoading = state.isLoading,
                                onExportPdf = onExportCantiereInterruttoriPdf,
                                onEditQuadro = { quadro ->
                                    impiantoInModifica = quadro
                                    onEditImpianto(quadro)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        2 -> {
                            StoricoVerificheTab(
                                sessioni = state.sessioniVerificaCantiere,
                                isAdmin = state.isAdmin,
                                onOpenPdfFile = onOpenSessionePdfFile,
                                onRestoreSessione = onRestoreSessioneVerifica,
                                onDeleteSessione = onDeleteSessioneVerifica,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            val impiantiAttiviIdsForGen = state.impiantiSelezionati.filter { it.value }.keys
                            val isGenEnabled = impiantiAttiviIdsForGen.isNotEmpty() && impiantiAttiviIdsForGen.all { id ->
                                !state.frequenzeSelezionatePerImpianto[id].isNullOrEmpty() || state.frequenzePerImpianto.containsKey(id)
                            }
                            CantiereImpiantiTab(
                                impiantiDelCantiere = state.impiantiDelCantiere,
                                impiantiSelezionati = state.impiantiSelezionati,
                                frequenzePerImpianto = state.frequenzePerImpianto,
                                frequenzeSelezionatePerImpianto = state.frequenzeSelezionatePerImpianto,
                                frequenzeDisponibiliMap = state.frequenzeDisponibiliPerImpianto,
                                isAdmin = state.isAdmin,
                                onImpiantoSelectionChanged = onImpiantoSelectionChanged,
                                onImpiantiSelectionChanged = onImpiantiSelectionChanged,
                                onEditImpianto = { impianto ->
                                    impiantoInModifica = impianto
                                    onEditImpianto(impianto)
                                },
                                onDeleteImpianto = { impianto ->
                                    impiantoInEliminazione = impianto
                                    showDeleteImpiantoDialog = true
                                },
                                onAddImpiantoClick = { showNuovoImpiantoDialog = true },
                                onFrequenzaPerImpiantoToggled = onFrequenzaPerImpiantoToggled,
                                onFrequenzaForImpiantiToggled = onFrequenzaForImpiantiToggled,
                                onUpdateMqtSwitchType = onUpdateMqtSwitchType,
                                onUpdateEmergenzaSiglaDocumentale = onUpdateEmergenzaSiglaDocumentale,
                                actionBarSlot = {
                                    CantiereActionBar(
                                        isEnabled = isGenEnabled,
                                        isLoading = state.isLoading,
                                        batchProgress = state.batchProgress,
                                        pdfFile = state.pdfFile,
                                        onGeneraPdf = onGeneraPdf,
                                        onOpenPdf = onOpenPdf
                                    )
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(screenScrollState),
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(vertical = 4.dp, horizontal = 2.dp)
        )
    }
}
