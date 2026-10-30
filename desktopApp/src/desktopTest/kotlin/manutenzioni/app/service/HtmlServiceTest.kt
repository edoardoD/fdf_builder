package manutenzioni.app.service

import manutenzioni.domain.model.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * Test per HtmlService — il template engine che genera l'HTML
 * da cui iText7 produce il PDF AcroForm.
 *
 * Nota: questi test verificano la logica di sostituzione dei placeholder
 * e la generazione delle righe <tr>. NON testano la conversione PDF.
 */
class HtmlServiceTest {

    private val htmlService = HtmlService()

    // Fixture comune
    private val impiantoGE = ImpiantoStandard(
        id = "test-ge",
        codIntervento = "GE",
        nomeCompleto = "Gruppo Elettrogeno",
        premessa = "Premessa di sicurezza per GE.",
        listaAttivita = listOf(
            Attivita(1, "Controllo visivo", "Controllo stato generale", Periodo(TipoPeriodo.M, 1)),
            Attivita(2, "Prova funzionale", "Prova avviamento", Periodo(TipoPeriodo.M, 3)),
            Attivita(3, "Manutenzione", "Cambio olio e filtri", Periodo(TipoPeriodo.A, 1))
        ),
        listaNormative = listOf(Normativa("CEI 11-20", "Impianti di produzione"))
    )

