package manutenzioni.domain.service

/**
 * Contratto di dominio per la generazione del codice HTML della scheda di manutenzione.
 */
interface Html {

    /**
     * Genera l'HTML completo utilizzando l'oggetto di contesto unificato [HtmlRenderContext].
     *
     * @param context L'oggetto contesto contenente tutti i parametri e metadati
     * @return Il contenuto HTML come stringa
     */
    fun buildHtml(context: HtmlRenderContext): String
}