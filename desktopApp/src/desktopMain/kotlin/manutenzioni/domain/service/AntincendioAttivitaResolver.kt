package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.RilevazioneGas

/**
 * Resolver per calcolare le attività effettive e le frequenze per i sistemi di
 * Rilevazione e Allarme (Rilevazione Incendi "RI" e Rilevazione Gas "RIG")
 * o qualsiasi impianto con attività condizionate (Cross-Plant Discovery)
 * in base alla composizione impiantistica del cantiere.
 */
object AntincendioAttivitaResolver {

    /** Codici intervento noti soggetti a Cross-Plant Discovery */
    val CROSS_PLANT_CODICI = setOf("RI", "RIG")

    /**
     * Determina se un impianto richiede la risoluzione contestuale Cross-Plant.
     * È vero per RI, RIG, le istanze di RilevazioneAntincendio/RilevazioneGas,
     * oppure per qualsiasi impianto con almeno un'attività che specifica `targetImpiantoCod`.
     */
    fun isCrossPlant(impianto: Impianto): Boolean {
        val cod = impianto.codIntervento.trim().uppercase()
        return cod in CROSS_PLANT_CODICI ||
                impianto is RilevazioneAntincendio ||
                impianto is RilevazioneGas ||
                impianto.listaAttivita.any { it.targetImpiantoCod != null }
    }

    /**
     * Risolve le attività effettive per l'impianto in esame.
     * Applica prima il filtro di frequenza inclusiva, poi il filtro contestuale
     * escludendo le attività con [Attivita.targetImpiantoCod] non presente nel cantiere.
     *
     * @param impianto L'impianto in esame (es. RI o RIG)
     * @param impiantiNelCantiere Tutti gli impianti presenti nel cantiere
     * @param frequenza La frequenza selezionata per la generazione
     * @return Lista delle attività filtrate
     */
    fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita> {
        // 1. Filtro base per frequenza
        val attivitaFrequenzaOk = FrequencyFilter.filterByFrequenza(impianto.listaAttivita, frequenza)

        // Se non è un impianto soggetto a cross-plant discovery, restituisce direttamente il filtro per frequenza
        if (!isCrossPlant(impianto)) {
            return attivitaFrequenzaOk
        }

        // 2. Filtro contestuale impianti presenti nel cantiere
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        return attivitaFrequenzaOk.filter { att ->
            val target = att.targetImpiantoCod?.trim()?.uppercase()
            target == null || target in codiciPresenti
        }
    }

    /**
     * Calcola le frequenze disponibili per un impianto, tenendo conto
     * degli impianti effettivamente presenti nel cantiere.
     * Per impianti cross-plant (RI, RIG), esclude le frequenze che derivano solo da attività condizionate
     * i cui impianti target non sono presenti nel cantiere.
     */
    fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo> {
        if (!isCrossPlant(impianto)) {
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

/** Alias semantico per il resolver cross-plant universale */
typealias CrossPlantAttivitaResolver = AntincendioAttivitaResolver

