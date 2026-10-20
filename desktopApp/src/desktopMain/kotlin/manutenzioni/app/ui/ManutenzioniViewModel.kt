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
import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.MqtSwitchType
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.EmergenzaSiglaDoc
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

/** Tabs della sezione amministrazione, con etichetta visualizzata nella TabRow */
enum class AdminTab(val label: String) {
    CLIENTI("CLIENTI"),
    CANTIERI("CANTIERI"),
    /** Template master globali delle schede (R1: ex "Impianti Locali / Globali") */
    IMPIANTI_GLOBALI("MASTER SCHEDE DI VERIFICA"),
    /** Gestione profili utenti e permessi RBAC (R31) */
    UTENTI("UTENTI E PERMESSI")
}

/**
 * Stato immutabile dell'interfaccia utente.
 * Ogni cambiamento genera una nuova istanza (unidirectional data flow).
 */
data class ManutenzioniUiState(
    val impianti: List<Impianto> = emptyList(),
    val selectedImpianto: Impianto? = null,
    val frequenzePerImpianto: Map<String, Periodo> = emptyMap(),
    /** Selezione multipla di periodicità per ciascun impianto del cantiere (R21) */
    val frequenzeSelezionatePerImpianto: Map<String, Set<Periodo>> = emptyMap(),
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
    val catalogoApprovato: List<ComponenteApprovato> = emptyList(),
    /** Scadenze future calcolate per il cantiere selezionato (R27) */
    val scadenzeCantiere: List<manutenzioni.domain.model.MeseScadenzaGroup> = emptyList(),
    /** Orizzonte temporale in mesi per il calcolo del calendario scadenze (default 12 mesi) */
    val orizzonteScadenzeMesi: Int = 12,
    /** Storico sessioni di verifica salvate per il cantiere selezionato (R25, R30) */
    val sessioniVerificaCantiere: List<manutenzioni.domain.model.SessioneVerifica> = emptyList(),
    /** Lista profili utenti per controllo accessi RBAC (R31) */
    val utenti: List<manutenzioni.domain.model.Utente> = emptyList(),
    /** Utente attivo nella sessione corrente (default: Amministratore) */
    val utenteAttivo: manutenzioni.domain.model.Utente = manutenzioni.domain.model.Utente(
        id = "usr-admin-default",
        username = "admin",
        nomeCompleto = "Amministratore di Sistema",
        ruolo = manutenzioni.domain.model.RuoloUtente.ADMIN
    )
) {
    val isAdmin: Boolean
        get() = utenteAttivo.ruolo == manutenzioni.domain.model.RuoloUtente.ADMIN

    /** Cantieri visibili in base al ruolo RBAC (R31): tutti per ADMIN, solo quelli assegnati per TECNICO */
    val cantieriVisibili: List<Cantiere>
        get() = if (isAdmin) {
            cantieriDisponibili
        } else {
            cantieriDisponibili.filter { it.id in utenteAttivo.cantieriAssegnatiIds }
        }
}

/**
 * Calcola il nome della sottocartella temporale `YYYY_MM` (es. `2026_10`) a partire dalla
 * data di inizio intervento del cantiere (`gg/mm/aaaa` o `aaaa-mm-gg`), con fallback al mese corrente (R29).
 */
