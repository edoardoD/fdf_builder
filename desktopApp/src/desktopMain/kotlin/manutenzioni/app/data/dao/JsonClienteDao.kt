package manutenzioni.app.data.dao

import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.Cliente

class JsonClienteDao(private val storage: JsonDatabaseStorage) {

    suspend fun caricaClienti(): List<Cliente> = storage.read { it.clienti }

    suspend fun salvaCliente(cliente: Cliente) {
        storage.update { db ->
            val list = db.clienti.toMutableList()
            val index = list.indexOfFirst { it.id == cliente.id }
            if (index >= 0) {
                list[index] = cliente
            } else {
                list.add(cliente)
            }
            db.copy(clienti = list)
        }
    }

    suspend fun updateCliente(id: String, newName: String) {
        storage.update { db ->
            val list = db.clienti.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(nome = newName)
                db.copy(clienti = list)
            } else {
                db
            }
        }
    }

    /**
     * Eliminazione a cascata atomica:
     * Rimuove il cliente, tutti i suoi cantieri e tutti gli impianti associati a tali cantieri.
     * Gli impianti template globali (cantiereId == null) vengono preservati.
     */
    suspend fun eliminaCliente(id: String) {
        storage.update { db ->
            val updatedClienti = db.clienti.filterNot { it.id == id }
            val cantieriDaEliminare = db.cantieri.filter { it.clienteId == id }.map { it.id }.toSet()
            val updatedCantieri = db.cantieri.filterNot { it.clienteId == id }
            val updatedImpianti = db.impianti.filterNot { it.cantiereId != null && it.cantiereId in cantieriDaEliminare }
            db.copy(
                clienti = updatedClienti,
                cantieri = updatedCantieri,
                impianti = updatedImpianti
            )
        }
    }
}
