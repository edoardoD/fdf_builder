package manutenzioni.app.service.attachments

import manutenzioni.domain.model.*
import manutenzioni.domain.service.attachments.TechnicalCell
import kotlin.test.*

class AntincendioAttachmentProviderTest {

    private val provider = AntincendioAttachmentProvider()
    private val renderer = TechnicalSheetHtmlRenderer()

    private val dispositivo1 = DispositivoRilevazione(
        loop = 1,
        indirizzo = "1",
        tipo = "TERMO",
        installazione = "A VISTA",
        descrizione = "SOTTOCENTRALE TERMICA",
        zona = "PINT",
        dataInstallazione = "10/2024",
        note = null
    )

    private val dispositivo2 = DispositivoRilevazione(
        loop = 1,
        indirizzo = "2",
        tipo = "FUMO",
        installazione = "CONTROSOFFITTO",
        descrizione = "LOCALE QUADRO ELETTRICO",
        zona = "PINT",
        dataInstallazione = "10/2024",
        note = null
    )

    private val dispositivo3 = DispositivoRilevazione(
        loop = 2,
        indirizzo = "RIV.L2.1",
        tipo = "FUMO",
        installazione = "A VISTA",
        descrizione = "CORRIDOIO PIANO TERRA",
        zona = "ZONA 2",
        dataInstallazione = "05/2023",
        note = null
    )

    @Test
    fun `canHandle riconosce RilevazioneAntincendio con dispositivi`() {
        val riVuoto = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = emptyList()
        )
        val riConDispositivi = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(dispositivo1)
        )
        val rig = RilevazioneGas(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas"
        )

        assertFalse(provider.canHandle(riVuoto))
        assertTrue(provider.canHandle(riConDispositivi))
        assertFalse(provider.canHandle(rig))
    }

    @Test
    fun `buildSheetData genera tabella con larghezza esattamente pari a 18_4 cm`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(dispositivo1, dispositivo2, dispositivo3)
        )

        val sheetData = provider.buildSheetData(ri, Periodo(TipoPeriodo.M, 6), "Cliente Test S.p.A.")
        assertNotNull(sheetData)
        assertEquals("RI", sheetData.codScheda)
        assertEquals("ELENCO COMPONENTI IMPIANTO RILEVAZIONE/SEGNALAZIONE INCENDI", sheetData.titoloBanner)

        val totalWidth = sheetData.columns.sumOf { it.totalWidthCm }
        assertEquals(18.4, totalWidth, 0.001, "La larghezza della tabella deve essere esattamente 18.4 cm")
    }

    @Test
    fun `buildSheetData popola correttamente righe, celle e campi AcroForm`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(dispositivo1, dispositivo2, dispositivo3)
        )

        val sheetData = provider.buildSheetData(ri, Periodo(TipoPeriodo.M, 6), "Cliente Test S.p.A.")
        assertNotNull(sheetData)
        assertEquals(3, sheetData.rows.size)

        val row1 = sheetData.rows[0]
        val indirizzoCell = row1.cells[0] as TechnicalCell.Text
        assertEquals("L1.1", indirizzoCell.text)

        val tipoCell = row1.cells[1] as TechnicalCell.Text
        assertEquals("TERMO", tipoCell.text)

        val descCell = row1.cells[3] as TechnicalCell.Text
        assertEquals("SOTTOCENTRALE TERMICA", descCell.text)

        // Radio buttons esito
        val radioOk = row1.cells[6] as TechnicalCell.Radio
        val radioNonOk = row1.cells[7] as TechnicalCell.Radio
        assertEquals("ril_esito_RI_1", radioOk.groupName)
        assertEquals("OK", radioOk.optionValue)
        assertEquals("ril_esito_RI_1", radioNonOk.groupName)
        assertEquals("NON_OK", radioNonOk.optionValue)

        // Input note
        val textNote = row1.cells[8] as TechnicalCell.TextInput
        assertEquals("ril_note_RI_1", textNote.fieldName)

        // Row 3 con indirizzo preformattato RIV.L2.1
        val row3 = sheetData.rows[2]
        val indirizzo3 = (row3.cells[0] as TechnicalCell.Text).text
        assertEquals("RIV.L2.1", indirizzo3)
    }

    @Test
    fun `buildPremessaSummary riepiloga dispositivi per loop`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(dispositivo1, dispositivo2, dispositivo3)
        )

        val summary = provider.buildPremessaSummary(ri)
        assertNotNull(summary)
        assertTrue(summary.contains("Dispositivi e moduli di rilevazione configurati (3):"))
        assertTrue(summary.contains("• Loop 1: 2 componenti"))
        assertTrue(summary.contains("• Loop 2: 1 componenti"))
    }

    @Test
    fun `renderStandaloneHtml genera HTML valido con tabella e form`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(dispositivo1)
        )

        val sheetData = provider.buildSheetData(ri, Periodo(TipoPeriodo.M, 6), "Acciaierie Italiane")
        assertNotNull(sheetData)

        val html = renderer.renderStandaloneHtml(sheetData, Periodo(TipoPeriodo.M, 6), "Acciaierie Italiane")
        assertTrue(html.contains("ELENCO COMPONENTI IMPIANTO RILEVAZIONE/SEGNALAZIONE INCENDI"))
        assertTrue(html.contains("ril_esito_RI_1"))
        assertTrue(html.contains("SOTTOCENTRALE TERMICA"))
        assertTrue(html.contains("L1.1"))
    }
}
