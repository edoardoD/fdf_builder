package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import manutenzioni.app.data.*

/**
 * Editor universale per impianti — gestisce sia creazione che modifica.
 *
 * Permette di editare:
 * - Codice intervento, nome completo, premessa
 * - Lista attività con tipo, descrizione e frequenza
 *
 * Se l'impianto è nuovo (codIntervento blank), mostra un titolo adeguato
 * e valida i campi obbligatori prima del salvataggio.
 *
 * @param impianto L'impianto da editare (può essere vuoto per creazione)
 * @param isNew true se l'impianto è in fase di creazione (non ancora persistito)
 * @param onSave Callback per il salvataggio (validato)
 */
@Composable
fun ImpiantoEditor(
    impianto: Impianto,
    isNew: Boolean = impianto.codIntervento.isBlank(),
    onSave: (Impianto) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stato locale mutabile per l'editing
    var codIntervento by remember(impianto) { mutableStateOf(impianto.codIntervento) }
    var nomeCompleto by remember(impianto) { mutableStateOf(impianto.nomeCompleto) }
    var premessa by remember(impianto) { mutableStateOf(impianto.premessa ?: "") }
    var attivitaList by remember(impianto) { mutableStateOf(impianto.listaAttivita) }

    // Stato di validazione locale
    var codInterventoError by remember { mutableStateOf(false) }
    var nomeCompletoError by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = if (isNew) "Nuovo Impianto" else "Editor Impianto",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                if (isNew) {
                    Text(
                        text = "Compila i campi obbligatori e salva",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
            }
            Button(
                onClick = {
                    // Validazione locale
                    codInterventoError = codIntervento.isBlank()
                    nomeCompletoError = nomeCompleto.isBlank()

                    if (!codInterventoError && !nomeCompletoError) {
                        val updated = impianto.copy(
                            codIntervento = codIntervento.trim(),
                            nomeCompleto = nomeCompleto.trim(),
                            premessa = premessa.ifBlank { null },
                            listaAttivita = attivitaList
                        )
                        onSave(updated)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (isNew) "Crea Impianto" else "Salva")
            }
        }

        // Campi principali
        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codIntervento,
                    onValueChange = {
                        codIntervento = it
                        codInterventoError = false
                    },
                    label = { Text("Codice Intervento *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = codInterventoError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        errorBorderColor = Color(0xFFD32F2F)
                    ),
                    placeholder = { Text("es. GE, CAB, QBT", fontSize = 12.sp) }
                )
                if (codInterventoError) {
                    Text(
                        text = "Il codice intervento è obbligatorio",
                        color = Color(0xFFD32F2F),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = nomeCompleto,
                    onValueChange = {
                        nomeCompleto = it
                        nomeCompletoError = false
                    },
                    label = { Text("Nome Completo *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nomeCompletoError,
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        errorBorderColor = Color(0xFFD32F2F)
                    ),
                    placeholder = { Text("es. Gruppo Elettrogeno", fontSize = 12.sp) }
                )
                if (nomeCompletoError) {
                    Text(
                        text = "Il nome completo è obbligatorio",
                        color = Color(0xFFD32F2F),
                        fontSize = 10.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
                OutlinedTextField(
                    value = premessa,
                    onValueChange = { premessa = it },
                    label = { Text("Premessa") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4
                )
            }
        }

        // Lista attività
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Attività (${attivitaList.size})",
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = {
                val newNum = (attivitaList.maxOfOrNull { it.nAttivita } ?: 0) + 1
                attivitaList = attivitaList + Attivita(
                    nAttivita = newNum,
                    tipoAttivita = "Controllo visivo",
                    descrizione = "",
                    frequenza = Periodo(TipoPeriodo.M, 1)
                )
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Aggiungi attività")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            itemsIndexed(attivitaList, key = { idx, att -> "${att.nAttivita}_$idx" }) { index, att ->
                AttivitaCard(
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
}

/**
 * Card per una singola attività nell'editor
 */
@Composable
private fun AttivitaCard(
    attivita: Attivita,
    onUpdate: (Attivita) -> Unit,
    onDelete: () -> Unit
) {
    var tipoAttivita by remember(attivita) { mutableStateOf(attivita.tipoAttivita ?: "") }
    var descrizione by remember(attivita) { mutableStateOf(attivita.descrizione ?: "") }
    var tipoPeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.tipo) }
    var valorePeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.valore.toString()) }

    Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Header riga con numero e bottone elimina
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività #${attivita.nAttivita}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tipoAttivita,
                    onValueChange = {
                        tipoAttivita = it
                        onUpdate(attivita.copy(tipoAttivita = it))
                    },
                    label = { Text("Tipo", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                // Frequenza: tipo (M/A)
                Column(modifier = Modifier.width(80.dp)) {
                    Text("Periodo", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.M,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.M
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.M, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("M", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.A,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.A
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.A, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("A", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = valorePeriodo,
                    onValueChange = {
                        valorePeriodo = it
                        val v = it.toIntOrNull() ?: 1
                        onUpdate(attivita.copy(frequenza = Periodo(tipoPeriodo, v)))
                    },
                    label = { Text("Val.", fontSize = 11.sp) },
                    modifier = Modifier.width(60.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            OutlinedTextField(
                value = descrizione,
                onValueChange = {
                    descrizione = it
                    onUpdate(attivita.copy(descrizione = it))
                },
                label = { Text("Descrizione", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
        }
    }
}

