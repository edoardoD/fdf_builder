package manutenzioni.app.service.attachments

import manutenzioni.app.service.Pdf
import manutenzioni.domain.model.*
import manutenzioni.domain.service.attachments.TechnicalCell
import java.io.File
import kotlin.test.*

class AntincendioAttachmentProviderTest {

    private val provider = AntincendioAttachmentProvider()
    private val renderer = TechnicalSheetHtmlRenderer()

    private val comune1 = DispositivoRilevazione.ComponenteComune(
        tipo = TipoComponenteComuneAntincendio.CENTRALE_INCENDIO,
        indirizzo = "CENTR.1",
        installazione = "A PARETE",
        descrizione = "LOCALE PORTINERIA",
        zona = "PT",
        dataInstallazione = "10/2024"
    )

    private val rivelatoreL1_10 = DispositivoRilevazione.RivelatoreLoop(
        loop = 1,
        numeroIndirizzo = 10,
        tipo = TipoRivelatore.FUMO,
        installazione = "CONTROSOFFITTO",
        descrizione = "LOCALE QUADRO ELETTRICO",
        zona = "PINT",
        dataInstallazione = "10/2024"
    )

    private val rivelatoreL1_2 = DispositivoRilevazione.RivelatoreLoop(
        loop = 1,
        numeroIndirizzo = 2,
        tipo = TipoRivelatore.BARRIERA,
        installazione = "A VISTA",
        descrizione = "BARRIERA RECEPTION",
        zona = "PT",
        dataInstallazione = "10/2024"
    )

    private val moduloL1_1 = DispositivoRilevazione.ModuloLoop(
        loop = 1,
        numeroIndirizzo = 1,
        tipo = TipoModuloAntincendio.M701,
        installazione = "A VISTA",
        descrizione = "TARGHE PIANO TERRA",
        zona = "PT",
        dataInstallazione = "10/2024"
    )

    private val moduloL5_12 = DispositivoRilevazione.ModuloLoop(
        loop = 5,
        numeroIndirizzo = 12,
        tipo = TipoModuloAntincendio.CMA_11E_IN,
        installazione = "A VISTA",
        descrizione = "GUASTO ALIMENTATORE A1",
        zona = "ZONA 5",
        dataInstallazione = "05/2023"
    )

