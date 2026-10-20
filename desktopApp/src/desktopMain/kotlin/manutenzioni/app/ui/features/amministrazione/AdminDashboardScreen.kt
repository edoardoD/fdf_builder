package manutenzioni.app.ui.features.amministrazione

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import manutenzioni.app.ui.AdminTab
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.app.ui.theme.SlateBorder

import manutenzioni.app.ui.ManutenzioniViewModel

import androidx.compose.ui.Alignment

@Composable
fun AdminDashboardScreen(
    state: ManutenzioniUiState,
    viewModel: ManutenzioniViewModel,
    onTabSelected: (AdminTab) -> Unit,
    onDisconnect: () -> Unit = {}
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Amministrazione",
                style = MaterialTheme.typography.h5,
                color = MaterialTheme.colors.onBackground
            )
            OutlinedButton(onClick = onDisconnect) {
                Text("Disconnetti DB", color = MaterialTheme.colors.error)
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        TabRow(
            selectedTabIndex = state.currentAdminTab.ordinal,
            backgroundColor = MaterialTheme.colors.surface,
            contentColor = MaterialTheme.colors.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            AdminTab.values().forEach { tab ->
                Tab(
                    selected = state.currentAdminTab == tab,
                    onClick = { onTabSelected(tab) },
                    text = { Text(tab.label) }
                )
            }
        }
        
        Divider(color = SlateBorder, thickness = 1.dp)
        Spacer(modifier = Modifier.height(24.dp))
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 16.dp)) {
            when (state.currentAdminTab) {
                AdminTab.CLIENTI -> {
                    AdminClientiTab(
                        clienti = state.clienti,
                        onAddCliente = { viewModel.addCliente(it) }, // Passare viewModel in qualche modo o delegare
                        onUpdateCliente = viewModel::updateCliente,
                        onDeleteCliente = { viewModel.deleteCliente(it) }
                    )
                }
                AdminTab.CANTIERI -> {
                    AdminCantieriTab(
                        clienti = state.clienti,
                        selectedCliente = state.selectedCliente,
                        cantieri = state.cantieriDisponibili,
                        onClienteSelected = { viewModel.selectCliente(it) },
                        onAddCantiere = { viewModel.addCantiere(it) },
                        onUpdateCantiere = { cantiere -> viewModel.updateCantiere(cantiere.id, cantiere.nome, cantiere.indirizzo) }
                    )
                }
                AdminTab.IMPIANTI_GLOBALI -> {
                    AdminImpiantiGlobaliTab(
                        impiantiGlobali = state.impiantiGlobali,
                        componentiStandard = state.componentiStandard,
                        onUpdateGlobale = viewModel::updateImpiantoGlobale
                    )
                }
                AdminTab.UTENTI -> {
                    AdminUtentiTab(
                        utenti = state.utenti,
                        utenteAttivo = state.utenteAttivo,
                        clienti = state.clienti,
                        cantieriDisponibili = state.cantieriDisponibili,
                        onSelectUtenteAttivo = viewModel::selectUtenteAttivo,
                        onSaveUtente = viewModel::saveUtente,
                        onDeleteUtente = viewModel::deleteUtente
                    )
                }
            }
        }
    }
}
