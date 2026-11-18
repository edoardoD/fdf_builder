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
    fun `RI - Con 0 impianti collegati genera esattamente le 10 attivita istituzionali senza iniezioni legacy`() {
        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = impiantoRiBase,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(10, risolte.size, "Deve contenere le 10 attività istituzionali di centrale e linee")
        assertEquals((1..10).toList(), risolte.map { it.nAttivita })
    }

    @Test
    fun `RI - Con impianto GE presente nel cantiere include attivita cross-plant con numerazione sequenziale`() {
        val ge = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo Elettrogeno")

        val risolte = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = impiantoRiBase,
            impiantiNelCantiere = listOf(impiantoRiBase, ge),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        assertEquals(11, risolte.size, "10 istituzionali + 1 cross-plant GE = 11 attività")
        assertEquals((1..11).toList(), risolte.map { it.nAttivita })
        assertTrue(risolte.any { it.targetImpiantoCod == "GE" })
    }

    @Test
    fun `RI - Invarianti dominio su Loop (1_8) e Indirizzo (1_150)`() {
        assertFailsWith<IllegalArgumentException> {
            DispositivoRilevazione.RivelatoreLoop(loop = 0, numeroIndirizzo = 1, descrizione = "Test")
        }
        assertFailsWith<IllegalArgumentException> {
            DispositivoRilevazione.RivelatoreLoop(loop = 9, numeroIndirizzo = 1, descrizione = "Test")
        }
        assertFailsWith<IllegalArgumentException> {
            DispositivoRilevazione.ModuloLoop(loop = 1, numeroIndirizzo = 0, descrizione = "Test")
        }
        assertFailsWith<IllegalArgumentException> {
            DispositivoRilevazione.ModuloLoop(loop = 8, numeroIndirizzo = 151, descrizione = "Test")
        }

        val riv8 = DispositivoRilevazione.RivelatoreLoop(
            loop = 8,
            numeroIndirizzo = 150,
            tipo = TipoRivelatore.BARRIERA,
            descrizione = "BARRIERA RECEPTION"
        )
        assertEquals("RIV.L8.150", riv8.indirizzo)
        assertEquals("LOOP 8", riv8.labelSezione)
        assertEquals("BARR.", riv8.labelTipo)
    }

    @Test
    fun `RI - Serializzazione polimorfica di RilevazioneAntincendio con Comuni, Rivelatori e Moduli`() {
        val comune = DispositivoRilevazione.ComponenteComune(
            tipo = TipoComponenteComuneAntincendio.CENTRALE_INCENDIO,
            indirizzo = "CENTR.1",
            descrizione = "PORTINERIA"
        )
        val disp1 = DispositivoRilevazione.RivelatoreLoop(
            loop = 1,
            numeroIndirizzo = 1,
            tipo = TipoRivelatore.OTTICO,
            descrizione = "CORRIDOIO",
            zona = "Z1"
        )
        val disp2 = DispositivoRilevazione.ModuloLoop(
            loop = 2,
            numeroIndirizzo = 1,
            tipo = TipoModuloAntincendio.PULSANTE,
            descrizione = "USCITA EST",
            zona = "Z2"
        )

        val ri = RilevazioneAntincendio(
            id = "ri-uuid-test",
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            cantiereId = "cantiere-alpha",
            listaDispositivi = listOf(comune, disp1, disp2)
        )

        val encoded = json.encodeToString(Impianto.serializer(), ri)
        assertTrue(encoded.contains("\"type\": \"RilevazioneAntincendio\""))
        assertTrue(encoded.contains("\"CENTR.1\""))
        assertTrue(encoded.contains("\"TERMOVELOCIMETRICO\"") || encoded.contains("\"OTTICO\""))
        assertTrue(encoded.contains("\"PULSANTE\""))

        val decoded = json.decodeFromString(Impianto.serializer(), encoded)
        assertTrue(decoded is RilevazioneAntincendio)
        assertEquals(3, decoded.listaDispositivi.size)
        assertTrue(decoded.listaDispositivi[0] is DispositivoRilevazione.ComponenteComune)
        assertTrue(decoded.listaDispositivi[1] is DispositivoRilevazione.RivelatoreLoop)
        assertTrue(decoded.listaDispositivi[2] is DispositivoRilevazione.ModuloLoop)
        assertEquals("RIV.L1.1", decoded.listaDispositivi[1].indirizzo)
        assertEquals("MOD.L2.1", decoded.listaDispositivi[2].indirizzo)
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
