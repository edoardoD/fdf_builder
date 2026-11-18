package manutenzioni.app.ui.coordinators

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import manutenzioni.app.ui.AppSection
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CalendarioScadenzeCalculator
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import manutenzioni.domain.service.RicorrenzeManutenzioneEngine
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

class InteractiveSchedulerCoordinator(
    private val repository: ManutenzioneRepository,
    private val uiState: MutableStateFlow<ManutenzioniUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onOpenCantiere: (cantiereId: String, clienteId: String?) -> Unit,
    private val onUpdateDatiIntervento: (dataInizioContratto: String?, dataIntervento: String?, dataInterventoPrecedente: String?, manutentore: String?) -> Unit
) {

    /** Ricalcola il Calendario Globale Multi-Cantiere filtrando i cantieri in base ai permessi RBAC dell'utente attivo */
    fun computeGlobalCalendar(
        clienti: List<Cliente>,
        tuttiCantieri: List<Cantiere>,
        impianti: List<Impianto>,
        orizzonteMesi: Int,
        utenteAttivo: Utente,
        frequenzePerImpiantoByCantiere: Map<String, Map<String, Set<Periodo>>>? = null
    ): List<MeseScadenzaGlobaleGroup> {
        val cantieriConsentiti = if (utenteAttivo.ruolo == RuoloUtente.ADMIN) {
            tuttiCantieri
        } else {
            tuttiCantieri.filter { it.id in utenteAttivo.cantieriAssegnatiIds }
        }
        return CalendarioScadenzeCalculator.calcolaScadenzeGlobali(
            clienti = clienti,
            cantieri = cantieriConsentiti,
            tuttiImpianti = impianti,
            mesiOrizzonte = orizzonteMesi,
            frequenzePerImpiantoByCantiere = frequenzePerImpiantoByCantiere
        )
    }

    fun computeGlobalCalendar(
        state: ManutenzioniUiState,
        frequenzePerImpiantoByCantiere: Map<String, Map<String, Set<Periodo>>>? = null
    ): List<MeseScadenzaGlobaleGroup> {
        return computeGlobalCalendar(
            clienti = state.clienti,
            tuttiCantieri = state.tuttiCantieri,
            impianti = state.impianti,
            orizzonteMesi = state.orizzonteScadenzeMesi,
            utenteAttivo = state.utenteAttivo,
            frequenzePerImpiantoByCantiere = frequenzePerImpiantoByCantiere
        )
    }

    fun setOrizzonteScadenze(mesi: Int) {
        val validMesi = mesi.coerceIn(1, 120)
        uiState.update { state ->
            val freqMap = state.selectedCantiere?.let { mapOf(it.id to state.frequenzeSelezionatePerImpianto) }
            val globalCal = computeGlobalCalendar(
                clienti = state.clienti,
                tuttiCantieri = state.tuttiCantieri,
                impianti = state.impianti,
                orizzonteMesi = validMesi,
                utenteAttivo = state.utenteAttivo,
                frequenzePerImpiantoByCantiere = freqMap
            )
            state.copy(
                orizzonteScadenzeMesi = validMesi,
                calendarioGlobale = globalCal
            )
        }
    }

    /** Filtra la vista del Calendario Globale per uno specifico mese ("2026-05") oppure mostra tutti i mesi (null) */
    fun selectMeseCalendarioGlobale(annoMese: String?) {
        uiState.update { it.copy(meseCalendarioSelezionato = annoMese) }
    }

    /**
     * Apre un cantiere direttamente dal Calendario Globale posizionandosi nella sezione Operatività
     * e pre-selezionando automaticamente gli impianti e le periodicità in scadenza in quel mese.
     */
    fun apriCantiereDaCalendario(cantiereScadenza: CantiereScadenzaInMese) {
        val stateSnap = uiState.value
        val cantiere = cantiereScadenza.cantiere
        val cliente = cantiereScadenza.cliente ?: stateSnap.clienti.find { it.id == cantiere.clienteId }

        val preselectedImpianti = cantiereScadenza.scadenzeImpianti.associate { it.impiantoId to true }
        val preselectedMultiFreq = stateSnap.frequenzeSelezionatePerImpianto.toMutableMap().apply {
            cantiereScadenza.scadenzeImpianti.forEach { item ->
                put(item.impiantoId, item.frequenzeIncluse.toSet())
            }
        }
        val preselectedSingleFreq = stateSnap.frequenzePerImpianto.toMutableMap().apply {
            cantiereScadenza.scadenzeImpianti.forEach { item ->
                put(item.impiantoId, item.frequenzaMassima)
            }
        }

        val updatedCantiere = cantiere.copy(
            dataIntervento = cantiereScadenza.dataPrevista
        )

        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaCantiere(updatedCantiere) }
            } catch (_: Exception) {}
        }

        val updatedTutti = stateSnap.tuttiCantieri.map { if (it.id == updatedCantiere.id) updatedCantiere else it }

        uiState.update { state ->
            state.copy(
                currentSection = AppSection.OPERATIVITA,
                selectedCliente = cliente,
                selectedCantiere = updatedCantiere,
                cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == updatedCantiere.id) updatedCantiere else it },
                tuttiCantieri = updatedTutti,
                selectedImpianto = null,
                impiantiSelezionati = preselectedImpianti,
                frequenzeSelezionatePerImpianto = preselectedMultiFreq,
                frequenzePerImpianto = preselectedSingleFreq,
                statusMessage = "✓ Cantiere '${updatedCantiere.nome}' aperto dal Calendario (${cantiereScadenza.dataPrevista}: ${cantiereScadenza.totaleImpiantiInScadenza} impianti pre-selezionati)",
                errorMessage = null
            )
        }
        onOpenCantiere(updatedCantiere.id, cliente?.id)
    }

    fun loadSessioniPerCantiere(cantiereId: String) {
        scope.launch {
            try {
                val sessioni = withContext(ioDispatcher) { repository.getSessioniPerCantiere(cantiereId) }
                uiState.update { state ->
                    if (state.selectedCantiere?.id == cantiereId) {
                        state.copy(sessioniVerificaCantiere = sessioni)
                    } else state
                }
            } catch (_: Exception) {}
        }
    }

    /**
     * Ripristina nel cantiere corrente i metadati di intervento (manutentore, date) di una sessione storica (R30).
     */
    fun ripristinaDatiDaSessione(sessione: SessioneVerifica) {
        onUpdateDatiIntervento(
            sessione.dataInizioContratto,
            sessione.dataIntervento,
            sessione.dataInterventoPrecedente,
            sessione.nomeManutentore
        )
        uiState.update {
            it.copy(
                statusMessage = "✓ Ripristinati dati sessione ${sessione.meseAnno} (Manutentore: ${sessione.nomeManutentore ?: "-"})",
                errorMessage = null
            )
        }
    }

    fun deleteSessioneVerifica(sessioneId: String) {
        val cantiereId = uiState.value.selectedCantiere?.id ?: return
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.eliminaSessioneVerifica(sessioneId) }
                val sessioni = withContext(ioDispatcher) { repository.getSessioniPerCantiere(cantiereId) }
                uiState.update {
                    it.copy(
                        sessioniVerificaCantiere = sessioni,
                        statusMessage = "✓ Sessione storica rimossa",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore eliminazione sessione: ${e.message}") }
            }
        }
    }

    fun loadEventiManutenzione() {
        scope.launch {
            try {
                val eventi = withContext(ioDispatcher) { repository.caricaEventiManutenzione() }
                uiState.update { it.copy(eventiManutenzione = eventi) }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore caricamento eventi calendario: ${e.message}") }
            }
        }
    }

    /** Seleziona o deseleziona un giorno a calendario (formato "dd/MM/yyyy", es. "22/10/2026") */
    fun selezionaGiornoCalendario(data: String?) {
        uiState.update { state ->
            val nextMonth = if (!data.isNullOrBlank()) {
                val parsed = CalendarioScadenzeCalculator.parseDataRiferimento(data)
                YearMonth.of(parsed.year, parsed.monthValue)
            } else {
                state.meseVisualizzatoCalendario
            }
            state.copy(
                dataCalendarioSelezionata = data,
                meseVisualizzatoCalendario = nextMonth
            )
        }
    }

    /** Sposta la vista del calendario di N mesi in avanti o indietro */
    fun cambiaMeseCalendario(deltaMesi: Long) {
        uiState.update { state ->
            val next = state.meseVisualizzatoCalendario.plusMonths(deltaMesi)
            val nextSel = state.dataCalendarioSelezionata?.takeIf { sel ->
                val p = CalendarioScadenzeCalculator.parseDataRiferimento(sel)
                p.year == next.year && p.monthValue == next.monthValue
            }
            state.copy(
                meseVisualizzatoCalendario = next,
                dataCalendarioSelezionata = nextSel
            )
        }
    }

    /** Riporta la vista del calendario al mese e giorno correnti */
    fun vaiAlMeseCorrente() {
        val now = LocalDate.now()
        val nowFormatted = "%02d/%02d/%04d".format(now.dayOfMonth, now.monthValue, now.year)
        uiState.update { state ->
            state.copy(
                meseVisualizzatoCalendario = YearMonth.of(now.year, now.monthValue),
                dataCalendarioSelezionata = nowFormatted
            )
        }
    }

    /**
     * Pianifica uno o più interventi di manutenzione per una lista di cantieri alla stessa data,
     * con calcolo e generazione automatica delle ricorrenze successive future.
     */
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
    ) {
        if (cantieriIds.isEmpty()) {
            uiState.update { it.copy(errorMessage = "Seleziona almeno un cantiere per pianificare l'intervento") }
            return
        }
        val cleanDate = dataPianificata.trim()
        if (cleanDate.isBlank()) {
            uiState.update { it.copy(errorMessage = "La data pianificata è obbligatoria") }
            return
        }

        scope.launch {
            uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val state = uiState.value
                val tuttiEventiGenerati = mutableListOf<EventoManutenzione>()

                withContext(ioDispatcher) {
                    for (cantiereId in cantieriIds) {
                        val cantiere = state.tuttiCantieri.find { it.id == cantiereId }
                            ?: repository.getCantieriForCliente("").find { it.id == cantiereId }
                            ?: continue

                        val impiantiCantiere = repository.getImpiantiForCantiere(cantiereId)
                        val targetImpianti = when {
                            impiantiIds.isNotEmpty() -> impiantiCantiere.filter { it.id in impiantiIds }
                            impiantiCodici.isNotEmpty() -> impiantiCantiere.filter { it.codIntervento.uppercase() in impiantiCodici.map { c -> c.uppercase() } }
                            else -> impiantiCantiere
                        }

                        if (targetImpianti.isEmpty()) continue

                        val snapshotList = targetImpianti.map { imp ->
                            val freqPerQuestoImpianto = frequenzePerImpianto[imp.id] ?: frequenza
                            val attivita = CrossPlantAttivitaResolver.resolveAttivita(imp, impiantiCantiere, freqPerQuestoImpianto)
                            val attDesc = attivita.mapIndexed { idx, att ->
                                val z = att.zona?.takeIf { it.isNotBlank() }?.let { " [$it]" } ?: ""
                                "${idx + 1}. ${att.tipoAttivita}$z: ${att.descrizione} (${att.frequenza.label()})"
                            }
                            val desc = when (imp) {
                                is QuadroBT -> if (imp.sigla.isNotBlank()) "${imp.nomeCompleto} (${imp.sigla})" else imp.nomeCompleto
                                else -> imp.nomeCompleto
                            }
                            ImpiantoPianificatoSnapshot(
                                impiantoId = imp.id,
                                codIntervento = imp.codIntervento,
                                descrizione = desc,
                                periodicitaScelta = freqPerQuestoImpianto,
                                numeroAttivitaPreviste = attivita.size,
                                attivitaDescrizioni = attDesc
                            )
                        }

                        val serieId = UUID.randomUUID().toString()
                        val eventoBase = EventoManutenzione(
                            id = UUID.randomUUID().toString(),
                            cantiereId = cantiere.id,
                            clienteId = cantiere.clienteId,
                            dataPianificata = cleanDate,
                            nomeManutentore = manutentore?.trim()?.ifBlank { null } ?: cantiere.nomeManutentore,
                            stato = StatoEventoManutenzione.PIANIFICATO,
                            impiantiPianificati = snapshotList,
                            note = note?.trim()?.ifBlank { null },
                            serieRicorrenzaId = serieId
                        )
                        tuttiEventiGenerati.add(eventoBase)

                        if (generaRicorrenze) {
                            val future = RicorrenzeManutenzioneEngine.generaRicorrenzeFuture(
                                eventoBase = eventoBase,
                                orizzonteMesi = orizzonteRicorrenzeMesi
                            )
                            tuttiEventiGenerati.addAll(future)
                        }
                    }

                    repository.salvaEventiManutenzioneBatch(tuttiEventiGenerati)
                }

                val freqMap: Map<String, Map<String, Set<Periodo>>>? = if (frequenzePerImpianto.isNotEmpty()) {
                    cantieriIds.associateWith {
                        frequenzePerImpianto.mapValues { setOf(it.value) }
                    }
                } else null

                val globalCal = computeGlobalCalendar(
                    clienti = state.clienti,
                    tuttiCantieri = state.tuttiCantieri,
                    impianti = state.impianti,
                    orizzonteMesi = state.orizzonteScadenzeMesi,
                    utenteAttivo = state.utenteAttivo,
                    frequenzePerImpiantoByCantiere = freqMap
                )

                val eventiRicaricati = withContext(ioDispatcher) { repository.caricaEventiManutenzione() }
                uiState.update {
                    it.copy(
                        eventiManutenzione = eventiRicaricati,
                        dataCalendarioSelezionata = cleanDate,
                        calendarioGlobale = globalCal,
                        isLoading = false,
                        statusMessage = "✓ Pianificati interventi per ${cantieriIds.size} cantieri al $cleanDate (${tuttiEventiGenerati.size} eventi totali generati con ricorrenze)",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore pianificazione interventi: ${e.message}") }
            }
        }
    }

    /**
     * Avvia l'esecuzione di un evento dal calendario: imposta lo stato su IN_CORSO,
     * carica il cantiere in Operatività con data e impianti dell'evento pre-selezionati.
     */
    fun avviaInterventoDaCalendario(evento: EventoManutenzione) {
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    repository.aggiornaStatoEvento(evento.id, StatoEventoManutenzione.IN_CORSO)
                }
                val eventi = withContext(ioDispatcher) { repository.caricaEventiManutenzione() }
                val stateSnap = uiState.value
                val cantiere = stateSnap.tuttiCantieri.find { it.id == evento.cantiereId }
                    ?: repository.getCantieriForCliente("").find { it.id == evento.cantiereId }
                val cliente = cantiere?.let { c -> stateSnap.clienti.find { it.id == c.clienteId } }

                val preselectedImpianti = evento.impiantiPianificati.associate { it.impiantoId to true }
                val preselectedMulti = evento.impiantiPianificati.associate { it.impiantoId to setOf(it.periodicitaScelta) }
                val preselectedSingle = evento.impiantiPianificati.associate { it.impiantoId to it.periodicitaScelta }

                val updatedCantiere = cantiere?.copy(
                    dataIntervento = evento.dataPianificata,
                    nomeManutentore = evento.nomeManutentore ?: cantiere.nomeManutentore
                )

                if (updatedCantiere != null) {
                    withContext(ioDispatcher) { repository.salvaCantiere(updatedCantiere) }
                }

                uiState.update { state ->
                    state.copy(
                        currentSection = AppSection.OPERATIVITA,
                        selectedCliente = cliente,
                        selectedCantiere = updatedCantiere,
                        selectedImpianto = null,
                        impiantiSelezionati = preselectedImpianti,
                        frequenzeSelezionatePerImpianto = preselectedMulti,
                        frequenzePerImpianto = preselectedSingle,
                        eventiManutenzione = eventi,
                        statusMessage = "✓ Intervento del ${evento.dataPianificata} avviato in Operatività per '${updatedCantiere?.nome}'",
                        errorMessage = null
                    )
                }
                if (cantiere != null) {
                    onOpenCantiere(cantiere.id, cliente?.id)
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore avvio intervento: ${e.message}") }
            }
        }
    }

    /**
     * Segna un evento di manutenzione come COMPLETATO alla data corrente.
     */
    fun completaEventoManutenzione(eventoId: String, dataCompletamento: String? = null) {
        val nowFormatted = dataCompletamento ?: run {
            val now = LocalDate.now()
            "%02d/%02d/%04d".format(now.dayOfMonth, now.monthValue, now.year)
        }
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    repository.aggiornaStatoEvento(
                        id = eventoId,
                        nuovoStato = StatoEventoManutenzione.COMPLETATO,
                        dataCompletamento = nowFormatted
                    )
                }
                val eventi = withContext(ioDispatcher) { repository.caricaEventiManutenzione() }
                uiState.update {
                    it.copy(
                        eventiManutenzione = eventi,
                        statusMessage = "✓ Intervento segnato come Completato in data $nowFormatted",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore completamento intervento: ${e.message}") }
            }
        }
    }

    /**
     * Elimina un evento dal calendario, con opzione per cancellare tutte le ricorrenze future della serie.
     */
    fun eliminaEventoManutenzione(eventoId: String, eliminaSerieFutura: Boolean = false) {
        scope.launch {
            try {
                withContext(ioDispatcher) {
                    repository.eliminaEventoManutenzione(eventoId, eliminaSerieFutura)
                }
                val eventi = withContext(ioDispatcher) { repository.caricaEventiManutenzione() }
                uiState.update {
                    it.copy(
                        eventiManutenzione = eventi,
                        statusMessage = if (eliminaSerieFutura) "✓ Evento e serie futura rimossi" else "✓ Evento rimosso dal calendario",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore eliminazione evento: ${e.message}") }
            }
        }
    }
}
