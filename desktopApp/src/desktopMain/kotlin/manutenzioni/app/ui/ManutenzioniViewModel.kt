package manutenzioni.app.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import manutenzioni.app.strategy.HtmlToPdfStrategy
import manutenzioni.app.ui.coordinators.*
import manutenzioni.app.ui.platform.AwtDirectoryPicker
import manutenzioni.app.ui.platform.AwtSystemFileLauncher
import manutenzioni.app.ui.platform.DirectoryPicker
import manutenzioni.app.ui.platform.SystemFileLauncher
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import manutenzioni.domain.strategy.PdfBatchGenerator
import java.io.File

/**
 * Clean Façade ViewModel orchestrating cohesive domain coordinators via Unidirectional Data Flow (UDF).
 * Preserves 100% of public methods, signatures, and reactive StateFlow contracts.
 */
class ManutenzioniViewModel(
    private val repository: ManutenzioneRepository,
    private val pdfStrategy: PdfBatchGenerator = HtmlToPdfStrategy(),
    private val directoryPicker: DirectoryPicker = AwtDirectoryPicker(),
    private val fileLauncher: SystemFileLauncher = AwtSystemFileLauncher(),
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val _uiState = MutableStateFlow(ManutenzioniUiState())
    val uiState: StateFlow<ManutenzioniUiState> = _uiState.asStateFlow()

    // 1. Scheduler Coordinator
    private val schedulerCoordinator: InteractiveSchedulerCoordinator = InteractiveSchedulerCoordinator(
        repository = repository,
        uiState = _uiState,
        scope = scope,
        ioDispatcher = ioDispatcher,
        onOpenCantiere = { cantiereId, clienteId ->
            clienteId?.let { anagraficaCoordinator.loadCantieriForCliente(it) }
            impiantoCoordinator.loadImpiantiForCantiere(cantiereId)
            schedulerCoordinator.loadSessioniPerCantiere(cantiereId)
        },
        onUpdateDatiIntervento = { dataInizioContratto, dataIntervento, dataInterventoPrecedente, manutentore ->
            anagraficaCoordinator.updateDatiInterventoCantiere(
                dataInizioContratto = dataInizioContratto,
                dataIntervento = dataIntervento,
                dataInterventoPrecedente = dataInterventoPrecedente,
                manutentore = manutentore
            )
        }
    )

    // 2. Anagrafica Coordinator
    private val anagraficaCoordinator: AnagraficaCoordinator = AnagraficaCoordinator(
        repository = repository,
        uiState = _uiState,
        scope = scope,
        ioDispatcher = ioDispatcher,
        directoryPicker = directoryPicker,
        onCantiereSelected = { cantiere ->
            if (cantiere != null) {
                impiantoCoordinator.loadImpiantiForCantiere(cantiere.id)
                schedulerCoordinator.loadSessioniPerCantiere(cantiere.id)
            }
        },
        onRefresh = { refresh() },
        recomputeGlobalCalendar = { state, freqMap ->
            schedulerCoordinator.computeGlobalCalendar(state, freqMap)
        }
    )

    // 3. Impianto Selection Coordinator
    private val impiantoCoordinator: ImpiantoSelectionCoordinator = ImpiantoSelectionCoordinator(
        repository = repository,
        uiState = _uiState,
        scope = scope,
        ioDispatcher = ioDispatcher,
        recomputeGlobalCalendar = { state, freqMap ->
            schedulerCoordinator.computeGlobalCalendar(state, freqMap)
        },
        onImpiantiGlobaliUpdated = { cantiereId, clienteId ->
            anagraficaCoordinator.loadCantieriForCliente(clienteId)
            impiantoCoordinator.loadImpiantiForCantiere(cantiereId)
        }
    )

    // 4. PDF Batch Coordinator
    private val pdfBatchCoordinator: PdfBatchCoordinator = PdfBatchCoordinator(
        repository = repository,
        pdfStrategy = pdfStrategy,
        uiState = _uiState,
        scope = scope,
        ioDispatcher = ioDispatcher,
        directoryPicker = directoryPicker,
        fileLauncher = fileLauncher
    )

    // 5. Catalog Coordinator
    private val catalogCoordinator: CatalogSearchCoordinator = CatalogSearchCoordinator(
        repository = repository,
        uiState = _uiState,
        scope = scope,
        ioDispatcher = ioDispatcher
    )

    init {
        refresh()
        impiantoCoordinator.loadImpiantiGlobali()
    }

    fun refresh() {
        impiantoCoordinator.loadImpianti()
        anagraficaCoordinator.loadClienti()
        anagraficaCoordinator.loadTuttiCantieri()
        catalogCoordinator.loadComponentiStandard()
        catalogCoordinator.loadCatalogoApprovato()
        anagraficaCoordinator.loadUtenti()
        schedulerCoordinator.loadEventiManutenzione()
        _uiState.value.selectedCliente?.let {
            anagraficaCoordinator.loadCantieriForCliente(it.id)
        }
    }

    // --- UI Navigation ---
    fun setSection(section: AppSection) {
        if (section == AppSection.DATABASE && !_uiState.value.isAdmin) {
            _uiState.update {
                it.copy(errorMessage = "Accesso negato: il ruolo Tecnico non dispone dei permessi per l'Amministrazione Database")
            }
            return
        }
        _uiState.update { it.copy(currentSection = section, errorMessage = null) }
        if (section == AppSection.CALENDARIO) anagraficaCoordinator.loadTuttiCantieri()
    }

    fun setAdminTab(tab: AdminTab) {
        _uiState.update { it.copy(currentAdminTab = tab) }
    }

    // --- Delegated Public Methods (100% Signature Preservation) ---

    // Anagrafica: Clienti & Cantieri
    fun selectCliente(cliente: Cliente) = anagraficaCoordinator.selectCliente(cliente)
    fun addCliente(cliente: Cliente) = anagraficaCoordinator.addCliente(cliente)
    fun renameCliente(id: String, newName: String) = anagraficaCoordinator.renameCliente(id, newName)
    fun updateCliente(cliente: Cliente) = anagraficaCoordinator.updateCliente(cliente)
    fun deleteCliente(id: String) = anagraficaCoordinator.deleteCliente(id)
    fun selectCantiere(cantiere: Cantiere?) = anagraficaCoordinator.selectCantiere(cantiere)
    fun addCantiere(cantiere: Cantiere) = anagraficaCoordinator.addCantiere(cantiere)
    fun renameCantiere(id: String, newName: String) = anagraficaCoordinator.renameCantiere(id, newName)
    fun updateCantiere(id: String, nome: String, indirizzo: String?) = anagraficaCoordinator.updateCantiere(id, nome, indirizzo)
    fun updateDatiInterventoCantiere(dataInizioContratto: String?, dataIntervento: String?, dataInterventoPrecedente: String?, manutentore: String?) =
        anagraficaCoordinator.updateDatiInterventoCantiere(dataInizioContratto, dataIntervento, dataInterventoPrecedente, manutentore)
    fun updateFirmaManutentoreCantiere(firma: String?) = anagraficaCoordinator.updateFirmaManutentoreCantiere(firma)
    fun selectFirmaManutentoreFile() = anagraficaCoordinator.selectFirmaManutentoreFile()

    // RBAC Utenti
    fun selectUtenteAttivo(utente: Utente) = anagraficaCoordinator.selectUtenteAttivo(utente)
    fun saveUtente(utente: Utente) = anagraficaCoordinator.saveUtente(utente)
    fun deleteUtente(utenteId: String) = anagraficaCoordinator.deleteUtente(utenteId)

    // Impianti & Components
    fun selectImpianto(impianto: Impianto) = impiantoCoordinator.selectImpianto(impianto)
    fun toggleImpiantoSelection(impiantoId: String, isSelected: Boolean) = impiantoCoordinator.toggleImpiantoSelection(impiantoId, isSelected)
    fun toggleImpiantiSelection(impiantoIds: List<String>, isSelected: Boolean) = impiantoCoordinator.toggleImpiantiSelection(impiantoIds, isSelected)
    fun updateFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) = impiantoCoordinator.updateFrequenzaForImpianto(impiantoId, frequenza)
    fun toggleFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) = impiantoCoordinator.toggleFrequenzaForImpianto(impiantoId, frequenza)
    fun toggleFrequenzaForImpianti(impiantoIds: List<String>, frequenza: Periodo) = impiantoCoordinator.toggleFrequenzaForImpianti(impiantoIds, frequenza)
    fun addImpiantiFromTemplates(templates: List<Impianto>) = impiantoCoordinator.addImpiantiFromTemplates(templates)
    fun createNewImpianto(template: Impianto? = null) = impiantoCoordinator.createNewImpianto(template)
    fun updateMqtSwitchType(impiantoId: String, switchType: MqtSwitchType) = impiantoCoordinator.updateMqtSwitchType(impiantoId, switchType)
    fun updateEmergenzaSiglaDocumentale(impiantoId: String, sigla: EmergenzaSiglaDoc) = impiantoCoordinator.updateEmergenzaSiglaDocumentale(impiantoId, sigla)
    fun saveImpianto(impianto: Impianto) = impiantoCoordinator.saveImpianto(impianto)
    fun deleteImpianto(id: String) = impiantoCoordinator.deleteImpianto(id)
    fun updateImpiantoGlobale(impianto: Impianto) = impiantoCoordinator.updateImpiantoGlobale(impianto)
    fun aggiungiInterruttoreAQuadro(quadro: QuadroBT, interruttore: InterruttoreBT) = impiantoCoordinator.aggiungiInterruttoreAQuadro(quadro, interruttore)
    fun aggiungiLampadaAEmergenza(impianto: ImpiantoEmergenza, lampada: LampadaEmergenza) = impiantoCoordinator.aggiungiLampadaAEmergenza(impianto, lampada)
    fun aggiungiLampadeAEmergenza(impianto: ImpiantoEmergenza, lampade: List<LampadaEmergenza>) = impiantoCoordinator.aggiungiLampadeAEmergenza(impianto, lampade)
    fun aggiornaLampadaInEmergenza(impianto: ImpiantoEmergenza, lampadaAggiornata: LampadaEmergenza) = impiantoCoordinator.aggiornaLampadaInEmergenza(impianto, lampadaAggiornata)
    fun sostituisciProduttoreComponente(quadro: QuadroBT, interruttoreId: String, nuovaVariante: VarianteProdotto) = impiantoCoordinator.sostituisciProduttoreComponente(quadro, interruttoreId, nuovaVariante)
    fun aggiungiDispositivoAdAntincendio(impianto: RilevazioneAntincendio, dispositivo: DispositivoRilevazione) = impiantoCoordinator.aggiungiDispositivoAdAntincendio(impianto, dispositivo)
    fun aggiungiDispositiviAdAntincendio(impianto: RilevazioneAntincendio, dispositivi: List<DispositivoRilevazione>) = impiantoCoordinator.aggiungiDispositiviAdAntincendio(impianto, dispositivi)
    fun aggiornaDispositivoInAntincendio(impianto: RilevazioneAntincendio, dispositivoAggiornato: DispositivoRilevazione) = impiantoCoordinator.aggiornaDispositivoInAntincendio(impianto, dispositivoAggiornato)
    fun eliminaDispositivoDaAntincendio(impianto: RilevazioneAntincendio, dispositivoId: String) = impiantoCoordinator.eliminaDispositivoDaAntincendio(impianto, dispositivoId)
    fun svuotaDispositiviAntincendio(impianto: RilevazioneAntincendio) = impiantoCoordinator.svuotaDispositiviAntincendio(impianto)

    // PDF Batch
    fun generatePdf() = pdfBatchCoordinator.generatePdf()
    fun generatePdfToDirectory(outputDir: File) = pdfBatchCoordinator.generatePdfToDirectory(outputDir)
    fun openPdfInSystem() = pdfBatchCoordinator.openPdfInSystem()
    fun exportStandaloneTechnicalSheet(impianto: Impianto) = pdfBatchCoordinator.exportStandaloneTechnicalSheet(impianto)
    fun exportCantiereInterruttoriPdf(outputPdfFile: File? = null) = pdfBatchCoordinator.exportCantiereInterruttoriPdf(outputPdfFile)
    fun exportRiepilogoGeneralePdf(outputPdfFile: File? = null) = pdfBatchCoordinator.exportRiepilogoGeneralePdf(outputPdfFile)
    fun exportRiepilogoDaCalendarioGlobale(cantiereScadenza: CantiereScadenzaInMese, outputPdfFile: File? = null) = pdfBatchCoordinator.exportRiepilogoDaCalendarioGlobale(cantiereScadenza, outputPdfFile)
    fun openSessionePdfFile(filePath: String) = pdfBatchCoordinator.openSessionePdfFile(filePath)

    // Interactive Scheduler & Sessions
    fun setOrizzonteScadenze(mesi: Int) = schedulerCoordinator.setOrizzonteScadenze(mesi)
    fun selectMeseCalendarioGlobale(annoMese: String?) = schedulerCoordinator.selectMeseCalendarioGlobale(annoMese)
    fun apriCantiereDaCalendario(cantiereScadenza: CantiereScadenzaInMese) = schedulerCoordinator.apriCantiereDaCalendario(cantiereScadenza)
    fun ripristinaDatiDaSessione(sessione: SessioneVerifica) = schedulerCoordinator.ripristinaDatiDaSessione(sessione)
    fun deleteSessioneVerifica(sessioneId: String) = schedulerCoordinator.deleteSessioneVerifica(sessioneId)
    fun loadEventiManutenzione() = schedulerCoordinator.loadEventiManutenzione()
    fun selezionaGiornoCalendario(data: String?) = schedulerCoordinator.selezionaGiornoCalendario(data)
    fun cambiaMeseCalendario(deltaMesi: Long) = schedulerCoordinator.cambiaMeseCalendario(deltaMesi)
    fun vaiAlMeseCorrente() = schedulerCoordinator.vaiAlMeseCorrente()
    fun pianificaInterventiBatch(
        dataPianificata: String,
        cantieriIds: List<String>,
        impiantiIds: List<String> = emptyList(),
        impiantiCodici: List<String> = emptyList(),
        frequenza: Periodo,
        frequenzePerImpianto: Map<String, Periodo> = emptyMap(),
        manutentore: String? = null,
        note: String? = null,
        generaRicorrenze: Boolean = true,
        orizzonteRicorrenzeMesi: Int = 24
    ) = schedulerCoordinator.pianificaInterventiBatch(
        dataPianificata = dataPianificata,
        cantieriIds = cantieriIds,
        impiantiIds = impiantiIds,
        impiantiCodici = impiantiCodici,
        frequenza = frequenza,
        frequenzePerImpianto = frequenzePerImpianto,
        manutentore = manutentore,
        note = note,
        generaRicorrenze = generaRicorrenze,
        orizzonteRicorrenzeMesi = orizzonteRicorrenzeMesi
    )
    fun avviaInterventoDaCalendario(evento: EventoManutenzione) = schedulerCoordinator.avviaInterventoDaCalendario(evento)
    fun completaEventoManutenzione(eventoId: String, dataCompletamento: String? = null) = schedulerCoordinator.completaEventoManutenzione(eventoId, dataCompletamento)
    fun eliminaEventoManutenzione(eventoId: String, eliminaSerieFutura: Boolean = false) = schedulerCoordinator.eliminaEventoManutenzione(eventoId, eliminaSerieFutura)
}
