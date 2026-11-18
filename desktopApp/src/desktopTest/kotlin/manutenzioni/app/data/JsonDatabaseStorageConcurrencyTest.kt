package manutenzioni.app.data

import kotlinx.coroutines.*
import manutenzioni.app.data.storage.JsonDatabaseStorage
import manutenzioni.domain.model.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class JsonDatabaseStorageConcurrencyTest {

    @Test
    fun test50ParallelCoroutinesConcurrentStress(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_test").toFile()
        val testDb = File(tempDir, "concurrent_test.json")
        testDb.writeText("""{"impianti":[]}""")
        val repo = JsonManutenzioneRepository(testDb)

        val clientCount = 20
        val plantCount = 15
        val readCount = 15
        val totalWorkers = clientCount + plantCount + readCount // Exactly 50 coroutines

        coroutineScope {
            val jobs = (1..totalWorkers).map { index ->
                launch(if (index % 2 == 0) Dispatchers.Default else Dispatchers.IO) {
                    when {
                        index <= clientCount -> {
                            repo.salvaCliente(Cliente(id = "cli-$index", nome = "Cliente Concorrente $index"))
                        }
                        index <= clientCount + plantCount -> {
                            val pIndex = index - clientCount
                            repo.salvaImpianto(ImpiantoStandard(
                                id = "imp-$pIndex",
                                codIntervento = "IMP-$pIndex",
                                nomeCompleto = "Impianto Concorrente $pIndex",
                                premessa = null,
                                listaAttivita = emptyList(),
                                listaNormative = emptyList()
                            ))
                        }
                        else -> {
                            // Concurrent reads interleaved with writes
                            repo.caricaClienti()
                            repo.caricaImpianti()
                            repo.getCantieriForCliente("dummy")
                        }
                    }
                }
            }
            jobs.joinAll()
        }

        // In-memory snapshot validation
        val clientsInMemory = repo.caricaClienti()
        val plantsInMemory = repo.caricaImpianti()
        assertEquals(clientCount, clientsInMemory.size, "Tutti i 20 clienti devono essere presenti")
        assertEquals(plantCount, plantsInMemory.size, "Tutti i 15 impianti devono essere presenti")

        // Disk persistence validation (fresh repository instance)
        val freshRepo = JsonManutenzioneRepository(testDb)
        val clientsOnDisk = freshRepo.caricaClienti()
        val plantsOnDisk = freshRepo.caricaImpianti()
        assertEquals(clientCount, clientsOnDisk.size, "La persistenza su disco deve contenere tutti i clienti")
        assertEquals(plantCount, plantsOnDisk.size, "La persistenza su disco deve contenere tutti gli impianti")

        // Ensure no dangling .tmp file
        val tmpFile = File(tempDir, "${testDb.name}.tmp")
        assertFalse(tmpFile.exists(), "Il file .tmp non deve rimanere orfano dopo la scrittura atomica")

        tempDir.deleteRecursively()
    }

    @Test
    fun testHighContentionSinglePlantSwitchUpdates(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_contention").toFile()
        val testDb = File(tempDir, "contention_test.json")
        val repo = JsonManutenzioneRepository(testDb)

        val initialQuadro = QuadroBT(
            id = "quadro-stress",
            codIntervento = "Q-TEST",
            nomeCompleto = "Quadro di Test Concorrenza",
            premessa = null,
            listaAttivita = emptyList(),
            listaNormative = emptyList(),
            sigla = "QG",
            descrizioneQuadro = "Generale",
            listaInterruttori = emptyList()
        )
        repo.salvaImpianto(initialQuadro)

        val concurrentUpdates = 50
        coroutineScope {
            (1..concurrentUpdates).map { i ->
                launch(Dispatchers.Default) {
                    val newSwitch = InterruttoreBT(
                        id = "sw-$i",
                        nome = "Interruttore $i",
                        quantita = 1,
                        siglaCircuito = "C-$i"
                    )
                    // Direct storage update guarantees atomic list transformation
                    repo.storage.update { db ->
                        val updatedList = db.impianti.map { imp ->
                            if (imp.id == "quadro-stress" && imp is QuadroBT) {
                                imp.copy(listaInterruttori = imp.listaInterruttori + newSwitch)
                            } else imp
                        }
                        db.copy(impianti = updatedList)
                    }
                }
            }.joinAll()
        }

        val finalQuadro = repo.getImpianto("Q-TEST") as QuadroBT
        assertEquals(50, finalQuadro.listaInterruttori.size, "Tutti i 50 interruttori devono essere registrati senza race conditions")

        tempDir.deleteRecursively()
    }

    @Test
    fun testAtomicCascadingDeletionUnderConcurrentReads(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_cascade").toFile()
        val testDb = File(tempDir, "cascade_test.json")
        val repo = JsonManutenzioneRepository(testDb)

        // Seed 1 client, 2 cantieri, each cantiere with 1 impianto
        val targetClientId = "cli-target"
        repo.salvaCliente(Cliente(id = targetClientId, nome = "Cliente Da Eliminare"))
        repo.salvaCantiere(Cantiere(id = "cant-1", clienteId = targetClientId, nome = "Cantiere 1"))
        repo.salvaCantiere(Cantiere(id = "cant-2", clienteId = targetClientId, nome = "Cantiere 2"))
        repo.salvaImpianto(ImpiantoStandard(id = "imp-c1", codIntervento = "Q1", nomeCompleto = "Q1", cantiereId = "cant-1"))
        repo.salvaImpianto(ImpiantoStandard(id = "imp-c2", codIntervento = "Q2", nomeCompleto = "Q2", cantiereId = "cant-2"))

        // Template plant (must NOT be deleted)
        repo.salvaImpianto(ImpiantoStandard(id = "imp-tpl", codIntervento = "Q-TPL", nomeCompleto = "Template Master", cantiereId = null))

        val readErrors = java.util.concurrent.atomic.AtomicInteger(0)
        coroutineScope {
            // Readers querying cantieri and impianti
            val readerJobs = (1..20).map {
                launch(Dispatchers.Default) {
                    repeat(10) {
                        val cantieri = repo.getCantieriForCliente(targetClientId)
                        val impianti = repo.getImpiantiForCantiere("cant-1")
                        // If cantieri is empty, impianti for that cantiere must ALSO be empty (atomic transition)
                        if (cantieri.isEmpty() && impianti.isNotEmpty()) {
                            readErrors.incrementAndGet()
                        }
                    }
                }
            }

            // Writer executing cascading delete
            val writerJob = launch(Dispatchers.IO) {
                repo.eliminaCliente(targetClientId)
            }

            (readerJobs + writerJob).joinAll()
        }

        assertEquals(0, readErrors.get(), "Nessun lettore deve osservare stati parzialmente cancellati")
        assertTrue(repo.getCantieriForCliente(targetClientId).isEmpty())
        assertTrue(repo.getImpiantiForCantiere("cant-1").isEmpty())
        assertTrue(repo.getImpiantiForCantiere("cant-2").isEmpty())
        assertNotNull(repo.getImpianto("Q-TPL"), "L'impianto template globale deve essere preservato")

        tempDir.deleteRecursively()
    }

    @Test
    fun testStorageAutoRecoveryFromEmptyAndCorruptedFiles(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_recovery").toFile()
        val testDb = File(tempDir, "recovery_test.json")

        // Case A: 0-byte empty file (simulating crash mid-write)
        testDb.writeText("")
        assertEquals(0L, testDb.length())

        val repoA = JsonManutenzioneRepository(testDb)
        val impiantiA = repoA.caricaImpianti()
        assertNotNull(impiantiA, "Il repository deve avviarsi senza eccezioni anche se il file è a 0 byte")
        repoA.salvaCliente(Cliente(id = "cli-rec-1", nome = "Cliente Post Ripristino"))
        assertTrue(testDb.length() > 0L, "Il file su disco deve essere riscritto e non più a 0 byte")

        // Case B: Corrupted / Malformed JSON (syntax error)
        testDb.writeText("{ \"impianti\": [ { \"id\": \"broken\" ... INVALID_SYNTAX")

        val repoB = JsonManutenzioneRepository(testDb)
        val utentiB = repoB.caricaUtenti()
        assertTrue(utentiB.isNotEmpty(), "Il database deve recuperare la configurazione predefinita con utente admin")
        assertEquals("admin", utentiB.first().username)

        // Verify forensic backup was created
        val backupFiles = tempDir.listFiles { _, name -> name.contains(".corrupt.") }
        assertNotNull(backupFiles)
        assertTrue(backupFiles.isNotEmpty(), "Deve essere stato generato un file di backup forense .corrupt.<timestamp>")

        tempDir.deleteRecursively()
    }

    @Test
    fun testRapidConcurrentWriteCollisionsAcrossMultipleEntities(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_rapid_writes").toFile()
        val testDb = File(tempDir, "rapid_writes.json")
        testDb.writeText("""{"impianti":[]}""")
        val repo = JsonManutenzioneRepository(testDb)

        val clientCount = 25
        val cantiereCount = 25
        val plantCount = 25
        val userCount = 25
        val totalWriters = clientCount + cantiereCount + plantCount + userCount // 100 writers
        val readerCount = 50

        coroutineScope {
            // Launch 100 writers concurrently across Default and IO dispatchers
            val writeJobs = (1..totalWriters).map { i ->
                launch(if (i % 2 == 0) Dispatchers.Default else Dispatchers.IO) {
                    when {
                        i <= clientCount -> {
                            repo.salvaCliente(Cliente(id = "cli-rapid-$i", nome = "Cliente Rapid $i"))
                        }
                        i <= clientCount + cantiereCount -> {
                            val cIndex = i - clientCount
                            repo.salvaCantiere(Cantiere(
                                id = "cant-rapid-$cIndex",
                                clienteId = "cli-rapid-1",
                                nome = "Cantiere Rapid $cIndex"
                            ))
                        }
                        i <= clientCount + cantiereCount + plantCount -> {
                            val pIndex = i - (clientCount + cantiereCount)
                            repo.salvaImpianto(ImpiantoStandard(
                                id = "imp-rapid-$pIndex",
                                codIntervento = "IMP-RAPID-$pIndex",
                                nomeCompleto = "Impianto Rapid $pIndex"
                            ))
                        }
                        else -> {
                            val uIndex = i - (clientCount + cantiereCount + plantCount)
                            repo.salvaUtente(Utente(
                                id = "usr-rapid-$uIndex",
                                username = "user$uIndex",
                                nomeCompleto = "Utente $uIndex",
                                ruolo = RuoloUtente.TECNICO
                            ))
                        }
                    }
                }
            }

            // Concurrently launch 50 readers reading various collections
            val readJobs = (1..readerCount).map { r ->
                launch(Dispatchers.Default) {
                    repeat(5) {
                        repo.caricaClienti()
                        repo.caricaCantieri()
                        repo.caricaImpianti()
                        repo.caricaUtenti()
                    }
                }
            }

            (writeJobs + readJobs).joinAll()
        }

        // Verify in-memory state
        assertEquals(clientCount, repo.caricaClienti().size, "All clients must be preserved in memory")
        assertEquals(cantiereCount, repo.caricaCantieri().size, "All cantieri must be preserved in memory")
        assertEquals(plantCount, repo.caricaImpianti().size, "All plants must be preserved in memory")
        // Utenti includes admin-default (1) + userCount (25) = 26
        assertEquals(userCount + 1, repo.caricaUtenti().size, "All users + default admin must be preserved")

        // Verify disk persistence via a completely fresh repository instance
        val freshRepo = JsonManutenzioneRepository(testDb)
        assertEquals(clientCount, freshRepo.caricaClienti().size, "All clients must be persisted on disk")
        assertEquals(cantiereCount, freshRepo.caricaCantieri().size, "All cantieri must be persisted on disk")
        assertEquals(plantCount, freshRepo.caricaImpianti().size, "All plants must be persisted on disk")
        assertEquals(userCount + 1, freshRepo.caricaUtenti().size, "All users must be persisted on disk")

        tempDir.deleteRecursively()
    }

    @Test
    fun testDiskReadWhileAtomicSwapInProgress(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_atomic_read").toFile()
        val testDb = File(tempDir, "atomic_swap_test.json")
        testDb.writeText("""{"impianti":[]}""")
        val repo = JsonManutenzioneRepository(testDb)

        val writeIterations = 80
        val isWriting = java.util.concurrent.atomic.AtomicBoolean(true)
        val successfulRawDiskReads = java.util.concurrent.atomic.AtomicInteger(0)
        val readErrors = java.util.concurrent.atomic.AtomicInteger(0)
        val jsonParser = JsonDatabaseStorage.defaultJsonConfig()

        coroutineScope {
            // Writer coroutine: performs rapid updates forcing flush + fsync + ATOMIC_MOVE
            val writerJob = launch(Dispatchers.IO) {
                try {
                    for (i in 1..writeIterations) {
                        repo.salvaCliente(Cliente(id = "cli-atomic-$i", nome = "Cliente Atomic $i"))
                        repo.salvaImpianto(ImpiantoStandard(
                            id = "imp-atomic-$i",
                            codIntervento = "Q-ATOMIC-$i",
                            nomeCompleto = "Impianto Atomic $i"
                        ))
                    }
                } finally {
                    isWriting.set(false)
                }
            }

            // Reader coroutines: read raw bytes from testDb on disk continuously during writer activity
            val readerJobs = (1..10).map {
                launch(Dispatchers.IO) {
                    while (isWriting.get()) {
                        try {
                            if (testDb.exists()) {
                                val rawContent = testDb.readText(Charsets.UTF_8)
                                if (rawContent.isBlank()) {
                                    readErrors.incrementAndGet()
                                } else {
                                    // Parse raw JSON to verify it is NEVER partially written or corrupted
                                    val parsed = jsonParser.decodeFromString(ManutenzioniDatabase.serializer(), rawContent)
                                    if (parsed.clienti.isEmpty() && parsed.impianti.isEmpty() && rawContent.length < 10) {
                                        readErrors.incrementAndGet()
                                    }
                                    successfulRawDiskReads.incrementAndGet()
                                }
                            }
                        } catch (e: Exception) {
                            // If an atomic move failed, partial file read would throw SerializationException or EOFException
                            readErrors.incrementAndGet()
                        }
                    }
                }
            }

            writerJob.join()
            readerJobs.joinAll()
        }

        assertEquals(0, readErrors.get(), "No raw disk reader should ever encounter partial/corrupted content during atomic swap")
        assertTrue(successfulRawDiskReads.get() > 10, "Multiple raw disk reads must have succeeded concurrently: ${successfulRawDiskReads.get()}")

        // Final verification of DB state
        val finalRepo = JsonManutenzioneRepository(testDb)
        assertEquals(writeIterations, finalRepo.caricaClienti().size)
        assertEquals(writeIterations, finalRepo.caricaImpianti().size)

        tempDir.deleteRecursively()
    }

    @Test
    fun testCorruptedAndBlankFileRecoveryVariants(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_recovery_variants").toFile()

        // Subcase 1: Blank whitespace-only file
        val blankDb = File(tempDir, "blank_whitespace.json")
        blankDb.writeText("   \n\t   \r\n   ")
        val repoBlank = JsonManutenzioneRepository(blankDb)
        val loadedBlank = repoBlank.caricaImpianti()
        assertNotNull(loadedBlank, "Blank whitespace file must recover without exception")
        repoBlank.salvaCliente(Cliente(id = "cli-blank", nome = "Recovered from Blank"))
        assertTrue(blankDb.length() > 0L)

        // Subcase 2: Truncated JSON mid-stream
        val truncatedDb = File(tempDir, "truncated.json")
        truncatedDb.writeText("""{"clienti": [{"id": "c1", "nome": "Troncato""")
        val repoTruncated = JsonManutenzioneRepository(truncatedDb)
        val usersTruncated = repoTruncated.caricaUtenti()
        assertTrue(usersTruncated.isNotEmpty(), "Truncated JSON must recover default admin user")
        assertEquals("admin", usersTruncated.first().username)

        // Subcase 3: Invalid JSON token syntax
        val invalidDb = File(tempDir, "invalid_tokens.json")
        invalidDb.writeText("""{ "invalid_token": [1, 2, 3, ] ] }""")
        val repoInvalid = JsonManutenzioneRepository(invalidDb)
        val plantsInvalid = repoInvalid.caricaImpianti()
        assertNotNull(plantsInvalid, "Invalid tokens must recover cleanly")

        // Subcase 4: Verify forensic backup files exist for truncated and invalid DBs
        val backupFiles = tempDir.listFiles { _, name -> name.contains(".corrupt.") }
        assertNotNull(backupFiles)
        assertTrue(backupFiles.size >= 2, "Forensic backup files must be generated for corrupted files")

        // Subcase 5: Subsequent writes must succeed on recovered storage
        repoInvalid.salvaCliente(Cliente(id = "cli-inv-1", nome = "Post Recovery Client"))
        val reloadedInvalid = JsonManutenzioneRepository(invalidDb)
        assertEquals(1, reloadedInvalid.caricaClienti().size)
        assertEquals("cli-inv-1", reloadedInvalid.caricaClienti().first().id)

        tempDir.deleteRecursively()
    }

    @Test
    fun testAdversarialCascadingDeleteWithConcurrentWritersAndReaders(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("storage_cascade_adv").toFile()
        val testDb = File(tempDir, "cascade_adv.json")
        testDb.writeText("""{"impianti":[]}""")
        val repo = JsonManutenzioneRepository(testDb)

        // Setup: 5 clients, each with 3 cantieri (15 cantieri), each cantiere with 3 plants (45 plants)
        // Plus 5 global templates (cantiereId == null)
        val clientIds = (1..5).map { "cli-$it" }
        for (cid in clientIds) {
            repo.salvaCliente(Cliente(id = cid, nome = "Cliente $cid"))
            for (cIndex in 1..3) {
                val cantId = "$cid-cant-$cIndex"
                repo.salvaCantiere(Cantiere(id = cantId, clienteId = cid, nome = "Cantiere $cantId"))
                for (pIndex in 1..3) {
                    val impId = "$cantId-imp-$pIndex"
                    repo.salvaImpianto(ImpiantoStandard(
                        id = impId,
                        codIntervento = "COD-$impId",
                        nomeCompleto = "Impianto $impId",
                        cantiereId = cantId
                    ))
                }
            }
        }

        // Global templates
        val templateIds = (1..5).map { "tpl-$it" }
        for (tid in templateIds) {
            repo.salvaImpianto(ImpiantoStandard(
                id = tid,
                codIntervento = "COD-TPL-$tid",
                nomeCompleto = "Template $tid",
                cantiereId = null
            ))
        }

        val isolationErrors = java.util.concurrent.atomic.AtomicInteger(0)
        val deleteDone = java.util.concurrent.atomic.AtomicBoolean(false)

        coroutineScope {
            // 20 concurrent readers continuously verifying cascading integrity:
            // 1. If client is missing from caricaClienti(), getCantieriForCliente(id) must be empty
            // 2. For every cantiere returned by caricaCantieri(), its parent client must exist in caricaClienti()
            // 3. For every plant returned with non-null cantiereId, its cantiere must exist in caricaCantieri()
            val readerJobs = (1..20).map {
                launch(Dispatchers.Default) {
                    while (!deleteDone.get()) {
                        val clients = repo.caricaClienti().associateBy { it.id }
                        val cantieri = repo.caricaCantieri()
                        val impianti = repo.caricaImpianti()

                        // Invariant: no orphan cantieri
                        for (c in cantieri) {
                            if (!clients.containsKey(c.clienteId)) {
                                isolationErrors.incrementAndGet()
                            }
                        }

                        // Invariant: no orphan plants
                        val cantiereSet = cantieri.map { it.id }.toSet()
                        for (imp in impianti) {
                            if (imp.cantiereId != null && !cantiereSet.contains(imp.cantiereId)) {
                                isolationErrors.incrementAndGet()
                            }
                        }
                    }
                }
            }

            // Concurrent writers:
            // Writer 1 deletes client 1
            // Writer 2 deletes client 3
            // Writer 3 deletes a specific cantiere of client 2
            // Writer 4 adds a new plant to client 4's cantiere
            val writerJobs = listOf(
                launch(Dispatchers.IO) { repo.eliminaCliente("cli-1") },
                launch(Dispatchers.IO) { repo.eliminaCliente("cli-3") },
                launch(Dispatchers.IO) { repo.eliminaCantiere("cli-2-cant-2") },
                launch(Dispatchers.IO) {
                    repo.salvaImpianto(ImpiantoStandard(
                        id = "cli-4-cant-1-imp-new",
                        codIntervento = "COD-NEW",
                        nomeCompleto = "New Plant",
                        cantiereId = "cli-4-cant-1"
                    ))
                }
            )

            writerJobs.joinAll()
            deleteDone.set(true)
            readerJobs.joinAll()
        }

        assertEquals(0, isolationErrors.get(), "Readers must never observe orphan cantieri or orphan plants during cascading delete")

        // Final state validation
        val survivingClients = repo.caricaClienti().map { it.id }.toSet()
        assertFalse(survivingClients.contains("cli-1"), "cli-1 must be deleted")
        assertFalse(survivingClients.contains("cli-3"), "cli-3 must be deleted")
        assertTrue(survivingClients.contains("cli-2"), "cli-2 must survive")
        assertTrue(survivingClients.contains("cli-4"), "cli-4 must survive")
        assertTrue(survivingClients.contains("cli-5"), "cli-5 must survive")

        // All 5 global templates must survive
        val survivingPlants = repo.caricaImpianti()
        for (tid in templateIds) {
            val tpl = survivingPlants.find { it.id == tid }
            assertNotNull(tpl, "Template $tid must survive cascading deletes")
            assertNull(tpl.cantiereId, "Template $tid must retain cantiereId == null")
        }

        tempDir.deleteRecursively()
    }
}

