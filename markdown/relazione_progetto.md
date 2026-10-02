# Relazione di Sviluppo: Manutenzioni Maker (Kotlin Desktop)

## 1. Introduzione e Obiettivi
Il presente documento illustra il lavoro di ingegnerizzazione e sviluppo software svolto per il progetto "Manutenzioni Maker". L'obiettivo del progetto è la digitalizzazione del ciclo di vita delle schede di verifica e manutenzione periodica per impianti elettrici/elettronici, permettendo la generazione di documenti PDF compilabili (AcroForm) a partire da template dinamici.

Il percorso di sviluppo ha visto un'evoluzione significativa da un iniziale prototipo di validazione (Proof of Concept) in Java a un'applicazione Desktop nativa, architetturalmente solida e scalabile, sviluppata in **Kotlin Multiplatform (KMP)** con interfaccia utente in **Compose Desktop**.

---

## 2. Milestone di Sviluppo e Lavoro Svolto

L'analisi dei commit del repository evidenzia un processo di sviluppo iterativo e strutturato. Di seguito le fasi principali del lavoro consegnato:

### Fase 1: Ricerca, Prototipazione e Fattibilità Tecnologica
*   **Proof of Concept (PoC) Generazione PDF**: Sviluppo iniziale di un prototipo (originariamente in Java) per validare la conversione di codice HTML in file PDF AcroForm. Questo step era critico per assicurare che le schede potessero contenere campi interattivi compilabili offline in cantiere.
*   **Definizione del Template Engine**: Creazione della struttura HTML base (`scheletro.html`) per accogliere i placeholder dinamici.

### Fase 2: Ingegnerizzazione Architetturale e Setup KMP
*   **Transizione a Kotlin Multiplatform e Compose**: Ristrutturazione completa del progetto per adottare un framework moderno (KMP). L'applicazione è stata divisa in moduli (`desktopApp` e `common`) per isolare la UI dalla logica di business e preparare il codice a futuri porting (es. Web o Mobile).
*   **Adozione della Clean Architecture (MVVM)**: Il codice è stato rigorosamente suddiviso in layer (Domain, Data, Service, Strategy, UI). Questo riduce il debito tecnico e rende l'applicazione altamente manutenibile.
*   **Build System & Tooling**: Configurazione avanzata di Gradle Kotlin DSL (`build.gradle.kts`, Version Catalogs) per automatizzare la pacchettizzazione nativa degli eseguibili su macOS, Windows e Linux.

### Fase 3: Sviluppo Core Business Logic e Persistenza
*   **Offline-First Data Layer**: Implementazione di un database NoSQL basato su file JSON locale (`manutenzioni_db.json`) utilizzando `kotlinx.serialization`. Questo garantisce il funzionamento dell'app in ambienti privi di connettività, requisito fondamentale per i tecnici.
*   **Motore di Calcolo Frequenze (`FrequencyFilter`)**: Sviluppo dell'algoritmo core che calcola l'inclusione delle attività in base alla periodicità selezionata (es. una manutenzione annuale include automaticamente i controlli semestrali e mensili). 

### Fase 4: Sviluppo Interfaccia Utente (Compose Desktop) e Integrazione
*   **Sviluppo UI Reattiva (Unidirectional Data Flow)**: Realizzazione del `ManutenzioniViewModel` e dei componenti visivi (`Sidebar`, `MainContent`, `ImpiantoEditor`).
*   **Editor Universale**: Creazione di un'interfaccia complessa per la creazione e modifica degli impianti, comprensiva di CRUD inline per le singole attività e gestione validazione campi.
*   **Gestione Clienti (Decoupled)**: Implementazione del sistema di selezione e creazione clienti, intenzionalmente disaccoppiato dal modello dati dell'impianto per massimizzare la flessibilità di utilizzo.
*   **Pipeline Strategy PDF (`HtmlToPdfStrategy`)**: Chiusura del ciclo con l'integrazione del motore iText7. Il sistema inietta dinamicamente i dati dell'impianto, del cliente e le righe della tabella generata nel PDF finale, rendendolo pronto per la firma.

---

## 3. Valore Aggiunto dell'Ingegnerizzazione

Il software sviluppato non è un semplice script, ma un **prodotto enterprise-grade** che offre le seguenti garanzie:
1.  **Affidabilità Offline**: L'ingegnere tecnico non dipende dal cloud. Il JSON database integrato è veloce e portabile.
2.  **Scalabilità**: L'architettura a layer permette, ad esempio, di sostituire facilmente il salvataggio JSON con un database Cloud (es. Realm, MongoDB) senza toccare l'interfaccia grafica.
3.  **UI/UX Premium**: L'utilizzo di Compose Desktop offre un'esperienza utente nativa, fluida e priva di refresh, con feedback in tempo reale tramite StatusBar e componenti reattivi.
4.  **Sicurezza dei Dati (Type-Safety)**: L'uso intensivo di `sealed class` ed `enum` (es. `TipoPeriodo`) azzera la possibilità di inserire dati corrotti nel sistema.

---

## 4. Stima e Valorizzazione del Progetto

Per giustificare economicamente il lavoro al cliente, è utile presentare una scomposizione dello sforzo in base ai moduli architetturali realizzati. La stima seguente riflette le tariffe medie di mercato per figure **Senior/Solution Architect** nel contesto europeo/italiano (rate giornaliero: €500 - €700 / 60-90 €/ora).

| Area di Sviluppo | Descrizione delle Attività | Effort Stimato (Giorni) |
| :--- | :--- | :---: |
| **Data Ingestion & Processing** | Lettura, interpretazione e organizzazione strutturata dei dati forniti da file Excel (>60 fogli). | 3 |
| **Architettura & Setup CI/CD** | Progettazione Clean Architecture, Setup Kotlin Multiplatform, Gradle Build Scripts, Packaging macOS/Windows/Linux. | 3 |
| **Data Layer & Offline DB** | Implementazione persistenza JSON, `ManutenzioneRepository`, Model Serialization, sistema di caching. | 2 |
| **Logica di Business (Domain)** | Algoritmo frequenze inclusive, interfacce, Strategy Pattern per l'orchestrazione. | 2 |
| **Integrazione PDF & Template HTML** | Motore HTML dinamico, setup iText7, generazione moduli AcroForm complessi. | 3 |
| **Interfaccia Utente (Compose)** | Sidebar, Editor Impianti, Gestione Stato ViewModel (MVVM), Dialog reattivi, Validazione form. | 5 |
| **Testing, Refactoring & QA** | Stabilizzazione codice, code review, validazione cicli architetturali (es. passaggio Java->Kotlin). | 2 |
| **TOTALE** | | **20 Giorni** |

### Proposta di Valorizzazione Commerciale

In base all'effort tecnico di **circa 130-140 ore di sviluppo specializzato**, il valore reale della codebase attuale si posiziona in un range compreso tra:

**€ 8.500,00 - € 12.000,00 (+ IVA)**

*Nota per la negoziazione: Questo prezzo include non solo le funzionalità visibili, ma l'altissimo livello qualitativo "sotto il cofano" (Clean Architecture, Compose Desktop, Multiplatform), che garantisce al cliente una manutenzione quasi a costo zero e una facilità di espansione futura senza precedenti. È consigliabile presentare il lavoro enfatizzando la durabilità e la flessibilità del software prodotto.*
