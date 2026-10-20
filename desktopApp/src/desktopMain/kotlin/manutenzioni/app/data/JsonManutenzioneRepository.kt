package manutenzioni.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import manutenzioni.domain.ManutenzioneRepository
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.normalizeType
import manutenzioni.domain.model.normalizeDates
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
        classDiscriminator = "type"
    }

    /** Cache in-memory del database */
    private var cache: MutableList<Impianto>? = null
    private var cacheClienti: MutableList<Cliente>? = null
    private var cacheCantieri: MutableList<Cantiere>? = null
    private var cacheComponenti: MutableList<manutenzioni.domain.model.ComponenteStandard>? = null
    private var cacheCatalogoApprovato: MutableList<manutenzioni.domain.model.ComponenteApprovato>? = null
    private var cacheSessioniVerifica: MutableList<manutenzioni.domain.model.SessioneVerifica>? = null
    private var cacheUtenti: MutableList<manutenzioni.domain.model.Utente>? = null
    private var cacheEventiManutenzione: MutableList<manutenzioni.domain.model.EventoManutenzione>? = null

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
        catalogoApprovato: List<manutenzioni.domain.model.ComponenteApprovato> = getCatalogoApprovatoCache(),
        sessioniVerifica: List<manutenzioni.domain.model.SessioneVerifica> = getSessioniVerificaCache(),
        utenti: List<manutenzioni.domain.model.Utente> = getUtentiCache(),
        eventiManutenzione: List<manutenzioni.domain.model.EventoManutenzione> = getEventiManutenzioneCache()
    ) {
        val db = ManutenzioniDatabase(impianti, clienti, cantieri, componenti, catalogoApprovato, sessioniVerifica, utenti, eventiManutenzione)
        dbFile.writeText(json.encodeToString(ManutenzioniDatabase.serializer(), db))
    }

    private fun getEventiManutenzioneCache(): MutableList<manutenzioni.domain.model.EventoManutenzione> {
        if (cacheEventiManutenzione == null) {
            val db = loadFromDisk()
            cacheEventiManutenzione = db.eventiManutenzione.toMutableList()
        }
        return cacheEventiManutenzione!!
    }

    private fun getSessioniVerificaCache(): MutableList<manutenzioni.domain.model.SessioneVerifica> {
        if (cacheSessioniVerifica == null) {
            val db = loadFromDisk()
            cacheSessioniVerifica = db.sessioniVerifica.toMutableList()
        }
        return cacheSessioniVerifica!!
    }

    private fun getUtentiCache(): MutableList<manutenzioni.domain.model.Utente> {
        if (cacheUtenti == null) {
            val db = loadFromDisk()
            val loaded = db.utenti.toMutableList()
            if (loaded.isEmpty()) {
                loaded.add(
                    manutenzioni.domain.model.Utente(
                        id = "usr-admin-default",
                        username = "admin",
                        nomeCompleto = "Amministratore di Sistema",
                        ruolo = manutenzioni.domain.model.RuoloUtente.ADMIN
                    )
                )
            }
            cacheUtenti = loaded
        }
        return cacheUtenti!!
    }

    private fun getImpiantiCache(): MutableList<Impianto> {
        if (cache == null) {
            val db = loadFromDisk()
            cache = db.impianti.map { it.normalizeType() }.toMutableList()
            if (cacheClienti == null) {
                cacheClienti = db.clienti.toMutableList()
            }
            if (cacheCantieri == null) {
                cacheCantieri = db.cantieri.map { it.normalizeDates() }.toMutableList()
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
                cache = db.impianti.map { it.normalizeType() }.toMutableList()
            }
            if (cacheCantieri == null) {
                cacheCantieri = db.cantieri.map { it.normalizeDates() }.toMutableList()
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
            cacheCantieri = db.cantieri.map { it.normalizeDates() }.toMutableList()
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
                val baseUpdated = list[i].copyWithBasicParams(
                    nomeCompleto = impiantoTemplate.nomeCompleto,
                    premessa = impiantoTemplate.premessa,
                    listaAttivita = impiantoTemplate.listaAttivita,
                    listaNormative = impiantoTemplate.listaNormative
                    // id, cantiereId, quantita e noteSpecifiche rimangono inalterati
                )
                list[i] = if (baseUpdated is manutenzioni.domain.model.QuadroMQT && baseUpdated.tipoInterruttore != null) {
                    baseUpdated.withTipoInterruttore(baseUpdated.tipoInterruttore)
                } else {
                    baseUpdated
                }
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
        return getCantieriCache().filter { it.clienteId == clienteId }.map { it.normalizeDates() }
    }

    override suspend fun getImpiantiForCantiere(cantiereId: String): List<Impianto> = mutex.withLock {
        return getImpiantiCache().filter { it.cantiereId == cantiereId }
    }

    // === Cantieri CRUD ===

    override suspend fun salvaCantiere(cantiere: Cantiere) = mutex.withLock {
        val normalized = cantiere.normalizeDates()
        val list = getCantieriCache()
        val index = list.indexOfFirst { it.id == normalized.id }
        if (index >= 0) {
            list[index] = normalized
        } else {
            list.add(normalized)
        }
        saveToDisk()
    }

    override suspend fun updateCantiere(id: String, newName: String) = mutex.withLock {
        val list = getCantieriCache()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val cantiere = list[index]
            list[index] = cantiere.copy(nome = newName).normalizeDates()
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

    // === Storicizzazione Sessioni di Verifica (Wave 4: R25, R30) ===
    override suspend fun salvaSessioneVerifica(sessione: manutenzioni.domain.model.SessioneVerifica) = mutex.withLock {
        val list = getSessioniVerificaCache()
        val index = list.indexOfFirst { it.id == sessione.id }
        if (index >= 0) {
            list[index] = sessione
        } else {
            list.add(0, sessione)
        }
        saveToDisk()
    }

    override suspend fun getSessioniPerCantiere(cantiereId: String): List<manutenzioni.domain.model.SessioneVerifica> = mutex.withLock {
        return getSessioniVerificaCache().filter { it.cantiereId == cantiereId }
    }

    override suspend fun eliminaSessioneVerifica(id: String) = mutex.withLock {
        val list = getSessioniVerificaCache()
        list.removeAll { it.id == id }
        saveToDisk()
    }

    // === Gestione Utenti & RBAC (Wave 4: R31) ===
    override suspend fun caricaUtenti(): List<manutenzioni.domain.model.Utente> = mutex.withLock {
        return getUtentiCache().toList()
    }

    override suspend fun salvaUtente(utente: manutenzioni.domain.model.Utente) = mutex.withLock {
        val list = getUtentiCache()
        val index = list.indexOfFirst { it.id == utente.id }
        if (index >= 0) {
            list[index] = utente
        } else {
            list.add(utente)
        }
        saveToDisk()
    }

    override suspend fun eliminaUtente(id: String) = mutex.withLock {
        val list = getUtentiCache()
        if (list.count { it.ruolo == manutenzioni.domain.model.RuoloUtente.ADMIN && it.id != id } > 0 ||
            list.firstOrNull { it.id == id }?.ruolo != manutenzioni.domain.model.RuoloUtente.ADMIN
        ) {
            list.removeAll { it.id == id }
            saveToDisk()
        }
    }

    // === Eventi di Manutenzione & Scheduler Ricorrenze ===

    override suspend fun salvaEventoManutenzione(evento: manutenzioni.domain.model.EventoManutenzione) = mutex.withLock {
        val list = getEventiManutenzioneCache()
        val index = list.indexOfFirst { it.id == evento.id }
        if (index >= 0) {
            list[index] = evento
        } else {
            list.add(evento)
        }
        saveToDisk()
    }

    override suspend fun salvaEventiManutenzioneBatch(eventi: List<manutenzioni.domain.model.EventoManutenzione>) = mutex.withLock {
        val list = getEventiManutenzioneCache()
        eventi.forEach { evento ->
            val index = list.indexOfFirst { it.id == evento.id }
            if (index >= 0) {
                list[index] = evento
            } else {
                list.add(evento)
            }
        }
        saveToDisk()
    }

    override suspend fun caricaEventiManutenzione(): List<manutenzioni.domain.model.EventoManutenzione> = mutex.withLock {
        return getEventiManutenzioneCache().toList()
    }

    override suspend fun caricaEventiPerMese(annoMese: String): List<manutenzioni.domain.model.EventoManutenzione> = mutex.withLock {
        // annoMese nel formato "yyyy-MM" (es. "2026-10") o "yyyy_MM"
        val normalized = annoMese.replace("_", "-")
        return getEventiManutenzioneCache().filter { ev ->
            val d = ev.dataPianificata.trim()
            val parts = d.split("/", "-", ".")
            if (parts.size == 3) {
                val y = if (parts[0].length == 4) parts[0] else parts[2]
                val m = (if (parts[0].length == 4) parts[1] else parts[1]).padStart(2, '0')
                "$y-$m" == normalized
            } else false
        }
    }

    override suspend fun caricaEventiPerCantiere(cantiereId: String): List<manutenzioni.domain.model.EventoManutenzione> = mutex.withLock {
        return getEventiManutenzioneCache().filter { it.cantiereId == cantiereId }
    }

    override suspend fun eliminaEventoManutenzione(id: String, eliminaSerieFutura: Boolean) = mutex.withLock {
        val list = getEventiManutenzioneCache()
        val target = list.find { it.id == id }
        if (target != null && eliminaSerieFutura && !target.serieRicorrenzaId.isNullOrBlank()) {
            val serieId = target.serieRicorrenzaId
            val baseDate = manutenzioni.domain.service.CalendarioScadenzeCalculator.parseDataRiferimento(target.dataPianificata)
            list.removeAll { ev ->
                ev.serieRicorrenzaId == serieId &&
                    manutenzioni.domain.service.CalendarioScadenzeCalculator.parseDataRiferimento(ev.dataPianificata) >= baseDate
            }
        } else {
            list.removeAll { it.id == id }
        }
        saveToDisk()
    }

    override suspend fun aggiornaStatoEvento(
        id: String,
        nuovoStato: manutenzioni.domain.model.StatoEventoManutenzione,
        dataCompletamento: String?
    ) = mutex.withLock {
        val list = getEventiManutenzioneCache()
        val index = list.indexOfFirst { it.id == id }
        if (index >= 0) {
            val corrente = list[index]
            list[index] = corrente.copy(
                stato = nuovoStato,
                dataCompletamentoEffettiva = if (nuovoStato == manutenzioni.domain.model.StatoEventoManutenzione.COMPLETATO) {
                    dataCompletamento ?: corrente.dataPianificata
                } else corrente.dataCompletamentoEffettiva
            )
            saveToDisk()
        }
    }
}
