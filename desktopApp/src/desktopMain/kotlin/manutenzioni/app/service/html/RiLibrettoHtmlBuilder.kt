package manutenzioni.app.service.html

import manutenzioni.app.service.attachments.TechnicalSheetHtmlRenderer
import manutenzioni.domain.model.*
import manutenzioni.domain.service.HtmlRenderContext
import manutenzioni.domain.service.attachments.TechnicalAttachmentRegistry
import manutenzioni.domain.service.rules.Uni11224RiCatalog
import java.io.File
import java.util.Base64

/**
 * Costruttore HTML/AcroForm per il **Libretto di Manutenzione Impianti di Rivelazione e Segnalazione Incendi (IRAI)**
 * conforme alle norme **UNI 11224:2026** e **UNI 9795:2021** e al modello [templateLibretto_RI.html].
 *
 * Compone in un unico fascicolo PDF multipagina:
 * 1. **Sezione A1 / B1**: Anagrafica e Identificazione del Sistema (con `Convenzionale`/`Indirizzato` mutuamente esclusivi e `ASD`/`Radio`/`EVAC` coesistenti), Tipo di Verifica, Esecuzione e Documenti di Progetto Acquisiti (con incrocio automatico sulla *Lista di controllo dettagliata di tutti i componenti del sistema*).
 * 2. **Liste di Riscontro Semestrali (`Periodicità: 06 MESI`)**:
 *    - In [TipoVerbaleRi.VERIFICA_PERIODICA_B]: `B2 - Lista di Riscontro per Verifica sul Sistema` (13 attività obbligatorie su griglia da 20 righe).
 *    - In [TipoVerbaleRi.VERIFICA_INIZIALE_A]: `A2 - Lista di Riscontro per Verifiche Preliminari sul Sistema` (19 attività) + `A3 - Lista di Riscontro per Controlli sul Sistema e Dispositivi` (17 attività).
 * 3. **Prospetto Opzionale ASD (`Prospetto B.3` / `Prospetto A.4`)**: aggiunto automaticamente quando `ri.hasAsd == true`.
 * 4. **Lista di Controllo Componenti (`ELENCO RIL`)**: fogli `Componenti Comuni`, `Rivelatori Loop 1..8`, `Moduli Loop 1..8` quando `ri.listaDispositivi.isNotEmpty()`.
 * 5. **Sezioni Finali Condivise (2, 3, 4, 5)**: Esito delle Verifiche (Opzioni A / B / C), Registro Dettagliato delle Non Conformità (`NC-1..NC-7`), Prospetto Sintetico Scadenze e Percentuali, Clausole Generali e Sottoscrizione Formale.
 */
