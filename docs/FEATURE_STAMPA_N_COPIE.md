# 📄 Feature: Stampa N Copie PDF — Documento di Progettazione

> **Versione:** 1.0 — **Data:** 2026-02-27
> **Stato:** Proposta — Pronta per implementazione
> **Prerequisiti:** AGENTS.md v2.1, codebase v2.1 (Gestione Clienti completata)

---

## 1. 📋 Descrizione della Feature

### Requisito funzionale

> Una volta selezionato un **Cliente**, un **Impianto** e una **Frequenza**, l'utente deve poter
> specificare un numero **N ≥ 1** di copie della stessa identica scheda di manutenzione.
> L'applicativo genera **N file PDF** identici, ciascuno con un nome progressivo,
> salvandoli in una **cartella scelta dall'utente** tramite un dialog di sistema.

### Esempio concreto

L'utente seleziona:
- **Cliente:** ACME S.r.l.
- **Impianto:** GE — Gruppo Elettrogeno
- **Frequenza:** 6 Mesi
- **Copie:** 4
- **Cartella:** `/Users/mario/Desktop/Schede_ACME`

**Output atteso:**

```
/Users/mario/Desktop/Schede_ACME/
├── GE_6_Mesi_copia_1.pdf
├── GE_6_Mesi_copia_2.pdf
├── GE_6_Mesi_copia_3.pdf
└── GE_6_Mesi_copia_4.pdf
```

Ogni file è identico: stessa scheda AcroForm compilabile, stessi campi radio, stessi dati.

### Perché N copie?

Un cliente come villa Igea Ha N quadri su cui fare la manutenzione.
Per questo motivo, vogliamo generare N copie di scheda AcroForm.


---

## 2. 🏗️ Analisi dell'Architettura Attuale

### Flusso corrente (singolo PDF)

```
┌──────────┐     ┌─────────────────────┐     ┌───────────────────┐
│ Sidebar  │────▶│  ViewModel          │────▶│ PdfGeneratorStrategy│
│ "Genera" │     │  generatePdf()      │     │  .generate()       │
└──────────┘     └─────────┬───────────┘     └─────────┬─────────┘
                           │                           │
                  FileDialog (AWT)            ┌────────▼────────┐
                  SAVE → 1 file               │ HtmlToPdfStrategy│
                                              │ 1. FrequencyFilter│
                                              │ 2. HtmlService    │
                                              │ 3. Pdf.buildPdf() │
                                              └─────────┬────────┘
                                                        │
                                                   1 file .pdf
```

### Contratti coinvolti

| Contratto | File | Firma attuale |
|---|---|---|
| `PdfGeneratorStrategy` | `domain/strategy/PdfGeneratorStrategy.kt` | `fun generate(impianto, frequenza, outputPath, clienteNome?): File` |
| `IPdf` | `domain/service/IPdf.kt` | `fun buildPdf(htmlFilePath, pdfFilePath)` |
| `Html` | `domain/service/Html.kt` | `fun fillHtml(...)` (legacy, non usata) |
| `ManutenzioneRepository` | `domain/ManutenzioneRepository.kt` | CRUD impianti + clienti |

### Osservazione chiave

Il metodo `generate()` produce **un singolo PDF** dato **un singolo `outputPath`**.
La feature N-copie richiede di produrre **N PDF identici** con **N `outputPath` diversi**
nella stessa cartella. Questo è fondamentalmente un **loop sul contratto esistente**,
non una modifica del contratto stesso.

---

## 3. 🎯 Strategia di Implementazione

### Principio guida: Estendere, non modificare

La feature N-copie **non modifica nessun contratto esistente**. Si implementa come:

1. Un **nuovo metodo** nella `PdfGeneratorStrategy` con **default implementation** che riusa `generate()`
2. Nuovi campi nell'**UiState** per il numero di copie e il progresso
3. Un nuovo metodo nel **ViewModel** che orchestra il loop
4. Modifiche alla **Sidebar** per l'input del numero di copie
5. Modifiche al **MainContent** per mostrare il riepilogo batch

```
PRIMA (v2.1)                          DOPO (v2.2)

generate() ◄── unico punto            generate()     ◄── invariato, 1 PDF
                d'ingresso             generateBatch() ◄── NUOVO, N PDF
                                           └── chiama generate() N volte
```

