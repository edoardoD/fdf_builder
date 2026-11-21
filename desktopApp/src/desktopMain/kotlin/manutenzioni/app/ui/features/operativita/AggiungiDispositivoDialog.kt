package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import manutenzioni.app.ui.components.ItalianDateInputField
import manutenzioni.app.ui.components.spreadsheet.SpreadsheetColumn
import manutenzioni.app.ui.components.spreadsheet.SpreadsheetCreationDialog
import manutenzioni.domain.model.DispositivoRilevazione
import manutenzioni.domain.model.TipoComponenteComuneAntincendio
import manutenzioni.domain.model.TipoModuloAntincendio
import manutenzioni.domain.model.TipoRivelatore
import manutenzioni.domain.service.DateValidationUtils
import java.util.UUID

sealed interface SezioneInserimentoAntincendio {
    object Comuni : SezioneInserimentoAntincendio
    data class Loop(val numero: Int) : SezioneInserimentoAntincendio
}

enum class FamigliaDispositivoLoop(val label: String) {
    RIVELATORE("Rivelatori"),
    MODULO("Moduli")
}

data class DispositivoComuneDraft(
    val id: String = UUID.randomUUID().toString(),
    val tipo: TipoComponenteComuneAntincendio = TipoComponenteComuneAntincendio.CENTRALE_INCENDIO,
    val indirizzo: String = "",
    val descrizione: String = "",
    val installazione: String = "",
    val zona: String = "",
    val dataInstallazione: String = "",
    val note: String = ""
)

data class DispositivoLoopDraft(
    val id: String = UUID.randomUUID().toString(),
    val loop: Int,
    val famiglia: FamigliaDispositivoLoop,
    val numeroIndirizzo: Int? = null,
    val tipoRivelatore: TipoRivelatore = TipoRivelatore.FUMO,
    val tipoModulo: TipoModuloAntincendio = TipoModuloAntincendio.PULSANTE,
    val tipoCustom: String = "",
    val descrizione: String = "",
    val installazione: String = "",
    val zona: String = "",
    val dataInstallazione: String = "",
    val note: String = ""
)

private val INSTALLAZIONI_SUGGERITE = listOf(
    "A VISTA", "CONTROSOFFITTO", "PAVIMENTO", "CANALE"
)

/**
 * Dialog di inserimento multi-riga stile foglio di calcolo per l'impianto Rilevazione Incendi (RI).
 * Consente di censire in sequenza:
 * - Componenti Comuni (centrale, alimentatori, sirene, ecc.) con indirizzo specifico
 * - Loop 1..8, ciascuno suddiviso in Rivelatori (1..150) e Moduli (1..150)
 * salvando tutte le bozze in un'unica operazione.
 */
