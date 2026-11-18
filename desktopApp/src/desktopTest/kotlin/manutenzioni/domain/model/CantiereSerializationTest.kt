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
            dataIntervento = "08/10/2026",
            nomeManutentore = "Mario Rossi",
            indirizzo = "Via Roma 1, Milano",
            firmaManutentore = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
        )

        val encoded = json.encodeToString(cantiere)
        assertTrue(encoded.contains("\"indirizzo\": \"Via Roma 1, Milano\""))
        assertTrue(encoded.contains("\"dataIntervento\": \"08/10/2026\""))
        assertTrue(encoded.contains("\"nomeManutentore\": \"Mario Rossi\""))
        assertTrue(encoded.contains("\"firmaManutentore\": \"data:image/png;base64,"))

        val decoded = json.decodeFromString<Cantiere>(encoded)
        assertEquals(cantiere, decoded)
        assertEquals("08/10/2026", decoded.dataIntervento)
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
    }

    @Test
    fun `deserializzazione con campi opzionali omessi usa default null`() {
        val minimalJson = """
            {
                "id": "cantiere-min",
                "nome": "Ospedale Civile",
                "clienteId": "cli-1"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<Cantiere>(minimalJson)
        assertEquals("cantiere-min", decoded.id)
        assertEquals("Ospedale Civile", decoded.nome)
        assertEquals("cli-1", decoded.clienteId)
        assertNull(decoded.dataInizioContratto)
        assertNull(decoded.dataIntervento)
        assertNull(decoded.dataInterventoPrecedente)
        assertNull(decoded.nomeManutentore)
        assertNull(decoded.indirizzo)
        assertNull(decoded.firmaManutentore)

        val normalized = decoded.normalizeDates()
        assertEquals(decoded, normalized)
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
        assertEquals("01/01/2025", promoted.dataInizioContratto)

        // Promozione senza nuova data (fine sessione, in attesa di nuovo ciclo)
        val promotedNoDate = cantiere.promoteInterventoToPrecedente()
        assertEquals("15/10/2026", promotedNoDate.dataInterventoPrecedente)
        assertNull(promotedNoDate.dataIntervento)
    }

    @Test
    fun `normalizeDates gestisce spazi bianchi e stringhe vuote`() {
        val cantiereConSpazi = Cantiere(
            id = "cantiere-ws",
            nome = "Spazi Test",
            clienteId = "cli-ws",
            dataInizioContratto = "  01/01/2026  ",
            dataIntervento = "  15/10/2026  ",
            dataInterventoPrecedente = "   "
        )

        val normalized = cantiereConSpazi.normalizeDates()
        assertEquals("01/01/2026", normalized.dataInizioContratto)
        assertEquals("15/10/2026", normalized.dataIntervento)
        assertNull(normalized.dataInterventoPrecedente)

        // Idempotenza: richiamare normalizeDates su un cantiere già normalizzato non altera i valori
        val doubleNormalized = normalized.normalizeDates()
        assertEquals(normalized, doubleNormalized)
    }
}
