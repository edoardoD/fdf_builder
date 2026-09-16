package manutenzioni.app.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.InterruttoreBT
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.ComponentCandidate
import manutenzioni.domain.model.ComponenteApprovato
import manutenzioni.domain.model.VarianteProdotto
import manutenzioni.domain.service.ProductSearchApi
import manutenzioni.app.service.EtimCatalogService
import manutenzioni.app.strategy.HtmlToPdfStrategy
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.strategy.PdfBatchGenerator
import java.io.File
import javax.swing.JFileChooser

/** Sezioni principali dell'applicazione */
enum class AppSection {
    OPERATIVITA,
    DATABASE
}

/** Tabs della sezione amministrazione */
enum class AdminTab {
    CLIENTI,
    CANTIERI,
    IMPIANTI_GLOBALI
}

/**
 * Stato immutabile dell'interfaccia utente.
 * Ogni cambiamento genera una nuova istanza (unidirectional data flow).
 */
data class ManutenzioniUiState(
    val impianti: List<Impianto> = emptyList(),
    val selectedImpianto: Impianto? = null,
    val frequenzePerImpianto: Map<String, Periodo> = emptyMap(),
    val clienti: List<Cliente> = emptyList(),
    val selectedCliente: Cliente? = null,
    val cantieriDisponibili: List<Cantiere> = emptyList(),
    val selectedCantiere: Cantiere? = null,
    val impiantiGlobali: List<Impianto> = emptyList(),
    val impiantiDelCantiere: List<Impianto> = emptyList(),
    val impiantiSelezionati: Map<String, Boolean> = emptyMap(),
    val pdfFile: File? = null,
    val isLoading: Boolean = false,
    val statusMessage: String = "Seleziona un cliente per iniziare",
    val errorMessage: String? = null,
    val currentSection: AppSection = AppSection.OPERATIVITA,
    val currentAdminTab: AdminTab = AdminTab.CLIENTI,
    /** Progresso batch: "Generazione copia X di N..." (null se non in corso) */
    val batchProgress: String? = null,
    /** Lista dei file generati nell'ultimo batch */
    val generatedFiles: List<File> = emptyList(),
    val componentiStandard: List<manutenzioni.domain.model.ComponenteStandard> = emptyList(),
    val isSearchingComponenti: Boolean = false,
    val candidateComponents: List<ComponentCandidate> = emptyList(),
    val catalogoApprovato: List<ComponenteApprovato> = emptyList()
)

/**
 * ViewModel che gestisce lo stato dell'applicazione.
 *
 * Utilizza StateFlow (Observer Pattern) per propagare gli aggiornamenti
 * alla UI Compose in modo reattivo.
 */
