package manutenzioni.domain.service.attachments

import manutenzioni.domain.model.Impianto
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Contratto del registro estensibile per i provider di allegati tecnici (Foglio 2+).
 *
 * Implementa l'Open-Closed Principle (OCP): consente l'aggiunta, rimozione e sostituzione
 * dinamica dei provider di allegati tecnici senza alcuna modifica a [manutenzioni.domain.service.Html]
 * o alla pipeline di generazione PDF.
 *
 * Tutte le implementazioni devono garantire la thread-safety per l'accesso concorrente.
 */
interface TechnicalAttachmentRegistry {

    /**
     * Registra un nuovo [TechnicalAttachmentProvider].
     *
     * @param provider Il provider da registrare
     * @param priority Se true (default), il provider viene inserito in testa alla lista
     *                 consentendo l'override dei provider preesistenti o di default.
     */
    fun registerProvider(provider: TechnicalAttachmentProvider, priority: Boolean = true)

    /**
     * Rimuove un provider precedentemente registrato.
     *
     * @param provider Il provider da rimuovere
     * @return true se il provider era presente ed è stato rimosso, false altrimenti
     */
    fun unregisterProvider(provider: TechnicalAttachmentProvider): Boolean

    /**
     * Restituisce il primo provider registrato in grado di gestire l'impianto specificato,
     * oppure null se nessun provider è compatibile.
     *
     * Utilizzato primariamente per la generazione del sommario in premessa (Foglio 1).
     */
    fun getProviderFor(impianto: Impianto): TechnicalAttachmentProvider?

    /**
     * Restituisce tutti i provider registrati in grado di gestire l'impianto specificato,
     * ordinati per priorità di registrazione.
     *
     * Utilizzato per la generazione dei fogli tecnici secondari allegati (Foglio 2+).
     */
    fun getProvidersFor(impianto: Impianto): List<TechnicalAttachmentProvider>

    /**
     * Restituisce una copia immutabile di tutti i provider attualmente registrati.
     */
    fun getAllProviders(): List<TechnicalAttachmentProvider>

    /**
     * Rimuove tutti i provider registrati.
     * Utile per isolamento nei test unitari ermetici.
     */
    fun clear()

    /**
     * Ripristina i provider allo stato iniziale/default previsto dal registro.
     */
    fun resetToDefaults()

    /**
     * Numero di provider attualmente registrati.
     */
    val size: Int get() = getAllProviders().size

    /**
     * Indica se il registro è vuoto.
     */
    val isEmpty: Boolean get() = getAllProviders().isEmpty()
}

/**
 * Implementazione in-memory pura del contratto [TechnicalAttachmentRegistry].
 *
 * Utilizza [CopyOnWriteArrayList] per garantire:
 * 1. Thread-safety totale durante letture e scritture concorrenti.
 * 2. Iterazione lock-free e snapshot consistente durante la generazione batch di PDF.
 * 3. Zero eccezioni [ConcurrentModificationException] anche se un provider viene registrato
 *    mentre è in corso una generazione documentale.
 *
 * Questa classe risiede nel domain layer e non possiede dipendenze dal layer applicativo.
 */
open class InMemoryTechnicalAttachmentRegistry(
    initialProviders: List<TechnicalAttachmentProvider> = emptyList()
) : TechnicalAttachmentRegistry {

    protected val providers = CopyOnWriteArrayList<TechnicalAttachmentProvider>(initialProviders)
    private val baselineProviders = ArrayList(initialProviders)

    override fun registerProvider(provider: TechnicalAttachmentProvider, priority: Boolean) {
        if (priority) {
            providers.add(0, provider)
        } else {
            providers.add(provider)
        }
    }

    override fun unregisterProvider(provider: TechnicalAttachmentProvider): Boolean {
        return providers.remove(provider)
    }

    override fun getProviderFor(impianto: Impianto): TechnicalAttachmentProvider? {
        return providers.firstOrNull { it.canHandle(impianto) }
    }

    override fun getProvidersFor(impianto: Impianto): List<TechnicalAttachmentProvider> {
        return providers.filter { it.canHandle(impianto) }
    }

    override fun getAllProviders(): List<TechnicalAttachmentProvider> {
        return providers.toList()
    }

    override fun clear() {
        providers.clear()
    }

    override fun resetToDefaults() {
        providers.clear()
        providers.addAll(baselineProviders)
    }
}
