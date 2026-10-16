package manutenzioni.app.strategy

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import manutenzioni.app.data.ManutenzioniDatabase
import manutenzioni.app.service.HtmlService
import manutenzioni.app.service.attachments.QuadroBtAttachmentProvider
import manutenzioni.app.ui.ManutenzioniViewModel
import manutenzioni.app.ui.resolveTemporalSubfolderName
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Suite di test per la Wave 3 — Branch 8 (`feat/pdf-batch-multifreq-naming-layout`):
 * - R11: Tab Cantiere "Elenco interruttori" aggregato e stampa PDF
 * - R21: Multi-selezione delle periodicità in generazione PDF (es. 6M + 1A insieme)
 * - R22: Naming univoco dei file PDF `{SIGLA}_{PERIODICITA}.pdf` (es. `QG_06.pdf`, `Q1_06.pdf`, `QG_1.pdf`)
 * - R23: Layout PDF: griglia fissa a 30 righe, font input maggiorato e codice periodicità a 2 cifre nel cartiglio
 * - R29: Creazione automatica sottocartella temporale `YYYY_MM` (es. `2026_10/`)
 * - R32: Colonna "Zona" al posto di "Impianto" e popolamento dai CSV originali
 */
class Wave3BatchAndLayoutTest {

    private val htmlService = HtmlService()

    @Test
    fun `R22 e R23 - Periodo codiceCartiglio formatta Mesi a 2 cifre e Anni a 1 cifra`() {
        assertEquals("01", Periodo(TipoPeriodo.M, 1).codiceCartiglio())
        assertEquals("02", Periodo(TipoPeriodo.M, 2).codiceCartiglio())
        assertEquals("03", Periodo(TipoPeriodo.M, 3).codiceCartiglio())
        assertEquals("04", Periodo(TipoPeriodo.M, 4).codiceCartiglio())
        assertEquals("06", Periodo(TipoPeriodo.M, 6).codiceCartiglio())
        assertEquals("1", Periodo(TipoPeriodo.A, 1).codiceCartiglio())
        assertEquals("2", Periodo(TipoPeriodo.A, 2).codiceCartiglio())
        assertEquals("5", Periodo(TipoPeriodo.A, 5).codiceCartiglio())
    }

    @Test
    fun `R23 e R32 - HtmlService genera griglia fissa a 30 righe, header Zona e codiceCartiglio`() {
        val impianto = ImpiantoStandard(
            id = "imp-amb",
            codIntervento = "AMB",
            nomeCompleto = "Ambulatori",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica illuminazione", Periodo(TipoPeriodo.M, 6), zona = "Ambulatorio"),
                Attivita(2, "Prova", "Verifica cablaggi", Periodo(TipoPeriodo.M, 6), zona = null)
            )
        )

        val html = htmlService.buildHtml(
            impianto = impianto,
            attivitaFiltrate = impianto.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Clinica San Luca"
        )

        // R23: Periodicità nel cartiglio a 2 cifre ("06")
        assertContains(html, "<p>Periodicità:</p>\n                        <p>06</p>")

        // R32: Intestazione colonna "Zona" al posto di "Impianto"
        assertContains(html, "<p>Zona</p>")
        assertFalse(html.contains("<p>Impianto</p>"), "L'header 'Impianto' deve essere sostituito da 'Zona'")

        // R32: La colonna Zona mostra "Ambulatorio" per l'attività 1 e NON cade in fallback su "6 Mesi" per l'attività 2
        assertContains(html, "<td class=\"cell--data-empty\"><p>Ambulatorio</p></td>")
        assertFalse(html.contains("<td class=\"cell--data-empty\"><p>6 Mesi</p></td>"), "Non deve esserci fallback sulla label della frequenza nella colonna Zona")

        // R23: Griglia fissa a 30 righe (<tr class="row--data"> compare esattamente 30 volte)
        val dataRowCount = Regex("""<tr class="row--data">""").findAll(html).count()
        assertEquals(30, dataRowCount, "La scheda deve avere esattamente 30 righe nella griglia dati")

