package manutenzioni.domain.model

import kotlinx.serialization.json.Json
import manutenzioni.app.service.HtmlService
import manutenzioni.domain.service.FrequencyFilter
import kotlin.test.*

class QuadroMqtSwitchTypeTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "type"
    }

    private val attivitaQmt21 = listOf(
        Attivita(1, "Controllo", "Controllo generale, installazione, integrità parti isolanti...", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(2, "Controllo", "Controllo ed esame a vista delle parti attive...", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(3, "Controllo", "Verifica funzionamento elettrico e tensione circuiti ausiliari", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(4, "Controllo", "Verifica intellegibilità e completezza targhe per sequenza manovre", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(5, "Controllo", "Verifica dello stato di integrità elettrico e meccanico...", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(6, "Verifica", "Controllo integrità calotta di protezione...", Periodo(TipoPeriodo.A, 1), zona = "TA-TO-TV"),
        Attivita(7, "Controllo", "Controllo integrità e collegamenti resistenza antiferrorisonanza...", Periodo(TipoPeriodo.A, 1), zona = "TV"),
        Attivita(8, "Verifica", "Verifica dello stato dei relè di protezione...", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(9, "Verifica", "Verifica funzionalità relè di protezione integrato...", Periodo(TipoPeriodo.A, 1), zona = "Q.M.T."),
        Attivita(10, "Controllo", "Esame a vista delle parti attive...", Periodo(TipoPeriodo.A, 1), zona = "SEZ."),
        Attivita(11, "Controllo", "Controllo generale e installazione...", Periodo(TipoPeriodo.A, 1), zona = "Interruttore"),
        Attivita(12, "Controllo", "Verifica funzionamento elettrico e tensioni ausiliarie", Periodo(TipoPeriodo.A, 1), zona = "Interruttore"),
        Attivita(13, "Verifica", "Controllo blocchi e interblocchi", Periodo(TipoPeriodo.A, 2), zona = "Q.M.T."),
        Attivita(14, "Controllo", "Pulizia e lubrificazione meccanismi di comando...", Periodo(TipoPeriodo.A, 2), zona = "Interruttore"),
        Attivita(15, "Controllo", "Controllo aperture per passaggio cavi e tamponature", Periodo(TipoPeriodo.A, 5), zona = "Q.M.T."),
        Attivita(16, "Controllo", "Controllo dei collegamenti di terra struttura metallica...", Periodo(TipoPeriodo.A, 5), zona = "Q.M.T."),
        Attivita(17, "Controllo", "Verifica funzionalità dei blocchi e interblocchi...", Periodo(TipoPeriodo.A, 5), zona = "SEZ."),
        Attivita(18, "Verifica", "Verifica funzionalità relè diretto da TA integrati...", Periodo(TipoPeriodo.A, 5), zona = "Interruttore"),
        Attivita(19, "Verifica", "Rilievo tempi di apertura e chiusura...", Periodo(TipoPeriodo.A, 5), zona = "Interruttore"),
        Attivita(20, "Controllo", "Esecuzione intervento di manutenzione straordinaria...", Periodo(TipoPeriodo.A, 5), zona = "Interruttore"),
        Attivita(21, "Controllo", "Verifica ed eventuale sostituzione dei componenti di usura...", Periodo(TipoPeriodo.A, 5), zona = "Interruttore")
    )

    @Test
    fun `QuadroMQT contiene 21 attivita con frequenze 1A, 2A, 5A`() {
        assertEquals(21, attivitaQmt21.size)
        
        val freq1A = attivitaQmt21.filter { it.frequenza == Periodo(TipoPeriodo.A, 1) }
        val freq2A = attivitaQmt21.filter { it.frequenza == Periodo(TipoPeriodo.A, 2) }
        val freq5A = attivitaQmt21.filter { it.frequenza == Periodo(TipoPeriodo.A, 5) }

        assertEquals(12, freq1A.size, "Attività a 1 Anno devono essere 12 (1..12)")
        assertEquals(2, freq2A.size, "Attività a 2 Anni devono essere 2 (13..14)")
        assertEquals(7, freq5A.size, "Attività a 5 Anni devono essere 7 (15..21)")
    }

    @Test
    fun `withTipoInterruttore aggiorna la colonna zona per le attivita interruttore`() {
        val qmt = QuadroMQT(
            codIntervento = "QMT",
            nomeCompleto = "Quadro Media Tensione",
            listaAttivita = attivitaQmt21,
            tipoInterruttore = null
        )

        // Switch a SF6
        val qmtSf6 = qmt.withTipoInterruttore(MqtSwitchType.SF6)
        assertEquals(MqtSwitchType.SF6, qmtSf6.tipoInterruttore)
        val interruttoreSf6 = qmtSf6.listaAttivita.filter { it.nAttivita in listOf(11, 12, 14, 18, 19, 20, 21) }
        assertTrue(interruttoreSf6.all { it.zona == "SF6" }, "Tutte le attività interruttore devono avere zona SF6")
        
        // Le attività di quadro e sezionatore restano inalterate
        assertEquals("Q.M.T.", qmtSf6.listaAttivita.first { it.nAttivita == 1 }.zona)
        assertEquals("SEZ.", qmtSf6.listaAttivita.first { it.nAttivita == 10 }.zona)
        assertEquals("TA-TO-TV", qmtSf6.listaAttivita.first { it.nAttivita == 6 }.zona)

        // Switch a ISV
        val qmtIsv = qmtSf6.withTipoInterruttore(MqtSwitchType.ISV)
        assertEquals(MqtSwitchType.ISV, qmtIsv.tipoInterruttore)
        val interruttoreIsv = qmtIsv.listaAttivita.filter { it.nAttivita in listOf(11, 12, 14, 18, 19, 20, 21) }
        assertTrue(interruttoreIsv.all { it.zona == "ISV" }, "Tutte le attività interruttore devono avere zona ISV")

        // Switch a IVOR
        val qmtIvor = qmtIsv.withTipoInterruttore(MqtSwitchType.IVOR)
        assertEquals(MqtSwitchType.IVOR, qmtIvor.tipoInterruttore)
        val interruttoreIvor = qmtIvor.listaAttivita.filter { it.nAttivita in listOf(11, 12, 14, 18, 19, 20, 21) }
        assertTrue(interruttoreIvor.all { it.zona == "IVOR" }, "Tutte le attività interruttore devono avere zona IVOR")
    }

    @Test
    fun `FrequencyFilter include correttamente le attivita QMT in base alla periodicita`() {
        val qmt = QuadroMQT(
            codIntervento = "QMT",
            nomeCompleto = "Quadro Media Tensione",
            listaAttivita = attivitaQmt21
        )

        // Intervento a 1 Anno (12 mesi): solo le 12 attività 1A (12 % 12 == 0)
        val filtrate1A = FrequencyFilter.filterByFrequenza(qmt.listaAttivita, Periodo(TipoPeriodo.A, 1))
        assertEquals(12, filtrate1A.size)
        assertTrue(filtrate1A.all { it.frequenza.inMesi() == 12 })

        // Intervento a 2 Anni (24 mesi): 1A e 2A (24 % 12 == 0 e 24 % 24 == 0)
        val filtrate2A = FrequencyFilter.filterByFrequenza(qmt.listaAttivita, Periodo(TipoPeriodo.A, 2))
        assertEquals(14, filtrate2A.size)

        // Intervento a 5 Anni (60 mesi): 1A e 5A (60 % 12 == 0 e 60 % 60 == 0)
        val filtrate5A = FrequencyFilter.filterByFrequenza(qmt.listaAttivita, Periodo(TipoPeriodo.A, 5))
        assertEquals(19, filtrate5A.size)
    }

    @Test
    fun `HtmlService include nel rendering HTML la keyword del tipo interruttore scelto`() {
        val htmlService = HtmlService()
        val qmt = QuadroMQT(
            id = "qmt-cantiere-1",
            codIntervento = "QMT",
            nomeCompleto = "Quadro Media Tensione Cantiere",
            listaAttivita = attivitaQmt21,
            tipoInterruttore = MqtSwitchType.ISV
        )

        val filtrate1A = FrequencyFilter.filterByFrequenza(qmt.listaAttivita, Periodo(TipoPeriodo.A, 1))
        val html = htmlService.buildHtml(
            impianto = qmt,
            attivitaFiltrate = filtrate1A,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Industrie Meccaniche Spa"
        )

        // Verifica che nel PDF HTML compaia la colonna Impianto/Zona con "ISV"
        assertContains(html, "<p>ISV</p>")
        assertContains(html, "<p>Q.M.T.</p>")
        assertContains(html, "<p>SEZ.</p>")
        assertContains(html, "<p>TA-TO-TV</p>")
    }

    @Test
    fun `normalizeType converte ImpiantoStandard QMT in QuadroMQT preservando i campi`() {
        val standard = ImpiantoStandard(
            id = "test-qmt-id",
            codIntervento = "QMT",
            nomeCompleto = "Quadro Media Tensione",
            listaAttivita = attivitaQmt21
        )

        val normalized = standard.normalizeType()
        assertTrue(normalized is QuadroMQT)
        assertEquals("test-qmt-id", normalized.id)
        assertEquals("QMT", normalized.codIntervento)
        assertEquals(21, normalized.listaAttivita.size)
    }

    @Test
    fun `Serializzazione e deserializzazione polimorfica di QuadroMQT`() {
        val qmt = QuadroMQT(
            id = "mqt-uuid-123",
            codIntervento = "QMT",
            nomeCompleto = "Quadro MT Cabina A",
            cantiereId = "cantiere-xyz",
            tipoInterruttore = MqtSwitchType.IVOR,
            listaAttivita = attivitaQmt21
        )

        val encoded = json.encodeToString(Impianto.serializer(), qmt)
        assertContains(encoded, "\"tipoInterruttore\": \"IVOR\"")
        assertContains(encoded, "\"type\": \"QuadroMQT\"")

        val decoded = json.decodeFromString(Impianto.serializer(), encoded)
        assertTrue(decoded is QuadroMQT)
        assertEquals(MqtSwitchType.IVOR, decoded.tipoInterruttore)
        assertEquals("cantiere-xyz", decoded.cantiereId)
        assertEquals(21, decoded.listaAttivita.size)
    }
}