class RiLibrettoHtmlBuilder(
    private val signatureRenderer: SignatureImageRenderer = SignatureImageRenderer(),
    private val sheetRenderer: TechnicalSheetHtmlRenderer = TechnicalSheetHtmlRenderer()
) {
    companion object {
        const val CHECKLIST_TOTAL_ROWS = 20
        private val ESITI_COLUMNS = listOf("P", "PI", "NA", "NP", "VN", "B")

        private val cachedLogoDataUri: String? by lazy {
            loadResourceAsDataUri("templateLibretto_RI.fld/image001.jpg", "desktopApp/src/desktopMain/resources/templateLibretto_RI.fld/image001.jpg")
        }

        private val cachedFooterDataUri: String? by lazy {
            loadResourceAsDataUri("templateLibretto_RI.fld/image002.jpg", "desktopApp/src/desktopMain/resources/templateLibretto_RI.fld/image002.jpg")
        }

        private fun loadResourceAsDataUri(classpathPath: String, fallbackFilePath: String): String? {
            return try {
                val bytes = RiLibrettoHtmlBuilder::class.java.classLoader
                    ?.getResourceAsStream(classpathPath)
                    ?.use { it.readBytes() }
                    ?: File(fallbackFilePath).takeIf { it.exists() }?.readBytes()
                if (bytes != null && bytes.isNotEmpty()) {
                    "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun buildLibrettoHtml(
        context: HtmlRenderContext,
        attachmentRegistry: TechnicalAttachmentRegistry
    ): String {
        val ri = (context.impianto as? RilevazioneAntincendio)
            ?: (context.impianto.normalizeType() as? RilevazioneAntincendio)
            ?: RilevazioneAntincendio(
                id = context.impianto.id,
                codIntervento = context.impianto.codIntervento,
                nomeCompleto = context.impianto.nomeCompleto,
                premessa = context.impianto.premessa,
                listaAttivita = context.attivita.ifEmpty { Uni11224RiCatalog.LISTA_B2_VERIFICA_PERIODICA_SISTEMA }
            )

        val isInizialeA = ri.tipoVerbale == TipoVerbaleRi.VERIFICA_INIZIALE_A
        val componentSheets = attachmentRegistry.getProvidersFor(ri)
            .flatMap { it.buildSheetsData(ri, Uni11224RiCatalog.FREQUENZA_SEMESTRALE, context.clienteNome) }

        // Calcolo numero totale di pagine del libretto:
        // Pagina 1 (A1/B1) + Liste di riscontro (B2 = 1 oppure A2+A3 = 2) + opzionale ASD (1) + fogli componenti + 3 pagine finali (Esito, NC/Scadenze, Clausole/Firme)
        val checklistPagesCount = (if (isInizialeA) 2 else 1) + (if (ri.hasAsd) 1 else 0)
        val totalPages = 1 + checklistPagesCount + componentSheets.size + 3

        val firmaTecnicoHtml = signatureRenderer.renderSignatureHtml(context.firmaTecnicoBase64)
        val firmaClienteHtml = if (context.firmaClienteBase64 != null) {
            signatureRenderer.renderSignatureHtml(
                signatureRaw = context.firmaClienteBase64,
                altText = SignatureImageRenderer.DEFAULT_ALT_CLIENTE
            )
        } else ""

        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        sb.appendLine("""<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">""")
        sb.appendLine("""<html xmlns="http://www.w3.org/1999/xhtml" lang="it">""")
        sb.appendLine("""<head>""")
        sb.appendLine("""    <meta charset="UTF-8" />""")
        sb.appendLine("""    <title>Libretto Verifica e Manutenzione Rilevazione Incendi (UNI 11224:2026)</title>""")
        appendStyles(sb)
        sb.appendLine("""</head>""")
        sb.appendLine("""<body>""")
        sb.appendLine("""    <form>""")

        var currentPage = 1

        // 1. PAGINA 1: A1 / B1 - ANAGRAFICA E IDENTIFICAZIONE DEL SISTEMA & CHECKLIST DOCUMENTALE
        appendPage1AnagraficaEDocumenti(
            sb = sb,
            ri = ri,
            context = context,
            isInizialeA = isInizialeA,
            hasComponentChecklist = componentSheets.isNotEmpty(),
            pageNumber = currentPage++,
            totalPages = totalPages
        )

        // 2. PAGINE LISTE DI RISCONTRO (A2 + A3 oppure B2)
        if (isInizialeA) {
            appendChecklistSheetPage(
                sb = sb,
                ri = ri,
                context = context,
                sectionTitle = "A2-Lista di Riscontro per Verifiche Preliminari sul Sistema",
                attivitaList = Uni11224RiCatalog.LISTA_A2_VERIFICHE_PRELIMINARI,
                fieldPrefix = "a2",
                firmaTecnicoHtml = firmaTecnicoHtml,
                asdHeaderConfig = null,
                pageNumber = currentPage++,
                totalPages = totalPages
            )
            val a3Attivita = context.attivita.takeIf { it.size == Uni11224RiCatalog.LISTA_A3_CONTROLLI_SISTEMA.size }
                ?: Uni11224RiCatalog.LISTA_A3_CONTROLLI_SISTEMA
            appendChecklistSheetPage(
                sb = sb,
                ri = ri,
                context = context,
                sectionTitle = "A3-Lista di Riscontro per Controlli sul Sistema e Dispositivi",
                attivitaList = a3Attivita,
                fieldPrefix = "",
                firmaTecnicoHtml = firmaTecnicoHtml,
                asdHeaderConfig = null,
                pageNumber = currentPage++,
                totalPages = totalPages
            )
        } else {
            val b2Attivita = context.attivita.takeIf {
                it.isNotEmpty() && !Uni11224RiCatalog.isLegacyOrInvalidRiAttivita(it)
            } ?: Uni11224RiCatalog.LISTA_B2_VERIFICA_PERIODICA_SISTEMA

            appendChecklistSheetPage(
                sb = sb,
                ri = ri,
                context = context,
                sectionTitle = "B2-Lista di Riscontro per Verifica sul Sistema",
                attivitaList = b2Attivita,
                fieldPrefix = "",
                firmaTecnicoHtml = firmaTecnicoHtml,
                asdHeaderConfig = null,
                pageNumber = currentPage++,
                totalPages = totalPages
            )
        }

        // 3. PROSPETTO OPZIONALE A.4 / B.3 — SISTEMI ASD (ASPIRAZIONE)
        if (ri.hasAsd) {
            val prospettoCode = if (isInizialeA) "Prospetto A.4" else "Prospetto B.3"
            appendChecklistSheetPage(
                sb = sb,
                ri = ri,
                context = context,
                sectionTitle = "$prospettoCode — Lista di Riscontro per Controlli Addizionali su Sistemi ASD (Aspirazione)",
                attivitaList = Uni11224RiCatalog.LISTA_ASD_CONTROLLI_ADDIZIONALI,
                fieldPrefix = "asd",
                firmaTecnicoHtml = firmaTecnicoHtml,
                asdHeaderConfig = AsdHeaderConfig(
                    classeSensibilita = ri.classeSensibilitaAsd,
                    tempoMaxTrasporto = ri.tempoMaxTrasportoAsd
                ),
                pageNumber = currentPage++,
                totalPages = totalPages
            )
        }

        // 4. FOGLI ALLEGATI COMPONENTI E LOOP (ELENCO RIL — Prospetto B.1)
        for (sheetData in componentSheets) {
            sb.appendLine(
                sheetRenderer.render(
                    data = sheetData,
                    frequenza = Uni11224RiCatalog.FREQUENZA_SEMESTRALE,
                    clienteNome = context.clienteNome,
                    cantiereNome = context.cantiereNome,
                    dataIntervento = context.dataIntervento,
                    nomeManutentore = context.nomeManutentore,
                    dataInizioContratto = context.dataInizioContratto
                )
            )
            currentPage++
        }

        // 5. PAGINA ESITO DELLE VERIFICHE (SEZIONE 2 — OPZIONI A / B / C)
        appendPageEsitoVerifiche(
            sb = sb,
            codImpianto = ri.codIntervento,
            pageNumber = currentPage++,
            totalPages = totalPages
        )

        // 6. PAGINA REGISTRO NON CONFORMITÀ (NC-1..NC-7) & SEZIONE 3 PROSPETTO SCADENZE
        appendPageRegistroNcEScadenze(
            sb = sb,
            codImpianto = ri.codIntervento,
            pageNumber = currentPage++,
            totalPages = totalPages
        )

        // 7. PAGINA SEZIONE 4 CLAUSOLE GENERALI & SEZIONE 5 SOTTOSCRIZIONE FORMALE
        appendPageClausoleEFirme(
            sb = sb,
            ri = ri,
            context = context,
            firmaTecnicoHtml = firmaTecnicoHtml,
            firmaClienteHtml = firmaClienteHtml,
            pageNumber = currentPage,
            totalPages = totalPages
        )

        sb.appendLine("""    </form>""")
        sb.appendLine("""</body>""")
        sb.appendLine("""</html>""")
        return sb.toString()
    }

    private data class AsdHeaderConfig(
        val classeSensibilita: ClasseSensibilitaAsd?,
        val tempoMaxTrasporto: TempoTrasportoAsd?
    )

    private fun appendStyles(sb: StringBuilder) {
        sb.appendLine(
            """
            <style>
                @page {
                    size: A4 portrait;
                    margin: 0.45cm 0.5cm;
                }
                * {
                    margin: 0;
                    padding: 0;
                    box-sizing: border-box;
                }
                html, body {
                    font-family: Calibri, Arial, sans-serif;
                    color: #000000;
                    width: 100%;
                }
                .ri-page {
                    width: 19.80cm;
                    margin: 0 auto;
                }
                .ri-page-break {
                    page-break-before: always;
                    break-before: page;
                }
                table {
                    border-collapse: collapse;
                    border-spacing: 0;
                    empty-cells: show;
                    width: 19.80cm;
                    table-layout: fixed;
                }
                tr {
                    page-break-inside: avoid;
                }
                td, th {
                    font-family: Calibri, Arial, sans-serif;
                    font-size: 8pt;
                    vertical-align: middle;
                    overflow-wrap: break-word;
                }
                p {
                    margin: 0;
                }
                .visually-hidden {
                    position: absolute !important;
                    height: 1px;
                    width: 1px;
                    overflow: hidden;
                    clip: rect(1px, 1px, 1px, 1px);
                    white-space: nowrap;
                }
                .ri-section-heading {
                    font-size: 11.5pt;
                    font-weight: bold;
                    color: #2980B9;
                    margin-top: 0.25cm;
                    margin-bottom: 0.14cm;
                }
                .ri-anagrafica-table td {
                    border-top: 0.5pt solid #D3D3D3;
                    border-bottom: 0.5pt solid #D3D3D3;
                    border-left: none;
                    border-right: none;
                    padding: 4px 6px;
                    font-size: 8.5pt;
                }
                .ri-anagrafica-label {
                    background-color: #F0F4F8;
                    font-weight: bold;
                    width: 6.60cm;
                }
                .ri-anagrafica-value {
                    width: 13.20cm;
                }
                .ri-navy-th {
                    background-color: #1B365D;
                    color: #FFFFFF;
                    font-weight: bold;
                    font-size: 8.5pt;
                    text-align: center;
                    border: 0.5pt solid #000000;
                    padding: 5px 6px;
                }
                .ri-grid-td {
                    border: 0.5pt solid #000000;
                    padding: 4px 6px;
                    font-size: 8.2pt;
                }
                .ri-text-input {
                    width: 100%;
                    height: 0.40cm;
                    line-height: 0.40cm;
                    border: none;
                    outline: none;
                    background: transparent;
                    font-family: Calibri, Arial, sans-serif;
                    font-size: 8.2pt;
                    padding: 0 2px;
                }
                /* Stili Tabella Scheda Tecnica (A2 / A3 / B2 / A.4 / B.3) */
                .cell--title {
                    background-color: #3366ff;
                    color: #ffffff;
                    font-size: 10pt;
                    text-align: center;
                    font-weight: bold;
                    height: 0.58cm;
                    border: 0.0133cm solid #000000;
                }
                .cell--header-section {
                    background-color: #b4c6e7;
                    color: #000000;
                    font-size: 6.5pt;
                    vertical-align: top;
                    text-align: left;
                    padding: 0.03cm 0.06cm;
                    border: 0.0133cm solid #000000;
                }
                .cell--header-form {
                    background-color: #b4c6e7;
                    color: #000000;
                    font-size: 7pt;
                    font-weight: bold;
                    vertical-align: top;
                    text-align: left;
                    padding: 0.04cm 0.06cm;
                    border: 0.0133cm solid #000000;
                }
                .cell--form-input {
                    background-color: transparent;
                    color: #000000;
                    font-size: 6.5pt;
                    font-weight: bold;
                    vertical-align: top;
                    text-align: left;
                    padding: 0.03cm 0.06cm;
                    border: 0.0133cm solid #000000;
                }
                .cell--label {
                    background-color: transparent;
                    color: #000000;
                    font-size: 6.6pt;
                    vertical-align: top;
                    text-align: left;
                    padding: 0.04cm 0.08cm;
                    border: 0.0133cm solid #000000;
                }
                .cell--header-column {
                    background-color: #d9e1f2;
                    color: #000000;
                    font-size: 7pt;
                    font-weight: bold;
                    vertical-align: middle;
                    text-align: center;
                    border: 0.0133cm solid #000000;
                }
                .cell--data-empty {
                    font-size: 6.8pt;
                    vertical-align: middle;
                    text-align: center;
                    border: 0.0133cm solid #000000;
                    padding: 1px 2px;
                }
                .cell--data-left {
                    font-size: 7.1pt;
                    vertical-align: middle;
                    text-align: left;
                    border: 0.0133cm solid #000000;
                    padding: 2px 4px;
                }
                .row--data {
                    height: 0.48cm;
                }
                .row--data input[type="radio"] {
                    width: 0.28cm;
                    height: 0.28cm;
                    margin: 0;
                }
            </style>
            """.trimIndent()
        )
    }

    private fun appendPageHeaderLogo(sb: StringBuilder) {
        val logoUri = cachedLogoDataUri
        if (logoUri != null) {
            sb.appendLine("""            <div style="margin-bottom: 0.15cm;">""")
            sb.appendLine("""                <img src="$logoUri" alt="Desiderio Impianti S.r.l." style="height: 1.55cm; object-fit: contain;" />""")
            sb.appendLine("""            </div>""")
        } else {
            sb.appendLine("""            <div style="margin-bottom: 0.15cm; font-size: 10pt; font-weight: bold; color: #1B365D;">DESIDERIO IMPIANTI S.R.L. — IMPIANTI ELETTRICI CIVILI ED INDUSTRIALI</div>""")
        }
    }

    private fun appendPageFooterBanner(sb: StringBuilder, pageNumber: Int, totalPages: Int) {
        sb.appendLine("""            <div style="margin-top: 0.20cm; text-align: center;">""")
        sb.appendLine("""                <p style="font-size: 8pt; color: #333333; margin-bottom: 2px;">$pageNumber di $totalPages</p>""")
        val footerUri = cachedFooterDataUri
        if (footerUri != null) {
            sb.appendLine("""                <img src="$footerUri" alt="Desiderio Impianti Footer" style="width: 13.5cm; height: 0.85cm; object-fit: contain;" />""")
        } else {
            sb.appendLine("""                <p style="font-size: 6.5pt; color: #555555;">DESIDERIO IMPIANTI S.R.L. — Via I. Golfarelli, 80 - 47122 Forlì - info@desiderioimpianti.it</p>""")
        }
        sb.appendLine("""            </div>""")
    }

    private fun appendPage1AnagraficaEDocumenti(
        sb: StringBuilder,
        ri: RilevazioneAntincendio,
        context: HtmlRenderContext,
        isInizialeA: Boolean,
        hasComponentChecklist: Boolean,
        pageNumber: Int,
        totalPages: Int
    ) {
        val cod = escapeHtml(ri.codIntervento)
        val clienteText = escapeHtml(context.clienteNome?.takeIf { it.isNotBlank() } ?: "")
        val cantiereText = escapeHtml(context.cantiereNome?.takeIf { it.isNotBlank() } ?: "")
        val responsabileText = escapeHtml(ri.responsabileSistema?.takeIf { it.isNotBlank() } ?: "")
        val manutentoreText = escapeHtml(context.nomeManutentore?.takeIf { it.isNotBlank() } ?: "")
        val localiText = escapeHtml(ri.localiProtetti?.takeIf { it.isNotBlank() } ?: "")

        val titoloVerbale = if (isInizialeA) {
            "VERBALE VERIFICA INIZIALE E CHECKLIST DOCUMENTALE"
        } else {
            "VERBALE VERIFICA PERIODICA E CHECKLIST DOCUMENTALE"
        }
        val codiceSezione = if (isInizialeA) "A1" else "B1"

        val markConv = if (ri.architettura == ArchitetturaImpiantoRi.CONVENZIONALE) "[X]" else "[  ]"
        val markInd = if (ri.architettura == ArchitetturaImpiantoRi.INDIRIZZATO) "[X]" else "[  ]"
        val markAsd = if (ri.hasAsd) "[X]" else "[  ]"
        val markRadio = if (ri.hasRadio) "[X]" else "[  ]"
        val markEvac = if (ri.hasEvac) "[X]" else "[  ]"

        val markTotale = if (ri.estensioneProtezione == EstensioneProtezioneRi.TOTALE) "[X]" else "[  ]"
        val markParziale = if (ri.estensioneProtezione == EstensioneProtezioneRi.PARZIALE) "[X]" else "[  ]"
        val localiDisplay = if (localiText.isNotEmpty()) localiText else "_________________________"

        val markEsecCivile = if (ri.tipoEsecuzione == TipoEsecuzioneRi.CIVILE) "[X]" else "[  ]"
        val markEsecTerziario = if (ri.tipoEsecuzione == TipoEsecuzioneRi.TERZIARIO_UFFICI) "[X]" else "[  ]"
        val markEsecIndustriale = if (ri.tipoEsecuzione == TipoEsecuzioneRi.INDUSTRIALE_DEPOSITO) "[X]" else "[  ]"
        val markEsecAtex = if (ri.tipoEsecuzione == TipoEsecuzioneRi.ATEX) "[X]" else "[  ]"

        val markListaCompSi = if (hasComponentChecklist) "[X]" else "[  ]"
        val markListaCompNo = if (!hasComponentChecklist) "[  ]" else "[  ]"
        val notaListaComp = if (hasComponentChecklist) {
            "Allegato Elenco Componenti (${ri.listaDispositivi.size} disp.)"
        } else ""

        sb.appendLine("""        <div class="ri-page">""")
        appendPageHeaderLogo(sb)
        sb.appendLine("""            <p style="font-size: 12.5pt; font-weight: bold; color: #1B365D; margin-bottom: 2px;">IMPIANTI DI RIVELAZIONE E SEGNALAZIONE INCENDI (IRAI)</p>""")
        sb.appendLine("""            <p style="font-size: 12pt; font-weight: bold; color: #0F2D5A; margin-bottom: 2px;">$titoloVerbale</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; font-style: italic; color: #505050; margin-bottom: 0.25cm;">Modulistica Operativa Conforme UNI 11224:2026 e UNI 9795:2021 - Appendici A/B (Controllo Iniziale / Presa in Carico / Verifica Periodica)</p>""")

        sb.appendLine("""            <p class="ri-section-heading">${codiceSezione}-ANAGRAFICA E IDENTIFICAZIONE DEL SISTEMA</p>""")
        sb.appendLine("""            <table class="ri-anagrafica-table">""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Ragione Sociale Committente / Cliente:</td><td class="ri-anagrafica-value"><input type="text" class="ri-text-input" name="ri_${cod}_cliente" value="$clienteText" /></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Ubicazione Impianto / Attività Protetta:</td><td class="ri-anagrafica-value"><input type="text" class="ri-text-input" name="ri_${cod}_ubicazione" value="$cantiereText" /></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Indirizzo e Sede Legale:</td><td class="ri-anagrafica-value"><input type="text" class="ri-text-input" name="ri_${cod}_sede_legale" value="$cantiereText" /></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Responsabile del Sistema / Datore di Lavoro:</td><td class="ri-anagrafica-value"><input type="text" class="ri-text-input" name="ri_${cod}_responsabile" value="$responsabileText" /></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Impresa Manutentrice Incaricata:</td><td class="ri-anagrafica-value"><strong>Desiderio Impianti S.r.l.</strong></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Abilitazione D.M. 37/08 (Lettera g):</td><td class="ri-anagrafica-value">[X] Sì &nbsp;&nbsp; [  ] No &nbsp;—&nbsp; N° Iscrizione CCIAA/Albo: <strong>03511730404 / R.E.A. 303732</strong></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Tecnico Manutentore Qualificato (D.M. 1/09/2021):</td><td class="ri-anagrafica-value">Nome e Cognome: <strong>${if (manutentoreText.isNotEmpty()) manutentoreText else "_________________________________________"}</strong></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Tipologia Impianto Rivelazione Incendi:</td><td class="ri-anagrafica-value"><strong>$markConv Convenzionale &nbsp;&nbsp; $markInd Indirizzato &nbsp;&nbsp; $markAsd ASD (Aspirazione) &nbsp;&nbsp; $markRadio Radio &nbsp;&nbsp; $markEvac EVAC</strong></td></tr>""")
        sb.appendLine("""                <tr><td class="ri-anagrafica-label">Estensione della Protezione Impianto:</td><td class="ri-anagrafica-value"><strong>$markTotale Totale dell'edificio &nbsp;&nbsp; $markParziale Parziale (Locali protetti: $localiDisplay )</strong></td></tr>""")
        sb.appendLine("""            </table>""")

        // Box TIPO DI VERIFICA & ESECUZIONE
        sb.appendLine("""            <table style="margin-top: 0.25cm;">""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 12.80cm;">TIPO DI VERIFICA</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 7.00cm;">ESECUZIONE</th>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td class="ri-grid-td" style="vertical-align: top; line-height: 1.45;">""")
        if (isInizialeA) {
            sb.appendLine("""                        <p>[  ] Controllo Iniziale Nuova Consegna</p>""")
            sb.appendLine("""                        <p>[X] Presa in Carico Manutenzione</p>""")
            sb.appendLine("""                        <p>[  ] Sistema Modificato / Integrato</p>""")
            sb.appendLine("""                        <p>[  ] Verifica Generale 12ennale</p>""")
        } else {
            sb.appendLine("""                        <p>[X] Verifica Periodica</p>""")
            sb.appendLine("""                        <p>[  ] Manutenzione ordinaria</p>""")
            sb.appendLine("""                        <p>[  ] Manutenzione straordinaria</p>""")
        }
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td class="ri-grid-td" style="vertical-align: top; line-height: 1.45;">""")
        sb.appendLine("""                        <p>$markEsecCivile Tipo Civile</p>""")
        sb.appendLine("""                        <p>$markEsecTerziario Terziario / Uffici</p>""")
        sb.appendLine("""                        <p>$markEsecIndustriale Industriale / Deposito</p>""")
        sb.appendLine("""                        <p>$markEsecAtex Luogo a Rischio Esplosione (ATEX)</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")

        // Tabella Documenti di Progetto e Riferimento Acquisiti
        sb.appendLine("""            <p class="ri-section-heading">Documenti di Progetto e Riferimento Acquisiti</p>""")
        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 10.80cm;">Documento di Riferimento</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 2.80cm;">Stato</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 6.20cm;">Identificativo / Note</th>""")
        sb.appendLine("""                </tr>""")

        val documenti = listOf(
            Triple("Disegni di Progetto Esecutivo / As-Built e Schemi Elettrici", "[  ] Sì  [  ] No", ""),
            Triple("Planimetrie con Posizione Rivelatori, Pulsanti e Segnalatori", "[  ] Sì  [  ] No", ""),
            Triple("Calcolo Flussometrico e Dimensionamento Rete ASD (se applicabile)", if (ri.hasAsd) "[  ] Sì  [  ] No" else "[  ] Sì  [X] No", if (ri.hasAsd) "" else "Non applicabile"),
            Triple("Dichiarazione di Conformità (DiCo ai sensi D.M. 37/2008) / DiRi", "[  ] Sì  [  ] No", ""),
            Triple("Manuale d'Uso e Manutenzione dell'Impianto e dei Componenti", "[  ] Sì  [  ] No", ""),
            Triple("Lista di controllo dettagliata di tutti i componenti del sistema", "$markListaCompSi Sì  $markListaCompNo No", notaListaComp)
        )
        documenti.forEachIndexed { idx, (docLabel, statoDefault, notaDefault) ->
            val docIdx = idx + 1
            sb.appendLine("""                <tr>""")
            sb.appendLine("""                    <td class="ri-grid-td">${escapeHtml(docLabel)}</td>""")
            sb.appendLine("""                    <td class="ri-grid-td" style="text-align: center;"><strong>$statoDefault</strong></td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_doc_note_$docIdx" value="${escapeHtml(notaDefault)}" /></td>""")
            sb.appendLine("""                </tr>""")
        }
        sb.appendLine("""            </table>""")

        // In Appendice B (B1), riga aggiuntiva per Controllo della disponibilità delle parti di ricambio
        if (!isInizialeA) {
            sb.appendLine("""            <table style="margin-top: 0.15cm;">""")
            sb.appendLine("""                <tr>""")
            sb.appendLine("""                    <th class="ri-navy-th" style="width: 1.50cm;">Obbl.</th>""")
            sb.appendLine("""                    <th class="ri-navy-th" style="width: 4.00cm;">Tipo di Controllo</th>""")
            sb.appendLine("""                    <th class="ri-navy-th" style="width: 7.30cm;">Scopo e Requisito della Verifica</th>""")
            sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.20cm;">Esito</th>""")
            sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.80cm;">Note</th>""")
            sb.appendLine("""                </tr>""")
            sb.appendLine("""                <tr>""")
            sb.appendLine("""                    <td class="ri-grid-td" style="text-align: center;"><strong>[X]</strong></td>""")
            sb.appendLine("""                    <td class="ri-grid-td">Visivo e Documentale</td>""")
            sb.appendLine("""                    <td class="ri-grid-td">Controllo della disponibilità delle parti di ricambio</td>""")
            sb.appendLine("""                    <td class="ri-grid-td" style="text-align: center;">[  ] Pos &nbsp; [  ] Neg</td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_ricambi_note" value="" /></td>""")
            sb.appendLine("""                </tr>""")
            sb.appendLine("""            </table>""")
        }

        appendPageFooterBanner(sb, pageNumber, totalPages)
        sb.appendLine("""        </div>""")
    }

    private fun appendChecklistSheetPage(
        sb: StringBuilder,
        ri: RilevazioneAntincendio,
        context: HtmlRenderContext,
        sectionTitle: String,
        attivitaList: List<Attivita>,
        fieldPrefix: String,
        firmaTecnicoHtml: String,
        asdHeaderConfig: AsdHeaderConfig?,
        pageNumber: Int,
        totalPages: Int
    ) {
        val cod = escapeHtml(ri.codIntervento)
        val pfx = if (fieldPrefix.isNotEmpty()) "${fieldPrefix}_" else ""
        val clienteText = if (!context.clienteNome.isNullOrBlank()) "Cliente: ${escapeHtml(context.clienteNome)}" else "Cliente:"
        val sitoText = if (!context.cantiereNome.isNullOrBlank()) "Sito: ${escapeHtml(context.cantiereNome)}" else "Sito"
        val contrattoDate = escapeHtml(context.dataInizioContratto?.trim().orEmpty())
        val precedenteDate = escapeHtml(context.dataInterventoPrecedente?.trim().orEmpty())
        val attualeDate = escapeHtml(context.dataIntervento?.trim().orEmpty())
        val manutentoreText = escapeHtml(context.nomeManutentore?.trim().orEmpty())

        val startingName = if (pfx.isEmpty()) "starting_date" else "${pfx}starting_date"
        val previousName = if (pfx.isEmpty()) "previous_date" else "${pfx}previous_date"
        val actualName = if (pfx.isEmpty()) "actual_date" else "${pfx}actual_date"
        val manutentoreName = if (pfx.isEmpty()) "manutentore" else "${pfx}manutentore"

        sb.appendLine("""        <div class="ri-page ri-page-break">""")
        appendPageHeaderLogo(sb)

        sb.appendLine("""            <table>""")
        sb.appendLine("""                <colgroup>""")
        sb.appendLine("""                    <col style="width: 1.50cm;" />""")
        sb.appendLine("""                    <col style="width: 1.45cm;" />""")
        sb.appendLine("""                    <col style="width: 2.35cm;" />""")
        sb.appendLine("""                    <col style="width: 9.75cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 0.45cm;" />""")
        sb.appendLine("""                    <col style="width: 1.60cm;" />""")
        sb.appendLine("""                </colgroup>""")
        sb.appendLine("""                <thead>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="12" class="cell--title">SCHEDA DI VERIFICA E MANUTENZIONE PERIODICA PROGRAMMATA IMPIANTI ELETTRICI/ELETTRONICI</td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="2" class="cell--header-section"><p>Codice Scheda:</p><p style="font-size: 8.5pt; font-weight: bold;">$cod . 06</p></td>""")
        sb.appendLine("""                        <td colspan="9" class="cell--header-section"><p>Oggetto scheda:</p><p style="font-size: 8.5pt; font-weight: bold;">$cod &nbsp;&nbsp; ${escapeHtml(ri.nomeCompleto)}</p></td>""")
        sb.appendLine("""                        <td class="cell--header-section"><p>Periodicità:</p><p style="font-size: 8.5pt; font-weight: bold;">06 MESI</p></td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="3" class="cell--header-form"><p>Azienda Appaltat.: Desiderio Impianti S.r.l.</p></td>""")
        sb.appendLine("""                        <td class="cell--header-form"><p>$sitoText &nbsp;&nbsp;&nbsp;&nbsp; $clienteText</p></td>""")
        sb.appendLine("""                        <td colspan="8" class="cell--form-input">""")
        sb.appendLine("""                            <p>Data inizio contratto:</p>""")
        sb.appendLine("""                            <input type="text" class="ri-text-input" name="$startingName" value="$contrattoDate" placeholder="gg/mm/aaaa" />""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="4" rowspan="3" class="cell--label">""")
        sb.appendLine("""                            <p style="font-weight: bold; margin-bottom: 2px;">Premessa e/o informazioni: ${escapeHtml(Uni11224RiCatalog.NORMATIVE_HEADER)}</p>""")
        sb.appendLine("""                            <p style="font-size: 6.2pt; line-height: 1.14;">${escapeHtml(Uni11224RiCatalog.PREMESSA_SICUREZZA_RI)}</p>""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                        <td colspan="8" class="cell--form-input">""")
        sb.appendLine("""                            <p>Data precedente attività V&amp;M:</p>""")
        sb.appendLine("""                            <input type="text" class="ri-text-input" name="$previousName" value="$precedenteDate" placeholder="gg/mm/aaaa" />""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="8" class="cell--form-input">""")
        sb.appendLine("""                            <p>Data attuale attività V&amp;M:</p>""")
        sb.appendLine("""                            <input type="text" class="ri-text-input" name="$actualName" value="$attualeDate" placeholder="gg/mm/aaaa" />""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="4" class="cell--form-input">""")
        sb.appendLine("""                            <p>Verificatore/Manutentore: <span style="font-size:1pt;color:#fff;">$manutentoreText</span></p>""")
        sb.appendLine("""                            <input type="text" class="ri-text-input" name="$manutentoreName" value="$manutentoreText" />""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                        <td colspan="4" class="cell--form-input">""")
        sb.appendLine("""                            <p>Firma:</p>""")
        sb.appendLine("""                            $firmaTecnicoHtml""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")

        // Titolo Prospetto (es. B2-Lista di Riscontro... o Prospetto B.3...)
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="12" style="background-color: #d9e1f2; border: 0.0133cm solid #000; font-size: 8pt; font-weight: bold; padding: 3px 6px;">${escapeHtml(sectionTitle)}</td>""")
        sb.appendLine("""                    </tr>""")

        // Barra opzionale per Prospetto A.4 / B.3 (Sistemi ASD)
        if (asdHeaderConfig != null) {
            val mA = if (asdHeaderConfig.classeSensibilita == ClasseSensibilitaAsd.CLASSE_A) "[X]" else "[  ]"
            val mB = if (asdHeaderConfig.classeSensibilita == ClasseSensibilitaAsd.CLASSE_B) "[X]" else "[  ]"
            val mC = if (asdHeaderConfig.classeSensibilita == ClasseSensibilitaAsd.CLASSE_C) "[X]" else "[  ]"
            val t60 = if (asdHeaderConfig.tempoMaxTrasporto == TempoTrasportoAsd.MAX_60S) "[X]" else "[  ]"
            val t90 = if (asdHeaderConfig.tempoMaxTrasporto == TempoTrasportoAsd.MAX_90S) "[X]" else "[  ]"
            val t120 = if (asdHeaderConfig.tempoMaxTrasporto == TempoTrasportoAsd.MAX_120S) "[X]" else "[  ]"
            sb.appendLine("""                    <tr>""")
            sb.appendLine("""                        <td colspan="12" style="background-color: #eef3fb; border: 0.0133cm solid #000; font-size: 7.5pt; font-weight: bold; text-align: center; padding: 3px 6px;">""")
            sb.appendLine("""                            Classe di Sensibilità ASD: &nbsp; $mA Classe A &nbsp; $mB Classe B &nbsp; $mC Classe C &nbsp;&nbsp; | &nbsp;&nbsp; Tempo Max Trasporto Richiesto: &nbsp; $t60 ≤ 60 s &nbsp; $t90 ≤ 90 s &nbsp; $t120 ≤ 120 s""")
            sb.appendLine("""                        </td>""")
            sb.appendLine("""                    </tr>""")
        }

        // Intestazioni Colonne Tabella Risultati
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td rowspan="2" class="cell--header-column">Codice Attività</td>""")
        sb.appendLine("""                        <td rowspan="2" class="cell--header-column">Tipo Attività</td>""")
        sb.appendLine("""                        <td rowspan="2" class="cell--header-column">Impianto</td>""")
        sb.appendLine("""                        <td rowspan="2" class="cell--header-column">Attività</td>""")
        sb.appendLine("""                        <td colspan="7" class="cell--header-column">Esito</td>""")
        sb.appendLine("""                        <td rowspan="2" class="cell--header-column">Nota</td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td class="cell--header-column">O</td>""")
        for (esito in ESITI_COLUMNS) {
            sb.appendLine("""                        <td class="cell--header-column">$esito</td>""")
        }
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                </thead>""")
        sb.appendLine("""                <tbody>""")

        // Righe attività reali (1..N)
        attivitaList.forEachIndexed { idx, att ->
            val rowNum = idx + 1
            val radioName = "${pfx}esito_${cod}_$rowNum"
            val vnName = "${pfx}esito_vn_${cod}_$rowNum"
            val noteName = "${pfx}note_${cod}_$rowNum"
            val obbMark = if (att.obbligatoria) "x" else ""
            val notaDefault = escapeHtml(att.notaPrecompilata.orEmpty())

            sb.appendLine("""                    <tr class="row--data">""")
            sb.appendLine("""                        <td class="cell--data-empty">$cod &nbsp; 06 &nbsp; .$rowNum</td>""")
            sb.appendLine("""                        <td class="cell--data-empty">${escapeHtml(att.tipoAttivita ?: "Controllo")}</td>""")
            sb.appendLine("""                        <td class="cell--data-empty">${escapeHtml(att.zona ?: "")}</td>""")
            sb.appendLine("""                        <td class="cell--data-left">${escapeHtml(att.descrizione ?: "")}</td>""")
            sb.appendLine("""                        <td class="cell--data-empty"><strong>$obbMark</strong></td>""")
            for (esito in ESITI_COLUMNS) {
                val group = if (esito == "VN") vnName else radioName
                val id = "${radioName}_${esito.lowercase()}"
                sb.appendLine("""                        <td class="cell--data-empty"><input type="radio" name="$group" id="$id" value="$esito" /></td>""")
            }
            sb.appendLine("""                        <td class="cell--data-left" style="padding: 0 2px;">""")
            if (notaDefault.isNotEmpty()) {
                sb.appendLine("""                            <span style="font-size: 1pt; color: #ffffff;">$notaDefault</span>""")
            }
            sb.appendLine("""                            <input type="text" class="ri-text-input" style="font-size: 6.2pt;" name="$noteName" id="$noteName" value="$notaDefault" />""")
            sb.appendLine("""                        </td>""")
            sb.appendLine("""                    </tr>""")
        }

        // Righe filler fino a 20 (RI 06 .N+1 .. RI 06 .20)
        if (attivitaList.size < CHECKLIST_TOTAL_ROWS) {
            for (fillerIndex in (attivitaList.size + 1)..CHECKLIST_TOTAL_ROWS) {
                val radioName = "${pfx}esito_${cod}_$fillerIndex"
                val vnName = "${pfx}esito_vn_${cod}_$fillerIndex"
                val noteName = "${pfx}note_${cod}_$fillerIndex"
                val tipoName = "${pfx}tipo_${cod}_$fillerIndex"
                val zonaName = "${pfx}zona_${cod}_$fillerIndex"
                val attName = "${pfx}attivita_${cod}_$fillerIndex"

                sb.appendLine("""                    <tr class="row--data">""")
                sb.appendLine("""                        <td class="cell--data-empty">$cod &nbsp; 06 &nbsp; .$fillerIndex</td>""")
                sb.appendLine("""                        <td class="cell--data-empty"><input type="text" class="ri-text-input" name="$tipoName" value="" /></td>""")
                sb.appendLine("""                        <td class="cell--data-empty"><input type="text" class="ri-text-input" name="$zonaName" value="" /></td>""")
                sb.appendLine("""                        <td class="cell--data-left"><input type="text" class="ri-text-input" name="$attName" value="" /></td>""")
                sb.appendLine("""                        <td class="cell--data-empty"></td>""")
                for (esito in ESITI_COLUMNS) {
                    val group = if (esito == "VN") vnName else radioName
                    val id = "${radioName}_${esito.lowercase()}"
                    sb.appendLine("""                        <td class="cell--data-empty"><input type="radio" name="$group" id="$id" value="$esito" /></td>""")
                }
                sb.appendLine("""                        <td class="cell--data-left" style="padding: 0 2px;"><input type="text" class="ri-text-input" name="$noteName" value="" /></td>""")
                sb.appendLine("""                    </tr>""")
            }
        }

        sb.appendLine("""                </tbody>""")
        sb.appendLine("""                <tfoot>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="12" style="border: 0.0133cm solid #000; font-size: 6.2pt; padding: 3px 5px;">""")
        sb.appendLine("""                            <p><strong>Legenda: O = Obbligatorio</strong></p>""")
        sb.appendLine("""                            <p>Nella colonna esito spuntare: P = Positivo | PI = Positivo previo intervento | NA = Intervento rimandato a sessione successiva | NP = Componente non presente | VN = Vedere nota | B = Gli interventi saranno eseguiti dal costruttore e/o dal centro assistenza | Numerare le Note e descriverle nell'apposito riquadro</p>""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                    <tr>""")
        sb.appendLine("""                        <td colspan="12" style="border: 0.0133cm solid #000; height: 1.10cm; padding: 0;">""")
        sb.appendLine("""                            <textarea name="${pfx}note_generali_$cod" style="width: 100%; height: 1.05cm; border: none; font-size: 7.5pt; font-family: Calibri, Arial, sans-serif; padding: 3px 5px;"></textarea>""")
        sb.appendLine("""                        </td>""")
        sb.appendLine("""                    </tr>""")
        sb.appendLine("""                </tfoot>""")
        sb.appendLine("""            </table>""")

        appendPageFooterBanner(sb, pageNumber, totalPages)
        sb.appendLine("""        </div>""")
    }

    private fun appendPageEsitoVerifiche(
        sb: StringBuilder,
        codImpianto: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val cod = escapeHtml(codImpianto)
        sb.appendLine("""        <div class="ri-page ri-page-break">""")
        appendPageHeaderLogo(sb)
        sb.appendLine("""            <p class="ri-section-heading">2. ESITO DELLE VERIFICHE SU IMPIANTO RILEVAZIONE INCENDI (UNI 11224:2026)</p>""")

        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 10.80cm;">Esito Verifiche e Non Conformità</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 2.80cm;">Stato</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 6.20cm;">Note</th>""")
        sb.appendLine("""                </tr>""")
        val quesiti = listOf(
            "Durante le prove sono state riscontrate Non Conformità?",
            "Le Non Conformità sono state risolte in loco?",
            "Il Committente / Responsabile è stato informato?"
        )
        quesiti.forEachIndexed { idx, domanda ->
            val qNum = idx + 1
            sb.appendLine("""                <tr>""")
            sb.appendLine("""                    <td class="ri-grid-td">$domanda</td>""")
            sb.appendLine("""                    <td class="ri-grid-td" style="text-align: center;">[  ] Sì &nbsp; [  ] No</td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_quesito_note_$qNum" value="" /></td>""")
            sb.appendLine("""                </tr>""")
        }
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td colspan="3" class="ri-grid-td" style="height: 1.50cm; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 7.8pt; color: #333;">Commenti e note:</p>""")
        sb.appendLine("""                        <textarea name="ri_${cod}_commenti_esito" style="width: 100%; height: 1.00cm; border: none; font-size: 8pt; font-family: Calibri, Arial, sans-serif;"></textarea>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")

        sb.appendLine("""            <p style="font-size: 8.8pt; font-weight: bold; color: #B40000; margin-top: 0.35cm; margin-bottom: 0.15cm;">ATTENZIONE: SELEZIONARE BARRARE SOLTANTO L'OPZIONE APPLICABILE ALLA SITUAZIONE RILEVATA:</p>""")

        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td style="background-color: #F0FFF0; padding: 8px 10px; border: 0.5pt solid #C8E6C9;">""")
        sb.appendLine("""                        <p style="font-size: 9pt; font-weight: bold; color: #006400; margin-bottom: 3px;"><input type="radio" name="ri_${cod}_opzione_esito" value="A" /> [  ] OPZIONE A — ESITO FAVOREVOLE (IMPIANTO CONFORME ED EFFICIENTE)</p>""")
        sb.appendLine("""                        <p style="font-size: 8.2pt; color: #282828; line-height: 1.25;">A seguito dei controlli visivi, documentali e delle prove funzionali condotte in conformità alla norma UNI 11224:2026, l'impianto è risultato CONFORME E PERFETTAMENTE FUNZIONANTE in ogni sua parte. Non si riscontrano non conformità né guasti. L'impianto si dichiara in stato di piena efficienza operativa ed assoggettabile al regolare ciclo di manutenzione semestrale.</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td style="background-color: #FFF4E5; padding: 8px 10px; border: 0.5pt solid #FFE0B2;">""")
        sb.appendLine("""                        <p style="font-size: 9pt; font-weight: bold; color: #B45000; margin-bottom: 3px;"><input type="radio" name="ri_${cod}_opzione_esito" value="B" /> [  ] OPZIONE B — PRESA IN CARICO CON RISERVA (PRESENZA DI ANOMALIE / NON CONFORMITÀ)</p>""")
        sb.appendLine("""                        <p style="font-size: 8.2pt; color: #282828; line-height: 1.25;">A seguito dei controlli eseguiti ai sensi della norma UNI 11224:2026, l'impianto viene preso in carico allo STATO DI FATTO, evidenziando le NON CONFORMITÀ / GUASTI PREESISTENTI dettagliati nella tabella sottostante.<br/>• L'impianto NON può considerarsi in stato di piena efficienza ai fini della sicurezza sul lavoro fino al ripristino delle anomalie.<br/>• L'Impresa Manutentrice viene sollevata da ogni responsabilità per malfunzionamenti o danni derivanti direttamente dai difetti preesistenti segnalati.<br/>• Il Committente si impegna a promuovere tempestivamente gli interventi di ripristino/riparazione prescritti.</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td style="background-color: #FFE6E6; padding: 8px 10px; border: 0.5pt solid #FFCDD2;">""")
        sb.appendLine("""                        <p style="font-size: 9pt; font-weight: bold; color: #960000; margin-bottom: 3px;"><input type="radio" name="ri_${cod}_opzione_esito" value="C" /> [  ] OPZIONE C — COMPONENTI NON PIÙ ASSOGGETTABILI A MANUTENZIONE (Art. 8.1 UNI 11224:2026)</p>""")
        sb.appendLine("""                        <p style="font-size: 8.2pt; color: #282828; line-height: 1.25;">Dalle verifiche preliminari è emersa l'INTROVABILITÀ SUL MERCATO di componenti/ricambi identici o compatibili con i dispositivi installati (es. centrale/schede/rivelatori obsoleti specificati in nota).<br/>Ai sensi dell'art. 8.1 della norma UNI 11224:2026, tali apparecchiature vengono formalmente dichiarate NON PIÙ ASSOGGETTABILI A MANUTENZIONE in caso di futuro guasto o malfunzionamento, rendendosi necessaria la loro sostituzione con tecnologie di nuova generazione.</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")

        appendPageFooterBanner(sb, pageNumber, totalPages)
        sb.appendLine("""        </div>""")
    }

    private fun appendPageRegistroNcEScadenze(
        sb: StringBuilder,
        codImpianto: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val cod = escapeHtml(codImpianto)
        sb.appendLine("""        <div class="ri-page ri-page-break">""")
        appendPageHeaderLogo(sb)
        sb.appendLine("""            <p class="ri-section-heading">REGISTRO DETTAGLIATO DELLE NON CONFORMITÀ (Da compilare in caso di Opzione B o C):</p>""")

        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.00cm;">N° / Zona</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 7.40cm;">Descrizione Anomalia / Componente Guasto</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 6.20cm;">Azione Correttiva Prescritta</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.20cm;">Priorità</th>""")
        sb.appendLine("""                </tr>""")
        for (ncIdx in 1..7) {
            sb.appendLine("""                <tr>""")
            sb.appendLine("""                    <td class="ri-grid-td"><strong>NC-$ncIdx</strong></td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_nc_desc_$ncIdx" value="" /></td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_nc_azione_$ncIdx" value="" /></td>""")
            sb.appendLine("""                    <td class="ri-grid-td"><input type="text" class="ri-text-input" name="ri_${cod}_nc_prio_$ncIdx" value="" /></td>""")
            sb.appendLine("""                </tr>""")
        }
        sb.appendLine("""            </table>""")

        sb.appendLine("""            <p class="ri-section-heading" style="margin-top: 0.35cm;">3. PROSPETTO SINTETICO SCADENZE E PERCENTUALI DI CONTROLLO PERIODICO</p>""")
        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.20cm; background-color: #1F4E78;">Anzianità Impianto</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 3.20cm; background-color: #1F4E78;">Tipologia Impianto</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 5.20cm; background-color: #1F4E78;">Quota Dispositivi da Testare (su 12 Mesi)</th>""")
        sb.appendLine("""                    <th class="ri-navy-th" style="width: 8.20cm; background-color: #1F4E78;">Note ed Adempimenti Straordinari</th>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""                <tr><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">Qualsiasi età</td><td class="ri-grid-td">Convenzionale</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">100% ad ogni visita semestrale</td><td class="ri-grid-td">Prova funzionale completa dell'impianto ad ogni singolo controllo periodico semestrale.</td></tr>""")
        sb.appendLine("""                <tr style="background-color:#F9FBFD;"><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">0 – 6 anni</td><td class="ri-grid-td">Indirizzato</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">50% all'anno (25% a visita semestrale)</td><td class="ri-grid-td">Ripartito su 2 visite distanziate di non meno di 5 mesi.</td></tr>""")
        sb.appendLine("""                <tr><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">7 – 12 anni</td><td class="ri-grid-td">Indirizzato</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">100% all'anno (50% a visita semestrale)</td><td class="ri-grid-td">Ripartito su 2 visite distanziate di non meno di 5 mesi.</td></tr>""")
        sb.appendLine("""                <tr style="background-color:#F9FBFD;"><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">Al 12° anno</td><td class="ri-grid-td">Tutti i sistemi</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">100% (Verifica Generale)</td><td class="ri-grid-td">Avvio della revisione in fabbrica o sostituzione integrativa dei rivelatori fumo/fiamma (1/3 all'anno dal 13° al 15° anno).</td></tr>""")
        sb.appendLine("""                <tr><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">13 – 24 anni</td><td class="ri-grid-td">Indirizzato</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">25% a visita semestrale (50% all'anno)</td><td class="ri-grid-td">Quota applicabile a seguito del completamento della revisione o sostituzione dei rivelatori.</td></tr>""")
        sb.appendLine("""                <tr style="background-color:#F9FBFD;"><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">Oltre 24 anni</td><td class="ri-grid-td">Indirizzato</td><td class="ri-grid-td" style="color:#1F4E78;font-weight:bold;">50% a visita semestrale (100% all'anno)</td><td class="ri-grid-td">Quota applicabile al termine del secondo ciclo dodicennale (dalla 2ª Verifica Generale).</td></tr>""")
        sb.appendLine("""            </table>""")

        sb.appendLine("""            <p class="ri-section-heading" style="margin-top: 0.25cm;">NOTE OPERATIVE SULLE CADENZE E PROVE:</p>""")
        sb.appendLine("""            <p style="font-size: 8.2pt; line-height: 1.35;">• Intervallo tra i controlli: I controlli periodici devono essere effettuati con cadenza semestrale (almeno due visite all'anno distanziate di non meno di 5 mesi).</p>""")
        sb.appendLine("""            <p style="font-size: 8.2pt; line-height: 1.35;">• Verifica Generale (12° anno): Comporta il controllo preliminare del 100% e l'obbligo di revisione o sostituzione dei rivelatori fumo/fiamma, da completarsi entro 3 anni.</p>""")
        sb.appendLine("""            <p style="font-size: 8.2pt; line-height: 1.35;">• Registrazione obbligatoria: Gli esiti di ciascuna visita devono essere verbalizzati sui Prospetti B.1 e B.2 ed allegati al Registro Antincendio aziendale ex D.Lgs. 81/2008.</p>""")

        appendPageFooterBanner(sb, pageNumber, totalPages)
        sb.appendLine("""        </div>""")
    }

    private fun appendPageClausoleEFirme(
        sb: StringBuilder,
        ri: RilevazioneAntincendio,
        context: HtmlRenderContext,
        firmaTecnicoHtml: String,
        firmaClienteHtml: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val luogoDefault = escapeHtml(context.cantiereNome?.trim().orEmpty())
        val dataDefault = escapeHtml(context.dataIntervento?.trim().orEmpty())
        val manutentoreDefault = escapeHtml(context.nomeManutentore?.trim().orEmpty())
        val responsabileDefault = escapeHtml(ri.responsabileSistema?.trim().orEmpty())

        sb.appendLine("""        <div class="ri-page ri-page-break">""")
        appendPageHeaderLogo(sb)
        sb.appendLine("""            <p class="ri-section-heading">4. CLAUSOLE GENERALI DI MANUTENZIONE E RESPONSABILITÀ (SEMPRE VALIDE)</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; line-height: 1.38; margin-bottom: 6px;">3.1 La responsabilità dell'Impresa Manutentrice decorre esclusivamente dalla data di sottoscrizione del presente verbale ed è limitata alle attività di controllo previste dalla norma UNI 11224. L'impresa risponde dello stato dell'impianto rilevato ed atteso nel presente documento.</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; line-height: 1.38; margin-bottom: 6px;">3.2 Ai sensi del D.Lgs. 81/2008 e del D.M. 1° settembre 2021, il Committente (Datore di Lavoro) ha l'obbligo tassativo di conservare ed archiviare il presente verbale unitamente alle relative liste di riscontro all'interno del Registro dei Controlli Antincendio Aziendale.</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; line-height: 1.38; margin-bottom: 6px;">3.3 Il Committente si impegna a dare preventiva od immediata comunicazione scritta all'Impresa Manutentrice in caso di variazioni di layout, modifiche edili, ristrutturazioni, cambio di destinazione d'uso o installazione di ostacoli che possano influenzare la copertura e la risposta dei rivelatori ai sensi della norma UNI 9795:2021.</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; line-height: 1.38; margin-bottom: 14px;">3.4 Il presente servizio manutentivo non sostituisce l'attività di Sorveglianza Ordinaria (UNI 11224 cap. 9), che rimane a carico del personale interno dell'attività per la verifica visiva quotidiana/settimanale dello stato della centrale e dell'assenza di ostacoli davanti ai dispositivi.</p>""")

        sb.appendLine("""            <p class="ri-section-heading">5. SOTTOSCRIZIONE FORMALE E CONFERMA DI RICEVUTA</p>""")
        sb.appendLine("""            <p style="font-size: 8.5pt; line-height: 1.45; margin-bottom: 12px;">Il sottoscritto Responsabile del Sistema dichiara di aver preso visione dei controlli eseguiti, dell'opzione selezionata e di accettare integralmente le relative clausole e prescrizioni. Il presente documento completo di <strong>$totalPages</strong> pagine è letto, confermato e sottoscritto in <strong>${if (luogoDefault.isNotEmpty()) luogoDefault else "__________________"}</strong> il <strong>${if (dataDefault.isNotEmpty()) dataDefault else "__________________"}</strong></p>""")

        sb.appendLine("""            <table>""")
        sb.appendLine("""                <tr>""")
        sb.appendLine("""                    <td class="ri-grid-td" style="width: 9.90cm; height: 2.80cm; vertical-align: top; padding: 8px;">""")
        sb.appendLine("""                        <p style="font-weight: bold; font-size: 8.5pt;">PER L'IMPRESA MANUTENTRICE</p>""")
        sb.appendLine("""                        <p style="font-size: 8.2pt; margin-bottom: 6px;">Il Tecnico Manutentore Qualificato: <strong>$manutentoreDefault</strong></p>""")
        sb.appendLine("""                        <div style="min-height: 1.00cm;">$firmaTecnicoHtml</div>""")
        sb.appendLine("""                        <p style="font-size: 7.5pt; color: #444;">___________________________________<br/>(Firma leggibile e timbro)</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td class="ri-grid-td" style="width: 9.90cm; height: 2.80cm; vertical-align: top; padding: 8px;">""")
        sb.appendLine("""                        <p style="font-weight: bold; font-size: 8.5pt;">PER IL COMMITTENTE / CLIENTE</p>""")
        sb.appendLine("""                        <p style="font-size: 8.2pt; margin-bottom: 6px;">Il Responsabile del Sistema / Datore di Lavoro: <strong>$responsabileDefault</strong></p>""")
        sb.appendLine("""                        <div style="min-height: 1.00cm;">$firmaClienteHtml</div>""")
        sb.appendLine("""                        <p style="font-size: 7.5pt; color: #444;">___________________________________<br/>(Firma per presa visione ed accettazione)</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")

        appendPageFooterBanner(sb, pageNumber, totalPages)
        sb.appendLine("""        </div>""")
    }
}
