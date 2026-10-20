package manutenzioni.app.data

import com.mongodb.client.model.Filters.`in`
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.normalizeType
import manutenzioni.domain.model.normalizeDates

import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.RilevazioneAntincendio

class MongoManutenzioneRepository(
    connectionString: String = "mongodb://localhost:27017",
    dbName: String = "manutenzioni_db"
) : ManutenzioneRepository {
    private val client = MongoClient.create(connectionString)
    private val database = client.getDatabase(dbName)

    private val impiantiCollection = database.getCollection<Impianto>("impianti")
    private val clientiCollection = database.getCollection<Cliente>("clienti")
    private val cantieriCollection = database.getCollection<Cantiere>("cantieri")
    private val componentiCollection = database.getCollection<manutenzioni.domain.model.ComponenteStandard>("componenti")
    private val catalogoApprovatoCollection = database.getCollection<manutenzioni.domain.model.ComponenteApprovato>("catalogo_approvato")
    private val sessioniVerificaCollection = database.getCollection<manutenzioni.domain.model.SessioneVerifica>("sessioni_verifica")
    private val utentiCollection = database.getCollection<manutenzioni.domain.model.Utente>("utenti")
    private val eventiManutenzioneCollection = database.getCollection<manutenzioni.domain.model.EventoManutenzione>("eventi_manutenzione")

    override suspend fun salvaImpianto(impianto: Impianto) {
        // Upsert by id
        impiantiCollection.replaceOne(
            filter = eq("id", impianto.id),
            replacement = impianto,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun caricaImpianti(): List<Impianto> {
        return impiantiCollection.find().toList().map { it.normalizeType() }
    }

    override suspend fun eliminaImpianto(id: String) {
        impiantiCollection.deleteOne(eq("id", id))
    }

    override suspend fun getImpianto(codIntervento: String): Impianto? {
        return impiantiCollection.find(eq("codIntervento", codIntervento)).firstOrNull()?.normalizeType()
    }

    override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) {
        // Update all impianti with the same codIntervento
        val impianti = impiantiCollection.find(eq("codIntervento", impiantoTemplate.codIntervento)).toList().map { it.normalizeType() }
        var hasGlobal = false
        for (imp in impianti) {
            if (imp.cantiereId == null) {
                hasGlobal = true
            }
            val baseUpdated = imp.copyWithBasicParams(
                nomeCompleto = impiantoTemplate.nomeCompleto,
                premessa = impiantoTemplate.premessa,
                listaAttivita = impiantoTemplate.listaAttivita,
                listaNormative = impiantoTemplate.listaNormative
            )
            val updated = when {
                baseUpdated is QuadroMQT && baseUpdated.tipoInterruttore != null ->
                    baseUpdated.withTipoInterruttore(baseUpdated.tipoInterruttore)
                baseUpdated is RilevazioneAntincendio && imp is RilevazioneAntincendio ->
                    baseUpdated.copy(
                        categorieRivelatori = imp.categorieRivelatori,
                        listaDispositivi = imp.listaDispositivi
                    )
                else -> baseUpdated
            }
            salvaImpianto(updated)
        }
        if (!hasGlobal) {
            salvaImpianto(impiantoTemplate.copyWithBasicParams(cantiereId = null))
        }
    }

    override suspend fun caricaClienti(): List<Cliente> {
        return clientiCollection.find().toList()
    }

    override suspend fun salvaCliente(cliente: Cliente) {
        clientiCollection.replaceOne(
            filter = eq("id", cliente.id),
            replacement = cliente,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun updateCliente(id: String, newName: String) {
        val cliente = clientiCollection.find(eq("id", id)).firstOrNull()
        if (cliente != null) {
            salvaCliente(cliente.copy(nome = newName))
        }
    }

    override suspend fun eliminaCliente(id: String) {
        val cantieri = cantieriCollection.find(eq("clienteId", id)).toList()
        val cantiereIds = cantieri.map { it.id }
        if (cantiereIds.isNotEmpty()) {
            impiantiCollection.deleteMany(`in`("cantiereId", cantiereIds))
            cantieriCollection.deleteMany(eq("clienteId", id))
        }
        clientiCollection.deleteOne(eq("id", id))
    }

    override suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> {
        return cantieriCollection.find(eq("clienteId", clienteId)).toList().map { it.normalizeDates() }
    }

    override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> {
        return impiantiCollection.find(eq("cantiereId", cantiereId)).toList().map { it.normalizeType() }
    }

    override suspend fun salvaCantiere(cantiere: Cantiere) {
        val normalized = cantiere.normalizeDates()
        cantieriCollection.replaceOne(
            filter = eq("id", normalized.id),
            replacement = normalized,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun updateCantiere(id: String, newName: String) {
        val cantiere = cantieriCollection.find(eq("id", id)).firstOrNull()?.normalizeDates()
        if (cantiere != null) {
            salvaCantiere(cantiere.copy(nome = newName))
        }
    }

    override suspend fun eliminaCantiere(id: String) {
        impiantiCollection.deleteMany(eq("cantiereId", id))
        cantieriCollection.deleteOne(eq("id", id))
    }
    
    // --- Anagrafica Componenti ---
    override suspend fun caricaComponentiStandard(): List<manutenzioni.domain.model.ComponenteStandard> {
        return componentiCollection.find().toList()
    }

    override suspend fun salvaComponenteStandard(componente: manutenzioni.domain.model.ComponenteStandard) {
        componentiCollection.replaceOne(
            filter = eq("id", componente.id),
            replacement = componente,
            options = ReplaceOptions().upsert(true)
        )
    }

    // --- Catalogo Approvato (ETIM & Equivalenze) ---
    override suspend fun caricaCatalogoApprovato(): List<manutenzioni.domain.model.ComponenteApprovato> {
        return catalogoApprovatoCollection.find().toList()
    }

    override suspend fun salvaOAggiornaComponenteApprovato(approvato: manutenzioni.domain.model.ComponenteApprovato) {
        val esistente = catalogoApprovatoCollection.find(eq("etimClassId", approvato.etimClassId)).toList()
            .firstOrNull { it.caratteristicheTecniche == approvato.caratteristicheTecniche }

        val daSalvare = if (esistente != null) {
            val variantiUnite = esistente.variantiProduttore.toMutableMap()
            variantiUnite.putAll(approvato.variantiProduttore)
            esistente.copy(variantiProduttore = variantiUnite)
        } else {
            approvato
        }

        catalogoApprovatoCollection.replaceOne(
            filter = eq("id", daSalvare.id),
            replacement = daSalvare,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun trovaEquivalentiApprovati(etimClassId: String): List<manutenzioni.domain.model.ComponenteApprovato> {
        return catalogoApprovatoCollection.find(eq("etimClassId", etimClassId)).toList()
    }

    // --- Storicizzazione Sessioni di Verifica (Wave 4: R25, R30) ---
    override suspend fun salvaSessioneVerifica(sessione: manutenzioni.domain.model.SessioneVerifica) {
        sessioniVerificaCollection.replaceOne(
            filter = eq("id", sessione.id),
            replacement = sessione,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun getSessioniPerCantiere(cantiereId: String): List<manutenzioni.domain.model.SessioneVerifica> {
        return sessioniVerificaCollection.find(eq("cantiereId", cantiereId)).toList()
    }

    override suspend fun eliminaSessioneVerifica(id: String) {
        sessioniVerificaCollection.deleteOne(eq("id", id))
    }

    // --- Gestione Utenti & RBAC (Wave 4: R31) ---
    override suspend fun caricaUtenti(): List<manutenzioni.domain.model.Utente> {
        val loaded = utentiCollection.find().toList()
        return loaded.ifEmpty {
            listOf(
                manutenzioni.domain.model.Utente(
                    id = "usr-admin-default",
                    username = "admin",
                    nomeCompleto = "Amministratore di Sistema",
                    ruolo = manutenzioni.domain.model.RuoloUtente.ADMIN
                )
            )
        }
    }

    override suspend fun salvaUtente(utente: manutenzioni.domain.model.Utente) {
        utentiCollection.replaceOne(
            filter = eq("id", utente.id),
            replacement = utente,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun eliminaUtente(id: String) {
        utentiCollection.deleteOne(eq("id", id))
    }

    // --- Eventi di Manutenzione & Scheduler Ricorrenze ---

    override suspend fun salvaEventoManutenzione(evento: manutenzioni.domain.model.EventoManutenzione) {
        eventiManutenzioneCollection.replaceOne(
            filter = eq("id", evento.id),
            replacement = evento,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun salvaEventiManutenzioneBatch(eventi: List<manutenzioni.domain.model.EventoManutenzione>) {
        for (evento in eventi) {
            salvaEventoManutenzione(evento)
        }
    }

    override suspend fun caricaEventiManutenzione(): List<manutenzioni.domain.model.EventoManutenzione> {
        return eventiManutenzioneCollection.find().toList()
    }

    override suspend fun caricaEventiPerMese(annoMese: String): List<manutenzioni.domain.model.EventoManutenzione> {
        val all = eventiManutenzioneCollection.find().toList()
        val normalized = annoMese.replace("_", "-")
        return all.filter { ev ->
            val d = ev.dataPianificata.trim()
            val parts = d.split("/", "-", ".")
            if (parts.size == 3) {
                val y = if (parts[0].length == 4) parts[0] else parts[2]
                val m = (if (parts[0].length == 4) parts[1] else parts[1]).padStart(2, '0')
                "$y-$m" == normalized
            } else false
        }
    }

    override suspend fun caricaEventiPerCantiere(cantiereId: String): List<manutenzioni.domain.model.EventoManutenzione> {
        return eventiManutenzioneCollection.find(eq("cantiereId", cantiereId)).toList()
    }

    override suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean) {
        if (!eliminaSerieFutura) {
            eventiManutenzioneCollection.deleteOne(eq("id", id))
        } else {
            val target = eventiManutenzioneCollection.find(eq("id", id)).firstOrNull()
            if (target != null && !target.serieRicorrenzaId.isNullOrBlank()) {
                val allInSerie = eventiManutenzioneCollection.find(eq("serieRicorrenzaId", target.serieRicorrenzaId)).toList()
                val baseDate = manutenzioni.domain.service.CalendarioScadenzeCalculator.parseDataRiferimento(target.dataPianificata)
                for (ev in allInSerie) {
                    val evDate = manutenzioni.domain.service.CalendarioScadenzeCalculator.parseDataRiferimento(ev.dataPianificata)
                    if (evDate >= baseDate) {
                        eventiManutenzioneCollection.deleteOne(eq("id", ev.id))
                    }
                }
            } else {
                eventiManutenzioneCollection.deleteOne(eq("id", id))
            }
        }
    }

    override suspend fun aggiornaStatoEvento(
        id: String,
        nuovoStato: manutenzioni.domain.model.StatoEventoManutenzione,
        dataCompletamento: String?
    ) {
        val target = eventiManutenzioneCollection.find(eq("id", id)).firstOrNull() ?: return
        val updated = target.copy(
            stato = nuovoStato,
            dataCompletamentoEffettiva = if (nuovoStato == manutenzioni.domain.model.StatoEventoManutenzione.COMPLETATO) {
                dataCompletamento ?: target.dataPianificata
            } else target.dataCompletamentoEffettiva
        )
        salvaEventoManutenzione(updated)
    }
}
