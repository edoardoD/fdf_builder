package manutenzioni.app.service

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import manutenzioni.domain.model.ComponentCandidate
import manutenzioni.domain.service.ProductSearchApi
import java.time.Instant

class EtimCatalogService : ProductSearchApi {

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    private val clientId = "desiderio_it"
    private val clientSecret = "Qpg31SbY74uZ26uQKSIz9X"
    private val tokenUrl = "https://etimauth.etim-international.com/connect/token"
    private val apiUrl = "https://etimapi.etim-international.com/api/v2/Class/Search"

    private var accessToken: String? = null
    private var tokenExpiry: Instant = Instant.MIN
    private val mutex = Mutex()

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long
    )

    @Serializable
    private data class EtimClassSearchResponse(
        val total: Int,
        val classes: List<EtimClass> = emptyList()
    )

    @Serializable
    private data class EtimClass(
        val code: String,
        val description: String? = null,
        val descriptionEn: String? = null
    )

    private suspend fun getValidToken(): String {
        mutex.withLock {
            if (accessToken != null && Instant.now().isBefore(tokenExpiry)) {
                return accessToken!!
            }

            val response = httpClient.submitForm(
                url = tokenUrl,
                formParameters = parameters {
                    append("grant_type", "client_credentials")
                    append("scope", "EtimApi")
                }
            ) {
                basicAuth(clientId, clientSecret)
            }

            if (response.status.isSuccess()) {
                val tokenResponse = response.body<TokenResponse>()
                accessToken = tokenResponse.accessToken
                // Sottraiamo 60 secondi come margine di sicurezza
                tokenExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn - 60)
                return accessToken!!
            } else {
                throw Exception("Failed to authenticate with ETIM API: ${response.status}")
            }
        }
    }

    override suspend fun search(query: String): List<ComponentCandidate> {
        val raw = query.trim()
        if (raw.isBlank()) return emptyList()

        val token = getValidToken()

        // Costruisci il body JSON per la ricerca
        val requestBody = buildString {
            append("""{""")
            append(""""from":0,""")
            append(""""size":10,""")
            append(""""languagecode":"it-IT",""")
            // Sanitizza la query per JSON
            append(""""searchString":"${raw.replace("\"", "\\\"")}",""")
            append(""""include":{"descriptions":true}""")
            append("""}""")
        }

        val response = httpClient.post(apiUrl) {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }

        if (response.status.isSuccess()) {
            val searchResponse = response.body<EtimClassSearchResponse>()
            
            // Estrai i parametri tecnici usando la vecchia euristica per popolare le caratteristiche
            val poli = extractPoli(raw)
            val curva = extractCurva(raw)
            val corrente = parseCorrente(raw)
            val potereInterruzione = extractPotereInterruzione(raw)
            
            val specs = mutableMapOf<String, String>()
            if (poli != null) specs["Poli"] = poli
            if (curva != null) specs["Curva"] = curva
            if (corrente != null) specs["Corrente Nominale"] = "${corrente}A"
            if (potereInterruzione != null) specs["Potere Interruzione"] = potereInterruzione

            return searchResponse.classes.map { etimClass ->
                ComponentCandidate(
                    etimClassId = etimClass.code,
                    etimClassName = etimClass.description ?: etimClass.descriptionEn ?: etimClass.code,
                    descrizioneStandard = etimClass.description ?: etimClass.descriptionEn ?: etimClass.code,
                    caratteristicheTecniche = specs,
                    variantiDisponibili = emptyList() // Nessuna variante cablata! L'utente le creerà
                )
            }
        } else {
            throw Exception("Failed to search ETIM classes: ${response.status}")
        }
    }

    // --- Metodi Euristici di parsing (mantenuti per estrarre le specs dal testo libero) ---

    private fun extractPoli(q: String): String? {
        val ql = q.lowercase()
        return when {
            ql.contains("4p") || ql.contains("tetrapolare") || ql.contains("3p+n") -> "4P"
            ql.contains("3p") || ql.contains("tripolare") -> "3P"
            (ql.contains("2p") && !ql.contains("1p+n")) || ql.contains("bipolare") -> "2P"
            (ql.contains("1p") && !ql.contains("1p+n")) || ql.contains("unipolare") -> "1P"
            ql.contains("1p+n") || ql.contains("1pn") -> "1P+N"
            else -> null
        }
    }

    private fun extractCurva(q: String): String? {
        val ql = q.lowercase()
        return when {
            ql.contains("curva b") || Regex("""\bb\s*\d{1,2}\b""").containsMatchIn(ql) -> "B"
            ql.contains("curva d") || Regex("""\bd\s*\d{1,2}\b""").containsMatchIn(ql) -> "D"
            ql.contains("curva c") || Regex("""\bc\s*\d{1,2}\b""").containsMatchIn(ql) -> "C"
            else -> null
        }
    }

    private fun extractPotereInterruzione(q: String): String? {
        val ql = q.lowercase()
        return when {
            ql.contains("6ka") || ql.contains("6000") -> "6kA"
            ql.contains("10ka") || ql.contains("10000") -> "10kA"
            ql.contains("4.5ka") || ql.contains("4500") -> "4.5kA"
            else -> null
        }
    }

    private fun parseCorrente(q: String): Int? {
        val ql = q.lowercase()
        val regexUnit = Regex("""\b(\d{1,3})\s*(?:a|amp|ampere)\b""")
        val matchUnit = regexUnit.find(ql)
        if (matchUnit != null) {
            val v = matchUnit.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }
        val regexCurva = Regex("""\b[cbd]\s*(\d{1,3})\b""")
        val matchCurva = regexCurva.find(ql)
        if (matchCurva != null) {
            val v = matchCurva.groupValues[1].toIntOrNull()
            if (v != null && v in 1..250) return v
        }
        return null
    }
}
