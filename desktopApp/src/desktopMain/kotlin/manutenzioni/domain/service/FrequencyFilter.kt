package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo

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
     * Le attività sono ordinate per periodicità canonica crescente:
     * prima i Mesi ("01".."06"), poi gli Anni ("1".."n"), preservando l'ordine relativo a parità di frequenza.
     */
    fun filterByFrequenza(
        attivita: List<Attivita>,
        frequenzaSelezionata: Periodo
    ): List<Attivita> {
        val mesiTarget = frequenzaSelezionata.inMesi()
        return attivita
            .filter { mesiTarget % it.frequenza.inMesi() == 0 }
            .sortedWith(
                compareBy<Attivita> { it.frequenza.inMesi() }
                    .thenBy { if (it.frequenza.tipo == TipoPeriodo.M) 0 else 1 }
                    .thenBy { it.frequenza.valore }
                    .thenBy { it.nAttivita }
            )
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

