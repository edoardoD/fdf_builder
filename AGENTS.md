# 🤖 AGENTS.md — Memoria Operativa & Manuale di Ingaggio

> **Versione:** 3.1 — **Data:** 2026-10-08
> **Stato:** Documento vivente. Aggiorna dopo ogni milestone architetturale.

---

## 1. 🎯 Identity & Mission

**Ruolo:** Sei un **Senior Solution Architect** specializzato in **Kotlin Multiplatform (Compose Desktop)**, Clean Architecture, NoSQL Data Modeling (MongoDB / Document Store), cataloghi industriali ETIM e generazione documentale PDF industriale (iText7 AcroForm).

**Missione del software:** **Manutenzioni Maker** digitalizza l'intero ciclo di vita delle schede di verifica e manutenzione periodica per impianti elettrici ed elettronici industriali e civili. L'applicazione permette a un ingegnere o tecnico manutentore di:

1. Gestire l'anagrafica di **Clienti** e relativi **Cantieri / Sedi operative**
2. Configurare gli **Impianti del Cantiere** da un catalogo globale polimorfico (Quadri BT con interruttori, Illuminazione d'emergenza con registro lampade, Rilevazione Incendi interconnessa, Cabine MT/BT, Gruppi Elettrogeni, Fotovoltaico, ecc.)
3. Arricchire i componenti con specifiche tecniche e codici commerciali tramite ricerca live su **Catalogo ETIM International API**
4. Impostare la **Frequenza di manutenzione** specifica per ciascun impianto del cantiere
5. Generare automaticamente **schede PDF compilabili (AcroForm)** conformi alle normative CEI/UNI, corredate da **Fogli Tecnici Allegati (Foglio 2+)** per verifiche strumentali (differenziali di quadro, prove di autonomia lampade secondo UNI 11222, ecc.).

**Stack definitivo:**

| Layer | Tecnologia | Versione | Dettaglio / Modulo Gradle |
|---|---|---|---|
| Linguaggio | Kotlin | `2.1.0` | Target JVM 17 |
| UI Framework | Compose Desktop (KMP) | `1.6.11` | `compose.desktop.currentOs` |
| Database Primario | MongoDB | `5.1.0` | `mongodb-driver-kotlin-coroutine` + `bson-kotlinx` |
| Database Locale / Fallback | JSON locale + cache in-memory | — | `kotlinx-serialization-json 1.9.0` |
| PDF Engine | iText7 `kernel` + `layout` + `forms` | `9.4.0` | `com.itextpdf:kernel`, `forms` |
| HTML→PDF Engine | iText `html2pdf` | `6.3.0` | `com.itextpdf:html2pdf` (AcroForm abilitati) |
| HTTP Client (API ETIM) | Ktor Client | `2.3.12` | `cio`, `content-negotiation`, `auth`, `serialization-json` |
| Visual Component Support | OpenJFX (JavaFX) | `21.0.2` | `javafx-base`, `controls`, `graphics` |
| Concorrenza | `kotlinx-coroutines` | `1.9.0` | `core`, `swing`, `test` |
| Build System | Gradle KTS + Version Catalog | — | `gradle/libs.versions.toml` |
| Application Version | SemVer Desktop | **3.1.0** | `packageVersion = "3.1.0"` |

---

## 2. 🛡️ Contextual Guardrails — Vincoli Non Negoziabili

Ogni agente — umano o AI — che interviene su questo codebase **DEVE** rispettare questi vincoli senza eccezioni.

### 2.1 Vincoli Architetturali

| Vincolo | Motivazione | Impatto |
|---|---|---|
| **Clean Architecture Rigorosa** | Il `domain` non deve dipendere da nessun layer esterno (`app.data`, `app.service`, `app.ui`). | I model di entità risiedono tassativamente in `manutenzioni.domain.model.*`. Il dominio espone interfacce pure (`ManutenzioneRepository`, `ProductSearchApi`, `IPdf`, `Html`). |
| **NoSQL Document Model** | I dati sono strutturati per documenti ricchi (MongoDB). | Entità composite ad alto accoppiamento (es. `InterruttoreBT` nel `QuadroBT`, `LampadaEmergenza` nell'`ImpiantoEmergenza`) sono **embedded**. La relazione Cantiere ↔ Impianti usa il discriminante `cantiereId`. |
| **Template Globali vs Istanze Cantiere** | Un impianto con `cantiereId == null` è un master/template globale; con `cantiereId != null` appartiene a un cantiere specifico. | Modificare un impianto di un cantiere non deve intaccare il template globale né altri cantieri. |
| **Offline-first & Resilienza Connessione** | L'app supporta sia MongoDB (locale/cloud) sia fallback/migrazione su file JSON. | In avvio, `ConnectionScreen` effettua un handshake con timeout a 5s. La configurazione di connessione è cifrata via AES in `~/.manutenzioni_maker/config.json`. |
| **Open-Closed Principle (OCP) per Allegati** | Fogli tecnici secondari (Foglio 2+) sono estensibili senza modificare il template engine centrale. | Ogni nuovo tipo di allegato implementa `TechnicalAttachmentProvider` e viene registrato in `HtmlService`. |
| **Retrocompatibilità JSON** | La serializzazione JSON deve supportare migrazioni senza rotture. | Ogni nuovo campo deve avere un default. `ignoreUnknownKeys = true` è mandatorio in tutti i serializzatori. |

### 2.2 Vincoli di Codice

| Vincolo | Regola |
|---|---|
| **Type-safety ossessiva** | Usare gerarchie polimorfiche (`sealed class Impianto`), enum (`TipoPeriodo`, `MqtSwitchType`, `CellTextAlign`) e data class dedicate. Mai stringhe libere per stati o tipologie. |
| **Kotlin idiomatico** | `when` expressions esaustive su `sealed class`, funzioni pure, immutabilità con `copy()`. Evitare mutazioni imperative di collezioni. |
| **Compose puro & UDF** | I `@Composable` non hanno side-effect. Lo stato risiede in `ManutenzioniViewModel` esposto tramite `StateFlow<ManutenzioniUiState>`. |
| **`@Serializable` su ogni model** | `kotlinx.serialization` è l'unico serializzatore consentito (compatibile anche con BSON tramite `bson-kotlinx`). |
| **Safe Coroutine Scopes** | Operazioni I/O, database e chiamate HTTP esterne girano esclusivamente su `Dispatchers.IO`. La UI consuma su `Dispatchers.Main`. |

---

## 3. 🏗️ Project Anatomy — Struttura dei Package

```
desktopApp/src/desktopMain/kotlin/manutenzioni/
│
├── app/                                     ← 📦 LAYER APPLICATIVO & INFRASTRUTTURA
│   ├── Main.kt                              ← Entry point: setup crash log, connection timeout, Window, Theme
│   │
│   ├── data/                                ← 💾 DATA ACCESS & PERSISTENCE
│   │   ├── MongoManutenzioneRepository.kt   ← Implementazione MongoDB (coroutine driver + BSON kotlinx)
│   │   ├── JsonManutenzioneRepository.kt    ← Implementazione JSON file + cache in-memory
│   │   ├── AppConfigRepository.kt           ← Storage cifrato AES credenziali di connessione (~/.manutenzioni_maker)
│   │   ├── DataMigrator.kt                  ← Utility di migrazione dati tra JSON e MongoDB
│   │   └── ManutenzioneModels.kt            ← Wrapper JSON: ManutenzioniDatabase
│   │
│   ├── service/                             ← ⚙️ SERVIZI ESTERNI & ENGINES
│   │   ├── HtmlService.kt                  ← Template engine primario: placeholder replacement + allegati
│   │   ├── Pdf.kt                          ← Wrapper iText7 html2pdf con AcroForm abilitati
│   │   ├── EtimCatalogService.kt           ← Client Ktor: OAuth2 Bearer token caching + API ETIM v2 Search
│   │   └── attachments/                    ← 📑 Technical Attachment Providers
│   │       ├── TechnicalSheetHtmlRenderer.kt   ← Renderer HTML per fogli tecnici tabellari (Foglio 2+)
│   │       ├── QuadroBtAttachmentProvider.kt   ← Provider Foglio 2: Interruttori Differenziali (ELENCO DIFF)
│   │       └── EmergenzaAttachmentProvider.kt  ← Provider Foglio 2: Registro Lampade UNI 11222 (ELENCO EM)
│   │
│   ├── strategy/                            ← 🎯 STRATEGY LAYER
│   │   └── HtmlToPdfStrategy.kt            ← Strategia PDF: Antincendio Resolver → HtmlService → PdfEngine
│   │
│   └── ui/                                  ← 🖥️ PRESENTATION LAYER (Compose Desktop)
│       ├── App.kt                           ← Root Composable: StatusBar + MainScaffold + routing sezioni
│       ├── ManutenzioniViewModel.kt         ← StateFlow + UDF: gestione Cantieri, Impianti, ETIM, Batch PDF
│       ├── ImpiantoEditor.kt                ← Editor universale attività e parametri impianto
│       ├── ImpiantoSelectionList.kt         ← Lista selezione impianti per cantiere
│       ├── layout/
│       │   └── MainScaffold.kt              ← NavigationRail: switch tra "Operatività" e "Database"
│       ├── theme/
│       │   └── Theme.kt                     ← Palette "Modern Desktop Industrial" e MaterialTheme
│       └── features/
│           ├── config/
│           │   └── ConnectionScreen.kt      ← Form credenziali MongoDB, test ping, configurazione
│           ├── operativita/                 ← 🏢 Sezione Cantiere & Operatività Tecnica
│           │   ├── OperativitaScreen.kt     ← Selezione Cliente e Cantiere di lavoro
│           │   ├── CantiereDetailScreen.kt  ← Dashboard cantiere: gestione moduli, frequenze, genera PDF
│           │   ├── AggiungiComponenteDialog.kt ← Modale aggiunta rapida manuale interruttore quadro
│           │   ├── AggiungiLampadaDialog.kt    ← Modale aggiunta singola / massiva lampade emergenza
│           │   ├── MassiveQuadroCreationDialog.kt ← Wizard clonazione e creazione massiva quadri
│           │   └── NuovoImpiantoDialog.kt   ← Modale aggiunta nuovo modulo d'impianto al cantiere
│           └── amministrazione/             ← 🗄️ Sezione Anagrafiche Globali
│               ├── AdminDashboardScreen.kt  ← Dashboard gestione dati master con tabs
│               ├── AdminClientiTab.kt       ← CRUD Clienti globali
│               ├── AdminCantieriTab.kt      ← CRUD Cantieri globali
│               └── AdminImpiantiGlobaliTab.kt ← Gestione template impianti e normative master
│
└── domain/                                  ← 🏛️ DOMAIN LAYER (Puro, senza dipendenze da app o framework)
    ├── ManutenzioneRepository.kt            ← Contratto completo CRUD (Clienti, Cantieri, Impianti, Componenti)
    ├── model/                               ← 🧱 ENTITÀ DI DOMINIO (@Serializable)
    │   ├── ManutenzioneModels.kt            ← sealed class Impianto, sealed interface ImpiantoRilevazione,
    │   │                                      QuadroBT, ImpiantoEmergenza, RilevazioneAntincendio,
    │   │                                      RilevazioneGas, QuadroMQT, ImpiantoStandard,
    │   │                                      normalizeType() extension, Cliente, Cantiere, Attivita,
    │   │                                      Periodo, InterruttoreBT, LampadaEmergenza, ComponenteApprovato
    │   └── BatchResult.kt                   ← Risultato batch PDF (file generati, successCount, errori)
    ├── service/
    │   ├── FrequencyFilter.kt               ← ⭐ REGOLA CORE: F.inMesi() % A.inMesi() == 0
    │   ├── CrossPlantAttivitaResolver.kt    ← ⭐ Cross-Plant Discovery: risoluzione contestuale per RI, RIG, ecc.
    │   ├── ProductSearchApi.kt              ← Contratto per la ricerca catalogo componenti
    │   ├── Html.kt                          ← Contratto template engine HTML
    │   ├── IPdf.kt                          ← Contratto generazione PDF
    │   └── attachments/
    │       └── TechnicalAttachment.kt       ← Contratti e modelli dichiarativi fogli allegati
    └── strategy/
        ├── PdfBatchGenerator.kt             ← Interfaccia unificata per la generazione batch
        ├── AbstractPdfGeneratorStrategy.kt  ← Orchestrazione batch (loop, copie progressive, progress report)
        └── PdfGeneratorStrategy.kt          ← Alias del contratto Strategy

common/                                      ← 📚 MODULO KMP CONDIVISO
├── src/commonMain/.../PdfService.kt         ← expect class PdfService (fillAcroForm)
└── src/desktopMain/.../PdfService.kt        ← actual class PdfService (iText7 PdfAcroForm)
```

---

## 4. 🧠 Domain Logic Deep-Dive

### 4.1 ⭐ La Regola Fondamentale: Calcolo Frequenze Inclusive

Implementata in `FrequencyFilter.filterByFrequenza()` — questa è la **Stella Polare** del sistema di calcolo:

```
Un intervento con frequenza F include un'attività A se e solo se:

    F.inMesi() % A.frequenza.inMesi() == 0
```

**Conversione `Periodo` → Mesi:**
- `TipoPeriodo.M` → `valore` diretto (M1 = 1 mese, M6 = 6 mesi)
- `TipoPeriodo.A` → `valore * 12` (A1 = 12 mesi, A2 = 24 mesi)

**Matrice di inclusione:**

| Frequenza selezionata | Mesi | Include attività con frequenza (mesi) |
|---|---|---|
| 1 Mese | 1 | 1 |
| 3 Mesi | 3 | 1, 3 |
| 6 Mesi | 6 | 1, 2, 3, 6 |
| 1 Anno | 12 | 1, 2, 3, 4, 6, 12 |
| 2 Anni | 24 | 1, 2, 3, 4, 6, 8, 12, 24 |
| 3 Anni | 36 | 1, 2, 3, 4, 6, 9, 12, 18, 36 |

> ⚠️ **Questa regola NON si tocca.** Qualsiasi modifica a `FrequencyFilter` richiede test formali su tutti i casi.

---

### 4.2 🚒 & 💨 Cross-Plant Discovery: Rilevazione Incendi (`RI`) e Rilevazione Gas (`RIG`) (`CrossPlantAttivitaResolver`)

I sistemi di rivelazione e allarme — sia **Rilevazione Incendi (`RI`)** che **Rilevazione Gas (`RIG`)** (entrambi implementano `ImpiantoRilevazione`) — non hanno una lista di attività statica: le prove da eseguire dipendono dagli altri impianti presenti nel cantiere (`contextImpianti`):

1. **Attività di base (`targetImpiantoCod == null`):** Controlli di centrale, firmware, batterie, efficienza alimentazioni, linee di trasmissione e segnalazioni ottico-acustiche. Vengono filtrate unicamente con la regola della frequenza.
2. **Attività contestuali (`targetImpiantoCod != null`):** Attività mirate di verifica interfacce verso altri sistemi:
   - **Per `RI`:** Sgancio per `CAB`, rivelazione lineari per `QMT`, rivelazione calore per `Q`, aspirazione per `PEM`, interfaccia con rivelazione gas `RIG`, ecc.
   - **Per `RIG`:** Rivelatori gas nel locale `GE` (Gruppo Elettrogeno), asservimento ed elettrovalvola di intercettazione combustibile su emergenza `PEM`, interfaccia e trasmissione allarme alla centrale incendi `RI`, sgancio alimentazione quadro locale gas `Q`, asservimento in `CAB` e protezioni di linea `SPD`.
   - L'attività viene inserita nel PDF **solo se** l'impianto con `codIntervento == targetImpiantoCod` è presente nella configurazione del cantiere.
3. **Risoluzione Frequenze Cantiere (`resolveFrequenze`):** Calcola per `RI` e `RIG` solo le frequenze le cui attività appartengono a impianti effettivamente presenti nel cantiere, evitando frequenze vuote nella UI.

---

### 4.3 📑 Fogli Tecnici Allegati OCP (`TechnicalAttachmentProvider`)

Per impianti complessi che richiedono verifiche strumentali puntuali su componenti, il sistema genera un **secondo foglio tecnico allegato (Foglio 2+)** conforme all'Open-Closed Principle:

1. **`QuadroBtAttachmentProvider`:**
   - Gestisce impianti `QuadroBT`.
   - Inietta nella premessa del Foglio 1 il conteggio e l'elenco riassuntivo degli interruttori.
   - Genera il **Foglio 2: Elenco Interruttori Differenziali**, con srotolamento automatico delle quantità maggiori di 1 (suffissi `.1`, `.2`), dati di targa e campi AcroForm per prova del tasto di test e misura del tempo/corrente di scatto.
2. **`EmergenzaAttachmentProvider`:**
   - Gestisce impianti `ImpiantoEmergenza`.
   - Inietta nella premessa del Foglio 1 il sommario delle lampade installate, autonomie dichiarate e posizioni.
   - Genera il **Foglio 2: Registro Lampade di Emergenza (UNI 11222)**, con colonne per ubicazione, autonomia dichiarata, data ultimo cambio batteria e campi compilabili per verifica funzionale e autonomia reale.
3. **`TechnicalSheetHtmlRenderer`:**
   - Costruisce dinamicamente la tabella HTML a larghezza millimetrica (18.4 cm), supportando colonne con sub-colonne, radio button AcroForm e text field univoci.

---

### 4.4 🔍 Integrazione Catalogo ETIM (`EtimCatalogService`)

Il modulo `service/EtimCatalogService.kt` implementa il contratto di dominio `ProductSearchApi` per connettersi all'API internazionale ETIM:
- **Autenticazione OAuth2 automatica:** Scambia `clientId` e `clientSecret` sull'endpoint token e memorizza il Bearer Token con scadenza, protetto da `Mutex` per garantire la thread-safety.
- **Client HTTP Ktor (CIO):** Esegue chiamate asincrone all'endpoint di ricerca classi ETIM.
- **Equivalenze & Approvazione:** Permette di catalogare componenti approvati aziendali (`ComponenteApprovato`), suggerendo varianti commerciali equivalenti in fase di composizione del quadro.

---

### 4.5 🚀 Pipeline di Generazione Batch PDF

```
User clicca "Genera PDF" in CantiereDetailScreen
                       │
                       ▼
┌────────────────────────────────────────────────────────┐
│  ManutenzioniViewModel.generatePdf()                   │
│  - Cicla su tutti gli impianti selezionati nel cantiere│
│  - Legge la frequenza specifica impostata per ciascuno │
│  - Invoca pdfStrategy.generateBatch(copies = 1)        │
└──────────────────────┬─────────────────────────────────┘
                       ▼
┌────────────────────────────────────────────────────────┐
│  AbstractPdfGeneratorStrategy.generateBatch()          │
│  - Gestisce il naming: {COD}_{FREQ}.pdf (o _copia_{i}) │
│  - Notifica callback di progresso alla UI              │
│  - Chiama la specifica implementazione:               │
└──────────────────────┬─────────────────────────────────┘
                       ▼
┌────────────────────────────────────────────────────────┐
│  HtmlToPdfStrategy.generate()                          │
│  1. CrossPlantAttivitaResolver.resolveAttivita()       │
│  2. HtmlService.buildHtml()                            │
│     ├── Inietta Header (Codice, Oggetto, Frequenza)    │
│     ├── Inietta Nome Cliente                           │
│     ├── Genera righe Tabella Attività con AcroForm     │
│     ├── TechnicalAttachmentProvider.buildPremessa...   │
│     └── TechnicalAttachmentProvider.buildSheetData...  │
│  3. File.createTempFile() → Scrive HTML                │
│  4. Pdf.buildPdf()                                     │
│     └── iText7 ConverterProperties(createAcroForm=true)│
│  5. Cleanup file temporaneo in finally                 │
└────────────────────────────────────────────────────────┘
```

---

## 5. 🎨 Vibe & UI Standards

### 5.1 Estetica: Modern Desktop Industrial
- **Palette colori:**
  - Primary: `#3366FF` (Electric Blue) — Bottoni principali, accenti, selezioni attive
  - Primary Variant: `#1A3DB8` — Hover / pressed
  - Background: `#F8FAFC` (Slate Canvas)
  - Surface: `#FFFFFF` con bordi sottili `#E2E8F0`
  - Feedback Successo: Verde `#2E7D32` su sfondo `#E8F5E9`
  - Feedback Errore: Rosso `#D32F2F` su sfondo `#FFEBEE`
- **Tipografia:** Tipografia di sistema pulita, pesi `FontWeight.SemiBold` per i titoli di sezione, `12.sp` per i dettagli e `10.sp` per etichette secondarie.

### 5.2 Struttura di Navigazione Desktop
1. **Top StatusBar:** Fornisce sempre feedback immediato (messaggio operativo, errore bloccante, progress indicator rotante).
2. **NavigationRail (`MainScaffold`):**
   - **Operatività (`AppSection.OPERATIVITA`):** Flusso primario per tecnici di cantiere.
     - `OperativitaScreen`: Selezione cliente committente e cantiere.
     - `CantiereDetailScreen`: Vista espansa con lista impianti, interruttori/lampade inline, selezione frequenze per impianto, generazione e apertura rapida dei PDF generati.
   - **Database (`AppSection.DATABASE`):** Gestione amministrativa globale.
     - `AdminDashboardScreen` con schede per Clienti, Cantieri e Impianti Template Master.
3. **Connection Gateway (`ConnectionScreen`):**
   - Schermata protetta per configurare host, porta, credenziali MongoDB o testare la connettività.

---

## 6. ⚙️ Operational Workflow — Il Processo Multi-Agente `/develop`

Per qualsiasi nuova feature o modifica sostanziale, segui il ciclo vitale orchestrato dallo skill `/develop`:

```
              ┌────────────────────────────────────────┐
              │          /develop (Orchestrator)       │
              └───────────────────┬────────────────────┘
                                  │
         ┌────────────────────────┼────────────────────────┐
         ▼                        ▼                        ▼
 1. 🏛️ model_agent        2. ⚙️ controller_agent      3. 🎨 view_agent
 (Data & Domain Layer)    (Logic, Service, ViewModel) (Compose Desktop UI)
```

### Sequenza Obbligatoria di Intervento:

1. **🏛️ Fase 1: Domain & Model First (`model_agent`)**
   - Definisci il model in `manutenzioni.domain.model.*` (`@Serializable`).
   - Se l'entità è persistita, aggiorna l'interfaccia `ManutenzioneRepository`.
   - Implementa i metodi sia in `MongoManutenzioneRepository` sia in `JsonManutenzioneRepository`.
   - Garantisci retrocompatibilità (default arguments su tutti i campi).

2. **⚙️ Fase 2: Logic & Service (`controller_agent`)**
   - Se l'aggiornamento impatta la generazione PDF, estendi o crea un `TechnicalAttachmentProvider` o aggiorna `HtmlService`.
   - Aggiorna i metodi e i flussi in `ManutenzioniViewModel`.
   - Mantieni l'immutabilità dello `UiState` via `_uiState.update { it.copy(...) }`.

3. **🎨 Fase 3: Presentation (`view_agent`)**
   - Costruisci o aggiorna i Composable in `app.ui.features.*`.
   - Usa solo lo stato proveniente dal ViewModel tramite StateFlow.
   - Non eseguire mai I/O o computazioni pesanti all'interno dei `@Composable`.

4. **✅ Fase 4: Validazione e Test**
   - Esegui i test di unità e di integrazione:
     ```bash
     ./gradlew :desktopApp:compileKotlinDesktop
     ./gradlew :desktopApp:test
     ```

---

## 7. 🚫 Anti-Pattern — Cosa NON Fare MAI

| ❌ VIETATO | ✅ CORRETTO | Motivazione |
|---|---|---|
| Importare `manutenzioni.app.*` dentro il package `domain` | Il domain dipende solo dalla standard library o KMP sharing | Clean Architecture pura |
| Usare logica relazionale / JOIN SQL | NoSQL document embedding (`cantiereId`, embedded lists) | MongoDB è il document store primario |
| Modificare la regola `F.inMesi() % A.inMesi() == 0` | Mantenere la formula intatta | È il fondamento matematico dell'inclusione periodica |
| Hardcodare campi HTML senza AcroForm | Usare `<input type="radio">` e `<input type="text">` univoci | Le schede DEVONO essere compilabili su tablet in cantiere |
| Silenziare eccezioni nella pipeline PDF con `println` | Lanciare eccezioni tipizzate o propagarle a `UiState.errorMessage` | L'utente deve conoscere lo stato esatto della generazione |
| Memorizzare password in chiaro su file | Cifrare le credenziali con AES in `AppConfigRepository` | Sicurezza delle credenziali aziendali del database |
| Mutare collezioni esterne dentro i `@Composable` | Emettere eventi verso il ViewModel (`onEvent(...)`) | Unidirectional Data Flow (UDF) |

---

## 8. 📋 Stato dei Debiti Tecnici & Roadmap

### 8.1 Debiti Tecnici — Stato di Risoluzione

| Debito Originale | Stato | Risoluzione applicata |
|---|---|---|
| Inversione dipendenza domain → data | **RISOLTO (v3.0)** | Tutti i modelli (`Impianto`, `Attivita`, `Cliente`, `Cantiere`) sono migrati nel package `manutenzioni.domain.model.*`. |
| `Html.fillHtml()` dead method | **RISOLTO (v3.1)** | Rimosso dall'interfaccia `Html`. Rimasto solo `buildHtml()` tipizzato. |
| `Pdf.buildPdf()` fallimento silenzioso | **RISOLTO (v3.1)** | Rilancia esplicitamente una `RuntimeException` con causa. |
| `PdfConfig` inutilizzato | **RISOLTO (v3.1)** | Rimosso da `Pdf.kt`. |
| Duplicazione logica Cross-Plant RI/RIG & `normalizeType` | **RISOLTO (v3.1)** | Unificata l'interfaccia `ImpiantoRilevazione`, centralizzata `Impianto.normalizeType()` nel domain model e generalizzato `CrossPlantAttivitaResolver` (OCP, senza alias o deprecati). |
| Thread-safety in `JsonManutenzioneRepository` | 🟡 Monitorato | La persistenza primaria è migrata a MongoDB Coroutine Driver. JSON rimane come fallback/esportazione. |
| Anteprima PDF nativa embedded nella UI | 🟡 Aperto | Attualmente i PDF vengono aperti tramite il visualizzatore di sistema (`Desktop.getDesktop().open(file)`). |

### 8.2 Roadmap Feature

| Milestone | Descrizione | Stato |
|---|---|---|
| **v1.0** | Selezione Impianto, Frequenza, Template HTML e generazione AcroForm iniziale | ✅ Completata |
| **v2.0** | Editor Impianto inline e CRUD attività | ✅ Completata |
| **v2.1** | Gestione Clienti e iniezione automatica cliente nell'header | ✅ Completata |
| **v2.2** | Creazione impianti master da UI | ✅ Completata |
| **v3.0** | **Cantiere-First Architecture:** Introduzione entità Cantiere, separazione template/istanze, gerarchia polimorfica `Impianto`, Cross-Plant Discovery Antincendio (`RI`), backend MongoDB | ✅ Completata |
| **v3.1** | **Fogli Tecnici Allegati OCP:** Secondo foglio per Quadri BT (differenziali) e Impianti Emergenza (lampade UNI 11222), integrazione Ktor con Catalogo ETIM International | ✅ Completata |
| **v3.2** | Viewer PDF embedded nativo nell'area principale (anteprima prima della stampa) | 🔲 Pianificata |
| **v3.3** | Sync Cloud Storage (Google Drive API / S3 per archiviazione automatica schede) | 🔲 Pianificata |
| **v4.0** | Firma Grafometrica digitale integrata e sincronizzazione offline bidirezionale | 🔲 Pianificata |

---

## 9. 🔑 Comandi Essenziali

```bash
# Compilazione desktop
./gradlew :desktopApp:compileKotlinDesktop

# Esecuzione dell'applicazione
./gradlew :desktopApp:run

# Esecuzione di tutta la suite di test
./gradlew :desktopApp:test

# Esecuzione di un test specifico
./gradlew :desktopApp:test --tests "manutenzioni.app.service.attachments.TechnicalAttachmentTest"

# Creazione installer nativo per macOS (.dmg)
./gradlew :desktopApp:packageDmg

# Creazione installer nativo per Windows (.msi)
./gradlew :desktopApp:packageMsi

# Creazione installer nativo per Linux (.deb)
./gradlew :desktopApp:packageDeb
```

---

> **📌 Promemoria per gli Agenti:**
> Consulta sempre questo manuale prima di iniziare un task. Se una modifica tocca il flusso dati o l'architettura, consulta la sezione **6 (Il Processo Multi-Agente)** e mantieni la **Clean Architecture** senza compromessi.
