package manutenzioni.domain.model

import kotlinx.serialization.json.Json
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import kotlin.test.*

class AntincendioWave2Test {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val attivitaIstituzionali10 = (1..10).map { i ->
        Attivita(
            nAttivita = i,
            tipoAttivita = if (i in listOf(6, 8, 9)) "Verifica" else "Controllo",
            descrizione = "Attività istituzionale $i centrale e linee",
            frequenza = when {
                i <= 3 -> Periodo(TipoPeriodo.M, 1)
                i <= 6 -> Periodo(TipoPeriodo.M, 2)
                i <= 9 -> Periodo(TipoPeriodo.M, 4)
                else -> Periodo(TipoPeriodo.M, 6)
            },
            targetImpiantoCod = null
        )
    }

    private val attivitaCrossPlantGe = Attivita(
        nAttivita = 11,
        tipoAttivita = "Verifica",
        descrizione = "Sgancio emergenza Gruppo Elettrogeno",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = "GE"
    )

    private val impiantoRiBase = RilevazioneAntincendio(
        codIntervento = "RI",
        nomeCompleto = "Rilevazione Incendi",
        listaAttivita = attivitaIstituzionali10 + attivitaCrossPlantGe
    )

    @Test
    fun `R18 - Con 0 categorie attive genera esattamente le 10 attivita istituzionali`() {
        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = impiantoRiBase,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(10, risolte.size, "Deve contenere le 10 attività istituzionali di centrale e linee")
        assertEquals((1..10).toList(), risolte.map { it.nAttivita })
    }

    @Test
    fun `R18 - Con 1 categoria attiva inietta 1 prova dinamica al 50 percento (totale 11)`() {
        val riCon1 = impiantoRiBase.withCategoria(CategoriaRivelatore.FUMO_PUNTIFORMI, true)

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = riCon1,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(11, risolte.size, "10 istituzionali + 1 dinamica = 11 attività")
        assertEquals((1..11).toList(), risolte.map { it.nAttivita })

        val dinamica = risolte.last()
        assertEquals(11, dinamica.nAttivita)
        assertEquals("Verifica", dinamica.tipoAttivita)
        assertEquals(Periodo(TipoPeriodo.M, 6), dinamica.frequenza)
        assertTrue(dinamica.descrizione?.contains("Rivelatori di Fumo Puntiformi") == true)
        assertTrue(dinamica.descrizione?.contains("prova sul 50% dell’impianto") == true)
    }

    @Test
    fun `R18 - Con 3 categorie attive inietta 3 prove dinamiche (totale 13)`() {
        val riCon3 = impiantoRiBase
            .withCategoria(CategoriaRivelatore.FUMO_PUNTIFORMI, true)
            .withCategoria(CategoriaRivelatore.CALORE_PUNTIFORMI, true)
            .withCategoria(CategoriaRivelatore.ASPIRAZIONE, true)

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = riCon3,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(13, risolte.size, "10 istituzionali + 3 dinamiche = 13 attività")
        assertEquals((1..13).toList(), risolte.map { it.nAttivita })

        val descrizioni = risolte.mapNotNull { it.descrizione }
        assertTrue(descrizioni.any { it.contains("Rivelatori di Fumo Puntiformi") })
        assertTrue(descrizioni.any { it.contains("Rivelatori di Calore Puntiformi") })
        assertTrue(descrizioni.any { it.contains("Rivelazione ad Aspirazione") })
    }

    @Test
    fun `R18 - Con tutte e 7 le categorie attive inietta 7 prove dinamiche (totale 17)`() {
        var riCon7 = impiantoRiBase
        CategoriaRivelatore.values().forEach { cat ->
            riCon7 = riCon7.withCategoria(cat, true)
        }

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = riCon7,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(17, risolte.size, "10 istituzionali + 7 dinamiche = 17 attività")
        assertEquals((1..17).toList(), risolte.map { it.nAttivita })

        CategoriaRivelatore.values().forEach { cat ->
            assertTrue(risolte.any { it.descrizione == cat.buildDescrizione() }, "Manca attività per ${cat.label}")
        }
    }

    @Test
    fun `R18 - Con impianto GE presente nel cantiere include anche attivita cross-plant con numerazione sequenziale`() {
        val ge = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo Elettrogeno")
        val riCon1 = impiantoRiBase.withCategoria(CategoriaRivelatore.FUMO_PUNTIFORMI, true)

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = riCon1,
            impiantiNelCantiere = listOf(riCon1, ge),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(12, risolte.size, "10 istituzionali + 1 dinamica + 1 GE = 12 attività")
        assertEquals((1..12).toList(), risolte.map { it.nAttivita })
        assertTrue(risolte.any { it.targetImpiantoCod == "GE" })
    }

    @Test
    fun `R19 - Serializzazione polimorfica di RilevazioneAntincendio con categorie e dispositivi`() {
        val disp1 = DispositivoRilevazione(
            loop = 1,
            indirizzo = "1",
            tipo = "FUMO",
            descrizione = "CORRIDOIO",
            zona = "Z1"
        )
        val disp2 = DispositivoRilevazione(
            loop = 2,
            indirizzo = "MOD.L2.1",
            tipo = "MODULO",
            descrizione = "MODULO SGANCIO",
            zona = "Z2"
        )

        val ri = RilevazioneAntincendio(
            id = "ri-uuid-test",
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            cantiereId = "cantiere-alpha",
            categorieRivelatori = mapOf(
                CategoriaRivelatore.FUMO_PUNTIFORMI.key to true,
                CategoriaRivelatore.FIAMMA.key to true
            ),
            listaDispositivi = listOf(disp1, disp2)
        )

        val encoded = json.encodeToString(Impianto.serializer(), ri)
        assertTrue(encoded.contains("\"type\": \"RilevazioneAntincendio\""))
        assertTrue(encoded.contains("\"fumo_puntiformi\": true"))
        assertTrue(encoded.contains("\"fiamma\": true"))
        assertTrue(encoded.contains("\"MOD.L2.1\""))

        val decoded = json.decodeFromString(Impianto.serializer(), encoded)
        assertTrue(decoded is RilevazioneAntincendio)
        assertEquals(2, decoded.categorieRivelatori.size)
        assertTrue(decoded.isCategoriaAttiva(CategoriaRivelatore.FUMO_PUNTIFORMI))
        assertTrue(decoded.isCategoriaAttiva(CategoriaRivelatore.FIAMMA))
        assertFalse(decoded.isCategoriaAttiva(CategoriaRivelatore.ASPIRAZIONE))
        assertEquals(2, decoded.listaDispositivi.size)
        assertEquals("MOD.L2.1", decoded.listaDispositivi[1].indirizzo)
    }

    @Test
    fun `R20 - Guardrail RilevazioneGas resta inalterato e non subisce iniezioni dinamiche`() {
        val gasAttivita = listOf(
            Attivita(1, "Controllo", "Verifica sensori gas metano", Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null),
            Attivita(2, "Verifica", "Verifica elettrovalvola intercettazione", Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null)
        )
        val rig = RilevazioneGas(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas",
            listaAttivita = gasAttivita
        )

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = rig,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(2, risolte.size)
        assertEquals("Verifica sensori gas metano", risolte[0].descrizione)
        assertEquals("Verifica elettrovalvola intercettazione", risolte[1].descrizione)
    }
}