        // Solo le prime 2 righe hanno i radio button AcroForm; le righe 3..30 sono neutre
        assertContains(html, "name=\"esito_AMB_1\"")
        assertContains(html, "name=\"esito_AMB_2\"")
        assertFalse(html.contains("name=\"esito_AMB_3\""), "Le righe di riempimento 3..30 non devono avere campi AcroForm")
        assertContains(html, "<td class=\"cell--data-empty\"><p>30</p></td>")
    }

    @Test
    fun `R32 - manutenzioni_db json ha il campo zona popolato dai CSV per tutti i template con zona`() {
        val stream = this::class.java.classLoader?.getResourceAsStream("manutenzioni_db.json")
        assertNotNull(stream, "manutenzioni_db.json deve essere presente nelle resources")
        val content = stream.bufferedReader().use { it.readText() }
        val json = Json {
            ignoreUnknownKeys = true
            classDiscriminator = "type"
        }
        val db = json.decodeFromString(ManutenzioniDatabase.serializer(), content)

        // Nei CSV originali, tutti gli impianti tranne EM, IS, SPD hanno la colonna Zona valorizzata
        val codiciConZona = setOf("AMB", "BACS", "CAB", "DEG", "DS", "FTV", "GE", "IE", "PEM", "Q", "QMT", "RI", "RIF", "RIG", "TRFO", "TRFS", "UPS")
        for (cod in codiciConZona) {
            val imp = db.impianti.firstOrNull { it.codIntervento == cod }
            assertNotNull(imp, "Impianto master $cod deve esistere in manutenzioni_db.json")
            assertTrue(
                imp.listaAttivita.all { !it.zona.isNullOrBlank() },
                "Tutte le attività dell'impianto $cod devono avere il campo 'zona' valorizzato"
            )
        }
    }

    @Test
    fun `R29 - resolveTemporalSubfolderName estrae YYYY_MM da vari formati data`() {
        assertEquals("2026_10", resolveTemporalSubfolderName("16/10/2026"))
        assertEquals("2026_03", resolveTemporalSubfolderName("5/3/2026"))
        assertEquals("2025_12", resolveTemporalSubfolderName("2025-12-31"))
        assertEquals("2026_06", resolveTemporalSubfolderName("06/2026"))
    }

    @Test
    fun `R21, R22, R29 - Multi-selezione frequenze genera file distinti per sigla e periodicita in sottocartella YYYY_MM`() = runBlocking {
        val cliente = Cliente(id = "cli-w3", nome = "Polo Industriale Spa")
        val cantiere = Cantiere(
            id = "cant-w3",
            nome = "Stabilimento Nord",
            clienteId = cliente.id,
            dataInizioIntervento = "15/10/2026",
            nomeManutentore = "Ing. Giuseppe Verdi"
        )

        val attivitaQuadro = listOf(
            Attivita(1, "Controllo", "Esame a vista quadro", Periodo(TipoPeriodo.M, 6), zona = "Quadro"),
            Attivita(2, "Prova", "Prova strumentale differenziali", Periodo(TipoPeriodo.A, 1), zona = "Quadro")
        )

        val qg = QuadroBT(
            id = "q-qg",
            codIntervento = "Q",
            nomeCompleto = "Quadro elettrico",
            sigla = "QG",
            descrizioneQuadro = "Quadro Generale Cabina",
            cantiereId = cantiere.id,
            listaAttivita = attivitaQuadro
        )
        val q1 = QuadroBT(
            id = "q-q1",
            codIntervento = "Q",
            nomeCompleto = "Quadro elettrico",
            sigla = "Q1",
            descrizioneQuadro = "Quadro Reparto 1",
            cantiereId = cantiere.id,
            listaAttivita = attivitaQuadro
        )
        val q2 = QuadroBT(
            id = "q-q2",
            codIntervento = "Q",
            nomeCompleto = "Quadro elettrico",
            sigla = "Q2",
            descrizioneQuadro = "Quadro Reparto 2",
            cantiereId = cantiere.id,
            listaAttivita = attivitaQuadro
        )

        val repo = InMemoryW3Repo(
            clienti = mutableListOf(cliente),
            cantieri = mutableListOf(cantiere),
            impianti = mutableListOf(qg, q1, q2)
        )
        val viewModel = ManutenzioniViewModel(repository = repo, pdfStrategy = HtmlToPdfStrategy())
        delay(100)

        viewModel.selectCliente(cliente)
        delay(50)
        viewModel.selectCantiere(cantiere)
        delay(100)

        // Seleziona tutti e 3 i quadri (QG, Q1, Q2)
        viewModel.toggleImpiantoSelection(qg.id, true)
        viewModel.toggleImpiantoSelection(q1.id, true)
        viewModel.toggleImpiantoSelection(q2.id, true)

        // R21: Per QG selezioniamo SIA 6 Mesi ("06") SIA 1 Anno ("1")
        val freq6M = Periodo(TipoPeriodo.M, 6)
        val freq1A = Periodo(TipoPeriodo.A, 1)
        viewModel.toggleFrequenzaForImpianto(qg.id, freq6M)
        viewModel.toggleFrequenzaForImpianto(qg.id, freq1A)

        // Per Q1 e Q2 selezioniamo 6 Mesi ("06")
        viewModel.toggleFrequenzaForImpianto(q1.id, freq6M)
        viewModel.toggleFrequenzaForImpianto(q2.id, freq6M)

        assertEquals(setOf(freq6M, freq1A), viewModel.uiState.value.frequenzeSelezionatePerImpianto[qg.id])

        val tempRoot = Files.createTempDirectory("wave3_batch_test_").toFile()
        try {
            viewModel.generatePdfToDirectory(tempRoot)

            var attempts = 0
            while ((viewModel.uiState.value.isLoading || viewModel.uiState.value.generatedFiles.isEmpty()) && attempts < 60) {
                delay(100)
                attempts++
            }

            // R29: Verifica creazione sottocartella 2026_10
            val subfolder = File(tempRoot, "2026_10")
            assertTrue(subfolder.exists() && subfolder.isDirectory, "La sottocartella temporale 2026_10 deve essere creata")

            // R21 + R22: Devono essere stati generati 4 file distinti senza sovrascritture:
            // QG_06.pdf, QG_1.pdf, Q1_06.pdf, Q2_06.pdf
            val generatedNames = viewModel.uiState.value.generatedFiles.map { it.name }.toSet()
            assertEquals(
                setOf("QG_06.pdf", "QG_1.pdf", "Q1_06.pdf", "Q2_06.pdf"),
                generatedNames,
                "I 4 PDF devono avere nomi univoci basati su sigla quadro e codice periodicità"
            )
            viewModel.uiState.value.generatedFiles.forEach { file ->
                assertEquals(subfolder.absolutePath, file.parentFile.absolutePath)
                assertTrue(file.exists() && file.length() > 0)
            }
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun `R11 - Elenco Interruttori Cantiere aggrega tutti i Quadri BT e genera PDF AcroForm`() = runBlocking {
        val cliente = Cliente(id = "cli-r11", nome = "Ospedale Maggiore")
        val cantiere = Cantiere(
            id = "cant-r11",
            nome = "Padiglione Chirurgia",
            clienteId = cliente.id,
            dataInizioIntervento = "16/10/2026",
            nomeManutentore = "Marco Neri"
        )

        val qg = QuadroBT(
            id = "qg-r11",
            codIntervento = "Q",
            nomeCompleto = "Quadro Generale",
            sigla = "QG",
            cantiereId = cantiere.id,
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "int-1",
                    nome = "Diff Generale",
                    produttore = "ABB",
                    codiceArticolo = "DDA204",
                    siglaCircuito = "QF1",
                    quantita = 1,
                    caratteristicheTecniche = mapOf("taratura" to "0,3A")
                )
            )
        )
        val q1 = QuadroBT(
            id = "q1-r11",
            codIntervento = "Q",
            nomeCompleto = "Quadro Piano 1",
            sigla = "Q1",
            cantiereId = cantiere.id,
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "int-2",
                    nome = "Magnetotermico Diff",
                    produttore = "Schneider",
                    codiceArticolo = "iC60N",
                    siglaCircuito = "QF2",
                    quantita = 2,
                    caratteristicheTecniche = mapOf("Idn" to "0,03A")
                )
            )
        )

        val provider = QuadroBtAttachmentProvider()
        val sheetData = provider.buildCantiereSheetData(
            quadri = listOf(qg, q1),
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = cliente.nome,
            cantiereNome = cantiere.nome
        )
        assertNotNull(sheetData)
        // 1 riga da QG + 2 righe srotolate da Q1 = 3 righe totali
        assertEquals(3, sheetData.rows.size)

        val repo = InMemoryW3Repo(
            clienti = mutableListOf(cliente),
            cantieri = mutableListOf(cantiere),
            impianti = mutableListOf(qg, q1)
        )
        val viewModel = ManutenzioniViewModel(repository = repo, pdfStrategy = HtmlToPdfStrategy())
        delay(100)
        viewModel.selectCliente(cliente)
        delay(50)
        viewModel.selectCantiere(cantiere)
        delay(100)

        val tempPdf = File.createTempFile("test_elenco_interruttori_cantiere_", ".pdf")
        try {
            viewModel.exportCantiereInterruttoriPdf(tempPdf)
            var attempts = 0
            while (viewModel.uiState.value.pdfFile != tempPdf && attempts < 50) {
                delay(100)
                attempts++
            }
            assertTrue(tempPdf.exists() && tempPdf.length() > 0, "Il PDF dell'elenco interruttori di cantiere deve essere generato")

            PdfReader(tempPdf).use { reader ->
                PdfDocument(reader).use { pdfDoc ->
                    val acroForm = PdfAcroForm.getAcroForm(pdfDoc, false)
                    assertNotNull(acroForm)
                    val fields = acroForm.allFormFields
                    assertTrue(fields.containsKey("tasto_diff_Q_1"))
                    assertTrue(fields.containsKey("tasto_diff_Q_2"))
                    assertTrue(fields.containsKey("tasto_diff_Q_3"))
                }
            }
        } finally {
            tempPdf.delete()
        }
    }

    private class InMemoryW3Repo(
        private val clienti: MutableList<Cliente>,
        private val cantieri: MutableList<Cantiere>,
        private val impianti: MutableList<Impianto>
    ) : ManutenzioneRepository {
        override suspend fun salvaImpianto(impianto: Impianto) {
            val idx = impianti.indexOfFirst { it.id == impianto.id }
            if (idx >= 0) impianti[idx] = impianto else impianti.add(impianto)
        }
        override suspend fun caricaImpianti(): List<Impianto> = impianti.toList()
        override suspend fun eliminaImpianto(id: String) { impianti.removeAll { it.id == id } }
        override suspend fun getImpianto(codIntervento: String): Impianto? = impianti.find { it.codIntervento == codIntervento }
        override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) {}
        override suspend fun caricaClienti(): List<Cliente> = clienti.toList()
        override suspend fun salvaCliente(cliente: Cliente) {
            val idx = clienti.indexOfFirst { it.id == cliente.id }
            if (idx >= 0) clienti[idx] = cliente else clienti.add(cliente)
        }
        override suspend fun updateCliente(id: String, newName: String) {}
        override suspend fun eliminaCliente(id: String) {}
        override suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> = cantieri.filter { it.clienteId == clienteId }
        override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> = impianti.filter { it.cantiereId == cantiereId }
        override suspend fun salvaCantiere(cantiere: Cantiere) {
            val idx = cantieri.indexOfFirst { it.id == cantiere.id }
            if (idx >= 0) cantieri[idx] = cantiere else cantieri.add(cantiere)
        }
        override suspend fun updateCantiere(id: String, newName: String) {}
        override suspend fun eliminaCantiere(id: String) {}
        override suspend fun caricaComponentiStandard(): List<ComponenteStandard> = emptyList()
        override suspend fun salvaComponenteStandard(componente: ComponenteStandard) {}
        override suspend fun caricaCatalogoApprovato(): List<ComponenteApprovato> = emptyList()
        override suspend fun salvaOAggiornaComponenteApprovato(approvato: ComponenteApprovato) {}
        override suspend fun trovaEquivalentiApprovati(etimClassId: String): List<ComponenteApprovato> = emptyList()
    }
}
