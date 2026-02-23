package manutenzioni.app.data

import kotlinx.serialization.json.Json
import manutenzioni.domain.ManutenzioneRepository
import java.io.File

/**
 * Implementazione concreta del repository basata su file JSON.
 * Al primo avvio, copia il database demo dalle resources nella working directory.
 * Tutte le operazioni CRUD persistono su file locale.
 */
class JsonManutenzioneRepository(
    private val dbFileName: String = "manutenzioni_db.json"
) : ManutenzioneRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dbFile: File = File(dbFileName)

    /** Cache in-memory del database */
    private var cache: MutableList<Impianto>? = null

    init {
        copyDefaultIfMissing()
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
                val emptyDb = ManutenzioniDatabase(emptyList())
                dbFile.writeText(json.encodeToString(ManutenzioniDatabase.serializer(), emptyDb))
                println("⚠ Database demo non trovato nelle resources. Creato database vuoto.")
            }
        }
    }

    private fun loadFromDisk(): MutableList<Impianto> {
        return try {
            val content = dbFile.readText()
            val db = json.decodeFromString(ManutenzioniDatabase.serializer(), content)
            db.impianti.toMutableList()
        } catch (e: Exception) {
            println("Errore nel caricamento del database: ${e.message}")
            mutableListOf()
        }
    }

    private fun saveToDisk(impianti: List<Impianto>) {
        val db = ManutenzioniDatabase(impianti)
        dbFile.writeText(json.encodeToString(ManutenzioniDatabase.serializer(), db))
    }

    private fun getCache(): MutableList<Impianto> {
        if (cache == null) {
            cache = loadFromDisk()
        }
        return cache!!
    }

    override suspend fun salvaImpianto(impianto: Impianto) {
        val list = getCache()
        val index = list.indexOfFirst { it.codIntervento == impianto.codIntervento }
        if (index >= 0) {
            list[index] = impianto
        } else {
            list.add(impianto)
        }
        saveToDisk(list)
    }

    override suspend fun caricaImpianti(): List<Impianto> {
        return getCache().toList()
    }

    override suspend fun eliminaImpianto(codIntervento: String) {
        val list = getCache()
        list.removeAll { it.codIntervento == codIntervento }
        saveToDisk(list)
    }

    override suspend fun getImpianto(codIntervento: String): Impianto? {
        return getCache().find { it.codIntervento == codIntervento }
    }
}

