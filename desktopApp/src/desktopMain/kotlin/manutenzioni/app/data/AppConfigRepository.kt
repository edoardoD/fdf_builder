package manutenzioni.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.Key
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class DbConfig(
    val host: String = "localhost",
    val port: String = "27017",
    val dbName: String = "manutenzioni_db",
    val username: String = "",
    val encryptedPasswordBase64: String = ""
)

/**
 * Repository thread-safe per la persistenza e crittografia della configurazione
 * di connessione a MongoDB (~/.manutenzioni_maker/config.json).
 *
 * Fornisce:
 * - Cartella standard unificata: ~/.manutenzioni_maker
 * - Crittografia AES-CBC con IV casuale ("v2:")
 * - Scrittura atomica del file di configurazione con hardware sync (fsync) per prevenire corruzioni
 */
object AppConfigRepository {

    private val USER_HOME = System.getProperty("user.home")
    val APP_DIR = File(USER_HOME, ".manutenzioni_maker")

    private val CONFIG_FILE = File(APP_DIR, "config.json")
    private val TEMP_CONFIG_FILE = File(APP_DIR, "config.json.tmp")

    private const val ALGORITHM_KEY = "AES"
    private const val CIPHER_CBC_TRANSFORMATION = "AES/CBC/PKCS5Padding"
    private const val V2_PREFIX = "v2:"

    private val SECRET_KEY_BYTES = "ManutenzioniM4k3r!K3y2026123456".toByteArray().copyOf(16) // 128 bit key
    private val SECRET_KEY: Key = SecretKeySpec(SECRET_KEY_BYTES, ALGORITHM_KEY)
    private val secureRandom = SecureRandom()

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val fileLock = Any()

    init {
        synchronized(fileLock) {
            if (!APP_DIR.exists()) {
                APP_DIR.mkdirs()
            }
        }
    }

    /**
     * Cifra la password usando AES-CBC con IV casuale a 16 byte.
     * Il risultato è prefissato con "v2:" e contiene [IV (16 bytes) + Ciphertext] codificato in Base64.
     */
    fun encryptPassword(password: String): String {
        if (password.isBlank()) return ""
        return try {
            val iv = ByteArray(16)
            secureRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(CIPHER_CBC_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SECRET_KEY, ivSpec)

            val encryptedBytes = cipher.doFinal(password.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(iv.size + encryptedBytes.size)
            System.arraycopy(iv, 0, combined, 0, iv.size)
            System.arraycopy(encryptedBytes, 0, combined, iv.size, encryptedBytes.size)

            V2_PREFIX + Base64.getEncoder().encodeToString(combined)
        } catch (e: Exception) {
            System.err.println("Errore cifratura password: ${e.message}")
            ""
        }
    }

    /**
     * Decifra la password cifrata nel formato AES-CBC ("v2:").
     */
    fun decryptPassword(encryptedBase64: String): String {
        if (encryptedBase64.isBlank() || !encryptedBase64.startsWith(V2_PREFIX)) return ""

        return try {
            val rawBase64 = encryptedBase64.removePrefix(V2_PREFIX)
            val combined = Base64.getDecoder().decode(rawBase64)
            if (combined.size <= 16) return ""

            val iv = ByteArray(16)
            System.arraycopy(combined, 0, iv, 0, 16)
            val ciphertext = ByteArray(combined.size - 16)
            System.arraycopy(combined, 16, ciphertext, 0, ciphertext.size)

            val cipher = Cipher.getInstance(CIPHER_CBC_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SECRET_KEY, IvParameterSpec(iv))
            val decryptedBytes = cipher.doFinal(ciphertext)
            String(decryptedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            System.err.println("Errore decifratura password v2: ${e.message}")
            ""
        }
    }

    /**
     * Salva la configurazione sul disco in maniera atomica e thread-safe.
     */
    fun saveConfig(config: DbConfig) = synchronized(fileLock) {
        try {
            if (!APP_DIR.exists()) {
                APP_DIR.mkdirs()
            }
            val jsonString = json.encodeToString(config)

            // Scrittura su file temporaneo con flush fisico (fsync)
            FileOutputStream(TEMP_CONFIG_FILE).use { fos ->
                fos.write(jsonString.toByteArray(Charsets.UTF_8))
                fos.flush()
                fos.fd.sync()
            }

            // Sostituzione atomica del file
            try {
                Files.move(
                    TEMP_CONFIG_FILE.toPath(),
                    CONFIG_FILE.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(
                    TEMP_CONFIG_FILE.toPath(),
                    CONFIG_FILE.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
            }
        } catch (e: Exception) {
            System.err.println("Errore durante il salvataggio atomico di config.json: ${e.message}")
            TEMP_CONFIG_FILE.delete()
            throw e
        }
    }

    /**
     * Carica la configurazione persistita da ~/.manutenzioni_maker/config.json.
     */
    fun loadConfig(): DbConfig? = synchronized(fileLock) {
        if (!CONFIG_FILE.exists()) {
            return null
        }
        return try {
            json.decodeFromString<DbConfig>(CONFIG_FILE.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            System.err.println("Errore caricamento config.json: ${e.message}")
            null
        }
    }

    /**
     * Genera la connection string per MongoDB a partire da [DbConfig].
     */
    fun buildConnectionString(config: DbConfig): String {
        val authPart = if (config.username.isNotBlank()) {
            val pass = decryptPassword(config.encryptedPasswordBase64)
            if (pass.isNotBlank()) {
                "${config.username}:${pass}@"
            } else {
                "${config.username}@"
            }
        } else {
            ""
        }
        return "mongodb://$authPart${config.host}:${config.port}/?authSource=admin"
    }
}
