package manutenzioni.app.service.attachments

import manutenzioni.domain.model.DispositivoRilevazione
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.service.attachments.*

/**
 * Provider per la generazione degli allegati "ELENCO COMPONENTI RILEVAZIONE INCENDI (ELENCO RIL)"
 * associati all'impianto di rivelazione e allarme incendi (RI) secondo normative UNI 9795 e UNI 11224.
 *
 * Produce fogli distinti e ordinati per:
 * 1. Componenti Comuni (centrale, alimentatore, sirene, ecc.)
 * 2. Per ogni Loop 1..8 popolato:
 *    - Rivelatori Loop N (ordinati per indirizzo 1..150)
 *    - Moduli Loop N (ordinati per indirizzo 1..150)
 */
class AntincendioAttachmentProvider : TechnicalAttachmentProvider {

    companion object {
        private const val NORMATIVE_RI =
            "NORME CEI 78-17:2015, CEI 0-10:2012, CEI EN 50110:2014, CEI 11-27:2014, CEI 64-8:2012, CEI 64-14:2007, UNI 9795, UNI 11224"
    }

    override fun canHandle(impianto: Impianto): Boolean {
        return (impianto is RilevazioneAntincendio || impianto.codIntervento.equals("RI", ignoreCase = true)) &&
                ((impianto as? RilevazioneAntincendio)?.listaDispositivi?.isNotEmpty() == true)
    }

    override fun buildPremessaSummary(impianto: Impianto): String? = null

    override fun buildSheetData(
        impianto: Impianto,
        frequenza: Periodo,
        clienteNome: String?
    ): TechnicalSheetData? = buildSheetsData(impianto, frequenza, clienteNome).firstOrNull()

    override fun buildSheetsData(
        impianto: Impianto,
        frequenza: Periodo,
        clienteNome: String?
    ): List<TechnicalSheetData> {
        val ri = impianto as? RilevazioneAntincendio ?: return emptyList()
        if (ri.listaDispositivi.isEmpty()) return emptyList()

        val sheets = mutableListOf<TechnicalSheetData>()

        // 1. Componenti Comuni
        val comuni = ri.listaDispositivi.filterIsInstance<DispositivoRilevazione.ComponenteComune>()
        if (comuni.isNotEmpty()) {
            sheets.add(
                buildSectionSheet(
                    ri = ri,
                    dispositivi = comuni,
                    titoloBanner = "ELENCO COMPONENTI COMUNI — RILEVAZIONE INCENDI",
                    sottotitoloBanner = "Apparecchiature comuni di centrale e segnalazione (UNI 9795 / UNI 11224)",
                    prefix = "ril_comuni"
                )
            )
        }

        // 2. Loop 1..8: Rivelatori e Moduli su pagine distinte
        val rivelatoriByLoop = ri.listaDispositivi
            .filterIsInstance<DispositivoRilevazione.RivelatoreLoop>()
            .groupBy { it.loop }
        val moduliByLoop = ri.listaDispositivi
            .filterIsInstance<DispositivoRilevazione.ModuloLoop>()
            .groupBy { it.loop }

        for (loopNum in 1..8) {
            val rivelatori = rivelatoriByLoop[loopNum].orEmpty().sortedBy { it.numeroIndirizzo }
            if (rivelatori.isNotEmpty()) {
                sheets.add(
                    buildSectionSheet(
                        ri = ri,
                        dispositivi = rivelatori,
                        titoloBanner = "ELENCO RIVELATORI LOOP $loopNum — RILEVAZIONE INCENDI",
                        sottotitoloBanner = "Rivelatori indirizzati (1..150) su Loop $loopNum (UNI 9795 / UNI 11224)",
                        prefix = "ril_l${loopNum}_riv"
                    )
                )
            }

            val moduli = moduliByLoop[loopNum].orEmpty().sortedBy { it.numeroIndirizzo }
            if (moduli.isNotEmpty()) {
                sheets.add(
                    buildSectionSheet(
                        ri = ri,
                        dispositivi = moduli,
                        titoloBanner = "ELENCO MODULI LOOP $loopNum — RILEVAZIONE INCENDI",
                        sottotitoloBanner = "Moduli di comando e pulsanti indirizzati (1..150) su Loop $loopNum (UNI 9795 / UNI 11224)",
                        prefix = "ril_l${loopNum}_mod"
                    )
                )
            }
        }

        return sheets
    }

