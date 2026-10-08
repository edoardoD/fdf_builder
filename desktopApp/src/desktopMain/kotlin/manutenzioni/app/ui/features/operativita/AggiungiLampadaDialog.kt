package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.LampadaEmergenza
import java.util.UUID

private val BRAND_SUGGESTIONS_EMERGENZA = listOf("Beghelli", "Schneider", "Lovato", "ABB", "Legrand", "CAME", "Linergy")
private val MODELLI_SUGGERITI_BEGHELLI = listOf("Formula 65 LED", "Ecoled SE 1h", "Ecoled 3h", "Pratica IP42", "Astriled")
private val AUTONOMIA_SUGGESTIONS = listOf("1h", "2h", "3h", "8h")
private val QUANTITA_PRESETS = listOf(1, 5, 10, 20, 50)

object LampadaGeneratorHelper {
    /**
     * Calcola la prossima sigla iniziale suggerita analizzando le lampade esistenti.
     * Se ci sono lampade con numeri (es. EM-01, EM-05), prende il massimo + 1.
     */
    fun calculateNextStartSigla(existing: List<LampadaEmergenza>, defaultPrefix: String): String {
        val cleanPrefix = defaultPrefix.ifBlank { "EM" }
        val maxNum = existing.mapNotNull { lampada ->
            Regex("""\d+""").findAll(lampada.sigla).lastOrNull()?.value?.toIntOrNull()
        }.maxOrNull() ?: existing.size
        val nextNum = maxNum + 1
        return "$cleanPrefix-${nextNum.toString().padStart(2, '0')}"
    }

    /**
     * Genera un elenco di sigle sequenziali partendo da [baseSigla] per una certa [quantita].
     * Es: "EM-01" con q=3 -> ["EM-01", "EM-02", "EM-03"]
     * Es: "L1" con q=2 -> ["L1", "L2"]
     * Es: "EM" con q=2 -> ["EM-01", "EM-02"]
     */
    fun generateSigle(baseSigla: String, defaultPrefix: String, quantita: Int): List<String> {
        val q = quantita.coerceAtLeast(1)
        val clean = baseSigla.trim()
        val match = Regex("""^(.*?)(\d+)$""").matchEntire(clean)
        val (prefix, startNum, padding) = if (match != null) {
            val p = match.groupValues[1]
            val num = match.groupValues[2].toIntOrNull() ?: 1
            val pad = match.groupValues[2].length
            Triple(p, num, pad)
        } else {
            val p = when {
                clean.isEmpty() -> "${defaultPrefix.ifBlank { "EM" }}-"
                clean.endsWith("-") || clean.endsWith(" ") -> clean
                else -> "$clean-"
            }
            Triple(p, 1, 2)
        }

        return (0 until q).map { offset ->
            val currentNum = startNum + offset
            "$prefix${currentNum.toString().padStart(padding, '0')}"
        }
    }

    /**
     * Trova la prossima sigla disponibile clonando una singola lampada.
     */
    fun findNextSiglaForClone(baseSigla: String, existing: List<LampadaEmergenza>, defaultPrefix: String): String {
        val clean = baseSigla.trim()
        val match = Regex("""^(.*?)(\d+)$""").matchEntire(clean)
        val (prefix, padding) = if (match != null) {
            Pair(match.groupValues[1], match.groupValues[2].length)
        } else {
            Pair(if (clean.isBlank()) "${defaultPrefix.ifBlank { "EM" }}-" else "$clean-", 2)
        }

        val allNumbersWithPrefix = existing.mapNotNull { l ->
            val m = Regex("""^(.*?)(\d+)$""").matchEntire(l.sigla.trim())
            if (m != null && m.groupValues[1] == prefix) {
                m.groupValues[2].toIntOrNull()
            } else null
        }
        val nextNum = (allNumbersWithPrefix.maxOrNull() ?: 0) + 1
        return "$prefix${nextNum.toString().padStart(padding, '0')}"
    }
}

@Composable
fun ModificaLampadaDialog(
    impianto: ImpiantoEmergenza,
    lampada: LampadaEmergenza,
    onDismiss: () -> Unit,
    onConferma: (LampadaEmergenza) -> Unit
) {
    AggiungiLampadaDialog(
        impianto = impianto,
        lampadaDaModificare = lampada,
        onDismiss = onDismiss,
        onConferma = { list ->
            list.firstOrNull()?.let(onConferma)
        }
    )
}

