package manutenzioni.app.service

import manutenzioni.app.service.html.AttivitaTableHtmlBuilder
import manutenzioni.app.service.html.HtmlEscaper
import manutenzioni.app.service.html.HtmlTemplateLoader
import manutenzioni.app.service.html.SchedaHeaderFormatter
import manutenzioni.app.service.html.SignatureImageRenderer
import manutenzioni.domain.model.*
import manutenzioni.domain.service.HtmlRenderContext
import java.io.File
import kotlin.test.*

class HtmlServiceModularTest {

    private val templateLoader = HtmlTemplateLoader()
    private val headerFormatter = SchedaHeaderFormatter()
    private val tableBuilder = AttivitaTableHtmlBuilder()
    private val signatureRenderer = SignatureImageRenderer()
    private val htmlService = HtmlService()

    private val fixtureGE = ImpiantoStandard(
        id = "test-ge-modular",
        codIntervento = "GE",
        nomeCompleto = "Gruppo Elettrogeno",
        premessa = "Premessa di sicurezza per Gruppo Elettrogeno.",
        noteSpecifiche = "Verificare livello gasolio",
        listaAttivita = listOf(
            Attivita(1, "Ispezione", "Controllo visivo quadro", Periodo(TipoPeriodo.M, 1), zona = "Locale GE"),
            Attivita(2, "Prova", "Avviamento a caldo", Periodo(TipoPeriodo.M, 6), zona = "Locale GE"),
            Attivita(3, "Manutenzione", "Sostituzione filtri", Periodo(TipoPeriodo.A, 1), zona = null)
        )
    )

    // ========================================================
    // 1. HtmlEscaper Tests
    // ========================================================

    @Test
    fun `HtmlEscaper escapa tutti i caratteri speciali HTML e XML`() {
        val input = """<div class="test" data-value='100'>Tom & Jerry</div>"""
        val expected = """&lt;div class=&quot;test&quot; data-value=&#39;100&#39;&gt;Tom &amp; Jerry&lt;/div&gt;"""
        assertEquals(expected, HtmlEscaper.escape(input))
    }

    @Test
    fun `HtmlEscaper su stringa vuota o senza caratteri speciali`() {
        assertEquals("", HtmlEscaper.escape(""))
        assertEquals("Test semplice", HtmlEscaper.escape("Test semplice"))
    }

    // ========================================================
    // 2. HtmlTemplateLoader Tests
    // ========================================================

    @Test
    fun `HtmlTemplateLoader carica scheletro e memorizza in cache in-memory`() {
        templateLoader.clearCache()
        assertFalse(templateLoader.isCached(HtmlTemplateLoader.DEFAULT_TEMPLATE))
        assertEquals(0, templateLoader.cacheSize())

        val templateContent = templateLoader.loadTemplate()
        assertTrue(templateContent.isNotEmpty(), "Il template scheletro.html deve essere valorizzato")
        assertTrue(templateContent.contains("<!-- COD_SCHEDA -->"))

        assertTrue(templateLoader.isCached(HtmlTemplateLoader.DEFAULT_TEMPLATE))
        assertEquals(1, templateLoader.cacheSize())

        // Secondo accesso: restituisce la copia cachata istantaneamente
        val cachedContent = templateLoader.loadTemplate()
        assertSame(templateContent, cachedContent)

        templateLoader.clearCache()
        assertEquals(0, templateLoader.cacheSize())
        assertFalse(templateLoader.isCached())
    }

    @Test
    fun `HtmlTemplateLoader lancia IllegalStateException per template inesistente`() {
        assertFailsWith<IllegalStateException> {
            templateLoader.loadTemplate("template_inesistente_xyz.html")
        }
    }

    // ========================================================
    // 3. SchedaHeaderFormatter Tests
    // ========================================================

