package manutenzioni.app.data

import kotlinx.coroutines.runBlocking
import manutenzioni.domain.model.*
import manutenzioni.domain.service.rules.Uni11224RiCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Script e test per allineare l'impianto globale RI (e le istanze nei cantieri)
 * al catalogo semestrale UNI 11224:2026 (06 Mesi).
 */
class AntincendioMigrationTest {

    private val MONGO_URI = "mongodb://localhost:27017"
    private val DB_NAME = "manutenzioni_db"

    @Test
    fun popolaImpiantoGlobaleRI() = runBlocking {
        val mongoAvailable = try {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("localhost", 27017), 200); true }
        } catch (_: Exception) {
            false
        }
        val repo: manutenzioni.domain.ManutenzioneRepository = if (mongoAvailable) {
            MongoManutenzioneRepository(MONGO_URI, DB_NAME)
        } else {
            val tempFile = java.nio.file.Files.createTempFile("antincendio_migration_test_", ".json").toFile().apply { deleteOnExit() }
            val resourceText = javaClass.classLoader.getResourceAsStream("manutenzioni_db.json")
                ?.bufferedReader()?.use { it.readText() }
                ?: java.io.File("desktopApp/src/desktopMain/resources/manutenzioni_db.json").takeIf { it.exists() }?.readText()
            if (resourceText != null) tempFile.writeText(resourceText)
            JsonManutenzioneRepository(tempFile)
        }

        val templateRi = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Impianto rilevazione incendi",
            premessa = Uni11224RiCatalog.PREMESSA_SICUREZZA_RI,
            listaAttivita = Uni11224RiCatalog.LISTA_B2_VERIFICA_PERIODICA_SISTEMA,
            listaNormative = Uni11224RiCatalog.LISTA_NORMATIVE_RI,
            cantiereId = null
        )

        repo.aggiornaImpiantiGlobalmente(templateRi)

        val impianti = repo.caricaImpianti()
        val updatedRi = impianti.firstOrNull { it.codIntervento == "RI" && it.cantiereId == null } as? RilevazioneAntincendio
        assertTrue(updatedRi != null, "Il template globale RI deve essere presente come RilevazioneAntincendio")
        assertEquals(13, updatedRi.listaAttivita.size)
        assertTrue(updatedRi.listaAttivita.all { it.frequenza == Periodo(TipoPeriodo.M, 6) })
    }
}
