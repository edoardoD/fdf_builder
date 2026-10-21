package manutenzioni.app.service

import com.itextpdf.forms.PdfAcroForm
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Cantiere
import manutenzioni.domain.model.Cliente
import manutenzioni.domain.model.ImpiantoEmergenza
import manutenzioni.domain.model.ImpiantoStandard
import manutenzioni.domain.model.LampadaEmergenza
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.QuadroBT
import manutenzioni.domain.model.QuadroMQT
import manutenzioni.domain.model.TipoPeriodo
import manutenzioni.domain.service.CalendarioScadenzeCalculator
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Test per Branch 9: `feat/calendario-scadenze-riepilogo` (Wave 4 — R27, R28).
 */
class Wave4CalendarioERiepilogoTest {

    private val cantiereBase = Cantiere(
        id = "cant-w4",
        nome = "Polo Logistico Nord",
        clienteId = "cli-w4",
        indirizzo = "Via dell'Industria 42, Milano",
        dataInizioContratto = "15/01/2026",
        dataIntervento = "15/01/2026",
        dataInterventoPrecedente = "15/01/2026",
        nomeManutentore = "Ing. Edoardo Desiderio"
    )

    private val impiantoGe = ImpiantoStandard(
        id = "imp-ge",
        codIntervento = "GE",
        nomeCompleto = "Gruppo Elettrogeno",
        listaAttivita = listOf(
            Attivita(1, "Controllo", "Avviamento mensile", Periodo(TipoPeriodo.M, 1)),
            Attivita(2, "Verifica", "Controllo semestrale", Periodo(TipoPeriodo.M, 6)),
            Attivita(3, "Manutenzione", "Tagliando annuale", Periodo(TipoPeriodo.A, 1))
        ),
        cantiereId = cantiereBase.id
    )

    private val impiantoQmt = QuadroMQT(
        id = "imp-qmt",
        codIntervento = "QMT",
        nomeCompleto = "Quadro Media Tensione",
        listaAttivita = listOf(
            Attivita(1, "Controllo", "Verifica annuale", Periodo(TipoPeriodo.A, 1)),
            Attivita(2, "Prova", "Prova quinquennale", Periodo(TipoPeriodo.A, 5))
        ),
        cantiereId = cantiereBase.id
    )

    @Test
    fun `CalendarioScadenzeCalculator calcola scadenze miste 1M 6M 1A 5A su orizzonte 60 mesi`() {
        val scadenze12 = CalendarioScadenzeCalculator.calcolaScadenze(
            cantiere = cantiereBase,
            impianti = listOf(impiantoGe, impiantoQmt),
            mesiOrizzonte = 12
        )

        // Con GE a 1M, ogni mese (1..12) ha almeno una scadenza
        assertEquals(12, scadenze12.size, "Su 12 mesi con impianto mensile devono esserci 12 gruppi mensili")

        // Mese +1 (Febbraio 2026): solo GE (1M -> 1 attività)
        val mese1 = scadenze12.first { it.indiceMese == 1 }
        assertEquals("Febbraio 2026", mese1.etichettaMese)
        assertEquals("15/02/2026", mese1.dataPrevista)
        assertEquals(1, mese1.scadenze.size)
        assertEquals(1, mese1.scadenze.first().numeroAttivita)

        // Mese +6 (Luglio 2026): GE con 1M + 6M -> 2 attività
        val mese6 = scadenze12.first { it.indiceMese == 6 }
        assertEquals("Luglio 2026", mese6.etichettaMese)
        val geMese6 = mese6.scadenze.first { it.codIntervento == "GE" }
        assertEquals(2, geMese6.numeroAttivita)

        // Mese +12 (Gennaio 2027): sia GE (3 attività) sia QMT (1 attività da 1A)
        val mese12 = scadenze12.first { it.indiceMese == 12 }
        assertEquals(2, mese12.scadenze.size)
        assertEquals(3, mese12.scadenze.first { it.codIntervento == "GE" }.numeroAttivita)
        assertEquals(1, mese12.scadenze.first { it.codIntervento == "QMT" }.numeroAttivita)

        // Su orizzonte 60 mesi (5 anni), al mese +60 QMT include sia 1A che 5A (2 attività)
        val scadenze60 = CalendarioScadenzeCalculator.calcolaScadenze(
            cantiere = cantiereBase,
            impianti = listOf(impiantoGe, impiantoQmt),
            mesiOrizzonte = 60
        )
        val mese60 = scadenze60.first { it.indiceMese == 60 }
        val qmtMese60 = mese60.scadenze.first { it.codIntervento == "QMT" }
        assertEquals(2, qmtMese60.numeroAttivita, "Al mese 60 QMT deve includere sia 1A che 5A")
    }

