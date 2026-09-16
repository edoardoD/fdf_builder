package manutenzioni.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import java.io.File

/**
 * Implementazione concreta del repository basata su file JSON.
 * Al primo avvio, copia il database demo dalle resources nella working directory.
 * Tutte le operazioni CRUD persistono su file locale.
 */
class JsonManutenzioneRepository(
    fileName: String = "manutenzioni_db.json"
) : ManutenzioneRepository {

    private val dbFile: File = resolveDbPath(fileName)
    private val mutex = Mutex()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Cache in-memory del database */
    private var cache: MutableList<Impianto>? = null
    private var cacheClienti: MutableList<Cliente>? = null
    private var cacheCantieri: MutableList<Cantiere>? = null
    private var cacheComponenti: MutableList<manutenzioni.domain.model.ComponenteStandard>? = null
    private var cacheCatalogoApprovato: MutableList<manutenzioni.domain.model.ComponenteApprovato>? = null

    init {
        println("📂 Database log: ${dbFile.absolutePath}")
        copyDefaultIfMissing()
    }

    /**
     * Risolve il percorso del database in modo che sia scrivibile su ogni SO.
     * Su Desktop usa la cartella home dell'utente.
     */
    private fun resolveDbPath(fileName: String): File {
        val userHome = System.getProperty("user.home")
        val appDataDir = File(userHome, ".manutenzioni-maker")
        if (!appDataDir.exists()) {
            appDataDir.mkdirs()
        }
        return File(appDataDir, fileName)
    }

    /**
     * Se il file JSON locale non esiste, copia quello di default dalle resources.
     */
    private fun copyDefaultIfMissing() {
        if (!dbFile.exists()) {
            val defaultJson = this::class.java.classLoader
                ?.getResourceAsStream("manutenzioni_db.json")
                ?.bufferedReader()
                ?.readText()

            if (defaultJson != null) {
                dbFile.writeText(defaultJson)
                println("✓ Database demo copiato in: ${dbFile.absolutePath}")
            } else {
                // Crea un database vuoto
                val emptyDb = ManutenzioniDatabase(emptyList(), emptyList(), emptyList(), emptyList())
                dbFile.writeText(json.encodeToString(ManutenzioniDatabase.serializer(), emptyDb))
                println("⚠ Database demo non trovato nelle resources. Creato database vuoto.")
            }
        }
    }

    private fun loadFromDisk(): ManutenzioniDatabase {
        return try {
            val content = dbFile.readText()
            json.decodeFromString(ManutenzioniDatabase.serializer(), content)
        } catch (e: Exception) {
            println("Errore nel caricamento del database: ${e.message}")
            ManutenzioniDatabase(emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    private fun saveToDisk(
        impianti: List<Impianto> = getImpiantiCache(), 
        clienti: List<Cliente> = getClientiCache(), 
        cantieri: List<Cantiere> = getCantieriCache(),
        componenti: List<manutenzioni.domain.model.ComponenteStandard> = getComponentiCache(),
        catalogoApprovato: List<manutenzioni.domain.model.ComponenteApprovato> = getCatalogoApprovatoCache()
    ) {
        val db = ManutenzioniDatabase(impianti, clienti, cantieri, componenti, catalogoApprovato)
        dbFile.writeText(json.encodeToString(ManutenzioniDatabase.serializer(), db))
    }

    private fun getImpiantiCache(): MutableList<Impianto> {
        if (cache == null) {
            val db = loadFromDisk()
            cache = db.impianti.toMutableList()
            if (cacheClienti == null) {
                cacheClienti = db.clienti.toMutableList()
            }
            if (cacheCantieri == null) {
                cacheCantieri = db.cantieri.toMutableList()
            }
            if (cacheComponenti == null) {
                cacheComponenti = db.componenti.toMutableList()
            }
            if (cacheCatalogoApprovato == null) {
                cacheCatalogoApprovato = db.catalogoApprovato.toMutableList()
            }
        }
        return cache!!
    }

    private fun getClientiCache(): MutableList<Cliente> {
        if (cacheClienti == null) {
            val db = loadFromDisk()
            cacheClienti = db.clienti.toMutableList()
            if (cache == null) {
                cache = db.impianti.toMutableList()
            }
            if (cacheCantieri == null) {
                cacheCantieri = db.cantieri.toMutableList()
            }
            if (cacheComponenti == null) {
                cacheComponenti = db.componenti.toMutableList()
            }
            if (cacheCatalogoApprovato == null) {
                cacheCatalogoApprovato = db.catalogoApprovato.toMutableList()
            }
        }
        return cacheClienti!!
    }

    private fun getCantieriCache(): MutableList<Cantiere> {
        if (cacheCantieri == null) {
            val db = loadFromDisk()
            cacheCantieri = db.cantieri.toMutableList()
            // Assicura che anche le altre cache siano popolate
            getImpiantiCache()
            getClientiCache()
            getComponentiCache()
            getCatalogoApprovatoCache()
        }
        return cacheCantieri!!
    }

    private fun getComponentiCache(): MutableList<manutenzioni.domain.model.ComponenteStandard> {
        if (cacheComponenti == null) {
            val db = loadFromDisk()
            cacheComponenti = db.componenti.toMutableList()
            // Assicura che anche le altre cache siano popolate
            getImpiantiCache()
            getClientiCache()
            getCantieriCache()
            getCatalogoApprovatoCache()
        }
        return cacheComponenti!!
    }

    private fun getCatalogoApprovatoCache(): MutableList<manutenzioni.domain.model.ComponenteApprovato> {
        if (cacheCatalogoApprovato == null) {
            val db = loadFromDisk()
            cacheCatalogoApprovato = db.catalogoApprovato.toMutableList()
            // Assicura che anche le altre cache siano popolate
            getImpiantiCache()
            getClientiCache()
            getCantieriCache()
            getComponentiCache()
        }
        return cacheCatalogoApprovato!!
    }

    // === Impianti CRUD ===

    override suspend fun salvaImpianto(impianto: Impianto) = mutex.withLock {
        val list = getImpiantiCache()
        val index = list.indexOfFirst { it.id == impianto.id }
        if (index >= 0) {
            list[index] = impianto
        } else {
            list.add(impianto)
        }
        saveToDisk()
    }

    override suspend fun caricaImpianti(): List<Impianto> = mutex.withLock {
        return getImpiantiCache().toList()
    }

    override suspend fun eliminaImpianto(id: String) = mutex.withLock {
        val list = getImpiantiCache()
        list.removeAll { it.id == id }
        saveToDisk()
    }

    override suspend fun getImpianto(codIntervento: String): Impianto? = mutex.withLock {
        return getImpiantiCache().find { it.codIntervento == codIntervento }
    }

    override suspend fun aggiornaImpiantiGlobalmente(impiantoTemplate: Impianto) = mutex.withLock {
        val list = getImpiantiCache()
        var hasGlobal = false
        for (i in list.indices) {
            if (list[i].codIntervento == impiantoTemplate.codIntervento) {
                if (list[i].cantiereId == null) {
                    hasGlobal = true
                }
                list[i] = list[i].copyWithBasicParams(
                    nomeCompleto = impiantoTemplate.nomeCompleto,
                    premessa = impiantoTemplate.premessa,
                    listaAttivita = impiantoTemplate.listaAttivita,
                    listaNormative = impiantoTemplate.listaNormative
                    // id, cantiereId, quantita e noteSpecifiche rimangono inalterati
                )
            }
        }
        if (!hasGlobal) {
            list.add(impiantoTemplate.copyWithBasicParams(cantiereId = null))
        }
        saveToDisk()
    }

    // === Clienti CRUD ===

    override suspend fun caricaClienti(): List<Cliente> = mutex.withLock {
        return getClientiCache().toList()
    }

    override suspend fun salvaCliente(cliente: Cliente) = mutex.withLock {
        val list = getClientiCache()
        val index = list.indexOfFirst { it.id == cliente.id }
        if (index >= 0) {
            list[index] = cliente
        } else {
            list.add(cliente)
        }
        saveToDisk()
    }

    override suspend fun updateCliente(id: String, newName: String) = mutex.withLock {
        val list = getClientiCache()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val cliente = list[index]
            list[index] = cliente.copy(nome = newName)
            saveToDisk()
        }
    }

    override suspend fun eliminaCliente(id: String) = mutex.withLock {
        val listClienti = getClientiCache()
        listClienti.removeAll { it.id == id }
        
        val listCantieri = getCantieriCache()
        val cantieriDaEliminare = listCantieri.filter { it.clienteId == id }.map { it.id }.toSet()
        listCantieri.removeAll { it.clienteId == id }
        
        val listImpianti = getImpiantiCache()
        // Gli impianti template hanno cantiereId = null, quindi non verranno mai eliminati da questa operazione
        listImpianti.removeAll { it.cantiereId != null && it.cantiereId in cantieriDaEliminare }
        
        saveToDisk()
    }

    // === Getters for new workflow ===

    override suspend fun getCantieriForCliente(clienteId: String): List<Cantiere> = mutex.withLock {
        return getCantieriCache().filter { it.clienteId == clienteId }
    }

    override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> = mutex.withLock {
        return getImpiantiCache().filter { it.cantiereId == cantiereId }
    }

    // === Cantieri CRUD ===

    override suspend fun salvaCantiere(cantiere: Cantiere) = mutex.withLock {
        val list = getCantieriCache()
        val index = list.indexOfFirst { it.id == cantiere.id }
        if (index >= 0) {
            list[index] = cantiere
        } else {
            list.add(cantiere)
        }
        saveToDisk()
    }

    override suspend fun updateCantiere(id: String, newName: String) = mutex.withLock {
        val list = getCantieriCache()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val cantiere = list[index]
            list[index] = cantiere.copy(nome = newName)
            saveToDisk()
        }
    }

    override suspend fun eliminaCantiere(id: String) = mutex.withLock {
        val list = getCantieriCache()
        list.removeAll { it.id == id }
        saveToDisk()
    }

    // === Componenti CRUD ===
    
    override suspend fun caricaComponentiStandard(): List<manutenzioni.domain.model.ComponenteStandard> = mutex.withLock {
        return getComponentiCache().toList()
    }

    override suspend fun salvaComponenteStandard(componente: manutenzioni.domain.model.ComponenteStandard) = mutex.withLock {
        val list = getComponentiCache()
        val index = list.indexOfFirst { it.id == componente.id }
        if (index >= 0) {
            list[index] = componente
        } else {
            list.add(componente)
        }
        saveToDisk()
    }

    // === Catalogo Approvato (ETIM) ===
    override suspend fun caricaCatalogoApprovato(): List<manutenzioni.domain.model.ComponenteApprovato> = mutex.withLock {
        return getCatalogoApprovatoCache().toList()
    }

    override suspend fun salvaOAggiornaComponenteApprovato(approvato: manutenzioni.domain.model.ComponenteApprovato) = mutex.withLock {
        val list = getCatalogoApprovatoCache()
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
        saveToDisk()
    }

    override suspend fun trovaEquivalentiApprovati(etimClassId: String): List<manutenzioni.domain.model.ComponenteApprovato> = mutex.withLock {
        return getCatalogoApprovatoCache().filter { it.etimClassId == etimClassId }
    }
}
