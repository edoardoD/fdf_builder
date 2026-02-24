package manutenzioni.app.strategy

import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import manutenzioni.app.service.HtmlService
import manutenzioni.app.service.Pdf
import manutenzioni.domain.service.FrequencyFilter
import manutenzioni.domain.strategy.PdfGeneratorStrategy
import java.io.File

/**
 * Strategia concreta: HTML → PDF con AcroForm.
 *
 * Orchestra il flusso completo:
 * 1. FrequencyFilter filtra le attività per la frequenza selezionata
 * 2. HtmlService genera l'HTML dinamico dal template
 * 3. Pdf (iText7 html2pdf) converte l'HTML in PDF con campi compilabili
 */
class HtmlToPdfStrategy(
    private val htmlService: HtmlService = HtmlService(),
    private val pdfEngine: Pdf = Pdf()
) : PdfGeneratorStrategy {

    override fun generate(impianto: Impianto, frequenza: Periodo, outputPath: String, clienteNome: String?): File {
        // 1. Filtra attività per frequenza inclusiva
        val attivitaFiltrate = FrequencyFilter.filterByFrequenza(
            attivita = impianto.listaAttivita,
            frequenzaSelezionata = frequenza
        )

        if (attivitaFiltrate.isEmpty()) {
            throw IllegalStateException(
                "Nessuna attività trovata per ${impianto.nomeCompleto} con frequenza ${frequenza.label()}"
            )
        }

        // 2. Genera HTML dinamico (con dati cliente)
        val htmlContent = htmlService.buildHtml(impianto, attivitaFiltrate, frequenza, clienteNome)

        // 3. Scrivi HTML temporaneo
        val tempHtml = File.createTempFile("manutenzione_${impianto.codIntervento}_", ".html")
        tempHtml.writeText(htmlContent)

        try {
            // 4. Converti HTML → PDF
            pdfEngine.buildPdf(tempHtml.absolutePath, outputPath)
        } finally {
            // 5. Pulizia file temporaneo
            tempHtml.delete()
        }

        val outputFile = File(outputPath)
        if (!outputFile.exists()) {
            throw IllegalStateException("Errore nella generazione del PDF: file non creato")
        }

        println("✓ PDF generato: $outputPath (${attivitaFiltrate.size} attività)")
        return outputFile
    }
}

