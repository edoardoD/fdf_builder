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
import manutenzioni.app.data.Cliente
import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import java.util.UUID

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
    onClienteSelected: (Cliente) -> Unit,
    onAddCliente: (Cliente) -> Unit,
    onImpiantoSelected: (Impianto) -> Unit,
    onFrequenzaSelected: (Periodo) -> Unit,
    onGeneraPdf: () -> Unit,
    onOpenPdf: () -> Unit,
    onViewModeChanged: (ViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    // Flag locale per evidenziare errore selezione cliente
    var showClienteError by remember { mutableStateOf(false) }
    // Flag per mostrare il Dialog di creazione cliente
    var showNuovoClienteDialog by remember { mutableStateOf(false) }

    // Dialog per inserimento nuovo cliente
    if (showNuovoClienteDialog) {
        NuovoClienteDialog(
            onDismiss = { showNuovoClienteDialog = false },
            onConfirm = { cliente ->
                onAddCliente(cliente)
                showNuovoClienteDialog = false
                showClienteError = false
            }
        )
    }

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

        // === Selezione Cliente ===
        Text(
            text = "Cliente",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold
        )
        ClienteDropdown(
            clienti = uiState.clienti,
            selected = uiState.selectedCliente,
            showError = showClienteError && uiState.selectedCliente == null,
            onSelected = { cliente ->
                onClienteSelected(cliente)
                showClienteError = false
            },
            onAddNew = { showNuovoClienteDialog = true }
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
            onClick = {
                if (uiState.selectedCliente == null) {
                    showClienteError = true
                } else {
                    onGeneraPdf()
                }
            },
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

/**
 * Dropdown per la selezione del cliente.
 * La prima voce è sempre "➕ Aggiungi Nuovo Cliente".
 * Se showError = true, il bordo diventa rosso.
 */
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun ClienteDropdown(
    clienti: List<Cliente>,
    selected: Cliente?,
    showError: Boolean,
    onSelected: (Cliente) -> Unit,
    onAddNew: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selected?.nome ?: "",
            onValueChange = {},
            readOnly = true,
            placeholder = { Text("Seleziona cliente...", fontSize = 12.sp) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
            singleLine = true,
            isError = showError,
            colors = TextFieldDefaults.outlinedTextFieldColors(
                errorBorderColor = Color(0xFFD32F2F)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Prima voce: Aggiungi Nuovo Cliente
            DropdownMenuItem(onClick = {
                expanded = false
                onAddNew()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colors.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "➕ Aggiungi Nuovo Cliente",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }
            }

            if (clienti.isNotEmpty()) {
                Divider()
            }

            clienti.forEach { cliente ->
                DropdownMenuItem(onClick = {
                    onSelected(cliente)
                    expanded = false
                }) {
                    Column {
                        Text(
                            text = cliente.nome,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (!cliente.partitaIva.isNullOrBlank()) {
                            Text(
                                text = "P.IVA: ${cliente.partitaIva}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }

    // Messaggio errore sotto il campo
    if (showError) {
        Text(
            text = "Seleziona un cliente prima di generare il PDF",
            color = Color(0xFFD32F2F),
            fontSize = 10.sp,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
        )
    }
}

/**
 * Dialog per l'inserimento rapido di un nuovo cliente.
 * Campi: Nome (obbligatorio), Indirizzo, Partita IVA.
 */
@Composable
private fun NuovoClienteDialog(
    onDismiss: () -> Unit,
    onConfirm: (Cliente) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var indirizzo by remember { mutableStateOf("") }
    var partitaIva by remember { mutableStateOf("") }
    var nomeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Nuovo Cliente",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = {
                        nome = it
                        nomeError = false
                    },
                    label = { Text("Nome *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nomeError,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                if (nomeError) {
                    Text(
                        text = "Il nome è obbligatorio",
                        color = Color(0xFFD32F2F),
                        fontSize = 10.sp
                    )
                }
                OutlinedTextField(
                    value = indirizzo,
                    onValueChange = { indirizzo = it },
                    label = { Text("Indirizzo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
                OutlinedTextField(
                    value = partitaIva,
                    onValueChange = { partitaIva = it },
                    label = { Text("Partita IVA") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (nome.isBlank()) {
                        nomeError = true
                    } else {
                        onConfirm(
                            Cliente(
                                id = UUID.randomUUID().toString(),
                                nome = nome.trim(),
                                indirizzo = indirizzo.trim().ifBlank { null },
                                partitaIva = partitaIva.trim().ifBlank { null }
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}
