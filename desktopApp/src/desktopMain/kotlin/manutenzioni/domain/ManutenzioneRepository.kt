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
}
