package manutenzioni.app.data

import kotlinx.coroutines.runBlocking
import manutenzioni.domain.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Script/Test per popolare o sincronizzare l'impianto globale RIG con le attività Cross-Plant.
 */
class GasMigrationTest {

    val attivitaGasList = listOf(
        // --- ATTIVITA' GLOBALI BASE ---
        Attivita(nAttivita = 1, tipoAttivita = "Controllo", descrizione = "Nel caso di modifiche all'impianto, controllare il firmware della centrale e dei teminali remoti ove presenti", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
        Attivita(nAttivita = 2, tipoAttivita = "Controllo", descrizione = "Verifica degli eventi registrati nella centrale. Verifica che non siano presenti allarmi.", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
        Attivita(nAttivita = 3, tipoAttivita = "Controllo", descrizione = "Verifica dell'efficienza, commutazione delle alimentazioni, segnalazioni, rimozione alimentazione primaria.", frequenza = Periodo(TipoPeriodo.M, 1), targetImpiantoCod = null),
        Attivita(nAttivita = 4, tipoAttivita = "Controllo", descrizione = "Stato delle batterie, efficienza di lampade, led e segnalazioni ottiche e digitali", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
        Attivita(nAttivita = 5, tipoAttivita = "Controllo", descrizione = "Prova di funzionamento delle segnalazioni ottice ed acustiche locali, verifica della capacità di ricevere gli allarmi provenienti dai dispositivi automatici e manuali", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
        Attivita(nAttivita = 6, tipoAttivita = "Verifica", descrizione = "Esecuzione del test automatico dell'impianto da parte della centrale", frequenza = Periodo(TipoPeriodo.M, 2), targetImpiantoCod = null),
        Attivita(nAttivita = 7, tipoAttivita = "Controllo", descrizione = "Controllo generale a vista sull'integrità esteriore, stato di conservazione e stabilità dei vari componenti dell'impianto", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
        Attivita(nAttivita = 8, tipoAttivita = "Verifica", descrizione = "Verifica di efficienza del sistema di visualizzazione grafica e possibilità di inviare e ricevere comandi", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
        Attivita(nAttivita = 9, tipoAttivita = "Verifica", descrizione = "Verifica di efficienza dei segnali di rinvio degli stati di allarme e guasto sui ripetitori, modem, combinatori", frequenza = Periodo(TipoPeriodo.M, 4), targetImpiantoCod = null),
        Attivita(nAttivita = 10, tipoAttivita = "Controllo", descrizione = "Verifica segnalazione guasto su apertura o corto circuito delle linee di rivelazione e di comando sovegliate", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null),
        Attivita(nAttivita = 11, tipoAttivita = "Controllo", descrizione = "Prova dei Rivelatori di GAS Puntiformi impiegando dispositivi artificiali di produzione di gas, simulando l'insorgere di un perdita di gas in funzione delle percentuali di concentrazione impostate in centrale.", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = null),

        // --- ATTIVITA' CONDIZIONATE (CROSS-PLANT DISCOVERY) ---
        Attivita(nAttivita = 12, tipoAttivita = "Gruppo elettrogeno", descrizione = "Prova dei Rivelatori di GAS Puntiformi asserviti al locale Gruppo Elettrogeno impiegando dispositivi artificiali di produzione di gas, simulando l'insorgere di una perdita in funzione delle soglie di allarme preimpostate", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "GE"),
        Attivita(nAttivita = 13, tipoAttivita = "Sgancio generale emergenza", descrizione = "Verifica dell'intervento dell'elettrovalvola di intercettazione gas/combustibile su comando di emergenza e simulazione di allarme gas dalla centrale", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "PEM"),
        Attivita(nAttivita = 14, tipoAttivita = "Rilevazione incendi", descrizione = "Verifica della trasmissione dei segnali di allarme e guasto dalla centrale Rilevazione Gas alla centrale di Rilevazione Incendi", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "RI"),
        Attivita(nAttivita = 15, tipoAttivita = "Quadro elettrico", descrizione = "Verifica dell'asservimento e sgancio alimentazione quadro elettrico locale tecnico su superamento della seconda soglia di allarme concentrazione gas", frequenza = Periodo(TipoPeriodo.A, 1), targetImpiantoCod = "Q"),
        Attivita(nAttivita = 16, tipoAttivita = "Limitatori Sovratensione", descrizione = "Verifica dell'integrità delle protezioni di linea e limitatori di sovratensione a monte della centrale di rivelazione gas", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "SPD"),
        Attivita(nAttivita = 17, tipoAttivita = "Cabina MT/BT", descrizione = "Verifica dell'asservimento e segnalazione allarme gas presso il locale cabina elettrica", frequenza = Periodo(TipoPeriodo.A, 1), targetImpiantoCod = "CAB")
    )

    @Test
    fun aggiornaImpiantoGlobaleRigNelJsonRepository() = runBlocking {
        val repo = JsonManutenzioneRepository()
        val impianti = repo.caricaImpianti()
        val existingRig = impianti.firstOrNull { it.codIntervento == "RIG" && it.cantiereId == null }

        val nuovoRig = RilevazioneGas(
            id = existingRig?.id ?: java.util.UUID.randomUUID().toString(),
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas",
            premessa = existingRig?.premessa ?: "Verifica periodica impianto di Rilevazione Gas",
            listaAttivita = attivitaGasList,
            listaNormative = existingRig?.listaNormative ?: listOf(
                Normativa("UNI EN 50194", "Apparecchi elettrici per la rivelazione di gas combustibili in locali ad uso domestico"),
                Normativa("UNI 11224", "Controllo iniziale e manutenzione dei sistemi di rivelazione incendi")
            ),
            cantiereId = null
        )

        repo.salvaImpianto(nuovoRig)
        val ricaricati = repo.caricaImpianti()
        val trovato = ricaricati.firstOrNull { it.codIntervento == "RIG" && it.cantiereId == null }
        assertTrue(trovato is RilevazioneGas)
        assertTrue(trovato.listaAttivita.any { it.targetImpiantoCod == "GE" })
    }
}
