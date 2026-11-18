package manutenzioni.app.data

import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Filters.or
import com.mongodb.client.model.Filters.exists
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Normativa
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.TipoPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AggiornaAttivitaQuadroTest {

    private val premessaQuadri = "Le attività sui quadri elettrici devono essere svolte da personale addestrato ai lavori elettrici con qualifica PES come da Norma CEI 11-27, eseguiti in totale sicurezza. Prima di accedere alle parti elettriche in tensione del quadro occorre verificare che siano state sezionate tutte le alimentazioni elettriche con tensione superiore a 25 Vca / 60 Vdc, verificando preventivamente tramite tester la presenza di eventuali tensioni residue pericolose"

    private val attivitaQuadri = listOf(
        Attivita(
            nAttivita = 1,
            tipoAttivita = "Prova",
            descrizione = "Prova di funzionamento di tutti i dispositivi differenziali tramite tasto di prova",
            frequenza = Periodo(TipoPeriodo.M, 6)
        ),
        Attivita(
            nAttivita = 2,
            tipoAttivita = "Controllo",
            descrizione = "Controllo presenza targa quadro, sigla identificazione quadro, schema elettrico, targhette identificazione apparecchiature ed interruttori, numerazione morsettiere, più alimentazioni.",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 3,
            tipoAttivita = "Controllo",
            descrizione = "Controllo presenza, integrità ed efficienza dei dispositivi di sicurezza apertura portelle, stato di conservazione interno/esterno armadio, ed integrità involucro",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 4,
            tipoAttivita = "Controllo",
            descrizione = "Controllo segni di scariche elettriche sull'involucro, o presenza bruciature su involucro o apparecchiature",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 5,
            tipoAttivita = "Controllo",
            descrizione = "Verifica corretto funzionamento della strumentazione di misura (se presente)",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 6,
            tipoAttivita = "Verifica",
            descrizione = "Controllo dispositivi di inserzione, integrità teleruttori, integrità fusibili di protezione, efficienza interruttori automatici, integrità scaricatori di sovratensione",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 7,
            tipoAttivita = "Controllo",
            descrizione = "Controllo dei dispositivi di comando e segnalazione (selettori, pulsanti, spie, ecc.)",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 8,
            tipoAttivita = "Verifica",
            descrizione = "Controllo dei collegamenti elettrici al circuito principale di potenza, serraggio delle connessioni, verifica di situazioni di surriscaldamento, controllo integrità terminali",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 9,
            tipoAttivita = "Verifica",
            descrizione = "Controllo dei collegamenti elettrici ai circuiti ausiliari, serraggio delle connessioni",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 10,
            tipoAttivita = "Controllo",
            descrizione = "Verifica della efficacia delle aperture di ventilazione naturale sul quadro, e verifica del mantenimento del grado di protezione IP di progetto",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 11,
            tipoAttivita = "Controllo",
            descrizione = "Controllo del corretto funzionamento della ventola di raffreddamento (se presente)",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 12,
            tipoAttivita = "Controllo",
            descrizione = "Controllo inaccessibilità del quadro alle persone non addestrate (portella chiusa a chiave, quadro entro locale chiuso a chiave o in zona confinata alle persone non addestrate, altro)",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 13,
            tipoAttivita = "Verifica",
            descrizione = "Controllo eventuale presenza di corpi estranei all'interno del quadro",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 14,
            tipoAttivita = "Controllo",
            descrizione = "Controllo serraggio della bulloneria meccanica e della bulloneria elettrica",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 15,
            tipoAttivita = "Controllo",
            descrizione = "Controllo dei blocchi e degli interblocchi",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 16,
            tipoAttivita = "Misura",
            descrizione = "Verifica e misura di continuità del collegamento di messa a terra e del collettore di terra del quadro",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 17,
            tipoAttivita = "Verifica",
            descrizione = "Controllo del corretto fissaggio a pavimento/parete del quadro tale da evitare il rovesciamento/caduta",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 18,
            tipoAttivita = "Controllo",
            descrizione = "Controllo presenza e integrità cartellonistica di sicurezza",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 19,
            tipoAttivita = "Manutenzione",
            descrizione = "Pulizia parti isolanti e parti attive, aspirazione polvere",
            frequenza = Periodo(TipoPeriodo.A, 1)
        ),
        Attivita(
            nAttivita = 20,
            tipoAttivita = "Manutenzione",
            descrizione = "Pulizia e lubrificazione leverismi, verifica delle parti estraibili",
            frequenza = Periodo(TipoPeriodo.A, 2)
        )
    )

    private val normativeQuadri = listOf(
        Normativa("CEI 17-13", "Apparecchiature assiemate di protezione e manovra per bassa tensione"),
        Normativa("CEI 64-8", "Impianti elettrici utilizzatori"),
        Normativa("CEI 11-27", "Lavori su impianti elettrici")
    )

    @Test
    fun `aggiorna attivita del quadro in MongoDB e verifica`() = runBlocking {
        val mongoAvailable = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("localhost", 27017), 200); true }
        } catch (_: Exception) {
            false
        }

        val repo: manutenzioni.domain.ManutenzioneRepository = if (mongoAvailable) {
            // 1. Elimina documenti orfani senza campo "id"
            val client = MongoClient.create("mongodb://localhost:27017")
            val db = client.getDatabase("manutenzioni_db")
            val col = db.getCollection<Document>("impianti")
            col.deleteMany(or(eq("id", null), eq("id", ""), exists("id", false)))
            client.close()
            MongoManutenzioneRepository()
        } else {
            val tempFile = java.nio.file.Files.createTempFile("aggiorna_quadro_test_", ".json").toFile().apply { deleteOnExit() }
            val resourceText = javaClass.classLoader.getResourceAsStream("manutenzioni_db.json")
                ?.bufferedReader()?.use { it.readText() }
                ?: java.io.File("desktopApp/src/desktopMain/resources/manutenzioni_db.json").takeIf { it.exists() }?.readText()
            if (resourceText != null) tempFile.writeText(resourceText)
            JsonManutenzioneRepository(tempFile)
        }

        // 2. Prepara il template globale aggiornato
        val templateQuadro = QuadroBT(
            id = "template-q-bt",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            premessa = premessaQuadri,
            listaAttivita = attivitaQuadri,
            listaNormative = normativeQuadri,
            cantiereId = null,
            quantita = 1
        )

        // 3. Propaga globalmente su MongoDB a tutti i quadri esistenti
        repo.aggiornaImpiantiGlobalmente(templateQuadro)

        // 4. Verifica che tutti i quadri abbiano le 20 attività e la nuova premessa
        val tuttiImpianti = repo.caricaImpianti().filter { it.codIntervento == "Q" }
        println("Quadri verificati in MongoDB: ${tuttiImpianti.size}")
        assertTrue(tuttiImpianti.isNotEmpty(), "Nessun quadro trovato in MongoDB")
        for (q in tuttiImpianti) {
            println(" - Quadro id=${q.id}, cantiere=${q.cantiereId}, attivita=${q.listaAttivita.size}, premessa=${q.premessa?.take(40)}...")
            assertEquals(20, q.listaAttivita.size, "Il quadro ${q.id} deve avere 20 attività")
            assertEquals(premessaQuadri, q.premessa)
            assertEquals(3, q.listaNormative.size)
        }
    }
}
