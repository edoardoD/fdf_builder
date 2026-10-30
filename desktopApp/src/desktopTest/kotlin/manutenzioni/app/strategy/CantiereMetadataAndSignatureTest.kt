package manutenzioni.app.strategy

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.service.HtmlService
import manutenzioni.app.ui.ManutenzioniViewModel
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.ImpiantoStandard
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.TipoPeriodo
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test End-to-End per Branch 7 (`feat/cantiere-metadata-and-signature`):
 * - R24: Data Inizio Attività precompilata nel campo AcroForm `starting_date` (e `diff_data_inizio` su Foglio 2)
 * - R25: Verificatore / Manutentore e Sito stampati nel cartiglio del PDF
 * - R26: Firma Digitale Tecnico (Data URI o File locale) iniettata nel riquadro Firma del PDF
 */
class CantiereMetadataAndSignatureTest {

    private val testDbFileName = "test_wave3_branch7_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File
    private val htmlService = HtmlService()

    // 1x1 pixel PNG valido in Base64
    private val samplePngBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
    private val sampleDataUri = "data:image/png;base64,$samplePngBase64"

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) testDbFile.delete()
        repo = JsonManutenzioneRepository(testDbFileName)
    }

    @AfterTest
    fun cleanup() {
        if (testDbFile.exists()) testDbFile.delete()
    }

    private suspend fun waitUntil(timeoutMs: Long = 5000, condition: () -> Boolean) {
        var elapsed = 0L
        while (!condition() && elapsed < timeoutMs) {
            delay(50)
            elapsed += 50
        }
    }

    @Test
    fun `HtmlService inietta Sito starting_date Manutentore e Firma da Data URI o file locale`() {
        val tempPng = File.createTempFile("firma_tecnico_", ".png")
        tempPng.writeBytes(Base64.getDecoder().decode(samplePngBase64))

        try {
            val quadro = QuadroBT(
                id = "q-test",
                codIntervento = "Q",
                nomeCompleto = "Quadro elettrico",
                sigla = "QG",
                descrizioneQuadro = "Quadro Generale",
                listaAttivita = listOf(
                    Attivita(1, "Prova", "Prova differenziali", Periodo(TipoPeriodo.M, 6), zona = "Quadro")
                ),
                listaInterruttori = listOf(
                    InterruttoreBT(
                        id = "int-1",
                        nome = "Diff Generale",
                        produttore = "ABB",
                        codiceArticolo = "DDA204",
                        siglaCircuito = "QF1",
                        caratteristicheTecniche = mapOf("taratura" to "0,03A-A")
                    )
                )
            )

            val html = htmlService.buildHtml(
                impianto = quadro,
                attivitaFiltrate = quadro.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = "Ospedali Privati",
                cantiereNome = "Villa Igea",
                dataInizioIntervento = "16/10/2026",
                nomeManutentore = "Ing. Edoardo Desiderio",
                firmaManutentore = tempPng.absolutePath
            )

            // R24: starting_date precompilato nel Foglio 1 e nel Foglio 2
            assertContains(html, """name="starting_date" id="starting-date" value="16/10/2026"""")
            assertContains(html, """name="diff_data_inizio" id="diff_data_inizio" value="16/10/2026"""")

            // R25: Sito e Manutentore nel Foglio 1 e Foglio 2
            assertContains(html, "<p>Sito: Villa Igea</p>")
            assertContains(html, "Ing. Edoardo Desiderio")
            assertContains(html, """name="diff_manutentore" id="diff_manutentore" value="Ing. Edoardo Desiderio"""")

            // R26: Firma convertita in <img> data:image/png;base64,...
            assertContains(html, """<img src="data:image/png;base64,""")
            assertContains(html, """alt="Firma Tecnico"""")
        } finally {
            tempPng.delete()
        }
    }

    @Test
    fun `pipeline End-to-End ViewModel genera PDF con AcroForm starting_date e cartiglio compilati`() = runBlocking {
        val viewModel = ManutenzioniViewModel(repository = repo, pdfStrategy = HtmlToPdfStrategy())

        val cliente = Cliente(id = "cli-e2e", nome = "Cavarei Soc. Coop.")
        val cantiere = Cantiere(
            id = "can-e2e",
            nome = "Polo Logistico Forlì",
            clienteId = cliente.id,
            dataInizioIntervento = "12/10/2026",
            nomeManutentore = "Mario Rossi",
            firmaManutentore = sampleDataUri
        )
        val impianto = ImpiantoStandard(
            id = "imp-pem-e2e",
            codIntervento = "PEM",
            nomeCompleto = "Sgancio generale emergenza",
            cantiereId = cantiere.id,
            listaAttivita = listOf(
                Attivita(
                    nAttivita = 1,
                    tipoAttivita = "Controllo",
                    zona = "PEM",
                    descrizione = "Controllo generale involucro e vetro",
                    frequenza = Periodo(TipoPeriodo.M, 6)
                )
            )
        )

        repo.salvaCliente(cliente)
        repo.salvaCantiere(cantiere)
        repo.salvaImpianto(impianto)

        viewModel.selectCliente(cliente)
        viewModel.selectCantiere(cantiere)
        waitUntil { viewModel.uiState.value.impiantiDelCantiere.isNotEmpty() }

        // Verifica anche updateDatiInterventoCantiere e updateFirmaManutentoreCantiere
        viewModel.updateDatiInterventoCantiere("15/10/2026", "Luigi Bianchi")
        viewModel.updateFirmaManutentoreCantiere(sampleDataUri)
        viewModel.toggleImpiantoSelection(impianto.id, true)
        viewModel.updateFrequenzaForImpianto(impianto.id, Periodo(TipoPeriodo.M, 6))

        val tempOutDir = Files.createTempDirectory("wave3_branch7_pdf_").toFile()
        try {
            viewModel.generatePdfToDirectory(tempOutDir)
            waitUntil { !viewModel.uiState.value.isLoading && viewModel.uiState.value.generatedFiles.isNotEmpty() }

            val pdfFile = viewModel.uiState.value.generatedFiles.firstOrNull()
            assertNotNull(pdfFile, "Il file PDF deve essere stato generato")
            assertTrue(pdfFile.exists() && pdfFile.length() > 0)

            PdfReader(pdfFile).use { reader ->
                PdfDocument(reader).use { pdfDoc ->
                    val acroForm = PdfAcroForm.getAcroForm(pdfDoc, false)
                    assertNotNull(acroForm, "Il PDF deve contenere un AcroForm")

                    val startingDateField = acroForm.getField("starting_date")
                    assertNotNull(startingDateField, "Il campo AcroForm starting_date deve esistere")
                    assertEquals("15/10/2026", startingDateField.valueAsString, "Il campo starting_date deve essere precompilato con dataInizioIntervento")

                    val pageText = PdfTextExtractor.getTextFromPage(pdfDoc.getPage(1))
                    val normalizedPageText = pageText.replace(Regex("\\s+"), " ")
                    assertContains(normalizedPageText, "Polo Logistico Forlì")
                    assertContains(normalizedPageText, "Luigi Bianchi")
                    assertContains(normalizedPageText, "Cavarei Soc. Coop.")
                }
            }
        } finally {
            tempOutDir.deleteRecursively()
        }
    }
}
