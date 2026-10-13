package manutenzioni.app.ui

import manutenzioni.app.ui.components.SiglaCloneHelper
import kotlin.test.Test
import kotlin.test.assertEquals

class SiglaCloneHelperTest {

    @Test
    fun `clona lampada con formato trattino e padding 2 cifre`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "EM-01",
            existingSigle = listOf("EM-01"),
            defaultPrefix = "EM"
        )
        assertEquals("EM-02", next)
    }

    @Test
    fun `clona lampada considerando il massimo esistente`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "EM-01",
            existingSigle = listOf("EM-01", "EM-02", "EM-05"),
            defaultPrefix = "EM"
        )
        assertEquals("EM-06", next)
    }

    @Test
    fun `clona interruttore quadro senza trattino con padding singolo`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "QA1",
            existingSigle = listOf("QA1", "QA2"),
            defaultPrefix = "Q"
        )
        assertEquals("QA3", next)
    }

    @Test
    fun `clona sensore antincendio con prefisso punto`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "RIV.L1.1",
            existingSigle = listOf("RIV.L1.1", "RIV.L1.2", "RIV.L1.3"),
            defaultPrefix = "RI"
        )
        assertEquals("RIV.L1.4", next)
    }

    @Test
    fun `gestisce baseSigla senza cifre finali applicando fallback numerato`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "DIFF",
            existingSigle = emptyList(),
            defaultPrefix = "Q"
        )
        assertEquals("DIFF-01", next)
    }

    @Test
    fun `gestisce baseSigla vuota con defaultPrefix`() {
        val next = SiglaCloneHelper.findNextSiglaForClone(
            baseSigla = "",
            existingSigle = listOf("EM-01"),
            defaultPrefix = "EM"
        )
        assertEquals("EM-02", next)
    }
}
