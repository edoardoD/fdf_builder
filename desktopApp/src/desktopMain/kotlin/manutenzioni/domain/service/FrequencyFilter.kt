package manutenzioni.domain.service

import manutenzioni.app.data.Attivita
import manutenzioni.app.data.Periodo

/**
 * Logica di frequenza inclusiva.
 *
 * Un intervento con frequenza F include un'attività A se:
 *   F.inMesi() % A.frequenza.inMesi() == 0
 *
 * Esempio: frequenza 12 mesi include attività da 1, 2, 3, 4, 6, 12 mesi
 *          frequenza 6 mesi include attività da 1, 2, 3, 6 mesi
 *          frequenza 3 mesi include attività da 1, 3 mesi
 */
object FrequencyFilter {

    /**
     * Filtra le attività incluse nella frequenza selezionata.
     * Le attività sono ordinate per frequenza crescente e poi per numero attività.
     */
    fun filterByFrequenza(
        attivita: List<Attivita>,
        frequenzaSelezionata: Periodo
    ): List<Attivita> {
        val mesiTarget = frequenzaSelezionata.inMesi()
        return attivita
            .filter { mesiTarget % it.frequenza.inMesi() == 0 }
            .sortedWith(compareBy({ it.frequenza.inMesi() }, { it.nAttivita }))
    }

    /**
     * Estrae tutte le frequenze distinte presenti nelle attività di un impianto,
     * ordinate per numero di mesi crescente.
     */
    fun frequenzeDisponibili(attivita: List<Attivita>): List<Periodo> {
        return attivita
            .map { it.frequenza }
            .distinctBy { it.inMesi() }
            .sortedBy { it.inMesi() }
    }
}

