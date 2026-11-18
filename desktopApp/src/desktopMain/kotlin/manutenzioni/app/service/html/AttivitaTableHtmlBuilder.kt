package manutenzioni.app.service.html

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.TipoPeriodo

/**
 * Costruttore specializzato per la tabella HTML delle attività di manutenzione (Sezione 6).
 *
 * Gestisce l'ordinamento canonico, la numerazione dinamica 1..N, la risoluzione polimorfica
 * della colonna Zona, i radio button AcroForm e il padding a 30 righe fisse (R2 & R23).
 */
class AttivitaTableHtmlBuilder(
    private val targetTotalRows: Int = DEFAULT_TARGET_ROWS
) {
    companion object {
        const val DEFAULT_TARGET_ROWS = 30
        private val ESITI_STANDARD = listOf("P", "PI", "NA", "NP", "VN", "B")
    }

    /**
     * Genera le righe `<tr>` per la tabella delle attività.
     *
     * @param attivita Lista delle attività filtrate per la frequenza selezionata.
     * @param impianto L'impianto a cui appartengono le attività.
     * @return Stringa HTML contenente le righe `<tr>` pronte per l'iniezione in <!-- ATTIVITA_ROWS -->.
     */
    fun buildAttivitaRows(attivita: List<Attivita>, impianto: Impianto): String {
        val sb = StringBuilder()
        val codImpianto = impianto.codIntervento

        // 1. Ordinamento canonico delle attività da 01-06 (Mesi) a 1-n (Anni), preservando l'ordine relativo
        val attivitaOrdinate = sortAttivitaCanonically(attivita)

        // 2. Generazione righe per le attività reali
        attivitaOrdinate.forEachIndexed { index, att ->
            val rowNum = index + 1
            val radioName = "esito_${codImpianto}_${rowNum}"

            // Risoluzione polimorfica della colonna Zona tramite Impianto.formatZonaAttivita
            val colZonaText = impianto.formatZonaAttivita(att)

            sb.appendLine("""                <tr class="row--data">""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>$rowNum</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.tipoAttivita ?: "")}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(colZonaText)}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.frequenza.codiceCartiglio())}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p style="text-align:justify;">${escapeHtml(att.descrizione ?: "")}</p></td>""")

            // Colonne esito con radio button:
            // P, PI, NA, NP, B appartengono al radio group principale (mutuamente esclusivo tra loro).
            // VN ha un gruppo radio separato (esito_vn_...) così non è esclusivo ed è selezionabile
            // insieme agli altri esiti, preservando comunque l'estetica a sfera ("radio btn").
            for (esito in ESITI_STANDARD) {
                val fieldName = if (esito == "VN") "esito_vn_${codImpianto}_${rowNum}" else radioName
                val id = "${radioName}_${esito.lowercase()}"
                sb.appendLine("""                    <td class="cell--data-empty">""")
                sb.appendLine("""                        <input type="radio" name="$fieldName" id="$id" value="$esito" />""")
                sb.appendLine("""                        <label for="$id" class="visually-hidden">$esito</label>""")
                sb.appendLine("""                    </td>""")
            }

            // Colonna nota
            val noteId = "note_${codImpianto}_${rowNum}"
            sb.appendLine("""                    <td class="cell--data-input cell--data-standard">""")
            sb.appendLine("""                        <input type="text" name="$noteId" id="$noteId" />""")
            sb.appendLine("""                        <label for="$noteId" class="visually-hidden">Nota</label>""")
            sb.appendLine("""                    </td>""")
            sb.appendLine("""                </tr>""")
        }

        // 3. Griglia fissa a 30 righe (R2 & R23): se N < 30, genera campi AcroForm compilabili su tutte le righe N+1..30
        if (attivita.size < targetTotalRows) {
            for (fillerIndex in (attivita.size + 1)..targetTotalRows) {
                val fillerRadioName = "esito_${codImpianto}_${fillerIndex}"
                val fillerNoteId = "note_${codImpianto}_${fillerIndex}"
                val fillerTipoId = "tipo_${codImpianto}_${fillerIndex}"
                val fillerZonaId = "zona_${codImpianto}_${fillerIndex}"
                val fillerPeriodicitaId = "periodicita_${codImpianto}_${fillerIndex}"
                val fillerAttivitaId = "attivita_${codImpianto}_${fillerIndex}"

                sb.appendLine("""                <tr class="row--data">""")
                sb.appendLine("""                    <td class="cell--data-empty"><p>$fillerIndex</p></td>""")
                sb.appendLine("""                    <td class="cell--data-input">""")
                sb.appendLine("""                        <input type="text" name="$fillerTipoId" id="$fillerTipoId" />""")
                sb.appendLine("""                        <label for="$fillerTipoId" class="visually-hidden">Tipo di Attività</label>""")
                sb.appendLine("""                    </td>""")
                sb.appendLine("""                    <td class="cell--data-input">""")
                sb.appendLine("""                        <input type="text" name="$fillerZonaId" id="$fillerZonaId" />""")
                sb.appendLine("""                        <label for="$fillerZonaId" class="visually-hidden">Zona</label>""")
                sb.appendLine("""                    </td>""")
                sb.appendLine("""                    <td class="cell--data-input">""")
                sb.appendLine("""                        <input type="text" name="$fillerPeriodicitaId" id="$fillerPeriodicitaId" />""")
                sb.appendLine("""                        <label for="$fillerPeriodicitaId" class="visually-hidden">Periodicità</label>""")
                sb.appendLine("""                    </td>""")
                sb.appendLine("""                    <td class="cell--data-input">""")
                sb.appendLine("""                        <input type="text" name="$fillerAttivitaId" id="$fillerAttivitaId" />""")
                sb.appendLine("""                        <label for="$fillerAttivitaId" class="visually-hidden">Attività</label>""")
                sb.appendLine("""                    </td>""")

                for (esito in ESITI_STANDARD) {
                    val fieldName = if (esito == "VN") "esito_vn_${codImpianto}_${fillerIndex}" else fillerRadioName
                    val id = "${fillerRadioName}_${esito.lowercase()}"
                    sb.appendLine("""                    <td class="cell--data-empty">""")
                    sb.appendLine("""                        <input type="radio" name="$fieldName" id="$id" value="$esito" />""")
                    sb.appendLine("""                        <label for="$id" class="visually-hidden">$esito</label>""")
                    sb.appendLine("""                    </td>""")
                }

                sb.appendLine("""                    <td class="cell--data-input cell--data-standard">""")
                sb.appendLine("""                        <input type="text" name="$fillerNoteId" id="$fillerNoteId" />""")
                sb.appendLine("""                        <label for="$fillerNoteId" class="visually-hidden">Nota</label>""")
                sb.appendLine("""                    </td>""")
                sb.appendLine("""                </tr>""")
            }
        }

        return sb.toString()
    }

    /**
     * Ordina la lista delle attività secondo la regola canonica:
     * durata in mesi crescente, tipo M prima di tipo A, poi per valore.
     */
    fun sortAttivitaCanonically(attivita: List<Attivita>): List<Attivita> {
        return attivita.sortedWith(
            compareBy<Attivita> { it.frequenza.inMesi() }
                .thenBy { if (it.frequenza.tipo == TipoPeriodo.M) 0 else 1 }
                .thenBy { it.frequenza.valore }
        )
    }
}
