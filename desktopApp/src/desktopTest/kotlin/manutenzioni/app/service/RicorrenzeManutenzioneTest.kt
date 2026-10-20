package manutenzioni.app.service

import kotlinx.coroutines.runBlocking
import manutenzioni.app.data.JsonManutenzioneRepository
import manutenzioni.domain.model.EventoManutenzione
import manutenzioni.domain.model.ImpiantoPianificatoSnapshot
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.StatoEventoManutenzione
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.service.RicorrenzeManutenzioneEngine
import java.io.File
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Suite di test per lo Scheduler delle ricorrenze di manutenzione (RicorrenzeManutenzioneEngine)
 * e la persistenza batch su repository locale.
 */
class RicorrenzeManutenzioneTest {

    private val testDbFileName = "test_calendar_scheduler_db.json"
    private lateinit var repo: JsonManutenzioneRepository
    private lateinit var testDbFile: File

    @BeforeTest
    fun setup() {
        val userHome = System.getProperty("user.home")
        testDbFile = File(File(userHome, ".manutenzioni-maker"), testDbFileName)
        if (testDbFile.exists()) testDbFile.delete()
        repo = JsonManutenzioneRepository(testDbFileName)
    }

    @AfterTest
    fun cleanup() {
        if (testDbFile.exists()) testDbFile.delete()
    }

    @Test
    fun `calcolaProssimeDateRicorrenti genera scadenze semestrali corrette`() {
        val dataBase = "22/10/2026"
        val freqSemestrale = Periodo(TipoPeriodo.M, 6)

        val prossime = RicorrenzeManutenzioneEngine.calcolaProssimeDateRicorrenti(
            dataBase = dataBase,
            frequenza = freqSemestrale,
            numeroOccorrenze = 4
        )

        assertEquals(4, prossime.size)
        assertEquals("22/04/2027", prossime[0], "1a scadenza semestrale")
        assertEquals("22/10/2027", prossime[1], "2a scadenza semestrale (+12 mesi)")
        assertEquals("22/04/2028", prossime[2], "3a scadenza semestrale (+18 mesi)")
        assertEquals("22/10/2028", prossime[3], "4a scadenza semestrale (+24 mesi)")
    }

    @Test
    fun `calcolaProssimeDateRicorrenti gestisce correttamente il fine mese e gli anni bisestili`() {
        // 31 Agosto 2026 + 6 mesi cade in Febbraio 2027 (anno non bisestile -> 28 giorni)
        val dataBase = "31/08/2026"
        val freqSemestrale = Periodo(TipoPeriodo.M, 6)

        val scadenze = RicorrenzeManutenzioneEngine.calcolaProssimeDateRicorrenti(
            dataBase = dataBase,
            frequenza = freqSemestrale,
            numeroOccorrenze = 4
        )

        assertEquals("28/02/2027", scadenze[0], "Febbraio 2027 ha 28 giorni")
        assertEquals("31/08/2027", scadenze[1], "Ritorno al 31 in Agosto")
        // Anno 2028 è bisestile (29 giorni a Febbraio)
        assertEquals("29/02/2028", scadenze[2], "Febbraio 2028 è bisestile (29 giorni)")
        assertEquals("31/08/2028", scadenze[3], "Ritorno al 31 in Agosto 2028")
    }

    @Test
    fun `calcolaProssimeDateRicorrenti con frequenza annuale genera intervalli di 12 mesi`() {
        val dataBase = "15/03/2026"
        val freqAnnuale = Periodo(TipoPeriodo.A, 1)

        val scadenze = RicorrenzeManutenzioneEngine.calcolaProssimeDateRicorrenti(
            dataBase = dataBase,
            frequenza = freqAnnuale,
            numeroOccorrenze = 3
        )

        assertEquals(3, scadenze.size)
        assertEquals("15/03/2027", scadenze[0])
        assertEquals("15/03/2028", scadenze[1])
        assertEquals("15/03/2029", scadenze[2])
    }

