package manutenzioni.domain.service.rules

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.FrequencyFilter

/**
 * Regola predefinita di Cross-Plant Discovery per qualsiasi impianto standard o specializzato
 * che non possieda una regola dedicata.
 *
 * Filtra le attività per frequenza inclusiva (formula core F.inMesi() % A.inMesi() == 0)
 * ed esclude le attività aventi un [Attivita.targetImpiantoCod] non presente tra i codici
 * degli impianti del cantiere.
 */
class DefaultCrossPlantRule : CrossPlantRule {

    override fun canHandle(impianto: Impianto): Boolean = true

    override fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita> {
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        // 1. Filtro base per frequenza inclusiva
        val attivitaFrequenzaOk = FrequencyFilter.filterByFrequenza(impianto.listaAttivita, frequenza)

        // Se non contiene alcuna attività condizionata, restituisce direttamente la lista rinumerata 1..N
        if (impianto.listaAttivita.none { it.targetImpiantoCod != null }) {
            return attivitaFrequenzaOk.mapIndexed { index, att -> att.copy(nAttivita = index + 1) }
        }

        // 2. Filtro contestuale in base agli impianti presenti nel cantiere e rinumerazione 1..N
        return attivitaFrequenzaOk.filter { att ->
            val target = att.targetImpiantoCod?.trim()?.uppercase()
            target == null || target in codiciPresenti
        }.mapIndexed { index, att -> att.copy(nAttivita = index + 1) }
    }

    override fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo> {
        if (impianto.listaAttivita.none { it.targetImpiantoCod != null }) {
            return FrequencyFilter.frequenzeDisponibili(impianto.listaAttivita)
        }

        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        // Filtra le attività contestualmente, poi estrae le frequenze distinte
        val attivitaApplicabili = impianto.listaAttivita.filter { att ->
            val target = att.targetImpiantoCod?.trim()?.uppercase()
            target == null || target in codiciPresenti
        }

        return FrequencyFilter.frequenzeDisponibili(attivitaApplicabili)
    }
}
