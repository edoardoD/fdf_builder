package manutenzioni.app.service

import manutenzioni.app.data.Attivita
import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.domain.service.Html
import java.io.File

/**
 * Implementazione concreta del template engine HTML.
 *
 * Legge il file scheletro.html, sostituisce i placeholder con dati reali
 * e genera dinamicamente le righe della tabella per ogni attività filtrata.
 */
class HtmlService(
    private val templatePath: String = "scheletro.html"
) : Html {

    /**
     * Genera l'HTML completo con i dati dell'impianto e le attività filtrate.
     *
     * @param clienteNome Nome del cliente da iniettare nell'header
     * @return il contenuto HTML come stringa
     */
    fun buildHtml(
        impianto: Impianto,
        attivitaFiltrate: List<Attivita>,
        frequenza: Periodo,
        clienteNome: String? = null
    ): String {
        var html = loadTemplate()

        // Sostituzione placeholder header
        html = html.replace("<!-- COD_SCHEDA -->", escapeHtml(impianto.codIntervento))
        html = html.replace("<!-- OGGETTO -->", escapeHtml(impianto.nomeCompleto))
        html = html.replace("<!-- PERIODICITA -->", escapeHtml(frequenza.label()))
        html = html.replace("<!-- PREMESSA -->", escapeHtml(impianto.premessa ?: ""))

        // Iniezione nome cliente nell'header
        val clienteText = if (!clienteNome.isNullOrBlank()) {
            "Cliente: ${escapeHtml(clienteNome)}"
        } else {
            "Cliente"
        }
        html = html.replace("<p>Cliente</p>", "<p>$clienteText</p>")

        // Generazione righe dinamiche
        val rows = buildAttivitaRows(attivitaFiltrate, impianto.codIntervento)
        html = html.replace("<!-- ATTIVITA_ROWS -->", rows)

        return html
    }

    /**
     * Implementazione dell'interfaccia Html — metodo generico con mappa.
     * Per un controllo più fine, usare buildHtml() direttamente.
     */
    override fun fillHtml(map: Map<String, Any?>) {
        // Delegato all'approccio type-safe buildHtml()
        // Questo metodo è mantenuto per retrocompatibilità con l'interfaccia
    }

    private fun loadTemplate(): String {
        // 1. Cerca nel classpath (resources)
        val fromClasspath = Thread.currentThread().contextClassLoader
            ?.getResourceAsStream(templatePath)
            ?: this::class.java.classLoader?.getResourceAsStream(templatePath)
            ?: this::class.java.getResourceAsStream("/$templatePath")

        if (fromClasspath != null) {
            return fromClasspath.bufferedReader().use { it.readText() }
        }

        // 2. Fallback: cerca su filesystem (percorso assoluto o relativo)
        val file = File(templatePath)
        if (file.exists()) {
            return file.readText()
        }

        throw IllegalStateException("Template HTML non trovato: $templatePath")
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
            sb.appendLine("""                    <td class="cell--data-empty"><p>${escapeHtml(att.descrizione ?: "")}</p></td>""")

            // Colonne esito con radio button
            for (esito in esiti) {
                val id = "${radioName}_${esito.lowercase()}"
                sb.appendLine("""                    <td class="cell--data-empty">""")
                sb.appendLine("""                        <input type="radio" name="$radioName" id="$id" value="$esito" />""")
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

