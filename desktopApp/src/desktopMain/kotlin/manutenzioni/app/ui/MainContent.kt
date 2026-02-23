package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
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
import manutenzioni.app.data.Impianto

/**
 * Area principale (75%) con stato duale:
 * - PDF_PREVIEW: mostra info sul PDF generato
 * - IMPIANTO_EDITOR: mostra l'editor dell'impianto selezionato
 */
@Composable
fun MainContent(
    uiState: ManutenzioniUiState,
    onSaveImpianto: (Impianto) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> {
            Box(modifier = modifier, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text("Elaborazione in corso...", color = Color.Gray)
                }
            }
        }

        uiState.selectedImpianto == null -> {
            WelcomeScreen(modifier)
        }

        uiState.viewMode == ViewMode.PDF_PREVIEW -> {
            PdfPreviewPanel(uiState, modifier)
        }

        uiState.viewMode == ViewMode.IMPIANTO_EDITOR -> {
            ImpiantoEditor(
                impianto = uiState.selectedImpianto,
                onSave = onSaveImpianto,
                modifier = modifier
            )
        }
    }
}

/**
 * Schermata di benvenuto quando nessun impianto è selezionato
 */
@Composable
private fun WelcomeScreen(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Card(
            elevation = 4.dp,
            backgroundColor = Color(0xFFF0F4FA)
        ) {
            Column(
                modifier = Modifier.padding(40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Default.Build,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colors.primary
                )
                Text(
                    text = "Manutenzioni Maker",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Seleziona un impianto dalla sidebar per iniziare",
                    color = Color.Gray
                )
                Divider(modifier = Modifier.width(200.dp))
                Text(
                    text = "1. Scegli l'impianto\n2. Seleziona la frequenza\n3. Genera il PDF compilabile",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

/**
 * Pannello anteprima PDF — mostra le informazioni sul file generato
 * e permette di aprirlo nel viewer di sistema.
 */
@Composable
private fun PdfPreviewPanel(uiState: ManutenzioniUiState, modifier: Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header con info impianto
        uiState.selectedImpianto?.let { impianto ->
            Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "${impianto.codIntervento} — ${impianto.nomeCompleto}",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    if (!impianto.premessa.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = impianto.premessa,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Info frequenza e attività
        if (uiState.selectedFrequenza != null && uiState.selectedImpianto != null) {
            val mesiTarget = uiState.selectedFrequenza.inMesi()
            val attivitaIncluse = uiState.selectedImpianto.listaAttivita
                .filter { mesiTarget % it.frequenza.inMesi() == 0 }
                .sortedWith(compareBy({ it.frequenza.inMesi() }, { it.nAttivita }))

            Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Frequenza: ${uiState.selectedFrequenza.label()} — ${attivitaIncluse.size} attività",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))

                    // Tabella attività
                    attivitaIncluse.forEach { att ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${att.nAttivita}.",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                text = att.frequenza.label(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.width(60.dp)
                            )
                            Text(
                                text = att.descrizione ?: "",
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Card PDF generato
        uiState.pdfFile?.let { file ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFE8F5E9),
                elevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(32.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "PDF Generato con successo",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2E7D32)
                        )
                        Text(
                            text = file.absolutePath,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "Dimensione: ${file.length() / 1024} KB",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }

        // Normative
        uiState.selectedImpianto?.let { impianto ->
            if (impianto.listaNormative.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Normative di riferimento",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        impianto.listaNormative.forEach { norm ->
                            Text(
                                text = "• ${norm.codNormativa}: ${norm.descrizione}",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }
            }
        }
    }
}

