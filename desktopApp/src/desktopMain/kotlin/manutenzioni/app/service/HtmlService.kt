package manutenzioni.app.service

import manutenzioni.app.service.attachments.DefaultTechnicalAttachmentRegistry
import manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer
import manutenzioni.app.service.html.AttivitaTableHtmlBuilder
import manutenzioni.app.service.html.HtmlTemplateLoader
import manutenzioni.app.service.html.RiLibrettoHtmlBuilder
import manutenzioni.app.service.html.SchedaHeaderFormatter
import manutenzioni.app.service.html.SignatureImageRenderer
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.service.Html
import manutenzioni.domain.service.HtmlRenderContext
import manutenzioni.domain.service.attachments.TechnicalAttachmentRegistry

/**
 * Facade modulare e leggera per il template engine HTML che implementa il contratto di dominio [Html].
 *
 * Coordina i componenti specializzati:
 * - [HtmlTemplateLoader]: caricamento e cache in-memory di scheletro.html
 * - [SchedaHeaderFormatter]: formattazione cartiglio, date, cliente, sito e premessa
 * - [AttivitaTableHtmlBuilder]: ordinamento canonico, radio group AcroForm e griglia fissa 30 righe
 * - [SignatureImageRenderer]: rendering Data URI e decodifica firme da file
 * - [TechnicalAttachmentRegistry]: registro estensibile OCP dei fogli allegati tecnici (Foglio 2+)
 */
class HtmlService(
    private val templatePath: String = HtmlTemplateLoader.DEFAULT_TEMPLATE,
    private val templateLoader: HtmlTemplateLoader = HtmlTemplateLoader(templatePath),
    private val headerFormatter: SchedaHeaderFormatter = SchedaHeaderFormatter(),
    private val tableBuilder: AttivitaTableHtmlBuilder = AttivitaTableHtmlBuilder(),
    private val signatureRenderer: SignatureImageRenderer = SignatureImageRenderer(),
    private val attachmentRegistry: TechnicalAttachmentRegistry = DefaultTechnicalAttachmentRegistry.shared,
    private val sheetRenderer: TechnicalSheetHtmlRenderer = TechnicalSheetHtmlRenderer(),
    private val riLibrettoBuilder: RiLibrettoHtmlBuilder = RiLibrettoHtmlBuilder(signatureRenderer, sheetRenderer)
) : Html {

    /**
     * Genera l'HTML completo tramite l'oggetto di contesto unificato [HtmlRenderContext].
     */
    override fun buildHtml(context: HtmlRenderContext): String {
        if (context.impianto is RilevazioneAntincendio && context.templateHtml == null) {
            return riLibrettoBuilder.buildLibrettoHtml(context, attachmentRegistry)
        }
        // 1. Caricamento del template HTML dalla cache (o override esplicito da context)
        val rawTemplate = context.templateHtml ?: templateLoader.loadTemplate(templatePath)

        // 2. Risoluzione sommario premessa dal registro allegati
        val attachmentSummary = attachmentRegistry.getProviderFor(context.impianto)?.buildPremessaSummary(context.impianto)

        // 3. Sostituzione header, cartiglio, cliente, sito, date AcroForm e premessa
        var html = headerFormatter.formatHeaderAndMetadata(
            templateHtml = rawTemplate,
            context = context,
            attachmentSummary = attachmentSummary
        )

        // 4. Iniezione Firma Digitale Tecnico (R26) e Firma Cliente
        val firmaTecnicoHtml = signatureRenderer.renderSignatureHtml(context.firmaTecnicoBase64)
        html = html.replace("<!-- FIRMA -->", firmaTecnicoHtml)
        if (context.firmaClienteBase64 != null) {
            val firmaClienteHtml = signatureRenderer.renderSignatureHtml(
                signatureRaw = context.firmaClienteBase64,
                altText = SignatureImageRenderer.DEFAULT_ALT_CLIENTE
            )
            html = html.replace("<!-- FIRMA_CLIENTE -->", firmaClienteHtml)
        }

        // 5. Generazione righe dinamiche delle attività con radio button AcroForm e griglia fissa 30 righe
        val rows = tableBuilder.buildAttivitaRows(context.attivita, context.impianto)
        html = html.replace("<!-- ATTIVITA_ROWS -->", rows)

        // 6. Generazione fogli tecnici allegati (Foglio 2+: differenziali, lampade, rivelatori/moduli)
        val extraSheets = attachmentRegistry.getProvidersFor(context.impianto)
            .flatMap { it.buildSheetsData(context.impianto, context.frequenza, context.clienteNome) }
            .joinToString("\n") { sheetData ->
                sheetRenderer.render(
                    data = sheetData,
                    frequenza = context.frequenza,
                    clienteNome = context.clienteNome,
                    cantiereNome = context.cantiereNome,
                    dataIntervento = context.dataIntervento,
                    nomeManutentore = context.nomeManutentore,
                    dataInizioContratto = context.dataInizioContratto
                )
            }

        // 7. Append fogli allegati
        html = if (html.contains("<!-- COMPONENTI_QUADRO_SHEET -->")) {
            html.replace("<!-- COMPONENTI_QUADRO_SHEET -->", extraSheets)
        } else if (extraSheets.isNotBlank()) {
            html.replace("</form>", "$extraSheets\n    </form>")
        } else {
            html
        }

        return html
    }
}
