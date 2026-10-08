package manutenzioni.app.service.attachments

import manutenzioni.domain.model.Periodo
import manutenzioni.domain.service.attachments.CellTextAlign
import manutenzioni.domain.service.attachments.TechnicalCell
import manutenzioni.domain.service.attachments.TechnicalSheetData

/**
 * Renderer HTML centralizzato e riutilizzabile per fogli allegati tecnici (AcroForm).
 *
 * Garantisce:
 * - Larghezza fissa a 18.4cm (perfetta per pagina A4 con margini industriali, zero sbordature).
 * - Layout intestazione uniforme conforme agli standard Desiderio Impianti S.r.l.
 * - Generazione robusta di campi AcroForm interattivi (radio button e input text).
 * - Paginazione automatica controllata (`page-break-before: always;`, `page-break-inside: avoid;`).
 */
class TechnicalSheetHtmlRenderer {

    fun render(
        data: TechnicalSheetData,
        frequenza: Periodo,
        clienteNome: String?
    ): String {
        val sb = StringBuilder()
        val clienteText = if (!clienteNome.isNullOrBlank()) escapeHtml(clienteNome) else "Cliente"
        val codSchedaEscaped = escapeHtml(data.codScheda)
        val prefix = escapeHtml(data.campoAcroFormPrefix)
        val sitoOImpianto = escapeHtml(data.sitoLabel ?: data.impiantoLabel)

        val containerCssClass = when (data.campoAcroFormPrefix) {
            "diff" -> "sheet--componenti"
            "emerg" -> "sheet--lampade"
            else -> "sheet--${data.campoAcroFormPrefix}"
        }

        sb.appendLine("""        <div class="sheet--technical $containerCssClass" style="page-break-before: always; break-before: page; margin-top: 0.6cm; width: 18.4cm;">""")
        sb.appendLine("""            <!-- Titolo documento superiore -->""")
        sb.appendLine("""            <p style="font-size: 7.5pt; font-weight: bold; margin-bottom: 0.2cm; text-transform: uppercase;">Verifiche Periodiche Programmate Impianti Elettrici</p>""")
        sb.appendLine()

        // --- Tabella Intestazione Scheda (larghezza totale: 18.4cm) ---
        sb.appendLine("""            <table style="width: 18.4cm; border-collapse: collapse; table-layout: fixed; margin-bottom: 0.35cm;">""")
        sb.appendLine("""                <colgroup>""")
        sb.appendLine("""                    <col style="width: 2.4cm;" />""")
        sb.appendLine("""                    <col style="width: 1.7cm;" />""")
        sb.appendLine("""                    <col style="width: 4.5cm;" />""")
        sb.appendLine("""                    <col style="width: 4.8cm;" />""")
        sb.appendLine("""                    <col style="width: 5.0cm;" />""")
        sb.appendLine("""                </colgroup>""")

        // Riga 1: Codice, Oggetto, Banner Titolo, Periodicità
        sb.appendLine("""                <tr style="height: 0.65cm;">""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Codice Scheda:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">$codSchedaEscaped</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Oggetto:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">${escapeHtml(data.oggetto)}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td colspan="2" style="border: 0.0133cm solid #000; background-color: #3366ff; color: #ffffff; text-align: center; vertical-align: middle; padding: 2px 4px;">""")
        sb.appendLine("""                        <p style="font-size: 9pt; font-weight: bold; margin: 0; letter-spacing: 0.5px;">${escapeHtml(data.titoloBanner)}</p>""")
        sb.appendLine("""                        <p style="font-size: 6pt; margin: 0;">${escapeHtml(data.sottotitoloBanner)}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Periodicità:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">${escapeHtml(frequenza.label())}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")

        // Riga 2: Azienda, Sito/Impianto, Cliente, Data inizio periodo
        sb.appendLine("""                <tr style="height: 0.65cm;">""")
        sb.appendLine("""                    <td colspan="2" style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Azienda Appaltatrice</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">Desiderio Impianti S.r.l.</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">${if (data.sitoLabel != null) "Sito" else "Impianto"}</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">$sitoOImpianto</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; background-color: #b4c6e7; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Cliente:</p>""")
        sb.appendLine("""                        <p style="font-size: 6.5pt; font-weight: bold; margin: 0;">$clienteText</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Data inizio periodo attività</p>""")
        sb.appendLine("""                        <input type="text" name="${prefix}_data_inizio" id="${prefix}_data_inizio" placeholder="gg/mm/aaaa" style="width: 100%; height: 0.44cm; font-size: 7.5pt; box-sizing: border-box;" />""")
        sb.appendLine("""                        <label for="${prefix}_data_inizio" class="visually-hidden">Data inizio periodo attività</label>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")

        // Riga 3: Premessa/Normative, Verificatore
        sb.appendLine("""                <tr style="height: 0.75cm;">""")
        sb.appendLine("""                    <td colspan="4" style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; font-weight: bold; margin: 0;">Premessa e/o informazioni:</p>""")
        sb.appendLine("""                        <p style="font-size: 4.8pt; margin: 0; color: #333333;">${escapeHtml(data.normative)}</p>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                    <td style="border: 0.0133cm solid #000; padding: 2px 4px; vertical-align: top;">""")
        sb.appendLine("""                        <p style="font-size: 5pt; margin: 0;">Verificatore / Manutentore</p>""")
        sb.appendLine("""                        <input type="text" name="${prefix}_manutentore" id="${prefix}_manutentore" placeholder="Nome verificatore" style="width: 100%; height: 0.44cm; font-size: 7.5pt; box-sizing: border-box;" />""")
        sb.appendLine("""                        <label for="${prefix}_manutentore" class="visually-hidden">Verificatore / Manutentore</label>""")
        sb.appendLine("""                    </td>""")
        sb.appendLine("""                </tr>""")
        sb.appendLine("""            </table>""")
        sb.appendLine()

        // --- Tabella Dati (larghezza totale: 18.4cm) ---
        sb.appendLine("""            <table style="width: 18.4cm; border-collapse: collapse; table-layout: fixed; page-break-inside: auto;">""")
        
        val targetWidthCm = 18.4
        val rawTotalWidth = data.columns.sumOf { col ->
            if (col.subColumns.isNotEmpty()) col.subColumns.sumOf { it.widthCm }
            else col.widthCm
        }
        val scale = if (rawTotalWidth > 0.0 && Math.abs(rawTotalWidth - targetWidthCm) > 0.05) {
            targetWidthCm / rawTotalWidth
        } else 1.0

        // 1. Colgroup
        sb.appendLine("""                <colgroup>""")
        for (col in data.columns) {
            if (col.subColumns.isNotEmpty()) {
                for (sub in col.subColumns) {
                    val w = sub.widthCm * scale
                    sb.appendLine("""                    <col style="width: ${formatCm(w)}cm;" />""")
                }
            } else {
                val w = col.widthCm * scale
                sb.appendLine("""                    <col style="width: ${formatCm(w)}cm;" />""")
            }
        }
        sb.appendLine("""                </colgroup>""")

        // 2. Thead
        val hasSubColumns = data.columns.any { it.subColumns.isNotEmpty() }
        sb.appendLine("""                <thead>""")
        sb.appendLine("""                    <tr style="height: 0.55cm; background-color: #d9e1f2;">""")
        for (col in data.columns) {
            if (col.subColumns.isNotEmpty()) {
                sb.appendLine("""                        <th colspan="${col.subColumns.size}" style="border: 0.0133cm solid #000; font-size: 5.5pt; font-weight: bold; text-align: center; vertical-align: middle;">${escapeHtml(col.title)}</th>""")
            } else {
                val rowspanAttr = if (hasSubColumns) """ rowspan="2"""" else ""
                sb.appendLine("""                        <th${rowspanAttr} style="border: 0.0133cm solid #000; font-size: 6pt; font-weight: bold; text-align: center; vertical-align: middle;">${escapeHtml(col.title)}</th>""")
            }
        }
        sb.appendLine("""                    </tr>""")

        if (hasSubColumns) {
            sb.appendLine("""                    <tr style="height: 0.45cm; background-color: #d9e1f2;">""")
            for (col in data.columns) {
                if (col.subColumns.isNotEmpty()) {
                    for (sub in col.subColumns) {
                        sb.appendLine("""                        <th style="border: 0.0133cm solid #000; font-size: 5pt; font-weight: bold; text-align: center; vertical-align: middle;">${escapeHtml(sub.title)}</th>""")
                    }
                }
            }
            sb.appendLine("""                    </tr>""")
        }
        sb.appendLine("""                </thead>""")

        // 3. Tbody
        sb.appendLine("""                <tbody>""")
        for (row in data.rows) {
            sb.appendLine("""                    <tr style="height: ${formatCm(row.heightCm)}cm; page-break-inside: avoid;">""")
            for (cell in row.cells) {
                renderCell(sb, cell)
            }
            sb.appendLine("""                    </tr>""")
        }
        sb.appendLine("""                </tbody>""")
        sb.appendLine("""            </table>""")

        // --- Footer ---
        sb.appendLine("""            <p style="font-size: 5pt; color: #555555; margin-top: 0.25cm; text-align: left;">Desiderio Impianti S.r.l. &nbsp;&nbsp;&nbsp; ${escapeHtml(data.footerDocTitle)} - $codSchedaEscaped</p>""")
        sb.appendLine("""        </div>""")

        return sb.toString()
    }

