package manutenzioni.domain.strategy

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.BatchResult
import java.io.File

/**
 * Classe astratta che implementa `PdfBatchGenerator` e fornisce la logica
 * di orchestrazione batch (loop, naming, progress, error collection).
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
abstract class AbstractPdfGeneratorStrategy : PdfBatchGenerator {

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
        dataInizioIntervento: String? = null,
        nomeManutentore: String? = null,
        firmaManutentore: String? = null,
        dataInizioContratto: String? = null,
        dataIntervento: String? = null,
        dataInterventoPrecedente: String? = null
    ): File

    /**
     * Implementazione batch: itera su `generate()` N volte, gestendo
     * naming progressivo, callback di progresso ed errori parziali.
     *
     * Naming standard (R22): `{SIGLA_O_COD}_{PERIODICITA}.pdf` (es. `QG_06.pdf`, `Q1_06.pdf`, `QG_1.pdf`, `GE_06.pdf`).
     * Se `copies > 1`, ogni file ha il suffisso `_copia_{i}`.
     */
    override fun generateBatch(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String?,
        contextImpianti: List<Impianto>,
        cantiereNome: String?,
        dataInizioIntervento: String?,
        nomeManutentore: String?,
        firmaManutentore: String?,
        onProgress: (current: Int, total: Int) -> Unit,
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?
    ): BatchResult {
        require(copies >= 1) { "Il numero di copie deve essere >= 1" }

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
                val fileName = if (copies == 1) {
                    "$baseName.pdf"
                } else {
                    "${baseName}_copia_$i.pdf"
                }
                val outputPath = File(outputDir, fileName).absolutePath
                val file = generate(
                    impianto = impianto,
                    frequenza = frequenza,
                    outputPath = outputPath,
                    clienteNome = clienteNome,
                    contextImpianti = contextImpianti,
                    cantiereNome = cantiereNome,
                    dataInizioIntervento = dataInizioIntervento,
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

        return BatchResult(
            generatedFiles = generatedFiles,
            totalRequested = copies,
            errors = errors
        )
    }
}
