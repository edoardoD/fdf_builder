package manutenzioni.domain.service

import manutenzioni.domain.model.ComponentCandidate

/**
 * Contratto del domain layer per la ricerca esterna di componenti elettrici via API.
 * Identifica la classe ETIM, le caratteristiche tecniche e le varianti commerciali.
 */
interface ProductSearchApi {
    /**
     * Esegue una ricerca live basata su parole chiave o codice fornitore.
     * Restituisce la lista dei candidati con le relative varianti commerciali.
     */
    suspend fun search(query: String): List<ComponentCandidate>
}
