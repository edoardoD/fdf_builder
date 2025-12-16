/*
 * Programma per convertire file HTML in PDF formato A4 con AcroForm compilabile
 */
package fdf_builder;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.forms.PdfAcroForm;
import com.itextpdf.kernel.pdf.PdfDocument;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class App {
    
    /**
     * Converte un file HTML in PDF formato A4 con AcroForm compilabile
     * @param htmlFilePath percorso del file HTML da convertire
     * @param pdfFilePath percorso del file PDF di output
     */
    public static void htmlToPdf(String htmlFilePath, String pdfFilePath) {
        try {
            // Verificare che il file HTML esista
            File htmlFile = new File(htmlFilePath);
            if (!htmlFile.exists()) {
                System.err.println("Errore: File HTML non trovato: " + htmlFilePath);
                return;
            }
            
            // Caricare il file HTML
            FileInputStream htmlInput = new FileInputStream(htmlFile);
            
            // Creare il writer PDF
            PdfWriter writer = new PdfWriter(new FileOutputStream(pdfFilePath));
            PdfDocument pdfDoc = new PdfDocument(writer);
            
            // Configurare le proprietà di conversione per supportare i form
            ConverterProperties converterProperties = new ConverterProperties();
            
            // Abilitare la conversione dei tag form in AcroForm
            converterProperties.setCreateAcroForm(true);
            
            // Convertire HTML a PDF con pagina A4 e AcroForm
            HtmlConverter.convertToPdf(
                htmlInput,
                pdfDoc,
                converterProperties
            );
            
            // Assicurare che l'AcroForm sia creato correttamente
            PdfAcroForm acroForm = PdfAcroForm.getAcroForm(pdfDoc, true);
            
            pdfDoc.close();
            htmlInput.close();
            
            System.out.println("✓ Conversione completata con successo!");
            System.out.println("  File PDF compilabile (AcroForm): " + pdfFilePath);
            
        } catch (Exception e) {
            System.err.println("Errore durante la conversione: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        String htmlFilePath;
        String pdfFilePath;
        
        if (args.length >= 2) {
            // Usare i parametri da linea di comando
            htmlFilePath = args[0];
            pdfFilePath = args[1];
        } else {
            // Usare i default (file nella radice del progetto)
            htmlFilePath = "schelettro_v2.html";
            pdfFilePath = "output.pdf";
            System.out.println("Utilizzo: java -jar app.jar <html_file> <pdf_file>");
            System.out.println("Usando default: " + htmlFilePath + " -> " + pdfFilePath);
        }

       
        
        htmlToPdf(htmlFilePath, pdfFilePath);
    }
}