### Perché NON toccare `generate()`

- **AGENTS.md §6.2 punto 1:** *"Non toccare la firma di `PdfGeneratorStrategy.generate()` senza aggiornare TUTTE le implementazioni."*
- `generate()` è usato dal `ViewModel.generatePdf()` attuale — rompere la firma rompe l'intera pipeline.
- La feature N-copie è un **superset** di quella singola: 1 copia = caso degenere di N copie.
- Aggiungere un default method nell'interfaccia preserva la **retrocompatibilità binaria**: qualsiasi implementazione futura di `PdfGeneratorStrategy` continua a compilare senza modifiche.

---

## 4. 📐 Piano Implementativo Layer-by-Layer

L'ordine segue **rigorosamente** AGENTS.md §6.1:

```
STEP 1 → Domain (contratti)
STEP 2 → Data Layer (nessuna modifica)
STEP 3 → Strategy Layer (implementazione batch)
STEP 4 → UI Layer (ViewModel + Composable)
STEP 5 → Validazione
```

---

### STEP 1 — 🏛️ DOMAIN LAYER

#### 1A. Nuovo data class: `BatchResult`

**File da creare:** `domain/model/BatchResult.kt`

```kotlin
package manutenzioni.domain.model

import java.io.File

/**
 * Risultato di una generazione batch di N copie PDF.
 *
 * @param generatedFiles Lista dei file generati con successo
 * @param totalRequested Numero totale di copie richieste
 * @param errors Mappa indice-copia → messaggio errore (per i falliti)
 */
data class BatchResult(
    val generatedFiles: List<File>,
    val totalRequested: Int,
    val errors: Map<Int, String> = emptyMap()
) {
    /** Numero di copie generate con successo */
    val successCount: Int get() = generatedFiles.size

    /** Numero di copie fallite */
    val failureCount: Int get() = errors.size

    /** true se tutte le copie sono state generate */
    val isFullSuccess: Boolean get() = errors.isEmpty()
}
```

**Perché un data class dedicato e non una semplice `List<File>`:**

- Trasporta informazioni di **errore parziale** (la copia 3 su 5 è fallita, le altre 4 sono ok).
- Permette alla UI di mostrare un **riepilogo differenziato** (✓/✗ per ogni copia).
- È **immutabile e type-safe** — coerente con AGENTS.md §2.2.
- È nel package `domain.model`, preparando il terreno per il refactoring futuro dei model (debito tecnico §8.1 di AGENTS.md).

#### 1B. Estensione dell'interfaccia: `PdfGeneratorStrategy`

**File da modificare:** `domain/strategy/PdfGeneratorStrategy.kt`

```kotlin
interface PdfGeneratorStrategy {

    // ── Firma esistente — INVARIATA ──
    fun generate(
        impianto: Impianto,
        frequenza: Periodo,
        outputPath: String,
        clienteNome: String? = null
    ): File

    // ── Nuovo metodo con default implementation ──
    /**
     * Genera N copie identiche dello stesso PDF in una directory.
     *
     * La default implementation itera su generate() N volte,
     * creando file con naming progressivo: {COD}_{FREQ}_copia_{i}.pdf
     *
     * @param impianto L'impianto selezionato
     * @param frequenza La frequenza di manutenzione
     * @param outputDir La directory di destinazione
     * @param copies Il numero di copie da generare (>= 1)
     * @param clienteNome Il nome del cliente
     * @param onProgress Callback per il progresso (copiaCorrente, totale)
     * @return BatchResult con i file generati e gli eventuali errori
     */
    fun generateBatch(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String? = null,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchResult {
        require(copies >= 1) { "Il numero di copie deve essere >= 1" }

        val baseName = "${impianto.codIntervento}_${frequenza.label().replace(" ", "_")}"
        val generatedFiles = mutableListOf<File>()
        val errors = mutableMapOf<Int, String>()

        for (i in 1..copies) {
            try {
                val fileName = if (copies == 1) {
                    "$baseName.pdf"
                } else {
                    "${baseName}_copia_$i.pdf"
                }
                val outputPath = File(outputDir, fileName).absolutePath
                val file = generate(impianto, frequenza, outputPath, clienteNome)
                generatedFiles.add(file)
            } catch (e: Exception) {
                errors[i] = e.message ?: "Errore sconosciuto"
            }
            onProgress(i, copies)
        }

        return BatchResult(
            generatedFiles = generatedFiles,
            totalRequested = copies,
            errors = errors
        )
    }
}
```

