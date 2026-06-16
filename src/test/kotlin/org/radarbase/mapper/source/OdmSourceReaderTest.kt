package org.radarbase.mapper.source

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class OdmSourceReaderTest {

    private val reader = OdmSourceReader()

    private fun sampleStream() =
        checkNotNull(javaClass.getResourceAsStream("/org/radarbase/mapper/sample.xml"))

    @Test
    fun parsesCorrectNumberOfRecords() {
        assertEquals(2, reader.readStream(sampleStream()).size)
    }

    @Test
    fun parsesStudyMetadata() {
        val record = reader.readStream(sampleStream()).first()
        assertEquals("RECURRENT-GB", record.fields["StudyOID"])
        assertEquals("v1.0.0", record.fields["MetaDataVersionOID"])
    }

    @Test
    fun parsesSubjectKey() {
        val records = reader.readStream(sampleStream())
        assertEquals("5b3adcb0", records[0].fields["SubjectKey"])
        assertEquals("9c4bedf1", records[1].fields["SubjectKey"])
    }

    @Test
    fun parsesStudyEvent() {
        val record = reader.readStream(sampleStream()).first()
        assertEquals("Weekly|W6", record.fields["StudyEventOID"])
        assertEquals("W6", record.fields["StudyEventRepeatKey"])
    }

    @Test
    fun parsesFormAndItemGroup() {
        val record = reader.readStream(sampleStream()).first()
        assertEquals("Weekly", record.fields["FormOID"])
        assertEquals("Weekly_IG", record.fields["ItemGroupOID"])
        assertEquals("1", record.fields["IGRepeatKey"])
    }

    @Test
    fun parsesItems() {
        val record = reader.readStream(sampleStream()).first()
        assertEquals(4, record.items.size)
        assertEquals("wqpa_date", record.items[0].id)
        assertEquals("2026-03-20", record.items[0].value)
        assertEquals("wqpa_q29", record.items[1].id)
        assertEquals("5", record.items[1].value)
    }

    @Test
    fun omitsStudyEventRepeatKeyWhenAbsent() {
        // second record in sample has StudyEventRepeatKey present; verify missing key = absent field
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <ODM xmlns="http://www.cdisc.org/ns/odm/v1.3" FileType="Transactional">
                <ClinicalData StudyOID="S1" MetaDataVersionOID="v1">
                    <SubjectData SubjectKey="u1">
                        <StudyEventData StudyEventOID="Q|W1">
                            <FormData FormOID="Q">
                                <ItemGroupData ItemGroupOID="Q_IG" IGRepeatKey="1">
                                    <ItemData ItemOID="q1" Value="3"/>
                                </ItemGroupData>
                            </FormData>
                        </StudyEventData>
                    </SubjectData>
                </ClinicalData>
            </ODM>
        """.trimIndent().byteInputStream()

        val record = reader.readStream(xml).first()
        assertFalse(record.fields.containsKey("StudyEventRepeatKey"))
    }
}
