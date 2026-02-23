package manutenzioni.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Composable root dell'applicazione.
 *
 * Layout: Sidebar (25%) + Area Principale (75%)
 */
@Composable
fun App(viewModel: ManutenzioniViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    MaterialTheme(
        colors = lightColors(
            primary = Color(0xFF3366FF),
            primaryVariant = Color(0xFF1A3DB8),
            secondary = Color(0xFFB4C6E7),
            surface = Color.White,
            background = Color(0xFFF5F5F5),
            error = Color(0xFFD32F2F)
        )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Barra di stato superiore
            StatusBar(uiState)

            Row(modifier = Modifier.fillMaxSize()) {
                // Sidebar — 25%
                Sidebar(
                    uiState = uiState,
                    onImpiantoSelected = viewModel::selectImpianto,
                    onFrequenzaSelected = viewModel::selectFrequenza,
                    onGeneraPdf = viewModel::generatePdf,
                    onOpenPdf = viewModel::openPdfInSystem,
                    onViewModeChanged = viewModel::setViewMode,
                    modifier = Modifier
                        .fillMaxWidth(0.25f)
                        .fillMaxHeight()
                        .background(Color(0xFFF0F4FA))
                        .padding(12.dp)
                )

                // Divider verticale
                Divider(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp),
                    color = Color(0xFFDDDDDD)
                )

                // Area principale — 75%
                MainContent(
                    uiState = uiState,
                    onSaveImpianto = viewModel::saveImpianto,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                )
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

