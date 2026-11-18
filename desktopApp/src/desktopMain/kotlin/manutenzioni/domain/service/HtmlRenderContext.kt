package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.Periodo

/**
 * Incapsula l'intero contesto e i parametri necessari per il rendering delle schede di manutenzione (Foglio 1 e Foglio 2+).
 *
 * @property impianto L'impianto o apparecchiatura oggetto della scheda
 * @property frequenza Periodicità di manutenzione selezionata (es. 6M, 1A)
 * @property attivita Lista canonica delle attività filtrate da includere nella tabella
 * @property clienteNome Ragione sociale o nominativo del cliente committente
 * @property cantiereNome Nome o ubicazione del cantiere/stabilimento
 * @property nomeManutentore Nome e cognome del tecnico verificatore/manutentore
 * @property firmaTecnicoBase64 Firma digitale del tecnico (Data URI Base64 o percorso file locale)
 * @property firmaClienteBase64 Firma del rappresentante del cliente (opzionale)
 * @property dataInizioContratto Data inizio contratto di manutenzione (campo starting_date)
 * @property dataIntervento Data effettiva dell'intervento (campo actual_date)
 * @property dataInterventoPrecedente Data dell'intervento precedente (campo previous_date)
 * @property contextImpianti Lista completa degli impianti presenti nel cantiere (per cross-plant discovery)
 * @property templateHtml Override opzionale per il template HTML grezzo (se omesso carica scheletro.html)
 */
data class HtmlRenderContext(
    val impianto: Impianto,
    val frequenza: Periodo,
    val attivita: List<Attivita> = emptyList(),
    val clienteNome: String? = null,
    val cantiereNome: String? = null,
    val nomeManutentore: String? = null,
    val firmaTecnicoBase64: String? = null,
    val firmaClienteBase64: String? = null,
    val dataInizioContratto: String? = null,
    val dataIntervento: String? = null,
    val dataInterventoPrecedente: String? = null,
    val contextImpianti: List<Impianto> = emptyList(),
    val templateHtml: String? = null
)