**Perché un default method e non un metodo astratto:**

| Scelta | Conseguenza |
|---|---|
| Metodo **astratto** | Rompe `HtmlToPdfStrategy` e qualsiasi futura implementazione → breaking change |
| Metodo **default** con body | Ogni implementazione esistente funziona immediatamente. `HtmlToPdfStrategy` può sovrascriverlo solo se serve ottimizzare |

**Perché `onProgress` come callback:**

- Il ViewModel deve aggiornare la progress bar durante la generazione.
- Il callback è l'approccio più leggero: non serve un `Flow` o un `Channel` per una sequenza lineare 1..N.
- Il domain layer resta indipendente dalla UI: il callback è una lambda pura `(Int, Int) -> Unit`.

**Perché il naming `_copia_1`, `_copia_2`:**

- Se `copies == 1`, il nome è identico al flusso singolo attuale (`GE_6_Mesi.pdf`) — **retrocompatibilità UX**.
- Se `copies > 1`, il suffisso numerico rende i file ordinabili nel filesystem.
- Nessun rischio di sovrascrittura: l'indice è sempre univoco.

---

### STEP 2 — 📦 DATA LAYER

#### Nessuna modifica necessaria

Il numero di copie **non è un dato persistito**. È un parametro di sessione:
l'utente lo sceglie ogni volta che preme "Genera".

- **`ManutenzioniDatabase`**: invariato. Nessun campo aggiunto.
- **`ManutenzioneModels.kt`**: invariato. Le copie non sono un'entità del dominio.
- **`JsonManutenzioneRepository`**: invariato. Il repository gestisce CRUD impianti/clienti, non la generazione PDF.
- **Retrocompatibilità JSON**: garantita al 100% perché non si tocca il modello dati.

Questo è coerente con AGENTS.md §4.2: *"Il cliente è selezionato a livello di sessione UI"*.
Il numero di copie segue lo stesso principio.

---

### STEP 3 — ⚙️ STRATEGY LAYER

#### `HtmlToPdfStrategy`: override opzionale

La **default implementation** in `PdfGeneratorStrategy.generateBatch()` funziona già perfettamente
per `HtmlToPdfStrategy` perché:

1. Chiama `generate()` in loop — e `generate()` è già implementato e testato.
2. Ogni invocazione crea il suo `tempHtml`, converte, pulisce — nessuna interferenza.
3. iText7 non mantiene stato tra chiamate (ogni `PdfWriter` è una nuova istanza).

**Non è necessario sovrascrivere `generateBatch()` in `HtmlToPdfStrategy`.**

Tuttavia, se in futuro si volesse **ottimizzare** (es. generare l'HTML una sola volta e
convertirlo N volte), si potrebbe fare override:

```kotlin
// OPZIONALE — Ottimizzazione futura in HtmlToPdfStrategy
override fun generateBatch(
    impianto: Impianto,
    frequenza: Periodo,
    outputDir: File,
    copies: Int,
    clienteNome: String?,
    onProgress: (Int, Int) -> Unit
): BatchResult {
    // 1. Filtra attività UNA SOLA VOLTA
    val attivitaFiltrate = FrequencyFilter.filterByFrequenza(...)
    // 2. Genera HTML UNA SOLA VOLTA
    val htmlContent = htmlService.buildHtml(...)
    val tempHtml = File.createTempFile(...)
    tempHtml.writeText(htmlContent)

    try {
        // 3. Converti N volte dallo stesso HTML
        for (i in 1..copies) {
            pdfEngine.buildPdf(tempHtml.absolutePath, outputPath_i)
            onProgress(i, copies)
        }
    } finally {
        tempHtml.delete() // Pulizia UNA SOLA VOLTA
    }
    ...
}
```

**Raccomandazione:** Non implementare questa ottimizzazione nella prima release. Il costo
di `HtmlService.buildHtml()` è trascurabile (string replacement), e la semplicità della
default implementation vale più dei millisecondi risparmiati.

---

### STEP 4 — 🖥️ UI LAYER

Questo è lo step più articolato. Si divide in 4 sotto-modifiche:

