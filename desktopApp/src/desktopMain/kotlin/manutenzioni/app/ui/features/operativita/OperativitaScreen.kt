package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.app.ui.theme.*

import manutenzioni.app.ui.features.amministrazione.ClienteDialog
import manutenzioni.app.ui.features.amministrazione.NuovoCantiereDialog

@Composable
fun OperativitaScreen(
    state: ManutenzioniUiState,
    onClienteSelected: (Cliente) -> Unit,
    onCantiereSelected: (Cantiere) -> Unit,
    onAddCliente: (Cliente) -> Unit,
    onAddCantiere: (Cantiere) -> Unit
) {
    var showNuovoClienteDialog by remember { mutableStateOf(false) }
    var showNuovoCantiereDialog by remember { mutableStateOf(false) }

    if (showNuovoClienteDialog) {
        ClienteDialog(
            initialCliente = null,
            onDismiss = { showNuovoClienteDialog = false },
            onConfirm = { cliente ->
                onAddCliente(cliente)
                onClienteSelected(cliente) // Seleziona automaticamente il nuovo cliente
                showNuovoClienteDialog = false
            }
        )
    }

    if (showNuovoCantiereDialog && state.selectedCliente != null) {
        NuovoCantiereDialog(
            clienteId = state.selectedCliente.id,
            onDismiss = { showNuovoCantiereDialog = false },
            onConfirm = { cantiere ->
                onAddCantiere(cantiere)
                showNuovoCantiereDialog = false
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        // Top Bar: Client Selector
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Dashboard Operativa",
                    style = MaterialTheme.typography.h5,
                    color = MaterialTheme.colors.onBackground
                )
                if (state.selectedCliente != null) {
                    Text(
                        text = "Cliente selezionato: ${state.selectedCliente.nome}",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.secondary
                    )
                } else {
                    Text(
                        text = "Seleziona un cliente per visualizzare i cantieri",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.error
                    )
                }
            }

            ClienteSelector(
                clienti = state.clienti,
                selectedCliente = state.selectedCliente,
                onClienteSelected = onClienteSelected,
                onNewClienteClick = { showNuovoClienteDialog = true }
            )
        }

        Divider(color = SlateBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))

        // Cantieri Grid
        if (state.selectedCliente != null) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 300.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    AddCantiereCard(onClick = { showNuovoCantiereDialog = true })
                }
                items(state.cantieriDisponibili) { cantiere ->
                    CantiereCard(
                        cantiere = cantiere,
                        onClick = { onCantiereSelected(cantiere) }
                    )
                }
            }
        }
    }
}

@Composable
fun AddCantiereCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.height(140.dp),
        elevation = 0.dp,
        border = BorderStroke(1.dp, MaterialTheme.colors.primary),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(32.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Aggiungi Cantiere",
                style = MaterialTheme.typography.subtitle1,
                color = MaterialTheme.colors.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ClienteSelector(
    clienti: List<Cliente>,
    selectedCliente: Cliente?,
    onClienteSelected: (Cliente) -> Unit,
    onNewClienteClick: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface
            ),
            border = BorderStroke(1.dp, SlateBorder),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(selectedCliente?.nome ?: "Seleziona Cliente")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            clienti.forEach { cliente ->
                DropdownMenuItem(onClick = {
                    onClienteSelected(cliente)
                    expanded = false
                }) {
                    Text(cliente.nome)
                }
            }
            if (onNewClienteClick != null) {
                Divider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(onClick = {
                    expanded = false
                    onNewClienteClick()
                }) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colors.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("+ Nuovo Cliente", color = MaterialTheme.colors.primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun CantiereCard(
    cantiere: Cantiere,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = cantiere.nome,
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.onSurface
            )
            cantiere.indirizzo?.let { indirizzo ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 $indirizzo",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary
                )
            }
            cantiere.dataInizioContratto?.let { dataContratto ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📅 Inizio contratto: $dataContratto",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = MaterialTheme.colors.onPrimary
                ),
                elevation = ButtonDefaults.elevation(0.dp, 0.dp)
            ) {
                Text("Gestione Verifiche Periodiche", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
