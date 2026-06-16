package org.radarbase.mapper.enrichment

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.radarbase.mapper.config.ProviderConfig
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * [EnrichmentProvider] that fetches subject attributes from the RADAR Management Portal.
 *
 * On construction, it:
 * 1. Obtains an OAuth2 client-credentials token using `client_secret_post` (credentials in
 *    the POST body) from [tokenUrl]. Defaults to `{url}/oauth/token`; set `token_url` to a
 *    Hydra endpoint (e.g. `http://radar-hydra-public:4444/oauth2/token`) for Hydra deployments.
 * 2. Fetches all subjects for each configured project from `{url}/api/subjects?projectName={project}`.
 * 3. Builds an in-memory map of `login → attributes[subjectAttribute]`.
 *
 * The lookup is eager and read-only after construction — no I/O during [lookup] calls.
 *
 * `client_id` and `client_secret` can be overridden at runtime via the environment variables
 * `MANAGEMENT_PORTAL_CLIENT_ID` and `MANAGEMENT_PORTAL_CLIENT_SECRET`.
 *
 * Config example (Management Portal built-in auth):
 * ```yaml
 * provider:
 *   type: management_portal
 *   url: https://radar-base.example.com/managementportal
 *   client_id: radar_mapper
 *   client_secret: secret
 *   projects: [RECURRENT-GB]
 *   subject_attribute: REDCapRecordId
 * ```
 *
 * Config example (Hydra auth, with optional scope/audience):
 * ```yaml
 * provider:
 *   type: management_portal
 *   url: https://radar-base.example.com/managementportal
 *   token_url: http://radar-hydra-public:4444/oauth2/token
 *   client_id: radar_mapper
 *   client_secret: secret
 *   scope: "openid"
 *   projects: [RECURRENT-GB]
 *   subject_attribute: REDCapRecordId
 * ```
 */
class ManagementPortalEnrichmentProvider(
    override val name: String,
    config: ProviderConfig,
) : EnrichmentProvider {

    private val table: Map<String, String>

    init {
        val baseUrl = requireNotNull(config.url) { "Enrichment '$name': 'url' is required for management_portal provider" }
            .trimEnd('/')
        // Allow env var overrides for credentials (e.g. injected as Kubernetes Secret env vars)
        val clientId = (System.getenv("MANAGEMENT_PORTAL_CLIENT_ID") ?: config.clientId)
            ?.takeIf { it.isNotBlank() }
            ?: error("Enrichment '$name': 'client_id' is required (or set MANAGEMENT_PORTAL_CLIENT_ID)")
        val clientSecret = (System.getenv("MANAGEMENT_PORTAL_CLIENT_SECRET") ?: config.clientSecret)
            ?.takeIf { it.isNotBlank() }
            ?: error("Enrichment '$name': 'client_secret' is required (or set MANAGEMENT_PORTAL_CLIENT_SECRET)")
        val tokenUrl = config.tokenUrl?.takeIf { it.isNotBlank() } ?: "$baseUrl/oauth/token"
        val projects = config.effectiveProjects
        val subjectAttribute = requireNotNull(config.subjectAttribute) { "Enrichment '$name': 'subject_attribute' is required for management_portal provider" }

        val http = HttpClient.newHttpClient()
        val jackson = ObjectMapper()

        val token = fetchToken(http, jackson, tokenUrl, clientId, clientSecret, config.scope, config.audience)

        table = projects
            .flatMap { project -> fetchSubjects(http, jackson, baseUrl, project, token) }
            .mapNotNull { subject ->
                val login = subject.login?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val value = subject.attributes[subjectAttribute]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                login to value
            }
            .toMap()

        logger.info(
            "Enrichment '{}': loaded {} entries from Management Portal (projects={}, tokenUrl={})",
            name,
            table.size,
            projects,
            tokenUrl,
        )
    }

    override fun lookup(key: String): String? = table[key]

    private fun fetchToken(
        http: HttpClient,
        jackson: ObjectMapper,
        tokenUrl: String,
        clientId: String,
        clientSecret: String,
        scope: String?,
        audience: String?,
    ): String {
        val body = buildString {
            append("grant_type=client_credentials")
            append("&client_id=$clientId")
            append("&client_secret=$clientSecret")
            if (!scope.isNullOrBlank()) append("&scope=${scope.trim()}")
            if (!audience.isNullOrBlank()) append("&audience=${audience.trim()}")
        }

        val request = HttpRequest.newBuilder()
            .uri(URI.create(tokenUrl))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Enrichment '$name': failed to obtain token from $tokenUrl — HTTP ${response.statusCode()}"
        }

        return jackson.readTree(response.body())
            .get("access_token")
            ?.asText()
            ?: error("Enrichment '$name': token response did not contain 'access_token'")
    }

    private fun fetchSubjects(
        http: HttpClient,
        jackson: ObjectMapper,
        baseUrl: String,
        project: String,
        token: String,
    ): List<SubjectDto> {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/subjects?projectName=$project&size=${Int.MAX_VALUE}"))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) {
            "Enrichment '$name': failed to fetch subjects — HTTP ${response.statusCode()}"
        }

        return jackson.readerForListOf(SubjectDto::class.java).readValue(response.body())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class SubjectDto(
        val login: String? = null,
        val attributes: Map<String, String> = emptyMap(),
    )

    private companion object {
        val logger = LoggerFactory.getLogger(ManagementPortalEnrichmentProvider::class.java)!!
    }
}
