package manutenzioni.app.service

import kotlinx.coroutines.*
import manutenzioni.app.service.attachments.DefaultTechnicalAttachmentRegistry
import manutenzioni.domain.model.*
import manutenzioni.domain.service.HtmlRenderContext
import manutenzioni.domain.service.attachments.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*

/**
 * Empirical Challenger M2.2 Test Suite:
 * 1. Dynamic registration and unregistration of custom providers without modifying HtmlService.kt.
 * 2. High-concurrency multithreaded stress testing on TechnicalAttachmentRegistry under concurrent PDF/HTML batch generation.
 */
class ChallengerM22OcpAndEtimTest {

    // =========================================================================
    // PART 1: Dynamic Registration & Unregistration without touching HtmlService
    // =========================================================================

    @Test
    fun `verify dynamic registration and unregistration of custom provider on shared registry without touching HtmlService`() {
        val sharedRegistry = DefaultTechnicalAttachmentRegistry.shared
        sharedRegistry.resetToDefaults()

        val fvPlant = ImpiantoStandard(
            id = "plant-fv-1",
            codIntervento = "FV",
            nomeCompleto = "Impianto Fotovoltaico 50kW",
            premessa = "Norma CEI 0-21 / CEI 82-25",
            listaAttivita = listOf(
                Attivita(
                    nAttivita = 1,
                    tipoAttivita = "Verifica Inverter",
                    descrizione = "Verifica isolamento e parametri di rete",
                    frequenza = Periodo(TipoPeriodo.A, 1),
                    zona = "Tetto"
                )
            )
        )

        val customFvProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "FV"

            override fun buildPremessaSummary(impianto: Impianto): String =
                "Generatore Fotovoltaico: 120 moduli monocristallini con 2 inverter di stringa."

            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData {
                return TechnicalSheetData(
                    codScheda = "FV",
                    oggetto = "REGISTRO STRINGHE FOTOVOLTAICHE",
                    titoloBanner = "VERIFICHE STRUMENTALI STRINGHE FOTOVOLTAICHE",
                    sottotitoloBanner = "Norma CEI EN 62446-1",
                    impiantoLabel = impianto.nomeCompleto,
                    sitoLabel = "Campo Fotovoltaico",
                    normative = "CEI EN 62446-1 / CEI 82-25",
                    campoAcroFormPrefix = "fv",
                    columns = listOf(
                        TableColumn("Stringa", 2.0),
                        TableColumn("Voc (V)", 3.0),
                        TableColumn("Isc (A)", 3.0),
                        TableColumn("Riso (MOhm)", 3.4),
                        TableColumn(
                            title = "Esito",
                            subColumns = listOf(SubColumn("CONFORME", 3.5), SubColumn("NON CONF", 3.5))
                        )
                    ),
                    rows = listOf(
                        TechnicalRow(
                            cells = listOf(
                                TechnicalCell.Text("STR-01"),
                                TechnicalCell.TextInput("fv_voc_1", placeholder = "V", label = "Voc"),
                                TechnicalCell.TextInput("fv_isc_1", placeholder = "A", label = "Isc"),
                                TechnicalCell.TextInput("fv_riso_1", placeholder = "MΩ", label = "Riso"),
                                TechnicalCell.Radio("fv_esito_1", "OK", "Conforme"),
                                TechnicalCell.Radio("fv_esito_1", "KO", "Non Conforme")
                            )
                        )
                    ),
                    footerDocTitle = "Fogli Tecnici Fotovoltaico"
                )
            }
        }

        // 1. BEFORE registration: HtmlService default instantiation must NOT contain FV custom summary or sheet
        val htmlServiceDefault = HtmlService()
        val htmlBefore = htmlServiceDefault.buildHtml(
            HtmlRenderContext(
                impianto = fvPlant,
                attivita = fvPlant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Solar Farm S.r.l."
            )
        )

        assertFalse(htmlBefore.contains("Generatore Fotovoltaico: 120 moduli"), "Prima della registrazione la premessa non deve contenere il sommario FV")
        assertFalse(htmlBefore.contains("VERIFICHE STRUMENTALI STRINGHE FOTOVOLTAICHE"), "Prima della registrazione il Foglio 2 non deve contenere il banner FV")
        assertFalse(htmlBefore.contains("fv_voc_1"), "Prima della registrazione non devono comparire campi fv_voc_1")

        // 2. DYNAMIC REGISTRATION on DefaultTechnicalAttachmentRegistry.shared
        sharedRegistry.registerProvider(customFvProvider)

        val htmlAfter = htmlServiceDefault.buildHtml(
            HtmlRenderContext(
                impianto = fvPlant,
                attivita = fvPlant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Solar Farm S.r.l."
            )
        )

        assertTrue(htmlAfter.contains("Generatore Fotovoltaico: 120 moduli"), "Dopo la registrazione il sommario premessa deve essere iniettato")
        assertTrue(htmlAfter.contains("VERIFICHE STRUMENTALI STRINGHE FOTOVOLTAICHE"), "Dopo la registrazione il banner del Foglio 2 deve essere presente")
        assertTrue(htmlAfter.contains("fv_voc_1"), "Il campo AcroForm fv_voc_1 deve essere presente nel Foglio 2")
        assertTrue(htmlAfter.contains("fv_esito_1"), "Il gruppo radio AcroForm fv_esito_1 deve essere presente")

