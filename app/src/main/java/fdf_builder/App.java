/*
 * Programma per convertire file HTML in PDF formato A4
 */
package fdf_builder;

import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.geom.PageSize;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class App {
    
    /**
     * Converte un file HTML in PDF formato A4
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
            
            // Convertire HTML a PDF con pagina A4
            HtmlConverter.convertToPdf(
                htmlInput,
                writer,
                null  // ConverterProperties con null usa i default
            );
            
            htmlInput.close();
            
            System.out.println("✓ Conversione completata con successo!");
            System.out.println("  File PDF: " + pdfFilePath);
            
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
