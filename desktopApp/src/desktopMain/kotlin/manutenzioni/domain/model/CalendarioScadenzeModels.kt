package manutenzioni.domain.model

import kotlinx.serialization.Serializable

/**
 * Singola voce di scadenza per un impianto all'interno di un mese pianificato (R27).
 */
@Serializable
data class ScadenzaImpiantoItem(
    val impiantoId: String,
    val codIntervento: String,
    val descrizioneImpianto: String,
    val frequenzaMassima: Periodo,
    val frequenzeIncluse: List<Periodo> = emptyList(),
    val numeroAttivita: Int = 0
)

/**
 * Raggruppamento mensile delle scadenze future calcolate per un cantiere (R27).
 */
@Serializable
data class MeseScadenzaGroup(
    val indiceMese: Int,
    val annoMese: String,          // es. "2026-10"
    val etichettaMese: String,     // es. "Ottobre 2026"
    val dataPrevista: String,      // es. "01/10/2026"
    val scadenze: List<ScadenzaImpiantoItem> = emptyList()
)
