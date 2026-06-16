package org.radarbase.mapper.filter

import org.radarbase.mapper.config.FilterConfig
import org.radarbase.mapper.source.MappedRecord

/**
 * Strips items whose OID appears in [FilterConfig.excludeItems] from every record.
 * Records are never dropped — only their item lists are pruned.
 */
class RecordFilter(config: FilterConfig) : FilterStrategy {

    private val excludedIds: Set<String> = config.excludeItems.toHashSet()

    override fun apply(record: MappedRecord): MappedRecord {
        if (excludedIds.isEmpty()) return record
        val filtered = record.items.filterNot { it.id in excludedIds }
        return if (filtered.size == record.items.size) record else record.copy(items = filtered)
    }
}
