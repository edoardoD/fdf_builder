package manutenzioni.app.strategy

import manutenzioni.app.service.HtmlService
import manutenzioni.app.service.Pdf
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.CrossPlantAttivitaResolver
import manutenzioni.domain.service.HtmlRenderContext
import manutenzioni.domain.strategy.AbstractPdfGeneratorStrategy
import java.io.File

/**
 * Strategia concreta: HTML → PDF con AcroForm.
 *
 * Estende [AbstractPdfGeneratorStrategy] che fornisce la logica batch
 * (loop, naming progressivo, progress callback, error handling).
 * Questa classe implementa solo il metodo `generate()` per produrre un singolo PDF.
 *
 * Orchestra il flusso completo:
 * 1. CrossPlantAttivitaResolver filtra le attività per frequenza e contesto cantiere
 * 2. HtmlService genera l'HTML dinamico dal template
 * 3. Pdf (iText7 html2pdf) converte l'HTML in PDF con campi compilabili
 */
class HtmlToPdfStrategy(
    private val htmlService: HtmlService = HtmlService(),
    private val pdfEngine: Pdf = Pdf()
) : AbstractPdfGeneratorStrategy() {

    override fun generate(
        impianto: Impianto,
        frequenza: Periodo,
        outputPath: String,
        clienteNome: String?,
        contextImpianti: List<Impianto>,
        cantiereNome: String?,
        nomeManutentore: String?,
        firmaManutentore: String?,
        dataInizioContratto: String?,
        dataIntervento: String?,
        dataInterventoPrecedente: String?
    ): File {
        // 1. Filtra attività per frequenza inclusiva e componenti del cantiere
        val attivitaFiltrate = CrossPlantAttivitaResolver.resolveAttivita(
            impianto = impianto,
            impiantiNelCantiere = contextImpianti,
            frequenza = frequenza
        )

        if (attivitaFiltrate.isEmpty()) {
            throw IllegalStateException(
                "Nessuna attività trovata per ${impianto.nomeCompleto} con frequenza ${frequenza.label()}"
            )
        }

        // 2. Genera HTML dinamico tramite HtmlRenderContext
        val htmlContent = htmlService.buildHtml(
            HtmlRenderContext(
                impianto = impianto,
                frequenza = frequenza,
                attivita = attivitaFiltrate,
                clienteNome = clienteNome,
                cantiereNome = cantiereNome,
                nomeManutentore = nomeManutentore,
                firmaTecnicoBase64 = firmaManutentore,
                dataInizioContratto = dataInizioContratto,
                dataIntervento = dataIntervento,
                dataInterventoPrecedente = dataInterventoPrecedente,
                contextImpianti = contextImpianti
            )
        )

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

