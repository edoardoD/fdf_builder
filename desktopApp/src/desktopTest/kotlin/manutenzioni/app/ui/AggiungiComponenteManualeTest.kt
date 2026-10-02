package manutenzioni.app.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.QuadroBT
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggiungiComponenteManualeTest {

    private val testDbFileName = "test_manual_interruttori_db.json"
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
    fun `aggiunta manuale di un interruttore aggiorna il quadro e il catalogo approvato`() = runBlocking {
        // 1. Prepara un QuadroBT
        val quadro = QuadroBT(
            id = "quadro-test-1",
            codIntervento = "QBT-01",
            nomeCompleto = "Quadro Bassa Tensione Generale",
            sigla = "QG"
        )
        repo.salvaImpianto(quadro)

        // 2. Aggiunge un interruttore manuale
        val interruttore = InterruttoreBT(
            nome = "Magnetotermico 1P+N C16 4.5kA",
            quantita = 3,
            siglaCircuito = "F1 - Prese",
            produttore = "BTicino",
            codiceArticolo = "GC8813AC16",
            note = "Btdin45"
        )

        viewModel.aggiungiInterruttoreAQuadro(quadro, interruttore)

        // Attendi che la coroutine asincrona nel ViewModel completi
        var attempts = 0
        while (attempts < 20 && viewModel.uiState.value.isLoading) {
            delay(100)
            attempts++
        }
        delay(200)

        // 3. Verifica persistenza sul repository
        val impianti = repo.caricaImpianti()
        val quadroSalvato = impianti.filterIsInstance<QuadroBT>().firstOrNull { it.id == quadro.id }
        assertTrue(quadroSalvato != null, "Il quadro deve esistere nel repository")
        assertEquals(1, quadroSalvato.listaInterruttori.size)
        val salvato = quadroSalvato.listaInterruttori.first()
        assertEquals("Magnetotermico 1P+N C16 4.5kA", salvato.nome)
        assertEquals(3, salvato.quantita)
        assertEquals("F1 - Prese", salvato.siglaCircuito)
        assertEquals("BTicino", salvato.produttore)
        assertEquals("GC8813AC16", salvato.codiceArticolo)

        // 4. Verifica arricchimento catalogo approvato (knowledge base)
        val catalogo = repo.caricaCatalogoApprovato()
        assertTrue(catalogo.isNotEmpty(), "Il catalogo approvato deve contenere il nuovo componente")
        val compApprovato = catalogo.firstOrNull { it.descrizioneStandard == salvato.nome }
        assertTrue(compApprovato != null, "Il componente deve essere presente nel catalogo approvato")
        assertTrue(compApprovato.variantiProduttore.containsKey("BTicino"))
        assertEquals("GC8813AC16", compApprovato.variantiProduttore["BTicino"]?.codice)
    }
}
