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
 * Editor per visualizzare e modificare i dati di un impianto.
 *
 * Permette di editare:
 * - Codice intervento, nome completo, premessa
 * - Lista attività con tipo, descrizione e frequenza
 */
@Composable
fun ImpiantoEditor(
    impianto: Impianto,
    onSave: (Impianto) -> Unit,
    modifier: Modifier = Modifier
) {
    // Stato locale mutabile per l'editing
    var codIntervento by remember(impianto) { mutableStateOf(impianto.codIntervento) }
    var nomeCompleto by remember(impianto) { mutableStateOf(impianto.nomeCompleto) }
    var premessa by remember(impianto) { mutableStateOf(impianto.premessa ?: "") }
    var attivitaList by remember(impianto) { mutableStateOf(impianto.listaAttivita) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Editor Impianto",
                style = MaterialTheme.typography.h6,
                fontWeight = FontWeight.Bold
            )
            Button(
                onClick = {
                    val updated = impianto.copy(
                        codIntervento = codIntervento,
                        nomeCompleto = nomeCompleto,
                        premessa = premessa.ifBlank { null },
                        listaAttivita = attivitaList
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF2E7D32),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salva")
            }
        }

        // Campi principali
        Card(modifier = Modifier.fillMaxWidth(), elevation = 2.dp) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = codIntervento,
                    onValueChange = { codIntervento = it },
                    label = { Text("Codice Intervento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = nomeCompleto,
                    onValueChange = { nomeCompleto = it },
                    label = { Text("Nome Completo") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
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

