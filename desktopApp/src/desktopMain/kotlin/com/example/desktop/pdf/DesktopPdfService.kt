package com.example.desktop.pdf

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

class DesktopPdfService {
    fun htmlToPdf(htmlFilePath: String, pdfFilePath: String) {
        try {
            val htmlFile = File(htmlFilePath)

            // Controllo esistenza file (Sintassi più pulita)
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
