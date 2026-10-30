package manutenzioni.domain.service

import manutenzioni.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FrequenzaCompatibilitaResolverTest {

    private val quadroBt = QuadroBT(
        id = "q-1",
        codIntervento = "Q",
        nomeCompleto = "Quadro Generale BT",
        listaAttivita = listOf(
            Attivita(
                nAttivita = 1,
                tipoAttivita = "Controllo",
                descrizione = "Controllo semestrale",
                frequenza = Periodo(TipoPeriodo.M, 6)
            ),
            Attivita(
                nAttivita = 2,
                tipoAttivita = "Verifica",
                descrizione = "Verifica annuale differenziali",
                frequenza = Periodo(TipoPeriodo.A, 1)
            )
        )
    )

    private val emergenza = ImpiantoEmergenza(
        id = "em-1",
        codIntervento = "EM",
        nomeCompleto = "Illuminazione di Emergenza (UNI 11222)",
        listaAttivita = listOf(
            Attivita(
                nAttivita = 1,
                tipoAttivita = "Verifica",
                descrizione = "Verifica autonomia semestrale",
                frequenza = Periodo(TipoPeriodo.M, 6)
            )
        )
    )

    private val context = listOf(quadroBt, emergenza)

    @Test
    fun `rileva discrepanza quando la frequenza scelta supera la frequenza massima di emergenza`() {
        val freqScelta = Periodo(TipoPeriodo.A, 1) // 12 Mesi

        val report = FrequenzaCompatibilitaResolver.analizzaDiscrepanze(
            frequenzaScelta = freqScelta,
            impiantiSelezionati = listOf(quadroBt, emergenza),
            contextImpianti = context
        )

        assertTrue(report.hasDiscrepanze)
        assertEquals(1, report.discrepanze.size)

        val discr = report.discrepanze.first()
        assertEquals("em-1", discr.impiantoId)
        assertEquals("EM", discr.codIntervento)
        assertEquals(6, discr.frequenzaMassima.inMesi())

        assertEquals(6, report.frequenzaMinimaTraImpianti.inMesi())
        assertEquals(Periodo(TipoPeriodo.M, 6), report.frequenzeConsigliatePerImpianto["em-1"])
        assertEquals(Periodo(TipoPeriodo.A, 1), report.frequenzeConsigliatePerImpianto["q-1"])
    }

    @Test
    fun `nessuna discrepanza se la frequenza scelta e compatibile con tutti gli impianti`() {
        val freqScelta = Periodo(TipoPeriodo.M, 6) // 6 Mesi

        val report = FrequenzaCompatibilitaResolver.analizzaDiscrepanze(
            frequenzaScelta = freqScelta,
            impiantiSelezionati = listOf(quadroBt, emergenza),
            contextImpianti = context
        )

        assertFalse(report.hasDiscrepanze)
        assertTrue(report.discrepanze.isEmpty())
        assertEquals(6, report.frequenzaMinimaTraImpianti.inMesi())
        assertEquals(Periodo(TipoPeriodo.M, 6), report.frequenzeConsigliatePerImpianto["em-1"])
        assertEquals(Periodo(TipoPeriodo.M, 6), report.frequenzeConsigliatePerImpianto["q-1"])
    }

    @Test
    fun `getFrequenzaMassima calcola correttamente il bound massimo di ciascun impianto`() {
        val maxQuadro = FrequenzaCompatibilitaResolver.getFrequenzaMassima(quadroBt, context)
        val maxEmergenza = FrequenzaCompatibilitaResolver.getFrequenzaMassima(emergenza, context)

        assertEquals(12, maxQuadro.inMesi())
        assertEquals(6, maxEmergenza.inMesi())
    }
}
