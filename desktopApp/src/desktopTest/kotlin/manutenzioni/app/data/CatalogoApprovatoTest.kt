package manutenzioni.app.data

import kotlinx.coroutines.runBlocking
import manutenzioni.domain.model.ComponenteApprovato
import manutenzioni.domain.model.VarianteProdotto
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CatalogoApprovatoTest {

    private val testDbFileName = "test_catalogo_approvato_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
        repo = JsonManutenzioneRepository(testDbFileName)
    }

    @AfterTest
    fun cleanup() {
        if (testDbFile.exists()) {
            testDbFile.delete()
        }
    }

    @Test
    fun `salvataggio e merge di produttori multipli per la stessa specifica ETIM`() = runBlocking {
        // 1. L'utente approva la variante BTicino per EC000042 16A 1P+N
        val approvatoBTicino = ComponenteApprovato(
            id = "comp-1",
            etimClassId = "EC000042",
            etimClassName = "Interruttore magnetotermico",
            descrizioneStandard = "Interruttore magnetotermico 1P+N 16A Curva C",
            caratteristicheTecniche = mapOf("Poli" to "1P+N", "In" to "16A", "Curva" to "C"),
            variantiProduttore = mapOf(
                "BTicino" to VarianteProdotto(produttore = "BTicino", codice = "GC8813AC16", dataApprovazione = "2026-09-15")
            ),
            dataCreazione = "2026-09-15"
        )
        repo.salvaOAggiornaComponenteApprovato(approvatoBTicino)

        val catalogo1 = repo.caricaCatalogoApprovato()
        assertEquals(1, catalogo1.size)
        assertEquals(1, catalogo1.first().variantiProduttore.size)
        assertTrue(catalogo1.first().variantiProduttore.containsKey("BTicino"))

        // 2. Successivamente l'utente approva la variante ABB per la STESSA specifica tecnica ETIM
        val approvatoABB = ComponenteApprovato(
            id = "comp-2",
            etimClassId = "EC000042",
            etimClassName = "Interruttore magnetotermico",
            descrizioneStandard = "Interruttore magnetotermico 1P+N 16A Curva C",
            caratteristicheTecniche = mapOf("Poli" to "1P+N", "In" to "16A", "Curva" to "C"),
            variantiProduttore = mapOf(
                "ABB" to VarianteProdotto(produttore = "ABB", codice = "SN201LC16", dataApprovazione = "2026-09-16")
            ),
            dataCreazione = "2026-09-16"
        )
        repo.salvaOAggiornaComponenteApprovato(approvatoABB)

        // 3. Verifica che il catalogo non sia duplicato ma abbia MERGIATO le due varianti!
        val catalogo2 = repo.caricaCatalogoApprovato()
        assertEquals(1, catalogo2.size, "Non devono esserci duplicati: la specifica è identica")
        val compMergiato = catalogo2.first()
        assertEquals(2, compMergiato.variantiProduttore.size, "Deve contenere sia BTicino che ABB")
        assertEquals("GC8813AC16", compMergiato.variantiProduttore["BTicino"]?.codice)
        assertEquals("SN201LC16", compMergiato.variantiProduttore["ABB"]?.codice)

        // 4. Test ricerca equivalenze
        val equivalenti = repo.trovaEquivalentiApprovati("EC000042")
        assertEquals(1, equivalenti.size)
        assertEquals(2, equivalenti.first().variantiProduttore.size)
    }
}
