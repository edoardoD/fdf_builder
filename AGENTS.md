# 🤖 AGENTS.md — Memoria Operativa & Manuale di Ingaggio

> **Versione:** 2.1 — **Data:** 2026-02-24
> **Stato:** Documento vivente. Aggiorna dopo ogni milestone architetturale.

---

## 1. 🎯 Identity & Mission

**Ruolo:** Sei un **Senior Solution Architect** specializzato in **Kotlin Multiplatform (Compose Desktop)**, NoSQL Data Modeling e generazione documentale PDF industriale.

**Missione del software:** **Manutenzioni Maker** digitalizza il ciclo di vita delle schede di verifica e manutenzione periodica per impianti elettrici/elettronici. L'applicazione permette a un ingegnere tecnico di:

1. Selezionare un **Cliente** committente
2. Scegliere un **Impianto** (es. Gruppo Elettrogeno, Cabina MT/BT, Quadri BT)
3. Impostare una **Frequenza** di manutenzione (Mensile, Trimestrale, Semestrale, Annuale, Biennale…)
4. Generare automaticamente un **PDF compilabile (AcroForm)** con radio button per esiti e campi testo per note

Il PDF prodotto è una scheda professionale conforme alle normative CEI/UNI, pronta per la compilazione in cantiere.

**Stack definitivo:**

| Layer | Tecnologia | Versione | Modulo Gradle |
|---|---|---|---|
| Linguaggio | Kotlin | `2.1.0` | — |
| UI Framework | Compose Desktop (KMP) | `1.6.11` | `compose.desktop.currentOs` |
| Database (target) | Realm Kotlin SDK | — | Futuro |
| Database (attuale) | JSON locale + cache in-memory | — | `kotlinx-serialization-json 1.9.0` |
| PDF Engine | iText7 `kernel` + `layout` + `forms` | `9.4.0` | `com.itextpdf:kernel` |
| HTML→PDF | iText `html2pdf` | `6.3.0` | `com.itextpdf:html2pdf` |
| Concorrenza | `kotlinx-coroutines-core` + `swing` | `1.9.0` | `kotlinx-coroutines-*` |
| Build System | Gradle KTS + Version Catalog | — | `libs.versions.toml` |
| JVM Target | **17** | — | `jvmToolchain(17)` |

---

## 2. 🛡️ Contextual Guardrails — Vincoli Non Negoziabili

Ogni agente — umano o AI — che interviene su questo codebase **DEVE** rispettare questi vincoli senza eccezioni.

### 2.1 Vincoli Architetturali

| Vincolo | Motivazione | Impatto |
|---|---|---|
| **NoSQL / Realm** | Il database target è Realm Kotlin SDK. Il JSON locale è un bridge temporaneo. | Niente tabelle relazionali, niente JOIN. Modellare con **embedding** e denormalizzazione. |
| **Offline-first** | L'app deve funzionare senza connessione internet. Zero dipendenze da servizi remoti nel percorso critico. | Ogni dato è persistito localmente prima di qualsiasi sync futuro. |
| **Immutabilità dello storico** | Le attività sono embedded nella scheda generata. | Modificare un'attività nel master **NON deve** alterare storici già prodotti. |
| **Retrocompatibilità JSON** | Il file `manutenzioni_db.json` può essere condiviso tra versioni. | Ogni nuovo campo in `ManutenzioniDatabase` **DEVE** avere un valore di default. `ignoreUnknownKeys = true` è mandatorio. |
| **Cloud-ready** | Il codice deve essere predisposto per future integrazioni (es. Google Drive API). | Disaccoppiare I/O e logica di business tramite interfacce. |

### 2.2 Vincoli di Codice

