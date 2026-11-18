package manutenzioni.domain.service.rules

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.service.FrequencyFilter

/**
 * Regola di dominio per i sistemi di Rilevazione Incendi ("RI").
 *
 * Applica le seguenti regole normative e operative:
 * 1. Attività istituzionali di centrale e linee: fino a 10 attività base a frequenza semestrale (6M)
 *    oppure filtrate per frequenze inferiori.
 * 2. Prove cross-plant condizionate: include le prove su altri impianti del cantiere
 *    (es. sgancio GE, PEM, interfacce RIG) solo se l'impianto target è presente nel cantiere.
 * 3. Frequenze disponibili: include sempre la frequenza semestrale (6M).
 */
class FireAlarmCrossPlantRule : CrossPlantRule {

    override fun canHandle(impianto: Impianto): Boolean {
        return impianto is RilevazioneAntincendio ||
                impianto.codIntervento.trim().equals("RI", ignoreCase = true)
    }

    override fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita> {
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        // 1. Attività istituzionali di centrale e linee (fino a 10 attività a 6M)
        val istituzionali = if (frequenza.inMesi() >= 6) {
            val base = impianto.listaAttivita.filter { it.targetImpiantoCod == null }
            if (base.size >= 10) base.take(10) else base
        } else {
            FrequencyFilter.filterByFrequenza(
                impianto.listaAttivita.filter { it.targetImpiantoCod == null },
                frequenza
            )
        }

        // 2. Attività cross-plant condizionate dagli altri impianti presenti nel cantiere
        val crossPlant = impianto.listaAttivita
            .filter { it.targetImpiantoCod != null && it.targetImpiantoCod.trim().uppercase() in codiciPresenti }
            .let { FrequencyFilter.filterByFrequenza(it, frequenza) }

        // 3. Concatenazione e rinumerazione progressiva (1..N)
        val unificate = istituzionali + crossPlant
        return unificate.mapIndexed { index, att -> att.copy(nAttivita = index + 1) }
    }

    override fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo> {
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()
        val crossPlant = impianto.listaAttivita.filter {
            val target = it.targetImpiantoCod?.trim()?.uppercase()
            target == null || target in codiciPresenti
        }
        val freqs = FrequencyFilter.frequenzeDisponibili(crossPlant).toMutableList()
        val semestrale = Periodo(TipoPeriodo.M, 6)
        if (!freqs.contains(semestrale)) {
            freqs.add(semestrale)
        }
        return freqs.sortedBy { it.inMesi() }
    }
}
