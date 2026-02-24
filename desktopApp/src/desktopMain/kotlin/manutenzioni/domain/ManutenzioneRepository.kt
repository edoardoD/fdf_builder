package manutenzioni.domain

import manutenzioni.app.data.Cliente
import manutenzioni.app.data.Impianto

interface ManutenzioneRepository {
    suspend fun salvaImpianto(impianto: Impianto)
    suspend fun caricaImpianti(): List<Impianto>
    suspend fun eliminaImpianto(codIntervento: String)
    suspend fun getImpianto(codIntervento: String): Impianto?

    // --- CRUD Clienti ---
    suspend fun caricaClienti(): List<Cliente>
    suspend fun salvaCliente(cliente: Cliente)
    suspend fun eliminaCliente(id: String)
}