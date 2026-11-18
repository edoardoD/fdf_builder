package manutenzioni.domain.strategy

import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.BatchResult
import java.io.File

/**
 * Contratto pubblico per la generazione di PDF.
 *
 * Espone **un unico punto d'ingresso**: `generateBatch(...)`.
 * Il caso singolo (1 copia) è un sottoinsieme naturale di N copie con `copies = 1`.
 *
 * Incapsula il dettaglio implementativo di `generate()` all'interno
 * della classe astratta `AbstractPdfGeneratorStrategy`.
 *
 * @see AbstractPdfGeneratorStrategy per l'implementazione base
 */
interface PdfBatchGenerator {

    /**
     * Genera N copie identiche dello stesso PDF in una directory.
     *
     * @param impianto L'impianto selezionato
     * @param frequenza La frequenza di manutenzione
     * @param outputDir La directory di destinazione
     * @param copies Il numero di copie da generare (>= 1)
     * @param clienteNome Il nome del cliente da inserire nell'header del PDF
     * @param onProgress Callback per il progresso (copiaCorrente, totale)
     * @return BatchResult con i file generati e gli eventuali errori
     */
    fun generateBatch(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String? = null,
        contextImpianti: List<Impianto> = emptyList(), // Impianti di contesto (tutti quelli del cantiere) per filtraggio avanzato
        cantiereNome: String? = null,
        nomeManutentore: String? = null,
        firmaManutentore: String? = null,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        dataInizioContratto: String? = null,
        dataIntervento: String? = null,
        dataInterventoPrecedente: String? = null
    ): BatchResult

    /**
     * Generazione batch asincrona non bloccante su coroutine.
     */
    suspend fun generateBatchAsync(
        impianto: Impianto,
        frequenza: Periodo,
        outputDir: File,
        copies: Int,
        clienteNome: String? = null,
        contextImpianti: List<Impianto> = emptyList(),
        cantiereNome: String? = null,
        nomeManutentore: String? = null,
        firmaManutentore: String? = null,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
        dataInizioContratto: String? = null,
        dataIntervento: String? = null,
        dataInterventoPrecedente: String? = null
    ): BatchResult = generateBatch(
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
