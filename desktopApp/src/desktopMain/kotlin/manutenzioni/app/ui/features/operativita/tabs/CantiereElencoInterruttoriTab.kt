package manutenzioni.app.ui.features.operativita.tabs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.features.operativita.model.ElencoInterruttoriTabUiState
import manutenzioni.app.ui.features.operativita.model.buildElencoInterruttoriTabUiState
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.QuadroBT

/**
 * Tab per visualizzare tutti gli interruttori differenziali del cantiere
 * aggregati dai vari Quadri BT e stamparne il PDF unico.
 */
@Composable
fun CantiereElencoInterruttoriTab(
    state: ElencoInterruttoriTabUiState,
    isLoading: Boolean,
    onExportPdf: () -> Unit,
    onEditQuadro: (QuadroBT) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Elenco Interruttori Differenziali del Cantiere",
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${state.quadriCount} quadri elettrici nel cantiere • ${state.totalInterruttoriCount} interruttori registrati",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }
                Button(
                    onClick = onExportPdf,
                    enabled = state.totalInterruttoriCount > 0 && !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary,
                        contentColor = Color.White
                    )
                ) {
                    Text("Stampa PDF Elenco Interruttori", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (state.rows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Nessun interruttore registrato nei quadri di questo cantiere.",
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Apri un Quadro Elettrico (Q) dalla tab 'Generazione schede di Verifica' per aggiungere gli interruttori.",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Quadro", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Sigla Circuito", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    Text("Componente / Articolo", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(2.2f))
                    Text("Taratura Diff.", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                    Text("Q.tà", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                    Text("Azioni", fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(0.8f))
                }
                Divider(color = SlateBorder)

                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    state.rows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(row.quadroSigla, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(row.siglaCircuito, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Text(row.datiInterruttore, fontSize = 12.sp, modifier = Modifier.weight(2.2f))
                            Text(row.taratura, fontSize = 12.sp, modifier = Modifier.weight(1.2f))
                            Text("${row.quantita}", fontSize = 12.sp, modifier = Modifier.weight(0.6f))
                            TextButton(
                                onClick = { onEditQuadro(row.originalQuadro) },
                                modifier = Modifier.weight(0.8f)
                            ) {
                                Text("Modifica", fontSize = 11.sp)
                            }
                        }
                        Divider(color = SlateBorder.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun CantiereElencoInterruttoriTab(
    quadri: List<QuadroBT>,
    isLoading: Boolean,
    onExportPdf: () -> Unit,
    onEditQuadro: (QuadroBT) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabState = remember(quadri) { buildElencoInterruttoriTabUiState(quadri) }
    CantiereElencoInterruttoriTab(
        state = tabState,
        isLoading = isLoading,
        onExportPdf = onExportPdf,
        onEditQuadro = onEditQuadro,
        modifier = modifier
    )
}