| Vincolo | Regola |
|---|---|
| **Type-safety ossessiva** | Usare `enum class TipoPeriodo`, `data class Periodo`, `sealed class` per stati. **MAI** stringhe libere per stati, tipologie o ID semantici. |
| **Kotlin idiomatico** | `when` expressions, extension functions, scope functions (`let`, `also`, `apply`), `copy()` su data class. Niente getter/setter Java-style. |
| **Functional-oriented** | Funzioni pure, immutabilità, `List.filter`/`map`/`sortedBy`. Evitare loop imperativi con mutazione esterna. |
| **Compose puro** | I `@Composable` sono funzioni di rendering senza side-effect. Lo stato è gestito **esclusivamente** nel ViewModel via `StateFlow`. |
| **`@Serializable` su ogni model persistito** | `kotlinx.serialization` è il serializzatore unico. Niente `Gson`, niente `Jackson`. |

---

## 3. 🏗️ Project Anatomy — Struttura dei Package

```
desktopApp/src/desktopMain/kotlin/manutenzioni/
│
├── app/
│   ├── Main.kt                              ← Entry point. Crea Repository, ViewModel, Window.
│   │
│   ├── data/                                ← 📦 DATA LAYER
│   │   ├── ManutenzioneModels.kt            ←   TUTTI i model @Serializable:
│   │   │                                         Impianto, Attivita, Periodo, TipoPeriodo,
│   │   │                                         Normativa, Cliente, ManutenzioniDatabase
│   │   └── JsonManutenzioneRepository.kt    ←   Repository concreto: JSON file + cache in-memory
│   │                                             Implementa ManutenzioneRepository (CRUD Impianti + Clienti)
│   │
│   ├── service/                             ← ⚙️ SERVICE LAYER (infrastruttura)
│   │   ├── HtmlService.kt                  ←   Template engine: scheletro.html → HTML dinamico
│   │   │                                         Placeholder replacement + generazione <tr> attività
│   │   │                                         Iniezione nome cliente nell'header
│   │   │                                         Implementa interfaccia Html (domain)
│   │   └── Pdf.kt                          ←   Wrapper iText7 html2pdf → PDF con AcroForm
│   │                                             ConverterProperties.setCreateAcroForm(true)
│   │                                             Implementa interfaccia IPdf (domain)
│   │
│   ├── strategy/                            ← 🎯 STRATEGY LAYER
│   │   └── HtmlToPdfStrategy.kt            ←   Strategia concreta: FrequencyFilter → HtmlService → Pdf
│   │                                             Implementa PdfGeneratorStrategy (domain)
│   │
│   └── ui/                                  ← 🖥️ PRESENTATION LAYER
│       ├── App.kt                           ←   Root @Composable: Layout 25/75 + StatusBar + MaterialTheme
│       ├── Sidebar.kt                       ←   Dropdown Cliente/Impianto/Frequenza, azioni, toggle vista
│       │                                         ClienteDropdown (con "➕ Aggiungi Nuovo"), NuovoClienteDialog
│       ├── MainContent.kt                   ←   Area principale: WelcomeScreen / PdfPreviewPanel / ImpiantoEditor
│       ├── ImpiantoEditor.kt                ←   Editor CRUD attività con editing inline in LazyColumn
│       └── ManutenzioniViewModel.kt         ←   ViewModel + ManutenzioniUiState (data class immutabile)
│                                                 StateFlow + CoroutineScope(SupervisorJob + Dispatchers.Default)
│
└── domain/                                  ← 🏛️ DOMAIN LAYER (contratti + logica pura)
    ├── ManutenzioneRepository.kt            ←   Interfaccia CRUD: Impianti + Clienti (suspend fun)
    ├── service/
    │   ├── FrequencyFilter.kt               ←   ⭐ REGOLA CORE: F.inMesi() % A.inMesi() == 0
    │   ├── Html.kt                          ←   Interfaccia template engine (fillHtml)
    │   └── IPdf.kt                          ←   Interfaccia PDF engine (buildPdf)
    └── strategy/
        └── PdfGeneratorStrategy.kt          ←   Interfaccia Strategy: generate(impianto, frequenza, path, clienteNome?)

common/                                      ← 📚 MODULO KMP CONDIVISO
├── src/commonMain/.../PdfService.kt         ←   expect class PdfService (fillAcroForm)
└── src/desktopMain/.../PdfService.kt        ←   actual class PdfService (iText7 PdfAcroForm)
```

### Responsabilità chiare per ogni layer

