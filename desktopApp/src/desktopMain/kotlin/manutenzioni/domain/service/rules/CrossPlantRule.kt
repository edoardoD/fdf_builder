package manutenzioni.domain.service.rules

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo

/**
 * Strategy interface per la risoluzione contestuale delle attività e delle frequenze
 * di un impianto in funzione degli altri impianti presenti nel cantiere (Cross-Plant Discovery).
 */
interface CrossPlantRule {
    /**
     * Determina se questa regola è applicabile all'impianto specificato.
     *
     * @param impianto L'impianto in esame.
     * @return `true` se la regola deve gestire l'impianto, `false` altrimenti.
     */
    fun canHandle(impianto: Impianto): Boolean

    /**
     * Risolve la lista ordinata e rinumerata (1..N) delle attività manutentive applicabili
     * per l'impianto in esame nel contesto del cantiere e per la frequenza richiesta.
     *
     * @param impianto L'impianto in esame.
     * @param impiantiNelCantiere La lista completa degli impianti configurati nel cantiere.
     * @param frequenza La frequenza periodica di intervento selezionata.
     * @return Lista di [Attivita] filtrate, arricchite e rinumerate 1..N.
     */
    fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita>

    /**
     * Risolve le frequenze di intervento disponibili per l'impianto in esame,
     * tenendo conto degli impianti effettivamente presenti nel cantiere.
     *
     * @param impianto L'impianto in esame.
     * @param impiantiNelCantiere La lista completa degli impianti configurati nel cantiere.
     * @return Lista di [Periodo] disponibili ordinata crescentemente per [Periodo.inMesi].
     */
    fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo>
}
