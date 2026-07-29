package org.radarbase.mapper.enrichment

import com.fasterxml.jackson.dataformat.csv.CsvMapper
import com.fasterxml.jackson.dataformat.csv.CsvSchema
import org.radarbase.mapper.config.ProviderConfig
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * [EnrichmentProvider] backed by a CSV file.
 *
 * The file is loaded once at construction time into an in-memory map.
 * Columns are identified by [ProviderConfig.keyColumn] and [ProviderConfig.valueColumn].
 *
 * Example `user-lookup.csv`:
 * ```
 * userId,recordId
 * 5b3adcb0-df49-45e9-8ed3-a042083d8e48,1
 * ```
 */
class CsvEnrichmentProvider(
    override val name: String,
    config: ProviderConfig,
) : EnrichmentProvider {

    private val table: Map<String, String>

    init {
        val csvPath = requireNotNull(config.path) { "Enrichment '$name': 'path' is required for csv provider" }
        val valueColumn = requireNotNull(config.valueColumn) { "Enrichment '$name': 'value_column' is required for csv provider" }
        val path = Path.of(csvPath)
        require(Files.exists(path)) { "Enrichment '$name': CSV file not found at '$csvPath'" }

        val csvMapper = CsvMapper()
        val schema = CsvSchema.emptySchema().withHeader()

        val keyColumns = config.effectiveKeyColumns
        table = Files.newBufferedReader(path).use { reader ->
            @Suppress("UNCHECKED_CAST")
            csvMapper.readerFor(Map::class.java)
                .with(schema)
                .readValues<Map<String, String>>(reader)
                .asSequence()
                .mapNotNull { row ->
                    val keyParts = keyColumns.map { col ->
                        row[col]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    }
                    val key = keyParts.joinToString(config.keySeparator)
                    val value = row[valueColumn]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    key to value
                }
                .toMap()
        }

        logger.info("Enrichment '{}': loaded {} entries from '{}'", name, table.size, config.path)
    }

    override fun lookup(key: String): String? = table[key]

    private companion object {
        val logger = LoggerFactory.getLogger(CsvEnrichmentProvider::class.java)!!
    }
}
