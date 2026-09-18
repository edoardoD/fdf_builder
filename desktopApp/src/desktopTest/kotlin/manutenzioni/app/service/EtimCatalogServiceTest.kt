package manutenzioni.app.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EtimCatalogServiceTest {

    private val service = EtimCatalogService()

    @Test
    fun `ricerca per descrizione tecnica magnetotermico identifica EC000042 e genera varianti`() = runBlocking {
        val results = service.search("magnetotermico 16a 1p+n curva c 4.5ka")
        assertTrue(results.isNotEmpty(), "I risultati non devono essere vuoti")

        val mcb = results.first()
        assertEquals("EC000042", mcb.etimClassId)
        assertEquals("16A", mcb.caratteristicheTecniche["Corrente nominale (In)"])
        assertEquals("1P+N", mcb.caratteristicheTecniche["Poli"])
        assertEquals("C", mcb.caratteristicheTecniche["Curva di intervento"])

        // Verifica varianti presenti per i brand principali
        val produttori = mcb.variantiDisponibili.map { it.produttore }
        assertTrue(produttori.contains("BTicino"), "Deve contenere variante BTicino")
        assertTrue(produttori.contains("ABB"), "Deve contenere variante ABB")
        assertTrue(produttori.contains("Schneider"), "Deve contenere variante Schneider")
        assertTrue(produttori.contains("Siemens"), "Deve contenere variante Siemens")
    }

    @Test
    fun `ricerca per codice articolo noto bticino GC8813AC16 ordina BTicino per primo`() = runBlocking {
        val results = service.search("GC8813AC16")
        assertTrue(results.isNotEmpty())

        val candidate = results.first()
        val primaVariante = candidate.variantiDisponibili.first()
        assertEquals("BTicino", primaVariante.produttore)
        assertEquals("GC8813AC16", primaVariante.codice)
    }

    @Test
    fun `ricerca differenziale puro identifica EC000003`() = runBlocking {
        val results = service.search("differenziale puro 25a 30ma tipo ac")
        assertTrue(results.isNotEmpty())

        val diff = results.first { it.etimClassId == "EC000003" }
        assertEquals("Interruttore differenziale puro", diff.etimClassName)
        assertEquals("25A", diff.caratteristicheTecniche["Corrente nominale (In)"])
        assertEquals("30mA", diff.caratteristicheTecniche["Sensibilità differenziale (Idn)"])
    }

    @Test
    fun `ricerca scaricatore sovratensione identifica EC000228`() = runBlocking {
        val results = service.search("scaricatore di sovratensione spd t2")
        assertTrue(results.isNotEmpty())

        val spd = results.first { it.etimClassId == "EC000228" }
        assertEquals("Scaricatore di sovratensione", spd.etimClassName)
    }

    @Test
    fun `ricerca query vuota restituisce lista vuota`() = runBlocking {
        val results = service.search("   ")
        assertTrue(results.isEmpty())
    }

    @Test
    fun `test query utente S251 NA C 6`() = runBlocking {
        val q = "S251 NA C 6 INTERRUTTORE AUTOMAT. 6KA 1P+N – ABB EF 070 6"
        val results = service.search(q)
        println("RESULTS for '$q': ${results.size}")
        results.forEach { cand ->
            println("  Candidate: ${cand.etimClassId} - ${cand.descrizioneStandard}")
            cand.variantiDisponibili.forEach { v ->
                println("    Variant: ${v.produttore} - ${v.codice} - ${v.descrizione}")
            }
        }
        assertTrue(results.isNotEmpty(), "La ricerca per '$q' deve produrre risultati!")
    }
}
