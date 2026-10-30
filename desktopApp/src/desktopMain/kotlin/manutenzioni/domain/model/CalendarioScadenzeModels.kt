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
    val numeroAttivita: Int = 0,
    /** Elenco sintetico delle verifiche/modifiche previste in scadenza per l'impianto */
    val attivitaInScadenza: List<String> = emptyList()
)

/**
 * Raggruppamento mensile delle scadenze future calcolate per un singolo cantiere (R27).
 */
@Serializable
data class MeseScadenzaGroup(
    val indiceMese: Int,
    val annoMese: String,          // es. "2026-10"
    val etichettaMese: String,     // es. "Ottobre 2026"
    val dataPrevista: String,      // es. "01/10/2026"
    val scadenze: List<ScadenzaImpiantoItem> = emptyList()
)

/**
 * Scadenze di un singolo cantiere all'interno di un determinato mese nel Calendario Globale Multi-Cantiere (R27).
 */
@Serializable
data class CantiereScadenzaInMese(
    val cantiere: Cantiere,
    val cliente: Cliente? = null,
    val dataPrevista: String,
    val indiceMeseDalContratto: Int,
    val scadenzeImpianti: List<ScadenzaImpiantoItem> = emptyList()
) {
    val totaleImpiantiInScadenza: Int
        get() = scadenzeImpianti.size

    val totaleAttivitaInScadenza: Int
        get() = scadenzeImpianti.sumOf { it.numeroAttivita }
}

/**
 * Raggruppamento mensile globale su tutti i cantieri (es. "Maggio 2026" -> elenco cantieri con impianti e attività da eseguire).
 */
@Serializable
data class MeseScadenzaGlobaleGroup(
    val annoMese: String,          // es. "2026-05"
    val etichettaMese: String,     // es. "Maggio 2026"
    val cantieriInScadenza: List<CantiereScadenzaInMese> = emptyList()
) {
    val totaleCantieri: Int
        get() = cantieriInScadenza.size

    val totaleImpianti: Int
        get() = cantieriInScadenza.sumOf { it.totaleImpiantiInScadenza }

    val totaleAttivita: Int
        get() = cantieriInScadenza.sumOf { it.totaleAttivitaInScadenza }
}
