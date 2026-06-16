package org.radarbase.mapper.config

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Root configuration for radar-mapper, loaded from `mapper.yml`.
 *
 * All enrichment slots — including `event_name` — live under `enrichment`. Slots are applied
 * in declaration order, so later slots can reference values resolved by earlier ones.
 *
 * Example:
 * ```yaml
 * source:
 *   type: local
 *   path: /data/odm/
 *
 * enrichment:
 *   - name: record_id
 *     source_field: SubjectKey
 *     output_field: SubjectKey
 *     provider:
 *       type: management_portal
 *       url: https://radar-base.example.com/managementportal
 *       # token_url: http://radar-hydra-public:4444/oauth2/token  # omit for MP built-in auth
 *       client_id: radar_mapper
 *       client_secret: secret
 *       projects: [RECURRENT-GB, RECURRENT-GB-PROXY]
 *       subject_attribute: REDCapRecordId
 *     on_missing: warn
 *   - name: event_name
 *     source_fields: [StudyEventOID, StudyOID]
 *     output_field: StudyEventOID
 *     provider:
 *       path: /config/event-lookup.csv
 *       key_columns: [questionnaireName, projectId]
 *       value_column: eventName
 *     on_missing: warn
 *
 * filter:
 *   exclude_items:
 *     - pacq_instructions_1
 *     - some_display_item
 *
 * destination:
 *   type: local
 *   path: /data/output/
 * ```
 */
data class MapperConfig(
    val source: SourceConfig,
    val enrichment: List<EnrichmentConfig> = emptyList(),
    val filter: FilterConfig = FilterConfig(),
    val destination: DestinationConfig,
)

data class SourceConfig(
    /** Storage backend: `local` (default) or `s3`. */
    val type: String = "local",
    /** Root path on the local filesystem, or object-key prefix within the S3 bucket. */
    val path: String,
    /** Required when [type] is `s3`. */
    val s3: S3StorageConfig? = null,
)

/** Format of the source files. */
data class SourceFormatConfig(
    val type: String = "odm",
)

data class EnrichmentConfig(
    /** Slot name. Used as the key in enriched fields and referenced by later slots' source_fields. */
    val name: String,
    /**
     * Single source field used as the lookup key. Use [sourceFields] for composite keys.
     * Exactly one of [sourceField] or [sourceFields] must be set.
     */
    @JsonProperty("source_field") val sourceField: String? = null,
    /**
     * Ordered list of source fields whose values are joined (with [ProviderConfig.keySeparator])
     * to form the composite lookup key. Takes precedence over [sourceField].
     */
    @JsonProperty("source_fields") val sourceFields: List<String>? = null,
    val provider: ProviderConfig,
    @JsonProperty("on_missing") val onMissing: OnMissing = OnMissing.WARN,
    /**
     * Field name to write the enriched value into on the output record.
     * Defaults to the slot name if not set.
     */
    @JsonProperty("output_field") val outputField: String? = null,
) {
    val effectiveSourceFields: List<String>
        get() = sourceFields
            ?: sourceField?.let { listOf(it) }
            ?: error("Either 'source_field' or 'source_fields' must be specified")
}

data class ProviderConfig(
    /** Provider type: `csv` (default) or `management_portal`. */
    val type: String = "csv",

    // ── CSV fields ────────────────────────────────────────────────────────────
    /** Path to the CSV file. Required when [type] is `csv`. */
    val path: String? = null,
    /** Single lookup key column. Use [keyColumns] for composite keys. */
    @JsonProperty("key_column") val keyColumn: String? = null,
    /**
     * Ordered list of CSV columns whose values are concatenated (with [keySeparator])
     * to form the composite lookup key. Takes precedence over [keyColumn].
     */
    @JsonProperty("key_columns") val keyColumns: List<String>? = null,
    /** Separator used to join composite key column values. Default: tab character. */
    @JsonProperty("key_separator") val keySeparator: String = "\t",
    /** Value column name. Required when [type] is `csv`. */
    @JsonProperty("value_column") val valueColumn: String? = null,

    // ── Management Portal fields ──────────────────────────────────────────────
    /** Base URL of the Management Portal. Required when [type] is `management_portal`. */
    val url: String? = null,
    /** OAuth2 client ID. Required when [type] is `management_portal`. */
    @JsonProperty("client_id") val clientId: String? = null,
    /** OAuth2 client secret. Required when [type] is `management_portal`. */
    @JsonProperty("client_secret") val clientSecret: String? = null,
    /** Single RADAR project name. Use [projects] to specify multiple. */
    val project: String? = null,
    /** RADAR project names to fetch subjects from. Takes precedence over [project]. */
    val projects: List<String>? = null,
    /** Subject attribute key whose value is used as the enrichment value (e.g. `REDCapRecordId`). */
    @JsonProperty("subject_attribute") val subjectAttribute: String? = null,
    /**
     * OAuth2 token endpoint URL. Defaults to `{url}/oauth/token` when not set.
     * Set this to a Hydra token URL (e.g. `http://radar-hydra-public:4444/oauth2/token`)
     * when using Hydra as the authorization server instead of Management Portal's built-in auth.
     */
    @JsonProperty("token_url") val tokenUrl: String? = null,
    /** OAuth2 scope(s) to request. Optional; omitted from the token request when blank. */
    val scope: String? = null,
    /** OAuth2 audience. Optional; omitted from the token request when blank. */
    val audience: String? = null,
) {
    /** Resolved list of project names from [projects] or [project]. */
    val effectiveProjects: List<String>
        get() = projects
            ?: project?.let { listOf(it) }
            ?: error("Either 'project' or 'projects' must be specified for management_portal provider")

    val effectiveKeyColumns: List<String>
        get() = keyColumns
            ?: keyColumn?.let { listOf(it) }
            ?: error("Either 'key_column' or 'key_columns' must be specified in provider config")
}

enum class OnMissing {
    /** Abort the run immediately. */
    FAIL,

    /** Log a warning and skip the record. */
    WARN,

    /** Silently skip the record. */
    SKIP,
}

/**
 * Declarative configuration for item-level filtering.
 *
 * Items whose OID appears in [excludeItems] are stripped from every record before writing.
 * The record itself is always kept — only its item list is pruned.
 *
 * Example:
 * ```yaml
 * filter:
 *   exclude_items:
 *     - pacq_instructions_1
 *     - some_display_item
 * ```
 */
data class FilterConfig(
    /** Item OIDs to remove from every record. */
    @JsonProperty("exclude_items") val excludeItems: List<String> = emptyList(),
)

data class DestinationConfig(
    /** Storage backend: `local` (default) or `s3`. */
    val type: String = "local",
    /** Root path on the local filesystem, or object-key prefix within the S3 bucket. */
    val path: String,
    /** Required when [type] is `s3`. */
    val s3: S3StorageConfig? = null,
)

/** Format to write output in. */
data class DestinationFormatConfig(
    val type: String = "odm",
)
