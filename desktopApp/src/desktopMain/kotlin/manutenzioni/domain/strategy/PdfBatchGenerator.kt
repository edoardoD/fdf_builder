package manutenzioni.domain.strategy

import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.domain.model.BatchResult
import java.io.File

/**
 * Contratto pubblico per la generazione di PDF.
 *
 * Espone **un unico punto d'ingresso**: `generateBatch(...)`.
 * Il caso singolo (1 copia) è un sottoinsieme naturale di N copie con `copies = 1`.
 *
 * Questo contratto sostituisce la vecchia `PdfGeneratorStrategy` (ora deprecata)
 * e incapsula il dettaglio implementativo di `generate()` all'interno
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
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): BatchResult
}