    @Test
    fun `buildHtml sostituisce COD_SCHEDA con codIntervento`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        assertContains(html, "GE")
    }

    @Test
    fun `buildHtml sostituisce OGGETTO con nomeCompleto`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        assertContains(html, "Gruppo Elettrogeno")
    }

    @Test
    fun `buildHtml per QuadroBT inietta descrizioneQuadro specifica nel cartiglio`() {
        val quadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadro elettrico",
            sigla = "QG",
            descrizioneQuadro = "Quadro Generale Reparto Presse"
        )
        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = emptyList(),
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        assertContains(html, "Quadro Generale Reparto Presse - QG")
    }

    @Test
    fun `buildHtml inietta nome cliente`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Acme S.r.l."
        )
        assertContains(html, "Cliente: Acme S.r.l.")
    }

    @Test
    fun `buildHtml senza cliente mostra placeholder generico`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        assertContains(html, "<p>Cliente</p>")
    }

    @Test
    fun `buildHtml genera righe con radio button per esiti`() {
        val attivitaFiltrate = listOf(
            Attivita(1, "Controllo", "Test attività", Periodo(TipoPeriodo.M, 1))
        )
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivitaFiltrate,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = null
        )

        // Verifica che i radio button per esiti primari abbiano name="esito_GE_1"
        assertContains(html, "name=\"esito_GE_1\"")
        // Verifica che l'esito VN sia separato e non esclusivo con name="esito_vn_GE_1"
        assertContains(html, "name=\"esito_vn_GE_1\"")
        // Verifica che ci siano tutti e 6 gli esiti
        listOf("P", "PI", "NA", "NP", "VN", "B").forEach { esito ->
            assertContains(html, "value=\"$esito\"")
        }
    }

    @Test
    fun `buildHtml genera campo nota per ogni riga`() {
        val attivitaFiltrate = listOf(
            Attivita(1, "Controllo", "Test attività", Periodo(TipoPeriodo.M, 1))
        )
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivitaFiltrate,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = null
        )
        assertContains(html, "note_GE_1")
    }

    @Test
    fun `buildHtml con QuadroBT mostra sigla nel codice scheda`() {
        val quadro = QuadroBT(
            id = "test-q",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG1",
            descrizioneQuadro = "Quadro Generale Piano Terra",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Controllo visivo", Periodo(TipoPeriodo.M, 1))
            )
        )
        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = quadro.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = null
        )
        assertContains(html, "Q - QG1")
    }

    @Test
    fun `buildHtml con QuadroBT mostra ubicazione e interruttori nella premessa`() {
        val quadro = QuadroBT(
            id = "test-q",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG1",
            descrizioneQuadro = "Piano Terra",
            listaInterruttori = listOf(
                InterruttoreBT(id = "i1", nome = "ABB 63A"),
                InterruttoreBT(id = "i2", nome = "Schneider 32A")
            ),
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Test", Periodo(TipoPeriodo.M, 1))
            )
        )
        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = quadro.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = null
        )
        assertContains(html, "Ubicazione Quadro: Piano Terra")
        assertContains(html, "ABB 63A")
        assertContains(html, "Schneider 32A")
    }

    @Test
    fun `buildHtml con lista vuota di attivita genera 30 righe di riempimento compilabili`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = emptyList(),
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        // Con R2, tutte le 30 righe di riempimento hanno campi form compilabili
        assertContains(html, "name=\"esito_GE_1\"")
        assertContains(html, "name=\"tipo_GE_1\"")
        assertContains(html, "name=\"esito_GE_30\"")
        assertContains(html, "name=\"tipo_GE_30\"")
    }

    @Test
    fun `buildHtml escapa caratteri HTML speciali`() {
        val impiantoConCaratteriSpeciali = ImpiantoStandard(
            id = "test-special",
            codIntervento = "T&S",
            nomeCompleto = "Test <Script> & Alert",
            listaAttivita = listOf(
                Attivita(1, "Test", "Descrizione con \"virgolette\" e <tag>", Periodo(TipoPeriodo.M, 1))
            )
        )
        val html = htmlService.buildHtml(
            impianto = impiantoConCaratteriSpeciali,
            attivitaFiltrate = impiantoConCaratteriSpeciali.listaAttivita,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = "O'Brien & Associati"
        )
        // I caratteri speciali devono essere escapati
        assertContains(html, "&amp;")
        assertFalse(html.contains("<Script>")) // Il tag deve essere escapato
    }

    @Test
    fun `buildHtml con QuadroBT e lista interruttori genera secondo foglio con tabella differenziali`() {
        val quadro = QuadroBT(
            id = "test-q-diff",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG1",
            descrizioneQuadro = "Piano Terra - Locale Tecnico",
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "i1",
                    nome = "Magnetotermico Diff",
                    produttore = "ABB",
                    codiceArticolo = "S204C63 + DDA204AC",
                    siglaCircuito = "QF1",
                    quantita = 1,
                    caratteristicheTecniche = mapOf("taratura" to "0,03 - AC")
                ),
                InterruttoreBT(
                    id = "i2",
                    nome = "Interruttore Differenziale Puro",
                    produttore = "BTicino",
                    codiceArticolo = "GN8813AC16",
                    siglaCircuito = "QF2",
                    quantita = 1,
                    caratteristicheTecniche = mapOf("Idn" to "0,3 - A")
                )
            ),
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica visiva", Periodo(TipoPeriodo.M, 1))
            )
        )

        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = quadro.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Cavarei Soc. Coop."
        )

        // Verifica interruzione di pagina per il secondo foglio
        assertContains(html, "page-break-before: always")
        assertContains(html, "sheet--componenti")

        // Intestazione del secondo foglio
        assertContains(html, "ELENCO INTERRUTTORI DIFFERENZIALI")
        assertContains(html, "Verifiche Periodiche Programmate Impianti Elettrici")
        assertContains(html, "Cavarei Soc. Coop.")
        assertContains(html, "Piano Terra - Locale Tecnico")

        // Intestazioni colonne tabella differenziali
        assertContains(html, "Sigla Quadro")
        assertContains(html, "Sigla Interruttore")
        assertContains(html, "Dati Interruttore")
        assertContains(html, "Taratura diff.")
        assertContains(html, "Prova con tasto")
        assertContains(html, "Prova con strumento")
        assertContains(html, "NR.MISURA")

        // Dati interruttori
        assertContains(html, "QG1")
        assertContains(html, "QF1")
        assertContains(html, "ABB S204C63 + DDA204AC")
        assertContains(html, "0,03 - AC")

        assertContains(html, "QF2")
        assertContains(html, "BTicino GN8813AC16")
        assertContains(html, "0,3 - A")

        // Campi AcroForm per prova con tasto e prova con strumento
        assertContains(html, "name=\"tasto_diff_Q_1\"")
        assertContains(html, "name=\"strumento_diff_Q_1\"")
        assertContains(html, "name=\"misura_diff_Q_1\"")

        assertContains(html, "name=\"tasto_diff_Q_2\"")
        assertContains(html, "name=\"strumento_diff_Q_2\"")
        assertContains(html, "name=\"misura_diff_Q_2\"")
    }

    @Test
    fun `buildHtml con interruttore con quantita maggiore di 1 srotola le righe fisiche`() {
        val quadro = QuadroBT(
            id = "test-q-qty",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QC Tangram",
            descrizioneQuadro = "Piano 1",
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "i1",
                    nome = "Differenziale",
                    produttore = "ABB",
                    codiceArticolo = "XT1B+RC222",
                    siglaCircuito = "QF1",
                    quantita = 2,
                    caratteristicheTecniche = mapOf("taratura" to "1A-1SEC")
                )
            ),
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica visiva", Periodo(TipoPeriodo.M, 1))
            )
        )

        val html = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = quadro.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Desiderio Impianti"
        )

        // Dovrebbero esserci 2 righe srotolate: QF1.1 e QF1.2
        assertContains(html, "QF1.1")
        assertContains(html, "QF1.2")

        // Campi AcroForm distinti per riga
        assertContains(html, "name=\"tasto_diff_Q_1\"")
        assertContains(html, "name=\"tasto_diff_Q_2\"")
        assertContains(html, "name=\"misura_diff_Q_1\"")
        assertContains(html, "name=\"misura_diff_Q_2\"")
    }

    @Test
    fun `buildHtml con impianto non QuadroBT non genera secondo foglio differenziali`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Test Client"
        )

        assertFalse(html.contains("ELENCO INTERRUTTORI DIFFERENZIALI"))
        assertFalse(html.contains("sheet--componenti"))
        assertFalse(html.contains("tasto_diff_"))
    }

    @Test
    fun `generazione end-to-end PDF produce documento multipagina con campi AcroForm interruttori`() {
        val quadro = QuadroBT(
            id = "test-q-e2e",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG Tangram",
            descrizioneQuadro = "Piano Terra",
            listaInterruttori = listOf(
                InterruttoreBT(
                    id = "i1",
                    nome = "Differenziale Generale",
                    produttore = "ABB",
                    codiceArticolo = "XT1B+RC222",
                    siglaCircuito = "QF1",
                    quantita = 1,
                    caratteristicheTecniche = mapOf("taratura" to "1A-1SEC")
                ),
                InterruttoreBT(
                    id = "i2",
                    nome = "Magnetotermico Diff Prese",
                    produttore = "ABB",
                    codiceArticolo = "S204C63 + DDA204AC",
                    siglaCircuito = "QF2",
                    quantita = 1,
                    caratteristicheTecniche = mapOf("taratura" to "0,03 - AC")
                )
            ),
            listaAttivita = listOf(
                Attivita(1, "Controllo visivo", "Verifica serraggi e pulizia", Periodo(TipoPeriodo.A, 1)),
                Attivita(2, "Verifica termografica", "Rilievo punti caldi", Periodo(TipoPeriodo.A, 1))
            )
        )

        val htmlContent = htmlService.buildHtml(
            impianto = quadro,
            attivitaFiltrate = quadro.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Cavarei Soc. Coop."
        )

        val tempHtml = java.io.File.createTempFile("test_quadro_", ".html")
        val tempPdf = java.io.File.createTempFile("test_quadro_", ".pdf")
        tempHtml.writeText(htmlContent)

        try {
            val pdfEngine = Pdf()
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            assertTrue(tempPdf.exists())
            assertTrue(tempPdf.length() > 0)

            // Verifica con iText7 che il PDF abbia almeno 2 pagine
            com.itextpdf.kernel.pdf.PdfReader(tempPdf).use { reader ->
                com.itextpdf.kernel.pdf.PdfDocument(reader).use { pdfDoc ->
                    assertTrue(pdfDoc.numberOfPages >= 2, "Il PDF deve avere almeno 2 fogli (checklist + componenti), trovate: ${pdfDoc.numberOfPages}")
                    
                    val acroForm = com.itextpdf.forms.PdfAcroForm.getAcroForm(pdfDoc, false)
                    kotlin.test.assertNotNull(acroForm, "AcroForm deve essere presente nel PDF")

                    // Verifica presenza dei campi form della scheda 1 e della scheda 2
                    val formFields = acroForm.allFormFields
                    assertTrue(formFields.containsKey("esito_Q_1"), "Deve contenere i radio esito della scheda 1")
                    assertTrue(formFields.containsKey("esito_vn_Q_1"), "Deve contenere il radio VN indipendente della scheda 1")
                    assertTrue(formFields.containsKey("tasto_diff_Q_1"), "Deve contenere i radio tasto prova della scheda 2")
                    assertTrue(formFields.containsKey("misura_diff_Q_1"), "Deve contenere il campo misura della scheda 2")
                    assertTrue(formFields.containsKey("tasto_diff_Q_2"), "Deve contenere i radio tasto prova per interruttore 2")
                    assertTrue(formFields.containsKey("misura_diff_Q_2"), "Deve contenere il campo misura per interruttore 2")
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `buildHtml genera VN non esclusivo con name separato ed estetica radio`() {
        val attivita = listOf(
            Attivita(1, "Verifica", "Controllo stato generale", Periodo(TipoPeriodo.M, 1)),
            Attivita(2, "Misura", "Misura parametri", Periodo(TipoPeriodo.M, 1))
        )

        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivita,
            frequenza = Periodo(TipoPeriodo.M, 1),
            clienteNome = "Azienda Alfa"
        )

        // Per riga 1:
        // Gli esiti primari (P, PI, NA, NP, B) appartengono a "esito_GE_1"
        listOf("p", "pi", "na", "np", "b").forEach { esito ->
            val expectedSnippet = """<input type="radio" name="esito_GE_1" id="esito_GE_1_$esito" value="${esito.uppercase()}" />"""
            assertContains(html, expectedSnippet)
        }

        // L'esito VN appartiene a "esito_vn_GE_1" e mantiene type="radio" per l'estetica a sfera
        val vnSnippet = """<input type="radio" name="esito_vn_GE_1" id="esito_GE_1_vn" value="VN" />"""
        assertContains(html, vnSnippet)

        // Per riga 2:
        assertContains(html, """<input type="radio" name="esito_GE_2" id="esito_GE_2_p" value="P" />""")
        assertContains(html, """<input type="radio" name="esito_vn_GE_2" id="esito_GE_2_vn" value="VN" />""")

        // Verifica generazione reale del PDF e presenza dei campi form
        val tempHtml = java.io.File.createTempFile("test_vn_", ".html")
        val tempPdf = java.io.File.createTempFile("test_vn_", ".pdf")
        tempHtml.writeText(html)
        try {
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            com.itextpdf.kernel.pdf.PdfReader(tempPdf).use { reader ->
                com.itextpdf.kernel.pdf.PdfDocument(reader).use { pdfDoc ->
                    val acroForm = com.itextpdf.forms.PdfAcroForm.getAcroForm(pdfDoc, false)
                    val fields = acroForm.allFormFields

                    val primaryField = fields["esito_GE_1"]
                    kotlin.test.assertNotNull(primaryField, "Il campo esito_GE_1 deve esistere")
                    assertTrue(primaryField is com.itextpdf.forms.fields.PdfButtonFormField && primaryField.isRadio)

                    val vnField = fields["esito_vn_GE_1"]
                    kotlin.test.assertNotNull(vnField, "Il campo esito_vn_GE_1 deve esistere")
                    assertTrue(vnField is com.itextpdf.forms.fields.PdfButtonFormField && vnField.isRadio)
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `buildHtml verifica ordine intestazione colonne tabella attivita`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Cliente Test"
        )

        // Sezione 5: verifica che le colonne dell'intestazione compaiano nell'ordine esatto:
        // N. Attività, Tipo di Attività, Zona, Periodicità, Attività, Esito, Nota
        val pattern = Regex(
            """N\.\s*Attività[\s\S]*?Tipo di Attività[\s\S]*?Zona[\s\S]*?Periodicità[\s\S]*?Attività[\s\S]*?Esito[\s\S]*?Nota"""
        )
        assertTrue(pattern.containsMatchIn(html), "L'ordine delle intestazioni di colonna deve essere: N. Attività, Tipo di Attività, Zona, Periodicità, Attività, Esito, Nota")
    }

    @Test
    fun `buildHtml con nAttivita non contigui formatta progressivo da 1 a N e periodicita 0n e n`() {
        // Attività con nAttivita non contigui (1, 4, 9, 25) e frequenze miste mesi/anni
        val attivitaNonContigue = listOf(
            Attivita(1, "Controllo", "Controllo visivo mensile", Periodo(TipoPeriodo.M, 1), zona = "Zona 1"),
            Attivita(4, "Controllo", "Controllo semestrale", Periodo(TipoPeriodo.M, 6), zona = "Zona 2"),
            Attivita(9, "Verifica", "Verifica annuale", Periodo(TipoPeriodo.A, 1), zona = "Zona 3"),
            Attivita(25, "Revisione", "Revisione biennale", Periodo(TipoPeriodo.A, 2), zona = "Zona 4")
        )

        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivitaNonContigue,
            frequenza = Periodo(TipoPeriodo.A, 2),
            clienteNome = "Collaudo Periodicità"
        )

        // 1. Verifica che la prima colonna mostri i numeri progressivi 1, 2, 3, 4 associati alle rispettive attività
        val row3Regex = Regex("""<td class="cell--data-empty"><p>3</p></td>\s*<td class="cell--data-empty"><p>Verifica</p></td>""")
        val row4Regex = Regex("""<td class="cell--data-empty"><p>4</p></td>\s*<td class="cell--data-empty"><p>Revisione</p></td>""")
        assertTrue(row3Regex.containsMatchIn(html), "L'attività 3 deve avere progressivo 1..N pari a 3")
        assertTrue(row4Regex.containsMatchIn(html), "L'attività 4 deve avere progressivo 1..N pari a 4")

        // Verifica che i numeri statici 9 e 25 del DB NON siano associati come numero di riga alle attività
        val static9Regex = Regex("""<td class="cell--data-empty"><p>9</p></td>\s*<td class="cell--data-empty"><p>Verifica</p></td>""")
        val static25Regex = Regex("""<td class="cell--data-empty"><p>25</p></td>\s*<td class="cell--data-empty"><p>Revisione</p></td>""")
        assertFalse(static9Regex.containsMatchIn(html), "Non deve comparire il numero statico 9 del DB per l'attività Verifica")
        assertFalse(static25Regex.containsMatchIn(html), "Non deve comparire il numero statico 25 del DB per l'attività Revisione")

        // 2. Verifica che la quarta colonna mostri il codice periodicità nel formato rigoroso:
        // '0n' per mesi (01, 06) e 'n' per anni (1, 2)
        assertContains(html, """<td class="cell--data-empty"><p>01</p></td>""")
        assertContains(html, """<td class="cell--data-empty"><p>06</p></td>""")
        assertContains(html, """<td class="cell--data-empty"><p>1</p></td>""")
        assertContains(html, """<td class="cell--data-empty"><p>2</p></td>""")
    }

    @Test
    fun `generazione end-to-end PDF verifica compilabilita AcroForm su tutte le 30 righe e riquadro note`() {
        val dueAttivita = listOf(
            Attivita(1, "Ispezione", "Prima attività", Periodo(TipoPeriodo.M, 6), zona = "Z1"),
            Attivita(2, "Test", "Seconda attività", Periodo(TipoPeriodo.M, 6), zona = "Z2")
        )

        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = dueAttivita,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Ospedale San Raffaele",
            cantiereNome = "Centrale Termica",
            dataInizioContratto = "01/01/2026",
            dataIntervento = "17/10/2026",
            dataInterventoPrecedente = "15/04/2026",
            nomeManutentore = "Ing. Desiderio"
        )

        // Generazione fisica del PDF e analisi delle annotazioni AcroForm con iText7
        val tempHtml = java.io.File.createTempFile("test_acroform_30_", ".html")
        val tempPdf = java.io.File.createTempFile("test_acroform_30_", ".pdf")
        tempHtml.writeText(html)

        try {
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            com.itextpdf.kernel.pdf.PdfReader(tempPdf).use { reader ->
                com.itextpdf.kernel.pdf.PdfDocument(reader).use { pdfDoc ->
                    val acroForm = com.itextpdf.forms.PdfAcroForm.getAcroForm(pdfDoc, false)
                    assertNotNull(acroForm, "L'AcroForm deve essere presente nel PDF generato")
                    val fields = acroForm.allFormFields

                    // 1. Verifica campi form su riga 1 (attività compilata)
                    assertTrue(fields.containsKey("esito_GE_1"), "Deve contenere esito_GE_1")
                    assertTrue(fields.containsKey("esito_vn_GE_1"), "Deve contenere esito_vn_GE_1")
                    assertTrue(fields.containsKey("note_GE_1"), "Deve contenere note_GE_1")

                    // 2. Verifica campi form su riga 30 (riga di riempimento)
                    assertTrue(fields.containsKey("tipo_GE_30"), "Deve contenere tipo_GE_30")
                    assertTrue(fields.containsKey("zona_GE_30"), "Deve contenere zona_GE_30")
                    assertTrue(fields.containsKey("periodicita_GE_30"), "Deve contenere periodicita_GE_30")
                    assertTrue(fields.containsKey("attivita_GE_30"), "Deve contenere attivita_GE_30")
                    assertTrue(fields.containsKey("esito_GE_30"), "Deve contenere esito_GE_30")
                    assertTrue(fields.containsKey("esito_vn_GE_30"), "Deve contenere esito_vn_GE_30")
                    assertTrue(fields.containsKey("note_GE_30"), "Deve contenere note_GE_30")

                    // 3. Verifica campo note generali nel footer (SEZIONE 8)
                    assertTrue(fields.containsKey("note_generali_GE"), "Deve contenere note_generali_GE in Sezione 8")

                    // 4. Verifica geometrica: ogni widget AcroForm deve avere larghezza e altezza > 0
                    for ((name, field) in fields) {
                        for (widget in field.widgets) {
                            val rect = widget.rectangle?.toRectangle()
                            assertNotNull(rect, "Il widget per $name deve avere un bounding box definito")
                            assertTrue(
                                rect.width > 0f && rect.height > 0f,
                                "Il campo $name ha bounding box non valido: ${rect.width}x${rect.height}"
                            )
                        }
                    }
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `buildHtml inietta le tre date distinte nei rispettivi campi AcroForm`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = impiantoGE.listaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Committente S.p.A.",
            cantiereNome = "Stabilimento Nord",
            dataInizioContratto = "01/01/2026",
            dataInterventoPrecedente = "15/04/2026",
            dataIntervento = "17/10/2026",
            nomeManutentore = "Ing. Edoardo Desiderio"
        )

        // 1. Data inizio contratto nel campo starting_date
        assertContains(html, """name="starting_date" id="starting-date" value="01/01/2026"""")

        // 2. Data intervento precedente nel campo previous_date
        assertContains(html, """name="previous_date" id="previous-date" value="15/04/2026"""")

        // 3. Data intervento nel campo actual_date
        assertContains(html, """name="actual_date" id="today-date" value="17/10/2026"""")
    }

    @Test
    fun `buildHtml popola zona mancante o vuota con fallback attivita`() {
        val attivitaSenzaZona = listOf(
            Attivita(nAttivita = 1, tipoAttivita = "Controllo", descrizione = "Zona null", frequenza = Periodo(TipoPeriodo.M, 1), zona = null),
            Attivita(nAttivita = 2, tipoAttivita = "Verifica", descrizione = "Zona blank", frequenza = Periodo(TipoPeriodo.M, 3), zona = "   "),
            Attivita(nAttivita = 3, tipoAttivita = "Misura", descrizione = "Zona esplicita", frequenza = Periodo(TipoPeriodo.M, 6), zona = "Locale Quadri")
        )
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivitaSenzaZona,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Test Zona Fallback"
        )

        val row1ZonaDefault = Regex("""<td class="cell--data-empty"><p>1</p></td>\s*<td class="cell--data-empty"><p>Controllo</p></td>\s*<td class="cell--data-empty"><p>attività</p></td>""")
        val row2ZonaDefault = Regex("""<td class="cell--data-empty"><p>2</p></td>\s*<td class="cell--data-empty"><p>Verifica</p></td>\s*<td class="cell--data-empty"><p>attività</p></td>""")
        val row3ZonaEsplicita = Regex("""<td class="cell--data-empty"><p>3</p></td>\s*<td class="cell--data-empty"><p>Misura</p></td>\s*<td class="cell--data-empty"><p>Locale Quadri</p></td>""")

        assertTrue(row1ZonaDefault.containsMatchIn(html), "Riga con zona null deve mostrare 'attività'")
        assertTrue(row2ZonaDefault.containsMatchIn(html), "Riga con zona vuota deve mostrare 'attività'")
        assertTrue(row3ZonaEsplicita.containsMatchIn(html), "Riga con zona valorizzata deve preservare 'Locale Quadri'")
    }

    @Test
    fun `buildHtml ordina righe attivita da 01-06 a 1-n anche se passate non ordinate`() {
        val attivitaDisordinate = listOf(
            Attivita(nAttivita = 10, tipoAttivita = "A2", descrizione = "Attività 2 anni", frequenza = Periodo(TipoPeriodo.A, 2), zona = "Z-A2"),
            Attivita(nAttivita = 20, tipoAttivita = "M6", descrizione = "Attività 6 mesi", frequenza = Periodo(TipoPeriodo.M, 6), zona = "Z-M6"),
            Attivita(nAttivita = 30, tipoAttivita = "A1", descrizione = "Attività 1 anno", frequenza = Periodo(TipoPeriodo.A, 1), zona = "Z-A1"),
            Attivita(nAttivita = 40, tipoAttivita = "M1", descrizione = "Attività 1 mese", frequenza = Periodo(TipoPeriodo.M, 1), zona = "Z-M1"),
            Attivita(nAttivita = 50, tipoAttivita = "M3", descrizione = "Attività 3 mesi", frequenza = Periodo(TipoPeriodo.M, 3), zona = "Z-M3")
        )
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = attivitaDisordinate,
            frequenza = Periodo(TipoPeriodo.A, 2),
            clienteNome = "Test Ordine"
        )

        val pattern = Regex(
            """<td class="cell--data-empty"><p>1</p></td>[\s\S]*?<p>01</p>[\s\S]*?""" +
            """<td class="cell--data-empty"><p>2</p></td>[\s\S]*?<p>03</p>[\s\S]*?""" +
            """<td class="cell--data-empty"><p>3</p></td>[\s\S]*?<p>06</p>[\s\S]*?""" +
            """<td class="cell--data-empty"><p>4</p></td>[\s\S]*?<p>1</p>[\s\S]*?""" +
            """<td class="cell--data-empty"><p>5</p></td>[\s\S]*?<p>2</p>"""
        )
        assertTrue(pattern.containsMatchIn(html), "Le righe devono essere ordinate da 01-06 a 1-n con progressivo 1..5")
    }

    @Test
    fun `buildHtml sposta legenda nella colonna Nota di thead e 30 righe stanno in 1 sola pagina PDF`() {
        val trentaAttivita = (1..30).map { idx ->
            Attivita(
                nAttivita = idx,
                tipoAttivita = "Controllo",
                descrizione = "Verifica strumentale e funzionale componente #$idx",
                frequenza = if (idx <= 15) Periodo(TipoPeriodo.M, 6) else Periodo(TipoPeriodo.A, 1),
                zona = if (idx % 2 == 0) null else "Zona $idx"
            )
        }
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = trentaAttivita,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Test 1 Pagina 30 Righe"
        )

        // 1. Nessuna riga row--legend nel tfoot
        assertFalse(html.contains("row--legend"), "La riga <tr class=\"row--legend\"> nel tfoot deve essere rimossa")
        // 2. La legenda è presente nella sotto-intestazione della colonna Nota (thead)
        assertContains(html, "cell--legend")
        assertContains(html, "<strong>Legenda:</strong> <strong>P</strong>=Positivo")

        // 3. Verifica che il PDF generato con 30 righe + Note Generali stia in esattamente 1 pagina A4
        val tempHtml = java.io.File.createTempFile("test_30rows_1page_", ".html")
        val tempPdf = java.io.File.createTempFile("test_30rows_1page_", ".pdf")
        tempHtml.writeText(html)
        try {
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            com.itextpdf.kernel.pdf.PdfReader(tempPdf).use { reader ->
                com.itextpdf.kernel.pdf.PdfDocument(reader).use { pdfDoc ->
                    kotlin.test.assertEquals(
                        1,
                        pdfDoc.numberOfPages,
                        "Una scheda standard con 30 righe e legenda spostata in colonna Nota deve occupare esattamente 1 pagina A4"
                    )
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `manutenzioni_db json non contiene nAttivita o n_attivita e carica numeri dinamici`() {
        val resourceStream = this::class.java.classLoader.getResourceAsStream("manutenzioni_db.json")
        assertNotNull(resourceStream, "manutenzioni_db.json deve essere presente nelle risorse")
        val rawJson = resourceStream.bufferedReader().readText()
        assertFalse(rawJson.contains("\"nAttivita\""), "manutenzioni_db.json non deve contenere il campo statico nAttivita")
        assertFalse(rawJson.contains("\"n_attivita\""), "manutenzioni_db.json non deve contenere il campo statico n_attivita")
    }

    @Test
    fun `GenerateHtmlCli genera file HTML valido da linea di comando non interattiva`() {
        val tempOut = java.io.File.createTempFile("cli_preview_", ".html")
        try {
            val html = manutenzioni.app.cli.GenerateHtmlCli.execute(
                arrayOf(
                    "--impianto", "GE",
                    "--frequenza", "06",
                    "--cliente", "Cliente CLI Test",
                    "--cantiere", "Cantiere CLI Test",
                    "--output", tempOut.absolutePath
                )
            )
            assertTrue(tempOut.exists() && tempOut.length() > 0, "Il file HTML di output deve essere creato")
            assertContains(html, "Cliente: Cliente CLI Test")
            assertContains(html.uppercase(), "GRUPP")
            assertContains(html, "<td class=\"cell--data-empty\"><p>attività</p></td>")
        } finally {
            tempOut.delete()
        }
    }
}






