package manutenzioni.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.ImpiantoStandard
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RuoloUtente
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.model.Utente
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test per Branch 10: `feat/storicizzazione-and-rbac` (Wave 4 — R25, R30, R31).
 */
class Wave4StoricizzazioneERbacTest {

    private val testDbFileName = "test_wave4_rbac_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File
    private lateinit var viewModel: ManutenzioniViewModel

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) testDbFile.delete()
        repo = JsonManutenzioneRepository(testDbFileName)
        viewModel = ManutenzioniViewModel(repository = repo)
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
    fun `storicizzazione immutabile SessioneVerifica preserva il manutentore nei cicli successivi (R25, R30)`(): Unit = runBlocking {
        val cliente = Cliente(id = "cli-hist", nome = "Ospedale Maggiore")
        val cantiere = Cantiere(
            id = "cant-hist",
            nome = "Blocco Operatorio",
            clienteId = cliente.id,
            dataInizioContratto = "01/01/2026",
            dataIntervento = "15/04/2026",
            dataInterventoPrecedente = "01/01/2026",
            nomeManutentore = "Ing. Marco Verdi"
        )
        val impianto = ImpiantoStandard(
            id = "imp-hist-ge",
            codIntervento = "GE",
            nomeCompleto = "Gruppo Elettrogeno",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica livelli", Periodo(TipoPeriodo.M, 6), zona = "Motore")
            ),
            cantiereId = cantiere.id
        )
        repo.salvaCliente(cliente)
        repo.salvaCantiere(cantiere)
        repo.salvaImpianto(impianto)

        viewModel.selectCliente(cliente)
        viewModel.selectCantiere(cantiere)
        waitUntil { viewModel.uiState.value.impiantiDelCantiere.isNotEmpty() }

        viewModel.toggleImpiantoSelection(impianto.id, true)
        viewModel.updateFrequenzaForImpianto(impianto.id, Periodo(TipoPeriodo.M, 6))

        val tempOutDir = Files.createTempDirectory("wave4_batch_test_").toFile()
        try {
            // Ciclo 1 (Aprile 2026 — Ing. Marco Verdi)
            viewModel.generatePdfToDirectory(tempOutDir)
            waitUntil { !viewModel.uiState.value.isLoading && viewModel.uiState.value.sessioniVerificaCantiere.size == 1 }

            val sessione1 = viewModel.uiState.value.sessioniVerificaCantiere.first()
            assertEquals("2026_04", sessione1.meseAnno)
            assertEquals("Ing. Marco Verdi", sessione1.nomeManutentore)
            assertEquals("15/04/2026", sessione1.dataIntervento)
            assertTrue(sessione1.elencoFilePdf.isNotEmpty())

            // Ciclo 2 (Ottobre 2026 — cambio manutentore in "Per. Ind. Luca Bianchi")
            viewModel.updateDatiInterventoCantiere(
                dataInizioContratto = "01/01/2026",
                dataIntervento = "15/10/2026",
                dataInterventoPrecedente = "15/04/2026",
                manutentore = "Per. Ind. Luca Bianchi"
            )
            delay(100)

            viewModel.generatePdfToDirectory(tempOutDir)
            waitUntil { !viewModel.uiState.value.isLoading && viewModel.uiState.value.sessioniVerificaCantiere.size == 2 }

            val sessioniPersistite = repo.getSessioniPerCantiere(cantiere.id)
            assertEquals(2, sessioniPersistite.size)

            val storicaAprile = sessioniPersistite.first { it.meseAnno == "2026_04" }
            val storicaOttobre = sessioniPersistite.first { it.meseAnno == "2026_10" }

            // Verifica immutabilità: la sessione di Aprile conserva "Ing. Marco Verdi" anche dopo il cambio in Ottobre!
            assertEquals("Ing. Marco Verdi", storicaAprile.nomeManutentore)
            assertEquals("15/04/2026", storicaAprile.dataIntervento)
            assertEquals("Per. Ind. Luca Bianchi", storicaOttobre.nomeManutentore)
            assertEquals("15/10/2026", storicaOttobre.dataIntervento)
        } finally {
            tempOutDir.deleteRecursively()
        }
    }

    @Test
    fun `RBAC filtra cantieri per ruolo TECNICO e blocca accesso a sezione DATABASE (R31)`(): Unit = runBlocking {
        val cliente = Cliente(id = "cli-rbac", nome = "Gruppo Industriale")
        val cantiereA = Cantiere(id = "cant-a", nome = "Stabilimento A (Assegnato)", clienteId = cliente.id)
        val cantiereB = Cantiere(id = "cant-b", nome = "Stabilimento B (Non Assegnato)", clienteId = cliente.id)
        repo.salvaCliente(cliente)
        repo.salvaCantiere(cantiereA)
        repo.salvaCantiere(cantiereB)

        viewModel.selectCliente(cliente)
        waitUntil { viewModel.uiState.value.cantieriDisponibili.size == 2 }

        // 1. Con profilo ADMIN di default: entrambi i cantieri sono visibili e l'accesso a DATABASE è consentito
        assertTrue(viewModel.uiState.value.isAdmin)
        assertEquals(2, viewModel.uiState.value.cantieriVisibili.size)
        viewModel.setSection(AppSection.DATABASE)
        assertEquals(AppSection.DATABASE, viewModel.uiState.value.currentSection)

        // 2. Creazione e attivazione di un profilo TECNICO assegnato solo a cantiereA
        val tecnico = Utente(
            id = "usr-tec-1",
            username = "m.rossi",
            nomeCompleto = "Mario Rossi",
            ruolo = RuoloUtente.TECNICO,
            cantieriAssegnatiIds = setOf(cantiereA.id)
        )
        viewModel.saveUtente(tecnico)
        waitUntil { viewModel.uiState.value.utenti.any { it.id == tecnico.id } }

        // Selezioniamo prima cantiereB come Admin, poi passiamo a Tecnico: cantiereB viene deselezionato automaticamente
        viewModel.selectCantiere(cantiereB)
        assertEquals(cantiereB.id, viewModel.uiState.value.selectedCantiere?.id)

        viewModel.selectUtenteAttivo(tecnico)
        val stateTecnico = viewModel.uiState.value
        assertEquals(false, stateTecnico.isAdmin)
        assertEquals(AppSection.OPERATIVITA, stateTecnico.currentSection, "Il passaggio a TECNICO deve riportare a OPERATIVITA")
        assertNull(stateTecnico.selectedCantiere, "Un cantiere non assegnato al tecnico deve essere chiuso")
        assertEquals(listOf(cantiereA.id), stateTecnico.cantieriVisibili.map { it.id })

        // Tentativo di accesso a DATABASE da parte del TECNICO viene bloccato
        viewModel.setSection(AppSection.DATABASE)
        assertEquals(AppSection.OPERATIVITA, viewModel.uiState.value.currentSection)
        assertTrue(viewModel.uiState.value.errorMessage != null)
    }
}
