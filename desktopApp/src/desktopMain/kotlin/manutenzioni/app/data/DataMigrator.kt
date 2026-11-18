package manutenzioni.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import manutenzioni.domain.ManutenzioneRepository
import java.time.Instant

/**
 * Risultato della migrazione di una singola collezione.
 */
data class CollectionMigrationResult(
    val collectionName: String,
    val itemsRead: Int,
    val itemsWritten: Int,
    val success: Boolean,
    val errors: List<String> = emptyList()
)

/**
 * Report dettagliato di un'operazione di migrazione completa.
 */
data class MigrationReport(
    val success: Boolean,
    val direction: String,
    val startedAt: String,
    val completedAt: String,
    val durationMs: Long,
    val totalItemsMigrated: Int,
    val collections: Map<String, CollectionMigrationResult>,
    val globalErrors: List<String> = emptyList()
) {
    fun printSummary() {
        println("=================================================================")
        println("📊 REPORT MIGRAZIONE DATI [$direction]")
        println("Esito: ${if (success) "✅ SUCCESSO" else "❌ FALLIMENTO / ERRORI PARZIALI"}")
        println("Inizio: $startedAt | Fine: $completedAt | Durata: ${durationMs}ms")
        println("Elementi totali migrati: $totalItemsMigrated")
        println("-----------------------------------------------------------------")
        collections.forEach { (name, res) ->
            val status = if (res.success) "OK" else "ERR (${res.errors.size})"
            println(" • %-22s: Letti: %4d | Scritti: %4d | Stato: %s".format(name, res.itemsRead, res.itemsWritten, status))
            res.errors.forEach { err -> println("     ⚠️ $err") }
        }
        if (globalErrors.isNotEmpty()) {
            println("-----------------------------------------------------------------")
            println("Errori globali:")
            globalErrors.forEach { println(" ❌ $it") }
        }
        println("=================================================================")
    }
}

/**
 * Motore di migrazione bidirezionale resiliente tra repository di manutenzioni
 * (tipicamente JSON locale e MongoDB remoto/locale).
 *
 * Migra tutte e 8 le collezioni di sistema:
 * 1. Clienti
 * 2. Cantieri
 * 3. Impianti (inclusi template globali e istanze di cantiere polimorfiche)
 * 4. Componenti Standard
 * 5. Catalogo Approvato (ETIM & Equivalenze)
 * 6. Sessioni di Verifica Storicizzate
 * 7. Utenti & Profili RBAC
 * 8. Eventi di Manutenzione & Scheduler Ricorrenze
 */
object DataMigrator {

    enum class Direction {
        JSON_TO_MONGO,
        MONGO_TO_JSON
    }

    /**
     * Esegue la migrazione di tutte le 8 collezioni da [source] a [target].
     */
    suspend fun migrate(
        source: ManutenzioneRepository,
        target: ManutenzioneRepository,
        directionLabel: String = "CUSTOM"
    ): MigrationReport = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val startedAtIso = Instant.ofEpochMilli(startTime).toString()
        val results = mutableMapOf<String, CollectionMigrationResult>()
        val globalErrors = mutableListOf<String>()

        // 1. Migrazione Clienti
        results["clienti"] = migrateCollection("Clienti") {
            val clienti = source.caricaClienti()
            for (cliente in clienti) {
                target.salvaCliente(cliente)
            }
            clienti.size to clienti.size
        }

        // 2. Migrazione Cantieri
        results["cantieri"] = migrateCollection("Cantieri") {
            val cantieri = try {
                val direct = source.caricaCantieri()
                if (direct.isNotEmpty()) direct else {
                    source.caricaClienti().flatMap { source.getCantieriForCliente(it.id) }.distinctBy { it.id }
                }
            } catch (e: Exception) {
                source.caricaClienti().flatMap { source.getCantieriForCliente(it.id) }.distinctBy { it.id }
            }
            for (cantiere in cantieri) {
                target.salvaCantiere(cantiere)
            }
            cantieri.size to cantieri.size
        }

        // 3. Migrazione Impianti
        results["impianti"] = migrateCollection("Impianti") {
            val impianti = source.caricaImpianti()
            for (impianto in impianti) {
                target.salvaImpianto(impianto)
            }
            impianti.size to impianti.size
        }

        // 4. Migrazione Componenti Standard
        results["componenti"] = migrateCollection("Componenti Standard") {
            val componenti = source.caricaComponentiStandard()
            for (comp in componenti) {
                target.salvaComponenteStandard(comp)
            }
            componenti.size to componenti.size
        }

        // 5. Migrazione Catalogo Approvato (ETIM)
        results["catalogoApprovato"] = migrateCollection("Catalogo Approvato ETIM") {
            val catalogo = source.caricaCatalogoApprovato()
            for (appr in catalogo) {
                target.salvaOAggiornaComponenteApprovato(appr)
            }
            catalogo.size to catalogo.size
        }

        // 6. Migrazione Sessioni di Verifica
        results["sessioniVerifica"] = migrateCollection("Sessioni di Verifica") {
            val sessioni = try {
                val direct = source.caricaSessioniVerifica()
                if (direct.isNotEmpty()) direct else {
                    val cantieri = source.caricaCantieri().ifEmpty {
                        source.caricaClienti().flatMap { source.getCantieriForCliente(it.id) }
                    }
                    cantieri.flatMap { source.getSessioniPerCantiere(it.id) }.distinctBy { it.id }
                }
            } catch (e: Exception) {
                emptyList()
            }
            for (sessione in sessioni) {
                target.salvaSessioneVerifica(sessione)
            }
            sessioni.size to sessioni.size
        }

