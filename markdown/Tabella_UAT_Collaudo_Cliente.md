# 📋 Scheda di Collaudo e Accettazione Utente (UAT)

> **Progetto:** Piattaforma Gestionale Manutenzioni Impianti (*Manutenzioni Maker*)  
> **Oggetto:** Matrice UAT per la Validazione e il Rilascio Formale della Release 4.0  
> **Versione Software in Collaudo:** `v4.0.0` (Stato di Avanzamento: Wave 1, Wave 2, Wave 3, Wave 4)  
> **Data di Emissione:** 30 Ottobre 2026  
> **Ambiente Operativo:** Postazioni Windows Ufficio Tecnico / Server LAN MongoDB (con supporto Offline)  
> **Destinatari:** Responsabile Ufficio Tecnico, Ingegneri di Cantiere, Tecnici Manutentori Verificatori  

---

### 📊 Cruscotto di Sintesi del Collaudo

| Metrica di Progetto | Valore / Dettaglio | Note di Sintesi |
|:---|:---:|:---|
| **Casi di Test UAT Totali** | **27 Scenari** | Copertura end-to-end su interfaccia desktop e schede PDF |
| **Onde di Sviluppo (Waves)** | **4 Wave Completate** | Wave 1 (Anagrafica), Wave 2 (Componenti), Wave 3 (PDF Batch), Wave 4 (Calendario/RBAC) |
| **Copertura Requisiti Cliente** | **100% (R1 – R32)** | Tutti i feedback operativi e normativi CEI/UNI integrati |
| **Conformità Documentale** | **AcroForm PDF Compilabili** | Schede base + Fogli 2 allegati (Differenziali, Lampade UNI 11222, Rilevazione UNI 11224) |

---

### 🎯 Istruzioni Operative per il Collaudatore

Per ciascun caso di test, eseguire i passaggi indicati nella colonna **Procedura Operativa** e verificare la corrispondenza con il **Risultato Atteso**.

Spuntare l'esito riscontrato:
* **`[ ] 🟢 PASS`**: Funzionalità pienamente conforme al risultato atteso.
* **`[ ] 🟡 COND`**: Funzionalità operativa, ma con riserva o piccolo rilievo non bloccante (da annotare nella colonna Note).
* **`[ ] 🔴 FAIL`**: Errore di esecuzione, anomalia bloccante o comportamento non conforme al requisito.

---

## 🌊 WAVE 1 — Quick-Wins, Anagrafica Master & Usabilità

> **Obiettivo:** Ottimizzare l'anagrafica di clienti e cantieri, velocizzare l'inserimento multiplo delle attività e allineare la terminologia dell'interfaccia alle consuetudini dei tecnici.

| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W1-01` | **R1** | **Amministrazione**<br>*(Master Schede)* | Accedere alla sezione **Database / Amministrazione** dal menu laterale. | Il tab precedentemente denominato "IMPIANTI LOCALI" ora recita chiaramente **"MASTER SCHEDE DI VERIFICA"**. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-02` | **R2** | **Admin -> Clienti**<br>*(Anagrafica)* | Nel tab Clienti, cliccare sull'icona matita *(Modifica)* su un cliente esistente. | Il dialog permette l'editing completo di **Ragione Sociale**, **Indirizzo** e **Partita I.V.A.**; i dati salvati persistono correttamente sul DB. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-03` | **R3** | **Admin -> Cantieri**<br>*(Anagrafica)* | Creare un nuovo cantiere o modificare un cantiere esistente. | È presente e attivo il campo **"Indirizzo del Cantiere"**; il valore viene salvato e visualizzato sia in griglia che nel dettaglio. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-04` | **R4** | **Dashboard Operativa**<br>*(Selezione Cliente)* | Nella vista principale di selezione cliente, osservare il pulsante operativo. | Il pulsante riporta il testo **"GESTIONE VERIFICHE PERIODICHE"** (invece del generico "Gestisci Impianti"). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-05` | **R5** | **Dashboard Cantiere**<br>*(Dettaglio Moduli)* | Accedere al dettaglio di un cantiere e visualizzare il pulsante in alto a destra. | Il pulsante di azione recita chiaramente **"AGGIUNGI ATTIVITA DI VERIFICA"** (invece di "Aggiungi Impianti"). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-06` | **R6** | **NuovoImpiantoDialog**<br>*(Selezione Multipla)* | Cliccare su *"AGGIUNGI ATTIVITA DI VERIFICA"* e spuntare più caselle (es. `Q`, `EM`, `RI`). | Testi e codici allineati a sinistra. Con un unico click di conferma, tutti gli impianti spuntati vengono associati simultaneamente al cantiere. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W1-07` | **R12** | **Dashboard Cantiere**<br>*(Tab Navigazione)* | Osservare le schede orizzontali interne alla dashboard di cantiere. | Il tab per l'esportazione documenti è rinominato **"Generazione schede di Verifica"** (invece di "Generazione PDF"). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

---

<div style="page-break-before: always;"></div>

## ⚙️ WAVE 2 — Componenti Specialistici, Quadri BT, Emergenza, QMT & Antincendio

> **Obiettivo:** Gestione del ciclo di vita dei componenti tecnici con interfaccia standardizzata (`TechnicalAttachmentSection`), adozione delle normative CEI/UNI ed emissione dei Fogli Tecnici 2 allegati.

### 2.1 Quadri Elettrici BT e Verifiche Differenziali
| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W2-01` | **R8** | **Quadri BT**<br>*(Stampa PDF)* | Assegnare a un quadro BT una denominazione specifica (es. *"Quadro Generale QG"*) e stampare il PDF. | Nel cartiglio del PDF (Foglio 1) compare la denominazione e sigla specifica del quadro, senza la dicitura fissa generica *"Quadro Elettrico"*. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-02` | **R9** | **Quadri BT**<br>*(Editing Componenti)* | Cliccare sull'icona matita *(Modifica)* su un interruttore già censito in un quadro. | Si apre il dialog con dati precaricati. La modifica di un parametro (es. taratura) aggiorna la riga inline senza dover eliminare e ricreare l'elemento. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-03` | **R10** | **Nuovo Interruttore**<br>*(Form Inserimento)* | Cliccare su *"Aggiungi Componente"* all'interno di un quadro BT. | L'ordine dei campi segue il flusso naturale del tecnico: 1. *Sigla* (es. `QA1`) $\rightarrow$ 2. *Costruttore* $\rightarrow$ 3. *Modello/Articolo & Taratura ($I_{\Delta n}$)* $\rightarrow$ 4. *Descrizione/Note* (opzionale). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-04` | **R13** | **Gerarchia UI Quadri**<br>*(Dashboard Cantiere)* | Osservare la struttura dell'interfaccia della sezione Quadri nel cantiere. | Gerarchia visuale pulita ad albero: Scheda Quadri $\rightarrow$ Elenco Quadri di Cantiere $\rightarrow$ Componenti Quadro (card, badge, bottoni) $\rightarrow$ Attività generali. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-05` | **R15** | **Foglio 2 DIFF**<br>*(Verifica Semestrale 6M)* | Selezionare per un quadro la verifica a **6 Mesi** (`DIFF.06`) e generare il PDF. | Nel Foglio 2 allegato, la tabella mostra dati interruttore, taratura e **solo la colonna "Prova con tasto"** (OK / NON OK). La colonna strumentale è esclusa. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-06` | **R15** | **Foglio 2 DIFF**<br>*(Verifica Annuale 1A)* | Selezionare per lo stesso quadro la verifica ad **1 Anno** (`DIFF.1`) e generare il PDF. | Nel Foglio 2 allegato compaiono **entrambe le verifiche**: Prova con tasto + Prova con strumento (tempo e corrente di scatto AcroForm su larghezza 18,4 cm). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

### 2.2 Illuminazione di Emergenza (EM) & Sicurezza (IS) — UNI 11222
| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W2-07` | **R7**<br>**R16** | **Schede IS $\leftrightarrow$ EM**<br>*(Unificazione)* | Configurare una scheda con codice `IS` (*Sicurezza*) e una con `EM` (*Emergenza*). | Entrambe presentano attivi il pulsante *"Aggiungi Lampade"*, il censimento massivo e i badge, condividendo lo stesso schema dati master senza duplicazioni. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-08` | **R14** | **Export Registro EM**<br>*(UNI 11222 Standalone)* | Nella sezione lampade, cliccare sul pulsante di esportazione registro indipendente. | Generazione immediata del solo **Foglio 2: Registro Lampade UNI 11222** (ubicazione, autonomia, data batteria, esito prova) senza generare le attività. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

### 2.3 Quadro Media Tensione (QMT) — Tecnologie Interruttore
| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W2-09` | **R17** | **QMT Selettore MT**<br>*(SF6, ISV, IVOR)* | Nel Quadro Media Tensione selezionare la tecnologia dell'interruttore: `SF6`, `ISV` o `IVOR`. | Nella tabella attività, le verifiche specifiche (11, 12, 14, 18, 19, 20, 21) e la colonna Impianto/Zona riportano la tecnologia selezionata al posto di "Interruttore". | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-10` | **R17** | **Frequenze QMT**<br>*(Regola F % A == 0)* | Generare la scheda QMT a **1 Anno**, **2 Anni** e **5 Anni**. | Rispetto rigoroso delle periodicità: a 1 Anno stampa 12 attività; a 2 Anni stampa esattamente 14 attività (12 da 1A + 2 da 2A); a 5 Anni l'intero catalogo di 21 attività. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

