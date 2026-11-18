package manutenzioni.app.ui.features.operativita.editor.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo

/**
 * Card per una singola attività nell'editor
 */
@Composable
fun AttivitaCard(
    numeroAttivita: Int,
    attivita: Attivita,
    onUpdate: (Attivita) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tipoAttivita by remember(attivita) { mutableStateOf(attivita.tipoAttivita ?: "") }
    var zona by remember(attivita) { mutableStateOf(attivita.zona ?: "") }
    var descrizione by remember(attivita) { mutableStateOf(attivita.descrizione ?: "") }
    var tipoPeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.tipo) }
    var valorePeriodo by remember(attivita) { mutableStateOf(attivita.frequenza.valore.toString()) }

    Card(modifier = modifier.fillMaxWidth(), elevation = 1.dp) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Attività #$numeroAttivita",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colors.primary
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Elimina",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tipoAttivita,
                    onValueChange = {
                        tipoAttivita = it
                        onUpdate(attivita.copy(tipoAttivita = it))
                    },
                    label = { Text("Tipo", fontSize = 11.sp) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                OutlinedTextField(
                    value = zona,
                    onValueChange = {
                        zona = it
                        onUpdate(attivita.copy(zona = it.ifBlank { null }))
                    },
                    label = { Text("Zona/Componente", fontSize = 11.sp) },
                    modifier = Modifier.weight(1.2f),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )

                Column(modifier = Modifier.width(80.dp)) {
                    Text("Periodo", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.M,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.M
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.M, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("M", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        RadioButton(
                            selected = tipoPeriodo == TipoPeriodo.A,
                            onClick = {
                                tipoPeriodo = TipoPeriodo.A
                                val v = valorePeriodo.toIntOrNull() ?: 1
                                onUpdate(attivita.copy(frequenza = Periodo(TipoPeriodo.A, v)))
                            },
                            modifier = Modifier.size(20.dp)
                        )
                        Text("A", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = valorePeriodo,
                    onValueChange = {
                        valorePeriodo = it
                        val v = it.toIntOrNull() ?: 1
                        onUpdate(attivita.copy(frequenza = Periodo(tipoPeriodo, v)))
                    },
                    label = { Text("Val.", fontSize = 11.sp) },
                    modifier = Modifier.width(60.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                )
            }

            OutlinedTextField(
                value = descrizione,
                onValueChange = {
                    descrizione = it
                    onUpdate(attivita.copy(descrizione = it))
                },
                label = { Text("Descrizione", fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 2,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
            )
        }
    }
}
