# Walkthrough: Integrazione MongoDB e KIAC

Ho completato tutta l'infrastruttura di codice per passare dal file JSON locale a MongoDB. Tutto il codice è stato testato a livello di compilazione ed è pronto per l'uso.

## Cosa è stato fatto

### 1. Setup Infrastrutturale
Ho creato il file [mongodb-kiac.yaml](file:///Users/edoardo/development_prdj/fdf_builder/mongodb-kiac.yaml) nella root del progetto. Questo file contiene la configurazione Kubernetes necessaria per tirare su un cluster MongoDB sul tuo Mac in ambiente isolato (esponendo la porta 27017).

### 2. Aggiornamento Dipendenze
Ho aggiunto il driver ufficiale di MongoDB per Kotlin (`org.mongodb:mongodb-driver-kotlin-coroutine`) all'interno di [libs.versions.toml](file:///Users/edoardo/development_prdj/fdf_builder/gradle/libs.versions.toml) e [build.gradle.kts](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/build.gradle.kts). Ho lanciato la build e confermo che Gradle ha scaricato e verificato correttamente i pacchetti.

### 3. Sviluppo del Data Layer MongoDB
Ho scritto un nuovo repository [MongoManutenzioneRepository.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/app/data/MongoManutenzioneRepository.kt). Questo file implementa la stessa interfaccia del vecchio repository JSON (quindi l'app non si accorge del cambiamento) ma invia i dati nativamente al server `mongodb://localhost:27017` utilizzando le API reattive (Coroutines).

### 4. Dependency Injection
Ho aggiornato [Main.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/app/Main.kt) in modo che l'applicazione principale ora usi nativamente il database MongoDB all'avvio anziché il file JSON locale. Inoltre, il ViewModel gestisce automaticamente gli errori di connessione mostrando un messaggio rosso nella UI in caso di timeout verso il database.

### 5. Script di Migrazione Dati (Nessuna Perdita Dati)
Come richiesto, ho creato uno script per mantenere intatti tutti i clienti e gli impianti attuali: [DataMigrator.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/app/data/DataMigrator.kt).
Lo script legge i dati dal file JSON esistente e li inietta nel nuovo database MongoDB.

---

## Prossimi Passi (Tocca a Te)

Ora che il codice è pronto, dobbiamo testare l'infrastruttura. Dal tuo terminale sul Mac:

1. **Avvia il cluster MongoDB tramite KIAC/kubectl:**
   ```bash
   kubectl apply -f mongodb-kiac.yaml
   ```
2. **Esegui lo script di migrazione dati:**
   Solo quando il container è attivo, avvia la migrazione per trasferire i tuoi vecchi dati JSON:
   ```bash
   ./gradlew :desktopApp:run -PmainClass=manutenzioni.app.data.DataMigratorKt
   ```
3. **Avvia l'app in modalità MongoDB:**
   ```bash
   ./gradlew :desktopApp:run
   ```

Se tutto funziona correttamente, l'app si aprirà mostrando i tuoi vecchi dati, ma ora starà salvando e leggendo nativamente su MongoDB in ambiente virtualizzato!