    @Test
    fun `RiepilogoInterventoHtmlRenderer e Pdf generano PDF Master Checklist compilabile (R28)`() {
        val cliente = Cliente(id = "cli-w4", nome = "Logistica Integrata S.p.A.")
        val quadro = QuadroBT(
            id = "imp-q1",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "QG",
            descrizioneQuadro = "Quadro Generale Magazzino",
            listaAttivita = listOf(Attivita(1, "Controllo", "Verifica generale", Periodo(TipoPeriodo.M, 6))),
            cantiereId = cantiereBase.id
        )
        val emergenza = ImpiantoEmergenza(
            id = "imp-em",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza",
            listaAttivita = listOf(Attivita(1, "Controllo", "Verifica lampade", Periodo(TipoPeriodo.M, 6))),
            listaLampade = listOf(LampadaEmergenza(sigla = "EM-01", modello = "Exiway", autonomia = "1h", posizione = "Uscita 1")),
            cantiereId = cantiereBase.id
        )

        val impianti = listOf(quadro, emergenza, impiantoGe)
        val renderer = RiepilogoInterventoHtmlRenderer()
        val html = renderer.buildRiepilogoHtml(
            cliente = cliente,
            cantiere = cantiereBase,
            impianti = impianti
        )

        assertContains(html, "FOGLIO DI RIEPILOGO GENERALE INTERVENTO")
        assertContains(html, "Logistica Integrata S.p.A.")
        assertContains(html, "Polo Logistico Nord")
        assertContains(html, "Ing. Edoardo Desiderio")
        assertContains(html, "Quadro Generale Magazzino (QG)")
        assertContains(html, "Sì (1 lampade)")

        val tempHtml = File.createTempFile("test_riepilogo_w4_", ".html")
        val tempPdf = File.createTempFile("test_riepilogo_w4_", ".pdf")
        tempHtml.writeText(html, Charsets.UTF_8)
        try {
            Pdf().buildPdf(tempHtml.absolutePath, tempPdf.absolutePath)
            assertTrue(tempPdf.exists() && tempPdf.length() > 0, "Il PDF di riepilogo deve essere generato")

            PdfReader(tempPdf).use { reader ->
                PdfDocument(reader).use { pdfDoc ->
                    assertTrue(pdfDoc.numberOfPages >= 1)
                    val acroForm = PdfAcroForm.getAcroForm(pdfDoc, false)
                    assertNotNull(acroForm, "Il PDF di riepilogo deve contenere campi AcroForm compilabili")
                    val fields = acroForm.allFormFields
                    assertTrue(fields.containsKey("riepilogo_starting_date"))
                    assertTrue(fields.containsKey("riepilogo_manutentore"))
                    assertTrue(fields.containsKey("riepilogo_esito_1"))
                    assertTrue(fields.containsKey("riepilogo_osservazioni_generali"))
                }
            }
        } finally {
            tempHtml.delete()
            tempPdf.delete()
        }
    }

    @Test
    fun `calcolaScadenzeGlobali aggrega multipli cantieri per mese (es Maggio 2026) con dettaglio attivita da fare`() {
        val cliente1 = Cliente(id = "cli-w4", nome = "Logistica Integrata S.p.A.")
        val cliente2 = Cliente(id = "cli-2", nome = "Ospedali Riuniti")

        // Cantiere 1 inizia il 15/01/2026 -> a Maggio 2026 (mese +4) ha GE (mensile)
        // Cantiere 2 inizia il 10/11/2025 -> a Maggio 2026 (mese +6) ha Quadro BT semestrale (6M)
        val cantiere2 = Cantiere(
            id = "cant-2",
            nome = "Villa Salus",
            clienteId = cliente2.id,
            dataInizioContratto = "10/11/2025",
            nomeManutentore = "Per. Ind. Rossi"
        )
        val quadroSemestraleC2 = QuadroBT(
            id = "imp-q-c2",
            codIntervento = "Q",
            nomeCompleto = "Quadri Elettrici BT",
            sigla = "Q-CHIR",
            descrizioneQuadro = "Quadro Reparto Chirurgia",
            listaAttivita = listOf(
                Attivita(1, "Prova Strumentale", "Prova scatto differenziali", Periodo(TipoPeriodo.M, 6), zona = "Interruttori")
            ),
            cantiereId = cantiere2.id
        )

        val globale = CalendarioScadenzeCalculator.calcolaScadenzeGlobali(
            clienti = listOf(cliente1, cliente2),
            cantieri = listOf(cantiereBase, cantiere2),
            tuttiImpianti = listOf(impiantoGe, impiantoQmt, quadroSemestraleC2),
            mesiOrizzonte = 12
        )

        val maggio2026 = globale.firstOrNull { it.annoMese == "2026-05" }
        assertNotNull(maggio2026, "Maggio 2026 deve essere presente nel calendario globale")
        assertEquals("Maggio 2026", maggio2026.etichettaMese)
        assertEquals(2, maggio2026.totaleCantieri, "A Maggio 2026 devono risultare entrambi i cantieri in scadenza")

        val scadenzaVillaSalus = maggio2026.cantieriInScadenza.first { it.cantiere.id == "cant-2" }
        assertEquals("Ospedali Riuniti", scadenzaVillaSalus.cliente?.nome)
        assertEquals("10/05/2026", scadenzaVillaSalus.dataPrevista)
        val impChir = scadenzaVillaSalus.scadenzeImpianti.first()
        assertEquals("Q - Q-CHIR", impChir.codIntervento)
        assertTrue(impChir.attivitaInScadenza.any { it.contains("Prova scatto differenziali") })
    }

