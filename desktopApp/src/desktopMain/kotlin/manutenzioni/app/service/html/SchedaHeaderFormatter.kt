package manutenzioni.app.service.html

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.service.HtmlRenderContext

/**
 * Formattatore specializzato per l'intestazione, il cartiglio e i metadati della scheda di manutenzione.
 *
 * Utilizza le proprietà polimorfiche di [Impianto] (siglaCartiglio, descrizioneDettagliata)
 * ed esegue l'iniezione sicura di Cliente, Cantiere (Sito), date AcroForm e testo premessa.
 */
class SchedaHeaderFormatter {

    /**
     * Risolve il codice sintetico per il cartiglio PDF (<!-- COD_SCHEDA -->).
     * Delega alla proprietà polimorfica [Impianto.siglaCartiglio].
     * Es: "Q - QG1" per QuadroBT con sigla, "GE" per Gruppo Elettrogeno.
     */
    fun formatCodiceScheda(impianto: Impianto): String {
        return impianto.siglaCartiglio
    }

    /**
     * Risolve l'oggetto descrittivo per il cartiglio PDF (<!-- OGGETTO -->).
     * Sfrutta la descrizione specifica per [QuadroBT] o [Impianto.descrizioneDettagliata].
     */
    fun formatOggetto(impianto: Impianto): String {
        return if (impianto is QuadroBT) {
            when {
                impianto.descrizioneQuadro.isNotBlank() && impianto.sigla.isNotBlank() ->
                    "${impianto.descrizioneQuadro.trim()} - ${impianto.sigla.trim()}"
                impianto.descrizioneQuadro.isNotBlank() ->
                    impianto.descrizioneQuadro.trim()
                impianto.sigla.isNotBlank() ->
                    "${impianto.nomeCompleto.trim()} - ${impianto.sigla.trim()}"
                else -> impianto.nomeCompleto
            }
        } else {
            impianto.descrizioneDettagliata
        }
    }

    /**
     * Compone il testo completo della premessa di sicurezza:
     * testo base impianto (senza caratteristiche tecniche/elenchi componenti) + eventuali note specifiche di cantiere + sommario allegato tecnico.
     */
    fun buildPremessa(impianto: Impianto, attachmentSummary: String? = null): String {
        val cleanedPremessa = sanitizePremessaBase(impianto.premessa)
        return buildString {
            if (cleanedPremessa.isNotBlank()) {
                append(cleanedPremessa)
            }
            if (!impianto.noteSpecifiche.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append("Note specifiche cantiere:\n").append(impianto.noteSpecifiche)
            }
            if (!attachmentSummary.isNullOrBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(attachmentSummary)
            }
        }
    }

    /**
     * Rimuove eventuali riepiloghi di ubicazione o componenti erroneamente concatenati nella premessa base.
     */
    fun sanitizePremessaBase(rawPremessa: String?): String {
        if (rawPremessa.isNullOrBlank()) return ""
        return rawPremessa
            .substringBefore("Ubicazione Quadro:")
            .substringBefore("Interruttori e componenti presenti:")
            .substringBefore("Dispositivi e moduli di rilevazione configurati")
            .substringBefore("Lampade di emergenza presenti")
            .trim()
    }