        // 7. Migrazione Utenti & RBAC
        results["utenti"] = migrateCollection("Utenti") {
            val utenti = source.caricaUtenti()
            for (utente in utenti) {
                target.salvaUtente(utente)
            }
            utenti.size to utenti.size
        }

        // 8. Migrazione Eventi di Manutenzione
        results["eventiManutenzione"] = migrateCollection("Eventi di Manutenzione") {
            val eventi = source.caricaEventiManutenzione()
            target.salvaEventiManutenzioneBatch(eventi)
            eventi.size to eventi.size
        }

        val endTime = System.currentTimeMillis()
        val totalItems = results.values.sumOf { it.itemsWritten }
        val allSuccessful = results.values.all { it.success } && globalErrors.isEmpty()

        MigrationReport(
            success = allSuccessful,
            direction = directionLabel,
            startedAt = startedAtIso,
            completedAt = Instant.ofEpochMilli(endTime).toString(),
            durationMs = endTime - startTime,
            totalItemsMigrated = totalItems,
            collections = results,
            globalErrors = globalErrors
        )
    }

    private inline fun migrateCollection(
        name: String,
        action: () -> Pair<Int, Int>
    ): CollectionMigrationResult {
        return try {
            val (read, written) = action()
            CollectionMigrationResult(
                collectionName = name,
                itemsRead = read,
                itemsWritten = written,
                success = true
            )
        } catch (e: Exception) {
            CollectionMigrationResult(
                collectionName = name,
                itemsRead = 0,
                itemsWritten = 0,
                success = false,
                errors = listOf("${e::class.simpleName}: ${e.message}")
            )
        }
    }

    suspend fun migrateJsonToMongo(
        jsonRepo: JsonManutenzioneRepository = JsonManutenzioneRepository(),
        mongoRepo: MongoManutenzioneRepository
    ): MigrationReport {
        return migrate(source = jsonRepo, target = mongoRepo, directionLabel = "JSON -> MongoDB")
    }

    suspend fun migrateMongoToJson(
        mongoRepo: MongoManutenzioneRepository,
        jsonRepo: JsonManutenzioneRepository = JsonManutenzioneRepository()
    ): MigrationReport {
        return migrate(source = mongoRepo, target = jsonRepo, directionLabel = "MongoDB -> JSON")
    }
}

/**
 * Entry point CLI per l'esecuzione standalone dello script di migrazione.
 *
 * Sintassi:
 * ```bash
 * # Da JSON locale verso MongoDB locale/remoto (default)
 * ./gradlew :desktopApp:run --args="--migrate --direction=json-to-mongo --mongo-uri=mongodb://localhost:27017"
 *
 * # Da MongoDB verso JSON locale di backup
 * ./gradlew :desktopApp:run --args="--migrate --direction=mongo-to-json --mongo-uri=mongodb://localhost:27017"
 * ```
 */
fun main(args: Array<String>) {
    runBlocking {
        println("🚀 Inizializzazione DataMigrator CLI...")

        var direction = DataMigrator.Direction.JSON_TO_MONGO
        var mongoUri = "mongodb://localhost:27017"
        var dbName = "manutenzioni_db"
        var jsonFile = "manutenzioni_db.json"

        for (arg in args) {
            when {
                arg.startsWith("--direction=") -> {
                    val dirVal = arg.substringAfter("--direction=").lowercase()
                    if (dirVal == "mongo-to-json") {
                        direction = DataMigrator.Direction.MONGO_TO_JSON
                    }
                }
                arg.startsWith("--mongo-uri=") -> mongoUri = arg.substringAfter("--mongo-uri=")
                arg.startsWith("--db-name=") -> dbName = arg.substringAfter("--db-name=")
                arg.startsWith("--json-file=") -> jsonFile = arg.substringAfter("--json-file=")
            }
        }

        println("Configurazione:")
        println(" • Direzione: $direction")
        println(" • Mongo URI: $mongoUri")
        println(" • Mongo DB:  $dbName")
        println(" • JSON File: $jsonFile")

        try {
            val jsonRepo = JsonManutenzioneRepository(jsonFile)
            val mongoRepo = MongoManutenzioneRepository(mongoUri, dbName)

            val report = when (direction) {
                DataMigrator.Direction.JSON_TO_MONGO -> DataMigrator.migrateJsonToMongo(jsonRepo, mongoRepo)
                DataMigrator.Direction.MONGO_TO_JSON -> DataMigrator.migrateMongoToJson(mongoRepo, jsonRepo)
            }

            report.printSummary()

            if (report.success) {
                println("🎉 Migrazione completata con successo!")
                kotlin.system.exitProcess(0)
            } else {
                System.err.println("❌ La migrazione si è conclusa con errori.")
                kotlin.system.exitProcess(1)
            }
        } catch (e: Exception) {
            System.err.println("💥 Errore fatale durante la migrazione: ${e.message}")
            e.printStackTrace()
            kotlin.system.exitProcess(2)
        }
    }
}