    @Test
    fun `calcolaScadenze privilegia dataIntervento rispetto a dataInizioContratto`() {
        val cantiereTest = Cantiere(
            id = "cant-date-prio",
            nome = "Cantiere Test Priorita Data",
            clienteId = "cli-test",
            dataInizioContratto = "01/01/2026",
            dataIntervento = "15/10/2026"
        )
        val impiantoSemestrale = ImpiantoStandard(
            id = "imp-sem",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di Emergenza",
            listaAttivita = listOf(
                Attivita(1, "Verifica", "Controllo semestrale", Periodo(TipoPeriodo.M, 6))
            ),
            cantiereId = cantiereTest.id
        )

        val scadenze = CalendarioScadenzeCalculator.calcolaScadenze(
            cantiere = cantiereTest,
            impianti = listOf(impiantoSemestrale),
            mesiOrizzonte = 12
        )

        // Se prendesse dataInizioContratto ("01/01/2026"), la prima scadenza a 6 mesi cadrebbe a Luglio 2026 ("01/07/2026").
        // Poiché deve privilegiare dataIntervento ("15/10/2026"), la prima scadenza a 6 mesi deve cadere ad Aprile 2027 ("15/04/2027")!
        assertTrue(scadenze.none { it.annoMese == "2026-07" }, "Luglio 2026 non deve comparire perché la data intervento base è Ottobre 2026")

        val primaScadenza = scadenze.firstOrNull()
        assertNotNull(primaScadenza, "Deve esserci una prima scadenza a 6 mesi")
        assertEquals("2027-04", primaScadenza.annoMese, "La prima scadenza a 6 mesi deve essere Aprile 2027")
        assertEquals("15/04/2027", primaScadenza.dataPrevista)
        assertEquals(6, primaScadenza.indiceMese)
    }

    @Test
    fun `calcolaScadenze filtra sulle sole frequenze configurate per il cantiere`() {
        val cantiereTest = Cantiere(
            id = "cant-freq-filter",
            nome = "Cantiere Test Filtro Frequenze",
            clienteId = "cli-test",
            dataIntervento = "01/01/2026"
        )
        // Impianto ha attività teoriche mensili (1M), trimestrali (3M) e semestrali (6M)
        val impiantoMultiAttivita = ImpiantoStandard(
            id = "imp-multi",
            codIntervento = "Q",
            nomeCompleto = "Quadro Generale",
            listaAttivita = listOf(
                Attivita(1, "Controllo", "Controllo mensile", Periodo(TipoPeriodo.M, 1)),
                Attivita(2, "Verifica", "Verifica trimestrale", Periodo(TipoPeriodo.M, 3)),
                Attivita(3, "Manutenzione", "Prova semestrale", Periodo(TipoPeriodo.M, 6))
            ),
            cantiereId = cantiereTest.id
        )

        // Configuriamo per questo cantiere ESCLUSIVAMENTE la frequenza a 6 mesi
        val freqConfigurate = mapOf(
            impiantoMultiAttivita.id to setOf(Periodo(TipoPeriodo.M, 6))
        )

        val scadenze = CalendarioScadenzeCalculator.calcolaScadenze(
            cantiere = cantiereTest,
            impianti = listOf(impiantoMultiAttivita),
            mesiOrizzonte = 12,
            frequenzeConfiguratePerImpianto = freqConfigurate
        )

        // Non devono comparire scadenze nei mesi 1, 2, 3, 4, 5
        val mesiIntermedi = listOf("2026-02", "2026-03", "2026-04", "2026-05", "2026-06")
        for (mese in mesiIntermedi) {
            assertTrue(scadenze.none { it.annoMese == mese }, "Il mese $mese non deve avere scadenze poiché è attiva solo la frequenza 6M")
        }

        // Devono comparire solo mese +6 (Luglio 2026) e mese +12 (Gennaio 2027)
        assertEquals(2, scadenze.size, "Su 12 mesi con frequenza 6M devono esserci esattamente 2 gruppi di scadenza")
        assertEquals("2026-07", scadenze[0].annoMese)
        assertEquals("2027-01", scadenze[1].annoMese)
        assertEquals("01/07/2026", scadenze[0].dataPrevista)
        assertEquals("01/01/2027", scadenze[1].dataPrevista)
    }
}
