package manutenzioni.domain

import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto

interface ManutenzioneRepository {
    suspend fun salvaImpianto(impianto: Impianto)
    suspend fun caricaImpianti(): List<Impianto>
    suspend fun eliminaImpianto(id: String)
    suspend fun getImpianto(codIntervento: String): Impianto?
    suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto)

    // --- CRUD Clienti ---
    suspend fun caricaClienti(): List<Cliente>
    suspend fun salvaCliente(cliente: Cliente)
    suspend fun updateCliente(id: String, newName: String)
    suspend fun eliminaCliente(id: String)

    // --- Getters for new workflow ---
    suspend fun getCantieriForCliente(clienteId: String): List<Cantiere>
    suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto>
    
    // --- CRUD Cantieri ---
    suspend fun salvaCantiere(cantiere: Cantiere)
    suspend fun updateCantiere(id: String, newName: String)
    suspend fun eliminaCantiere(id: String)
    
    // --- Anagrafica Componenti ---
    suspend fun caricaComponentiStandard(): List<manutenzioni.domain.model.ComponenteStandard>
    suspend fun salvaComponenteStandard(componente: manutenzioni.domain.model.ComponenteStandard)

    // --- Catalogo Approvato (ETIM & Equivalenze) ---
    suspend fun caricaCatalogoApprovato(): List<manutenzioni.domain.model.ComponenteApprovato>
    suspend fun salvaOAggiornaComponenteApprovato(approvato: manutenzioni.domain.model.ComponenteApprovato)
    suspend fun trovaEquivalentiApprovati(etimClassId: String): List<manutenzioni.domain.model.ComponenteApprovato>

    // --- Storicizzazione Sessioni di Verifica (Wave 4: R25, R30) ---
    suspend fun salvaSessioneVerifica(sessione: manutenzioni.domain.model.SessioneVerifica) {}
    suspend fun getSessioniPerCantiere(cantiereId: String): List<manutenzioni.domain.model.SessioneVerifica> = emptyList()
    suspend fun eliminaSessioneVerifica(id: String) {}

    // --- Gestione Utenti & RBAC (Wave 4: R31) ---
    suspend fun caricaUtenti(): List<manutenzioni.domain.model.Utente> = emptyList()
    suspend fun salvaUtente(utente: manutenzioni.domain.model.Utente) {}
    suspend fun eliminaUtente(id: String) {}

    // --- Eventi di Manutenzione & Scheduler Ricorrenze ---
    suspend fun salvaEventoManutenzione(evento: manutenzioni.domain.model.EventoManutenzione) {}
    suspend fun salvaEventiManutenzioneBatch(eventi: List<manutenzioni.domain.model.EventoManutenzione>) {}
    suspend fun caricaEventiManutenzione(): List<manutenzioni.domain.model.EventoManutenzione> = emptyList()
    suspend fun caricaEventiPerMese(annoMese: String): List<manutenzioni.domain.model.EventoManutenzione> = emptyList()
    suspend fun caricaEventiPerCantiere(cantiereId: String): List<manutenzioni.domain.model.EventoManutenzione> = emptyList()
    suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean = false) {}
    suspend fun aggiornaStatoEvento(id: String, nuovoStato: manutenzioni.domain.model.StatoEventoManutenzione, dataCompletamento: String? = null) {}
}
