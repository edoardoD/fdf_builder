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
fun AggiungiComponenteDialog(
    quadro: QuadroBT,
    onDismiss: () -> Unit,
    onConferma: (InterruttoreBT) -> Unit
) {
    var nome by remember { mutableStateOf("") }
    var produttore by remember { mutableStateOf("") }
    var codiceArticolo by remember { mutableStateOf("") }
    var serie by remember { mutableStateOf("") }
    var quantita by remember { mutableStateOf(1) }
    var siglaCircuito by remember { mutableStateOf("") }

    val scrollState = rememberScrollState()

    fun submit() {
        if (nome.isNotBlank()) {
            onConferma(
                InterruttoreBT(
                    nome = nome.trim(),
                    quantita = quantita.coerceAtLeast(1),
                    siglaCircuito = siglaCircuito.trim().ifBlank { null },
                    produttore = produttore.trim().ifBlank { null },
                    codiceArticolo = codiceArticolo.trim().ifBlank { null },
                    note = serie.trim().ifBlank { null }
                )
            )
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Aggiungi Componente al Quadro",
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
                // 1. Descrizione / Nome Componente (Obbligatorio)
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it },
                    label = { Text("Descrizione / Nome Componente *") },
                    placeholder = { Text("es. Magnetotermico 1P+N C16 4.5kA, Differenziale puro 25A 30mA...") },
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

                // 2. Produttore con suggerimenti rapidi (Chip)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = produttore,
                        onValueChange = { produttore = it },
                        label = { Text("Produttore / Marca") },
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
                        Text("Marchi frequenti:", fontSize = 11.sp, color = Color.Gray)
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

                // 3. Codice Articolo & Serie / Note
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = codiceArticolo,
                        onValueChange = { codiceArticolo = it },
                        label = { Text("Codice Articolo") },
                        placeholder = { Text("es. GC8813AC16, S201-C16") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = serie,
                        onValueChange = { serie = it },
                        label = { Text("Serie / Note Aggiuntive") },
                        placeholder = { Text("es. Btdin45, Curva C") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                // 4. Quantità e Sigla Circuito
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
                        // Stepper Quantità
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Q.tà:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { if (quantita > 1) quantita-- },
                                modifier = Modifier.size(32.dp),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("-", fontWeight = FontWeight.Bold)
                            }
                            Text(
                                text = "$quantita",
                                modifier = Modifier.padding(horizontal = 10.dp),
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

                        // Sigla Circuito
                        OutlinedTextField(
                            value = siglaCircuito,
                            onValueChange = { siglaCircuito = it },
                            label = { Text("Sigla Circuito (es. Q1, F1 - Prese)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { submit() })
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = nome.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Aggiungi al Quadro",
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
