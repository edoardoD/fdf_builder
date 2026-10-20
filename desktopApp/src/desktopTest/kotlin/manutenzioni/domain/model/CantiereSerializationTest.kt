package manutenzioni.domain.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CantiereSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `serializzazione e deserializzazione con tutti i campi`() {
        val cantiere = Cantiere(
            id = "cantiere-123",
            nome = "Villa Igea",
            clienteId = "cliente-456",
            dataInizioIntervento = "08/10/2026",
            nomeManutentore = "Mario Rossi",
            indirizzo = "Via Roma 1, Milano",
            firmaManutentore = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )

        val encoded = json.encodeToString(cantiere)
        assertTrue(encoded.contains("\"indirizzo\": \"Via Roma 1, Milano\""))
        assertTrue(encoded.contains("\"dataInizioIntervento\": \"08/10/2026\""))
        assertTrue(encoded.contains("\"nomeManutentore\": \"Mario Rossi\""))
        assertTrue(encoded.contains("\"firmaManutentore\": \"data:image/png;base64,"))

        val decoded = json.decodeFromString<Cantiere>(encoded)
        assertEquals(cantiere, decoded)
        assertEquals("08/10/2026", decoded.dataInizioIntervento)
        assertEquals("Mario Rossi", decoded.nomeManutentore)
        assertTrue(decoded.firmaManutentore!!.startsWith("data:image/png;base64,"))
    }

    @Test
    fun `serializzazione e deserializzazione con tutte e tre le date (contratto, intervento, precedente)`() {
        val cantiere = Cantiere(
            id = "cantiere-789",
            nome = "Torre Unipol",
            clienteId = "cli-999",
            dataInizioContratto = "01/01/2026",
            dataIntervento = "15/10/2026",
            dataInterventoPrecedente = "15/04/2026",
            nomeManutentore = "Giuseppe Verdi",
            indirizzo = "Via Larga 10, Bologna",
            firmaManutentore = "data:image/png;base64,ABCDEF123456"
        )

        val encoded = json.encodeToString(cantiere)
        assertTrue(encoded.contains("\"dataInizioContratto\": \"01/01/2026\""))
        assertTrue(encoded.contains("\"dataIntervento\": \"15/10/2026\""))
        assertTrue(encoded.contains("\"dataInterventoPrecedente\": \"15/04/2026\""))

        val decoded = json.decodeFromString<Cantiere>(encoded)
        assertEquals(cantiere, decoded)
        assertEquals("01/01/2026", decoded.dataInizioContratto)
        assertEquals("15/10/2026", decoded.dataIntervento)
        assertEquals("15/04/2026", decoded.dataInterventoPrecedente)
        assertEquals("15/10/2026", decoded.effectiveDataIntervento)
        assertEquals("15/10/2026", decoded.dataInterventoEffettiva)
    }

    @Test
    fun `retrocompatibilita con JSON legacy privo di nuovi campi`() {
        val legacyJson = """
            {
                "id": "cantiere-legacy",
                "nome": "Ospedale Civile",
                "clienteId": "cli-1"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<Cantiere>(legacyJson)
        assertEquals("cantiere-legacy", decoded.id)
        assertEquals("Ospedale Civile", decoded.nome)
        assertEquals("cli-1", decoded.clienteId)
        assertNull(decoded.dataInizioIntervento)
        assertNull(decoded.dataInizioContratto)
        assertNull(decoded.dataIntervento)
        assertNull(decoded.dataInterventoPrecedente)
        assertNull(decoded.effectiveDataIntervento)
        assertNull(decoded.dataInterventoEffettiva)
        assertNull(decoded.nomeManutentore)
        assertNull(decoded.indirizzo)
        assertNull(decoded.firmaManutentore)

        val normalized = decoded.normalizeDates()
        assertEquals(decoded, normalized)
    }

    @Test
    fun `retrocompatibilita con JSON legacy contenente solo dataInizioIntervento`() {
        val legacyJson = """
            {
                "id": "cantiere-w3",
                "nome": "Sede Centrale",
                "clienteId": "cli-10",
                "dataInizioIntervento": "10/05/2026",
                "nomeManutentore": "Luigi Bianchi"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<Cantiere>(legacyJson)
        assertEquals("10/05/2026", decoded.dataInizioIntervento)
        assertNull(decoded.dataIntervento)
        assertNull(decoded.dataInizioContratto)
        assertNull(decoded.dataInterventoPrecedente)
        assertEquals("10/05/2026", decoded.effectiveDataIntervento)
        assertEquals("10/05/2026", decoded.dataInterventoEffettiva)

        val normalized = decoded.normalizeDates()
        assertEquals("10/05/2026", normalized.dataIntervento)
        assertEquals("10/05/2026", normalized.dataInizioIntervento)
        assertEquals("10/05/2026", normalized.effectiveDataIntervento)
        assertEquals("10/05/2026", normalized.dataInterventoEffettiva)
        assertNull(normalized.dataInizioContratto)
        assertNull(normalized.dataInterventoPrecedente)
    }

    @Test
    fun `domain helper promoteInterventoToPrecedente trasferisce dataIntervento in dataInterventoPrecedente`() {
        val cantiere = Cantiere(
            id = "cantiere-p1",
            nome = "Polo Logistico",
            clienteId = "cli-polo",
            dataInizioContratto = "01/01/2025",
            dataIntervento = "15/10/2026",
            dataInterventoPrecedente = "15/04/2026"
        )

        // Promozione con nuova data di intervento
        val promoted = cantiere.promoteInterventoToPrecedente("15/04/2027")
        assertEquals("15/10/2026", promoted.dataInterventoPrecedente)
        assertEquals("15/04/2027", promoted.dataIntervento)
        assertEquals("15/04/2027", promoted.dataInizioIntervento)
        assertEquals("15/04/2027", promoted.effectiveDataIntervento)
        assertEquals("01/01/2025", promoted.dataInizioContratto)

        // Promozione senza nuova data (fine sessione, in attesa di nuovo ciclo)
        val promotedNoDate = cantiere.promoteInterventoToPrecedente()
        assertEquals("15/10/2026", promotedNoDate.dataInterventoPrecedente)
        assertNull(promotedNoDate.dataIntervento)
        assertNull(promotedNoDate.dataInizioIntervento)
        assertNull(promotedNoDate.effectiveDataIntervento)

        // Promozione a partire da cantiere legacy con solo dataInizioIntervento
        val legacyCantiere = Cantiere(
            id = "cantiere-legacy",
            nome = "Vecchio Polo",
            clienteId = "cli-vecchio",
            dataInizioIntervento = "20/09/2026"
        )
        val promotedFromLegacy = legacyCantiere.promoteInterventoToPrecedente("20/03/2027")
        assertEquals("20/09/2026", promotedFromLegacy.dataInterventoPrecedente)
        assertEquals("20/03/2027", promotedFromLegacy.dataIntervento)
        assertEquals("20/03/2027", promotedFromLegacy.dataInizioIntervento)
    }

    @Test
    fun `normalizeDates gestisce spazi bianchi e sincronizzazione dual-write`() {
        val cantiereConSpazi = Cantiere(
            id = "cantiere-ws",
            nome = "Spazi Test",
            clienteId = "cli-ws",
            dataInizioContratto = "  01/01/2026  ",
            dataIntervento = "  15/10/2026  ",
            dataInterventoPrecedente = "   ",
            dataInizioIntervento = null
        )

        val normalized = cantiereConSpazi.normalizeDates()
        assertEquals("01/01/2026", normalized.dataInizioContratto)
        assertEquals("15/10/2026", normalized.dataIntervento)
        assertEquals("15/10/2026", normalized.dataInizioIntervento)
        assertNull(normalized.dataInterventoPrecedente)

        // Idempotenza: richiamare normalizeDates su un cantiere già normalizzato non altera i valori
        val doubleNormalized = normalized.normalizeDates()
        assertEquals(normalized, doubleNormalized)
    }
}
