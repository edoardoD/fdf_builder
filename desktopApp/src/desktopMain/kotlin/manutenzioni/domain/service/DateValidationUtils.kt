package manutenzioni.domain.service

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Utility pure di dominio per la validazione, formattazione e maschera delle date nel formato italiano (GG/MM/AAAA - dd/MM/yyyy).
 */
object DateValidationUtils {

    val FORMATTER_IT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    /**
     * Verifica se una stringa rappresenta una data valida nel formato "dd/MM/yyyy" (es. "22/10/2026").
     * Controlla rigorosamente la validità di giorni (01-31), mesi (01-12) e la correttezza degli anni bisestili (es. 29 febbraio).
     */
    fun isValidItalianDate(dateStr: String?): Boolean {
        val trimmed = dateStr?.trim().orEmpty()
        if (trimmed.length != 10) return false

        val parts = trimmed.split("/")
        if (parts.size != 3) return false

        val day = parts[0].toIntOrNull() ?: return false
        val month = parts[1].toIntOrNull() ?: return false
        val year = parts[2].toIntOrNull() ?: return false

        if (year !in 1900..2100) return false
        if (month !in 1..12) return false

        val maxDay = try {
            YearMonth.of(year, month).lengthOfMonth()
        } catch (_: Exception) {
            return false
        }

        return day in 1..maxDay
    }

    /**
     * Effettua il parsing sicuro di una stringa "dd/MM/yyyy" in [LocalDate].
     * Ritorna null se la data non è valida o non conforme.
     */
    fun parseItalianDate(dateStr: String?): LocalDate? {
        if (!isValidItalianDate(dateStr)) return null
        return try {
            LocalDate.parse(dateStr!!.trim(), FORMATTER_IT)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converte un [LocalDate] nella stringa formattata italiana "dd/MM/yyyy".
     */
    fun formatToItalianDate(localDate: LocalDate): String {
        return localDate.format(FORMATTER_IT)
    }

    /**
     * Applica la maschera di digitazione automatica "GG/MM/AAAA".
     * Estrae le sole cifre (fino a 8) e inserisce progressivamente gli slash di separazione.
     *
     * Esempi:
     * - "22" -> "22"
     * - "221" -> "22/1"
     * - "2210" -> "22/10"
     * - "22102026" -> "22/10/2026"
     * - "22/10/2026" -> "22/10/2026"
     */
    fun formatItalianDateInput(rawInput: String): String {
        val digits = rawInput.filter { it.isDigit() }.take(8)
        return buildString {
            for (i in digits.indices) {
                append(digits[i])
                if ((i == 1 || i == 3) && i != digits.lastIndex) {
                    append("/")
                }
            }
        }
    }
}
