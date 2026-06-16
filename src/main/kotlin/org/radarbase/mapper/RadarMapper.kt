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

    @JvmStatic
    fun main(args: Array<String>) {
        val configPath = args.firstOrNull()
            ?: System.getenv("MAPPER_CONFIG")
            ?: "mapper.yml"

        logger.info("Loading configuration from '{}'", configPath)

        val config = try {
            yamlMapper.readValue(File(configPath), MapperConfig::class.java)
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