#### 4A. `ManutenzioniUiState` — Nuovi campi

**File:** `ManutenzioniViewModel.kt`

```kotlin
data class ManutenzioniUiState(
    // ── Campi esistenti — INVARIATI ──
    val impianti: List<Impianto> = emptyList(),
    val selectedImpianto: Impianto? = null,
    val frequenzeDisponibili: List<Periodo> = emptyList(),
    val selectedFrequenza: Periodo? = null,
    val clienti: List<Cliente> = emptyList(),
    val selectedCliente: Cliente? = null,
    val pdfFile: File? = null,
    val isLoading: Boolean = false,
    val statusMessage: String = "Seleziona un impianto per iniziare",
    val errorMessage: String? = null,
    val viewMode: ViewMode = ViewMode.PDF_PREVIEW,

    // ── Nuovi campi per batch N-copie ──
    val numberOfCopies: Int = 1,                    // Copie richieste (min 1)
    val batchResult: BatchResult? = null,            // Risultato dell'ultima generazione batch
    val batchProgress: BatchProgress? = null         // Progresso generazione in corso
)

/**
 * Progresso di una generazione batch in corso.
 * Immutabile — ogni aggiornamento produce una nuova istanza.
 */
data class BatchProgress(
    val current: Int,     // Copia corrente in generazione
    val total: Int        // Totale copie richieste
) {
    val percentage: Float get() = current.toFloat() / total.toFloat()
    val isComplete: Boolean get() = current >= total
}
```

**Perché `numberOfCopies` nell'UiState e non come stato locale `remember`:**

- Il numero di copie influenza il testo del bottone ("Genera 1 PDF" vs "Genera 4 PDF").
- Il ViewModel ne ha bisogno per chiamare `generateBatch()`.
- La StatusBar può mostrare "Generazione copia 3/4 in corso...".
- Secondo AGENTS.md §5.3: *"Lo stato globale vive nel ViewModel"*.

**Perché `batchResult` separato da `pdfFile`:**

- `pdfFile: File?` resta per la retrocompatibilità con `openPdfInSystem()` e la card singola.
- `batchResult` porta informazioni più ricche (successi, errori, lista file).
- Quando `numberOfCopies == 1`, si imposta anche `pdfFile` con il singolo file per non rompere nulla.

#### 4B. `ManutenzioniViewModel` — Nuovi metodi

**File:** `ManutenzioniViewModel.kt`

```kotlin
/** Aggiorna il numero di copie richieste */
fun setNumberOfCopies(n: Int) {
    _uiState.update { it.copy(numberOfCopies = n.coerceIn(1, 99)) }
}

/** Genera N copie del PDF in una cartella scelta dall'utente */
fun generateBatchPdf() {
    val state = _uiState.value
    val impianto = state.selectedImpianto ?: return
    val frequenza = state.selectedFrequenza ?: return
    val copies = state.numberOfCopies

    // ── Directory picker via AWT FileDialog ──
    // Usiamo JFileChooser in modalità DIRECTORIES_ONLY
    // perché FileDialog non supporta nativamente la selezione cartelle
    val chooser = javax.swing.JFileChooser().apply {
        dialogTitle = "Scegli cartella di destinazione"
        fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        approveButtonText = "Genera qui"
    }

    val result = chooser.showOpenDialog(null)
    if (result != javax.swing.JFileChooser.APPROVE_OPTION) return

    val outputDir = chooser.selectedFile
    if (!outputDir.exists()) outputDir.mkdirs()

    // ── Generazione asincrona ──
    scope.launch {
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                batchResult = null,
                batchProgress = BatchProgress(current = 0, total = copies),
                statusMessage = "Generazione $copies copie in corso..."
            )
        }

        try {
            val batchResult = pdfStrategy.generateBatch(
                impianto = impianto,
                frequenza = frequenza,
                outputDir = outputDir,
                copies = copies,
                clienteNome = state.selectedCliente?.nome,
                onProgress = { current, total ->
                    _uiState.update {
                        it.copy(
                            batchProgress = BatchProgress(current, total),
                            statusMessage = "Generazione copia $current di $total..."
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    pdfFile = batchResult.generatedFiles.firstOrNull(),
                    batchResult = batchResult,
                    batchProgress = null,
                    isLoading = false,
                    statusMessage = if (batchResult.isFullSuccess) {
                        "✓ ${batchResult.successCount} PDF generati in ${outputDir.name}/"
                    } else {
                        "⚠ ${batchResult.successCount}/${batchResult.totalRequested} PDF generati (${batchResult.failureCount} errori)"
                    },
                    viewMode = ViewMode.PDF_PREVIEW
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    batchProgress = null,
                    errorMessage = "Errore generazione batch: ${e.message}"
                )
            }
        }
    }
}

/** Apre la cartella contenente i PDF generati nel file manager di sistema */
fun openBatchFolder() {
    val dir = _uiState.value.batchResult?.generatedFiles?.firstOrNull()?.parentFile ?: return
    try {
        java.awt.Desktop.getDesktop().open(dir)
    } catch (e: Exception) {
        _uiState.update {
            it.copy(errorMessage = "Impossibile aprire la cartella: ${e.message}")
        }
    }
}
```

