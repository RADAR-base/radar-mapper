package org.radarbase.mapper.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.radarbase.mapper.config.FilterConfig
import org.radarbase.mapper.source.MappedItem
import org.radarbase.mapper.source.MappedRecord

class RecordFilterTest {

    private fun record(vararg itemIds: String) = MappedRecord(
        fields = emptyMap(),
        items = itemIds.map { MappedItem(id = it, value = "x") },
    )

    @Test
    fun `no exclusions — record unchanged`() {
        val filter = RecordFilter(FilterConfig())
        val record = record("q1", "q2")
        assertEquals(record, filter.apply(record))
    }

    @Test
    fun `excluded item is removed`() {
        val filter = RecordFilter(FilterConfig(excludeItems = listOf("pacq_instructions_1")))
        val result = filter.apply(record("pacq_instructions_1", "q1", "q2"))
        assertEquals(listOf("q1", "q2"), result.items.map { it.id })
    }

    @Test
    fun `multiple excluded items are all removed`() {
        val filter = RecordFilter(FilterConfig(excludeItems = listOf("inst_1", "inst_2")))
        val result = filter.apply(record("inst_1", "q1", "inst_2", "q2"))
        assertEquals(listOf("q1", "q2"), result.items.map { it.id })
    }

    @Test
    fun `exclusion of absent item leaves record unchanged`() {
        val filter = RecordFilter(FilterConfig(excludeItems = listOf("nonexistent")))
        val record = record("q1", "q2")
        assertEquals(record, filter.apply(record))
    }

    @Test
    fun `fields are preserved after item filtering`() {
        val filter = RecordFilter(FilterConfig(excludeItems = listOf("inst_1")))
        val record = MappedRecord(
            fields = mapOf("SubjectKey" to "1", "StudyEventOID" to "SE_BASELINE"),
            items = listOf(MappedItem("inst_1", "ignore"), MappedItem("q1", "42")),
        )
        val result = filter.apply(record)
        assertEquals(record.fields, result.fields)
        assertEquals(listOf("q1"), result.items.map { it.id })
    }
}
