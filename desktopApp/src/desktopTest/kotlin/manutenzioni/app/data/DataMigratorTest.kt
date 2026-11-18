package manutenzioni.app.data

import kotlinx.coroutines.runBlocking
import manutenzioni.domain.model.*
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DataMigratorTest {

    @Test
    fun testHermeticFull8CollectionMigration(): Unit = runBlocking {
        val tempDir = Files.createTempDirectory("migrator_test").toFile()
        val sourceDbFile = File(tempDir, "source_db.json")
        val targetDbFile = File(tempDir, "target_db.json")
        sourceDbFile.writeText("""{"impianti":[]}""")
        targetDbFile.writeText("""{"impianti":[]}""")

        val sourceRepo = JsonManutenzioneRepository(sourceDbFile)
        val targetRepo = JsonManutenzioneRepository(targetDbFile)

        // 1. Seed Clienti (2)
        val cliente1 = Cliente(id = "cli-1", nome = "Azienda Alfa S.r.l.")
        val cliente2 = Cliente(id = "cli-2", nome = "Beta Impianti S.p.A.")
        sourceRepo.salvaCliente(cliente1)
        sourceRepo.salvaCliente(cliente2)

        // 2. Seed Cantieri (3)
        val cantiere1 = Cantiere(id = "cant-1", clienteId = "cli-1", nome = "Sede Principale")
        val cantiere2 = Cantiere(id = "cant-2", clienteId = "cli-1", nome = "Magazzino Logistico")
        val cantiere3 = Cantiere(id = "cant-3", clienteId = "cli-2", nome = "Stabilimento Produttivo")
        sourceRepo.salvaCantiere(cantiere1)
        sourceRepo.salvaCantiere(cantiere2)
        sourceRepo.salvaCantiere(cantiere3)

        // 3. Seed Impianti (4: QuadroBT, ImpiantoEmergenza, RilevazioneAntincendio, ImpiantoStandard)
        val imp1 = QuadroBT(
            id = "imp-1",
            codIntervento = "Q",
            nomeCompleto = "Quadro Generale",
            cantiereId = "cant-1",
            sigla = "QG",
            listaInterruttori = listOf(InterruttoreBT(nome = "Differenziale Luce", quantita = 1))
        )
        val imp2 = ImpiantoEmergenza(
            id = "imp-2",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza",
            cantiereId = "cant-1",
            listaLampade = listOf(LampadaEmergenza(modello = "Beghelli 3h"))
        )
        val imp3 = RilevazioneAntincendio(
            id = "imp-3",
            codIntervento = "RI",
            nomeCompleto = "Centrale Incendi",
            cantiereId = "cant-2",
            listaDispositivi = listOf(
                DispositivoRilevazione.RivelatoreLoop(
                    loop = 1,
                    numeroIndirizzo = 1,
                    tipo = TipoRivelatore.OTTICO,
                    descrizione = "Ufficio"
                )
            )
        )
        val imp4 = ImpiantoStandard(
            id = "imp-4",
            codIntervento = "GE",
            nomeCompleto = "Gruppo Elettrogeno",
            cantiereId = "cant-3"
        )
        sourceRepo.salvaImpianto(imp1)
        sourceRepo.salvaImpianto(imp2)
        sourceRepo.salvaImpianto(imp3)
        sourceRepo.salvaImpianto(imp4)

        // 4. Seed Componenti Standard (2)
        sourceRepo.salvaComponenteStandard(ComponenteStandard(id = "comp-std-1", tipo = TipoComponenteStandard.INTERRUTTORE_BT, nome = "Interruttore 16A"))
        sourceRepo.salvaComponenteStandard(ComponenteStandard(id = "comp-std-2", tipo = TipoComponenteStandard.LAMPADA_EMERGENZA, nome = "Lampada LED 1h"))

        // 5. Seed Catalogo Approvato (2)
        sourceRepo.salvaOAggiornaComponenteApprovato(ComponenteApprovato(id = "appr-1", etimClassId = "EC000042", etimClassName = "Interruttore MT", descrizioneStandard = "16A 1P+N"))
        sourceRepo.salvaOAggiornaComponenteApprovato(ComponenteApprovato(id = "appr-2", etimClassId = "EC000200", etimClassName = "Interruttore Differenziale", descrizioneStandard = "Pure 25A 30mA"))

        // 6. Seed Sessioni di Verifica (2)
        sourceRepo.salvaSessioneVerifica(SessioneVerifica(id = "sess-1", cantiereId = "cant-1", clienteId = "cli-1", meseAnno = "2026-11"))
        sourceRepo.salvaSessioneVerifica(SessioneVerifica(id = "sess-2", cantiereId = "cant-2", clienteId = "cli-1", meseAnno = "2026-11"))

        // 7. Seed Utenti (2)
        sourceRepo.salvaUtente(Utente(id = "usr-1", username = "mario.rossi", nomeCompleto = "Mario Rossi", ruolo = RuoloUtente.ADMIN))
        sourceRepo.salvaUtente(Utente(id = "usr-2", username = "luca.bianchi", nomeCompleto = "Luca Bianchi", ruolo = RuoloUtente.TECNICO))

        // 8. Seed EventoManutenzione (3)
        val ev1 = EventoManutenzione(id = "ev-1", cantiereId = "cant-1", clienteId = "cli-1", dataPianificata = "15/11/2026", stato = StatoEventoManutenzione.PIANIFICATO)
        val ev2 = EventoManutenzione(id = "ev-2", cantiereId = "cant-2", clienteId = "cli-1", dataPianificata = "20/11/2026", stato = StatoEventoManutenzione.PIANIFICATO)
        val ev3 = EventoManutenzione(id = "ev-3", cantiereId = "cant-3", clienteId = "cli-2", dataPianificata = "25/11/2026", stato = StatoEventoManutenzione.PIANIFICATO)
        sourceRepo.salvaEventiManutenzioneBatch(listOf(ev1, ev2, ev3))

        // Run Full Migration
        val expectedClienti = sourceRepo.caricaClienti().size
        val expectedCantieri = sourceRepo.caricaCantieri().size
        val expectedImpianti = sourceRepo.caricaImpianti().size
        val expectedComp = sourceRepo.caricaComponentiStandard().size
        val expectedCat = sourceRepo.caricaCatalogoApprovato().size
        val expectedSess = sourceRepo.caricaSessioniVerifica().size
        val expectedUtenti = sourceRepo.caricaUtenti().size
        val expectedEventi = sourceRepo.caricaEventiManutenzione().size
        val totalExpected = expectedClienti + expectedCantieri + expectedImpianti + expectedComp + expectedCat + expectedSess + expectedUtenti + expectedEventi

        val report = DataMigrator.migrate(source = sourceRepo, target = targetRepo, directionLabel = "TEST_SOURCE -> TEST_TARGET")

        assertTrue(report.success, "Il report della migrazione deve avere esito positivo")
        assertEquals(totalExpected, report.totalItemsMigrated, "Tutti gli elementi della sorgente devono essere stati migrati")
        assertEquals(8, report.collections.size, "Tutte le 8 collezioni devono essere presenti nel report")

        // Verify target repository contains all 8 collections with exact items
        assertEquals(expectedClienti, targetRepo.caricaClienti().size)
        assertEquals(expectedCantieri, targetRepo.caricaCantieri().size)
        assertEquals(expectedImpianti, targetRepo.caricaImpianti().size)
        assertEquals(expectedComp, targetRepo.caricaComponentiStandard().size)
        assertEquals(expectedCat, targetRepo.caricaCatalogoApprovato().size)
        assertEquals(expectedSess, targetRepo.caricaSessioniVerifica().size)
        assertEquals(expectedUtenti, targetRepo.caricaUtenti().size)
        assertEquals(expectedEventi, targetRepo.caricaEventiManutenzione().size)

        tempDir.deleteRecursively()
    }
}
