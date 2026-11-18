package manutenzioni.app.service.attachments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import manutenzioni.app.service.HtmlService
import manutenzioni.domain.model.*
import manutenzioni.domain.service.attachments.*
import kotlin.test.*

class TechnicalAttachmentRegistryTest {

    private val fixtureQuadro = QuadroBT(
        codIntervento = "Q",
        nomeCompleto = "Quadri Elettrici BT",
        sigla = "QG1",
        listaInterruttori = listOf(
            InterruttoreBT(id = "i1", nome = "Differenziale 1", quantita = 1)
        )
    )

    private val fixtureEmergenza = ImpiantoEmergenza(
        codIntervento = "EM",
        nomeCompleto = "Illuminazione di Emergenza",
        listaLampade = listOf(
            LampadaEmergenza(id = "l1", modello = "Beghelli 24W", sigla = "EM-01")
        )
    )

    private val fixtureAntincendio = RilevazioneAntincendio(
        codIntervento = "RI",
        nomeCompleto = "Rivelazione Incendi",
        quantitaComponenti = mapOf("Ottico di fumo" to 10),
        listaDispositivi = listOf(
            DispositivoRilevazione.RivelatoreLoop(
                loop = 1,
                numeroIndirizzo = 1,
                tipo = TipoRivelatore.OTTICO,
                descrizione = "Rilevatore fumo"
            )
        )
    )

    @Test
    fun `DefaultTechnicalAttachmentRegistry pre-registra i tre provider istituzionali`() {
        val registry = DefaultTechnicalAttachmentRegistry()
        assertEquals(3, registry.size)
        assertFalse(registry.isEmpty)

        assertNotNull(registry.getProviderFor(fixtureQuadro), "Deve gestire QuadroBT")
        assertNotNull(registry.getProviderFor(fixtureEmergenza), "Deve gestire ImpiantoEmergenza")
        assertNotNull(registry.getProviderFor(fixtureAntincendio), "Deve gestire RilevazioneAntincendio")

        val quadroProviders = registry.getProvidersFor(fixtureQuadro)
        assertEquals(1, quadroProviders.size)
        assertTrue(quadroProviders.first() is QuadroBtAttachmentProvider)
    }

    @Test
    fun `registerProvider con priority inserisce il provider in testa e consente override`() {
        val registry = DefaultTechnicalAttachmentRegistry()

        val customQuadroProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto is QuadroBT
            override fun buildPremessaSummary(impianto: Impianto): String = "CUSTOM_SUMMARY_PREMESSA"
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData? = null
        }

        registry.registerProvider(customQuadroProvider, priority = true)
        assertEquals(4, registry.size)

