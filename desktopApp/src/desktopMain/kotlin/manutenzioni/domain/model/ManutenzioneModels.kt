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

    fun withSiglaDocumentale(sigla: EmergenzaSiglaDoc): ImpiantoEmergenza =
        copy(
            codIntervento = sigla.codIntervento,
            nomeCompleto = sigla.nomeCompleto
        )

    val siglaDocumentale: EmergenzaSiglaDoc
        get() = EmergenzaSiglaDoc.fromCod(codIntervento)
}

@Serializable
enum class EmergenzaSiglaDoc(
    val codIntervento: String,
    val label: String,
    val nomeCompleto: String
) {
    EM(
        codIntervento = "EM",
        label = "Illuminazione di emergenza",
        nomeCompleto = "Illuminazione di emergenza"
    ),
    IS(
        codIntervento = "IS",
        label = "Illuminazione di sicurezza",
        nomeCompleto = "Illuminazione di sicurezza"
    );

    val labelBreve: String
        get() = when (this) {
            EM -> "Emergenza"
            IS -> "Sicurezza"
        }

    val displayLabel: String
        get() = "$codIntervento ($labelBreve)"

    companion object {
        fun fromCod(cod: String?): EmergenzaSiglaDoc =
            if (cod?.equals("IS", ignoreCase = true) == true) IS else EM
    }
}


/**
 * Interfaccia per impianti di rilevazione/allarme che dispongono di conteggio o inventario
 * dei componenti/dispositivi (es. rivelatori puntiformi, gas, ecc.).
 */
sealed interface ImpiantoRilevazione {
    val quantitaComponenti: Map<String, Int>
}

@Serializable
enum class CategoriaRivelatore(val key: String, val label: String) {
    FUMO_PUNTIFORMI("fumo_puntiformi", "Rivelatori di Fumo Puntiformi"),
    FUMO_LINEARI("fumo_lineari", "Rivelatori di Fumo Lineari"),
    CALORE_PUNTIFORMI("calore_puntiformi", "Rivelatori di Calore Puntiformi"),
    CALORE_LINEARI("calore_lineari", "Rivelatori di Calore Lineari"),
    ASPIRAZIONE("aspirazione", "Rivelazione ad Aspirazione"),
    CONDOTTA("condotta", "Rivelatori di condotta"),
    FIAMMA("fiamma", "Rivelatori di Fiamma");

    fun buildDescrizione(): String {
        return "Prova dei $label (prova sul 50% dell’impianto) impiegando dispositivi artificiali di produzione del fumo o di altro, simulando l'insorgere di un incendio"
    }
}

@Serializable
data class DispositivoRilevazione(
    val id: String = java.util.UUID.randomUUID().toString(),
    val loop: Int = 1,
    val indirizzo: String,
    val tipo: String,
    val installazione: String? = null,
    val descrizione: String,
    val zona: String? = null,
    val dataInstallazione: String? = null,
    val note: String? = null
)

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
    
    override val quantitaComponenti: Map<String, Int> = emptyMap(),

    // R18: Mappa di booleani per le 7 categorie di rivelatori
    val categorieRivelatori: Map<String, Boolean> = emptyMap(),

    // R19: Registro componenti a Loop (1..4)
    val listaDispositivi: List<DispositivoRilevazione> = emptyList()
) : Impianto(), ImpiantoRilevazione {
    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche,
        categorieRivelatori = categorieRivelatori,
        listaDispositivi = listaDispositivi
    )

    fun isCategoriaAttiva(cat: CategoriaRivelatore): Boolean = categorieRivelatori[cat.key] == true

    fun withCategoria(cat: CategoriaRivelatore, attiva: Boolean): RilevazioneAntincendio {
        val mut = categorieRivelatori.toMutableMap()
        if (attiva) mut[cat.key] = true else mut.remove(cat.key)
        return copy(categorieRivelatori = mut)
    }

    fun withDispositivi(dispositivi: List<DispositivoRilevazione>): RilevazioneAntincendio =
        copy(listaDispositivi = dispositivi)
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
    
    override val quantitaComponenti: Map<String, Int> = emptyMap()
) : Impianto(), ImpiantoRilevazione {
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
    override val codIntervento: String = "QMT",
    override val nomeCompleto: String = "Quadro Media Tensione",
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
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche,
        tipoInterruttore = tipoInterruttore
    )

    /**
     * Sostituisce la keyword "Interruttore" (o precedente switch type) nella colonna "zona"
     * delle attività con la tipologia di interruttore specificata (es. SF6, ISV, IVOR).
     */
    fun withTipoInterruttore(tipo: MqtSwitchType?): QuadroMQT {
        val targetKeyword = tipo?.label ?: "Interruttore"
        val switchLabels = setOf("Interruttore", "interruttore", "ISF6", "SF6", "ISV", "IVOR")
        val updatedAttivita = listaAttivita.map { att ->
            if (att.zona != null && switchLabels.any { it.equals(att.zona, ignoreCase = true) }) {
                att.copy(zona = targetKeyword)
            } else {
                att
            }
        }
        return copy(
            tipoInterruttore = tipo,
            listaAttivita = updatedAttivita
        )
    }
}

