package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoRilevazione
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.rules.CrossPlantRule
import manutenzioni.domain.service.rules.DefaultCrossPlantRule
import manutenzioni.domain.service.rules.FireAlarmCrossPlantRule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Resolver unificato per calcolare le attività effettive e le frequenze disponibili per
 * gli impianti del cantiere, orchestrando il registro delle regole [CrossPlantRule].
 *
 * Mantiene la compatibilità al 100% con tutte le chiamate esistenti nel ViewModel, nei servizi PDF
 * e nei test, consentendo al contempo l'estensione dinamica Open-Closed per nuovi tipi di impianto.
 */
object CrossPlantAttivitaResolver {

    private val defaultRule: CrossPlantRule = DefaultCrossPlantRule()
    private val customRules = CopyOnWriteArrayList<CrossPlantRule>()

    init {
        // Registra le regole di base standard
        customRules.add(FireAlarmCrossPlantRule())
    }

    /**
     * Registra una nuova regola di Cross-Plant Discovery all'inizio della catena di priorità.
     */
    fun registerRule(rule: CrossPlantRule) {
        customRules.add(0, rule)
    }

    /**
     * Rimuove una regola dal registro.
     */
    fun unregisterRule(rule: CrossPlantRule) {
        customRules.remove(rule)
    }

    /**
     * Ripristina il registro alle sole regole di default previste dal sistema.
     */
    fun resetToDefaults() {
        customRules.clear()
        customRules.add(FireAlarmCrossPlantRule())
    }

    /**
     * Restituisce la lista immutabile delle regole registrate.
     */
    fun getRegisteredRules(): List<CrossPlantRule> = customRules.toList()

    /**
     * Seleziona la regola appropriata per l'impianto specificato.
     */
    fun findRuleFor(impianto: Impianto): CrossPlantRule {
        return customRules.firstOrNull { it.canHandle(impianto) } ?: defaultRule
    }

    /**
     * Determina se un impianto richiede la risoluzione contestuale Cross-Plant.
     * È vero per [ImpiantoRilevazione] (RI, RIG), per qualsiasi impianto
     * che contenga almeno un'attività che specifica un [Attivita.targetImpiantoCod],
     * oppure per qualsiasi impianto gestito da una regola registrata non di default.
     */
    fun isCrossPlant(impianto: Impianto): Boolean {
        return impianto is ImpiantoRilevazione ||
                impianto.listaAttivita.any { it.targetImpiantoCod != null } ||
                customRules.any { it.canHandle(impianto) }
    }

    /**
     * Risolve le attività effettive per l'impianto in esame delegando alla regola corrispondente.
     *
     * @param impianto L'impianto in esame (es. RI, RIG o qualsiasi impianto con vincoli cross-plant)
     * @param impiantiNelCantiere Tutti gli impianti presenti nel cantiere
     * @param frequenza La frequenza selezionata per la generazione
     * @return Lista delle attività filtrate e rinumerate 1..N
     */
    fun resolveAttivita(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>,
        frequenza: Periodo
    ): List<Attivita> {
        return findRuleFor(impianto).resolveAttivita(impianto, impiantiNelCantiere, frequenza)
    }

    /**
     * Calcola le frequenze disponibili per un impianto delegando alla regola corrispondente.
     * Esclude le frequenze che deriverebbero solo da attività condizionate
     * i cui impianti target non sono presenti nel cantiere.
     */
    fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo> {
        return findRuleFor(impianto).resolveFrequenze(impianto, impiantiNelCantiere)
    }
}
