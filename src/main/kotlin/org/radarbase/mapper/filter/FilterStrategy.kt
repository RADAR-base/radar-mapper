package org.radarbase.mapper.filter

import org.radarbase.mapper.source.MappedRecord

/**
 * Transforms a [MappedRecord] by removing items that do not satisfy the filter.
 * The record itself is always kept; only its [MappedRecord.items] list is pruned.
 */
interface FilterStrategy {
    fun apply(record: MappedRecord): MappedRecord
}
