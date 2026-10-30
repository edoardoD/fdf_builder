package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.CategoriaRivelatore
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoRilevazione
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.TipoPeriodo

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
     * Per [RilevazioneAntincendio] include tassativamente le 10 attività istituzionali
     * a 6M e inietta le prove al 50% per le categorie di rivelatori attive (R18).
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
        val codiciPresenti = impiantiNelCantiere.map { it.codIntervento.trim().uppercase() }.toSet()

        if (impianto is RilevazioneAntincendio) {
            // 1. Attività istituzionali di centrale e linee (tassativamente 10 attività a 6M)
            val istituzionali = if (frequenza.inMesi() >= 6) {
                val base = impianto.listaAttivita.filter { it.targetImpiantoCod == null }
                if (base.size >= 10) base.take(10) else base
            } else {
                FrequencyFilter.filterByFrequenza(impianto.listaAttivita.filter { it.targetImpiantoCod == null }, frequenza)
            }

            // 2. Attività dinamiche di prova al 50% per ciascuna categoria di rivelatori attiva (R18)
            val dinamiche = if (frequenza.inMesi() % 6 == 0) {
                CategoriaRivelatore.values().filter { impianto.isCategoriaAttiva(it) }.map { cat ->
                    Attivita(
                        nAttivita = 0,
                        tipoAttivita = "Verifica",
                        zona = "Componenti",
                        descrizione = cat.buildDescrizione(),
                        frequenza = Periodo(TipoPeriodo.M, 6),
                        targetImpiantoCod = null
                    )
                }
            } else emptyList()

            // 3. Attività cross-plant condizionate dagli altri impianti presenti nel cantiere
            val crossPlant = impianto.listaAttivita
                .filter { it.targetImpiantoCod != null && it.targetImpiantoCod.trim().uppercase() in codiciPresenti }
                .let { FrequencyFilter.filterByFrequenza(it, frequenza) }

            // 4. Concatenazione e rinumerazione progressiva (1..N)
            val unificate = istituzionali + dinamiche + crossPlant
            return unificate.mapIndexed { index, att -> att.copy(nAttivita = index + 1) }
        }

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
        if (impianto is RilevazioneAntincendio) {
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
