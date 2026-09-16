package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import manutenzioni.app.ui.features.amministrazione.AdminDashboardScreen
import manutenzioni.app.ui.features.operativita.CantiereDetailScreen
import manutenzioni.app.ui.features.operativita.OperativitaScreen
import manutenzioni.app.ui.layout.MainScaffold
import manutenzioni.app.ui.theme.ManutenzioniTheme

@Composable
fun App(viewModel: ManutenzioniViewModel, onDisconnect: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsState()

    ManutenzioniTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra di stato superiore (mantenuta per feedback operativo)
            StatusBar(uiState)

            MainScaffold(
                currentSection = uiState.currentSection,
                onSectionSelected = viewModel::setSection
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
                                onEditImpianto = { impianto -> viewModel.selectImpianto(impianto) },
                                onSaveImpianto = viewModel::saveImpianto,
                                onFrequenzaPerImpiantoSelected = viewModel::updateFrequenzaForImpianto,
                                onGeneraPdf = viewModel::generatePdf,
                                onOpenPdf = viewModel::openPdfInSystem,
                                onCreateNewImpianto = viewModel::createNewImpianto,
                                onDeleteImpianto = viewModel::deleteImpianto,
                                onSearchComponenti = viewModel::searchCandidateComponents,
                                onClearSearchComponenti = viewModel::clearCandidateComponents,
                                onApprovaComponente = viewModel::approvaEAssegnaComponenteAQuadro,
                                onSostituisciProduttore = viewModel::sostituisciProduttoreComponente
                            )
                        }
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
 * Barra di stato con messaggio corrente + indicatore errore/caricamento
 */
@Composable
private fun StatusBar(uiState: ManutenzioniUiState) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (uiState.errorMessage != null) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = uiState.errorMessage ?: uiState.statusMessage,
                color = if (uiState.errorMessage != null) Color(0xFFD32F2F) else Color(0xFF2E7D32),
                style = MaterialTheme.typography.body2
            )
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            }
        }
    }
}
