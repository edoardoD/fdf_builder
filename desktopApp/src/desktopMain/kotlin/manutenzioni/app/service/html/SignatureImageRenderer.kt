package manutenzioni.app.service.html

import java.io.File
import java.util.Base64

/**
 * Renderer per firme grafiche/digitali (tecnico e cliente).
 *
 * Converte percorsi file o Data URI in elementi `<img>` HTML conformi
 * alle dimensioni del cartiglio con gestione sicura degli errori I/O.
 */
class SignatureImageRenderer(
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES
) {
    companion object {
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024 // 5 MB
        const val DEFAULT_STYLE = "height: 1.05cm; max-height: 1.10cm; max-width: 7.2cm; object-fit: contain; display: block; margin-top: 2px;"
        const val DEFAULT_ALT_TECNICO = "Firma Tecnico"
        const val DEFAULT_ALT_CLIENTE = "Firma Cliente"
    }

    /**
     * Converte una stringa firma (percorso file locale o Data URI) in tag HTML `<img>`.
     * Se il valore è nullo, vuoto o punta a un file inesistente, restituisce una stringa vuota `""`.
     */
    fun renderSignatureHtml(
        signatureRaw: String?,
        altText: String = DEFAULT_ALT_TECNICO,
        style: String = DEFAULT_STYLE
    ): String {
        val raw = signatureRaw?.trim().orEmpty()
        if (raw.isEmpty()) return ""

        val dataUri = resolveDataUri(raw) ?: return ""
        return """<img src="$dataUri" alt="${escapeHtml(altText)}" style="$style" />"""
    }

    /**
     * Risolve il Data URI (restituisce il Data URI esistente o legge i byte del file e codifica in Base64).
     */
    fun resolveDataUri(raw: String): String? {
        if (raw.startsWith("data:image/", ignoreCase = true)) {
            return raw
        }

        val file = File(raw)
        if (!file.exists() || !file.isFile) {
            return null
        }

        if (file.length() > maxFileSizeBytes) {
            return null
        }

        val bytes = try {
            file.readBytes()
        } catch (_: Throwable) {
            return null
        }

        val mime = detectMimeType(file.extension)
        val b64 = Base64.getEncoder().encodeToString(bytes)
        return "data:$mime;base64,$b64"
    }

    private fun detectMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "svg" -> "image/svg+xml"
            else -> "image/png"
        }
    }
}
