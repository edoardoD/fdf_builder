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
        assertNull(decoded.nomeManutentore)
        assertNull(decoded.indirizzo)
        assertNull(decoded.firmaManutentore)
    }
}