**Perché `JFileChooser` e non `FileDialog`:**

| | `FileDialog` (AWT) | `JFileChooser` (Swing) |
|---|---|---|
| Selezione file | ✅ | ✅ |
| Selezione cartella | ❌ Non nativo | ✅ `DIRECTORIES_ONLY` |
| Look & feel nativo | ✅ | Accettabile su Desktop |

L'utente deve scegliere una **cartella**, non un file. `FileDialog` non ha una modalità
cartella su tutti i sistemi operativi; `JFileChooser.DIRECTORIES_ONLY` è la soluzione standard.

**Perché il dialog è nel ViewModel e non nel Composable:**

- Il ViewModel attuale (`generatePdf()`) usa già `FileDialog` direttamente.
- Mantenere la consistenza con il pattern esistente.
- Il dialog AWT/Swing è **bloccante** (sincronizzato con il thread chiamante), quindi
  si apre **prima** del `scope.launch` per non bloccare la coroutine.
- Se in futuro si volesse disaccoppiare ulteriormente, si potrebbe estrarre un'interfaccia
  `FilePickerPort` nel domain. Ma per questa feature è over-engineering.

**Perché `generateBatchPdf()` è un metodo separato da `generatePdf()`:**

- `generatePdf()` è un flusso consolidato (v2.1) → non si tocca.
- In futuro, la Sidebar può avere **un unico bottone** che chiama `generateBatchPdf()`
  anche per N=1. Ma **durante la transizione** è più sicuro avere entrambi i metodi.
- Quando il flusso batch sarà stabile e testato, `generatePdf()` diventa un wrapper:
  `fun generatePdf() = generateBatchPdf()` — e poi si depreca.

#### 4C. `Sidebar.kt` — Input numero copie + bottone aggiornato

**Modifiche alla Sidebar:**

1. **Nuovo parametro callback** nella firma di `Sidebar`:
   ```kotlin
   onNumberOfCopiesChanged: (Int) -> Unit,
   onGeneraBatchPdf: () -> Unit,
   ```

2. **Nuovo composable `CopiesSelector`** — sotto il dropdown Frequenza:

```kotlin
/**
 * Selettore del numero di copie con bottoni +/-.
 * Valore minimo: 1, massimo: 99.
 */
@Composable
private fun CopiesSelector(
    copies: Int,
    enabled: Boolean,
    onChanged: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Copie:",
            style = MaterialTheme.typography.subtitle2,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(50.dp)
        )

        IconButton(
            onClick = { onChanged((copies - 1).coerceAtLeast(1)) },
            enabled = enabled && copies > 1,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowDown, "Diminuisci")
        }

        OutlinedTextField(
            value = copies.toString(),
            onValueChange = { text ->
                text.toIntOrNull()?.let { onChanged(it.coerceIn(1, 99)) }
            },
            modifier = Modifier.width(56.dp),
            enabled = enabled,
            textStyle = LocalTextStyle.current.copy(
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            ),
            singleLine = true
        )

        IconButton(
            onClick = { onChanged((copies + 1).coerceAtMost(99)) },
            enabled = enabled && copies < 99,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Default.KeyboardArrowUp, "Aumenta")
        }
    }
}
```

3. **Testo del bottone dinamico:**

