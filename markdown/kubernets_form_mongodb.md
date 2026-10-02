# Piano di Implementazione: Migrazione Database a MongoDB

Questo piano definisce i passaggi operativi che l'agente dovrà seguire per migrare il sistema di persistenza dell'applicazione "Manutenzioni Maker" dall'attuale file JSON a un database MongoDB, sviluppando in sicurezza su un container locale (KIAC/Docker) per poi passare al Windows Server in produzione.

## User Review Required

> [!IMPORTANT]
> **Scelta del Driver MongoDB:** Il piano prevede l'uso del driver asincrono ufficiale per Kotlin (`mongodb-driver-kotlin-coroutine`). Questo si sposa perfettamente con l'attuale uso delle coroutine nel ViewModel. Procediamo con il driver coroutine (reattivo) o preferisci quello sincrono classico?

> [!WARNING]
> **Gestione Configurazione (Connection String):** Avremo bisogno di un file di configurazione (es. `application.properties` o variabili d'ambiente) per salvare l'indirizzo del database in modo da non dover ricompilare l'app quando si passa dal Mac al Windows Server. Vuoi che l'agente configuri un sistema di properties locale?

## Open Questions

1. Hai già predisposto l'ambiente KIAC sul tuo Mac, o vuoi che il primo task dell'agente sia fornirti il file YAML di configurazione per tirare su il cluster MongoDB?
2. Attualmente i dati esistenti nel `manutenzioni_db.json` devono essere salvati e trasferiti su MongoDB, oppure partirai da un database vuoto per i test?

---

## Proposed Changes

### 1. Setup Ambiente Virtuale (Locale su M3)
*   Creazione di un file di deployment per Kubernetes/KIAC (o un `docker-compose.yml` equivalente) per avviare un container MongoDB locale (porta 27017) senza autenticazione per la fase iniziale di sviluppo.

---

### 2. Aggiornamento Dipendenze (Build System)

#### [MODIFY] gradle/libs.versions.toml
*   Aggiunta della versione per il driver MongoDB Kotlin.
*   Dichiarazione della libreria `org.mongodb:mongodb-driver-kotlin-coroutine`.

#### [MODIFY] desktopApp/build.gradle.kts
*   Aggiunta della dipendenza al modulo MongoDB.

---

### 3. Sviluppo del Data Layer (Repository)

#### [NEW] desktopApp/src/desktopMain/kotlin/manutenzioni/app/data/MongoManutenzioneRepository.kt
*   Implementazione dell'interfaccia `ManutenzioneRepository` del *Domain Layer*.
*   Inizializzazione del `MongoClient` puntando a `mongodb://localhost:27017`.
*   Sviluppo dei metodi CRUD mappando le `@Serializable` data class (come `Impianto`, `Cliente`, `Attivita`) direttamente in documenti BSON. MongoDB Kotlin Serialization framework gestisce nativamente le classi `@Serializable` di Kotlinx.

#### [MODIFY] desktopApp/src/desktopMain/kotlin/manutenzioni/app/Main.kt (Dependency Injection)
*   Scambio dell'implementazione: invece di istanziare `JsonManutenzioneRepository`, l'app instanzierà il nuovo `MongoManutenzioneRepository`.

---

### 4. Gestione Transizione e Migrazione Dati (Opzionale)

#### [NEW] desktopApp/src/desktopMain/kotlin/manutenzioni/app/data/DataMigrator.kt
*   Script eseguibile (runnable) temporaneo che legge tutti i dati dall'attuale `manutenzioni_db.json` utilizzando il vecchio repository e li inserisce massivamente (`insertMany`) nelle nuove collezioni MongoDB. Questo assicura che il lavoro fatto finora non vada perso.

---

### 5. Gestione Errori e UX (UI Layer)

#### [MODIFY] desktopApp/src/desktopMain/kotlin/manutenzioni/app/ui/ManutenzioniViewModel.kt
*   Implementazione di un controllo di connessione (Health Check) all'avvio.
*   Aggiornamento del `ManutenzioniUiState` per mostrare un alert o la StatusBar rossa se il server MongoDB non è raggiungibile sulla rete (fondamentale per la UX dell'Ufficio Tecnico in caso il Windows Server si riavvii o la rete cada).

---

## Verification Plan

### Automated Tests
*   Esecuzione di unit test mockando il MongoClient per verificare che la logica di business continui a funzionare inalterata.
*   Esecuzione di `./gradlew :desktopApp:compileKotlinDesktop` per validare le dipendenze.

### Manual Verification
1.  **Avvio KIAC/Container:** Il container MongoDB gira correttamente sul Mac M3 esponendo la porta 27017.
2.  **Run dell'App Locale:** Eseguendo `./gradlew :desktopApp:run`, l'applicazione parte, si connette al container e mostra l'interfaccia vuota.
3.  **Migrazione Dati:** Avvio dello script di migrazione e verifica visiva tramite "MongoDB Compass" che i clienti e gli impianti siano stati popolati nel database virtuale.
4.  **Operatività:** Creazione di un nuovo impianto dall'app (ImpiantoEditor) e verifica immediata in MongoDB Compass della sua persistenza.
5.  **Offline Check:** Spegnimento del container per verificare che la UI gestisca l'errore in modo aggraziato mostrando *"Errore di connessione al database di rete"*.
