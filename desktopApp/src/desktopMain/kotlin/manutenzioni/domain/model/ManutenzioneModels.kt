package manutenzioni.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class Impianto {
    abstract val id: String
    abstract val codIntervento: String // Esempio: "GE", "CAB"
    abstract val nomeCompleto: String  // Esempio: "Gruppo Elettrogeno"
    abstract val premessa: String?     // Testo descrittivo sulla sicurezza
    abstract val listaAttivita: List<Attivita>
    abstract val listaNormative: List<Normativa>
    abstract val cantiereId: String?
    abstract val quantita: Int
    abstract val noteSpecifiche: String?

    // Metodo helper per creare una copia con parametri comuni, utile per il ViewModel/Editor
    abstract fun copyWithBasicParams(
        id: String = this.id,
        codIntervento: String = this.codIntervento,
        nomeCompleto: String = this.nomeCompleto,
        premessa: String? = this.premessa,
        listaAttivita: List<Attivita> = this.listaAttivita,
        listaNormative: List<Normativa> = this.listaNormative,
        cantiereId: String? = this.cantiereId,
        quantita: Int = this.quantita,
        noteSpecifiche: String? = this.noteSpecifiche
    ): Impianto
}

@Serializable
@SerialName("ImpiantoStandard")
data class ImpiantoStandard(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
@SerialName("QuadroBT")
data class QuadroBT(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,
    
    val sigla: String = "",
    val descrizioneQuadro: String = "",
    val listaInterruttori: List<InterruttoreBT> = emptyList()
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
@SerialName("ImpiantoEmergenza")
data class ImpiantoEmergenza(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,
    
    val listaLampade: List<LampadaEmergenza> = emptyList()
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
@SerialName("RilevazioneAntincendio")
data class RilevazioneAntincendio(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,
    
    val quantitaComponenti: Map<String, Int> = emptyMap()
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
@SerialName("RilevazioneGas")
data class RilevazioneGas(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,
    
    val quantitaComponenti: Map<String, Int> = emptyMap()
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
@SerialName("QuadroMQT")
data class QuadroMQT(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = emptyList(),
    override val listaNormative: List<Normativa> = emptyList(),
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,
    
    val tipoInterruttore: MqtSwitchType? = null
) : Impianto() {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )
}

@Serializable
enum class MqtSwitchType(val label: String) {
    SF6("SF6"),
    VUOTO("Vuoto"),
    ARIA("Aria")
}

@Serializable
data class InterruttoreBT(
    val id: String = java.util.UUID.randomUUID().toString(),
    val nome: String,
    val quantita: Int = 1,
    val siglaCircuito: String? = null,
    val produttore: String? = null,
    val codiceArticolo: String? = null,
    val etimClassId: String? = null,
    val etimClassName: String? = null,
    val caratteristicheTecniche: Map<String, String> = emptyMap(),
    val note: String? = null
)

@Serializable
data class VarianteProdotto(
    val produttore: String,
    val codice: String,
    val serie: String? = null,
    val descrizione: String? = null,
    val prezzoListino: Double? = null,
    val dataApprovazione: String = ""
)

@Serializable
data class ComponenteApprovato(
    val id: String = java.util.UUID.randomUUID().toString(),
    val etimClassId: String,
    val etimClassName: String,
    val descrizioneStandard: String,
    val caratteristicheTecniche: Map<String, String> = emptyMap(),
    val variantiProduttore: Map<String, VarianteProdotto> = emptyMap(),
    val dataCreazione: String = ""
)

@Serializable
data class ComponentCandidate(
    val etimClassId: String,
    val etimClassName: String,
    val descrizioneStandard: String,
    val caratteristicheTecniche: Map<String, String> = emptyMap(),
    val variantiDisponibili: List<VarianteProdotto> = emptyList()
)

@Serializable
data class LampadaEmergenza(
    val id: String = java.util.UUID.randomUUID().toString(),
    val modello: String,
    val autonomia: String // es. "1h", "3h"
)

@Serializable
data class ComponenteStandard(
    val id: String = java.util.UUID.randomUUID().toString(),
    val tipo: TipoComponenteStandard,
    val nome: String,
    val attributi: Map<String, String> = emptyMap() // Per es. autonomia lampade
)

@Serializable
enum class TipoComponenteStandard {
    INTERRUTTORE_BT,
    LAMPADA_EMERGENZA,
    COMPONENTE_ANTINCENDIO,
    COMPONENTE_GAS
}

@Serializable
data class Attivita(
    val nAttivita: Int,
    val tipoAttivita: String?,
    val descrizione: String?,
    val frequenza: Periodo, // Ordinamento basato su questo oggetto
    val visibile: Boolean = true, // Per gestire la visibilità condizionale (es. attività custom Antincendio)
    val componentRef: String? = null, // ID del componente che attiva questa attività
    val targetImpiantoCod: String? = null // Codice impianto bersaglio per discovery cross-plant (es. "GE", "PEM")
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
    val partitaIva: String? = null,
)

@Serializable
data class Cantiere(
    val id: String,
    val nome: String,
    val clienteId: String
)
