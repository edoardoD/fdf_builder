package manutenzioni.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import manutenzioni.app.data.Cliente
import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.app.strategy.HtmlToPdfStrategy
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.strategy.PdfBatchGenerator
import java.io.File
import javax.swing.JFileChooser

/** Modalità di visualizzazione dell'area principale */
enum class ViewMode {
    PDF_PREVIEW,
    IMPIANTO_EDITOR
}

/**
 * Stato immutabile dell'interfaccia utente.
 * Ogni cambiamento genera una nuova istanza (unidirectional data flow).
 */
data class ManutenzioniUiState(
    val impianti: List<Impianto> = emptyList(),
    val selectedImpianto: Impianto? = null,
    val frequenzeDisponibili: List<Periodo> = emptyList(),
    val selectedFrequenza: Periodo? = null,
    val clienti: List<Cliente> = emptyList(),
    val selectedCliente: Cliente? = null,
    val pdfFile: File? = null,
    val isLoading: Boolean = false,
    val statusMessage: String = "Seleziona un impianto per iniziare",
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.PDF_PREVIEW,
    /** Numero di copie da generare (>= 1, default = 1) */
    val numberOfCopies: Int = 1,
    /** Progresso batch: "Generazione copia X di N..." (null se non in corso) */
    val batchProgress: String? = null,
    /** Lista dei file generati nell'ultimo batch */
    val generatedFiles: List<File> = emptyList()
)

/**
 * ViewModel che gestisce lo stato dell'applicazione.
 *
 * Utilizza StateFlow (Observer Pattern) per propagare gli aggiornamenti
 * alla UI Compose in modo reattivo.
 */
