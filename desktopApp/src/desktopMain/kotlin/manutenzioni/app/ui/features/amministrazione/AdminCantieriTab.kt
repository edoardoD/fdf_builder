package manutenzioni.app.ui.features.amministrazione

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.app.ui.features.operativita.ClienteSelector
import manutenzioni.app.ui.theme.SlateBorder

@Composable
fun AdminCantieriTab(
    clienti: List<Cliente>,
    selectedCliente: Cliente?,
    cantieri: List<Cantiere>,
    onClienteSelected: (Cliente) -> Unit,
    onAddCantiere: (Cantiere) -> Unit,
    onUpdateCantiere: (Cantiere) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var cantiereInModifica by remember { mutableStateOf<Cantiere?>(null) }

    if (showDialog && selectedCliente != null) {
        NuovoCantiereDialog(
            clienteId = selectedCliente.id,
            onDismiss = { showDialog = false },
            onConfirm = { cantiere ->
                onAddCantiere(cantiere)
                showDialog = false
            }
        )
    }

    cantiereInModifica?.let { cantiere ->
        key(cantiere.id) {
            NuovoCantiereDialog(
                clienteId = cantiere.clienteId,
                initialCantiere = cantiere,
                onDismiss = { cantiereInModifica = null },
                onConfirm = { aggiornato ->
                    onUpdateCantiere(aggiornato)
                    cantiereInModifica = null
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Gestione Cantieri", style = MaterialTheme.typography.h6)
                Text("Seleziona un cliente per visualizzarne i cantieri", style = MaterialTheme.typography.caption)
            }
            
            ClienteSelector(
                clienti = clienti,
                selectedCliente = selectedCliente,
                onClienteSelected = onClienteSelected
            )
        }

        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        if (selectedCliente == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun cliente selezionato.", color = MaterialTheme.colors.secondary)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = { showDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Nuovo Cantiere")
                }
            }

            if (cantieri.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Nessun cantiere per questo cliente.", color = MaterialTheme.colors.secondary)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    items(cantieri) { cantiere ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(cantiere.nome, style = MaterialTheme.typography.body1, fontWeight = FontWeight.SemiBold)
                                    cantiere.indirizzo?.let { indirizzo ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("📍 $indirizzo", fontSize = 12.sp, color = MaterialTheme.colors.secondary)
                                    }
                                    cantiere.dataInizioContratto?.let { dataContratto ->
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("📅 Inizio contratto: $dataContratto", fontSize = 12.sp, color = MaterialTheme.colors.primary)
                                    }
                                }
                                IconButton(onClick = {
                                    cantiereInModifica = cantiere
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = MaterialTheme.colors.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog unico per creazione e modifica di un cantiere (R3, R4).
 * In modifica (`initialCantiere != null`) aggiorna nome, indirizzo e data inizio contratto via `copy()`,
 * preservando i metadati di intervento (data intervento, data intervento precedente, manutentore).
 */
@Composable
fun NuovoCantiereDialog(
    clienteId: String,
    initialCantiere: Cantiere? = null,
    onDismiss: () -> Unit,
    onConfirm: (Cantiere) -> Unit
) {
    var nome by remember { mutableStateOf(initialCantiere?.nome ?: "") }
    var indirizzo by remember { mutableStateOf(initialCantiere?.indirizzo ?: "") }
    var dataInizioContratto by remember { mutableStateOf(initialCantiere?.dataInizioContratto ?: "") }
    var nomeError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialCantiere == null) "Nuovo Cantiere" else "Modifica Cantiere") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = nome,
                    onValueChange = { nome = it; nomeError = false },
                    label = { Text("Nome *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = nomeError
                )
                if (nomeError) {
                    Text("Il nome è obbligatorio", color = MaterialTheme.colors.error, style = MaterialTheme.typography.caption)
                }
                OutlinedTextField(
                    value = indirizzo,
                    onValueChange = { indirizzo = it },
                    label = { Text("Indirizzo cantiere") },
                    placeholder = { Text("Via, numero civico, CAP, città") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = dataInizioContratto,
                    onValueChange = { dataInizioContratto = it },
                    label = { Text("Data inizio contratto") },
                    placeholder = { Text("GG/MM/AAAA (es. 01/01/2026)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (nome.isBlank()) {
                    nomeError = true
                } else {
                    val indirizzoPulito = indirizzo.trim().ifBlank { null }
                    val dataInizioContrattoPulita = dataInizioContratto.trim().ifBlank { null }
                    val cantiere = initialCantiere?.copy(
                        nome = nome.trim(),
                        indirizzo = indirizzoPulito,
                        dataInizioContratto = dataInizioContrattoPulita
                    ) ?: Cantiere(
                        id = UUID.randomUUID().toString(),
                        nome = nome.trim(),
                        clienteId = clienteId,
                        indirizzo = indirizzoPulito,
                        dataInizioContratto = dataInizioContrattoPulita
                    )
                    onConfirm(cantiere)
                }
            }) {
                Text(if (initialCantiere == null) "Crea" else "Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