        val activeProvider = registry.getProviderFor(fixtureQuadro)
        assertSame(customQuadroProvider, activeProvider, "Il provider prioritario deve essere risolto per primo")
        assertEquals("CUSTOM_SUMMARY_PREMESSA", activeProvider?.buildPremessaSummary(fixtureQuadro))
    }

    @Test
    fun `registerProvider senza priority appende in coda`() {
        val registry = DefaultTechnicalAttachmentRegistry()

        val secondaryProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto is QuadroBT
            override fun buildPremessaSummary(impianto: Impianto): String = "SECONDARY_SUMMARY"
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData? = null
        }

        registry.registerProvider(secondaryProvider, priority = false)
        assertEquals(4, registry.size)

        val activeProvider = registry.getProviderFor(fixtureQuadro)
        assertTrue(activeProvider is QuadroBtAttachmentProvider, "Il provider in coda non deve sovrascrivere quello di default")

        val allQuadroProviders = registry.getProvidersFor(fixtureQuadro)
        assertEquals(2, allQuadroProviders.size)
        assertSame(secondaryProvider, allQuadroProviders.last())
    }

    @Test
    fun `unregisterProvider rimuove il provider correttamente`() {
        val registry = DefaultTechnicalAttachmentRegistry()
        val initialSize = registry.size

        val tempProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = true
            override fun buildPremessaSummary(impianto: Impianto): String? = null
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData? = null
        }

        registry.registerProvider(tempProvider)
        assertEquals(initialSize + 1, registry.size)

        val removed = registry.unregisterProvider(tempProvider)
        assertTrue(removed, "unregisterProvider deve restituire true")
        assertEquals(initialSize, registry.size)

        val removedAgain = registry.unregisterProvider(tempProvider)
        assertFalse(removedAgain, "Rimuovere due volte deve restituire false")
    }

    @Test
    fun `resetToDefaults ripristina esattamente i tre provider iniziali`() {
        val registry = DefaultTechnicalAttachmentRegistry()
        registry.clear()
        assertEquals(0, registry.size)
        assertTrue(registry.isEmpty)

        registry.resetToDefaults()
        assertEquals(3, registry.size)
        assertFalse(registry.isEmpty)
        assertNotNull(registry.getProviderFor(fixtureQuadro))
    }

    @Test
    fun `thread-safety sotto accesso concorrente ad alta intensita con coroutine`() = runBlocking {
        val registry = DefaultTechnicalAttachmentRegistry()

        val jobs = (1..30).map { id ->
            async(Dispatchers.Default) {
                for (iter in 1..200) {
                    val p = object : TechnicalAttachmentProvider {
                        override fun canHandle(impianto: Impianto): Boolean = false
                        override fun buildPremessaSummary(impianto: Impianto): String? = null
                        override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData? = null
                    }
                    registry.registerProvider(p, priority = (iter % 2 == 0))
                    val providers = registry.getAllProviders()
                    assertTrue(providers.isNotEmpty())
                    registry.getProviderFor(fixtureQuadro)
                    registry.getProvidersFor(fixtureEmergenza)
                    registry.unregisterProvider(p)
                }
            }
        }

        jobs.awaitAll()
        assertEquals(3, registry.size, "Al termine delle coroutine concorrenti devono rimanere solo i 3 provider standard")
    }

    @Test
    fun `Open-Closed extensibility nuovo provider genera Foglio 2 senza modificare HtmlService`() {
        val registry = DefaultTechnicalAttachmentRegistry()

        val mockGasProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "RIG"

            override fun buildPremessaSummary(impianto: Impianto): String =
                "Centralina Rilevazione Gas metano con 4 sensori remoti."

            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData {
                return TechnicalSheetData(
                    codScheda = impianto.codIntervento,
                    oggetto = "REGISTRO RIVELATORI GAS",
                    titoloBanner = "REGISTRO CONTROLLI IMPIANTO RIVELAZIONE GAS",
                    sottotitoloBanner = "Norma CEI EN 60079-29-2",
                    impiantoLabel = impianto.nomeCompleto,
                    sitoLabel = null,
                    normative = "CEI EN 60079-29-2",
                    campoAcroFormPrefix = "gas",
                    columns = listOf(
                        TableColumn("N°", 1.0),
                        TableColumn("Sensore Gas", 6.0),
                        TableColumn(
                            title = "Test",
                            subColumns = listOf(SubColumn("OK", 2.0), SubColumn("NON OK", 2.0))
                        ),
                        TableColumn("Note", 7.4)
                    ),
                    rows = listOf(
                        TechnicalRow(
                            cells = listOf(
                                TechnicalCell.Text("1"),
                                TechnicalCell.Text("Sensore Metano Centrale Termica"),
                                TechnicalCell.Radio("gas_test_1", "OK", "OK"),
                                TechnicalCell.Radio("gas_test_1", "NON_OK", "NON OK"),
                                TechnicalCell.TextInput("gas_note_1", label = "Note")
                            )
                        )
                    ),
                    footerDocTitle = "Scheda Allegata Rilevazione Gas"
                )
            }
        }

        registry.registerProvider(mockGasProvider)

        val htmlService = HtmlService(attachmentRegistry = registry)

        val impiantoGas = ImpiantoStandard(
            id = "gas-1",
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas Metano"
        )

        val html = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = impiantoGas,
                attivita = emptyList(),
                frequenza = Periodo(TipoPeriodo.A, 1),
                clienteNome = "Centrale Elettrica SpA"
            )
        )

        // Verifica che il sommario sia stato iniettato nella premessa
        assertContains(html, "Centralina Rilevazione Gas metano con 4 sensori remoti.")
        // Verifica che il Foglio 2 sia stato generato con il banner del gas
        assertContains(html, "REGISTRO CONTROLLI IMPIANTO RIVELAZIONE GAS")
        assertContains(html, "Norma CEI EN 60079-29-2")
        // Verifica campi AcroForm del nuovo foglio
        assertContains(html, "gas_test_1")
        assertContains(html, "gas_note_1")
    }
}
