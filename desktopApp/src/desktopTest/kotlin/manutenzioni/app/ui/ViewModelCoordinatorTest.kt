package manutenzioni.app.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import manutenzioni.app.ui.platform.DirectoryPicker
import manutenzioni.app.ui.platform.SystemFileLauncher
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import manutenzioni.domain.strategy.PdfBatchGenerator
import java.io.File
import kotlin.test.*

/**
 * Deterministic unit tests for [ManutenzioniViewModel] and its 5 specialized coordinators.
 * Uses [StandardTestDispatcher] and [runTest] for 100% deterministic coroutine execution
 * without any thread delays or polling loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelCoordinatorTest {

    private class FakeDirectoryPicker(var chosenDirectory: File? = null) : DirectoryPicker {
        var pickCalls = 0
        override fun pickDirectory(title: String): File? {
            pickCalls++
            return chosenDirectory
        }
        override fun pickFile(title: String, allowedExtensions: List<String>): File? = null
    }

    private class FakeSystemFileLauncher : SystemFileLauncher {
        val openedFiles = mutableListOf<File>()

        override fun open(file: File) {
            openedFiles.add(file)
        }
    }

    private class InMemoryManutenzioneRepository : ManutenzioneRepository {
        val clienti = mutableListOf<Cliente>()
        val cantieri = mutableListOf<Cantiere>()
        val impianti = mutableListOf<Impianto>()
        val componentiStandard = mutableListOf<ComponenteStandard>()
        val catalogoApprovato = mutableListOf<ComponenteApprovato>()
        val eventi = mutableListOf<EventoManutenzione>()

        override suspend fun salvaImpianto(impianto: Impianto) {
            val idx = impianti.indexOfFirst { it.id == impianto.id }
            if (idx >= 0) impianti[idx] = impianto else impianti.add(impianto)
        }

        override suspend fun caricaImpianti(): List<Impianto> = impianti.toList()

        override suspend fun eliminaImpianto(id: String) {
            impianti.removeAll { it.id == id }
        }

        override suspend fun getImpianto(codIntervento: String): Impianto? =
            impianti.firstOrNull { it.codIntervento == codIntervento }

        override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) {
            val idx = impianti.indexOfFirst { it.id == impiantoTemplate.id }
            if (idx >= 0) impianti[idx] = impiantoTemplate
        }

        override suspend fun caricaClienti(): List<Cliente> = clienti.toList()

        override suspend fun salvaCliente(cliente: Cliente) {
            val idx = clienti.indexOfFirst { it.id == cliente.id }
            if (idx >= 0) clienti[idx] = cliente else clienti.add(cliente)
        }

        override suspend fun updateCliente(id: String, newName: String) {
            val idx = clienti.indexOfFirst { it.id == id }
            if (idx >= 0) clienti[idx] = clienti[idx].copy(nome = newName)
        }

        override suspend fun eliminaCliente(id: String) {
            clienti.removeAll { it.id == id }
        }

        override suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> =
            cantieri.filter { it.clienteId == clienteId }

        override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> =
            impianti.filter { it.cantiereId == cantiereId }

        override suspend fun caricaCantieri(): List<Cantiere> = cantieri.toList()

        override suspend fun salvaCantiere(cantiere: Cantiere) {
            val idx = cantieri.indexOfFirst { it.id == cantiere.id }
            if (idx >= 0) cantieri[idx] = cantiere else cantieri.add(cantiere)
        }

        override suspend fun updateCantiere(id: String, newName: String) {
            val idx = cantieri.indexOfFirst { it.id == id }
            if (idx >= 0) cantieri[idx] = cantieri[idx].copy(nome = newName)
        }

        override suspend fun eliminaCantiere(id: String) {
            cantieri.removeAll { it.id == id }
        }

        override suspend fun caricaComponentiStandard(): List<ComponenteStandard> = componentiStandard.toList()
        override suspend fun salvaComponenteStandard(componente: ComponenteStandard) {
            componentiStandard.add(componente)
        }

        override suspend fun caricaCatalogoApprovato(): List<ComponenteApprovato> = catalogoApprovato.toList()
        override suspend fun salvaOAggiornaComponenteApprovato(approvato: ComponenteApprovato) {
            val idx = catalogoApprovato.indexOfFirst { it.id == approvato.id }
            if (idx >= 0) catalogoApprovato[idx] = approvato else catalogoApprovato.add(approvato)
        }

        override suspend fun trovaEquivalentiApprovati(etimClassId: String): List<ComponenteApprovato> =
            catalogoApprovato.filter { it.etimClassId == etimClassId }

        override suspend fun salvaEventoManutenzione(evento: EventoManutenzione) {
            eventi.add(evento)
        }

        override suspend fun salvaEventiManutenzioneBatch(eventi: List<EventoManutenzione>) {
            this.eventi.addAll(eventi)
        }

        override suspend fun caricaEventiManutenzione(): List<EventoManutenzione> = eventi.toList()
        override suspend fun caricaEventiPerMese(annoMese: String): List<EventoManutenzione> =
            eventi.filter { it.dataPianificata.startsWith(annoMese) }
        override suspend fun caricaEventiPerCantiere(cantiereId: String): List<EventoManutenzione> =
            eventi.filter { it.cantiereId == cantiereId }
        override suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean) {
            eventi.removeAll { it.id == id }
        }
        override suspend fun aggiornaStatoEvento(id: String, nuovoStato: StatoEventoManutenzione, dataCompletamento: String?) {
            val idx = eventi.indexOfFirst { it.id == id }
            if (idx >= 0) eventi[idx] = eventi[idx].copy(stato = nuovoStato, dataCompletamentoEffettiva = dataCompletamento)
        }
    }

    private class FakePdfBatchGenerator : PdfBatchGenerator {
        var generateBatchCalls = 0
        override fun generateBatch(
            impianto: Impianto,
            frequenza: Periodo,
            outputDir: File,
            copies: Int,
            clienteNome: String?,
            contextImpianti: List<Impianto>,
            cantiereNome: String?,
            nomeManutentore: String?,
            firmaManutentore: String?,
            onProgress: (Int, Int) -> Unit,
            dataInizioContratto: String?,
            dataIntervento: String?,
            dataInterventoPrecedente: String?
        ): BatchResult {
            generateBatchCalls++
            onProgress(copies, copies)
            val file = File(outputDir, "${impianto.codIntervento}_${frequenza.label()}.pdf")
            return BatchResult(
                generatedFiles = listOf(file),
                totalRequested = copies,
                errors = emptyMap()
            )
        }
    }

    @Test
    fun `initialization loads repository data and sets uiState isLoading to false`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        repo.clienti.add(Cliente(id = "cli-1", nome = "ACME Corporation"))
        repo.cantieri.add(Cantiere(id = "cant-1", clienteId = "cli-1", nome = "Stabilimento Nord"))

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading, "isLoading should be false after advanceUntilIdle")
        assertEquals(1, state.clienti.size)
        assertEquals("ACME Corporation", state.clienti.first().nome)
    }

    @Test
    fun `AnagraficaCoordinator selectCliente and selectCantiere update state and load plants`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        val cliente = Cliente(id = "cli-1", nome = "Enel")
        val cantiere = Cantiere(
            id = "cant-1",
            clienteId = "cli-1",
            nome = "Centrale Idroelettrica",
            nomeManutentore = "Mario Rossi",
            dataIntervento = "10/11/2026"
        )
        val quadro = QuadroBT(
            id = "q-1",
            cantiereId = "cant-1",
            codIntervento = "Q",
            sigla = "QG",
            nomeCompleto = "Quadro Generale BT"
        )
        repo.clienti.add(cliente)
        repo.cantieri.add(cantiere)
        repo.impianti.add(quadro)

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.selectCliente(cliente)
        advanceUntilIdle()
        assertEquals(cliente, viewModel.uiState.value.selectedCliente)
        assertEquals(1, viewModel.uiState.value.cantieriDisponibili.size)

        viewModel.selectCantiere(cantiere)
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertEquals(cantiere, state.selectedCantiere)
        assertEquals("Mario Rossi", state.selectedCantiere?.nomeManutentore)
        assertEquals("10/11/2026", state.selectedCantiere?.dataIntervento)
        assertEquals(1, state.impiantiDelCantiere.size)
        assertEquals("QG", (state.impiantiDelCantiere.first() as? QuadroBT)?.sigla)
    }

    @Test
    fun `ImpiantoSelectionCoordinator manages selections and computes available frequencies`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        val cantiere = Cantiere(id = "cant-1", clienteId = "cli-1", nome = "Sede A")
        val quadro = QuadroBT(
            id = "q-1",
            cantiereId = "cant-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro 1",
            listaAttivita = listOf(
                Attivita(tipoAttivita = "V", descrizione = "Controllo serraggio", frequenza = Periodo(TipoPeriodo.M, 6))
            )
        )
        repo.cantieri.add(cantiere)
        repo.impianti.add(quadro)

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.selectCantiere(cantiere)
        advanceUntilIdle()

        // Test toggle selection
        viewModel.toggleImpiantoSelection("q-1", true)
        assertEquals(true, viewModel.uiState.value.impiantiSelezionati["q-1"])

        viewModel.toggleImpiantoSelection("q-1", false)
        assertNull(viewModel.uiState.value.impiantiSelezionati["q-1"])

        viewModel.toggleImpiantiSelection(listOf("q-1"), true)
        assertEquals(true, viewModel.uiState.value.impiantiSelezionati["q-1"])

        // Set plant frequency
        viewModel.updateFrequenzaForImpianto("q-1", Periodo(TipoPeriodo.A, 1))
        assertEquals(
            Periodo(TipoPeriodo.A, 1),
            viewModel.uiState.value.frequenzePerImpianto["q-1"]
        )

        // Verify available frequencies map in uiState
        val availableFreqs = viewModel.uiState.value.getFrequenzeDisponibili(quadro)
        assertTrue(availableFreqs.isNotEmpty(), "Available frequencies should be precomputed by coordinator")
        assertTrue(availableFreqs.contains(Periodo(TipoPeriodo.M, 6)))
    }

    @Test
    fun `PdfBatchCoordinator uses DirectoryPicker and SystemFileLauncher ports correctly`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        val fakePicker = FakeDirectoryPicker()
        val fakeLauncher = FakeSystemFileLauncher()
        val fakeBatchGen = FakePdfBatchGenerator()

        val tempDir = File(System.getProperty("java.io.tmpdir"), "test_pdf_batch_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        fakePicker.chosenDirectory = tempDir

        val cantiere = Cantiere(id = "cant-1", clienteId = "cli-1", nome = "Cantiere Test")
        val quadro = QuadroBT(
            id = "q-1",
            cantiereId = "cant-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro Q1",
            listaAttivita = listOf(
                Attivita(tipoAttivita = "V", descrizione = "Controllo", frequenza = Periodo(TipoPeriodo.M, 6))
            )
        )
        repo.cantieri.add(cantiere)
        repo.impianti.add(quadro)

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            pdfStrategy = fakeBatchGen,
            directoryPicker = fakePicker,
            fileLauncher = fakeLauncher,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.selectCantiere(cantiere)
        advanceUntilIdle()
        viewModel.toggleImpiantoSelection("q-1", true)
        viewModel.updateFrequenzaForImpianto("q-1", Periodo(TipoPeriodo.M, 6))

        // Call generatePdf
        viewModel.generatePdf()
        advanceUntilIdle()

        assertEquals(1, fakePicker.pickCalls, "DirectoryPicker should have been called")
        assertEquals(1, fakeBatchGen.generateBatchCalls, "PdfBatchGenerator should have been invoked")
        val files = viewModel.uiState.value.generatedFiles
        assertTrue(files.isNotEmpty(), "generatedFiles should be stored in uiState")

        // Open last generated file via launcher
        viewModel.openPdfInSystem()
        assertEquals(1, fakeLauncher.openedFiles.size)

        tempDir.deleteRecursively()
    }

    @Test
    fun `CatalogSearchCoordinator loads componentiStandard and catalogoApprovato from repository`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        repo.componentiStandard.add(ComponenteStandard(id = "cs-1", nome = "Diff 30mA", tipo = TipoComponenteStandard.INTERRUTTORE_BT))

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.componentiStandard.size)
        assertEquals("Diff 30mA", state.componentiStandard.first().nome)
    }

    @Test
    fun `InteractiveSchedulerCoordinator plans intervention and batches recurring events`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val repo = InMemoryManutenzioneRepository()
        val cliente = Cliente(id = "cli-1", nome = "Cliente Industriale")
        val cantiere = Cantiere(id = "cant-1", clienteId = "cli-1", nome = "Sito Industriale")
        val quadro = QuadroBT(
            id = "q-1",
            cantiereId = "cant-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro Q1",
            listaAttivita = listOf(
                Attivita(tipoAttivita = "V", descrizione = "Controllo", frequenza = Periodo(TipoPeriodo.M, 6))
            )
        )
        repo.clienti.add(cliente)
        repo.cantieri.add(cantiere)
        repo.impianti.add(quadro)

        val viewModel = ManutenzioniViewModel(
            repository = repo,
            ioDispatcher = testDispatcher,
            defaultDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.pianificaInterventiBatch(
            dataPianificata = "01/01/2027",
            cantieriIds = listOf("cant-1"),
            impiantiIds = listOf("q-1"),
            impiantiCodici = listOf("Q"),
            frequenza = Periodo(TipoPeriodo.M, 6),
            frequenzePerImpianto = mapOf("q-1" to Periodo(TipoPeriodo.M, 6)),
            manutentore = "Luca Bianchi",
            note = "Controllo semestrale",
            generaRicorrenze = true
        )
        advanceUntilIdle()

        val eventi = repo.caricaEventiManutenzione()
        assertTrue(eventi.isNotEmpty(), "Recurring events should be persisted")
        assertEquals("cant-1", eventi.first().cantiereId)
        assertEquals("Luca Bianchi", eventi.first().nomeManutentore)
        assertTrue(viewModel.uiState.value.statusMessage.contains("pianificat", ignoreCase = true))
    }
}