class ManutenzioniViewModel(
    private val repository: ManutenzioneRepository,
    private val pdfStrategy: PdfBatchGenerator = HtmlToPdfStrategy()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ManutenzioniUiState())
    val uiState: StateFlow<ManutenzioniUiState> = _uiState.asStateFlow()

    init {
        loadImpianti()
        loadClienti()
    }

    /** Carica gli impianti dal repository */
    private fun loadImpianti() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val impianti = repository.caricaImpianti()
                _uiState.update {
                    it.copy(
                        impianti = impianti,
                        isLoading = false,
                        statusMessage = "${impianti.size} impianti caricati"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Errore caricamento: ${e.message}"
                    )
                }
            }
        }
    }

    /** Carica i clienti dal repository */
    private fun loadClienti() {
        scope.launch {
            try {
                val clienti = repository.caricaClienti()
                _uiState.update { it.copy(clienti = clienti) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore caricamento clienti: ${e.message}")
                }
            }
        }
    }

    /** Seleziona un cliente esistente */
    fun selectCliente(cliente: Cliente) {
        _uiState.update {
            it.copy(
                selectedCliente = cliente,
                statusMessage = "Cliente: ${cliente.nome}",
                errorMessage = null
            )
        }
    }

    /** Aggiunge un nuovo cliente e lo seleziona automaticamente */
    fun addCliente(cliente: Cliente) {
        scope.launch {
            try {
                repository.salvaCliente(cliente)
                val clienti = repository.caricaClienti()
                _uiState.update {
                    it.copy(
                        clienti = clienti,
                        selectedCliente = cliente,
                        statusMessage = "✓ Cliente ${cliente.nome} aggiunto e selezionato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore salvataggio cliente: ${e.message}")
                }
            }
        }
    }

    /** Seleziona un impianto e calcola le frequenze disponibili */
    fun selectImpianto(impianto: Impianto) {
        val frequenze = FrequencyFilter.frequenzeDisponibili(impianto.listaAttivita)
        _uiState.update {
            it.copy(
                selectedImpianto = impianto,
                frequenzeDisponibili = frequenze,
                selectedFrequenza = null,
                pdfFile = null,
                statusMessage = "${impianto.nomeCompleto} — seleziona una frequenza",
                errorMessage = null
            )
        }
    }

    /** Seleziona una frequenza e genera automaticamente il PDF */
    fun selectFrequenza(frequenza: Periodo) {
        _uiState.update { it.copy(selectedFrequenza = frequenza) }
    }

    /** Imposta il numero di copie da generare (min 1) */
    fun setNumberOfCopies(n: Int) {
        _uiState.update { it.copy(numberOfCopies = n.coerceIn(1, 99)) }
    }

    /** Seleziona una cartella di output in modo cross-platform (macOS: FileDialog, altri: JFileChooser) */
    private fun selectOutputDirectoryCompatibile(): File? {
        return try {
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("mac")) {
                System.setProperty("apple.awt.fileDialogForDirectories", "true")
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Seleziona o crea una cartella", java.awt.FileDialog.LOAD)
                dialog.isVisible = true
                val selectedDir = dialog.directory
                val selectedFile = dialog.file
                System.setProperty("apple.awt.fileDialogForDirectories", "false")
                if (selectedDir == null || selectedFile == null) return null
                val dir = File(selectedDir, selectedFile)
                if (!dir.exists()) dir.mkdirs()
                if (dir.isDirectory) dir else null
            } else {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Seleziona o crea la cartella di destinazione"
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    approveButtonText = "Salva qui"
                }
                val result = chooser.showOpenDialog(null)
                if (result != JFileChooser.APPROVE_OPTION) return null
                val dir = chooser.selectedFile
                if (!dir.exists()) dir.mkdirs()
                if (dir.isDirectory) dir else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Genera il PDF con la strategia corrente — usa sempre il flusso batch */
    fun generatePdf() {
        val state = _uiState.value
        val impianto = state.selectedImpianto ?: return
        val frequenza = state.selectedFrequenza ?: return
        val copies = state.numberOfCopies

        val outputDir = selectOutputDirectoryCompatibile() ?: return

        scope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    batchProgress = "Avvio generazione...",
                    statusMessage = "Generazione di $copies ${if (copies == 1) "copia" else "copie"} in corso...",
                    generatedFiles = emptyList()
                )
            }
            try {
                val batchResult = withContext(Dispatchers.IO) {
                    pdfStrategy.generateBatch(
                        impianto = impianto,
                        frequenza = frequenza,
                        outputDir = outputDir,
                        copies = copies,
                        clienteNome = state.selectedCliente?.nome,
                        onProgress = { current, total ->
                            _uiState.update {
                                it.copy(
                                    batchProgress = "Generazione copia $current di $total...",
                                    statusMessage = "Generazione copia $current di $total..."
                                )
                            }
                        }
                    )
                }

                val statusMsg = if (batchResult.isFullSuccess) {
                    "✓ ${batchResult.successCount} ${if (batchResult.successCount == 1) "PDF generato" else "PDF generati"} in ${outputDir.name}/"
                } else {
                    "⚠ ${batchResult.successCount}/${batchResult.totalRequested} PDF generati. ${batchResult.failureCount} errori."
                }

                val errorMsg = if (batchResult.errors.isNotEmpty()) {
                    "Errori: " + batchResult.errors.entries.joinToString("; ") { "Copia ${it.key}: ${it.value}" }
                } else null

                _uiState.update {
                    it.copy(
                        pdfFile = batchResult.generatedFiles.firstOrNull(),
                        generatedFiles = batchResult.generatedFiles,
                        isLoading = false,
                        batchProgress = null,
                        statusMessage = statusMsg,
                        errorMessage = errorMsg,
                        viewMode = ViewMode.PDF_PREVIEW
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        batchProgress = null,
                        errorMessage = "Errore generazione PDF: ${e.message}"
                    )
                }
            }
        }
    }

    /** Cambia la modalità di visualizzazione */
    fun setViewMode(mode: ViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
    }

    /** Apre il PDF nel viewer di sistema */
    fun openPdfInSystem() {
        val file = _uiState.value.pdfFile ?: return
        try {
            val desktop = java.awt.Desktop.getDesktop()
            desktop.open(file)
        } catch (e: Exception) {
            _uiState.update {
                it.copy(errorMessage = "Impossibile aprire il PDF: ${e.message}")
            }
        }
    }

    /** Salva un impianto modificato */
    fun saveImpianto(impianto: Impianto) {
        scope.launch {
            try {
                repository.salvaImpianto(impianto)
                loadImpianti()
                _uiState.update {
                    it.copy(
                        selectedImpianto = impianto,
                        statusMessage = "✓ Impianto ${impianto.codIntervento} salvato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore salvataggio: ${e.message}")
                }
            }
        }
    }

    /** Ricarica i dati dal repository */
    fun refresh() {
        loadImpianti()
    }
}