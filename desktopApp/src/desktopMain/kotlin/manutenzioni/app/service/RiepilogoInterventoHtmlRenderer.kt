package manutenzioni.app.service

import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.Impianto
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.MeseScadenzaGroup
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.RilevazioneAntincendio
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.service.CalendarioScadenzeCalculator
import manutenzioni.domain.service.CrossPlantAttivitaResolver

/**
 * Renderer HTML per il **Foglio di Riepilogo Generale Intervento (Master Checklist — R28)**.
 *
 * Produce un documento HTML A4 con campi AcroForm compilabili che sintetizza:
 * 1. Anagrafica Cliente, Cantiere/Sito, Date di contratto/intervento, Verificatore/Manutentore e Firma.
 * 2. Quadro sinottico di tutti gli impianti del cantiere con frequenze applicate, numero attività,
 *    presenza allegati tecnici e campi AcroForm per esito sintetico (OK / OSS / NC) e note.
 * 3. Prospetto delle scadenze pianificate in conformità al Calendario Scadenze (R27).
 */
class RiepilogoInterventoHtmlRenderer {

    fun buildRiepilogoHtml(
        cliente: Cliente?,
        cantiere: Cantiere,
        impianti: List<Impianto>,
        frequenzeSelezionatePerImpianto: Map<String, Set<Periodo>> = emptyMap(),
        frequenzePerImpianto: Map<String, Periodo> = emptyMap(),
        scadenze: List<MeseScadenzaGroup> = CalendarioScadenzeCalculator.calcolaScadenze(cantiere, impianti, 12)
    ): String {
        val clienteNome = escapeHtml(cliente?.nome ?: "Non specificato")
        val cantiereNome = escapeHtml(cantiere.nome)
        val cantiereIndirizzo = escapeHtml(cantiere.indirizzo ?: "-")
        val dataContratto = escapeHtml(cantiere.dataInizioContratto ?: "")
        val dataPrecedente = escapeHtml(cantiere.dataInterventoPrecedente ?: "")
        val dataIntervento = escapeHtml(cantiere.effectiveDataIntervento ?: "")
        val manutentore = escapeHtml(cantiere.nomeManutentore ?: "")
        val firmaHtml = if (!cantiere.firmaManutentore.isNullOrBlank()) {
            """<img src="${cantiere.firmaManutentore.trim()}" alt="Firma" style="max-height: 0.9cm; max-width: 3.5cm; object-fit: contain;" />"""
        } else {
            ""
        }

        val impiantiRows = StringBuilder()
        impianti.forEachIndexed { idx, imp ->
            val rowNum = idx + 1
            val codLabel = if (imp is QuadroBT && imp.sigla.isNotBlank()) {
                "${imp.codIntervento} - ${imp.sigla}"
            } else {
                imp.codIntervento
            }
            val descLabel = when (imp) {
                is QuadroBT -> {
                    val d = imp.descrizioneQuadro.trim()
                    val s = imp.sigla.trim()
                    when {
                        d.isNotEmpty() && s.isNotEmpty() -> "$d ($s)"
                        d.isNotEmpty() -> d
                        else -> imp.nomeCompleto
                    }
                }
                else -> imp.nomeCompleto
            }
            val freqs = frequenzeSelezionatePerImpianto[imp.id]?.takeIf { it.isNotEmpty() }?.sortedBy { it.inMesi() }
                ?: frequenzePerImpianto[imp.id]?.let { listOf(it) }
                ?: CrossPlantAttivitaResolver.resolveFrequenze(imp, impianti).ifEmpty { listOf(Periodo(TipoPeriodo.A, 1)) }

            val freqText = freqs.joinToString(", ") { "${it.label()} [${it.codiceCartiglio()}]" }
            val maxFreq = freqs.maxByOrNull { it.inMesi() } ?: Periodo(TipoPeriodo.A, 1)
            val numAttivita = CrossPlantAttivitaResolver.resolveAttivita(imp, impianti, maxFreq).size

            val allegatoDettaglio = when (imp) {
                is QuadroBT -> if (imp.listaInterruttori.isNotEmpty()) "Sì (${imp.listaInterruttori.sumOf { it.quantita.coerceAtLeast(1) }} diff.)" else "No"
                is ImpiantoEmergenza -> if (imp.listaLampade.isNotEmpty()) "Sì (${imp.listaLampade.size} lampade)" else "No"
                is RilevazioneAntincendio -> if (imp.listaDispositivi.isNotEmpty()) "Sì (${imp.listaDispositivi.size} disp.)" else "No"
                else -> "No"
            }

            val radioGroup = "riepilogo_esito_${rowNum}"
            impiantiRows.appendLine(
                """
                <tr>
                    <td style="text-align:center;">$rowNum</td>
                    <td style="text-align:center; font-weight:bold;">${escapeHtml(codLabel)}</td>
                    <td>${escapeHtml(descLabel)}</td>
                    <td style="text-align:center;">${escapeHtml(freqText)}</td>
                    <td style="text-align:center;">$numAttivita</td>
                    <td style="text-align:center;">${escapeHtml(allegatoDettaglio)}</td>
                    <td style="text-align:center;"><input type="radio" name="$radioGroup" value="OK" /></td>
                    <td style="text-align:center;"><input type="radio" name="$radioGroup" value="OSS" /></td>
                    <td style="text-align:center;"><input type="radio" name="$radioGroup" value="NC" /></td>
                    <td style="padding: 0;"><input type="text" name="riepilogo_note_${rowNum}" value="" style="width:100%; height:100%; min-height:0.40cm; line-height:0.40cm; border:none; outline:none; background:transparent; font-size:7pt; margin:0; padding:0 3px; box-sizing:border-box; vertical-align:middle;" /></td>
                </tr>
                """.trimIndent()
            )
        }

        val scadenzeRows = StringBuilder()
        scadenze.forEach { group ->
            val riepilogoMese = group.scadenze.joinToString(" • ") { item ->
                val codiciFreq = item.frequenzeIncluse.joinToString("/") { it.codiceCartiglio() }
                "${item.codIntervento} ($codiciFreq: ${item.numeroAttivita} att.)"
            }
            scadenzeRows.appendLine(
                """
                <tr>
                    <td style="text-align:center; font-weight:bold;">${escapeHtml(group.etichettaMese)}</td>
                    <td style="text-align:center;">${escapeHtml(group.dataPrevista)}</td>
                    <td style="text-align:center;">${group.scadenze.size}</td>
                    <td>${escapeHtml(riepilogoMese)}</td>
                </tr>
                """.trimIndent()
            )
        }

        return """
        <!DOCTYPE html>
        <html lang="it">
        <head>
            <meta charset="UTF-8" />
            <title>Foglio di Riepilogo Generale Intervento - $cantiereNome</title>
            <style>
                @page {
                    size: A4 portrait;
                    margin: 0.8cm;
                }
                body {
                    font-family: Calibri, Arial, sans-serif;
                    font-size: 8pt;
                    color: #1E293B;
                    margin: 0;
                    padding: 0;
                }
                table {
                    width: 100%;
                    border-collapse: collapse;
                    margin-bottom: 0.35cm;
                }
                th, td {
                    border: 1px solid #334155;
                    padding: 3px 5px;
                    vertical-align: middle;
                }
                .title-banner {
                    background-color: #1E3A8A;
                    color: #FFFFFF;
                    text-align: center;
                    font-weight: bold;
                    font-size: 11pt;
                    padding: 6px;
                }
                .section-header {
                    background-color: #E2E8F0;
                    font-weight: bold;
                    font-size: 8.5pt;
                    text-transform: uppercase;
                    padding: 4px 6px;
                }
                .th-col {
                    background-color: #F1F5F9;
                    font-weight: bold;
                    font-size: 7.5pt;
                    text-align: center;
                }
                input[type="text"] {
                    width: 100%;
                    border: none;
                    outline: none;
                    background: transparent;
                    margin: 0;
                    padding: 0 3px;
                    box-sizing: border-box;
                    font-family: Calibri, Arial, sans-serif;
                    vertical-align: middle;
                }
            </style>
        </head>
        <body>
            <form>
                <table>
                    <tr>
                        <td colspan="4" class="title-banner">
                            FOGLIO DI RIEPILOGO GENERALE INTERVENTO (MASTER CHECKLIST)<br/>
                            <span style="font-size:8.5pt; font-weight:normal;">VERIFICA E MANUTENZIONE PERIODICA PROGRAMMATA IMPIANTI</span>
                        </td>
                    </tr>
                    <tr>
                        <td style="width:25%;"><strong>Cliente:</strong><br/>$clienteNome</td>
                        <td style="width:30%;"><strong>Cantiere / Sito:</strong><br/>$cantiereNome</td>
                        <td style="width:25%;"><strong>Indirizzo:</strong><br/>$cantiereIndirizzo</td>
                        <td style="width:20%;">
                            <strong>Data Inizio Contratto:</strong><br/>
                            <input type="text" name="riepilogo_starting_date" value="$dataContratto" style="width:100%; height:0.40cm; line-height:0.40cm; border:none; outline:none; background:transparent; margin:0; padding:0 2px; font-size:7.5pt; box-sizing:border-box;" />
                        </td>
                    </tr>
                    <tr>
                        <td>
                            <strong>Data Intervento Precedente:</strong><br/>
                            <input type="text" name="riepilogo_previous_date" value="$dataPrecedente" style="width:100%; height:0.40cm; line-height:0.40cm; border:none; outline:none; background:transparent; margin:0; padding:0 2px; font-size:7.5pt; box-sizing:border-box;" />
                        </td>
                        <td>
                            <strong>Data Intervento Attuale:</strong><br/>
                            <input type="text" name="riepilogo_actual_date" value="$dataIntervento" style="width:100%; height:0.40cm; line-height:0.40cm; border:none; outline:none; background:transparent; margin:0; padding:0 2px; font-size:7.5pt; box-sizing:border-box;" />
                        </td>
                        <td>
                            <strong>Verificatore / Manutentore:</strong><br/>
                            <input type="text" name="riepilogo_manutentore" value="$manutentore" style="width:100%; height:0.40cm; line-height:0.40cm; border:none; outline:none; background:transparent; margin:0; padding:0 2px; font-weight:bold; font-size:7.5pt; box-sizing:border-box;" />
                        </td>
                        <td>
                            <strong>Firma Tecnico:</strong><br/>
                            $firmaHtml
                        </td>
                    </tr>
                </table>

                <table>
                    <tr>
                        <td colspan="10" class="section-header">1. Quadro Sinottico Impianti e Schede di Verifica del Cantiere (${impianti.size} moduli)</td>
                    </tr>
                    <tr>
                        <th class="th-col" style="width:5%;">N.</th>
                        <th class="th-col" style="width:10%;">Cod. / Sigla</th>
                        <th class="th-col" style="width:25%;">Descrizione Impianto</th>
                        <th class="th-col" style="width:15%;">Periodicità</th>
                        <th class="th-col" style="width:7%;">N. Att.</th>
                        <th class="th-col" style="width:10%;">Foglio 2</th>
                        <th class="th-col" style="width:4%;">OK</th>
                        <th class="th-col" style="width:4%;">OSS</th>
                        <th class="th-col" style="width:4%;">NC</th>
                        <th class="th-col" style="width:16%;">Note Sintetiche</th>
                    </tr>
                    $impiantiRows
                </table>

                <table>
                    <tr>
                        <td colspan="4" class="section-header">2. Calendario Scadenze Programmate (${scadenze.size} scadenze rilevate)</td>
                    </tr>
                    <tr>
                        <th class="th-col" style="width:18%;">Mese / Anno</th>
                        <th class="th-col" style="width:14%;">Data Prevista</th>
                        <th class="th-col" style="width:10%;">N. Impianti</th>
                        <th class="th-col" style="width:58%;">Impianti e Periodicità in Scadenza</th>
                    </tr>
                    $scadenzeRows
                </table>

                <table>
                    <tr>
                        <td class="section-header">3. Osservazioni Generali sull'Intervento</td>
                    </tr>
                    <tr>
                        <td style="padding: 0;">
                            <input type="text" name="riepilogo_osservazioni_generali" id="riepilogo_osservazioni_generali" value="" style="width:100%; height:1.5cm; line-height:1.5cm; border:none; outline:none; background:transparent; margin:0; padding:0 4px; font-family:Calibri, Arial, sans-serif; font-size:8pt; box-sizing:border-box;" />
                        </td>
                    </tr>
                </table>
            </form>
        </body>
        </html>
        """.trimIndent()
    }

    private fun escapeHtml(text: String): String =
        text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
