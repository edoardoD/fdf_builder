package com.example.pdf

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.PdfWriter

actual class PdfService {
    actual fun fillAcroForm(templatePath: String, outputPath: String, fieldValues: Map<String, String>) {
        val reader = PdfReader(templatePath)
        val writer = PdfWriter(outputPath)
        val pdfDoc = PdfDocument(reader, writer)
        val form = PdfAcroForm.getAcroForm(pdfDoc, true)
        
        fieldValues.forEach { (key, value) ->
            form.getField(key)?.setValue(value)
        }
        
        form.flattenFields()
        pdfDoc.close()
    }
}