        // 3. UNREGISTRATION
        val removed = sharedRegistry.unregisterProvider(customFvProvider)
        assertTrue(removed, "unregisterProvider deve confermare la rimozione")

        val htmlAfterRemoval = htmlServiceDefault.buildHtml(
            HtmlRenderContext(
                impianto = fvPlant,
                attivita = fvPlant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Solar Farm S.r.l."
            )
        )

        assertFalse(htmlAfterRemoval.contains("Generatore Fotovoltaico: 120 moduli"), "Dopo la rimozione il sommario FV deve scomparire")
        assertFalse(htmlAfterRemoval.contains("VERIFICHE STRUMENTALI STRINGHE FOTOVOLTAICHE"), "Dopo la rimozione il Foglio 2 FV deve scomparire")

        sharedRegistry.resetToDefaults()
    }

    @Test
    fun `priority override replaces standard provider and falls back cleanly on unregister`() {
        val sharedRegistry = DefaultTechnicalAttachmentRegistry.shared
        sharedRegistry.resetToDefaults()

        val fixtureQuadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadro Principale",
            sigla = "QP1",
            listaInterruttori = listOf(InterruttoreBT(id = "i1", nome = "Diff Generale", quantita = 1))
        )

        val customQuadroProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto is QuadroBT
            override fun buildPremessaSummary(impianto: Impianto): String = "OVERRIDE_PREMESSA_QUADRO"
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData {
                return TechnicalSheetData(
                    codScheda = "Q-CUSTOM",
                    oggetto = "CUSTOM DIFFERENZIALI",
                    titoloBanner = "BANNER DIFFERENZIALI OVERRIDE",
                    sottotitoloBanner = "Norma Custom",
                    impiantoLabel = "Quadro Custom",
                    normative = "Norma Speciale",
                    campoAcroFormPrefix = "custom_diff",
                    columns = listOf(TableColumn("Colonna Custom", 18.4)),
                    rows = listOf(TechnicalRow(cells = listOf(TechnicalCell.Text("Cella Custom")))),
                    footerDocTitle = "Doc Custom"
                )
            }
        }

        // Registra con priority = true
        sharedRegistry.registerProvider(customQuadroProvider, priority = true)

        val htmlService = HtmlService()
        val overriddenHtml = htmlService.buildHtml(
            HtmlRenderContext(
                impianto = fixtureQuadro,
                attivita = emptyList(),
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Test Client"
            )
        )

        assertTrue(overriddenHtml.contains("OVERRIDE_PREMESSA_QUADRO"), "La premessa deve usare il provider ad alta priorità")
        assertTrue(overriddenHtml.contains("BANNER DIFFERENZIALI OVERRIDE"), "Il Foglio 2 deve contenere il banner del provider prioritario")

        // Unregister dell'override: deve ripristinare il provider di default (QuadroBtAttachmentProvider)
        sharedRegistry.unregisterProvider(customQuadroProvider)

        val restoredHtml = htmlService.buildHtml(
            HtmlRenderContext(
                impianto = fixtureQuadro,
                attivita = emptyList(),
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Test Client"
            )
        )

        assertFalse(restoredHtml.contains("OVERRIDE_PREMESSA_QUADRO"))
        assertTrue(restoredHtml.contains("ELENCO INTERRUTTORI DIFFERENZIALI"), "Deve tornare al provider di default CEI 64-8")

        sharedRegistry.resetToDefaults()
    }

    @Test
    fun `multiple matching providers for single plant are chained into multi-sheet Foglio 2 and Foglio 3`() {
        val isolatedRegistry = DefaultTechnicalAttachmentRegistry()

        val plant = ImpiantoStandard(
            id = "cab-1",
            codIntervento = "CAB",
            nomeCompleto = "Cabina MT/BT"
        )

        val provider1 = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "CAB"
            override fun buildPremessaSummary(impianto: Impianto): String = "Sommario Cabina Trasformatore"
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData =
                TechnicalSheetData(
                    codScheda = "CAB-SHEET-1",
                    oggetto = "TRASFORMATORE MT/BT",
                    titoloBanner = "VERIFICHE TRASFORMATORE IN RESINA",
                    sottotitoloBanner = "CEI 99-2",
                    impiantoLabel = "Cabina",
                    normative = "CEI 99-2",
                    campoAcroFormPrefix = "trafo",
                    columns = listOf(TableColumn("Parametro", 18.4)),
                    rows = listOf(TechnicalRow(cells = listOf(TechnicalCell.Text("Verifica Termometrica")))),
                    footerDocTitle = "Fogli Trafo"
                )
        }

        val provider2 = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "CAB"
            override fun buildPremessaSummary(impianto: Impianto): String? = null
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData =
                TechnicalSheetData(
                    codScheda = "CAB-SHEET-2",
                    oggetto = "PROTEZIONI MT",
                    titoloBanner = "VERIFICHE RELE DI PROTEZIONE MT",
                    sottotitoloBanner = "CEI 0-16",
                    impiantoLabel = "Cabina MT",
                    normative = "CEI 0-16",
                    campoAcroFormPrefix = "rele",
                    columns = listOf(TableColumn("Prova Sgancio", 18.4)),
                    rows = listOf(TechnicalRow(cells = listOf(TechnicalCell.Text("Tempo di scatto 51N")))),
                    footerDocTitle = "Fogli Protezioni MT"
                )
        }

        isolatedRegistry.registerProvider(provider1)
        isolatedRegistry.registerProvider(provider2, priority = false)

        val htmlService = HtmlService(attachmentRegistry = isolatedRegistry)
        val html = htmlService.buildHtml(
            HtmlRenderContext(
                impianto = plant,
                attivita = emptyList(),
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Substation Test"
            )
        )

        assertTrue(html.contains("Sommario Cabina Trasformatore"), "Premessa del primo provider deve essere presente")
        assertTrue(html.contains("VERIFICHE TRASFORMATORE IN RESINA"), "Foglio 2 (Trafo) deve essere presente")
        assertTrue(html.contains("VERIFICHE RELE DI PROTEZIONE MT"), "Foglio 3 (Rele MT) deve essere presente")
    }

    // =========================================================================
    // PART 2: High-Concurrency Multithreaded Stress on TechnicalAttachmentRegistry
    // =========================================================================

    @Test
    fun `multithreaded concurrent batch generation with dynamic registry mutations does not crash or corrupt`() = runBlocking(Dispatchers.Default) {
        val registry = DefaultTechnicalAttachmentRegistry.shared
        registry.resetToDefaults()

        val plants = listOf(
            QuadroBT(
                codIntervento = "Q",
                nomeCompleto = "Quadro BT Generale",
                sigla = "QG",
                listaInterruttori = listOf(InterruttoreBT(id = "i1", nome = "Diff Luce", quantita = 1))
            ),
            ImpiantoEmergenza(
                codIntervento = "EM",
                nomeCompleto = "Soccorritore Emergenza",
                listaLampade = listOf(LampadaEmergenza(id = "l1", modello = "Liner 11W", sigla = "L-01"))
            ),
            RilevazioneAntincendio(
                codIntervento = "RI",
                nomeCompleto = "Centrale Antincendio Notifier",
                quantitaComponenti = mapOf("Sensori" to 20),
                listaDispositivi = listOf(
                    DispositivoRilevazione.RivelatoreLoop(
                        loop = 1,
                        numeroIndirizzo = 1,
                        tipo = TipoRivelatore.OTTICO,
                        descrizione = "Ufficio"
                    )
                )
            ),
            ImpiantoStandard(
                codIntervento = "GE",
                nomeCompleto = "Gruppo Elettrogeno 250kVA"
            )
        )

        val htmlService = HtmlService(attachmentRegistry = registry)
        val successCount = AtomicInteger(0)
        val generatedLengths = ConcurrentHashMap<Int, Int>()

        // 30 worker paralleli su coroutine dispatcher
        val jobs = (1..30).map { threadId ->
            async {
                for (iter in 1..25) {
                    val plant = plants[(threadId + iter) % plants.size]

                    // Dinamicamente inseriamo e togliamo un provider temporaneo
                    val tempProvider = object : TechnicalAttachmentProvider {
                        override fun canHandle(impianto: Impianto): Boolean = (impianto.codIntervento == "GE" && iter % 2 == 0)
                        override fun buildPremessaSummary(impianto: Impianto): String = "Temp GE Summary #$threadId-$iter"
                        override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData? = null
                    }

                    if (threadId % 3 == 0) {
                        registry.registerProvider(tempProvider, priority = (iter % 2 == 0))
                    }

                    // Generazione concorrente HTML
                    val html = htmlService.buildHtml(
                        HtmlRenderContext(
                            impianto = plant,
                            attivita = emptyList(),
                            frequenza = Periodo(TipoPeriodo.M, 6),
                            clienteNome = "Concurrent Client #$threadId",
                            cantiereNome = "Sito #$threadId"
                        )
                    )

                    assertTrue(html.isNotBlank())
                    generatedLengths[threadId * 1000 + iter] = html.length
                    successCount.incrementAndGet()

                    // Interrogazioni concorrenti del registro
                    registry.getProviderFor(plant)
                    registry.getProvidersFor(plant)
                    val all = registry.getAllProviders()
                    assertTrue(all.isNotEmpty())

                    if (threadId % 3 == 0) {
                        registry.unregisterProvider(tempProvider)
                    }
                }
            }
        }

        jobs.awaitAll()

        assertEquals(30 * 25, successCount.get(), "Tutte le 750 generazioni concorrenti devono completarsi senza errori")
        assertEquals(3, registry.size, "Al termine devono rimanere solo i 3 provider standard")
        registry.resetToDefaults()
    }
}
