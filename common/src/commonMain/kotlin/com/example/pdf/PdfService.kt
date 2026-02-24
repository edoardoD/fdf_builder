package com.example.pdf

expect class PdfService() {
    fun fillAcroForm(templatePath: String, outputPath: String, fieldValues: Map<String, String>)
}
