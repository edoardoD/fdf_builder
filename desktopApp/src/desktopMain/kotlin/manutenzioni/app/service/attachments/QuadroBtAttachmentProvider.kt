package manutenzioni.app.service.attachments

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.service.attachments.*

/**
 * Provider per la generazione dell'allegato "ELENCO INTERRUTTORI DIFFERENZIALI"
 * associato ai quadri elettrici BT.
 */
class QuadroBtAttachmentProvider : TechnicalAttachmentProvider {

    override fun canHandle(impianto: Impianto): Boolean {
        return impianto is QuadroBT && impianto.listaInterruttori.isNotEmpty()
    }

    override fun buildPremessaSummary(impianto: Impianto): String? {
        val quadro = impianto as? QuadroBT ?: return null
        if (quadro.listaInterruttori.isEmpty()) return null

        return buildString {
            if (quadro.descrizioneQuadro.isNotBlank()) {
                append("Ubicazione Quadro: ").append(quadro.descrizioneQuadro).append("\n")
            }
            append("Interruttori e componenti presenti:\n")
            append(quadro.listaInterruttori.joinToString("\n") {
                val qta = if (it.quantita > 1) "${it.quantita}x " else ""
                val prod = if (!it.produttore.isNullOrBlank() && !it.codiceArticolo.isNullOrBlank()) " [${it.produttore} ${it.codiceArticolo}]" else ""
                val circuito = if (!it.siglaCircuito.isNullOrBlank()) " (Circuito: ${it.siglaCircuito})" else ""
                "• $qta${it.nome}$prod$circuito"
            })
        }
    }

    override fun buildSheetData(
        impianto: Impianto,
        frequenza: Periodo,
        clienteNome: String?
    ): TechnicalSheetData? {
        val quadro = impianto as? QuadroBT ?: return null
        if (quadro.listaInterruttori.isEmpty()) return null

        val siglaQuadro = if (quadro.sigla.isNotBlank()) quadro.sigla else quadro.codIntervento
        val codScheda = if (quadro.sigla.isNotBlank()) "${quadro.codIntervento} - ${quadro.sigla}" else quadro.codIntervento
        val sitoText = if (quadro.descrizioneQuadro.isNotBlank()) quadro.descrizioneQuadro else "Sito / Ubicazione Quadro"

        val columns = listOf(
            TableColumn("Sigla Quadro", 2.4),
            TableColumn("Sigla Interruttore", 1.7),
            TableColumn("Dati Interruttore", 4.6),
            TableColumn("Taratura diff.", 2.4),
            TableColumn(
                title = "Prova con tasto",
                subColumns = listOf(
                    SubColumn("OK", 1.0),
                    SubColumn("NON OK", 1.4)
                )
            ),
            TableColumn(
                title = "Prova con strumento",
                subColumns = listOf(
                    SubColumn("OK", 1.0),
                    SubColumn("NON OK", 1.4),
                    SubColumn("NR.MISURA", 2.5)
                )
            )
        )

        val rows = mutableListOf<TechnicalRow>()
        var rowCounter = 1

        for (interruttore in quadro.listaInterruttori) {
            val qta = if (interruttore.quantita > 0) interruttore.quantita else 1

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

                val tastoRadioName = "tasto_diff_${quadro.codIntervento}_$rowNum"
                val strumentoRadioName = "strumento_diff_${quadro.codIntervento}_$rowNum"
                val misuraTextName = "misura_diff_${quadro.codIntervento}_$rowNum"

                val cells = listOf(
                    TechnicalCell.Text(siglaQuadro, align = CellTextAlign.CENTER, fontSizePt = 6.0),
                    TechnicalCell.Text(siglaInterruttore, align = CellTextAlign.CENTER, bold = true, fontSizePt = 6.0),
                    TechnicalCell.Text(datiInterruttore, align = CellTextAlign.CENTER, fontSizePt = 6.0),
                    TechnicalCell.Text(taratura, align = CellTextAlign.CENTER, fontSizePt = 6.0),
                    TechnicalCell.Radio(tastoRadioName, "OK", "Tasto OK"),
                    TechnicalCell.Radio(tastoRadioName, "NON_OK", "Tasto NON OK"),
                    TechnicalCell.Radio(strumentoRadioName, "OK", "Strumento OK"),
                    TechnicalCell.Radio(strumentoRadioName, "NON_OK", "Strumento NON OK"),
                    TechnicalCell.TextInput(misuraTextName, placeholder = "", label = "Misura", fontSizePt = 7.5, align = CellTextAlign.CENTER)
                )

                rows.add(TechnicalRow(heightCm = 0.48, cells = cells))
            }
        }

        return TechnicalSheetData(
            codScheda = codScheda,
            oggetto = "DIFF",
            titoloBanner = "ELENCO INTERRUTTORI DIFFERENZIALI",
            sottotitoloBanner = "Elenco interruttori differenziali e verifiche",
            impiantoLabel = quadro.nomeCompleto,
            sitoLabel = sitoText,
            normative = "NORME CEI 78-17:2015, CEI 0-10:2012, CEI EN 50110:2014, CEI 11-27:2014, CEI 64-8:2012, CEI 64-14:2007",
            campoAcroFormPrefix = "diff",
            columns = columns,
            rows = rows,
            footerDocTitle = "ELENCO INTERRUTTORI DIFFERENZIALI"
        )
    }

    private fun extractTaratura(interruttore: InterruttoreBT): String {
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
}