### 2.4 Rivelazione Incendi (RI) & Rivelazione Gas (RIG) — UNI 9795 / UNI 11224
| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W2-11` | **R18** | **RI 7 Checkbox**<br>*(Attività Dinamiche)* | In RI spuntare solo *Fumo Puntiformi* e *Aspirazione ASD*. Generare la scheda a 6 Mesi. | Il PDF a 6M include le **10 attività istituzionali di centrale** + esattamente le 2 prove al 50% corrispondenti ai soli sensori spuntati, escludendo gli altri. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-12` | **R19** | **Registro Loop RI**<br>*(Foglio 2 ELENCO RIL)* | Censire dispositivi nei Loop (1..4) ed esportare il Foglio Tecnico Rivelazione. | Generazione del **Foglio 2 allegato (`ELENCO RIL`)** conforme UNI 9795/11224 con indirizzi, ubicazione e campi AcroForm interattivi (*Prova Allarme OK / NON OK*). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W2-13` | **R20** | **Rivelazione Gas (RIG)**<br>*(Isolamento Funzioni)* | Configurare una scheda Rivelazione Gas (`RIG`) e stampare il PDF. | La scheda gas mantiene inalterate le logiche native di intercettazione combustibile e trasmissione allarmi senza anomalie o interferenze da RI. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

---

<div style="page-break-before: always;"></div>

## 📑 WAVE 3 — Cantiere Workflow & PDF Batch Engine

> **Obiettivo:** Integrazione dei metadati operativi (data, manutentore, firma) nel cartiglio PDF, generazione batch multifrequenza, layout a 30 righe fisse e denominazione univoca dei file.

| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W3-01` | **R24** | **Cartiglio PDF**<br>*(Data Inizio Periodo)* | Inserire nella dashboard cantiere la data inizio attività (es. `15/11/2026`) e generare il PDF. | Il campo AcroForm `starting_date` in alto a destra nel cartiglio PDF risulta precompilato con la data, ma resta modificabile dal tablet. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-02` | **R25** | **Cartiglio PDF**<br>*(Tecnico Verificatore)* | Impostare nel cantiere il nome del *Verificatore* (es. *Mario Rossi*). Stampare la scheda. | Il riquadro *"Verificatore/Manutentore"* nel cartiglio del PDF riporta in chiaro il nominativo specificato. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-03` | **R26** | **Cartiglio Chiusura**<br>*(Firma Tecnico)* | Verificare la sezione finale del cartiglio in calce alle schede di verifica. | Predisposizione grafica formale del box firma per la convalida e sottoscrizione dell'intervento. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-04` | **R21** | **Generazione PDF**<br>*(Batch Multi-Frequenza)* | Selezionare contemporaneamente per un quadro sia la periodicità 6 Mesi che 1 Anno. | La procedura batch produce entrambi i file in un unico ciclo di generazione continuo senza interruzioni. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-05` | **R22** | **Naming File PDF**<br>*(Univocità File)* | Eseguire la generazione batch di quadri multipli (es. `QG` e `Q1`). | I file sono nominati rigorosamente come `QG_06.pdf`, `QG_1.pdf`, `Q1_06.pdf`, azzerando il rischio di sovrascritture. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-06` | **R23** | **Layout PDF**<br>*(Griglia 30 Righe)* | Generare una scheda con poche attività (es. 10 righe). | Il PDF mantiene la griglia completa a **30 righe fisse** (con righe vuote di padding), preservando la geometria millimetrica della pagina. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-07` | **R32** | **Stampa Colonna Zona**<br>*(Origine da CSV)* | Verificare schede originate da CSV (es. Trasformatore `TRFS` o Cabina `CAB`). | La colonna centrale "Impianto/Zona" stampa fedelmente il valore del campo "Zona" del CSV (es. *"Trasformatore"*, *"Q.M.T."*). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-08` | **R29** | **Cartella Temporale**<br>*(Export Batch)* | Generare un plico batch indicando una cartella di destinazione su disco. | I PDF vengono salvati automaticamente all'interno di una sottocartella strutturata per data (es. `2026_10/` o `2026_11/`). | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-09` | **R11** | **Dashboard Cantiere**<br>*(Elenco Interruttori)* | Cliccare sul tab dedicato *"Elenco interruttori"* nel cantiere. | Mostra la tabella consolidata di tutti gli interruttori differenziali del cantiere, con tasto per esportare il PDF riepilogativo. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W3-10` | *Layout* | **Campi AcroForm**<br>*(Adattamento Celle)* | Aprire il PDF con Adobe Acrobat e compilare i campi note e misure. | Le caselle di testo AcroForm occupano il 100% dell'area della cella senza doppi bordi interni o disallineamenti di font. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

