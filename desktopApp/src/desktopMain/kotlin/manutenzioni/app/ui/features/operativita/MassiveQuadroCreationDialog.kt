package manutenzioni.app.ui.features.operativita

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import manutenzioni.app.ui.components.SiglaCloneHelper
import manutenzioni.app.ui.components.spreadsheet.SpreadsheetColumn
import manutenzioni.app.ui.components.spreadsheet.SpreadsheetCreationDialog
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.QuadroBT
import java.util.UUID

data class QuadroDraft(
    val id: String = UUID.randomUUID().toString(),
    val sigla: String = "",
    val descrizione: String = "",
    val interruttori: List<InterruttoreBT> = emptyList()
)

@Composable
fun MassiveQuadroCreationDialog(
    cantiereId: String,
    templateQuadro: Impianto?,
    onDismiss: () -> Unit,
    onSave: (List<QuadroBT>) -> Unit,
    interruttoriDisponibili: List<InterruttoreBT> = emptyList()
) {
    val drafts = remember { mutableStateListOf(QuadroDraft()) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val columns = remember(interruttoriDisponibili) {
        listOf(
            SpreadsheetColumn.Text<QuadroDraft>(
                header = "Sigla *",
                weight = 0.25f,
                placeholder = "es. QG, Q1",
                uppercase = true,
                getter = { it.sigla },
                setter = { row, value -> row.copy(sigla = value) },
                isError = { it.sigla.isBlank() && it.descrizione.isNotBlank() }
            ),
            SpreadsheetColumn.Text<QuadroDraft>(
                header = "Descrizione Quadro",
                weight = 0.50f,
                placeholder = "es. Quadro Generale Locale Tecnico",
                getter = { it.descrizione },
                setter = { row, value -> row.copy(descrizione = value) }
            ),
            SpreadsheetColumn.Custom<QuadroDraft>(
                header = "Componenti Standard",
                weight = 0.25f,
                content = { row, onUpdate ->
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.fillMaxWidth().height(28.dp)
                        ) {
                            Text("${row.interruttori.size} Comp.", fontSize = 11.sp)
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            if (interruttoriDisponibili.isEmpty()) {
                                DropdownMenuItem(onClick = { expanded = false }) {
                                    Text("Nessun componente standard trovato", fontSize = 12.sp)
                                }
                            } else {
                                interruttoriDisponibili.forEach { interruttore ->
                                    DropdownMenuItem(
                                        onClick = {
                                            val newList = row.interruttori + interruttore.copy(id = UUID.randomUUID().toString())
                                            onUpdate(row.copy(interruttori = newList))
                                            expanded = false
                                        }
                                    ) {
                                        Text("+ ${interruttore.nome}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            )
        )
    }

    val validCount = drafts.count { it.sigla.isNotBlank() }

    SpreadsheetCreationDialog(
        title = "Aggiunta Rapida Quadri Elettrici",
        subtitle = "Compila in sequenza Sigla, Descrizione e componenti iniziali stile foglio di calcolo.",
        rows = drafts,
        rowKey = { _, draft -> draft.id },
        columns = columns,
        onUpdateRow = { idx, updated ->
            drafts[idx] = updated
            validationError = null
        },
        onAddRow = {
            val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                baseSigla = drafts.lastOrNull()?.sigla?.ifBlank { "Q1" } ?: "Q1",
                existingSigle = drafts.map { it.sigla },
                defaultPrefix = "Q"
            )
            drafts.add(QuadroDraft(sigla = if (drafts.any { it.sigla.isNotBlank() }) nextSigla else ""))
        },
        onAddMultipleRows = { count ->
            repeat(count) {
                val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                    baseSigla = drafts.lastOrNull()?.sigla?.ifBlank { "Q1" } ?: "Q1",
                    existingSigle = drafts.map { it.sigla },
                    defaultPrefix = "Q"
                )
                drafts.add(QuadroDraft(sigla = if (drafts.any { it.sigla.isNotBlank() }) nextSigla else ""))
            }
        },
        onDuplicateRow = { idx, row ->
            val nextSigla = SiglaCloneHelper.findNextSiglaForClone(
                baseSigla = row.sigla.ifBlank { "Q1" },
                existingSigle = drafts.map { it.sigla },
                defaultPrefix = "Q"
            )
            drafts.add(
                idx + 1,
                row.copy(
                    id = UUID.randomUUID().toString(),
                    sigla = nextSigla,
                    interruttori = row.interruttori.map { it.copy(id = UUID.randomUUID().toString()) }
                )
            )
        },
        onRemoveRow = { idx ->
            if (drafts.size > 1) drafts.removeAt(idx) else drafts[0] = QuadroDraft()
        },
        footerSummary = "$validCount ${if (validCount == 1) "quadro valido pronto" else "quadri validi pronti"} al salvataggio",
        validationError = validationError,
        confirmLabel = "Salva Quadri ($validCount)",
        isConfirmEnabled = validCount > 0,
        onDismiss = onDismiss,
        onConfirm = {
            val validDrafts = drafts.filter { it.sigla.isNotBlank() }
            if (validDrafts.isEmpty()) {
                validationError = "Inserisci almeno un quadro con Sigla valorizzata."
                return@SpreadsheetCreationDialog
            }
            val quadriCreati = validDrafts.map { d ->
                QuadroBT(
                    cantiereId = cantiereId,
                    codIntervento = templateQuadro?.codIntervento ?: "Q",
                    nomeCompleto = templateQuadro?.nomeCompleto ?: "Quadri Elettrici BT",
                    sigla = d.sigla.trim(),
                    descrizioneQuadro = d.descrizione.trim(),
                    listaInterruttori = d.interruttori,
                    listaAttivita = templateQuadro?.listaAttivita ?: emptyList(),
                    listaNormative = templateQuadro?.listaNormative ?: emptyList(),
                    premessa = templateQuadro?.premessa
                )
            }
            onSave(quadriCreati)
        }
    )
}
