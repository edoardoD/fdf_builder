package manutenzioni.domain.strategy

import manutenzioni.app.data.Impianto
import manutenzioni.app.data.Periodo
import java.io.File

/**
 * Strategy Pattern: interfaccia per la generazione del PDF.
 *
 * Disaccoppia la logica di generazione dalla UI, permettendo
 * di sostituire il motore di rendering (HTML→PDF, iText diretto, ecc.)
 * senza modificare il codice chiamante.
 */
interface PdfGeneratorStrategy {

    /**
     * Genera un PDF per l'impianto dato con la frequenza selezionata.
     *
     * @param impianto L'impianto selezionato
     * @param frequenza La frequenza di manutenzione selezionata
     * @param outputPath Il percorso del file PDF di output
     * @return Il file PDF generato
     */
    fun generate(impianto: Impianto, frequenza: Periodo, outputPath: String): File
}

