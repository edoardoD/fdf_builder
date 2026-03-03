package manutenzioni.domain.strategy

import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.domain.model.BatchResult
import java.io.File

/**
 * Strategy Pattern: interfaccia per la generazione del PDF.
 *
 * Disaccoppia la logica di generazione dalla UI, permettendo
 * di sostituire il motore di rendering (HTML→PDF, iText diretto, ecc.)
 * senza modificare il codice chiamante.
 *
 * @deprecated Sostituita da [PdfBatchGenerator] come contratto pubblico e
 * [AbstractPdfGeneratorStrategy] come classe base. Il metodo `generate()` è
 * ora incapsulato come `protected` in `AbstractPdfGeneratorStrategy`.
 * Usa `PdfBatchGenerator.generateBatch(copies = 1)` per generare un singolo PDF.
 *
 * @see PdfBatchGenerator
 * @see AbstractPdfGeneratorStrategy
 */
@Deprecated(
    message = "Usa PdfBatchGenerator come contratto pubblico. generate() è incapsulato in AbstractPdfGeneratorStrategy.",
    replaceWith = ReplaceWith("PdfBatchGenerator")
)
interface PdfGeneratorStrategy {

    /**
     * Genera un PDF per l'impianto dato con la frequenza selezionata.
     *
     * @param impianto L'impianto selezionato
     * @param frequenza La frequenza di manutenzione selezionata
     * @param outputPath Il percorso del file PDF di output
     * @param clienteNome Il nome del cliente da inserire nell'header del PDF
     * @return Il file PDF generato
     */
    fun generate(impianto: Impianto, frequenza: Periodo, outputPath: String, clienteNome: String? = null): File

    /**
     * Genera N copie identiche dello stesso PDF in una directory.
     *
     * La default implementation itera su generate() N volte,
     * creando file con naming progressivo: {COD}_{FREQ}_copia_{i}.pdf
     * Se copies == 1, il nome è identico al flusso singolo: {COD}_{FREQ}.pdf
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

