package manutenzioni.app.service.attachments

import manutenzioni.domain.service.attachments.InMemoryTechnicalAttachmentRegistry
import manutenzioni.domain.service.attachments.TechnicalAttachmentProvider

/**
 * Registro predefinito degli allegati tecnici per l'applicazione Manutenzioni Maker.
 *
 * Pre-registra automaticamente i tre provider istituzionali:
 * 1. [QuadroBtAttachmentProvider]: Verifiche interruttori differenziali (norma CEI 64-8)
 * 2. [EmergenzaAttachmentProvider]: Registro lampade di sicurezza (norma UNI 11222)
 * 3. [AntincendioAttachmentProvider]: Registro rivelatori e moduli antincendio (norma UNI 11224)
 *
 * Supporta sia l'uso come istanza isolata (per dependency injection e test)
 * sia l'accesso via singleton condiviso [DefaultTechnicalAttachmentRegistry.shared].
 */
class DefaultTechnicalAttachmentRegistry(
    customProviders: List<TechnicalAttachmentProvider>? = null
) : InMemoryTechnicalAttachmentRegistry(
    initialProviders = customProviders ?: createDefaultProviders()
) {

    override fun resetToDefaults() {
        providers.clear()
        providers.addAll(createDefaultProviders())
    }

    companion object {
        /**
         * Crea la lista immutabile dei tre provider standard di sistema.
         */
        fun createDefaultProviders(): List<TechnicalAttachmentProvider> = listOf(
            QuadroBtAttachmentProvider(),
            EmergenzaAttachmentProvider(),
            AntincendioAttachmentProvider()
        )

        /**
         * Istanza singleton globale condivisa utilizzata di default dai servizi applicativi.
         * Thread-safe e modificabile a runtime per registrazioni dinamiche di plugin o nuovi impianti.
         */
        val shared: DefaultTechnicalAttachmentRegistry by lazy {
            DefaultTechnicalAttachmentRegistry()
        }
    }
}
