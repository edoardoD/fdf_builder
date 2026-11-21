package manutenzioni.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ImpiantoKind {
    STANDARD,
    QUADRO_BT,
    QUADRO_MQT,
    EMERGENZA,
    ANTINCENDIO,
    GAS
}

object ImpiantoCodici {
    const val QUADRO_BT = "Q"
    const val QUADRO_MQT = "QMT"
    const val EMERGENZA = "EM"
    const val SICUREZZA = "IS"
    const val ANTINCENDIO = "RI"
    const val GAS = "RIG"

    fun isQuadroBT(cod: String?): Boolean {
        val trimmed = cod?.trim() ?: return false
        return trimmed.equals(QUADRO_BT, ignoreCase = true) ||
            (trimmed.startsWith("Q", ignoreCase = true) && !trimmed.equals(QUADRO_MQT, ignoreCase = true))
    }

    fun isEmergenza(cod: String?): Boolean {
        val trimmed = cod?.trim() ?: return false
        return trimmed.equals(EMERGENZA, ignoreCase = true) || trimmed.equals(SICUREZZA, ignoreCase = true)
    }

    fun isQuadroMQT(cod: String?): Boolean =
        cod?.trim().equals(QUADRO_MQT, ignoreCase = true)

    fun isAntincendio(cod: String?): Boolean =
        cod?.trim().equals(ANTINCENDIO, ignoreCase = true)

