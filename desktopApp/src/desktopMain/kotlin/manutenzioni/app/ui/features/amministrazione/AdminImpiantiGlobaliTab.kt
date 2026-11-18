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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.UUID
import manutenzioni.domain.model.Impianto
import manutenzioni.app.ui.features.operativita.editor.ImpiantoEditor
import manutenzioni.app.ui.theme.SlateBorder

@Composable
fun AdminImpiantiGlobaliTab(
    impiantiGlobali: List<Impianto>,
    componentiStandard: List<manutenzioni.domain.model.ComponenteStandard>,
    onUpdateGlobale: (Impianto) -> Unit
) {
    var impiantoInModifica by remember { mutableStateOf<Impianto?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    if (impiantoInModifica != null) {
        Card(elevation = 8.dp, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize()) {
            ImpiantoEditor(
                impianto = impiantoInModifica!!,
                componentiStandard = componentiStandard,
                isReadOnlyAdminFields = false, // Permette di modificare Codice e Nome!
                onSave = { impianto, _ ->
                    onUpdateGlobale(impianto)
                    impiantoInModifica = null
                    isCreatingNew = false
                },
                onCancel = {
                    impiantoInModifica = null
                    isCreatingNew = false
                }
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Libreria Impianti", style = MaterialTheme.typography.h6)
                Text("Crea o aggiorna le matrici base. Le modifiche si propagano ai cantieri.", style = MaterialTheme.typography.caption)
            }
            
            Button(onClick = {
                val nuovo = manutenzioni.domain.model.ImpiantoStandard(
                    id = UUID.randomUUID().toString(),
                    codIntervento = "NUOVO",
                    nomeCompleto = "Nuovo Modello",
                    premessa = null,
                    listaAttivita = emptyList(),
                    cantiereId = null // DEVE ESSERE NULL PER ESSERE GLOBALE
                )
                impiantoInModifica = nuovo
                isCreatingNew = true
            }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nuovo Modello")
            }
        }

        Divider()
        Spacer(modifier = Modifier.height(16.dp))

        if (impiantiGlobali.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nessun modello globale definito.", color = MaterialTheme.colors.secondary)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                items(impiantiGlobali) { impianto ->
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
                            Column {
                                Text(impianto.nomeCompleto, style = MaterialTheme.typography.body1)
                                Text("Codice: ${impianto.codIntervento}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.secondary)
                            }
                            IconButton(onClick = {
                                impiantoInModifica = impianto
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
