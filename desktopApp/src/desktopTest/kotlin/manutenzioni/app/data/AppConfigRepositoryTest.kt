package manutenzioni.app.data

import java.io.File
import kotlin.test.*

class AppConfigRepositoryTest {

    private var originalConfig: DbConfig? = null

    @BeforeTest
    fun backupOriginalConfig() {
        originalConfig = AppConfigRepository.loadConfig()
    }

    @AfterTest
    fun restoreOriginalConfig() {
        if (originalConfig != null) {
            AppConfigRepository.saveConfig(originalConfig!!)
        } else {
            val configFile = File(AppConfigRepository.APP_DIR, "config.json")
            if (configFile.exists()) {
                configFile.delete()
            }
        }
    }

    @Test
    fun `Encryption and Decryption round-trip with AES-CBC produces v2 prefix and recovers plaintext`() {
        val plaintext = "M4nut3nzi0ni_Pr0d_2026!#"
        val encrypted = AppConfigRepository.encryptPassword(plaintext)

        assertTrue(encrypted.startsWith("v2:"), "La password cifrata deve avere il prefisso v2:")
        val decrypted = AppConfigRepository.decryptPassword(encrypted)

        assertEquals(plaintext, decrypted, "La password decifrata deve corrispondere al testo in chiaro originale")
    }

    @Test
    fun `Blank and whitespace passwords return empty string without error`() {
        assertEquals("", AppConfigRepository.encryptPassword(""))
        assertEquals("", AppConfigRepository.encryptPassword("   "))
        assertEquals("", AppConfigRepository.decryptPassword(""))
        assertEquals("", AppConfigRepository.decryptPassword("   "))
    }

    @Test
    fun `Atomic configuration save and load cycle operates reliably`() {
        val testConfig = DbConfig(
            host = "10.0.0.50",
            port = "27019",
            dbName = "manutenzioni_prod",
            username = "tecnico_admin",
            encryptedPasswordBase64 = AppConfigRepository.encryptPassword("my_secret_pass")
        )

        AppConfigRepository.saveConfig(testConfig)
        val loaded = AppConfigRepository.loadConfig()

        assertNotNull(loaded, "La configurazione deve essere caricata con successo")
        assertEquals(testConfig.host, loaded.host)
        assertEquals(testConfig.port, loaded.port)
        assertEquals(testConfig.dbName, loaded.dbName)
        assertEquals(testConfig.username, loaded.username)
        assertEquals(testConfig.encryptedPasswordBase64, loaded.encryptedPasswordBase64)
        assertEquals("my_secret_pass", AppConfigRepository.decryptPassword(loaded.encryptedPasswordBase64))
    }

    @Test
    fun `ConnectionString builder formats correctly with and without credentials`() {
        val configWithAuth = DbConfig(
            host = "db.azienda.internal",
            port = "27017",
            username = "operatore",
            encryptedPasswordBase64 = AppConfigRepository.encryptPassword("chiave123")
        )
        val connectionStringAuth = AppConfigRepository.buildConnectionString(configWithAuth)
        assertEquals("mongodb://operatore:chiave123@db.azienda.internal:27017/?authSource=admin", connectionStringAuth)

        val configNoAuth = DbConfig(
            host = "localhost",
            port = "27017",
            username = "",
            encryptedPasswordBase64 = ""
        )
        val connectionStringNoAuth = AppConfigRepository.buildConnectionString(configNoAuth)
        assertEquals("mongodb://localhost:27017/?authSource=admin", connectionStringNoAuth)
    }
}
