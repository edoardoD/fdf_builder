package manutenzioni.app.data.dao

import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.normalizeDates

class JsonCantiereDao(private val storage: JsonDatabaseStorage) {

    suspend fun caricaCantieri(): List<Cantiere> = storage.read { db ->
        db.cantieri.map { it.normalizeDates() }
    }

    suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> = storage.read { db ->
        db.cantieri.filter { it.clienteId == clienteId }.map { it.normalizeDates() }
    }

    suspend fun salvaCantiere(cantiere: Cantiere) {
        val normalized = cantiere.normalizeDates()
        storage.update { db ->
            val list = db.cantieri.toMutableList()
            val index = list.indexOfFirst { it.id == normalized.id }
            if (index >= 0) {
                list[index] = normalized
            } else {
                list.add(normalized)
            }
            db.copy(cantieri = list)
        }
    }

    suspend fun updateCantiere(id: String, newName: String) {
        storage.update { db ->
            val list = db.cantieri.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                list[index] = list[index].copy(nome = newName).normalizeDates()
                db.copy(cantieri = list)
            } else {
                db
            }
        }
    }

    suspend fun eliminaCantiere(id: String) {
        storage.update { db ->
            val updated = db.cantieri.filterNot { it.id == id }
            val updatedImpianti = db.impianti.filterNot { it.cantiereId == id }
            db.copy(cantieri = updated, impianti = updatedImpianti)
        }
    }
}