```kotlin
Button(onClick = { onGeneraBatchPdf() }, ...) {
    Icon(Icons.Default.Build, ...)
    Spacer(Modifier.width(8.dp))
    Text(
        if (uiState.numberOfCopies == 1) "Genera PDF"
        else "Genera ${uiState.numberOfCopies} PDF"
    )
}
```

4. **Progress bar** (visibile durante la generazione batch):

```kotlin
uiState.batchProgress?.let { progress ->
    Column {
        LinearProgressIndicator(
            progress = progress.percentage,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = "Copia ${progress.current} di ${progress.total}",
            fontSize = 10.sp,
            color = Color.Gray
        )
    }
}
```

5. **Bottone "Apri Cartella"** (visibile dopo batch):

```kotlin
if (uiState.batchResult != null) {
    OutlinedButton(
        onClick = onOpenBatchFolder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.FolderOpen, ...)
        Spacer(Modifier.width(8.dp))
        Text("Apri Cartella")
    }
}
```

#### Layout Sidebar aggiornato — Flusso visivo

```
┌─────────────────────────┐
│  Manutenzioni Maker     │
├─────────────────────────┤
│  Cliente                │
│  [▼ ACME S.r.l.      ] │
├─────────────────────────┤
│  Impianto               │
│  [▼ GE — Gruppo Ele. ] │
│                         │
│  Frequenza              │
│  [▼ 6 Mesi           ] │
│                         │
│  Copie:  [−] [ 4 ] [+] │  ◄── NUOVO
├─────────────────────────┤
│  ┌───────────────────┐  │
│  │ 12 attività       │  │
│  │ incluse           │  │
│  └───────────────────┘  │
│                         │
│  ┌───────────────────┐  │
│  │ 🔨 Genera 4 PDF   │  │  ◄── Testo dinamico
│  └───────────────────┘  │
│  ████████████░░░░ 3/4   │  ◄── NUOVO: progress bar
│                         │
│  ┌───────────────────┐  │
│  │ 📂 Apri Cartella  │  │  ◄── NUOVO: dopo batch
│  └───────────────────┘  │
├─────────────────────────┤
│  Vista                  │
│  ◉ Anteprima            │
│  ○ Editor Impianto      │
└─────────────────────────┘
```

#### 4D. `MainContent.kt` — Riepilogo batch nel pannello anteprima

Nella sezione `PdfPreviewPanel`, dopo la card del PDF singolo, aggiungere un riepilogo batch:

```kotlin
// Card riepilogo batch (visibile solo se batchResult != null)
uiState.batchResult?.let { result ->
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = if (result.isFullSuccess) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (result.isFullSuccess)
                    "✓ Tutti i ${result.successCount} PDF generati"
                else
                    "⚠ ${result.successCount}/${result.totalRequested} PDF generati",
                fontWeight = FontWeight.Bold,
                color = if (result.isFullSuccess) Color(0xFF2E7D32) else Color(0xFFE65100)
            )
            Spacer(Modifier.height(8.dp))

            // Lista file generati
            result.generatedFiles.forEach { file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, tint = Color(0xFF2E7D32), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = file.name, fontSize = 11.sp)
                }
            }

            // Lista errori
            result.errors.forEach { (index, msg) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Close, tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "Copia $index: $msg", fontSize = 11.sp, color = Color(0xFFD32F2F))
                }
            }
        }
    }
}
```

#### 4E. `App.kt` — Collegamento callback

**File:** `App.kt`

Aggiungere i nuovi callback nella chiamata a `Sidebar`:

```kotlin
Sidebar(
    uiState = uiState,
    onClienteSelected = viewModel::selectCliente,
    onAddCliente = viewModel::addCliente,
    onImpiantoSelected = viewModel::selectImpianto,
    onFrequenzaSelected = viewModel::selectFrequenza,
    onGeneraPdf = viewModel::generateBatchPdf,     // ← ora chiama il batch
    onOpenPdf = viewModel::openPdfInSystem,
    onOpenBatchFolder = viewModel::openBatchFolder, // ← NUOVO
    onNumberOfCopiesChanged = viewModel::setNumberOfCopies, // ← NUOVO
    onViewModeChanged = viewModel::setViewMode,
    modifier = ...
)
```

---

### STEP 5 — ✅ VALIDAZIONE

#### 5A. Compilazione

```bash
./gradlew :desktopApp:compileKotlinDesktop
```

