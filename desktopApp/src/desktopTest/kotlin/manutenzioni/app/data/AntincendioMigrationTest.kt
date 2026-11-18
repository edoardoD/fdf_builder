package manutenzioni.app.data

import kotlinx.coroutines.runBlocking
import manutenzioni.domain.model.*
import kotlin.test.Test

/**
 * Script per popolare l'impianto globale RI in MongoDB
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
        
        val attivitaList = listOf(
            // --- ATTIVITA' GLOBALI BASE ---
            Attivita(nAttivita = 1, tipoAttivita = "Controllo", descrizione = "Nel caso di modifiche all'impianto, controllare il firmware della centrale e dei terminali remoti ove presenti", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
            Attivita(nAttivita = 2, tipoAttivita = "Controllo", descrizione = "Verifica degli eventi registrati nella centrale. Verifica che non siano presenti allarmi.", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
            Attivita(nAttivita = 3, tipoAttivita = "Controllo", descrizione = "Verifica dell'efficienza, commutazione delle alimentazioni, segnalazioni, rimozione alimentazione primaria.", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
            Attivita(nAttivita = 4, tipoAttivita = "Controllo", descrizione = "Stato delle batterie, efficienza di lampade, led e segnalazioni ottiche e digitali", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
            Attivita(nAttivita = 5, tipoAttivita = "Controllo", descrizione = "Prova di funzionamento delle segnalazioni ottiche ed acustiche locali, verifica della capacità di ricevere gli allarmi provenienti dai dispositivi automatici e manuali", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
            Attivita(nAttivita = 6, tipoAttivita = "Verifica", descrizione = "Esecuzione del test automatico dell'impianto da parte della centrale", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
            Attivita(nAttivita = 7, tipoAttivita = "Controllo", descrizione = "Controllo generale a vista sull'integrità esteriore, stato di conservazione e stabilità dei vari componenti dell'impianto", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
            Attivita(nAttivita = 8, tipoAttivita = "Verifica", descrizione = "Verifica di efficienza del sistema di visualizzazione grafica e possibilità di inviare e ricevere comandi", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
            Attivita(nAttivita = 9, tipoAttivita = "Verifica", descrizione = "Verifica di efficienza dei segnali di rinvio degli stati di allarme e guasto sui ripetitori, modem, combinatori", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
            Attivita(nAttivita = 10, tipoAttivita = "Controllo", descrizione = "Verifica segnalazione guasto su apertura o corto circuito delle linee di rivelazione e di comando sorvegliate", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null),
            
            // --- ATTIVITA' CONDIZIONATE (dal CSV) ---
            Attivita(nAttivita = 11, tipoAttivita = "Cabina MT/BT", descrizione = "Prova dei Rivelatori di Fumo Puntiformi (prova sul 50% dell'impianto) impiegando dispositivi artificiali di produzione del fumo o di altro, simulando l'insorgere di un incendio", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = "CAB"),
            Attivita(nAttivita = 12, tipoAttivita = "Gruppo di continuità", descrizione = "Prova dei Rivelatori di Fumo Puntiformi (prova sul 50% dell'impianto) impiegando dispositivi artificiali di produzione del fumo o di altro, simulando l'insorgere di un incendio", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = "UPS"),
            Attivita(nAttivita = 13, tipoAttivita = "Quadro Media Tensione", descrizione = "Prova dei Rivelatori di Fumo Lineari (prova sul 50% dell'impianto) impiegando filtri di oscuramento forniti dal costruttore per simulare l'insorgere di un incendio", frequenza = Periodo(TipoPeriodo.M, 3), targetImpiantoCod = "QMT"),
            Attivita(nAttivita = 14, tipoAttivita = "Quadro elettrico", descrizione = "Prova dei Rivelatori di Calore Lineari (prova 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo, suggeriti o forniti dal costruttore", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = "Q"),
            Attivita(nAttivita = 15, tipoAttivita = "Sgancio generale emergenza", descrizione = "Prova dei Sistemi di Rivelazione ad Aspirazione (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo, suggeriti o forniti dal costruttore", frequenza = Periodo(TipoPeriodo.M, 5), targetImpiantoCod = "PEM"),
            Attivita(nAttivita = 16, tipoAttivita = "Illuminazione emergenza", descrizione = "Prova dei Rivelatori per Condotta (prova del 50% dell'impianto) con dispositivi e strumenti predisposti allo scopo, in relazione al tubo di campionamento, suggeriti dal costruttore", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "EM"),
            Attivita(nAttivita = 17, tipoAttivita = "Illuminazione sicurezza", descrizione = "Prova dei Rivelatori di Fiamma (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo, suggeriti o forniti dal costruttore", frequenza = Periodo(TipoPeriodo.A, 1), targetImpiantoCod = "IS"),
            Attivita(nAttivita = 18, tipoAttivita = "Impianto elettrico", descrizione = "Prova dei Pulsanti di Allarme Manuale di Incendio (prova sul 50% dell'impianto) impiegando gli utensili forniti dal costruttore per la simulazione della rottura vetro", frequenza = Periodo(TipoPeriodo.A, 2), targetImpiantoCod = "IE"),
            Attivita(nAttivita = 19, tipoAttivita = "Rilevazione incendi", descrizione = "Prova dei Pulsanti Allarme Manuale (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo, suggeriti o forniti dal costruttore", frequenza = Periodo(TipoPeriodo.A, 3), targetImpiantoCod = null),
            Attivita(nAttivita = 20, tipoAttivita = "Trasformatore MT/BT a secco", descrizione = "Prova dei Segnalatori Ottico Acustici (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo, suggeriti o forniti dal costruttore", frequenza = Periodo(TipoPeriodo.A, 4), targetImpiantoCod = "TRFS"),
            Attivita(nAttivita = 21, tipoAttivita = "Trasformatore MT/BT in olio", descrizione = "Prova dei moduli di ingresso/uscita e del Comando dei Fermi Elettromagnetici (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo", frequenza = Periodo(TipoPeriodo.A, 5), targetImpiantoCod = "TRFO"),
            Attivita(nAttivita = 22, tipoAttivita = "Impianto fotovoltaico", descrizione = "Prova dei moduli di ingresso/uscita e del Comando di Attuatori dei Sistemi di Estinzione (prova del 50% dell'impianto) impiegando dispositivi e strumenti predisposti allo scopo", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "FTV"),
            Attivita(nAttivita = 23, tipoAttivita = "Limitatori Sovratensione", descrizione = "Prova dei Rivelatori di GAS Puntiformi impiegando dispositivi artificiali di produzione di gas, simulando l'insorgere di un perdita di gas in funzione delle percentuali di concentrazione impostate in centrale.", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "SPD"),
            Attivita(nAttivita = 24, tipoAttivita = "Gruppo elettrogeno", descrizione = "Prova dei Rivelatori di GAS Puntiformi impiegando dispositivi artificiali di produzione di gas, simulando l'insorgere di un perdita di gas in funzione delle percentuali di concentrazione impostate in centrale.", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "GE")
        )

        // Cerchiamo se esiste già un impianto globale RI
        val impianti = repo.caricaImpianti()
        val existingRi = impianti.firstOrNull { it.codIntervento == "RI" && it.cantiereId == null }

        if (existingRi != null) {
            val aggiornato = existingRi.copyWithBasicParams(listaAttivita = attivitaList)
            repo.salvaImpianto(aggiornato)
            println("✅ Impianto Globale RI AGGIORNATO nel database.")
        } else {
            val nuovoRi = RilevazioneAntincendio(
                codIntervento = "RI",
                nomeCompleto = "Rilevazione Incendi",
                premessa = "Verifica periodica impianto di Rilevazione Incendi",
                listaAttivita = attivitaList,
                cantiereId = null // E' un template globale
            )
            repo.salvaImpianto(nuovoRi)
            println("✅ Impianto Globale RI CREATO nel database.")
        }
    }
}
