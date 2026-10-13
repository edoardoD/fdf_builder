package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.theme.SlateBorder
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.QuadroBT

/** Un template Quadro BT richiede il wizard di creazione massiva (sigle QG, Q1, ...). */
internal fun Impianto.isQuadroBtTemplate(): Boolean =
    this is QuadroBT || codIntervento.equals("Q", ignoreCase = true)

/**
 * Dialog "Aggiungi Attività di Verifica" (R5/R6).
 *
 * Permette la selezione multipla tramite checkbox dei template master globali e la conferma
 * con un solo click. Testi ed etichette sono allineati a sinistra.
 * Lo stato di selezione è puramente UI (locale); la persistenza è delegata al ViewModel
 * tramite [onConfirmMultiple].
 */
@Composable
fun NuovoImpiantoDialog(
    impiantiGlobali: List<Impianto>,
    onDismiss: () -> Unit,
    onConfirmMultiple: (List<Impianto>) -> Unit,
    onCreateEmpty: () -> Unit
) {
    var selectedImpiantoIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var filtro by remember { mutableStateOf("") }

    val templatesFiltrati = remember(impiantiGlobali, filtro) {
        val q = filtro.trim()
        if (q.isEmpty()) impiantiGlobali
        else impiantiGlobali.filter {
            it.codIntervento.contains(q, ignoreCase = true) || it.nomeCompleto.contains(q, ignoreCase = true)
        }
    }
    val tuttiFiltratiSelezionati = templatesFiltrati.isNotEmpty() &&
        templatesFiltrati.all { it.id in selectedImpiantoIds }
    val selezionati = impiantiGlobali.filter { it.id in selectedImpiantoIds }
    val haQuadriBt = selezionati.any { it.isQuadroBtTemplate() }

    AlertDialog(
        modifier = Modifier.width(620.dp),
        onDismissRequest = onDismiss,
        title = {
            Text("Aggiungi Attività di Verifica", fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Start)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                Text(
                    "Seleziona una o più schede dalla libreria master e conferma con un solo click.",
                    style = MaterialTheme.typography.body2,
                    color = MaterialTheme.colors.secondary,
                    textAlign = TextAlign.Start
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = filtro,
                    onValueChange = { filtro = it },
                    placeholder = { Text("Filtra per codice o descrizione…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colors.primary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = MaterialTheme.colors.primary,
                        unfocusedBorderColor = SlateBorder
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (impiantiGlobali.isEmpty()) {
                    Text("Nessun modello globale disponibile.", color = MaterialTheme.colors.secondary)
                } else {
                    // Header: seleziona tutti + contatore
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = templatesFiltrati.isNotEmpty()) {
                                val ids = templatesFiltrati.map { it.id }.toSet()
                                selectedImpiantoIds = if (tuttiFiltratiSelezionati) selectedImpiantoIds - ids else selectedImpiantoIds + ids
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = tuttiFiltratiSelezionati,
                            onCheckedChange = null,
                            enabled = templatesFiltrati.isNotEmpty(),
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colors.primary)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Seleziona tutti", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${selectedImpiantoIds.size} selezionat${if (selectedImpiantoIds.size == 1) "a" else "e"}",
                            fontSize = 12.sp,
                            color = MaterialTheme.colors.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Divider(color = SlateBorder)

                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp)) {
                        items(templatesFiltrati, key = { it.id }) { template ->
                            val checked = template.id in selectedImpiantoIds
                            TemplateCheckboxRow(
                                template = template,
                                checked = checked,
                                onToggle = {
                                    selectedImpiantoIds = if (checked) selectedImpiantoIds - template.id else selectedImpiantoIds + template.id
                                }
                            )
                        }
                    }
                }

                if (haQuadriBt) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "ℹ I Quadri BT selezionati proseguiranno nel wizard di creazione quadri (sigle QG, Q1, …).",
                        fontSize = 11.sp,
                        color = MaterialTheme.colors.secondary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Divider(color = SlateBorder)
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onCreateEmpty) {
                    Text("+ Crea scheda vuota personalizzata")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmMultiple(selezionati) },
                enabled = selezionati.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(backgroundColor = MaterialTheme.colors.primary, contentColor = Color.White)
            ) {
                Text(if (selezionati.isEmpty()) "Aggiungi" else "Aggiungi (${selezionati.size})", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        }
    )
}

@Composable
private fun TemplateCheckboxRow(
    template: Impianto,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val primary = MaterialTheme.colors.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(
                color = if (checked) primary.copy(alpha = 0.06f) else Color.Transparent,
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onToggle)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            colors = CheckboxDefaults.colors(checkedColor = primary)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = primary.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, primary.copy(alpha = 0.25f)),
            modifier = Modifier.widthIn(min = 48.dp)
        ) {
            Text(
                template.codIntervento.ifBlank { "—" },
                color = primary,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            template.nomeCompleto,
            style = MaterialTheme.typography.body2,
            textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}
