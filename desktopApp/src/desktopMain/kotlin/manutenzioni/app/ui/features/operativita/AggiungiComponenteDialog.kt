package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.QuadroBT

private val BRAND_SUGGESTIONS = listOf("BTicino", "ABB", "Schneider", "Siemens", "Gewiss", "Hager", "Finder")

@Composable
fun ModificaComponenteDialog(
    quadro: QuadroBT,
    interruttore: InterruttoreBT,
    onDismiss: () -> Unit,
    onConferma: (InterruttoreBT) -> Unit
) {
    AggiungiComponenteDialog(
        quadro = quadro,
        interruttoreDaModificare = interruttore,
        onDismiss = onDismiss,
        onConferma = onConferma
    )
}

/**
 * Dialog di creazione / modifica interruttore differenziale o componente per Quadro BT.
 *
 * Rispetta l'ordinamento campi richiesto (R10):
 * 1. Sigla Circuito / Interruttore (+ quantità)
 * 2. Produttore / Marca
 * 3. Codice Articolo & Taratura differenziale
 * 4. Descrizione / Nome Componente (opzionale, non vincolante alla validazione)
 * 5. Note / Serie (opzionale)
 */
@Composable
fun AggiungiComponenteDialog(
    quadro: QuadroBT,
    interruttoreDaModificare: InterruttoreBT? = null,
    onDismiss: () -> Unit,
    onConferma: (InterruttoreBT) -> Unit
) {
    val isEditMode = interruttoreDaModificare != null

    var siglaCircuito by remember { mutableStateOf(interruttoreDaModificare?.siglaCircuito ?: "") }
    var quantita by remember { mutableStateOf(interruttoreDaModificare?.quantita ?: 1) }
    var produttore by remember { mutableStateOf(interruttoreDaModificare?.produttore ?: "") }
    var codiceArticolo by remember { mutableStateOf(interruttoreDaModificare?.codiceArticolo ?: "") }
    var taratura by remember {
        mutableStateOf(
            interruttoreDaModificare?.caratteristicheTecniche?.get("taratura")
                ?: interruttoreDaModificare?.caratteristicheTecniche?.get("Idn")
                ?: interruttoreDaModificare?.caratteristicheTecniche?.get("IΔn")
                ?: ""
        )
    }
    var nome by remember { mutableStateOf(interruttoreDaModificare?.nome ?: "") }
    var serie by remember { mutableStateOf(interruttoreDaModificare?.note ?: "") }

    val scrollState = rememberScrollState()

    fun submit() {
        // Calcola nome effettivo: se lasciato vuoto, costruisce da produttore + codice o sigla (R10)
        val nomeRisolto = when {
            nome.isNotBlank() -> nome.trim()
            codiceArticolo.isNotBlank() && produttore.isNotBlank() -> "${produttore.trim()} ${codiceArticolo.trim()}"
            codiceArticolo.isNotBlank() -> codiceArticolo.trim()
            siglaCircuito.isNotBlank() -> "Interruttore ${siglaCircuito.trim()}"
            else -> "Interruttore"
        }

        val caratteristicheAggiornate = (interruttoreDaModificare?.caratteristicheTecniche ?: emptyMap()).toMutableMap()
        if (taratura.isNotBlank()) {
            caratteristicheAggiornate["taratura"] = taratura.trim()
        } else {
            caratteristicheAggiornate.remove("taratura")
        }

        val risultato = (interruttoreDaModificare ?: InterruttoreBT(nome = nomeRisolto)).copy(
            nome = nomeRisolto,
            quantita = quantita.coerceAtLeast(1),
            siglaCircuito = siglaCircuito.trim().ifBlank { null },
            produttore = produttore.trim().ifBlank { null },
            codiceArticolo = codiceArticolo.trim().ifBlank { null },
            note = serie.trim().ifBlank { null },
            caratteristicheTecniche = caratteristicheAggiornate
        )
        onConferma(risultato)
        onDismiss()
    }

    val isValid = siglaCircuito.isNotBlank() || codiceArticolo.isNotBlank() || nome.isNotBlank() || produttore.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = if (isEditMode) "Modifica Componente Quadro" else "Aggiungi Componente al Quadro",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quadro: ${quadro.sigla.ifBlank { quadro.codIntervento }} — ${quadro.descrizioneQuadro.ifBlank { quadro.nomeCompleto }}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Campo 1: Sigla Circuito & Quantità (R10)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = siglaCircuito,
                            onValueChange = { siglaCircuito = it },
                            label = { Text("1. Sigla Circuito (es. QA1, QF1)") },
                            placeholder = { Text("QA1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )

                        // Stepper Quantità
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Q.tà:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            OutlinedButton(
                                onClick = { if (quantita > 1) quantita-- },
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$quantita",
                                modifier = Modifier.padding(horizontal = 8.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            OutlinedButton(
                                onClick = { quantita++ },
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("+", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 2. Campo 2: Produttore / Marca con suggerimenti rapidi (R10)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = produttore,
                        onValueChange = { produttore = it },
                        label = { Text("2. Produttore / Marca") },
                        placeholder = { Text("es. BTicino, ABB, Schneider...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marchi:", fontSize = 11.sp, color = Color.Gray)
                        BRAND_SUGGESTIONS.forEach { brand ->
                            val isSelected = produttore.equals(brand, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable {
                                    produttore = if (isSelected) "" else brand
                                }
                            ) {
                                Text(
                                    text = brand,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 3. Campi 3 e 4: Codice Articolo & Taratura differenziale (R10)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = codiceArticolo,
                        onValueChange = { codiceArticolo = it },
                        label = { Text("3. Codice Articolo") },
                        placeholder = { Text("es. GC8813AC16, S201-C16") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )

                    OutlinedTextField(
                        value = taratura,
                        onValueChange = { taratura = it },
                        label = { Text("4. Taratura diff. (IΔn)") },
                        placeholder = { Text("es. 0,03A-A, 0,3A-AS, 30mA") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                // 4. Campo 5: Descrizione / Nome Componente (Opzionale) (R10)
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("5. Descrizione / Nome Componente (Opzionale)") },
                    placeholder = { Text("es. Magnetotermico Differenziale 1P+N C16") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        if (nome.isNotEmpty()) {
                            IconButton(onClick = { nome = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cancella")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // 5. Note / Serie Aggiuntive (Opzionale)
                OutlinedTextField(
                    value = serie,
                    onValueChange = { serie = it },
                    label = { Text("Note / Serie Aggiuntive (Opzionale)") },
                    placeholder = { Text("es. Curva C, Btdin45, quadro pompe") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (isValid) submit() })
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = isValid,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isEditMode) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isEditMode) "Salva Modifiche" else "Aggiungi al Quadro",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        modifier = Modifier.fillMaxWidth(0.85f)
    )
}
