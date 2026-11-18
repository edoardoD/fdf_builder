package manutenzioni.e2e

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.app.service.HtmlService
import manutenzioni.app.service.attachments.DefaultTechnicalAttachmentRegistry
import manutenzioni.app.strategy.HtmlToPdfStrategy
import manutenzioni.app.ui.ManutenzioniViewModel
import manutenzioni.app.ui.platform.DirectoryPicker
import manutenzioni.app.ui.platform.SystemFileLauncher
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.service.attachments.*
import manutenzioni.domain.service.rules.CrossPlantRule
import java.io.File
import java.nio.file.Files
import kotlin.test.*

/**
 * Suite End-to-End (Tier 4) per verificare l'integrazione completa tra
 * Domain Layer, Persistence (JsonDatabaseStorage + DAO), Service Layer (HtmlService + Allegati OCP),
 * Strategy Layer (HtmlToPdfStrategy) e Presentation Layer (ManutenzioniViewModel + Coordinators).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndWorkflowTest {

    private val lenientJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun `E2E Scenario 1 - Flusso completo Cantiere Multi-Impianto dalla creazione alla generazione Batch PDF`() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val tempDbFile = Files.createTempFile("e2e_db_", ".json").toFile().apply { deleteOnExit() }
        val outputDir = Files.createTempDirectory("e2e_pdf_out_").toFile()

        try {
            val storage = manutenzioni.app.data.storage.JsonDatabaseStorage(
                dbFile = tempDbFile,
                ioDispatcher = testDispatcher
            )
            val repo = JsonManutenzioneRepository(storage)

            // 1. Seed template globali (Q, EM, RI, CAB)
            val templateQuadro = QuadroBT(
                id = "tpl-q",
                codIntervento = "Q",
                nomeCompleto = "Quadri Elettrici BT",
                premessa = "Premessa Quadri BT",
                listaAttivita = listOf(
                    Attivita(1, "Prova", "Prova tasto test differenziali", Periodo(TipoPeriodo.M, 6)),
                    Attivita(2, "Controllo", "Verifica serraggio morsetti", Periodo(TipoPeriodo.A, 1))
                ),
                cantiereId = null
            )
            val templateEmergenza = ImpiantoEmergenza(
                id = "tpl-em",
                codIntervento = "EM",
                nomeCompleto = "Illuminazione di Emergenza",
                premessa = "Premessa UNI 11222",
                listaAttivita = listOf(
                    Attivita(1, "Verifica", "Verifica funzionale lampade", Periodo(TipoPeriodo.M, 6)),
                    Attivita(2, "Prova", "Prova autonomia annuale", Periodo(TipoPeriodo.A, 1))
                ),
                cantiereId = null
            )
            val templateRi = RilevazioneAntincendio(
                id = "tpl-ri",
                codIntervento = "RI",
                nomeCompleto = "Rilevazione Incendi",
                premessa = "Premessa UNI 11224",
                listaAttivita = listOf(
                    Attivita(1, "Controllo", "Controllo centrale", Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null),
                    Attivita(2, "Quadro elettrico", "Rivelatori calore su quadro", Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "Q"),
                    Attivita(3, "Cabina MT/BT", "Rivelatori fumo cabina", Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "CAB")
                ),
                cantiereId = null
            )

            repo.salvaImpianto(templateQuadro)
            repo.salvaImpianto(templateEmergenza)
            repo.salvaImpianto(templateRi)

            val fakeDirectoryPicker = object : DirectoryPicker {
                override fun pickDirectory(title: String): File = outputDir
                override fun pickFile(title: String, allowedExtensions: List<String>): File? = null
            }
            val openedFiles = mutableListOf<File>()
            val fakeLauncher = object : SystemFileLauncher {
                override fun open(file: File) {
                    openedFiles.add(file)
                }
            }

            val viewModel = ManutenzioniViewModel(
                repository = repo,
                pdfStrategy = HtmlToPdfStrategy(),
                defaultDispatcher = testDispatcher,
                ioDispatcher = testDispatcher,
                directoryPicker = fakeDirectoryPicker,
                fileLauncher = fakeLauncher
            )
            advanceUntilIdle()

            // 2. Crea Cliente e Cantiere via ViewModel
            val cliente = Cliente(id = "cli-san-luca", nome = "Ospedale San Luca")
            viewModel.addCliente(cliente)
            advanceUntilIdle()
            viewModel.selectCliente(cliente)
            advanceUntilIdle()

            val cantiere = Cantiere(id = "cant-polo-chirurgico", clienteId = cliente.id, nome = "Polo Chirurgico")
            viewModel.addCantiere(cantiere)
            advanceUntilIdle()
            viewModel.selectCantiere(cantiere)
            advanceUntilIdle()

            // 3. Aggiungi istanze impianti al cantiere (QuadroBT con differenziale, Emergenza con lampada, RI)
            val quadroCantiere = QuadroBT(
                id = "q-cantiere-1",
                codIntervento = "Q",
                nomeCompleto = "Quadro Generale QGBT",
                premessa = templateQuadro.premessa,
                listaAttivita = templateQuadro.listaAttivita,
                cantiereId = cantiere.id,
                sigla = "QGBT",
                listaInterruttori = listOf(
                    InterruttoreBT(
                        id = "int-1",
                        nome = "QF1 - ABB DS201 C16 AC30",
                        quantita = 2,
                        caratteristicheTecniche = mapOf(
                            "Corrente nominale (In)" to "16A",
                            "Sensibilità differenziale (Idn)" to "30mA",
                            "Tipo differenziale" to "AC"
                        )
                    )
                )
            )
            val emergenzaCantiere = ImpiantoEmergenza(
                id = "em-cantiere-1",
                codIntervento = "EM",
                nomeCompleto = "Illuminazione Emergenza Piano 1",
                premessa = templateEmergenza.premessa,
                listaAttivita = templateEmergenza.listaAttivita,
                cantiereId = cantiere.id,
                listaLampade = listOf(
                    LampadaEmergenza(
                        id = "L1",
                        sigla = "EM-01",
                        posizione = "Corridoio Est",
                        modello = "OVA 11W",
                        autonomia = "1h"
                    )
                )
            )
            val riCantiere = RilevazioneAntincendio(
                id = "ri-cantiere-1",
                codIntervento = "RI",
                nomeCompleto = "Rilevazione Incendi Polo",
                premessa = templateRi.premessa,
                listaAttivita = templateRi.listaAttivita,
                cantiereId = cantiere.id
            )

            repo.salvaImpianto(quadroCantiere)
            repo.salvaImpianto(emergenzaCantiere)
            repo.salvaImpianto(riCantiere)

            // Ricarica cantiere per sincronizzare lo stato
            viewModel.selectCantiere(cantiere)
            advanceUntilIdle()

            val impiantiNelCantiere = viewModel.uiState.value.impiantiDelCantiere
            assertEquals(3, impiantiNelCantiere.size)

            // 4. Verifica Cross-Plant Discovery per RI: Q è presente nel cantiere, CAB non è presente
            val riAttivita = CrossPlantAttivitaResolver.resolveAttivita(
                impianto = riCantiere,
                impiantiNelCantiere = impiantiNelCantiere,
                frequenza = Periodo(TipoPeriodo.A, 1)
            )
            assertEquals(2, riAttivita.size, "Deve includere l'attività base + l'attività per Q, escludendo CAB")
            assertTrue(riAttivita.any { it.targetImpiantoCod == "Q" })
            assertFalse(riAttivita.any { it.targetImpiantoCod == "CAB" })

            // 5. Genera HTML + PDF per ciascun impianto e verifica esistenza su disco
            val strategy = HtmlToPdfStrategy()
            for (imp in impiantiNelCantiere) {
                val res = strategy.generateBatch(
                    impianto = imp,
                    frequenza = Periodo(TipoPeriodo.A, 1),
                    outputDir = outputDir,
                    copies = 1,
                    clienteNome = cliente.nome,
                    contextImpianti = impiantiNelCantiere,
                    cantiereNome = cantiere.nome
                )
                assertTrue(res.isFullSuccess, "Generazione PDF per ${imp.codIntervento} deve riuscire: ${res.errors}")
                assertEquals(1, res.generatedFiles.size)
                assertTrue(res.generatedFiles.first().exists() && res.generatedFiles.first().length() > 0)
            }
        } finally {
            outputDir.deleteRecursively()
            tempDbFile.delete()
        }
    }

    @Test
    fun `E2E Scenario 2 - Retrocompatibilita JSON con chiavi sconosciute e campi opzionali omessi`() {
        val legacyJsonPayload = """
            {
                "type": "QuadroBT",
                "id": "legacy-quadro-01",
                "codIntervento": "Q",
                "nomeCompleto": "Quadro Legacy v2",
                "listaAttivita": [
                    {
                        "nAttivita": 1,
                        "tipoAttivita": "Controllo",
                        "descrizione": "Verifica generale",
                        "frequenza": { "tipo": "M", "valore": 6 },
                        "legacyExtraField": "ignored"
                    }
                ],
                "obsoletePropertyFromOldVersion": 999
            }
        """.trimIndent()

        val decoded = lenientJson.decodeFromString<Impianto>(legacyJsonPayload)
        assertTrue(decoded is QuadroBT)
        assertEquals("legacy-quadro-01", decoded.id)
        assertEquals(1, decoded.quantita)
        assertTrue(decoded.isQuadro)
        assertEquals(1, decoded.listaAttivita.size)
    }

    @Test
    fun `E2E Scenario 3 - Estensibilita OCP combinata CrossPlantRule e TechnicalAttachmentProvider`() {
        // 1. Registra un provider di allegato custom senza modificare HtmlService
        val customRegistry = DefaultTechnicalAttachmentRegistry(emptyList())
        val customProvider = object : TechnicalAttachmentProvider {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "FTV"
            override fun buildPremessaSummary(impianto: Impianto): String = "SOMMARIO INVERTER FTV CUSTOM"
            override fun buildSheetData(impianto: Impianto, frequenza: Periodo, clienteNome: String?): TechnicalSheetData {
                return TechnicalSheetData(
                    codScheda = "ALL-FTV-01",
                    oggetto = "Impianto Fotovoltaico",
                    titoloBanner = "REGISTRO STRINGHE FOTOVOLTAICHE",
                    sottotitoloBanner = "Verifica Tensioni a Vuoto",
                    impiantoLabel = impianto.nomeCompleto,
                    normative = "CEI 82-25",
                    campoAcroFormPrefix = "ftv",
                    columns = listOf(
                        TableColumn(title = "STRINGA", widthCm = 9.2),
                        TableColumn(title = "VOC", widthCm = 9.2)
                    ),
                    rows = listOf(
                        TechnicalRow(
                            cells = listOf(
                                TechnicalCell.Text("S1", align = CellTextAlign.LEFT),
                                TechnicalCell.TextInput(fieldName = "ftv_voc_1", label = "Voc S1")
                            )
                        )
                    ),
                    footerDocTitle = "Allegato Tecnico Fotovoltaico"
                )
            }
        }
        customRegistry.registerProvider(customProvider)

        val ftvPlant = ImpiantoStandard(
            id = "ftv-1",
            codIntervento = "FTV",
            nomeCompleto = "Impianto Fotovoltaico 100kWp",
            premessa = "Verifica FTV",
            listaAttivita = listOf(Attivita(1, "Misura", "Misura isolamento stringhe", Periodo(TipoPeriodo.M, 6)))
        )

        val htmlService = HtmlService(attachmentRegistry = customRegistry)
        val html = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = ftvPlant,
                attivita = ftvPlant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = "Solar Corp"
            )
        )

        assertTrue(html.contains("SOMMARIO INVERTER FTV CUSTOM"))
        assertTrue(html.contains("REGISTRO STRINGHE FOTOVOLTAICHE"))
        assertTrue(html.contains("ftv_voc_1"))

        // 2. Verifica una CrossPlantRule custom isolata registrata tramite CrossPlantAttivitaResolver
        val customRule = object : CrossPlantRule {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "FTV"

            override fun resolveAttivita(
                impianto: Impianto,
                impiantiNelCantiere: List<Impianto>,
                frequenza: Periodo
            ): List<Attivita> {
                val hasCabina = impiantiNelCantiere.any { it.codIntervento == "CAB" }
                return if (hasCabina) FrequencyFilter.filterByFrequenza(impianto.listaAttivita, frequenza) else emptyList()
            }

            override fun resolveFrequenze(impianto: Impianto, impiantiNelCantiere: List<Impianto>): List<Periodo> {
                val hasCabina = impiantiNelCantiere.any { it.codIntervento == "CAB" }
                return if (hasCabina) FrequencyFilter.frequenzeDisponibili(impianto.listaAttivita) else emptyList()
            }
        }
        CrossPlantAttivitaResolver.registerRule(customRule)
        try {
            val resolvedWithoutCab = CrossPlantAttivitaResolver.resolveAttivita(
                impianto = ftvPlant,
                impiantiNelCantiere = listOf(ftvPlant),
                frequenza = Periodo(TipoPeriodo.M, 6)
            )
            assertTrue(resolvedWithoutCab.isEmpty())

            val cabPlant = ImpiantoStandard(id = "cab-1", codIntervento = "CAB", nomeCompleto = "Cabina MT/BT")
            val resolvedWithCab = CrossPlantAttivitaResolver.resolveAttivita(
                impianto = ftvPlant,
                impiantiNelCantiere = listOf(ftvPlant, cabPlant),
                frequenza = Periodo(TipoPeriodo.M, 6)
            )
            assertEquals(1, resolvedWithCab.size)
        } finally {
            CrossPlantAttivitaResolver.resetToDefaults()
        }
    }
}