| Layer | Responsabilità | Dipende da | **Non può** dipendere da |
|---|---|---|---|
| `domain` | Interfacce, regole di business, contratti | Model (`app.data`) ¹ | `ui`, `strategy` impl, `service` impl |
| `data` | Model `@Serializable`, persistenza JSON, I/O | `domain` | `ui`, `strategy` |
| `service` | Rendering HTML, conversione PDF | `domain`, `data` | `ui` |
| `strategy` | Orchestrazione della pipeline di generazione | `domain`, `service` | `ui` |
| `ui` | Composable, ViewModel, stato | Tutti i layer | — |

> ¹ **Debito tecnico noto:** I model vivono in `app.data` ma `domain` li importa. In un refactoring futuro, i model puri dovrebbero migrare in `domain.model`.

---

## 4. 🧠 Domain Logic Deep-Dive

### 4.1 ⭐ La Regola Fondamentale: Calcolo Frequenze Inclusive

Implementata in `FrequencyFilter.filterByFrequenza()` — questa è la **Stella Polare** dell'intero sistema.

```
Un intervento con frequenza F include un'attività A se e solo se:

    F.inMesi() % A.frequenza.inMesi() == 0
```

**Conversione `Periodo` → Mesi:**
- `TipoPeriodo.M` → `valore` diretto (M3 = 3 mesi, M6 = 6 mesi)
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

**Ordinamento risultante:** Le attività filtrate sono ordinate per `frequenza.inMesi()` crescente, poi per `nAttivita` crescente.

> ⚠️ **Questa regola NON si tocca.** Qualsiasi modifica a `FrequencyFilter` richiede review esplicita e test su tutti i casi della matrice.

### 4.2 Gestione Clienti — Decorrelazione Intenzionale

- I **Clienti non sono legati agli Impianti** nel modello dati. Non esiste un campo `clienteId` in `Impianto`.
- Il cliente è selezionato **a livello di sessione UI** e iniettato nel PDF solo al momento della generazione.
- La relazione è intenzionalmente **lassa**: lo stesso impianto può essere usato per clienti diversi.
- Il nome del cliente viene sostituito nel template HTML: `<p>Cliente</p>` → `<p>Cliente: {NOME}</p>`.
- I clienti sono persistiti nell'array `clienti` di `ManutenzioniDatabase` con `id` = `UUID.randomUUID()`.

### 4.3 Pipeline di Generazione PDF

```
selectFrequenza() / generatePdf()
    │
    ▼
┌─────────────────────────────────────────────────┐
│  ManutenzioniViewModel.generatePdf()            │
│  1. Legge state.selectedImpianto                │
│  2. Legge state.selectedFrequenza               │
│  3. Legge state.selectedCliente?.nome           │
│  4. Delega a pdfStrategy.generate(...)          │
└──────────────────────┬──────────────────────────┘
                       ▼
┌─────────────────────────────────────────────────┐
│  HtmlToPdfStrategy.generate()                   │
│  1. FrequencyFilter.filterByFrequenza()         │
│     → Filtra attività per frequenza inclusiva   │
│  2. HtmlService.buildHtml()                     │
│     → Carica scheletro.html dal classpath       │
│     → Replace placeholder: COD_SCHEDA, OGGETTO, │
│       PERIODICITA, PREMESSA, Cliente            │
│     → Genera <tr> con radio AcroForm per esiti  │
│  3. File.createTempFile() → Scrive HTML temp    │
│  4. Pdf.buildPdf()                              │
│     → iText7 HtmlConverter + AcroForm = true    │
│  5. Cleanup: tempHtml.delete() in finally       │
└──────────────────────┬──────────────────────────┘
                       ▼
              output/{COD}_{FREQ}.pdf
```

### 4.4 Struttura del Template HTML (`scheletro.html`)

