package manutenzioni.app.ui

import manutenzioni.domain.model.*
import manutenzioni.domain.service.FrequencyFilter
import java.io.File
import java.time.YearMonth

/** Sezioni principali dell'applicazione */
enum class AppSection {
    OPERATIVITA,
    CALENDARIO,
    DATABASE
}

/** Tabs della sezione amministrazione, con etichetta visualizzata nella TabRow */
enum class AdminTab(val label: String) {
    CLIENTI("CLIENTI"),
    CANTIERI("CANTIERI"),
    /** Template master globali delle schede (R1: ex "Impianti Locali / Globali") */
    IMPIANTI_GLOBALI("MASTER SCHEDE DI VERIFICA"),
    /** Gestione profili utenti e permessi RBAC (R31) */
    UTENTI("UTENTI E PERMESSI")
}

/**
 * Stato immutabile dell'interfaccia utente.
 * Ogni cambiamento genera una nuova istanza (unidirectional data flow).
 */
data class ManutenzioniUiState(
    val impianti: List<Impianto> = emptyList(),
    val selectedImpianto: Impianto? = null,
    val frequenzePerImpianto: Map<String, Periodo> = emptyMap(),
    /** Selezione multipla di periodicità per ciascun impianto del cantiere (R21) */
    val frequenzeSelezionatePerImpianto: Map<String, Set<Periodo>> = emptyMap(),
    /** Mappa pre-calcolata delle frequenze disponibili per ciascun impianto del cantiere */
    val frequenzeDisponibiliPerImpianto: Map<String, List<Periodo>> = emptyMap(),
    val clienti: List<Cliente> = emptyList(),
    val selectedCliente: Cliente? = null,
    val cantieriDisponibili: List<Cantiere> = emptyList(),
    val selectedCantiere: Cantiere? = null,
    val impiantiGlobali: List<Impianto> = emptyList(),
    val impiantiDelCantiere: List<Impianto> = emptyList(),
    val impiantiSelezionati: Map<String, Boolean> = emptyMap(),
    val pdfFile: File? = null,
    val isLoading: Boolean = false,
    val statusMessage: String = "Seleziona un cliente per iniziare",
    val errorMessage: String? = null,
    val currentSection: AppSection = AppSection.OPERATIVITA,
    val currentAdminTab: AdminTab = AdminTab.CLIENTI,
    /** Progresso batch: "Generazione copia X di N..." (null se non in corso) */
    val batchProgress: String? = null,
    /** Lista dei file generati nell'ultimo batch */
    val generatedFiles: List<File> = emptyList(),
    val componentiStandard: List<ComponenteStandard> = emptyList(),
    val catalogoApprovato: List<ComponenteApprovato> = emptyList(),
    /** Lista completa di tutti i cantieri registrati nel database (per vista Calendario Globale e RBAC) */
    val tuttiCantieri: List<Cantiere> = emptyList(),
    /** Calendario Globale Multi-Cantiere raggruppato per mese (es. "Maggio 2026" -> tutti i cantieri con manutenzioni previste — R27) */
    val calendarioGlobale: List<MeseScadenzaGlobaleGroup> = emptyList(),
    /** Filtro mese attivo nel Calendario Globale (es. "2026-05" per Maggio 2026, oppure null per tutti i mesi) */
    val meseCalendarioSelezionato: String? = null,
    /** Orizzonte temporale in mesi per il calcolo del calendario scadenze (default 12 mesi) */
    val orizzonteScadenzeMesi: Int = 12,
    /** Storico sessioni di verifica salvate per il cantiere selezionato (R25, R30) */
    val sessioniVerificaCantiere: List<SessioneVerifica> = emptyList(),
    /** Trigger UDF: impianto RI appena aggiunto al cantiere per aprire subito l'inserimento componenti/loop */
    val pendingAntincendioLoopTrigger: RilevazioneAntincendio? = null,
    /** Eventi di manutenzione pianificati e gestiti a calendario (Interactive Scheduler) */
    val eventiManutenzione: List<EventoManutenzione> = emptyList(),
    /** Data del giorno correntemente selezionato a calendario ("dd/MM/yyyy", es. "22/10/2026") */
    val dataCalendarioSelezionata: String? = null,
    /** Mese attualmente visualizzato nella griglia interattiva del calendario */
    val meseVisualizzatoCalendario: YearMonth = YearMonth.now(),
    /** Lista profili utenti per controllo accessi RBAC (R31) */
    val utenti: List<Utente> = emptyList(),
    /** Utente attivo nella sessione corrente (default: Amministratore) */
    val utenteAttivo: Utente = Utente(
        id = "usr-admin-default",
        username = "admin",
        nomeCompleto = "Amministratore di Sistema",
        ruolo = RuoloUtente.ADMIN
    )
) {
    val isAdmin: Boolean
        get() = utenteAttivo.ruolo == RuoloUtente.ADMIN

    /** Cantieri visibili in base al ruolo RBAC (R31): tutti per ADMIN, solo quelli assegnati per TECNICO */
    val cantieriVisibili: List<Cantiere>
        get() = if (isAdmin) {
            cantieriDisponibili
        } else {
            cantieriDisponibili.filter { it.id in utenteAttivo.cantieriAssegnatiIds }
        }

    /** Cantieri globali di tutti i clienti visibili nel Calendario in base al ruolo RBAC (R31) */
    val tuttiCantieriVisibili: List<Cantiere>
        get() = if (isAdmin) {
            tuttiCantieri
        } else {
            tuttiCantieri.filter { it.id in utenteAttivo.cantieriAssegnatiIds }
        }

    /** Eventi del mese correntemente visualizzato nella griglia del calendario */
    val eventiMeseVisualizzato: List<EventoManutenzione>
        get() {
            val ymTarget = "%04d-%02d".format(meseVisualizzatoCalendario.year, meseVisualizzatoCalendario.monthValue)
            return eventiManutenzione.filter { ev ->
                val d = ev.dataPianificata.trim()
                val parts = d.split("/", "-", ".")
                if (parts.size == 3) {
                    val y = if (parts[0].length == 4) parts[0] else parts[2]
                    val m = (if (parts[0].length == 4) parts[1] else parts[1]).padStart(2, '0')
                    "$y-$m" == ymTarget
                } else false
            }
        }

    /** Mappa di tutti gli eventi del mese raggruppati per data ("dd/MM/yyyy") */
    val mappaEventiPerGiorno: Map<String, List<EventoManutenzione>>
        get() = eventiMeseVisualizzato.groupBy { it.dataPianificata.trim() }

    /** Eventi pianificati per il giorno selezionato a calendario (o emptyList se nessun giorno è selezionato) */
    val eventiGiornoSelezionato: List<EventoManutenzione>
        get() = dataCalendarioSelezionata?.let { sel ->
            eventiManutenzione.filter { it.dataPianificata.trim() == sel.trim() }
        } ?: emptyList()

    /**
     * Risolve le frequenze disponibili per l'impianto specificato usando la mappa precalcolata
     * con fallback trasparente al calcolo via FrequencyFilter.
     */
    fun getFrequenzeDisponibili(impianto: Impianto): List<Periodo> {
        val cached = frequenzeDisponibiliPerImpianto[impianto.id]
        if (!cached.isNullOrEmpty()) return cached
        return FrequencyFilter.frequenzeDisponibili(impianto.listaAttivita)
    }
}