    @Test
    fun `generaRicorrenzeFuture genera eventi clonati con serieId condiviso e stati pianificati`() {
        val serieId = UUID.randomUUID().toString()
        val eventoBase = EventoManutenzione(
            id = "evt-base-001",
            cantiereId = "cant-001",
            clienteId = "cli-001",
            dataPianificata = "22/10/2026",
            nomeManutentore = "Ing. Mario Rossi",
            stato = StatoEventoManutenzione.PIANIFICATO,
            impiantiPianificati = listOf(
                ImpiantoPianificatoSnapshot(
                    impiantoId = "imp-em-1",
                    codIntervento = "EM",
                    descrizione = "Illuminazione di Emergenza",
                    periodicitaScelta = Periodo(TipoPeriodo.M, 6),
                    numeroAttivitaPreviste = 5,
                    attivitaDescrizioni = listOf("Verifica autonomia", "Controllo visivo")
                )
            ),
            serieRicorrenzaId = serieId,
            note = "Intervento programmato semestrale"
        )

        val future = RicorrenzeManutenzioneEngine.generaRicorrenzeFuture(
            eventoBase = eventoBase,
            orizzonteMesi = 24
        )

        // 24 mesi con passo a 6 mesi = 4 eventi futuri
        assertEquals(4, future.size)

        val datePreviste = listOf("22/04/2027", "22/10/2027", "22/04/2028", "22/10/2028")
        future.forEachIndexed { idx, ev ->
            assertEquals(datePreviste[idx], ev.dataPianificata)
            assertEquals(serieId, ev.serieRicorrenzaId, "Tutte le ricorrenze devono condividere la stessa serieRicorrenzaId")
            assertEquals(StatoEventoManutenzione.PIANIFICATO, ev.stato)
            assertNull(ev.dataCompletamentoEffettiva)
            assertNull(ev.sessioneVerificaId)
            assertEquals("cant-001", ev.cantiereId)
            assertEquals("Ing. Mario Rossi", ev.nomeManutentore)
            assertTrue(ev.id != eventoBase.id, "Ogni ricorrenza deve avere un id univoco")
        }
    }