    fun isGas(cod: String?): Boolean =
        cod?.trim().equals(GAS, ignoreCase = true)
}

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

    // ==========================================
    // 1. IDENTIFICAZIONE E CARTIGLIO
    // ==========================================

    /**
     * Sigla per il cartiglio PDF (R22, R23) e intestazione schede.
     * Es: "Q - QG1" se [QuadroBT] ha la sigla valorizzata, altrimenti [codIntervento].
     */
    open val siglaCartiglio: String
        get() = codIntervento

    /**
     * Prefisso standard per il naming del file PDF esportato (R23, R28).
     * Sostituisce spazi con underscore.
     * Es: "Q-QG1" per [QuadroBT] con sigla, "Q" altrimenti.
     */
    open val filePrefix: String
        get() = codIntervento.trim().replace(" ", "_")

    /**
     * Codice sintetico con sigla tra parentesi per elenchi compatti (es. "Q (QG1)").
     */
    open val displayCode: String
        get() = codIntervento

    /**
     * Descrizione dettagliata/oggetto per il cartiglio PDF e intestazione scheda.
     * Per [QuadroBT] privilegia la descrizione specifica del quadro rispetto al nome generico.
     */
    open val descrizioneDettagliata: String
        get() = nomeCompleto

    /**
     * Nome completo con sigla descrittiva per liste e dialog (es. "Quadri Elettrici BT (QG1)").
     */
    open val nomeConSigla: String
        get() = nomeCompleto

    /**
     * Etichetta distintiva della tecnologia/configurazione (es. "Interruttore: SF6", "EM (Emergenza)").
     * Restituisce null se l'impianto non ha una sotto-tipologia configurata.
     */
    open val subtipoLabel: String?
        get() = null

    // ==========================================
    // 2. FOGLIO ALLEGATO (FOGLIO 2+) & COMPONENTI
    // ==========================================

    /**
     * Conteggio aggregato dei componenti secondari installati
     * (differenziali per QuadroBT, lampade per Emergenza, dispositivi per Antincendio).
     */
    open val conteggioComponenti: Int
        get() = 0

    /**
     * Testo sintetico di riepilogo dell'allegato tecnico nel sommario PDF.
     * Es: "Sì (12 diff.)", "Sì (35 lampade)", "No".
     */
    open val dettaglioAllegato: String
        get() = "No"

    /**
     * Testo informativo compatto per dialog e schede (es. "12 interruttori", "35 lampade").
     */
    open val extraDettaglio: String?
        get() = null

    /**
     * Indica se l'impianto possiede componenti che attivano la generazione del Foglio 2+.
     */
    open val hasTechnicalAttachment: Boolean
        get() = false

    // ==========================================
    // 3. DISCRIMINANTI DI CATEGORIA & KIND
    // ==========================================

    open val isQuadro: Boolean get() = false
    open val isEmergenza: Boolean get() = false
    open val isAntincendio: Boolean get() = false
    open val isQuadroMqt: Boolean get() = false
    open val isGas: Boolean get() = false
    open val requiresMassiveCreation: Boolean get() = false

    open val kind: ImpiantoKind get() = ImpiantoKind.STANDARD

    // ==========================================
    // 4. FORMATTAZIONE ATTIVITÀ & ZONA
    // ==========================================

    /**
     * Risolve il valore visualizzato nella colonna "Zona" della scheda PDF per l'attività specificata.
     * Per [QuadroMQT], sostituisce dinamicamente la dicitura generica "interruttore"
     * con la tecnologia configurata (SF6, ISV, IVOR).
     */
    open fun formatZonaAttivita(attivita: Attivita): String {
        return if (!attivita.zona.isNullOrBlank()) attivita.zona else "attività"
    }

    // ==========================================
    // 5. METODI DI COPIA E CICLO DI VITA
    // ==========================================

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

    /**
     * Istanzia una copia indipendente di un template globale da associare a un cantiere.
     * Assegna un nuovo UUID, il [cantiereId] specificato, resetta [quantita] = 1,
     * e applica eventuali valori predefiniti del sottotipo (es. default SF6 per QuadroMQT).
     */
    abstract fun instantiateForCantiere(
        cantiereId: String?,
        newId: String = java.util.UUID.randomUUID().toString()
    ): Impianto

    /**
     * Sincronizza i dati anagrafici e normativi modificati nel master template
     * (nomeCompleto, premessa, listaAttivita, listaNormative) preservando rigorosamente
     * lo stato puntuale dell'istanza di cantiere (id, cantiereId, quantita, noteSpecifiche,
     * liste di componenti installati e parametri tecnologici).
     */
    abstract fun applyTemplateUpdates(template: Impianto): Impianto
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
    override val kind: ImpiantoKind get() = ImpiantoKind.STANDARD

    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )

    override fun instantiateForCantiere(cantiereId: String?, newId: String): ImpiantoStandard =
        copy(id = newId, cantiereId = cantiereId, quantita = 1)

    override fun applyTemplateUpdates(template: Impianto): ImpiantoStandard = copy(
        nomeCompleto = template.nomeCompleto,
        premessa = template.premessa,
        listaAttivita = template.listaAttivita,
        listaNormative = template.listaNormative
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
    override val kind: ImpiantoKind get() = ImpiantoKind.QUADRO_BT
    override val isQuadro: Boolean get() = true
    override val requiresMassiveCreation: Boolean get() = true

    override val siglaCartiglio: String
        get() = if (sigla.isNotBlank()) "$codIntervento - ${sigla.trim()}" else codIntervento

    override val filePrefix: String
        get() = if (sigla.isNotBlank()) {
            "${codIntervento}-${sigla.trim().replace(" ", "_")}"
        } else {
            codIntervento.trim().replace(" ", "_")
        }

    override val displayCode: String
        get() = if (sigla.isNotBlank()) "$codIntervento (${sigla.trim()})" else codIntervento

    override val descrizioneDettagliata: String
        get() = listOfNotNull(
            descrizioneQuadro.trim().ifBlank { null },
            nomeCompleto.trim().ifBlank { null }
        ).firstOrNull() ?: nomeCompleto

    override val nomeConSigla: String
        get() = if (sigla.isNotBlank()) "$nomeCompleto (${sigla.trim()})" else nomeCompleto

    override val subtipoLabel: String?
        get() = if (sigla.isNotBlank()) "Sigla: ${sigla.trim()}" else null

    override val conteggioComponenti: Int
        get() = listaInterruttori.sumOf { it.quantita.coerceAtLeast(1) }

    override val extraDettaglio: String?
        get() = if (listaInterruttori.isNotEmpty()) "${listaInterruttori.size} interruttori" else null

    override val dettaglioAllegato: String
        get() = if (listaInterruttori.isNotEmpty()) "Sì ($conteggioComponenti diff.)" else "No"

    override val hasTechnicalAttachment: Boolean
        get() = listaInterruttori.isNotEmpty()

    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche
    )

    override fun instantiateForCantiere(cantiereId: String?, newId: String): QuadroBT =
        copy(id = newId, cantiereId = cantiereId, quantita = 1)

    override fun applyTemplateUpdates(template: Impianto): QuadroBT = copy(
        nomeCompleto = template.nomeCompleto,
        premessa = template.premessa,
        listaAttivita = template.listaAttivita,
        listaNormative = template.listaNormative
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
    override val kind: ImpiantoKind get() = ImpiantoKind.EMERGENZA
    override val isEmergenza: Boolean get() = true

    override val subtipoLabel: String
        get() = siglaDocumentale.displayLabel

    override val conteggioComponenti: Int
        get() = listaLampade.size

    override val extraDettaglio: String?
        get() = if (listaLampade.isNotEmpty()) "${listaLampade.size} lampade" else null

    override val dettaglioAllegato: String
        get() = if (listaLampade.isNotEmpty()) "Sì ($conteggioComponenti lampade)" else "No"

    override val hasTechnicalAttachment: Boolean
        get() = listaLampade.isNotEmpty()

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

    override fun instantiateForCantiere(cantiereId: String?, newId: String): ImpiantoEmergenza =
        copy(id = newId, cantiereId = cantiereId, quantita = 1)

    override fun applyTemplateUpdates(template: Impianto): ImpiantoEmergenza = copy(
        nomeCompleto = template.nomeCompleto,
        premessa = template.premessa,
        listaAttivita = template.listaAttivita,
        listaNormative = template.listaNormative
    )
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
enum class TipoRivelatore(val label: String) {
    FUMO("FUMO"),
    TERMO("TERMO"),
    BARRIERA("BARR."),
    OTTICO("OTTICO"),
    TERMOVELOCIMETRICO("TERMOVELOCIMETRICO")
}

@Serializable
enum class TipoModuloAntincendio(val label: String) {
    MODULO_COMANDO("MODULO DI COMANDO"),
    PULSANTE("PULSANTE"),
    M701("M701"),
    M710("M710"),
    CMA_11E_IN("CMA 11-E (IN)"),
    CMA_11E_OUT("CMA 11-E (OUT)"),
    ALTRO("ALTRO")
}

@Serializable
enum class TipoComponenteComuneAntincendio(val label: String) {
    CENTRALE_INCENDIO("CENTRALE INCENDIO"),
    ALIMENTATORE_SUPPLEMENTARE("ALIMENTATORE SUPPLEMENTARE"),
    SIRENA_ESTERNA("SIRENA ESTERNA"),
    TARGA_OTTICO_ACUSTICA("TARGA OTTICO-ACUSTICA"),
    PANNELLO_RIPETITORE("PANNELLO RIPETITORE"),
    COMBINATORE("COMBINATORE"),
    ALTRO("ALTRO")
}

/**
 * Architettura base dell'impianto di rilevazione incendi (UNI 9795 / UNI 11224:2026):
 * mutuamente esclusiva tra Convenzionale e Indirizzato.
 */
@Serializable
enum class ArchitetturaImpiantoRi(val label: String) {
    CONVENZIONALE("Convenzionale"),
    INDIRIZZATO("Indirizzato")
}

/**
 * Tipologia di Libretto / Verbale UNI 11224:2026 da generare per l'impianto RI.
 */
@Serializable
enum class TipoVerbaleRi(val label: String, val codiceSezioneAnagrafica: String) {
    VERIFICA_PERIODICA_B("Verifica Periodica Semestrale (Appendice B)", "B1"),
    VERIFICA_INIZIALE_A("Verifica Iniziale / Presa in Carico (Appendice A)", "A1")
}

/**
 * Estensione della protezione dell'impianto di rilevazione incendi.
 */
@Serializable
enum class EstensioneProtezioneRi(val label: String) {
    TOTALE("Totale dell'edificio"),
    PARZIALE("Parziale")
}

/**
 * Classificazione dell'ambiente/esecuzione dell'impianto RI (A1 / B1).
 */
@Serializable
enum class TipoEsecuzioneRi(val label: String) {
    CIVILE("Tipo Civile"),
    TERZIARIO_UFFICI("Terziario / Uffici"),
    INDUSTRIALE_DEPOSITO("Industriale / Deposito"),
    ATEX("Luogo a Rischio Esplosione (ATEX)")
}

/**
 * Classe di sensibilità per i sistemi di rivelazione ad aspirazione ASD (Prospetto A.4 / B.3).
 */
@Serializable
enum class ClasseSensibilitaAsd(val label: String) {
    CLASSE_A("Classe A"),
    CLASSE_B("Classe B"),
    CLASSE_C("Classe C")
}

/**
 * Tempo massimo di trasporto richiesto per i sistemi ASD (Prospetto A.4 / B.3).
 */
@Serializable
enum class TempoTrasportoAsd(val label: String) {
    MAX_60S("≤ 60 s"),
    MAX_90S("≤ 90 s"),
    MAX_120S("≤ 120 s")
}

@Serializable
sealed class DispositivoRilevazione {
    abstract val id: String
    abstract val indirizzo: String
    abstract val installazione: String?
    abstract val descrizione: String
    abstract val zona: String?
    abstract val dataInstallazione: String?
    abstract val note: String?

    abstract val labelTipo: String
    abstract val labelSezione: String

    @Serializable
    @SerialName("ComponenteComune")
    data class ComponenteComune(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val tipo: TipoComponenteComuneAntincendio = TipoComponenteComuneAntincendio.CENTRALE_INCENDIO,
        val tipoCustom: String? = null,
        override val indirizzo: String,
        override val installazione: String? = null,
        override val descrizione: String,
        override val zona: String? = null,
        override val dataInstallazione: String? = null,
        override val note: String? = null
    ) : DispositivoRilevazione() {
        override val labelTipo: String
            get() = if (tipo == TipoComponenteComuneAntincendio.ALTRO && !tipoCustom.isNullOrBlank()) {
                tipoCustom.trim().uppercase()
            } else {
                tipo.label
            }

        override val labelSezione: String
            get() = "COMUNI"
    }

    @Serializable
    @SerialName("RivelatoreLoop")
    data class RivelatoreLoop(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val loop: Int = 1,
        val numeroIndirizzo: Int,
        val tipo: TipoRivelatore = TipoRivelatore.FUMO,
        override val installazione: String? = null,
        override val descrizione: String,
        override val zona: String? = null,
        override val dataInstallazione: String? = null,
        override val note: String? = null
    ) : DispositivoRilevazione() {
        init {
            require(loop in 1..8) { "Il loop deve essere compreso tra 1 e 8 (trovato: $loop)" }
            require(numeroIndirizzo in 1..150) { "L'indirizzo del rivelatore deve essere compreso tra 1 e 150 (trovato: $numeroIndirizzo)" }
        }

        override val indirizzo: String
            get() = "RIV.L$loop.$numeroIndirizzo"

        override val labelTipo: String
            get() = tipo.label

        override val labelSezione: String
            get() = "LOOP $loop"
    }

    @Serializable
    @SerialName("ModuloLoop")
    data class ModuloLoop(
        override val id: String = java.util.UUID.randomUUID().toString(),
        val loop: Int = 1,
        val numeroIndirizzo: Int,
        val tipo: TipoModuloAntincendio = TipoModuloAntincendio.MODULO_COMANDO,
        val tipoCustom: String? = null,
        override val installazione: String? = null,
        override val descrizione: String,
        override val zona: String? = null,
        override val dataInstallazione: String? = null,
        override val note: String? = null
    ) : DispositivoRilevazione() {
        init {
            require(loop in 1..8) { "Il loop deve essere compreso tra 1 e 8 (trovato: $loop)" }
            require(numeroIndirizzo in 1..150) { "L'indirizzo del modulo deve essere compreso tra 1 e 150 (trovato: $numeroIndirizzo)" }
        }

        override val indirizzo: String
            get() = "MOD.L$loop.$numeroIndirizzo"

        override val labelTipo: String
            get() = if (tipo == TipoModuloAntincendio.ALTRO && !tipoCustom.isNullOrBlank()) {
                tipoCustom.trim().uppercase()
            } else {
                tipo.label
            }

        override val labelSezione: String
            get() = "LOOP $loop"
    }
}

@Serializable
@SerialName("RilevazioneAntincendio")
data class RilevazioneAntincendio(
    override val id: String = java.util.UUID.randomUUID().toString(),
    override val codIntervento: String,
    override val nomeCompleto: String,
    override val premessa: String? = null,
    override val listaAttivita: List<Attivita> = manutenzioni.domain.service.rules.Uni11224RiCatalog.LISTA_B2_VERIFICA_PERIODICA_SISTEMA,
    override val listaNormative: List<Normativa> = manutenzioni.domain.service.rules.Uni11224RiCatalog.LISTA_NORMATIVE_RI,
    override val cantiereId: String? = null,
    override val quantita: Int = 1,
    override val noteSpecifiche: String? = null,

    override val quantitaComponenti: Map<String, Int> = emptyMap(),

    // Registro componenti: Componenti Comuni + Loop 1..8 (Rivelatori e Moduli, ind. 1..150)
    val listaDispositivi: List<DispositivoRilevazione> = emptyList(),

    // Caratteristiche tecnologiche e anagrafiche UNI 11224:2026 storicizzate nel DB
    val architettura: ArchitetturaImpiantoRi = ArchitetturaImpiantoRi.INDIRIZZATO,
    val hasAsd: Boolean = false,
    val hasRadio: Boolean = false,
    val hasEvac: Boolean = false,
    val classeSensibilitaAsd: ClasseSensibilitaAsd? = null,
    val tempoMaxTrasportoAsd: TempoTrasportoAsd? = null,
    val tipoVerbale: TipoVerbaleRi = TipoVerbaleRi.VERIFICA_PERIODICA_B,
    val estensioneProtezione: EstensioneProtezioneRi = EstensioneProtezioneRi.TOTALE,
    val localiProtetti: String? = null,
    val tipoEsecuzione: TipoEsecuzioneRi = TipoEsecuzioneRi.INDUSTRIALE_DEPOSITO,
    val responsabileSistema: String? = null
) : Impianto(), ImpiantoRilevazione {
    override val kind: ImpiantoKind get() = ImpiantoKind.ANTINCENDIO
    override val isAntincendio: Boolean get() = true

    override val subtipoLabel: String
        get() = listOfNotNull(
            architettura.label,
            "ASD".takeIf { hasAsd },
            "Radio".takeIf { hasRadio },
            "EVAC".takeIf { hasEvac }
        ).joinToString(" • ")

    override val conteggioComponenti: Int
        get() = listaDispositivi.size

    override val extraDettaglio: String?
        get() = if (listaDispositivi.isNotEmpty()) "${listaDispositivi.size} dispositivi" else null

    override val dettaglioAllegato: String
        get() = if (listaDispositivi.isNotEmpty()) "Sì ($conteggioComponenti disp.)" else "No"

    override val hasTechnicalAttachment: Boolean
        get() = listaDispositivi.isNotEmpty()

    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?,
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>,
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto,
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative,
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche,
        quantitaComponenti = quantitaComponenti,
        listaDispositivi = listaDispositivi,
        architettura = architettura,
        hasAsd = hasAsd,
        hasRadio = hasRadio,
        hasEvac = hasEvac,
        classeSensibilitaAsd = classeSensibilitaAsd,
        tempoMaxTrasportoAsd = tempoMaxTrasportoAsd,
        tipoVerbale = tipoVerbale,
        estensioneProtezione = estensioneProtezione,
        localiProtetti = localiProtetti,
        tipoEsecuzione = tipoEsecuzione,
        responsabileSistema = responsabileSistema
    )

    fun withDispositivi(dispositivi: List<DispositivoRilevazione>): RilevazioneAntincendio =
        copy(listaDispositivi = dispositivi)

    override fun instantiateForCantiere(cantiereId: String?, newId: String): RilevazioneAntincendio =
        copy(
            id = newId,
            cantiereId = cantiereId,
            quantita = 1,
            listaAttivita = manutenzioni.domain.service.rules.Uni11224RiCatalog.normalizeRiAttivita(listaAttivita)
        )

    override fun applyTemplateUpdates(template: Impianto): RilevazioneAntincendio {
        val templateRi = template as? RilevazioneAntincendio
        return copy(
            nomeCompleto = template.nomeCompleto,
            premessa = template.premessa,
            listaAttivita = manutenzioni.domain.service.rules.Uni11224RiCatalog.normalizeRiAttivita(template.listaAttivita),
            listaNormative = template.listaNormative,
            listaDispositivi = this.listaDispositivi.ifEmpty { templateRi?.listaDispositivi ?: emptyList() },
            quantitaComponenti = this.quantitaComponenti.ifEmpty { templateRi?.quantitaComponenti ?: emptyMap() }
        )
    }
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
    override val kind: ImpiantoKind get() = ImpiantoKind.GAS
    override val isGas: Boolean get() = true

    override val conteggioComponenti: Int
        get() = quantitaComponenti.values.sum()

    override val extraDettaglio: String?
        get() = if (quantitaComponenti.isNotEmpty()) "$conteggioComponenti componenti" else null

    override val dettaglioAllegato: String
        get() = if (conteggioComponenti > 0) "Sì ($conteggioComponenti comp.)" else "No"

    override val hasTechnicalAttachment: Boolean
        get() = conteggioComponenti > 0

    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto = copy(
        id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
        premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
        cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche,
        quantitaComponenti = quantitaComponenti
    )

    override fun instantiateForCantiere(cantiereId: String?, newId: String): RilevazioneGas =
        copy(id = newId, cantiereId = cantiereId, quantita = 1)

    override fun applyTemplateUpdates(template: Impianto): RilevazioneGas {
        val templateGas = template as? RilevazioneGas
        return copy(
            nomeCompleto = template.nomeCompleto,
            premessa = template.premessa,
            listaAttivita = template.listaAttivita,
            listaNormative = template.listaNormative,
            quantitaComponenti = this.quantitaComponenti.ifEmpty { templateGas?.quantitaComponenti ?: emptyMap() }
        )
    }
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
    override val kind: ImpiantoKind get() = ImpiantoKind.QUADRO_MQT
    override val isQuadroMqt: Boolean get() = true

    override val subtipoLabel: String?
        get() = tipoInterruttore?.let { "Interruttore: ${it.label}" }

    override fun formatZonaAttivita(attivita: Attivita): String {
        if (attivita.zona.isNullOrBlank()) return "attività"
        val switchLabels = setOf("Interruttore", "interruttore", "ISF6", "SF6", "ISV", "IVOR")
        if (switchLabels.any { it.equals(attivita.zona, ignoreCase = true) }) {
            return tipoInterruttore?.label ?: "SF6"
        }
        return attivita.zona
    }

    override fun copyWithBasicParams(
        id: String, codIntervento: String, nomeCompleto: String, premessa: String?, 
        listaAttivita: List<Attivita>, listaNormative: List<Normativa>, 
        cantiereId: String?, quantita: Int, noteSpecifiche: String?
    ): Impianto {
        val base = copy(
            id = id, codIntervento = codIntervento, nomeCompleto = nomeCompleto, 
            premessa = premessa, listaAttivita = listaAttivita, listaNormative = listaNormative, 
            cantiereId = cantiereId, quantita = quantita, noteSpecifiche = noteSpecifiche,
            tipoInterruttore = tipoInterruttore
        )
        return if (tipoInterruttore != null) base.withTipoInterruttore(tipoInterruttore) else base
    }

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

    override fun instantiateForCantiere(cantiereId: String?, newId: String): QuadroMQT {
        val tipo = tipoInterruttore ?: MqtSwitchType.SF6
        return copy(id = newId, cantiereId = cantiereId, quantita = 1, tipoInterruttore = tipo)
            .withTipoInterruttore(tipo)
    }

    override fun applyTemplateUpdates(template: Impianto): QuadroMQT {
        val effectiveSwitch = this.tipoInterruttore ?: (template as? QuadroMQT)?.tipoInterruttore
        val base = copy(
            nomeCompleto = template.nomeCompleto,
            premessa = template.premessa,
            listaAttivita = template.listaAttivita,
            listaNormative = template.listaNormative,
            tipoInterruttore = effectiveSwitch
        )
        return if (effectiveSwitch != null) base.withTipoInterruttore(effectiveSwitch) else base
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
    val zona: String? = null, // Sotto-sistema/zona dell'impianto (es. "Q.M.T.", "SEZ.", "Interruttore", "SF6", "ISV", "IVOR")
    val obbligatoria: Boolean = true, // Colonna "O" (Obbligatorio = x) nei libretti UNI 11224:2026
    val notaPrecompilata: String? = null // Nota operativa pre-stampata o suggerita (es. "A campione scollegando un morsetto")
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
    val dataInizioContratto: String? = null,
    val dataIntervento: String? = null,
    val dataInterventoPrecedente: String? = null,
    val nomeManutentore: String? = null,
    /** Indirizzo della sede operativa / cantiere (R3). */
    val indirizzo: String? = null,
    /** Percorso o Data URI base64 della firma digitale del tecnico manutentore (R26). */
    val firmaManutentore: String? = null
)

/**
 * Normalizza le date del cantiere rimuovendo spazi superflui e convertendo stringhe vuote in null.
 */
fun Cantiere.normalizeDates(): Cantiere {
    val trimmedContratto = dataInizioContratto?.trim()?.ifBlank { null }
    val trimmedIntervento = dataIntervento?.trim()?.ifBlank { null }
    val trimmedPrecedente = dataInterventoPrecedente?.trim()?.ifBlank { null }
    return if (dataInizioContratto != trimmedContratto ||
        dataIntervento != trimmedIntervento ||
        dataInterventoPrecedente != trimmedPrecedente
    ) {
        copy(
            dataInizioContratto = trimmedContratto,
            dataIntervento = trimmedIntervento,
            dataInterventoPrecedente = trimmedPrecedente
        )
    } else {
        this
    }
}

/**
 * Promuove l'attuale [Cantiere.dataIntervento] a [Cantiere.dataInterventoPrecedente],
 * impostando opzionalmente la nuova data di intervento.
 */
fun Cantiere.promoteInterventoToPrecedente(nuovaDataIntervento: String? = null): Cantiere {
    val current = dataIntervento?.trim()?.ifBlank { null }
    val trimmedNuova = nuovaDataIntervento?.trim()?.ifBlank { null }
    return copy(
        dataInterventoPrecedente = current ?: dataInterventoPrecedente,
        dataIntervento = trimmedNuova
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
        this !is ImpiantoEmergenza && ImpiantoCodici.isEmergenza(codIntervento) -> {
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
        this !is QuadroBT && ImpiantoCodici.isQuadroBT(codIntervento) -> {
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
        this !is RilevazioneAntincendio && ImpiantoCodici.isAntincendio(codIntervento) -> {
            RilevazioneAntincendio(
                id = id,
                codIntervento = codIntervento,
                nomeCompleto = nomeCompleto,
                premessa = premessa ?: manutenzioni.domain.service.rules.Uni11224RiCatalog.PREMESSA_SICUREZZA_RI,
                listaAttivita = manutenzioni.domain.service.rules.Uni11224RiCatalog.normalizeRiAttivita(numberedAttivita),
                listaNormative = listaNormative.ifEmpty { manutenzioni.domain.service.rules.Uni11224RiCatalog.LISTA_NORMATIVE_RI },
                cantiereId = cantiereId,
                quantita = quantita,
                noteSpecifiche = noteSpecifiche,
                quantitaComponenti = (this as? ImpiantoRilevazione)?.quantitaComponenti ?: emptyMap(),
                listaDispositivi = (this as? RilevazioneAntincendio)?.listaDispositivi ?: emptyList()
            )
        }
        this is RilevazioneAntincendio -> {
            val normalizedAttivita = manutenzioni.domain.service.rules.Uni11224RiCatalog.normalizeRiAttivita(numberedAttivita)
            if (normalizedAttivita != listaAttivita) copy(listaAttivita = normalizedAttivita) else this
        }
        this !is RilevazioneGas && ImpiantoCodici.isGas(codIntervento) -> {
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
        this !is QuadroMQT && ImpiantoCodici.isQuadroMQT(codIntervento) -> {
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

/**
 * Snapshot immutabile di una sessione di verifica / generazione schede PDF per un cantiere (R25, R30).
 * Garantisce che il nome del manutentore, le date dell'intervento e i file PDF generati
 * rimangano storicizzati nel tempo senza essere sovrascritti dai cicli successivi.
 */
@Serializable
data class SessioneVerifica(
    val id: String = java.util.UUID.randomUUID().toString(),
    val cantiereId: String,
    val clienteId: String = "",
    val meseAnno: String = "",
    val dataInizioContratto: String? = null,
    val dataIntervento: String? = null,
    val dataInterventoPrecedente: String? = null,
    val nomeManutentore: String? = null,
    val dataCreazione: String = "",
    val cartellaOutput: String? = null,
    val elencoFilePdf: List<String> = emptyList(),
    val riepilogoImpianti: List<String> = emptyList(),
    val noteSessione: String? = null
)

/**
 * Ruoli utente per il controllo accessi RBAC (R31).
 */
@Serializable
enum class RuoloUtente(val label: String) {
    ADMIN("Amministratore"),
    TECNICO("Tecnico Manutentore")
}

/**
 * Profilo utente per la gestione permessi e visibilità cantieri (R31).
 */
@Serializable
data class Utente(
    val id: String = java.util.UUID.randomUUID().toString(),
    val username: String,
    val nomeCompleto: String,
    val ruolo: RuoloUtente = RuoloUtente.TECNICO,
    val cantieriAssegnatiIds: Set<String> = emptySet()
)


