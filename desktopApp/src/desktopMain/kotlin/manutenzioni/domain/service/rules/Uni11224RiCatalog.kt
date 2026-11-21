package manutenzioni.domain.service.rules

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Normativa
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo

/**
 * Catalogo normativo ufficiale per l'Impianto di Rilevazione e Segnalazione Incendi ("RI" — IRAI)
 * conforme alle norme UNI 11224:2026 e UNI 9795:2021.
 *
 * Tutte le attività hanno periodicità semestrale obbligatoria: [FREQUENZA_SEMESTRALE] (`06 MESI`).
 */
object Uni11224RiCatalog {

    val FREQUENZA_SEMESTRALE: Periodo = Periodo(TipoPeriodo.M, 6)

    const val NORMATIVE_HEADER: String =
        "CEI 0-10:2012, CEI EN 50110:2014, CEI 11-27:2014, CEI 64-8, CEI 64-14:2007, UNI 9795:2021, UNI 11224:2026"

    const val PREMESSA_SICUREZZA_RI: String =
        "Il controllo periodico deve essere effettuato da tecnico manutentore qualificato. Prima di procedere con le prove della parte di rivelazione degli impianti di rivelazione e spegnimento, porre le apparecchiature di comando dello spegnimento automatico in sicurezza, per evitare l'attivazione dello spegnimento a causa delle operazioni di controllo. In modo particolare assicurarsi che gli effetti delle prove (segnalazioni e comandi) non producano situazioni di pericolo o attuazioni indesiderate, pianificando ed organizzando le prove, ed i metodi di prova, con il responsabile della sicurezza e/o con il responsabile del servizio prevenzione e protezione competente."

    val LISTA_NORMATIVE_RI: List<Normativa> = listOf(
        Normativa(
            codNormativa = "UNI 9795:2021",
            descrizione = "Sistemi fissi automatici di rivelazione e di segnalazione allarme d'incendio"
        ),
        Normativa(
            codNormativa = "UNI 11224:2026",
            descrizione = "Controllo iniziale e manutenzione dei sistemi di rivelazione incendi"
        )
    )

    /**
     * Prospetto A.2 — Lista di Riscontro per Verifiche Preliminari sul Sistema (19 attività, Appendice A).
     */
    val LISTA_A2_VERIFICHE_PRELIMINARI: List<Attivita> = listOf(
        attivitaRi(1, "Documentazione", "Rispondenza del sistema al progetto esecutivo", obbligatoria = true),
        attivitaRi(2, "Documentazione", "Rispondenza alle norme di riferimento (vedere dichiarazione di conformità)", obbligatoria = true),
        attivitaRi(3, "Documentazione", "Corrispondenza della documentazione ai componenti installati", obbligatoria = true),
        attivitaRi(4, "Parte Elettrica", "Chiara identificazione delle interconnessioni", obbligatoria = true),
        attivitaRi(5, "Parte Elettrica", "Percorsi dei cavi esenti da interferenze ambientali ed elettriche", obbligatoria = true),
        attivitaRi(6, "Parte Elettrica", "Stesura dei tubi senza inclinazioni anomale", obbligatoria = false),
        attivitaRi(7, "Parte Elettrica", "Esecuzione delle curve e delle giunte a regola d’arte", obbligatoria = true),
        attivitaRi(8, "Parte Elettrica", "Supporti meccanici stabili ed in numero adeguato", obbligatoria = true),
        attivitaRi(9, "Parte Elettrica", "Ingressi dei tubi alle cassette dotati di raccordi adeguati", obbligatoria = false),
        attivitaRi(10, "Parte Elettrica", "Controllo positivo della pulizia dei rivelatori", obbligatoria = true),
        attivitaRi(11, "Parte Elettrica", "Messa a terra delle alimentazioni di rete", obbligatoria = true),
        attivitaRi(12, "Parte Elettrica", "Stabilità dei collegamenti e fissaggio dei morsetti", obbligatoria = false),
        attivitaRi(13, "Parte Elettrica", "Impiego dei capicorda su tutti i collegamenti", obbligatoria = false),
        attivitaRi(14, "Parte Elettrica", "Chiara identificazione e colori di cavi e morsetti", obbligatoria = true),
        attivitaRi(15, "Parte Elettrica", "Grado di riempimento dei tubi secondo norma", obbligatoria = false),
        attivitaRi(16, "Parte Elettrica", "Collegamento e messa a terra del cavo schermato", obbligatoria = false),
        attivitaRi(17, "Terra e Rumore Elettrico", "Controllo della messa a terra", obbligatoria = true),
        attivitaRi(18, "Parte Elettrica", "Controllo del rumore elettrico", obbligatoria = false),
        attivitaRi(
            19,
            "Terra e Rumore Elettrico",
            "Controllo della disponibilità delle parti di ricambio (solo nel caso di controllo iniziale per presa in carico della manutenzione)",
            obbligatoria = true
        )
    )

