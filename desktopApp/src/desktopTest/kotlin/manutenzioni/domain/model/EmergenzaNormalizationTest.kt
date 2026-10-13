package manutenzioni.domain.model

import manutenzioni.app.service.attachments.EmergenzaAttachmentProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EmergenzaNormalizationTest {

    private val provider = EmergenzaAttachmentProvider()

    @Test
    fun `normalizeType converte ImpiantoStandard con codIntervento IS in ImpiantoEmergenza`() {
        val standard = ImpiantoStandard(
            codIntervento = "IS",
            nomeCompleto = "Illuminazione di sicurezza",
            premessa = "Premessa IS"
        )
        val normalized = standard.normalizeType()
        assertIs<ImpiantoEmergenza>(normalized)
        assertEquals("IS", normalized.codIntervento)
        assertEquals("Illuminazione di sicurezza", normalized.nomeCompleto)
        assertEquals("Premessa IS", normalized.premessa)
    }

    @Test
    fun `normalizeType converte ImpiantoStandard con codIntervento EM in ImpiantoEmergenza`() {
        val standard = ImpiantoStandard(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza"
        )
        val normalized = standard.normalizeType()
        assertIs<ImpiantoEmergenza>(normalized)
        assertEquals("EM", normalized.codIntervento)
    }

    @Test
    fun `cambio sigla documentale da EM a IS preserva integralmente la lista lampade`() {
        val lampade = listOf(
            LampadaEmergenza(
                sigla = "EM-01",
                modello = "Prisma LED",
                produttore = "Beghelli",
                autonomia = "1h",
                posizione = "Corridoio 1"
            ),
            LampadaEmergenza(
                sigla = "EM-02",
                modello = "Stile In",
                produttore = "Beghelli",
                autonomia = "2h",
                posizione = "Uscita Sicurezza"
            )
        )
        val emergEM = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza",
            listaLampade = lampade
        )

        // Switch a IS
        val emergIS = emergEM.copy(
            codIntervento = "IS",
            nomeCompleto = "Illuminazione di sicurezza"
        )

        assertEquals("IS", emergIS.codIntervento)
        assertEquals(2, emergIS.listaLampade.size)
        assertEquals("EM-01", emergIS.listaLampade[0].sigla)
        assertEquals("Beghelli", emergIS.listaLampade[0].produttore)
    }

    @Test
    fun `EmergenzaAttachmentProvider gestisce sia EM che IS con banner appropriati`() {
        val lampada = LampadaEmergenza(
            sigla = "L-01",
            modello = "Formula 65",
            autonomia = "1h"
        )
        val emergEM = ImpiantoEmergenza(codIntervento = "EM", nomeCompleto = "Illuminazione emergenza", listaLampade = listOf(lampada))
        val emergIS = ImpiantoEmergenza(codIntervento = "IS", nomeCompleto = "Illuminazione sicurezza", listaLampade = listOf(lampada))

        assertTrue(provider.canHandle(emergEM))
        assertTrue(provider.canHandle(emergIS))

        val dataEM = provider.buildSheetData(emergEM, Periodo(TipoPeriodo.M, 6), null)
        assertNotNull(dataEM)
        assertEquals("EM", dataEM.oggetto)
        assertEquals("LAMPADE DI EMERGENZA", dataEM.titoloBanner)

        val dataIS = provider.buildSheetData(emergIS, Periodo(TipoPeriodo.M, 6), null)
        assertNotNull(dataIS)
        assertEquals("IS", dataIS.oggetto)
        assertEquals("ILLUMINAZIONE DI SICUREZZA", dataIS.titoloBanner)
    }
}