---

<div style="page-break-before: always;"></div>

## 📅 WAVE 4 — Calendario Interattivo, Pianificazione, Storicizzazione & RBAC

> **Obiettivo:** Gestione delle scadenze preventive con vista mensile interattiva, pianificazione con frequenze differenziate per impianto, snapshot immutabile `SessioneVerifica` e controllo degli accessi.

| ID Test | Req. | Modulo / Schermata | Procedura Operativa (Azione) | Risultato Atteso (Criterio Accettazione) | Esito Collaudo | Note / Evidenze |
|:---:|:---:|:---|:---|:---|:---:|:---|
| `UAT-W4-01` | **R27** | **Calendario Globale**<br>*(Navigation Rail)* | Accedere alla sezione **Calendario** dal menu laterale principale. | Visualizzazione della griglia mensile interattiva con tutti i cantieri attivi e la collocazione temporale delle verifiche in scadenza. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-02` | **R28** | **Pianifica Intervento**<br>*(Dialog Pianificazione)* | Cliccare su *"Pianifica Intervento"* e selezionare un cantiere. | Gli impianti del cantiere vengono caricati dinamicamente in una checklist verticale con selezione indipendente delle periodicità. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-03` | *Logic* | **Scheduler Engine**<br>*(Scadenze Miste)* | Configurare nello stesso cantiere un quadro a 6M e un trasformatore a 1A. | Il calendario evidenzia correttamente le scadenze sfalsate senza forzare tutti gli impianti sulla stessa data contrattuale fittizia. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-04` | **R30** | **SessioneVerifica DB**<br>*(Storicizzazione)* | Eseguire un ciclo di stampa inserendo Tecnico A. Modificare poi il manutentore in cantiere con Tecnico B. | Nello storico delle verifiche pregresse, la sessione registrata mantiene immutabile il Tecnico A e la data effettiva dell'intervento. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-05` | **R30** | **Storico Verifiche**<br>*(Riapertura File)* | Selezionare una sessione passata nello storico cantiere. | Elenco dei file PDF archiviati cliccabili per l'apertura immediata nel visualizzatore di sistema per consultazione audit. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-06` | **R31** | **Profilazione RBAC**<br>*(Ruolo Tecnico)* | Effettuare l'accesso con un utente profilato come `TECNICO`. | Accesso limitato alla sola operatività e ai cantieri assegnati. Il menu di amministrazione master è disabilitato/inaccessibile. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |
| `UAT-W4-07` | **R31** | **Profilazione RBAC**<br>*(Ruolo Admin)* | Effettuare l'accesso con un utente profilato come `ADMIN`. | Visibilità e governo completo di tutte le funzioni di sistema: Gestione Utenti, Master Schede, Clienti, Cantieri e configurazione server. | [ ] 🟢 PASS<br>[ ] 🟡 COND<br>[ ] 🔴 FAIL | |

