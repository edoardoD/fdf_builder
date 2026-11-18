package manutenzioni.domain.service

import manutenzioni.domain.model.*
import manutenzioni.domain.service.rules.CrossPlantRule
import manutenzioni.domain.service.rules.DefaultCrossPlantRule
import manutenzioni.domain.service.rules.FireAlarmCrossPlantRule
import kotlin.test.*

class CrossPlantRuleTest {

    @BeforeTest
    fun setup() {
        CrossPlantAttivitaResolver.resetToDefaults()
    }

    @AfterTest
    fun cleanup() {
        CrossPlantAttivitaResolver.resetToDefaults()
    }

    @Test
    fun `FireAlarmCrossPlantRule canHandle accepts RilevazioneAntincendio and RI code`() {
        val rule = FireAlarmCrossPlantRule()
        val antincendioModel = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi"
        )
        val standardWithRiCode = ImpiantoStandard(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi Standard"
        )
        val genericStandard = ImpiantoStandard(
            codIntervento = "GE",
            nomeCompleto = "Gruppo Elettrogeno"
        )

        assertTrue(rule.canHandle(antincendioModel), "Deve gestire RilevazioneAntincendio")
        assertTrue(rule.canHandle(standardWithRiCode), "Deve gestire codIntervento RI")
        assertFalse(rule.canHandle(genericStandard), "Non deve gestire impianti generici non antincendio")
    }

    @Test
    fun `FireAlarmCrossPlantRule injects 10 institutional base activities at 6M`() {
        val rule = FireAlarmCrossPlantRule()
        val baseActivities = (1..15).map { idx ->
            Attivita(
                nAttivita = idx,
                descrizione = "Attivita Base $idx",
                frequenza = Periodo(TipoPeriodo.M, 6),
                targetImpiantoCod = null
            )
        }
        val plant = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaAttivita = baseActivities
        )

        val resolved = rule.resolveAttivita(
            impianto = plant,
            impiantiNelCantiere = listOf(plant),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )

        // Must take at most 10 institutional activities when size >= 10
        assertEquals(10, resolved.size)
        assertEquals(1, resolved.first().nAttivita)
        assertEquals(10, resolved.last().nAttivita)
    }

    @Test
    fun `FireAlarmCrossPlantRule includes cross-plant activities only when target plant exists in cantiere`() {
        val rule = FireAlarmCrossPlantRule()
        val antincendio = RilevazioneAntincendio(
            codIntervento = "RI",
            nomeCompleto = "Rilevazione Incendi",
            listaAttivita = listOf(
                Attivita(
                    nAttivita = 1,
                    descrizione = "Centrale",
                    frequenza = Periodo(TipoPeriodo.M, 6)
                ),
                Attivita(
                    nAttivita = 2,
                    descrizione = "Sgancio GE",
                    frequenza = Periodo(TipoPeriodo.M, 6),
                    targetImpiantoCod = "GE"
                )
            )
        )

        val resolvedWithoutGe = rule.resolveAttivita(
            impianto = antincendio,
            impiantiNelCantiere = listOf(antincendio),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        assertEquals(1, resolvedWithoutGe.size)
        assertEquals("Centrale", resolvedWithoutGe[0].descrizione)

        val resolvedWithGe = rule.resolveAttivita(
            impianto = antincendio,
            impiantiNelCantiere = listOf(antincendio, ImpiantoStandard(codIntervento = "GE", nomeCompleto = "GE")),
            frequenza = Periodo(TipoPeriodo.M, 6)
        )
        assertEquals(2, resolvedWithGe.size)
        assertEquals(listOf(1, 2), resolvedWithGe.map { it.nAttivita })
    }

    @Test
    fun `DefaultCrossPlantRule filters conditioned activities based on cantiere composition`() {
        val rule = DefaultCrossPlantRule()
        val plant = ImpiantoStandard(
            codIntervento = "CAB",
            nomeCompleto = "Cabina Elettrica",
            listaAttivita = listOf(
                Attivita(nAttivita = 1, descrizione = "Verifica generale", frequenza = Periodo(TipoPeriodo.M, 6)),
                Attivita(nAttivita = 2, descrizione = "Interfaccia GE", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "GE"),
                Attivita(nAttivita = 3, descrizione = "Interfaccia PEM", frequenza = Periodo(TipoPeriodo.M, 6), targetImpiantoCod = "PEM")
            )
        )

        // Cantiere has CAB and GE, but NOT PEM
        val cantierePlants = listOf(
            plant,
            ImpiantoStandard(codIntervento = "GE", nomeCompleto = "Gruppo Elettrogeno")
        )

        val resolved = rule.resolveAttivita(plant, cantierePlants, Periodo(TipoPeriodo.M, 6))
        assertEquals(2, resolved.size)
        assertEquals("Verifica generale", resolved[0].descrizione)
        assertEquals("Interfaccia GE", resolved[1].descrizione)
        assertEquals(1, resolved[0].nAttivita)
        assertEquals(2, resolved[1].nAttivita)
    }

    @Test
    fun `Custom rule can be registered with high priority and resetToDefaults restores state`() {
        val customRule = object : CrossPlantRule {
            override fun canHandle(impianto: Impianto): Boolean = impianto.codIntervento == "CUSTOM"

            override fun resolveAttivita(
                impianto: Impianto,
                impiantiNelCantiere: List<Impianto>,
                frequenza: Periodo
            ): List<Attivita> {
                return listOf(
                    Attivita(nAttivita = 1, descrizione = "Attivita Custom Regola", frequenza = frequenza)
                )
            }

            override fun resolveFrequenze(
                impianto: Impianto,
                impiantiNelCantiere: List<Impianto>
            ): List<Periodo> = listOf(Periodo(TipoPeriodo.M, 12))
        }

        CrossPlantAttivitaResolver.registerRule(customRule)

        val customPlant = ImpiantoStandard(codIntervento = "CUSTOM", nomeCompleto = "Impianto Custom")
        assertTrue(CrossPlantAttivitaResolver.isCrossPlant(customPlant))
        val resolved = CrossPlantAttivitaResolver.resolveAttivita(customPlant, listOf(customPlant), Periodo(TipoPeriodo.M, 6))
        assertEquals(1, resolved.size)
        assertEquals("Attivita Custom Regola", resolved.first().descrizione)

        CrossPlantAttivitaResolver.resetToDefaults()
        assertEquals(1, CrossPlantAttivitaResolver.getRegisteredRules().size)
        assertFalse(CrossPlantAttivitaResolver.getRegisteredRules().any { it.canHandle(customPlant) })
    }

    @Test
    fun `Polymorphic Impianto properties and lifecycle encapsulation operate correctly`() {
        // 1. QuadroBT
        val quadro = QuadroBT(
            codIntervento = "Q",
            nomeCompleto = "Quadro Generale BT",
            sigla = "QG1",
            descrizioneQuadro = "Quadro Principale Stabilimento",
            listaInterruttori = listOf(
                InterruttoreBT(nome = "Int 1", quantita = 2),
                InterruttoreBT(nome = "Int 2", quantita = 3)
            )
        )
        assertEquals("Q - QG1", quadro.siglaCartiglio)
        assertEquals("Q-QG1", quadro.filePrefix)
        assertEquals("Q (QG1)", quadro.displayCode)
        assertEquals("Quadro Principale Stabilimento", quadro.descrizioneDettagliata)
        assertEquals("Quadro Generale BT (QG1)", quadro.nomeConSigla)
        assertEquals("Sigla: QG1", quadro.subtipoLabel)
        assertEquals(5, quadro.conteggioComponenti)
        assertEquals("Sì (5 diff.)", quadro.dettaglioAllegato)
        assertEquals("2 interruttori", quadro.extraDettaglio)
        assertTrue(quadro.hasTechnicalAttachment)
        assertTrue(quadro.isQuadro)
        assertEquals(ImpiantoKind.QUADRO_BT, quadro.kind)

        // 2. QuadroMQT
        val mqt = QuadroMQT(
            codIntervento = "QMT",
            nomeCompleto = "Quadro MT",
            tipoInterruttore = MqtSwitchType.ISV,
            listaAttivita = listOf(
                Attivita(nAttivita = 1, descrizione = "Verifica", zona = "Interruttore", frequenza = Periodo(TipoPeriodo.M, 6))
            )
        )
        assertEquals(ImpiantoKind.QUADRO_MQT, mqt.kind)
        assertTrue(mqt.isQuadroMqt)
        assertEquals("Interruttore: ISV", mqt.subtipoLabel)
        assertEquals("ISV", mqt.formatZonaAttivita(mqt.listaAttivita.first()))

        val instantiatedMqt = mqt.instantiateForCantiere("cant-123")
        assertEquals("cant-123", instantiatedMqt.cantiereId)
        assertEquals(MqtSwitchType.ISV, (instantiatedMqt as QuadroMQT).tipoInterruttore)

        // 3. ImpiantoEmergenza
        val emergenza = ImpiantoEmergenza(
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza",
            listaLampade = listOf(
                LampadaEmergenza(modello = "Beghelli 1h")
            )
        )
        assertEquals(ImpiantoKind.EMERGENZA, emergenza.kind)
        assertTrue(emergenza.isEmergenza)
        assertEquals(1, emergenza.conteggioComponenti)
        assertEquals("Sì (1 lampade)", emergenza.dettaglioAllegato)
        assertEquals("1 lampade", emergenza.extraDettaglio)
        assertTrue(emergenza.hasTechnicalAttachment)

        // 4. ImpiantoCodici helper tests
        assertTrue(ImpiantoCodici.isQuadroBT("Q"))
        assertTrue(ImpiantoCodici.isQuadroBT("Q1"))
        assertFalse(ImpiantoCodici.isQuadroBT("QMT"))
        assertTrue(ImpiantoCodici.isEmergenza("EM"))
        assertTrue(ImpiantoCodici.isEmergenza("IS"))
        assertTrue(ImpiantoCodici.isQuadroMQT("QMT"))
        assertTrue(ImpiantoCodici.isAntincendio("RI"))
        assertTrue(ImpiantoCodici.isGas("RIG"))
    }
}
