package manutenzioni.app.service.attachments

import manutenzioni.app.service.HtmlService
import manutenzioni.domain.model.*
import kotlin.test.*

class TechnicalAttachmentTest {

    private val quadroProvider = QuadroBtAttachmentProvider()
    private val emergenzaProvider = EmergenzaAttachmentProvider()
    private val renderer = TechnicalSheetHtmlRenderer()
    private val htmlService = HtmlService()

    @Test
    fun `QuadroBtAttachmentProvider canHandle riconosce solo QuadroBT con interruttori`() {
        val quadroVuoto = QuadroBT(codIntervento = "Q", nomeCompleto = "Quadro Generale", listaInterruttori = emptyList())
        val quadroPieno = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadro Generale",
            listaInterruttori = listOf(InterruttoreBT(nome = "Magnetotermico 16A"))
        )
        val impiantoStandard = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo Elettrogeno")

        assertFalse(quadroProvider.canHandle(quadroVuoto))
        assertTrue(quadroProvider.canHandle(quadroPieno))
        assertFalse(quadroProvider.canHandle(impiantoStandard))
    }

    @Test
    fun `QuadroBtAttachmentProvider srotola le quantita maggiori di uno con suffissi corretti`() {
        val quadro = QuadroBT(
            codIntervento = "Q",
            sigla = "QG",
            descrizioneQuadro = "Locale Tecnico PT",
            nomeCompleto = "Quadro Generale",
            listaInterruttori = listOf(
                InterruttoreBT(
                    nome = "Differenziale Puro",
                    produttore = "ABB",
                    codiceArticolo = "F202AC-25",
                    siglaCircuito = "QF1",
                    quantita = 2,
                    caratteristicheTecniche = mapOf("Idn" to "0.03A")
                )
            )
        )

        val sheetData = quadroProvider.buildSheetData(quadro, Periodo(TipoPeriodo.A, 1), "Cliente Test")
        assertNotNull(sheetData)
        assertEquals("Q - QG", sheetData.codScheda)
        assertEquals("ELENCO INTERRUTTORI DIFFERENZIALI", sheetData.titoloBanner)
        assertEquals(2, sheetData.rows.size)

        // Verifica srotolamento QF1.1 e QF1.2
        val row1 = sheetData.rows[0]
        val row2 = sheetData.rows[1]
        val cell1Sigla = (row1.cells[1] as? manutenzioni.domain.service.attachments.TechnicalCell.Text)?.text
        val cell2Sigla = (row2.cells[1] as? manutenzioni.domain.service.attachments.TechnicalCell.Text)?.text
        assertEquals("QF1.1", cell1Sigla)
        assertEquals("QF1.2", cell2Sigla)

        // Verifica rendering HTML
        val html = renderer.render(sheetData, Periodo(TipoPeriodo.A, 1), "Cliente Test")
        assertContains(html, "width: 18.4cm")
        assertContains(html, "QF1.1")
        assertContains(html, "QF1.2")
        assertContains(html, "ABB F202AC-25")
        assertContains(html, "0.03A")
        assertContains(html, "tasto_diff_Q_1")
        assertContains(html, "tasto_diff_Q_2")
    }

    @Test
    fun `EmergenzaAttachmentProvider canHandle riconosce solo ImpiantoEmergenza con lampade`() {
        val emergenzaVuoto = ImpiantoEmergenza(codIntervento = "EM", nomeCompleto = "Illuminazione di Emergenza", listaLampade = emptyList())
        val emergenzaPieno = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza",
            listaLampade = listOf(LampadaEmergenza(sigla = "EM-01", modello = "Beghelli Formula 65"))
        )
        val quadro = QuadroBT(codIntervento = "Q", nomeCompleto = "Quadro")

        assertFalse(emergenzaProvider.canHandle(emergenzaVuoto))
        assertTrue(emergenzaProvider.canHandle(emergenzaPieno))
        assertFalse(emergenzaProvider.canHandle(quadro))
    }

    @Test
    fun `EmergenzaAttachmentProvider genera premessa e sheetData conformi alle norme UNI 11222`() {
        val emergenza = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione Emergenza Piano 1",
            listaLampade = listOf(
                LampadaEmergenza(
                    sigla = "EM-01",
                    modello = "Beghelli Formula 65",
                    produttore = "Beghelli",
                    autonomia = "1h",
                    posizione = "Corridoio Ovest",
                    dataUltimoCambioBatteria = "2024-05-10"
                ),
                LampadaEmergenza(
                    sigla = "EM-02",
                    modello = "Eaton NEXI 600",
                    produttore = "Eaton",
                    autonomia = "3h",
                    posizione = "Uscita Sicurezza Scala B"
                )
            )
        )

        val summary = emergenzaProvider.buildPremessaSummary(emergenza)
        assertNotNull(summary)
        assertContains(summary, "Lampade di emergenza presenti (2):")
        assertContains(summary, "[EM-01] Beghelli Formula 65 — Beghelli (Autonomia: 1h) | Corridoio Ovest")
        assertContains(summary, "[EM-02] Eaton NEXI 600 — Eaton (Autonomia: 3h) | Uscita Sicurezza Scala B")

        val sheetData = emergenzaProvider.buildSheetData(emergenza, Periodo(TipoPeriodo.M, 6), "Ospedale Civile")
        assertNotNull(sheetData)
        assertEquals("EM", sheetData.codScheda)
        assertEquals("LAMPADE DI EMERGENZA", sheetData.titoloBanner)
        assertContains(sheetData.normative, "UNI 11222")
        assertEquals(2, sheetData.rows.size)

        // Verifica rendering HTML
        val html = renderer.render(sheetData, Periodo(TipoPeriodo.M, 6), "Ospedale Civile")
        assertContains(html, "width: 18.4cm")
        assertContains(html, "sheet--lampade")
        assertContains(html, "EM-01")
        assertContains(html, "Beghelli Formula 65")
        assertContains(html, "Corridoio Ovest")
        assertContains(html, "2024-05-10")
        assertContains(html, "EM-02")
        assertContains(html, "Eaton NEXI 600")

        // Radio button AcroForm
        assertContains(html, "name=\"emerg_funz_EM_1\"")
        assertContains(html, "name=\"emerg_aut_EM_1\"")
        assertContains(html, "name=\"emerg_note_EM_1\"")
        assertContains(html, "name=\"emerg_funz_EM_2\"")
        assertContains(html, "name=\"emerg_aut_EM_2\"")
        assertContains(html, "name=\"emerg_note_EM_2\"")
    }

    @Test
    fun `HtmlService genera end-to-end secondo foglio per ImpiantoEmergenza`() {
        val emergenza = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione Emergenza",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica visiva lampade", Periodo(TipoPeriodo.M, 6))
            ),
            listaLampade = listOf(
                LampadaEmergenza(sigla = "EM-01", modello = "Schneider Exiway", autonomia = "1h", posizione = "Atrio")
            )
        )

        val html = htmlService.buildHtml(
            impianto = emergenza,
            attivitaFiltrate = emergenza.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Azienda Ospedaliera"
        )

        // Foglio 1: attività e premessa
        assertContains(html, "Verifica visiva lampade")
        assertContains(html, "Lampade di emergenza presenti (1):")
        assertContains(html, "Azienda Ospedaliera")

        // Foglio 2: secondo foglio allegato
        assertContains(html, "page-break-before: always")
        assertContains(html, "sheet--lampade")
        assertContains(html, "LAMPADE DI EMERGENZA")
        assertContains(html, "Schneider Exiway")
        assertContains(html, "Atrio")
        assertContains(html, "emerg_funz_EM_1")
    }

    @Test
    fun `Impianto standard con codice EM viene riconosciuto e convertito a ImpiantoEmergenza`() {
        val standardEM = ImpiantoStandard(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione emergenza"
        )
        val emerg = ImpiantoEmergenza(
            id = standardEM.id,
            codIntervento = standardEM.codIntervento,
            nomeCompleto = standardEM.nomeCompleto,
            listaLampade = listOf(LampadaEmergenza(sigla = "EM-01", modello = "Beghelli"))
        )
        assertTrue(emergenzaProvider.canHandle(emerg))
        val summary = emergenzaProvider.buildPremessaSummary(emerg)
        assertNotNull(summary)
        assertContains(summary, "Lampade di emergenza presenti (1):")
    }

    @Test
    fun `conversione PDF genera campi AcroForm per le note delle lampade`() {
        val emergenza = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione Emergenza",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica visiva lampade", Periodo(TipoPeriodo.M, 6))
            ),
            listaLampade = listOf(
                LampadaEmergenza(sigla = "EM-01", modello = "Schneider Exiway", autonomia = "1h", posizione = "Atrio")
            )
        )

        val html = htmlService.buildHtml(
            impianto = emergenza,
            attivitaFiltrate = emergenza.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Azienda Ospedaliera"
        )

        val tempHtml = java.io.File.createTempFile("test_emerg_", ".html")
        val tempPdf = java.io.File.createTempFile("test_emerg_", ".pdf")
        try {
            tempHtml.writeText(html)
            val pdfService = manutenzioni.app.service.Pdf()
            pdfService.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            val pdfDoc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfReader(tempPdf))
            val form = com.itextpdf.forms.PdfAcroForm.getAcroForm(pdfDoc, false)
            assertNotNull(form, "Il form AcroForm deve esistere")
            val fieldNames = form.allFormFields.keys
            println("Campi AcroForm trovati: $fieldNames")
            assertTrue(fieldNames.contains("emerg_note_EM_1"), "Il campo emerg_note_EM_1 deve essere presente in AcroForm")

            val startingDate = form.getField("starting_date") as? com.itextpdf.forms.fields.PdfTextFormField
            val dataInizio = form.getField("emerg_data_inizio") as? com.itextpdf.forms.fields.PdfTextFormField
            val manutentore = form.getField("emerg_manutentore") as? com.itextpdf.forms.fields.PdfTextFormField
            val emergNote = form.getField("emerg_note_EM_1") as? com.itextpdf.forms.fields.PdfTextFormField
            val mainNote = form.getField("note_EM_1") as? com.itextpdf.forms.fields.PdfTextFormField
            assertNotNull(emergNote, "Il campo emerg_note_EM_1 deve essere istanziato come TextFormField")
            val emergRect = emergNote.widgets.firstOrNull()?.rectangle?.toRectangle()
            assertNotNull(emergRect, "Il rettangolo del widget emergNote non deve essere null")
            assertTrue(emergRect.height >= 10.0f, "L'altezza del campo note deve essere di almeno 10pt per essere compilabile, trovata: ${emergRect.height}")
            assertNotNull(emergNote.widgets.firstOrNull()?.borderStyle, "Il campo note deve avere un borderStyle visibile per indicare che è un campo compilabile")

            // Test compilazione del campo
            emergNote.setValue("Batteria sostituita il 15/03/2026")
            kotlin.test.assertEquals("Batteria sostituita il 15/03/2026", emergNote.valueAsString)

            pdfDoc.close()
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }
}