---

<div style="page-break-before: always;"></div>

## 📊 Sintesi delle Verifiche e Registro Rilievi

Compilare al termine della sessione congiunta di collaudo:

| Riepilogo Collaudo | Conteggio Casi | Percentuale su Totale |
|:---|:---:|:---:|
| **Test Eseguiti Totali** | `_____ / 27` | `100 %` |
| **Test Superati [ 🟢 PASS ]** | `_____` | `____ %` |
| **Test Accettati con Riserva [ 🟡 COND ]** | `_____` | `____ %` |
| **Test Non Superati [ 🔴 FAIL ]** | `_____` | `____ %` |

### Registro Rilievi ed Eventuali Note del Cliente:

| ID Rilievo | Test Rif. | Descrizione dell'Osservazione Riscontrata | Criticità *(Bassa / Media / Bloccante)* | Risoluzione Prevista |
|:---:|:---:|:---|:---:|:---|
| **RIL-01** | | | | |
| **RIL-02** | | | | |
| **RIL-03** | | | | |

---

## ✍️ Verbale di Collaudo e Accettazione Formale (Sign-Off UAT)

Il Committente / Responsabile dell'Ufficio Tecnico, esaminate le prove previste dalla presente matrice di collaudo per la Release **v3.1.0**:

- [ ] **DICHIARA ACCETTATA LA RELEASE SENZA RISERVE:**  
  Tutti i requisiti delle Wave 1, 2, 3 e 4 sono pienamente soddisfatti. Viene autorizzata la messa in produzione a regime e la chiusura della fase di sviluppo.

- [ ] **DICHIARA ACCETTATA LA RELEASE CON RISERVA NON BLOCCANTE:**  
  Il software entra regolarmente in produzione. Le note minori annotate nel registro rilievi saranno gestite nel primo ciclo di assistenza e rifinitura.

- [ ] **COLLAUDO SOSPESO:**  
  Sono state riscontrate anomalie bloccanti descritte nel registro rilievi che richiedono intervento correttivo prima del rilascio finale.

<br>

**Luogo e Data del Collaudo:** ________________________, ____ / ____ / 2026  

<br>

| Per l'Ufficio Tecnico / Committenza | Per lo Sviluppo Software / Fornitore |
|:---:|:---:|
| <br><br>_____________________________________________ | <br><br>_____________________________________________ |
| *(Firma leggibile e Timbro)* | *Prof. Edoardo Desiderio* |
