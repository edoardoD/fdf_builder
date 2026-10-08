package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.ImpiantoStandard
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AntincendioAttivitaResolverTest {

    private val attivitaBase = Attivita(
        nAttivita = 1,
        tipoAttivita = "Prova fumo",
        descrizione = "Prova Rivelatori Fumo",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = null
    )

    private val attivitaGe = Attivita(
        nAttivita = 2,
        tipoAttivita = "Prova SGANCIO",
        descrizione = "Sgancio GE",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = "GE"
    )

    private val attivitaPem = Attivita(
        nAttivita = 3,
        tipoAttivita = "Prova PEM",
        descrizione = "Sgancio PEM",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = "PEM"
    )
    
    private val attivitaGas = Attivita(
        nAttivita = 4,
        tipoAttivita = "Prova GAS",
        descrizione = "Prova GAS",
        frequenza = Periodo(TipoPeriodo.A, 1),
        targetImpiantoCod = "RIG"
    )

    private val impiantoRi = ImpiantoStandard(
        codIntervento = "RI",
        nomeCompleto = "Rilevazione Incendi",
        listaAttivita = listOf(attivitaBase, attivitaGe, attivitaPem, attivitaGas)
    )

    @Test
    fun `resolveAttivita con cantiere vuoto mostra solo base per 6 mesi`() {
        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRi,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        
        assertEquals(1, filtrate.size)
        assertEquals("Prova fumo", filtrate.first().tipoAttivita)
    }

    @Test
    fun `resolveAttivita con cantiere che ha GE mostra base e GE per 6 mesi`() {
        val ge = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo")
        
        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRi,
            impiantiNelCantiere = listOf(impiantoRi, ge),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        
        assertEquals(2, filtrate.size)
        assertTrue(filtrate.any { it.targetImpiantoCod == null })
        assertTrue(filtrate.any { it.targetImpiantoCod == "GE" })
    }

    @Test
    fun `resolveAttivita per 1 anno include base, GE e GAS se RIG e GE sono presenti`() {
        val ge = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo")
        val rig = ImpiantoStandard(codIntervento = "RIG", nomeCompleto = "Rilevazione Gas")
        
        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRi,
            impiantiNelCantiere = listOf(impiantoRi, ge, rig),
            frequenza = Periodo(TipoPeriodo.A, 1) // 12 mesi include 6 mesi e 12 mesi
        )
        
        assertEquals(3, filtrate.size)
        assertTrue(filtrate.any { it.targetImpiantoCod == null }) // Base (6m)
        assertTrue(filtrate.any { it.targetImpiantoCod == "GE" }) // GE (6m)
        assertTrue(filtrate.any { it.targetImpiantoCod == "RIG" }) // GAS (1a)
    }

    // --- TEST PER RILEVAZIONE GAS (RIG) ---

    private val attivitaGasBase = Attivita(
        nAttivita = 1,
        tipoAttivita = "Controllo",
        descrizione = "Verifica eventi centrale gas",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = null
    )

    private val attivitaGasGe = Attivita(
        nAttivita = 2,
        tipoAttivita = "Rivelatori GAS GE",
        descrizione = "Prova Rivelatori Gas Locale GE",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = "GE"
    )

    private val attivitaGasPem = Attivita(
        nAttivita = 3,
        tipoAttivita = "Sgancio Elettrovalvola",
        descrizione = "Verifica intervento elettrovalvola gas su PEM",
        frequenza = Periodo(TipoPeriodo.M, 6),
        targetImpiantoCod = "PEM"
    )

    private val attivitaGasQ = Attivita(
        nAttivita = 4,
        tipoAttivita = "Sgancio Quadro",
        descrizione = "Sgancio alimentazione quadro locale gas",
        frequenza = Periodo(TipoPeriodo.A, 1),
        targetImpiantoCod = "Q"
    )

    private val impiantoRig = manutenzioni.domain.model.RilevazioneGas(
        codIntervento = "RIG",
        nomeCompleto = "Rilevazione Gas",
        listaAttivita = listOf(attivitaGasBase, attivitaGasGe, attivitaGasPem, attivitaGasQ)
    )

    @Test
    fun `resolveAttivita con RIG e cantiere vuoto mostra solo base per 6 mesi`() {
        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRig,
            impiantiNelCantiere = emptyList(),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        assertEquals(1, filtrate.size)
        assertEquals("Controllo", filtrate.first().tipoAttivita)
        assertEquals(null, filtrate.first().targetImpiantoCod)
    }

    @Test
    fun `resolveAttivita con RIG e cantiere che include GE e PEM mostra base, GE e PEM per 6 mesi`() {
        val ge = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo")
        val pem = ImpiantoStandard(codIntervento = "PEM", nomeCompleto = "Pulsante Sgancio")

        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRig,
            impiantiNelCantiere = listOf(impiantoRig, ge, pem),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        assertEquals(3, filtrate.size)
        assertTrue(filtrate.any { it.targetImpiantoCod == null })
        assertTrue(filtrate.any { it.targetImpiantoCod == "GE" })
        assertTrue(filtrate.any { it.targetImpiantoCod == "PEM" })
        assertTrue(filtrate.none { it.targetImpiantoCod == "Q" })
    }

    @Test
    fun `resolveAttivita per RIG a 1 anno include attivita con target Q se Quadro e presente`() {
        val quadro = manutenzioni.domain.model.QuadroBT(codIntervento = "Q", nomeCompleto = "Quadro BT")

        val filtrate = AntincendioAttivitaResolver.resolveAttivita(
            impianto = impiantoRig,
            impiantiNelCantiere = listOf(impiantoRig, quadro),
            frequenza = Periodo(TipoPeriodo.A, 1)
        )
        assertEquals(2, filtrate.size)
        assertTrue(filtrate.any { it.targetImpiantoCod == null })
        assertTrue(filtrate.any { it.targetImpiantoCod == "Q" })
    }

    @Test
    fun `resolveFrequenze per RIG esclude frequenze di impianti target non presenti nel cantiere`() {
        val soloGe = ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo")
        val freq = AntincendioAttivitaResolver.resolveFrequenze(
            impianto = impiantoRig,
            impiantiNelCantiere = listOf(impiantoRig, soloGe)
        )
        assertEquals(1, freq.size)
        assertEquals(Periodo(TipoPeriodo.M, 6), freq.first())

        val conQuadro = listOf(impiantoRig, soloGe, manutenzioni.domain.model.QuadroBT(codIntervento = "Q", nomeCompleto = "Quadro"))
        val freqConQ = AntincendioAttivitaResolver.resolveFrequenze(
            impianto = impiantoRig,
            impiantiNelCantiere = conQuadro
        )
        assertEquals(2, freqConQ.size)
        assertTrue(freqConQ.contains(Periodo(TipoPeriodo.M, 6)))
        assertTrue(freqConQ.contains(Periodo(TipoPeriodo.A, 1)))
    }
}
