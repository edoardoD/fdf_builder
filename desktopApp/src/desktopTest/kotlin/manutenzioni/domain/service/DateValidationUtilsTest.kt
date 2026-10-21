package manutenzioni.domain.service

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateValidationUtilsTest {

    @Test
    fun `isValidItalianDate valida correttamente date valide`() {
        assertTrue(DateValidationUtils.isValidItalianDate("22/10/2026"))
        assertTrue(DateValidationUtils.isValidItalianDate("01/01/2026"))
        assertTrue(DateValidationUtils.isValidItalianDate("31/12/2025"))
        // 2024 è bisestile -> 29/02 è valido
        assertTrue(DateValidationUtils.isValidItalianDate("29/02/2024"))
    }

    @Test
    fun `isValidItalianDate rifiuta date non valide, bisestili errati o formati scorretti`() {
        assertFalse(DateValidationUtils.isValidItalianDate(null))
        assertFalse(DateValidationUtils.isValidItalianDate(""))
        assertFalse(DateValidationUtils.isValidItalianDate("22/10/26")) // Anno incompleto
        assertFalse(DateValidationUtils.isValidItalianDate("32/01/2026")) // Giorno > 31
        assertFalse(DateValidationUtils.isValidItalianDate("00/01/2026")) // Giorno 0
        assertFalse(DateValidationUtils.isValidItalianDate("15/13/2026")) // Mese > 12
        assertFalse(DateValidationUtils.isValidItalianDate("31/04/2026")) // Aprile ha 30 giorni
        // 2025 NON è bisestile -> 29/02 è invalido
        assertFalse(DateValidationUtils.isValidItalianDate("29/02/2025"))
        assertFalse(DateValidationUtils.isValidItalianDate("30/02/2024"))
    }

    @Test
    fun `parse e format convertono bidirezionalmente senza perdite`() {
        val dateStr = "21/10/2026"
        val parsed = DateValidationUtils.parseItalianDate(dateStr)
        assertNotNull(parsed)
        assertEquals(2026, parsed.year)
        assertEquals(10, parsed.monthValue)
        assertEquals(21, parsed.dayOfMonth)

        val formatted = DateValidationUtils.formatToItalianDate(parsed)
        assertEquals(dateStr, formatted)

        assertNull(DateValidationUtils.parseItalianDate("invalid-date"))
    }

    @Test
    fun `formatItalianDateInput applica maschera progressiva con slash`() {
        assertEquals("", DateValidationUtils.formatItalianDateInput(""))
        assertEquals("2", DateValidationUtils.formatItalianDateInput("2"))
        assertEquals("22", DateValidationUtils.formatItalianDateInput("22"))
        assertEquals("22/1", DateValidationUtils.formatItalianDateInput("221"))
        assertEquals("22/10", DateValidationUtils.formatItalianDateInput("2210"))
        assertEquals("22/10/2", DateValidationUtils.formatItalianDateInput("22102"))
        assertEquals("22/10/2026", DateValidationUtils.formatItalianDateInput("22102026"))
        // Gestione con slash già presenti o caratteri spuri
        assertEquals("22/10/2026", DateValidationUtils.formatItalianDateInput("22/10/2026"))
        assertEquals("22/10/2026", DateValidationUtils.formatItalianDateInput("22-10-2026"))
        assertEquals("22/10/2026", DateValidationUtils.formatItalianDateInput("221020269999")) // Troncato a 8 cifre
    }
}