internal fun resolveTemporalSubfolderName(dataInizioIntervento: String?): String {
    val raw = dataInizioIntervento?.trim().orEmpty()
    if (raw.isNotEmpty()) {
        // Formato gg/mm/aaaa o gg-mm-aaaa o gg.mm.aaaa
        val dmyRegex = Regex("""^(\d{1,2})[/\-.](\d{1,2})[/\-.](\d{4})$""")
        dmyRegex.matchEntire(raw)?.let { m ->
            val month = m.groupValues[2].padStart(2, '0')
            val year = m.groupValues[3]
            return "${year}_${month}"
        }
        // Formato ISO aaaa-mm-gg o aaaa/mm/gg
        val ymdRegex = Regex("""^(\d{4})[/\-.](\d{1,2})[/\-.](\d{1,2})$""")
        ymdRegex.matchEntire(raw)?.let { m ->
            val year = m.groupValues[1]
            val month = m.groupValues[2].padStart(2, '0')
            return "${year}_${month}"
        }
        // Formato mm/aaaa o aaaa_mm
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
    val now = java.time.LocalDate.now()
    return "%04d_%02d".format(now.year, now.monthValue)
}

/**
 * Funzione pura: istanzia un template globale come copia indipendente per un cantiere.
 * Genera un nuovo id, assegna il `cantiereId` e resetta la quantità a 1.
 * Per i Quadri MT applica la tecnologia interruttore di default (SF6) se non impostata.
 */
internal fun instantiateTemplateForCantiere(template: Impianto, cantiereId: String?): Impianto {
    val copy = template.copyWithBasicParams(
        id = java.util.UUID.randomUUID().toString(),
        cantiereId = cantiereId,
        quantita = 1
    )
    return if (copy is QuadroMQT) copy.withTipoInterruttore(copy.tipoInterruttore ?: MqtSwitchType.SF6) else copy
}

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

    /** Carica gli impianti dal repository e arricchisce eventuali zone mancanti dai master bundled (R32) */
    private fun loadImpianti() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rawImpianti = repository.caricaImpianti()
                val impianti = enrichImpiantiZonaIfNeeded(rawImpianti)
                _uiState.update {
                    it.copy(
                        impianti = impianti,
                        impiantiGlobali = impianti.filter { imp -> imp.cantiereId == null },
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

    /**
     * Se il database attivo (JSON utente o MongoDB) contiene attività con `zona == null`,
     * propaga automaticamente il valore `zona` dal catalogo master `manutenzioni_db.json` (R32).
     */
    private suspend fun enrichImpiantiZonaIfNeeded(impianti: List<Impianto>): List<Impianto> {
        val needsEnrichment = impianti.any { imp ->
            imp.listaAttivita.any { it.zona.isNullOrBlank() }
        }
        if (!needsEnrichment) return impianti

        val bundledZonaMap: Map<Pair<String, Int>, String> = try {
            val stream = this::class.java.classLoader?.getResourceAsStream("manutenzioni_db.json")
            if (stream != null) {
                val content = stream.bufferedReader().use { it.readText() }
                val json = kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                    classDiscriminator = "type"
                }
                val bundledDb = json.decodeFromString(manutenzioni.app.data.ManutenzioniDatabase.serializer(), content)
                buildMap {
                    for (imp in bundledDb.impianti) {
                        imp.listaAttivita.forEachIndexed { idx, att ->
                            if (!att.zona.isNullOrBlank()) {
                                val dynamicNum = if (att.nAttivita > 0) att.nAttivita else idx + 1
                                put(imp.codIntervento.uppercase() to dynamicNum, att.zona)
                            }
                        }
                    }
                }
            } else emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

        if (bundledZonaMap.isEmpty()) return impianti

        return impianti.map { imp ->
            var changed = false
            val updatedAttivita = imp.listaAttivita.mapIndexed { idx, att ->
                if (att.zona.isNullOrBlank()) {
                    val dynamicNum = if (att.nAttivita > 0) att.nAttivita else idx + 1
                    val mappedZona = bundledZonaMap[imp.codIntervento.uppercase() to dynamicNum]
                    if (!mappedZona.isNullOrBlank()) {
                        changed = true
                        att.copy(zona = mappedZona)
                    } else att
                } else att
            }
            if (changed) {
                val baseUpdated = imp.copyWithBasicParams(listaAttivita = updatedAttivita)
                val finalUpdated = if (baseUpdated is QuadroMQT && baseUpdated.tipoInterruttore != null) {
                    baseUpdated.withTipoInterruttore(baseUpdated.tipoInterruttore)
                } else baseUpdated
                try {
                    withContext(Dispatchers.IO) { repository.salvaImpianto(finalUpdated) }
                } catch (_: Exception) {}
                finalUpdated
            } else imp
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

    /**
     * Aggiorna nome e indirizzo di un cantiere (R3) preservando i metadati di intervento
     * (data inizio, manutentore) presenti nello stato corrente.
     */
    fun updateCantiere(id: String, nome: String, indirizzo: String?) {
        if (nome.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Il nome del cantiere è obbligatorio") }
            return
        }
        val state = _uiState.value
        val current = state.cantieriDisponibili.find { it.id == id }
            ?: state.selectedCantiere?.takeIf { it.id == id }
            ?: return
        val updated = current.copy(
            nome = nome.trim(),
            indirizzo = indirizzo?.trim()?.ifBlank { null }
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.salvaCantiere(updated) }
                val cantieri = withContext(Dispatchers.IO) { repository.getCantieriForCliente(updated.clienteId) }
                _uiState.update {
                    it.copy(
                        cantieriDisponibili = if (it.selectedCliente?.id == updated.clienteId) cantieri else it.cantieriDisponibili,
                        selectedCantiere = if (it.selectedCantiere?.id == updated.id) updated else it.selectedCantiere,
                        statusMessage = "✓ Cantiere ${updated.nome} aggiornato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore aggiornamento cantiere: ${e.message}") }
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

    /** Aggiorna l'anagrafica completa di un cliente: nome, indirizzo e partita IVA (R2) */
    fun updateCliente(cliente: Cliente) {
        if (cliente.nome.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Il nome del cliente è obbligatorio") }
            return
        }
        val normalized = cliente.copy(
            nome = cliente.nome.trim(),
            indirizzo = cliente.indirizzo?.trim()?.ifBlank { null },
            partitaIva = cliente.partitaIva?.trim()?.ifBlank { null }
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.salvaCliente(normalized) }
                val clienti = withContext(Dispatchers.IO) { repository.caricaClienti() }
                _uiState.update {
                    it.copy(
                        clienti = clienti,
                        selectedCliente = if (it.selectedCliente?.id == normalized.id) normalized else it.selectedCliente,
                        statusMessage = "✓ Cliente ${normalized.nome} aggiornato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore aggiornamento cliente: ${e.message}") }
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
                    scadenzeCantiere = emptyList(),
                    sessioniVerificaCantiere = emptyList(),
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
                scadenzeCantiere = emptyList(),
                sessioniVerificaCantiere = emptyList(),
                statusMessage = "Cantiere: ${cantiere.nome}. Seleziona gli impianti.",
                errorMessage = null
            )
        }
        loadImpiantiForCantiere(cantiere.id)
        loadSessioniPerCantiere(cantiere.id)
    }

    /** Aggiorna e storicizza nel database la data di inizio contratto, data intervento, data intervento precedente e il nome del manutentore per il cantiere attivo */
    fun updateDatiInterventoCantiere(
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?,
        manutentore: String?
    ) {
        val currentCantiere = _uiState.value.selectedCantiere ?: return
        val dataInterventoNormalized = dataIntervento?.trim()?.ifBlank { null }
        val updated = currentCantiere.copy(
            dataInizioContratto = dataInizioContratto?.trim()?.ifBlank { null },
            dataIntervento = dataInterventoNormalized,
            dataInizioIntervento = dataInterventoNormalized, // retrocompat sync
            dataInterventoPrecedente = dataInterventoPrecedente?.trim()?.ifBlank { null },
            nomeManutentore = manutentore?.trim()?.ifBlank { null }
        )
        _uiState.update { state ->
            val scadenze = manutenzioni.domain.service.CalendarioScadenzeCalculator.calcolaScadenze(
                cantiere = updated,
                impianti = state.impiantiDelCantiere,
                mesiOrizzonte = state.orizzonteScadenzeMesi
            )
            state.copy(
                selectedCantiere = updated,
                cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == updated.id) updated else it },
                scadenzeCantiere = scadenze
            )
        }
        scope.launch(Dispatchers.IO) {
            try {
                repository.salvaCantiere(updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore salvataggio dati intervento: ${e.message}") }
            }
        }
    }

    /** Overload a 2 parametri per retrocompatibilità */
    fun updateDatiInterventoCantiere(dataInizio: String?, manutentore: String?) {
        val c = _uiState.value.selectedCantiere
        updateDatiInterventoCantiere(
            dataInizioContratto = c?.dataInizioContratto,
            dataIntervento = dataInizio,
            dataInterventoPrecedente = c?.dataInterventoPrecedente,
            manutentore = manutentore
        )
    }

    /** Aggiorna o rimuove la firma digitale del tecnico manutentore per il cantiere attivo (R26) */
    fun updateFirmaManutentoreCantiere(firma: String?) {
        val currentCantiere = _uiState.value.selectedCantiere ?: return
        val normalized = firma?.trim()?.ifBlank { null }
        val updated = currentCantiere.copy(firmaManutentore = normalized)
        _uiState.update { state ->
            state.copy(
                selectedCantiere = updated,
                cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == updated.id) updated else it },
                statusMessage = if (normalized != null) "✓ Firma digitale del tecnico associata al cantiere" else "✓ Firma digitale rimossa"
            )
        }
        scope.launch(Dispatchers.IO) {
            try {
                repository.salvaCantiere(updated)
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore salvataggio firma tecnico: ${e.message}") }
            }
        }
    }

    /** Apre un selettore di file per scegliere l'immagine della firma del tecnico e la converte in Data URI Base64 (R26) */
    fun selectFirmaManutentoreFile() {
        try {
            val os = System.getProperty("os.name").lowercase()
            val selectedFile: File? = if (os.contains("mac")) {
                val dialog = java.awt.FileDialog(null as java.awt.Frame?, "Seleziona immagine firma tecnico (PNG/JPG)", java.awt.FileDialog.LOAD)
                dialog.setFilenameFilter { _, name ->
                    val lower = name.lowercase()
                    lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                }
                dialog.isVisible = true
                if (dialog.directory != null && dialog.file != null) File(dialog.directory, dialog.file) else null
            } else {
                val chooser = JFileChooser().apply {
                    dialogTitle = "Seleziona immagine firma tecnico (PNG/JPG)"
                    fileSelectionMode = JFileChooser.FILES_ONLY
                }
                val result = chooser.showOpenDialog(null)
                if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            }
            if (selectedFile != null && selectedFile.exists() && selectedFile.isFile) {
                val bytes = selectedFile.readBytes()
                val ext = selectedFile.extension.lowercase()
                val mime = when (ext) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    else -> "image/png"
                }
                val b64 = java.util.Base64.getEncoder().encodeToString(bytes)
                updateFirmaManutentoreCantiere("data:$mime;base64,$b64")
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Errore caricamento immagine firma: ${e.message}") }
        }
    }

    private fun loadImpiantiForCantiere(cantiereId: String) {
        scope.launch {
            try {
                val rawImpianti = repository.getImpiantiForCantiere(cantiereId)
                val impianti = enrichImpiantiZonaIfNeeded(rawImpianti)
                _uiState.update { state ->
                    val cantiere = state.selectedCantiere
                    val scadenze = if (cantiere != null && cantiere.id == cantiereId) {
                        manutenzioni.domain.service.CalendarioScadenzeCalculator.calcolaScadenze(
                            cantiere = cantiere,
                            impianti = impianti,
                            mesiOrizzonte = state.orizzonteScadenzeMesi
                        )
                    } else state.scadenzeCantiere
                    state.copy(
                        impiantiDelCantiere = impianti,
                        scadenzeCantiere = scadenze
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(errorMessage = "Errore caricamento impianti: ${e.message}")
                }
            }
        }
    }

    private fun loadSessioniPerCantiere(cantiereId: String) {
        scope.launch {
            try {
                val sessioni = withContext(Dispatchers.IO) { repository.getSessioniPerCantiere(cantiereId) }
                _uiState.update { state ->
                    if (state.selectedCantiere?.id == cantiereId) {
                        state.copy(sessioniVerificaCantiere = sessioni)
                    } else state
                }
            } catch (_: Exception) {}
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

    /**
     * Seleziona o deseleziona una lista di impianti in blocco (R3).
     */
    fun toggleImpiantiSelection(impiantoIds: List<String>, isSelected: Boolean) {
        _uiState.update { state ->
            val updated = state.impiantiSelezionati.toMutableMap()
            impiantoIds.forEach { id ->
                if (isSelected) updated[id] = true else updated.remove(id)
            }
            state.copy(impiantiSelezionati = updated)
        }
    }

    /** Imposta la frequenza scelta per uno specifico impianto (tramite codIntervento o id) */
    fun updateFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) {
        _uiState.update { state ->
            val updatedFrequenze = state.frequenzePerImpianto.toMutableMap()
            updatedFrequenze[impiantoId] = frequenza
            val updatedMulti = state.frequenzeSelezionatePerImpianto.toMutableMap()
            if (updatedMulti[impiantoId].isNullOrEmpty()) {
                updatedMulti[impiantoId] = setOf(frequenza)
            }
            state.copy(
                frequenzePerImpianto = updatedFrequenze,
                frequenzeSelezionatePerImpianto = updatedMulti
            )
        }
    }

    /**
     * Attiva o disattiva una periodicità per uno specifico impianto del cantiere (R21 — selezione multipla).
     * Garantisce che almeno una periodicità rimanga selezionata.
     */
    fun toggleFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) {
        _uiState.update { state ->
            val currentSet = state.frequenzeSelezionatePerImpianto[impiantoId]
                ?: state.frequenzePerImpianto[impiantoId]?.let { setOf(it) }
                ?: emptySet()

            val nextSet = if (frequenza in currentSet) {
                if (currentSet.size > 1) currentSet - frequenza else currentSet
            } else {
                currentSet + frequenza
            }

            val updatedMulti = state.frequenzeSelezionatePerImpianto.toMutableMap()
            updatedMulti[impiantoId] = nextSet

            val updatedSingle = state.frequenzePerImpianto.toMutableMap()
            nextSet.minByOrNull { it.inMesi() }?.let { primary ->
                updatedSingle[impiantoId] = primary
            }

            state.copy(
                frequenzePerImpianto = updatedSingle,
                frequenzeSelezionatePerImpianto = updatedMulti
            )
        }
    }

    /**
     * Attiva o disattiva una periodicità per più impianti del cantiere in blocco (R3).
     * Se tutti gli impianti specificati hanno già la frequenza attiva, la rimuove (purché ciascuno mantenga almeno una frequenza).
     * Altrimenti, aggiunge la frequenza a tutti gli impianti specificati.
     */
    fun toggleFrequenzaForImpianti(impiantoIds: List<String>, frequenza: Periodo) {
        if (impiantoIds.isEmpty()) return
        _uiState.update { state ->
            val currentMulti = state.frequenzeSelezionatePerImpianto.toMutableMap()
            val currentSingle = state.frequenzePerImpianto.toMutableMap()
            val allHaveIt = impiantoIds.all { id ->
                val set = currentMulti[id] ?: currentSingle[id]?.let { setOf(it) } ?: emptySet()
                frequenza in set
            }
            impiantoIds.forEach { id ->
                val currentSet = currentMulti[id] ?: currentSingle[id]?.let { setOf(it) } ?: emptySet()
                val nextSet = if (allHaveIt) {
                    if (currentSet.size > 1) currentSet - frequenza else currentSet
                } else {
                    currentSet + frequenza
                }
                currentMulti[id] = nextSet
                nextSet.minByOrNull { it.inMesi() }?.let { primary -> currentSingle[id] = primary }
            }
            state.copy(frequenzePerImpianto = currentSingle, frequenzeSelezionatePerImpianto = currentMulti)
        }
    }

    /** Cambia la sezione corrente dell'app (bloccando l'accesso a DATABASE per ruolo TECNICO — R31) */
    fun setSection(section: AppSection) {
        if (section == AppSection.DATABASE && !_uiState.value.isAdmin) {
            _uiState.update {
                it.copy(errorMessage = "Accesso negato: il ruolo Tecnico non dispone dei permessi per l'Amministrazione Database")
            }
            return
        }
        _uiState.update { it.copy(currentSection = section, errorMessage = null) }
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
            val allImpianti = enrichImpiantiZonaIfNeeded(repository.caricaImpianti())
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
        val outputDir = selectOutputDirectoryCompatibile() ?: return
        generatePdfToDirectory(outputDir)
    }

    /**
     * Genera i PDF per gli impianti selezionati del cantiere attivo nella directory specificata.
     * Supporta multi-selezione delle periodicità per ciascun impianto (R21), naming univoco
     * `{SIGLA}_{PERIODICITA}.pdf` (R22) e creazione della sottocartella temporale `YYYY_MM` (R29).
     */
    fun generatePdfToDirectory(outputDir: File) {
        val state = _uiState.value
        val impiantiSelezionatiIds = state.impiantiSelezionati.filter { it.value }.keys
        if (impiantiSelezionatiIds.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Nessun impianto selezionato.") }
            return
        }

        val cantiere = state.selectedCantiere
        val subfolderName = resolveTemporalSubfolderName(cantiere?.dataInterventoEffettiva)

        _uiState.update {
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

            // Sottocartella temporale YYYY_MM (es. 2026_10) derivata da dataInterventoEffettiva (R29 & R4)
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
                    ?: listOf(state.frequenzePerImpianto[impianto.id] ?: Periodo(manutenzioni.domain.model.TipoPeriodo.A, 1))

                for (frequenza in frequenzeScelte) {
                    try {
                        val batchResult = withContext(Dispatchers.IO) {
                            pdfStrategy.generateBatch(
                                impianto = impianto,
                                frequenza = frequenza,
                                outputDir = targetOutputDir,
                                copies = 1,
                                clienteNome = state.selectedCliente?.nome,
                                contextImpianti = state.impiantiDelCantiere,
                                cantiereNome = cantiere?.nome,
                                dataInizioIntervento = cantiere?.dataInizioIntervento,
                                nomeManutentore = cantiere?.nomeManutentore,
                                firmaManutentore = cantiere?.firmaManutentore,
                                onProgress = { current, total ->
                                    _uiState.update {
                                        it.copy(
                                            batchProgress = "Impianto ${index + 1}/${impiantiDaGenerare.size} (${frequenza.codiceCartiglio()}): PDF $current di $total...",
                                            statusMessage = "Generazione ${impianto.codIntervento} (${frequenza.label()})..."
                                        )
                                    }
                                },
                                dataInizioContratto = cantiere?.dataInizioContratto,
                                dataIntervento = cantiere?.effectiveDataIntervento,
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

            // Storicizzazione automatica immutabile della sessione di verifica (Wave 4: R25, R30)
            // Viene salvata PRIMA della promozione della data così da fotografare esattamente l'intervento eseguito
            if (totalSuccessCount > 0 && cantiere != null) {
                val nowTimestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                val sessione = manutenzioni.domain.model.SessioneVerifica(
                    cantiereId = cantiere.id,
                    clienteId = cantiere.clienteId,
                    meseAnno = subfolderName,
                    dataInizioContratto = cantiere.dataInizioContratto,
                    dataIntervento = cantiere.effectiveDataIntervento,
                    dataInterventoPrecedente = cantiere.dataInterventoPrecedente,
                    nomeManutentore = cantiere.nomeManutentore,
                    dataCreazione = nowTimestamp,
                    cartellaOutput = targetOutputDir.absolutePath,
                    elencoFilePdf = allGeneratedFiles.map { it.absolutePath },
                    riepilogoImpianti = impiantiDaGenerare.map { imp ->
                        if (imp is QuadroBT && imp.sigla.isNotBlank()) "${imp.codIntervento}-${imp.sigla}" else imp.codIntervento
                    }
                )
                withContext(Dispatchers.IO) {
                    try {
                        repository.salvaSessioneVerifica(sessione)
                        val sessioniAggiornate = repository.getSessioniPerCantiere(cantiere.id)
                        _uiState.update { it.copy(sessioniVerificaCantiere = sessioniAggiornate) }
                    } catch (e: Exception) {
                        println("Warning: salvataggio sessione verifica fallito: ${e.message}")
                    }
                }
            }

            // Promozione automatica della data intervento a dataInterventoPrecedente se la generazione ha successo (R4)
            if (totalSuccessCount > 0 && totalErrors.isEmpty() && cantiere != null) {
                val currentData = cantiere.effectiveDataIntervento
                if (!currentData.isNullOrBlank()) {
                    val promoted = cantiere.copy(dataInterventoPrecedente = currentData)
                    withContext(Dispatchers.IO) {
                        try {
                            repository.salvaCantiere(promoted)
                        } catch (e: Exception) {
                            println("Warning: salvataggio promozione data cantiere fallito: ${e.message}")
                        }
                    }
                    _uiState.update { state ->
                        state.copy(
                            selectedCantiere = promoted,
                            cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == promoted.id) promoted else it }
                        )
                    }
                }
            }

            val statusMsg = "✓ $totalSuccessCount PDF generati in $subfolderName/. ${totalErrors.size} errori."
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

    /** Esporta singolarmente il foglio tecnico allegato (Foglio 2 standalone: UNI 11222 o Differenziali) */
    fun exportStandaloneTechnicalSheet(impianto: Impianto) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val providers = listOf(
                    manutenzioni.app.service.attachments.QuadroBtAttachmentProvider(),
                    manutenzioni.app.service.attachments.EmergenzaAttachmentProvider(),
                    manutenzioni.app.service.attachments.AntincendioAttachmentProvider()
                )
                val provider = providers.firstOrNull { it.canHandle(impianto) }
                if (provider == null) {
                    _uiState.update { it.copy(errorMessage = "Nessun componente registrato per generare il Foglio 2") }
                    return@launch
                }
                val stateSnap = _uiState.value
                val frequenza = stateSnap.frequenzePerImpianto[impianto.id] ?: Periodo(manutenzioni.domain.model.TipoPeriodo.M, 6)
                val clienteNome = stateSnap.selectedCliente?.nome
                val cantiere = stateSnap.selectedCantiere
                val sheetData = provider.buildSheetData(impianto, frequenza, clienteNome)
                if (sheetData == null) {
                    _uiState.update { it.copy(errorMessage = "Nessun dato registrato per il Foglio 2") }
                    return@launch
                }
                val renderer = manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer()
                val html = renderer.renderStandaloneHtml(
                    data = sheetData,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome,
                    dataInizioIntervento = cantiere?.dataInizioIntervento,
                    nomeManutentore = cantiere?.nomeManutentore
                )
                val tempHtml = java.io.File.createTempFile("allegato_${impianto.codIntervento}_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val outputPdf = java.io.File.createTempFile("Foglio2_${impianto.codIntervento}_${sheetData.oggetto}_", ".pdf")
                manutenzioni.app.service.Pdf().buildPdf(tempHtml.absolutePath, outputPdf.absolutePath)
                tempHtml.delete()

                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    try {
                        java.awt.Desktop.getDesktop().open(outputPdf)
                    } catch (_: Exception) {}
                    _uiState.update { it.copy(pdfFile = outputPdf, statusMessage = "Foglio 2 generato: ${outputPdf.name}") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore esportazione Foglio 2: ${e.message}") }
            }
        }
    }

    /**
     * Esporta in PDF l'elenco aggregato di tutti gli interruttori differenziali di tutti i Quadri BT
     * presenti nel cantiere attivo (R11).
     */
    fun exportCantiereInterruttoriPdf(outputPdfFile: File? = null) {
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val stateSnap = _uiState.value
                val quadri = stateSnap.impiantiDelCantiere.filterIsInstance<QuadroBT>()
                val provider = manutenzioni.app.service.attachments.QuadroBtAttachmentProvider()
                val frequenza = Periodo(manutenzioni.domain.model.TipoPeriodo.A, 1)
                val clienteNome = stateSnap.selectedCliente?.nome
                val cantiere = stateSnap.selectedCantiere
                val sheetData = provider.buildCantiereSheetData(
                    quadri = quadri,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome
                )
                if (sheetData == null) {
                    _uiState.update { it.copy(errorMessage = "Nessun interruttore registrato nei quadri del cantiere") }
                    return@launch
                }
                val renderer = manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer()
                val html = renderer.renderStandaloneHtml(
                    data = sheetData,
                    frequenza = frequenza,
                    clienteNome = clienteNome,
                    cantiereNome = cantiere?.nome,
                    dataInizioIntervento = cantiere?.dataInizioIntervento,
                    nomeManutentore = cantiere?.nomeManutentore
                )
                val tempHtml = java.io.File.createTempFile("elenco_interruttori_cantiere_", ".html")
                tempHtml.writeText(html, Charsets.UTF_8)
                val safeCantiere = cantiere?.nome?.trim()?.replace(" ", "_")?.replace("/", "-") ?: "Cantiere"
                val outputPdf = outputPdfFile ?: java.io.File.createTempFile("Elenco_Interruttori_${safeCantiere}_", ".pdf")
                manutenzioni.app.service.Pdf().buildPdf(tempHtml.absolutePath, outputPdf.absolutePath)
                tempHtml.delete()

                if (outputPdfFile == null) {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        try {
                            java.awt.Desktop.getDesktop().open(outputPdf)
                        } catch (_: Exception) {}
                    }
                }
                _uiState.update {
                    it.copy(
                        pdfFile = outputPdf,
                        statusMessage = "✓ Elenco Interruttori Cantiere generato: ${outputPdf.name}",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore esportazione Elenco Interruttori: ${e.message}") }
            }
        }
    }

    /**
     * Aggiunge in blocco più template globali al cantiere selezionato (R6 — selezione multipla).
     * Ogni template viene istanziato come copia indipendente (nuovo id, `cantiereId` del cantiere)
     * senza alterare il master globale. Un solo ricaricamento finale della lista del cantiere.
     */
    fun addImpiantiFromTemplates(templates: List<Impianto>) {
        val cantiereId = _uiState.value.selectedCantiere?.id
        if (cantiereId == null) {
            _uiState.update { it.copy(errorMessage = "Seleziona un cantiere prima di aggiungere attività di verifica") }
            return
        }
        if (templates.isEmpty()) return
        val nuoviImpianti = templates.map { instantiateTemplateForCantiere(it, cantiereId) }

        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val impiantiDelCantiere = withContext(Dispatchers.IO) {
                    nuoviImpianti.forEach { repository.salvaImpianto(it) }
                    repository.getImpiantiForCantiere(cantiereId)
                }
                _uiState.update {
                    it.copy(
                        impiantiDelCantiere = if (it.selectedCantiere?.id == cantiereId) impiantiDelCantiere else it.impiantiDelCantiere,
                        isLoading = false,
                        statusMessage = "✓ ${nuoviImpianti.size} attività di verifica aggiunte: " +
                            nuoviImpianti.joinToString(", ") { imp -> imp.codIntervento },
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Errore aggiunta attività di verifica: ${e.message}")
                }
            }
        }
    }

    /**
     * Crea un nuovo impianto vuoto, lo seleziona e apre l'editor.
     * Se è passato un template, lo persiste direttamente per il cantiere.
     */
    fun createNewImpianto(template: Impianto? = null) {
        val baseImpianto = template?.let { instantiateTemplateForCantiere(it, _uiState.value.selectedCantiere?.id) }
        val newImpianto = baseImpianto ?: manutenzioni.domain.model.ImpiantoStandard(
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

    /** Aggiorna la tecnologia dell'interruttore MT per un QuadroMQT e persiste la modifica */
    fun updateMqtSwitchType(impiantoId: String, switchType: MqtSwitchType) {
        val currentImpianto = _uiState.value.impiantiDelCantiere.find { it.id == impiantoId } as? QuadroMQT ?: return
        val updated = currentImpianto.withTipoInterruttore(switchType)
        saveImpianto(updated)
    }

    /** Aggiorna la sigla documentale (EM / IS) per un ImpiantoEmergenza e persiste la modifica */
    fun updateEmergenzaSiglaDocumentale(impiantoId: String, sigla: EmergenzaSiglaDoc) {
        val currentImpianto = (_uiState.value.impiantiDelCantiere.find { it.id == impiantoId }
            ?: _uiState.value.impianti.find { it.id == impiantoId }) as? ImpiantoEmergenza ?: return
        val updated = currentImpianto.withSiglaDocumentale(sigla)
        saveImpianto(updated)
    }

    /** Salva un impianto (nuovo o modificato) e aggiorna la lista */
    fun saveImpianto(impianto: Impianto) {
        val impiantoToSave = if (impianto is QuadroMQT && impianto.tipoInterruttore != null) {
            impianto.withTipoInterruttore(impianto.tipoInterruttore)
        } else {
            impianto
        }

        // Validazione campi obbligatori
        if (impiantoToSave.id.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Il codice intervento è obbligatorio")
            }
            return
        }
        if (impiantoToSave.nomeCompleto.isBlank()) {
            _uiState.update {
                it.copy(errorMessage = "Il nome completo è obbligatorio")
            }
            return
        }

        scope.launch {
            try {
                repository.salvaImpianto(impiantoToSave)
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
                        selectedImpianto = impiantoToSave,
                        statusMessage = "✓ Impianto ${impiantoToSave.id} salvato",
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
        loadUtenti()
        _uiState.value.selectedCliente?.let {
            loadCantieriForCliente(it.id)
        }
    }

    private fun loadUtenti() {
        scope.launch {
            try {
                val utenti = withContext(Dispatchers.IO) { repository.caricaUtenti() }
                if (utenti.isNotEmpty()) {
                    _uiState.update { state ->
                        val active = utenti.find { it.id == state.utenteAttivo.id } ?: utenti.first()
                        state.copy(utenti = utenti, utenteAttivo = active)
                    }
                }
            } catch (_: Exception) {}
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

    fun aggiungiInterruttoreAQuadro(
        quadro: QuadroBT,
        interruttore: InterruttoreBT
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val nuovoInterruttore = interruttore.copy(
                    id = if (interruttore.id.isBlank()) java.util.UUID.randomUUID().toString() else interruttore.id,
                    quantita = interruttore.quantita.coerceAtLeast(1)
                )

                val updatedInterruttori = quadro.listaInterruttori + nuovoInterruttore
                val updatedQuadro = quadro.copy(listaInterruttori = updatedInterruttori)

                // 1. Salva sul repository
                repository.salvaImpianto(updatedQuadro)

                // 2. Se presenti produttore e codice, memorizza nel catalogo approvato per autocompletamento futuro
                if (!nuovoInterruttore.produttore.isNullOrBlank() && !nuovoInterruttore.codiceArticolo.isNullOrBlank()) {
                    val nowIso = java.time.LocalDate.now().toString()
                    val variante = VarianteProdotto(
                        produttore = nuovoInterruttore.produttore,
                        codice = nuovoInterruttore.codiceArticolo,
                        serie = nuovoInterruttore.note,
                        dataApprovazione = nowIso
                    )
                    val approvato = ComponenteApprovato(
                        etimClassId = nuovoInterruttore.etimClassId ?: "MANUALE",
                        etimClassName = nuovoInterruttore.etimClassName ?: nuovoInterruttore.nome,
                        descrizioneStandard = nuovoInterruttore.nome,
                        caratteristicheTecniche = nuovoInterruttore.caratteristicheTecniche,
                        variantiProduttore = mapOf(variante.produttore to variante),
                        dataCreazione = nowIso
                    )
                    repository.salvaOAggiornaComponenteApprovato(approvato)
                }

                // 3. Ricarica dati aggiornati
                val approvatiAggiornati = repository.caricaCatalogoApprovato()
                val impiantiCantiere = quadro.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()

                val feedback = buildString {
                    append("✓ Componente '${nuovoInterruttore.nome}'")
                    if (!nuovoInterruttore.produttore.isNullOrBlank()) {
                        append(" (${nuovoInterruttore.produttore}")
                        if (!nuovoInterruttore.codiceArticolo.isNullOrBlank()) append(" ${nuovoInterruttore.codiceArticolo}")
                        append(")")
                    }
                    append(" aggiunto al quadro")
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedQuadro,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        catalogoApprovato = approvatiAggiornati,
                        statusMessage = feedback
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta componente: ${e.message}") }
            }
        }
    }

    fun aggiungiLampadaAEmergenza(
        impianto: manutenzioni.domain.model.ImpiantoEmergenza,
        lampada: manutenzioni.domain.model.LampadaEmergenza
    ) {
        aggiungiLampadeAEmergenza(impianto, listOf(lampada))
    }

    fun aggiungiLampadeAEmergenza(
        impianto: manutenzioni.domain.model.ImpiantoEmergenza,
        lampade: List<manutenzioni.domain.model.LampadaEmergenza>
    ) {
        if (lampade.isEmpty()) return
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val updatedImpianto = impianto.copy(
                    listaLampade = impianto.listaLampade + lampade
                )

                repository.salvaImpianto(updatedImpianto)

                val impiantiCantiere = impianto.cantiereId
                    ?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()

                val feedback = if (lampade.size == 1) {
                    val label = lampade.first().sigla.ifBlank { lampade.first().modello }
                    "✓ Lampada '$label' aggiunta all'impianto di emergenza"
                } else {
                    val modelloFirst = lampade.first().modello
                    val marcaFirst = lampade.first().produttore?.let { " $it" } ?: ""
                    "✓ Aggiunte ${lampade.size} lampade$marcaFirst '$modelloFirst' all'impianto di emergenza"
                }

                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedImpianto,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = feedback
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta lampade: ${e.message}") }
            }
        }
    }

    fun aggiornaLampadaInEmergenza(
        impianto: manutenzioni.domain.model.ImpiantoEmergenza,
        lampadaAggiornata: manutenzioni.domain.model.LampadaEmergenza
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }

                val updatedImpianto = impianto.copy(
                    listaLampade = impianto.listaLampade.map {
                        if (it.id == lampadaAggiornata.id) lampadaAggiornata else it
                    }
                )

                repository.salvaImpianto(updatedImpianto)

                val impiantiCantiere = impianto.cantiereId
                    ?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()

                val label = lampadaAggiornata.sigla.ifBlank { lampadaAggiornata.modello }
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedImpianto,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Lampada '$label' modificata con successo"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore modifica lampada: ${e.message}") }
            }
        }
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

    fun aggiungiDispositivoAdAntincendio(
        impianto: manutenzioni.domain.model.RilevazioneAntincendio,
        dispositivo: manutenzioni.domain.model.DispositivoRilevazione
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(listaDispositivi = impianto.listaDispositivi + dispositivo)
                repository.salvaImpianto(updated)
                val impiantiCantiere = impianto.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Dispositivo '${dispositivo.indirizzo}' aggiunto a Rilevazione Incendi"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta dispositivo: ${e.message}") }
            }
        }
    }

    fun aggiornaDispositivoInAntincendio(
        impianto: manutenzioni.domain.model.RilevazioneAntincendio,
        dispositivoAggiornato: manutenzioni.domain.model.DispositivoRilevazione
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(
                    listaDispositivi = impianto.listaDispositivi.map {
                        if (it.id == dispositivoAggiornato.id) dispositivoAggiornato else it
                    }
                )
                repository.salvaImpianto(updated)
                val impiantiCantiere = impianto.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Dispositivo '${dispositivoAggiornato.indirizzo}' aggiornato"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore modifica dispositivo: ${e.message}") }
            }
        }
    }

    fun eliminaDispositivoDaAntincendio(
        impianto: manutenzioni.domain.model.RilevazioneAntincendio,
        dispositivoId: String
    ) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(
                    listaDispositivi = impianto.listaDispositivi.filter { it.id != dispositivoId }
                )
                repository.salvaImpianto(updated)
                val impiantiCantiere = impianto.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Dispositivo rimosso da Rilevazione Incendi"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore eliminazione dispositivo: ${e.message}") }
            }
        }
    }

    fun svuotaDispositiviAntincendio(impianto: manutenzioni.domain.model.RilevazioneAntincendio) {
        scope.launch {
            try {
                _uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(listaDispositivi = emptyList())
                repository.salvaImpianto(updated)
                val impiantiCantiere = impianto.cantiereId?.let { repository.getImpiantiForCantiere(it) } ?: emptyList()
                val tuttiImpianti = repository.caricaImpianti()
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Registro componenti antincendio svuotato"
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Errore svuotamento registro: ${e.message}") }
            }
        }
    }

    // === Wave 4: Calendario Scadenze & Foglio di Riepilogo Generale (R27, R28) ===

    fun setOrizzonteScadenze(mesi: Int) {
        val validMesi = mesi.coerceIn(1, 120)
        _uiState.update { state ->
            val scadenze = state.selectedCantiere?.let { cantiere ->
                manutenzioni.domain.service.CalendarioScadenzeCalculator.calcolaScadenze(
                    cantiere = cantiere,
                    impianti = state.impiantiDelCantiere,
                    mesiOrizzonte = validMesi
                )
            } ?: emptyList()
            state.copy(
                orizzonteScadenzeMesi = validMesi,
                scadenzeCantiere = scadenze
            )
        }
    }

    /**
     * Genera ed esporta il PDF del Foglio di Riepilogo Generale Intervento (Master Checklist — R28).
     */
    fun exportRiepilogoGeneralePdf(outputPdfFile: File? = null) {
        val stateSnap = _uiState.value
        val cantiere = stateSnap.selectedCantiere
        if (cantiere == null) {
            _uiState.update { it.copy(errorMessage = "Seleziona un cantiere prima di generare il Foglio di Riepilogo Generale") }
            return
        }
        scope.launch(Dispatchers.IO) {
            try {
                val scadenze = manutenzioni.domain.service.CalendarioScadenzeCalculator.calcolaScadenze(
                    cantiere = cantiere,
                    impianti = stateSnap.impiantiDelCantiere,
                    mesiOrizzonte = stateSnap.orizzonteScadenzeMesi
                )
                val renderer = manutenzioni.app.service.RiepilogoInterventoHtmlRenderer()
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
                val subfolder = resolveTemporalSubfolderName(cantiere.dataInterventoEffettiva)
                val safeCantiere = cantiere.nome.trim().replace(" ", "_").replace("/", "-").ifBlank { "Cantiere" }
                val targetPdf = outputPdfFile ?: File.createTempFile("RIEPILOGO_INTERVENTO_${safeCantiere}_${subfolder}_", ".pdf")
                manutenzioni.app.service.Pdf().buildPdf(tempHtml.absolutePath, targetPdf.absolutePath)
                tempHtml.delete()

                if (outputPdfFile == null) {
                    withContext(Dispatchers.Main) {
                        try {
                            java.awt.Desktop.getDesktop().open(targetPdf)
                        } catch (_: Exception) {}
                    }
                }
                _uiState.update {
                    it.copy(
                        pdfFile = targetPdf,
                        scadenzeCantiere = scadenze,
                        statusMessage = "✓ Foglio di Riepilogo Generale generato: ${targetPdf.name}",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore generazione Foglio di Riepilogo Generale: ${e.message}") }
            }
        }
    }

    // === Wave 4: Storico Sessioni di Verifica & RBAC (R25, R30, R31) ===

    /**
     * Ripristina nel cantiere corrente i metadati di intervento (manutentore, date) di una sessione storica (R30).
     */
    fun ripristinaDatiDaSessione(sessione: manutenzioni.domain.model.SessioneVerifica) {
        updateDatiInterventoCantiere(
            dataInizioContratto = sessione.dataInizioContratto,
            dataIntervento = sessione.dataIntervento,
            dataInterventoPrecedente = sessione.dataInterventoPrecedente,
            manutentore = sessione.nomeManutentore
        )
        _uiState.update {
            it.copy(
                statusMessage = "✓ Ripristinati dati sessione ${sessione.meseAnno} (Manutentore: ${sessione.nomeManutentore ?: "-"})",
                errorMessage = null
            )
        }
    }

    fun deleteSessioneVerifica(sessioneId: String) {
        val cantiereId = _uiState.value.selectedCantiere?.id ?: return
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.eliminaSessioneVerifica(sessioneId) }
                val sessioni = withContext(Dispatchers.IO) { repository.getSessioniPerCantiere(cantiereId) }
                _uiState.update {
                    it.copy(
                        sessioniVerificaCantiere = sessioni,
                        statusMessage = "✓ Sessione storica rimossa",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore eliminazione sessione: ${e.message}") }
            }
        }
    }

    fun openSessionePdfFile(filePath: String) {
        val file = File(filePath)
        if (!file.exists()) {
            _uiState.update { it.copy(errorMessage = "Il file PDF non è più presente nel percorso: $filePath") }
            return
        }
        try {
            java.awt.Desktop.getDesktop().open(file)
            _uiState.update { it.copy(pdfFile = file, statusMessage = "Apertura ${file.name}...") }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = "Impossibile aprire il file: ${e.message}") }
        }
    }

    /**
     * Cambia l'utente attivo per il controllo accessi RBAC (R31).
     * Se il nuovo utente ha ruolo TECNICO, garantisce il posizionamento su OPERATIVITA
     * e deseleziona l'eventuale cantiere non assegnato a quel tecnico.
     */
    fun selectUtenteAttivo(utente: manutenzioni.domain.model.Utente) {
        _uiState.update { state ->
            val isTecnico = utente.ruolo == manutenzioni.domain.model.RuoloUtente.TECNICO
            val cantiereConsentito = !isTecnico ||
                (state.selectedCantiere != null && state.selectedCantiere.id in utente.cantieriAssegnatiIds)
            state.copy(
                utenteAttivo = utente,
                currentSection = if (isTecnico) AppSection.OPERATIVITA else state.currentSection,
                selectedCantiere = if (cantiereConsentito) state.selectedCantiere else null,
                impiantiDelCantiere = if (cantiereConsentito) state.impiantiDelCantiere else emptyList(),
                statusMessage = "Profilo attivo: ${utente.nomeCompleto} (${utente.ruolo.label})",
                errorMessage = null
            )
        }
    }

    fun saveUtente(utente: manutenzioni.domain.model.Utente) {
        if (utente.nomeCompleto.isBlank() || utente.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Username e nome completo sono obbligatori") }
            return
        }
        val normalized = utente.copy(
            username = utente.username.trim(),
            nomeCompleto = utente.nomeCompleto.trim()
        )
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.salvaUtente(normalized) }
                val utenti = withContext(Dispatchers.IO) { repository.caricaUtenti() }
                _uiState.update { state ->
                    val updatedActive = if (state.utenteAttivo.id == normalized.id) normalized else state.utenteAttivo
                    state.copy(
                        utenti = utenti,
                        utenteAttivo = updatedActive,
                        statusMessage = "✓ Utente ${normalized.nomeCompleto} (${normalized.ruolo.label}) salvato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore salvataggio utente: ${e.message}") }
            }
        }
    }

    fun deleteUtente(utenteId: String) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { repository.eliminaUtente(utenteId) }
                val utenti = withContext(Dispatchers.IO) { repository.caricaUtenti() }
                _uiState.update { state ->
                    val active = utenti.find { it.id == state.utenteAttivo.id } ?: utenti.firstOrNull() ?: state.utenteAttivo
                    state.copy(
                        utenti = utenti,
                        utenteAttivo = active,
                        statusMessage = "✓ Utente rimosso",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Errore eliminazione utente: ${e.message}") }
            }
        }
    }
}
