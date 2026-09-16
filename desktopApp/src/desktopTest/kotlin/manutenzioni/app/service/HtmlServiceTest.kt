package manutenzioni.app.service

import manutenzioni.domain.model.*
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertContains
import kotlin.test.assertFalse

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

        // Verifica che i radio button abbiano nomi univoci
        assertContains(html, "esito_GE_1")
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
    fun `buildHtml con lista vuota di attivita non genera righe`() {
        val html = htmlService.buildHtml(
            impianto = impiantoGE,
            attivitaFiltrate = emptyList(),
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = null
        )
        // Nessun campo esito generato
        assertFalse(html.contains("esito_GE_"))
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
}
