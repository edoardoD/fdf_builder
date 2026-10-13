package manutenzioni.app.ui.components

/**
 * Helper unificato per l'incremento automatico della sigla durante la clonazione rapida di componenti
 * (interruttori quadri BT, lampade emergenza, rivelatori e moduli antincendio).
 *
 * Supporta vari formati:
 * - "EM-01" -> "EM-02"
 * - "QA1" -> "QA2"
 * - "RIV.L1.1" -> "RIV.L1.2"
 * - "DIFF-01" -> "DIFF-02"
 */
object SiglaCloneHelper {

    /**
     * Calcola la prossima sigla disponibile a partire da una sigla base e dalla lista delle sigle esistenti.
     *
     * @param baseSigla La sigla dell'elemento di partenza (es. "EM-01", "QA1", "RIV.L1.1")
     * @param existingSigle La lista di tutte le sigle attualmente registrate nello stesso contesto
     * @param defaultPrefix Prefisso di fallback se baseSigla è vuota (es. "EM", "Q", "RI")
     */
    fun findNextSiglaForClone(
        baseSigla: String,
        existingSigle: List<String>,
        defaultPrefix: String = ""
    ): String {
        val clean = baseSigla.trim()
        val match = Regex("""^(.*?)(\d+)$""").matchEntire(clean)
        val (prefix, padding) = if (match != null) {
            Pair(match.groupValues[1], match.groupValues[2].length)
        } else {
            val fallbackPrefix = if (clean.isNotBlank()) clean else defaultPrefix.ifBlank { "ITEM" }
            val separator = if (fallbackPrefix.endsWith("-") || fallbackPrefix.endsWith(".")) "" else "-"
            Pair("$fallbackPrefix$separator", 2)
        }

        val allNumbersWithPrefix = existingSigle.mapNotNull { s ->
            val m = Regex("""^(.*?)(\d+)$""").matchEntire(s.trim())
            if (m != null && m.groupValues[1] == prefix) {
                m.groupValues[2].toIntOrNull()
            } else null
        }
        val nextNum = (allNumbersWithPrefix.maxOrNull() ?: 0) + 1
        return "$prefix${nextNum.toString().padStart(padding, '0')}"
    }
}
