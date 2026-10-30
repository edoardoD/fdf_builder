# Relazione Tecnica di Progetto e Consuntivo Lavori
**Progetto:** Piattaforma Gestionale Manutenzioni Impianti (*Manutenzioni Maker*)  
**Oggetto:** Descrizione dettagliata delle attività svolte per il rilascio della Release 1.0  
**Data:** 26 Ottobre 2026  
**Stato:** Installato e operativo su postazioni ufficio e server aziendale  
**Compenso Pattuito:** € 5.000,00 (da regolare con Prestazione Occasionale anno 2026)  

---

## 1. Premessa e Spirito del Progetto

Il presente documento descrive in modo organico il lavoro di sviluppo, analisi e configurazione svolto per la realizzazione della **Release 1.0** del software gestionale **Manutenzioni Maker**, sviluppato su misura per le esigenze del nostro Ufficio Tecnico.

Trattandosi per me di un primo importante progetto software strutturato, l'opportunità e la cifra concordata di **€ 5.000,00** hanno rappresentato un attestato di fiducia significativo e un investimento importante da parte dell'azienda. Proprio per questo motivo, ho voluto affrontare il lavoro con il massimo senso di responsabilità e dedizione, andando ben oltre la richiesta di partenza:

* **La richiesta iniziale:** L'obiettivo iniziale prevedeva essenzialmente la digitalizzazione della modulistica cartacea tramite la generazione massiva di semplici file PDF compilabili.
* **Cosa ho scelto di realizzare:** Per dare all'azienda uno strumento realmente solido, veloce e durevole negli anni — evitando soluzioni improvvisate o "fogli volanti" che avrebbero creato problemi nel tempo — ho progettato e sviluppato da zero una vera e propria **applicazione desktop nativa**, dotata di una base dati centralizzata sul server dell'ufficio, funzionamento autonomo anche in assenza di rete (*offline-first*), calcolo automatico dei controlli normativi e generazione di schede tecniche di secondo livello (differenziali di quadro e registro lampade d'emergenza).

---

## 2. Consuntivo Dettagliato delle Attività Svolte

Per dare piena visibilità e trasparenza all'impegno dedicato in questi mesi, di seguito viene riassunto il lavoro svolto nelle sue diverse fasi:

### 2.1 Analisi delle Necessità e Mappatura Normativa
* Confronto diretto con l'Ufficio Tecnico e i tecnici di cantiere per comprendere a fondo le problematiche quotidiane: tempi persi nella compilazione manuale, errori nelle scadenze e difficoltà nel reperire i dati storici.
* Mappatura dei requisiti di controllo e delle normative per ciascuna categoria d'impianto:
  * **Quadri Elettrici BT:** Controlli generali di quadro, pulizia, serraggi e prove strumentali sui differenziali;
  * **Illuminazione di Sicurezza / Emergenza (UNI 11222):** Gestione registro lampade, scadenze batterie, verifiche funzionali e prove di autonomia;
  * **Rilevazione Incendi (UNI 9795 / UNI 11224) & Rilevazione Gas:** Verifiche di centrale, linee di trasmissione, sensori e prove di interfacciamento con gli altri impianti;
  * **Cabine MT/BT, Quadri Media Tensione (QMT) e Gruppi Elettrogeni (GE).**

### 2.2 Studio di Fattibilità e Scelta Tecnologica
* **Primo prototipo di prova (Java):** Nelle prime fasi ho realizzato un prototipo sperimentale per testare la generazione automatica di campi modulo interattivi (*AcroForm*) all'interno dei PDF.
* **Evoluzione verso una tecnologia moderna (Kotlin & Compose Desktop):** Constatati i limiti del primo prototipo, ho preferito reimpostare l'intero progetto su una tecnologia d'avanguardia: **Kotlin Multiplatform con interfaccia grafica in Compose Desktop**. Questo garantisce un'applicazione nativa per Windows, rapida, fluida e con una struttura del codice pulita (*Clean Architecture*) che ne renderà agevole la manutenzione e l'eventuale estensione futura.

### 2.3 Riordino e Bonifica dell'Archivio Storico (>60 Fogli Excel)
* **Analisi dei dati preesistenti:** Uno dei compiti più impegnativi e silenziosi è stato prendere in carico l'archivio storico dell'ufficio: una cartella disomogenea con **oltre 60 fogli Excel**, ricchi di dati preziosi ma strutturati con criteri, sigle e formule da interpretare correttamente.
* **Reverse engineering e normalizzazione:** Ho analizzato e decodificato manualmente e con script dedicati le relazioni tra gli impianti, i componenti (es. abbinamento quadri ↔ interruttori differenziali) e le relative frequenze di controllo, bonificando i testi e inserendoli in modo ordinato nel catalogo del software.

### 2.4 Architettura dei Dati e Doppia Persistenza (Server LAN + Offline)
* **Database Centralizzato (MongoDB):** Installazione e configurazione del database sul server dell'ufficio per permettere a tutti gli operatori di lavorare sulla stessa base dati condivisa e aggiornata.
* **Sicurezza e Modalità Offline:** Ho implementato una modalità di backup locale con salvataggio sicuro (cifratura AES delle impostazioni): se la rete o il server dovessero avere problemi temporanei, il programma continua a funzionare salvando i dati localmente, garantendo continuità di lavoro senza blocchi.

### 2.5 Sviluppo del Software e Funzionalità Realizzate
* **Interfaccia Utente Grafica:** Creazione di un'interfaccia semplice, pulita e moderna con:
  * Selezione rapida Cliente e Cantieri;
  * Gestione degli impianti per ciascun cantiere;
  * Editor dedicato per l'aggiunta e la modifica dei componenti (interruttori, lampade, ecc.);
  * Sezione amministrativa per personalizzare i template master.
* **Calcolo Matematico delle Periodicità (`FrequencyFilter`):** Implementazione della regola per cui un intervento a periodicità maggiore include automaticamente le attività delle frequenze inferiori compatibili (es. un controllo annuale seleziona in automatico i controlli mensili, trimestrali, semestrali e annuali, evitando dimenticanze manuali).
* **Prove Incrociate tra Impianti (`CrossPlantAttivitaResolver`):** Il sistema riconosce quali impianti sono presenti nello stesso cantiere e inserisce nel PDF le prove congiunte solo se necessario (ad esempio, le prove di sgancio della Cabina o del Quadro da parte della centrale Incendi compaiono solo se quegli impianti sono effettivamente configurati per quel cantiere).
* **Fogli Tecnici Allegati (Foglio 2+):** Oltre alla scheda base, il software produce automaticamente il foglio 2 per le verifiche strumentali:
  * *Registro Interruttori Differenziali:* con codici, dati di targa e campi compilabili per prova a scatto e misura tempi/correnti;
  * *Registro Lampade UNI 11222:* con ubicazione, autonomia dichiarata, data batteria e campi di esito prova.
* **Volume di Lavoro:** Sono state scritte e collaudate oltre **23.000 righe di codice** proprietario, con test automatici per verificare che i calcoli e i documenti generati siano sempre corretti.

### 2.6 Intervento Sistemistico in Loco (2 Giornate)
* Installazione, configurazione e ottimizzazione del servizio database direttamente sul **Windows Server** aziendale.
* Configurazione delle regole di rete locale (firewall, porte e permessi).
* Configurazione postazione per postazione sui singoli PC dell'ufficio per verificare connettività, compatibilità e corretto avvio dell'applicativo da parte di ciascun operatore.

---

<div style="page-break-before: always;"></div>

## 3. Riepilogo dell'Effort e Trasparenza Economica

Riconoscendo appieno l'importanza dei **€ 5.000,00** concordati per questo primo lavoro, ho voluto documentare con precisione il tempo e l'impegno dedicati, per mostrare che l'investimento è stato pienamente onorato con professionalità e serietà:

| Fase di Lavoro | Descrizione Sintetica | Tempo Dedicato |
|:---|:---|:---:|
| **Analisi, bonifica dati e reverse engineering** | Studio requisiti operativi, riordino e decodifica di oltre 60 fogli Excel disomogenei. | **3 giorni** |
| **Architettura e setup tecnologico** | Prototipo iniziale Java, passaggio a Kotlin Desktop, setup build e packaging nativo. | **3 giorni** |
| **Database e persistenza ibrida** | Progettazione schema NoSQL, setup MongoDB server e modalità offline resiliente. | **2 giorni** |
| **Logica di business e algoritmi normativi** | Regola calcolo frequenze inclusive, risoluzione impianti congiunti (incendi/gas/quadri). | **2 giorni** |
| **Motore PDF e Fogli Tecnici Allegati** | Template dinamici, integrazione iText7, campi compilabili AcroForm e Fogli 2 strumentali. | **3 giorni** |
| **Sviluppo interfaccia grafica (UI)** | Schermate operative, gestione cantieri/clienti, editor componenti e validazioni form. | **5 giorni** |
| **Testing, collaudo e verifiche** | Test delle logiche di calcolo, correzioni di stabilità e verifica generazione PDF. | **2 giorni** |
| **Intervento sistemistico in ufficio** | Installazione su server aziendale, configurazione rete e setup sui PC dei colleghi. | **2 giorni** |
| **TOTALE TEMPO DEDICATO** | **Oltre 150 ore di lavoro scrupoloso** | **~22 giornate** |

### Un Riscontro Oggettivo di Mercato
A titolo di pura trasparenza aziendale: commissionare all'esterno lo sviluppo di un applicativo gestionale proprietario con queste caratteristiche (database dedicato, interfaccia desktop compilata nativamente, motore di calcolo normativo, generatore PDF interattivo e assistenza sistemistica in loco) comporta normalmente preventivi compresi tra **€ 12.000,00 ed € 15.000,00**.

Il compenso pattuito di **€ 5.000,00** ha permesso all'azienda di dotarsi di uno strumento solido e duraturo con un costo estremamente contenuto, dando al contempo a me la possibilità di dimostrare concretezza, affidabilità e valore.

---

## 4. Chiusura della Release 1.0 e Gestione delle Nuove Idee (Fase 2)

Per lavorare con metodo e fare in modo che il software porti reali benefici operativi, è utile procedere per passi ordinati:

### 4.1 Cosa Comprende la Release 1.0 (Consegnata e Operativa)
* La **Release 1.0** è la versione completa, stabile e installata sui computer dell'ufficio. Include tutte le funzioni concordate: anagrafiche, gestione cantieri, selezione impianti, calcolo corretto delle frequenze, generazione delle schede PDF interattive e allegati per differenziali e lampade.
* **Assistenza e Supporto:** Resto naturalmente a disposizione per risolvere prontamente qualsiasi eventuale problema di funzionamento, difetto o errore (*bug*) che dovesse manifestarsi nell'uso quotidiano della Release 1.0.

### 4.2 Come Gestire le Ulteriori Richieste (Roadmap Fase 2)
Durante i test e le prime dimostrazioni sono emerse diverse buone idee e desiderata (dalla gestione di dettagli tecnici specifici come le tipologie interruttori MT, al censimento sensori a loop per l'antincendio, fino a rifiniture grafiche o pulsanti dedicati). Ho già catalogato e appuntato con cura tutte queste proposte (oltre 30 punti).

Tuttavia, come buona regola di gestione:
* **È utile "tirare una riga" e mettere in produzione la Release 1.0**, permettendo ai tecnici di utilizzarla davvero sul campo per qualche settimana.
* Continuare ad accumulare modifiche e ritocchi prima ancora di aver messo alla prova sul campo la prima versione rischierebbe di disperdere energie.
* Tutte le nuove funzionalità e personalizzazioni costituiranno la **Fase 2**: una volta che il programma sarà entrato a regime nella routine dell'ufficio, potremo sederci insieme, valutare quali di queste aggiunte siano realmente prioritarie e concordare con calma tempi e modalità per realizzarle.

---

## 5. Chiusura Amministrativa e Proprietà del Software (Prestazione Occasionale 2026)

Avendo completato tutte le attività concordate, installato e collaudato con successo il programma sui PC dell'ufficio e fornito l'assistenza necessaria:

* Il compenso pattuito di **€ 5.000,00** verrà regolato mediante il rilascio di una regolare **ricevuta per prestazione occasionale relativa all'anno 2026** (con relativa ritenuta d'acconto di legge).
* Con l'emissione della ricevuta e il saldo del compenso, l'azienda diventerà **proprietaria effettiva ed esclusiva dell'applicazione**, comprensiva dell'intero codice sorgente, dei moduli e di tutta la documentazione tecnica prodotta.

Resto a completa disposizione per affiancare i colleghi nei primi utilizzi pratici, raccogliere i loro riscontri e garantire che il gestionale diventi uno strumento prezioso per il lavoro di tutti i giorni.

---

<br>

**In fede,**
**Prof.** Edoardo Desiderio
