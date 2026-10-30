package manutenzioni.app.service

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfButtonFormField
import com.itextpdf.forms.fields.PdfTextFormField
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfName
import com.itextpdf.kernel.pdf.PdfReader
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.TipoPeriodo
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AcroFormFullCellAdaptationTest {

    private val htmlService = HtmlService()
    private val pdfEngine = Pdf()

    @Test
    fun `test foglio 1 - acroform text fields eliminate inner borders and span full cell`() {
        val attivitaList = listOf(
            Attivita(
                tipoAttivita = "Controllo",
                frequenza = Periodo(TipoPeriodo.M, 6),
                descrizione = "Verifica generale serraggi"
            )
        )
        val quadro = QuadroBT(
            id = "q-test",
            cantiereId = "cant-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro Principale",
            sigla = "QG",
            descrizioneQuadro = "Quadro Generale di Distribuzione",
            listaAttivita = attivitaList
        )

        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = attivitaList,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Azienda Alfa S.r.l.",
            cantiereNome = "Sede Principale",
            dataInizioContratto = "01/01/2026",
            dataInterventoPrecedente = "01/07/2025",
            dataInizioIntervento = "15/01/2026",
            nomeManutentore = "Mario Rossi"
        )

        val tempHtml = File.createTempFile("test_foglio1_", ".html")
        val tempPdf = File.createTempFile("test_foglio1_", ".pdf")

        try {
            tempHtml.writeText(html)
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form, "AcroForm deve essere presente nel PDF")

                // Verifica campi data di testata
                val startingDate = form.getField("starting_date") as? PdfTextFormField
                assertNotNull(startingDate, "starting_date deve esistere")
                val startingWidget = startingDate.widgets.firstOrNull()
                assertNotNull(startingWidget)
                assertNull(startingWidget.borderStyle, "starting_date non deve avere borderStyle interno (rimosso da Pdf)")
                assertNull(startingWidget.pdfObject.get(PdfName.BS), "starting_date non deve avere /BS nel dizionario PDF")

                // Verifica campi note della tabella
                val note1 = form.getField("note_Q_1") as? PdfTextFormField
                assertNotNull(note1, "note_Q_1 deve esistere")
                val note1Widget = note1.widgets.firstOrNull()
                assertNotNull(note1Widget)
                assertNull(note1Widget.borderStyle, "note_Q_1 non deve avere borderStyle interno")
                val note1Rect = note1Widget.rectangle.toRectangle()
                assertTrue(note1Rect.height >= 9.0f, "L'altezza della casella nota deve adattarsi alla riga (trovato: ${note1Rect.height})")

                // Verifica che i radio button non siano stati alterati
                val radioField = form.getField("esito_Q_1") as? PdfButtonFormField
                assertNotNull(radioField, "esito_Q_1 deve esistere")
                assertTrue(radioField.isRadio, "esito_Q_1 deve essere un radio button")

                // Test compilazione
                startingDate.setValue("02/02/2026")
                assertEquals("02/02/2026", startingDate.valueAsString)
                note1.setValue("Eseguito con esito positivo")
                assertEquals("Eseguito con esito positivo", note1.valueAsString)
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `test foglio 2 allegato differenziali - input text box seamless without inner border`() {
        val attivitaList = listOf(
            Attivita(
                tipoAttivita = "Verifica",
                frequenza = Periodo(TipoPeriodo.A, 1),
                descrizione = "Verifica differenziali"
            )
        )
        val quadro = QuadroBT(
            id = "q-diff",
            cantiereId = "cant-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro BT Laboratorio",
            sigla = "Q-LAB",
            descrizioneQuadro = "Quadro Laboratorio",
            listaAttivita = attivitaList,
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "diff-1",
                    nome = "Differenziale Generale",
                    siglaCircuito = "DIFF-1",
                    produttore = "ABB",
                    codiceArticolo = "DS201",
                    quantita = 1
                )
            )
        )

        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = attivitaList,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Ospedale Regionale",
            cantiereNome = "Presidio Ovest",
            dataInizioContratto = "10/02/2026",
            nomeManutentore = "Ing. Bianchi"
        )

        val tempHtml = File.createTempFile("test_foglio2_", ".html")
        val tempPdf = File.createTempFile("test_foglio2_", ".pdf")

        try {
            tempHtml.writeText(html)
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                assertEquals(2, pdfDoc.numberOfPages, "Deve generare 2 pagine (Foglio 1 + Foglio 2 differenziali)")
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form)

                val misuraField = form.getField("misura_diff_Q_1") as? PdfTextFormField
                assertNotNull(misuraField, "misura_diff_Q_1 deve esistere")
                val widget = misuraField.widgets.firstOrNull()
                assertNotNull(widget)
                assertNull(widget.borderStyle, "misura_diff non deve avere borderStyle interno")
                assertNull(widget.pdfObject.get(PdfName.BS))

                misuraField.setValue("28.4")
                assertEquals("28.4", misuraField.valueAsString)
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `test foglio riepilogo generale R28 - seamless inputs on header and notes`() {
        val renderer = RiepilogoInterventoHtmlRenderer()
        val cantiere = Cantiere(
            id = "c-riep",
            clienteId = "cl-1",
            nome = "Cantiere Milano Centro",
            indirizzo = "Via Dante 10",
            dataInizioContratto = "01/01/2026",
            dataInterventoPrecedente = "01/07/2025",
            dataInizioIntervento = "20/01/2026",
            nomeManutentore = "Tecnico Specializzato"
        )
        val cliente = Cliente(id = "cl-1", nome = "Gruppo Immobiliare")
        val impianto = QuadroBT(id = "q-1", cantiereId = cantiere.id, codIntervento = "Q", nomeCompleto = "Quadro Principale")

        val html = renderer.buildRiepilogoHtml(
            cliente = cliente,
            cantiere = cantiere,
            impianti = listOf(impianto)
        )

        val tempHtml = File.createTempFile("test_riepilogo_", ".html")
        val tempPdf = File.createTempFile("test_riepilogo_", ".pdf")

        try {
            tempHtml.writeText(html)
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form)

                val riepStarting = form.getField("riepilogo_starting_date") as? PdfTextFormField
                assertNotNull(riepStarting)
                assertNull(riepStarting.widgets.firstOrNull()?.borderStyle)

                val riepNote = form.getField("riepilogo_note_1") as? PdfTextFormField
                assertNotNull(riepNote)
                assertNull(riepNote.widgets.firstOrNull()?.borderStyle)

                val osservazioni = form.getField("riepilogo_osservazioni_generali") as? PdfTextFormField
                assertNotNull(osservazioni)
                assertNull(osservazioni.widgets.firstOrNull()?.borderStyle)

                osservazioni.setValue("Tutti gli impianti sono conformi alle normative CEI.")
                assertEquals("Tutti gli impianti sono conformi alle normative CEI.", osservazioni.valueAsString)
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }
}
