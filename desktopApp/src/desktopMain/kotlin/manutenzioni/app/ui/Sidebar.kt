package manutenzioni.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import manutenzioni.app.data.Periodo

/**
 * Sidebar di selezione — 25% della larghezza.
 *
 * Contiene:
 * - Dropdown selezione Impianto
 * - Dropdown selezione Frequenza (abilitato solo dopo selezione impianto)
 * - Bottoni azione (Genera PDF, Apri PDF)
 * - Toggle vista (Anteprima / Editor)
 */
@Composable
fun Sidebar(
    uiState: ManutenzioniUiState,
    onImpiantoSelected: (Impianto) -> Unit,
    onFrequenzaSelected: (Periodo) -> Unit,
    onGeneraPdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onViewModeChanged: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Titolo
        Text(
            text = "Manutenzioni Maker",
            style = MaterialTheme.typography.h6.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = MaterialTheme.colors.primary
        )

        Divider()

        // === Selezione Impianto ===
        Text(
            text = "Impianto",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold
        )
        ImpiantoDropdown(
            impianti = uiState.impianti,
            selected = uiState.selectedImpianto,
            onSelected = onImpiantoSelected
        )

        // === Selezione Frequenza ===
        Text(
            text = "Frequenza",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold
        )
        FrequenzaDropdown(
            frequenze = uiState.frequenzeDisponibili,
            selected = uiState.selectedFrequenza,
            enabled = uiState.selectedImpianto != null,
            onSelected = onFrequenzaSelected
        )

        Divider()

        // === Info attività filtrate ===
        if (uiState.selectedImpianto != null && uiState.selectedFrequenza != null) {
            val mesiTarget = uiState.selectedFrequenza.inMesi()
            val count = uiState.selectedImpianto.listaAttivita
                .count { mesiTarget % it.frequenza.inMesi() == 0 }
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = Color(0xFFE3F2FD),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "$count attività incluse",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Frequenza ${uiState.selectedFrequenza.label()} include tutte le attività con periodo divisore",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // === Bottoni Azione ===
        Button(
            onClick = onGeneraPdf,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedImpianto != null
                    && uiState.selectedFrequenza != null
                    && !uiState.isLoading,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = MaterialTheme.colors.primary,
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Genera PDF")
        }

        // Bottone Apri PDF (visibile solo se c'è un file generato)
        if (uiState.pdfFile != null) {
            OutlinedButton(
                onClick = onOpenPdf,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apri nel Viewer")
            }
        }

        Divider()

        // === Toggle Vista ===
        Text(
            text = "Vista",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.viewMode == ViewMode.PDF_PREVIEW,
                onClick = { onViewModeChanged(ViewMode.PDF_PREVIEW) }
            )
            Text("Anteprima", modifier = Modifier.clickable { onViewModeChanged(ViewMode.PDF_PREVIEW) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(
                selected = uiState.viewMode == ViewMode.IMPIANTO_EDITOR,
                onClick = { onViewModeChanged(ViewMode.IMPIANTO_EDITOR) }
            )
            Text("Editor Impianto", modifier = Modifier.clickable { onViewModeChanged(ViewMode.IMPIANTO_EDITOR) })
        }
    }
}

/**
 * Dropdown per la selezione dell'impianto
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ImpiantoDropdown(
    impianti: List<Impianto>,
    selected: Impianto?,
    onSelected: (Impianto) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.let { "${it.codIntervento} — ${it.nomeCompleto}" } ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Seleziona impianto...", fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            impianti.forEach { impianto ->
                DropdownMenuItem(onClick = {
                    onSelected(impianto)
                    expanded = false
                }) {
                    Column {
                        Text(
                            text = "${impianto.codIntervento} — ${impianto.nomeCompleto}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${impianto.listaAttivita.size} attività",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dropdown per la selezione della frequenza
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun FrequenzaDropdown(
    frequenze: List<Periodo>,
    selected: Periodo?,
    enabled: Boolean,
    onSelected: (Periodo) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.label() ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            placeholder = {
                Text(
                    if (enabled) "Seleziona frequenza..." else "Prima seleziona un impianto",
                    fontSize = 12.sp
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = true
        )

        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            frequenze.forEach { freq ->
                DropdownMenuItem(onClick = {
                    onSelected(freq)
                    expanded = false
                }) {
                    Text(text = freq.label(), fontSize = 12.sp)
                }
            }
        }
    }
}

