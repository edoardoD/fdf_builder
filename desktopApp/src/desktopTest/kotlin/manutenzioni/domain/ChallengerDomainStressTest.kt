package manutenzioni.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import manutenzioni.app.data.ManutenzioniDatabase
import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.service.rules.CrossPlantRule
import manutenzioni.domain.service.rules.DefaultCrossPlantRule
import manutenzioni.domain.service.rules.FireAlarmCrossPlantRule
import kotlin.test.*

class ChallengerDomainStressTest {

    private val jsonWithUnknownKeys = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    @BeforeTest
    fun setup() {
        CrossPlantAttivitaResolver.resetToDefaults()
    }

    @AfterTest
    fun cleanup() {
        CrossPlantAttivitaResolver.resetToDefaults()
    }

    // =========================================================================
    // TASK 1: CrossPlantAttivitaResolver Edge Cases & Stress Scenarios
    // =========================================================================

    @Test
    fun `CrossPlantAttivitaResolver handles empty context with 0, 10, and 20 base activities`() {
        val rule = FireAlarmCrossPlantRule()

        // 1. Plant with 0 base activities
        val emptyPlant = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi Vuoto",
            listaAttivita = emptyList()
        )
        val resEmpty = rule.resolveAttivita(emptyPlant, emptyList(), Periodo(TipoPeriodo.M, 6))
        assertTrue(resEmpty.isEmpty(), "Con 0 attività base deve restituire lista vuota")

        val freqsEmpty = rule.resolveFrequenze(emptyPlant, emptyList())
        assertEquals(listOf(6), freqsEmpty.map { it.inMesi() }, "Deve includere comunque il semestrale 6M")

