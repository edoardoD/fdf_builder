package manutenzioni.app.service

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Suite di test Bruteforce per verificare la robustezza del motore di ricerca ETIM
 * contro un vasto set di codici articolo e descrizioni tecniche industriali reali
 * (ABB, BTicino, Schneider, Siemens, Gewiss, Dehn).
 */
class EtimCatalogBruteForceTest {

    private val service = EtimCatalogService()

    @Test
    fun `test query utente S251 NA C 6 riconosce esattamente ABB S251 e codice EF 070 6`() = runBlocking {
        val q = "S251 NA C 6 INTERRUTTORE AUTOMAT. 6KA 1P+N – ABB EF 070 6"
        val results = service.search(q)
        assertTrue(results.isNotEmpty(), "La ricerca per '$q' deve produrre risultati!")

        val candidate = results.first()
        assertEquals("EC000042", candidate.etimClassId, "Deve essere classificato come magnetotermico EC000042")
        assertEquals("1P+N", candidate.caratteristicheTecniche["Poli"])
        assertEquals("6A", candidate.caratteristicheTecniche["Corrente nominale (In)"])
        assertEquals("C", candidate.caratteristicheTecniche["Curva di intervento"])
        assertEquals("6kA", candidate.caratteristicheTecniche["Potere di interruzione (Icn)"])

        // La prima variante DEVE essere ABB con il codice cercato S251 / EF 070 6
        val primaVariante = candidate.variantiDisponibili.first()
        assertEquals("ABB", primaVariante.produttore)
        assertTrue(
            primaVariante.codice.contains("S251") && primaVariante.codice.contains("EF 070 6"),
            "Il codice della prima variante deve contenere S251 e EF 070 6, trovato: ${primaVariante.codice}"
        )

        // Tra le varianti deve essere presente anche l'alternativa moderna SN201
        val sn201 = candidate.variantiDisponibili.find { it.codice.contains("SN201") }
        assertNotNull(sn201, "Deve contenere anche la variante equivalente moderna SN201")

        // Devono essere presenti le alternative cross-brand equivalenti per 1P+N 6A 6kA
        val bticino = candidate.variantiDisponibili.find { it.produttore == "BTicino" }
        assertNotNull(bticino)
        assertTrue(bticino.codice.contains("GN8813AC6") || bticino.codice.contains("FN81NC6"))

        val schneider = candidate.variantiDisponibili.find { it.produttore == "Schneider" }
        assertNotNull(schneider)
        assertTrue(schneider.codice.contains("A9N21556"))
    }

    @Test
    fun `test ricerca per codice ordine fornitore EF 070 6 senza prefisso ABB`() = runBlocking {
        val results = service.search("EF 070 6")
        assertTrue(results.isNotEmpty())
        val primaVariante = results.first().variantiDisponibili.first()
        assertEquals("ABB", primaVariante.produttore)
        assertEquals("6A", results.first().caratteristicheTecniche["Corrente nominale (In)"])
    }

