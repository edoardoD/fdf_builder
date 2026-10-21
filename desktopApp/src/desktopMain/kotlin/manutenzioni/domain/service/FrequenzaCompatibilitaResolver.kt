package manutenzioni.domain.service

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo

/**
 * Dettaglio della discrepanza di frequenza rilevata per un singolo impianto.
 */
data class DiscrepanzaFrequenzaInfo(
    val impiantoId: String,
    val codIntervento: String,
    val nomeCompleto: String,
    val frequenzaMassima: Periodo,
    val frequenzeDisponibili: List<Periodo>
)

/**
 * Report di analisi delle discrepanze tra la frequenza scelta a calendario
 * e le frequenze ammissibili/massime dei singoli impianti coinvolti.
 */
data class DiscrepanzaFrequenzaReport(
    val discrepanze: List<DiscrepanzaFrequenzaInfo>,
    val frequenzaMinimaTraImpianti: Periodo,
    val frequenzeConsigliatePerImpianto: Map<String, Periodo>
) {
    val hasDiscrepanze: Boolean get() = discrepanze.isNotEmpty()
}

/**
 * Servizio di dominio per l'analisi della compatibilità delle periodicità e il rilevamento
 * di discrepanze tra la frequenza scelta e i vincoli normativi / massimi dei singoli impianti.
 *
 * Esempio: se si pianifica a 1 Anno (12 Mesi) ma tra gli impianti è presente l'Illuminazione di Emergenza (EM)
 * la cui frequenza massima prevista da norma UNI 11222 è 6 Mesi, il resolver intercetta la discrepanza
 * e propone l'assegnazione contestuale differenziata (EM a 6 Mesi, Quadri a 1 Anno).
 */
object FrequenzaCompatibilitaResolver {

    /**
     * Calcola la frequenza massima supportata da un [impianto] tenendo conto del contesto impiantistico del cantiere.
     */
    fun getFrequenzaMassima(impianto: Impianto, contextImpianti: List<Impianto>): Periodo {
        val freqs = CrossPlantAttivitaResolver.resolveFrequenze(impianto, contextImpianti)
        return freqs.maxByOrNull { it.inMesi() } ?: Periodo(TipoPeriodo.A, 1)
    }

    /**
     * Analizza la lista degli impianti selezionati rispetto a una [frequenzaScelta].
     *
     * Identifica tutti gli impianti la cui frequenza massima supportata è inferiore alla frequenza scelta,
     * calcola la frequenza minima comune tra tutti gli impianti e mappa per ciascun impianto la frequenza
     * contestuale consigliata (cap alla frequenza massima per chi ha discrepanze, [frequenzaScelta] per gli altri).
     */
    fun analizzaDiscrepanze(
        frequenzaScelta: Periodo,
        impiantiSelezionati: List<Impianto>,
        contextImpianti: List<Impianto>
    ): DiscrepanzaFrequenzaReport {
        if (impiantiSelezionati.isEmpty()) {
            return DiscrepanzaFrequenzaReport(
                discrepanze = emptyList(),
                frequenzaMinimaTraImpianti = frequenzaScelta,
                frequenzeConsigliatePerImpianto = emptyMap()
            )
        }

        val discrepanze = mutableListOf<DiscrepanzaFrequenzaInfo>()
        val frequenzeConsigliate = mutableMapOf<String, Periodo>()
        var minFrequenzaMesi = frequenzaScelta.inMesi()
        var minFrequenzaPeriodo = frequenzaScelta

        for (imp in impiantiSelezionati) {
            val freqs = CrossPlantAttivitaResolver.resolveFrequenze(imp, contextImpianti)
            val maxFreq = freqs.maxByOrNull { it.inMesi() } ?: Periodo(TipoPeriodo.A, 1)

            if (maxFreq.inMesi() < minFrequenzaMesi) {
                minFrequenzaMesi = maxFreq.inMesi()
                minFrequenzaPeriodo = maxFreq
            }

            if (frequenzaScelta.inMesi() > maxFreq.inMesi()) {
                discrepanze.add(
                    DiscrepanzaFrequenzaInfo(
                        impiantoId = imp.id,
                        codIntervento = imp.codIntervento,
                        nomeCompleto = imp.nomeCompleto,
                        frequenzaMassima = maxFreq,
                        frequenzeDisponibili = freqs
                    )
                )
                // Consigliata: la massima ammissibile per questo impianto
                frequenzeConsigliate[imp.id] = maxFreq
            } else {
                frequenzeConsigliate[imp.id] = frequenzaScelta
            }
        }

        return DiscrepanzaFrequenzaReport(
            discrepanze = discrepanze,
            frequenzaMinimaTraImpianti = minFrequenzaPeriodo,
            frequenzeConsigliatePerImpianto = frequenzeConsigliate
        )
    }
}