        // 2. Plant with exactly 10 base activities
        val tenActivities = (1..10).map { i ->
            Attivita(nAttivita = i, descrizione = "Base $i", frequenza = Periodo(TipoPeriodo.M, 6))
        }
        val plant10 = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "RI 10 Attivita",
            listaAttivita = tenActivities
        )
        val res10 = rule.resolveAttivita(plant10, emptyList(), Periodo(TipoPeriodo.M, 6))
        assertEquals(10, res10.size)
        assertEquals((1..10).toList(), res10.map { it.nAttivita })

        // 3. Plant with 20 base activities (must cap at 10 institutional activities at 6M)
        val twentyActivities = (1..20).map { i ->
            Attivita(nAttivita = i, descrizione = "Base $i", frequenza = Periodo(TipoPeriodo.M, 6))
        }
        val plant20 = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "RI 20 Attivita",
            listaAttivita = twentyActivities
        )
        val res20 = rule.resolveAttivita(plant20, emptyList(), Periodo(TipoPeriodo.M, 6))
        assertEquals(10, res20.size, "Deve limitare tassativamente a 10 le attività istituzionali base a 6M")
        assertEquals((1..10).toList(), res20.map { it.nAttivita })
    }

    @Test
    fun `CrossPlantAttivitaResolver excludes conditioned activities when context is empty or non-matching`() {
        val conditionedActivities = listOf(
            Attivita(nAttivita = 1, descrizione = "Base Centrale", frequenza = Periodo(TipoPeriodo.M, 6)),
            Attivita(nAttivita = 2, descrizione = "Sgancio GE", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "GE"),
            Attivita(nAttivita = 3, descrizione = "Interblocco PEM", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "PEM"),
            Attivita(nAttivita = 4, descrizione = "Interfaccia CAB", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "CAB")
        )

        // Case A: DefaultCrossPlantRule on standard plant
        val standardPlant = ImpiantoStandard(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas",
            listaAttivita = conditionedActivities
        )

        // Empty context -> only base activity remains
        val resEmpty = CrossPlantAttivitaResolver.resolveAttivita(standardPlant, emptyList(), Periodo(TipoPeriodo.M, 6))
        assertEquals(1, resEmpty.size)
        assertEquals("Base Centrale", resEmpty[0].descrizione)
        assertEquals(1, resEmpty[0].nAttivita)

        // Non-matching context: plants with other codes (TR, Q, EM)
        val nonMatchingContext = listOf(
            ImpiantoStandard(codIntervento = "TR", nomeCompleto = "Trasformatore"),
            ImpiantoStandard(codIntervento = "Q", nomeCompleto = "Quadro"),
            ImpiantoStandard(codIntervento = "EM", nomeCompleto = "Emergenza")
        )
        val resNonMatching = CrossPlantAttivitaResolver.resolveAttivita(standardPlant, nonMatchingContext, Periodo(TipoPeriodo.M, 6))
        assertEquals(1, resNonMatching.size)
        assertEquals("Base Centrale", resNonMatching[0].descrizione)

        // Case B: FireAlarmCrossPlantRule with conditioned activities and empty context
        val riPlant = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaAttivita = conditionedActivities
        )

        val resRiEmpty = CrossPlantAttivitaResolver.resolveAttivita(riPlant, emptyList(), Periodo(TipoPeriodo.M, 6))
        // 1 base activity (conditioned activities GE, PEM, CAB excluded)
        assertEquals(1, resRiEmpty.size)
        assertEquals("Base Centrale", resRiEmpty[0].descrizione)
        assertEquals(1, resRiEmpty[0].nAttivita)
    }

    @Test
    fun `CrossPlantAttivitaResolver matches target plant codes case-insensitively with whitespace trimming`() {
        val conditionedActivities = listOf(
            Attivita(nAttivita = 1, descrizione = "Verifica GE", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "  ge  "),
            Attivita(nAttivita = 2, descrizione = "Verifica PEM", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "PeM"),
            Attivita(nAttivita = 3, descrizione = "Verifica CAB", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "CAB\t")
        )
        val plant = ImpiantoStandard(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas",
            listaAttivita = conditionedActivities
        )

        // Context plants have mixed case and whitespace
        val cantierePlants = listOf(
            ImpiantoStandard(codIntervento = "  GE ", nomeCompleto = "Gruppo Elettrogeno"),
            ImpiantoStandard(codIntervento = "pem", nomeCompleto = "Pompe Emergenza"),
            ImpiantoStandard(codIntervento = "\tcab ", nomeCompleto = "Cabina MT/BT")
        )

        val resolved = CrossPlantAttivitaResolver.resolveAttivita(plant, cantierePlants, Periodo(TipoPeriodo.M, 6))
        assertEquals(3, resolved.size, "Tutte e 3 le attività devono essere risolte nonostante differenze di case/spazi")
        assertEquals(listOf(1, 2, 3), resolved.map { it.nAttivita })
    }

    @Test
    fun `CrossPlantAttivitaResolver handles overlapping frequencies and deduplication in resolveFrequenze`() {
        val activities = listOf(
            Attivita(nAttivita = 1, descrizione = "A1", frequenza = Periodo(TipoPeriodo.M, 6)),
            Attivita(nAttivita = 2, descrizione = "A2", frequenza = Periodo(TipoPeriodo.M, 6)),
            Attivita(nAttivita = 3, descrizione = "A3", frequenza = Periodo(TipoPeriodo.A, 1)), // 12M
            Attivita(nAttivita = 4, descrizione = "A4", frequenza = Periodo(TipoPeriodo.M, 12)), // 12M duplicate in months
            Attivita(nAttivita = 5, descrizione = "A5", frequenza = Periodo(TipoPeriodo.A, 2))  // 24M
        )
        val plant = ImpiantoStandard(
            codIntervento = "TEST",
            nomeCompleto = "Test Plant",
            listaAttivita = activities
        )

        // At 12M (1 year), should include 6M and 12M activities (A1, A2, A3, A4), excluding 24M (A5)
        val resolvedAt12M = CrossPlantAttivitaResolver.resolveAttivita(plant, listOf(plant), Periodo(TipoPeriodo.A, 1))
        assertEquals(4, resolvedAt12M.size)
        assertEquals(listOf(1, 2, 3, 4), resolvedAt12M.map { it.nAttivita })

        // resolveFrequenze should deduplicate frequencies sharing same inMesi()
        val freqs = CrossPlantAttivitaResolver.resolveFrequenze(plant, listOf(plant))
        assertEquals(listOf(6, 12, 24), freqs.map { it.inMesi() }, "Deve deduplicare 12M e 1A preservando l'ordine crescente")
    }

    @Test
    fun `CrossPlantAttivitaResolver handles concurrent invocations under multithreaded stress`() = runBlocking(Dispatchers.Default) {
        val plant = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi MultiThread",
            listaAttivita = (1..15).map {
                Attivita(nAttivita = it, descrizione = "Att $it", frequenza = Periodo(TipoPeriodo.M, 6))
            }
        )

        val jobs = (1..50).map { threadIdx ->
            async {
                val resolved = CrossPlantAttivitaResolver.resolveAttivita(
                    plant,
                    listOf(plant),
                    Periodo(TipoPeriodo.M, 6)
                )
                val freqs = CrossPlantAttivitaResolver.resolveFrequenze(plant, listOf(plant))
                val isCp = CrossPlantAttivitaResolver.isCrossPlant(plant)
                Triple(resolved.size, freqs.size, isCp)
            }
        }

        val results = jobs.awaitAll()
        assertEquals(50, results.size)
        for (res in results) {
            assertEquals(10, res.first, "10 istituzionali senza iniezioni dinamiche")
            assertTrue(res.second >= 1)
            assertTrue(res.third)
        }
    }

    // =========================================================================
    // TASK 2: Impianto Polymorphic Getters across all 6 subclasses with edge data
    // =========================================================================

    @Test
    fun `QuadroBT polymorphic getters handle blank, null, special characters, and negative counts`() {
        // 1. Completely blank sigla and descrizione
        val blankQuadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadri BT",
            sigla = "   ",
            descrizioneQuadro = "   ",
            listaInterruttori = emptyList()
        )
        assertEquals("Q", blankQuadro.siglaCartiglio)
        assertEquals("Q", blankQuadro.filePrefix)
        assertEquals("Q", blankQuadro.displayCode)
        assertEquals("Quadri BT", blankQuadro.descrizioneDettagliata)
        assertEquals("Quadri BT", blankQuadro.nomeConSigla)
        assertNull(blankQuadro.subtipoLabel)
        assertEquals(0, blankQuadro.conteggioComponenti)
        assertEquals("No", blankQuadro.dettaglioAllegato)
        assertNull(blankQuadro.extraDettaglio)
        assertFalse(blankQuadro.hasTechnicalAttachment)
        assertTrue(blankQuadro.isQuadro)
        assertTrue(blankQuadro.requiresMassiveCreation)
        assertEquals(ImpiantoKind.QUADRO_BT, blankQuadro.kind)

        // 2. Special characters and spaces in sigla
        val specialQuadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadri BT",
            sigla = "QG/1 @ 2026!",
            descrizioneQuadro = "Quadro Principale Stabilimento",
            listaInterruttori = listOf(
                InterruttoreBT(nome = "Int 1", quantita = 0), // quantita 0 should coerceAtLeast(1) -> 1
                InterruttoreBT(nome = "Int 2", quantita = -5), // quantita -5 should coerceAtLeast(1) -> 1
                InterruttoreBT(nome = "Int 3", quantita = 4)
            )
        )
        assertEquals("Q - QG/1 @ 2026!", specialQuadro.siglaCartiglio)
        assertEquals("Q-QG/1_@_2026!", specialQuadro.filePrefix)
        assertEquals("Q (QG/1 @ 2026!)", specialQuadro.displayCode)
        assertEquals("Quadro Principale Stabilimento", specialQuadro.descrizioneDettagliata)
        assertEquals("Quadri BT (QG/1 @ 2026!)", specialQuadro.nomeConSigla)
        assertEquals("Sigla: QG/1 @ 2026!", specialQuadro.subtipoLabel)
        assertEquals(6, specialQuadro.conteggioComponenti, "0 -> 1, -5 -> 1, 4 -> 4 = sum 6")
        assertEquals("Sì (6 diff.)", specialQuadro.dettaglioAllegato)
        assertEquals("3 interruttori", specialQuadro.extraDettaglio)
        assertTrue(specialQuadro.hasTechnicalAttachment)

        // 3. Fallback when both descrizioneQuadro and nomeCompleto are blank
        val ultraBlankQuadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "   ",
            sigla = "",
            descrizioneQuadro = "   "
        )
        assertEquals("   ", ultraBlankQuadro.descrizioneDettagliata)
    }

    @Test
    fun `ImpiantoEmergenza polymorphic getters handle EM, IS, and edge data`() {
        val em = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza",
            listaLampade = listOf(
                LampadaEmergenza(modello = "Beghelli 1h"),
                LampadaEmergenza(modello = "Schneider 3h")
            )
        )
        assertEquals(ImpiantoKind.EMERGENZA, em.kind)
        assertTrue(em.isEmergenza)
        assertEquals(EmergenzaSiglaDoc.EM, em.siglaDocumentale)
        assertEquals("EM (Emergenza)", em.subtipoLabel)
        assertEquals(2, em.conteggioComponenti)
        assertEquals("Sì (2 lampade)", em.dettaglioAllegato)
        assertEquals("2 lampade", em.extraDettaglio)
        assertTrue(em.hasTechnicalAttachment)

        // Test IS
        val isPlant = em.withSiglaDocumentale(EmergenzaSiglaDoc.IS)
        assertEquals("IS", isPlant.codIntervento)
        assertEquals("IS (Sicurezza)", isPlant.subtipoLabel)

        // Test withSiglaDocumentale preserving id and lampade
        assertEquals(2, isPlant.listaLampade.size)
        assertEquals(em.id, isPlant.id)
    }

    @Test
    fun `QuadroMQT polymorphic getters and formatZonaAttivita handle null and all switch types`() {
        // 1. tipoInterruttore is null
        val nullMqt = QuadroMQT(
            codIntervento = "QMT",
            nomeCompleto = "Quadro Media Tensione",
            tipoInterruttore = null
        )
        assertEquals(ImpiantoKind.QUADRO_MQT, nullMqt.kind)
        assertTrue(nullMqt.isQuadroMqt)
        assertNull(nullMqt.subtipoLabel)

        // formatZonaAttivita fallback
        val attSwitch = Attivita(nAttivita = 1, descrizione = "Verifica", zona = "Interruttore", frequenza = Periodo(TipoPeriodo.M, 6))
        val attSez = Attivita(nAttivita = 2, descrizione = "Verifica", zona = "SEZ.", frequenza = Periodo(TipoPeriodo.M, 6))
        val attBlank = Attivita(nAttivita = 3, descrizione = "Verifica", zona = "   ", frequenza = Periodo(TipoPeriodo.M, 6))

        assertEquals("SF6", nullMqt.formatZonaAttivita(attSwitch), "Default fallback su SF6 quando tipoInterruttore è null")
        assertEquals("SEZ.", nullMqt.formatZonaAttivita(attSez))
        assertEquals("attività", nullMqt.formatZonaAttivita(attBlank))

        // 2. ISV and IVOR switch types
        val isvMqt = nullMqt.withTipoInterruttore(MqtSwitchType.ISV)
        assertEquals("Interruttore: ISV", isvMqt.subtipoLabel)
        assertEquals("ISV", isvMqt.formatZonaAttivita(attSwitch))

        val ivorMqt = nullMqt.withTipoInterruttore(MqtSwitchType.IVOR)
        assertEquals("Interruttore: IVOR", ivorMqt.subtipoLabel)
        assertEquals("IVOR", ivorMqt.formatZonaAttivita(attSwitch))

        // 3. instantiateForCantiere defaults to SF6 when null
        val instantiated = nullMqt.instantiateForCantiere("cantiere-99")
        assertEquals(MqtSwitchType.SF6, (instantiated as QuadroMQT).tipoInterruttore)
        assertEquals("cantiere-99", instantiated.cantiereId)
    }

    @Test
    fun `RilevazioneAntincendio and RilevazioneGas polymorphic getters handle edge counts`() {
        // RilevazioneGas with empty components vs populated
        val emptyGas = RilevazioneGas(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas",
            quantitaComponenti = emptyMap()
        )
        assertEquals(ImpiantoKind.GAS, emptyGas.kind)
        assertTrue(emptyGas.isGas)
        assertEquals(0, emptyGas.conteggioComponenti)
        assertEquals("No", emptyGas.dettaglioAllegato)
        assertNull(emptyGas.extraDettaglio)
        assertFalse(emptyGas.hasTechnicalAttachment)

        val populatedGas = emptyGas.copy(
            quantitaComponenti = mapOf("sensoriMetano" to 4, "elettrovalvole" to 2)
        )
        assertEquals(6, populatedGas.conteggioComponenti)
        assertEquals("Sì (6 comp.)", populatedGas.dettaglioAllegato)
        assertEquals("6 componenti", populatedGas.extraDettaglio)
        assertTrue(populatedGas.hasTechnicalAttachment)

        // RilevazioneAntincendio with devices
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(
                DispositivoRilevazione.RivelatoreLoop(
                    loop = 1,
                    numeroIndirizzo = 1,
                    tipo = TipoRivelatore.OTTICO,
                    descrizione = "Rivelatore Corridoio"
                )
            )
        )
        assertEquals(ImpiantoKind.ANTINCENDIO, ri.kind)
        assertTrue(ri.isAntincendio)
        assertEquals(1, ri.conteggioComponenti)
        assertEquals("Sì (1 disp.)", ri.dettaglioAllegato)
        assertEquals("1 dispositivi", ri.extraDettaglio)
        assertTrue(ri.hasTechnicalAttachment)
    }

    @Test
    fun `Impianto normalizeType converts standard plants to specialized subclasses correctly`() {
        val standardQuadro = ImpiantoStandard(codIntervento = "Q", nomeCompleto = "Quadro Generale")
        val standardEmergenza = ImpiantoStandard(codIntervento = "EM", nomeCompleto = "Emergenza")
        val standardSicurezza = ImpiantoStandard(codIntervento = "IS", nomeCompleto = "Sicurezza")
        val standardRi = ImpiantoStandard(codIntervento = "RI", nomeCompleto = "Antincendio")
        val standardRig = ImpiantoStandard(codIntervento = "RIG", nomeCompleto = "Rilevazione Gas")
        val standardQmt = ImpiantoStandard(codIntervento = "QMT", nomeCompleto = "Quadro MT")
        val standardCab = ImpiantoStandard(codIntervento = "CAB", nomeCompleto = "Cabina MT/BT")

        assertTrue(standardQuadro.normalizeType() is QuadroBT)
        assertTrue(standardEmergenza.normalizeType() is ImpiantoEmergenza)
        assertTrue(standardSicurezza.normalizeType() is ImpiantoEmergenza)
        assertTrue(standardRi.normalizeType() is RilevazioneAntincendio)
        assertTrue(standardRig.normalizeType() is RilevazioneGas)
        assertTrue(standardQmt.normalizeType() is QuadroMQT)
        assertTrue(standardCab.normalizeType() is ImpiantoStandard)
    }

    // =========================================================================
    // TASK 3: @Serializable JSON Roundtrip with Unknown Keys (ignoreUnknownKeys=true)
    // =========================================================================

    @Test
    fun `Polymorphic Impianto deserialization survives unknown root and nested keys across all 6 subclasses`() {
        val allSubtypesJson = """
        [
            {
                "type": "ImpiantoStandard",
                "id": "std-1",
                "codIntervento": "CAB",
                "nomeCompleto": "Cabina MT/BT",
                "listaAttivita": [
                    {
                        "descrizione": "Verifica trafo",
                        "frequenza": { "tipo": "M", "valore": 6, "unknownPeriodField": true },
                        "unknownAttivitaField": "extraValue"
                    }
                ],
                "listaNormative": [
                    {
                        "codNormativa": "CEI 99-2",
                        "descrizione": "Cabine",
                        "unknownNormField": 123
                    }
                ],
                "unknownStandardField": "stdUnknown"
            },
            {
                "type": "QuadroBT",
                "id": "q-1",
                "codIntervento": "Q",
                "nomeCompleto": "Quadro BT",
                "sigla": "QG1",
                "descrizioneQuadro": "Quadro Principale",
                "listaInterruttori": [
                    {
                        "id": "int-1",
                        "nome": "Interruttore 1",
                        "quantita": 2,
                        "unknownSwitchField": "extra"
                    }
                ],
                "unknownQuadroField": { "subObj": 42 }
            },
            {
                "type": "ImpiantoEmergenza",
                "id": "em-1",
                "codIntervento": "EM",
                "nomeCompleto": "Emergenza",
                "listaLampade": [
                    {
                        "id": "lamp-1",
                        "modello": "Beghelli 1h",
                        "unknownLampField": "extra"
                    }
                ],
                "unknownEmergenzaArray": [1, 2, 3]
            },
            {
                "type": "RilevazioneAntincendio",
                "id": "ri-1",
                "codIntervento": "RI",
                "nomeCompleto": "Antincendio",
                "listaDispositivi": [
                    {
                        "type": "RivelatoreLoop",
                        "id": "disp-1",
                        "loop": 1,
                        "numeroIndirizzo": 1,
                        "tipo": "OTTICO",
                        "descrizione": "Rivelatore",
                        "unknownDeviceField": true
                    }
                ],
                "unknownRiField": "extra"
            },
            {
                "type": "RilevazioneGas",
                "id": "rig-1",
                "codIntervento": "RIG",
                "nomeCompleto": "Gas",
                "quantitaComponenti": { "sensori": 5, "unknownComponent": 2 },
                "unknownGasField": 999
            },
            {
                "type": "QuadroMQT",
                "id": "qmt-1",
                "codIntervento": "QMT",
                "nomeCompleto": "Quadro MT",
                "tipoInterruttore": "ISV",
                "unknownMqtField": false
            }
        ]
        """.trimIndent()

        val deserialized = jsonWithUnknownKeys.decodeFromString<List<Impianto>>(allSubtypesJson)
        assertEquals(6, deserialized.size)

        // 1. ImpiantoStandard
        val std = deserialized[0]
        assertTrue(std is ImpiantoStandard)
        assertEquals("CAB", std.codIntervento)
        assertEquals(1, std.listaAttivita.size)
        assertEquals("Verifica trafo", std.listaAttivita[0].descrizione)

        // 2. QuadroBT
        val q = deserialized[1]
        assertTrue(q is QuadroBT)
        assertEquals("QG1", q.sigla)
        assertEquals(1, q.listaInterruttori.size)
        assertEquals("Interruttore 1", q.listaInterruttori[0].nome)

        // 3. ImpiantoEmergenza
        val em = deserialized[2]
        assertTrue(em is ImpiantoEmergenza)
        assertEquals(1, em.listaLampade.size)
        assertEquals("Beghelli 1h", em.listaLampade[0].modello)

        // 4. RilevazioneAntincendio
        val ri = deserialized[3]
        assertTrue(ri is RilevazioneAntincendio)
        assertEquals(1, ri.listaDispositivi.size)
        assertTrue(ri.listaDispositivi[0] is DispositivoRilevazione.RivelatoreLoop)

        // 5. RilevazioneGas
        val rig = deserialized[4]
        assertTrue(rig is RilevazioneGas)
        assertEquals(7, rig.conteggioComponenti)

        // 6. QuadroMQT
        val qmt = deserialized[5]
        assertTrue(qmt is QuadroMQT)
        assertEquals(MqtSwitchType.ISV, qmt.tipoInterruttore)

        // Roundtrip re-serialization must preserve types
        val reEncoded = jsonWithUnknownKeys.encodeToString<List<Impianto>>(deserialized)
        val reDecoded = jsonWithUnknownKeys.decodeFromString<List<Impianto>>(reEncoded)
        assertEquals(6, reDecoded.size)
        assertEquals(deserialized.map { it.id }, reDecoded.map { it.id })
    }

    @Test
    fun `Auxiliary domain models survive deserialization with unknown keys`() {
        // 1. Cliente with extra unknown fields
        val clienteJson = """
        {
            "id": "cli-123",
            "nome": "Azienda Ospedaliera",
            "indirizzo": "Via Roma 1",
            "partitaIva": "IT12345678901",
            "legacyCrmId": 98765,
            "extraMeta": { "active": true, "rating": "AAA" }
        }
        """.trimIndent()
        val cliente = jsonWithUnknownKeys.decodeFromString<Cliente>(clienteJson)
        assertEquals("cli-123", cliente.id)
        assertEquals("Azienda Ospedaliera", cliente.nome)

        // 2. Cantiere with dataIntervento and unknown fields
        val cantiereJson = """
        {
            "id": "cant-456",
            "nome": "Sede Centrale",
            "clienteId": "cli-123",
            "dataIntervento": "2026-05-10",
            "nomeManutentore": "Mario Rossi",
            "unknownWeatherCondition": "Sunny",
            "unsupportedDeviceList": ["tablet-1", "tablet-2"]
        }
        """.trimIndent()
        val cantiere = jsonWithUnknownKeys.decodeFromString<Cantiere>(cantiereJson)
        assertEquals("cant-456", cantiere.id)
        assertEquals("2026-05-10", cantiere.dataIntervento)

        val normalizedCantiere = cantiere.normalizeDates()
        assertEquals("2026-05-10", normalizedCantiere.dataIntervento)

        // 3. SessioneVerifica with unknown keys
        val sessioneJson = """
        {
            "id": "sess-789",
            "cantiereId": "cant-456",
            "clienteId": "cli-123",
            "meseAnno": "2026-05",
            "elencoFilePdf": ["file1.pdf", "file2.pdf"],
            "futureTelemetryReport": { "bandwidth": 100 }
        }
        """.trimIndent()
        val sessione = jsonWithUnknownKeys.decodeFromString<SessioneVerifica>(sessioneJson)
        assertEquals("sess-789", sessione.id)
        assertEquals(listOf("file1.pdf", "file2.pdf"), sessione.elencoFilePdf)

        // 4. Utente with unknown keys
        val utenteJson = """
        {
            "id": "usr-001",
            "username": "tecnico1",
            "nomeCompleto": "Luigi Bianchi",
            "ruolo": "TECNICO",
            "futureRoleGroup": "MAINT_LEVEL_2"
        }
        """.trimIndent()
        val utente = jsonWithUnknownKeys.decodeFromString<Utente>(utenteJson)
        assertEquals("usr-001", utente.id)
        assertEquals(RuoloUtente.TECNICO, utente.ruolo)
    }

    @Test
    fun `JsonDatabaseStorage defaultJsonConfig handles full ManutenzioniDatabase with unknown collections`() {
        val config = JsonDatabaseStorage.defaultJsonConfig()
        val fullDbJson = """
        {
            "clienti": [
                { "id": "c1", "nome": "Cliente 1" }
            ],
            "cantieri": [
                { "id": "k1", "nome": "Cantiere 1", "clienteId": "c1" }
            ],
            "impianti": [
                { "type": "ImpiantoStandard", "id": "i1", "codIntervento": "GE", "nomeCompleto": "Gruppo Elettrogeno" }
            ],
            "interruttoriApprovati": [],
            "catalogoApprovato": [],
            "sessioniVerifica": [],
            "utenti": [],
            "eventiManutenzione": [],
            "unsupportedCollectionV2": [
                { "key": "val" }
            ]
        }
        """.trimIndent()

        val db = config.decodeFromString<ManutenzioniDatabase>(fullDbJson)
        assertEquals(1, db.clienti.size)
        assertEquals(1, db.cantieri.size)
        assertEquals(1, db.impianti.size)
        assertEquals("c1", db.clienti.first().id)
        assertEquals("k1", db.cantieri.first().id)
        assertEquals("i1", db.impianti.first().id)
    }
}