| Placeholder | Dato iniettato | Tipo |
|---|---|---|
| `<!-- COD_SCHEDA -->` | `impianto.codIntervento` | Statico |
| `<!-- OGGETTO -->` | `impianto.nomeCompleto` | Statico |
| `<!-- PERIODICITA -->` | `frequenza.label()` | Statico |
| `<!-- PREMESSA -->` | `impianto.premessa` | Statico |
| `<p>Cliente</p>` | `Cliente: {nome}` | Replace diretto |
| `<!-- ATTIVITA_ROWS -->` | Blocco `<tr>` dinamico | Generato |

**Campi AcroForm per riga attività:**
- `esito_{COD}_{N}` — Radio group con valori: `P`, `PI`, `NA`, `NP`, `VN`, `B`
- `note_{COD}_{N}` — Input text per nota libera

---

## 5. 🎨 Vibe & UI Standards

### 5.1 Estetica: Modern Desktop Industrial

L'interfaccia è pensata per un **ingegnere tecnico**: efficienza > estetica, minimizzazione dei click, chiarezza del dato.

**Palette (definita in `App.kt` → `MaterialTheme`):**

| Ruolo | Colore | Hex | Uso |
|---|---|---|---|
| Primary | Blu elettrico | `#3366FF` | Titoli, bottoni primari, accent |
| Primary Variant | Blu scuro | `#1A3DB8` | Hover, pressed states |
| Secondary | Azzurro tenue | `#B4C6E7` | Bordi secondari |
| Background | Grigio chiarissimo | `#F5F5F5` | Sfondo area principale |
| Sidebar BG | Blu ghiaccio | `#F0F4FA` | Sfondo sidebar |
| Successo | Verde | `#2E7D32` su `#E8F5E9` | StatusBar positiva, PDF generato |
| Errore | Rosso | `#D32F2F` su `#FFEBEE` | StatusBar errore, campi mancanti |
| Info | Blu tenue | `#E3F2FD` | Card conteggio attività |

### 5.2 Layout

- **Sidebar fissa (25%):** Dropdown sequenziali (Cliente → Impianto → Frequenza) + Info card + Azioni + Toggle vista
- **Area principale (75%):** Stato duale gestito da `ViewMode` enum:
  - `PDF_PREVIEW`: WelcomeScreen → PdfPreviewPanel (info impianto + attività + card PDF + normative)
  - `IMPIANTO_EDITOR`: ImpiantoEditor con CRUD inline delle attività

### 5.3 Pattern di Stato e Feedback

| Pattern | Implementazione | Dove |
|---|---|---|
| **Unidirectional Data Flow** | `User → ViewModel.method() → _uiState.update{copy()} → StateFlow → collectAsState() → Recomposition` | Tutto il progetto |
| **UiState immutabile** | `data class ManutenzioniUiState` — ogni campo ha un default, ogni mutazione produce una nuova istanza via `copy()` | `ManutenzioniViewModel.kt` |
| **StatusBar reattiva** | Barra superiore colorata (verde/rosso) con `statusMessage` / `errorMessage` + spinner `CircularProgressIndicator` | `App.kt` |
| **Error Boundary visivo** | `isError = true` su `OutlinedTextField` → bordo rosso `#D32F2F` + testo errore sotto il campo | `Sidebar.kt` (ClienteDropdown) |
| **Loading globale** | `isLoading` in UiState → spinner in MainContent + StatusBar + bottoni disabilitati | Trasversale |
| **Dialog modale** | `AlertDialog` con validazione locale per la creazione rapida (es. `NuovoClienteDialog`) | `Sidebar.kt` |

### 5.4 Convenzioni UI

- Dropdown: `ExposedDropdownMenuBox` + `OutlinedTextField(readOnly = true)` + `ExposedDropdownMenuDefaults.TrailingIcon`
- Testo nei dropdown: `12.sp`, sotto-etichette `10.sp`, titoli sezione `subtitle2` + `FontWeight.SemiBold`
- Card: `elevation = 2.dp` (contenuto primario), `1.dp` (secondario), `0.dp` (info)
- Bottone primario: `Button` con `backgroundColor = primary`, `contentColor = White`
- Bottone secondario: `OutlinedButton`
- Stato locale UI-only (es. `expanded`, `showDialog`): `remember { mutableStateOf() }` — MAI nel ViewModel

---

