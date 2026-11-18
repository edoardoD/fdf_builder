package manutenzioni.domain.strategy

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import manutenzioni.domain.model.BatchResult
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import java.io.File

/**
 * Classe astratta che implementa `PdfBatchGenerator` e fornisce la logica
 * di orchestrazione batch (loop, naming, progress, error collection) confinata su [ioDispatcher].
 *
 * Le sottoclassi devono implementare solo `generate(...)` — il metodo
 * che sa produrre un singolo file PDF. `generate` è **protected**: non
 * è parte del contratto pubblico e non deve essere chiamato dai client
 * (ViewModel, Controller, ecc.).
 *
 * Vantaggi dell'incapsulamento:
 * - Il client ha un unico punto d'ingresso (`generateBatch`), sia per 1 sia per N copie.
 * - La logica di naming, error handling e progress reporting è centralizzata.
 * - Le sottoclassi possono ottimizzare `generate` senza impattare il contratto pubblico.
 *
 * @see PdfBatchGenerator interfaccia pubblica
 */
abstract class AbstractPdfGeneratorStrategy(
    protected val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : PdfBatchGenerator {

    /**
     * Genera un singolo PDF per l'impianto dato.
     *
     * **Visibilità protected:** non fa parte del contratto pubblico.
     * I client devono usare `generateBatch(copies = 1)` per generare un singolo file.
     *
     * @param impianto L'impianto selezionato
     * @param frequenza La frequenza di manutenzione selezionata
     * @param outputPath Il percorso del file PDF di output
     * @param clienteNome Il nome del cliente da inserire nell'header del PDF
     * @return Il file PDF generato
     */
    protected abstract fun generate(
        impianto: Impianto,
        frequenza: Periodo,
        outputPath: String,
        clienteNome: String? = null,
        contextImpianti: List<Impianto> = emptyList(),
        cantiereNome: String? = null,
        nomeManutentore: String? = null,
        firmaManutentore: String? = null,
        dataInizioContratto: String? = null,
        dataIntervento: String? = null,
        dataInterventoPrecedente: String? = null
    ): File

    /**
     * Implementazione batch sincrona: delega a [generateBatchAsync] eseguendolo
     * all'interno del contesto [ioDispatcher].
     */
    override fun generateBatch(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String?,
        contextImpianti: List<Impianto>,
        cantiereNome: String?,
        nomeManutentore: String?,
        firmaManutentore: String?,
        onProgress: (current: Int, total: Int) -> Unit,
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?
    ): BatchResult = runBlocking(ioDispatcher) {
        generateBatchAsync(
            impianto = impianto,
            frequenza = frequenza,
            outputDir = outputDir,
            copies = copies,
            clienteNome = clienteNome,
            contextImpianti = contextImpianti,
            cantiereNome = cantiereNome,
            nomeManutentore = nomeManutentore,
            firmaManutentore = firmaManutentore,
            onProgress = onProgress,
            dataInizioContratto = dataInizioContratto,
            dataIntervento = dataIntervento,
            dataInterventoPrecedente = dataInterventoPrecedente
        )
    }

    /**
     * Implementazione batch sospesa confinata su [ioDispatcher]: itera su `generate()` N volte, gestendo
     * naming progressivo, callback di progresso ed errori parziali.
     *
     * Naming standard (R22): `{SIGLA_O_COD}_{PERIODICITA}.pdf` (es. `QG_06.pdf`, `Q1_06.pdf`, `QG_1.pdf`, `GE_06.pdf`).
     * Se `copies > 1`, ogni file ha il suffisso `_copia_{i}`.
     */
    override suspend fun generateBatchAsync(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String?,
        contextImpianti: List<Impianto>,
        cantiereNome: String?,
        nomeManutentore: String?,
        firmaManutentore: String?,
        onProgress: (current: Int, total: Int) -> Unit,
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?
    ): BatchResult = withContext(ioDispatcher) {
        require(copies >= 1) { "Il numero di copie deve essere >= 1" }
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }

        val prefix = if (impianto is QuadroBT && impianto.sigla.isNotBlank()) {
            impianto.sigla.trim().replace(" ", "_").replace("/", "-").replace("\\", "-")
        } else {
            impianto.codIntervento.trim().replace(" ", "_")
        }
        val baseName = "${prefix}_${frequenza.codiceCartiglio()}"
        val generatedFiles = mutableListOf<File>()
        val errors = mutableMapOf<Int, String>()

        for (i in 1..copies) {
            try {
                val fileName = if (copies == 1) "$baseName.pdf" else "${baseName}_copia_$i.pdf"
                val outputPath = File(outputDir, fileName).absolutePath
                val file = generate(
                    impianto = impianto,
                    frequenza = frequenza,
                    outputPath = outputPath,
                    clienteNome = clienteNome,
                    contextImpianti = contextImpianti,
                    cantiereNome = cantiereNome,
                    nomeManutentore = nomeManutentore,
                    firmaManutentore = firmaManutentore,
                    dataInizioContratto = dataInizioContratto,
                    dataIntervento = dataIntervento,
                    dataInterventoPrecedente = dataInterventoPrecedente
                )
                generatedFiles.add(file)
            } catch (e: Exception) {
                errors[i] = e.message ?: "Errore sconosciuto"
            }
            onProgress(i, copies)
        }

        BatchResult(
            generatedFiles = generatedFiles,
            totalRequested = copies,
            errors = errors
        )
    }
}
