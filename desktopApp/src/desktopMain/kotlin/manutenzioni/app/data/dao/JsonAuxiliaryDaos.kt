package manutenzioni.app.data.dao

import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.*
import manutenzioni.domain.service.CalendarioScadenzeCalculator

class JsonComponentiDao(private val storage: JsonDatabaseStorage) {
    suspend fun caricaComponentiStandard(): List<ComponenteStandard> = storage.read { it.componenti }

    suspend fun salvaComponenteStandard(componente: ComponenteStandard) {
        storage.update { db ->
            val list = db.componenti.toMutableList()
            val index = list.indexOfFirst { it.id == componente.id }
            if (index >= 0) list[index] = componente else list.add(componente)
            db.copy(componenti = list)
        }
    }
}

class JsonCatalogoApprovatoDao(private val storage: JsonDatabaseStorage) {
    suspend fun caricaCatalogoApprovato(): List<ComponenteApprovato> = storage.read { it.catalogoApprovato }

    suspend fun salvaOAggiornaComponenteApprovato(approvato: ComponenteApprovato) {
        storage.update { db ->
            val list = db.catalogoApprovato.toMutableList()
            val index = list.indexOfFirst {
                it.etimClassId == approvato.etimClassId && it.caratteristicheTecniche == approvato.caratteristicheTecniche
            }
            if (index >= 0) {
                val esistente = list[index]
                val variantiUnite = esistente.variantiProduttore.toMutableMap()
                variantiUnite.putAll(approvato.variantiProduttore)
                list[index] = esistente.copy(variantiProduttore = variantiUnite)
            } else {
                list.add(approvato)
            }
            db.copy(catalogoApprovato = list)
        }
    }

    suspend fun trovaEquivalentiApprovati(etimClassId: String): List<ComponenteApprovato> = storage.read { db ->
        db.catalogoApprovato.filter { it.etimClassId == etimClassId }
    }
}

class JsonSessioniVerificaDao(private val storage: JsonDatabaseStorage) {
    suspend fun caricaSessioniVerifica(): List<SessioneVerifica> = storage.read { it.sessioniVerifica }

    suspend fun salvaSessioneVerifica(sessione: SessioneVerifica) {
        storage.update { db ->
            val list = db.sessioniVerifica.toMutableList()
            val index = list.indexOfFirst { it.id == sessione.id }
            if (index >= 0) {
                list[index] = sessione
            } else {
                list.add(0, sessione) // Ordine cronologico decrescente (la più recente prima)
            }
            db.copy(sessioniVerifica = list)
        }
    }

    suspend fun getSessioniPerCantiere(cantiereId: String): List<SessioneVerifica> = storage.read { db ->
        db.sessioniVerifica.filter { it.cantiereId == cantiereId }
    }

    suspend fun eliminaSessioneVerifica(id: String) {
        storage.update { db ->
            val updated = db.sessioniVerifica.filterNot { it.id == id }
            db.copy(sessioniVerifica = updated)
        }
    }
}

class JsonUtentiDao(private val storage: JsonDatabaseStorage) {
    suspend fun caricaUtenti(): List<Utente> = storage.read { it.utenti }

    suspend fun salvaUtente(utente: Utente) {
        storage.update { db ->
            val list = db.utenti.toMutableList()
            val index = list.indexOfFirst { it.id == utente.id }
            if (index >= 0) list[index] = utente else list.add(utente)
            db.copy(utenti = list)
        }
    }

    suspend fun eliminaUtente(id: String) {
        storage.update { db ->
            val list = db.utenti
            // Guardrail di sicurezza: non eliminare l'ultimo amministratore di sistema
            val hasOtherAdmin = list.count { it.ruolo == RuoloUtente.ADMIN && it.id != id } > 0
            val targetIsAdmin = list.firstOrNull { it.id == id }?.ruolo == RuoloUtente.ADMIN

            if (hasOtherAdmin || !targetIsAdmin) {
                val updated = list.filterNot { it.id == id }
                db.copy(utenti = updated)
            } else {
                db
            }
        }
    }
}

class JsonEventiManutenzioneDao(private val storage: JsonDatabaseStorage) {
    suspend fun salvaEventoManutenzione(evento: EventoManutenzione) {
        storage.update { db ->
            val list = db.eventiManutenzione.toMutableList()
            val index = list.indexOfFirst { it.id == evento.id }
            if (index >= 0) list[index] = evento else list.add(evento)
            db.copy(eventiManutenzione = list)
        }
    }

    suspend fun salvaEventiManutenzioneBatch(eventi: List<EventoManutenzione>) {
        storage.update { db ->
            val map = db.eventiManutenzione.associateBy { it.id }.toMutableMap()
            eventi.forEach { map[it.id] = it }
            db.copy(eventiManutenzione = map.values.toList())
        }
    }

    suspend fun caricaEventiManutenzione(): List<EventoManutenzione> = storage.read { it.eventiManutenzione }

    suspend fun caricaEventiPerMese(annoMese: String): List<EventoManutenzione> = storage.read { db ->
        val normalized = annoMese.replace("_", "-")
        db.eventiManutenzione.filter { ev ->
            val d = ev.dataPianificata.trim()
            val parts = d.split("/", "-", ".")
            if (parts.size == 3) {
                val y = if (parts[0].length == 4) parts[0] else parts[2]
                val m = (if (parts[0].length == 4) parts[1] else parts[1]).padStart(2, '0')
                "$y-$m" == normalized
            } else false
        }
    }

    suspend fun caricaEventiPerCantiere(cantiereId: String): List<EventoManutenzione> = storage.read { db ->
        db.eventiManutenzione.filter { it.cantiereId == cantiereId }
    }

    suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean) {
        storage.update { db ->
            val target = db.eventiManutenzione.find { it.id == id }
            if (target != null && eliminaSerieFutura && !target.serieRicorrenzaId.isNullOrBlank()) {
                val serieId = target.serieRicorrenzaId
                val baseDate = CalendarioScadenzeCalculator.parseDataRiferimento(target.dataPianificata)
                val updated = db.eventiManutenzione.filterNot { ev ->
                    ev.serieRicorrenzaId == serieId &&
                        CalendarioScadenzeCalculator.parseDataRiferimento(ev.dataPianificata) >= baseDate
                }
                db.copy(eventiManutenzione = updated)
            } else {
                val updated = db.eventiManutenzione.filterNot { it.id == id }
                db.copy(eventiManutenzione = updated)
            }
        }
    }

    suspend fun aggiornaStatoEvento(
        id: String,
        nuovoStato: StatoEventoManutenzione,
        dataCompletamento: String?
    ) {
        storage.update { db ->
            val list = db.eventiManutenzione.toMutableList()
            val index = list.indexOfFirst { it.id == id }
            if (index >= 0) {
                val corrente = list[index]
                list[index] = corrente.copy(
                    stato = nuovoStato,
                    dataCompletamentoEffettiva = if (nuovoStato == StatoEventoManutenzione.COMPLETATO) {
                        dataCompletamento ?: corrente.dataPianificata
                    } else corrente.dataCompletamentoEffettiva
                )
                db.copy(eventiManutenzione = list)
            } else {
                db
            }
        }
    }
}