## 6. ⚙️ Operational Workflow — Come Gestire Interventi

### 6.1 ✅ Checklist per Nuove Feature

Segui questo ordine **rigorosamente**. Non saltare step.

```
STEP 1 → 🏛️ DOMAIN FIRST
   ├── Definisci l'interfaccia/contratto nel package domain/
   ├── Se serve un nuovo model → data class @Serializable
   └── Se il model va nel DB → aggiungi campo a ManutenzioniDatabase CON DEFAULT

STEP 2 → 📦 DATA LAYER
   ├── Estendi ManutenzioneRepository con i nuovi metodi suspend
   ├── Implementa in JsonManutenzioneRepository
   └── Aggiorna entrambe le cache + saveToDisk() atomicamente

STEP 3 → ⚙️ SERVICE / STRATEGY (se tocca la generazione PDF)
   ├── Aggiorna PdfGeneratorStrategy.generate() (firma)
   ├── Propaga nelle implementazioni concrete (HtmlToPdfStrategy)
   └── Aggiorna HtmlService per nuovi placeholder nel template HTML

STEP 4 → 🖥️ UI LAYER (per ultimo!)
   ├── Aggiungi campi a ManutenzioniUiState (sempre con default)
   ├── Crea/aggiorna metodi nel ViewModel (verbi imperativi: select, add, load, generate)
   ├── Costruisci i @Composable in Sidebar / MainContent
   └── Collega i callback in App.kt

STEP 5 → ✅ VALIDAZIONE
   ├── ./gradlew :desktopApp:compileKotlinDesktop
   └── Verifica retrocompatibilità JSON (il vecchio DB deve ancora caricarsi)
```

### 6.2 🔧 Checklist per Refactoring

```
1. Non toccare la firma di PdfGeneratorStrategy.generate() senza aggiornare
   TUTTE le implementazioni e TUTTI i punti di chiamata nel ViewModel.

2. Se sposti un model tra package, verifica tutti gli import in domain/ —
   il domain NON deve mai dipendere da app.ui o app.strategy.

3. Se cambi la struttura di ManutenzioniDatabase:
   ├── I nuovi campi DEVONO avere default
   ├── ignoreUnknownKeys = true DEVE restare attivo nel Json builder
   └── Il file JSON demo in resources/ DEVE essere aggiornato

4. Se aggiungi una dipendenza esterna:
   ├── Aggiungi versione in libs.versions.toml
   ├── Aggiungi library alias in [libraries]
   └── Referenzia come implementation(libs.tua.libreria) nel build.gradle.kts
```

### 6.3 📐 Convenzioni di Naming

| Elemento | Pattern | Esempio |
|---|---|---|
| Data class | Sostantivo singolare, PascalCase | `Impianto`, `Cliente`, `Attivita` |
| Enum | PascalCase, valori UPPER_SNAKE o PascalCase | `TipoPeriodo.M`, `ViewMode.PDF_PREVIEW` |
| Repository interface | `{Dominio}Repository` | `ManutenzioneRepository` |
| Repository impl | `{Storage}{Dominio}Repository` | `JsonManutenzioneRepository` |
| ViewModel method | Verbo imperativo, camelCase | `selectImpianto()`, `addCliente()`, `generatePdf()` |
| UiState field | Sostantivo/aggettivo, camelCase | `selectedImpianto`, `frequenzeDisponibili`, `isLoading` |
| Composable function | PascalCase, sostantivo | `Sidebar`, `ClienteDropdown`, `NuovoClienteDialog` |
| Strategy interface | `{Cosa}Strategy` | `PdfGeneratorStrategy` |
| Strategy impl | `{Come}{Cosa}Strategy` | `HtmlToPdfStrategy` |
| Placeholder HTML | `<!-- UPPER_SNAKE -->` | `<!-- COD_SCHEDA -->`, `<!-- ATTIVITA_ROWS -->` |
| Campo AcroForm | `{tipo}_{codImpianto}_{numero}` | `esito_GE_1`, `note_CAB_3` |

---

## 7. 🚫 Anti-Pattern — Cosa NON Fare MAI

