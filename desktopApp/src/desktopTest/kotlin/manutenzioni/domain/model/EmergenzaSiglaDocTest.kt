package manutenzioni.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EmergenzaSiglaDocTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun `EmergenzaSiglaDoc enum ha codici e valori conformi ai contratti`() {
        assertEquals("EM", EmergenzaSiglaDoc.EM.codIntervento)
        assertEquals("Illuminazione di emergenza", EmergenzaSiglaDoc.EM.label)
        assertEquals("Illuminazione di emergenza", EmergenzaSiglaDoc.EM.nomeCompleto)
        assertEquals("Emergenza", EmergenzaSiglaDoc.EM.labelBreve)
        assertEquals("EM (Emergenza)", EmergenzaSiglaDoc.EM.displayLabel)

        assertEquals("IS", EmergenzaSiglaDoc.IS.codIntervento)
        assertEquals("Illuminazione di sicurezza", EmergenzaSiglaDoc.IS.label)
        assertEquals("Illuminazione di sicurezza", EmergenzaSiglaDoc.IS.nomeCompleto)
        assertEquals("Sicurezza", EmergenzaSiglaDoc.IS.labelBreve)
        assertEquals("IS (Sicurezza)", EmergenzaSiglaDoc.IS.displayLabel)
    }

    @Test
    fun `fromCod converte stringhe gestendo case-insensitivity e fallback su EM`() {
        assertEquals(EmergenzaSiglaDoc.IS, EmergenzaSiglaDoc.fromCod("IS"))
        assertEquals(EmergenzaSiglaDoc.IS, EmergenzaSiglaDoc.fromCod("is"))
        assertEquals(EmergenzaSiglaDoc.IS, EmergenzaSiglaDoc.fromCod("Is"))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod("EM"))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod("em"))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod("Em"))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod(null))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod(""))
        assertEquals(EmergenzaSiglaDoc.EM, EmergenzaSiglaDoc.fromCod("UNKNOWN"))
    }

    @Test
    fun `withSiglaDocumentale aggiorna codIntervento e nomeCompleto preservando tutti gli altri campi e lampade`() {
        val lampade = listOf(
            LampadaEmergenza(
                sigla = "EM-01",
                modello = "Formula 65",
                produttore = "Beghelli",
                autonomia = "1h",
                posizione = "Corridoio 1"
            ),
            LampadaEmergenza(
                sigla = "EM-02",
                modello = "NEXI 600",
                produttore = "Eaton",
                autonomia = "3h",
                posizione = "Scala B"
            )
        )
        val original = ImpiantoEmergenza(
            id = "test-id-123",
            codIntervento = "EM",
            nomeCompleto = "Illuminazione di emergenza",
            premessa = "Premessa test cantiere",
            noteSpecifiche = "Note specifiche per test",
            cantiereId = "cantiere-xyz",
            quantita = 2,
            listaAttivita = listOf(Attivita(1, "Controllo", "Verifica lampade", Periodo(TipoPeriodo.M, 6))),
            listaLampade = lampade
        )

        assertEquals(EmergenzaSiglaDoc.EM, original.siglaDocumentale)

        // Passa a IS
        val switchedToIS = original.withSiglaDocumentale(EmergenzaSiglaDoc.IS)
        assertEquals("IS", switchedToIS.codIntervento)
        assertEquals("Illuminazione di sicurezza", switchedToIS.nomeCompleto)
        assertEquals(EmergenzaSiglaDoc.IS, switchedToIS.siglaDocumentale)
        assertEquals("test-id-123", switchedToIS.id)
        assertEquals("Premessa test cantiere", switchedToIS.premessa)
        assertEquals("Note specifiche per test", switchedToIS.noteSpecifiche)
        assertEquals("cantiere-xyz", switchedToIS.cantiereId)
        assertEquals(2, switchedToIS.quantita)
        assertEquals(1, switchedToIS.listaAttivita.size)
        assertEquals(2, switchedToIS.listaLampade.size)
        assertEquals("EM-01", switchedToIS.listaLampade[0].sigla)
        assertEquals("EM-02", switchedToIS.listaLampade[1].sigla)

        // Torna a EM
        val switchedBackToEM = switchedToIS.withSiglaDocumentale(EmergenzaSiglaDoc.EM)
        assertEquals("EM", switchedBackToEM.codIntervento)
        assertEquals("Illuminazione di emergenza", switchedBackToEM.nomeCompleto)
        assertEquals(EmergenzaSiglaDoc.EM, switchedBackToEM.siglaDocumentale)
        assertEquals(2, switchedBackToEM.listaLampade.size)
    }

    @Test
    fun `EmergenzaSiglaDoc serializza e deserializza correttamente via kotlinx serialization`() {
        val encodedIS = json.encodeToString(EmergenzaSiglaDoc.serializer(), EmergenzaSiglaDoc.IS)
        val decodedIS = json.decodeFromString(EmergenzaSiglaDoc.serializer(), encodedIS)
        assertEquals(EmergenzaSiglaDoc.IS, decodedIS)

        val encodedEM = json.encodeToString(EmergenzaSiglaDoc.serializer(), EmergenzaSiglaDoc.EM)
        val decodedEM = json.decodeFromString(EmergenzaSiglaDoc.serializer(), encodedEM)
        assertEquals(EmergenzaSiglaDoc.EM, decodedEM)
    }

    @Test
    fun `ImpiantoEmergenza con sigla documentale IS si serializza e deserializza polimorficamente come Impianto`() {
        val impiantoIS: Impianto = ImpiantoEmergenza(
            id = "poly-is-1",
            codIntervento = "IS",
            nomeCompleto = "Illuminazione di sicurezza",
            listaLampade = listOf(
                LampadaEmergenza(sigla = "IS-01", modello = "Prisma LED", autonomia = "1h")
            )
        )

        val encoded = json.encodeToString(Impianto.serializer(), impiantoIS)
        val decoded = json.decodeFromString(Impianto.serializer(), encoded)
        assertIs<ImpiantoEmergenza>(decoded)
        assertEquals("IS", decoded.codIntervento)
        assertEquals(EmergenzaSiglaDoc.IS, decoded.siglaDocumentale)
        assertEquals(1, decoded.listaLampade.size)
        assertEquals("IS-01", decoded.listaLampade[0].sigla)
    }
}