    private fun renderCell(sb: StringBuilder, cell: TechnicalCell) {
        when (cell) {
            is TechnicalCell.Text -> {
                val alignStr = when (cell.align) {
                    CellTextAlign.LEFT -> "text-align: left;"
                    CellTextAlign.CENTER -> "text-align: center;"
                    CellTextAlign.RIGHT -> "text-align: right;"
                }
                val weightStr = if (cell.bold) " font-weight: bold;" else ""
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; font-size: ${cell.fontSizePt}pt; ${alignStr} vertical-align: middle; padding: 2px;$weightStr">${escapeHtml(cell.text)}</td>""")
            }
            is TechnicalCell.Radio -> {
                val id = "${cell.groupName}_${cell.optionValue.lowercase()}"
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; text-align: center; vertical-align: middle; padding: 1px;">""")
                sb.appendLine("""                            <input type="radio" name="${cell.groupName}" id="$id" value="${cell.optionValue}" />""")
                sb.appendLine("""                            <label for="$id" class="visually-hidden">${escapeHtml(cell.label)}</label>""")
                sb.appendLine("""                        </td>""")
            }
            is TechnicalCell.TextInput -> {
                val alignStr = when (cell.align) {
                    CellTextAlign.LEFT -> "text-align: left;"
                    CellTextAlign.CENTER -> "text-align: center;"
                    CellTextAlign.RIGHT -> "text-align: right;"
                }
                val fontPt = if (cell.fontSizePt < 7.0) 7.5 else cell.fontSizePt
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000; ${alignStr} vertical-align: middle; padding: 1px 2px;">""")
                sb.appendLine("""                            <input type="text" name="${cell.fieldName}" id="${cell.fieldName}" placeholder="${escapeHtml(cell.placeholder)}" style="width: 100%; height: 0.44cm; font-size: ${fontPt}pt; box-sizing: border-box; ${alignStr}" />""")
                sb.appendLine("""                            <label for="${cell.fieldName}" class="visually-hidden">${escapeHtml(cell.label)}</label>""")
                sb.appendLine("""                        </td>""")
            }
            is TechnicalCell.Empty -> {
                sb.appendLine("""                        <td style="border: 0.0133cm solid #000;"></td>""")
            }
        }
    }

    private fun formatCm(value: Double): String = String.format(java.util.Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
}