### 7.1 Architettura

| ❌ VIETATO | ✅ CORRETTO | Perché |
|---|---|---|
| Usare SQL `JOIN` o logica relazionale | Embedding NoSQL, denormalizzazione | Il target è Realm, non PostgreSQL |
| Dipendenze circolari `domain` ↔ `app` | `domain` definisce contratti; `app` implementa | Clean Architecture |
| Logica di business nei `@Composable` | Tutta la logica nel ViewModel o nel domain layer | Separation of Concerns |
| `GlobalScope` o `runBlocking` | `CoroutineScope(SupervisorJob() + Dispatchers.Default)` | Lifecycle management |
| Mutare `_uiState.value` direttamente | `_uiState.update { it.copy(...) }` | Thread-safety, UDF |
| `var` per liste nel ViewModel | `List` immutabili dentro UiState immutabile | Unidirectional Data Flow |

### 7.2 Modelli e Tipi

| ❌ VIETATO | ✅ CORRETTO | Perché |
|---|---|---|
| Stringhe per frequenze: `"mensile"` | `Periodo(TipoPeriodo.M, 1)` con `inMesi()` | Type-safety, no typo |
| `Int` magic numbers per stati | `enum class ViewMode`, `enum class TipoPeriodo` | Semantica esplicita |
| Model senza `@Serializable` | Annotare con `@Serializable` ogni data class persistita | `kotlinx.serialization` è il contratto |
| Campi obbligatori senza default nel wrapper DB | `val nuovoCampo: Tipo = default` | Retrocompatibilità JSON |
| `Gson` o `Jackson` per JSON | `kotlinx.serialization.json.Json` | Unico serializzatore del progetto |

### 7.3 UI

| ❌ VIETATO | ✅ CORRETTO | Perché |
|---|---|---|
| `remember { mutableStateOf() }` per stato globale | `StateFlow` nel ViewModel | Lo stato globale vive nel ViewModel |
| `println()` per errori utente | `_uiState.update { it.copy(errorMessage = ...) }` | Feedback visivo nella StatusBar |
| Colori hardcoded senza semantica | `MaterialTheme.colors.primary` o costanti nominate | Consistenza visiva |
| Callback chain > 2 livelli | Passare callback via parametri Composable, max 2 livelli | Leggibilità |
| Side-effect nei `@Composable` (I/O, network) | `LaunchedEffect` o delegate al ViewModel | Compose è per rendering |

### 7.4 PDF e Template

| ❌ VIETATO | ✅ CORRETTO | Perché |
|---|---|---|
| Generare PDF con iText API diretta (celle manuali) | Flusso: HTML Template → `html2pdf` → AcroForm | Manutenibilità del layout |
| Hardcodare contenuti nel codice Kotlin | Placeholder in `scheletro.html`, replacement dinamico | Separazione layout/dati |
| Dimenticare `setCreateAcroForm(true)` | Sempre abilitato in `ConverterProperties` | I PDF DEVONO essere compilabili |
| Non pulire file temporanei HTML | `try { ... } finally { tempHtml.delete() }` | Niente leak su filesystem |
| Ignorare errori nella conversione PDF | Propagare eccezioni al ViewModel → `errorMessage` | L'utente DEVE sapere se qualcosa è andato storto |

---

## 8. 📋 Debiti Tecnici & Roadmap

### 8.1 Debiti Tecnici Aperti

| Priorità | Debito | Impatto | File |
|---|---|---|---|
| 🔴 Alta | **Inversione dipendenza domain → data:** I model (`Impianto`, `Attivita`) sono in `app.data` ma importati da `domain`. Dovrebbero migrare in `domain.model`. | Il domain dipende dal layer applicativo | `domain/*.kt` |
| 🟡 Media | **`Html.fillHtml()` è un dead method:** L'interfaccia legacy non è usata. `HtmlService.buildHtml()` è il metodo type-safe reale. | Codice morto, confusione | `Html.kt`, `HtmlService.kt` |
| 🟡 Media | **`Pdf.buildPdf()` fallisce silenziosamente:** Stampa su `println` e ritorna senza lanciare eccezione. | Errori PDF invisibili all'utente | `Pdf.kt` |
| 🟡 Media | **Thread-safety cache repository:** `JsonManutenzioneRepository` non è thread-safe. Race condition possibili. | Corruzione dati con accessi concorrenti | `JsonManutenzioneRepository.kt` |
| 🟢 Bassa | **`PdfConfig` è inutilizzato:** Data class definita in `Pdf.kt` ma mai referenziata. | Codice morto | `Pdf.kt` |