    /**
     * Prospetto A.3 — Lista di Riscontro per Controlli sul Sistema e Dispositivi (17 attività, Appendice A).
     */
    val LISTA_A3_CONTROLLI_SISTEMA: List<Attivita> = listOf(
        attivitaRi(1, "Centrale", "Efficienza e commutazione delle alimentazioni, con rimozione alimentazione primaria"),
        attivitaRi(2, "Centrale", "Controllo del valore e dello stato delle batterie"),
        attivitaRi(3, "Centrale", "Efficienza di lampade, led e segnalazioni ottiche e digitali"),
        attivitaRi(4, "Centrale", "Efficienza delle segnalazioni acustiche"),
        attivitaRi(5, "Centrale", "Verifica dell’intensità delle segnalazioni acustiche"),
        attivitaRi(6, "Sistemi di Ripetizione", "Efficienza dei segnali di rinvio degli stati di allarme e guasto su ripetitori, modem, comunicatore/combinatori"),
        attivitaRi(7, "Sistemi di Ripetizione", "Efficienza del sistema di visualizzazione grafica e possibilità di inviare e ricevere comandi"),
        attivitaRi(8, "Linee", "Assorbimenti delle linee nei margini di tolleranza previsti"),
        attivitaRi(9, "Linee", "Segnalazione guasto su apertura o corto circuito delle linee di rivelazione o ingresso sorvegliate"),
        attivitaRi(10, "Linee", "Segnalazione guasto su apertura o corto circuito delle linee di comando sorvegliate"),
        attivitaRi(11, "Tutti i componenti", "Esecuzione positiva delle prove di allarme su tutti i componenti che prevede:"),
        attivitaRi(12, "Tutti i componenti", "Segnalazioni coerenti su centrale, lampade, led, display e altri ripetitori"),
        attivitaRi(13, "Tutti i componenti", "Controllo con esito positivo delle funzioni e delle temporizzazioni dei comandi"),
        attivitaRi(14, "Tutti i componenti", "Controllo positivo delle congruenze delle segnalazioni su sistema di visualizzazione grafica e/o stampante"),
        attivitaRi(15, "Funzionale del sistema", "Esecuzione positiva delle prove di guasto a campione con rimozione di alcuni rivelatori dalla base"),
        attivitaRi(
            16,
            "Funzionale del sistema",
            "Controlli addizionali sul sistema radio Segnalazione guasto con rimozione dispositivi a campione Segnalazione guasto con rimozione batteria a campione, vedere punto 8.3.4.5. Misurazione segnale radio"
        ),
        attivitaRi(17, "Funzionale del sistema", "Controllo positivo della udibilità e/o visibilità delle segnalazioni di allarme nell’ambiente protetto")
    )

    /**
     * Prospetto B.2 — Lista di Riscontro per Verifica sul Sistema (13 attività, Appendice B — Verifica Periodica Semestrale).
     */
    val LISTA_B2_VERIFICA_PERIODICA_SISTEMA: List<Attivita> = listOf(
        attivitaRi(1, "Centrale", "Efficienza e commutazione delle alimentazioni, con rimozione alimentazione primaria"),
        attivitaRi(2, "Centrale", "Controllo del valore e dello stato delle batterie"),
        attivitaRi(3, "Centrale", "Efficienza di lampade, led e segnalazioni ottiche e digitali"),
        attivitaRi(4, "Centrale", "Efficienza delle segnalazioni acustiche"),
        attivitaRi(5, "Centrale", "Verifica dell’intensità delle segnalazioni acustiche"),
        attivitaRi(6, "Sistemi di Ripetizione", "Efficienza dei segnali di rinvio degli stati di allarme e guasto su ripetitori, modem, comunicatore/combinatori"),
        attivitaRi(7, "Sistemi di Ripetizione", "Efficienza del sistema di visualizzazione grafica e possibilità di inviare e ricevere comandi"),
        attivitaRi(
            8,
            "Linee",
            "Segnalazione guasto su apertura o corto circuito delle linee di rivelazione o ingresso sorvegliate",
            notaPrecompilata = "A campione scollegando un morsetto"
        ),
        attivitaRi(
            9,
            "Linee",
            "Segnalazione guasto su apertura o corto circuito delle linee di comando sorvegliate",
            notaPrecompilata = "A campione scollegando un morsetto"
        ),
        attivitaRi(
            10,
            "Tutti i componenti",
            "Esecuzione positiva delle prove di allarme su tutti i componenti come indicato nel punto 10.2 UNI 11224",
            notaPrecompilata = "Non barrare esito se presente lista di controllo"
        ),
        attivitaRi(11, "Tutti i componenti", "Controllo positivo delle segnalazione di allarme presso l'impianto"),
        attivitaRi(
            12,
            "Funzionale del sistema",
            "Controlli addizionali sul sistema radio Segnalazione guasto con rimozione dispositivi a campione Segnalazione guasto con rimozione batteria a campione, vedere punto 8.3.4.5. Misurazione segnale radio"
        ),
        attivitaRi(13, "Tutti i componenti", "Controllo positivo della udibilità e/o visibilità delle segnalazioni di allarme nell’ambiente protetto")
    )