    @Test
    fun `SchedaHeaderFormatter formatta codice scheda e oggetto polimorficamente`() {
        val quadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG1",
            descrizioneQuadro = "Quadro Generale Principale"
        )
        assertEquals("Q - QG1", headerFormatter.formatCodiceScheda(quadro))
        assertEquals("Quadro Generale Principale - QG1", headerFormatter.formatOggetto(quadro))
        assertEquals("GE", headerFormatter.formatCodiceScheda(fixtureGE))
        assertEquals("Gruppo Elettrogeno", headerFormatter.formatOggetto(fixtureGE))
    }

    @Test
    fun `SchedaHeaderFormatter compone premessa con note specifiche e sommario allegato`() {
        val premessa = headerFormatter.buildPremessa(
            impianto = fixtureGE,
            attachmentSummary = "Sommario componenti allegati: 5 elementi"
        )
        assertContains(premessa, "Premessa di sicurezza per Gruppo Elettrogeno.")
        assertContains(premessa, "Note specifiche cantiere:\nVerificare livello gasolio")
        assertContains(premessa, "Sommario componenti allegati: 5 elementi")
    }

    // ========================================================
    // 4. AttivitaTableHtmlBuilder Tests
    // ========================================================

    @Test
    fun `AttivitaTableHtmlBuilder ordina canonicamente le attivita da mesi ad anni`() {
        val attivitaDisordinate = listOf(
            Attivita(1, "A2", "Attività 2 anni", Periodo(TipoPeriodo.A, 2)),
            Attivita(2, "M6", "Attività 6 mesi", Periodo(TipoPeriodo.M, 6)),
            Attivita(3, "M1", "Attività 1 mese", Periodo(TipoPeriodo.M, 1)),
            Attivita(4, "A1", "Attività 1 anno", Periodo(TipoPeriodo.A, 1)),
            Attivita(5, "M3", "Attività 3 mesi", Periodo(TipoPeriodo.M, 3))
        )
        val ordinate = tableBuilder.sortAttivitaCanonically(attivitaDisordinate)
        assertEquals("M1", ordinate[0].tipoAttivita)
        assertEquals("M3", ordinate[1].tipoAttivita)
        assertEquals("M6", ordinate[2].tipoAttivita)
        assertEquals("A1", ordinate[3].tipoAttivita)
        assertEquals("A2", ordinate[4].tipoAttivita)
    }

    @Test
    fun `AttivitaTableHtmlBuilder genera esattamente 30 righe totali con filler compilabili`() {
        val rowsHtml = tableBuilder.buildAttivitaRows(fixtureGE.listaAttivita, fixtureGE)

        // Verifica che le prime 3 righe abbiano i dati reali
        assertContains(rowsHtml, "<p>1</p>")
        assertContains(rowsHtml, "<p>2</p>")
        assertContains(rowsHtml, "<p>3</p>")
        assertContains(rowsHtml, "Controllo visivo quadro")
        assertContains(rowsHtml, "Avviamento a caldo")
        assertContains(rowsHtml, "Sostituzione filtri")

        // Per la riga 3 con zona null, formatZonaAttivita produce "attività"
        assertContains(rowsHtml, "<p>attività</p>")

        // Verifica che la riga 4 (filler) e la riga 30 (filler) esistano con campi interattivi
        assertContains(rowsHtml, "tipo_GE_4")
        assertContains(rowsHtml, "zona_GE_4")
        assertContains(rowsHtml, "periodicita_GE_4")
        assertContains(rowsHtml, "attivita_GE_4")
        assertContains(rowsHtml, "esito_GE_4")
        assertContains(rowsHtml, "esito_vn_GE_4")
        assertContains(rowsHtml, "note_GE_4")

        assertContains(rowsHtml, "tipo_GE_30")
        assertContains(rowsHtml, "zona_GE_30")
        assertContains(rowsHtml, "periodicita_GE_30")
        assertContains(rowsHtml, "attivita_GE_30")
        assertContains(rowsHtml, "esito_GE_30")
        assertContains(rowsHtml, "esito_vn_GE_30")
        assertContains(rowsHtml, "note_GE_30")
    }

    // ========================================================
    // 5. SignatureImageRenderer Tests
    // ========================================================

    @Test
    fun `SignatureImageRenderer gestisce data URI e percorsi file validi e invalidi`() {
        // 1. Data URI già codificato
        val dataUri = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
        val htmlImg = signatureRenderer.renderSignatureHtml(dataUri, altText = "Firma Tecnico")
        assertTrue(htmlImg.startsWith("""<img src="$dataUri""""))
        assertTrue(htmlImg.contains("""alt="Firma Tecnico""""))

        // 2. Stringa null o vuota
        assertEquals("", signatureRenderer.renderSignatureHtml(null))
        assertEquals("", signatureRenderer.renderSignatureHtml("   "))

        // 3. File inesistente
        assertEquals("", signatureRenderer.renderSignatureHtml("/percorso/fantoccio/non/esistente.png"))

        // 4. File reale temporaneo
        val tempImg = File.createTempFile("test_signature_", ".png")
        try {
            tempImg.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
            val renderedFromFile = signatureRenderer.renderSignatureHtml(tempImg.absolutePath)
            assertTrue(renderedFromFile.startsWith("<img src=\"data:image/png;base64,"))
        } finally {
            tempImg.delete()
        }
    }

    // ========================================================
    // 6. HtmlRenderContext & Facade Integration Tests
    // ========================================================

    @Test
    fun `HtmlRenderContext contiene tutti i campi canonici`() {
        val ctx = HtmlRenderContext(
            impianto = fixtureGE,
            frequenza = Periodo(TipoPeriodo.A, 1),
            attivita = fixtureGE.listaAttivita,
            clienteNome = "Cliente Acme",
            cantiereNome = "Sede Centrale",
            nomeManutentore = "Mario Rossi",
            firmaTecnicoBase64 = "data:image/png;base64,abc",
            firmaClienteBase64 = "data:image/png;base64,def",
            dataInizioContratto = "01/01/2026",
            dataIntervento = "15/10/2026",
            dataInterventoPrecedente = "15/04/2026",
            contextImpianti = listOf(fixtureGE)
        )

        assertEquals("Cliente Acme", ctx.clienteNome)
        assertEquals("Sede Centrale", ctx.cantiereNome)
        assertEquals("15/10/2026", ctx.dataIntervento)
    }

    @Test
    fun `HtmlService supporta override template da HtmlRenderContext senza leggere da disco`() {
        val customTemplate = """
            <!DOCTYPE html>
            <html>
            <body>
                <h1><!-- COD_SCHEDA --> - <!-- OGGETTO --></h1>
                <p>Periodicità: <!-- PERIODICITA --></p>
                <div id="attivita"><!-- ATTIVITA_ROWS --></div>
                <div id="firma"><!-- FIRMA --></div>
            </body>
            </html>
        """.trimIndent()

        val ctx = HtmlRenderContext(
            impianto = fixtureGE,
            frequenza = Periodo(TipoPeriodo.M, 6),
            attivita = listOf(fixtureGE.listaAttivita.first()),
            templateHtml = customTemplate
        )

        val rendered = htmlService.buildHtml(ctx)
        assertTrue(rendered.contains("<h1>GE - Gruppo Elettrogeno</h1>"))
        assertTrue(rendered.contains("<p>Periodicità: 06</p>"))
        assertTrue(rendered.contains("esito_GE_1"))
    }

    // ========================================================
    // 7. Pdf & Strategy Hardening Tests
    // ========================================================

    @Test
    fun `Pdf lancia IllegalArgumentException se il file HTML di input non esiste`() {
        val pdf = Pdf()
        val tempPdf = File.createTempFile("test_fail_", ".pdf")
        try {
            assertFailsWith<IllegalArgumentException> {
                pdf.buildPdf("/percorso/file/inesistente/scheda.html", tempPdf.absolutePath)
            }
        } finally {
            tempPdf.delete()
        }
    }

    @Test
    fun `AbstractPdfGeneratorStrategy genera batch sia in modalita sincrona che sospesa`() {
        kotlinx.coroutines.runBlocking {
            val testOutputDir = File.createTempFile("test_batch_dir_", "").apply {
                delete()
                mkdirs()
            }
            val strategy = object : manutenzioni.domain.strategy.AbstractPdfGeneratorStrategy() {
                override fun generate(
                    impianto: Impianto,
                    frequenza: Periodo,
                    outputPath: String,
                    clienteNome: String?,
                    contextImpianti: List<Impianto>,
                    cantiereNome: String?,
                    nomeManutentore: String?,
                    firmaManutentore: String?,
                    dataInizioContratto: String?,
                    dataIntervento: String?,
                    dataInterventoPrecedente: String?
                ): File {
                    val f = File(outputPath)
                    f.writeText("PDF_CONTENT")
                    return f
                }
            }

            try {
                // Sincrono
                val syncResult = strategy.generateBatch(
                    impianto = fixtureGE,
                    frequenza = Periodo(TipoPeriodo.A, 1),
                    outputDir = testOutputDir,
                    copies = 2
                )
                assertEquals(2, syncResult.generatedFiles.size)
                assertEquals(2, syncResult.totalRequested)
                assertTrue(syncResult.isFullSuccess)

                // Asincrono
                val asyncResult = strategy.generateBatchAsync(
                    impianto = fixtureGE,
                    frequenza = Periodo(TipoPeriodo.M, 6),
                    outputDir = testOutputDir,
                    copies = 1
                )
                assertEquals(1, asyncResult.generatedFiles.size)
                assertTrue(asyncResult.isFullSuccess)
            } finally {
                testOutputDir.deleteRecursively()
            }
        }
    }
}

