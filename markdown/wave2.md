Ecco una proposta di email formale e strutturata, pronta per essere inviata al cliente o agli stakeholder di progetto.

***

**Oggetto:** Rilascio Aggiornamento Software — Completamento Requisiti "Wave 2": Quadri BT, Emergenza/Sicurezza, QMT e Rivelazione Incendi

---

Gentili tutti,

con la presente vi comunichiamo il completamento e il rilascio in ambiente di collaudo della **Seconda Wave di Sviluppo** dell'applicazione **Manutenzioni Maker**, finalizzata al perfezionamento dei domini specialistici impiantistici, alla gestione del ciclo di vita dei componenti tecnici e all'allineamento dei documenti PDF compilabili (AcroForm) alle normative CEI e UNI di settore.

Di seguito riportiamo il dettaglio puntuale delle richieste del cliente assolte con questo rilascio:

---

### 1. Quadri Elettrici BT & Verifiche Differenziali
* **Richiesta 8 — Cartiglio Dinamico Quadro BT:** Il cartiglio del PDF ora riporta fedelmente la sigla specifica (es. *QG*, *Q1*) e la denominazione/ubicazione del quadro (es. *Locale Tecnico Piano Terra*), eliminando la dicitura generica statica.
* **Richiesta 9 — Modifica Rapida Componenti Inline:** Gli interruttori differenziali censiti nel quadro possono ora essere modificati direttamente tramite un dialog dedicato con dati precaricati, senza dover cancellare e reinserire l'elemento.
* **Richiesta 10 — Ottimizzazione Form Inserimento Interruttore:** Riorganizzato l'ordine dei campi secondo il flusso operativo del tecnico:
  1. *Sigla Circuito*
  2. *Costruttore*
  3. *Codice Articolo / Modello* e *Taratura Differenziale* ($I_{\Delta n}$)
  4. *Descrizione / Note* (ora opzionale e non bloccante per la validazione).
* **Richiesta 13 — Gerarchia Visiva della Dashboard Quadri:** Ristrutturata l'interfaccia ad albero gerarchico per facilitare la navigazione: *Scheda Quadri* $\rightarrow$ *Elenco Quadri di Cantiere* $\rightarrow$ *Dettaglio Interruttori e Attività*.
* **Richiesta 15 — Foglio 2 Differenziali Condizionale (6 Mesi vs 1 Anno):**
  * Nella verifica **Semestrale (6 Mesi - `DIFF.06`)**: la tabella allegata mostra unicamente i dati interruttore, la taratura e la *"Prova con tasto"* (OK / NON OK), escludendo la colonna strumentale.
  * Nella verifica **Annuale (1 Anno - `DIFF.1`)**: la tabella integra automaticamente entrambe le prove (*"Prova con tasto"* e *"Prova strumentale"* con misura di tempo/corrente di scatto), preservando la larghezza millimetrica di 18,4 cm.

---

### 2. Illuminazione di Emergenza (EM) & Sicurezza (IS)
* **Richiesta 7 — Piena Operatività su Schede "IS":** Il pulsante *"Aggiungi Lampade"* e l'intero registro apparecchi sono ora attivi e fruibili sia per schede codificate come **EM** (*Emergenza*) sia per **IS** (*Sicurezza*).
* **Richiesta 14 — Esportazione Autonoma Foglio 2 (UNI 11222):** Introdotto il pulsante dedicato per esportare direttamente in PDF il **Registro/Elenco complessivo delle lampade** come documento tecnico indipendente, senza dover generare l'intero plico delle attività.
* **Richiesta 16 — Allineamento e Unificazione di Dominio IS $\leftrightarrow$ EM:** `IS` ed `EM` condividono ora il medesimo modello dati nel database master, garantendo l'assenza di duplicazioni e permettendo al tecnico di selezionare la sigla documentale richiesta dal cantiere con adeguamento automatico di cartiglio e banner.

---

### 3. Quadro Media Tensione (QMT)
* **Richiesta 17 — Selettore Tecnologia Interruttore (SF6, ISV, IVOR):**
  * Aggiunto il selettore interattivo della tecnologia dell'interruttore MT direttamente nella configurazione del cantiere.
  * Adeguamento automatico della colonna *Zona/Impianto* nelle attività (sostituzione della dicitura generica *"Interruttore"* con la sigla della tecnologia scelta: *SF6*, *ISV* o *IVOR*).
  * Validazione dell'inclusione periodica conforme alla regola $F \pmod A == 0$ (12 attività per verifica annuale, 14 attività per biennale, 19 attività per quinquennale).

---

### 4. Rivelazione Incendi (RI) & Rivelazione Gas (RIG)
* **Richiesta 18 — Rivelazione Incendi: Checkbox Componenti & Attività Dinamiche:**
  * Predisposta una sezione a **7 checkbox** per selezionare le tipologie di sensori installati (*Fumo Puntiformi*, *Fumo Lineari*, *Calore Puntiformi*, *Calore Lineari*, *Aspirazione ASD*, *Condotta*, *Fiamma*).
  * Per le verifiche a 6 Mesi, il sistema include sempre le **10 attività istituzionali di centrale e linee** e inietta automaticamente per ciascuna categoria attiva la prova di funzionamento al 50%:
    > *"Prova dei [Componente Selezionato] (prova sul 50% dell’impianto) impiegando dispositivi artificiali di produzione del fumo o di altro, simulando l'insorgere di un incendio"*
* **Richiesta 19 — Registro Componenti a Loop (LOOP 1..4) & Foglio 2 ELENCO RIL:**
  * Implementato il registro per censire sensori e moduli di rivelazione suddivisi per Loop (da 1 a 4).
  * Generazione del **Foglio 2 allegato (`ELENCO RIL`)** conforme a **UNI 9795 / UNI 11224** con indirizzi, tipo, ubicazione, radio button AcroForm interattivi (*Prova Allarme OK / NON OK*) e campo note.
* **Richiesta 20 — Guardrail Rivelazione Gas (RIG):** Confermato il perfetto isolamento e la stabilità delle schede gas rispetto alle logiche antincendio.

---

### 5. Standardizzazione Architetturale dell'Interfaccia
* **Componente Universale Allegati Tecnici:** Quadri BT, Lampade di Emergenza e Rilevazione Incendi adottano ora la stessa interfaccia standardizzata `TechnicalAttachmentSection`, dotata di badge di stato, clonazione automatica intelligente delle sigle (`QA1` $\rightarrow$ `QA2`, `RIV.L1.1` $\rightarrow$ `RIV.L1.2`) ed esportazione autonoma dei fogli allegati.

---

Tutte le modifiche sono state coperte da test di unità e integrazione automatizzati e sono già incorporate nel ramo di sviluppo principale (`develop`).

Restiamo a disposizione per una sessione congiunta di verifica e collaudo.

Cordiali saluti,  
*Il Team di Sviluppo*