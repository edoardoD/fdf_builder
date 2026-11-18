package manutenzioni.app.ui.coordinators

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import manutenzioni.app.data.ManutenzioniDatabase
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import java.time.LocalDate
import java.util.UUID

class ImpiantoSelectionCoordinator(
    private val repository: ManutenzioneRepository,
    private val uiState: MutableStateFlow<ManutenzioniUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val recomputeGlobalCalendar: (state: ManutenzioniUiState, freqMap: Map<String, Map<String, Set<Periodo>>>?) -> List<MeseScadenzaGlobaleGroup>,
    private val onImpiantiGlobaliUpdated: (cantiereId: String, clienteId: String) -> Unit
) {

    fun loadImpianti() {
        scope.launch {
            uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val rawImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }
                val impianti = enrichImpiantiZonaIfNeeded(rawImpianti)
                uiState.update { state ->
                    val globalCal = recomputeGlobalCalendar(state.copy(impianti = impianti), null)
                    state.copy(
                        impianti = impianti,
                        impiantiGlobali = impianti.filter { imp -> imp.cantiereId == null },
                        calendarioGlobale = globalCal,
                        isLoading = false,
                        statusMessage = "${impianti.size} impianti caricati"
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Errore caricamento: ${e.message}"
                    )
                }
            }
        }
    }

    suspend fun enrichImpiantiZonaIfNeeded(impianti: List<Impianto>): List<Impianto> {
        val needsEnrichment = impianti.any { imp ->
            imp.listaAttivita.any { it.zona.isNullOrBlank() }
        }
        if (!needsEnrichment) return impianti

        val bundledZonaMap: Map<Pair<String, Int>, String> = try {
            val stream = this::class.java.classLoader?.getResourceAsStream("manutenzioni_db.json")
            if (stream != null) {
                val content = stream.bufferedReader().use { it.readText() }
                val json = Json {
                    ignoreUnknownKeys = true
                    classDiscriminator = "type"
                }
                val bundledDb = json.decodeFromString(ManutenzioniDatabase.serializer(), content)
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
                    withContext(ioDispatcher) { repository.salvaImpianto(finalUpdated) }
                } catch (_: Exception) {}
                finalUpdated
            } else imp
        }
    }

    fun loadImpiantiGlobali() {
        scope.launch {
            val allImpianti = enrichImpiantiZonaIfNeeded(withContext(ioDispatcher) { repository.caricaImpianti() })
            val globali = allImpianti.filter { it.cantiereId == null }
            uiState.update { it.copy(impiantiGlobali = globali) }
        }
    }

    fun loadImpiantiForCantiere(cantiereId: String) {
        scope.launch {
            try {
                val rawImpianti = withContext(ioDispatcher) { repository.getImpiantiForCantiere(cantiereId) }
                val impianti = enrichImpiantiZonaIfNeeded(rawImpianti)
                val frequenzeMap = impianti.associate { it.id to CrossPlantAttivitaResolver.resolveFrequenze(it, impianti) }
                uiState.update { state ->
                    state.copy(
                        impiantiDelCantiere = impianti,
                        frequenzeDisponibiliPerImpianto = frequenzeMap
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore caricamento impianti: ${e.message}")
                }
            }
        }
    }

    fun selectImpianto(impianto: Impianto) {
        uiState.update {
            it.copy(
                selectedImpianto = impianto,
                pdfFile = null,
                statusMessage = "${impianto.nomeCompleto} — seleziona",
                errorMessage = null
            )
        }
    }

    fun toggleImpiantoSelection(impiantoId: String, isSelected: Boolean) {
        val updatedSelection = uiState.value.impiantiSelezionati.toMutableMap()
        if (isSelected) {
            updatedSelection[impiantoId] = true
        } else {
            updatedSelection.remove(impiantoId)
        }
        uiState.update { state ->
            state.copy(impiantiSelezionati = updatedSelection)
        }
    }

    fun toggleImpiantiSelection(impiantoIds: List<String>, isSelected: Boolean) {
        uiState.update { state ->
            val updated = state.impiantiSelezionati.toMutableMap()
            impiantoIds.forEach { id ->
                if (isSelected) updated[id] = true else updated.remove(id)
            }
            state.copy(impiantiSelezionati = updated)
        }
    }

    fun updateFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) {
        uiState.update { state ->
            val updatedFrequenze = state.frequenzePerImpianto.toMutableMap()
            updatedFrequenze[impiantoId] = frequenza
            val updatedMulti = state.frequenzeSelezionatePerImpianto.toMutableMap()
            if (updatedMulti[impiantoId].isNullOrEmpty()) {
                updatedMulti[impiantoId] = setOf(frequenza)
            }
            val freqMap = state.selectedCantiere?.let { mapOf(it.id to updatedMulti) }
            val globalCal = recomputeGlobalCalendar(state, freqMap)
            state.copy(
                frequenzePerImpianto = updatedFrequenze,
                frequenzeSelezionatePerImpianto = updatedMulti,
                calendarioGlobale = globalCal
            )
        }
    }

    fun toggleFrequenzaForImpianto(impiantoId: String, frequenza: Periodo) {
        uiState.update { state ->
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

            val freqMap = state.selectedCantiere?.let { mapOf(it.id to updatedMulti) }
            val globalCal = recomputeGlobalCalendar(state, freqMap)

            state.copy(
                frequenzePerImpianto = updatedSingle,
                frequenzeSelezionatePerImpianto = updatedMulti,
                calendarioGlobale = globalCal
            )
        }
    }

    fun toggleFrequenzaForImpianti(impiantoIds: List<String>, frequenza: Periodo) {
        if (impiantoIds.isEmpty()) return
        uiState.update { state ->
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

            val freqMap = state.selectedCantiere?.let { mapOf(it.id to currentMulti) }
            val globalCal = recomputeGlobalCalendar(state, freqMap)
            state.copy(
                frequenzePerImpianto = currentSingle,
                frequenzeSelezionatePerImpianto = currentMulti,
                calendarioGlobale = globalCal
            )
        }
    }

    fun addImpiantiFromTemplates(templates: List<Impianto>) {
        val cantiereId = uiState.value.selectedCantiere?.id
        if (cantiereId == null) {
            uiState.update { it.copy(errorMessage = "Seleziona un cantiere prima di aggiungere attività di verifica") }
            return
        }
        if (templates.isEmpty()) return
        val nuoviImpianti = templates.map { instantiateTemplateForCantiere(it, cantiereId) }

        scope.launch {
            uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val (impiantiDelCantiere, tuttiImpianti) = withContext(ioDispatcher) {
                    nuoviImpianti.forEach { repository.salvaImpianto(it) }
                    repository.getImpiantiForCantiere(cantiereId) to repository.caricaImpianti()
                }
                val frequenzeMap = impiantiDelCantiere.associate { it.id to CrossPlantAttivitaResolver.resolveFrequenze(it, impiantiDelCantiere) }
                uiState.update { state ->
                    val globalCal = recomputeGlobalCalendar(state.copy(impianti = tuttiImpianti), null)
                    state.copy(
                        impianti = tuttiImpianti,
                        impiantiDelCantiere = if (state.selectedCantiere?.id == cantiereId) impiantiDelCantiere else state.impiantiDelCantiere,
                        frequenzeDisponibiliPerImpianto = frequenzeMap,
                        calendarioGlobale = globalCal,
                        isLoading = false,
                        statusMessage = "✓ ${nuoviImpianti.size} attività di verifica aggiunte: " +
                            nuoviImpianti.joinToString(", ") { imp -> imp.codIntervento },
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(isLoading = false, errorMessage = "Errore aggiunta attività di verifica: ${e.message}")
                }
            }
        }
    }

    fun createNewImpianto(template: Impianto? = null) {
        val baseImpianto = template?.let { instantiateTemplateForCantiere(it, uiState.value.selectedCantiere?.id) }
        val newImpianto = baseImpianto ?: ImpiantoStandard(
            id = UUID.randomUUID().toString(),
            codIntervento = "",
            nomeCompleto = "",
            premessa = null,
            listaAttivita = emptyList(),
            cantiereId = uiState.value.selectedCantiere?.id,
            quantita = 1
        )
        if (template != null) {
            saveImpianto(newImpianto)
        } else {
            uiState.update {
                it.copy(
                    selectedImpianto = newImpianto,
                    pdfFile = null,
                    statusMessage = "Nuovo impianto — compila i dati e salva",
                    errorMessage = null
                )
            }
        }
    }

    fun updateMqtSwitchType(impiantoId: String, switchType: MqtSwitchType) {
        val currentImpianto = uiState.value.impiantiDelCantiere.find { it.id == impiantoId } as? QuadroMQT ?: return
        val updated = currentImpianto.withTipoInterruttore(switchType)
        saveImpianto(updated)
    }

    fun updateEmergenzaSiglaDocumentale(impiantoId: String, sigla: EmergenzaSiglaDoc) {
        val currentImpianto = (uiState.value.impiantiDelCantiere.find { it.id == impiantoId }
            ?: uiState.value.impianti.find { it.id == impiantoId }) as? ImpiantoEmergenza ?: return
        val updated = currentImpianto.withSiglaDocumentale(sigla)
        saveImpianto(updated)
    }

    fun saveImpianto(impianto: Impianto) {
        val impiantoToSave = if (impianto is QuadroMQT && impianto.tipoInterruttore != null) {
            impianto.withTipoInterruttore(impianto.tipoInterruttore)
        } else {
            impianto
        }

        if (impiantoToSave.id.isBlank()) {
            uiState.update { it.copy(errorMessage = "Il codice intervento è obbligatorio") }
            return
        }
        if (impiantoToSave.nomeCompleto.isBlank()) {
            uiState.update { it.copy(errorMessage = "Il nome completo è obbligatorio") }
            return
        }

        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaImpianto(impiantoToSave) }
                val impiantiAggiornati = withContext(ioDispatcher) { repository.caricaImpianti() }
                loadImpiantiGlobali()
                val cantiereId = uiState.value.selectedCantiere?.id
                val impiantiDelCantiereAggiornati = cantiereId?.let {
                    withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) }
                } ?: emptyList()
                val frequenzeMap = impiantiDelCantiereAggiornati.associate { it.id to CrossPlantAttivitaResolver.resolveFrequenze(it, impiantiDelCantiereAggiornati) }

                uiState.update { state ->
                    val globalCal = recomputeGlobalCalendar(state.copy(impianti = impiantiAggiornati), null)
                    state.copy(
                        impianti = impiantiAggiornati,
                        impiantiDelCantiere = impiantiDelCantiereAggiornati,
                        frequenzeDisponibiliPerImpianto = frequenzeMap,
                        calendarioGlobale = globalCal,
                        selectedImpianto = impiantoToSave,
                        statusMessage = "✓ Impianto ${impiantoToSave.id} salvato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore salvataggio: ${e.message}")
                }
            }
        }
    }

    fun deleteImpianto(id: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.eliminaImpianto(id) }
                val impiantiAggiornati = withContext(ioDispatcher) { repository.caricaImpianti() }
                val cantiereId = uiState.value.selectedCantiere?.id
                val impiantiDelCantiereAggiornati = cantiereId?.let {
                    withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) }
                } ?: emptyList()
                val frequenzeMap = impiantiDelCantiereAggiornati.associate { it.id to CrossPlantAttivitaResolver.resolveFrequenze(it, impiantiDelCantiereAggiornati) }

                uiState.update { state ->
                    val isCurrent = state.selectedImpianto?.id == id
                    val updatedSelection = state.impiantiSelezionati.toMutableMap()
                    updatedSelection.remove(id)
                    val globalCal = recomputeGlobalCalendar(state.copy(impianti = impiantiAggiornati), null)

                    state.copy(
                        impianti = impiantiAggiornati,
                        impiantiDelCantiere = impiantiDelCantiereAggiornati,
                        frequenzeDisponibiliPerImpianto = frequenzeMap,
                        calendarioGlobale = globalCal,
                        selectedImpianto = if (isCurrent) null else state.selectedImpianto,
                        impiantiSelezionati = updatedSelection,
                        statusMessage = "✓ Impianto eliminato con successo",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore eliminazione impianto: ${e.message}") }
            }
        }
    }

    fun updateImpiantoGlobale(impianto: Impianto) {
        if (impianto.id.isBlank() || impianto.nomeCompleto.isBlank()) return

        scope.launch {
            try {
                withContext(ioDispatcher) { repository.aggiornaImpiantiGlobalmente(impianto) }
                loadImpiantiGlobali()
                val impiantiAggiornati = withContext(ioDispatcher) { repository.caricaImpianti() }
                uiState.update {
                    it.copy(
                        impianti = impiantiAggiornati,
                        selectedImpianto = impianto,
                        statusMessage = "✓ Impianto ${impianto.id} aggiornato globalmente",
                        errorMessage = null
                    )
                }
                uiState.value.selectedCantiere?.let { onImpiantiGlobaliUpdated(it.id, it.clienteId) }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore aggiornamento globale: ${e.message}")
                }
            }
        }
    }

    fun aggiungiInterruttoreAQuadro(quadro: QuadroBT, interruttore: InterruttoreBT) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }

                val nuovoInterruttore = interruttore.copy(
                    id = if (interruttore.id.isBlank()) UUID.randomUUID().toString() else interruttore.id,
                    quantita = interruttore.quantita.coerceAtLeast(1)
                )

                val updatedInterruttori = quadro.listaInterruttori + nuovoInterruttore
                val updatedQuadro = quadro.copy(listaInterruttori = updatedInterruttori)

                withContext(ioDispatcher) {
                    repository.salvaImpianto(updatedQuadro)

                    if (!nuovoInterruttore.produttore.isNullOrBlank() && !nuovoInterruttore.codiceArticolo.isNullOrBlank()) {
                        val nowIso = LocalDate.now().toString()
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
                }

                val approvatiAggiornati = withContext(ioDispatcher) { repository.caricaCatalogoApprovato() }
                val impiantiCantiere = quadro.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }

                val feedback = buildString {
                    append("✓ Componente '${nuovoInterruttore.nome}'")
                    if (!nuovoInterruttore.produttore.isNullOrBlank()) {
                        append(" (${nuovoInterruttore.produttore}")
                        if (!nuovoInterruttore.codiceArticolo.isNullOrBlank()) append(" ${nuovoInterruttore.codiceArticolo}")
                        append(")")
                    }
                    append(" aggiunto al quadro")
                }

                uiState.update { state ->
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
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta componente: ${e.message}") }
            }
        }
    }

    fun aggiungiLampadaAEmergenza(impianto: ImpiantoEmergenza, lampada: LampadaEmergenza) {
        aggiungiLampadeAEmergenza(impianto, listOf(lampada))
    }

    fun aggiungiLampadeAEmergenza(impianto: ImpiantoEmergenza, lampade: List<LampadaEmergenza>) {
        if (lampade.isEmpty()) return
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }

                val updatedImpianto = impianto.copy(
                    listaLampade = impianto.listaLampade + lampade
                )

                withContext(ioDispatcher) { repository.salvaImpianto(updatedImpianto) }

                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }

                val feedback = if (lampade.size == 1) {
                    val label = lampade.first().sigla.ifBlank { lampade.first().modello }
                    "✓ Lampada '$label' aggiunta all'impianto di emergenza"
                } else {
                    val modelloFirst = lampade.first().modello
                    val marcaFirst = lampade.first().produttore?.let { " $it" } ?: ""
                    "✓ Aggiunte ${lampade.size} lampade$marcaFirst '$modelloFirst' all'impianto di emergenza"
                }

                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedImpianto,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = feedback
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta lampade: ${e.message}") }
            }
        }
    }

    fun aggiornaLampadaInEmergenza(impianto: ImpiantoEmergenza, lampadaAggiornata: LampadaEmergenza) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }

                val updatedImpianto = impianto.copy(
                    listaLampade = impianto.listaLampade.map {
                        if (it.id == lampadaAggiornata.id) lampadaAggiornata else it
                    }
                )

                withContext(ioDispatcher) { repository.salvaImpianto(updatedImpianto) }

                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }

                val label = lampadaAggiornata.sigla.ifBlank { lampadaAggiornata.modello }
                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updatedImpianto,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Lampada '$label' modificata con successo"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore modifica lampada: ${e.message}") }
            }
        }
    }

    fun sostituisciProduttoreComponente(quadro: QuadroBT, interruttoreId: String, nuovaVariante: VarianteProdotto) {
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

                withContext(ioDispatcher) { repository.salvaImpianto(updatedQuadro) }
                val impiantiCantiere = quadro.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()

                uiState.update { state ->
                    state.copy(
                        selectedImpianto = updatedQuadro,
                        impiantiDelCantiere = impiantiCantiere,
                        statusMessage = "✓ Sostituito con ${nuovaVariante.produttore} ${nuovaVariante.codice}"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore sostituzione: ${e.message}") }
            }
        }
    }

    fun aggiungiDispositivoAdAntincendio(impianto: RilevazioneAntincendio, dispositivo: DispositivoRilevazione) {
        aggiungiDispositiviAdAntincendio(impianto, listOf(dispositivo))
    }

    fun aggiungiDispositiviAdAntincendio(impianto: RilevazioneAntincendio, dispositivi: List<DispositivoRilevazione>) {
        if (dispositivi.isEmpty()) return
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(listaDispositivi = impianto.listaDispositivi + dispositivi)
                withContext(ioDispatcher) { repository.salvaImpianto(updated) }
                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }
                val msg = if (dispositivi.size == 1) {
                    "✓ Componente '${dispositivi.first().indirizzo}' aggiunto a Rilevazione Incendi"
                } else {
                    "✓ Aggiunti ${dispositivi.size} componenti a Rilevazione Incendi"
                }
                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = msg
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore aggiunta componenti: ${e.message}") }
            }
        }
    }

    fun aggiornaDispositivoInAntincendio(impianto: RilevazioneAntincendio, dispositivoAggiornato: DispositivoRilevazione) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(
                    listaDispositivi = impianto.listaDispositivi.map {
                        if (it.id == dispositivoAggiornato.id) dispositivoAggiornato else it
                    }
                )
                withContext(ioDispatcher) { repository.salvaImpianto(updated) }
                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }
                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Dispositivo '${dispositivoAggiornato.indirizzo}' aggiornato"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore modifica dispositivo: ${e.message}") }
            }
        }
    }

    fun eliminaDispositivoDaAntincendio(impianto: RilevazioneAntincendio, dispositivoId: String) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(
                    listaDispositivi = impianto.listaDispositivi.filter { it.id != dispositivoId }
                )
                withContext(ioDispatcher) { repository.salvaImpianto(updated) }
                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }
                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Dispositivo rimosso da Rilevazione Incendi"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore eliminazione dispositivo: ${e.message}") }
            }
        }
    }

    fun svuotaDispositiviAntincendio(impianto: RilevazioneAntincendio) {
        scope.launch {
            try {
                uiState.update { it.copy(isLoading = true) }
                val updated = impianto.copy(listaDispositivi = emptyList())
                withContext(ioDispatcher) { repository.salvaImpianto(updated) }
                val impiantiCantiere = impianto.cantiereId?.let { withContext(ioDispatcher) { repository.getImpiantiForCantiere(it) } } ?: emptyList()
                val tuttiImpianti = withContext(ioDispatcher) { repository.caricaImpianti() }
                uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        selectedImpianto = updated,
                        impiantiDelCantiere = impiantiCantiere,
                        impianti = tuttiImpianti,
                        statusMessage = "✓ Registro componenti antincendio svuotato"
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(isLoading = false, errorMessage = "Errore svuotamento registro: ${e.message}") }
            }
        }
    }

    companion object {
        fun instantiateTemplateForCantiere(template: Impianto, cantiereId: String?): Impianto {
            val copy = template.copyWithBasicParams(
                id = UUID.randomUUID().toString(),
                cantiereId = cantiereId,
                quantita = 1
            )
            return if (copy is QuadroMQT) copy.withTipoInterruttore(copy.tipoInterruttore ?: MqtSwitchType.SF6) else copy
        }
    }
}
