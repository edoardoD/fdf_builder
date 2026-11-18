package manutenzioni.app.service.html

/**
 * Utility per l'escape sicuro di caratteri speciali in contesti HTML/XML.
 */
object HtmlEscaper {

    fun escape(text: String): String {
        if (text.isEmpty()) return ""
        return buildString(text.length + 16) {
            for (ch in text) {
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
    }
}

fun escapeHtml(text: String): String = HtmlEscaper.escape(text)
