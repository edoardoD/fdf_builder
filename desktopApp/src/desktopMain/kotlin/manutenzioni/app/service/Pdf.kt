package manutenzioni.app.service

import com.itextpdf.html2pdf.ConverterProperties
import com.itextpdf.html2pdf.HtmlConverter
import com.itextpdf.html2pdf.resolver.font.DefaultFontProvider
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import manutenzioni.domain.service.IPdf
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Eccezione specifica di dominio lanciata in caso di errore durante la conversione PDF.
 */
class PdfGenerationException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/**
 * Motore iText7 html2pdf con supporto AcroForm interattivi.
 * Tutte le operazioni di I/O su file e generazione PDF sono confinate rigorosamente a [ioDispatcher].
 */
open class Pdf(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IPdf {

    override fun buildPdf(htmlFilePath: String, pdfFilePath: String) {
        runBlocking(ioDispatcher) {
            buildPdfAsync(htmlFilePath, pdfFilePath)
        }
    }

    override suspend fun buildPdfAsync(htmlFilePath: String, pdfFilePath: String): Unit = withContext(ioDispatcher) {
        val htmlFile = File(htmlFilePath)
        if (!htmlFile.exists() || !htmlFile.isFile) {
            throw IllegalArgumentException("File HTML sorgente non trovato: $htmlFilePath")
        }

        val pdfFile = File(pdfFilePath)
        pdfFile.parentFile?.mkdirs()

        try {
            FileInputStream(htmlFile).use { htmlInput ->
                PdfWriter(FileOutputStream(pdfFile)).use { writer ->
                    val pdfDoc = PdfDocument(writer)

                    val converterProperties = ConverterProperties().apply {
                        setCreateAcroForm(true)
                        setFontProvider(DefaultFontProvider(true, true, true))
                    }

                    val document = HtmlConverter.convertToDocument(htmlInput, pdfDoc, converterProperties)

                    // Post-processing AcroForm: rimuove i bordi interni predefiniti (BS/Border/C)
                    // dalle caselle di testo per garantire un'integrazione fluida con i bordi della tabella HTML,
                    // preservando i pulsanti radio.
                    val acroForm = com.itextpdf.forms.PdfAcroForm.getAcroForm(pdfDoc, false)
                    if (acroForm != null) {
                        for ((_, field) in acroForm.allFormFields) {
                            if (field !is com.itextpdf.forms.fields.PdfButtonFormField || !field.isRadio) {
                                for (widget in field.widgets) {
                                    widget.pdfObject.remove(com.itextpdf.kernel.pdf.PdfName.BS)
                                    widget.pdfObject.remove(com.itextpdf.kernel.pdf.PdfName.Border)
                                    widget.pdfObject.remove(com.itextpdf.kernel.pdf.PdfName.C)
                                }
                            }
                        }
                    }

                    document.close()
                }
            }
        } catch (e: Exception) {
            if (e is IllegalArgumentException) throw e
            throw PdfGenerationException("Errore durante la conversione PDF ($htmlFilePath -> $pdfFilePath): ${e.message}", e)
        }
    }
}