class ManutenzioniViewModel(
    private val repository: ManutenzioneRepository,
    private val pdfStrategy: PdfBatchGenerator = HtmlToPdfStrategy(),
    private val productSearchApi: ProductSearchApi = EtimCatalogService()
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var searchJob: Job? = null

    private val _uiState = MutableStateFlow(ManutenzioniUiState())
    val uiState: StateFlow<ManutenzioniUiState> = _uiState.asStateFlow()

    init {
        refresh()
        loadImpiantiGlobali()
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
    
    private fun loadComponentiStandard() {
        scope.launch {
            try {
                val componenti = repository.caricaComponentiStandard()
                _uiState.update { it.copy(componentiStandard = componenti) }
            } catch (e: Exception) {
                // Ignore for now, or log
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
                selectedCantiere = null,
                cantieriDisponibili = emptyList(),
                impiantiDelCantiere = emptyList(),
                impiantiSelezionati = emptyMap(),
                statusMessage = "Cliente: ${cliente.nome}. Ora seleziona un cantiere.",
                errorMessage = null
            )
        }
        loadCantieriForCliente(cliente.id)
    }

    fun renameCantiere(id: String, newName: String) {
        scope.launch {
            try {
                repository.updateCantiere(id, newName)
                _uiState.value.selectedCliente?.id?.let { loadCantieriForCliente(it) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore ridenominazione cantiere: ${e.message}") }
            }
        }
    }

    private fun loadCantieriForCliente(clienteId: String) {
        scope.launch {
            try {
                val cantieri = repository.getCantieriForCliente(clienteId)
                _uiState.update { it.copy(cantieriDisponibili = cantieri) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore caricamento cantieri: ${e.message}")
                }
            }
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

    fun renameCliente(id: String, newName: String) {
        scope.launch {
            try {
                repository.updateCliente(id, newName)
                refresh()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore ridenominazione cliente: ${e.message}") }
            }
        }
    }

    fun deleteCliente(id: String) {
        scope.launch {
            try {
                repository.eliminaCliente(id)
                val clienti = repository.caricaClienti()
                _uiState.update { state -> 
                    // Se stavamo visualizzando proprio il cliente eliminato, chiudiamo tutto a cascata
                    val isCurrent = state.selectedCliente?.id == id
                    state.copy(
                        clienti = clienti,
                        selectedCliente = if (isCurrent) null else state.selectedCliente,
                        cantieriDisponibili = if (isCurrent) emptyList() else state.cantieriDisponibili,
                        selectedCantiere = if (isCurrent) null else state.selectedCantiere,
                        impiantiDelCantiere = if (isCurrent) emptyList() else state.impiantiDelCantiere,
                        impiantiSelezionati = if (isCurrent) emptyMap() else state.impiantiSelezionati,
                        selectedImpianto = if (isCurrent) null else state.selectedImpianto,
                        statusMessage = "✓ Cliente eliminato con successo",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore eliminazione cliente: ${e.message}") }
            }
        }
    }

    fun addCantiere(cantiere: Cantiere) {
        scope.launch {
            try {
                repository.salvaCantiere(cantiere)
                val cantieri = repository.getCantieriForCliente(cantiere.clienteId)
                _uiState.update {
                    it.copy(
                        cantieriDisponibili = cantieri,
                        selectedCantiere = cantiere,
                        statusMessage = "✓ Cantiere ${cantiere.nome} aggiunto",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore salvataggio cantiere: ${e.message}")
                }
            }
        }
    }

    fun selectCantiere(cantiere: Cantiere?) {
        if (cantiere == null) {
            _uiState.update {
                it.copy(
                    selectedCantiere = null,
                    impiantiDelCantiere = emptyList(),
                    impiantiSelezionati = emptyMap(),
                    statusMessage = "Seleziona un cantiere."
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedCantiere = cantiere,
                selectedImpianto = null, // Fix: Chiudi editor impianto precedente
                impiantiDelCantiere = emptyList(),
                impiantiSelezionati = emptyMap(),
                statusMessage = "Cantiere: ${cantiere.nome}. Seleziona gli impianti.",
                errorMessage = null
            )
        }
        loadImpiantiForCantiere(cantiere.id)
    }

    private fun loadImpiantiForCantiere(cantiereId: String) {
        scope.launch {
            try {
                val impianti = repository.getImpiantiForCantiere(cantiereId)
                _uiState.update { it.copy(impiantiDelCantiere = impianti) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore caricamento impianti: ${e.message}")
                }
            }
        }
    }

    /** Seleziona un impianto e calcola le frequenze disponibili */
    fun selectImpianto(impianto: Impianto) {
        _uiState.update {
            it.copy(
                selectedImpianto = impianto,
                pdfFile = null,
                statusMessage = "${impianto.nomeCompleto} — seleziona",
                errorMessage = null
            )
        }
    }

    fun toggleImpiantoSelection(impiantoId: String, isSelected: Boolean) {
        val updatedSelection = _uiState.value.impiantiSelezionati.toMutableMap()
        if (isSelected) {
            updatedSelection[impiantoId] = true
        } else {
            updatedSelection.remove(impiantoId)
        }

        _uiState.update { state ->
            state.copy(
                impiantiSelezionati = updatedSelection
            )
        }
    }

    /** Imposta la frequenza scelta per uno specifico impianto (tramite codIntervento o id) */
    fun updateFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) {
        val updatedFrequenze = _uiState.value.frequenzePerImpianto.toMutableMap()
        updatedFrequenze[impiantoId] = frequenza
        _uiState.update { it.copy(frequenzePerImpianto = updatedFrequenze) }
    }

    /** Cambia la sezione corrente dell'app */
    fun setSection(section: AppSection) {
        _uiState.update { it.copy(currentSection = section) }
    }

    fun setAdminTab(tab: AdminTab) {
        _uiState.update { it.copy(currentAdminTab = tab) }
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

    private fun loadImpiantiGlobali() {
        scope.launch {
            val allImpianti = repository.caricaImpianti()
            val globali = allImpianti.filter { it.cantiereId == null }
            _uiState.update { it.copy(impiantiGlobali = globali) }
        }
    }

    /** Genera il PDF con la strategia corrente — usa sempre il flusso batch */
    fun generatePdf() {
        val state = _uiState.value
        val impiantiSelezionatiIds = state.impiantiSelezionati.filter { it.value }.keys
        if (impiantiSelezionatiIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nessun impianto selezionato.") }
            return
        }
        // Non possiamo più bloccare se non c'è una "frequenza globale", procediamo.
        // La frequenza sarà letta dall'impianto stesso. Se manca, saltiamo o usiamo default.

        val outputDir = selectOutputDirectoryCompatibile() ?: return

        scope.launch {
            val impiantiDaGenerare = state.impiantiDelCantiere.filter { it.id in impiantiSelezionatiIds }

            _uiState.update {
                it.copy(
                    isLoading = true,
                    errorMessage = null,
                    batchProgress = "Avvio generazione...",
                    statusMessage = "Generazione di ${impiantiDaGenerare.size} PDF in corso...",
                    generatedFiles = emptyList()
                )
            }

            val allGeneratedFiles = mutableListOf<File>()
            var totalSuccessCount = 0
            val totalErrors = mutableMapOf<String, String>()

            for ((index, impianto) in impiantiDaGenerare.withIndex()) {
                try {
                    val batchResult = withContext(Dispatchers.IO) {
                        pdfStrategy.generateBatch(
                            impianto = impianto,
                            frequenza = state.frequenzePerImpianto[impianto.id] ?: Periodo(manutenzioni.domain.model.TipoPeriodo.A, 1),
                            outputDir = outputDir,
                            copies = 1,
                            clienteNome = state.selectedCliente?.nome,
                            contextImpianti = state.impiantiDelCantiere,
                            onProgress = { current, total ->
                                _uiState.update {
                                    it.copy(
                                        batchProgress = "Impianto ${index + 1}/${impiantiDaGenerare.size}: PDF $current di $total...",
                                        statusMessage = "Generazione ${impianto.id}..."
                                    )
                                }
                            }
                        )
                    }
                    allGeneratedFiles.addAll(batchResult.generatedFiles)
                    totalSuccessCount += batchResult.successCount
                    if (!batchResult.isFullSuccess) {
                        batchResult.errors.forEach { (copy, error) ->
                            totalErrors["${impianto.id} (copia $copy)"] = error
                        }
                    }
                } catch (e: Exception) {
                    totalErrors[impianto.id] = e.message ?: "Errore sconosciuto"
                }
            }

            val statusMsg = "✓ $totalSuccessCount PDF generati. ${totalErrors.size} errori."
            val errorMsg = if (totalErrors.isNotEmpty()) {
                "Errori: " + totalErrors.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            } else null

                _uiState.update {
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

    /**
     * Crea un nuovo impianto vuoto, lo seleziona e apre l'editor.
     * Se è passato un template, lo persiste direttamente per il cantiere.
     */
    fun createNewImpianto(template: Impianto? = null) {
        val newImpianto = template?.copyWithBasicParams(
            id = java.util.UUID.randomUUID().toString(),
            cantiereId = _uiState.value.selectedCantiere?.id,
            quantita = 1
        ) ?: manutenzioni.domain.model.ImpiantoStandard(
            id = java.util.UUID.randomUUID().toString(),
            codIntervento = "",
            nomeCompleto = "",
            premessa = null,
            listaAttivita = emptyList(),
            cantiereId = _uiState.value.selectedCantiere?.id,
            quantita = 1
        )
        if (template != null) {
            saveImpianto(newImpianto)
        } else {
            _uiState.update {
                it.copy(
                    selectedImpianto = newImpianto,
                    pdfFile = null,
                    statusMessage = "Nuovo impianto — compila i dati e salva",
                    errorMessage = null
                )
            }
        }
    }

    /** Salva un impianto (nuovo o modificato) e aggiorna la lista */
    fun saveImpianto(impianto: Impianto) {
        // Validazione campi obbligatori
        if (impianto.id.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Il codice intervento è obbligatorio")
            }
            return
        }
        if (impianto.nomeCompleto.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Il nome completo è obbligatorio")
            }
            return
        }

        scope.launch {
            try {
                repository.salvaImpianto(impianto)
                val impiantiAggiornati = repository.caricaImpianti()
                loadImpiantiGlobali()
                // Aggiorna la lista degli impianti del cantiere corrente
                val impiantiDelCantiereAggiornati = _uiState.value.selectedCantiere?.id?.let { cantiereId ->
                    repository.getImpiantiForCantiere(cantiereId)
                } ?: emptyList()
                
                _uiState.update {
                    it.copy(
                        impianti = impiantiAggiornati,
                        impiantiDelCantiere = impiantiDelCantiereAggiornati, // Fix: ricarica lista
                        selectedImpianto = impianto,
                        statusMessage = "✓ Impianto ${impianto.id} salvato",
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

    fun deleteImpianto(id: String) {
        scope.launch {
            try {
                // Troviamo prima il codIntervento per pulire le selezioni
                val impiantoDaEliminare = _uiState.value.impiantiDelCantiere.find { it.id == id } ?: _uiState.value.impianti.find { it.id == id }
                val codIntervento = impiantoDaEliminare?.codIntervento
                
                repository.eliminaImpianto(id)
                val impiantiAggiornati = repository.caricaImpianti()
                
                val cantiereId = _uiState.value.selectedCantiere?.id
                val impiantiDelCantiereAggiornati = cantiereId?.let { 
                    repository.getImpiantiForCantiere(it) 
                } ?: emptyList()
                
                _uiState.update { state ->
                    val isCurrent = state.selectedImpianto?.id == id
                    val updatedSelection = state.impiantiSelezionati.toMutableMap()
                    updatedSelection.remove(id)

                    state.copy(
                        impianti = impiantiAggiornati,
                        impiantiDelCantiere = impiantiDelCantiereAggiornati,
                        selectedImpianto = if (isCurrent) null else state.selectedImpianto,
                        impiantiSelezionati = updatedSelection,
                        statusMessage = "✓ Impianto eliminato con successo",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore eliminazione impianto: ${e.message}") }
            }
        }
    }

    /** Aggiorna l'impianto su tutti i cantieri in cui è presente */
    fun updateImpiantoGlobale(impianto: Impianto) {
        if (impianto.id.isBlank() || impianto.nomeCompleto.isBlank()) return

        scope.launch {
            try {
                repository.aggiornaImpiantiGlobalmente(impianto)
                loadImpiantiGlobali()
                val impiantiAggiornati = repository.caricaImpianti()
                _uiState.update {
                    it.copy(
                        impianti = impiantiAggiornati,
                        selectedImpianto = impianto,
                        statusMessage = "✓ Impianto ${impianto.id} aggiornato globalmente",
                        errorMessage = null
                    )
                }
                _uiState.value.selectedCantiere?.let { loadCantieriForCliente(it.clienteId); loadImpiantiForCantiere(it.id) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore aggiornamento globale: ${e.message}")
                }
            }
        }
    }

    /** Ricarica i dati dal repository */
    fun refresh() {
        loadImpianti()
        loadClienti()
        loadComponentiStandard()
        loadCatalogoApprovato()
        _uiState.value.selectedCliente?.let {
            loadCantieriForCliente(it.id)
        }
    }

    private fun loadCatalogoApprovato() {
        scope.launch {
            try {
                val approvati = repository.caricaCatalogoApprovato()
                _uiState.update { it.copy(catalogoApprovato = approvati) }
            } catch (e: Exception) {
                // Ignore or log
            }
        }
    }

    // === Ricerca ETIM & Approvazione Componenti ===

    fun searchCandidateComponents(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(candidateComponents = emptyList(), isSearchingComponenti = false) }
            return
        }

        searchJob = scope.launch {
            _uiState.update { it.copy(isSearchingComponenti = true) }
            delay(300) // Debounce 300ms
            try {
                val results = productSearchApi.search(query)
                _uiState.update { it.copy(candidateComponents = results, isSearchingComponenti = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSearchingComponenti = false, errorMessage = "Errore ricerca componenti: ${e.message}") }
            }
        }
    }

    fun clearCandidateComponents() {
        searchJob?.cancel()
        _uiState.update { it.copy(candidateComponents = emptyList(), isSearchingComponenti = false) }
    }

    fun approvaEAssegnaComponenteAQuadro(
        quadro: QuadroBT,
        candidate: ComponentCandidate,
        varianteScelta: VarianteProdotto,
        quantita: Int,
        siglaCircuito: String?
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val nowIso = java.time.LocalDate.now().toString()
                val varianteConData = varianteScelta.copy(dataApprovazione = nowIso)

                // 1. Costruisci il nuovo InterruttoreBT arricchito
                val nuovoInterruttore = InterruttoreBT(
                    id = java.util.UUID.randomUUID().toString(),
                    nome = candidate.descrizioneStandard,
                    quantita = quantita.coerceAtLeast(1),
                    siglaCircuito = siglaCircuito?.trim()?.ifBlank { null },
                    produttore = varianteConData.produttore,
                    codiceArticolo = varianteConData.codice,
                    etimClassId = candidate.etimClassId,
                    etimClassName = candidate.etimClassName,
                    caratteristicheTecniche = candidate.caratteristicheTecniche,
                    note = varianteConData.serie?.let { "Serie: $it" }
                )

                // 2. Aggiorna il quadro
                val updatedInterruttori = quadro.listaInterruttori + nuovoInterruttore
                val updatedQuadro = quadro.copy(listaInterruttori = updatedInterruttori)

                // 3. Salva su MongoDB / Repository
                repository.salvaImpianto(updatedQuadro)

                // 4. Arricchisci il Catalogo Approvato (Knowledge Base)
                val approvato = ComponenteApprovato(
                    etimClassId = candidate.etimClassId,
                    etimClassName = candidate.etimClassName,
                    descrizioneStandard = candidate.descrizioneStandard,
                    caratteristicheTecniche = candidate.caratteristicheTecniche,
                    variantiProduttore = mapOf(varianteConData.produttore to varianteConData),
                    dataCreazione = nowIso
                )
                repository.salvaOAggiornaComponenteApprovato(approvato)

                // 5. Ricarica dati
                val approvatiAggiornati = repository.caricaCatalogoApprovato()
                val impiantiCantiere = quadro.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedQuadro,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        catalogoApprovato = approvatiAggiornati,
                        candidateComponents = emptyList(),
                        statusMessage = "✓ Componente ${varianteConData.produttore} ${varianteConData.codice} approvato e aggiunto al quadro"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore approvazione componente: ${e.message}") }
            }
        }
    }

    fun sostituisciProduttoreComponente(
        quadro: QuadroBT,
        interruttoreId: String,
        nuovaVariante: VarianteProdotto
    ) {
        scope.launch {
            try {
                val index = quadro.listaInterruttori.indexOfFirst { it.id == interruttoreId }
                if (index < 0) return@launch

                val corrente = quadro.listaInterruttori[index]
                val aggiornato = corrente.copy(
                    produttore = nuovaVariante.produttore,
                    codiceArticolo = nuovaVariante.codice,
                    note = nuovaVariante.serie?.let { "Serie: $it" } ?: corrente.note
                )

                val newList = quadro.listaInterruttori.toMutableList()
                newList[index] = aggiornato
                val updatedQuadro = quadro.copy(listaInterruttori = newList)

                repository.salvaImpianto(updatedQuadro)
                val impiantiCantiere = quadro.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()

                _uiState.update { state ->
                    state.copy(
                        selectedImpianto = updatedQuadro,
                        impiantiDelCantiere = impiantiCantiere,
                        statusMessage = "✓ Sostituito con ${nuovaVariante.produttore} ${nuovaVariante.codice}"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore sostituzione: ${e.message}") }
            }
        }
    }
}
