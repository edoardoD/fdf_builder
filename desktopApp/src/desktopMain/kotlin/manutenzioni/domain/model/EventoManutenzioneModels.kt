package manutenzioni.domain.model

import kotlinx.serialization.Serializable

/**
 * Stato operativo di un intervento pianificato nel calendario.
 */
@Serializable
enum class StatoEventoManutenzione(val label: String) {
    PIANIFICATO("Pianificato"),
    IN_CORSO("In Corso"),
    COMPLETATO("Completato"),
    ANNULLATO("Annullato")
}

/**
 * Snapshot dell'impianto e della periodicità pianificata all'interno dell'evento di manutenzione.
 */
@Serializable
data class ImpiantoPianificatoSnapshot(
    val impiantoId: String,
    val codIntervento: String,
    val descrizione: String,
    val periodicitaScelta: Periodo,
    val numeroAttivitaPreviste: Int = 0,
    val attivitaDescrizioni: List<String> = emptyList()
)

/**
 * Evento di manutenzione pianificato per un cantiere a una determinata data (Interactive Scheduler).
 *
 * Supporta la pianificazione singola o massiva su più cantieri, la generazione automatica
 * di ricorrenze periodiche (es. semestrale, annuale) e il collegamento alla sessione storica eseguita.
 */
@Serializable
data class EventoManutenzione(
    val id: String = java.util.UUID.randomUUID().toString(),
    val cantiereId: String,
    val clienteId: String,
    /** Data pianificata nel formato standard "dd/MM/yyyy" (es. "22/10/2026") */
    val dataPianificata: String,
    /** Ora inizio opzionale "HH:mm" (es. "09:00") */
    val oraInizio: String? = null,
    val nomeManutentore: String? = null,
    val stato: StatoEventoManutenzione = StatoEventoManutenzione.PIANIFICATO,
    val impiantiPianificati: List<ImpiantoPianificatoSnapshot> = emptyList(),
    val note: String? = null,
    /** UUID condiviso da tutte le istanze generate dalla stessa regola di ricorrenza */
    val serieRicorrenzaId: String? = null,
    val dataCompletamentoEffettiva: String? = null,
    val sessioneVerificaId: String? = null
)
