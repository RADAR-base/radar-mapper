package org.radarbase.mapper.pipeline

import org.radarbase.mapper.config.MapperConfig
import org.radarbase.mapper.config.OnMissing
import org.radarbase.mapper.enrichment.CsvEnrichmentProvider
import org.radarbase.mapper.enrichment.EnrichmentProvider
import org.radarbase.mapper.enrichment.ManagementPortalEnrichmentProvider
import org.radarbase.mapper.filter.FilterStrategy
import org.radarbase.mapper.filter.RecordFilter
import org.radarbase.mapper.source.MappedRecord
import org.radarbase.mapper.source.OdmSourceReader
import org.radarbase.mapper.source.SourceReader
import org.radarbase.mapper.storage.LocalStorageService
import org.radarbase.mapper.storage.S3StorageService
import org.radarbase.mapper.storage.StorageService
import org.radarbase.mapper.writer.OdmWriter
import org.radarbase.mapper.writer.RecordWriter
import org.slf4j.LoggerFactory
import java.nio.file.Files

/**
 * Orchestrates the full mapper run:
 * 1. Scan source path for ODM XML files.
 * 2. Parse each file into [MappedRecord] list.
 * 3. Apply enrichment to each record's [MappedRecord.fields].
 * 4. Write enriched records to the destination path.
 *
 * Enrichment providers are loaded eagerly on construction.
 */
class MapperPipeline(private val config: MapperConfig) {

    private val storage: StorageService = when (config.source.type) {
        "local" -> LocalStorageService()
        "s3" -> S3StorageService(requireNotNull(config.source.s3) { "source.s3 config is required when source.type=s3" })
        else -> error("Unknown source storage type '${config.source.type}'")
    }

    private val sourceReader: SourceReader = OdmSourceReader()

    private val writer: RecordWriter = OdmWriter()

    private val providers: Map<String, EnrichmentProvider> = config.enrichment
        .associate { it.name to buildProvider(it.name, it.provider) }

    private val filter: FilterStrategy = RecordFilter(config.filter)

    private fun buildProvider(name: String, config: org.radarbase.mapper.config.ProviderConfig): EnrichmentProvider =
        when (config.type) {
            "csv" -> CsvEnrichmentProvider(name, config)
            "management_portal" -> ManagementPortalEnrichmentProvider(name, config)
            else -> error("Unknown enrichment provider type '${config.type}' for '$name'")
        }

    fun run() {
        val sourceRoot = config.source.path.trimEnd('/')
        val destRoot = config.destination.path.trimEnd('/')

        storage.ensureOutputDirectory(destRoot)

        val files = storage.listFiles(sourceRoot, ".xml")
        logger.info("Found {} ODM file(s) in {}", files.size, sourceRoot)

        var totalWritten = 0
        var totalSkipped = 0

        for (filePath in files) {
            val (written, skipped) = processFile(filePath, sourceRoot, destRoot)
            totalWritten += written
            totalSkipped += skipped
        }

        logger.info("Run complete: {} record(s) written, {} record(s) skipped", totalWritten, totalSkipped)
    }

    private fun processFile(filePath: String, sourceRoot: String, destRoot: String): Pair<Int, Int> {
        val destPath = destRoot + filePath.removePrefix(sourceRoot)
        if (storage.exists(destPath)) {
            logger.debug("Skipping already-processed file: {}", filePath)
            return Pair(0, 0)
        }

        val records = storage.newInputStream(filePath).use { sourceReader.readStream(it) }
        var skipped = 0

        val enriched = records.mapNotNull { record ->
            try {
                filter.apply(enrich(record))
            } catch (e: EnrichmentException) {
                when (config.enrichment.find { it.name == e.slot }?.onMissing ?: OnMissing.WARN) {
                    OnMissing.FAIL -> throw e
                    OnMissing.WARN -> {
                        logger.warn("Skipping record — {}", e.message)
                        skipped++
                        null
                    }
                    OnMissing.SKIP -> {
                        skipped++
                        null
                    }
                }
            }
        }

        // Only persist the output if no records were warned-and-skipped. Warned records may
        // become enrichable on the next run (e.g. a subject gets a REDCap ID assigned), so
        // leaving the destination absent lets the file be retried. Use on_missing: skip to
        // permanently drop a record and still write the file.
        if (enriched.isNotEmpty() && skipped == 0) {
            val tempFile = Files.createTempFile("radar-mapper-", ".xml")
            try {
                writer.write(enriched, tempFile)
                storage.store(tempFile, destPath)
                logger.debug("Wrote {} record(s) to {}", enriched.size, destPath)
            } catch (e: Exception) {
                Files.deleteIfExists(tempFile)
                throw e
            }
        } else if (skipped > 0) {
            logger.warn(
                "{}: {} record(s) skipped — file will be retried on the next run",
                filePath,
                skipped,
            )
        }

        return Pair(enriched.size, skipped)
    }

    private fun enrich(record: MappedRecord): MappedRecord {
        val fields = record.fields.toMutableMap()

        // Apply slots in declaration order. Each slot's result is stored under its name so
        // later slots can reference it in their source_fields, then written to output_field.
        for (enrichConfig in config.enrichment) {
            val slot = enrichConfig.name
            val keyParts = enrichConfig.effectiveSourceFields.map { field ->
                fields[field]
                    ?: throw EnrichmentException(slot, "source field '$field' not found in record")
            }
            val key = keyParts.joinToString(enrichConfig.provider.keySeparator)
            val value = providers.getValue(slot).lookup(key)
                ?: throw EnrichmentException(slot, "no mapping for key '$key'")
            fields[slot] = value
            val outputField = enrichConfig.outputField
            if (outputField != null) fields[outputField] = value
        }

        fields.remove("StudyEventRepeatKey")

        return record.copy(fields = fields)
    }

    private companion object {
        val logger = LoggerFactory.getLogger(MapperPipeline::class.java)!!
    }
}

class EnrichmentException(val slot: String, message: String) :
    IllegalStateException("Enrichment '$slot': $message")
