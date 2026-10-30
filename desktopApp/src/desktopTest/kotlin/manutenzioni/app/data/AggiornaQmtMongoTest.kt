package manutenzioni.app.data

import com.mongodb.client.model.Filters.eq
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.MqtSwitchType
import manutenzioni.domain.model.Normativa
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.model.normalizeType
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AggiornaQmtMongoTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    @Test
    fun `aggiorna QMT in MongoDB con i dati di QMT csv e verifica`() = runBlocking {
        val repo = MongoManutenzioneRepository(
            connectionString = "mongodb://localhost:27017",
            dbName = "manutenzioni_db"
        )

        // 1. Carica il template QMT da manutenzioni_db.json (allineato a QMT.csv)
        val resourceStream = javaClass.classLoader.getResourceAsStream("manutenzioni_db.json")
        val jsonText = resourceStream?.bufferedReader()?.use { it.readText() }
            ?: File("desktopApp/src/desktopMain/resources/manutenzioni_db.json").readText()

        val dbWrapper = json.decodeFromString<ManutenzioniDatabase>(jsonText)
        val qmtTemplate = dbWrapper.impianti
            .map { it.normalizeType() }
            .filterIsInstance<QuadroMQT>()
            .firstOrNull { it.codIntervento.equals("QMT", ignoreCase = true) }

        assertNotNull(qmtTemplate, "Template QMT non trovato nelle risorse")
        assertEquals(21, qmtTemplate.listaAttivita.size, "Il template QMT deve avere 21 attività")

        // 2. Aggiorna globalmente MongoDB
        repo.aggiornaImpiantiGlobalmente(qmtTemplate)

        // 3. Verifica MongoDB
        val tuttiImpianti = repo.caricaImpianti().filter { it.codIntervento == "QMT" }
        println("QMT trovati in MongoDB: ${tuttiImpianti.size}")
        assertTrue(tuttiImpianti.isNotEmpty(), "Nessun impianto QMT trovato nel DB")

        // 3.1 Verifica template globale (cantiereId == null)
        val global = tuttiImpianti.firstOrNull { it.cantiereId == null }
        assertNotNull(global, "Template globale QMT non trovato in MongoDB")
        assertTrue(global is QuadroMQT)
        assertEquals(21, global.listaAttivita.size, "Il template globale deve avere 21 attività")
        
        // Verifica che le attività dell'interruttore abbiano zona "Interruttore"
        val switchActsGlobal = listOf(11, 12, 14, 18, 19, 20, 21)
        for (n in switchActsGlobal) {
            val act = global.listaAttivita.first { it.nAttivita == n }
            assertEquals("Interruttore", act.zona, "Attività $n del template globale deve avere zona='Interruttore'")
        }

        // 3.2 Verifica istanza di cantiere (cantiereId != null)
        val cantiereQmt = tuttiImpianti.firstOrNull { it.cantiereId != null }
        if (cantiereQmt != null) {
            assertTrue(cantiereQmt is QuadroMQT)
            println("Trovata istanza cantiere QMT id=${cantiereQmt.id}, cantiereId=${cantiereQmt.cantiereId}, tipoInterruttore=${cantiereQmt.tipoInterruttore}")
            assertEquals(21, cantiereQmt.listaAttivita.size, "L'istanza cantiere deve avere 21 attività")
            val switchLabel = cantiereQmt.tipoInterruttore?.label ?: "SF6"
            for (n in switchActsGlobal) {
                val act = cantiereQmt.listaAttivita.first { it.nAttivita == n }
                assertEquals(switchLabel, act.zona, "Attività $n dell'istanza cantiere deve avere zona='$switchLabel'")
            }
        }
    }
}
