package manutenzioni.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.domain.model.*
import java.io.File
import kotlin.test.*

class EmergenzaViewModelTest {

    private val testDbFileName = "test_emerg_vm_db.json"
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

    private suspend fun waitUntil(timeoutMs: Long = 3000, condition: () -> Boolean) {
        var elapsed = 0L
        while (!condition() && elapsed < timeoutMs) {
            delay(50)
            elapsed += 50
        }
    }

    @Test
    fun `updateEmergenzaSiglaDocumentale commuta tra EM e IS aggiornando stato e repository`() = runBlocking {
        val cliente = Cliente(id = "cli-emerg-1", nome = "Cliente Ospedale")
        val cantiere = Cantiere(id = "can-emerg-1", nome = "Presidio Sanitario", clienteId = cliente.id)
        repo.salvaCliente(cliente)
        repo.salvaCantiere(cantiere)

        val lampade = listOf(
            LampadaEmergenza(sigla = "L-01", modello = "Formula 65", autonomia = "1h", posizione = "Ingresso")
        )
        val emerg = ImpiantoEmergenza(
            id = "plant-em-1",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza",
            cantiereId = cantiere.id,
            listaLampade = lampade
        )
        repo.salvaImpianto(emerg)

        viewModel.selectCliente(cliente)
        viewModel.selectCantiere(cantiere)
        waitUntil { viewModel.uiState.value.selectedCantiere?.id == cantiere.id }
        waitUntil { viewModel.uiState.value.impiantiDelCantiere.any { it.id == emerg.id } }

        val plantBefore = viewModel.uiState.value.impiantiDelCantiere.first { it.id == emerg.id } as ImpiantoEmergenza
        assertEquals(EmergenzaSiglaDoc.EM, plantBefore.siglaDocumentale)
        assertEquals("EM", plantBefore.codIntervento)

        // Commutazione a IS
        viewModel.updateEmergenzaSiglaDocumentale(emerg.id, EmergenzaSiglaDoc.IS)
        waitUntil {
            val p = viewModel.uiState.value.impiantiDelCantiere.find { it.id == emerg.id } as? ImpiantoEmergenza
            p?.codIntervento == "IS"
        }

        val plantIS = viewModel.uiState.value.impiantiDelCantiere.first { it.id == emerg.id } as ImpiantoEmergenza
        assertEquals("IS", plantIS.codIntervento)
        assertEquals("Illuminazione di sicurezza", plantIS.nomeCompleto)
        assertEquals(EmergenzaSiglaDoc.IS, plantIS.siglaDocumentale)
        assertEquals(1, plantIS.listaLampade.size)
        assertEquals("L-01", plantIS.listaLampade[0].sigla)

        // Verifica persistenza nel repository
        val persistedIS = repo.getImpiantiForCantiere(cantiere.id).first { it.id == emerg.id } as ImpiantoEmergenza
        assertEquals("IS", persistedIS.codIntervento)
        assertEquals("Illuminazione di sicurezza", persistedIS.nomeCompleto)
        assertEquals(1, persistedIS.listaLampade.size)

        // Commutazione di ritorno a EM
        viewModel.updateEmergenzaSiglaDocumentale(emerg.id, EmergenzaSiglaDoc.EM)
        waitUntil {
            val p = viewModel.uiState.value.impiantiDelCantiere.find { it.id == emerg.id } as? ImpiantoEmergenza
            p?.codIntervento == "EM"
        }

        val plantEM = viewModel.uiState.value.impiantiDelCantiere.first { it.id == emerg.id } as ImpiantoEmergenza
        assertEquals("EM", plantEM.codIntervento)
        assertEquals("Illuminazione di emergenza", plantEM.nomeCompleto)
        assertEquals(EmergenzaSiglaDoc.EM, plantEM.siglaDocumentale)
        assertEquals(1, plantEM.listaLampade.size)
    }
}
