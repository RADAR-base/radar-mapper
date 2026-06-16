package org.radarbase.mapper.pipeline

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.radarbase.mapper.config.DestinationConfig
import org.radarbase.mapper.config.EnrichmentConfig
import org.radarbase.mapper.config.EventNameConfig
import org.radarbase.mapper.config.MapperConfig
import org.radarbase.mapper.config.OnMissing
import org.radarbase.mapper.config.ProviderConfig
import org.radarbase.mapper.config.SourceConfig
import org.radarbase.mapper.source.OdmSourceReader
import java.nio.file.Files
import java.nio.file.Path

class MapperPipelineTest {

    private fun resourcePath(name: String): String =
        checkNotNull(javaClass.getResource("/org/radarbase/mapper/$name")).path

    private fun buildConfig(
        sourcePath: String,
        destPath: String,
        onMissing: OnMissing = OnMissing.FAIL,
    ) = MapperConfig(
        source = SourceConfig(path = sourcePath),
        enrichment = mapOf(
            "record_id" to EnrichmentConfig(
                sourceField = "SubjectKey",
                provider = ProviderConfig(
                    path = resourcePath("user-lookup.csv"),
                    keyColumn = "userId",
                    valueColumn = "recordId",
                ),
                onMissing = onMissing,
            ),
        ),
        eventName = EventNameConfig(
            sourceFields = listOf("StudyEventOID", "StudyOID"),
            provider = ProviderConfig(
                path = resourcePath("event-lookup.csv"),
                keyColumns = listOf("questionnaireName", "projectId"),
                valueColumn = "eventName",
            ),
            onMissing = onMissing,
        ),
        destination = DestinationConfig(path = destPath),
    )

    @Test
    fun enrichesRecordsAndWritesOdm(@TempDir tempDir: Path) {
        val sourceDir = Files.createTempDirectory(tempDir, "source")
        Files.copy(Path.of(resourcePath("sample.xml")), sourceDir.resolve("sample.xml"))

        val destDir = tempDir.resolve("output")
        MapperPipeline(buildConfig(sourceDir.toString(), destDir.toString())).run()

        val records = OdmSourceReader().readFile(destDir.resolve("sample.xml"))
        assertEquals(2, records.size)

        // record_id enriched into SubjectKey
        assertEquals("1", records[0].fields["SubjectKey"])
        assertEquals("2", records[1].fields["SubjectKey"])

        // event name from composite lookup (questionnaireName + projectId)
        assertEquals("month_3_participant_arm_1", records[0].fields["StudyEventOID"])  // Weekly|W6 + RECURRENT-GB
        assertEquals("week_1_participant_arm_1", records[1].fields["StudyEventOID"])   // Weekly|W1 + RECURRENT-GB

        // repeat key cleared
        assertFalse(records.any { it.fields.containsKey("StudyEventRepeatKey") })

        // items preserved
        assertEquals(4, records[0].items.size)
        assertEquals("wqpa_date", records[0].items[0].id)
        assertEquals("2026-03-20", records[0].items[0].value)
    }

    @Test
    fun correctlyResolvesTypoEventNames(@TempDir tempDir: Path) {
        // Write an ODM with Weekly|W12 which maps to month_6_participan_arm_1 (REDCap typo)
        val sourceDir = Files.createTempDirectory(tempDir, "source")
        sourceDir.resolve("w12.xml").toFile().writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3" FileType="Transactional">
                <ClinicalData StudyOID="RECURRENT-GB" MetaDataVersionOID="v1.0.0">
                    <SubjectData SubjectKey="5b3adcb0">
                        <StudyEventData StudyEventOID="Weekly|W12" StudyEventRepeatKey="W12">
                            <FormData FormOID="Weekly">
                                <ItemGroupData ItemGroupOID="Weekly_IG" IGRepeatKey="1">
                                    <ItemData ItemOID="wqpa_q29" Value="4"/>
                                </ItemGroupData>
                            </FormData>
                        </StudyEventData>
                    </SubjectData>
                </ClinicalData>
            </ODM>
            """.trimIndent(),
        )

        val destDir = tempDir.resolve("output")
        MapperPipeline(buildConfig(sourceDir.toString(), destDir.toString())).run()

        val records = OdmSourceReader().readFile(destDir.resolve("w12.xml"))
        assertEquals(1, records.size)
        // Verify the lookup returns the exact REDCap typo string
        assertEquals("month_6_participan_arm_1", records[0].fields["StudyEventOID"])
    }

    @Test
    fun failsLoudlyWhenUserMissingFromLookup(@TempDir tempDir: Path) {
        val sourceDir = Files.createTempDirectory(tempDir, "source")
        sourceDir.resolve("unknown.xml").toFile().writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3" FileType="Transactional">
                <ClinicalData StudyOID="RECURRENT-GB" MetaDataVersionOID="v1.0.0">
                    <SubjectData SubjectKey="unknown-user">
                        <StudyEventData StudyEventOID="Weekly|W1">
                            <FormData FormOID="Weekly">
                                <ItemGroupData ItemGroupOID="Weekly_IG" IGRepeatKey="1">
                                    <ItemData ItemOID="wqpa_q29" Value="5"/>
                                </ItemGroupData>
                            </FormData>
                        </StudyEventData>
                    </SubjectData>
                </ClinicalData>
            </ODM>
            """.trimIndent(),
        )

        val destDir = tempDir.resolve("output")
        assertThrows(EnrichmentException::class.java) {
            MapperPipeline(buildConfig(sourceDir.toString(), destDir.toString(), onMissing = OnMissing.FAIL)).run()
        }
    }

    @Test
    fun skipsRecordWhenOnMissingIsSkip(@TempDir tempDir: Path) {
        val sourceDir = Files.createTempDirectory(tempDir, "source")
        sourceDir.resolve("unknown.xml").toFile().writeText(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3" FileType="Transactional">
                <ClinicalData StudyOID="RECURRENT-GB" MetaDataVersionOID="v1.0.0">
                    <SubjectData SubjectKey="unknown-user">
                        <StudyEventData StudyEventOID="Weekly|W1">
                            <FormData FormOID="Weekly">
                                <ItemGroupData ItemGroupOID="Weekly_IG" IGRepeatKey="1">
                                    <ItemData ItemOID="wqpa_q29" Value="5"/>
                                </ItemGroupData>
                            </FormData>
                        </StudyEventData>
                    </SubjectData>
                </ClinicalData>
            </ODM>
            """.trimIndent(),
        )

        val destDir = tempDir.resolve("output")
        MapperPipeline(buildConfig(sourceDir.toString(), destDir.toString(), onMissing = OnMissing.SKIP)).run()

        assertFalse(Files.exists(destDir.resolve("unknown.xml")))
    }
}
