package manutenzioni.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.ui.features.operativita.LampadaGeneratorHelper
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.LampadaEmergenza
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LampadaGeneratorTest {

    private val testDbFileName = "test_lampade_generator_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File
    private lateinit var viewModel: ManutenzioniViewModel

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
        repo = JsonManutenzioneRepository(testDbFileName)
        viewModel = ManutenzioniViewModel(repository = repo)
    }

    @AfterTest
    fun cleanup() {
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
    }

    @Test
    fun `generateSigle genera correttamente 10 sigle sequenziali da EM-01`() {
        val result = LampadaGeneratorHelper.generateSigle(
            baseSigla = "EM-01",
            defaultPrefix = "EM",
            quantita = 10
        )
        assertEquals(10, result.size)
        assertEquals("EM-01", result[0])
        assertEquals("EM-02", result[1])
        assertEquals("EM-05", result[4])
        assertEquals("EM-10", result[9])
    }

    @Test
    fun `generateSigle gestisce prefissi personalizzati e formattazione con zeri`() {
        val res1 = LampadaGeneratorHelper.generateSigle(baseSigla = "L-001", defaultPrefix = "EM", quantita = 3)
        assertEquals(listOf("L-001", "L-002", "L-003"), res1)

        val res2 = LampadaGeneratorHelper.generateSigle(baseSigla = "LAMP1", defaultPrefix = "EM", quantita = 2)
        assertEquals(listOf("LAMP1", "LAMP2"), res2)

        val res3 = LampadaGeneratorHelper.generateSigle(baseSigla = "EM", defaultPrefix = "EM", quantita = 3)
        assertEquals(listOf("EM-01", "EM-02", "EM-03"), res3)
    }

    @Test
    fun `calculateNextStartSigla calcola il numero successivo considerando il massimo presente`() {
        val lampade = listOf(
            LampadaEmergenza(sigla = "EM-01", modello = "Beghelli"),
            LampadaEmergenza(sigla = "EM-04", modello = "Beghelli")
        )
        val next = LampadaGeneratorHelper.calculateNextStartSigla(lampade, "EM")
        assertEquals("EM-05", next)
    }

    @Test
    fun `findNextSiglaForClone incrementa la sigla correttamente`() {
        val lampade = listOf(
            LampadaEmergenza(sigla = "EM-01", modello = "Beghelli"),
            LampadaEmergenza(sigla = "EM-02", modello = "Beghelli")
        )
        val next = LampadaGeneratorHelper.findNextSiglaForClone("EM-01", lampade, "EM")
        assertEquals("EM-03", next)
    }

    @Test
    fun `creazione massiva di 10 lampade Beghelli via ViewModel persiste correttamente nel repository`() = runBlocking {
        // 1. Salva impianto emergenza iniziale
        val impianto = ImpiantoEmergenza(
            id = "emerg-test-1",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza Stabilimento",
            listaLampade = emptyList()
        )
        repo.salvaImpianto(impianto)

        // 2. Genera 10 lampade Beghelli Formula 65
        val sigle = LampadaGeneratorHelper.generateSigle("EM-01", "EM", 10)
        val dieciLampade = sigle.map { sigla ->
            LampadaEmergenza(
                sigla = sigla,
                modello = "Formula 65 LED",
                produttore = "Beghelli",
                autonomia = "3h",
                posizione = "Piano 1 Corridoio"
            )
        }

        // 3. Invoca la nuova aggiunta massiva
        viewModel.aggiungiLampadeAEmergenza(impianto, dieciLampade)

        // Attendi completamento coroutine nel ViewModel
        var attempts = 0
        while (attempts < 20 && viewModel.uiState.value.isLoading) {
            delay(100)
            attempts++
        }
        delay(200)

        // 4. Verifica persistenza
        val impianti = repo.caricaImpianti()
        val emergSalvato = impianti.filterIsInstance<ImpiantoEmergenza>().firstOrNull { it.id == impianto.id }
        assertTrue(emergSalvato != null, "L'impianto deve esistere")
        assertEquals(10, emergSalvato.listaLampade.size, "Devono essere state salvate 10 lampade")
        assertEquals("EM-01", emergSalvato.listaLampade.first().sigla)
        assertEquals("EM-10", emergSalvato.listaLampade.last().sigla)
        assertTrue(emergSalvato.listaLampade.all { it.produttore == "Beghelli" })
        assertTrue(emergSalvato.listaLampade.all { it.modello == "Formula 65 LED" })
        assertTrue(emergSalvato.listaLampade.all { it.autonomia == "3h" })
    }

    @Test
    fun `modifica di una singola lampada aggiorna i campi e persiste nel repository`() = runBlocking {
        // 1. Salva impianto con 2 lampade
        val lampada1 = LampadaEmergenza(id = "lamp-1", sigla = "EM-01", modello = "bho", produttore = "Schneider", posizione = "piano terra")
        val lampada2 = LampadaEmergenza(id = "lamp-2", sigla = "EM-02", modello = "bho", produttore = "Schneider", posizione = "piano terra")
        val impianto = ImpiantoEmergenza(
            id = "emerg-modifica-test",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza Test",
            listaLampade = listOf(lampada1, lampada2)
        )
        repo.salvaImpianto(impianto)

        // 2. Modifica la prima lampada (es. da "bho" a "Exiway Light", posizione a "Uscita Nord")
        val lampadaModificata = lampada1.copy(
            modello = "Exiway Light 3h",
            posizione = "Piano Terra - Uscita Nord",
            autonomia = "3h",
            note = "Sostituita batteria"
        )
        viewModel.aggiornaLampadaInEmergenza(impianto, lampadaModificata)

        // Attendi coroutine nel ViewModel
        var attempts = 0
        while (attempts < 20 && viewModel.uiState.value.isLoading) {
            delay(100)
            attempts++
        }
        delay(200)

        // 3. Verifica persistenza
        val impianti = repo.caricaImpianti()
        val emergSalvato = impianti.filterIsInstance<ImpiantoEmergenza>().firstOrNull { it.id == impianto.id }
        assertTrue(emergSalvato != null)
        assertEquals(2, emergSalvato.listaLampade.size)
        val l1Salvata = emergSalvato.listaLampade.first { it.id == "lamp-1" }
        assertEquals("Exiway Light 3h", l1Salvata.modello)
        assertEquals("Piano Terra - Uscita Nord", l1Salvata.posizione)
        assertEquals("3h", l1Salvata.autonomia)
        assertEquals("Sostituita batteria", l1Salvata.note)
        // La lampada 2 deve rimanere inalterata
        val l2Salvata = emergSalvato.listaLampade.first { it.id == "lamp-2" }
        assertEquals("bho", l2Salvata.modello)
        assertEquals("piano terra", l2Salvata.posizione)
    }
}
