package manutenzioni.domain.service.rules

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.TipoVerbaleRi

/**
 * Regola di dominio per i sistemi di Rilevazione e Segnalazione Incendi ("RI" — IRAI)
 * conforme alle norme UNI 11224:2026 e UNI 9795:2021.
 *
 * Vincoli normativi:
 * 1. Periodicità unica obbligatoria: Semestrale (`06 MESI` — [Uni11224RiCatalog.FREQUENZA_SEMESTRALE]).
 * 2. Liste di riscontro obbligatorie:
 *    - [TipoVerbaleRi.VERIFICA_PERIODICA_B]: 13 attività di verifica sul sistema (Prospetto B.2).
 *    - [TipoVerbaleRi.VERIFICA_INIZIALE_A]: 17 attività di controllo sul sistema e dispositivi (Prospetto A.3),
 *      affiancate nel libretto dalle 19 verifiche preliminari (Prospetto A.2).
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
        val ri = impianto as? RilevazioneAntincendio
        if (ri != null && ri.tipoVerbale == TipoVerbaleRi.VERIFICA_INIZIALE_A) {
            return Uni11224RiCatalog.LISTA_A3_CONTROLLI_SISTEMA
        }
        return Uni11224RiCatalog.normalizeRiAttivita(impianto.listaAttivita)
    }

    override fun resolveFrequenze(
        impianto: Impianto,
        impiantiNelCantiere: List<Impianto>
    ): List<Periodo> = listOf(Uni11224RiCatalog.FREQUENZA_SEMESTRALE)
}