    @Test
    fun `test brute force su 80 query di interruttori reali industriali`() = runBlocking {
        data class TestCase(
            val query: String,
            val expectedEtimClass: String,
            val expectedPoli: String? = null,
            val expectedCorrente: String? = null,
            val expectedBrand: String? = null
        )

        val testCases = listOf(
            // === SERIE ABB MCB (S251, S200, SN201) ===
            TestCase("S251 NA C 6 INTERRUTTORE AUTOMAT. 6KA 1P+N – ABB EF 070 6", "EC000042", "1P+N", "6A", "ABB"),
            TestCase("S251 NA C 10", "EC000042", "1P+N", "10A", "ABB"),
            TestCase("S251 NA C 16", "EC000042", "1P+N", "16A", "ABB"),
            TestCase("S251 NA C 20", "EC000042", "1P+N", "20A", "ABB"),
            TestCase("S251 NA C 25", "EC000042", "1P+N", "25A", "ABB"),
            TestCase("S251 NA C 32", "EC000042", "1P+N", "32A", "ABB"),
            TestCase("EF 070 6", "EC000042", "1P+N", "6A", "ABB"),
            TestCase("EF 070 16", "EC000042", "1P+N", "16A", "ABB"),
            TestCase("S201-C6", "EC000042", "1P+N", "6A", "ABB"),
            TestCase("S201-C10", "EC000042", "1P+N", "10A", "ABB"),
            TestCase("S201-C16", "EC000042", "1P+N", "16A", "ABB"),
            TestCase("S202-C16", "EC000042", "2P", "16A", "ABB"),
            TestCase("S202-C25", "EC000042", "2P", "25A", "ABB"),
            TestCase("S202-C32", "EC000042", "2P", "32A", "ABB"),
            TestCase("S203-C32", "EC000042", "3P", "32A", "ABB"),
            TestCase("S204-C40", "EC000042", "4P", "40A", "ABB"),
            TestCase("S204-C63", "EC000042", "4P", "63A", "ABB"),
            TestCase("SN201 L C16", "EC000042", "1P+N", "16A", "ABB"),
            TestCase("SN201 L C10", "EC000042", "1P+N", "10A", "ABB"),
            TestCase("SN201 L C6", "EC000042", "1P+N", "6A", "ABB"),

            // === SERIE ABB RCBO & DIFFERENZIALI (DS201, DS901, F202, F204) ===
            TestCase("DS201 C16 AC30", "EC000905", "1P+N", "16A", "ABB"),
            TestCase("DS201 C25 AC30", "EC000905", "1P+N", "25A", "ABB"),
            TestCase("DS201 C10 AC30", "EC000905", "1P+N", "10A", "ABB"),
            TestCase("DS901L C16 30mA", "EC000905", "1P+N", "16A", "ABB"),
            TestCase("F202 AC-25/0.03", "EC000003", "1P+N", "25A", "ABB"),
            TestCase("F202 AC-40/0.03", "EC000003", "1P+N", "40A", "ABB"),
            TestCase("F204 AC-40/0.03", "EC000003", "4P", "40A", "ABB"),
            TestCase("F204 AC-63/0.3", "EC000003", "4P", "63A", "ABB"),

            // === SERIE ABB SCATOLATI & SEZIONATORI & SPD ===
            TestCase("XT1B 160 TMD 160-1600", "EC000228", null, "160A", "ABB"),
            TestCase("interruttore scatolato 100A abb xt1", "EC000228", null, "100A", "ABB"),
            TestCase("E202/32G", "EC000216", "2P", "32A", "ABB"),
            TestCase("E204/63G", "EC000216", "4P", "63A", "ABB"),
            TestCase("OVR T2 1N 40-275 P", "EC000228", "1P+N", null, "ABB"),

            // === SERIE BTICINO MCB (FN81, GN88, G88) ===
            TestCase("FN81NC16", "EC000042", "1P+N", "16A", "BTicino"),
            TestCase("FN81NC10", "EC000042", "1P+N", "10A", "BTicino"),
            TestCase("FN81NC6", "EC000042", "1P+N", "6A", "BTicino"),
            TestCase("FN82C16", "EC000042", "2P", "16A", "BTicino"),
            TestCase("FN84C32", "EC000042", "4P", "32A", "BTicino"),
            TestCase("FN84C63", "EC000042", "4P", "63A", "BTicino"),
            TestCase("G8823A16", "EC000042", "2P", "16A", "BTicino"),
            TestCase("G8843A40", "EC000042", "4P", "40A", "BTicino"),
            TestCase("GN8813AC16", "EC000905", "1P+N", "16A", "BTicino"),
            TestCase("GN8813AC10", "EC000905", "1P+N", "10A", "BTicino"),
            TestCase("GN8843AC32", "EC000905", "4P", "32A", "BTicino"),
            TestCase("GN8813A10", "EC000905", "1P+N", "10A", "BTicino"),
            TestCase("GC8813AC16", "EC000905", "1P+N", "16A", "BTicino"),
            TestCase("GC8813AC25", "EC000905", "1P+N", "25A", "BTicino"),

            // === SERIE BTICINO DIFFERENZIALI PURI & SEZIONATORI ===
            TestCase("G723AC25", "EC000003", "1P+N", "25A", "BTicino"),
            TestCase("G723AC40", "EC000003", "1P+N", "40A", "BTicino"),
            TestCase("G724AC40", "EC000003", "4P", "40A", "BTicino"),
            TestCase("G724AC63", "EC000003", "4P", "63A", "BTicino"),
            TestCase("F82/32", "EC000216", "2P", "32A", "BTicino"),
            TestCase("F84/63", "EC000216", "4P", "63A", "BTicino"),

            // === SERIE SCHNEIDER (Acti9, Multi9) ===
            TestCase("A9N21556", "EC000042", "1P+N", "6A", "Schneider"),
            TestCase("A9N21555", "EC000042", "1P+N", "16A", "Schneider"),
            TestCase("A9F74216", "EC000042", "2P", "16A", "Schneider"),
            TestCase("A9F74440", "EC000042", "4P", "40A", "Schneider"),
            TestCase("A9R41225", "EC000003", "1P+N", "25A", "Schneider"),
            TestCase("A9R41240", "EC000003", "1P+N", "40A", "Schneider"),
            TestCase("A9D31616", "EC000905", "1P+N", "16A", "Schneider"),
            TestCase("A9L16292", "EC000228", "1P+N", null, "Schneider"),
            TestCase("A9S65232", "EC000216", "2P", "32A", "Schneider"),

            // === SERIE SIEMENS & GEWISS & DEHN ===
            TestCase("5SL6516-7", "EC000042", "1P+N", "16A", "Siemens"),
            TestCase("5SL6506-7", "EC000042", "1P+N", "6A", "Siemens"),
            TestCase("5SV3314-6", "EC000003", "1P+N", "25A", "Siemens"),
            TestCase("5SU1353-7KK16", "EC000905", "1P+N", "16A", "Siemens"),
            TestCase("GW90026", "EC000042", "1P+N", "6A", "Gewiss"),
            TestCase("GW94617", "EC000003", "1P+N", "25A", "Gewiss"),
            TestCase("DEHNguard M TT 275", "EC000228", "1P+N", null, "Dehn"),

            // === QUERY DESCRITTIVE DIS CORSIVE DA COMPUTO / FATTURA ===
            TestCase("magnetotermico 16a 1p+n curva c 4.5ka", "EC000042", "1P+N", "16A", null),
            TestCase("differenziale puro 25a 30ma tipo ac", "EC000003", "1P+N", "25A", null),
            TestCase("interruttore automatico 1P+N 10A 6kA ABB", "EC000042", "1P+N", "10A", "ABB"),
            TestCase("interruttore magnetotermico differenziale 16A 30mA Bticino", "EC000905", "1P+N", "16A", "BTicino"),
            TestCase("sezionatore 4P 63A Schneider", "EC000216", "4P", "63A", "Schneider"),
            TestCase("scaricatore sovratensione tipo 2 dehn", "EC000228", "1P+N", null, "Dehn"),
            TestCase("interruttore automatico bticino btdin 16a", "EC000042", "1P+N", "16A", "BTicino"),
            TestCase("differenziale salvavita 4P 40A 300mA tipo A", "EC000003", "4P", "40A", null)
        )

        var passedCount = 0
        val errors = mutableListOf<String>()

        testCases.forEachIndexed { idx, tc ->
            val results = service.search(tc.query)
            if (results.isEmpty()) {
                errors.add("[$idx] Query '${tc.query}' non ha prodotto risultati!")
                return@forEachIndexed
            }

            val candidate = results.first()
            if (candidate.etimClassId != tc.expectedEtimClass) {
                errors.add("[$idx] Query '${tc.query}' classe ETIM errata: attesa ${tc.expectedEtimClass}, ottenuta ${candidate.etimClassId}")
                return@forEachIndexed
            }

            if (tc.expectedPoli != null && candidate.caratteristicheTecniche["Poli"] != tc.expectedPoli) {
                errors.add("[$idx] Query '${tc.query}' poli errati: attesi ${tc.expectedPoli}, ottenuti ${candidate.caratteristicheTecniche["Poli"]}")
                return@forEachIndexed
            }

            if (tc.expectedCorrente != null && candidate.caratteristicheTecniche["Corrente nominale (In)"] != tc.expectedCorrente) {
                errors.add("[$idx] Query '${tc.query}' corrente errata: attesa ${tc.expectedCorrente}, ottenuta ${candidate.caratteristicheTecniche["Corrente nominale (In)"]}")
                return@forEachIndexed
            }

            if (tc.expectedBrand != null) {
                val topBrand = candidate.variantiDisponibili.firstOrNull()?.produttore
                if (topBrand != tc.expectedBrand) {
                    errors.add("[$idx] Query '${tc.query}' brand primario errato: atteso ${tc.expectedBrand}, ottenuto $topBrand")
                    return@forEachIndexed
                }
            }

            // Almeno 3 varianti fornite per ogni candidato
            if (candidate.variantiDisponibili.size < 3) {
                errors.add("[$idx] Query '${tc.query}' ha meno di 3 varianti alternative (${candidate.variantiDisponibili.size})")
                return@forEachIndexed
            }

            passedCount++
        }

        println("==================================================================")
        println("  BRUTE FORCE TEST RESULTS: $passedCount / ${testCases.size} PASSED")
        println("==================================================================")

        if (errors.isNotEmpty()) {
            errors.forEach { System.err.println("  ❌ $it") }
        }

        assertEquals(testCases.size, passedCount, "Tutti i ${testCases.size} test case devono passare! Errori: ${errors.joinToString("; ")}")
    }
}
