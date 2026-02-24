package manutenzioni.app.service

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import manutenzioni.domain.service.IPdf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

data class PdfConfig(
    val templatePath: String = "scheletro.html",
    val outputPath: String
)

class Pdf : IPdf {
    override fun buildPdf(htmlFilePath: String, pdfFilePath: String) {
        try {
            val htmlFile = File(htmlFilePath)

            // Sarà necessario fornire dati giusti all'observer per far sì
            // che possa essere mostrato nella GUI l'errore
            if (!htmlFile.exists()) {
                println("Errore: File HTML non trovato: $htmlFilePath")
                return
            }

            // 'use' chiude automaticamente htmlInput e il PDF alla fine del blocco
            FileInputStream(htmlFile).use { htmlInput ->
                PdfWriter(FileOutputStream(pdfFilePath)).use { writer ->
                    val pdfDoc = PdfDocument(writer)

                    // Configurazione proprietà
                    val converterProperties = ConverterProperties().apply {
                        setCreateAcroForm(true) // Abilita i form compilabili
                    }

                    // Esecuzione conversione
                    HtmlConverter.convertToPdf(htmlInput, pdfDoc, converterProperties)

                    // Nota: In iText 7, PdfDocument viene chiuso automaticamente dal writer.use
                    // amche qui bisognerà agigornare la lista degli observable in modo
                    // che il messaggio venga gestito correttamente dalla GUI
                    println("✓ Conversione completata con successo!")
                    println("  File PDF compilabile (AcroForm): $pdfFilePath")
                }
            }
        } catch (e: Exception) {

            println("Errore durante la conversione: ${e.message}")
            e.printStackTrace()
        }
    }
}