Verifica che il build passi senza errori. Questo garantisce che:
- L'interfaccia `PdfGeneratorStrategy` con il default method compila.
- `HtmlToPdfStrategy` continua a compilare senza override di `generateBatch()`.
- I nuovi campi in `ManutenzioniUiState` hanno tutti default → nessun call site rotto.

#### 5B. Retrocompatibilità JSON

Nessun campo è stato aggiunto a `ManutenzioniDatabase`, `Impianto`, `Attivita`, `Cliente`, o `Periodo`.
Il file `manutenzioni_db.json` è **completamente invariato**.

```
ignoreUnknownKeys = true   ← resta attivo
Nuovi campi nel DB         ← zero
```

#### 5C. CI/CD Pipeline

Il workflow `.github/workflows/release.yml` esegue:

```yaml
- name: 🏗️ Build installer & UberJar
  run: ./gradlew :desktopApp:${{ matrix.task }} :desktopApp:packageUberJarForCurrentOS
```

Dove `matrix.task` è uno tra `packageDmg`, `packageMsi`, `packageDeb`.

**Nessuna modifica necessaria al workflow:**

- Il build Gradle non cambia (nessuna nuova dipendenza).
- Il `mainClass` resta `manutenzioni.app.MainKt`.
- Il package nativo include automaticamente i nuovi file `.kt`.
- Il Fat JAR include automaticamente il nuovo `BatchResult.kt`.

---

## 5. 🔄 Diagramma Flusso Completo — Dopo l'Implementazione

```
┌──────────┐     setNumberOfCopies(4)      ┌─────────────────────────┐
│ Sidebar  │──────────────────────────────▶│    ViewModel            │
│          │                               │    _uiState.update {    │
│ [Copie:4]│     generateBatchPdf()        │      numberOfCopies = 4 │
│ [Genera] │──────────────────────────────▶│    }                    │
└──────────┘                               └──────────┬──────────────┘
                                                      │
                                           JFileChooser (DIRECTORIES_ONLY)
                                           Utente sceglie: /Desktop/Schede/
                                                      │
                                                      ▼
                                           ┌─────────────────────────────────┐
                                           │  scope.launch {                 │
                                           │    pdfStrategy.generateBatch(   │
                                           │      impianto, frequenza,       │
                                           │      outputDir, copies=4,       │
                                           │      clienteNome,               │
                                           │      onProgress = { i, n ->     │
                                           │        _uiState.update {        │
                                           │          batchProgress(i, n)    │
                                           │        }                        │
                                           │      }                          │
                                           │    )                            │
                                           │  }                              │
                                           └──────────┬──────────────────────┘
                                                      │
                                                      ▼
                                           ┌──────────────────────────────┐
                                           │  PdfGeneratorStrategy        │
                                           │  .generateBatch() default    │
                                           │                              │
                                           │  for (i in 1..4) {          │
                                           │    generate(                 │
                                           │      impianto, frequenza,    │
                                           │      "GE_6_Mesi_copia_$i",  │
                                           │      clienteNome             │
                                           │    )  ← contratto invariato  │
                                           │    onProgress(i, 4)          │
                                           │  }                           │
                                           │                              │
                                           │  return BatchResult(files,4) │
                                           └──────────┬───────────────────┘
                                                      │
                                                      ▼
                                           /Desktop/Schede/
                                           ├── GE_6_Mesi_copia_1.pdf
                                           ├── GE_6_Mesi_copia_2.pdf
                                           ├── GE_6_Mesi_copia_3.pdf
                                           └── GE_6_Mesi_copia_4.pdf
```

---

## 6. 📊 Matrice di Impatto per File

