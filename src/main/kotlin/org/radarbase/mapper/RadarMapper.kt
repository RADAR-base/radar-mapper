package org.radarbase.mapper

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.radarbase.mapper.config.MapperConfig
import org.radarbase.mapper.pipeline.EnrichmentException
import org.radarbase.mapper.pipeline.MapperPipeline
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.system.exitProcess

object RadarMapper {

    private val logger = LoggerFactory.getLogger(RadarMapper::class.java)

    private val yamlMapper = ObjectMapper(YAMLFactory())
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .registerKotlinModule()

    private fun validateConfig(config: MapperConfig) {
        require(config.source.path.isNotBlank()) { "source.path must not be blank" }
        require(config.destination.path.isNotBlank()) { "destination.path must not be blank" }

        if (config.source.type == "s3") {
            requireNotNull(config.source.s3) { "source.s3 config is required when source.type=s3" }
            require(config.source.s3.endpoint.isNotBlank()) { "source.s3.endpoint must not be blank" }
            require(config.source.s3.bucket.isNotBlank()) { "source.s3.bucket must not be blank" }
        }
        if (config.destination.type == "s3") {
            requireNotNull(config.destination.s3) { "destination.s3 config is required when destination.type=s3" }
            require(config.destination.s3.endpoint.isNotBlank()) { "destination.s3.endpoint must not be blank" }
            require(config.destination.s3.bucket.isNotBlank()) { "destination.s3.bucket must not be blank" }
        }

        for (slot in config.enrichment) {
            require(slot.sourceField != null || slot.sourceFields != null) {
                "enrichment '${slot.name}': either 'source_field' or 'source_fields' must be set"
            }
            val p = slot.provider
            when (p.type) {
                "csv" -> {
                    requireNotNull(p.path) { "enrichment '${slot.name}': provider.path is required for csv provider" }
                    require(p.keyColumn != null || p.keyColumns != null) {
                        "enrichment '${slot.name}': either 'key_column' or 'key_columns' must be set for csv provider"
                    }
                    requireNotNull(p.valueColumn) { "enrichment '${slot.name}': provider.value_column is required for csv provider" }
                }
                "management_portal" -> {
                    requireNotNull(p.url) { "enrichment '${slot.name}': provider.url is required for management_portal provider" }
                    require(p.project != null || p.projects != null) {
                        "enrichment '${slot.name}': either 'project' or 'projects' must be set for management_portal provider"
                    }
                    requireNotNull(p.subjectAttribute) {
                        "enrichment '${slot.name}': provider.subject_attribute is required for management_portal provider"
                    }
                }
                else -> throw IllegalArgumentException("enrichment '${slot.name}': unknown provider type '${p.type}'")
            }
        }

        logger.info("Configuration validated successfully")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val configPath = args.firstOrNull()
            ?: System.getenv("MAPPER_CONFIG")
            ?: "mapper.yml"

        logger.info("Loading configuration from '{}'", configPath)

        val config = try {
            val loaded = yamlMapper.readValue(File(configPath), MapperConfig::class.java)
            validateConfig(loaded)
            loaded
        } catch (e: Exception) {
            logger.error("Failed to load config from '{}': {}", configPath, e.message)
            exitProcess(1)
        }

        try {
            MapperPipeline(config).run()
        } catch (e: EnrichmentException) {
            logger.error(
                "Enrichment failure — add the missing entry to the lookup table and retry. {}",
                e.message,
            )
            exitProcess(2)
        } catch (e: Exception) {
            logger.error("Unexpected error: {}", e.message, e)
            exitProcess(3)
        }
    }
}
