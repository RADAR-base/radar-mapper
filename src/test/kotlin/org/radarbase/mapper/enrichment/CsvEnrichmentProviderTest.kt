package org.radarbase.mapper.enrichment

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.radarbase.mapper.config.ProviderConfig

class CsvEnrichmentProviderTest {

    private fun providerFor(
        resource: String,
        keyColumn: String? = null,
        keyColumns: List<String>? = null,
        keySeparator: String = "\t",
        valueColumn: String,
    ): CsvEnrichmentProvider {
        val path = checkNotNull(javaClass.getResource("/org/radarbase/mapper/$resource")).path
        return CsvEnrichmentProvider(
            name = "test",
            config = ProviderConfig(
                path = path,
                keyColumn = keyColumn,
                keyColumns = keyColumns,
                keySeparator = keySeparator,
                valueColumn = valueColumn,
            ),
        )
    }

    @Test
    fun loadsUserLookup() {
        val provider = providerFor("user-lookup.csv", keyColumn = "userId", valueColumn = "recordId")
        assertEquals("1", provider.lookup("5b3adcb0"))
        assertEquals("2", provider.lookup("9c4bedf1"))
    }

    @Test
    fun returnsNullForUnknownKey() {
        val provider = providerFor("user-lookup.csv", keyColumn = "userId", valueColumn = "recordId")
        assertNull(provider.lookup("unknown-user"))
    }

    @Test
    fun compositeKeyLookupReturnsCorrectEventName() {
        val provider = providerFor(
            "event-lookup.csv",
            keyColumns = listOf("questionnaireName", "projectId"),
            keySeparator = "\t",
            valueColumn = "eventName",
        )
        // Regular event names resolved via projectId
        assertEquals("week_1_participant_arm_1", provider.lookup("Weekly|W1\tRECURRENT-GB"))
        assertEquals("week_1_proxy_arm_1", provider.lookup("Weekly|W1\tRECURRENT-GB-PROXY"))
        assertEquals("month_3_participant_arm_1", provider.lookup("Weekly|W6\tRECURRENT-GB"))
        // Intentional REDCap typo: 'participan' not 'participant'
        assertEquals("month_6_participan_arm_1", provider.lookup("Weekly|W12\tRECURRENT-GB"))
        assertEquals("month_9_participan_arm_1", provider.lookup("Weekly|W18\tRECURRENT-GB"))
    }

    @Test
    fun compositeKeyReturnsNullForUnknownCombination() {
        val provider = providerFor(
            "event-lookup.csv",
            keyColumns = listOf("questionnaireName", "projectId"),
            keySeparator = "\t",
            valueColumn = "eventName",
        )
        assertNull(provider.lookup("Weekly|W99\tRECURRENT-GB"))
    }
}
