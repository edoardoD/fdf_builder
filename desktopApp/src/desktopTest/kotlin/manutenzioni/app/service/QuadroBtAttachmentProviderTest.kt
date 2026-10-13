package manutenzioni.app.service

import manutenzioni.app.service.attachments.QuadroBtAttachmentProvider
import manutenzioni.domain.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class QuadroBtAttachmentProviderTest {

    private val provider = QuadroBtAttachmentProvider()

    private val quadroConInterruttori = QuadroBT(
        codIntervento = "Q",
        nomeCompleto = "Quadro elettrico",
        sigla = "QG",
        descrizioneQuadro = "Quadro Generale Produzione",
        listaInterruttori = listOf(
            InterruttoreBT(
                nome = "Magnetotermico Differenziale",
                quantita = 1,
                siglaCircuito = "QA1",
                produttore = "ABB",
                codiceArticolo = "S201-C16",
                caratteristicheTecniche = mapOf("taratura" to "0,03A-A")
            ),
            InterruttoreBT(
                nome = "Differenziale Puro",
                quantita = 2,
                siglaCircuito = "QA2",
                produttore = "BTicino",
                codiceArticolo = "GC8813AC16",
                caratteristicheTecniche = mapOf("taratura" to "0,3A-AS")
            )
        )
    )

    @Test
    fun `frequenza 6 mesi genera tabella DIFF solo con prova con tasto`() {
        val sheetData = provider.buildSheetData(
            impianto = quadroConInterruttori,
            frequenza = Periodo(TipoPeriodo.M, 6),
            clienteNome = "Azienda Test"
        )

        assertNotNull(sheetData)
        val colTitles = sheetData.columns.map { it.title }
        assertTrue(colTitles.contains("Prova con tasto"), "Deve contenere Prova con tasto")
        assertFalse(colTitles.contains("Prova con strumento"), "A 6 mesi NON deve contenere Prova con strumento (R15)")

        // Verifica larghezza totale 18.4 cm
        val totalWidth = sheetData.columns.sumOf { it.totalWidthCm }
        assertEquals(18.4, Math.round(totalWidth * 10.0) / 10.0, "La larghezza totale deve essere 18.4 cm")

        // 1 elemento da 1 + 1 elemento da 2 = 3 righe espanse
        assertEquals(3, sheetData.rows.size)
        // A 6 mesi le celle per riga sono 6 (sigla quadro, sigla interruttore, dati, taratura, tasto OK, tasto NON OK)
        assertEquals(6, sheetData.rows[0].cells.size)
    }

    @Test
    fun `frequenza 1 anno genera tabella DIFF con prova con tasto e strumento`() {
        val sheetData = provider.buildSheetData(
            impianto = quadroConInterruttori,
            frequenza = Periodo(TipoPeriodo.A, 1),
            clienteNome = "Azienda Test"
        )

        assertNotNull(sheetData)
        val colTitles = sheetData.columns.map { it.title }
        assertTrue(colTitles.contains("Prova con tasto"), "Deve contenere Prova con tasto")
        assertTrue(colTitles.contains("Prova con strumento"), "A 1 anno deve contenere Prova con strumento (R15)")

        val totalWidth = sheetData.columns.sumOf { it.totalWidthCm }
        assertEquals(18.4, Math.round(totalWidth * 10.0) / 10.0, "La larghezza totale deve essere 18.4 cm")

        assertEquals(3, sheetData.rows.size)
        // A 1 anno le celle per riga sono 9 (sigla quadro, sigla interruttore, dati, taratura, tasto OK/NON OK, strumento OK/NON OK, misura input)
        assertEquals(9, sheetData.rows[0].cells.size)
    }
}
