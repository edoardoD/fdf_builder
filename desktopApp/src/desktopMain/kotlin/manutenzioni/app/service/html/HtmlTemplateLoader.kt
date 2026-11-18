package manutenzioni.app.service.html

import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Caricatore con cache in-memory thread-safe per i template HTML (es. scheletro.html).
 *
 * Evita la rilettura dal disco o dal classpath per ogni singolo documento generato,
 * garantendo prestazioni ottimali nelle generazioni batch multipagina.
 */
class HtmlTemplateLoader(
    private val defaultTemplatePath: String = DEFAULT_TEMPLATE
) {
    companion object {
        const val DEFAULT_TEMPLATE = "scheletro.html"
    }

    private val cache = ConcurrentHashMap<String, String>()

    /**
     * Carica il contenuto del template specificato. Se già presente in memoria,
     * restituisce immediatamente la copia cachata.
     *
     * @param templatePath Percorso risorsa classpath o percorso file su disco.
     * @throws IllegalStateException Se il template non viene trovato in nessuna sorgente.
     */
    fun loadTemplate(templatePath: String = defaultTemplatePath): String {
        return cache.computeIfAbsent(templatePath) { path ->
            readFromSource(path)
        }
    }

    /**
     * Svuota la cache in-memory (utile per testing o ricaricamento a caldo).
     */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Verifica se un determinato template è attualmente presente nella cache in-memory.
     */
    fun isCached(templatePath: String = defaultTemplatePath): Boolean {
        return cache.containsKey(templatePath)
    }

    /**
     * Dimensione attuale della cache (numero di template caricati).
     */
    fun cacheSize(): Int = cache.size

    private fun readFromSource(path: String): String {
        // 1. Cerca nel classpath (risorse applicative) con 4 fallback di classloader
        val fromClasspath: InputStream? = Thread.currentThread().contextClassLoader?.getResourceAsStream(path)
            ?: this::class.java.classLoader?.getResourceAsStream(path)
            ?: this::class.java.getResourceAsStream("/$path")
            ?: ClassLoader.getSystemResourceAsStream(path)

        if (fromClasspath != null) {
            return fromClasspath.bufferedReader(Charsets.UTF_8).use { it.readText() }
        }

        // 2. Fallback: cerca su filesystem (percorso assoluto o relativo di lavoro)
        val file = File(path)
        if (file.exists() && file.isFile) {
            return file.readText(Charsets.UTF_8)
        }

        throw IllegalStateException(
            "Template HTML non trovato: $path. Assicurati che il file sia presente nelle risorse."
        )
    }
}
