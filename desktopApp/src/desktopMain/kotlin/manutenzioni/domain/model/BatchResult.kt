package manutenzioni.domain.model

import java.io.File;
/**
 * Risultato di una generazione batch di N copie PDF.
 *
 * @param generatedFiles Lista dei file generati con successo
 * @param totalRequested Numero totale di copie richieste
 * @param errors Mappa indice-copia → messaggio errore (per i falliti)
 */

data class BatchResult(
    val generatedFiles: List<File>,
    val totalRequested: Int,
    val errors: Map<Int, String> = emptyMap()
){
    /** Numero di copie generate con successo */
    val successCount: Int get() = generatedFiles.size

    /** Numero di copie fallite */
    val failureCount: Int get() = errors.size

    /** true se tutte le copie sono state generate */
    val isFullSuccess: Boolean get() = errors.isEmpty()
}
