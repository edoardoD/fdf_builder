package manutenzioni.app.data

import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.bson.Document
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Test di ispezione diretta su MongoDB (con fallback ermetico su JSON se MongoDB è offline)
 * per verificare lo stato del campo "quantita" in tutti i documenti della collezione "impianti".
 */
class MongoQuantitaInspectionTest {

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017"
        private const val DB_NAME   = "manutenzioni_db"

        private fun isMongoAvailable(): Boolean = try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("localhost", 27017), 200)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    @Test
    fun `ispezione campo quantita su tutti i documenti della collezione impianti in MongoDB`() = runBlocking {
        if (!isMongoAvailable()) {
            val tempFile = Files.createTempFile("quantita_inspection_", ".json").toFile().apply { deleteOnExit() }
            val resourceText = javaClass.classLoader.getResourceAsStream("manutenzioni_db.json")
                ?.bufferedReader()?.use { it.readText() }
                ?: File("desktopApp/src/desktopMain/resources/manutenzioni_db.json").takeIf { it.exists() }?.readText()
            if (resourceText != null) {
                tempFile.writeText(resourceText)
            }
            val jsonRepo = JsonManutenzioneRepository(tempFile)
            val impianti = jsonRepo.caricaImpianti()
            val anomali = impianti.filter { it.quantita > 1 }
            assertTrue(
                anomali.isEmpty(),
                "Trovati ${anomali.size} impianti con quantita > 1 nel DB JSON: ${anomali.map { it.codIntervento }}"
            )
            return@runBlocking
        }

        val client = MongoClient.create(MONGO_URI)
        val database = client.getDatabase(DB_NAME)
        val collection = database.getCollection<Document>("impianti")

        val rawDocs = collection.find().toList()

        println()
        println("═══════════════════════════════════════════════════════════════════════════════════")
        println("  REPORT ISPEZIONE MONGODB: Collezione 'impianti' (${rawDocs.size} documenti totali)")
        println("═══════════════════════════════════════════════════════════════════════════════════")

        var conCampoQuantita = 0
        var conQuantitaMaggioreDiUno = 0
        val impiantiAnomali = mutableListOf<String>()

        for ((index, doc) in rawDocs.withIndex()) {
            val id = doc.getString("id") ?: "N/A"
            val cod = doc.getString("codIntervento") ?: "N/A"
            val nome = doc.getString("nomeCompleto") ?: "N/A"
            val cantiereId = doc.getString("cantiereId")
            val hasQuantitaKey = doc.containsKey("quantita")
            val quantitaVal = if (hasQuantitaKey) doc.getInteger("quantita") else null

            val tipoAmbito = if (cantiereId == null) "[GLOBALE/TEMPLATE]" else "[CANTIERE $cantiereId]"

            if (hasQuantitaKey) {
                conCampoQuantita++
            }

            if (quantitaVal != null && quantitaVal > 1) {
                conQuantitaMaggioreDiUno++
                impiantiAnomali.add("$cod ($nome) -> quantita = $quantitaVal in $tipoAmbito")
            }

            println(
                String.format(
                    "  [%02d] %-6s | %-30s | %-22s | campo 'quantita' presente: %-5s | valore: %s",
                    index + 1,
                    cod,
                    nome.take(30),
                    if (cantiereId == null) "Globale" else "Cantiere (${cantiereId.take(8)}...)",
                    hasQuantitaKey,
                    quantitaVal?.toString() ?: "ASSENTE (default 1)"
                )
            )
        }

        println("═══════════════════════════════════════════════════════════════════════════════════")
        println("  RIASSUNTO:")
        println("  - Documenti totali esaminati          : ${rawDocs.size}")
        println("  - Documenti con campo 'quantita' BSON : $conCampoQuantita")
        println("  - Documenti con 'quantita' > 1        : $conQuantitaMaggioreDiUno")
        if (impiantiAnomali.isNotEmpty()) {
            println()
            println("  ⚠️  ATTENZIONE! Trovati impianti con quantita > 1:")
            impiantiAnomali.forEach { println("     - $it") }
        } else {
            println("  ✓ Nessun impianto ha quantita > 1 nel database.")
        }
        println("═══════════════════════════════════════════════════════════════════════════════════")
        println()

        client.close()

        assertTrue(
            conQuantitaMaggioreDiUno == 0,
            "Trovati $conQuantitaMaggioreDiUno impianti con quantita > 1 in MongoDB:\n" +
                    impiantiAnomali.joinToString("\n")
        )
    }
}
