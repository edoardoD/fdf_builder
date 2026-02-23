package manutenzioni.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.app.strategy.HtmlToPdfStrategy
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.strategy.PdfGeneratorStrategy
import java.io.File

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
    val pdfFile: File? = null,
    val isLoading: Boolean = false,
    val statusMessage: String = "Seleziona un impianto per iniziare",
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.PDF_PREVIEW
)

/**
 * ViewModel che gestisce lo stato dell'applicazione.
 *
 * Utilizza StateFlow (Observer Pattern) per propagare gli aggiornamenti
 * alla UI Compose in modo reattivo.
 */
class ManutenzioniViewModel(
    private val repository: ManutenzioneRepository,
    private val pdfStrategy: PdfGeneratorStrategy = HtmlToPdfStrategy()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _uiState = MutableStateFlow(ManutenzioniUiState())
    val uiState: StateFlow<ManutenzioniUiState> = _uiState.asStateFlow()

    init {
        loadImpianti()
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
        generatePdf()
    }

    /** Genera il PDF con la strategia corrente */
    fun generatePdf() {
        val state = _uiState.value
        val impianto = state.selectedImpianto ?: return
        val frequenza = state.selectedFrequenza ?: return

        scope.launch {
            _uiState.update {
                it.copy(isLoading = true, errorMessage = null, statusMessage = "Generazione PDF in corso...")
            }
            try {
                val outputDir = File("output")
                if (!outputDir.exists()) outputDir.mkdirs()

                val outputPath = "output/${impianto.codIntervento}_${frequenza.label().replace(" ", "_")}.pdf"
                val file = pdfStrategy.generate(impianto, frequenza, outputPath)

                _uiState.update {
                    it.copy(
                        pdfFile = file,
                        isLoading = false,
                        statusMessage = "✓ PDF generato: ${file.name}",
                        viewMode = ViewMode.PDF_PREVIEW
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
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