    @Test
    fun `canHandle riconosce RilevazioneAntincendio con dispositivi`() {
        val riVuoto = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = emptyList()
        )
        val riConDispositivi = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(rivelatoreL1_2)
        )
        val rig = RilevazioneGas(
            codIntervento = "RIG",
            nomeCompleto = "Rilevazione Gas"
        )

        assertFalse(provider.canHandle(riVuoto))
        assertTrue(provider.canHandle(riConDispositivi))
        assertFalse(provider.canHandle(rig))
    }

    @Test
    fun `buildSheetsData genera fogli distinti ordinati per Comuni e Loop 1_8 omettendo sezioni vuote`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(moduloL5_12, rivelatoreL1_10, comune1, moduloL1_1, rivelatoreL1_2)
        )

        val sheets = provider.buildSheetsData(ri, Periodo(TipoPeriodo.M, 6), "Cliente Test S.p.A.")
        assertEquals(4, sheets.size, "Deve generare 4 fogli: Comuni, Rivelatori L1, Moduli L1, Moduli L5")

        // Verifica ordine sezioni
        assertEquals("ril_comuni", sheets[0].campoAcroFormPrefix)
        assertTrue(sheets[0].titoloBanner.contains("COMPONENTI COMUNI"))

        assertEquals("ril_l1_riv", sheets[1].campoAcroFormPrefix)
        assertTrue(sheets[1].titoloBanner.contains("RIVELATORI LOOP 1"))

        assertEquals("ril_l1_mod", sheets[2].campoAcroFormPrefix)
        assertTrue(sheets[2].titoloBanner.contains("MODULI LOOP 1"))

        assertEquals("ril_l5_mod", sheets[3].campoAcroFormPrefix)
        assertTrue(sheets[3].titoloBanner.contains("MODULI LOOP 5"))

        // Verifica larghezza esattamente 18.4 cm su ogni foglio
        sheets.forEach { sheet ->
            val totalWidth = sheet.columns.sumOf { it.totalWidthCm }
            assertEquals(18.4, totalWidth, 0.001, "La larghezza della tabella deve essere 18.4 cm")
        }

        // Verifica ordinamento numerico in Rivelatori Loop 1 (prima ind. 2, poi ind. 10)
        val l1RivRows = sheets[1].rows
        assertEquals(2, l1RivRows.size)
        assertEquals("RIV.L1.2", (l1RivRows[0].cells[0] as TechnicalCell.Text).text)
        assertEquals("BARR.", (l1RivRows[0].cells[1] as TechnicalCell.Text).text)
        assertEquals("RIV.L1.10", (l1RivRows[1].cells[0] as TechnicalCell.Text).text)
        assertEquals("FUMO", (l1RivRows[1].cells[1] as TechnicalCell.Text).text)
    }

    @Test
    fun `buildSheetsData produce 6 colonne Esito aziendali (P, PI, NA, NP, VN, B) e nomi AcroForm univoci`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(comune1, rivelatoreL1_2, moduloL1_1)
        )

        val sheets = provider.buildSheetsData(ri, Periodo(TipoPeriodo.M, 6), "Cliente Test")
        val esitoCol = sheets[1].columns[6]
        assertEquals(listOf("P", "PI", "NA", "NP", "VN", "B"), esitoCol.subColumns.map { it.title })

        val radioComuni = (sheets[0].rows[0].cells[6] as TechnicalCell.Radio).groupName
        val radioL1Riv = (sheets[1].rows[0].cells[6] as TechnicalCell.Radio).groupName
        val radioL1Mod = (sheets[2].rows[0].cells[6] as TechnicalCell.Radio).groupName
        val radioL1RivB = (sheets[1].rows[0].cells[11] as TechnicalCell.Radio)
        assertEquals("B", radioL1RivB.optionValue)

        assertEquals("ril_comuni_esito_RI_1", radioComuni)
        assertEquals("ril_l1_riv_esito_RI_1", radioL1Riv)
        assertEquals("ril_l1_mod_esito_RI_1", radioL1Mod)
        assertEquals(3, setOf(radioComuni, radioL1Riv, radioL1Mod).size)
    }

    @Test
    fun `buildPremessaSummary restituisce null per non inquinare la premessa del Foglio 1`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(comune1, rivelatoreL1_2)
        )

        assertNull(provider.buildPremessaSummary(ri))
    }

    @Test
    fun `renderStandaloneHtml multi-foglio genera HTML e PDF AcroForm valido`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaDispositivi = listOf(comune1, rivelatoreL1_2, moduloL1_1, moduloL5_12)
        )

        val sheets = provider.buildSheetsData(ri, Periodo(TipoPeriodo.M, 6), "Acciaierie Italiane")
        val html = renderer.renderStandaloneHtml(sheets, Periodo(TipoPeriodo.M, 6), "Acciaierie Italiane")

        assertTrue(html.contains("COMPONENTI COMUNI"))
        assertTrue(html.contains("RIVELATORI LOOP 1"))
        assertTrue(html.contains("MODULI LOOP 1"))
        assertTrue(html.contains("MODULI LOOP 5"))
        assertTrue(html.contains("ril_comuni_esito_RI_1"))
        assertTrue(html.contains("ril_l1_riv_esito_RI_1"))
        assertTrue(html.contains("ril_l5_mod_esito_RI_1"))

        val tempHtml = File.createTempFile("test_ri_multi_", ".html")
        val tempPdf = File.createTempFile("test_ri_multi_", ".pdf")
        try {
            tempHtml.writeText(html, Charsets.UTF_8)
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            assertTrue(tempPdf.exists() && tempPdf.length() > 1000L, "PDF multi-pagina generato con successo")
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `HtmlService integra tutti i fogli multi-sezione di RilevazioneAntincendio dopo Foglio 1`() {
        val ri = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Verifica centrale", Periodo(TipoPeriodo.M, 6))
            ),
            listaDispositivi = listOf(comune1, rivelatoreL1_2, moduloL1_1, moduloL5_12)
        )

        val html = manutenzioni.app.service.HtmlService().buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = ri,
                frequenza = Periodo(TipoPeriodo.M, 6),
                attivita = ri.listaAttivita,
                clienteNome = "Cliente Batch Test"
            )
        )

        assertTrue(html.contains("ELENCO COMPONENTI COMUNI"))
        assertTrue(html.contains("ELENCO RIVELATORI LOOP 1"))
        assertTrue(html.contains("ELENCO MODULI LOOP 1"))
        assertTrue(html.contains("ELENCO MODULI LOOP 5"))
        assertFalse(html.contains("LOOP 2"))
        assertFalse(html.contains("LOOP 3"))
    }

    @Test
    fun `Libretto RI Periodica B - Incrocia anagrafica, Data inizio contratto, esclusivita Convenzionale_Indirizzato e Prospetto ASD opzionale`() {
        val riSenzaAsd = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Impianto rilevazione incendi",
            architettura = ArchitetturaImpiantoRi.CONVENZIONALE,
            hasAsd = false,
            hasRadio = true,
            hasEvac = true,
            tipoVerbale = TipoVerbaleRi.VERIFICA_PERIODICA_B,
            estensioneProtezione = EstensioneProtezioneRi.PARZIALE,
            localiProtetti = "Magazzino Centrale e Uffici P1",
            tipoEsecuzione = TipoEsecuzioneRi.ATEX,
            responsabileSistema = "Ing. Mario Rossi",
            listaDispositivi = listOf(comune1, rivelatoreL1_2)
        )

        val htmlSenzaAsd = manutenzioni.app.service.HtmlService().buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = riSenzaAsd,
                frequenza = Periodo(TipoPeriodo.M, 6),
                attivita = riSenzaAsd.listaAttivita,
                clienteNome = "Industrie Meccaniche S.p.A.",
                cantiereNome = "Stabilimento Forlì",
                nomeManutentore = "Edoardo Desiderio",
                dataInizioContratto = "01/03/2026",
                dataInterventoPrecedente = "01/05/2026",
                dataIntervento = "01/11/2026"
            )
        )

        assertTrue(htmlSenzaAsd.contains("VERBALE VERIFICA PERIODICA E CHECKLIST DOCUMENTALE"))
        assertTrue(htmlSenzaAsd.contains("B1-ANAGRAFICA E IDENTIFICAZIONE DEL SISTEMA"))
        assertTrue(htmlSenzaAsd.contains("B2-Lista di Riscontro per Verifica sul Sistema"))
        assertTrue(htmlSenzaAsd.contains("Data inizio contratto:"))
        assertFalse(htmlSenzaAsd.contains("Data inizio periodo"))
        assertTrue(htmlSenzaAsd.contains("01/03/2026"))
        assertTrue(htmlSenzaAsd.contains("Ing. Mario Rossi"))
        assertTrue(htmlSenzaAsd.contains("[X] Convenzionale"))
        assertTrue(htmlSenzaAsd.contains("[  ] Indirizzato"))
        assertTrue(htmlSenzaAsd.contains("[  ] ASD (Aspirazione)"))
        assertTrue(htmlSenzaAsd.contains("[X] Radio"))
        assertTrue(htmlSenzaAsd.contains("[X] EVAC"))
        assertTrue(htmlSenzaAsd.contains("[X] Parziale (Locali protetti: Magazzino Centrale e Uffici P1 )"))
        assertTrue(htmlSenzaAsd.contains("[X] Luogo a Rischio Esplosione (ATEX)"))
        assertTrue(htmlSenzaAsd.contains("Allegato Elenco Componenti (2 disp.)"))
        assertTrue(htmlSenzaAsd.contains("A campione scollegando un morsetto"))
        assertTrue(htmlSenzaAsd.contains("Non barrare esito se presente lista di controllo"))
        assertFalse(htmlSenzaAsd.contains("Prospetto B.3"), "Con hasAsd=false il Prospetto B.3 non deve essere presente")
        assertTrue(htmlSenzaAsd.contains("2. ESITO DELLE VERIFICHE SU IMPIANTO RILEVAZIONE INCENDI (UNI 11224:2026)"))
        assertTrue(htmlSenzaAsd.contains("REGISTRO DETTAGLIATO DELLE NON CONFORMITÀ"))
        assertTrue(htmlSenzaAsd.contains("3. PROSPETTO SINTETICO SCADENZE E PERCENTUALI DI CONTROLLO PERIODICO"))
        assertTrue(htmlSenzaAsd.contains("4. CLAUSOLE GENERALI DI MANUTENZIONE E RESPONSABILITÀ"))
        assertTrue(htmlSenzaAsd.contains("5. SOTTOSCRIZIONE FORMALE E CONFERMA DI RICEVUTA"))

        // Attivando hasAsd = true -> deve includere Prospetto B.3 con Classe Sensibilità e Tempo Max Trasporto
        val riConAsd = riSenzaAsd.copy(
            architettura = ArchitetturaImpiantoRi.INDIRIZZATO,
            hasAsd = true,
            classeSensibilitaAsd = ClasseSensibilitaAsd.CLASSE_B,
            tempoMaxTrasportoAsd = TempoTrasportoAsd.MAX_60S
        )
        val htmlConAsd = manutenzioni.app.service.HtmlService().buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = riConAsd,
                frequenza = Periodo(TipoPeriodo.M, 6),
                attivita = riConAsd.listaAttivita,
                clienteNome = "Industrie Meccaniche S.p.A.",
                cantiereNome = "Stabilimento Forlì"
            )
        )
        assertTrue(htmlConAsd.contains("[  ] Convenzionale"))
        assertTrue(htmlConAsd.contains("[X] Indirizzato"))
        assertTrue(htmlConAsd.contains("[X] ASD (Aspirazione)"))
        assertTrue(htmlConAsd.contains("Prospetto B.3 — Lista di Riscontro per Controlli Addizionali su Sistemi ASD (Aspirazione)"))
        assertTrue(htmlConAsd.contains("[X] Classe B"))
        assertTrue(htmlConAsd.contains("[X] ≤ 60 s"))
    }

    @Test
    fun `Libretto RI Iniziale A - Genera A1, A2, A3, Prospetto A4 ASD, Fogli Loop e converte in PDF AcroForm`() {
        val riIniziale = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Impianto rilevazione incendi",
            architettura = ArchitetturaImpiantoRi.INDIRIZZATO,
            hasAsd = true,
            classeSensibilitaAsd = ClasseSensibilitaAsd.CLASSE_A,
            tempoMaxTrasportoAsd = TempoTrasportoAsd.MAX_90S,
            tipoVerbale = TipoVerbaleRi.VERIFICA_INIZIALE_A,
            listaDispositivi = listOf(comune1, rivelatoreL1_2, moduloL1_1)
        )

        val html = manutenzioni.app.service.HtmlService().buildHtml(
            manutenzioni.domain.service.HtmlRenderContext(
                impianto = riIniziale,
                frequenza = Periodo(TipoPeriodo.M, 6),
                attivita = manutenzioni.domain.service.rules.Uni11224RiCatalog.LISTA_A3_CONTROLLI_SISTEMA,
                clienteNome = "Logistica Adriatica S.r.l.",
                cantiereNome = "Hub Cesena",
                dataInizioContratto = "15/11/2026",
                dataIntervento = "20/11/2026",
                nomeManutentore = "Edoardo Desiderio"
            )
        )

        assertTrue(html.contains("VERBALE VERIFICA INIZIALE E CHECKLIST DOCUMENTALE"))
        assertTrue(html.contains("A1-ANAGRAFICA E IDENTIFICAZIONE DEL SISTEMA"))
        assertTrue(html.contains("A2-Lista di Riscontro per Verifiche Preliminari sul Sistema"))
        assertTrue(html.contains("A3-Lista di Riscontro per Controlli sul Sistema e Dispositivi"))
        assertTrue(html.contains("Prospetto A.4 — Lista di Riscontro per Controlli Addizionali su Sistemi ASD (Aspirazione)"))
        assertTrue(html.contains("ELENCO COMPONENTI COMUNI"))
        assertTrue(html.contains("ELENCO RIVELATORI LOOP 1"))
        assertTrue(html.contains("ELENCO MODULI LOOP 1"))

        val tempHtml = File.createTempFile("test_libretto_ri_a_", ".html")
        val tempPdf = File.createTempFile("test_libretto_ri_a_", ".pdf")
        try {
            tempHtml.writeText(html, Charsets.UTF_8)
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            assertTrue(tempPdf.exists() && tempPdf.length() > 5000L, "Il Libretto PDF RI completo deve essere generato")
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }
}
