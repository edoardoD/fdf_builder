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
import manutenzioni.app.ui.platform.DirectoryPicker
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.*
import java.util.Base64

class AnagraficaCoordinator(
    private val repository: ManutenzioneRepository,
    private val uiState: MutableStateFlow<ManutenzioniUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val directoryPicker: DirectoryPicker,
    private val onCantiereSelected: (Cantiere?) -> Unit,
    private val onRefresh: () -> Unit,
    private val recomputeGlobalCalendar: (state: ManutenzioniUiState, freqMap: Map<String, Map<String, Set<Periodo>>>?) -> List<MeseScadenzaGlobaleGroup>
) {

    fun loadClienti() {
        scope.launch {
            try {
                val clienti = withContext(ioDispatcher) { repository.caricaClienti() }
                uiState.update { it.copy(clienti = clienti) }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore caricamento clienti: ${e.message}")
                }
            }
        }
    }

    fun selectCliente(cliente: Cliente) {
        uiState.update {
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

    fun addCliente(cliente: Cliente) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaCliente(cliente) }
                val clienti = withContext(ioDispatcher) { repository.caricaClienti() }
                uiState.update {
                    it.copy(
                        clienti = clienti,
                        selectedCliente = cliente,
                        statusMessage = "✓ Cliente ${cliente.nome} aggiunto e selezionato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore salvataggio cliente: ${e.message}")
                }
            }
        }
    }

    fun renameCliente(id: String, newName: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.updateCliente(id, newName) }
                onRefresh()
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore ridenominazione cliente: ${e.message}") }
            }
        }
    }

    fun updateCliente(cliente: Cliente) {
        if (cliente.nome.isBlank()) {
            uiState.update { it.copy(errorMessage = "Il nome del cliente è obbligatorio") }
            return
        }
        val normalized = cliente.copy(
            nome = cliente.nome.trim(),
            indirizzo = cliente.indirizzo?.trim()?.ifBlank { null },
            partitaIva = cliente.partitaIva?.trim()?.ifBlank { null }
        )
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaCliente(normalized) }
                val clienti = withContext(ioDispatcher) { repository.caricaClienti() }
                uiState.update {
                    it.copy(
                        clienti = clienti,
                        selectedCliente = if (it.selectedCliente?.id == normalized.id) normalized else it.selectedCliente,
                        statusMessage = "✓ Cliente ${normalized.nome} aggiornato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore aggiornamento cliente: ${e.message}") }
            }
        }
    }

    fun deleteCliente(id: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.eliminaCliente(id) }
                val clienti = withContext(ioDispatcher) { repository.caricaClienti() }
                uiState.update { state ->
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
                uiState.update { it.copy(errorMessage = "Errore eliminazione cliente: ${e.message}") }
            }
        }
    }

    fun loadCantieriForCliente(clienteId: String) {
        scope.launch {
            try {
                val cantieri = withContext(ioDispatcher) { repository.getCantieriForCliente(clienteId) }
                uiState.update { it.copy(cantieriDisponibili = cantieri) }
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore caricamento cantieri: ${e.message}")
                }
            }
        }
    }

    fun loadTuttiCantieri() {
        scope.launch {
            try {
                val clienti = withContext(ioDispatcher) { repository.caricaClienti() }
                val tuttiCantieri = withContext(ioDispatcher) {
                    clienti.flatMap { cli -> repository.getCantieriForCliente(cli.id) }
                }
                uiState.update { state ->
                    val globalCal = recomputeGlobalCalendar(state.copy(clienti = clienti, tuttiCantieri = tuttiCantieri), null)
                    state.copy(
                        clienti = clienti,
                        tuttiCantieri = tuttiCantieri,
                        calendarioGlobale = globalCal
                    )
                }
            } catch (_: Exception) {}
        }
    }

    fun selectCantiere(cantiere: Cantiere?) {
        if (cantiere == null) {
            uiState.update {
                it.copy(
                    selectedCantiere = null,
                    impiantiDelCantiere = emptyList(),
                    impiantiSelezionati = emptyMap(),
                    sessioniVerificaCantiere = emptyList(),
                    statusMessage = "Seleziona un cantiere."
                )
            }
            onCantiereSelected(null)
            return
        }
        uiState.update {
            it.copy(
                selectedCantiere = cantiere,
                selectedImpianto = null,
                impiantiDelCantiere = emptyList(),
                impiantiSelezionati = emptyMap(),
                sessioniVerificaCantiere = emptyList(),
                statusMessage = "Cantiere: ${cantiere.nome}. Seleziona gli impianti.",
                errorMessage = null
            )
        }
        onCantiereSelected(cantiere)
    }

    fun addCantiere(cantiere: Cantiere) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaCantiere(cantiere) }
                val cantieri = withContext(ioDispatcher) { repository.getCantieriForCliente(cantiere.clienteId) }
                uiState.update {
                    it.copy(
                        cantieriDisponibili = cantieri,
                        selectedCantiere = cantiere,
                        statusMessage = "✓ Cantiere ${cantiere.nome} aggiunto",
                        errorMessage = null
                    )
                }
                loadTuttiCantieri()
            } catch (e: Exception) {
                uiState.update {
                    it.copy(errorMessage = "Errore salvataggio cantiere: ${e.message}")
                }
            }
        }
    }

    fun renameCantiere(id: String, newName: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.updateCantiere(id, newName) }
                uiState.value.selectedCliente?.id?.let { loadCantieriForCliente(it) }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore ridenominazione cantiere: ${e.message}") }
            }
        }
    }

    fun updateCantiere(id: String, nome: String, indirizzo: String?) {
        if (nome.isBlank()) {
            uiState.update { it.copy(errorMessage = "Il nome del cantiere è obbligatorio") }
            return
        }
        val state = uiState.value
        val current = state.cantieriDisponibili.find { it.id == id }
            ?: state.selectedCantiere?.takeIf { it.id == id }
            ?: return
        val updated = current.copy(
            nome = nome.trim(),
            indirizzo = indirizzo?.trim()?.ifBlank { null }
        )
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaCantiere(updated) }
                val cantieri = withContext(ioDispatcher) { repository.getCantieriForCliente(updated.clienteId) }
                uiState.update {
                    it.copy(
                        cantieriDisponibili = if (it.selectedCliente?.id == updated.clienteId) cantieri else it.cantieriDisponibili,
                        selectedCantiere = if (it.selectedCantiere?.id == updated.id) updated else it.selectedCantiere,
                        statusMessage = "✓ Cantiere ${updated.nome} aggiornato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore aggiornamento cantiere: ${e.message}") }
            }
        }
    }

    fun updateDatiInterventoCantiere(
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?,
        manutentore: String?
    ) {
        val currentCantiere = uiState.value.selectedCantiere ?: return
        val dataInterventoNormalized = dataIntervento?.trim()?.ifBlank { null }
        val updated = currentCantiere.copy(
            dataInizioContratto = dataInizioContratto?.trim()?.ifBlank { null },
            dataIntervento = dataInterventoNormalized,
            dataInterventoPrecedente = dataInterventoPrecedente?.trim()?.ifBlank { null },
            nomeManutentore = manutentore?.trim()?.ifBlank { null }
        )
        uiState.update { state ->
            val updatedTutti = state.tuttiCantieri.map { if (it.id == updated.id) updated else it }
            val freqMap = mapOf(updated.id to state.frequenzeSelezionatePerImpianto)
            val globalCal = recomputeGlobalCalendar(
                state.copy(tuttiCantieri = updatedTutti),
                freqMap
            )
            state.copy(
                selectedCantiere = updated,
                cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == updated.id) updated else it },
                tuttiCantieri = updatedTutti,
                calendarioGlobale = globalCal
            )
        }
        scope.launch(ioDispatcher) {
            try {
                repository.salvaCantiere(updated)
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore salvataggio dati intervento: ${e.message}") }
            }
        }
    }

    fun updateFirmaManutentoreCantiere(firma: String?) {
        val currentCantiere = uiState.value.selectedCantiere ?: return
        val normalized = firma?.trim()?.ifBlank { null }
        val updated = currentCantiere.copy(firmaManutentore = normalized)
        uiState.update { state ->
            state.copy(
                selectedCantiere = updated,
                cantieriDisponibili = state.cantieriDisponibili.map { if (it.id == updated.id) updated else it },
                statusMessage = if (normalized != null) "✓ Firma digitale del tecnico associata al cantiere" else "✓ Firma digitale rimossa"
            )
        }
        scope.launch(ioDispatcher) {
            try {
                repository.salvaCantiere(updated)
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore salvataggio firma tecnico: ${e.message}") }
            }
        }
    }

    fun selectFirmaManutentoreFile() {
        try {
            val selectedFile = directoryPicker.pickFile(
                title = "Seleziona immagine firma tecnico (PNG/JPG)",
                allowedExtensions = listOf("png", "jpg", "jpeg", "gif")
            )
            if (selectedFile != null && selectedFile.exists() && selectedFile.isFile) {
                val bytes = selectedFile.readBytes()
                val ext = selectedFile.extension.lowercase()
                val mime = when (ext) {
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    else -> "image/png"
                }
                val b64 = Base64.getEncoder().encodeToString(bytes)
                updateFirmaManutentoreCantiere("data:$mime;base64,$b64")
            }
        } catch (e: Exception) {
            uiState.update { it.copy(errorMessage = "Errore caricamento immagine firma: ${e.message}") }
        }
    }

    fun loadUtenti() {
        scope.launch {
            try {
                val utenti = withContext(ioDispatcher) { repository.caricaUtenti() }
                if (utenti.isNotEmpty()) {
                    uiState.update { state ->
                        val active = utenti.find { it.id == state.utenteAttivo.id } ?: utenti.first()
                        state.copy(utenti = utenti, utenteAttivo = active)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun selectUtenteAttivo(utente: Utente) {
        uiState.update { state ->
            val isTecnico = utente.ruolo == RuoloUtente.TECNICO
            val cantiereConsentito = !isTecnico ||
                (state.selectedCantiere != null && state.selectedCantiere.id in utente.cantieriAssegnatiIds)
            val nextSection = if (isTecnico && state.currentSection == AppSection.DATABASE) {
                AppSection.OPERATIVITA
            } else {
                state.currentSection
            }
            val globalCal = recomputeGlobalCalendar(state.copy(utenteAttivo = utente), null)
            state.copy(
                utenteAttivo = utente,
                currentSection = nextSection,
                selectedCantiere = if (cantiereConsentito) state.selectedCantiere else null,
                impiantiDelCantiere = if (cantiereConsentito) state.impiantiDelCantiere else emptyList(),
                calendarioGlobale = globalCal,
                statusMessage = "Profilo attivo: ${utente.nomeCompleto} (${utente.ruolo.label})",
                errorMessage = null
            )
        }
    }

    fun saveUtente(utente: Utente) {
        if (utente.nomeCompleto.isBlank() || utente.username.isBlank()) {
            uiState.update { it.copy(errorMessage = "Username e nome completo sono obbligatori") }
            return
        }
        val normalized = utente.copy(
            username = utente.username.trim(),
            nomeCompleto = utente.nomeCompleto.trim()
        )
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.salvaUtente(normalized) }
                val utenti = withContext(ioDispatcher) { repository.caricaUtenti() }
                uiState.update { state ->
                    val updatedActive = if (state.utenteAttivo.id == normalized.id) normalized else state.utenteAttivo
                    state.copy(
                        utenti = utenti,
                        utenteAttivo = updatedActive,
                        statusMessage = "✓ Utente ${normalized.nomeCompleto} (${normalized.ruolo.label}) salvato",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore salvataggio utente: ${e.message}") }
            }
        }
    }

    fun deleteUtente(utenteId: String) {
        scope.launch {
            try {
                withContext(ioDispatcher) { repository.eliminaUtente(utenteId) }
                val utenti = withContext(ioDispatcher) { repository.caricaUtenti() }
                uiState.update { state ->
                    val active = utenti.find { it.id == state.utenteAttivo.id } ?: utenti.firstOrNull() ?: state.utenteAttivo
                    state.copy(
                        utenti = utenti,
                        utenteAttivo = active,
                        statusMessage = "✓ Utente rimosso",
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                uiState.update { it.copy(errorMessage = "Errore eliminazione utente: ${e.message}") }
            }
        }
    }
}
