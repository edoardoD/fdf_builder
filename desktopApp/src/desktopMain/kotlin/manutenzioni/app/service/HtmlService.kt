package manutenzioni.app.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.Html
import java.io.File
import java.io.InputStream

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
    override fun buildHtml(
        impianto: Impianto,
        attivitaFiltrate: List<Attivita>,
        frequenza: Periodo,
        clienteNome: String?
    ): String {
        var html = loadTemplate()

        // Sostituzione placeholder header
        val cod = if (impianto is manutenzioni.domain.model.QuadroBT && impianto.sigla.isNotBlank()) {
            "${impianto.codIntervento} - ${impianto.sigla}"
        } else {
            impianto.codIntervento
        }
        html = html.replace("<!-- COD_SCHEDA -->", escapeHtml(cod))
        html = html.replace("<!-- OGGETTO -->", escapeHtml(impianto.nomeCompleto))
        html = html.replace("<!-- PERIODICITA -->", escapeHtml(frequenza.label()))
        val premessaCompleta = buildString {
            if (!impianto.premessa.isNullOrBlank()) append(impianto.premessa)
            if (!impianto.noteSpecifiche.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Note specifiche cantiere:\n").append(impianto.noteSpecifiche)
            }
            if (impianto is manutenzioni.domain.model.QuadroBT) {
                if (isNotEmpty()) append("\n\n")
                if (impianto.descrizioneQuadro.isNotBlank()) {
                    append("Ubicazione Quadro: ").append(impianto.descrizioneQuadro).append("\n")
                }
                if (impianto.listaInterruttori.isNotEmpty()) {
                    append("Interruttori e componenti presenti:\n")
                    append(impianto.listaInterruttori.joinToString("\n") {
                        val qta = if (it.quantita > 1) "${it.quantita}x " else ""
                        val prod = if (!it.produttore.isNullOrBlank() && !it.codiceArticolo.isNullOrBlank()) " [${it.produttore} ${it.codiceArticolo}]" else ""
                        val circuito = if (!it.siglaCircuito.isNullOrBlank()) " (Circuito: ${it.siglaCircuito})" else ""
                        "• $qta${it.nome}$prod$circuito"
                    })
                }
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

        // Generazione righe dinamiche
        val rows = buildAttivitaRows(attivitaFiltrate, impianto.codIntervento)
        html = html.replace("<!-- ATTIVITA_ROWS -->", rows)

        // Generazione secondo foglio: Elenco interruttori differenziali e componenti per QuadroBT
        val componentiSheetHtml = if (impianto is manutenzioni.domain.model.QuadroBT && impianto.listaInterruttori.isNotEmpty()) {
            buildComponentiSheet(impianto, frequenza, clienteNome)
        } else {
            ""
        }

        html = if (html.contains("<!-- COMPONENTI_QUADRO_SHEET -->")) {
            html.replace("<!-- COMPONENTI_QUADRO_SHEET -->", componentiSheetHtml)
        } else if (componentiSheetHtml.isNotBlank()) {
            html.replace("</form>", "$componentiSheetHtml\n    </form>")
        } else {
            html
        }

        return html
    }

    /**
     * Genera il secondo foglio del PDF con la tabella tecnica per le verifiche periodiche
     * dei componenti e interruttori differenziali interni al quadro (AcroForm).
     */
    private fun buildComponentiSheet(
        impianto: manutenzioni.domain.model.QuadroBT,
        frequenza: Periodo,
        clienteNome: String?
    ): String {
        val sb = StringBuilder()
        val siglaQuadro = if (impianto.sigla.isNotBlank()) impianto.sigla else impianto.codIntervento
        val codScheda = if (impianto.sigla.isNotBlank()) "${impianto.codIntervento} - ${impianto.sigla}" else impianto.codIntervento
        val clienteText = if (!clienteNome.isNullOrBlank()) escapeHtml(clienteNome) else "Cliente"
        val sitoText = if (impianto.descrizioneQuadro.isNotBlank()) escapeHtml(impianto.descrizioneQuadro) else "Sito / Ubicazione Quadro"

        // Larghezza totale allineata esattamente alla tabella della pagina 1 (18.4cm) per evitare overflow sul margine destro
        sb.appendLine("""        <div class="sheet--componenti" style="page-break-before: always; break-before: page; margin-top: 0.6cm; width: 18.4cm;">""")
        sb.appendLine("""            <!-- Titolo documento superiore -->""")
        sb.appendLine("""            <p style="font-size: 7.5pt; font-weight: bold; margin-bottom: 0.2cm; text-transform: uppercase;">Verifiche Periodiche Programmate Impianti Elettrici</p>""")
        sb.appendLine()
        sb.appendLine("""            <!-- Tabella Intestazione Scheda Differenziali (larghezza totale: 18.4cm) -->""")
        sb.appendLine("""            <table style="width: 18.4cm; border-collapse: collapse; table-layout: fixed; margin-bottom: 0.35cm;">""")
        sb.appendLine("""                <colgroup>""")
        sb.appendLine("""                    <col style="width: 2.4cm;" />""")
        sb.appendLine("""                    <col style="width: 1.7cm;" />""")
        sb.appendLine("""                    <col style="width: 4.5cm;" />""")
        sb.appendLine("""                    <col style="width: 4.8cm;" />""")
        sb.appendLine("""                    <col style="width: 5.0cm;" />""")
        sb.appendLine("""                </colgroup>""")
        sb.appendLine("""                <!-- Riga 1: Titoli e Codici -->""")
        sb.appendLine("""                <tr style="height: 0.65cm;">""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Codice Scheda:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">${escapeHtml(codScheda)}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Oggetto:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">DIFF</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td colspan="2" style="border: 0.0133cm solid #000; background-color: #3366ff; color: #ffffff; text-align: center; vertical-align: middle; padding: 2px 4px;">""")
        sb.appendLine("""                        <p style="font-size: 9pt; font-weight: bold; margin: 0; letter-spacing: 0.5px;">ELENCO INTERRUTTORI DIFFERENZIALI</p>""")
        sb.appendLine("""                        <p style="font-size: 6pt; margin: 0;">Elenco interruttori differenziali e verifiche</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Periodicità:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">${escapeHtml(frequenza.label())}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <!-- Riga 2: Azienda, Sito, Cliente, Data inizio -->""")
        sb.appendLine("""                <tr style="height: 0.65cm;">""")
        sb.appendLine("""                    <td colspan="2" style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Azienda Appaltatrice</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">Desiderio Impianti S.r.l.</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Sito</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">$sitoText</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Cliente:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">$clienteText</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Data inizio periodo attività</p>""")
        sb.appendLine("""                        <input type="text" name="diff_data_inizio" id="diff_data_inizio" placeholder="gg/mm/aaaa" style="width: 100%; border: none; font-size: 6pt;" />""")
        sb.appendLine("""                        <label for="diff_data_inizio" class="visually-hidden">Data inizio periodo attività</label>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <!-- Riga 3: Premessa/Norme e Firme/Manutentore -->""")
        sb.appendLine("""                <tr style="height: 0.75cm;">""")
        sb.appendLine("""                    <td colspan="4" style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; font-weight: bold; margin: 0;">Premessa e/o informazioni:</p>""")
        sb.appendLine("""                        <p style="font-size: 4.8pt; margin: 0; color: #333333;">NORME CEI 78-17:2015, CEI 0-10:2012, CEI EN 50110:2014, CEI 11-27:2014, CEI 64-8:2012, CEI 64-14:2007</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Verificatore / Manutentore</p>""")
        sb.appendLine("""                        <input type="text" name="diff_manutentore" id="diff_manutentore" placeholder="Nome verificatore" style="width: 100%; border: none; font-size: 6pt;" />""")
        sb.appendLine("""                        <label for="diff_manutentore" class="visually-hidden">Verificatore / Manutentore</label>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")
        sb.appendLine()
        sb.appendLine("""            <!-- Tabella Dati Interruttori Differenziali (larghezza totale: 18.4cm) -->""")
        sb.appendLine("""            <table style="width: 18.4cm; border-collapse: collapse; table-layout: fixed; page-break-inside: auto;">""")
        sb.appendLine("""                <colgroup>""")
        sb.appendLine("""                    <col style="width: 2.4cm;" />""")
        sb.appendLine("""                    <col style="width: 1.7cm;" />""")
        sb.appendLine("""                    <col style="width: 4.6cm;" />""")
        sb.appendLine("""                    <col style="width: 2.4cm;" />""")
        sb.appendLine("""                    <col style="width: 1.0cm;" />""")
        sb.appendLine("""                    <col style="width: 1.4cm;" />""")
        sb.appendLine("""                    <col style="width: 1.0cm;" />""")
        sb.appendLine("""                    <col style="width: 1.4cm;" />""")
        sb.appendLine("""                    <col style="width: 2.5cm;" />""")
        sb.appendLine("""                </colgroup>""")
        sb.appendLine("""                <thead>""")
        sb.appendLine("""                    <!-- Riga 1 Intestazione Colonne -->""")
        sb.appendLine("""                    <tr style="height: 0.55cm; background-color: #d9e1f2;">""")
        sb.appendLine("""                        <th rowspan="2" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Sigla Quadro</th>""")
        sb.appendLine("""                        <th rowspan="2" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Sigla Interruttore</th>""")
        sb.appendLine("""                        <th rowspan="2" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Dati Interruttore</th>""")
        sb.appendLine("""                        <th rowspan="2" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Taratura diff.</th>""")
        sb.appendLine("""                        <th colspan="2" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Prova con tasto</th>""")
        sb.appendLine("""                        <th colspan="3" style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">Prova con strumento</th>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <!-- Riga 2 Sotto-colonne OK / NON OK / MISURA -->""")
        sb.appendLine("""                    <tr style="height: 0.45cm; background-color: #d9e1f2;">""")
        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">OK</th>""")
        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">NON OK</th>""")
        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">OK</th>""")
        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">NON OK</th>""")
        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">NR.MISURA</th>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                </thead>""")
        sb.appendLine("""                <tbody>""")

        var rowCounter = 1
        for (interruttore in impianto.listaInterruttori) {
            val qta = if (interruttore.quantita > 0) interruttore.quantita else 1

            // Dati Interruttore: marca + codice o nome
            val datiInterruttore = buildString {
                if (!interruttore.produttore.isNullOrBlank()) {
                    append(interruttore.produttore).append(" ")
                }
                if (!interruttore.codiceArticolo.isNullOrBlank()) {
                    append(interruttore.codiceArticolo)
                } else {
                    append(interruttore.nome)
                }
            }

            val taratura = extractTaratura(interruttore)

            for (q in 1..qta) {
                val rowNum = rowCounter++
                val siglaInterruttore = if (interruttore.siglaCircuito.isNullOrBlank()) {
                    "QF$rowNum"
                } else {
                    if (qta > 1) "${interruttore.siglaCircuito}.$q" else interruttore.siglaCircuito
                }

                val tastoRadioName = "tasto_diff_${impianto.codIntervento}_$rowNum"
                val strumentoRadioName = "strumento_diff_${impianto.codIntervento}_$rowNum"
                val misuraTextName = "misura_diff_${impianto.codIntervento}_$rowNum"

                sb.appendLine("""                    <tr style="height: 0.48cm; page-break-inside: avoid;">""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; font-size: 6pt; text-align: center; vertical-align: middle; padding: 2px;">${escapeHtml(siglaQuadro)}</td>""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; font-size: 6pt; text-align: center; vertical-align: middle; padding: 2px; font-weight: bold;">${escapeHtml(siglaInterruttore)}</td>""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; font-size: 6pt; text-align: center; vertical-align: middle; padding: 2px;">${escapeHtml(datiInterruttore)}</td>""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; font-size: 6pt; text-align: center; vertical-align: middle; padding: 2px;">${escapeHtml(taratura)}</td>""")
                // Prova con tasto OK / NON OK
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="radio" name="$tastoRadioName" id="${tastoRadioName}_ok" value="OK" />""")
                sb.appendLine("""                            <label for="${tastoRadioName}_ok" class="visually-hidden">Tasto OK</label>""")
                sb.appendLine("""                        </td>""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="radio" name="$tastoRadioName" id="${tastoRadioName}_nok" value="NON_OK" />""")
                sb.appendLine("""                            <label for="${tastoRadioName}_nok" class="visually-hidden">Tasto NON OK</label>""")
                sb.appendLine("""                        </td>""")
                // Prova con strumento OK / NON OK
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="radio" name="$strumentoRadioName" id="${strumentoRadioName}_ok" value="OK" />""")
                sb.appendLine("""                            <label for="${strumentoRadioName}_ok" class="visually-hidden">Strumento OK</label>""")
                sb.appendLine("""                        </td>""")
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="radio" name="$strumentoRadioName" id="${strumentoRadioName}_nok" value="NON_OK" />""")
                sb.appendLine("""                            <label for="${strumentoRadioName}_nok" class="visually-hidden">Strumento NON OK</label>""")
                sb.appendLine("""                        </td>""")
                // NR.MISURA
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="text" name="$misuraTextName" id="$misuraTextName" style="width: 100%; border: none; text-align: center; font-size: 6pt;" />""")
                sb.appendLine("""                            <label for="$misuraTextName" class="visually-hidden">Numero Misura</label>""")
                sb.appendLine("""                        </td>""")
                sb.appendLine("""                    </tr>""")
            }
        }

        sb.appendLine("""                </tbody>""")
        sb.appendLine("""            </table>""")
        sb.appendLine("""            <!-- Footer scheda differenziali -->""")
        sb.appendLine("""            <p style="font-size: 5pt; color: #555555; margin-top: 0.25cm; text-align: left;">Desiderio Impianti S.r.l. &nbsp;&nbsp;&nbsp; ELENCO INTERRUTTORI DIFFERENZIALI - ${escapeHtml(codScheda)}</p>""")
        sb.appendLine("""        </div>""")

        return sb.toString()
    }

    /**
     * Estrae la taratura differenziale o le specifiche tecniche rilevanti per la colonna "Taratura diff."
     */
    private fun extractTaratura(interruttore: manutenzioni.domain.model.InterruttoreBT): String {
        val car = interruttore.caratteristicheTecniche
        if (car.isEmpty()) return ""

        val directKey = car["taratura"] ?: car["Idn"] ?: car["IΔn"] ?: car["taraturaDiff"] ?: car["sensibilita"]
        if (!directKey.isNullOrBlank()) return directKey

        val fuzzy = car.entries.firstOrNull { (k, v) ->
            (k.contains("taratura", ignoreCase = true) ||
             k.contains("diff", ignoreCase = true) ||
             k.contains("idn", ignoreCase = true)) && v.isNotBlank()
        }?.value
        if (!fuzzy.isNullOrBlank()) return fuzzy

        return car.values.filter { it.isNotBlank() }.joinToString(" - ")
    }

    // Metodo legacy `fillHtml` rimosso secondo il refactoring plan

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
        // Questo è utile durante lo sviluppo, ma in produzione (JAR/App) il file è dentro le risorse
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