| File | Layer | Modifica | Breaking Change |
|---|---|---|---|
| `domain/model/BatchResult.kt` | Domain | **NUOVO** file | ❌ No |
| `domain/strategy/PdfGeneratorStrategy.kt` | Domain | Aggiunta `generateBatch()` con default | ❌ No — default method |
| `app/strategy/HtmlToPdfStrategy.kt` | Strategy | **NESSUNA** modifica | ❌ No |
| `app/service/HtmlService.kt` | Service | **NESSUNA** modifica | ❌ No |
| `app/service/Pdf.kt` | Service | **NESSUNA** modifica | ❌ No |
| `app/data/ManutenzioneModels.kt` | Data | **NESSUNA** modifica | ❌ No |
| `app/data/JsonManutenzioneRepository.kt` | Data | **NESSUNA** modifica | ❌ No |
| `domain/service/FrequencyFilter.kt` | Domain | **NESSUNA** modifica ⭐ | ❌ No |
| `app/ui/ManutenzioniViewModel.kt` | UI | Nuovi campi UiState + metodi | ❌ No — tutti i default |
| `app/ui/Sidebar.kt` | UI | Nuovi parametri + `CopiesSelector` | ❌ No — nuovi parametri |
| `app/ui/MainContent.kt` | UI | Riepilogo batch in `PdfPreviewPanel` | ❌ No — aggiunta visiva |
| `app/ui/App.kt` | UI | Nuovi callback collegati | ❌ No |
| `manutenzioni_db.json` | Risorsa | **NESSUNA** modifica | ❌ No |
| `.github/workflows/release.yml` | CI/CD | **NESSUNA** modifica | ❌ No |

**Risultato: 0 breaking change. 0 contratti rotti. 0 file di dati modificati.**

---

## 7. ⚠️ Rischi e Mitigazioni

| Rischio | Probabilità | Impatto | Mitigazione |
|---|---|---|---|
| **iText7 errore su copia N** (es. file locked dal SO) | Bassa | Medio | `BatchResult.errors` cattura l'errore per-copia; le altre copie continuano |
| **Disco pieno durante batch** | Bassa | Alto | Il `catch` in `generateBatch()` interrompe il loop; la UI mostra quante copie sono riuscite |
| **Utente inserisce 99 copie** (generazione lenta) | Media | Basso | Progress bar con `LinearProgressIndicator` + StatusBar "Copia X di Y" |
| **`JFileChooser` brutto su macOS** | Certa | Basso | Accettabile per directory picker. Se necessario, si può usare `apple.awt.fileDialogForDirectories` come workaround macOS |
| **Thread-safety: `_uiState.update` dalla callback `onProgress`** | Media | Medio | `_uiState` è un `MutableStateFlow` — `update {}` è **già thread-safe** (atomic read-modify-write) |

---

## 8. 🛤️ Sequenza di Implementazione Raccomandata

```
Giorno 1:
  ├── Creare domain/model/BatchResult.kt
  ├── Aggiungere generateBatch() in PdfGeneratorStrategy.kt
  └── ./gradlew :desktopApp:compileKotlinDesktop ✓

Giorno 2:
  ├── Aggiungere campi a ManutenzioniUiState (numberOfCopies, batchResult, batchProgress)
  ├── Implementare setNumberOfCopies() e generateBatchPdf() nel ViewModel
  └── ./gradlew :desktopApp:compileKotlinDesktop ✓

Giorno 3:
  ├── Creare CopiesSelector composable in Sidebar.kt
  ├── Aggiornare il bottone "Genera PDF" con testo dinamico
  ├── Aggiungere progress bar e bottone "Apri Cartella"
  ├── Collegare callback in App.kt
  └── ./gradlew :desktopApp:run → test manuale

Giorno 4:
  ├── Aggiornare PdfPreviewPanel in MainContent.kt (riepilogo batch)
  ├── Test end-to-end: 1 copia, 5 copie, annullamento dialog
  └── ./gradlew :desktopApp:compileKotlinDesktop ✓ → commit & push
```

---

## 9. 🔮 Evoluzioni Future

Questa feature è progettata come **fondamenta** per scenari più complessi:

| Evoluzione | Come si estende |
|---|---|
| **Batch multi-frequenza** (1 impianto, N frequenze) | `generateBatch()` accetta `List<Periodo>` → loop su frequenze |
| **Batch multi-impianto** (N impianti, 1 frequenza) | `generateBatch()` accetta `List<Impianto>` → loop su impianti |
| **Matrice completa** (N impianti × M frequenze × K copie) | `BatchRequest` sealed class con varianti (v3.0) |
| **Upload automatico su Google Drive** | `BatchResult.generatedFiles` → Google Drive API (v3.1 roadmap) |
| **Naming personalizzato** | Parametro `namingStrategy: (Int) -> String` in `generateBatch()` |

Tutte queste evoluzioni non richiedono modifiche a `generate()`, `FrequencyFilter`, o al modello dati.
La **Stella Polare** resta intatta.