    private fun buildSectionSheet(
        ri: RilevazioneAntincendio,
        dispositivi: List<DispositivoRilevazione>,
        titoloBanner: String,
        sottotitoloBanner: String,
        prefix: String
    ): TechnicalSheetData {
        // Larghezze millimetriche: 2.0 + 2.0 + 1.8 + 3.5 + 1.3 + 1.6 + (0.6 * 6) + 2.6 = 18.4 cm
        val columns = listOf(
            TableColumn("Indirizzo", 2.0),
            TableColumn("Tipo", 2.0),
            TableColumn("Installazione", 1.8),
            TableColumn("Descrizione", 3.5),
            TableColumn("Zona", 1.3),
            TableColumn("Data Inst./Rev.", 1.6),
            TableColumn(
                title = "Esito",
                subColumns = listOf(
                    SubColumn("P", 0.6),
                    SubColumn("PI", 0.6),
                    SubColumn("NA", 0.6),
                    SubColumn("NP", 0.6),
                    SubColumn("VN", 0.6),
                    SubColumn("B", 0.6)
                )
            ),
            TableColumn("Note", 2.6)
        )

        val rows = dispositivi.mapIndexed { idx, disp ->
            val rowNum = idx + 1
            val esitoRadioName = "${prefix}_esito_${ri.codIntervento}_$rowNum"
            val noteFieldName = "${prefix}_note_${ri.codIntervento}_$rowNum"

            val cells = listOf(
                TechnicalCell.Text(disp.indirizzo, bold = true, fontSizePt = 7.5),
                TechnicalCell.Text(disp.labelTipo, align = CellTextAlign.CENTER, fontSizePt = 7.0),
                TechnicalCell.Text(disp.installazione ?: "", align = CellTextAlign.CENTER, fontSizePt = 7.0),
                TechnicalCell.Text(disp.descrizione, align = CellTextAlign.LEFT, fontSizePt = 7.2),
                TechnicalCell.Text(disp.zona ?: "", align = CellTextAlign.CENTER, fontSizePt = 7.0),
                TechnicalCell.Text(disp.dataInstallazione ?: "", align = CellTextAlign.CENTER, fontSizePt = 7.0),
                TechnicalCell.Radio(esitoRadioName, "P", "Positivo"),
                TechnicalCell.Radio(esitoRadioName, "PI", "Positivo prev. int."),
                TechnicalCell.Radio(esitoRadioName, "NA", "Rimandato"),
                TechnicalCell.Radio(esitoRadioName, "NP", "Non presente"),
                TechnicalCell.Radio(esitoRadioName, "VN", "Vedere nota"),
                TechnicalCell.Radio(esitoRadioName, "B", "Costr./Assist."),
                TechnicalCell.TextInput(noteFieldName, label = "Note", fontSizePt = 8.0)
            )

            TechnicalRow(heightCm = 0.58, cells = cells)
        }

        return TechnicalSheetData(
            codScheda = ri.codIntervento,
            oggetto = "RI",
            titoloBanner = titoloBanner,
            sottotitoloBanner = sottotitoloBanner,
            impiantoLabel = ri.nomeCompleto,
            sitoLabel = null,
            normative = NORMATIVE_RI,
            campoAcroFormPrefix = prefix,
            columns = columns,
            rows = rows,
            footerDocTitle = titoloBanner
        )
    }
}