    /**
     * Prospetto A.4 / Prospetto B.3 — Lista di Riscontro per Controlli Addizionali su Sistemi ASD (Aspirazione) (14 attività).
     */
    val LISTA_ASD_CONTROLLI_ADDIZIONALI: List<Attivita> = listOf(
        attivitaRi(1, "Rivelatori ASD", "Efficienza e commutazione delle alimentazioni, con rimozione alimentazione primaria"),
        attivitaRi(2, "Rivelatori ASD", "Controllo del valore e dello stato delle batterie"),
        attivitaRi(3, "Rivelatori ASD", "Efficienza di lampade, led e segnalazioni ottiche e digitali del rivelatore ASD"),
        attivitaRi(4, "Rivelatori ASD", "Verifica che le soglie siano state programmate secondo i dati di progetto"),
        attivitaRi(5, "Sistema di Ripetizione", "Efficienza dei segnali di rinvio degli stati di allarme e guasto su ripetitori, modem o comunicatore/combinatore"),
        attivitaRi(6, "Sistema di Ripetizione", "Efficienza del sistema di visualizzazione grafica e possibilità di inviare e ricevere comandi"),
        attivitaRi(7, "Sistema di Aspirazione", "Ispezione visiva dei tratti di tubo per rilevare eventuali ostruzioni o danneggiamenti"),
        attivitaRi(8, "Sistema di Aspirazione", "Controllo con vacuometro delle depressioni su ciascun foro"),
        attivitaRi(9, "Sistema di Aspirazione", "Segnalazione guasto su ostruzione del sistema aspirante ottenuta occludendo i fori (vedi 8.3.3.4)"),
        attivitaRi(10, "ASD a commutazione", "Controllo di commutazione e corrispondenza tra segnalazioni e zone di origine", obbligatoria = false),
        attivitaRi(11, "Segnalazioni", "Controllo di intervento delle soglie di allarme (almeno un punto di aspirazione per ciascun ramo)"),
        attivitaRi(12, "Segnalazioni", "Esito delle logiche funzionali dei comandi e delle Temporizzazioni"),
        attivitaRi(13, "Segnalazioni", "Controllo positivo delle congruenze delle segnalazioni su sistema di visualizzazione grafica e/o stampante"),
        attivitaRi(
            14,
            "Funzionale del sistema",
            "Controllo del tempo massimo di trasporto sul punto di aspirazione più remoto con fumo o sistema di simulazione per ciascun ramo",
            notaPrecompilata = "Tempo rilevato:"
        )
    )

    /**
     * Determina se una lista di attività di un impianto RI appartiene al vecchio schema legacy
     * (es. frequenze 1M/2M/4M/annuali, attività cross-plant con `targetImpiantoCod != null`
     * o vecchie descrizioni pre-UNI 11224:2026) e deve essere sostituita con il catalogo canonico a 06 Mesi.
     */
    fun isLegacyOrInvalidRiAttivita(listaAttivita: List<Attivita>): Boolean {
        if (listaAttivita.isEmpty()) return true
        if (listaAttivita.any { it.frequenza != FREQUENZA_SEMESTRALE || it.targetImpiantoCod != null }) return true
        val firstDesc = listaAttivita.first().descrizione.orEmpty()
        if (firstDesc.contains("controllare il firmware", ignoreCase = true) ||
            firstDesc.contains("eventi registrati nella centrale", ignoreCase = true)
        ) {
            return true
        }
        return false
    }

    /**
     * Normalizza la lista attività di un impianto RI garantendo periodicità 06 Mesi e
     * sostituendo eventuali attività legacy con [LISTA_B2_VERIFICA_PERIODICA_SISTEMA].
     */
    fun normalizeRiAttivita(listaAttivita: List<Attivita>): List<Attivita> {
        return if (isLegacyOrInvalidRiAttivita(listaAttivita)) {
            LISTA_B2_VERIFICA_PERIODICA_SISTEMA
        } else {
            listaAttivita.mapIndexed { idx, att ->
                att.copy(nAttivita = idx + 1, frequenza = FREQUENZA_SEMESTRALE, targetImpiantoCod = null)
            }
        }
    }

    private fun attivitaRi(
        numero: Int,
        zona: String,
        descrizione: String,
        obbligatoria: Boolean = true,
        notaPrecompilata: String? = null
    ): Attivita = Attivita(
        nAttivita = numero,
        tipoAttivita = "Controllo",
        descrizione = descrizione,
        frequenza = FREQUENZA_SEMESTRALE,
        zona = zona,
        obbligatoria = obbligatoria,
        notaPrecompilata = notaPrecompilata
    )
}
