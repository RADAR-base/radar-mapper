package org.radarbase.mapper.enrichment

/**
 * Looks up an enrichment value for a given key.
 * Implementations load their lookup data eagerly on construction.
 */
interface EnrichmentProvider {
    /** Human-readable name of this provider, used in error messages. */
    val name: String

    /**
     * Returns the enrichment value for [key], or `null` if no mapping exists.
     * Must be thread-safe and free of I/O after construction.
     */
    fun lookup(key: String): String?
}
