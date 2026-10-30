package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import manutenzioni.domain.model.DispositivoRilevazione
import manutenzioni.domain.service.DateValidationUtils
import manutenzioni.app.ui.components.ItalianDateInputField
import java.util.UUID

private val TIPI_DISPOSITIVO_SUGGERITI = listOf(
    "FUMO", "TERMO", "OTTICO", "MODULO", "PULSANTE", "ASPIRAZIONE", "CONDOTTA", "FIAMMA"
)

private val INSTALLAZIONI_SUGGERITE = listOf(
    "A VISTA", "CONTROSOFFITTO", "PAVIMENTO", "CANALE"
)

@Composable
fun AggiungiDispositivoDialog(
    dispositivoIniziale: DispositivoRilevazione? = null,
    defaultLoop: Int = 1,
    suggestedIndirizzo: String = "",
    onDismiss: () -> Unit,
    onConfirm: (DispositivoRilevazione) -> Unit
) {
    val isEdit = dispositivoIniziale != null

    var selectedLoop by remember { mutableStateOf(dispositivoIniziale?.loop ?: defaultLoop.coerceIn(1, 4)) }
    var indirizzo by remember { mutableStateOf(dispositivoIniziale?.indirizzo ?: suggestedIndirizzo) }
    var tipo by remember { mutableStateOf(dispositivoIniziale?.tipo ?: "FUMO") }
    var installazione by remember { mutableStateOf(dispositivoIniziale?.installazione ?: "") }
    var descrizione by remember { mutableStateOf(dispositivoIniziale?.descrizione ?: "") }
    var zona by remember { mutableStateOf(dispositivoIniziale?.zona ?: "") }
    var dataInstallazione by remember { mutableStateOf(dispositivoIniziale?.dataInstallazione ?: "") }
    var note by remember { mutableStateOf(dispositivoIniziale?.note ?: "") }

    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp,
            modifier = Modifier
                .width(580.dp)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isEdit) Icons.Default.Edit else Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colors.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isEdit) "Modifica Dispositivo Rilevazione Incendi" else "Aggiungi Dispositivo a Loop (RI)",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Configura il componente di rivelazione fumo/calore o modulo asservito (UNI 9795 / UNI 11224)",
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                // 1. Selezione LOOP (1..4)
                Text("Loop Centrale:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    (1..4).forEach { loopNum ->
                        val isSelected = selectedLoop == loopNum
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color.LightGray),
                            elevation = if (isSelected) 2.dp else 0.dp,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedLoop = loopNum }
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 8.dp)) {
                                Text(
                                    text = "LOOP $loopNum",
                                    color = if (isSelected) Color.White else Color.Black,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 2. Indirizzo & Tipo
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = indirizzo,
                        onValueChange = {
                            indirizzo = it
                            validationError = null
                        },
                        label = { Text("Indirizzo Componente *") },
                        placeholder = { Text("es. 1 o RIV.L1.1") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = tipo,
                        onValueChange = {
                            tipo = it
                            validationError = null
                        },
                        label = { Text("Tipo Sensore / Modulo *") },
                        placeholder = { Text("es. FUMO, TERMO") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Suggerimenti rapidi Tipo
                Row(
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TIPI_DISPOSITIVO_SUGGERITI.take(5).forEach { tip ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (tipo.equals(tip, ignoreCase = true)) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { tipo = tip }
                        ) {
                            Text(
                                text = tip,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (tipo.equals(tip, ignoreCase = true)) MaterialTheme.colors.primary else Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 3. Descrizione / Ubicazione
                OutlinedTextField(
                    value = descrizione,
                    onValueChange = {
                        descrizione = it
                        validationError = null
                    },
                    label = { Text("Descrizione / Ubicazione Locale *") },
                    placeholder = { Text("es. SOTTOCENTRALE TERMICA, LOCALE QUADRO ELETTRICO") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))

                // 4. Installazione & Zona
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = installazione,
                        onValueChange = { installazione = it },
                        label = { Text("Installazione") },
                        placeholder = { Text("es. CONTROSOFFITTO, A VISTA") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = zona,
                        onValueChange = { zona = it },
                        label = { Text("Zona / Piano") },
                        placeholder = { Text("es. PINT, ZONA 1") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Suggerimenti rapidi Installazione
                Row(
                    modifier = Modifier.padding(top = 6.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    INSTALLAZIONI_SUGGERITE.forEach { inst ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (installazione.equals(inst, ignoreCase = true)) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                            modifier = Modifier.clickable { installazione = inst }
                        ) {
                            Text(
                                text = inst,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (installazione.equals(inst, ignoreCase = true)) MaterialTheme.colors.primary else Color.DarkGray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 5. Data Installazione / Revisione & Note
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ItalianDateInputField(
                        value = dataInstallazione,
                        onValueChange = {
                            dataInstallazione = it
                            if (validationError != null && validationError!!.contains("data", ignoreCase = true)) {
                                validationError = null
                            }
                        },
                        label = "Data Inst. / Sostituzione",
                        placeholder = "es. 15/10/2024",
                        modifier = Modifier.weight(1f),
                        isRequired = false,
                        isError = validationError != null && dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(dataInstallazione)
                    )

                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("Note di verifica") },
                        placeholder = { Text("Note opzionali") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Errore validazione
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colors.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Bottoni Azione
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Annulla")
                    }

                    Spacer(Modifier.width(12.dp))

                    Button(
                        onClick = {
                            if (indirizzo.isBlank()) {
                                validationError = "L'indirizzo del componente è obbligatorio (es. 1 o RIV.L1.1)"
                                return@Button
                            }
                            if (tipo.isBlank()) {
                                validationError = "Il tipo di componente è obbligatorio (es. FUMO, TERMO)"
                                return@Button
                            }
                            if (descrizione.isBlank()) {
                                validationError = "La descrizione/ubicazione è obbligatoria (es. LOCALE QUADRO)"
                                return@Button
                            }
                            if (dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(dataInstallazione)) {
                                validationError = "Inserisci una data valida nel formato GG/MM/AAAA (es. 15/10/2024)"
                                return@Button
                            }

                            val cleanIndirizzo = indirizzo.trim()
                            val disp = DispositivoRilevazione(
                                id = dispositivoIniziale?.id ?: UUID.randomUUID().toString(),
                                loop = selectedLoop,
                                indirizzo = cleanIndirizzo,
                                tipo = tipo.trim().uppercase(),
                                installazione = installazione.trim().ifBlank { null }?.uppercase(),
                                descrizione = descrizione.trim().uppercase(),
                                zona = zona.trim().ifBlank { null }?.uppercase(),
                                dataInstallazione = dataInstallazione.trim().ifBlank { null },
                                note = note.trim().ifBlank { null }
                            )

                            onConfirm(disp)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isEdit) "Salva Modifiche" else "Aggiungi al Loop")
                    }
                }
            }
        }
    }
}