    /**
     * Applica all'HTML di template tutte le sostituzioni di intestazione, cartiglio,
     * cliente, sito, date AcroForm e manutentore via [HtmlRenderContext].
     */
    fun formatHeaderAndMetadata(
        templateHtml: String,
        context: HtmlRenderContext,
        attachmentSummary: String? = null
    ): String {
        var html = templateHtml
        val impianto = context.impianto

        // 1. Sostituzione placeholder Cartiglio primario
        val codScheda = formatCodiceScheda(impianto)
        val oggetto = formatOggetto(impianto)
        html = html.replace("<!-- COD_SCHEDA -->", escapeHtml(codScheda))
        html = html.replace("<!-- COD_IMPIANTO -->", escapeHtml(impianto.codIntervento))
        html = html.replace("<!-- OGGETTO -->", escapeHtml(oggetto))
        html = html.replace("<!-- PERIODICITA -->", escapeHtml(context.frequenza.codiceCartiglio()))

        // 2. Sostituzione Premessa
        val premessaCompleta = buildPremessa(impianto, attachmentSummary)
        html = html.replace("<!-- PREMESSA -->", escapeHtml(premessaCompleta))

        // 3. Iniezione Sito (Cantiere)
        if (!context.cantiereNome.isNullOrBlank()) {
            val escapedSito = escapeHtml(context.cantiereNome.trim())
            html = html.replace("<!-- SITO -->", escapedSito)
            html = html.replace("<p>Sito</p>", "<p>Sito: $escapedSito</p>")
        } else {
            html = html.replace("<p>Sito: <!-- SITO --></p>", "<p>Sito</p>")
            html = html.replace("<!-- SITO -->", "")
        }

        // 4. Iniezione Nome Cliente
        val clienteText = if (!context.clienteNome.isNullOrBlank()) {
            "Cliente: ${escapeHtml(context.clienteNome)}"
        } else {
            "Cliente"
        }
        html = html.replace("<p>Cliente</p>", "<p>$clienteText</p>")

        // 5. Precompilazione starting_date (Data inizio contratto)
        val contrattoDate = context.dataInizioContratto?.trim()?.ifBlank { null }
        if (!contrattoDate.isNullOrBlank()) {
            val escapedDate = escapeHtml(contrattoDate)
            html = html.replace("value=\"<!-- DATA_INIZIO_CONTRATTO -->\"", "value=\"$escapedDate\"")
            html = html.replace("<!-- DATA_INIZIO_CONTRATTO -->", escapedDate)
        } else {
            html = html.replace(" value=\"<!-- DATA_INIZIO_CONTRATTO -->\"", "")
            html = html.replace("<!-- DATA_INIZIO_CONTRATTO -->", "")
        }

        // 6. Precompilazione previous_date (Data intervento precedente)
        val precedenteDate = context.dataInterventoPrecedente?.trim()?.ifBlank { null }
        if (!precedenteDate.isNullOrBlank()) {
            val escapedDate = escapeHtml(precedenteDate)
            html = html.replace("value=\"<!-- DATA_PRECEDENTE -->\"", "value=\"$escapedDate\"")
            html = html.replace("<!-- DATA_PRECEDENTE -->", escapedDate)
        } else {
            html = html.replace(" value=\"<!-- DATA_PRECEDENTE -->\"", "")
            html = html.replace("<!-- DATA_PRECEDENTE -->", "")
        }

        // 7. Precompilazione actual_date (Data intervento)
        val interventoDate = context.dataIntervento?.trim()?.ifBlank { null }
        if (!interventoDate.isNullOrBlank()) {
            val escapedDate = escapeHtml(interventoDate)
            html = html.replace("value=\"<!-- DATA_INTERVENTO -->\"", "value=\"$escapedDate\"")
            html = html.replace("<!-- DATA_INTERVENTO -->", escapedDate)
        } else {
            html = html.replace(" value=\"<!-- DATA_INTERVENTO -->\"", "")
            html = html.replace("<!-- DATA_INTERVENTO -->", "")
        }

        // 8. Iniezione Verificatore/Manutentore
        val manutentoreText = if (!context.nomeManutentore.isNullOrBlank()) {
            escapeHtml(context.nomeManutentore.trim())
        } else {
            ""
        }
        if (manutentoreText.isNotEmpty()) {
            html = html.replace("value=\"<!-- MANUTENTORE -->\"", "value=\"$manutentoreText\"")
        } else {
            html = html.replace(" value=\"<!-- MANUTENTORE -->\"", "")
        }
        html = html.replace("<!-- MANUTENTORE -->", manutentoreText)

        return html
    }
}
