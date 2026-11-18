package manutenzioni.app.data.storage

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import manutenzioni.app.data.ManutenzioniDatabase
import manutenzioni.domain.model.RuoloUtente
import manutenzioni.domain.model.Utente
import manutenzioni.domain.model.normalizeDates
import manutenzioni.domain.model.normalizeType
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Motore di persistenza thread-safe basato su file JSON e snapshot immutabile in memoria.
 *
 * Proprietà fondamentali:
 * 1. Thread-safety assoluta tramite kotlinx.coroutines.sync.Mutex.
 * 2. Confinamento di ogni operazione I/O su Dispatchers.IO.
 * 3. Scrittura atomica su file temporaneo (.tmp) con flush + fsync + ATOMIC_MOVE.
 * 4. Ripristino automatico in caso di file vuoto (0 byte) o JSON corrotto.
 * 5. Normalizzazione unificata dei modelli (Impianto, Cantiere, Utenti).
 */
class JsonDatabaseStorage(
    val dbFile: File,
    private val json: Json = defaultJsonConfig(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    /** Costruttore secondario per compatibilità con nome file semplice */
    constructor(fileName: String = "manutenzioni_db.json") : this(resolveDbPath(fileName))

    private val mutex = Mutex()
    private var inMemoryDb: ManutenzioniDatabase? = null

    companion object {
        fun defaultJsonConfig(): Json = Json {
            prettyPrint = true
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "type"
        }

        /**
         * Risolve il percorso del database nella cartella standard dell'utente (~/.manutenzioni_maker).
         */
        fun resolveDbPath(fileName: String): File {
            val userHome = System.getProperty("user.home")
            val appDataDir = File(userHome, ".manutenzioni_maker")
            if (!appDataDir.exists()) {
                appDataDir.mkdirs()
            }
            return File(appDataDir, fileName)
        }
    }

    /**
     * Esegue una lettura thread-safe sullo snapshot immutabile in memoria.
     */
    suspend fun <T> read(block: (ManutenzioniDatabase) -> T): T = mutex.withLock {
        val db = getOrLoad()
        block(db)
    }

    /**
     * Ottiene una copia dello snapshot immutabile corrente.
     */
    suspend fun getSnapshot(): ManutenzioniDatabase = mutex.withLock {
        getOrLoad()
    }

    /**
     * Esegue una mutazione atomica tramite funzione di trasformazione pura.
     * Aggiorna lo snapshot in memoria e persiste immediatamente su disco via atomic move.
     */
    suspend fun update(transform: (ManutenzioniDatabase) -> ManutenzioniDatabase): ManutenzioniDatabase = mutex.withLock {
        val current = getOrLoad()
        val updated = normalize(transform(current))
        inMemoryDb = updated
        persistToDiskInternal(updated)
        updated
    }

    /**
     * Recupera lo snapshot corrente dalla memoria o lo carica da disco se nullo.
     */
    private suspend fun getOrLoad(): ManutenzioniDatabase {
        val mem = inMemoryDb
        if (mem != null) return mem

        val loaded = loadFromDisk()
        inMemoryDb = loaded
        return loaded
    }

    /**
     * Carica e deserializza il file JSON da disco in modo sicuro su Dispatchers.IO.
     * Gestisce file inesistenti, vuoti o corrotti con auto-ripristino.
     */
    private suspend fun loadFromDisk(): ManutenzioniDatabase = withContext(ioDispatcher) {
        if (!dbFile.exists() || dbFile.length() == 0L) {
            return@withContext recoverFromEmptyOrMissing()
        }

        try {
            val content = dbFile.readText(Charsets.UTF_8)
            if (content.isBlank()) {
                return@withContext recoverFromEmptyOrMissing()
            }
            val decoded = json.decodeFromString(ManutenzioniDatabase.serializer(), content)
            normalize(decoded)
        } catch (e: Exception) {
            println("⚠️ [JsonDatabaseStorage] Errore nel caricamento del database (${dbFile.name}): ${e.message}. Avvio ripristino automatico...")
            recoverFromCorruptedFile(e)
        }
    }

    /**
     * Persiste lo snapshot su disco tramite file temporaneo e sostituzione atomica.
     */
    private suspend fun persistToDiskInternal(db: ManutenzioniDatabase) = withContext(ioDispatcher) {
        val parentDir = dbFile.parentFile ?: File(".")
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        // Il file .tmp DEVE trovarsi nella stessa cartella per garantire lo stesso filesystem
        val tmpFile = File(parentDir, "${dbFile.name}.tmp")
        val jsonBytes = json.encodeToString(ManutenzioniDatabase.serializer(), db).toByteArray(Charsets.UTF_8)

        // Scrittura con flush e fsync hardware per garantire durabilità
        FileOutputStream(tmpFile).use { fos ->
            fos.write(jsonBytes)
            fos.flush()
            fos.fd.sync()
        }

        // Sostituzione atomica del file destinazione
        try {
            Files.move(
                tmpFile.toPath(),
                dbFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: AtomicMoveNotSupportedException) {
            // Fallback per filesystem che non supportano ATOMIC_MOVE (es. condivisioni di rete)
            Files.move(
                tmpFile.toPath(),
                dbFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }

    /**
     * Gestione del ripristino automatico in caso di file mancante o vuoto (0 byte).
     */
    private suspend fun recoverFromEmptyOrMissing(): ManutenzioniDatabase {
        val defaultDb = loadFromClasspathResource() ?: ManutenzioniDatabase(impianti = emptyList())
        val normalized = normalize(defaultDb)
        persistToDiskInternal(normalized)
        return normalized
    }

    /**
     * Ripristino da file corrotto:
     * 1. Se esiste già uno snapshot in memoria valido, lo ri-persiste.
     * 2. Archivia il file corrotto come backup (.corrupt.<timestamp>).
     * 3. Ricarica la risorsa demo o un database pulito.
     */
    private suspend fun recoverFromCorruptedFile(e: Exception): ManutenzioniDatabase {
        val mem = inMemoryDb
        if (mem != null) {
            println("ℹ️ [JsonDatabaseStorage] Snapshot in-memory valido disponibile: ripristino file su disco.")
            persistToDiskInternal(mem)
            return mem
        }

        try {
            val backupFile = File(dbFile.parentFile, "${dbFile.name}.corrupt.${System.currentTimeMillis()}")
            Files.copy(dbFile.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("📦 [JsonDatabaseStorage] File corrotto archiviato per sicurezza in: ${backupFile.absolutePath}")
        } catch (ex: Exception) {
            println("⚠️ [JsonDatabaseStorage] Impossibile creare backup del file corrotto: ${ex.message}")
        }

        val demoDb = loadFromClasspathResource() ?: ManutenzioniDatabase(impianti = emptyList())
        val normalized = normalize(demoDb)
        persistToDiskInternal(normalized)
        return normalized
    }

    /**
     * Tenta il caricamento del database demo dalle risorse del classpath.
     */
    private fun loadFromClasspathResource(): ManutenzioniDatabase? {
        return try {
            val stream = this::class.java.classLoader?.getResourceAsStream("manutenzioni_db.json")
                ?: Thread.currentThread().contextClassLoader?.getResourceAsStream("manutenzioni_db.json")
            stream?.bufferedReader()?.use { reader ->
                val content = reader.readText()
                if (content.isNotBlank()) {
                    json.decodeFromString(ManutenzioniDatabase.serializer(), content)
                } else null
            }
        } catch (e: Exception) {
            println("⚠️ [JsonDatabaseStorage] Risorsa demo classpath non caricabile: ${e.message}")
            null
        }
    }

    /**
     * Applica le normalizzazioni obbligatorie ai modelli:
     * - Impianti: `normalizeType()`
     * - Cantieri: `normalizeDates()`
     * - Utenti: seed utente admin di default se lista vuota
     */
    private fun normalize(db: ManutenzioniDatabase): ManutenzioniDatabase {
        val normalizedImpianti = db.impianti.map { it.normalizeType() }
        val normalizedCantieri = db.cantieri.map { it.normalizeDates() }
        val utentiWithAdmin = if (db.utenti.isEmpty()) {
            listOf(
                Utente(
                    id = "usr-admin-default",
                    username = "admin",
                    nomeCompleto = "Amministratore di Sistema",
                    ruolo = RuoloUtente.ADMIN
                )
            )
        } else {
            db.utenti
        }
        return db.copy(
            impianti = normalizedImpianti,
            cantieri = normalizedCantieri,
            utenti = utentiWithAdmin
        )
    }
}