@Serializable
enum class MqtSwitchType(val label: String) {
    SF6("SF6"),
    ISV("ISV"),
    IVOR("IVOR")
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
    val sigla: String = "",                         // es. "EM-01", "EM-02" — identificativo univoco per impianto
    val modello: String,                            // es. "Beghelli Ecoled 3h"
    val produttore: String? = null,                 // es. "Beghelli", "Schneider Electric"
    val autonomia: String = "1h",                   // es. "1h", "3h" — autonomia dichiarata
    val posizione: String? = null,                  // es. "Corridoio Piano 1", "Sala Macchine"
    val dataUltimoCambioBatteria: String? = null,   // ISO: "2024-03-15"
    val note: String? = null
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
    @kotlinx.serialization.Transient
    val nAttivita: Int = 0,
    val tipoAttivita: String? = null,
    val descrizione: String? = null,
    val frequenza: Periodo, // Ordinamento basato su questo oggetto
    val visibile: Boolean = true, // Per gestire la visibilità condizionale (es. attività custom Antincendio)
    val componentRef: String? = null, // ID del componente che attiva questa attività
    val targetImpiantoCod: String? = null, // Codice impianto bersaglio per discovery cross-plant (es. "GE", "PEM")
    val zona: String? = null // Sotto-sistema/zona dell'impianto (es. "Q.M.T.", "SEZ.", "Interruttore", "SF6", "ISV", "IVOR")
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

