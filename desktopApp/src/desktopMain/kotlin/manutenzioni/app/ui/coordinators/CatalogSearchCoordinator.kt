package manutenzioni.app.ui.coordinators

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import manutenzioni.app.ui.ManutenzioniUiState
import manutenzioni.domain.ManutenzioneRepository

class CatalogSearchCoordinator(
    private val repository: ManutenzioneRepository,
    private val uiState: MutableStateFlow<ManutenzioniUiState>,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun loadComponentiStandard() {
        scope.launch {
            try {
                val comp = withContext(ioDispatcher) { repository.caricaComponentiStandard() }
                uiState.update { it.copy(componentiStandard = comp) }
            } catch (_: Exception) {}
        }
    }

    fun loadCatalogoApprovato() {
        scope.launch {
            try {
                val approvati = withContext(ioDispatcher) { repository.caricaCatalogoApprovato() }
                uiState.update { it.copy(catalogoApprovato = approvati) }
            } catch (_: Exception) {}
        }
    }
}
