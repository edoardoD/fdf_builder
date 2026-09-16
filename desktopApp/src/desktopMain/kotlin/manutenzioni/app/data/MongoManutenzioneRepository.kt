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

    override suspend fun salvaImpianto(impianto: Impianto) {
        // Upsert by id
        impiantiCollection.replaceOne(
            filter = eq("id", impianto.id),
            replacement = impianto,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun caricaImpianti(): List<Impianto> {
        return impiantiCollection.find().toList()
    }

    override suspend fun eliminaImpianto(id: String) {
        impiantiCollection.deleteOne(eq("id", id))
    }

    override suspend fun getImpianto(codIntervento: String): Impianto? {
        return impiantiCollection.find(eq("codIntervento", codIntervento)).firstOrNull()
    }

    override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) {
        // Update all impianti with the same codIntervento
        val impianti = impiantiCollection.find(eq("codIntervento", impiantoTemplate.codIntervento)).toList()
        var hasGlobal = false
        for (imp in impianti) {
            if (imp.cantiereId == null) {
                hasGlobal = true
            }
            val updated = impiantoTemplate.copyWithBasicParams(
                id = imp.id, 
                cantiereId = imp.cantiereId, 
                quantita = imp.quantita, 
                noteSpecifiche = imp.noteSpecifiche
            )
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
        return cantieriCollection.find(eq("clienteId", clienteId)).toList()
    }

    override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> {
        return impiantiCollection.find(eq("cantiereId", cantiereId)).toList()
    }

    override suspend fun salvaCantiere(cantiere: Cantiere) {
        cantieriCollection.replaceOne(
            filter = eq("id", cantiere.id),
            replacement = cantiere,
            options = ReplaceOptions().upsert(true)
        )
    }

    override suspend fun updateCantiere(id: String, newName: String) {
        val cantiere = cantieriCollection.find(eq("id", id)).firstOrNull()
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
}
