package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoRilevazione
import manutenzioni.domain.model.Periodo

/**
 * Resolver per calcolare le attività effettive e le frequenze per i sistemi di
 * Rilevazione e Allarme (Rilevazione Incendi "RI", Rilevazione Gas "RIG", ecc.)
 * o qualsiasi impianto con attività condizionate (Cross-Plant Discovery)
 * in base alla composizione impiantistica presente nel cantiere.
 */
object CrossPlantAttivitaResolver {

    /**
     * Determina se un impianto richiede la risoluzione contestuale Cross-Plant.
     * È vero per [ImpiantoRilevazione] (RI, RIG) o per qualsiasi impianto
     * che contenga almeno un'attività che specifica un [Attivita.targetImpiantoCod].
     */
    fun isCrossPlant(impianto: Impianto): Boolean {
        return impianto is ImpiantoRilevazione ||
                impianto.listaAttivita.any { it.targetImpiantoCod != null }
    }

    /**
     * Risolve le attività effettive per l'impianto in esame.
     * Applica prima il filtro di frequenza inclusiva, poi il filtro contestuale
     * escludendo le attività con [Attivita.targetImpiantoCod] non presente nel cantiere.
     *
     * @param impianto L'impianto in esame (es. RI, RIG o qualsiasi impianto con vincoli cross-plant)
     * @param impiantiNelCantiere Tutti gli impianti presenti nel cantiere
     * @param frequenza La frequenza selezionata per la generazione
     * @return Lista delle attività filtrate
     */
    fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita> {
        // 1. Filtro base per frequenza inclusiva
        val attivitaFrequenzaOk = FrequencyFilter.filterByFrequenza(impianto.listaAttivita, frequenza)

        // Se non contiene alcuna attività condizionata, restituisce direttamente la lista
        if (impianto.listaAttivita.none { it.targetImpiantoCod != null }) {
            return attivitaFrequenzaOk
        }

        // 2. Filtro contestuale in base agli impianti presenti nel cantiere
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        return attivitaFrequenzaOk.filter { att ->
            val target = att.targetImpiantoCod?.trim()?.uppercase()
            target == null || target in codiciPresenti
        }
    }

    /**
     * Calcola le frequenze disponibili per un impianto, tenendo conto
     * degli impianti effettivamente presenti nel cantiere.
     * Esclude le frequenze che deriverebbero solo da attività condizionate
     * i cui impianti target non sono presenti nel cantiere.
     */
    fun resolveFrequenze(
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
