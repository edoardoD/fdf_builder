package manutenzioni.app.service.attachments

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.attachments.*

/**
 * Provider per la generazione dell'allegato "LAMPADE DI EMERGENZA"
 * associato all'impianto di illuminazione di emergenza.
 */
class EmergenzaAttachmentProvider : TechnicalAttachmentProvider {

    override fun canHandle(impianto: Impianto): Boolean {
        return impianto is ImpiantoEmergenza && impianto.listaLampade.isNotEmpty()
    }

    override fun buildPremessaSummary(impianto: Impianto): String? {
        val emerg = impianto as? ImpiantoEmergenza ?: return null
        if (emerg.listaLampade.isEmpty()) return null

        val totale = emerg.listaLampade.size
        return buildString {
            append("Lampade di emergenza presenti ($totale):\n")
            append(emerg.listaLampade.joinToString("\n") {
                val sigla = if (it.sigla.isNotBlank()) "[${it.sigla}] " else ""
                val prod = if (!it.produttore.isNullOrBlank()) " — ${it.produttore}" else ""
                val pos = if (!it.posizione.isNullOrBlank()) " | ${it.posizione}" else ""
                "• $sigla${it.modello}$prod (Autonomia: ${it.autonomia})$pos"
            })
        }
    }

    override fun buildSheetData(
        impianto: Impianto,
        frequenza: Periodo,
        clienteNome: String?
    ): TechnicalSheetData? {
        val emerg = impianto as? ImpiantoEmergenza ?: return null
        if (emerg.listaLampade.isEmpty()) return null

        val columns = listOf(
            TableColumn("N°", 0.6),
            TableColumn("Sigla", 1.2),
            TableColumn("Posizione", 2.5),
            TableColumn("Modello", 2.8),
            TableColumn("Produttore", 1.8),
            TableColumn("Autonomia", 1.1),
            TableColumn("Ult. Batteria", 1.5),
            TableColumn(
                title = "Test Funzionam.",
                subColumns = listOf(
                    SubColumn("OK", 0.9),
                    SubColumn("NON OK", 1.1)
                )
            ),
            TableColumn(
                title = "Test Autonomia",
                subColumns = listOf(
                    SubColumn("OK", 0.9),
                    SubColumn("NON OK", 1.1)
                )
            ),
            TableColumn("Note", 2.9)
        )

        val rows = emerg.listaLampade.mapIndexed { idx, lampada ->
            val rowNum = idx + 1
            val funzRadioName = "emerg_funz_${emerg.codIntervento}_$rowNum"
            val autRadioName = "emerg_aut_${emerg.codIntervento}_$rowNum"
            val noteFieldName = "emerg_note_${emerg.codIntervento}_$rowNum"

            val cells = listOf(
                TechnicalCell.Text(rowNum.toString(), fontSizePt = 6.0),
                TechnicalCell.Text(lampada.sigla, bold = true, fontSizePt = 6.0),
                TechnicalCell.Text(lampada.posizione ?: "", align = CellTextAlign.LEFT, fontSizePt = 5.5),
                TechnicalCell.Text(lampada.modello, align = CellTextAlign.LEFT, fontSizePt = 5.5),
                TechnicalCell.Text(lampada.produttore ?: "", align = CellTextAlign.CENTER, fontSizePt = 5.5),
                TechnicalCell.Text(lampada.autonomia, fontSizePt = 6.0),
                TechnicalCell.Text(lampada.dataUltimoCambioBatteria ?: "", fontSizePt = 5.5),
                TechnicalCell.Radio(funzRadioName, "OK", "Funzionamento OK"),
                TechnicalCell.Radio(funzRadioName, "NON_OK", "Funzionamento NON OK"),
                TechnicalCell.Radio(autRadioName, "OK", "Autonomia OK"),
                TechnicalCell.Radio(autRadioName, "NON_OK", "Autonomia NON OK"),
                TechnicalCell.TextInput(noteFieldName, label = "Note", fontSizePt = 7.5)
            )

            TechnicalRow(heightCm = 0.5, cells = cells)
        }

        return TechnicalSheetData(
            codScheda = emerg.codIntervento,
            oggetto = "EMERG",
            titoloBanner = "LAMPADE DI EMERGENZA",
            sottotitoloBanner = "Elenco e verifiche periodiche illuminazione di emergenza",
            impiantoLabel = emerg.nomeCompleto,
            sitoLabel = null,
            normative = "NORME CEI 64-8:2012, CEI EN 60598-2-22:2015, CEI EN 1838:2014, UNI 11222:2013",
            campoAcroFormPrefix = "emerg",
            columns = columns,
            rows = rows,
            footerDocTitle = "LAMPADE DI EMERGENZA"
        )
    }
}
