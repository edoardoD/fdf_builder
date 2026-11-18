package manutenzioni.app.data.dao

import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.Impianto

class JsonImpiantoDao(private val storage: JsonDatabaseStorage) {

    suspend fun caricaImpianti(): List<Impianto> = storage.read { it.impianti }

    suspend fun salvaImpianto(impianto: Impianto) {
        storage.update { db ->
            val list = db.impianti.toMutableList()
            val index = list.indexOfFirst { it.id == impianto.id }
            if (index >= 0) {
                list[index] = impianto
            } else {
                list.add(impianto)
            }
            db.copy(impianti = list)
        }
    }

    suspend fun eliminaImpianto(id: String) {
        storage.update { db ->
            val updated = db.impianti.filterNot { it.id == id }
            db.copy(impianti = updated)
        }
    }

    suspend fun getImpianto(codIntervento: String): Impianto? = storage.read { db ->
        db.impianti.find { it.codIntervento == codIntervento }
    }

    suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> = storage.read { db ->
        db.impianti.filter { it.cantiereId == cantiereId }
    }

    suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) {
        storage.update { db ->
            var hasGlobal = false
            val updatedList = db.impianti.map { current ->
                if (current.codIntervento == impiantoTemplate.codIntervento) {
                    if (current.cantiereId == null) {
                        hasGlobal = true
                    }
                    current.applyTemplateUpdates(impiantoTemplate)
                } else {
                    current
                }
            }.toMutableList()

            if (!hasGlobal) {
                updatedList.add(impiantoTemplate.copyWithBasicParams(cantiereId = null))
            }

            db.copy(impianti = updatedList)
        }
    }
}
