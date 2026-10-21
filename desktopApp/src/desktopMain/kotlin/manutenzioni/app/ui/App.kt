package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import manutenzioni.app.ui.features.amministrazione.AdminDashboardScreen
import manutenzioni.app.ui.features.calendario.CalendarioGlobaleScreen
import manutenzioni.app.ui.features.operativita.CantiereDetailScreen
import manutenzioni.app.ui.features.operativita.OperativitaScreen
import manutenzioni.app.ui.layout.MainScaffold
import manutenzioni.app.ui.theme.ManutenzioniTheme

@Composable
fun App(viewModel: ManutenzioniViewModel, onDisconnect: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    ManutenzioniTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra di stato superiore (con selettore rapido profilo RBAC)
            StatusBar(
                uiState = uiState,
                onSelectUtenteAttivo = viewModel::selectUtenteAttivo
            )

            MainScaffold(
                currentSection = uiState.currentSection,
                onSectionSelected = viewModel::setSection,
                isAdmin = uiState.isAdmin
            ) {
                when (uiState.currentSection) {
                    AppSection.OPERATIVITA -> {
                        if (uiState.selectedCantiere == null) {
                            OperativitaScreen(
                                state = uiState,
                                onClienteSelected = viewModel::selectCliente,
                                onCantiereSelected = viewModel::selectCantiere,
                                onAddCliente = viewModel::addCliente,
                                onAddCantiere = viewModel::addCantiere
                            )
                        } else {
                            CantiereDetailScreen(
                                state = uiState,
                                onBack = { viewModel.selectCantiere(null) },
                                onImpiantoSelectionChanged = viewModel::toggleImpiantoSelection,
                                onImpiantiSelectionChanged = viewModel::toggleImpiantiSelection,
                                onEditImpianto = { impianto -> viewModel.selectImpianto(impianto) },
                                onSaveImpianto = viewModel::saveImpianto,
                                onFrequenzaPerImpiantoSelected = viewModel::updateFrequenzaForImpianto,
                                onFrequenzaPerImpiantoToggled = viewModel::toggleFrequenzaForImpianto,
                                onFrequenzaForImpiantiToggled = viewModel::toggleFrequenzaForImpianti,
                                onGeneraPdf = viewModel::generatePdf,
                                onOpenPdf = viewModel::openPdfInSystem,
                                onCreateNewImpianto = viewModel::createNewImpianto,
                                onDeleteImpianto = viewModel::deleteImpianto,
                                onSearchComponenti = viewModel::searchCandidateComponents,
                                onClearSearchComponenti = viewModel::clearCandidateComponents,
                                onApprovaComponente = viewModel::approvaEAssegnaComponenteAQuadro,
                                onAggiungiComponenteAQuadro = viewModel::aggiungiInterruttoreAQuadro,
                                onSostituisciProduttore = viewModel::sostituisciProduttoreComponente,
                                onAggiungiLampadaAEmergenza = viewModel::aggiungiLampadaAEmergenza,
                                onAggiungiLampadeAEmergenza = viewModel::aggiungiLampadeAEmergenza,
                                onAggiornaLampadaInEmergenza = viewModel::aggiornaLampadaInEmergenza,
                                onExportStandaloneTechnicalSheet = viewModel::exportStandaloneTechnicalSheet,
                                onExportCantiereInterruttoriPdf = { viewModel.exportCantiereInterruttoriPdf() },
                                onUpdateDatiIntervento = viewModel::updateDatiInterventoCantiere,
                                onSelectFirmaManutentore = viewModel::selectFirmaManutentoreFile,
                                onRemoveFirmaManutentore = { viewModel.updateFirmaManutentoreCantiere(null) },
                                onUpdateMqtSwitchType = viewModel::updateMqtSwitchType,
                                onUpdateEmergenzaSiglaDocumentale = viewModel::updateEmergenzaSiglaDocumentale,
                                onAddImpiantiFromTemplates = viewModel::addImpiantiFromTemplates,
                                onOpenSessionePdfFile = viewModel::openSessionePdfFile,
                                onRestoreSessioneVerifica = viewModel::ripristinaDatiDaSessione,
                                onDeleteSessioneVerifica = viewModel::deleteSessioneVerifica
                            )
                        }
                    }
                    AppSection.CALENDARIO -> {
                        CalendarioGlobaleScreen(
                            state = uiState,
                            onOrizzonteChanged = viewModel::setOrizzonteScadenze,
                            onSelectMese = viewModel::selectMeseCalendarioGlobale,
                            onApriCantiereDaCalendario = viewModel::apriCantiereDaCalendario,
                            onExportRiepilogoCantierePdf = { viewModel.exportRiepilogoDaCalendarioGlobale(it) },
                            onDateSelected = viewModel::selezionaGiornoCalendario,
                            onPreviousMonth = { viewModel.cambiaMeseCalendario(-1) },
                            onNextMonth = { viewModel.cambiaMeseCalendario(1) },
                            onTodayClicked = viewModel::vaiAlMeseCorrente,
                            onPianificaInterventiBatch = { data, cantieri, impIds, impCod, freq, man, note, ric ->
                                viewModel.pianificaInterventiBatch(data, cantieri, impIds, impCod, freq, man, note, ric)
                            },
                            onAvviaInterventoDaCalendario = viewModel::avviaInterventoDaCalendario,
                            onCompletaEventoManutenzione = { viewModel.completaEventoManutenzione(it) },
                            onEliminaEventoManutenzione = { id, serie -> viewModel.eliminaEventoManutenzione(id, serie) }
                        )
                    }
                    AppSection.DATABASE -> {
                        AdminDashboardScreen(
                            state = uiState,
                            viewModel = viewModel,
                            onTabSelected = viewModel::setAdminTab,
                            onDisconnect = onDisconnect
                        )
                    }
                }
            }
        }
    }
}

/**
 * Barra di stato con messaggio corrente, indicatore errore/caricamento e selettore rapido profilo RBAC (R31).
 */
@Composable
private fun StatusBar(
    uiState: ManutenzioniUiState,
    onSelectUtenteAttivo: (manutenzioni.domain.model.Utente) -> Unit = {}
) {
    var expandedUserMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (uiState.errorMessage != null) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = uiState.errorMessage ?: uiState.statusMessage,
                color = if (uiState.errorMessage != null) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                style = MaterialTheme.typography.body2,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                }
                Box {
                    TextButton(
                        onClick = { expandedUserMenu = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "👤 ${uiState.utenteAttivo.nomeCompleto} [${uiState.utenteAttivo.ruolo.label}]",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.primary
                        )
                    }
                    DropdownMenu(
                        expanded = expandedUserMenu,
                        onDismissRequest = { expandedUserMenu = false }
                    ) {
                        uiState.utenti.forEach { utente ->
                            DropdownMenuItem(onClick = {
                                onSelectUtenteAttivo(utente)
                                expandedUserMenu = false
                            }) {
                                Text("${utente.nomeCompleto} (${utente.ruolo.label})")
                            }
                        }
                    }
                }
            }
        }
    }
}
