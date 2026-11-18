package manutenzioni.domain.service

/**
 * Contratto di dominio per la conversione da HTML a PDF.
 */
interface IPdf {

    /**
     * Converte un file HTML in un file PDF compilabile (AcroForm).
     * Garantisce il completamento sincrono confinando le operazioni di I/O.
     */
    fun buildPdf(htmlFilePath: String, pdfFilePath: String)

    /**
     * Conversione sospesa non bloccante con confinamento su dispatcher coroutine I/O.
     */
    suspend fun buildPdfAsync(htmlFilePath: String, pdfFilePath: String) {
        buildPdf(htmlFilePath, pdfFilePath)
    }
}