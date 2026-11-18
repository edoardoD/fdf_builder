package manutenzioni.app.service

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.forms.fields.PdfButtonFormField
import com.itextpdf.forms.fields.PdfTextFormField
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import manutenzioni.app.service.attachments.QuadroBtAttachmentProvider
import manutenzioni.app.service.html.AttivitaTableHtmlBuilder
import manutenzioni.app.service.html.HtmlEscaper
import manutenzioni.domain.model.*
import manutenzioni.domain.service.HtmlRenderContext
import manutenzioni.domain.service.attachments.*
import java.io.File
import kotlin.test.*

/**
 * Empirical challenge suite for Milestone 2:
 * 1. AcroForm radio button group naming uniqueness (single-plant, multi-plant, multi-copy).
 * 2. XML/HTML character escaping under adversarial/hostile input vectors.
 * 3. 30-row fixed grid padding: 5 -> 25 fillers; 30 -> 0 fillers; >30 -> no truncation.
 * 4. End-to-end PDF generation and AcroForm field manipulation with iText7.
 * 5. Multithreaded concurrency stress testing on template loader and HTML generation facade.
 */
class ChallengerM2ServiceStressTest {

    private val htmlService = HtmlService()
    private val tableBuilder = AttivitaTableHtmlBuilder()
    private val pdfEngine = Pdf()

    private fun createPlant(
        cod: String,
        activityCount: Int,
        frequenza: Periodo = Periodo(TipoPeriodo.M, 6)
    ): ImpiantoStandard {
        val activities = (1..activityCount).map { i ->
            Attivita(
                nAttivita = i,
                tipoAttivita = "Controllo",
                descrizione = "Attività di manutenzione #$i per impianto $cod",
                frequenza = frequenza,
                zona = "Zona $i"
            )
        }
        return ImpiantoStandard(
            id = "plant-$cod",
            codIntervento = cod,
            nomeCompleto = "Impianto $cod di Collaudo",
            premessa = "Premessa per $cod",
            listaAttivita = activities
        )
    }

    // =========================================================================
    // CHALLENGE 1: AcroForm Radio Button Group Naming Uniqueness & Independence
    // =========================================================================