    /**
     * Codice compatto da 2 caratteri per i Mesi (es. "01", "02", "03", "06")
     * e 1 carattere per gli Anni (es. "1", "2", "5") per il cartiglio PDF e naming file (R22, R23).
     */
    fun codiceCartiglio(): String = when (tipo) {
        TipoPeriodo.M -> valore.toString().padStart(2, '0')
        TipoPeriodo.A -> valore.toString()
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
    val clienteId: String,
    @Deprecated("Usare dataIntervento; mantenuto per retrocompatibilità JSON/BSON", ReplaceWith("dataIntervento"))
    val dataInizioIntervento: String? = null,
    val dataInizioContratto: String? = null,
    val dataIntervento: String? = null,
    val dataInterventoPrecedente: String? = null,
    val nomeManutentore: String? = null,
    /** Indirizzo della sede operativa / cantiere (R3). Opzionale per retrocompatibilità. */
    val indirizzo: String? = null,
    /** Percorso o Data URI base64 della firma digitale del tecnico manutentore (R26). */
    val firmaManutentore: String? = null
) {
    /**
     * Data intervento effettiva con fallback automatico su [dataInizioIntervento]
     * per garantire retrocompatibilità trasparente con record legacy JSON e MongoDB.
     */
    val effectiveDataIntervento: String?
        get() = dataIntervento?.trim()?.ifBlank { null } ?: dataInizioIntervento?.trim()?.ifBlank { null }

    /**
     * Alias italiano per [effectiveDataIntervento].
     */
    val dataInterventoEffettiva: String?
        get() = effectiveDataIntervento
}

/**
 * Normalizza le date del cantiere, migrando [dataInizioIntervento] su [dataIntervento] se non ancora valorizzato.
 * Garantisce la sincronizzazione bidirezionale tra dataIntervento e il campo legacy dataInizioIntervento.
 */
fun Cantiere.normalizeDates(): Cantiere {
    val eff = effectiveDataIntervento
    val trimmedContratto = dataInizioContratto?.trim()?.ifBlank { null }
    val trimmedPrecedente = dataInterventoPrecedente?.trim()?.ifBlank { null }
    return if (dataInizioContratto != trimmedContratto ||
        dataInterventoPrecedente != trimmedPrecedente ||
        dataIntervento != eff ||
        dataInizioIntervento != eff
    ) {
        copy(
            dataInizioContratto = trimmedContratto,
            dataIntervento = eff,
            dataInizioIntervento = eff,
            dataInterventoPrecedente = trimmedPrecedente
        )
    } else {
        this
    }
}

/**
 * Promuove l'attuale data intervento (da [effectiveDataIntervento]) a [dataInterventoPrecedente],
 * impostando opzionalmente la nuova data di intervento sia su [dataIntervento] che su [dataInizioIntervento].
 */
fun Cantiere.promoteInterventoToPrecedente(nuovaDataIntervento: String? = null): Cantiere {
    val currentEffettiva = effectiveDataIntervento
    val trimmedNuova = nuovaDataIntervento?.trim()?.ifBlank { null }
    return copy(
        dataInterventoPrecedente = currentEffettiva ?: dataInterventoPrecedente,
        dataIntervento = trimmedNuova,
        dataInizioIntervento = trimmedNuova
    )
}


/**
 * Normalizza il tipo concreto dell'impianto a partire dal suo codice intervento,
 * garantendo che schede generiche caricate da JSON o MongoDB vengano istanziate
 * come la rispettiva sottoclasse polimorfica (QuadroBT, ImpiantoEmergenza, RilevazioneAntincendio, RilevazioneGas).
 */
fun Impianto.normalizeType(): Impianto {
    val numberedAttivita = listaAttivita.mapIndexed { idx, att ->
        if (att.nAttivita == idx + 1) att else att.copy(nAttivita = idx + 1)
    }
    return when {
        this !is ImpiantoEmergenza && (codIntervento.equals("EM", ignoreCase = true) || codIntervento.equals("IS", ignoreCase = true)) -> {
            ImpiantoEmergenza(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa,
                listaAttivita = numberedAttivita,
                listaNormative = listaNormative,
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche,
                listaLampade = emptyList()
            )
        }
        this !is QuadroBT && codIntervento.equals("Q", ignoreCase = true) -> {
            QuadroBT(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa,
                listaAttivita = numberedAttivita,
                listaNormative = listaNormative,
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche,
                sigla = "",
                descrizioneQuadro = "",
                listaInterruttori = emptyList()
            )
        }
        this !is RilevazioneAntincendio && codIntervento.equals("RI", ignoreCase = true) -> {
            RilevazioneAntincendio(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa,
                listaAttivita = numberedAttivita,
                listaNormative = listaNormative,
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche,
                quantitaComponenti = (this as? ImpiantoRilevazione)?.quantitaComponenti ?: emptyMap(),
                categorieRivelatori = (this as? RilevazioneAntincendio)?.categorieRivelatori ?: emptyMap(),
                listaDispositivi = (this as? RilevazioneAntincendio)?.listaDispositivi ?: emptyList()
            )
        }
        this !is RilevazioneGas && codIntervento.equals("RIG", ignoreCase = true) -> {
            RilevazioneGas(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa,
                listaAttivita = numberedAttivita,
                listaNormative = listaNormative,
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche
            )
        }
        this !is QuadroMQT && codIntervento.equals("QMT", ignoreCase = true) -> {
            QuadroMQT(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa,
                listaAttivita = numberedAttivita,
                listaNormative = listaNormative,
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche,
                tipoInterruttore = (this as? QuadroMQT)?.tipoInterruttore
            )
        }
        else -> if (numberedAttivita != listaAttivita) copyWithBasicParams(listaAttivita = numberedAttivita) else this
    }
}

