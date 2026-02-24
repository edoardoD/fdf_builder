package manutenzioni.app.data
import kotlinx.serialization.Serializable

@Serializable
data class Impianto(
    val codIntervento: String, // Esempio: "GE", "CAB"
    val nomeCompleto: String,  // Esempio: "Gruppo Elettrogeno"
    val premessa: String?,     // Testo descrittivo sulla sicurezza
    val listaAttivita: List<Attivita>,
    val listaNormative: List<Normativa> = emptyList()
)

@Serializable
data class Attivita(
    val nAttivita: Int,
    val tipoAttivita: String?,
    val descrizione: String?,
    val frequenza: Periodo // Ordinamento basato su questo oggetto
)

@Serializable
data class Periodo(
    val tipo: TipoPeriodo, // M o A
    val valore: Int        // 6, 12 per M oppure 1, 6 per A
) {
    /** Converte il periodo in mesi per confronti di frequenza inclusiva */
    fun inMesi(): Int = when (tipo) {
        TipoPeriodo.M -> valore
        TipoPeriodo.A -> valore * 12
    }

    /** Etichetta leggibile per la UI */
    fun label(): String = when (tipo) {
        TipoPeriodo.M -> "$valore ${if (valore == 1) "Mese" else "Mesi"}"
        TipoPeriodo.A -> "$valore ${if (valore == 1) "Anno" else "Anni"}"
    }
}

@Serializable
enum class TipoPeriodo { M, A }

@Serializable
data class Normativa(
    val codNormativa: String,
    val descrizione: String
)

/**
 * Entità Cliente — rappresenta il committente della manutenzione.
 * Embedding-ready per NoSQL (Realm).
 */
@Serializable
data class Cliente(
    val id: String,
    val nome: String,
    val indirizzo: String? = null,
    val partitaIva: String? = null
)

/** Wrapper per la serializzazione del database JSON */
@Serializable
data class ManutenzioniDatabase(
    val impianti: List<Impianto>,
    val clienti: List<Cliente> = emptyList()
)

