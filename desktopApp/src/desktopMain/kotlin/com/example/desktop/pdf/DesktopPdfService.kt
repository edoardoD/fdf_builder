package com.example.desktop.pdf

import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import java.io.File

class DesktopPdfService {
    fun sampleCreatePdf(path: String) {
        val file = File(path)
        file.parentFile?.mkdirs()
        
        val writer = PdfWriter(path)
        val pdf = PdfDocument(writer)
        val document = Document(pdf)
        document.add(Paragraph("Hello iText7 from Kotlin Multiplatform Desktop!"))
        document.add(Paragraph("Generated at: ${java.util.Date()}"))
        document.close()
    }
}
