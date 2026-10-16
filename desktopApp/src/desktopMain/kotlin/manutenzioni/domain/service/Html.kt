package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo

interface Html {
    /**
     * Genera l'HTML completo con i dati dell'impianto e le attività filtrate.
     *
     * @param impianto L'impianto selezionato
     * @param attivitaFiltrate Le attività filtrate in base alla frequenza
     * @param frequenza La frequenza dell'intervento
     * @param clienteNome Nome del cliente da iniettare nell'header (opzionale)
     * @return il contenuto HTML come stringa
     */
    fun buildHtml(
        impianto: Impianto,
        attivitaFiltrate: List<Attivita>,
        frequenza: Periodo,
        clienteNome: String? = null,
        cantiereNome: String? = null,
        dataInizioIntervento: String? = null,
        nomeManutentore: String? = null,
        firmaManutentore: String? = null
    ): String
}