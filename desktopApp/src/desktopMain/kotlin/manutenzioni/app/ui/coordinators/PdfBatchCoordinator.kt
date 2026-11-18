package manutenzioni.app.ui.coordinators

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import manutenzioni.app.service.Pdf
import manutenzioni.app.service.RiepilogoInterventoHtmlRenderer
import manutenzioni.app.service.attachments.AntincendioAttachmentProvider
import manutenzioni.app.service.attachments.EmergenzaAttachmentProvider
import manutenzioni.app.service.attachments.QuadroBtAttachmentProvider
import manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.app.ui.platform.DirectoryPicker
import manutenzioni.app.ui.platform.SystemFileLauncher
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CalendarioScadenzeCalculator
import manutenzioni.domain.strategy.PdfBatchGenerator
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class PdfBatchCoordinator(
    private val repository: ManutenzioneRepository,
    private val pdfStrategy: PdfBatchGenerator,
    private val uiState: MutableStateFlow<ManutenzioniUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val directoryPicker: DirectoryPicker,
    private val fileLauncher: SystemFileLauncher
) {

    fun generatePdf() {
        val state = uiState.value
        val impiantiSelezionatiIds = state.impiantiSelezionati.filter { it.value }.keys
        if (impiantiSelezionatiIds.isEmpty()) {
            uiState.update { it.copy(errorMessage = "Nessun impianto selezionato.") }
            return
        }
        val outputDir = directoryPicker.pickDirectory() ?: return
        generatePdfToDirectory(outputDir)
    }

    fun generatePdfToDirectory(outputDir: File) {
        val state = uiState.value
        val impiantiSelezionatiIds = state.impiantiSelezionati.filter { it.value }.keys
        if (impiantiSelezionatiIds.isEmpty()) {
            uiState.update { it.copy(errorMessage = "Nessun impianto selezionato.") }
            return
        }

        val cantiere = state.selectedCantiere
        val subfolderName = resolveTemporalSubfolderName(cantiere?.dataIntervento)

        uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                batchProgress = "Avvio generazione...",
                statusMessage = "Generazione PDF in $subfolderName in corso...",
                generatedFiles = emptyList()
            )
        }

        scope.launch {
            val impiantiDaGenerare = state.impiantiDelCantiere.filter { it.id in impiantiSelezionatiIds }

            val targetOutputDir = File(outputDir, subfolderName).apply {
                if (!exists()) mkdirs()
            }

            val allGeneratedFiles = mutableListOf<File>()
            var totalSuccessCount = 0
            val totalErrors = mutableMapOf<String, String>()

            for ((index, impianto) in impiantiDaGenerare.withIndex()) {
                val frequenzeScelte = state.frequenzeSelezionatePerImpianto[impianto.id]
                    ?.takeIf { it.isNotEmpty() }
                    ?.sortedBy { it.inMesi() }
                    ?: listOf(state.frequenzePerImpianto[impianto.id] ?: Periodo(TipoPeriodo.A, 1))

                for (frequenza in frequenzeScelte) {
                    try {
                        val batchResult = withContext(ioDispatcher) {
                            pdfStrategy.generateBatch(
                                impianto = impianto,
                                frequenza = frequenza,
                                outputDir = targetOutputDir,
                                copies = 1,
                                clienteNome = state.selectedCliente?.nome,
                                contextImpianti = state.impiantiDelCantiere,
                                cantiereNome = cantiere?.nome,
                                nomeManutentore = cantiere?.nomeManutentore,
                                firmaManutentore = cantiere?.firmaManutentore,
                                onProgress = { current, total ->
                                    uiState.update {
                                        it.copy(
                                            batchProgress = "Impianto ${index + 1}/${impiantiDaGenerare.size} (${frequenza.codiceCartiglio()}): PDF $current di $total...",
                                            statusMessage = "Generazione ${impianto.codIntervento} (${frequenza.label()})..."
                                        )
                                    }
                                },
                                dataInizioContratto = cantiere?.dataInizioContratto,
                                dataIntervento = cantiere?.dataIntervento,
                                dataInterventoPrecedente = cantiere?.dataInterventoPrecedente
                            )
                        }
                        allGeneratedFiles.addAll(batchResult.generatedFiles)
                        totalSuccessCount += batchResult.successCount
                        if (!batchResult.isFullSuccess) {
                            batchResult.errors.forEach { (copy, error) ->
                                totalErrors["${impianto.id}_${frequenza.codiceCartiglio()} (copia $copy)"] = error
                            }
                        }
                    } catch (e: Exception) {
                        totalErrors["${impianto.id}_${frequenza.codiceCartiglio()}"] = e.message ?: "Errore sconosciuto"
                    }
                }
            }

            if (totalSuccessCount > 0 && cantiere != null) {
                val nowTimestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                val sessione = SessioneVerifica(
                    cantiereId = cantiere.id,
                    clienteId = cantiere.clienteId,
                    meseAnno = subfolderName,
                    dataInizioContratto = cantiere.dataInizioContratto,
                    dataIntervento = cantiere.dataIntervento,
                    dataInterventoPrecedente = cantiere.dataInterventoPrecedente,
                    nomeManutentore = cantiere.nomeManutentore,
                    dataCreazione = nowTimestamp,
                    cartellaOutput = targetOutputDir.absolutePath,
                    elencoFilePdf = allGeneratedFiles.map { it.absolutePath },
                    riepilogoImpianti = impiantiDaGenerare.map { imp ->
                        if (imp is QuadroBT && imp.sigla.isNotBlank()) "${imp.codIntervento}-${imp.sigla}" else imp.codIntervento
                    }
                )
                withContext(ioDispatcher) {
                    try {
                        repository.salvaSessioneVerifica(sessione)
                        val sessioniAggiornate = repository.getSessioniPerCantiere(cantiere.id)
                        uiState.update { it.copy(sessioniVerificaCantiere = sessioniAggiornate) }
                    } catch (e: Exception) {
                        println("Warning: salvataggio sessione verifica fallito: ${e.message}")
                    }
                }
            }

            if (totalSuccessCount > 0 && totalErrors.isEmpty() && cantiere != null) {
                val currentData = cantiere.dataIntervento
                if (!currentData.isNullOrBlank()) {
                    val promoted = cantiere.copy(dataInterventoPrecedente = currentData)
                    withContext(ioDispatcher) {
                        try {
                            repository.salvaCantiere(promoted)
                        } catch (e: Exception) {
                            println("Warning: salvataggio promozione data cantiere fallito: ${e.message}")
                        }
                    }
                    uiState.update { s ->
                        s.copy(
                            selectedCantiere = promoted,
                            cantieriDisponibili = s.cantieriDisponibili.map { if (it.id == promoted.id) promoted else it }
                        )
                    }
                }
            }

            val statusMsg = "✓ $totalSuccessCount PDF generati in $subfolderName/. ${totalErrors.size} errori."
            val errorMsg = if (totalErrors.isNotEmpty()) {
                "Errori: " + totalErrors.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            } else null

            uiState.update {
                it.copy(
                    pdfFile = allGeneratedFiles.firstOrNull(),
                    generatedFiles = allGeneratedFiles,
                    isLoading = false,
                    batchProgress = null,
                    statusMessage = statusMsg,
                    errorMessage = errorMsg
                )
            }
        }
    }

    fun openPdfInSystem() {
        val file = uiState.value.pdfFile ?: return
        try {
            fileLauncher.open(file)
        } catch (e: Exception) {
            uiState.update {
                it.copy(errorMessage = "Impossibile aprire il PDF: ${e.message}")
            }
        }
    }

    fun openSessionePdfFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            uiState.update { it.copy(errorMessage = "Il file PDF non è più presente nel percorso: $filePath") }
            return
        }
        try {
            fileLauncher.open(file)
            uiState.update { it.copy(pdfFile = file, statusMessage = "Apertura ${file.name}...") }
        } catch (e: Exception) {
            uiState.update { it.copy(errorMessage = "Impossibile aprire il file: ${e.message}") }
        }
    }

    fun exportStandaloneTechnicalSheet(impianto: Impianto) {
        scope.launch(ioDispatcher) {
            try {
                val providers = listOf(
                    QuadroBtAttachmentProvider(),
                    EmergenzaAttachmentProvider(),
                    AntincendioAttachmentProvider()
                )
                val provider = providers.firstOrNull { it.canHandle(impianto) }
                if (provider == null) {
                    uiState.update { it.copy(errorMessage = "Nessun componente registrato per generare il Foglio 2") }
                    return@launch
                }
                val stateSnap = uiState.value
                val frequenza = stateSnap.frequenzePerImpianto[impianto.id] ?: Periodo(TipoPeriodo.M, 6)
                val clienteNome = stateSnap.selectedCliente?.nome
                val cantiere = stateSnap.selectedCantiere
                val sheetsData = provider.buildSheetsData(impianto, frequenza, clienteNome)
                if (sheetsData.isEmpty()) {
                    uiState.update { it.copy(errorMessage = "Nessun dato registrato per il Foglio 2") }
                    return@launch
                }
                val renderer = TechnicalSheetHtmlRenderer()
                val html = renderer.renderStandaloneHtml(
                    sheets = sheetsData,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome,
                    dataIntervento = cantiere?.dataIntervento,
                    nomeManutentore = cantiere?.nomeManutentore,
                    dataInizioContratto = cantiere?.dataInizioContratto
                )
                val tempHtml = File.createTempFile("allegato_${impianto.codIntervento}_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val outputPdf = File.createTempFile("Foglio2_${impianto.codIntervento}_${sheetsData.first().oggetto}_", ".pdf")
                Pdf().buildPdf(tempHtml.absolutePath, outputPdf.absolutePath)
                tempHtml.delete()

                try {
                    fileLauncher.open(outputPdf)
                } catch (_: Exception) {}
                uiState.update { it.copy(pdfFile = outputPdf, statusMessage = "Foglio 2 generato: ${outputPdf.name}") }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore esportazione Foglio 2: ${e.message}") }
            }
        }
    }

    fun exportCantiereInterruttoriPdf(outputPdfFile: File? = null) {
        scope.launch(ioDispatcher) {
            try {
                val stateSnap = uiState.value
                val quadri = stateSnap.impiantiDelCantiere.filterIsInstance<QuadroBT>()
                val provider = QuadroBtAttachmentProvider()
                val frequenza = Periodo(TipoPeriodo.A, 1)
                val clienteNome = stateSnap.selectedCliente?.nome
                val cantiere = stateSnap.selectedCantiere
                val sheetData = provider.buildCantiereSheetData(
                    quadri = quadri,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome
                )
                if (sheetData == null) {
                    uiState.update { it.copy(errorMessage = "Nessun interruttore registrato nei quadri del cantiere") }
                    return@launch
                }
                val renderer = TechnicalSheetHtmlRenderer()
                val html = renderer.renderStandaloneHtml(
                    data = sheetData,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome,
                    dataIntervento = cantiere?.dataIntervento,
                    nomeManutentore = cantiere?.nomeManutentore,
                    dataInizioContratto = cantiere?.dataInizioContratto
                )
                val tempHtml = File.createTempFile("elenco_interruttori_cantiere_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val safeCantiere = cantiere?.nome?.trim()?.replace(" ", "_")?.replace("/", "-") ?: "Cantiere"
                val outputPdf = outputPdfFile ?: File.createTempFile("Elenco_Interruttori_${safeCantiere}_", ".pdf")
                Pdf().buildPdf(tempHtml.absolutePath, outputPdf.absolutePath)
                tempHtml.delete()

                if (outputPdfFile == null) {
                    try {
                        fileLauncher.open(outputPdf)
                    } catch (_: Exception) {}
                }
                uiState.update {
                    it.copy(
                        pdfFile = outputPdf,
                        statusMessage = "✓ Elenco Interruttori Cantiere generato: ${outputPdf.name}",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore esportazione Elenco Interruttori: ${e.message}") }
            }
        }
    }

    fun exportRiepilogoGeneralePdf(outputPdfFile: File? = null) {
        val stateSnap = uiState.value
        val cantiere = stateSnap.selectedCantiere
        if (cantiere == null) {
            uiState.update { it.copy(errorMessage = "Seleziona un cantiere prima di generare il Foglio di Riepilogo Generale") }
            return
        }
        scope.launch(ioDispatcher) {
            try {
                val scadenze = CalendarioScadenzeCalculator.calcolaScadenze(
                    cantiere = cantiere,
                    impianti = stateSnap.impiantiDelCantiere,
                    mesiOrizzonte = stateSnap.orizzonteScadenzeMesi
                )
                val renderer = RiepilogoInterventoHtmlRenderer()
                val html = renderer.buildRiepilogoHtml(
                    cliente = stateSnap.selectedCliente,
                    cantiere = cantiere,
                    impianti = stateSnap.impiantiDelCantiere,
                    frequenzeSelezionatePerImpianto = stateSnap.frequenzeSelezionatePerImpianto,
                    frequenzePerImpianto = stateSnap.frequenzePerImpianto,
                    scadenze = scadenze
                )
                val tempHtml = File.createTempFile("riepilogo_intervento_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val subfolder = resolveTemporalSubfolderName(cantiere.dataIntervento)
                val safeCantiere = cantiere.nome.trim().replace(" ", "_").replace("/", "-").ifBlank { "Cantiere" }
                val targetPdf = outputPdfFile ?: File.createTempFile("RIEPILOGO_INTERVENTO_${safeCantiere}_${subfolder}_", ".pdf")
                Pdf().buildPdf(tempHtml.absolutePath, targetPdf.absolutePath)
                tempHtml.delete()

                if (outputPdfFile == null) {
                    try {
                        fileLauncher.open(targetPdf)
                    } catch (_: Exception) {}
                }
                uiState.update {
                    it.copy(
                        pdfFile = targetPdf,
                        statusMessage = "✓ Foglio di Riepilogo Generale generato: ${targetPdf.name}",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore generazione Foglio di Riepilogo Generale: ${e.message}") }
            }
        }
    }

    fun exportRiepilogoDaCalendarioGlobale(
        cantiereScadenza: CantiereScadenzaInMese,
        outputPdfFile: File? = null
    ) {
        val stateSnap = uiState.value
        val cantiere = cantiereScadenza.cantiere
        val cliente = cantiereScadenza.cliente ?: stateSnap.clienti.find { it.id == cantiere.clienteId }
        scope.launch(ioDispatcher) {
            try {
                val impiantiCantiere = repository.getImpiantiForCantiere(cantiere.id)
                val scadenze = CalendarioScadenzeCalculator.calcolaScadenze(
                    cantiere = cantiere,
                    impianti = impiantiCantiere,
                    mesiOrizzonte = stateSnap.orizzonteScadenzeMesi
                )
                val freqMulti = cantiereScadenza.scadenzeImpianti.associate { it.impiantoId to it.frequenzeIncluse.toSet() }
                val freqSingle = cantiereScadenza.scadenzeImpianti.associate { it.impiantoId to it.frequenzaMassima }
                val renderer = RiepilogoInterventoHtmlRenderer()
                val html = renderer.buildRiepilogoHtml(
                    cliente = cliente,
                    cantiere = cantiere,
                    impianti = impiantiCantiere,
                    frequenzeSelezionatePerImpianto = freqMulti,
                    frequenzePerImpianto = freqSingle,
                    scadenze = scadenze
                )
                val tempHtml = File.createTempFile("riepilogo_globale_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val subfolder = resolveTemporalSubfolderName(cantiereScadenza.dataPrevista)
                val safeCantiere = cantiere.nome.trim().replace(" ", "_").replace("/", "-").ifBlank { "Cantiere" }
                val targetPdf = outputPdfFile ?: File.createTempFile("RIEPILOGO_INTERVENTO_${safeCantiere}_${subfolder}_", ".pdf")
                Pdf().buildPdf(tempHtml.absolutePath, targetPdf.absolutePath)
                tempHtml.delete()

                if (outputPdfFile == null) {
                    try {
                        fileLauncher.open(targetPdf)
                    } catch (_: Exception) {}
                }
                uiState.update {
                    it.copy(
                        pdfFile = targetPdf,
                        statusMessage = "✓ Riepilogo PDF generato per '${cantiere.nome}': ${targetPdf.name}",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore generazione Riepilogo PDF: ${e.message}") }
            }
        }
    }

    companion object {
        fun resolveTemporalSubfolderName(dataIntervento: String?): String {
            val raw = dataIntervento?.trim().orEmpty()
            if (raw.isNotEmpty()) {
                val dmyRegex = Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$""")
                dmyRegex.matchEntire(raw)?.let { m ->
                    val month = m.groupValues[2].padStart(2, '0')
                    val year = m.groupValues[3]
                    return "${year}_${month}"
                }
                val ymdRegex = Regex("""^(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})$""")
                ymdRegex.matchEntire(raw)?.let { m ->
                    val year = m.groupValues[1]
                    val month = m.groupValues[2].padStart(2, '0')
                    return "${year}_${month}"
                }
                val myRegex = Regex("""^(\d{1,2})[/\-.](\d{4})$""")
                myRegex.matchEntire(raw)?.let { m ->
                    val month = m.groupValues[1].padStart(2, '0')
                    val year = m.groupValues[2]
                    return "${year}_${month}"
                }
                val ymRegex = Regex("""^(\d{4})[_\-.](\d{1,2})$""")
                ymRegex.matchEntire(raw)?.let { m ->
                    val year = m.groupValues[1]
                    val month = m.groupValues[2].padStart(2, '0')
                    return "${year}_${month}"
                }
            }
            val now = LocalDate.now()
            return "%04d_%02d".format(now.year, now.monthValue)
        }
    }
}
