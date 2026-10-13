package manutenzioni.app.service.attachments

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.service.attachments.*

/**
 * Provider per la generazione dell'allegato "ELENCO COMPONENTI RILEVAZIONE INCENDI (ELENCO RIL)"
 * associato all'impianto di rivelazione e allarme incendi (RI) secondo normative UNI 9795 e UNI 11224.
 */
class AntincendioAttachmentProvider : TechnicalAttachmentProvider {

    override fun canHandle(impianto: Impianto): Boolean {
        return (impianto is RilevazioneAntincendio || impianto.codIntervento.equals("RI", ignoreCase = true)) &&
                ((impianto as? RilevazioneAntincendio)?.listaDispositivi?.isNotEmpty() == true)
    }

    override fun buildPremessaSummary(impianto: Impianto): String? {
        val ri = impianto as? RilevazioneAntincendio ?: return null
        if (ri.listaDispositivi.isEmpty()) return null

        val totale = ri.listaDispositivi.size
        val perLoop = ri.listaDispositivi.groupBy { it.loop }
        return buildString {
            append("Dispositivi e moduli di rilevazione configurati ($totale):\n")
            for (loopNum in 1..4) {
                val inLoop = perLoop[loopNum] ?: emptyList()
                if (inLoop.isNotEmpty()) {
                    append("• Loop $loopNum: ${inLoop.size} componenti\n")
                }
            }
        }.trimEnd()
    }

    override fun buildSheetData(
        impianto: Impianto,
        frequenza: Periodo,
        clienteNome: String?
    ): TechnicalSheetData? {
        val ri = impianto as? RilevazioneAntincendio ?: return null
        if (ri.listaDispositivi.isEmpty()) return null

        // Larghezze millimetriche: 2.0 + 1.6 + 2.0 + 4.0 + 1.6 + 1.8 + (1.1 + 1.3) + 3.0 = 18.4 cm
        val columns = listOf(
            TableColumn("Indirizzo", 2.0),
            TableColumn("Tipo", 1.6),
            TableColumn("Installazione", 2.0),
            TableColumn("Descrizione", 4.0),
            TableColumn("Zona", 1.6),
            TableColumn("Data Inst./Rev.", 1.8),
            TableColumn(
                title = "Esito Prova",
                subColumns = listOf(
                    SubColumn("OK", 1.1),
                    SubColumn("NON OK", 1.3)
                )
            ),
            TableColumn("Note", 3.0)
        )

        val rows = ri.listaDispositivi.mapIndexed { idx, disp ->
            val rowNum = idx + 1
            val esitoRadioName = "ril_esito_${ri.codIntervento}_$rowNum"
            val noteFieldName = "ril_note_${ri.codIntervento}_$rowNum"

            val formattedIndirizzo = if (disp.indirizzo.startsWith("L", ignoreCase = true) || disp.indirizzo.contains(".")) {
                disp.indirizzo
            } else {
                "L${disp.loop}.${disp.indirizzo}"
            }

            val cells = listOf(
                TechnicalCell.Text(formattedIndirizzo, bold = true, fontSizePt = 6.0),
                TechnicalCell.Text(disp.tipo, align = CellTextAlign.CENTER, fontSizePt = 5.5),
                TechnicalCell.Text(disp.installazione ?: "", align = CellTextAlign.CENTER, fontSizePt = 5.5),
                TechnicalCell.Text(disp.descrizione, align = CellTextAlign.LEFT, fontSizePt = 5.5),
                TechnicalCell.Text(disp.zona ?: "", align = CellTextAlign.CENTER, fontSizePt = 5.5),
                TechnicalCell.Text(disp.dataInstallazione ?: "", align = CellTextAlign.CENTER, fontSizePt = 5.5),
                TechnicalCell.Radio(esitoRadioName, "OK", "Prova Allarme OK"),
                TechnicalCell.Radio(esitoRadioName, "NON_OK", "Prova Allarme NON OK"),
                TechnicalCell.TextInput(noteFieldName, label = "Note", fontSizePt = 7.5)
            )

            TechnicalRow(heightCm = 0.5, cells = cells)
        }

        val banner = "ELENCO COMPONENTI IMPIANTO RILEVAZIONE/SEGNALAZIONE INCENDI"
        val subtitolo = "Registro componenti a Loop e verifiche periodiche (UNI 9795 / UNI 11224)"

        return TechnicalSheetData(
            codScheda = ri.codIntervento,
            oggetto = "RI",
            titoloBanner = banner,
            sottotitoloBanner = subtitolo,
            impiantoLabel = ri.nomeCompleto,
            sitoLabel = null,
            normative = "NORME CEI 78-17:2015, CEI 0-10:2012, CEI EN 50110:2014, CEI 11-27:2014, CEI 64-8:2012, CEI 64-14:2007, UNI 9795, UNI 11224",
            campoAcroFormPrefix = "ril",
            columns = columns,
            rows = rows,
            footerDocTitle = banner
        )
    }
}
