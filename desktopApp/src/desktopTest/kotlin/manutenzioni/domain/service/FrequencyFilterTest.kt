package manutenzioni.domain.service

import manutenzioni.domain.model.Attivita
import manutenzioni.domain.model.Periodo
import manutenzioni.domain.model.TipoPeriodo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test per FrequencyFilter — la STELLA POLARE del sistema.
 *
 * Regola fondamentale:
 *   F.inMesi() % A.frequenza.inMesi() == 0
 *
 * Matrice di inclusione (da AGENTS.md):
 *   1 Mese  → include 1
 *   3 Mesi  → include 1, 3
 *   6 Mesi  → include 1, 2, 3, 6
 *   1 Anno  → include 1, 2, 3, 4, 6, 12
 *   2 Anni  → include 1, 2, 3, 4, 6, 8, 12, 24
 *   3 Anni  → include 1, 2, 3, 4, 6, 9, 12, 18, 36
 */
class FrequencyFilterTest {

    // Helper per creare attività con una data frequenza in mesi
    private fun attivitaConMesi(nAttivita: Int, mesi: Int): Attivita {
        val periodo = if (mesi >= 12 && mesi % 12 == 0)
            Periodo(TipoPeriodo.A, mesi / 12)
        else
            Periodo(TipoPeriodo.M, mesi)
        return Attivita(
            nAttivita = nAttivita,
            tipoAttivita = "Test",
            descrizione = "Attività test #$nAttivita ($mesi mesi)",
            frequenza = periodo
        )
    }

    // =========== MATRICE DI INCLUSIONE COMPLETA ===========

    private val tutteLeAttivita = listOf(
        attivitaConMesi(1, 1),
        attivitaConMesi(2, 2),
        attivitaConMesi(3, 3),
        attivitaConMesi(4, 4),
        attivitaConMesi(5, 6),
        attivitaConMesi(6, 8),
        attivitaConMesi(7, 9),
        attivitaConMesi(8, 12),
        attivitaConMesi(9, 18),
        attivitaConMesi(10, 24),
        attivitaConMesi(11, 36)
    )

    @Test
    fun `frequenza 1 mese include solo attivita da 1 mese`() {
        val freq = Periodo(TipoPeriodo.M, 1)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `frequenza 3 mesi include attivita da 1, 3 mesi`() {
        val freq = Periodo(TipoPeriodo.M, 3)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1, 3), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `frequenza 6 mesi include attivita da 1, 2, 3, 6 mesi`() {
        val freq = Periodo(TipoPeriodo.M, 6)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1, 2, 3, 6), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `frequenza 1 anno include attivita da 1, 2, 3, 4, 6, 12 mesi`() {
        val freq = Periodo(TipoPeriodo.A, 1)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1, 2, 3, 4, 6, 12), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `frequenza 2 anni include attivita da 1, 2, 3, 4, 6, 8, 12, 24 mesi`() {
        val freq = Periodo(TipoPeriodo.A, 2)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1, 2, 3, 4, 6, 8, 12, 24), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `frequenza 3 anni include attivita da 1, 2, 3, 4, 6, 9, 12, 18, 36 mesi`() {
        val freq = Periodo(TipoPeriodo.A, 3)
        val result = FrequencyFilter.filterByFrequenza(tutteLeAttivita, freq)
        assertEquals(listOf(1, 2, 3, 4, 6, 9, 12, 18, 36), result.map { it.frequenza.inMesi() })
    }

    // =========== ORDINAMENTO ===========

    @Test
    fun `risultati ordinati per frequenza crescente poi per nAttivita`() {
        val attivita = listOf(
            attivitaConMesi(5, 6),
            attivitaConMesi(1, 1),
            attivitaConMesi(3, 3),
            attivitaConMesi(2, 1), // seconda attività mensile
            attivitaConMesi(4, 3)  // seconda attività trimestrale
        )
        val result = FrequencyFilter.filterByFrequenza(attivita, Periodo(TipoPeriodo.M, 6))

        // Ordinamento: prima per frequenza.inMesi(), poi per nAttivita
        assertEquals(listOf(1, 2, 3, 4, 5), result.map { it.nAttivita })
        assertEquals(listOf(1, 1, 3, 3, 6), result.map { it.frequenza.inMesi() })
    }

    @Test
    fun `risultati ordinati canonicamente da 01-06 (TipoPeriodo M) a 1-n (TipoPeriodo A)`() {
        val attivitaNonOrdinate = listOf(
            Attivita(nAttivita = 1, tipoAttivita = "A2", descrizione = "Biennale", frequenza = Periodo(TipoPeriodo.A, 2)),
            Attivita(nAttivita = 2, tipoAttivita = "M6", descrizione = "Semestrale", frequenza = Periodo(TipoPeriodo.M, 6)),
            Attivita(nAttivita = 3, tipoAttivita = "A1", descrizione = "Annuale", frequenza = Periodo(TipoPeriodo.A, 1)),
            Attivita(nAttivita = 4, tipoAttivita = "M1", descrizione = "Mensile", frequenza = Periodo(TipoPeriodo.M, 1)),
            Attivita(nAttivita = 5, tipoAttivita = "M3", descrizione = "Trimestrale", frequenza = Periodo(TipoPeriodo.M, 3))
        )
        val result = FrequencyFilter.filterByFrequenza(attivitaNonOrdinate, Periodo(TipoPeriodo.A, 2))
        assertEquals(
            listOf("01", "03", "06", "1", "2"),
            result.map { it.frequenza.codiceCartiglio() }
        )
    }

    // =========== LISTA VUOTA ===========

    @Test
    fun `lista vuota restituisce lista vuota`() {
        val result = FrequencyFilter.filterByFrequenza(emptyList(), Periodo(TipoPeriodo.A, 1))
        assertTrue(result.isEmpty())
    }

    // =========== FREQUENZE DISPONIBILI ===========

    @Test
    fun `frequenzeDisponibili restituisce periodi distinti ordinati`() {
        val attivita = listOf(
            attivitaConMesi(1, 1),
            attivitaConMesi(2, 3),
            attivitaConMesi(3, 1), // duplicato
            attivitaConMesi(4, 6),
            attivitaConMesi(5, 12)
        )
        val result = FrequencyFilter.frequenzeDisponibili(attivita)
        assertEquals(listOf(1, 3, 6, 12), result.map { it.inMesi() })
    }

    @Test
    fun `frequenzeDisponibili lista vuota restituisce lista vuota`() {
        val result = FrequencyFilter.frequenzeDisponibili(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `frequenzeDisponibili con un solo tipo di frequenza restituisce un solo elemento`() {
        val attivita = listOf(
            attivitaConMesi(1, 6),
            attivitaConMesi(2, 6),
            attivitaConMesi(3, 6)
        )
        val result = FrequencyFilter.frequenzeDisponibili(attivita)
        assertEquals(1, result.size)
        assertEquals(6, result.first().inMesi())
    }
}
