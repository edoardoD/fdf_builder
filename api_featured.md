# Walkthrough: Generazione Secondo Foglio PDF & Test Brute Force Catalogo ETIM

## 1. 📄 Generazione Secondo Foglio PDF per Componenti Quadro BT

È stata implementata in [HtmlService.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/app/service/HtmlService.kt) la generazione del secondo foglio all'interno del PDF per i quadri elettrici ([QuadroBT](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/domain/model/ManutenzioneModels.kt)), fedele al modello industriale reale allegato dall'utente (*"ELENCO INTERRUTTORI DIFFERENZIALI"*).

### Modifiche Apportate:
- **Rilevamento `QuadroBT`**: in `buildHtml`, se l'impianto è un `QuadroBT` con componenti associati (`listaInterruttori.isNotEmpty()`), viene invocata `buildComponentiSheet(impianto, frequenza, clienteNome)`.
- **Iniezione nel template**: blocco HTML iniettato sul placeholder `<!-- COMPONENTI_QUADRO_SHEET -->`.
- **Interruzione di pagina pulita**: `<div class="sheet--componenti" style="page-break-before: always; break-before: page; ...">`.
- **Tabella 9 Colonne & AcroForm**:
  - `Sigla Quadro`, `Sigla Interruttore`, `Dati Interruttore`, `Taratura diff.`
  - `Prova con tasto`: radio button `OK` / `NON OK`
  - `Prova con strumento`: radio button `OK` / `NON OK`
  - `NR.MISURA`: campo testo AcroForm compilabile
- **Srotolamento per `quantita > 1`**: righe distinte `.1` e `.2` con campi indipendenti.
- **Correzione Allineamento e Overflow (18.4cm)**:
  - Container e tabella calibrati a `18.4cm` fisso con `table-layout: fixed`.
  - Perfetto allineamento margini destro e sinistro A4 senza alcuna sbordatura.

---

## 2. ⚡ Catalogo ETIM: Risoluzione Query S251/EF070 & Suite Brute Force Industriale

### Problema Risolto:
L'utente ha riscontrato che la query reale da fattura/computo metrico:
> `S251 NA C 6 INTERRUTTORE AUTOMAT. 6KA 1P+N – ABB EF 070 6`

non appariva nei risultati o non veniva correttamente interpretata dal catalog engine.

### Cause Identificate:
1. **Abbreviazioni tecniche non normalizzate**: `automat.`, `int.`, `diff.`, trattini lunghi (`–`, `—`, `−`).
2. **Codice ordine fornitore Metel/ABB**: il codice storico `EF 070 6` indicava la corrente nominale nella cifra finale (`6` -> 6A, `EF 070 16` -> 16A).
3. **Spaziatura curva/amperaggio**: `C 6` invece di `C6`.
4. **Collisione categorie**: MCB veniva inserito a inizio lista alterando l'ordinamento di componenti specifici (RCBO, SPD, sezionatori).
5. **Estrazione poli da sigle compatte**: codici come `FN82C16`, `FN84C32`, `G8843A40`, `GN8843AC32` non avevano delimitatori di parola dopo il prefisso dei poli.

### Soluzioni Implementate in [EtimCatalogService.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopMain/kotlin/manutenzioni/app/service/EtimCatalogService.kt):
1. **Normalizzazione stringa**:
   - Mappatura automatica di `automat.` -> `automatico`, `diff.` -> `differenziale`, `sez.` -> `sezionatore`.
   - Normalizzazione dei trattini Unicode e rimozione spazi multipli.
2. **Riconoscimento codici ABB S250 / S251**:
   - Generazione della variante esatta cercata: `S251 NA C $corrente (EF 070 $corrente)`.
   - Aggiunta automatica del sostitutivo moderno equivalente di catalogo: `SN201-L-C$corrente`.
3. **Parser Corrente e Poli Multi-Serie**:
   - Riconoscimento codici ABB (`EF 070 X`, `S201..S204`), BTicino (`FN81..FN84`, `GN8813..43`, `G723..724`, `F82..84`), Schneider (`A9N21556`, `A9F742..744`, `A9R`, `A9S`, `A9D`, `A9L`), Siemens (`5SL6506..16`, `5SV33`, `5SU1`), Gewiss (`GW90026`, `GW94617`), Dehn (`DEHNguard`).
4. **Classificazione Gerarchica e Mutuamente Esclusiva**:
   - `EC000228`: SPD Scaricatore di sovratensione
   - `EC000216`: Sezionatore di manovra
   - `EC000228`: MCCB Scatolato industriale
   - `EC000905`: RCBO Interruttore magnetotermico differenziale
   - `EC000003`: RCCB Interruttore differenziale puro
   - `EC000042`: MCB Interruttore magnetotermico standard

---

## 3. 🧪 Risultati Test Brute Force ([EtimCatalogBruteForceTest.kt](file:///Users/edoardo/development_prdj/fdf_builder/desktopApp/src/desktopTest/kotlin/manutenzioni/app/service/EtimCatalogBruteForceTest.kt))

È stata eseguita una batteria di test esaustiva su:
- Test query utente esatta: `S251 NA C 6 INTERRUTTORE AUTOMAT. 6KA 1P+N – ABB EF 070 6` -> **PASSED**
- Test ricerca per solo codice fornitore: `EF 070 6` -> **PASSED**
- **77 query reali industriali di interruttori** coprendo ABB, BTicino, Schneider, Siemens, Gewiss, Dehn:
  - MCB 1P, 1P+N, 2P, 3P, 4P da 6A a 63A (curve B, C, D)
  - RCBO 1P+N e 4P (30mA e selettivi)
  - Differenziali puri 2P, 4P (tipo AC, A, 30mA, 300mA)
  - Sezionatori 2P, 4P da 32A e 63A
  - Scaricatori SPD Tipo 2 (Dehn, ABB, Schneider, BTicino)
  - MCCB Scatolati industriali da 100A e 160A

### Esito Esecuzione:
```text
==================================================================
  BRUTE FORCE TEST RESULTS: 77 / 77 PASSED (100%)
==================================================================
BUILD SUCCESSFUL in 1s
```

Tutti i controlli globali (`./gradlew check`) e la compilazione dell'applicazione desktop (`./gradlew :desktopApp:compileKotlinDesktop`) sono completati con successo con **0 errori e 0 regressioni**.