    @Test
    fun `repository salva ed estrae eventi per mese e gestisce cancellazione singola o a cascata`() = runBlocking {
        val serieA = "serie-cantiere-alpha"
        val serieB = "serie-cantiere-beta"

        val eventiOttobre = listOf(
            EventoManutenzione(
                id = "evt-ott-1",
                cantiereId = "cantiere-1",
                clienteId = "cli-1",
                dataPianificata = "22/10/2026",
                serieRicorrenzaId = serieA,
                impiantiPianificati = listOf(
                    ImpiantoPianificatoSnapshot("imp-1", "EM", "Emergenza", Periodo(TipoPeriodo.M, 6), 3)
                )
            ),
            EventoManutenzione(
                id = "evt-ott-2",
                cantiereId = "cantiere-2",
                clienteId = "cli-2",
                dataPianificata = "22/10/2026",
                serieRicorrenzaId = serieB,
                impiantiPianificati = listOf(
                    ImpiantoPianificatoSnapshot("imp-2", "Q", "Quadro BT", Periodo(TipoPeriodo.M, 6), 4)
                )
            )
        )

        val eventiFuturi = listOf(
            EventoManutenzione(
                id = "evt-apr-1",
                cantiereId = "cantiere-1",
                clienteId = "cli-1",
                dataPianificata = "22/04/2027",
                serieRicorrenzaId = serieA,
                impiantiPianificati = listOf(
                    ImpiantoPianificatoSnapshot("imp-1", "EM", "Emergenza", Periodo(TipoPeriodo.M, 6), 3)
                )
            )
        )

        // 1. Batch insert
        repo.salvaEventiManutenzioneBatch(eventiOttobre + eventiFuturi)

        // 2. Query per mese "2026-10"
        val trovatiOttobre = repo.caricaEventiPerMese("2026-10")
        assertEquals(2, trovatiOttobre.size)
        assertTrue(trovatiOttobre.any { it.id == "evt-ott-1" })
        assertTrue(trovatiOttobre.any { it.id == "evt-ott-2" })

        // 3. Query per mese "2027-04"
        val trovatiAprile = repo.caricaEventiPerMese("2027-04")
        assertEquals(1, trovatiAprile.size)
        assertEquals("evt-apr-1", trovatiAprile[0].id)

        // 4. Aggiorna stato evento a IN_CORSO poi a COMPLETATO
        repo.aggiornaStatoEvento("evt-ott-1", StatoEventoManutenzione.IN_CORSO)
        val inCorso = repo.caricaEventiManutenzione().find { it.id == "evt-ott-1" }
        assertNotNull(inCorso)
        assertEquals(StatoEventoManutenzione.IN_CORSO, inCorso.stato)

        repo.aggiornaStatoEvento("evt-ott-1", StatoEventoManutenzione.COMPLETATO, dataCompletamento = "22/10/2026")
        val completato = repo.caricaEventiManutenzione().find { it.id == "evt-ott-1" }
        assertNotNull(completato)
        assertEquals(StatoEventoManutenzione.COMPLETATO, completato.stato)
        assertEquals("22/10/2026", completato.dataCompletamentoEffettiva)

        // 5. Cancellazione singola di evt-ott-2 senza cancellare la serie
        repo.eliminaEventoManutenzione("evt-ott-2", eliminaSerieFutura = false)
        val dopoSingola = repo.caricaEventiManutenzione()
        assertNull(dopoSingola.find { it.id == "evt-ott-2" })

        // 6. Cancellazione a cascata di serieA: deve eliminare evt-ott-1 e evt-apr-1
        repo.eliminaEventoManutenzione("evt-ott-1", eliminaSerieFutura = true)
        val dopoCascata = repo.caricaEventiManutenzione()
        assertNull(dopoCascata.find { it.id == "evt-ott-1" })
        assertNull(dopoCascata.find { it.id == "evt-apr-1" })
        assertEquals(0, dopoCascata.size, "Tutti gli eventi eliminati")
    }

    @Test
    fun `pianificazione massiva su 5 cantieri simultanei genera eventi coordinati`() {
        val cantieriIds = listOf("cant-1", "cant-2", "cant-3", "cant-4", "cant-5")
        val dataIntervento = "22/10/2026"
        val freq = Periodo(TipoPeriodo.M, 6)

        val batchEventi = cantieriIds.map { cId ->
            val serieId = UUID.randomUUID().toString()
            EventoManutenzione(
                id = UUID.randomUUID().toString(),
                cantiereId = cId,
                clienteId = "cli-multi",
                dataPianificata = dataIntervento,
                serieRicorrenzaId = serieId,
                impiantiPianificati = listOf(
                    ImpiantoPianificatoSnapshot(
                        impiantoId = "imp-$cId",
                        codIntervento = "EM",
                        descrizione = "Emergenza",
                        periodicitaScelta = freq,
                        numeroAttivitaPreviste = 4
                    )
                )
            )
        }

        // Calcola per ciascuno le ricorrenze a 12 mesi (2 occorrenze future a 6 mesi)
        val future = batchEventi.flatMap { base ->
            RicorrenzeManutenzioneEngine.generaRicorrenzeFuture(base, orizzonteMesi = 12)
        }

        assertEquals(5, batchEventi.size, "5 eventi base")
        assertEquals(10, future.size, "2 future per ciascuno dei 5 cantieri = 10 eventi futuri")

        // Tutte le prime date future devono essere 22/04/2027
        val primeScadenze = future.filter { it.dataPianificata == "22/04/2027" }
        assertEquals(5, primeScadenze.size)

        // Tutte le seconde date future devono essere 22/10/2027
        val secondeScadenze = future.filter { it.dataPianificata == "22/10/2027" }
        assertEquals(5, secondeScadenze.size)
    }
}