@Composable
fun MassiveDispositivoAntincendioDialog(
    dispositiviEsistenti: List<DispositivoRilevazione>,
    cantiereNome: String? = null,
    onDismiss: () -> Unit,
    onConfirmBatch: (List<DispositivoRilevazione>) -> Unit
) {
    var sezioneAttiva by remember { mutableStateOf<SezioneInserimentoAntincendio>(SezioneInserimentoAntincendio.Loop(1)) }
    var famigliaAttiva by remember { mutableStateOf(FamigliaDispositivoLoop.RIVELATORE) }

    val comuniDrafts = remember { mutableStateListOf<DispositivoComuneDraft>() }
    val loopDrafts = remember { mutableStateListOf<DispositivoLoopDraft>() }
    var validationError by remember { mutableStateOf<String?>(null) }

    fun nextFreeIndirizzoFor(loopNum: Int, famiglia: FamigliaDispositivoLoop): Int {
        val usatiEsistenti = when (famiglia) {
            FamigliaDispositivoLoop.RIVELATORE -> dispositiviEsistenti
                .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                .filter { it.loop == loopNum }
                .map { it.numeroIndirizzo }
            FamigliaDispositivoLoop.MODULO -> dispositiviEsistenti
                .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
                .filter { it.loop == loopNum }
                .map { it.numeroIndirizzo }
        }.toSet()

        val usatiDraft = loopDrafts
            .filter { it.loop == loopNum && it.famiglia == famiglia }
            .mapNotNull { it.numeroIndirizzo }
            .toSet()

        val usati = usatiEsistenti + usatiDraft
        return (1..150).firstOrNull { it !in usati } ?: 150
    }

    LaunchedEffect(Unit) {
        if (loopDrafts.isEmpty()) {
            loopDrafts.add(
                DispositivoLoopDraft(
                    loop = 1,
                    famiglia = FamigliaDispositivoLoop.RIVELATORE,
                    numeroIndirizzo = nextFreeIndirizzoFor(1, FamigliaDispositivoLoop.RIVELATORE)
                )
            )
        }
    }

    fun ensureRowForActiveContext() {
        when (val sez = sezioneAttiva) {
            SezioneInserimentoAntincendio.Comuni -> {
                if (comuniDrafts.isEmpty()) {
                    comuniDrafts.add(DispositivoComuneDraft(indirizzo = "CENTR.1"))
                }
            }
            is SezioneInserimentoAntincendio.Loop -> {
                val hasRows = loopDrafts.any { it.loop == sez.numero && it.famiglia == famigliaAttiva }
                if (!hasRows) {
                    loopDrafts.add(
                        DispositivoLoopDraft(
                            loop = sez.numero,
                            famiglia = famigliaAttiva,
                            numeroIndirizzo = nextFreeIndirizzoFor(sez.numero, famigliaAttiva)
                        )
                    )
                }
            }
        }
    }

    val comuniColumns = remember {
        listOf(
            SpreadsheetColumn.Dropdown<DispositivoComuneDraft, TipoComponenteComuneAntincendio>(
                header = "Tipo Componente",
                weight = 0.20f,
                options = TipoComponenteComuneAntincendio.values().toList(),
                optionLabel = { it.label },
                getter = { it.tipo },
                setter = { row, opt -> row.copy(tipo = opt) }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Indirizzo *",
                weight = 0.12f,
                placeholder = "es. CENTR.1",
                uppercase = true,
                getter = { it.indirizzo },
                setter = { row, v -> row.copy(indirizzo = v) },
                isError = { it.indirizzo.isBlank() && it.descrizione.isNotBlank() }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Descrizione / Ubicazione *",
                weight = 0.25f,
                placeholder = "es. LOCALE PORTINERIA",
                uppercase = true,
                getter = { it.descrizione },
                setter = { row, v -> row.copy(descrizione = v) },
                isError = { it.descrizione.isBlank() && it.indirizzo.isNotBlank() }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Installazione",
                weight = 0.12f,
                placeholder = "A VISTA",
                uppercase = true,
                getter = { it.installazione },
                setter = { row, v -> row.copy(installazione = v) }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Zona",
                weight = 0.09f,
                placeholder = "PT",
                uppercase = true,
                getter = { it.zona },
                setter = { row, v -> row.copy(zona = v) }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Data Inst./Rev.",
                weight = 0.11f,
                placeholder = "gg/mm/aaaa",
                getter = { it.dataInstallazione },
                setter = { row, v -> row.copy(dataInstallazione = v) },
                isError = { it.dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(it.dataInstallazione) }
            ),
            SpreadsheetColumn.Text<DispositivoComuneDraft>(
                header = "Note",
                weight = 0.11f,
                placeholder = "Opzionale",
                getter = { it.note },
                setter = { row, v -> row.copy(note = v) }
            )
        )
    }

    fun buildLoopColumns(loopNum: Int, famiglia: FamigliaDispositivoLoop): List<SpreadsheetColumn<DispositivoLoopDraft>> {
        val usatiEsistenti = when (famiglia) {
            FamigliaDispositivoLoop.RIVELATORE -> dispositiviEsistenti
                .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                .filter { it.loop == loopNum }
                .map { it.numeroIndirizzo }
            FamigliaDispositivoLoop.MODULO -> dispositiviEsistenti
                .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
                .filter { it.loop == loopNum }
                .map { it.numeroIndirizzo }
        }.toSet()

        val tipoColumn: SpreadsheetColumn<DispositivoLoopDraft> = when (famiglia) {
            FamigliaDispositivoLoop.RIVELATORE -> SpreadsheetColumn.Dropdown(
                header = "Tipo Rivelatore",
                weight = 0.18f,
                options = TipoRivelatore.values().toList(),
                optionLabel = { it.label },
                getter = { it.tipoRivelatore },
                setter = { row, opt -> row.copy(tipoRivelatore = opt) }
            )
            FamigliaDispositivoLoop.MODULO -> SpreadsheetColumn.Dropdown(
                header = "Tipo Modulo",
                weight = 0.18f,
                options = TipoModuloAntincendio.values().toList(),
                optionLabel = { it.label },
                getter = { it.tipoModulo },
                setter = { row, opt -> row.copy(tipoModulo = opt) }
            )
        }

        return listOf(
            SpreadsheetColumn.NumericInt(
                header = "Ind. (1..150) *",
                weight = 0.10f,
                range = 1..150,
                placeholder = "1..150",
                getter = { it.numeroIndirizzo },
                setter = { row, num -> row.copy(numeroIndirizzo = num) },
                isError = { row ->
                    val n = row.numeroIndirizzo
                    if (n == null) row.descrizione.isNotBlank()
                    else n !in 1..150 || n in usatiEsistenti ||
                        loopDrafts.count { it.loop == loopNum && it.famiglia == famiglia && it.numeroIndirizzo == n } > 1
                }
            ),
            tipoColumn,
            SpreadsheetColumn.Text(
                header = "Descrizione / Ubicazione *",
                weight = 0.27f,
                placeholder = "es. CORRIDOIO UFFICI P1",
                uppercase = true,
                getter = { it.descrizione },
                setter = { row, v -> row.copy(descrizione = v) },
                isError = { it.descrizione.isBlank() && it.numeroIndirizzo != null }
            ),
            SpreadsheetColumn.Text(
                header = "Installazione",
                weight = 0.13f,
                placeholder = "CONTROSOFFITTO",
                uppercase = true,
                getter = { it.installazione },
                setter = { row, v -> row.copy(installazione = v) }
            ),
            SpreadsheetColumn.Text(
                header = "Zona",
                weight = 0.09f,
                placeholder = "PINT",
                uppercase = true,
                getter = { it.zona },
                setter = { row, v -> row.copy(zona = v) }
            ),
            SpreadsheetColumn.Text(
                header = "Data Inst./Rev.",
                weight = 0.11f,
                placeholder = "gg/mm/aaaa",
                getter = { it.dataInstallazione },
                setter = { row, v -> row.copy(dataInstallazione = v) },
                isError = { it.dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(it.dataInstallazione) }
            ),
            SpreadsheetColumn.Text(
                header = "Note",
                weight = 0.12f,
                placeholder = "Opzionale",
                getter = { it.note },
                setter = { row, v -> row.copy(note = v) }
            )
        )
    }

    val totalComuniReady = comuniDrafts.count { it.indirizzo.isNotBlank() && it.descrizione.isNotBlank() }
    val totalLoopReady = loopDrafts.count { it.numeroIndirizzo in 1..150 && it.descrizione.isNotBlank() }
    val totalReady = totalComuniReady + totalLoopReady

    @Composable
    fun sectionHeaderControls() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val isComuni = sezioneAttiva is SezioneInserimentoAntincendio.Comuni
                val comuniCount = dispositiviEsistenti.count { it is DispositivoRilevazione.ComponenteComune } +
                    comuniDrafts.count { it.descrizione.isNotBlank() }

                SectionSelectorChip(
                    label = if (comuniCount > 0) "Comuni ($comuniCount)" else "Componenti Comuni",
                    selected = isComuni,
                    onClick = {
                        sezioneAttiva = SezioneInserimentoAntincendio.Comuni
                        ensureRowForActiveContext()
                    },
                    modifier = Modifier.weight(1.4f)
                )

                (1..4).forEach { loopNum ->
                    val isSelected = (sezioneAttiva as? SezioneInserimentoAntincendio.Loop)?.numero == loopNum
                    val count = dispositiviEsistenti.count {
                        (it is DispositivoRilevazione.RivelatoreLoop && it.loop == loopNum) ||
                            (it is DispositivoRilevazione.ModuloLoop && it.loop == loopNum)
                    } + loopDrafts.count { it.loop == loopNum && it.descrizione.isNotBlank() }

                    SectionSelectorChip(
                        label = if (count > 0) "Loop $loopNum ($count)" else "Loop $loopNum",
                        selected = isSelected,
                        onClick = {
                            sezioneAttiva = SezioneInserimentoAntincendio.Loop(loopNum)
                            ensureRowForActiveContext()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                (5..8).forEach { loopNum ->
                    val isSelected = (sezioneAttiva as? SezioneInserimentoAntincendio.Loop)?.numero == loopNum
                    val count = dispositiviEsistenti.count {
                        (it is DispositivoRilevazione.RivelatoreLoop && it.loop == loopNum) ||
                            (it is DispositivoRilevazione.ModuloLoop && it.loop == loopNum)
                    } + loopDrafts.count { it.loop == loopNum && it.descrizione.isNotBlank() }

                    SectionSelectorChip(
                        label = if (count > 0) "Loop $loopNum ($count)" else "Loop $loopNum",
                        selected = isSelected,
                        onClick = {
                            sezioneAttiva = SezioneInserimentoAntincendio.Loop(loopNum)
                            ensureRowForActiveContext()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                if (sezioneAttiva is SezioneInserimentoAntincendio.Loop) {
                    Spacer(Modifier.width(8.dp))
                    FamigliaDispositivoLoop.values().forEach { fam ->
                        val active = famigliaAttiva == fam
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (active) Color(0xFF0F172A) else Color(0xFFF1F5F9),
                            border = BorderStroke(1.dp, if (active) Color(0xFF0F172A) else Color(0xFFCBD5E1)),
                            modifier = Modifier.clickable {
                                famigliaAttiva = fam
                                ensureRowForActiveContext()
                            }
                        ) {
                            Text(
                                text = fam.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (active) Color.White else Color(0xFF334155),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    fun confirmAll() {
        validationError = null
        val result = mutableListOf<DispositivoRilevazione>()

        for ((idx, d) in comuniDrafts.withIndex()) {
            val isTouched = d.indirizzo.isNotBlank() || d.descrizione.isNotBlank() ||
                d.installazione.isNotBlank() || d.zona.isNotBlank() || d.dataInstallazione.isNotBlank()
            if (!isTouched) continue
            if (d.indirizzo.isBlank() || d.descrizione.isBlank()) {
                validationError = "Componenti Comuni riga ${idx + 1}: Indirizzo e Descrizione sono obbligatori."
                sezioneAttiva = SezioneInserimentoAntincendio.Comuni
                return
            }
            if (d.dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(d.dataInstallazione)) {
                validationError = "Componenti Comuni riga ${idx + 1}: data non valida (formato gg/mm/aaaa)."
                sezioneAttiva = SezioneInserimentoAntincendio.Comuni
                return
            }
            result.add(
                DispositivoRilevazione.ComponenteComune(
                    tipo = d.tipo,
                    indirizzo = d.indirizzo.trim().uppercase(),
                    descrizione = d.descrizione.trim().uppercase(),
                    installazione = d.installazione.trim().ifBlank { null }?.uppercase(),
                    zona = d.zona.trim().ifBlank { null }?.uppercase(),
                    dataInstallazione = d.dataInstallazione.trim().ifBlank { null },
                    note = d.note.trim().ifBlank { null }
                )
            )
        }

        val seenByFamily = mutableSetOf<Triple<Int, FamigliaDispositivoLoop, Int>>()
        val existingSet = buildSet {
            dispositiviEsistenti.forEach { d ->
                when (d) {
                    is DispositivoRilevazione.RivelatoreLoop -> add(Triple(d.loop, FamigliaDispositivoLoop.RIVELATORE, d.numeroIndirizzo))
                    is DispositivoRilevazione.ModuloLoop -> add(Triple(d.loop, FamigliaDispositivoLoop.MODULO, d.numeroIndirizzo))
                    is DispositivoRilevazione.ComponenteComune -> Unit
                }
            }
        }

        for (d in loopDrafts) {
            val isTouched = d.descrizione.isNotBlank() || d.installazione.isNotBlank() ||
                d.zona.isNotBlank() || d.dataInstallazione.isNotBlank() || d.note.isNotBlank()
            if (!isTouched) continue

            val addr = d.numeroIndirizzo
            if (addr == null || addr !in 1..150) {
                validationError = "Loop ${d.loop} (${d.famiglia.label}): l'indirizzo deve essere un numero tra 1 e 150."
                sezioneAttiva = SezioneInserimentoAntincendio.Loop(d.loop)
                famigliaAttiva = d.famiglia
                return
            }
            if (d.descrizione.isBlank()) {
                validationError = "Loop ${d.loop} (${d.famiglia.label}) ind. $addr: la descrizione/ubicazione è obbligatoria."
                sezioneAttiva = SezioneInserimentoAntincendio.Loop(d.loop)
                famigliaAttiva = d.famiglia
                return
            }
            if (d.dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(d.dataInstallazione)) {
                validationError = "Loop ${d.loop} (${d.famiglia.label}) ind. $addr: data non valida (formato gg/mm/aaaa)."
                sezioneAttiva = SezioneInserimentoAntincendio.Loop(d.loop)
                famigliaAttiva = d.famiglia
                return
            }
            val key = Triple(d.loop, d.famiglia, addr)
            if (key in existingSet || !seenByFamily.add(key)) {
                validationError = "Loop ${d.loop} (${d.famiglia.label}): indirizzo $addr duplicato."
                sezioneAttiva = SezioneInserimentoAntincendio.Loop(d.loop)
                famigliaAttiva = d.famiglia
                return
            }

            result.add(
                when (d.famiglia) {
                    FamigliaDispositivoLoop.RIVELATORE -> DispositivoRilevazione.RivelatoreLoop(
                        loop = d.loop,
                        numeroIndirizzo = addr,
                        tipo = d.tipoRivelatore,
                        descrizione = d.descrizione.trim().uppercase(),
                        installazione = d.installazione.trim().ifBlank { null }?.uppercase(),
                        zona = d.zona.trim().ifBlank { null }?.uppercase(),
                        dataInstallazione = d.dataInstallazione.trim().ifBlank { null },
                        note = d.note.trim().ifBlank { null }
                    )
                    FamigliaDispositivoLoop.MODULO -> DispositivoRilevazione.ModuloLoop(
                        loop = d.loop,
                        numeroIndirizzo = addr,
                        tipo = d.tipoModulo,
                        tipoCustom = d.tipoCustom.trim().ifBlank { null },
                        descrizione = d.descrizione.trim().uppercase(),
                        installazione = d.installazione.trim().ifBlank { null }?.uppercase(),
                        zona = d.zona.trim().ifBlank { null }?.uppercase(),
                        dataInstallazione = d.dataInstallazione.trim().ifBlank { null },
                        note = d.note.trim().ifBlank { null }
                    )
                }
            )
        }

        if (result.isEmpty()) {
            validationError = "Compila almeno un componente con Descrizione prima di salvare."
            return
        }
        onConfirmBatch(result)
    }

    val subtitlePrefix = cantiereNome?.takeIf { it.isNotBlank() }?.let { "Cantiere: $it — " }.orEmpty()

    when (val sez = sezioneAttiva) {
        SezioneInserimentoAntincendio.Comuni -> {
            SpreadsheetCreationDialog(
                title = "ELENCO COMPONENTI IMPIANTO RILEVAZIONE INCENDI",
                subtitle = "${subtitlePrefix}Inserimento rapido Componenti Comuni e Loop 1..8 (Rivelatori e Moduli, ind. 1..150)",
                rows = comuniDrafts,
                rowKey = { _, r -> r.id },
                columns = comuniColumns,
                onUpdateRow = { idx, updated ->
                    comuniDrafts[idx] = updated
                    validationError = null
                },
                onAddRow = {
                    comuniDrafts.add(DispositivoComuneDraft(indirizzo = "COM.${comuniDrafts.size + 1}"))
                },
                onAddMultipleRows = { count ->
                    repeat(count) {
                        comuniDrafts.add(DispositivoComuneDraft(indirizzo = "COM.${comuniDrafts.size + 1}"))
                    }
                },
                onDuplicateRow = { idx, row ->
                    comuniDrafts.add(
                        idx + 1,
                        row.copy(id = UUID.randomUUID().toString(), indirizzo = "COM.${comuniDrafts.size + 1}")
                    )
                },
                onRemoveRow = { idx ->
                    if (comuniDrafts.size > 1) comuniDrafts.removeAt(idx)
                    else comuniDrafts[0] = DispositivoComuneDraft()
                },
                headerControls = { sectionHeaderControls() },
                footerSummary = "Pronti al salvataggio: $totalReady componenti (Comuni: $totalComuniReady • Loop: $totalLoopReady)",
                validationError = validationError,
                confirmLabel = "Salva Componenti ($totalReady)",
                isConfirmEnabled = totalReady > 0,
                onDismiss = onDismiss,
                onConfirm = ::confirmAll
            )
        }

        is SezioneInserimentoAntincendio.Loop -> {
            val activeRows = loopDrafts.filter { it.loop == sez.numero && it.famiglia == famigliaAttiva }
            val columns = remember(sez.numero, famigliaAttiva, dispositiviEsistenti, loopDrafts.size) {
                buildLoopColumns(sez.numero, famigliaAttiva)
            }

            SpreadsheetCreationDialog(
                title = "ELENCO COMPONENTI IMPIANTO RILEVAZIONE INCENDI",
                subtitle = "${subtitlePrefix}Sezione attiva: Loop ${sez.numero} — ${famigliaAttiva.label} (indirizzi 1..150)",
                rows = activeRows,
                rowKey = { _, r -> r.id },
                columns = columns,
                onUpdateRow = { idx, updated ->
                    val targetId = activeRows[idx].id
                    val globalIdx = loopDrafts.indexOfFirst { it.id == targetId }
                    if (globalIdx >= 0) loopDrafts[globalIdx] = updated
                    validationError = null
                },
                onAddRow = {
                    loopDrafts.add(
                        DispositivoLoopDraft(
                            loop = sez.numero,
                            famiglia = famigliaAttiva,
                            numeroIndirizzo = nextFreeIndirizzoFor(sez.numero, famigliaAttiva)
                        )
                    )
                },
                onAddMultipleRows = { count ->
                    repeat(count) {
                        loopDrafts.add(
                            DispositivoLoopDraft(
                                loop = sez.numero,
                                famiglia = famigliaAttiva,
                                numeroIndirizzo = nextFreeIndirizzoFor(sez.numero, famigliaAttiva)
                            )
                        )
                    }
                },
                onDuplicateRow = { idx, row ->
                    val nextAddr = nextFreeIndirizzoFor(sez.numero, famigliaAttiva)
                    val targetId = activeRows[idx].id
                    val globalIdx = loopDrafts.indexOfFirst { it.id == targetId }
                    val insertPos = if (globalIdx >= 0) globalIdx + 1 else loopDrafts.size
                    loopDrafts.add(
                        insertPos,
                        row.copy(id = UUID.randomUUID().toString(), numeroIndirizzo = nextAddr)
                    )
                },
                onRemoveRow = { idx ->
                    val targetId = activeRows[idx].id
                    loopDrafts.removeAll { it.id == targetId }
                    ensureRowForActiveContext()
                },
                headerControls = { sectionHeaderControls() },
                footerSummary = "Pronti al salvataggio: $totalReady componenti (Comuni: $totalComuniReady • Loop: $totalLoopReady)",
                validationError = validationError,
                confirmLabel = "Salva Componenti ($totalReady)",
                isConfirmEnabled = totalReady > 0,
                onDismiss = onDismiss,
                onConfirm = ::confirmAll
            )
        }
    }
}

@Composable
private fun SectionSelectorChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (selected) MaterialTheme.colors.primary else MaterialTheme.colors.surface,
        border = BorderStroke(1.dp, if (selected) MaterialTheme.colors.primary else Color(0xFFCBD5E1)),
        elevation = if (selected) 2.dp else 0.dp,
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF1E293B),
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
    }
}

/**
 * Dialog di modifica puntuale per un singolo componente già registrato
 * ([DispositivoRilevazione.ComponenteComune], [DispositivoRilevazione.RivelatoreLoop], [DispositivoRilevazione.ModuloLoop]).
 */
@Composable
fun AggiungiDispositivoDialog(
    dispositivoIniziale: DispositivoRilevazione,
    dispositiviEsistenti: List<DispositivoRilevazione> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (DispositivoRilevazione) -> Unit
) {
    var selectedLoop by remember {
        mutableStateOf(
            when (dispositivoIniziale) {
                is DispositivoRilevazione.RivelatoreLoop -> dispositivoIniziale.loop
                is DispositivoRilevazione.ModuloLoop -> dispositivoIniziale.loop
                is DispositivoRilevazione.ComponenteComune -> 1
            }
        )
    }
    var indirizzoComune by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.ComponenteComune)?.indirizzo ?: "")
    }
    var numeroIndirizzoLoop by remember {
        mutableStateOf(
            when (dispositivoIniziale) {
                is DispositivoRilevazione.RivelatoreLoop -> dispositivoIniziale.numeroIndirizzo.toString()
                is DispositivoRilevazione.ModuloLoop -> dispositivoIniziale.numeroIndirizzo.toString()
                is DispositivoRilevazione.ComponenteComune -> "1"
            }
        )
    }
    var tipoComune by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.ComponenteComune)?.tipo ?: TipoComponenteComuneAntincendio.CENTRALE_INCENDIO)
    }
    var tipoCustomComune by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.ComponenteComune)?.tipoCustom ?: "")
    }
    var tipoRivelatore by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.RivelatoreLoop)?.tipo ?: TipoRivelatore.FUMO)
    }
    var tipoModulo by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.ModuloLoop)?.tipo ?: TipoModuloAntincendio.PULSANTE)
    }
    var tipoCustomModulo by remember {
        mutableStateOf((dispositivoIniziale as? DispositivoRilevazione.ModuloLoop)?.tipoCustom ?: "")
    }

    var installazione by remember { mutableStateOf(dispositivoIniziale.installazione ?: "") }
    var descrizione by remember { mutableStateOf(dispositivoIniziale.descrizione) }
    var zona by remember { mutableStateOf(dispositivoIniziale.zona ?: "") }
    var dataInstallazione by remember { mutableStateOf(dispositivoIniziale.dataInstallazione ?: "") }
    var note by remember { mutableStateOf(dispositivoIniziale.note ?: "") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            elevation = 8.dp,
            modifier = Modifier.width(600.dp).wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colors.primary, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = when (dispositivoIniziale) {
                            is DispositivoRilevazione.ComponenteComune -> "Modifica Componente Comune"
                            is DispositivoRilevazione.RivelatoreLoop -> "Modifica Rivelatore (Loop)"
                            is DispositivoRilevazione.ModuloLoop -> "Modifica Modulo (Loop)"
                        },
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                when (dispositivoIniziale) {
                    is DispositivoRilevazione.ComponenteComune -> {
                        Text("Tipologia Componente Comune:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        EnumDropdownSelector(
                            selectedLabel = tipoComune.label,
                            options = TipoComponenteComuneAntincendio.values().toList(),
                            optionLabel = { it.label },
                            onSelected = { tipoComune = it }
                        )
                        if (tipoComune == TipoComponenteComuneAntincendio.ALTRO) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = tipoCustomComune,
                                onValueChange = { tipoCustomComune = it },
                                label = { Text("Specifica Tipologia") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = indirizzoComune,
                            onValueChange = { indirizzoComune = it; validationError = null },
                            label = { Text("Indirizzo Specifico *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }

                    is DispositivoRilevazione.RivelatoreLoop, is DispositivoRilevazione.ModuloLoop -> {
                        Text("Loop Centrale (1..8):", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            (1..4).forEach { loopNum ->
                                SectionSelectorChip(
                                    label = "LOOP $loopNum",
                                    selected = selectedLoop == loopNum,
                                    onClick = { selectedLoop = loopNum },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            (5..8).forEach { loopNum ->
                                SectionSelectorChip(
                                    label = "LOOP $loopNum",
                                    selected = selectedLoop == loopNum,
                                    onClick = { selectedLoop = loopNum },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = numeroIndirizzoLoop,
                                onValueChange = { v ->
                                    numeroIndirizzoLoop = v.filter { it.isDigit() }
                                    validationError = null
                                },
                                label = { Text("Indirizzo (1..150) *") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Column(modifier = Modifier.weight(1.4f)) {
                                Text("Tipologia:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Spacer(Modifier.height(6.dp))
                                if (dispositivoIniziale is DispositivoRilevazione.RivelatoreLoop) {
                                    EnumDropdownSelector(
                                        selectedLabel = tipoRivelatore.label,
                                        options = TipoRivelatore.values().toList(),
                                        optionLabel = { it.label },
                                        onSelected = { tipoRivelatore = it }
                                    )
                                } else {
                                    EnumDropdownSelector(
                                        selectedLabel = tipoModulo.label,
                                        options = TipoModuloAntincendio.values().toList(),
                                        optionLabel = { it.label },
                                        onSelected = { tipoModulo = it }
                                    )
                                }
                            }
                        }

                        if (dispositivoIniziale is DispositivoRilevazione.ModuloLoop && tipoModulo == TipoModuloAntincendio.ALTRO) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = tipoCustomModulo,
                                onValueChange = { tipoCustomModulo = it },
                                label = { Text("Modello / Sigla Modulo Custom") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = descrizione,
                    onValueChange = { descrizione = it; validationError = null },
                    label = { Text("Descrizione / Ubicazione Locale *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = installazione,
                        onValueChange = { installazione = it },
                        label = { Text("Installazione") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = zona,
                        onValueChange = { zona = it },
                        label = { Text("Zona / Piano") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.padding(top = 6.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ItalianDateInputField(
                        value = dataInstallazione,
                        onValueChange = { dataInstallazione = it },
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
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        color = MaterialTheme.colors.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Annulla")
                    }
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (descrizione.isBlank()) {
                                validationError = "La descrizione/ubicazione è obbligatoria."
                                return@Button
                            }
                            if (dataInstallazione.isNotBlank() && !DateValidationUtils.isValidItalianDate(dataInstallazione)) {
                                validationError = "Inserisci una data valida (GG/MM/AAAA)."
                                return@Button
                            }

                            val result: DispositivoRilevazione = when (dispositivoIniziale) {
                                is DispositivoRilevazione.ComponenteComune -> {
                                    if (indirizzoComune.isBlank()) {
                                        validationError = "L'indirizzo del componente comune è obbligatorio."
                                        return@Button
                                    }
                                    dispositivoIniziale.copy(
                                        tipo = tipoComune,
                                        tipoCustom = tipoCustomComune.trim().ifBlank { null },
                                        indirizzo = indirizzoComune.trim().uppercase(),
                                        descrizione = descrizione.trim().uppercase(),
                                        installazione = installazione.trim().ifBlank { null }?.uppercase(),
                                        zona = zona.trim().ifBlank { null }?.uppercase(),
                                        dataInstallazione = dataInstallazione.trim().ifBlank { null },
                                        note = note.trim().ifBlank { null }
                                    )
                                }

                                is DispositivoRilevazione.RivelatoreLoop -> {
                                    val num = numeroIndirizzoLoop.toIntOrNull()
                                    if (num == null || num !in 1..150) {
                                        validationError = "L'indirizzo del rivelatore deve essere compreso tra 1 e 150."
                                        return@Button
                                    }
                                    val dup = dispositiviEsistenti
                                        .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
                                        .any { it.id != dispositivoIniziale.id && it.loop == selectedLoop && it.numeroIndirizzo == num }
                                    if (dup) {
                                        validationError = "Esiste già un rivelatore con indirizzo $num nel Loop $selectedLoop."
                                        return@Button
                                    }
                                    dispositivoIniziale.copy(
                                        loop = selectedLoop,
                                        numeroIndirizzo = num,
                                        tipo = tipoRivelatore,
                                        descrizione = descrizione.trim().uppercase(),
                                        installazione = installazione.trim().ifBlank { null }?.uppercase(),
                                        zona = zona.trim().ifBlank { null }?.uppercase(),
                                        dataInstallazione = dataInstallazione.trim().ifBlank { null },
                                        note = note.trim().ifBlank { null }
                                    )
                                }

                                is DispositivoRilevazione.ModuloLoop -> {
                                    val num = numeroIndirizzoLoop.toIntOrNull()
                                    if (num == null || num !in 1..150) {
                                        validationError = "L'indirizzo del modulo deve essere compreso tra 1 e 150."
                                        return@Button
                                    }
                                    val dup = dispositiviEsistenti
                                        .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
                                        .any { it.id != dispositivoIniziale.id && it.loop == selectedLoop && it.numeroIndirizzo == num }
                                    if (dup) {
                                        validationError = "Esiste già un modulo con indirizzo $num nel Loop $selectedLoop."
                                        return@Button
                                    }
                                    dispositivoIniziale.copy(
                                        loop = selectedLoop,
                                        numeroIndirizzo = num,
                                        tipo = tipoModulo,
                                        tipoCustom = tipoCustomModulo.trim().ifBlank { null },
                                        descrizione = descrizione.trim().uppercase(),
                                        installazione = installazione.trim().ifBlank { null }?.uppercase(),
                                        zona = zona.trim().ifBlank { null }?.uppercase(),
                                        dataInstallazione = dataInstallazione.trim().ifBlank { null },
                                        note = note.trim().ifBlank { null }
                                    )
                                }
                            }
                            onConfirm(result)
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.primary,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Salva Modifiche")
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> EnumDropdownSelector(
    selectedLabel: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(selectedLabel, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color(0xFF0F172A))
                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF64748B))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(onClick = { onSelected(opt); expanded = false }) {
                    Text(optionLabel(opt), fontSize = 13.sp)
                }
            }
        }
    }
}
