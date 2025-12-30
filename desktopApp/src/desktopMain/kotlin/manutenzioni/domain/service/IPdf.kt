package manutenzioni.domain.service

interface IPdf{
    /**
     * crea il pdf partendo da un file html*/
    fun buildPdf(htmlFilePath: String, pdfFilePath: String)
}