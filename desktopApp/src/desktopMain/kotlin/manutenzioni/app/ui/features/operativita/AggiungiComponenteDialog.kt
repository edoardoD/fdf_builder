package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.*

@Composable
fun AggiungiComponenteDialog(
    quadro: QuadroBT,
    candidateComponents: List<ComponentCandidate>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDismiss: () -> Unit,
    onApprovaEAssegna: (ComponentCandidate, VarianteProdotto, Int, String) -> Unit,
    onAggiungiManuale: (InterruttoreBT) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCandidateIndex by remember { mutableStateOf(0) }
    var selectedVariante by remember { mutableStateOf<VarianteProdotto?>(null) }
    var quantita by remember { mutableStateOf(1) }
    var siglaCircuito by remember { mutableStateOf("") }

    // Tabella manuale di fallback
    var showManualInput by remember { mutableStateOf(false) }
    var manualNome by remember { mutableStateOf("") }
    var manualProduttore by remember { mutableStateOf("") }
    var manualCodice by remember { mutableStateOf("") }

    // Aggiorna la variante selezionata quando cambiano i candidati
    LaunchedEffect(candidateComponents) {
        if (candidateComponents.isNotEmpty()) {
            val candidate = candidateComponents.firstOrNull()
            selectedCandidateIndex = 0
            selectedVariante = candidate?.variantiDisponibili?.firstOrNull()
        } else {
            selectedVariante = null
        }
    }

    AlertDialog(
        onDismissRequest = {
            onClearSearch()
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ricerca & Approvazione Componente",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Quadro: ${quadro.sigla.ifBlank { quadro.codIntervento }} — ${quadro.descrizioneQuadro.ifBlank { quadro.nomeCompleto }}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .padding(top = 8.dp)
            ) {
                // Barra di ricerca Live
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        onSearch(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Cerca per codice (es. GC8813, S201) o descrizione (es. 'magnetotermico 16A 1P+N C')") },
                    placeholder = { Text("Digita per cercare in tempo reale...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Cerca")
                    },
                    trailingIcon = {
                        if (isSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                onClearSearch()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Cancella")
                            }
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (candidateComponents.isEmpty()) {
                    if (searchQuery.isBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            backgroundColor = MaterialTheme.colors.surface,
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "💡 Suggerimenti per la ricerca:",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("• Digita una descrizione tecnica: 'magnetotermico 16A', 'diff puro 40A 30mA', 'scaricatore 40kA', 'sezionatore 32A'", fontSize = 12.sp, color = Color.Gray)
                                Text("• Oppure digita il codice fornitore: 'GC8813AC16', 'S201-C16', 'A9N21556', '5SL6'", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    } else if (!isSearching) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            backgroundColor = Color(0xFFFFFBEB),
                            border = BorderStroke(1.dp, Color(0xFFFDE68A))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Nessun componente trovato con le parole cercate.",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = Color(0xFF92400E)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = {
                                        manualNome = searchQuery
                                        showManualInput = true
                                    }
                                ) {
                                    Text("Inserisci manualmente questo componente")
                                }
                            }
                        }
                    }
                } else {
                    // Risultati API trovati
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(candidateComponents) { candidate ->
                            val isCandidateSelected = candidateComponents.indexOf(candidate) == selectedCandidateIndex

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCandidateIndex = candidateComponents.indexOf(candidate)
                                        selectedVariante = candidate.variantiDisponibili.firstOrNull()
                                    },
                                elevation = if (isCandidateSelected) 4.dp else 1.dp,
                                border = BorderStroke(
                                    width = if (isCandidateSelected) 2.dp else 1.dp,
                                    color = if (isCandidateSelected) MaterialTheme.colors.primary else Color(0xFFE2E8F0)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = candidate.descrizioneStandard,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Surface(
                                            color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "ETIM: ${candidate.etimClassId}",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colors.primary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Parametri tecnici ETIM
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        candidate.caratteristicheTecniche.forEach { (k, v) ->
                                            Surface(
                                                color = Color(0xFFF1F5F9),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "$k: $v",
                                                    fontSize = 10.sp,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    color = Color(0xFF475569)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))
                                    Divider()
                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = "Scegli Produttore da assegnare al Quadro:",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))

                                    // Selettore Produttori / Varianti
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        candidate.variantiDisponibili.forEach { variante ->
                                            val isVarSelected = isCandidateSelected && selectedVariante?.codice == variante.codice

                                            Button(
                                                onClick = {
                                                    selectedCandidateIndex = candidateComponents.indexOf(candidate)
                                                    selectedVariante = variante
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    backgroundColor = if (isVarSelected) MaterialTheme.colors.primary else Color(0xFFF8FAFC),
                                                    contentColor = if (isVarSelected) Color.White else Color(0xFF1E293B)
                                                ),
                                                border = BorderStroke(1.dp, if (isVarSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                                elevation = ButtonDefaults.elevation(0.dp, 0.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    Text(text = variante.produttore, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                                    Text(text = variante.codice, fontSize = 10.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Dettaglio finale: Quantità e Circuito
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Stepper quantità
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

                            // Sigla circuito
                            OutlinedTextField(
                                value = siglaCircuito,
                                onValueChange = { siglaCircuito = it },
                                label = { Text("Circuito (es. Q1, Prese FM)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                }

                // Sezione Inserimento Manuale di fallback
                if (showManualInput) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Inserimento Manuale (Non a catalogo ETIM)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            OutlinedTextField(
                                value = manualNome,
                                onValueChange = { manualNome = it },
                                label = { Text("Nome / Descrizione *") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = manualProduttore,
                                    onValueChange = { manualProduttore = it },
                                    label = { Text("Produttore") },
                                    modifier = Modifier.weight(0.5f),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = manualCodice,
                                    onValueChange = { manualCodice = it },
                                    label = { Text("Codice Articolo") },
                                    modifier = Modifier.weight(0.5f),
                                    singleLine = true
                                )
                            }
                            Button(
                                onClick = {
                                    if (manualNome.isNotBlank()) {
                                        onAggiungiManuale(
                                            InterruttoreBT(
                                                nome = manualNome.trim(),
                                                quantita = quantita,
                                                siglaCircuito = siglaCircuito.ifBlank { null },
                                                produttore = manualProduttore.ifBlank { null },
                                                codiceArticolo = manualCodice.ifBlank { null }
                                            )
                                        )
                                        onClearSearch()
                                        onDismiss()
                                    }
                                },
                                enabled = manualNome.isNotBlank()
                            ) {
                                Text("Aggiungi Manualmente al Quadro")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidate = candidateComponents.getOrNull(selectedCandidateIndex)
                    val variante = selectedVariante
                    if (candidate != null && variante != null) {
                        onApprovaEAssegna(candidate, variante, quantita, siglaCircuito)
                        onClearSearch()
                        onDismiss()
                    }
                },
                enabled = candidateComponents.isNotEmpty() && selectedVariante != null,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color(0xFF16A34A),
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (selectedVariante != null) "⭐ Approva & Assegna (${selectedVariante?.produttore})" else "Approva & Assegna",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onClearSearch()
                onDismiss()
            }) {
                Text("Annulla")
            }
        },
        modifier = Modifier.fillMaxWidth(0.92f)
    )
}
