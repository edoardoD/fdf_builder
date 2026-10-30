package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.SessioneVerifica
import java.io.File

/**
 * Tab "Storico Verifiche (Archivio Mensile & Registro Manutentori)" (Wave 4: R25, R30).
 */
@Composable
fun StoricoVerificheTab(
    sessioni: List<SessioneVerifica>,
    isAdmin: Boolean,
    onOpenPdfFile: (String) -> Unit,
    onRestoreSessione: (SessioneVerifica) -> Unit,
    onDeleteSessione: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var filtroRicerca by remember { mutableStateOf("") }

    val sessioniFiltrate = remember(sessioni, filtroRicerca) {
        val q = filtroRicerca.trim()
        if (q.isEmpty()) sessioni
        else sessioni.filter { s ->
            s.meseAnno.contains(q, ignoreCase = true) ||
                (s.nomeManutentore?.contains(q, ignoreCase = true) == true) ||
                (s.dataIntervento?.contains(q, ignoreCase = true) == true) ||
                s.riepilogoImpianti.any { it.contains(q, ignoreCase = true) }
        }
    }

    Card(
        modifier = modifier,
        elevation = 0.dp,
        border = BorderStroke(1.dp, SlateBorder),
        shape = RoundedCornerShape(8.dp),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colors.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Archivio Mensile Schede e Registro Storico Manutentori",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${sessioni.size} sessioni di verifica storicizzate • Il nome del manutentore e i PDF generati restano immutabili per ciascun ciclo",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.secondary
                    )
                }

                OutlinedTextField(
                    value = filtroRicerca,
                    onValueChange = { filtroRicerca = it },
                    placeholder = { Text("Filtra per mese (es. 2026_10) o manutentore…", fontSize = 12.sp) },
                    singleLine = true,
                    modifier = Modifier.width(320.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = SlateBorder
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Divider(color = SlateBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (sessioniFiltrate.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nessuna sessione di verifica archiviata per questo cantiere.",
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Ogni volta che generi un batch di schede PDF, la sessione e il nome del tecnico vengono salvati automaticamente qui.",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.secondary
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sessioniFiltrate.forEach { sessione ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = 0.dp,
                            border = BorderStroke(1.dp, SlateBorder),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colors.primary, RoundedCornerShape(4.dp))
                                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = sessione.meseAnno.ifBlank { "SESSIONE" },
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = Color(0xFF2E7D32),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Manutentore: ${sessione.nomeManutentore?.takeIf { it.isNotBlank() } ?: "Non specificato"}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF2E7D32)
                                            )
                                        }
                                        Text(
                                            text = "• Data intervento: ${sessione.dataIntervento ?: "-"} (prec.: ${sessione.dataInterventoPrecedente ?: "-"})",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colors.secondary
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { onRestoreSessione(sessione) },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Ripristina Dati Sessione", fontSize = 11.sp)
                                        }
                                        if (isAdmin) {
                                            IconButton(
                                                onClick = { onDeleteSessione(sessione.id) },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Elimina sessione",
                                                    tint = MaterialTheme.colors.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                if (sessione.riepilogoImpianti.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Impianti verificati: ${sessione.riepilogoImpianti.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colors.secondary
                                    )
                                }

                                if (sessione.elencoFilePdf.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Divider(color = SlateBorder.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Schede PDF archiviate (${sessione.elencoFilePdf.size}):",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        sessione.elencoFilePdf.take(6).forEach { path ->
                                            val fileName = File(path).name
                                            TextButton(
                                                onClick = { onOpenPdfFile(path) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                            ) {
                                                Text("📄 $fileName", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
