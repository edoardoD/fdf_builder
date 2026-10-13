package manutenzioni.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.Impianto

@Composable
fun ImpiantoSelectionList(
    impianti: List<Impianto>,
    selectionState: Map<String, Boolean>,
    onSelectionChanged: (String, Boolean) -> Unit,
    onEditImpianto: (Impianto) -> Unit,
    onDeleteImpianto: (Impianto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (impianti.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text("Nessun impianto trovato per questo cantiere.", color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
        }
        return
    }

    Column(modifier = modifier) {
        Text(
            text = "Seleziona Impianti da Includere",
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(impianti, key = { it.id }) { impianto ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val isSelected = selectionState[impianto.id] == true
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                onSelectionChanged(impianto.id, checked)
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = impianto.nomeCompleto,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Codice: ${impianto.codIntervento}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        
                        if (impianto is manutenzioni.domain.model.QuadroBT && impianto.sigla.isNotBlank()) {
                            Text(
                                text = "Sigla: ${impianto.sigla}",
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        if (impianto is manutenzioni.domain.model.QuadroMQT && impianto.tipoInterruttore != null) {
                            Text(
                                text = "Interruttore: ${impianto.tipoInterruttore.label}",
                                style = MaterialTheme.typography.caption,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colors.primary,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        }

                        Row {
                            IconButton(onClick = { onEditImpianto(impianto) }) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Modifica impianto",
                                    tint = MaterialTheme.colors.primary
                                )
                            }
                            IconButton(onClick = { onDeleteImpianto(impianto) }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Elimina impianto",
                                    tint = MaterialTheme.colors.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