    @Test
    fun `single plant generates strictly unique AcroForm names per row for primary and VN radio groups`() {
        val plant = createPlant("GE", 5)
        val html = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = plant,
                attivita = plant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = "Cliente Test"
            )
        )

        val tempHtml = File.createTempFile("chal_acro_single_", ".html")
        val tempPdf = File.createTempFile("chal_acro_single_", ".pdf")
        tempHtml.writeText(html)

        try {
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)

            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form, "AcroForm deve essere presente nel PDF")

                val allFields = form.allFormFields

                // Righe da 1 a 30: verificare esistenza e unicità dei gruppi radio
                for (row in 1..30) {
                    val primaryName = "esito_GE_$row"
                    val vnName = "esito_vn_GE_$row"
                    val noteName = "note_GE_$row"

                    assertTrue(allFields.containsKey(primaryName), "Gruppo primario $primaryName deve esistere")
                    assertTrue(allFields.containsKey(vnName), "Gruppo VN $vnName deve esistere")
                    assertTrue(allFields.containsKey(noteName), "Campo note $noteName deve esistere")

                    val primaryField = allFields[primaryName] as? PdfButtonFormField
                    assertNotNull(primaryField, "$primaryName deve essere un PdfButtonFormField")
                    assertTrue(primaryField.isRadio, "$primaryName deve essere di tipo radio")

                    val vnField = allFields[vnName] as? PdfButtonFormField
                    assertNotNull(vnField, "$vnName deve essere un PdfButtonFormField")
                    assertTrue(vnField.isRadio, "$vnName deve essere di tipo radio")

                    // Verifica indipendenza logica degli esiti
                    // Impostiamo l'esito primario a 'P' e il VN a 'VN'
                    primaryField.setValue("P")
                    vnField.setValue("VN")

                    assertEquals("P", primaryField.valueAsString, "Il campo primario deve valere P")
                    assertEquals("VN", vnField.valueAsString, "Il campo VN deve valere VN indipendentemente")

                    // Modifichiamo il primario in 'PI' e verifichiamo che VN rimanga invariato
                    primaryField.setValue("PI")
                    assertEquals("PI", primaryField.valueAsString)
                    assertEquals("VN", vnField.valueAsString, "La modifica di $primaryName non deve alterare $vnName")
                }

                // Per le righe filler (6..30) devono esistere anche i campi testo compilabili
                for (row in 6..30) {
                    assertTrue(allFields.containsKey("tipo_GE_$row"), "Filler row $row deve avere tipo_GE_$row")
                    assertTrue(allFields.containsKey("zona_GE_$row"), "Filler row $row deve avere zona_GE_$row")
                    assertTrue(allFields.containsKey("periodicita_GE_$row"), "Filler row $row deve avere periodicita_GE_$row")
                    assertTrue(allFields.containsKey("attivita_GE_$row"), "Filler row $row deve avere attivita_GE_$row")
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `multi plant batch generates disjoint AcroForm field name sets across different plant codes`() {
        val plantCodes = listOf("GE", "Q", "CAB", "RIG", "RI")
        val fieldSetsPerPlant = mutableMapOf<String, Set<String>>()

        for (cod in plantCodes) {
            val plant = createPlant(cod, 10)
            val html = htmlService.buildHtml(
                manutenzioni.domain.service.HtmlRenderContext(
                    impianto = plant,
                    attivita = plant.listaAttivita,
                    frequenza = Periodo(TipoPeriodo.M, 6),
                    clienteNome = "Multi Plant Client"
                )
            )

            val tempHtml = File.createTempFile("chal_multi_${cod}_", ".html")
            val tempPdf = File.createTempFile("chal_multi_${cod}_", ".pdf")
            tempHtml.writeText(html)

            try {
                pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
                PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                    val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                    assertNotNull(form)
                    fieldSetsPerPlant[cod] = form.allFormFields.keys.toSet()
                }
            } finally {
                tempHtml.delete()
                tempPdf.delete()
            }
        }

        // Verifica che i campi di tabella siano disgiunti tra tutti i codici impianto
        for (i in 0 until plantCodes.size) {
            for (j in (i + 1) until plantCodes.size) {
                val codA = plantCodes[i]
                val codB = plantCodes[j]
                val fieldsA = fieldSetsPerPlant[codA]!!.filter { it.contains(codA) }.toSet()
                val fieldsB = fieldSetsPerPlant[codB]!!.filter { it.contains(codB) }.toSet()

                val intersection = fieldsA.intersect(fieldsB)
                assertTrue(
                    intersection.isEmpty(),
                    "I campi AcroForm tra $codA e $codB devono essere completamente disgiunti. Trovata intersezione: $intersection"
                )
            }
        }
    }

    @Test
    fun `QuadroBT cantiere aggregation builds sequential distinct AcroForm names without collisions`() {
        val q1 = QuadroBT(
            id = "q-1",
            codIntervento = "Q",
            sigla = "QG",
            nomeCompleto = "Quadro Generale",
            listaInterruttori = listOf(
                InterruttoreBT(id = "i1", nome = "Diff 1", quantita = 2, siglaCircuito = "QF1"),
                InterruttoreBT(id = "i2", nome = "Diff 2", quantita = 1, siglaCircuito = "QF2")
            )
        )
        val q2 = QuadroBT(
            id = "q-2",
            codIntervento = "Q",
            sigla = "Q1",
            nomeCompleto = "Quadro Reparto",
            listaInterruttori = listOf(
                InterruttoreBT(id = "i3", nome = "Diff 3", quantita = 2, siglaCircuito = "QF3")
            )
        )

        val provider = QuadroBtAttachmentProvider()
        val sheetData = provider.buildCantiereSheetData(listOf(q1, q2), Periodo(TipoPeriodo.A, 1))
        assertNotNull(sheetData)

        // Totale righe: Q1(2+1=3) + Q2(2=2) = 5 righe
        assertEquals(5, sheetData.rows.size)

        // Tutte le righe devono avere radio name e misura text name strettamente univoci (1..5)
        val tastoNames = mutableSetOf<String>()
        val misuraNames = mutableSetOf<String>()

        sheetData.rows.forEachIndexed { idx, row ->
            val rowNum = idx + 1
            val tastoCell = row.cells.first { it is TechnicalCell.Radio && it.groupName.startsWith("tasto_diff_") } as TechnicalCell.Radio
            val misuraCell = row.cells.first { it is TechnicalCell.TextInput && it.fieldName.startsWith("misura_diff_") } as TechnicalCell.TextInput

            assertEquals("tasto_diff_Q_$rowNum", tastoCell.groupName)
            assertEquals("misura_diff_Q_$rowNum", misuraCell.fieldName)

            assertTrue(tastoNames.add(tastoCell.groupName), "Duplicate tasto radio name: ${tastoCell.groupName}")
            assertTrue(misuraNames.add(misuraCell.fieldName), "Duplicate misura text name: ${misuraCell.fieldName}")
        }
    }

    // =========================================================================
    // CHALLENGE 2: XML and HTML Character Escaping under Hostile / Adversarial Input
    // =========================================================================

    @Test
    fun `HtmlEscaper handles all dangerous XML entities and adversarial payloads`() {
        val dangerousVectors = listOf(
            "<script>alert('XSS')</script>" to "&lt;script&gt;alert(&#39;XSS&#39;)&lt;/script&gt;",
            """"quoted" & 'single' <tags>""" to """&quot;quoted&quot; &amp; &#39;single&#39; &lt;tags&gt;""",
            "AT&T & Sons S.r.l." to "AT&amp;T &amp; Sons S.r.l.",
            """<img src="x" onerror="alert(1)">""" to """&lt;img src=&quot;x&quot; onerror=&quot;alert(1)&quot;&gt;""",
            "<<<>>>&&&\"\"\"'''" to "&lt;&lt;&lt;&gt;&gt;&gt;&amp;&amp;&amp;&quot;&quot;&quot;&#39;&#39;&#39;"
        )

        for ((input, expected) in dangerousVectors) {
            assertEquals(expected, HtmlEscaper.escape(input), "Escaping fallito per input: $input")
        }
    }

    @Test
    fun `HtmlService buildHtml safely sanitizes adversarial metadata and produces parseable PDF`() {
        val hostileClient = """Alfa <script>alert("client")</script> & "Omega" S.p.A."""
        val hostileSite = """Stabilimento "Nord" <br> & 'Sud' <img src=x>"""
        val hostileTech = """Ing. O'Connor & "Partner" <script>"""
        val hostilePlant = ImpiantoStandard(
            id = "hostile-1",
            codIntervento = "H&S",
            nomeCompleto = """Impianto "Critico" <Danger> & Alert""",
            premessa = """Premessa con <tags> & "virgolette" 'apici'""",
            noteSpecifiche = """Note cantiere <script> & "test"""",
            listaAttivita = listOf(
                Attivita(
                    nAttivita = 1,
                    tipoAttivita = """<Check> & "Verify"""",
                    descrizione = """Controllo <p>pressione</p> & temperatura "alta" 'max'""",
                    frequenza = Periodo(TipoPeriodo.M, 6),
                    zona = """Locale <Caldaia> & "Vapore""""
                )
            )
        )

        val renderedHtml = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = hostilePlant,
                attivita = hostilePlant.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = hostileClient,
                cantiereNome = hostileSite,
                nomeManutentore = hostileTech,
                dataInizioContratto = "01/01/2026",
                dataIntervento = "15/10/2026",
                dataInterventoPrecedente = "15/04/2026"
            )
        )

        // 1. Verifica che i tag pericolosi NON siano presenti in forma non escapata
        assertFalse(renderedHtml.contains("<script>alert"), "Il tag <script> non deve comparire in chiaro")
        assertFalse(renderedHtml.contains("<img src=x>"), "Il tag <img src=x> non deve comparire in chiaro")
        assertFalse(renderedHtml.contains("<Danger>"), "Il testo <Danger> deve essere escapato")
        assertFalse(renderedHtml.contains("<tags>"), "Il testo <tags> deve essere escapato")
        assertFalse(renderedHtml.contains("<Check>"), "Il testo <Check> deve essere escapato")

        // 2. Verifica che le entità escapate siano presenti
        assertTrue(renderedHtml.contains("&lt;script&gt;alert"))
        assertTrue(renderedHtml.contains("&amp; &quot;Omega&quot;"))
        assertTrue(renderedHtml.contains("O&#39;Connor"))

        // 3. Test empirico fondamentale: iText7 html2pdf DEVE parsare l'HTML senza eccezioni XML
        val tempHtml = File.createTempFile("chal_hostile_", ".html")
        val tempPdf = File.createTempFile("chal_hostile_", ".pdf")
        tempHtml.writeText(renderedHtml)

        try {
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            assertTrue(tempPdf.exists() && tempPdf.length() > 0, "Il PDF con metadati ostili deve essere generato con successo")

            // Verifica che il PDF sia valido e leggibile da iText7
            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                assertEquals(1, pdfDoc.numberOfPages)
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form)
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    // =========================================================================
    // CHALLENGE 3: 30-Row Fixed Grid Padding & Overflow Verification
    // =========================================================================

    @Test
    fun `30-row padding with exactly 5 activities produces exactly 5 real rows and 25 filler rows`() {
        val plant = createPlant("GE", 5)
        val rowsHtml = tableBuilder.buildAttivitaRows(plant.listaAttivita, plant)

        val dataRows = Regex("""<tr class="row--data">""").findAll(rowsHtml).count()
        assertEquals(30, dataRows, "La griglia deve contenere esattamente 30 righe totali")

        // Righe 1..5 sono reali (hanno tag <p> e testo attività)
        for (i in 1..5) {
            assertTrue(rowsHtml.contains("<p>$i</p>"), "Riga reale $i deve avere il numero progressivo in <p>")
            assertTrue(rowsHtml.contains("Attività di manutenzione #$i"), "Riga reale $i deve contenere il testo dell'attività")
            assertFalse(rowsHtml.contains("\"tipo_GE_$i\""), "Riga reale $i NON deve avere campo input tipo_GE_$i")
            assertFalse(rowsHtml.contains("\"attivita_GE_$i\""), "Riga reale $i NON deve avere campo input attivita_GE_$i")
        }

        // Righe 6..30 sono filler (hanno campi input interattivi)
        for (i in 6..30) {
            assertTrue(rowsHtml.contains("<p>$i</p>"), "Riga filler $i deve avere il numero progressivo in <p>")
            assertTrue(rowsHtml.contains("\"tipo_GE_$i\""), "Riga filler $i deve avere input tipo_GE_$i")
            assertTrue(rowsHtml.contains("\"zona_GE_$i\""), "Riga filler $i deve avere input zona_GE_$i")
            assertTrue(rowsHtml.contains("\"periodicita_GE_$i\""), "Riga filler $i deve avere input periodicita_GE_$i")
            assertTrue(rowsHtml.contains("\"attivita_GE_$i\""), "Riga filler $i deve avere input attivita_GE_$i")
            assertTrue(rowsHtml.contains("\"esito_GE_$i\""), "Riga filler $i deve avere radio group esito_GE_$i")
            assertTrue(rowsHtml.contains("\"esito_vn_GE_$i\""), "Riga filler $i deve avere radio group esito_vn_GE_$i")
            assertTrue(rowsHtml.contains("\"note_GE_$i\""), "Riga filler $i deve avere input note_GE_$i")
        }

        // Riga 31 non deve esistere
        assertFalse(rowsHtml.contains("<p>31</p>"))
    }

    @Test
    fun `30-row padding with exactly 30 activities produces 30 real rows and 0 filler rows`() {
        val plant = createPlant("GE", 30)
        val rowsHtml = tableBuilder.buildAttivitaRows(plant.listaAttivita, plant)

        val dataRows = Regex("""<tr class="row--data">""").findAll(rowsHtml).count()
        assertEquals(30, dataRows, "La griglia deve contenere esattamente 30 righe")

        // Tutte le 30 righe devono essere reali, 0 filler
        for (i in 1..30) {
            assertTrue(rowsHtml.contains("<p>$i</p>"), "Riga reale $i deve essere presente")
            assertTrue(rowsHtml.contains("Attività di manutenzione #$i"))
            assertFalse(rowsHtml.contains("tipo_GE_$i"), "Riga $i non deve essere un filler")
            assertFalse(rowsHtml.contains("attivita_GE_$i"), "Riga $i non deve essere un filler")
        }

        // Non devono comparire campi filler
        assertFalse(rowsHtml.contains("tipo_GE_"))
        assertFalse(rowsHtml.contains("attivita_GE_"))
    }

    @Test
    fun `plants with more than 30 activities render ALL activities without truncation`() {
        val plant35 = createPlant("GE", 35)
        val rowsHtml35 = tableBuilder.buildAttivitaRows(plant35.listaAttivita, plant35)

        val dataRows35 = Regex("""<tr class="row--data">""").findAll(rowsHtml35).count()
        assertEquals(35, dataRows35, "Tutte le 35 attività devono essere renderizzate senza troncamento")

        // Verifica che l'attività 35 esista con i suoi radio button e note
        assertTrue(rowsHtml35.contains("<p>35</p>"))
        assertTrue(rowsHtml35.contains("Attività di manutenzione #35"))
        assertTrue(rowsHtml35.contains("esito_GE_35"))
        assertTrue(rowsHtml35.contains("esito_vn_GE_35"))
        assertTrue(rowsHtml35.contains("note_GE_35"))

        // Nessun filler presente
        assertFalse(rowsHtml35.contains("tipo_GE_"))

        // End-to-end PDF generation con 35 attività (verifica che tutti i campi siano renderizzati)
        val fullHtml = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = plant35,
                attivita = plant35.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = "Collaudo 35 Righe"
            )
        )

        val tempHtml = File.createTempFile("chal_35rows_", ".html")
        val tempPdf = File.createTempFile("chal_35rows_", ".pdf")
        tempHtml.writeText(fullHtml)

        try {
            pdfEngine.buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            assertTrue(tempPdf.exists() && tempPdf.length() > 0)

            PdfDocument(PdfReader(tempPdf)).use { pdfDoc ->
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form)
                assertTrue(form.allFormFields.containsKey("esito_GE_35"), "Il campo esito_GE_35 deve essere presente")
                assertTrue(form.allFormFields.containsKey("esito_vn_GE_35"), "Il campo esito_vn_GE_35 deve essere presente")
                assertTrue(form.allFormFields.containsKey("note_GE_35"), "Il campo note_GE_35 deve essere presente")
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }

        // Stress test estremo: 50 attività (deve andare sicuramente a pagina 2 senza perdere campi)
        val plant50 = createPlant("GE", 50)
        val fullHtml50 = htmlService.buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = plant50,
                attivita = plant50.listaAttivita,
                frequenza = Periodo(TipoPeriodo.M, 6),
                clienteNome = "Collaudo 50 Righe"
            )
        )
        val tempHtml50 = File.createTempFile("chal_50rows_", ".html")
        val tempPdf50 = File.createTempFile("chal_50rows_", ".pdf")
        tempHtml50.writeText(fullHtml50)

        try {
            pdfEngine.buildPdf(tempHtml50.absolutePath, tempPdf50.absolutePath)
            assertTrue(tempPdf50.exists() && tempPdf50.length() > 0)

            PdfDocument(PdfReader(tempPdf50)).use { pdfDoc ->
                assertTrue(pdfDoc.numberOfPages >= 2, "Con 50 righe il PDF deve andare a pagina 2 senza crash, trovate: ${pdfDoc.numberOfPages}")
                val form = PdfAcroForm.getAcroForm(pdfDoc, false)
                assertNotNull(form)
                assertTrue(form.allFormFields.containsKey("esito_GE_50"), "Il campo esito_GE_50 a pagina 2 deve essere presente")
                assertTrue(form.allFormFields.containsKey("esito_vn_GE_50"), "Il campo esito_vn_GE_50 a pagina 2 deve essere presente")
                assertTrue(form.allFormFields.containsKey("note_GE_50"), "Il campo note_GE_50 a pagina 2 deve essere presente")
            }
        } finally {
            tempHtml50.delete()
            tempPdf50.delete()
        }
    }

    @Test
    fun `plants with 0 activities produce exactly 30 filler rows with complete AcroForm inputs`() {
        val emptyPlant = createPlant("GE", 0)
        val rowsHtml = tableBuilder.buildAttivitaRows(emptyPlant.listaAttivita, emptyPlant)

        val dataRows = Regex("""<tr class="row--data">""").findAll(rowsHtml).count()
        assertEquals(30, dataRows, "Con 0 attività devono essere generate esattamente 30 righe filler")

        // Dalla riga 1 alla 30 tutti sono filler
        for (i in 1..30) {
            assertTrue(rowsHtml.contains("<p>$i</p>"))
            assertTrue(rowsHtml.contains("tipo_GE_$i"))
            assertTrue(rowsHtml.contains("zona_GE_$i"))
            assertTrue(rowsHtml.contains("periodicita_GE_$i"))
            assertTrue(rowsHtml.contains("attivita_GE_$i"))
            assertTrue(rowsHtml.contains("esito_GE_$i"))
            assertTrue(rowsHtml.contains("esito_vn_GE_$i"))
            assertTrue(rowsHtml.contains("note_GE_$i"))
        }
    }

    // =========================================================================
    // CHALLENGE 4: Multithreaded Concurrency Stress on Template & Facade
    // =========================================================================

    @Test
    fun `concurrent invocations of buildHtml across multiple threads maintain deterministic outputs`() = runBlocking(Dispatchers.Default) {
        val plants = (1..10).map { idx ->
            createPlant("P$idx", 5 + idx)
        }

        val tasks = (1..40).map { threadIdx ->
            async {
                val plant = plants[threadIdx % plants.size]
                val html = htmlService.buildHtml(
                    manutenzioni.domain.service.HtmlRenderContext(
                        impianto = plant,
                        attivita = plant.listaAttivita,
                        frequenza = Periodo(TipoPeriodo.M, 6),
                        clienteNome = "Concurrent Client #$threadIdx",
                        cantiereNome = "Site #$threadIdx",
                        nomeManutentore = "Tech #$threadIdx"
                    )
                )
                // Asserzioni thread-local
                assertTrue(html.contains("Concurrent Client #$threadIdx"))
                assertTrue(html.contains("Site #$threadIdx"))
                assertTrue(html.contains("esito_${plant.codIntervento}_1"))
                assertTrue(html.contains("esito_vn_${plant.codIntervento}_1"))
                val rowCount = Regex("""<tr class="row--data">""").findAll(html).count()
                val expectedRows = maxOf(30, plant.listaAttivita.size)
                assertEquals(expectedRows, rowCount)
                html.length
            }
        }

        val results = tasks.awaitAll()
        assertEquals(40, results.size)
        results.forEach { length ->
            assertTrue(length > 5000, "Ogni HTML generato deve essere completo e consistente")
        }
    }
}
