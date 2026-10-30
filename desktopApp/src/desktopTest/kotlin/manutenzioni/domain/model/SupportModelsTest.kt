package manutenzioni.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotEquals

/**
 * Test per le data class di supporto: Attivita, Normativa, Cliente, Cantiere,
 * InterruttoreBT, LampadaEmergenza, ComponenteStandard.
 */
class SupportModelsTest {

    // =========== ATTIVITA ===========

    @Test
    fun `Attivita default visibile e true`() {
        val att = Attivita(1, "Test", "Descrizione", Periodo(TipoPeriodo.M, 1))
        assertTrue(att.visibile)
    }

    @Test
    fun `Attivita default componentRef e null`() {
        val att = Attivita(1, "Test", "Descrizione", Periodo(TipoPeriodo.M, 1))
        assertEquals(null, att.componentRef)
    }

    // =========== CLIENTE ===========

    @Test
    fun `Cliente ha campi opzionali con default null`() {
        val cliente = Cliente(id = "c1", nome = "Acme")
        assertEquals(null, cliente.indirizzo)
        assertEquals(null, cliente.partitaIva)
    }

    // =========== CANTIERE ===========

    @Test
    fun `Cantiere contiene il riferimento al clienteId`() {
        val cantiere = Cantiere(id = "cant-1", nome = "Villa Igea", clienteId = "c1")
        assertEquals("c1", cantiere.clienteId)
    }

    // =========== INTERRUTTORE BT ===========

    @Test
    fun `InterruttoreBT genera un ID univoco automatico`() {
        val a = InterruttoreBT(nome = "ABB 63A")
        val b = InterruttoreBT(nome = "ABB 63A")
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `InterruttoreBT note di default e null`() {
        val interruttore = InterruttoreBT(nome = "Schneider 32A")
        assertEquals(null, interruttore.note)
    }

    // =========== COMPONENTE STANDARD ===========

    @Test
    fun `ComponenteStandard con attributi vuoti di default`() {
        val comp = ComponenteStandard(
            tipo = TipoComponenteStandard.INTERRUTTORE_BT,
            nome = "ABB 63A"
        )
        assertTrue(comp.attributi.isEmpty())
    }

    @Test
    fun `TipoComponenteStandard elenca tutti i tipi`() {
        val tipi = TipoComponenteStandard.entries
        assertEquals(4, tipi.size)
        assertTrue(tipi.contains(TipoComponenteStandard.INTERRUTTORE_BT))
        assertTrue(tipi.contains(TipoComponenteStandard.LAMPADA_EMERGENZA))
        assertTrue(tipi.contains(TipoComponenteStandard.COMPONENTE_ANTINCENDIO))
        assertTrue(tipi.contains(TipoComponenteStandard.COMPONENTE_GAS))
    }

    // =========== MQT SWITCH TYPE ===========

    @Test
    fun `MqtSwitchType ha label corrette`() {
        assertEquals("SF6", MqtSwitchType.SF6.label)
        assertEquals("ISV", MqtSwitchType.ISV.label)
        assertEquals("IVOR", MqtSwitchType.IVOR.label)
    }

    // =========== LAMPADA EMERGENZA ===========

    @Test
    fun `LampadaEmergenza genera ID univoco`() {
        val a = LampadaEmergenza(modello = "Philips TBS", autonomia = "1h")
        val b = LampadaEmergenza(modello = "Philips TBS", autonomia = "1h")
        assertNotEquals(a.id, b.id)
    }
}
