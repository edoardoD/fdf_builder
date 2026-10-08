package manutenzioni.app.service

import manutenzioni.app.service.attachments.EmergenzaAttachmentProvider
import manutenzioni.app.service.attachments.QuadroBtAttachmentProvider
import manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.service.Html
import manutenzioni.domain.service.attachments.TechnicalAttachmentProvider
import java.io.File
import java.io.InputStream

/**
 * Implementazione concreta del template engine HTML.
 *
 * Legge il file scheletro.html, sostituisce i placeholder con dati reali
 * e genera dinamicamente le righe della tabella per ogni attività filtrata.
 * Delega la generazione dei fogli tecnici allegati (Foglio 2+) e i sommari
 * in premessa a provider modulari conformi all'Open-Closed Principle (OCP).
 */
class HtmlService(
    private val templatePath: String = "scheletro.html",
    private val attachmentProviders: List<TechnicalAttachmentProvider> = listOf(
        QuadroBtAttachmentProvider(),
        EmergenzaAttachmentProvider()
    ),
    private val sheetRenderer: TechnicalSheetHtmlRenderer = TechnicalSheetHtmlRenderer()
) : Html {

    /**
     * Genera l'HTML completo con i dati dell'impianto e le attività filtrate.
     *
     * @param clienteNome Nome del cliente da iniettare nell'header
     * @return il contenuto HTML come stringa
     */
    override fun buildHtml(
        impianto: Impianto,
        attivitaFiltrate: List<Attivita>,
        frequenza: Periodo,
        clienteNome: String?
    ): String {
        var html = loadTemplate()

        // Sostituzione placeholder header
        val cod = if (impianto is QuadroBT && impianto.sigla.isNotBlank()) {
            "${impianto.codIntervento} - ${impianto.sigla}"
        } else {
            impianto.codIntervento
        }
        html = html.replace("<!-- COD_SCHEDA -->", escapeHtml(cod))
        html = html.replace("<!-- OGGETTO -->", escapeHtml(impianto.nomeCompleto))
        html = html.replace("<!-- PERIODICITA -->", escapeHtml(frequenza.label()))

        // Premessa dinamica: testo base + note specifiche + sintesi componenti delegata ai provider
        val premessaCompleta = buildString {
            if (!impianto.premessa.isNullOrBlank()) append(impianto.premessa)
            if (!impianto.noteSpecifiche.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Note specifiche cantiere:\n").append(impianto.noteSpecifiche)
            }
            val attachmentSummary = attachmentProviders
                .firstOrNull { it.canHandle(impianto) }
                ?.buildPremessaSummary(impianto)
            if (!attachmentSummary.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(attachmentSummary)
            }
        }
        html = html.replace("<!-- PREMESSA -->", escapeHtml(premessaCompleta))

        // Iniezione nome cliente nell'header
        val clienteText = if (!clienteNome.isNullOrBlank()) {
            "Cliente: ${escapeHtml(clienteNome)}"
        } else {
            "Cliente"
        }
        html = html.replace("<p>Cliente</p>", "<p>$clienteText</p>")

        // Generazione righe dinamiche delle attività
        val rows = buildAttivitaRows(attivitaFiltrate, impianto.codIntervento)
        html = html.replace("<!-- ATTIVITA_ROWS -->", rows)

        // Generazione fogli tecnici allegati (secondo foglio: differenziali, lampade, ecc.)
        val extraSheets = attachmentProviders
            .filter { it.canHandle(impianto) }
            .mapNotNull { it.buildSheetData(impianto, frequenza, clienteNome) }
            .joinToString("\n") { sheetRenderer.render(it, frequenza, clienteNome) }

        // Append fogli aggiuntivi
        html = if (html.contains("<!-- COMPONENTI_QUADRO_SHEET -->")) {
            html.replace("<!-- COMPONENTI_QUADRO_SHEET -->", extraSheets)
        } else if (extraSheets.isNotBlank()) {
            html.replace("</form>", "$extraSheets\n    </form>")
        } else {
            html
        }

        return html
    }

    private fun loadTemplate(): String {
        // 1. Cerca nel classpath (resources)
        val fromClasspath: InputStream? = Thread.currentThread().contextClassLoader?.getResourceAsStream(templatePath)
            ?: this::class.java.classLoader?.getResourceAsStream(templatePath)
            ?: this::class.java.getResourceAsStream("/$templatePath")
            ?: ClassLoader.getSystemResourceAsStream(templatePath)

        if (fromClasspath != null) {
            return fromClasspath.bufferedReader().use { it.readText() }
        }

        // 2. Fallback: cerca su filesystem (percorso assoluto o relativo)
        val file = File(templatePath)
        if (file.exists()) {
            return file.readText()
        }

        throw IllegalStateException("Template HTML non trovato: $templatePath. Assicurati che il file sia presente nelle risorse.")
    }

    /**
     * Genera le righe <tr> HTML per ogni attività, con radio button per esiti
     * e campo testo per le note. I nomi dei campi sono univoci per riga.
     */
    private fun buildAttivitaRows(attivita: List<Attivita>, codImpianto: String): String {
        val sb = StringBuilder()
        val esiti = listOf("P", "PI", "NA", "NP", "VN", "B")

        attivita.forEachIndexed { index, att ->
            val rowNum = index + 1
            val radioName = "esito_${codImpianto}_${rowNum}"

            sb.appendLine("""                <tr class="row--data">""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.nAttivita.toString())}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.tipoAttivita ?: "")}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.frequenza.label())}</p></td>""")
            sb.appendLine("""                    <td class="cell--data-empty"><p style="text-align:justify;">${escapeHtml(att.descrizione ?: "")}</p></td>""")
            // Colonne esito con radio button:
            // P, PI, NA, NP, B appartengono al radio group principale (mutuamente esclusivo tra loro).
            // VN ha un gruppo radio separato (esito_vn_...) così non è esclusivo ed è selezionabile
            // insieme agli altri esiti, preservando comunque l'estetica a sfera ("radio btn").
            for (esito in esiti) {
                val fieldName = if (esito == "VN") "esito_vn_${codImpianto}_${rowNum}" else radioName
                val id = "${radioName}_${esito.lowercase()}"
                sb.appendLine("""                    <td class="cell--data-empty">""")
                sb.appendLine("""                        <input type="radio" name="$fieldName" id="$id" value="$esito" />""")
                sb.appendLine("""                        <label for="$id" class="visually-hidden">$esito</label>""")
                sb.appendLine("""                    </td>""")
            }

            // Colonna nota
            val noteId = "note_${codImpianto}_${rowNum}"
            sb.appendLine("""                    <td class="cell--data-standard">""")
            sb.appendLine("""                        <input type="text" name="$noteId" id="$noteId" />""")
            sb.appendLine("""                        <label for="$noteId" class="visually-hidden">Nota</label>""")
            sb.appendLine("""                    </td>""")
            sb.appendLine("""                    <td class="cell--spacer"></td>""")
            sb.appendLine("""                </tr>""")
        }

        return sb.toString()
    }

    /** Escape base per contenuti HTML */
    private fun escapeHtml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }
}