@Composable
fun AggiungiLampadaDialog(
    impianto: ImpiantoEmergenza,
    lampadaDaModificare: LampadaEmergenza? = null,
    onDismiss: () -> Unit,
    onConferma: (List<LampadaEmergenza>) -> Unit
) {
    val isEditMode = lampadaDaModificare != null

    var quantita by remember { mutableStateOf(1) }
    var sigla by remember { mutableStateOf(lampadaDaModificare?.sigla ?: "") }
    var modello by remember { mutableStateOf(lampadaDaModificare?.modello ?: "") }
    var produttore by remember { mutableStateOf(lampadaDaModificare?.produttore ?: "Beghelli") }
    var autonomia by remember { mutableStateOf(lampadaDaModificare?.autonomia ?: "1h") }
    var posizione by remember { mutableStateOf(lampadaDaModificare?.posizione ?: "") }
    var dataUltimaCambio by remember { mutableStateOf(lampadaDaModificare?.dataUltimoCambioBatteria ?: "") }
    var note by remember { mutableStateOf(lampadaDaModificare?.note ?: "") }

    // Suggerisci sigla automatica progressiva se creazione da zero
    val defaultPrefix = if (impianto.codIntervento.isNotBlank()) impianto.codIntervento else "EM"
    val nextSigla = remember(impianto.listaLampade) {
        LampadaGeneratorHelper.calculateNextStartSigla(impianto.listaLampade, defaultPrefix)
    }
    LaunchedEffect(lampadaDaModificare) {
        if (!isEditMode && sigla.isBlank()) sigla = nextSigla
    }

    val generatedSigle = remember(sigla, quantita, defaultPrefix, isEditMode) {
        if (isEditMode) listOf(sigla.trim())
        else LampadaGeneratorHelper.generateSigle(sigla, defaultPrefix, quantita)
    }

    val scrollState = rememberScrollState()

    fun submit() {
        if (modello.isNotBlank() && sigla.isNotBlank()) {
            if (lampadaDaModificare != null) {
                val modificata = lampadaDaModificare.copy(
                    sigla = sigla.trim(),
                    modello = modello.trim(),
                    produttore = produttore.trim().ifBlank { null },
                    autonomia = autonomia.trim().ifBlank { "1h" },
                    posizione = posizione.trim().ifBlank { null },
                    dataUltimoCambioBatteria = dataUltimaCambio.trim().ifBlank { null },
                    note = note.trim().ifBlank { null }
                )
                onConferma(listOf(modificata))
            } else {
                val lista = generatedSigle.map { currSigla ->
                    LampadaEmergenza(
                        id = UUID.randomUUID().toString(),
                        sigla = currSigla,
                        modello = modello.trim(),
                        produttore = produttore.trim().ifBlank { null },
                        autonomia = autonomia.trim().ifBlank { "1h" },
                        posizione = posizione.trim().ifBlank { null },
                        dataUltimoCambioBatteria = dataUltimaCambio.trim().ifBlank { null },
                        note = note.trim().ifBlank { null }
                    )
                }
                onConferma(lista)
            }
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = when {
                        isEditMode -> "Modifica Lampada (${lampadaDaModificare?.sigla})"
                        quantita > 1 -> "Aggiunta Massiva Lampade ($quantita)"
                        else -> "Aggiungi Lampada di Emergenza"
                    },
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Impianto: ${impianto.codIntervento} — ${impianto.nomeCompleto}",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.secondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Quantità e modalità massiva (visibile solo in creazione)
                if (!isEditMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Quantità:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Spacer(modifier = Modifier.width(10.dp))
                                    OutlinedButton(
                                        onClick = { if (quantita > 1) quantita-- },
                                        modifier = Modifier.size(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold)
                                    }
                                    Text(
                                        text = "$quantita",
                                        modifier = Modifier.padding(horizontal = 10.dp),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                    OutlinedButton(
                                        onClick = { if (quantita < 200) quantita++ },
                                        modifier = Modifier.size(32.dp),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Preset rapidi
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    QUANTITA_PRESETS.forEach { preset ->
                                        val isSelected = quantita == preset
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                            border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                            modifier = Modifier.clickable { quantita = preset }
                                        ) {
                                            Text(
                                                text = "$preset",
                                                fontSize = 11.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color.White else Color(0xFF334155),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Banner info se modalità massiva (> 1)
                            if (quantita > 1 && generatedSigle.isNotEmpty()) {
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            tint = Color(0xFF2563EB),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "Verranno create $quantita lampade con sigle da '${generatedSigle.first()}' a '${generatedSigle.last()}'",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Sigla Base + Modello
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = sigla,
                        onValueChange = { sigla = it },
                        label = { Text(if (isEditMode) "Sigla *" else "Sigla Iniziale *") },
                        placeholder = { Text("es. EM-01") },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    OutlinedTextField(
                        value = modello,
                        onValueChange = { modello = it },
                        label = { Text("Modello *") },
                        placeholder = { Text("es. Formula 65 LED, Ecoled 3h...") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            if (modello.isNotEmpty()) {
                                IconButton(onClick = { modello = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Cancella")
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                }

                // Modelli frequenti
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Modelli tipici:", fontSize = 11.sp, color = Color.Gray)
                    MODELLI_SUGGERITI_BEGHELLI.take(3).forEach { mod ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (modello == mod) MaterialTheme.colors.primary.copy(alpha = 0.15f) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (modello == mod) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable { modello = mod }
                        ) {
                            Text(
                                text = mod,
                                fontSize = 10.sp,
                                fontWeight = if (modello == mod) FontWeight.Bold else FontWeight.Normal,
                                color = if (modello == mod) MaterialTheme.colors.primary else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 3. Produttore con chip suggerimenti
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = produttore,
                        onValueChange = { produttore = it },
                        label = { Text("Produttore / Marca") },
                        placeholder = { Text("es. Beghelli, Schneider...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Marchi frequenti:", fontSize = 11.sp, color = Color.Gray)
                        BRAND_SUGGESTIONS_EMERGENZA.forEach { brand ->
                            val isSelected = produttore.equals(brand, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colors.primary else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable {
                                    produttore = if (isSelected) "" else brand
                                }
                            ) {
                                Text(
                                    text = brand,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 4. Autonomia con chip rapidi
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = autonomia,
                        onValueChange = { autonomia = it },
                        label = { Text("Autonomia dichiarata") },
                        placeholder = { Text("es. 1h, 2h, 3h, 8h") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rapida:", fontSize = 11.sp, color = Color.Gray)
                        AUTONOMIA_SUGGESTIONS.forEach { option ->
                            val isSelected = autonomia.equals(option, ignoreCase = true)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) Color(0xFF2E7D32) else Color(0xFFF1F5F9),
                                border = BorderStroke(1.dp, if (isSelected) Color(0xFF2E7D32) else Color(0xFFCBD5E1)),
                                modifier = Modifier.clickable { autonomia = option }
                            ) {
                                Text(
                                    text = option,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // 5. Posizione
                OutlinedTextField(
                    value = posizione,
                    onValueChange = { posizione = it },
                    label = { Text(if (isEditMode) "Posizione / Ubicazione" else "Posizione / Ubicazione (comune)") },
                    placeholder = { Text("es. Corridoio Piano 1, Uscite di Sicurezza, Sala Quadri") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // 6. Data ultimo cambio batteria
                OutlinedTextField(
                    value = dataUltimaCambio,
                    onValueChange = { dataUltimaCambio = it },
                    label = { Text("Data ultimo cambio batteria") },
                    placeholder = { Text("es. 15/03/2024") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )

                // 7. Note Aggiuntive
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note aggiuntive / Dettagli") },
                    placeholder = { Text("es. Batteria sostituita con ricambio originale, test positivo...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = modello.isNotBlank() && sigla.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = MaterialTheme.colors.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    if (isEditMode) Icons.Default.Check else Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isEditMode -> "Salva Modifiche"
                        quantita > 1 -> "Aggiungi $quantita Lampade"
                        else -> "Aggiungi Lampada"
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        modifier = Modifier.fillMaxWidth(0.85f)
    )
}