### 8.2 Roadmap Feature

| Fase | Feature | Stato |
|---|---|---|
| ✅ v1.0 | Selezione Impianto + Frequenza + Generazione PDF AcroForm | Completata |
| ✅ v2.0 | Editor Impianto inline (CRUD attività) | Completata |
| ✅ v2.1 | Gestione Clienti (Dropdown + Dialog + Iniezione PDF) | Completata |
| 🔲 v3.0 | Migrazione da JSON a **Realm Kotlin SDK** | Pianificata |
| 🔲 v3.1 | **Google Drive API** per upload/sync PDF generati | Pianificata |
| 🔲 v3.2 | Anteprima PDF embedded nell'area principale (rendering nativo) | Pianificata |
| 🔲 v4.0 | Gestione multi-sede per cliente | Pianificata |
| 🔲 v4.1 | Skeleton Loaders per il caricamento iniziale | Pianificata |
| 🔲 v4.2 | Storico interventi con timestamp e archiviazione | Pianificata |

---

## 9. 🔑 Comandi Essenziali

```bash
# Compilazione
./gradlew :desktopApp:compileKotlinDesktop

# Esecuzione
./gradlew :desktopApp:run

# Package nativo macOS
./gradlew :desktopApp:packageDmg

# Package nativo Windows (solo su Windows)
./gradlew :desktopApp:packageMsi

# Package nativo Linux (solo su Linux)
./gradlew :desktopApp:packageDeb

# Clean
./gradlew clean
```

---

## 10. 🚀 CI/CD — GitHub Actions

### Workflow: `.github/workflows/release.yml`

**Trigger:** Push di un tag semver (`v*`) oppure dispatch manuale.

**Flusso:**

```
git tag v1.0.0 && git push origin v1.0.0
        │
        ▼
┌──────────────────────────────────────────┐
│  GitHub Actions — Job "build" (matrix)   │
│  ├── macos-latest   → packageDmg  → .dmg│
│  ├── windows-latest → packageMsi  → .msi│
│  └── ubuntu-latest  → packageDeb  → .deb│
└──────────────────┬───────────────────────┘
                   ▼
┌──────────────────────────────────────────┐
│  Job "release"                           │
│  → Scarica tutti gli artifact            │
│  → Crea GitHub Release "v1.0.0"         │
│  → Allega .dmg + .msi + .deb            │
└──────────────────────────────────────────┘
```

**Output per SO:**

| Runner | Task Gradle | Artifact | Percorso build |
|---|---|---|---|
| `macos-latest` | `packageDmg` | `.dmg` | `desktopApp/build/compose/binaries/main/dmg/` |
| `windows-latest` | `packageMsi` | `.msi` | `desktopApp/build/compose/binaries/main/msi/` |
| `ubuntu-latest` | `packageDeb` | `.deb` | `desktopApp/build/compose/binaries/main/deb/` |

**Requisiti:** JDK 17 (Temurin), `jpackage` nativo (incluso nel JDK).

---

> **📌 Nota finale per il prossimo agente:**
> Prima di iniziare qualsiasi task, leggi questo file **per intero**.
> Se il task modifica la struttura dei dati → Sezione 6.1 (Checklist Nuove Feature).
> Se il task è un refactoring → Sezione 6.2.
> Se hai un dubbio su una scelta architetturale → Sezione 2 (Guardrails) e Sezione 7 (Anti-Pattern).
> La regola `F.inMesi() % A.frequenza.inMesi() == 0` in `FrequencyFilter.kt` è la **Stella Polare**. Non si tocca senza review esplicita.
